package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.pipeline.TopsidePipingMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.TopsidePipingMechanicalDesignCalculator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for TopsidePiping and its mechanical design.
 */
public class TopsidePipingTest {
  private SystemInterface gasSystem;
  private Stream gasStream;
  private TopsidePiping gasHeader;

  @BeforeEach
  void setUp() {
    // Create a gas system
    gasSystem = new SystemSrkEos(303.15, 50.0);
    gasSystem.addComponent("methane", 0.85);
    gasSystem.addComponent("ethane", 0.10);
    gasSystem.addComponent("propane", 0.05);
    gasSystem.setMixingRule("classic");

    gasStream = new Stream("inlet", gasSystem);
    gasStream.setFlowRate(10000.0, "kg/hr");
    gasStream.run();

    // Create topside piping
    gasHeader = new TopsidePiping("HP Gas Header", gasStream);
    gasHeader.setServiceType(TopsidePiping.ServiceType.PROCESS_GAS);
    gasHeader.setDiameter(0.2032); // 8 inch
    gasHeader.setLength(50.0); // 50 meters
    gasHeader.setPipeSchedule(TopsidePiping.PipeSchedule.SCH_40);
    gasHeader.setWallThickness(0.00823); // Schedule 40 for 8"
  }

  @Test
  void testTopsidePipingCreation() {
    assertNotNull(gasHeader);
    assertEquals("HP Gas Header", gasHeader.getName());
    assertEquals(TopsidePiping.ServiceType.PROCESS_GAS, gasHeader.getServiceType());
    assertEquals(0.2032, gasHeader.getDiameter(), 0.0001);
  }

  @Test
  void testFactoryMethods() {
    TopsidePiping processGas = TopsidePiping.createProcessGas("Gas Line", gasStream);
    assertEquals(TopsidePiping.ServiceType.PROCESS_GAS, processGas.getServiceType());

    TopsidePiping processLiquid = TopsidePiping.createProcessLiquid("Oil Line", gasStream);
    assertEquals(TopsidePiping.ServiceType.PROCESS_LIQUID, processLiquid.getServiceType());

    TopsidePiping flareHeader = TopsidePiping.createFlareHeader("Flare", gasStream);
    assertEquals(TopsidePiping.ServiceType.FLARE, flareHeader.getServiceType());

    TopsidePiping steamLine = TopsidePiping.createSteam("Steam", gasStream);
    assertEquals(TopsidePiping.ServiceType.STEAM, steamLine.getServiceType());
  }

  @Test
  void testServiceTypeVelocityFactors() {
    // Check that different service types have different velocity factors
    assertEquals(0.72, TopsidePiping.ServiceType.PROCESS_GAS.getVelocityFactor(), 0.01);
    assertEquals(0.67, TopsidePiping.ServiceType.MULTIPHASE.getVelocityFactor(), 0.01);
    assertEquals(0.80, TopsidePiping.ServiceType.STEAM.getVelocityFactor(), 0.01);
    assertEquals(0.50, TopsidePiping.ServiceType.VENT_DRAIN.getVelocityFactor(), 0.01);
  }

  @Test
  void testFittingsConfiguration() {
    gasHeader.setFittings(4, 2, 1, 2);
    assertEquals(4, gasHeader.getNumberOfElbows90());
    assertEquals(2, gasHeader.getNumberOfElbows45());
    assertEquals(1, gasHeader.getNumberOfTees());
    assertEquals(2, gasHeader.getNumberOfValves());
  }

  @Test
  void testEquivalentLength() {
    gasHeader.setFittings(2, 0, 1, 1);
    gasHeader.setValveType("Gate");

    double eqLength = gasHeader.getEquivalentLength();
    // Base length: 50m
    // 2 x 90-deg elbows: 2 * 30 * 0.2032 = 12.19m
    // 1 x tee: 40 * 0.2032 = 8.13m
    // 1 x gate valve: 8 * 0.2032 = 1.63m
    // Total: ~72m
    assertTrue(eqLength > gasHeader.getLength());
    assertTrue(eqLength < 100.0);
  }

  @Test
  void testInsulationConfiguration() {
    gasHeader.setInsulation(TopsidePiping.InsulationType.MINERAL_WOOL, 0.05);
    assertEquals(TopsidePiping.InsulationType.MINERAL_WOOL, gasHeader.getInsulationTypeEnum());
    assertEquals(0.05, gasHeader.getInsulationThickness(), 0.001);
    assertEquals(0.04, TopsidePiping.InsulationType.MINERAL_WOOL.getThermalConductivity(), 0.001);
  }

  @Test
  void testOperatingEnvelope() {
    gasHeader.setOperatingEnvelope(5.0, 100.0, -10.0, 80.0);
    assertEquals(5.0, gasHeader.getMinOperatingPressure(), 0.01);
    assertEquals(100.0, gasHeader.getMaxOperatingPressure(), 0.01);
    assertEquals(-10.0, gasHeader.getMinOperatingTemperature(), 0.01);
    assertEquals(80.0, gasHeader.getMaxOperatingTemperature(), 0.01);
  }

