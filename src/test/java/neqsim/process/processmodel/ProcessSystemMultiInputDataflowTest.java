package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for dependency-aware dataflow dispatch with multi-input equipment. */
class ProcessSystemMultiInputDataflowTest {
  private static final class Fixture {
    private final ProcessSystem process;
    private final List<Stream> feeds;
    private final List<Heater> products;

    private Fixture(ProcessSystem process, List<Stream> feeds, List<Heater> products) {
      this.process = process;
      this.feeds = feeds;
      this.products = products;
    }
  }

  private static final class RecordingProcessSystem extends ProcessSystem {
    private static final long serialVersionUID = 1000L;
    private int parallelRuns;
    private int dataflowRuns;

    private RecordingProcessSystem() {
      super("recording process");
    }

    @Override
    public synchronized void runParallel(UUID id) {
      parallelRuns++;
      setCalculationIdentifier(id);
    }

    @Override
    public synchronized void runDataflow(UUID id) {
      dataflowRuns++;
      setCalculationIdentifier(id);
    }
  }

  private static SystemInterface createFluid(boolean cpa) {
    SystemInterface fluid = cpa ? new SystemSrkCPAstatoil(303.15, 60.0) : new SystemSrkEos(303.15, 60.0);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.04);
    fluid.addComponent(cpa ? "water" : "nC10", 0.01);
    fluid.setMixingRule(cpa ? 10 : 2);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static Fixture createFixture(boolean cpa) {
    ProcessSystem process = new ProcessSystem("wide mixer process");
    List<Stream> feeds = new ArrayList<>();
    List<Heater> products = new ArrayList<>();
    for (int branch = 0; branch < 2; branch++) {
      Stream first = new Stream("first feed " + branch, createFluid(cpa));
      first.setFlowRate(12000.0 + 1000.0 * branch, "kg/hr");
      Stream second = new Stream("second feed " + branch, createFluid(cpa));
      second.setTemperature(295.15 + branch, "K");
      second.setFlowRate(3000.0 + 250.0 * branch, "kg/hr");
      Mixer mixer = new Mixer("mixer " + branch);
      mixer.addStream(first);
      mixer.addStream(second);
      Heater firstHeater = new Heater("first heater " + branch, mixer.getOutletStream());
      firstHeater.setOutTemperature(308.15 + branch);
      Heater product = new Heater("product heater " + branch, firstHeater.getOutletStream());
      product.setOutTemperature(312.15 + branch);
      process.add(first);
      process.add(second);
      process.add(mixer);
      process.add(firstHeater);
      process.add(product);
      feeds.add(first);
      feeds.add(second);
      products.add(product);
    }
    return new Fixture(process, feeds, products);
  }

  private static void compare(Fixture expected, Fixture actual) {
    double expectedFeedFlow = 0.0;
    double actualFeedFlow = 0.0;
    double expectedProductFlow = 0.0;
    double actualProductFlow = 0.0;
    for (int i = 0; i < expected.feeds.size(); i++) {
      expectedFeedFlow += expected.feeds.get(i).getFlowRate("kg/hr");
      actualFeedFlow += actual.feeds.get(i).getFlowRate("kg/hr");
    }
    for (int i = 0; i < expected.products.size(); i++) {
      StreamInterface expectedProduct = expected.products.get(i).getOutletStream();
      StreamInterface actualProduct = actual.products.get(i).getOutletStream();
      expectedProductFlow += expectedProduct.getFlowRate("kg/hr");
      actualProductFlow += actualProduct.getFlowRate("kg/hr");
      assertEquals(expectedProduct.getTemperature("K"), actualProduct.getTemperature("K"), 1.0e-10);
      assertEquals(expectedProduct.getPressure("bara"), actualProduct.getPressure("bara"), 1.0e-10);
      assertEquals(expectedProduct.getFluid().getEnthalpy(), actualProduct.getFluid().getEnthalpy(), 1.0e-7);
      assertEquals(expectedProduct.getThermoSystem().getNumberOfPhases(),
          actualProduct.getThermoSystem().getNumberOfPhases());
      assertArrayEquals(expectedProduct.getThermoSystem().getMolarComposition(),
          actualProduct.getThermoSystem().getMolarComposition(), 1.0e-12);
    }
    assertEquals(expectedFeedFlow, actualFeedFlow, 1.0e-8);
    assertEquals(expectedProductFlow, actualProductFlow, 1.0e-8);
    assertEquals(expectedFeedFlow, expectedProductFlow, 1.0e-6);
    assertEquals(actualFeedFlow, actualProductFlow, 1.0e-6);
  }

  private static void verifyExecutionParity(boolean cpa) throws Exception {
    Fixture levelParallel = createFixture(cpa);
    Fixture dataflow = createFixture(cpa);

    levelParallel.process.runParallel(new UUID(1L, 1L));
    dataflow.process.runOptimized(new UUID(1L, 1L));
    compare(levelParallel, dataflow);
    assertTrue(dataflow.process.getExecutionStrategyExplanation().contains("dataflow"));

    levelParallel.feeds.get(0).setFlowRate(12500.0, "kg/hr");
    dataflow.feeds.get(0).setFlowRate(12500.0, "kg/hr");
    levelParallel.feeds.get(1).setTemperature(296.15, "K");
    dataflow.feeds.get(1).setTemperature(296.15, "K");
    levelParallel.process.runParallel(new UUID(2L, 2L));
    dataflow.process.runOptimized(new UUID(2L, 2L));
    compare(levelParallel, dataflow);

    levelParallel.process.runParallel(new UUID(3L, 3L));
    dataflow.process.runOptimized(new UUID(3L, 3L));
    compare(levelParallel, dataflow);
  }

  @Test
  void wideMultiInputProcessUsesDataflow() {
    RecordingProcessSystem process = new RecordingProcessSystem();
    Fixture fixture = createFixture(false);
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : fixture.process.getUnitOperations()) {
      process.add(unit);
    }

    process.runOptimized(new UUID(4L, 4L));

    assertEquals(0, process.parallelRuns);
    assertEquals(1, process.dataflowRuns);
  }

  @Test
  void srkDataflowMatchesLevelParallelAcrossNearbyAndRepeatedRuns() throws Exception {
    verifyExecutionParity(false);
  }

  @Test
  void cpaDataflowMatchesLevelParallelAcrossNearbyAndRepeatedRuns() throws Exception {
    verifyExecutionParity(true);
  }

  @Test
  void independentWideMixerProcessesRemainThreadSafe() throws Exception {
    Fixture reference = createFixture(false);
    reference.process.runOptimized(new UUID(5L, 5L));

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<Fixture>> futures = new ArrayList<>();
      for (int scenario = 0; scenario < 4; scenario++) {
        final int scenarioIndex = scenario;
        futures.add(executor.submit(() -> {
          Fixture fixture = createFixture(false);
          fixture.process.runOptimized(new UUID(6L, scenarioIndex + 1L));
          return fixture;
        }));
      }
      for (Future<Fixture> future : futures) {
        compare(reference, future.get());
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
