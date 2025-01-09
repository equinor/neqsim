package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

public class CompressorChartTest {
  public Compressor comp1;

  @BeforeEach
  public void setUp() {
    SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);

    // testFluid.addComponent("methane", 1.0);
    // testFluid.setMixingRule(2);
    // testFluid.setTotalFlowRate(0.635, "MSm3/day");

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
    testFluid.setMultiPhaseCheck(true);

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(1.0, "MSm3/day");

    Stream stream_1 = new Stream("Stream1", testFluid);
    comp1 = new Compressor("cmp1", stream_1);
    comp1.setUsePolytropicCalc(true);
    // comp1.getAntiSurge().setActive(true);
    comp1.setSpeed(11918);

    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
    // double[] speed = new double[] { 1000.0, 2000.0, 3000.0, 4000.0 };
    // double[][] flow = new double[][] { { 453.2, 600.0, 750.0, 800.0 }, { 453.2,
    // 600.0, 750.0, 800.0
    // }, { 453.2, 600.0, 750.0, 800.0 }, { 453.2, 600.0, 750.0, 800.0 } };
    // double[][] head = new double[][] { { 10000.0, 9000.0, 8000.0, 7500.0 }, {
    // 10000.0, 9000.0, 8000.0, 7500.0 }, { 10000.0, 9000.0, 8000.0, 7500.0 }, {
    // 10000.0, 9000.0, 8000.0, 7500.0 } };
    // double[][] polyEff = new double[][] { {
    // 90.0, 91.0, 89.0, 88.0 }, { 90.0, 91.0, 89.0, 88.0 }, { 90.0, 91.0, 89.0,
    // 88.1 }, { 90.0, 91.0, 89.0, 88.1 } };

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

    // double[] chartConditions = new double[] { 0.3, 1.0, 1.0, 1.0 };
    // double[] speed = new double[] { 13402.0 };
    // double[][] flow = new double[][] { { 1050.0, 1260.0, 1650.0, 1950.0 } };
    // double[][] head = new double[][] { { 8555.0, 8227.0, 6918.0, 5223.0 } };
    // double[][] head = new double[][] { { 85.0, 82.0, 69.0, 52.0 } };
    // double[][] polyEff = new double[][] { { 66.8, 69.0, 66.4, 55.6 } };
    comp1.getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
    comp1.getCompressorChart().setHeadUnit("kJ/kg");

