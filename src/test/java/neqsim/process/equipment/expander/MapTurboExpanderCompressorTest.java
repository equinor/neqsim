package neqsim.process.equipment.expander;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.CompressorChartKhader2015;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link MapTurboExpanderCompressor}, the map-based single-shaft
 * turboexpander-compressor.
 */
public class MapTurboExpanderCompressorTest extends neqsim.NeqSimTest {

  /**
   * Build a representative process gas.
   *
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @return a configured fluid
   */
  private SystemInterface makeGas(double temperatureC, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    fluid.addComponent("nitrogen", 1.205);
    fluid.addComponent("CO2", 1.340);
    fluid.addComponent("methane", 87.974);
    fluid.addComponent("ethane", 5.258);
    fluid.addComponent("propane", 3.283);
    fluid.addComponent("i-butane", 0.082);
    fluid.addComponent("n-butane", 0.487);
    fluid.setMixingRule(2);
    fluid.setTemperature(temperatureC, "C");
    fluid.setPressure(pressureBara, "bara");
    return fluid;
  }

  /**
   * Attach a multi-speed performance map to the compressor side.
   *
   * @param machine the turboexpander-compressor
   * @param compressorFeed the compressor inlet stream
   */
  private void setCompressorChart(MapTurboExpanderCompressor machine, Stream compressorFeed) {
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
            {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403}};
    double[][] polyEff =
        new double[][] {{77.245, 79.415, 80.738, 80.523, 79.221, 75.472, 69.603, 58.732},
            {77.011, 79.307, 80.894, 80.719, 79.531, 75.591, 69.685, 60.004},
            {77.004, 79.169, 80.804, 80.654, 78.853, 73.666, 66.274, 57.672},
            {77.072, 80.463, 81.139, 79.637, 75.381, 69.533, 63.800, 58.812},
            {76.971, 79.834, 80.947, 80.581, 78.046, 73.040, 66.557, 59.862},
            {77.506, 80.206, 81.034, 79.609, 76.381, 70.803, 64.644, 60.530},
            {77.818, 80.065, 81.063, 79.896, 76.198, 69.290, 60.857},
            {78.092, 80.935, 80.790, 78.864, 75.217, 70.311, 65.551, 61.039}};

