package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.dexpi.Dexpi201PidMaterializer;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.engineering.pid.PidSeparatorControlStudy.Definition;
import neqsim.process.engineering.pid.PidSeparatorControlStudy.Limits;
import neqsim.process.engineering.pid.PidSeparatorControlStudy.RefinementLimits;
import neqsim.process.engineering.pid.PidSeparatorControlStudy.RefinementResult;
import neqsim.process.engineering.pid.PidSeparatorControlStudy.Result;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.LevelControlValve;
import neqsim.process.equipment.valve.PressureControlValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.processmodel.dexpi.DexpiInstrumentInfo;
import neqsim.process.processmodel.dexpi.DexpiXmlReader;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** End-to-end qualification of revision-bound coupled separator pressure and level control evidence. */
public class PidSeparatorControlStudyTest extends NeqSimTest {
  private static final Logger logger = LogManager.getLogger(PidSeparatorControlStudyTest.class);

  @TempDir
  Path temporaryDirectory;

  /** Executes and exactly repeats a coupled inlet-ramp case with explicit numerical policy. */
  @Test
  void coupledRampIsDeterministicConservativeAndTraceable() {
    Fixture first = fixture();
    Fixture second = fixture();
    Definition definition = definition("DYN-C034-NOMINAL", 1.0, 80.0, 80.0);
    Result expected = PidSeparatorControlStudy.run(first.project, first.pid, first.bindings, definition, broadLimits());
    Result repeated = PidSeparatorControlStudy.run(second.project, second.pid, second.bindings, definition,
        broadLimits());

    logger.info("DYN-C034 nominal result {}", expected.toJson());
    assertTrue(expected.isPassed(), expected.toJson());
    assertEquals(40, expected.getSamples().size());
    assertEquals(expected.getResultFingerprint(), repeated.getResultFingerprint());
    assertEquals(20.8, number(expected.getSamples().get(0).toMap(), "inletFlowKgPerSecond"), 0.0);
    assertEquals(28.0, number(expected.getFinalSample().toMap(), "inletFlowKgPerSecond"), 0.0);
    assertTrue(expected.getChecks().get("ENERGY_POLICY").isPassed());
    assertTrue(expected.getChecks().get("TRACKER_ORACLE_DIFFERENCE").isPassed());
    Map<String, Object> evidence = PidSeparatorControlStudy.evidence(expected, null);
    assertEquals("EXECUTED_PASS", evidence.get("executionStatus"));
    assertEquals(Boolean.FALSE, evidence.get("silIplSrsCreditEstablished"));
    assertEquals(Boolean.FALSE, evidence.get("fitnessForConstruction"));
  }

  /** Halving the physical timestep stays inside an independently declared response sensitivity envelope. */
  @Test
  void timestepRefinementRetainsCoupledResponse() {
    Fixture fixture = fixture();
    Result coarse = PidSeparatorControlStudy.run(fixture.project, fixture.pid, fixture.bindings,
        definition("DYN-C034-COARSE", 1.0, 80.0, 80.0), broadLimits());
    Result refined = PidSeparatorControlStudy.run(fixture.project, fixture.pid, fixture.bindings,
        definition("DYN-C034-REFINED", 0.5, 80.0, 80.0), broadLimits());
    RefinementResult comparison = PidSeparatorControlStudy.compareRefinement(coarse, refined,
        new RefinementLimits(0.20, 0.20, 0.01, 0.01, 1.0));

    logger.info("DYN-C034 refinement {}", comparison.toMap());
    assertTrue(comparison.isPassed(), comparison.toMap().toString());
    assertTrue(
        ((Map<?, ?>) PidSeparatorControlStudy.evidence(coarse, comparison).get("refinement")).containsKey("checks"));
  }

  /** A smaller declared gas-valve candidate retains its observed failure instead of relaxing the pressure limit. */
  @Test
  void cvCandidateScreenPreservesObservedFailure() {
    Fixture fixture = fixture();
    Limits pressureLimit = limitsWithMaximumPressure(60.0);
    Result passing = PidSeparatorControlStudy.run(fixture.project, fixture.pid, fixture.bindings,
        definition("DYN-C034-CV80-PASS", 1.0, 80.0, 80.0), pressureLimit);
    Result smaller = PidSeparatorControlStudy.run(fixture.project, fixture.pid, fixture.bindings,
        definition("DYN-C034-CV20", 1.0, 20.0, 80.0), pressureLimit);

    logger.info("DYN-C034 Cv screen selected={} smaller={}", passing.toMap().get("responseMetrics"),
        smaller.toMap().get("responseMetrics"));
    assertTrue(passing.isPassed(), passing.toJson());
    assertFalse(smaller.isPassed(), smaller.toJson());
    assertFalse(smaller.getChecks().get("MAXIMUM_PRESSURE").isPassed());
  }

