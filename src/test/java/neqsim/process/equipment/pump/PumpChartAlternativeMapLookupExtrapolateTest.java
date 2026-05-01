package neqsim.process.equipment.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for PumpChartAlternativeMapLookupExtrapolate covering BEP, specific speed, NPSH,
 * density/viscosity corrections, and operating status.
 */
class PumpChartAlternativeMapLookupExtrapolateTest {
  /** Shared chart instance loaded with typical pump curves. */
  private static PumpChartAlternativeMapLookupExtrapolate chart;
  private static double[] speed;
  private static double[][] flow;
  private static double[][] head;
  private static double[][] polyEff;

  @BeforeAll
  static void setUp() {
    chart = new PumpChartAlternativeMapLookupExtrapolate();
    // Typical pump curve data (3 speed curves)
    speed = new double[] {3000, 2700, 2400};
    flow = new double[][] {{100, 200, 300, 400, 500}, {90, 180, 270, 360, 450},
        {80, 160, 240, 320, 400}};
    head = new double[][] {{80, 75, 65, 50, 30}, {65, 60, 52, 40, 22}, {50, 47, 40, 30, 16}};
    polyEff = new double[][] {{60, 75, 82, 78, 55}, {58, 73, 80, 76, 53}, {55, 70, 78, 74, 50}};

    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
    chart.setCurves(chartConditions, speed, flow, head, polyEff);
  }

  @Test
  void testBestEfficiencyFlowRate() {
    double bep = chart.getBestEfficiencyFlowRate();
    // The BEP should be near 300 (peak of first speed curve at 82% efficiency)
    assertTrue(bep > 200 && bep < 400, "BEP flow should be near 300 m3/h, got " + bep);
  }

  @Test
  void testBestEfficiencyConsistent() {
    double bep1 = chart.getBestEfficiencyFlowRate();
    double bep2 = chart.getBestEfficiencyFlowRate();
    assertEquals(bep1, bep2, 1e-6, "BEP should be cached consistently");
  }

  @Test
  void testSpecificSpeed() {
    double ns = chart.getSpecificSpeed();
    assertTrue(ns > 0, "Specific speed should be positive, got " + ns);
    // Ns = N * sqrt(Q_m3s) / H_m^0.75 — with N=3000, Q~300m3/h~0.083m3/s, H~65m
    // Ns ~ 3000 * sqrt(0.083) / 65^0.75 ~ 3000 * 0.288 / 22.6 ~ 38
    assertTrue(ns > 10 && ns < 200, "Specific speed should be in reasonable range, got " + ns);
  }

  @Test
  void testSpecificSpeedUnitHandling() {
    // Create a chart with kJ/kg head unit
    PumpChartAlternativeMapLookupExtrapolate kjChart =
        new PumpChartAlternativeMapLookupExtrapolate();
    kjChart.setHeadUnit("kJ/kg");

    // Convert head from meters to kJ/kg: head_kJ = head_m * g / 1000
    double[][] headKj = new double[head.length][];
    for (int i = 0; i < head.length; i++) {
      headKj[i] = new double[head[i].length];
      for (int j = 0; j < head[i].length; j++) {
        headKj[i][j] = head[i][j] * 9.80665 / 1000.0;
      }
    }
    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
    kjChart.setCurves(chartConditions, speed, flow, headKj, polyEff);
    double ns = kjChart.getSpecificSpeed();
    assertTrue(ns > 0, "Specific speed should be positive for kJ/kg chart");
  }

  @Test
  void testOperatingStatusNormal() {
    String status = chart.getOperatingStatus(250, 3000);
    assertTrue("NORMAL".equals(status) || "OPTIMAL".equals(status),
        "Status at mid-flow should be NORMAL or OPTIMAL, got " + status);
  }

  @Test
  void testOperatingStatusSurge() {
    // Flow far below minimum (100 * 0.9 = 90)
    String status = chart.getOperatingStatus(10, 3000);
    assertEquals("SURGE", status, "Very low flow should report SURGE");
  }

  @Test
  void testOperatingStatusStonewall() {
    // Flow far above maximum (500 * 1.1 = 550)
    String status = chart.getOperatingStatus(700, 3000);
    assertEquals("STONEWALL", status, "Very high flow should report STONEWALL");
  }

  @Test
  void testNPSHCurveNotAvailableByDefault() {
    assertFalse(chart.hasNPSHCurve(), "NPSH should not be available before setting");
    assertEquals(0.0, chart.getNPSHRequired(300, 3000), 1e-6);
  }

  @Test
  void testNPSHCurveSetAndQuery() {
    PumpChartAlternativeMapLookupExtrapolate npshChart =
        new PumpChartAlternativeMapLookupExtrapolate();
    double[] chartConds = new double[] {0.3, 1.0, 1.0, 1.0};
    npshChart.setCurves(chartConds, speed, flow, head, polyEff);

    // NPSH typically increases with flow
    double[][] npshReq = new double[][] {{3.0, 3.5, 4.5, 6.0, 9.0}, {2.4, 2.8, 3.6, 4.8, 7.2},
        {1.9, 2.2, 2.8, 3.8, 5.7}};

    npshChart.setNPSHCurve(npshReq);
    assertTrue(npshChart.hasNPSHCurve(), "NPSH should be available after setting");

    double npsh = npshChart.getNPSHRequired(300, 3000);
    assertTrue(npsh > 0, "NPSH required should be positive, got " + npsh);
  }