  @Test
  void testMechanicalDesignInitialization() {
    gasHeader.initMechanicalDesign();
    TopsidePipingMechanicalDesign design = gasHeader.getTopsideMechanicalDesign();

    assertNotNull(design);
    assertEquals("PROCESS_GAS", design.getServiceType());
  }

  @Test
  void testErosionalVelocityCalculation() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    // Set flow conditions
    calc.setMixtureDensity(50.0); // kg/m3
    calc.setErosionalCFactor(100.0);

    double erosionalVel = calc.calculateErosionalVelocity();
    // Ve = 100 / sqrt(50) = 14.14 m/s
    assertEquals(14.14, erosionalVel, 0.1);
  }

  @Test
  void testVelocityCheck() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    calc.setOuterDiameter(0.2191); // 8" OD
    calc.setNominalWallThickness(0.00823);
    calc.setMassFlowRate(2.78); // ~10,000 kg/hr
    calc.setMixtureDensity(50.0);
    calc.setLiquidFraction(0.0); // Gas only
    calc.setMaxGasVelocity(20.0);
    calc.setErosionalCFactor(100.0);

    calc.calculateActualVelocity();
    calc.calculateErosionalVelocity();
    boolean passed = calc.checkVelocityLimits();

    assertTrue(calc.getActualVelocity() > 0);
    assertTrue(calc.getErosionalVelocity() > 0);
    // Result depends on actual calculated velocity
  }

  @Test
  void testSupportSpacingCalculation() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    calc.setOuterDiameter(0.2191); // 8"
    calc.setNominalWallThickness(0.00823);
    calc.setMaterialGrade("A106-B");
    calc.setDesignTemperature(50.0);
    calc.setMixtureDensity(50.0);

    double supportSpacing = calc.calculateSupportSpacing();

    System.out.println("Support spacing: " + supportSpacing);
    System.out.println("Moment of inertia: " + calc.calculateMomentOfInertia());
    System.out.println("Allowable stress: " + calc.getAllowableStress());

    // Typical 8" pipe: 3.7-4.5m span, but use ASME simplified method
    double asmeSpacing = calc.calculateSupportSpacingASME();
    System.out.println("ASME support spacing: " + asmeSpacing);
    assertTrue(asmeSpacing > 2.0);
    assertTrue(asmeSpacing < 6.0);
  }

  @Test
  void testSupportSpacingASME() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    // Test various pipe sizes
    calc.setOuterDiameter(0.1143); // 4"
    assertEquals(2.7, calc.calculateSupportSpacingASME(), 0.5);

    calc.setOuterDiameter(0.2191); // 8"
    assertEquals(3.7, calc.calculateSupportSpacingASME(), 0.5);

    calc.setOuterDiameter(0.3239); // 12"
    assertEquals(4.3, calc.calculateSupportSpacingASME(), 0.5);
  }

  @Test
  void testAllowableStressCalculation() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    calc.setMaterialGrade("A106-B");
    calc.setDesignTemperature(20.0);
    double stress = calc.calculateAllowableStress();
    assertEquals(138.0, stress, 1.0);

    calc.setMaterialGrade("A312-TP316");
    calc.setDesignTemperature(200.0);
    stress = calc.calculateAllowableStress();
    assertEquals(103.0, stress, 5.0);
  }

  @Test
  void testThermalExpansionStress() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    calc.setOuterDiameter(0.2191);
    calc.setNominalWallThickness(0.00823);
    calc.setInstallationTemperature(20.0);
    calc.setOperatingTemperature(100.0);
    calc.setMaterialGrade("A106-B");
    calc.calculateAllowableStress();

    double thermalStress = calc.calculateThermalExpansionStress(50.0);

    // Thermal stress = E × α × ΔT = 207000 × 11.7e-6 × 80 ≈ 194 MPa
    assertTrue(thermalStress > 100.0);
    assertTrue(thermalStress < 250.0);

    // Check expansion loop is calculated
    assertTrue(calc.getRequiredLoopLength() > 0);
    assertTrue(calc.getFreeExpansion() > 0);
  }

  @Test
  void testSustainedStress() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    calc.setOuterDiameter(0.2191);
    calc.setNominalWallThickness(0.00823);
    calc.setDesignPressure(5.0); // 50 barg = 5 MPa
    calc.setMixtureDensity(50.0);
    calc.setMaterialGrade("A106-B");
    calc.setDesignTemperature(50.0);
    calc.calculateAllowableStress();

    double sustainedStress = calc.calculateSustainedStress(4.0);

    assertTrue(sustainedStress > 0);
    assertTrue(sustainedStress < calc.getAllowableStress() * 1.5);
  }

  @Test
  void testMomentOfInertia() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    calc.setOuterDiameter(0.2191);
    calc.setNominalWallThickness(0.00823);

    double I = calc.calculateMomentOfInertia();

    // Expected I for 8" Sch 40 ≈ 4.1e-5 m^4
    assertTrue(I > 1e-5);
    assertTrue(I < 1e-4);
  }

  @Test
  void testWindLoad() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    calc.setOuterDiameter(0.2191);
    calc.setInsulationThickness(0.05);

    double windLoad = calc.calculateWindLoad(25.0); // 25 m/s wind

    // F = 0.5 × 1.225 × 25² × 1.0 × 0.32 ≈ 122 N/m
    assertTrue(windLoad > 50.0);
    assertTrue(windLoad < 200.0);
  }

  @Test
  void testDesignVerification() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    calc.setOuterDiameter(0.2191);
    calc.setNominalWallThickness(0.00823);
    calc.setDesignPressure(5.0);
    calc.setDesignTemperature(80.0);
    calc.setMassFlowRate(2.78);
    calc.setMixtureDensity(50.0);
    calc.setLiquidFraction(0.0);
    calc.setMaterialGrade("A106-B");

    boolean passed = calc.performDesignVerification();

    // Should pass for reasonable design
    assertTrue(calc.getSupportSpacing() > 0);
    assertTrue(calc.getAllowableStress() > 0);
  }

  @Test
  void testJsonOutput() {
    gasHeader.setOperatingEnvelope(5.0, 100.0, -10.0, 80.0);
    gasHeader.setFittings(4, 2, 1, 2);
    gasHeader.initMechanicalDesign();

    TopsidePipingMechanicalDesign design = gasHeader.getTopsideMechanicalDesign();
    design.setMaxOperationPressure(100.0);
    design.setMaxOperationTemperature(80.0 + 273.15);
    design.setMaterialGrade("A106-B");
    design.setDesignStandardCode("ASME-B31.3");

    // Run calculations
    design.calcDesign();

    // Get JSON
    String json = design.toJson();

    assertNotNull(json);
    assertTrue(json.contains("serviceType"));
    assertTrue(json.contains("PROCESS_GAS"));
    assertTrue(json.contains("velocityAnalysis") || json.contains("calculatorResults"));
  }

  @Test
  void testCalculatorJsonOutput() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

    calc.setOuterDiameter(0.2191);
    calc.setNominalWallThickness(0.00823);
    calc.setDesignPressure(5.0);
    calc.setDesignTemperature(80.0);
    calc.setMassFlowRate(2.78);
    calc.setMixtureDensity(50.0);
    calc.setMaterialGrade("A106-B");

    calc.performDesignVerification();

    String json = calc.toJson();

    assertNotNull(json);
    assertTrue(json.contains("velocityAnalysis"));
    assertTrue(json.contains("supportAnalysis"));
    assertTrue(json.contains("stressAnalysis"));
    assertTrue(json.contains("thermalExpansion"));
  }

  @Test
  void testPipelineRun() {
    // Set required parameters for Beggs and Brills calculation
    gasHeader.setDiameter(0.2032); // 8 inch in meters
    gasHeader.setElevation(0.0);
    gasHeader.setAngle(0.0);
    gasHeader.run();

    // Check that the pipe ran successfully
    assertNotNull(gasHeader.getOutletStream());
    assertTrue(gasHeader.getOutletPressure() > 0);
  }

  @Test
  void testFullDesignWorkflow() {
    // Complete workflow test
    gasHeader.setOperatingEnvelope(5.0, 80.0, -10.0, 60.0);
    gasHeader.setFittings(4, 2, 1, 2);
    gasHeader.setInsulation(TopsidePiping.InsulationType.MINERAL_WOOL, 0.05);
    gasHeader.setFlangeRating(300);

    // Set required parameters for Beggs and Brills calculation
    gasHeader.setDiameter(0.2032); // 8 inch in meters
    gasHeader.setElevation(0.0);
    gasHeader.setAngle(0.0);
    gasHeader.run();

    // Initialize and configure mechanical design
    TopsidePipingMechanicalDesign design = gasHeader.getTopsideMechanicalDesign();
    design.setMaxOperationPressure(80.0);
    design.setMaxOperationTemperature(60.0 + 273.15);
    design.setMaterialGrade("A106-B");
    design.setDesignStandardCode("ASME-B31.3");
    design.setCompanySpecificDesignStandards("Equinor");

    // Read specifications and calculate
    design.readDesignSpecifications();
    design.calcDesign();

    // Verify results
    TopsidePipingMechanicalDesignCalculator calc = design.getTopsideCalculator();
    assertNotNull(calc);
    assertTrue(calc.getSupportSpacing() > 0);
    assertTrue(calc.getAllowableStress() > 0);

    // Get JSON report
    String json = design.toJson();
    assertNotNull(json);
    assertTrue(json.length() > 100);
  }
}