  /** Stale canonical revisions and incomplete native wiring fail before simulation. */
  @Test
  void staleOrIncompleteBindingsFailClosed() {
    Fixture fixture = fixture();
    fixture.pid.getElements().get(0).attribute("changedAfterBinding", Boolean.TRUE);
    assertThrows(IllegalStateException.class, () -> PidSeparatorControlStudy.run(fixture.project, fixture.pid,
        fixture.bindings, definition("STALE", 1.0, 80.0, 80.0), broadLimits()));

    Fixture unwired = fixture();
    PressureControlValve valve = (PressureControlValve) unwired.project.getProcessSystem().getUnit(PCV_NAME);
    valve.setController(new ControllerDeviceBaseClass("replacement PIC"));
    assertThrows(IllegalStateException.class, () -> PidSeparatorControlStudy.run(unwired.project, unwired.pid,
        unwired.bindings, definition("UNWIRED", 1.0, 80.0, 80.0), broadLimits()));
  }

  /** Native DEXPI 2.0.1 export/materialization is readable with the public instrument reader. */
  @Test
  void controlEvidenceUsesDexpi201ReadableCanonicalIdentities() throws Exception {
    Fixture fixture = fixture();
    Result result = PidSeparatorControlStudy.run(fixture.project, fixture.pid, fixture.bindings,
        definition("DYN-C034-DEXPI", 1.0, 80.0, 80.0), broadLimits());
    assertTrue(result.isPassed(), result.toJson());
    Path dexpi = temporaryDirectory.resolve("separator-control-dexpi-201.xml");
    DexpiEngineeringExporter.exportDexpi201(fixture.project, dexpi, metadata());
    Dexpi201PidMaterializer.Result materialized = Dexpi201PidMaterializer.materialize(fixture.project, fixture.pid,
        fixture.bindings, dexpi);
    assertTrue(materialized.isComplete(), materialized.toJson());
    assertTrue(materialized.getAssessment().isSchemaAndProfileConformant(),
        materialized.getAssessment().getErrors().toString());

    List<DexpiInstrumentInfo> imported = DexpiXmlReader.readInstruments(dexpi.toFile());
    Set<String> importedTags = new HashSet<String>();
    Map<String, DexpiInstrumentInfo> importedByTag = new LinkedHashMap<String, DexpiInstrumentInfo>();
    for (DexpiInstrumentInfo instrument : imported) {
      importedTags.add(instrument.getTagName());
      importedByTag.put(instrument.getTagName(), instrument);
    }
    assertTrue(importedTags.contains(PT_TAG), importedTags.toString());
    assertTrue(importedTags.contains(PIC_TAG), importedTags.toString());
    assertTrue(importedTags.contains(PCV_TAG), importedTags.toString());
    assertTrue(importedTags.contains(LT_TAG), importedTags.toString());
    assertTrue(importedTags.contains(LIC_TAG), importedTags.toString());
    assertTrue(importedTags.contains(LCV_TAG), importedTags.toString());
    assertTrue(importedTags.size() >= 6, importedTags.toString());
    assertEquals("P", importedByTag.get(PT_TAG).getCategory());
    assertEquals("T", importedByTag.get(PT_TAG).getFunctions());
    assertEquals("IC", importedByTag.get(PIC_TAG).getFunctions());
    assertEquals(PCV_TAG, importedByTag.get(PCV_TAG).getActuatingTag());
    assertEquals(6, materialized.getNativeIds().size());
  }

