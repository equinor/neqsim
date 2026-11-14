package neqsim.process.engineering.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import neqsim.NeqSimTest;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.engineering.pid.PidSimulationSession;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Quantifies native DEXPI 2.0.1 to exact executable dynamic-binding handoff. */
class Dexpi201DynamicBindingReaderTest extends NeqSimTest {
  private static final double STEP_SECONDS = 0.25;
  private static final double STROKE_SECONDS = 2.0;

  @TempDir
  Path temporaryDirectory;

  /**
   * Round-trips native sensing, signal and actuation identities and executes deterministic valve travel.
   *
   * @throws Exception if export or intake fails
   */
  @Test
  void restoresExactBindingsAndRunsDeterministicDynamicSafeguard() throws Exception {
    EngineeringProject project = project();
    PidDesignModel pid = pid();
    PidSimulationBindings sourceBindings = bindings(project, pid);
    Path file = temporaryDirectory.resolve("instrumented-safeguard.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Dexpi201PidMaterializer.Result projection = Dexpi201PidMaterializer.materialize(project, pid, sourceBindings, file);
    assertTrue(projection.isComplete(), projection.toJson());

    Dexpi201DynamicBindingReader.Result intake = Dexpi201DynamicBindingReader.read(file, project, pid,
        sourceBindings.toJson());
    assertEquals(3, intake.getVerifiedTags().size());
    assertEquals(2, intake.getVerifiedSignals().size());
    assertEquals(1, intake.getVerifiedActuations().size());
    assertEquals(64, intake.getSourceSha256().length());
    assertTrue(intake.toJson().contains("CONTROLLED_NATIVE_DEXPI_201_AND_EXPLICIT_BINDING_ARTIFACT"));

    PidSimulationSession session = new PidSimulationSession(project, pid, intake.getBindings());
    DynamicSafetyScenarioResult first = session.runDynamicScenario(closureScenario(intake.getBindings()),
        Collections.singletonList("SDV-101"));
    DynamicSafetyScenarioResult repeated = session.runDynamicScenario(closureScenario(intake.getBindings()),
        Collections.singletonList("SDV-101"));
    assertTrue(first.isPassed(), first.toJson());
    assertEquals(first.toJson(), repeated.toJson());
    assertClosureProfile(first);
    assertEquals(100.0, ((ESDValve) project.getEngineeringProcessSystem().getUnit("ESDV-101")).getPercentValveOpening(),
        0.0);
  }

  /** Stale governed revisions and unresolved native references fail before scenario execution. */
  @Test
  void rejectsStaleOrUnresolvedHandoff() throws Exception {
    EngineeringProject project = project();
    PidDesignModel pid = pid();
    PidSimulationBindings sourceBindings = bindings(project, pid);
    Path file = temporaryDirectory.resolve("invalid-safeguard.xml");
    DexpiEngineeringExporter.exportDexpi201(project, file, metadata());
    Dexpi201PidMaterializer.materialize(project, pid, sourceBindings, file);

    project.setRevision("B");
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi201DynamicBindingReader.read(file, project, pid, sourceBindings.toJson()));
    project.setRevision("A");
    String xml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    String corrupted = xml.replace("objects=\"#PidActuatingSystem_sdv\"", "objects=\"#missing-actuating-system\"");
    assertFalse(xml.equals(corrupted));
    Files.write(file, corrupted.getBytes(StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class,
        () -> Dexpi201DynamicBindingReader.read(file, project, pid, sourceBindings.toJson()));
  }

  /** Builds a solved exact-identity ESD process. */
  private static EngineeringProject project() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ESDValve valve = new ESDValve("ESDV-101", feed);
    valve.setCv(500.0);
    valve.setStrokeTime(STROKE_SECONDS);
    PressureTransmitter transmitter = new PressureTransmitter("pressure sensor", feed);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("pressure control");
    controller.setTransmitter(transmitter);
    controller.setActive(false);
    ProcessSystem process = new ProcessSystem("DEXPI 2.0.1 dynamic reference");
    process.add(feed);
    process.add(valve);
    process.add(transmitter);
    process.add(controller);
    process.run();
    return NorsokOffshoreEngineeringBuilder.from("DEXPI dynamic handoff", process, "2.0.1")
        .projectId("DEXPI-DYNAMIC-TEST").registerProposedInstruments(false).build().setRevision("A");
  }