  @Test
  void testDensityCorrectionDefault() {
    assertFalse(chart.hasDensityCorrection(), "Density correction should be off by default");
    assertEquals(-1.0, chart.getReferenceDensity(), 1e-6);
  }

  @Test
  void testDensityCorrectionEnabled() {
    PumpChartAlternativeMapLookupExtrapolate dcChart =
        new PumpChartAlternativeMapLookupExtrapolate();
    double[] chartConds = new double[] {0.3, 1.0, 1.0, 1.0};
    dcChart.setCurves(chartConds, speed, flow, head, polyEff);
    dcChart.setReferenceDensity(998.0);

    assertTrue(dcChart.hasDensityCorrection());

    double baseHead = dcChart.getHead(300, 3000);
    // Lighter fluid → more head
    double corrHead = dcChart.getCorrectedHead(300, 3000, 900.0);
    assertTrue(corrHead > baseHead, "Lighter fluid (900 kg/m3 vs ref 998) should give more head");

    // Heavier fluid → less head
    double corrHead2 = dcChart.getCorrectedHead(300, 3000, 1100.0);
    assertTrue(corrHead2 < baseHead, "Heavier fluid (1100 vs 998) should give less head");
  }

  @Test
  void testViscosityCorrectionLowViscosity() {
    PumpChartAlternativeMapLookupExtrapolate vcChart =
        new PumpChartAlternativeMapLookupExtrapolate();
    double[] chartConds = new double[] {0.3, 1.0, 1.0, 1.0};
    vcChart.setCurves(chartConds, speed, flow, head, polyEff);

    // Viscosity <= 1 cSt — no correction (water-like)
    vcChart.calculateViscosityCorrection(0.8, 300, 65, 3000);
    assertEquals(1.0, vcChart.getFlowCorrectionFactor(), 1e-6, "No correction for low viscosity");
    assertEquals(1.0, vcChart.getHeadCorrectionFactor(), 1e-6);
    assertEquals(1.0, vcChart.getEfficiencyCorrectionFactor(), 1e-6);
  }

  @Test
  void testViscosityCorrectionHighViscosity() {
    PumpChartAlternativeMapLookupExtrapolate vcChart =
        new PumpChartAlternativeMapLookupExtrapolate();
    double[] chartConds = new double[] {0.3, 1.0, 1.0, 1.0};
    vcChart.setCurves(chartConds, speed, flow, head, polyEff);
    vcChart.setUseViscosityCorrection(true);

    // 100 cSt oil — should get significant correction
    vcChart.calculateViscosityCorrection(100.0, 300, 65, 3000);
    assertTrue(vcChart.getFlowCorrectionFactor() < 1.0,
        "Flow correction should reduce for high viscosity");
    assertTrue(vcChart.getHeadCorrectionFactor() < 1.0,
        "Head correction should reduce for high viscosity");
    assertTrue(vcChart.getEfficiencyCorrectionFactor() < 1.0,
        "Efficiency correction should reduce for high viscosity");
  }

  @Test
  void testFullyCorrectedHead() {
    PumpChartAlternativeMapLookupExtrapolate fcChart =
        new PumpChartAlternativeMapLookupExtrapolate();
    double[] chartConds = new double[] {0.3, 1.0, 1.0, 1.0};
    fcChart.setCurves(chartConds, speed, flow, head, polyEff);
    fcChart.setReferenceDensity(998.0);
    fcChart.setUseViscosityCorrection(true);

    double baseHead = fcChart.getHead(300, 3000);

    // With light fluid and moderate viscosity
    double corrHead = fcChart.getFullyCorrectedHead(300, 3000, 900.0, 30.0);
    // Should differ from base (both corrections active)
    assertTrue(Math.abs(corrHead - baseHead) > 0.1,
        "Fully corrected head should differ from base head");
  }

  @Test
  void testCorrectedEfficiency() {
    PumpChartAlternativeMapLookupExtrapolate ceChart =
        new PumpChartAlternativeMapLookupExtrapolate();
    double[] chartConds = new double[] {0.3, 1.0, 1.0, 1.0};
    ceChart.setCurves(chartConds, speed, flow, head, polyEff);
    ceChart.setUseViscosityCorrection(true);

    double baseEff = ceChart.getEfficiency(300, 3000);
    // At low viscosity — no change
    double corrEff1 = ceChart.getCorrectedEfficiency(300, 3000, 0.5);
    assertEquals(baseEff, corrEff1, 1e-6, "No correction at low viscosity");

    // At high viscosity — reduced
    ceChart.calculateViscosityCorrection(50.0, 300, 65, 3000);
    double corrEff2 = ceChart.getCorrectedEfficiency(300, 3000, 50.0);
    assertTrue(corrEff2 < baseEff, "High viscosity should reduce efficiency");
  }

  @Test
  void testIsUsePumpChart() {
    PumpChartAlternativeMapLookupExtrapolate pc = new PumpChartAlternativeMapLookupExtrapolate();
    assertFalse(pc.isUsePumpChart());
    pc.setUsePumpChart(true);
    assertTrue(pc.isUsePumpChart());
  }

  @Test
  void testReferenceViscosity() {
    PumpChartAlternativeMapLookupExtrapolate pc = new PumpChartAlternativeMapLookupExtrapolate();
    assertEquals(-1.0, pc.getReferenceViscosity(), 1e-6);
    pc.setReferenceViscosity(5.0);
    assertEquals(5.0, pc.getReferenceViscosity(), 1e-6);
  }
}