    double[] surgeflow = new double[] {2789.0, 2550.0, 2500.0, 2200.0};
    double[] surgehead = new double[] {80.0, 72.0, 70.0, 65.0};
    comp1.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeflow, surgehead);
    // comp1.getAntiSurge().setActive(true);
    comp1.getAntiSurge().setSurgeControlFactor(1.0);
    /*
     * double[] surgeflow = new double[] { 453.2, 550.0, 700.0, 800.0 }; double[] surgehead = new
     * double[] { 6000.0, 7000.0, 8000.0, 10000.0 };
     * comp1.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeflow, surgehead)
     * double[] stoneWallflow = new double[] { 923.2, 950.0, 980.0, 1000.0 }; double[] stoneWallHead
     * = new double[] { 6000.0, 7000.0, 8000.0, 10000.0 };
     * comp1.getCompressorChart().getStoneWallCurve().setCurve(chartConditions, stoneWallflow,
     * stoneWallHead);
     */
  }

  @Test
  public void test_Run() {
    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    Stream stream_1 = (Stream) comp1.getInletStream();
    operations.add(stream_1);
    operations.add(comp1);
    operations.run();
    // operations.displayResult();

    /*
     * logger.info("power " + comp1.getPower()); logger.info("is surge " +
     * comp1.getAntiSurge().isSurge()); System.out .println("fraction in anti surge line " +
     * comp1.getAntiSurge().getCurrentSurgeFraction()); logger.info("Polytropic head from curve:" +
     * comp1.getPolytropicHead()); logger.info("Polytropic eff from curve:" +
     * comp1.getPolytropicEfficiency() * 100.0); logger.info("flow " +
     * stream_1.getThermoSystem().getFlowRate("m3/hr"));
     *
     * logger.info("speed " + comp1.getCompressorChart().getSpeed(
     * stream_1.getThermoSystem().getFlowRate("m3/hr") + 10.0, comp1.getPolytropicHead()));
     * logger.info("pressure out " + comp1.getOutletPressure()); logger.info("temperature out " +
     * (comp1.getOutTemperature() - 273.15) + " C");
     */

    double temperatureOut = 273.15 + 84;
    comp1.setOutletPressure(96.0);
    comp1.setOutTemperature(temperatureOut);
    operations.run();
    double polytropicHead = comp1.getPolytropicHead();
    double flowRate = stream_1.getThermoSystem().getFlowRate("m3/hr");
    double calcSpeed = comp1.getCompressorChart().getSpeed(flowRate, polytropicHead);

    assertTrue(calcSpeed > 0);
    /*
     * logger.info("polytopic head " + polytropicHead); logger.info("polytopic efficiency " +
     * comp1.getPolytropicEfficiency()); logger.info("temperature out " + (comp1.getOutTemperature()
     * - 273.15) + " C"); logger.info("calculated speed " + calcSpeed); logger.info("power " +
     * comp1.getPower());
     */

    // comp1.getCompressorChart().plot();
  }

  @Test
  void testSetHeadUnit() {
    CompressorChart cc = new CompressorChart();
    String origUnit = cc.getHeadUnit();
    Assertions.assertEquals("meter", origUnit);
    String newUnit = "kJ/kg";
    cc.setHeadUnit(newUnit);
    Assertions.assertEquals(newUnit, cc.getHeadUnit());
    cc.setHeadUnit(origUnit);
    Assertions.assertEquals(origUnit, cc.getHeadUnit());

    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {
      cc.setHeadUnit("doesNotExist");
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: CompressorChart:setHeadUnit - Input headUnit does not support value doesNotExist",
        thrown.getMessage());
  }

  @Test
  public void runCurveTest() {
    SystemInterface testFluid = new SystemPrEos(273.15 + 29.96, 75.73);

    testFluid.addComponent("nitrogen", 0.7823);
    testFluid.addComponent("CO2", 1.245);
    testFluid.addComponent("methane", 88.4681);
    testFluid.addComponent("ethane", 4.7652);
    testFluid.addComponent("propane", 2.3669);
    testFluid.addComponent("i-butane", 0.3848);
    testFluid.addComponent("n-butane", 0.873);
    testFluid.addComponent("i-pentane", 0.243);
    testFluid.addComponent("n-pentane", 0.2947);
    testFluid.addComponent("n-hexane", 0.2455);
    testFluid.addComponent("n-heptane", 0.1735);
    testFluid.addComponent("n-octane", 0.064);
    testFluid.addComponent("water", 0.076);
    testFluid.setMixingRule("classic");

    testFluid.setTemperature(29.96, "C");
    testFluid.setPressure(75.73, "bara");
    testFluid.setTotalFlowRate(559401.418270102, "kg/hr");

    Stream stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();

    // stream_1.getFluid().prettyPrint();

    Compressor comp1 = new Compressor("compressor 1", stream_1);
    comp1.setCompressorChartType("interpolate and extrapolate");
    comp1.setUsePolytropicCalc(true);
    comp1.setSpeed(8765);
    comp1.setUseGERG2008(false);

    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};

    double[] speed = new double[] {7000, 7500, 8000, 8500, 9000, 9500, 9659, 10000, 10500};

    double[][] flow = new double[][] {{4512.7, 5120.8, 5760.9, 6401, 6868.27},
        {4862.47, 5486.57, 6172.39, 6858.21, 7550.89},
        {5237.84, 5852.34, 6583.88, 7315.43, 8046.97, 8266.43},
        {5642.94, 6218.11, 6995.38, 7772.64, 8549.9, 9000.72},
        {6221.77, 6583.88, 7406.87, 8229.85, 9052.84, 9768.84},
        {6888.85, 6949.65, 7818.36, 8687.07, 9555.77, 10424.5, 10546.1},
        {7109.83, 7948.87, 8832.08, 9715.29, 10598.5, 10801.6},
        {7598.9, 8229.85, 9144.28, 10058.7, 10973.1, 11338.9},
        {8334.1, 8641.35, 9601.5, 10561.6, 11521.8, 11963.5}};

    double[][] head = new double[][] {{61.885, 59.639, 56.433, 52.481, 49.132},
        {71.416, 69.079, 65.589, 61.216, 55.858}, {81.621, 79.311, 75.545, 70.727, 64.867, 62.879,},
        {92.493, 90.312, 86.3, 81.079, 74.658, 70.216},
        {103.512, 102.073, 97.83, 92.254, 85.292, 77.638},
        {114.891, 114.632, 110.169, 104.221, 96.727, 87.002, 85.262},
        {118.595, 114.252, 108.203, 100.55, 90.532, 87.54},
        {126.747, 123.376, 117.113, 109.056, 98.369, 92.632},
        {139.082, 137.398, 130.867, 122.264, 110.548, 103.247},};
    double[][] polyEff = new double[][] {{78.3, 78.2, 77.2, 75.4, 73.4},

        {78.3, 78.3, 77.5, 75.8, 73}, {78.2, 78.4, 77.7, 76.1, 73.5, 72.5},
        {78.2, 78.4, 77.9, 76.4, 74, 71.9}, {78.3, 78.4, 78, 76.7, 74.5, 71.2},
        {78.3, 78.4, 78.1, 77, 74.9, 71.3, 70.5}, {78.4, 78.1, 77.1, 75, 71.4, 70.2},
        {78.3, 78.2, 77.2, 75.2, 71.7, 69.5}, {78.2, 78.2, 77.3, 75.5, 72.2, 69.6}};

    comp1.getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
    comp1.getCompressorChart().setHeadUnit("kJ/kg");

    double[] surgeflow =
        new double[] {4512.7, 4862.47, 5237.84, 5642.94, 6221.77, 6888.85, 7109.83, 7598.9};
    double[] surgehead =
        new double[] {61.885, 71.416, 81.621, 92.493, 103.512, 114.891, 118.595, 126.747};
    comp1.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeflow, surgehead);
    // comp1.getAntiSurge().setActive(true);
    comp1.getAntiSurge().setSurgeControlFactor(1.0);
    comp1.run();

    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("speed " + comp1.getSpeed());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("out pres " + comp1.getOutletPressure());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("out temp " + (comp1.getOutTemperature() - 273.15));
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("feed flow " + (comp1.getInletStream().getFlowRate("m3/hr")));
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("polytropic head " + comp1.getPolytropicFluidHead());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("polytropic efficiency " + comp1.getPolytropicEfficiency());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("dist to surge " + comp1.getDistanceToSurge());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("surge flow rate margin " + comp1.getSurgeFlowRateMargin());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("surge flow rate " + comp1.getSurgeFlowRate());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("duty " + comp1.getPower("MW"));
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("surge " + comp1.isSurge());
    Assertions.assertTrue(comp1.isSurge() == false);
    Assertions.assertEquals(158.7732888, comp1.getOutletPressure(), 1e-3);
  }

  @Test
  public void runCurveTest2() {
    SystemInterface testFluid = new SystemPrEos(273.15 + 29.96, 75.73);

    testFluid.addComponent("nitrogen", 0.7823);
    testFluid.addComponent("CO2", 1.245);
    testFluid.addComponent("methane", 88.4681);
    testFluid.addComponent("ethane", 4.7652);
    testFluid.addComponent("propane", 2.3669);
    testFluid.addComponent("i-butane", 0.3848);
    testFluid.addComponent("n-butane", 0.873);
    testFluid.setMixingRule("classic");

    Stream stream_1 = new Stream("Stream1", testFluid);
    stream_1.setTemperature(29.96, "C");
    stream_1.setPressure(75.73, "bara");
    stream_1.setFlowRate(559401.4, "kg/hr");
    stream_1.run();

    Compressor comp1 = new Compressor("compressor 1", stream_1);
    comp1.setCompressorChartType("interpolate and extrapolate");
    comp1.setUsePolytropicCalc(true);
    comp1.setPolytropicEfficiency(0.85);
    comp1.setSpeed(9000);
    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};

    double[] speed = new double[] {7000, 7500, 8000, 8500, 9000, 9500, 9659, 10000, 10500};

    double[][] flow = new double[][] {{4512.7, 5120.8, 5760.9, 6401, 6868.27},
        {4862.47, 5486.57, 6172.39, 6858.21, 7550.89},
        {5237.84, 5852.34, 6583.88, 7315.43, 8046.97, 8266.43},
        {5642.94, 6218.11, 6995.38, 7772.64, 8549.9, 9000.72},
        {6221.77, 6583.88, 7406.87, 8229.85, 9052.84, 9768.84},
        {6888.85, 6949.65, 7818.36, 8687.07, 9555.77, 10424.5, 10546.1},
        {7109.83, 7948.87, 8832.08, 9715.29, 10598.5, 10801.6},
        {7598.9, 8229.85, 9144.28, 10058.7, 10973.1, 11338.9},
        {8334.1, 8641.35, 9601.5, 10561.6, 11521.8, 11963.5}};

    double[][] head = new double[][] {{61.885, 59.639, 56.433, 52.481, 49.132},
        {71.416, 69.079, 65.589, 61.216, 55.858}, {81.621, 79.311, 75.545, 70.727, 64.867, 62.879,},
        {92.493, 90.312, 86.3, 81.079, 74.658, 70.216},
        {103.512, 102.073, 97.83, 92.254, 85.292, 77.638},
        {114.891, 114.632, 110.169, 104.221, 96.727, 87.002, 85.262},
        {118.595, 114.252, 108.203, 100.55, 90.532, 87.54},
        {126.747, 123.376, 117.113, 109.056, 98.369, 92.632},
        {139.082, 137.398, 130.867, 122.264, 110.548, 103.247},};
    double[][] polyEff = new double[][] {{78.3, 78.2, 77.2, 75.4, 73.4},

        {78.3, 78.3, 77.5, 75.8, 73}, {78.2, 78.4, 77.7, 76.1, 73.5, 72.5},
        {78.2, 78.4, 77.9, 76.4, 74, 71.9}, {78.3, 78.4, 78, 76.7, 74.5, 71.2},
        {78.3, 78.4, 78.1, 77, 74.9, 71.3, 70.5}, {78.4, 78.1, 77.1, 75, 71.4, 70.2},
        {78.3, 78.2, 77.2, 75.2, 71.7, 69.5}, {78.2, 78.2, 77.3, 75.5, 72.2, 69.6}};

    comp1.getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
    comp1.getCompressorChart().setHeadUnit("kJ/kg");

    double[] surgeflow =
        new double[] {4512.7, 4862.47, 5237.84, 5642.94, 6221.77, 6888.85, 7109.83, 7598.9};
    double[] surgehead =
        new double[] {61.885, 71.416, 81.621, 92.493, 103.512, 114.891, 118.595, 126.747};
    comp1.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeflow, surgehead);
    // comp1.getAntiSurge().setActive(true);
    comp1.getAntiSurge().setSurgeControlFactor(1.0);
    comp1.getCompressorChart().setUseCompressorChart(true);
    comp1.setOutletPressure(220.0, "bara");
    comp1.setSolveSpeed(true);
    comp1.run();

    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("feed flow " + (comp1.getInletStream().getFlowRate("m3/hr")));
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("out pressure " + (comp1.getOutletStream().getPressure("bara")));
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("power " + comp1.getPower("MW"));
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("polytropic head " + comp1.getPolytropicFluidHead());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("polytropic efficiency " + comp1.getPolytropicEfficiency());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("speed " + comp1.getSpeed());

    stream_1.setFlowRate(309401.4, "kg/hr");
    stream_1.run();
    comp1.setOutletPressure(170.0, "bara");
    comp1.run();
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("feed flow " + (comp1.getInletStream().getFlowRate("m3/hr")));
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("out pressure " + (comp1.getOutletStream().getPressure("bara")));
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("power " + comp1.getPower("MW"));
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("polytropic head " + comp1.getPolytropicFluidHead());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("polytropic efficiency " + comp1.getPolytropicEfficiency());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("speed " + comp1.getSpeed());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("dist to surge " + comp1.getDistanceToSurge());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("surge flow rate margin " + comp1.getSurgeFlowRateMargin());
    org.apache.logging.log4j.LogManager.getLogger(CompressorChartTest.class)
        .debug("surge flow rate " + comp1.getSurgeFlowRate());
  }
}
