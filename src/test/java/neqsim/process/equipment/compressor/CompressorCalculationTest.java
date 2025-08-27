package neqsim.process.equipment.compressor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CompressorCalculationTest extends neqsim.NeqSimTest {
  Compressor comp1;
  Stream stream_1;

  @BeforeEach
  public void setUp() {
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
    testFluid.setMultiPhaseCheck(true);

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(1.0, "MSm3/day");

    stream_1 = new Stream("Stream1", testFluid);
    comp1 = new Compressor("comp1", stream_1);
  }

  private void setCurves() {
    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
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
    comp1.getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);

    comp1.getCompressorChart().setHeadUnit("kJ/kg");

    double[] surgeflow = new double[] {2789.0, 2550.0, 2500.0, 2200.0};
    double[] surgehead = new double[] {80.0, 72.0, 70.0, 65.0};
    comp1.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeflow, surgehead);
    // comp1.getAntiSurge().setActive(true);
    comp1.getAntiSurge().setSurgeControlFactor(1.0);
  }

  @Test
  public void testCompressorChartEquals() {
    Assertions.assertEquals(comp1, comp1.copy());
    setCurves();
    Assertions.assertEquals(comp1, comp1.copy());
  }

  @Test
  public void testRun() {
    setCurves();
    comp1.setUsePolytropicCalc(true);
    comp1.setSpeed(11918);
    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(comp1);
    operations.run();

    // logger.info("power " + comp1.getPower());
    Assertions.assertEquals(1163805.2992826412, comp1.getPower(), 1220.01);
    Assertions.assertFalse(comp1.getAntiSurge().isSurge());
    Assertions.assertEquals(0.0, comp1.getAntiSurge().getCurrentSurgeFraction());

    // logger.info("Polytropic head from curve:" + comp1.getPolytropicHead());

    Assertions.assertEquals(55.4041541169498, comp1.getPolytropicHead(), 0.01);
    // logger.info("Polytropic eff from curve:" + comp1.getPolytropicEfficiency() * 100.0);
    Assertions.assertEquals(0.43175024049749283, comp1.getPolytropicEfficiency(), 0.01);

    // logger.info("flow " + stream_1.getThermoSystem().getFlowRate("m3/hr"));
    // logger.info("speed " + comp1.getCompressorChart().getSpeed(
    // stream_1.getThermoSystem().getFlowRate("m3/hr") + 10.0, comp1.getPolytropicHead()));
    // logger.info("pressure out " + comp1.getOutletPressure());
    // logger.info("temperature out " + (comp1.getOutTemperature() - 273.15) + " C");
    /*
     * double temperatureOut = 273.15 + 84; comp1.setOutletPressure(96.0);
     * comp1.setOutTemperature(temperatureOut); operations.run(); double polytropicHead =
     * comp1.getPolytropicHead(); double flowRate = stream_1.getThermoSystem().getFlowRate("m3/hr");
     * double calcSpeed = comp1.getCompressorChart().getSpeed(flowRate, polytropicHead);
     * logger.info("polytopic head " + polytropicHead); logger.info("polytopic efficiency " +
     * comp1.getPolytropicEfficiency()); logger.info("temperature out " + (comp1.getOutTemperature()
     * - 273.15) + " C"); logger.info("calculated speed " + calcSpeed);
     */
  }
}
