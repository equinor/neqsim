package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.GsonBuilder;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests executable P&amp;ID scope, persisted bindings, actual actuator travel and source nonmutation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PidSimulationBindingsTest {
  private static final double ACTUATOR_STROKE_SECONDS = 2.0;
  private static final double CONTROL_SCAN_SECONDS = 0.25;
  private static final double RESPONSE_BUDGET_SECONDS = ACTUATOR_STROKE_SECONDS + CONTROL_SCAN_SECONDS;

  @TempDir
  Path temporaryDirectory;

  /** Exact tag resolution and steady state operate on a source-independent copy. */
  @Test
  void runsSteadyStateAndRestoresBindingsWithoutLiveReferences() {
    EngineeringProject project = project();
    PidDesignModel pid = pid();
    PidSimulationBindings bindings = bindings(project, pid);
    PidSimulationSession session = new PidSimulationSession(project, pid, bindings);
    ProcessSystem solved = session.runSteadyState();

    assertTrue(solved.solved());
    assertNotSame(project.getEngineeringProcessSystem(), solved);
    assertNotSame(project.getEngineeringProcessSystem().getUnit("ESDV-101"),
        bindings.resolveEquipment("SDV-101", solved));
    assertEquals(1000.0, ((Stream) solved.getUnit("FEED")).getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(50.0, bindings.resolveMeasurement("PT-101", solved).getMeasuredValue("bara"), 1.0e-8);
    assertNotSame(bindings.resolveController("PIC-101", solved),
        bindings.resolveController("PIC-101", project.getEngineeringProcessSystem()));

    PidDesignModel restoredPid = PidDesignModel.fromJson(new GsonBuilder().create().toJson(pid.toMap()));
    EngineeringProject restoredProject = NorsokOffshoreEngineeringBuilder
        .from(project.getName(), project.getEngineeringProcessSystem().copy()).projectId(project.getProjectId())
        .registerProposedInstruments(false).build().setRevision(project.getRevision());
    PidSimulationBindings restored = PidSimulationBindings.fromJson(restoredProject, restoredPid, bindings.toJson());
    assertEquals(bindings.toJson(), restored.toJson());
    assertEquals(Collections.singletonList("PT-HOLD"), restored.getUnboundPidTags());
    assertFalse((Boolean) restored.toMap().get("allProposalsBound"));
    assertEquals("SCENARIO_SPECIFIC_PREFLIGHT_REQUIRED", restored.toMap().get("dynamicReadiness"));
    assertThrows(IllegalArgumentException.class, () -> PidSimulationBindings.fromJson(project, restoredPid, bindings
        .toJson().replace("neqsim.process.equipment.valve.ESDValve", "neqsim.process.equipment.stream.Stream")));
  }

  /** A final P&amp;ID tag drives timed closure on an isolated real ESD actuator. */
  @Test
  void verifiesTimedDynamicResponseThroughPidIdentityAndPreservesSource() {
    EngineeringProject project = project();
    PidDesignModel pid = pid();
    final PidSimulationBindings bindings = bindings(project, pid);
    PidSimulationSession session = new PidSimulationSession(project, pid, bindings);
    DynamicSafetyScenario scenario = closureScenario(bindings, null);
    DynamicSafetyScenarioResult result = session.runDynamicScenario(scenario, Collections.singletonList("SDV-101"));

    assertTrue(result.isPassed(), result.toJson());
    assertEquals(3.0, result.getCompletedDurationSeconds(), 1.0e-12);
    assertClosureProfile(result);
    ESDValve original = (ESDValve) project.getEngineeringProcessSystem().getUnit("ESDV-101");
    assertTrue(original.isEnergized());
    assertEquals(100.0, original.getPercentValveOpening(), 1.0e-12);
    assertEquals(Collections.singletonList("PT-HOLD"), bindings.getUnboundPidTags());
    assertEquals("ESDV-101", bindings.getSimulationName("SDV-101"));
    assertEquals(Collections.singletonList("ESDV-101"), result.getEquipmentTags());
    assertThrows(IllegalArgumentException.class,
        () -> session.runDynamicScenario(scenario, Collections.singletonList("PT-HOLD")));
    assertThrows(IllegalArgumentException.class, () -> session
        .runDynamicScenario(closureScenario(bindings, null, false, "SDV-101"), Collections.singletonList("SDV-101")));
  }

  /**
   * The saved model, P&amp;ID and verified register reload into an executable dynamic test handoff.
   *
   * @throws Exception if a handoff artifact cannot be written or restored
   */
  @Test
  void restoresPersistedPidAndModelThenRunsSteadyAndDynamicTests() throws Exception {
    EngineeringProject original = project();
    PidDesignModel pid = pid();
    PidSimulationBindings originalBindings = bindings(original, pid);
    Path sourceFile = temporaryDirectory.resolve("simulation-model.neqsim");
    Path pidFile = temporaryDirectory.resolve("pid-design-model.json");
    Path bindingFile = temporaryDirectory.resolve("pid-simulation-bindings.json");
    original.getEngineeringProcessSystem().copy().saveToNeqsim(sourceFile.toString());
    Files.write(pidFile, new GsonBuilder().create().toJson(pid.toMap()).getBytes(StandardCharsets.UTF_8));
    Files.write(bindingFile, originalBindings.toJson().getBytes(StandardCharsets.UTF_8));

    ProcessSystem restoredProcess = ProcessSystem.loadFromNeqsim(sourceFile.toString());
    EngineeringProject restored = NorsokOffshoreEngineeringBuilder.from(original.getName(), restoredProcess)
        .projectId(original.getProjectId()).registerProposedInstruments(false).build()
        .setRevision(original.getRevision());
    PidDesignModel restoredPid = PidDesignModel
        .fromJson(new String(Files.readAllBytes(pidFile), StandardCharsets.UTF_8));
    PidSimulationBindings restoredBindings = PidSimulationBindings.fromJson(restored, restoredPid,
        new String(Files.readAllBytes(bindingFile), StandardCharsets.UTF_8));
    PidSimulationSession session = new PidSimulationSession(restored, restoredPid, restoredBindings);
    ProcessSystem steady = session.runSteadyState();
    assertTrue(steady.solved());
    assertEquals(50.0, restoredBindings.resolveMeasurement("PT-101", steady).getMeasuredValue("bara"), 1.0e-8);
    assertNotSame(restoredBindings.resolveController("PIC-101", steady),
        restoredBindings.resolveController("PIC-101", restoredProcess));
    DynamicSafetyScenarioResult response = session.runDynamicScenario(closureScenario(restoredBindings, null),
        Collections.singletonList("SDV-101"));
    assertTrue(response.isPassed(), response.toJson());
    assertClosureProfile(response);
    assertEquals(100.0, ((ESDValve) restoredProcess.getUnit("ESDV-101")).getPercentValveOpening(), 1.0e-12);
    assertEquals(100.0, ((ESDValve) original.getProcessSystem().getUnit("ESDV-101")).getPercentValveOpening(), 1.0e-12);
    assertEquals(originalBindings.toJson(), restoredBindings.toJson());
  }

  /** Capability preflight sees case-specific configuration and cannot execute a time-zero event after failure. */
  @Test
  void blocksUnsupportedDynamicConfigurationBeforeInitiatingEvents() {
    EngineeringProject project = project();
    PidDesignModel pid = pid();
    final PidSimulationBindings bindings = bindings(project, pid);
    AtomicInteger events = new AtomicInteger();
    DynamicSafetyScenario scenario = closureScenario(bindings, events);
    DynamicSafetyScenarioResult result = new PidSimulationSession(project, pid, bindings).runDynamicScenario(scenario,
        Collections.singletonList("SDV-101"));

    assertFalse(result.isPassed());
    assertTrue(result.toJson().contains("runTransientAdaptive"));
    assertEquals("STRICT_PREFLIGHT_FAILED", result.getTimingPolicy());
    assertEquals(0.0, result.getCompletedDurationSeconds(), 0.0);
    assertEquals(0, events.get());
    assertFalse(project.getEngineeringProcessSystem().isAdaptiveTimestepEnabled());
  }

  /** Strict preflight audits the final configuration after logic factories have registered their controls. */
  @Test
  void blocksUnsupportedConfigurationIntroducedByLogicFactory() {
    EngineeringProject project = project();
    PidDesignModel pid = pid();
    PidSimulationBindings bindings = bindings(project, pid);
    AtomicInteger events = new AtomicInteger();
    DynamicSafetyScenario scenario = closureScenario(bindings, events, true);
    DynamicSafetyScenarioResult result = new PidSimulationSession(project, pid, bindings).runDynamicScenario(scenario,
        Collections.singletonList("SDV-101"));

    assertFalse(result.isPassed());
    assertTrue(result.toJson().contains("runTransientAdaptive"));
    assertEquals("STRICT_PREFLIGHT_FAILED", result.getTimingPolicy());
    assertEquals(0.0, result.getCompletedDurationSeconds(), 0.0);
    assertEquals(0, events.get());
    assertFalse(project.getEngineeringProcessSystem().isAdaptiveTimestepEnabled());
  }

  /** Wrong roles, unknown targets and stale source identities fail instead of selecting a fallback. */
  @Test
  void rejectsWrongTypesAndChangedRevisionOrTopology() {
    EngineeringProject project = project();
    PidDesignModel pid = pid();
    PidSimulationBindings bindings = bindings(project, pid);
    assertThrows(IllegalArgumentException.class, () -> bindings.bindEquipment("SDV-101", "FEED"));
    assertThrows(IllegalArgumentException.class, () -> bindings.bindMeasurement("PIC-101", "pressure sensor"));
    assertThrows(IllegalArgumentException.class, () -> bindings.bindController("PT-101", "pressure control"));
    assertThrows(IllegalArgumentException.class, () -> bindings.bindMeasurement("PT-HOLD", "unknown sensor"));
    assertThrows(IllegalArgumentException.class,
        () -> bindings.resolveMeasurement("SDV-101", project.getEngineeringProcessSystem()));

    project.setRevision("B");
    assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(project, pid));
    project.setRevision("A");
    project.getEngineeringProcessSystem().add(
        new Stream("NEW-BOUNDARY", ((Stream) project.getProcessSystem().getUnit("FEED")).getThermoSystem().clone()));
    assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(project, pid));
  }

  /** Distinct measurement identities with one name cannot produce an ambiguous binding. */
  @Test
  void rejectsAmbiguousNamesAndChangedPidContent() {
    EngineeringProject project = project();
    PidDesignModel pid = pid();
    PidSimulationBindings bindings = bindings(project, pid);
    pid.getElements().get(0).attribute("reviewedLocation", "gas outlet");
    assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(project, pid));

    project.getProcessSystem()
        .add(new PressureTransmitter("pressure sensor", (Stream) project.getProcessSystem().getUnit("FEED")));
    assertThrows(IllegalArgumentException.class, () -> PidSimulationBindings.from(project, pid));
  }

  /** Implicit sensor/controller wiring changes invalidate bindings even when names and revision are unchanged. */
  @Test
  void rejectsChangedControllerTransmitterAndMeasurementLocation() {
    EngineeringProject project = project();
    ProcessSystem process = project.getEngineeringProcessSystem();
    Stream feed = (Stream) process.getUnit("FEED");
    ESDValve valve = (ESDValve) process.getUnit("ESDV-101");
    PressureTransmitter spare = new PressureTransmitter("spare pressure sensor", valve.getOutletStream());
    process.add(spare);
    ControllerDeviceBaseClass controller = (ControllerDeviceBaseClass) process.getControllerDevices().get(0);
    PressureTransmitter original = (PressureTransmitter) process.getMeasurementDevices().get(0);
    assertTrue(controller.getTransmitter() == original);
    PidDesignModel pid = pid();
    PidSimulationBindings bindings = bindings(project, pid);

    controller.setTransmitter(spare);
    assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(project, pid));
    controller.setTransmitter(original);
    bindings.assertCurrent(project, pid);
    original.setStream(valve.getOutletStream());
    assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(project, pid));
    original.setStream(feed);
    bindings.assertCurrent(project, pid);
    controller.setTransmitter(new PressureTransmitter("pressure sensor", feed));
    assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(project, pid));
  }

  /** Reassigning an existing controller to another actuator changes control topology without changing its name. */
  @Test
  void rejectsReattachedControllerWithUnchangedNames() {
    EngineeringProject project = project();
    ProcessSystem process = project.getEngineeringProcessSystem();
    ESDValve first = (ESDValve) process.getUnit("ESDV-101");
    ESDValve second = new ESDValve("ESDV-102", first.getOutletStream());
    second.setCv(500.0);
    second.setStrokeTime(2.0);
    process.add(second);
    ControllerDeviceBaseClass controller = (ControllerDeviceBaseClass) process.getControllerDevices().get(0);
    first.setController(controller);
    PidDesignModel pid = pid();
    PidSimulationBindings bindings = bindings(project, pid);
    first.setController(null);
    second.setController(controller);
    assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(project, pid));
  }

  /**
   * Creates the explicit actuator, measurement and controller bindings.
   *
   * @param project source project
   * @param pid selected P&amp;ID revision
   * @return explicit binding register
   */
  private static PidSimulationBindings bindings(EngineeringProject project, PidDesignModel pid) {
    return PidSimulationBindings.from(project, pid).bindEquipment("SDV-101", "ESDV-101")
        .bindMeasurement("PT-101", "pressure sensor").bindController("PIC-101", "pressure control");
  }

  /**
   * Builds a deterministic methane flow model with a two-second ESD actuator.
   *
   * @return source project with an executed steady-state basis
   */
  private static EngineeringProject project() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ESDValve valve = new ESDValve("ESDV-101", feed);
    valve.setCv(500.0);
    valve.setStrokeTime(ACTUATOR_STROKE_SECONDS);
    PressureTransmitter sensor = new PressureTransmitter("pressure sensor", feed);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("pressure control");
    controller.setTransmitter(sensor);
    controller.setActive(false);
    ProcessSystem process = new ProcessSystem("P&ID test process");
    process.add(feed);
    process.add(valve);
    process.add(sensor);
    process.add(controller);
    process.run();
    return NorsokOffshoreEngineeringBuilder.from("P&ID dynamic reference", process).projectId("PID-TEST").build()
        .setRevision("A");
  }

  /**
   * Creates a small controlled P&amp;ID scope and one unresolved proposal.
   *
   * @return P&amp;ID model
   */
  private static PidDesignModel pid() {
    return new PidDesignModel("PID-TEST", "EXPLICIT-TEST")
        .add(new PidElement("valve-id", "SDV-101", PidElementType.SHUTDOWN_VALVE).equipment("ESDV-101")
            .attribute("integer", Integer.valueOf(1)).attribute("decimal", Double.valueOf(1.0)))
        .add(new PidElement("sensor-id", "PT-101", PidElementType.MEASUREMENT).equipment("FEED"))
        .add(new PidElement("controller-id", "PIC-101", PidElementType.CONTROLLER).equipment("ESDV-101"))
        .add(new PidElement("hold-id", "PT-HOLD", PidElementType.MEASUREMENT).equipment("FEED"));
  }

  /**
   * Creates a physical closure test, optionally requesting a deliberately unsupported case mode.
   *
   * @param bindings explicit P&amp;ID-to-model identity mapping
   * @param events event counter for the deliberately blocked test, or null for the passing test
   * @return controlled test scenario
   */
  private static DynamicSafetyScenario closureScenario(final PidSimulationBindings bindings,
      final AtomicInteger events) {
    return closureScenario(bindings, events, false);
  }

  /**
   * Creates the final test configuration with either case- or logic-factory-introduced activation changes.
   *
   * @param bindings explicit P&amp;ID mapping
   * @param events event counter, or null for the passing case
   * @param configureInLogic whether the unsupported execution mode is introduced by the logic factory
   * @return controlled closure scenario
   */
  private static DynamicSafetyScenario closureScenario(final PidSimulationBindings bindings, final AtomicInteger events,
      final boolean configureInLogic) {
    return closureScenario(bindings, events, configureInLogic, "ESDV-101");
  }

  /**
   * Creates a closure test with an explicit simulation-tag evidence declaration.
   *
   * @param bindings explicit P&amp;ID mapping
   * @param events event counter, or null
   * @param configureInLogic whether a logic factory introduces an unsupported execution mode
   * @param declaredSimulationName model name retained in scenario evidence
   * @return controlled scenario
   */
  private static DynamicSafetyScenario closureScenario(final PidSimulationBindings bindings, final AtomicInteger events,
      final boolean configureInLogic, String declaredSimulationName) {
    DynamicSafetyScenario.Builder builder = DynamicSafetyScenario.builder("PID-CLOSE", "P&ID ESD closure")
        .durationSeconds(3.0).timeStepSeconds(CONTROL_SCAN_SECONDS).triggerTimeSeconds(events == null ? 0.5 : 0.0)
        .addEquipmentTag(declaredSimulationName).eventExactTiming(true)
        .initiatingEvent("PID-CLOSE-A", new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          /** {@inheritDoc} */
          @Override
          public void apply(ProcessSystem process) {
            if (events != null) {
              events.incrementAndGet();
            }
          }
        }).addLogic("PID-ESD-LOGIC-A", new DynamicSafetyScenario.LogicFactory() {
          private static final long serialVersionUID = 1000L;

          /** {@inheritDoc} */
          @Override
          public ProcessLogic create(ProcessSystem process) {
            if (configureInLogic) {
              process.setAdaptiveTimestepEnabled(true);
            }
            ESDLogic logic = new ESDLogic("P&ID ESD final element");
            logic.addAction(new TripValveAction((ESDValve) bindings.resolveEquipment("SDV-101", process)), 0.0);
            return logic;
          }
        }).addCriterion(DynamicScenarioCriterion
            .builder("closed", "ESD valve closed", "%", "PID-OPENING-A", new DynamicScenarioCriterion.Extractor() {
              private static final long serialVersionUID = 1000L;

              /** {@inheritDoc} */
              @Override
              public double extract(ProcessSystem process) {
                return ((ESDValve) bindings.resolveEquipment("SDV-101", process)).getPercentValveOpening();
              }
            }).acceptanceRange(null, Double.valueOf(1.0e-9)).deadlineSeconds(RESPONSE_BUDGET_SECONDS).build());
    if (events != null && !configureInLogic) {
      builder.configureCase("ADAPTIVE-UNSUPPORTED", new DynamicSafetyScenario.ProcessManipulator() {
        private static final long serialVersionUID = 1000L;

        /** {@inheritDoc} */
        @Override
        public void apply(ProcessSystem process) {
          process.setAdaptiveTimestepEnabled(true);
        }
      });
    }
    return builder.build();
  }

  /**
   * Verifies the complete analytical travel profile, including the declared end-of-step control-scan delay.
   *
   * @param result measured scenario history
   */
  private static void assertClosureProfile(DynamicSafetyScenarioResult result) {
    Map<String, Object> history = result.getCriterionResults().get("closed").toMap();
    List<?> times = (List<?>) history.get("timeSeconds");
    List<?> openings = (List<?>) history.get("values");
    assertEquals(11, times.size());
    assertEquals(times.size(), openings.size());
    for (int index = 0; index < times.size(); index++) {
      double timeSinceInitiator = ((Number) times.get(index)).doubleValue();
      double timeSinceActuatorCommand = Math.max(0.0, timeSinceInitiator - CONTROL_SCAN_SECONDS);
      double expectedOpening = 100.0 * Math.max(0.0, 1.0 - timeSinceActuatorCommand / ACTUATOR_STROKE_SECONDS);
      assertEquals(expectedOpening, ((Number) openings.get(index)).doubleValue(), 1.0e-10,
          "Linear actuator opening at " + timeSinceInitiator + " s after initiator");
    }
    assertEquals(RESPONSE_BUDGET_SECONDS, result.getCriterionResults().get("closed").getFirstSatisfiedSeconds(),
        1.0e-12);
  }
}
