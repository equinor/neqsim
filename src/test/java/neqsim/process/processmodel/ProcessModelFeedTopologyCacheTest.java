package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests cached external-feed discovery and topology invalidation in {@link ProcessModel}. */
class ProcessModelFeedTopologyCacheTest {

  /** ProcessSystem that counts complete feed-topology scans. */
  private static final class CountingProcessSystem extends ProcessSystem {
    private static final long serialVersionUID = 1L;
    private int producedScans;
    private int inletScans;

    private CountingProcessSystem(String name) {
      super(name);
    }

    /** {@inheritDoc} */
    @Override
    void collectProducedStreams(Set<StreamInterface> produced) {
      producedScans++;
      super.collectProducedStreams(produced);
    }

    /** {@inheritDoc} */
    @Override
    void collectInletStreams(Set<StreamInterface> inlets) {
      inletScans++;
      super.collectInletStreams(inlets);
    }
  }

  /** Creates a small single-phase SRK gas. */
  private static SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Creates a configured feed stream. */
  private static Stream createFeed(String name, double flowKgPerHour) {
    Stream feed = new Stream(name, createGasFluid());
    feed.setFlowRate(flowKgPerHour, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    return feed;
  }

  /** Adds a feed and heater to a process area. */
  private static Heater addHeatedFeed(ProcessSystem area, String prefix, double flowKgPerHour) {
    Stream feed = createFeed(prefix + " feed", flowKgPerHour);
    Heater heater = new Heater(prefix + " heater", feed);
    heater.setOutletTemperature(303.15);
    area.add(feed);
    area.add(heater);
    return heater;
  }

  /** Repeated flow reads reuse topology while current values remain live. */
  @Test
  void cachedFeedTopologyReadsCurrentFlowAndTracksChildMutations() {
    CountingProcessSystem area = new CountingProcessSystem("area");
    addHeatedFeed(area, "main", 100.0);
    ProcessModel model = new ProcessModel();
    model.add("area", area);

    assertEquals(100.0, model.getTotalFeedFlowRate(), 0.0);
    assertEquals(1, area.producedScans);
    assertEquals(1, area.inletScans);
    assertEquals(100.0, model.getTotalFeedFlowRate(), 0.0);
    assertEquals(1, area.producedScans, "unchanged topology should not be rescanned");
    assertEquals(1, area.inletScans, "unchanged topology should not be rescanned");

    Stream mainFeed = (Stream) area.getUnit("main feed");
    mainFeed.setFlowRate(125.0, "kg/hr");
    mainFeed.setTemperature(30.0, "C");
    mainFeed.setPressure(55.0, "bara");
    assertEquals(125.0, model.getTotalFeedFlowRate(), 0.0, "flow values must never be cached");
    assertEquals(1, area.producedScans, "state changes should not invalidate topology");
    assertEquals(1, area.inletScans, "state changes should not invalidate topology");

    addHeatedFeed(area, "added", 25.0);
    assertEquals(150.0, model.getTotalFeedFlowRate(), 0.0);
    assertEquals(2, area.producedScans, "child structure version should invalidate the model plan");
    assertEquals(2, area.inletScans, "child structure version should invalidate the model plan");

    area.removeUnit("added heater");
    area.removeUnit("added feed");
    assertEquals(125.0, model.getTotalFeedFlowRate(), 0.0);
    assertEquals(3, area.producedScans);
    assertEquals(3, area.inletScans);
  }

  /** Duplicate consumers count an external feed once, and area replacement invalidates the plan. */
  @Test
  void identityDistinctFeedsSurviveAreaRemovalAndReplacement() {
    ProcessSystem firstArea = new ProcessSystem("first");
    Stream sharedFeed = createFeed("shared feed", 100.0);
    firstArea.add(sharedFeed);
    firstArea.add(new Heater("first consumer", sharedFeed));
    firstArea.add(new Heater("second consumer", sharedFeed));

    ProcessModel model = new ProcessModel();
    model.add("first", firstArea);
    assertEquals(100.0, model.getTotalFeedFlowRate(), 0.0, "one feed identity must be counted once");

    ProcessSystem secondArea = new ProcessSystem("second");
    addHeatedFeed(secondArea, "second", 20.0);
    model.add("second", secondArea);
    assertEquals(120.0, model.getTotalFeedFlowRate(), 0.0);

    assertTrue(model.remove("second"));
    assertEquals(100.0, model.getTotalFeedFlowRate(), 0.0);

    ProcessSystem replacement = new ProcessSystem("replacement");
    addHeatedFeed(replacement, "replacement", 30.0);
    model.add("second", replacement);
    assertEquals(130.0, model.getTotalFeedFlowRate(), 0.0);
  }

  /** Explicit child invalidation updates the feed boundary after direct stream rewiring. */
  @Test
  void rewiringAChildAreaInvalidatesCachedFeedTopology() {
    ProcessSystem upstream = new ProcessSystem("upstream");
    Heater producer = addHeatedFeed(upstream, "upstream", 100.0);

    ProcessSystem downstream = new ProcessSystem("downstream");
    Heater consumer = addHeatedFeed(downstream, "downstream", 50.0);

    ProcessModel model = new ProcessModel();
    model.add("upstream", upstream);
    model.add("downstream", downstream);
    assertEquals(150.0, model.getTotalFeedFlowRate(), 0.0);

    consumer.setInletStream(producer.getOutletStream());
    downstream.invalidateGraph();
    assertEquals(100.0, model.getTotalFeedFlowRate(), 0.0,
        "the replaced downstream source should no longer be a plant feed");
  }

  /** A serialized model rebuilds its transient topology plan using restored stream identities. */
  @Test
  void serializedModelRebuildsFeedTopology() throws Exception {
    ProcessSystem area = new ProcessSystem("area");
    addHeatedFeed(area, "main", 100.0);
    ProcessModel model = new ProcessModel();
    model.add("area", area);
    assertEquals(100.0, model.getTotalFeedFlowRate(), 0.0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(model);
    }
    ProcessModel restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (ProcessModel) input.readObject();
    }

    assertEquals(100.0, restored.getTotalFeedFlowRate(), 0.0);
    Stream restoredFeed = (Stream) restored.get("area").getUnit("main feed");
    restoredFeed.setFlowRate(140.0, "kg/hr");
    assertEquals(140.0, restored.getTotalFeedFlowRate(), 0.0);

    addHeatedFeed(restored.get("area"), "added after restore", 10.0);
    assertEquals(150.0, restored.getTotalFeedFlowRate(), 0.0,
        "topology mutations after deserialization must invalidate the rebuilt plan");
  }

