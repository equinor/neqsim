package neqsim.process.equipment.compressor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.CompressorChartKhader2015.RealCurve;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class CompressorChartKhader2015Test {
  @Test
  void testSetCurves() {
    SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);

    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.addComponent("i-pentane", 0.056);
    testFluid.addComponent("n-pentane", 0.053);
    testFluid.setMixingRule(2);

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(3.0, "MSm3/day");

    Stream stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();
    Compressor comp1 = new Compressor("cmp1", stream_1);
    comp1.setUsePolytropicCalc(true);
    double compspeed = 10000;
    comp1.setSpeed(compspeed);

    // compressor chart conditions: temperature [C], pressure [bara], density
    // [kg/m3], molecular
    // weight [g/mol]
    // Note: Only temperature and pressure are used by CompressorChartKhader2015,
    // but values should
    // be realistic.
    double[] chartConditions = new double[] {25.0, 50.0, 50.0, 20.0};

    double[] speed = new double[] {12913, 12298, 11683, 11098, 10453, 9224, 8609, 8200};
    double[][] flow = new double[][] {
        {2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329, 5661.0331},
        {2571.1753, 2943.7254, 3440.2675, 3837.4448, 4253.0898, 4668.6643, 4997.1926, 5387.4952},
        {2415.3793, 2763.0706, 3141.7095, 3594.7436, 4047.6467, 4494.1889, 4853.7353, 5138.7858},
        {2247.2043, 2799.7342, 3178.3428, 3656.1551, 4102.778, 4394.1591, 4648.3224, 4840.4998},
        {2072.8397, 2463.9483, 2836.4078, 3202.5266, 3599.6333, 3978.0203, 4257.0022, 4517.345},
        {1835.9552, 2208.455, 2618.1322, 2940.8034, 3244.7852, 3530.1279, 3753.3738, 3895.9746},
        {1711.3386, 1965.8848, 2356.9431, 2685.9247, 3008.5154, 3337.2855, 3591.5092},
        {1636.5807, 2002.8708, 2338.0319, 2642.1245, 2896.4894, 3113.6264, 3274.8764, 3411.2977}};
    double[][] head =
        new double[][] {{80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728},
            {72.2122, 71.8369, 68.9009, 65.8341, 60.7167, 54.702, 47.2749, 35.7471},
            {65.1576, 64.5253, 62.6118, 59.1619, 54.0455, 47.0059, 39.195, 31.6387},
            {58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109},
            {52.3295, 51.0573, 49.5283, 46.3326, 42.3685, 37.2502, 31.4884, 25.598},
            {40.6578, 39.6416, 37.6008, 34.6603, 30.9503, 27.1116, 23.2713, 20.4546},
            {35.2705, 34.6359, 32.7228, 31.0645, 27.0985, 22.7482, 18.0113},
            {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403},};
    double[][] polyEff = new double[][] {
        {77.2452238409573, 79.4154186459363, 80.737960012489, 80.5229826589649, 79.2210931638144,
            75.4719133864634, 69.6034181197298, 58.7322388482707},
        {77.0107837113504, 79.3069974136389, 80.8941189021135, 80.7190194665918, 79.5313242980328,
            75.5912622896367, 69.6846136362097, 60.0043057990909},
        {77.0043065299874, 79.1690958847856, 80.8038169975675, 80.6543975614197, 78.8532389102705,
            73.6664774270613, 66.2735600426727, 57.671664571658},
        {77.0716623789093, 80.4629750233093, 81.1390811169072, 79.6374242667478, 75.380928428817,
            69.5332969549779, 63.7997587622339, 58.8120614497758},
        {76.9705872525642, 79.8335492585324, 80.9468133671171, 80.5806471927835, 78.0462158225426,
            73.0403707523258, 66.5572286338589, 59.8624822515064},
        {77.5063036680357, 80.2056198362559, 81.0339108025933, 79.6085962687939, 76.3814534404405,
            70.8027503005902, 64.6437367160571, 60.5299349982342},
        {77.8175271586685, 80.065165942218, 81.0631362122632, 79.8955051771299, 76.1983240929369,
            69.289982774309, 60.8567149372229},
        {78.0924334304045, 80.9353551568667, 80.7904437766234, 78.8639325223295, 75.2170936751143,
            70.3105081673411, 65.5507568533569, 61.0391468300337}};

    CompressorChartKhader2015 compChart = new CompressorChartKhader2015(stream_1, 0.9);
    compChart.setCurves(chartConditions, speed, flow, head, flow, polyEff);
    comp1.setCompressorChart(compChart);
    comp1.getCompressorChart().setHeadUnit("kJ/kg");

    Assertions.assertEquals(2431.46694, stream_1.getFlowRate("m3/hr"), 0.01);
    Assertions.assertEquals(80.326453, comp1.getCompressorChart()
        .getPolytropicEfficiency(stream_1.getFlowRate("m3/hr"), compspeed), 0.01);
    Assertions.assertEquals(41.56192413,
        comp1.getCompressorChart().getPolytropicHead(stream_1.getFlowRate("m3/hr"), compspeed),
        0.01);

    Assertions.assertEquals(0.00256412315,
        compChart.getCorrectedCurves(chartConditions, speed, flow, head, polyEff, polyEff)
            .get(0).correctedFlowFactor[0],
        0.0001);

    Assertions.assertEquals(2986.0877, compChart.getRealCurves().get(0).flow[0], 0.0001);
    Assertions.assertEquals(91.7406053, compChart.getRealCurves().get(0).head[0], 0.0001);

    StoneWallCurve sw = compChart.getStoneWallCurve();
    SurgeCurve sc = compChart.getSurgeCurve();

    testFluid = new SystemSrkEos(298.15, 50.0);

    testFluid.addComponent("methane", 8.35736E-1);
    testFluid.addComponent("ethane", 4.64298E-2);
    testFluid.addComponent("propane", 1.17835E-1);
    testFluid.setMixingRule(2);

    testFluid.setTemperature(25.0, "C");
    testFluid.setPressure(50.0, "bara");
    testFluid.setTotalFlowRate(3.0, "MSm3/day");

    stream_1.setFluid(testFluid);
    stream_1.run();
    // stream_1.getFluid().prettyPrint();

    compChart = new CompressorChartKhader2015(stream_1.getFluid(), 0.9);
    compChart.setCurves(chartConditions, speed, flow, head, flow, polyEff);
    comp1.setCompressorChart(compChart);
    comp1.getCompressorChart().setHeadUnit("kJ/kg");

    Assertions.assertEquals(2244.86217, stream_1.getFlowRate("m3/hr"), 0.01);
    Assertions.assertEquals(79.1115252, comp1.getCompressorChart()
        .getPolytropicEfficiency(stream_1.getFlowRate("m3/hr"), compspeed), 0.01);
    Assertions.assertEquals(45.197307809,
        comp1.getCompressorChart().getPolytropicHead(stream_1.getFlowRate("m3/hr"), compspeed),
        0.01);

    ((CompressorChartKhader2015) comp1.getCompressorChart()).setImpellerOuterDiameter(0.9);
    comp1.getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
    comp1.run();
    Assertions.assertEquals(75.11224727, comp1.getOutletStream().getPressure("bara"), 0.01);
    comp1.getSurgeFlowRate();

    CompressorChartKhader2015 testChart = new CompressorChartKhader2015(stream_1.getFluid(), 0.9);
    testChart.setCurves(chartConditions, speed, flow, head, flow, polyEff);

    sw = testChart.getStoneWallCurve();
    sc = testChart.getSurgeCurve();

    double cs = testChart.getReferenceFluid().getPhase(0).getSoundSpeed();
    double D = testChart.getImpellerOuterDiameter();
    // Assertions.assertEquals(sw.flow.length, sc.flow.length);
  }

  @Test
  void testGetNewCurves() {
    // compressor chart conditions: temperature [C], pressure [bara], density
    // [kg/m3], molecular
    // weight [g/mol]
    // Note: Only temperature and pressure are used by CompressorChartKhader2015,
    // but values should
    // be realistic.
    double[] chartConditions = new double[] {25.0, 50.0, 50.0, 20.0};

    double[] speed = new double[] {12913, 12298, 11683, 11098, 10453, 9224, 8609, 8200};
    double[][] flow = new double[][] {
        {2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329, 5661.0331},
        {2571.1753, 2943.7254, 3440.2675, 3837.4448, 4253.0898, 4668.6643, 4997.1926, 5387.4952},
        {2415.3793, 2763.0706, 3141.7095, 3594.7436, 4047.6467, 4494.1889, 4853.7353, 5138.7858},
        {2247.2043, 2799.7342, 3178.3428, 3656.1551, 4102.778, 4394.1591, 4648.3224, 4840.4998},
        {2072.8397, 2463.9483, 2836.4078, 3202.5266, 3599.6333, 3978.0203, 4257.0022, 4517.345},
        {1835.9552, 2208.455, 2618.1322, 2940.8034, 3244.7852, 3530.1279, 3753.3738, 3895.9746},
        {1711.3386, 1965.8848, 2356.9431, 2685.9247, 3008.5154, 3337.2855, 3591.5092},
        {1636.5807, 2002.8708, 2338.0319, 2642.1245, 2896.4894, 3113.6264, 3274.8764, 3411.2977}};
    double[][] head =
        new double[][] {{80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728},
            {72.2122, 71.8369, 68.9009, 65.8341, 60.7167, 54.702, 47.2749, 35.7471},
            {65.1576, 64.5253, 62.6118, 59.1619, 54.0455, 47.0059, 39.195, 31.6387},
            {58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109},
            {52.3295, 51.0573, 49.5283, 46.3326, 42.3685, 37.2502, 31.4884, 25.598},
            {40.6578, 39.6416, 37.6008, 34.6603, 30.9503, 27.1116, 23.2713, 20.4546},
            {35.2705, 34.6359, 32.7228, 31.0645, 27.0985, 22.7482, 18.0113},
            {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403},};
    double[][] polyEff = new double[][] {
        {77.2452238409573, 79.4154186459363, 80.737960012489, 80.5229826589649, 79.2210931638144,
            75.4719133864634, 69.6034181197298, 58.7322388482707},
        {77.0107837113504, 79.3069974136389, 80.8941189021135, 80.7190194665918, 79.5313242980328,
            75.5912622896367, 69.6846136362097, 60.0043057990909},
        {77.0043065299874, 79.1690958847856, 80.8038169975675, 80.6543975614197, 78.8532389102705,
            73.6664774270613, 66.2735600426727, 57.671664571658},
        {77.0716623789093, 80.4629750233093, 81.1390811169072, 79.6374242667478, 75.380928428817,
            69.5332969549779, 63.7997587622339, 58.8120614497758},
        {76.9705872525642, 79.8335492585324, 80.9468133671171, 80.5806471927835, 78.0462158225426,
            73.0403707523258, 66.5572286338589, 59.8624822515064},
        {77.5063036680357, 80.2056198362559, 81.0339108025933, 79.6085962687939, 76.3814534404405,
            70.8027503005902, 64.6437367160571, 60.5299349982342},
        {77.8175271586685, 80.065165942218, 81.0631362122632, 79.8955051771299, 76.1983240929369,
            69.289982774309, 60.8567149372229},
        {78.0924334304045, 80.9353551568667, 80.7904437766234, 78.8639325223295, 75.2170936751143,
            70.3105081673411, 65.5507568533569, 61.0391468300337}};

    SystemInterface actualFluid = new SystemSrkEos(298.15, 50.0);
    actualFluid.addComponent("nitrogen", 1.205);
    actualFluid.addComponent("CO2", 1.340);
    actualFluid.addComponent("methane", 87.974);
    actualFluid.addComponent("ethane", 5.258);
    actualFluid.addComponent("propane", 3.283);
    actualFluid.addComponent("i-butane", 0.082);
    actualFluid.addComponent("n-butane", 0.487);
    actualFluid.addComponent("i-pentane", 0.056);
    actualFluid.addComponent("n-pentane", 0.053);
    actualFluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(actualFluid);
    ops.TPflash();
    actualFluid.initThermoProperties();

    SystemInterface referenceFluid = new SystemSrkEos(298.15, 50.0);
    referenceFluid.addComponent("nitrogen", 1.205);
    referenceFluid.addComponent("CO2", 1.340);
    referenceFluid.addComponent("methane", 87.974);
    referenceFluid.addComponent("ethane", 5.258);
    referenceFluid.addComponent("propane", 3.283);
    referenceFluid.addComponent("i-butane", 0.082);
    referenceFluid.addComponent("n-butane", 0.487);
    referenceFluid.addComponent("i-pentane", 0.056);
    referenceFluid.addComponent("n-pentane", 0.053);
    referenceFluid.setMixingRule("classic");

    CompressorChartKhader2015 compChart =
        new CompressorChartKhader2015(actualFluid, referenceFluid, 0.9);
    compChart.setCurves(chartConditions, speed, flow, head, flow, polyEff);
    java.util.List<RealCurve> newcomprcurve = compChart.getRealCurves();

    actualFluid = new SystemSrkEos(298.15, 50.0);
    actualFluid.addComponent("nitrogen", 1.205);
    actualFluid.addComponent("CO2", 1.340);
    actualFluid.addComponent("methane", 87.974);
    actualFluid.addComponent("ethane", 5.258);
    actualFluid.setMixingRule("classic");
    ops = new ThermodynamicOperations(actualFluid);
    ops.TPflash();
    actualFluid.initThermoProperties();

    compChart = new CompressorChartKhader2015(actualFluid, 0.9);
    compChart.setReferenceFluid(referenceFluid);
    compChart.setCurves(chartConditions, speed, flow, head, flow, polyEff);
    newcomprcurve = compChart.getRealCurves();

    // Assert that newcomprcurve contains the same values as the input curves
    for (int i = 0; i < newcomprcurve.size(); i++) {
      RealCurve curve = newcomprcurve.get(i);
      Assertions.assertEquals(speed[i], curve.speed, 1e-6);
      for (int j = 0; j < curve.flow.length; j++) {
        Assertions.assertNotEquals(flow[i][j], curve.flow[j], 1e-6,
            "Flow mismatch at curve " + i + ", point " + j);
        Assertions.assertNotEquals(head[i][j], curve.head[j], 1e-6,
            "Head mismatch at curve " + i + ", point " + j);
        Assertions.assertEquals(polyEff[i][j], curve.polytropicEfficiency[j], 1e-6,
            "PolyEff mismatch at curve " + i + ", point " + j);
      }
    }
  }

  @Test
  void testSingleSpeedCurves() {
    SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);

    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.addComponent("i-pentane", 0.056);
    testFluid.addComponent("n-pentane", 0.053);
    testFluid.setMixingRule(2);

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(4.5, "MSm3/day"); // Increased flow to be above surge at 12913 RPM

    Stream stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();
    Compressor comp1 = new Compressor("cmp1", stream_1);
    comp1.setUsePolytropicCalc(true);
    double compspeed = 12913; // IMPORTANT: Match the single speed curve!
    comp1.setSpeed(compspeed);

    // compressor chart conditions: temperature [C], pressure [bara], density
    // [kg/m3], molecular
    // weight [g/mol]
    // Note: Only temperature and pressure are used by CompressorChartKhader2015,
    // but values should
    // be realistic.
    double[] chartConditions = new double[] {25.0, 50.0, 50.0, 20.0};

    double[] speed = new double[] {12913};
    double[][] flow = new double[][] {
        {2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329, 5661.0331}};
    double[][] head = new double[][] {{93.2, 92.54, 91.66, 90.27, 88.18, 84.87, 83.2, 80.61}};
    double[][] polyEff = new double[][] {{77.2452238409573, 79.4154186459363, 80.737960012489,
        80.5229826589649, 79.2210931638144, 75.4719133864634, 69.6034181197298, 58.7322388482707}};

    CompressorChartKhader2015 compChart = new CompressorChartKhader2015(stream_1, 0.9);
    compChart.setCurves(chartConditions, speed, flow, head, flow, polyEff);
    comp1.setCompressorChart(compChart);
    comp1.getCompressorChart().setHeadUnit("kJ/kg");

    // For single speed compressor, surge and stone wall curves should NOT be active
    // as they require at least 2 speed curves to be properly generated
    // The check `if (chartValues.size() > 1)` prevents generation for single speed
    Assertions.assertNotNull(compChart.getSurgeCurve(),
        "Surge curve object should exist (but not active)");
    Assertions.assertFalse(compChart.getSurgeCurve().isActive(),
        "Surge curve should not be active for single speed compressor");
    Assertions.assertNotNull(compChart.getStoneWallCurve(),
        "Stone wall curve object should exist (but not active)");
    Assertions.assertFalse(compChart.getStoneWallCurve().isActive(),
        "Stone wall curve should not be active for single speed compressor");

    // Verify that for a single speed compressor, we only have one RealCurve
    Assertions.assertEquals(1, compChart.getRealCurves().size(),
        "Single speed compressor should have exactly one real curve");

    // For a single speed compressor:
    // - The surge point would be the minimum flow point on the single curve
    // - The stone wall (choke) point would be the maximum flow point on the single curve
    // - Both are SINGLE POINTS, not curves (you need multiple speeds to form a curve)
    RealCurve singleCurve = compChart.getRealCurves().get(0);
    double minFlow = singleCurve.flow[0]; // First point (minimum flow)
    double maxFlow = singleCurve.flow[singleCurve.flow.length - 1]; // Last point (maximum flow)

    System.out.println("Single speed compressor operating envelope:");
    System.out.println("  Speed: " + singleCurve.speed + " RPM");
    System.out.println("  Surge point (min flow): " + minFlow + " m3/hr at head "
        + singleCurve.head[0] + " kJ/kg");
    System.out.println("  Stone wall point (max flow): " + maxFlow + " m3/hr at head "
        + singleCurve.head[singleCurve.head.length - 1] + " kJ/kg");
    System.out.println(
        "  Note: These are SINGLE POINTS, not curves. Multiple speeds are needed for surge/stone wall curves.");

    // Verify the compressor can still operate and calculate head/efficiency
    double testFlow = 3500.0; // m3/hr - middle of the flow range
    double testSpeed = 12913; // Use the single speed available

    double headValue = compChart.getPolytropicHead(testFlow, testSpeed);
    double effValue = compChart.getPolytropicEfficiency(testFlow, testSpeed);

    Assertions.assertTrue(headValue > 0, "Head should be calculated for single speed compressor");
    Assertions.assertTrue(effValue > 0,
        "Efficiency should be calculated for single speed compressor");

    // Test extrapolation below minimum flow (should still work)
    double lowFlow = 2000.0; // m3/hr - below minimum flow
    double headLowFlow = compChart.getPolytropicHead(lowFlow, testSpeed);
    Assertions.assertTrue(headLowFlow > 0, "Head should be extrapolated for flow below minimum");

    // Test extrapolation above maximum flow (should still work)
    double highFlow = 6000.0; // m3/hr - above maximum flow
    double headHighFlow = compChart.getPolytropicHead(highFlow, testSpeed);
    Assertions.assertTrue(headHighFlow > 0, "Head should be extrapolated for flow above maximum");

    // Verify head decreases with increasing flow (typical compressor behavior)
    Assertions.assertTrue(headLowFlow > headValue, "Head should be higher at lower flow");
    Assertions.assertTrue(headValue > headHighFlow, "Head should be lower at higher flow");

    // Demonstrate that the operating envelope is bounded by single points:
    // - Cannot operate below surge point (minFlow at this speed)
    // - Cannot operate above stone wall point (maxFlow at this speed)
    Assertions.assertTrue(minFlow < maxFlow,
        "Surge point flow should be less than stone wall point flow");

    // ===== Test Surge Safety Factor =====
    // The compressor has an AntiSurge object with a safety factor (default 1.05 = 5% margin)
    AntiSurge antiSurge = comp1.getAntiSurge();
    Assertions.assertNotNull(antiSurge, "AntiSurge object should exist");
    Assertions.assertEquals(1.05, antiSurge.getSurgeControlFactor(), 0.001,
        "Default surge control factor should be 1.05 (5% safety margin)");

    // For a single speed compressor, the surge point is the minimum flow on the curve
    // The surge control factor ensures operation at a safe distance from this point
    // Minimum safe flow = minimum curve flow * surgeControlFactor
    double actualSurgeFlow = minFlow; // The actual surge point (minimum flow on curve)
    double safeSurgeFlow = actualSurgeFlow * antiSurge.getSurgeControlFactor();

    System.out.println("\nSurge Safety Margin for Single Speed Compressor:");
    System.out.println("  Actual surge flow (minimum flow point): " + actualSurgeFlow + " m3/hr");
    System.out.println("  Surge control factor: " + antiSurge.getSurgeControlFactor());
    System.out.println("  Safe minimum flow (with 5% margin): " + safeSurgeFlow + " m3/hr");
    System.out.println("  Safety margin: " + (safeSurgeFlow - actualSurgeFlow) + " m3/hr ("
        + ((antiSurge.getSurgeControlFactor() - 1.0) * 100) + "%)");

    // User can modify the safety factor
    antiSurge.setSurgeControlFactor(1.10); // 10% safety margin
    Assertions.assertEquals(1.10, antiSurge.getSurgeControlFactor(), 0.001);
    double safeSurgeFlow10pct = actualSurgeFlow * 1.10;
    System.out.println("\nWith 10% safety margin:");
    System.out.println("  Safe minimum flow: " + safeSurgeFlow10pct + " m3/hr");
    System.out.println("  Safety margin: " + (safeSurgeFlow10pct - actualSurgeFlow) + " m3/hr");

    // For a single speed compressor:
    // - The surge point is fixed at the minimum flow of that speed curve
    // - The safety margin moves the operating limit to the right (higher flow)
    // - This is CRITICAL because you cannot change speed to move away from surge
    // - The AntiSurge system will add flow to keep the compressor above safe limit
    Assertions.assertTrue(safeSurgeFlow > actualSurgeFlow,
        "Safe surge flow should be higher than actual surge flow");
    Assertions.assertTrue(safeSurgeFlow10pct > safeSurgeFlow,
        "Higher safety factor should give more margin");

    System.out.println("\nKey Point for Single Speed Compressors:");
    System.out.println(
        "  Since speed cannot be adjusted, the safety margin is ESSENTIAL to prevent surge.");
    System.out.println("  The AntiSurge system will recirculate/add flow if operation gets too "
        + "close to surge point.");

    // ===== NEW: Using getSurgeFlowAtSpeed() and getSurgeHeadAtSpeed() =====
    // These methods work for BOTH single speed and multi-speed compressors
    // They retrieve the surge point at the current speed directly from the chart
    System.out.println("\n=== Using getSurgeFlowAtSpeed() and getSurgeHeadAtSpeed() ===");
    double currentSpeed = comp1.getSpeed(); // This is now 12913 RPM
    double surgeFlowAtSpeed = compChart.getSurgeFlowAtSpeed(currentSpeed);
    double surgeHeadAtSpeed = compChart.getSurgeHeadAtSpeed(currentSpeed);
    double stoneWallFlowAtSpeed = compChart.getStoneWallFlowAtSpeed(currentSpeed);
    double stoneWallHeadAtSpeed = compChart.getStoneWallHeadAtSpeed(currentSpeed);

    System.out.println("Current compressor speed: " + currentSpeed + " RPM");
    System.out.println("Surge flow at this speed: " + surgeFlowAtSpeed + " m3/hr");
    System.out.println("Surge head at this speed: " + surgeHeadAtSpeed + " kJ/kg");
    System.out.println("Stone wall flow at this speed: " + stoneWallFlowAtSpeed + " m3/hr");
    System.out.println("Stone wall head at this speed: " + stoneWallHeadAtSpeed + " kJ/kg");

    // Verify these match the curve data
    Assertions.assertEquals(minFlow, surgeFlowAtSpeed, 0.01,
        "getSurgeFlowAtSpeed should return minimum flow");
    Assertions.assertEquals(maxFlow, stoneWallFlowAtSpeed, 0.01,
        "getStoneWallFlowAtSpeed should return maximum flow");

    // Calculate safe operating flow with surge control factor
    double safeMinFlowFromMethod = surgeFlowAtSpeed * antiSurge.getSurgeControlFactor();
    System.out.println(
        "\nSafe minimum flow (using method + safety factor): " + safeMinFlowFromMethod + " m3/hr");
    Assertions.assertEquals(safeSurgeFlow10pct, safeMinFlowFromMethod, 0.01,
        "Safe flow calculated from method should match");

    // ===== NEW: Using getSafetyFactorCorrectedFlowHeadAtCurrentSpeed() =====
    // This convenience method returns both safe flow and head in one call
    System.out.println("\n=== Using getSafetyFactorCorrectedFlowHeadAtCurrentSpeed() ===");
    double[] safeFlowHead = comp1.getSafetyFactorCorrectedFlowHeadAtCurrentSpeed();
    System.out.println("Safe operating point at current speed:");
    System.out.println("  Safe flow (with " + ((antiSurge.getSurgeControlFactor() - 1.0) * 100)
        + "% margin): " + safeFlowHead[0] + " m3/hr");
    System.out.println("  Head at safe flow: " + safeFlowHead[1] + " kJ/kg");

    // Verify the results match
    Assertions.assertEquals(safeMinFlowFromMethod, safeFlowHead[0], 0.01,
        "Safe flow from convenience method should match manual calculation");

    // The head at safe flow should be slightly lower than surge head (since flow is higher)
    System.out.println("\nComparison:");
    System.out.println("  Surge head at surge flow: " + surgeHeadAtSpeed + " kJ/kg");
    System.out.println("  Head at safe flow: " + safeFlowHead[1] + " kJ/kg");
    System.out.println("  Head difference: " + (surgeHeadAtSpeed - safeFlowHead[1]) + " kJ/kg");

    // ===== Test Distance to Surge (Current Implementation Issue) =====
    System.out.println("\n=== Testing Distance to Surge for Single Speed ===");

    // Run the compressor at a test flow to get actual operating point
    comp1.run();
    double actualFlow = comp1.getInletStream().getFlowRate("m3/hr");
    System.out.println("Compressor inlet flow: " + actualFlow + " m3/hr");

    // Try the existing getDistanceToSurge() method
    // This should NOW work for single speed because we updated the implementation
    double distanceToSurge = comp1.getDistanceToSurge();
    System.out.println("Distance to surge (using getDistanceToSurge()): " + distanceToSurge);
    System.out.println("  Operating at " + (distanceToSurge * 100) + "% above surge point");

    // Calculate distance to surge manually using the new methods to verify
    double manualDistanceToSurge = actualFlow / surgeFlowAtSpeed - 1.0;
    System.out.println("\nManual calculation using getSurgeFlowAtSpeed():");
    System.out.println("  Distance to surge: " + manualDistanceToSurge);
    System.out.println("  Current flow / Surge flow = " + actualFlow + " / " + surgeFlowAtSpeed
        + " = " + (actualFlow / surgeFlowAtSpeed));

    // Verify they match
    Assertions.assertEquals(manualDistanceToSurge, distanceToSurge, 0.001,
        "getDistanceToSurge() should match manual calculation for single speed");

    // Test the new getDistanceToStoneWall() method
    double distanceToStoneWall = comp1.getDistanceToStoneWall();
    System.out.println(
        "\nDistance to stone wall (using getDistanceToStoneWall()): " + distanceToStoneWall);
    System.out.println("  Stone wall is " + (distanceToStoneWall * 100) + "% above current flow");

    // Calculate distance to stone wall manually to verify
    double manualDistanceToStoneWall = stoneWallFlowAtSpeed / actualFlow - 1.0;
    System.out.println("\nManual calculation using getStoneWallFlowAtSpeed():");
    System.out.println("  Distance to stone wall: " + manualDistanceToStoneWall);
    System.out.println("  Stone wall flow / Current flow = " + stoneWallFlowAtSpeed + " / "
        + actualFlow + " = " + (stoneWallFlowAtSpeed / actualFlow));

    // Verify they match
    Assertions.assertEquals(manualDistanceToStoneWall, distanceToStoneWall, 0.001,
        "getDistanceToStoneWall() should match manual calculation for single speed");

    // Verify we're operating within the envelope
    Assertions.assertTrue(actualFlow > surgeFlowAtSpeed, "Should be operating above surge point");
    Assertions.assertTrue(actualFlow < stoneWallFlowAtSpeed,
        "Should be operating below stone wall point");
    Assertions.assertTrue(distanceToSurge > 0,
        "Distance to surge should be positive (operating above surge)");
    Assertions.assertTrue(distanceToStoneWall > 0,
        "Distance to stone wall should be positive (stone wall above current flow)");
  }
}
