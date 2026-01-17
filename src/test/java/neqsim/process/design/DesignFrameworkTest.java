package neqsim.process.design;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the design framework including DesignSpecification, AutoSizeable, and DesignOptimizer.
 */
class DesignFrameworkTest {

  private static SystemInterface testFluid;
  private static ProcessSystem testProcess;
  private static Separator separator;

  @BeforeAll
  static void setup() {
    // Create a simple gas-condensate fluid
    testFluid = new SystemSrkEos(298.15, 50.0);
    testFluid.addComponent("methane", 0.85);
    testFluid.addComponent("ethane", 0.08);
    testFluid.addComponent("propane", 0.04);
    testFluid.addComponent("n-pentane", 0.03);
    testFluid.setMixingRule("classic");

    // Create a simple process
    testProcess = new ProcessSystem();

    Stream feed = new Stream("Feed", testFluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    separator = new Separator("HP-Separator", feed);
    separator.setInternalDiameter(2.0);
    separator.setSeparatorLength(6.0);

    testProcess.add(feed);
    testProcess.add(separator);
    testProcess.run();
  }

  @Test
  void testDesignSpecificationBuilder() {
    // Test creating a design specification
    DesignSpecification spec =
        DesignSpecification.forSeparator("Test-Sep").setKFactor(0.08).setDiameter(2.5, "m")
            .setLength(7.0, "m").setMaterial("316L").setStandard("ASME-VIII").setSafetyFactor(1.25);

    assertEquals("Test-Sep", spec.getEquipmentName());
    assertEquals("Separator", spec.getEquipmentType());
    assertEquals(1.25, spec.getSafetyFactor(), 0.001);
    assertEquals("316L", spec.getMaterialGrade());
    assertEquals("ASME-VIII", spec.getDesignStandard());
    assertTrue(spec.getDesignParameters().containsKey("kFactor"));
    assertTrue(spec.getDesignParameters().containsKey("diameter"));
  }

  @Test
  void testDesignSpecificationApply() {
    // Create a separator and apply spec
    Stream feed = new Stream("Feed", testFluid.clone());
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    feed.run();

    Separator sep = new Separator("Spec-Test-Sep", feed);

    DesignSpecification spec = DesignSpecification.forSeparator("Spec-Test-Sep").setKFactor(0.10)
        .setDiameter(3.0, "m").setLength(9.0, "m");

    spec.applyTo(sep);

    assertEquals(0.10, sep.getDesignGasLoadFactor(), 0.001);
    assertEquals(3.0, sep.getInternalDiameter(), 0.001);
    assertEquals(9.0, sep.getSeparatorLength(), 0.001);
  }

  @Test
  void testAutoSizeableSeparator() {
    // Create a separator with known conditions
    Stream feed = new Stream("Feed", testFluid.clone());
    feed.setFlowRate(15000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    feed.run();

    Separator sep = new Separator("AutoSize-Sep", feed);
    sep.setDesignGasLoadFactor(0.08); // K = 0.08 m/s
    sep.run();

    // Should not be auto-sized initially
    assertFalse(sep.isAutoSized());

    // Auto-size with safety factor
    sep.autoSize(1.2);

    // Should now be auto-sized
    assertTrue(sep.isAutoSized());

    // Diameter should be positive
    assertTrue(sep.getInternalDiameter() > 0);

    // Should produce a sizing report
    String report = sep.getSizingReport();
    assertNotNull(report);
    assertTrue(report.contains("Auto-Sizing Report"));
    assertTrue(report.contains("true")); // autoSized = true
  }

  @Test
  void testEquipmentConstraintRegistry() {
    EquipmentConstraintRegistry registry = EquipmentConstraintRegistry.getInstance();

    // Check separator constraints
    List<EquipmentConstraintRegistry.ConstraintTemplate> sepConstraints =
        registry.getConstraintTemplates("Separator");
    assertFalse(sepConstraints.isEmpty());
    assertTrue(sepConstraints.stream().anyMatch(c -> c.getType().equals("gasLoadFactor")));

    // Check compressor constraints
    List<EquipmentConstraintRegistry.ConstraintTemplate> compConstraints =
        registry.getConstraintTemplates("Compressor");
    assertFalse(compConstraints.isEmpty());
    assertTrue(compConstraints.stream().anyMatch(c -> c.getType().equals("surgeLine")));

    // Check pipeline constraints
    List<EquipmentConstraintRegistry.ConstraintTemplate> pipeConstraints =
        registry.getConstraintTemplates("Pipeline");
    assertFalse(pipeConstraints.isEmpty());
    assertTrue(pipeConstraints.stream().anyMatch(c -> c.getType().equals("maxVelocity")));
  }

  @Test
  void testProcessBasisBuilder() {
    ProcessBasis basis =
        ProcessBasis.builder().setFeedFluid(testFluid).setFeedFlowRate(50000.0, "kg/hr")
            .setFeedPressure(80.0, "bara").setFeedTemperature(40.0, "C")
            .addStagePressure(1, 50.0, "bara").addStagePressure(2, 20.0, "bara")
            .addStagePressure(3, 5.0, "bara").setCompanyStandard("Equinor", "TR2000")
            .setSafetyFactor(1.15).setAmbientTemperature(15.0, "C").build();

    assertEquals(50000.0, basis.getFeedFlowRate(), 0.1);
    assertEquals(80.0, basis.getFeedPressure(), 0.001);
    assertEquals(40.0 + 273.15, basis.getFeedTemperature(), 0.001);
    assertEquals(3, basis.getNumberOfStages());
    assertEquals(50.0, basis.getStagePressure(1), 0.001);
    assertEquals("Equinor", basis.getCompanyStandard());
    assertEquals(1.15, basis.getSafetyFactor(), 0.001);
  }

  @Test
  void testDesignResult() {
    DesignResult result = new DesignResult(testProcess);
    result.setConverged(true);
    result.setIterations(15);
    result.setObjectiveValue(12500.0);

    result.addOptimizedFlowRate("Export Gas", 10000.0);
    result.addEquipmentSize("HP-Separator", "diameter", 2.5);
    result.addEquipmentSize("HP-Separator", "length", 7.5);
    result.addConstraintStatus("HP-Separator", "K-factor", 0.065, 0.08, 0.8125);

    assertTrue(result.isConverged());
    assertEquals(15, result.getIterations());
    assertEquals(12500.0, result.getObjectiveValue(), 0.1);
    assertEquals(10000.0, result.getOptimizedFlowRate("Export Gas"), 0.1);

    Map<String, Double> sizes = result.getEquipmentSizes("HP-Separator");
    assertEquals(2.5, sizes.get("diameter"), 0.001);
    assertEquals(7.5, sizes.get("length"), 0.001);

    assertFalse(result.hasViolations());

    String summary = result.getSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Converged: true"));
  }

  @Test
  void testDesignOptimizerWorkflow() {
    // Create a simple process
    SystemInterface fluid = new SystemSrkEos(298.15, 80.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(80.0, "bara");

    Separator sep = new Separator("HP-Sep", feed);
    sep.setInternalDiameter(2.0);
    sep.setSeparatorLength(6.0);
    sep.setDesignGasLoadFactor(0.08);

    ThrottlingValve valve = new ThrottlingValve("HP-Valve", sep.getGasOutStream());
    valve.setOutletPressure(30.0, "bara");
    valve.setCv(200.0);

    process.add(feed);
    process.add(sep);
    process.add(valve);

    // Test the workflow
    DesignOptimizer optimizer = DesignOptimizer.forProcess(process).autoSizeEquipment(1.2)
        .applyDefaultConstraints().setObjective(DesignOptimizer.ObjectiveType.MAXIMIZE_PRODUCTION);

    // Just run validation (full optimization would require more setup)
    DesignResult result = optimizer.validate();

    assertNotNull(result);
    assertNotNull(result.getProcess());
  }

  @Test
  void testValveDesignSpecification() {
    // Create valve with design spec
    Stream feed = new Stream("Feed", testFluid.clone());
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("Test-Valve", feed);
    valve.setOutletPressure(30.0, "bara");

    DesignSpecification spec =
        DesignSpecification.forValve("Test-Valve").setCv(150.0).setMaxValveOpening(90.0);

    spec.applyTo(valve);

    assertEquals(150.0, valve.getCv(), 0.1);
  }

  @Test
  void testHeaterDesignSpecification() {
    // Create heater with design spec
    Stream feed = new Stream("Feed", testFluid.clone());
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.run();

    Heater heater = new Heater("Test-Heater", feed);
    heater.setOutTemperature(60.0, "C");

    DesignSpecification spec = DesignSpecification.forHeater("Test-Heater").setMaxDuty(5.0, "MW");

    spec.applyTo(heater);

    assertEquals(5.0e6, heater.getMaxDesignDuty(), 1.0);
  }

  @Test
  void testAutoSizeableValve() {
    // Create a valve with known conditions
    Stream feed = new Stream("Feed", testFluid.clone());
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("AutoSize-Valve", feed);
    valve.setOutletPressure(30.0, "bara");
    valve.run();

    // Should not be auto-sized initially
    assertFalse(valve.isAutoSized());

    // Auto-size with default safety factor
    valve.autoSize();

    // Should now be auto-sized
    assertTrue(valve.isAutoSized());

    // Should have calculated Cv
    assertTrue(valve.getMechanicalDesign().getValveCvMax() > 0);

    // Should produce a sizing report
    String report = valve.getSizingReport();
    assertNotNull(report);
    assertTrue(report.contains("Valve Sizing Report"));

    // JSON report should be valid
    String jsonReport = valve.getSizingReportJson();
    assertNotNull(jsonReport);
    assertTrue(jsonReport.contains("equipmentType"));
    assertTrue(jsonReport.contains("ThrottlingValve"));
  }

  @Test
  void testAutoSizeablePipeline() {
    // Create a pipeline with known conditions
    SystemInterface gasFluid = new SystemSrkEos(298.15, 80.0);
    gasFluid.addComponent("methane", 0.95);
    gasFluid.addComponent("ethane", 0.05);
    gasFluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", gasFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setPressure(80.0, "bara");
    feed.setTemperature(25.0, "C");
    feed.run();

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills pipeline =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("AutoSize-Pipeline", feed);
    pipeline.setLength(10000.0); // 10 km
    pipeline.setElevation(0.0);
    pipeline.setDiameter(0.2); // Initial diameter 200mm
    pipeline.run();

    // Should not be auto-sized initially
    assertFalse(pipeline.isAutoSized());

    // Auto-size
    pipeline.autoSize(1.2);

    // Should now be auto-sized
    assertTrue(pipeline.isAutoSized());

    // Diameter should be set
    assertTrue(pipeline.getDiameter() > 0);

    // Should produce sizing reports
    String report = pipeline.getSizingReport();
    assertNotNull(report);
    assertTrue(report.contains("Pipeline Sizing Report"));

    String jsonReport = pipeline.getSizingReportJson();
    assertNotNull(jsonReport);
    assertTrue(jsonReport.contains("PipeBeggsAndBrills"));
  }

  @Test
  void testThreeStageSeparationTemplate() {
    // Create a multiphase fluid for the template
    SystemInterface oilGasFluid = new SystemSrkEos(323.15, 85.0);
    oilGasFluid.addComponent("methane", 0.50);
    oilGasFluid.addComponent("ethane", 0.10);
    oilGasFluid.addComponent("propane", 0.10);
    oilGasFluid.addComponent("n-pentane", 0.15);
    oilGasFluid.addComponent("n-heptane", 0.10);
    oilGasFluid.addComponent("nC10", 0.05);
    oilGasFluid.setMixingRule("classic");

    // Create process basis
    ProcessBasis basis = ProcessBasis.builder().setFeedFluid(oilGasFluid)
        .setFeedFlowRate(50000.0, "kg/hr").setFeedPressure(85.0, "bara")
        .setFeedTemperature(50.0, "C").addStagePressure(1, 80.0, "bara")
        .addStagePressure(2, 20.0, "bara").addStagePressure(3, 2.0, "bara").build();

    // Create template
    neqsim.process.design.template.ThreeStageSeparationTemplate template =
        new neqsim.process.design.template.ThreeStageSeparationTemplate();

    // Check applicability
    assertTrue(template.isApplicable(oilGasFluid));

    // Check required equipment types
    String[] requiredEquipment = template.getRequiredEquipmentTypes();
    assertEquals(2, requiredEquipment.length);
    assertEquals("Separator", requiredEquipment[0]);
    assertEquals("ThrottlingValve", requiredEquipment[1]);

    // Check expected outputs
    String[] outputs = template.getExpectedOutputs();
    assertTrue(outputs.length > 0);

    // Create process from template
    ProcessSystem process = template.create(basis);

    assertNotNull(process);
    assertTrue(process.size() > 0);

    // Run the process
    process.run();

    // Verify HP separator exists
    assertNotNull(process.getUnit("HP Separator"));

    // Verify MP separator exists
    assertNotNull(process.getUnit("MP Separator"));

    // Verify LP separator exists
    assertNotNull(process.getUnit("LP Separator"));
  }

  @Test
  void testDesignOptimizerFromTemplate() {
    // Create a multiphase fluid
    SystemInterface oilGasFluid = new SystemSrkEos(323.15, 85.0);
    oilGasFluid.addComponent("methane", 0.60);
    oilGasFluid.addComponent("ethane", 0.10);
    oilGasFluid.addComponent("propane", 0.10);
    oilGasFluid.addComponent("n-pentane", 0.10);
    oilGasFluid.addComponent("n-heptane", 0.10);
    oilGasFluid.setMixingRule("classic");

    // Create process basis
    ProcessBasis basis = ProcessBasis.builder().setFeedFluid(oilGasFluid)
        .setFeedFlowRate(30000.0, "kg/hr").setFeedPressure(85.0, "bara")
        .setFeedTemperature(50.0, "C").addStagePressure(1, 80.0, "bara")
        .addStagePressure(2, 20.0, "bara").addStagePressure(3, 2.0, "bara").build();

    // Create process using template through DesignOptimizer
    neqsim.process.design.template.ThreeStageSeparationTemplate template =
        new neqsim.process.design.template.ThreeStageSeparationTemplate();

    DesignOptimizer optimizer = DesignOptimizer.fromTemplate(template, basis);

    // Validate the process
    DesignResult result = optimizer.validate();

    assertNotNull(result);
    assertNotNull(result.getProcess());
  }
}