  /** Builds the exact signal chain represented by the native P&amp;ID. */
  private static PidDesignModel pid() {
    return new PidDesignModel("DEXPI-DYNAMIC-TEST", "DEXPI-DYNAMIC")
        .add(new PidElement("pt", "PT-101", PidElementType.MEASUREMENT).equipment("ESDV-101").line("FEED")
            .connect("pic"))
        .add(new PidElement("pic", "PIC-101", PidElementType.CONTROLLER).equipment("ESDV-101").connect("sdv"))
        .add(new PidElement("sdv", "SDV-101", PidElementType.SHUTDOWN_VALVE).equipment("ESDV-101").line("FEED"));
  }

  /** Builds explicit runtime bindings with no name inference. */
  private static PidSimulationBindings bindings(EngineeringProject project, PidDesignModel pid) {
    return PidSimulationBindings.from(project, pid).bindMeasurement("PT-101", "pressure sensor")
        .bindController("PIC-101", "pressure control").bindEquipment("SDV-101", "ESDV-101");
  }

  /** Builds a fixed-timestep final-element response test. */
  private static DynamicSafetyScenario closureScenario(final PidSimulationBindings bindings) {
    return DynamicSafetyScenario.builder("DEXPI-ESD-CLOSE", "DEXPI-bound ESD closure").durationSeconds(2.5)
        .timeStepSeconds(STEP_SECONDS).triggerTimeSeconds(STEP_SECONDS).addEquipmentTag("ESDV-101")
        .eventExactTiming(true).initiatingEvent("DEXPI-ESD-INIT", new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          /** {@inheritDoc} */
          @Override
          public void apply(ProcessSystem process) {
            // The exact event boundary is retained independently of the logic action below.
          }
        }).addLogic("DEXPI-ESD-LOGIC", new DynamicSafetyScenario.LogicFactory() {
          private static final long serialVersionUID = 1000L;

          /** {@inheritDoc} */
          @Override
          public ProcessLogic create(ProcessSystem process) {
            ESDLogic logic = new ESDLogic("DEXPI-bound final element");
            logic.addAction(new TripValveAction((ESDValve) bindings.resolveEquipment("SDV-101", process)), 0.0);
            return logic;
          }
        }).addCriterion(DynamicScenarioCriterion
            .builder("closed", "ESD valve closed", "%", "DEXPI-SDV-OPENING", new DynamicScenarioCriterion.Extractor() {
              private static final long serialVersionUID = 1000L;

              /** {@inheritDoc} */
              @Override
              public double extract(ProcessSystem process) {
                return ((ESDValve) bindings.resolveEquipment("SDV-101", process)).getPercentValveOpening();
              }
            }).acceptanceRange(null, Double.valueOf(1.0e-9)).deadlineSeconds(STROKE_SECONDS + STEP_SECONDS).build())
        .build();
  }

  /** Checks the analytical linear stroke including the declared end-of-step scan delay. */
  private static void assertClosureProfile(DynamicSafetyScenarioResult result) {
    Map<String, Object> history = result.getCriterionResults().get("closed").toMap();
    List<?> times = (List<?>) history.get("timeSeconds");
    List<?> values = (List<?>) history.get("values");
    assertEquals(times.size(), values.size());
    for (int index = 0; index < times.size(); index++) {
      double elapsed = ((Number) times.get(index)).doubleValue();
      double actuatorElapsed = Math.max(0.0, elapsed - STEP_SECONDS);
      double expected = 100.0 * Math.max(0.0, 1.0 - actuatorElapsed / STROKE_SECONDS);
      assertEquals(expected, ((Number) values.get(index)).doubleValue(), 1.0e-10);
    }
    assertEquals(STROKE_SECONDS + STEP_SECONDS, result.getCriterionResults().get("closed").getFirstSatisfiedSeconds(),
        1.0e-12);
  }

  /** Returns fixed controlled export provenance. */
  private static Dexpi20PlantExportMetadata metadata() {
    return Dexpi20PlantExportMetadata.builder("2026-11-14T12:00:00Z", "NeqSim test", "NeqSim", "development")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "DEXPI-DYNAMIC-TEST")
        .build();
  }
}
