package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** End-to-end process, real vessel dynamics and live-frame lifecycle examples. */
class SourceTermSessionTest extends neqsim.NeqSimTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);

  private Stream feed(String name) {
    SystemInterface fluid = new SystemSrkEos(300.0, 5.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(10.0, "kg/hr");
    return feed;
  }

  private SourceTermSession connect(ProcessSystem process) {
    SourceTermSession session = new SourceTermSession("study-1", process, CLOCK);
    session.addSource("feed-opening", "feed", 0.01, 0.62, 101325.0, new HomogeneousEquilibriumReleaseModel());
    return session;
  }

  private JsonObject json(SourceTermFrame frame) {
    SourceTermFrame.verifyEnvelope(frame.toJson());
    return JsonParser.parseString(frame.toJson()).getAsJsonObject();
  }

  private double upstream(SourceTermFrame frame, String field) {
    assertTrue(frame.getStatus() == SourceTermFrame.Status.VALID
        || frame.getStatus() == SourceTermFrame.Status.VALID_WITH_WARNINGS, frame.toJson());
    return json(frame).getAsJsonObject("source").getAsJsonObject("stations").getAsJsonObject("UPSTREAM_STAGNATION")
        .getAsJsonObject(field).get("value").getAsDouble();
  }

  @Test
  void singleProcessDocumentationAndExternalLiveCapture() {
    Stream feed = feed("feed");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    SourceTermSession session = connect(process);
    List<SourceTermFrame> delivered = new ArrayList<SourceTermFrame>();
    Consumer<SourceTermFrame> listener = delivered::add;
    session.subscribe(listener);
    SourceTermFrame initial = session.runSteadyState().get(0);
    assertEquals(500000.0, upstream(initial, "pressure"), 1e-5);
    double originalMoles = feed.getFluid().getTotalNumberOfMoles();
    UUID externalId = UUID.randomUUID();
    feed.setPressure(4.5, "bara");
    process.run(externalId);
    SourceTermFrame current = session.capture(Collections.singletonMap(SourceTermSession.SINGLE_AREA, externalId))
        .get(0);
    assertEquals(450000.0, upstream(current, "pressure"), 1e-5);
    assertEquals(originalMoles, feed.getFluid().getTotalNumberOfMoles(), 1e-10);
    assertEquals(2, delivered.size());
    assertEquals(1, current.getSequence());
    assertEquals("2026-09-20T12:00:00Z", json(current).get("generatedAt").getAsString());
    assertEquals(externalId.toString(),
        json(current).getAsJsonObject("provenance").get("areaCalculationId").getAsString());
    assertTrue(session.unsubscribe(listener));
    assertEquals(0, session.getDeliveryFailureCount());
  }

  @Test
  void processModelDocumentationPreservesAreaIdentityAndStepsComposition() {
    ProcessSystem areaA = new ProcessSystem();
    ProcessSystem areaB = new ProcessSystem();
    Stream feedA = feed("feed");
    Stream feedB = feed("feed");
    areaA.add(feedA);
    areaB.add(feedB);
    ProcessModel model = new ProcessModel();
    model.add("train-a", areaA);
    model.add("train-b", areaB);
    SourceTermSession session = new SourceTermSession("study-1", model, CLOCK);
    session.addSource("a-opening", "train-a", "feed", -1, 0.01, 0.62, 101325.0,
        new HomogeneousEquilibriumReleaseModel());
    session.addSource("b-opening", "train-b", "feed", -1, 0.01, 0.62, 101325.0,
        new HomogeneousEquilibriumReleaseModel());
    List<SourceTermFrame> first = session.runSteadyState();
    assertEquals(2, first.size());
    assertTrue(model.isModelConverged());
    assertEquals(areaA.getCalculationIdentifier().toString(),
        json(first.get(0)).getAsJsonObject("provenance").get("areaCalculationId").getAsString());
    Map<String, UUID> ids = new LinkedHashMap<String, UUID>();
    ids.put("train-a", areaA.getCalculationIdentifier());
    ids.put("train-b", areaB.getCalculationIdentifier());
    assertEquals(500000.0, upstream(session.capture(ids).get(1), "pressure"), 1e-5);
    feedA.getFluid().setMolarComposition(new double[] {0.8, 0.2});
    feedA.setTemperature(320.0, "K");
    List<SourceTermFrame> next = session.step(0.25);
    assertEquals(320.0, upstream(next.get(0), "temperature"), 1e-8);
    JsonObject station = json(next.get(0)).getAsJsonObject("source").getAsJsonObject("stations")
        .getAsJsonObject("UPSTREAM_STAGNATION");
    assertEquals(0.2, station.getAsJsonObject("componentMoleFractions").get("ethane").getAsDouble(), 1e-10);
    assertEquals(0.25, next.get(0).getSimulationTimeS(), 0);
    assertEquals(0.25, areaB.getTime(), 0);
    assertEquals(areaA.getCalculationIdentifier(), areaB.getCalculationIdentifier());
    assertEquals(4, next.get(0).getSequence());
    assertEquals(5, next.get(1).getSequence());
    assertThrows(UnsupportedOperationException.class, () -> next.clear());
  }

  @Test
  void realSeparatorInventoryDynamicsReachSourceFramesForBothContainers() {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = feed("feed");
      Separator vessel = new Separator("vessel", feed);
      vessel.setInternalDiameter(1.0);
      vessel.setSeparatorLength(2.0);
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      process.add(vessel);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("separation", process);
        session = new SourceTermSession("vessel-study", model, CLOCK);
        session.addSource("vessel-opening", "separation", "vessel", -1, 0.005, 0.62, 101325.0,
            new HomogeneousEquilibriumReleaseModel());
        session.addSource("gas-opening", "separation", "vessel", 0, 0.005, 0.62, 101325.0,
            new HomogeneousEquilibriumReleaseModel());
      } else {
        session = new SourceTermSession("vessel-study", process, CLOCK);
        session.addSource("vessel-opening", "vessel", 0.005, 0.62, 101325.0, new HomogeneousEquilibriumReleaseModel());
        session.addSource("gas-opening", SourceTermSession.SINGLE_AREA, "vessel", 0, 0.005, 0.62, 101325.0,
            new HomogeneousEquilibriumReleaseModel());
      }
      session.runSteadyState();
      vessel.setCalculateSteadyState(false);
      vessel.setHeatInput(2.0, "kW");
      double initialTemperature = vessel.getFluid().getTemperature();
      List<SourceTermFrame> frames = session.step(0.1);
      double actualTemperature = vessel.getFluid().getTemperature();
      assertNotEquals(initialTemperature, actualTemperature, 1e-7);
      assertEquals(actualTemperature, upstream(frames.get(0), "temperature"), 1e-5);
      assertEquals(vessel.getFluid().getPressure("Pa"), upstream(frames.get(0), "pressure"), 1e-3);
      assertEquals(vessel.getGasOutStream().getFluid().getTemperature(), upstream(frames.get(1), "temperature"), 1e-5);
      assertEquals(0.1, frames.get(0).getSimulationTimeS(), 0.0);
      String areaName = useModel ? "separation" : SourceTermSession.SINGLE_AREA;
      List<SourceTermFrame> captured = session
          .capture(Collections.singletonMap(areaName, process.getCalculationIdentifier()));
      assertEquals(actualTemperature, upstream(captured.get(0), "temperature"), 1e-5);
      assertFalse(session.isFaulted());
    }
  }

  @Test
  void staleUnrunMismatchedAndChangedInputNeverReusesPreviousSource() {
    ProcessSystem process = new ProcessSystem();
    Stream feed = feed("feed");
    process.add(feed);
    SourceTermSession session = connect(process);
    SourceTermFrame unrun = session.capture(Collections.singletonMap(SourceTermSession.SINGLE_AREA, UUID.randomUUID()))
        .get(0);
    assertEquals(SourceTermFrame.Status.STALE, unrun.getStatus());
    assertFalse(json(unrun).has("source"));
    session.runSteadyState();
    SourceTermFrame mismatch = session
        .capture(Collections.singletonMap(SourceTermSession.SINGLE_AREA, UUID.randomUUID())).get(0);
    assertEquals(SourceTermFrame.Status.STALE, mismatch.getStatus());
    feed.setTemperature(330.0, "K");
    SourceTermFrame changed = session
        .capture(Collections.singletonMap(SourceTermSession.SINGLE_AREA, process.getCalculationIdentifier())).get(0);
    assertEquals(SourceTermFrame.Status.STALE, changed.getStatus());
    assertFalse(json(changed).has("source"));
  }

  @Test
  void failedStepIsDiagnosticAndRequiresExplicitProcessRecovery() {
    FailingStream feed = new FailingStream("feed", feed("template").getFluid());
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    SourceTermSession session = connect(process);
    session.runSteadyState();
    feed.fail = true;
    SourceTermFrame frame = session.step(0.5).get(0);
    assertEquals(SourceTermFrame.Status.INVALID, frame.getStatus());
    assertFalse(json(frame).has("source"));
    assertEquals(0.0, frame.getSimulationTimeS());
    assertTrue(session.isFaulted());
    assertThrows(IllegalStateException.class, () -> session.step(0.5));
    assertThrows(IllegalStateException.class, () -> session
        .capture(Collections.singletonMap(SourceTermSession.SINGLE_AREA, process.getCalculationIdentifier())));
    feed.fail = false;
    process.setTime(0.0);
    session.runSteadyState();
    assertFalse(session.isFaulted());
    assertEquals(500000.0, upstream(session.step(0.5).get(0), "pressure"), 1e-4);
  }

  @Test
  void callbacksAreIsolatedAndCannotReenterSession() {
    ProcessSystem process = new ProcessSystem();
    process.add(feed("feed"));
    SourceTermSession session = connect(process);
    List<SourceTermFrame> received = new ArrayList<SourceTermFrame>();
    session.subscribe(frame -> {
      throw new IllegalStateException("receiver disconnected");
    });
    session.subscribe(frame -> session.step(0.1));
    session.subscribe(received::add);
    SourceTermFrame frame = session.runSteadyState().get(0);
    assertEquals(500000.0, upstream(frame, "pressure"), 1e-5);
    assertEquals(1, received.size());
    assertEquals(2, session.getDeliveryFailureCount());
    assertEquals(0.0, process.getTime(), 0.0);
  }

  @Test
  void invalidStepAndConfigurationAreRejectedBeforeMutation() {
    ProcessSystem process = new ProcessSystem();
    process.add(feed("feed"));
    SourceTermSession session = connect(process);
    assertThrows(IllegalStateException.class, () -> session.step(1.0));
    session.runSteadyState();
    for (double dt : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> session.step(dt));
    }
    assertEquals(0.0, process.getTime(), 0.0);
    assertFalse(session.isFaulted());
    assertThrows(IllegalArgumentException.class, () -> session.addSource("feed-opening", "feed", 0.01, 0.62, 101325.0,
        new HomogeneousEquilibriumReleaseModel()));
    assertThrows(IllegalArgumentException.class, () -> session.addSource("unknown", "no-such-unit", 0.01, 0.62,
        101325.0, new HomogeneousEquilibriumReleaseModel()));
    session.setEnabled("feed-opening", false);
    SourceTermFrame disabled = session.step(0.25).get(0);
    assertEquals(SourceTermFrame.Status.DISABLED, disabled.getStatus());
    assertFalse(json(disabled).has("source"));
    session.setEnabled("feed-opening", true);
    assertEquals(500000.0, upstream(session.step(0.25).get(0), "pressure"), 1e-5);
  }

  @Test
  void misalignedModelClocksAreRejectedAndSourceFailuresRemainLocal() {
    ProcessSystem areaA = new ProcessSystem();
    ProcessSystem areaB = new ProcessSystem();
    areaA.add(feed("feed"));
    areaB.add(feed("feed"));
    ProcessModel model = new ProcessModel();
    model.add("a", areaA);
    model.add("b", areaB);
    SourceTermSession session = new SourceTermSession("study", model);
    session.addSource("a-opening", "a", "feed", -1, 0.01, 0.62, 101325.0, new HomogeneousEquilibriumReleaseModel());
    session.addSource("b-opening", "b", "feed", -1, 0.01, 0.62, 101325.0, new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;

      public String getModelId() {
        return "test-failure";
      }

      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        throw new IllegalStateException("synthetic release failure");
      }
    });
    List<SourceTermFrame> frames = session.runSteadyState();
    assertEquals(500000.0, upstream(frames.get(0), "pressure"), 1e-5);
    assertEquals(SourceTermFrame.Status.INVALID, frames.get(1).getStatus());
    assertFalse(session.isFaulted());
    areaB.setTime(1.0);
    SourceTermFrame failed = session.step(0.25).get(0);
    assertEquals(SourceTermFrame.Status.INVALID, failed.getStatus());
    assertEquals(0.0, areaA.getTime(), 0);
    assertEquals(1.0, areaB.getTime(), 0);
    assertTrue(session.isFaulted());
  }

  @Test
  void failedSteadyRunDoesNotPublishEarlierSuccessfulValues() {
    Stream feed = new Stream("feed", feed("template").getFluid()) {
      private static final long serialVersionUID = 1L;

      @Override
      public void run(UUID id) {
        if (getTemperature("K") > 310.0) {
          throw new IllegalStateException("synthetic steady failure");
        }
        super.run(id);
      }
    };
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    SourceTermSession session = new SourceTermSession("default-clock-study", process);
    session.addSource("opening", "feed", 0.01, 0.62, 101325.0, new HomogeneousEquilibriumReleaseModel());
    assertEquals(300.0, upstream(session.runSteadyState().get(0), "temperature"), 1e-8);
    feed.setTemperature(320.0, "K");
    SourceTermFrame failed = session.runSteadyState().get(0);
    assertEquals(SourceTermFrame.Status.INVALID, failed.getStatus());
    assertFalse(json(failed).has("source"));
    assertTrue(session.isFaulted());
  }

  @Test
  void changedUpstreamStreamInvalidatesDownstreamExternalSampling() {
    Stream feed = feed("feed");
    Separator vessel = new Separator("vessel", feed);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(vessel);
    SourceTermSession session = new SourceTermSession("downstream", process);
    session.addSource("opening", "vessel", 0.01, 0.62, 101325.0, new HomogeneousEquilibriumReleaseModel());
    session.runSteadyState();
    UUID oldId = process.getCalculationIdentifier();
    feed.setPressure(4.0, "bara");
    SourceTermFrame stale = session.capture(Collections.singletonMap(SourceTermSession.SINGLE_AREA, oldId)).get(0);
    assertEquals(SourceTermFrame.Status.STALE, stale.getStatus());
    assertFalse(json(stale).has("source"));
  }

  @Test
  void snapshotsAreCapturedBeforeEvaluatingAnyReleaseModel() {
    Stream first = feed("first");
    Stream second = feed("second");
    ProcessSystem process = new ProcessSystem();
    process.add(first);
    process.add(second);
    SourceTermSession session = new SourceTermSession("snapshot-test", process);
    session.addSource("first-opening", "first", 0.01, 0.62, 101325.0, new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;

      public String getModelId() {
        return "snapshot-test-model";
      }

      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        // Deliberately adversarial test model, not a permitted production callback behavior.
        second.setTemperature(340.0, "K");
        return new HomogeneousEquilibriumReleaseModel().calculate(request);
      }
    });
    session.addSource("second-opening", "second", 0.01, 0.62, 101325.0, new HomogeneousEquilibriumReleaseModel());
    List<SourceTermFrame> frames = session.runSteadyState();
    assertEquals(340.0, second.getTemperature("K"), 0);
    assertEquals(300.0, upstream(frames.get(1), "temperature"), 1e-8);
  }

  private static final class FailingStream extends Stream {
    private static final long serialVersionUID = 1L;
    private boolean fail;

    private FailingStream(String name, SystemInterface fluid) {
      super(name, fluid);
    }

    @Override
    public void runTransient(double dt, UUID id) {
      if (fail) {
        throw new IllegalStateException("synthetic equipment step failure");
      }
      super.runTransient(dt, id);
    }
  }
}
