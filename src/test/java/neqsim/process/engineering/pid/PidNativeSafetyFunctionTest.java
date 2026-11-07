package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.dexpi.Dexpi201PidMaterializer;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.action.EnergizeESDValveAction;
import neqsim.process.logic.action.SetValveOpeningAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioRunner;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Native SIF import, configured-copy identity, voting fan-in and manual-input exchange regressions. */
class PidNativeSafetyFunctionTest {
  @TempDir
  Path directory;

  /** Import creates one shared function and three actual separately bound inputs. */
  @Test
  void importsNativeVotingGraphAndValidatesActualReadback() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction nativeFunction = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, nativeFunction, "HIPPS");
    assertEquals(0, fixture.pid.getElements().size(), "import is atomic and does not mutate the input model");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    PidSafetyFunctionValidator.ConfiguredFunction receipt = PidSafetyFunctionValidator.validateConfigured(
        fixture.project, imported, bindings, fixture.process, nativeFunction.getName(), nativeFunction);
    assertEquals(3, ((java.util.List<?>) receipt.toMap().get("channelBindings")).size());
    assertEquals(1, ((java.util.List<?>) receipt.toMap().get("targetBindings")).size());
    assertEquals("NOT_ESTABLISHED_BY_OBJECT_IDENTITY", receipt.toMap().get("hardwareIndependence"));
    assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionImporter.fromNativeFunction(fixture.project,
        imported, bindings, fixture.process, nativeFunction, "HIPPS"));
  }

  /** A proposal change invalidates old bindings and requires a differently configured native implementation. */
  @Test
  void changedTripRequiresRebindingReconfigurationAndDifferentFingerprint() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction original = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, original, "HIPPS");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    PidSafetyFunctionValidator.ConfiguredFunction before = PidSafetyFunctionValidator
        .validateConfigured(fixture.project, imported, bindings, fixture.process, original.getName(), original);
    for (PidElement element : imported.getElementsByType(PidElementType.MEASUREMENT)) {
      element.attribute("simulationTripSetpoint", Double.valueOf(42.0));
    }
    assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(fixture.project, imported));
    PidSimulationBindings revised = bind(fixture.project, imported);
    assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionValidator.validateConfigured(fixture.project,
        imported, revised, fixture.process, original.getName(), original));
    ClosedLoopSafetyFunction changed = hipps(fixture.process, 42.0);
    PidSafetyFunctionValidator.ConfiguredFunction after = PidSafetyFunctionValidator.validateConfigured(fixture.project,
        imported, revised, fixture.process, changed.getName(), changed);
    assertFalse(before.getFingerprint().equals(after.getFingerprint()));
    assertThrows(IllegalStateException.class, () -> before.assertSameConfiguration(after));
  }

  /** Actual reference identity is checked independently from matching names/classes. */
  @Test
  void rejectsCapturedOriginalChannelsInAConfiguredCopy() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "HIPPS");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionValidator.validateConfigured(fixture.project,
        imported, bindings, fixture.process.copy(), function.getName(), function));
  }

  /** Matching function names and actual target identity do not make an opening command a trip policy. */
  @Test
  void rejectsDifferentActuationSemanticsWithSameNamesAndTargets() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "HIPPS");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    for (boolean energize : new boolean[] {true, false}) {
      ESDLogic wrong = new ESDLogic("HIPPS final element");
      ESDValve target = (ESDValve) fixture.process.getUnit("ESDV-001");
      wrong.addAction(energize ? new EnergizeESDValveAction(target) : new SetValveOpeningAction(target, 100.0), 0.0);
      ClosedLoopSafetyFunction.Builder builder = ClosedLoopSafetyFunction
          .builder("HIPPS-101", "SIF-HIPPS-101", fixture.process, VotingPattern.TWO_OUT_OF_THREE, wrong)
          .logicSolverDelaySeconds(0.1);
      for (SafetyFunctionChannel channel : function.getChannels()) {
        builder.addChannel(channel);
      }
      ClosedLoopSafetyFunction changed = builder.build();
      assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionValidator.validateConfigured(fixture.project,
          imported, bindings, fixture.process, changed.getName(), changed));
    }
  }

  /** Ordered trip delays are part of the governed command policy and cannot silently change. */
  @Test
  void rejectsChangedFinalElementActionDelay() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "HIPPS");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    ESDLogic changedPolicy = new ESDLogic("HIPPS final element");
    changedPolicy.addAction(new TripValveAction((ESDValve) fixture.process.getUnit("ESDV-001")), 0.5);
    ClosedLoopSafetyFunction.Builder builder = ClosedLoopSafetyFunction
        .builder("HIPPS-101", "SIF-HIPPS-101", fixture.process, VotingPattern.TWO_OUT_OF_THREE, changedPolicy)
        .logicSolverDelaySeconds(0.1);
    for (SafetyFunctionChannel channel : function.getChannels()) {
      builder.addChannel(channel);
    }
    ClosedLoopSafetyFunction changed = builder.build();
    assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionValidator.validateConfigured(fixture.project,
        imported, bindings, fixture.process, changed.getName(), changed));
  }

  /** Native trip commands do not imply closure when the actual configured fail position is open. */
  @Test
  void rejectsFailOpenTargetForTheIsolationContract() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "HIPPS");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    ((ESDValve) fixture.process.getUnit("ESDV-001")).setFailSafePosition(100.0);
    assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionValidator.validateConfigured(fixture.project,
        imported, bindings, fixture.process, function.getName(), function));
    assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionImporter.fromNativeFunction(fixture.project,
        fixture.pid, fixture.bindings, fixture.process, function, "HIPPS"));
  }

  /** Canonical BPCS travel edits cannot silently retain the fixture's fixed native travel assumption. */
  @Test
  void rejectsUnsupportedBpcsTravelProposalAndInstantaneousNativeMode() {
    Fixture fixture = fixture();
    ProcessSystem configured = fixture.process.copy();
    ThrottlingValve actual = (ThrottlingValve) configured.getUnit("PCV-001");
    actual.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
    actual.setTravelTime(3.0);
    PidDesignModel pid = new PidDesignModel(fixture.project.getProjectId(), "BPCS-TRAVEL-GUARD");
    PidElement proposed = new PidElement("pcv", "PCV-001", PidElementType.CONTROL_VALVE).equipment("V-101").line("GAS")
        .attribute("simulationValveTravelSeconds", Double.valueOf(3.0));
    pid.add(proposed);
    PidSimulationBindings baseline = PidSimulationBindings.from(fixture.project, pid).bindEquipment("PCV-001",
        "PCV-001");
    AdvancedSafetyPidExample.validateBpcsReadback(pid, baseline, configured);
    proposed.attribute("simulationValveTravelSeconds", Double.valueOf(1.0));
    PidSimulationBindings changed = PidSimulationBindings.from(fixture.project, pid).bindEquipment("PCV-001",
        "PCV-001");
    assertThrows(IllegalArgumentException.class,
        () -> AdvancedSafetyPidExample.validateBpcsReadback(pid, changed, configured));
    proposed.attribute("simulationValveTravelSeconds", Double.valueOf(3.0));
    actual.setTravelModel(ValveTravelModel.NONE);
    PidSimulationBindings restored = PidSimulationBindings.from(fixture.project, pid).bindEquipment("PCV-001",
        "PCV-001");
    assertThrows(IllegalArgumentException.class,
        () -> AdvancedSafetyPidExample.validateBpcsReadback(pid, restored, configured));
  }

  /** Inter-unit valves appear once and real valve-outlet monitors receive reciprocal source-backed details. */
  @Test
  void interstageValveAndOutletMonitorHaveOnePhysicalRepresentation() throws Exception {
    Fixture fixture = fixture();
    Stream feed = (Stream) fixture.process.getUnit("FEED");
    ESDValve interstage = (ESDValve) fixture.process.getUnit("ESDV-001");
    Separator upstream = new Separator("V-100", feed);
    upstream.getGasOutStream().setName("V100-GAS");
    upstream.getLiquidOutStream().setName("V100-LIQUID");
    interstage.setInletStream(upstream.getGasOutStream());
    java.util.List<neqsim.process.equipment.ProcessEquipmentInterface> previous = new java.util.ArrayList<neqsim.process.equipment.ProcessEquipmentInterface>(
        fixture.process.getUnitOperations());
    fixture.process.getUnitOperations().clear();
    fixture.process.add(feed);
    fixture.process.add(upstream);
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : previous) {
      if (unit != feed) {
        fixture.process.add(unit);
      }
    }
    ThrottlingValve export = (ThrottlingValve) fixture.process.getUnit("PCV-001");
    export.getOutletStream().setName("GAS-EXPORT");
    VolumeFlowTransmitter flow = new VolumeFlowTransmitter("FT-EXPORT", export.getOutletStream());
    fixture.process.add(flow);
    fixture.process.run();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder
        .from("Actual interstage layout", fixture.process, "2.0.1").projectId("NATIVE-SIF")
        .registerProposedInstruments(false).build().setRevision("A");
    PidDesignModel empty = new PidDesignModel(project.getProjectId(), "TEST");
    PidDesignModel pid = PidSafetyFunctionImporter.fromNativeFunction(project, empty,
        PidSimulationBindings.from(project, empty), fixture.process, hipps(fixture.process), "HIPPS");
    pid.add(new PidElement("flow", "FT-EXPORT", PidElementType.MEASUREMENT).equipment("PCV-001").line("GAS-EXPORT"));
    PidSimulationBindings bindings = bind(project, pid);
    Path exported = ProfessionalPidPackageExporter.export(project, pid, bindings, metadata(),
        directory.resolve("interstage-package"));
    int valveCount = 0;
    int flowCount = 0;
    boolean detailRoute = false;
    try (java.util.stream.Stream<Path> files = Files.list(exported.resolve("drawings"))) {
      for (Path layout : (Iterable<Path>) files.filter(file -> file.getFileName().toString().startsWith("pid-sheet-")
          && file.getFileName().toString().endsWith(".layout.json"))::iterator) {
        JsonObject spec = JsonParser.parseString(new String(Files.readAllBytes(layout), StandardCharsets.UTF_8))
            .getAsJsonObject();
        for (com.google.gson.JsonElement item : spec.getAsJsonArray("nodes")) {
          String id = item.getAsJsonObject().get("id").getAsString();
          valveCount += "ESDV-001".equals(id) ? 1 : 0;
          flowCount += "FT-EXPORT".equals(id) ? 1 : 0;
        }
        detailRoute |= spec.toString().contains("detail-source:FT-EXPORT") && spec.toString().contains("GAS-EXPORT")
            && spec.toString().contains("main V-101 / PID-");
      }
    }
    assertEquals(1, valveCount, "The same actual interstage valve must not become two depicted physical valves");
    assertEquals(1, flowCount);
    assertTrue(detailRoute, "Actual outlet stream and reciprocal primary-sheet identity must remain explicit");
  }

  /** Multiple canonical aliases cannot turn one native source into three independent channels. */
  @Test
  void rejectsAReusedInstrumentAlias() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "HIPPS");
    imported.add(new PidElement("alias", "PT-ALIAS", PidElementType.MEASUREMENT).equipment("V-101").line("GAS")
        .connect("native-sif:SIF-HIPPS-101").attribute("simulationTripUnit", "bara")
        .attribute("simulationTripSetpoint", 41.0).attribute("simulationTripDirection", "HIGH")
        .attribute("simulationResponseDelaySeconds", 0.1));
    PidSimulationBindings bindings = bind(fixture.project, imported).bindMeasurement("PT-ALIAS", "PT-A");
    assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionValidator.validateConfigured(fixture.project,
        imported, bindings, fixture.process, function.getName(), function));
  }

  /** No-demand execution is retained without claiming that final-element demand response was tested. */
  @Test
  void attachesFullWindowNoDemandToItsExactNativeConfiguration() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "HIPPS");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    PidSafetyFunctionValidator.ConfiguredFunction receipt = PidSafetyFunctionValidator
        .validateConfigured(fixture.project, imported, bindings, fixture.process, function.getName(), function);
    DynamicSafetyScenario scenario = monitoredScenario(fixture, imported, bindings, receipt, false);
    DynamicSafetyScenarioResult result = new PidSimulationSession(fixture.project, imported, bindings)
        .runDynamicScenario(scenario, bindings.getBoundPidTags());
    Map<String, Object> attached = PidSafetyFunctionValidator.attachResult(receipt, scenario, result,
        Collections.singleton("envelope"));
    assertTrue(result.isPassed(), result.toJson());
    assertEquals("MONITORED_NO_DEMAND", attached.get("executionStatus"));
    assertEquals(Boolean.TRUE, attached.get("fullHorizonCompleted"));
    assertEquals(Boolean.FALSE, attached.get("fitnessForConstruction"));
  }

  /** A later violation cannot be erased by an initially satisfied envelope sample. */
  @Test
  void preservesObservedFullWindowFailure() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "HIPPS");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    PidSafetyFunctionValidator.ConfiguredFunction receipt = PidSafetyFunctionValidator
        .validateConfigured(fixture.project, imported, bindings, fixture.process, function.getName(), function);
    DynamicSafetyScenario scenario = monitoredScenario(fixture, imported, bindings, receipt, true);
    DynamicSafetyScenarioResult result = new PidSimulationSession(fixture.project, imported, bindings)
        .runDynamicScenario(scenario, bindings.getBoundPidTags());
    Map<String, Object> attached = PidSafetyFunctionValidator.attachResult(receipt, scenario, result,
        Collections.singleton("envelope"));
    assertFalse(result.isPassed(), result.toJson());
    assertEquals("OBSERVED_FAIL", attached.get("executionStatus"));
    assertEquals(Boolean.FALSE, attached.get("sampledBoundsPassed"));
  }

  /** A vote before the end of the window does not prove an actuation after a longer solver delay. */
  @Test
  void aVoteWithoutActuationDoesNotAcquireResponseCredit() {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process, 39.0, 2.0);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "HIPPS");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    PidSafetyFunctionValidator.ConfiguredFunction receipt = PidSafetyFunctionValidator
        .validateConfigured(fixture.project, imported, bindings, fixture.process, function.getName(), function);
    DynamicSafetyScenario scenario = monitoredScenario(fixture, imported, bindings, receipt, false);
    DynamicSafetyScenarioResult result = new PidSimulationSession(fixture.project, imported, bindings)
        .runDynamicScenario(scenario, bindings.getBoundPidTags());
    Map<String, Object> attached = PidSafetyFunctionValidator.attachResult(receipt, scenario, result,
        Collections.singleton("envelope"));
    assertTrue(result.isPassed(), "The explicit sampled pressure envelope is satisfied");
    assertEquals("DEMAND_WITHOUT_ACTUATION", attached.get("executionStatus"));
  }

  /** A governed stroke edit changes actual native closure and requires a fresh bound configuration and rerun. */
  @Test
  void canonicalStrokeRevisionChangesTheExecutedNativeResponse() throws Exception {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process, 39.0);
    PidDesignModel pid = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid, fixture.bindings,
        fixture.process, function, "HIPPS");
    PidSimulationBindings originalBindings = bind(fixture.project, pid);
    PidSafetyFunctionValidator.ConfiguredFunction original = PidSafetyFunctionValidator
        .validateConfigured(fixture.project, pid, originalBindings, fixture.process, function.getName(), function);
    String originalPid = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(pid.toMap());
    DynamicSafetyScenario first = monitoredScenario(fixture, pid, originalBindings, original, false, true);
    DynamicSafetyScenarioResult before = new PidSimulationSession(fixture.project, pid, originalBindings)
        .runDynamicScenario(first, originalBindings.getBoundPidTags());
    assertFalse(before.isPassed(), "A one-second stroke cannot meet the 0.8-second initiating-event deadline");
    ProcessSystem revisedCopy = fixture.process.copy();
    ((ESDValve) revisedCopy.getUnit("ESDV-001")).setStrokeTime(0.5);
    ClosedLoopSafetyFunction revisedFunction = hipps(revisedCopy, 39.0);
    for (PidElement element : pid.getElements()) {
      if ("ESDV-001".equals(element.getTag())) {
        element.attribute("simulationStrokeSeconds", Double.valueOf(0.5));
      } else if ("SIF-HIPPS-101".equals(element.getTag())) {
        element.attribute("simulationFinalElementActionConfiguration",
            ((ESDLogic) revisedFunction.getFinalElementLogic()).getTripActionConfiguration());
      }
    }
    assertThrows(IllegalStateException.class, () -> originalBindings.assertCurrent(fixture.project, pid));
    PidSimulationBindings revisedBindings = bind(fixture.project, pid);
    PidSafetyFunctionValidator.ConfiguredFunction revised = PidSafetyFunctionValidator.validateConfigured(
        fixture.project, pid, revisedBindings, revisedCopy, revisedFunction.getName(), revisedFunction);
    assertFalse(original.getFingerprint().equals(revised.getFingerprint()));
    DynamicSafetyScenario second = monitoredScenario(fixture, pid, revisedBindings, revised, false, true);
    DynamicSafetyScenarioResult after = new PidSimulationSession(fixture.project, pid, revisedBindings)
        .runDynamicScenario(second, revisedBindings.getBoundPidTags());
    assertTrue(after.isPassed(), after.toJson());
    assertTrue(after.getCriterionResults().get("closure").getFirstSatisfiedSeconds().doubleValue() <= 0.8);
    assertEquals(1.0, ((ESDValve) fixture.process.getUnit("ESDV-001")).getStrokeTime(), 0.0,
        "The scenario consumes the canonical revised value on its copy without mutating the physical source");
    String auditDirectory = System.getProperty("neqsim.pid.proposalAuditDirectory");
    if (auditDirectory != null) {
      Path audit = java.nio.file.Paths.get(auditDirectory);
      Files.createDirectories(audit);
      Files.write(audit.resolve("stroke-original-pid.json"), originalPid.getBytes(StandardCharsets.UTF_8));
      Files.write(audit.resolve("stroke-revised-pid.json"), new com.google.gson.GsonBuilder().setPrettyPrinting()
          .create().toJson(pid.toMap()).getBytes(StandardCharsets.UTF_8));
      Files.write(audit.resolve("stroke-original-scenario.json"), new com.google.gson.GsonBuilder().setPrettyPrinting()
          .create().toJson(first.toMap()).getBytes(StandardCharsets.UTF_8));
      Files.write(audit.resolve("stroke-revised-scenario.json"), new com.google.gson.GsonBuilder().setPrettyPrinting()
          .create().toJson(second.toMap()).getBytes(StandardCharsets.UTF_8));
      Files.write(audit.resolve("stroke-original-result.json"), before.toJson().getBytes(StandardCharsets.UTF_8));
      Files.write(audit.resolve("stroke-revised-result.json"), after.toJson().getBytes(StandardCharsets.UTF_8));
      Files.write(audit.resolve("stroke-original-configuration.json"), new com.google.gson.GsonBuilder()
          .setPrettyPrinting().create().toJson(original.toMap()).getBytes(StandardCharsets.UTF_8));
      Files.write(audit.resolve("stroke-revised-configuration.json"), new com.google.gson.GsonBuilder()
          .setPrettyPrinting().create().toJson(revised.toMap()).getBytes(StandardCharsets.UTF_8));
    }
  }

  /**
   * Creates a strict native monitoring test; the optional synthetic observation tests verdict policy only.
   *
   * @param fixture source project
   * @param pid canonical graph
   * @param bindings actual bindings
   * @param receipt frozen native configuration
   * @param laterViolation whether to create a deliberately failing observation after an initially valid sample
   * @return controlled full-horizon scenario
   */
  private static DynamicSafetyScenario monitoredScenario(Fixture fixture, PidDesignModel pid,
      PidSimulationBindings bindings, PidSafetyFunctionValidator.ConfiguredFunction receipt, boolean laterViolation) {
    return monitoredScenario(fixture, pid, bindings, receipt, laterViolation, false);
  }

  /**
   * Builds a monitoring or finite-stroke integrity test using actual canonical factory settings.
   *
   * @param fixture source project
   * @param pid canonical graph
   * @param bindings actual bindings
   * @param receipt frozen native configuration
   * @param laterViolation whether to synthesize a policy-only later envelope violation
   * @param requireClosure whether the native actuator must reach zero within 0.8 seconds
   * @return controlled native scenario
   */
  private static DynamicSafetyScenario monitoredScenario(Fixture fixture, PidDesignModel pid,
      PidSimulationBindings bindings, PidSafetyFunctionValidator.ConfiguredFunction receipt, boolean laterViolation,
      boolean requireClosure) {
    DynamicSafetyScenario.Builder builder = DynamicSafetyScenario.builder("MONITORED", "Native monitoring")
        .durationSeconds(1.0).timeStepSeconds(0.1).triggerTimeSeconds(0.0).eventExactTiming(true)
        .initiatingEvent("NO-OP-v1", process -> {
        }).addEvidenceReference(receipt.getEvidenceReference()).addLogic("BOUND-HIPPS-v1", process -> {
          ((ESDValve) process.getUnit("ESDV-001"))
              .setStrokeTime(((Number) importedAttribute(pid, "ESDV-001", "simulationStrokeSeconds")).doubleValue());
          double threshold = ((Number) importedAttribute(pid, "PT-A", "simulationTripSetpoint")).doubleValue();
          double delay = ((Number) importedAttribute(pid, "SIF-HIPPS-101", "simulationLogicDelaySeconds"))
              .doubleValue();
          ClosedLoopSafetyFunction actual = hipps(process, threshold, delay);
          receipt.assertSameConfiguration(PidSafetyFunctionValidator.validateConfigured(fixture.project, pid, bindings,
              process, actual.getName(), actual));
          return actual;
        })
        .addCriterion(DynamicScenarioCriterion
            .builder("envelope", "Sampled envelope", "bara", "ENVELOPE-v1",
                process -> laterViolation && process.getTime() > 0.5 ? 44.0 : 40.0)
            .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS)
            .acceptanceRange(Double.valueOf(0.0), Double.valueOf(43.0)).deadlineSeconds(1.0).build());
    if (requireClosure) {
      builder.addCriterion(DynamicScenarioCriterion
          .builder("closure", "Actual native actuator closure", "%", "ACTUAL-ESDV-OPENING-v1",
              process -> ((ESDValve) process.getUnit("ESDV-001")).getPercentValveOpening())
          .acceptanceRange(Double.valueOf(0.0), Double.valueOf(0.0)).deadlineSeconds(0.8).build());
    }
    for (String tag : bindings.getBoundPidTags()) {
      builder.addEquipmentTag(bindings.getSimulationName(tag));
    }
    return builder.build();
  }

  /** An actual manual button is a field function without an invented process sensing location. */
  @Test
  void manualButtonHasNoFakeSensingTapInNativeDexpi() throws Exception {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = manual(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "MANUAL_ESD");
    PidSimulationBindings bindings = bind(fixture.project, imported);
    assertEquals("MANUAL_INPUT", PidPhysicalProjection.resolve(fixture.process, imported, bindings).getAttachments()
        .get("HS-001").getKind().name());
    Path xml = directory.resolve("manual.xml");
    DexpiEngineeringExporter.exportDexpi201(fixture.project, xml, metadata());
    Dexpi201PidMaterializer.Result result = Dexpi201PidMaterializer.materialize(fixture.project, imported, bindings,
        xml);
    assertTrue(result.isComplete(), result.toJson());
    assertTrue(result.getAssessment().isSchemaAndProfileConformant(), result.getAssessment().getErrors().toString());
    Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
    Element button = object(document, result.getNativeIds().get("HS-001"));
    assertNotNull(button);
    assertEquals("Plant/Instrumentation.ProcessInstrumentationFunction", button.getAttribute("type"));
    assertEquals(0, button.getElementsByTagName("Reference").getLength(), "no fabricated SensingLocation reference");
    assertFalse(new String(Files.readAllBytes(xml), StandardCharsets.UTF_8).contains("PidSensor_native-sif-HS-001"));
  }

  /** Manual metadata cannot convert an actual pressure measurement into a button. */
  @Test
  void rejectsManualRoleOnWrongBoundHardware() {
    Fixture fixture = fixture();
    PidDesignModel pid = new PidDesignModel(fixture.project.getProjectId(), "TEST");
    pid.add(new PidElement("fake", "FAKE-HS", PidElementType.MEASUREMENT).equipment("V-101").attribute("inputKind",
        "MANUAL_PUSH_BUTTON"));
    PidSimulationBindings bindings = PidSimulationBindings.from(fixture.project, pid).bindMeasurement("FAKE-HS",
        "PT-A");
    PidPhysicalProjection.Attachment location = PidPhysicalProjection.resolve(fixture.process, pid, bindings)
        .getAttachments().get("FAKE-HS");
    assertEquals(PidPhysicalProjection.Kind.UNRESOLVED, location.getKind());
  }

  /** One shared voting symbol receives all three actual routes without continuation-terminal overlaps. */
  @Test
  void nativeProfessionalDrawingContainsTrueThreeInputFanIn() throws Exception {
    Fixture fixture = fixture();
    ClosedLoopSafetyFunction function = hipps(fixture.process);
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, fixture.process, function, "HIPPS");
    Path exported = ProfessionalPidPackageExporter.export(fixture.project, imported, bind(fixture.project, imported),
        metadata(), directory.resolve("package"));
    Path layout;
    try (java.util.stream.Stream<Path> files = Files.list(exported.resolve("drawings"))) {
      layout = files.filter(file -> file.getFileName().toString().matches("pid-sheet-.*\\.layout\\.json")).findFirst()
          .get();
    }
    JsonObject spec = JsonParser.parseString(new String(Files.readAllBytes(layout), StandardCharsets.UTF_8))
        .getAsJsonObject();
    JsonArray edges = spec.getAsJsonArray("edges");
    int incoming = 0;
    for (com.google.gson.JsonElement edge : edges) {
      JsonObject row = edge.getAsJsonObject();
      if (row.toString().contains("signal:PT-") && row.toString().contains("SIF-HIPPS-101")) {
        incoming++;
      }
    }
    assertEquals(3, incoming, spec.toString());
    String svg = new String(
        Files.readAllBytes(layout.resolveSibling(layout.getFileName().toString().replace(".layout.json", ".svg"))),
        StandardCharsets.UTF_8);
    assertTrue(svg.contains("HIPPS 2oo3"));
    assertFalse(svg.contains("signal-to:SIF-HIPPS-101"));
  }

  /**
   * Builds a small actual native source; these regressions do not qualify its process response.
   *
   * @return solved source and empty canonical model
   */
  private static Fixture fixture() {
    SystemSrkEos fluid = new SystemSrkEos(308.15, 45.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ESDValve inlet = new ESDValve("ESDV-001", feed);
    inlet.setOutletPressure(40.0);
    inlet.setStrokeTime(1.0);
    inlet.getOutletStream().setName("VESSEL-FEED");
    Separator vessel = new Separator("V-101", inlet.getOutletStream());
    vessel.getGasOutStream().setName("GAS");
    vessel.getLiquidOutStream().setName("LIQUID");
    ThrottlingValve gas = new ThrottlingValve("PCV-001", vessel.getGasOutStream());
    gas.setOutletPressure(35.0);
    ThrottlingValve liquid = new ThrottlingValve("LCV-001", vessel.getLiquidOutStream());
    liquid.setOutletPressure(20.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(inlet);
    process.add(vessel);
    process.add(gas);
    process.add(liquid);
    for (String tag : new String[] {"PT-A", "PT-B", "PT-C"}) {
      PressureTransmitter source = new PressureTransmitter(tag, vessel.getGasOutStream());
      source.setTransientSamplingEnabled(true);
      process.add(source);
    }
    process.add(new PushButton("HS-001"));
    process.run();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Native safety graph", process, "2.0.1")
        .projectId("NATIVE-SIF").registerProposedInstruments(false).build().setRevision("A");
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "TEST");
    return new Fixture(process, project, pid, PidSimulationBindings.from(project, pid));
  }

  /** @return actual healthy 2oo3 function at the standard study threshold */
  private static ClosedLoopSafetyFunction hipps(ProcessSystem process) {
    return hipps(process, 41.0);
  }

  /**
   * Builds the actual native three-source function.
   *
   * @param process actual configured process
   * @param threshold requested native threshold
   * @return native function
   */
  private static ClosedLoopSafetyFunction hipps(ProcessSystem process, double threshold) {
    return hipps(process, threshold, 0.1);
  }

  /**
   * Builds the native function from an explicit threshold and solver delay.
   *
   * @param process actual configured process
   * @param threshold requested native trip threshold
   * @param delay requested solver delay, seconds
   * @return native function
   */
  private static ClosedLoopSafetyFunction hipps(ProcessSystem process, double threshold, double delay) {
    ESDLogic action = new ESDLogic("HIPPS final element");
    action.addAction(new TripValveAction((ESDValve) process.getUnit("ESDV-001")), 0.0);
    ClosedLoopSafetyFunction.Builder builder = ClosedLoopSafetyFunction
        .builder("HIPPS-101", "SIF-HIPPS-101", process, VotingPattern.TWO_OUT_OF_THREE, action)
        .logicSolverDelaySeconds(delay);
    for (int index = 0; index < 3; index++) {
      builder.addChannel(
          SafetyFunctionChannel.highTripFromMeasurement(process.getMeasurementDevices().get(index), "bara", threshold)
              .responseDelaySeconds(0.1).build());
    }
    return builder.build();
  }

  /**
   * Reads one canonical test setting used by the real native factory.
   *
   * @param pid canonical graph
   * @param name exact proposal tag
   * @param key governed setting
   * @return actual setting
   */
  private static Object importedAttribute(PidDesignModel pid, String name, String key) {
    for (PidElement element : pid.getElements()) {
      if (name.equals(element.getTag())) {
        return element.getAttributes().get(key);
      }
    }
    throw new IllegalArgumentException("Missing canonical test element " + name);
  }

  /**
   * Builds the actual manual function.
   *
   * @param process actual configured process
   * @return manual native function
   */
  private static ClosedLoopSafetyFunction manual(ProcessSystem process) {
    ESDLogic action = new ESDLogic("Manual final element");
    action.addAction(new TripValveAction((ESDValve) process.getUnit("ESDV-001")), 0.0);
    return ClosedLoopSafetyFunction
        .builder("MANUAL-101", "SIF-MANUAL-101", process, VotingPattern.ONE_OUT_OF_ONE, action)
        .addChannel(
            SafetyFunctionChannel.manualTripFromPushButton((PushButton) process.getMeasurementDevices().get(3)).build())
        .build();
  }

  /**
   * Binds every imported actual hardware identity.
   *
   * @param project governed source
   * @param pid imported canonical model
   * @return exact actual bindings
   */
  private static PidSimulationBindings bind(EngineeringProject project, PidDesignModel pid) {
    PidSimulationBindings bindings = PidSimulationBindings.from(project, pid);
    for (PidElement element : pid.getElements()) {
      if (element.getType() == PidElementType.MEASUREMENT) {
        if (!"PT-ALIAS".equals(element.getTag())) {
          bindings.bindMeasurement(element.getTag(), element.getTag());
        }
      } else if (element.getType() == PidElementType.SHUTDOWN_VALVE) {
        bindings.bindEquipment(element.getTag(), element.getTag());
      }
    }
    return bindings;
  }

  /** @return deterministic native export metadata */
  private static Dexpi20PlantExportMetadata metadata() {
    return Dexpi20PlantExportMetadata.builder("2026-11-05T00:00:00Z", "NeqSim native safety test", "NeqSim", "test")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "NATIVE-SIF")
        .build();
  }

  /**
   * Locates one native object.
   *
   * @param document native instance
   * @param id actual native ID
   * @return matching object or null
   */
  private static Element object(Document document, String id) {
    NodeList elements = document.getElementsByTagName("Object");
    for (int index = 0; index < elements.getLength(); index++) {
      Element element = (Element) elements.item(index);
      if (id.equals(element.getAttribute("id"))) {
        return element;
      }
    }
    return null;
  }

  /** Simple owned native source bundle for each independent regression. */
  private static final class Fixture {
    final ProcessSystem process;
    final EngineeringProject project;
    final PidDesignModel pid;
    final PidSimulationBindings bindings;

    /**
     * Retains the local regression objects.
     *
     * @param process native source
     * @param project governed source
     * @param pid canonical source
     * @param bindings initial source register
     */
    Fixture(ProcessSystem process, EngineeringProject project, PidDesignModel pid, PidSimulationBindings bindings) {
      this.process = process;
      this.project = project;
      this.pid = pid;
      this.bindings = bindings;
    }
  }
}
