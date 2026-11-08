package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.engineering.HeatExchangerAutomationWorkflow.Settings;
import neqsim.process.engineering.pid.HeatExchangerControllerTuning;
import neqsim.process.engineering.pid.HeatExchangerControllerTuning.Definition;
import neqsim.process.engineering.pid.HeatExchangerControllerTuning.Result;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidNativeControlSystem;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.equipment.heatexchanger.DynamicHeatExchanger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi201XmlWriter;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemWaterIF97;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface.ControllerMode;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.measurementdevice.TemperatureTransmitter;

/**
 * Executable documentation and integrated native drawing, dynamics, tuning and DEXPI acceptance tests.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HeatExchangerAutomationWorkflowTest {
  @TempDir
  Path temporaryDirectory;

  /**
   * Builds the public natural-gas/reference-water example without project-specific data.
   *
   * @return instrumented project with exactly one actual PID and nine actual measurements
   */
  private EngineeringProject example() {
    return example(false);
  }

  /**
   * Builds an example with an optional additional process-inlet valve.
   *
   * @param hotValve whether to include the separate upstream hot-side valve
   * @return instrumented actual source project
   */
  private EngineeringProject example(boolean hotValve) {
    return HeatExchangerAutomationWorkflow.instrument(sourceExample(hotValve), "E-101", "TCV-201", "HX-101-DEMO",
        new Settings(101, 201, 45.0, 1.5, 10.0, 0.0));
  }

  /**
   * Builds the uninstrumented public source case for preflight checks.
   *
   * @param hotValve whether to include the separate upstream process valve
   * @return actual ordered source process
   */
  private ProcessSystem sourceExample(boolean hotValve) {
    SystemSrkEos gas = new SystemSrkEos(373.15, 30.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");
    gas.setTotalFlowRate(1.0, "kg/sec");
    Stream feed = new Stream("NG-FEED", gas);
    SystemWaterIF97 water = new SystemWaterIF97(288.15, 5.0);
    water.setTotalFlowRate(2.0, "kg/sec");
    Stream supply = new Stream("CW-SUPPLY", water);
    ThrottlingValve valve = new ThrottlingValve("TCV-201", supply);
    valve.setOutletPressure(3.0);
    valve.setKv(12.0);
    valve.setPercentValveOpening(50.0);
    valve.getOutletStream().setName("CW-TO-E101");
    DynamicHeatExchanger exchanger = new DynamicHeatExchanger("E-101", feed, valve.getOutletStream());
    exchanger.setWallMass(500.0);
    exchanger.setWallCp(500.0);
    exchanger.setShellHoldupVolume(0.10);
    exchanger.setTubeHoldupVolume(0.05);
    exchanger.setHeatTransferArea(20.0);
    exchanger.setShellSideHtc(1000.0);
    exchanger.setTubeSideHtc(1000.0);
    exchanger.getOutStream(0).setName("NG-PRODUCT");
    exchanger.getOutStream(1).setName("CW-RETURN");
    ProcessSystem process = new ProcessSystem("E101 automation");
    process.add(feed);
    if (hotValve) {
      ThrottlingValve inletValve = new ThrottlingValve("HV-101", feed);
      inletValve.setOutletPressure(29.0);
      inletValve.getOutletStream().setName("NG-TO-E101");
      exchanger.setFeedStream(0, inletValve.getOutletStream());
      process.add(inletValve);
    }
    process.add(supply);
    process.add(valve);
    process.add(exchanger);
    return process;
  }

  /**
   * Supplies explicit export provenance and plant identity.
   *
   * @return controlled reference-case metadata
   */
  private Dexpi20PlantExportMetadata metadata() {
    return Dexpi20PlantExportMetadata
        .builder("2026-11-08T01:00:00+01:00", "NeqSim reference example", "NeqSim", "3.28.0-development")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "HX-101-DEMO")
        .build();
  }

  /**
   * Reads an emitted JSON companion.
   *
   * @param path actual generated file
   * @return parsed JSON object
   * @throws Exception if reading fails
   */
  private JsonObject json(Path path) throws Exception {
    return JsonParser.parseString(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
  }

  /**
   * Qualifies automatic native P&amp;ID/PFD generation and DEXPI from the same actual instrumented process.
   *
   * @throws Exception if export or independent artifact inspection fails
   */
  @Test
  void automaticallyExportsFourPortsNineMeasurementsAndOneActualControlLoop() throws Exception {
    EngineeringProject project = example();
    ProcessSystem process = project.getEngineeringProcessSystem();
    PidDesignModel pid = PidNativeControlSystem.fromProcess(project);
    PidSimulationBindings bindings = PidNativeControlSystem.bind(project, pid);
    assertEquals(9, process.getMeasurementDevices().size());
    assertEquals(11, bindings.getBoundPidTags().size());
    assertTrue(bindings.getUnboundPidTags().isEmpty());
    Path directory = HeatExchangerAutomationWorkflow.export(project, metadata(), temporaryDirectory.resolve("native"),
        null);
    assertTrue(Dexpi201XmlWriter.assess(directory.resolve("plant.dexpi.xml")).isSchemaAndProfileConformant());
    JsonObject readiness = json(directory.resolve("professional-pid-readiness.json"));
    assertTrue(readiness.get("drawingHasNoErrors").getAsBoolean(), readiness.toString());
    assertEquals("GENERATED_FOR_REVIEW", readiness.get("generationStatus").getAsString(), readiness.toString());
    assertEquals("GENERATED_FOR_REVIEW", readiness.getAsJsonObject("pfdOverview").get("generationStatus").getAsString(),
        readiness.toString());
    assertTrue(readiness.getAsJsonObject("nativePidProjection").get("completeSupportedProjection").getAsBoolean());
    assertFalse(readiness.get("fitnessForConstruction").getAsBoolean());
    JsonObject projection = json(directory.resolve("plant.dexpi.xml.pid-projection.json"));
    assertEquals(11, projection.getAsJsonObject("nativeIdsByPidTag").size());
    assertEquals(2, projection.getAsJsonArray("signalLinks").size());
    assertTrue(Files.size(directory.resolve("drawings/pid-drawing-set.pdf")) > 1000);
    ProcessSystem restored = ProcessSystem.loadFromNeqsim(directory.resolve("simulation-model.neqsim").toString());
    assertNotNull(restored);
    restored.runTransient(0.5, UUID.randomUUID());
    assertTrue(
        Math.abs(((DynamicHeatExchanger) restored.getUnit("E-101")).getLastThermalBalance().getResidualJ()) < 0.01);
    assertThrows(IllegalArgumentException.class,
        () -> HeatExchangerAutomationWorkflow.export(project, metadata(), directory, null));
    String requestedOutput = System.getProperty("neqsim.heatExchangerAutomationOutputDirectory", "");
    if (!requestedOutput.isEmpty()) {
      HeatExchangerAutomationWorkflow.export(project, metadata(), Paths.get(requestedOutput), null);
    }
  }

  /**
   * Tests real thermal/control candidate execution, source isolation, recommendation application and package readback.
   *
   * @throws Exception if the qualified package cannot be inspected
   */
  @Test
  void tunesNativeLoopWithoutMutatingSourceAndExportsTheAppliedRevision() throws Exception {
    EngineeringProject project = example();
    PidDesignModel pid = PidNativeControlSystem.fromProcess(project);
    PidSimulationBindings bindings = PidNativeControlSystem.bind(project, pid);
    DynamicHeatExchanger exchanger = (DynamicHeatExchanger) project.getEngineeringProcessSystem().getUnit("E-101");
    ThrottlingValve valve = (ThrottlingValve) project.getEngineeringProcessSystem().getUnit("TCV-201");
    double energy = exchanger.getThermalInventoryEnergyJ();
    double opening = valve.getPercentValveOpening();
    Definition definition = new Definition("TIC-102", 45.0, 1.0, 180.0, 20.0, 0.3, 0.01,
        new double[][] {{0.1, 100.0, 0.0}, {1.5, 10.0, 0.0}, {4.0, 15.0, 0.0}});
    Result result = HeatExchangerControllerTuning.tune(project, pid, bindings, definition);
    assertTrue(result.hasRecommendation(), result.toJson());
    assertEquals(energy, exchanger.getThermalInventoryEnergyJ(), 0.0);
    assertEquals(opening, valve.getPercentValveOpening(), 0.0);
    assertEquals(0.0, exchanger.getTime(), 0.0);
    assertEquals(1.5, ((neqsim.process.controllerdevice.ControllerDeviceBaseClass) valve.getController()).getKp(), 0.0);
    double[] selected = result.getRecommendedParameters();
    double[] escaped = result.getRecommendedParameters();
    escaped[0] = -9.0;
    assertArrayEquals(selected, result.getRecommendedParameters(), 0.0);
    ControllerDeviceBaseClass controller = (ControllerDeviceBaseClass) valve.getController();
    ControllerDeviceBaseClass.ControllerTransientState state = controller.captureTransientState();
    controller.runTransient(opening, 1.0, UUID.randomUUID());
    assertThrows(IllegalArgumentException.class,
        () -> HeatExchangerControllerTuning.applyRecommendation(project, pid, bindings, result, "B"));
    controller.restoreTransientState(state);
    SystemInterface original = exchanger.getInStream(0).getFluid();
    SystemSrkEos changed = new SystemSrkEos(original.getTemperature(), original.getPressure());
    changed.addComponent("methane", 0.9);
    changed.addComponent("propane", 0.1);
    changed.setMixingRule("classic");
    changed.setTotalFlowRate(original.getFlowRate("kg/sec"), "kg/sec");
    exchanger.getInStream(0).setThermoSystem(changed);
    assertThrows(IllegalArgumentException.class,
        () -> HeatExchangerControllerTuning.applyRecommendation(project, pid, bindings, result, "B"));
    exchanger.getInStream(0).setThermoSystem(original);
    PidDesignModel revised = HeatExchangerControllerTuning.applyRecommendation(project, pid, bindings, result, "B");
    assertEquals("B", project.getRevision());
    assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(project, revised));
    Path directory = HeatExchangerAutomationWorkflow.export(project, metadata(), temporaryDirectory.resolve("tuned"),
        result);
    JsonObject evidence = json(directory.resolve("heat-exchanger-controller-tuning.json"));
    assertEquals("MATCHES_TESTED_CANDIDATE", evidence.get("applicationStatus").getAsString());
    assertEquals("B", evidence.get("exportRevision").getAsString());
    assertEquals(3, evidence.getAsJsonObject("study").getAsJsonArray("candidates").size());
    controller.setMode(ControllerMode.MANUAL);
    assertThrows(IllegalArgumentException.class, () -> HeatExchangerAutomationWorkflow.export(project, metadata(),
        temporaryDirectory.resolve("manual"), result));
    controller.restoreTransientState(state);
    controller.setControllerParameters(selected[0], selected[1], selected[2]);
    controller.setDeadBand(5.0);
    assertThrows(IllegalArgumentException.class, () -> HeatExchangerAutomationWorkflow.export(project, metadata(),
        temporaryDirectory.resolve("deadband"), result));
    exchanger.getInStream(0).setTemperature(105.0, "C");
    assertThrows(IllegalArgumentException.class,
        () -> HeatExchangerAutomationWorkflow.export(project, metadata(), temporaryDirectory.resolve("stale"), result));
  }

  /** Checks bounded candidate input validation and rejects duplicate automatic tag allocation before mutation. */
  @Test
  void rejectsInvalidStudyInputsAndDuplicateInstrumentation() {
    assertThrows(IllegalArgumentException.class,
        () -> new Definition("TIC-102", 45.0, Double.NaN, 100.0, 10.0, 0.2, 0.01, new double[][] {{1.0, 10.0, 0.0}}));
    assertThrows(IllegalArgumentException.class, () -> new Definition("TIC-102", 45.0, 1.0, 100.0, 10.0, 0.2, 0.01,
        new double[][] {{Double.POSITIVE_INFINITY, 10.0, 0.0}}));
    assertThrows(IllegalArgumentException.class, () -> new Settings(101, 102, 45.0, 1.0, 10.0, 0.0));
    EngineeringProject project = example();
    assertThrows(IllegalArgumentException.class,
        () -> HeatExchangerAutomationWorkflow.instrument(project.getEngineeringProcessSystem(), "E-101", "TCV-201",
            "DUPLICATE", new Settings(101, 201, 45.0, 1.0, 10.0, 0.0)));
    assertEquals(9, project.getEngineeringProcessSystem().getMeasurementDevices().size());
    for (String collision : new String[] {"TIC-102", "TT-101"}) {
      ProcessSystem source = sourceExample(false);
      source.add(new ControllerDeviceBaseClass(collision));
      assertThrows(IllegalArgumentException.class, () -> HeatExchangerAutomationWorkflow.instrument(source, "E-101",
          "TCV-201", "COLLISION", new Settings(101, 201, 45.0, 1.0, 10.0, 0.0)));
      assertEquals(0, source.getMeasurementDevices().size());
      assertTrue(source.getUnit("TCV-201").getController() == null);
    }
  }

  /** Verifies stale canonical settings fail before tuning and unsuccessful short studies remain explicit. */
  @Test
  void rejectsStaleCanonicalPolicyAndReportsNoAcceptableCandidate() {
    EngineeringProject project = example();
    PidDesignModel pid = PidNativeControlSystem.fromProcess(project);
    PidSimulationBindings bindings = PidNativeControlSystem.bind(project, pid);
    Definition definition = new Definition("TIC-102", 45.0, 1.0, 1.0, 1.0, 0.001, 0.01,
        new double[][] {{1.0, 10.0, 0.0}});
    Result result = HeatExchangerControllerTuning.tune(project, pid, bindings, definition);
    assertFalse(result.hasRecommendation());
    assertEquals("NO_ACCEPTABLE_CANDIDATE",
        JsonParser.parseString(result.toJson()).getAsJsonObject().get("status").getAsString());
    TemperatureTransmitter measurement = (TemperatureTransmitter) project.getEngineeringProcessSystem()
        .getMeasurementDevice("TT-102");
    measurement.setFirstOrderTimeConstant(3.0);
    assertThrows(IllegalArgumentException.class,
        () -> HeatExchangerControllerTuning.tune(project, pid, bindings, definition));
  }

  /** Whole-process rollback restores the actual controller, sensors, valve and exchanger after a failed trial. */
  @Test
  void rollsBackAndReplaysTheActualThermalControlChain() {
    ProcessSystem process = example().getEngineeringProcessSystem();
    assertTrue(process.getTransientTransactionCoverage().isComplete(),
        process.getTransientTransactionCoverage().getBlockingIssues().toString());
    ProcessSystem reference = process.copy();
    DynamicHeatExchanger exchanger = (DynamicHeatExchanger) process.getUnit("E-101");
    Stream outlet = (Stream) exchanger.getOutStream(0);
    double before = exchanger.getThermalInventoryEnergyJ();
    assertThrows(IllegalStateException.class, () -> {
      try (TransientStepTransaction transaction = process.beginTransientStepTransaction()) {
        process.runTransient(1.0, UUID.randomUUID());
        throw new IllegalStateException("Rejected downstream trial");
      }
    });
    assertEquals(before, exchanger.getThermalInventoryEnergyJ(), 0.0);
    assertTrue(outlet == exchanger.getOutStream(0));
    UUID id = UUID.randomUUID();
    process.runTransient(1.0, id);
    reference.runTransient(1.0, id);
    assertEquals(((DynamicHeatExchanger) reference.getUnit("E-101")).getThermalInventoryEnergyJ(),
        exchanger.getThermalInventoryEnergyJ(), 0.0);
    assertEquals(((ThrottlingValve) reference.getUnit("TCV-201")).getPercentValveOpening(),
        ((ThrottlingValve) process.getUnit("TCV-201")).getPercentValveOpening(), 0.0);
  }

  /**
   * Both upstream valves and the hot-side tapping segment must remain native source attachments.
   *
   * @throws Exception if exporting or inspecting the native drawing fails
   */
  @Test
  void drawsTheActualHotInletValveWithoutMovingItsDownstreamMeasurements() throws Exception {
    EngineeringProject project = example(true);
    Path directory = HeatExchangerAutomationWorkflow.export(project, metadata(),
        temporaryDirectory.resolve("both-valves"), null);
    JsonObject readiness = json(directory.resolve("professional-pid-readiness.json"));
    assertTrue(readiness.get("drawingHasNoErrors").getAsBoolean(), readiness.toString());
    String layout = new String(Files.readAllBytes(directory.resolve("drawings/pid-sheet-001.layout.json")),
        StandardCharsets.UTF_8);
    assertTrue(layout.contains("HV-101"));
    assertTrue(layout.contains("NG-TO-E101"));
    JsonObject geometry = JsonParser.parseString(layout).getAsJsonObject();
    double firstMaterialX = geometry.getAsJsonObject("exchangerMaterialSegments").getAsJsonArray("NG-TO-E101").get(0)
        .getAsJsonObject().get("x").getAsDouble();
    for (com.google.gson.JsonElement node : geometry.getAsJsonArray("nodes")) {
      JsonObject item = node.getAsJsonObject();
      if ("TT-101".equals(item.get("tag").getAsString()) || "PT-101".equals(item.get("tag").getAsString())) {
        double tapX = item.getAsJsonArray("bbox").get(0).getAsDouble()
            + item.getAsJsonArray("bbox").get(2).getAsDouble() / 2.0;
        assertTrue(tapX > firstMaterialX && tapX < 355.0, "Inlet instruments must sense downstream of HV-101");
      }
    }
  }
}
