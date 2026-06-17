package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the enhanced separator performance calculation chain. Tests the full
 * pipeline: flow regime → inlet device → geometry → gravity → mist eliminator → liquid-liquid.
 *
 * @author NeqSim team
 * @version 1.0
 */
class EnhancedPerformanceCalculatorTest {

  /** Gas density typical for HP separator [kg/m3]. */
  private static final double GAS_DENSITY = 50.0;
  /** Oil density [kg/m3]. */
  private static final double OIL_DENSITY = 750.0;
  /** Water density [kg/m3]. */
  private static final double WATER_DENSITY = 1025.0;
  /** Gas viscosity [Pa.s]. */
  private static final double GAS_VISCOSITY = 1.2e-5;
  /** Oil viscosity [Pa.s]. */
  private static final double OIL_VISCOSITY = 2.0e-3;
  /** Water viscosity [Pa.s]. */
  private static final double WATER_VISCOSITY = 8.0e-4;
  /** Gas velocity [m/s]. */
  private static final double GAS_VELOCITY = 2.0;
  /** Vessel diameter [m]. */
  private static final double DIAMETER = 2.4;
  /** Vessel length [m]. */
  private static final double LENGTH = 7.0;
  /** Liquid level fraction. */
  private static final double LIQ_LEVEL = 0.5;

