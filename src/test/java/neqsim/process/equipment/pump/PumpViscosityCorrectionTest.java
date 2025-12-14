package neqsim.process.equipment.pump;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for viscosity correction feature in PumpChart.
 * 
 * <p>
 * Tests the Hydraulic Institute (HI) method for correcting pump performance when pumping viscous
 * fluids.
 * </p>
 */
class PumpViscosityCorrectionTest {

  private PumpChart chart;
  private double[] speed;
  private double[][] flow;
  private double[][] head;
  private double[][] efficiency;
  private double[] chartConditions;

  @BeforeEach
  void setUp() {
    chart = new PumpChart();

    // Set up a typical pump curve (tested with water at 998 kg/m³)
    speed = new double[] {1000.0, 1500.0};
    flow = new double[][] {{10, 20, 30, 40, 50}, {15, 30, 45, 60, 75}};
    head = new double[][] {{100, 95, 88, 78, 65}, {225, 214, 198, 175, 146}};
    efficiency = new double[][] {{60, 75, 82, 78, 68}, {62, 77, 84, 80, 70}};
    chartConditions = new double[] {18.0, 298.15, 1.0, 1.0, 998.0}; // MW, T, P, Z, density

    chart.setCurves(chartConditions, speed, flow, head, efficiency);
    chart.setHeadUnit("meter");
  }

  @Test
  void testViscosityCorrectionDefaults() {
    // By default, viscosity correction should be disabled
    assertFalse(chart.isUseViscosityCorrection());
    assertEquals(1.0, chart.getFlowCorrectionFactor());
    assertEquals(1.0, chart.getHeadCorrectionFactor());
    assertEquals(1.0, chart.getEfficiencyCorrectionFactor());
  }

  @Test
  void testViscosityCorrectionForWater() {
    // Water-like viscosity (1 cSt) should not trigger correction
    chart.setUseViscosityCorrection(true);
    chart.calculateViscosityCorrection(1.0, 30.0, 88.0, 1000.0);

    assertEquals(1.0, chart.getFlowCorrectionFactor(), 0.01);
    assertEquals(1.0, chart.getHeadCorrectionFactor(), 0.01);
    assertEquals(1.0, chart.getEfficiencyCorrectionFactor(), 0.01);
  }

  @Test
  void testViscosityCorrectionForModerateViscosity() {
    // Moderate viscosity (50 cSt) should reduce performance
    chart.setUseViscosityCorrection(true);

    double flowBEP = 30.0; // m³/hr
    double headBEP = 88.0; // meters
    double viscosity = 50.0; // cSt

    chart.calculateViscosityCorrection(viscosity, flowBEP, headBEP, 1000.0);

    // Correction factors should be less than 1.0
    assertTrue(chart.getFlowCorrectionFactor() < 1.0, "Flow correction should reduce capacity");
    assertTrue(chart.getHeadCorrectionFactor() < 1.0, "Head correction should reduce head");
    assertTrue(chart.getEfficiencyCorrectionFactor() < 1.0,
        "Efficiency correction should reduce efficiency");

    // But not too severe for moderate viscosity
    assertTrue(chart.getFlowCorrectionFactor() > 0.8,
        "Flow correction should not be too severe at 50 cSt");
    assertTrue(chart.getEfficiencyCorrectionFactor() > 0.7,
        "Efficiency correction should not be too severe at 50 cSt");
  }

  @Test
  void testViscosityCorrectionForHighViscosity() {
    // High viscosity (500 cSt) should significantly reduce performance
    chart.setUseViscosityCorrection(true);

    double flowBEP = 30.0;
    double headBEP = 88.0;
    double viscosity = 500.0; // Heavy oil

    chart.calculateViscosityCorrection(viscosity, flowBEP, headBEP, 1000.0);

    // Correction factors should be less than 1.0 for high viscosity
    assertTrue(chart.getFlowCorrectionFactor() < 1.0, "Flow should be reduced at 500 cSt");
    assertTrue(chart.getEfficiencyCorrectionFactor() < 1.0,
        "Efficiency should be reduced at 500 cSt");
    // At very high viscosity, expect noticeable degradation
    assertTrue(chart.getFlowCorrectionFactor() <= 0.9,
        "Flow correction at 500 cSt should be <= 0.9");
  }

  @Test
  void testGetViscosityCorrectedHead() {
    chart.setUseViscosityCorrection(true);

    double flowBEP = 30.0;
    double headBEP = 88.0;
    double viscosity = 100.0; // cSt

    chart.calculateViscosityCorrection(viscosity, flowBEP, headBEP, 1000.0);

    double baseHead = chart.getHead(30.0, 1000.0);
    double correctedHead = chart.getViscosityCorrectedHead(baseHead);

    assertTrue(correctedHead < baseHead,
        "Corrected head should be less than base head for viscous fluid");
    assertTrue(correctedHead > 0, "Corrected head should be positive");
  }

