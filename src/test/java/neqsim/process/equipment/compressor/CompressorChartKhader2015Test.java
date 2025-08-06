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

}
