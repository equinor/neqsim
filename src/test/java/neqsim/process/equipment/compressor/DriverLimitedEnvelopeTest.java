package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.driver.ElectricMotorDriver;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link Compressor#getDriverLimitedEnvelope()}: a derated driver (bypassed VFD power cell) must reduce the
 * compressor head capacity through the chart without a manual affinity-law calculation.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DriverLimitedEnvelopeTest {
  /** Chart conditions for the reference compressor map. */
  private static final double[] CHART_CONDITIONS = new double[] {0.3, 1.0, 1.0, 1.0};

  /** Speed lines of the reference map (RPM). */
  private static final double[] SPEED = new double[] {12913, 11098, 8200};

  /**
   * Builds a charted compressor on a natural-gas suction stream.
   *
   * @return a compressor with chart and surge curve loaded
   */
  private Compressor buildCompressor() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 90.0);
    fluid.addComponent("ethane", 10.0);
    fluid.setMixingRule(2);
    fluid.setTemperature(24.0, "C");
    fluid.setPressure(48.0, "bara");
    fluid.setTotalFlowRate(2.0, "MSm3/day");

    Stream stream = new Stream("suction", fluid);
    stream.run();

    Compressor comp = new Compressor("export", stream);
    comp.setUsePolytropicCalc(true);
    comp.setSpeed(11918);

    double[][] flow = new double[][] {
        {2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329, 5661.0331},
        {2247.2043, 2799.7342, 3178.3428, 3656.1551, 4102.778, 4394.1591, 4648.3224, 4840.4998},
        {1636.5807, 2002.8708, 2338.0319, 2642.1245, 2896.4894, 3113.6264, 3274.8764, 3411.2977}};
    double[][] head = new double[][] {{80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728},
        {58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109},
        {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403}};
    double[][] polyEff = new double[][] {
        {77.2452238409573, 79.4154186459363, 80.737960012489, 80.5229826589649, 79.2210931638144, 75.4719133864634,
            69.6034181197298, 58.7322388482707},
        {77.0716623789093, 80.4629750233093, 81.1390811169072, 79.6374242667478, 75.380928428817, 69.5332969549779,
            63.7997587622339, 58.8120614497758},
        {78.0924334304045, 80.9353551568667, 80.7904437766234, 78.8639325223295, 75.2170936751143, 70.3105081673411,
            65.5507568533569, 61.0391468300337}};

    comp.getCompressorChart().setCurves(CHART_CONDITIONS, SPEED, flow, head, polyEff);
    comp.getCompressorChart().setHeadUnit("kJ/kg");
    comp.getCompressorChart().setUseCompressorChart(true);

    double[] surgeFlow = new double[] {2789.0, 2250.0, 1640.0};
    double[] surgeHead = new double[] {80.0, 58.6, 32.2};
    comp.getCompressorChart().getSurgeCurve().setCurve(CHART_CONDITIONS, surgeFlow, surgeHead);
    return comp;
  }

  /**
   * Builds a VFD motor with the requested number of bypassed power cells.
   *
   * @param bypassed bypassed cells per phase (0 for none)
   * @return the configured driver
   */
  private ElectricMotorDriver buildDriver(int bypassed) {
    ElectricMotorDriver driver = new ElectricMotorDriver(5000.0, 3600.0, 0.96);
    driver.setHasVFD(true);
    driver.setMaxSpeedRatio(1.0);
    if (bypassed > 0) {
      driver.setCellBypassDerating(9, bypassed);
    }
    return driver;
  }

  /** With a healthy driver the envelope equals the reference chart envelope. */
  @Test
  void testHealthyDriverGivesReferenceEnvelope() {
    Compressor comp = buildCompressor();
    comp.setDriverCurve(buildDriver(0));

    DriverLimitedEnvelope envelope = comp.getDriverLimitedEnvelope();

    assertEquals(1.0, envelope.getDriverSpeedFraction(), 1.0e-9);
    assertEquals(12913.0, envelope.getLimitedChartSpeed(), 1.0e-6);
    assertFalse(envelope.isDerated());
    assertEquals(1.0, envelope.getHeadFraction(), 1.0e-9);
    assertEquals(5000.0, envelope.getMaxDriverPower(), 1.0e-6);
  }

  /** One of nine cells bypassed lowers speed by 1/9, and the chart head by roughly the affinity-law square. */
  @Test
  void testBypassedCellReducesHeadAndPower() {
    Compressor comp = buildCompressor();
    comp.setDriverCurve(buildDriver(1));

    DriverLimitedEnvelope envelope = comp.getDriverLimitedEnvelope();

    double fraction = 8.0 / 9.0;
    assertEquals(fraction, envelope.getDriverSpeedFraction(), 1.0e-9);
    assertEquals(fraction * 12913.0, envelope.getLimitedChartSpeed(), 1.0e-6);
    assertTrue(envelope.isDerated());
    assertEquals(5000.0 * fraction, envelope.getMaxDriverPower(), 1.0e-6);

    assertEquals(fraction * fraction, envelope.getHeadFraction(), 0.08);
    assertTrue(envelope.getHeadFraction() < 1.0);
    assertTrue(envelope.getSurgeFlow() < comp.getCompressorChart().getSurgeFlowAtSpeed(12913.0));
  }

  /** Without a driver curve the method fails with a remediation hint. */
  @Test
  void testMissingDriverCurveThrows() {
    Compressor comp = buildCompressor();
    assertThrows(IllegalStateException.class, () -> comp.getDriverLimitedEnvelope());
  }
}