  @Test
  void testGetViscosityCorrectedEfficiency() {
    chart.setUseViscosityCorrection(true);

    double flowBEP = 30.0;
    double headBEP = 88.0;
    double viscosity = 100.0; // cSt

    chart.calculateViscosityCorrection(viscosity, flowBEP, headBEP, 1000.0);

    double baseEfficiency = chart.getEfficiency(30.0, 1000.0);
    double correctedEfficiency = chart.getViscosityCorrectedEfficiency(baseEfficiency);

    assertTrue(correctedEfficiency < baseEfficiency,
        "Corrected efficiency should be less than base efficiency");
    assertTrue(correctedEfficiency > 0, "Corrected efficiency should be positive");
  }

  @Test
  void testGetFullyCorrectedHead() {
    chart.setUseViscosityCorrection(true);

    double flowBEP = chart.getBestEfficiencyFlowRate();
    double headBEP = chart.getHead(flowBEP, 1000.0);
    double viscosity = 100.0;
    double actualDensity = 850.0; // Lighter than water

    chart.calculateViscosityCorrection(viscosity, flowBEP, headBEP, 1000.0);

    // Get head with both density and viscosity corrections
    double fullyCorrectHead = chart.getFullyCorrectedHead(30.0, 1000.0, actualDensity, viscosity);
    double densityOnlyHead = chart.getCorrectedHead(30.0, 1000.0, actualDensity);
    double baseHead = chart.getHead(30.0, 1000.0);

    // Density correction increases head (lighter fluid)
    assertTrue(densityOnlyHead > baseHead,
        "Density correction should increase head for lighter fluid");

    // Viscosity reduces head
    // The fully corrected head accounts for both effects
    assertNotEquals(baseHead, fullyCorrectHead,
        "Fully corrected head should differ from base head");
  }

  @Test
  void testGetCorrectedEfficiencyMethod() {
    chart.setUseViscosityCorrection(true);

    double flowBEP = chart.getBestEfficiencyFlowRate();
    double headBEP = chart.getHead(flowBEP, 1000.0);

    chart.calculateViscosityCorrection(100.0, flowBEP, headBEP, 1000.0);

    double correctedEff = chart.getCorrectedEfficiency(30.0, 1000.0, 100.0);
    double baseEff = chart.getEfficiency(30.0, 1000.0);

    assertTrue(correctedEff < baseEff, "Viscosity should reduce efficiency");
  }

  @Test
  void testReferenceViscosityGetterSetter() {
    chart.setReferenceViscosity(1.0);
    assertEquals(1.0, chart.getReferenceViscosity());

    chart.setReferenceViscosity(5.0);
    assertEquals(5.0, chart.getReferenceViscosity());
  }

  @Test
  void testViscosityCorrectionToggle() {
    // Test enable/disable
    assertFalse(chart.isUseViscosityCorrection());

    chart.setUseViscosityCorrection(true);
    assertTrue(chart.isUseViscosityCorrection());

    chart.setUseViscosityCorrection(false);
    assertFalse(chart.isUseViscosityCorrection());
  }

  @Test
  void testCorrectionFactorsAfterCalculation() {
    chart.setUseViscosityCorrection(true);

    // Calculate correction for moderate viscosity
    chart.calculateViscosityCorrection(75.0, 30.0, 88.0, 1000.0);

    double cQ = chart.getFlowCorrectionFactor();
    double cH = chart.getHeadCorrectionFactor();
    double cEta = chart.getEfficiencyCorrectionFactor();

    // All factors should be in valid range
    assertTrue(cQ > 0 && cQ <= 1.0, "Cq should be between 0 and 1");
    assertTrue(cH > 0 && cH <= 1.0, "Ch should be between 0 and 1");
    assertTrue(cEta > 0 && cEta <= 1.0, "Cη should be between 0 and 1");

    // Efficiency degrades fastest
    assertTrue(cEta <= cH, "Efficiency should degrade at least as much as head");
  }

  @Test
  void testNoCorrectionWhenDisabled() {
    // Calculate correction but keep it disabled
    chart.calculateViscosityCorrection(100.0, 30.0, 88.0, 1000.0);
    chart.setUseViscosityCorrection(false);

    double baseHead = 100.0;
    double correctedHead = chart.getViscosityCorrectedHead(baseHead);

    assertEquals(baseHead, correctedHead, "No correction should be applied when disabled");
  }
}