  private static final String FEED_NAME = "20-FEED-001";
  private static final String SEPARATOR_NAME = "20-VG-001";
  private static final String PT_NAME = "20-PT-001";
  private static final String PIC_NAME = "20-PIC-001";
  private static final String PCV_NAME = "20-PCV-001";
  private static final String LT_NAME = "20-LT-001";
  private static final String LIC_NAME = "20-LIC-001";
  private static final String LCV_NAME = "20-LCV-001";
  private static final String PT_TAG = "PT-20-001";
  private static final String PIC_TAG = "PIC-20-001";
  private static final String PCV_TAG = "PCV-20-001";
  private static final String LT_TAG = "LT-20-001";
  private static final String LIC_TAG = "LIC-20-001";
  private static final String LCV_TAG = "LCV-20-001";

  /** Builds an isolated public synthetic two-phase separator with both real control loops. */
  private static Fixture fixture() {
    SystemInterface fluid = new SystemSrkEos(288.15, 55.0);
    fluid.addComponent("methane", 0.72);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-hexane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream(FEED_NAME, fluid);
    feed.setFlowRate(20.0, "kg/sec");
    feed.setPressure(55.0, "bara");
    feed.run();
    Separator separator = new Separator(SEPARATOR_NAME);
    separator.addStream(feed);
    separator.setInternalDiameter(2.2);
    separator.setSeparatorLength(7.0);
    separator.setLiquidLevel(0.5);
    separator.setCalculateSteadyState(false);

    PressureControlValve pressureValve = new PressureControlValve(PCV_NAME, separator.getGasOutStream());
    pressureValve.setOutletPressure(45.0);
    pressureValve.setPercentValveOpening(50.0);
    pressureValve.setTravelTimeConstant(4.0);
    pressureValve.setPhysicalStepIdempotencyEnabled(true);
    pressureValve.setAutoMode(false);
    pressureValve.setCalculateSteadyState(false);
    PressureTransmitter pressureTransmitter = new PressureTransmitter(PT_NAME, separator.getGasOutStream());
    pressureTransmitter.setUnit("bara");
    pressureTransmitter.setMinimumValue(40.0);
    pressureTransmitter.setMaximumValue(70.0);
    pressureTransmitter.setTransientSamplingEnabled(true);
    ControllerDeviceBaseClass pressureController = new ControllerDeviceBaseClass(PIC_NAME);
    pressureController.setTransmitter(pressureTransmitter);
    pressureController.setControllerSetPoint(55.0, "bara");
    pressureController.setControllerParameters(6.0, 30.0, 0.0);
    pressureController.setReverseActing(false);
    pressureController.setOutputLimits(5.0, 95.0);
    pressureValve.setController(pressureController);

    LevelControlValve levelValve = new LevelControlValve(LCV_NAME, separator.getLiquidOutStream());
    levelValve.setOutletPressure(40.0);
    levelValve.setPercentValveOpening(50.0);
    levelValve.setTravelTimeConstant(4.0);
    levelValve.setPhysicalStepIdempotencyEnabled(true);
    levelValve.setAutoMode(false);
    levelValve.setCalculateSteadyState(false);
    LevelTransmitter levelTransmitter = new LevelTransmitter(LT_NAME, separator);
    levelTransmitter.setTransientSamplingEnabled(true);
    ControllerDeviceBaseClass levelController = new ControllerDeviceBaseClass(LIC_NAME);
    levelController.setTransmitter(levelTransmitter);
    levelController.setControllerSetPoint(0.5);
    levelController.setControllerParameters(0.8, 120.0, 0.0);
    levelController.setReverseActing(false);
    levelValve.setController(levelController);

    ProcessSystem process = new ProcessSystem("DYN-C034 separator control process");
    process.add(feed);
    process.add(separator);
    process.add(pressureValve);
    process.add(levelValve);
    process.add(pressureTransmitter);
    process.add(levelTransmitter);
    process.run();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder
        .from("DYN-C034 separator control qualification", process, "2.0.1").projectId("DYN-C034")
        .registerProposedInstruments(false).build().setRevision("A");
    PidDesignModel pid = pid(separator);
    PidSimulationBindings bindings = PidSimulationBindings.from(project, pid).bindMeasurement(PT_TAG, PT_NAME)
        .bindController(PIC_TAG, PIC_NAME).bindEquipment(PCV_TAG, PCV_NAME).bindMeasurement(LT_TAG, LT_NAME)
        .bindController(LIC_TAG, LIC_NAME).bindEquipment(LCV_TAG, LCV_NAME);
    return new Fixture(project, pid, bindings);
  }