  /** Convergence, caloric state, mass flow, and phase state remain deterministic at nearby conditions. */
  @Test
  void cachedFeedTopologyPreservesProcessResultsAndNearbyRuns() {
    ProcessSystem upstream = new ProcessSystem("upstream");
    Heater firstHeater = addHeatedFeed(upstream, "main", 1000.0);

    ProcessSystem downstream = new ProcessSystem("downstream");
    downstream.add(firstHeater.getOutletStream());
    Heater secondHeater = new Heater("downstream heater", firstHeater.getOutletStream());
    secondHeater.setOutletTemperature(308.15);
    downstream.add(secondHeater);

    ProcessModel model = new ProcessModel();
    model.add("upstream", upstream);
    model.add("downstream", downstream);
    assertTrue(model.runUntilConverged(25, 1.0e-6));

    double firstEnthalpy = secondHeater.getOutletStream().getFluid().getEnthalpy();
    double firstFlow = secondHeater.getOutletStream().getFlowRate("kg/hr");
    int firstPhases = secondHeater.getOutletStream().getFluid().getNumberOfPhases();
    double firstClosure = model.getLastMassClosureError();
    int firstIterations = model.getLastIterationCount();
    double firstUpstreamDuty = firstHeater.getDuty();
    double firstDownstreamDuty = secondHeater.getDuty();
    String firstMassClosureSummary = model.getMassClosureSummary();
    String firstWorstFlowStream = model.getWorstBoundaryStreamName("flow");

    model.run();
    assertTrue(model.isModelConverged());
    assertEquals(firstEnthalpy, secondHeater.getOutletStream().getFluid().getEnthalpy(), 1.0e-10);
    assertEquals(firstFlow, secondHeater.getOutletStream().getFlowRate("kg/hr"), 1.0e-10);
    assertEquals(firstPhases, secondHeater.getOutletStream().getFluid().getNumberOfPhases());
    assertEquals(firstClosure, model.getLastMassClosureError(), 1.0e-15);
    assertEquals(firstIterations, model.getLastIterationCount());
    assertEquals(firstUpstreamDuty, firstHeater.getDuty(), 1.0e-10);
    assertEquals(firstDownstreamDuty, secondHeater.getDuty(), 1.0e-10);
    assertEquals(firstMassClosureSummary, model.getMassClosureSummary());
    assertEquals(firstWorstFlowStream, model.getWorstBoundaryStreamName("flow"));

    Stream feed = (Stream) upstream.getUnit("main feed");
    feed.setTemperature(30.0, "C");
    feed.setPressure(55.0, "bara");
    assertTrue(model.runUntilConverged(25, 1.0e-6));
    assertEquals(1000.0, model.getTotalFeedFlowRate(), 1.0e-10);
    assertEquals(1000.0, secondHeater.getOutletStream().getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(1, secondHeater.getOutletStream().getFluid().getNumberOfPhases());
    assertTrue(model.getLastMassClosureError() <= model.getMassClosureTolerance());
  }
}