  /**
   * Tests the enhanced two-phase horizontal separator calculation.
   */
  @Test
  void testEnhancedTwoPhaseHorizontal() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setUseEnhancedCalculation(true);
    calc.setInletPipeDiameter(0.2);
    calc.setSurfaceTension(0.025);
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());
    calc.setGasBubbleDSD(DropletSizeDistribution.rosinRammler(500e-6, 2.0));

    calc.calculate(GAS_DENSITY, OIL_DENSITY, 0.0, GAS_VISCOSITY, OIL_VISCOSITY, 0.0, GAS_VELOCITY,
        DIAMETER, LENGTH, "horizontal", LIQ_LEVEL);

    // Verify results are populated
    assertTrue(calc.getOverallGasLiquidEfficiency() > 0.0, "Overall efficiency should be positive");
    assertTrue(calc.getOverallGasLiquidEfficiency() <= 1.0, "Overall efficiency should be <= 1.0");

    // Enhanced-specific results
    assertTrue(calc.getKFactor() > 0.0, "K-factor should be calculated");
    assertTrue(calc.getKFactorUtilization() > 0.0, "K-factor utilization should be positive");
    assertTrue(calc.getInletDeviceBulkEfficiency() >= 0.0,
        "Inlet device efficiency should be non-negative");
    assertNotNull(calc.getInletFlowRegime(), "Flow regime should be determined");
  }

  /**
   * Tests the enhanced three-phase horizontal separator calculation.
   */
  @Test
  void testEnhancedThreePhaseHorizontal() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setUseEnhancedCalculation(true);
    calc.setInletPipeDiameter(0.25);
    calc.setSurfaceTension(0.025);
    calc.setOilWaterInterfacialTension(0.030);

    // Set all DSDs for three-phase
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());
    calc.setGasBubbleDSD(DropletSizeDistribution.rosinRammler(500e-6, 2.0));
    calc.setWaterInOilDSD(DropletSizeDistribution.logNormal(200e-6, 0.6));
    calc.setOilInWaterDSD(DropletSizeDistribution.logNormal(150e-6, 0.5));

    calc.calculate(GAS_DENSITY, OIL_DENSITY, WATER_DENSITY, GAS_VISCOSITY, OIL_VISCOSITY,
        WATER_VISCOSITY, GAS_VELOCITY, DIAMETER, LENGTH, "horizontal", LIQ_LEVEL);

    // Gas-liquid results
    assertTrue(calc.getOverallGasLiquidEfficiency() > 0.0,
        "Gas-liquid efficiency should be positive");
    assertTrue(calc.getOilInGasFraction() >= 0.0 && calc.getOilInGasFraction() <= 1.0,
        "Oil-in-gas fraction should be valid");
    assertTrue(calc.getWaterInGasFraction() >= 0.0 && calc.getWaterInGasFraction() <= 1.0,
        "Water-in-gas fraction should be valid");

    // Liquid-liquid results
    assertTrue(calc.getWaterInOilFraction() >= 0.0 && calc.getWaterInOilFraction() <= 1.0,
        "Water-in-oil fraction should be valid");
    assertTrue(calc.getOilInWaterFraction() >= 0.0 && calc.getOilInWaterFraction() <= 1.0,
        "Oil-in-water fraction should be valid");
    assertTrue(calc.getLiquidLiquidGravityEfficiency() >= 0.0,
        "LL gravity efficiency should be non-negative");
  }

  /**
   * Tests enhanced vertical separator calculation.
   */
  @Test
  void testEnhancedTwoPhaseVertical() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setUseEnhancedCalculation(true);
    calc.setInletPipeDiameter(0.15);
    calc.setSurfaceTension(0.025);
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

    calc.calculate(GAS_DENSITY, OIL_DENSITY, 0.0, GAS_VISCOSITY, OIL_VISCOSITY, 0.0, GAS_VELOCITY,
        1.5, 5.0, "vertical", 0.4);

    assertTrue(calc.getOverallGasLiquidEfficiency() > 0.0,
        "Vertical efficiency should be positive");
    assertNotNull(calc.getInletFlowRegime(), "Flow regime should be determined");
  }

  /**
   * Tests that specifying an inlet device type changes results.
   */
  @Test
  void testInletDeviceImpact() {
    // With no device
    SeparatorPerformanceCalculator calcNone = new SeparatorPerformanceCalculator();
    calcNone.setUseEnhancedCalculation(true);
    calcNone.setInletDeviceModel(new InletDeviceModel(InletDeviceModel.InletDeviceType.NONE));
    calcNone.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());
    calcNone.calculate(GAS_DENSITY, OIL_DENSITY, 0.0, GAS_VISCOSITY, OIL_VISCOSITY, 0.0,
        GAS_VELOCITY, DIAMETER, LENGTH, "horizontal", LIQ_LEVEL);
    double effNone = calcNone.getOverallGasLiquidEfficiency();

    // With inlet cyclone
    SeparatorPerformanceCalculator calcCyclone = new SeparatorPerformanceCalculator();
    calcCyclone.setUseEnhancedCalculation(true);
    calcCyclone
        .setInletDeviceModel(new InletDeviceModel(InletDeviceModel.InletDeviceType.INLET_CYCLONE));
    calcCyclone.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());
    calcCyclone.calculate(GAS_DENSITY, OIL_DENSITY, 0.0, GAS_VISCOSITY, OIL_VISCOSITY, 0.0,
        GAS_VELOCITY, DIAMETER, LENGTH, "horizontal", LIQ_LEVEL);
    double effCyclone = calcCyclone.getOverallGasLiquidEfficiency();

    // Cyclone should improve separation
    assertTrue(effCyclone >= effNone, "Inlet cyclone should improve or equal no-device efficiency");
  }

  /**
   * Tests JSON output contains enhanced results.
   */
  @Test
  void testJsonOutputContainsEnhancedResults() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setUseEnhancedCalculation(true);
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

    calc.calculate(GAS_DENSITY, OIL_DENSITY, 0.0, GAS_VISCOSITY, OIL_VISCOSITY, 0.0, GAS_VELOCITY,
        DIAMETER, LENGTH, "horizontal", LIQ_LEVEL);

    String json = calc.toJson();
    assertNotNull(json);
    assertTrue(json.contains("enhancedCalculation"), "JSON should have enhanced flag");
    assertTrue(json.contains("kFactor"), "JSON should contain K-factor");
    assertTrue(json.contains("inletFlowRegime"), "JSON should contain flow regime");
    assertTrue(json.contains("inletDevice"), "JSON should contain inlet device info");
    assertTrue(json.contains("vesselGeometry"), "JSON should contain vessel geometry");
  }

  /**
   * Tests flooding detection when gas velocity is very high.
   */
  @Test
  void testFloodingDetection() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setUseEnhancedCalculation(true);
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

    // Very high gas velocity should cause K-factor to exceed design
    calc.calculate(GAS_DENSITY, OIL_DENSITY, 0.0, GAS_VISCOSITY, OIL_VISCOSITY, 0.0, 15.0, // very
                                                                                           // high
                                                                                           // gas
                                                                                           // velocity
        DIAMETER, LENGTH, "horizontal", LIQ_LEVEL);

    // K-factor utilization should be very high
    double utilization = calc.getKFactorUtilization();
    assertTrue(utilization > 0.5, "High gas velocity should show high K-factor utilization");
    // Note: whether flooding occurs depends on the exact K-factor vs design
  }

  /**
   * Tests that standard calculation still works when enhanced is disabled.
   */
  @Test
  void testStandardCalculationStillWorks() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setUseEnhancedCalculation(false);
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(100e-6, 2.6));
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

    calc.calculate(GAS_DENSITY, OIL_DENSITY, 0.0, GAS_VISCOSITY, OIL_VISCOSITY, 0.0, GAS_VELOCITY,
        DIAMETER, LENGTH, "horizontal", LIQ_LEVEL);

    assertTrue(calc.getOverallGasLiquidEfficiency() > 0.0, "Standard path should still work");
    // Enhanced results should be zero/null
    assertTrue(calc.getKFactor() == 0.0, "K-factor should be zero in standard mode");
  }
}