    CompressorChartKhader2015 chart = new CompressorChartKhader2015(compressorFeed, 0.9);
    chart.setCurves(chartConditions, speed, flow, head, flow, polyEff);
    machine.getCompressor().setCompressorChart(chart);
    machine.getCompressor().getCompressorChart().setHeadUnit("kJ/kg");
    machine.getCompressor().setUsePolytropicCalc(true);
  }

  @Test
  public void testBalancedSpeedMode() {
    Stream expanderFeed = new Stream("expFeed", makeGas(25.0, 60.0));
    expanderFeed.setFlowRate(3.0, "MSm3/day");
    expanderFeed.run();

    Stream compressorFeed = new Stream("compFeed", makeGas(20.0, 48.0));
    compressorFeed.setFlowRate(3.0, "MSm3/day");
    compressorFeed.run();

    MapTurboExpanderCompressor machine =
        new MapTurboExpanderCompressor("TEC", expanderFeed, compressorFeed);
    machine.setExpanderOutletPressure(30.0);
    machine.setExpanderIsentropicEfficiency(0.85);
    machine.setMechanicalEfficiency(0.98);
    setCompressorChart(machine, compressorFeed);
    machine.setShaftMode(MapTurboExpanderCompressor.ShaftMode.BALANCED_SPEED);

    machine.run(java.util.UUID.randomUUID());

    // Shaft power is produced by the expander.
    Assertions.assertTrue(machine.getAvailableShaftPower() > 0.0,
        "Expander should produce positive shaft power");

    // Shaft speed must lie within the compressor chart speed range.
    double minSpeed = machine.getCompressor().getCompressorChart().getMinSpeedCurve();
    double maxSpeed = machine.getCompressor().getCompressorChart().getMaxSpeedCurve();
    Assertions.assertTrue(
        machine.getShaftSpeed() >= minSpeed - 1.0 && machine.getShaftSpeed() <= maxSpeed + 1.0,
        "Shaft speed " + machine.getShaftSpeed() + " outside chart range [" + minSpeed + ", "
            + maxSpeed + "]");

    // When not speed-limited, the power balance must close.
    if (!machine.isSpeedLimited()) {
      double relativeResidual =
          Math.abs(machine.getPowerBalanceResidual()) / machine.getAvailableShaftPower();
      Assertions.assertTrue(relativeResidual < 1.0e-2,
          "Power balance not closed, relative residual = " + relativeResidual);
    }

    // Compressor discharge pressure is an output and must exceed the suction pressure.
    Assertions.assertTrue(
        machine.getCompressorOutStream().getPressure() > compressorFeed.getPressure(),
        "Compressor should raise the pressure");
  }

  @Test
  public void testSpecifiedPressuresMode() {
    Stream expanderFeed = new Stream("expFeed", makeGas(25.0, 60.0));
    expanderFeed.setFlowRate(3.0, "MSm3/day");
    expanderFeed.run();

    Stream compressorFeed = new Stream("compFeed", makeGas(20.0, 48.0));
    compressorFeed.setFlowRate(3.0, "MSm3/day");
    compressorFeed.run();

    MapTurboExpanderCompressor machine =
        new MapTurboExpanderCompressor("TEC2", expanderFeed, compressorFeed);
    machine.setExpanderOutletPressure(30.0);
    machine.setExpanderIsentropicEfficiency(0.85);
    machine.setCompressorOutletPressure(58.0);
    machine.getCompressor().setIsentropicEfficiency(0.78);
    machine.setShaftMode(MapTurboExpanderCompressor.ShaftMode.SPECIFIED_PRESSURES);

    machine.run(java.util.UUID.randomUUID());

    Assertions.assertTrue(machine.getAvailableShaftPower() > 0.0);
    Assertions.assertTrue(machine.getConsumedCompressorPower() > 0.0);
    // Residual = available shaft power - compressor power (may be non-zero by design).
    Assertions.assertEquals(machine.getAvailableShaftPower() - machine.getConsumedCompressorPower(),
        machine.getPowerBalanceResidual(), 1.0);
    Assertions.assertEquals(58.0, machine.getCompressorOutStream().getPressure(), 1e-6);
  }

  @Test
  public void testInfeasibleTurndownPath() {
    // A very small expander pressure drop yields little shaft power, so the brake compressor
    // cannot be driven even at the minimum map speed: the machine is at its turndown / surge
    // limit (UNDER_POWER_SURGE) with a negative power balance residual.
    Stream expanderFeed = new Stream("expFeedLow", makeGas(25.0, 31.0));
    expanderFeed.setFlowRate(3.0, "MSm3/day");
    expanderFeed.run();

    Stream compressorFeed = new Stream("compFeed", makeGas(20.0, 48.0));
    compressorFeed.setFlowRate(3.0, "MSm3/day");
    compressorFeed.run();

    MapTurboExpanderCompressor machine =
        new MapTurboExpanderCompressor("TEC3", expanderFeed, compressorFeed);
    machine.setExpanderOutletPressure(30.0);
    machine.setExpanderIsentropicEfficiency(0.85);
    machine.setMechanicalEfficiency(0.98);
    setCompressorChart(machine, compressorFeed);
    machine.setShaftMode(MapTurboExpanderCompressor.ShaftMode.BALANCED_SPEED);

    machine.run(java.util.UUID.randomUUID());

    // Shaft is pegged at the search bound and the machine is not feasible.
    Assertions.assertTrue(machine.isSpeedLimited(), "Shaft speed should be pegged at a bound");
    Assertions.assertFalse(machine.isFeasible(),
        "Machine should be infeasible at the turndown limit");
    Assertions.assertEquals(MapTurboExpanderCompressor.OperatingStatus.UNDER_POWER_SURGE,
        machine.getOperatingStatus(), "Should be under-power / surge limited");
    Assertions.assertTrue(machine.getPowerBalanceResidual() < 0.0,
        "Power balance residual should be negative when the expander is under-powered");

    // Shaft pegged at the minimum map speed.
    double minSpeed = machine.getCompressor().getCompressorChart().getMinSpeedCurve();
    Assertions.assertEquals(minSpeed, machine.getShaftSpeed(), 1.0,
        "Shaft should be pegged at the minimum map speed");
  }

  @Test
  public void testToJsonReportsKeyResults() {
    Stream expanderFeed = new Stream("expFeed", makeGas(25.0, 60.0));
    expanderFeed.setFlowRate(3.0, "MSm3/day");
    expanderFeed.run();

    Stream compressorFeed = new Stream("compFeed", makeGas(20.0, 48.0));
    compressorFeed.setFlowRate(3.0, "MSm3/day");
    compressorFeed.run();

    MapTurboExpanderCompressor machine =
        new MapTurboExpanderCompressor("TEC4", expanderFeed, compressorFeed);
    machine.setExpanderOutletPressure(30.0);
    machine.setExpanderIsentropicEfficiency(0.85);
    setCompressorChart(machine, compressorFeed);
    machine.setShaftMode(MapTurboExpanderCompressor.ShaftMode.BALANCED_SPEED);
    machine.run(java.util.UUID.randomUUID());

    String json = machine.toJson();
    Assertions.assertNotNull(json);
    Assertions.assertTrue(json.contains("shaftSpeed_rpm"), "JSON should report shaft speed");
    Assertions.assertTrue(json.contains("operatingStatus"), "JSON should report operating status");
    Assertions.assertTrue(json.contains("powerBalanceResidual_MW"),
        "JSON should report power balance residual");
    Assertions.assertTrue(machine.toMap().containsKey("feasible"),
        "Result map should contain the feasibility flag");
  }
}