  /** Creates canonical pressure and level loops with the actual outlet identities. */
  private static PidDesignModel pid(Separator separator) {
    PidDesignModel model = new PidDesignModel("DYN-C034", "DEXPI-2.0.1-SEPARATOR-CONTROL");
    model.add(element("pt", PT_TAG, PidElementType.MEASUREMENT, "PRESSURE-MEASUREMENT")
        .line(separator.getGasOutStream().getName()).attribute("measuredVariable", "PRESSURE").connect("pic"));
    model.add(element("pic", PIC_TAG, PidElementType.CONTROLLER, "PRESSURE-CONTROLLER").connect("pcv"));
    model.add(element("pcv", PCV_TAG, PidElementType.CONTROL_VALVE, "PRESSURE-CONTROL-VALVE")
        .line(separator.getGasOutStream().getName()));
    model.add(element("lt", LT_TAG, PidElementType.MEASUREMENT, "LEVEL-MEASUREMENT")
        .attribute("measuredVariable", "LEVEL").attribute("sensingLocation", "VESSEL").connect("lic"));
    model.add(element("lic", LIC_TAG, PidElementType.CONTROLLER, "LEVEL-CONTROLLER").connect("lcv"));
    model.add(element("lcv", LCV_TAG, PidElementType.CONTROL_VALVE, "LEVEL-CONTROL-VALVE")
        .line(separator.getLiquidOutStream().getName()));
    return model;
  }

  /** Creates one traceable review-required P&amp;ID element. */
  private static PidElement element(String id, String tag, PidElementType type, String purpose) {
    return new PidElement(id, tag, type).equipment(SEPARATOR_NAME)
        .provenance("DYN-C034", "Synthetic separator control acceptance qualification")
        .status(PidProposalStatus.REVIEW_REQUIRED).attribute("proposalPurpose", purpose)
        .attribute("engineeringApprovalRequired", Boolean.TRUE);
  }

  /** Creates one candidate definition. */
  private static Definition definition(String id, double timeStepSeconds, double pcvCvUs, double lcvCvUs) {
    return new Definition(id, FEED_NAME, SEPARATOR_NAME, PT_TAG, PIC_TAG, PCV_TAG, LT_TAG, LIC_TAG, LCV_TAG, 20.0, 28.0,
        10.0, 40.0, timeStepSeconds, pcvCvUs, lcvCvUs);
  }

  /** Broad diagnostic limits used until the frozen public-case values are asserted below. */
  private static Limits broadLimits() {
    return new Limits(1.0, 100.0, 0.0, 1.0, 10000.0, 100.0, 100.0, 1.0, 100.0, 100.0, 1000.0, 1000.0, 0.0, 100.0,
        1.0e-8, 1.0e-6, 1.0e-5, 1.0e-6);
  }

  /** Reuses the same frozen policy except for a data-derived pressure-screen threshold. */
  private static Limits limitsWithMaximumPressure(double maximumPressureBara) {
    return new Limits(1.0, maximumPressureBara, 0.0, 1.0, 10000.0, 100.0, 100.0, 1.0, 100.0, 100.0, 1000.0, 1000.0, 0.0,
        100.0, 1.0e-8, 1.0e-6, 1.0e-5, 1.0e-6);
  }

  /** @return controlled DEXPI 2.0.1 metadata */
  private static Dexpi20PlantExportMetadata metadata() {
    return Dexpi20PlantExportMetadata.builder("2026-11-06T18:00:00+00:00", "NeqSim DYN-C034", "NeqSim", "3.28.0")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "DYN-C034").build();
  }

  /** Reads one numeric map field. */
  private static double number(Map<String, Object> map, String key) {
    return ((Number) map.get(key)).doubleValue();
  }

  /** Reads one nested numeric map field. */
  @SuppressWarnings("unchecked")
  private static double number(Map<String, Object> map, String parent, String key) {
    return number((Map<String, Object>) map.get(parent), key);
  }

  /** Controlled fixture identities. */
  private static final class Fixture {
    private final EngineeringProject project;
    private final PidDesignModel pid;
    private final PidSimulationBindings bindings;

    /** Creates one fixture. */
    private Fixture(EngineeringProject project, PidDesignModel pid, PidSimulationBindings bindings) {
      this.project = project;
      this.pid = pid;
      this.bindings = bindings;
    }
  }
}
