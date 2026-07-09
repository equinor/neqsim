package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the anti-surge control-line, recycle energy-penalty, and chart-calibration additions (NIP-02, NIP-03,
 * NIP-04).
 *
 * @author NeqSim
 * @version 1.0
 */
public class CompressorAntiSurgeControlLineTest {
  private Compressor compressor;

  /**
   * Builds a charted single-stage compressor operating to the right of the surge line.
   */
  @BeforeEach
  public void setUp() {
    SystemInterface gas = new SystemSrkEos(303.15, 110.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.08);
    gas.addComponent("propane", 0.05);
    gas.addComponent("nitrogen", 0.02);
    gas.setMixingRule("classic");
    gas.setMultiPhaseCheck(false);

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(120000.0, "kg/hr");
    inlet.setTemperature(30.0, "C");
    inlet.setPressure(110.0, "bara");
    inlet.run();

    compressor = new Compressor("test compressor", inlet);
    compressor.setOutletPressure(250.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.78);
    compressor.setSpeed(9000);
    compressor.run();

    CompressorChartGenerator gen = new CompressorChartGenerator(compressor);
    gen.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = gen.generateCompressorChart("normal curves", 5);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.run();
  }

  /**
   * The control line sits above the surge line by the configured margin, and distance-to-control is smaller than
   * distance-to-surge (NIP-02).
   */
  @Test
  public void testControlLineAndDistance() {
    compressor.setSurgeControlMargin(0.10);
    assertEquals(0.10, compressor.getSurgeControlMargin(), 1e-12);

    double surgeFlow = compressor.getSurgeFlowRate();
    double controlFlow = compressor.getControlLineFlow();
    assertEquals(surgeFlow * 1.10, controlFlow, 1e-6, "control line = surge * (1 + margin)");
    assertTrue(controlFlow > surgeFlow, "control line must be to the right of surge");

    double distSurge = compressor.getDistanceToSurge();
    double distControl = compressor.getDistanceToControlLine();
    assertTrue(distControl < distSurge, "distance to control line must be less than distance to surge");
    assertTrue(distSurge > 0, "operating point should be to the right of surge in this case");
  }

  /**
   * A zero margin makes the control line coincide with the surge line.
   */
  @Test
  public void testZeroMarginEqualsSurge() {
    compressor.setSurgeControlMargin(0.0);
    assertEquals(compressor.getSurgeFlowRate(), compressor.getControlLineFlow(), 1e-9);
    assertEquals(compressor.getDistanceToSurge(), compressor.getDistanceToControlLine(), 1e-9);
  }

  /**
   * A negative margin is rejected.
   */
  @Test
  public void testNegativeMarginRejected() {
    assertThrows(IllegalArgumentException.class, () -> compressor.setSurgeControlMargin(-0.1));
  }

  /**
   * Recycle power penalty scales linearly with the recycle fraction and matches shaft power at 100 % recycle (NIP-03).
   */
  @Test
  public void testRecyclePowerPenalty() {
    double power = compressor.getPower("kW");
    assertTrue(power > 0.0, "shaft power should be positive");
    assertEquals(0.10 * power, compressor.getAntiSurgeRecyclePower(0.10, "kW"), 1e-6);
    assertEquals(power, compressor.getAntiSurgeRecyclePower(1.0, "kW"), 1e-6);
    assertEquals(0.0, compressor.getAntiSurgeRecyclePower(0.0, "kW"), 1e-9);
    // Heat duty equals the wasted shaft work at screening level.
    assertEquals(compressor.getAntiSurgeRecyclePower(0.2, "kW"), compressor.getAntiSurgeRecycleHeatDuty(0.2, "kW"),
        1e-9);
  }

  /**
   * When the operating point is to the right of the control line, no recycle is required.
   */
  @Test
  public void testRequiredRecycleFractionZeroWhenRightOfControlLine() {
    compressor.setSurgeControlMargin(0.10);
    // Operating point is far to the right of surge, so required recycle should be 0.
    assertEquals(0.0, compressor.getRequiredRecycleFractionToControlLine(), 1e-9);
  }

  /**
   * The operating-point map exposes the new control-line fields (NIP-02 schema 1.1).
   */
  @Test
  public void testOperatingPointExposesControlLineFields() {
    compressor.setSurgeControlMargin(0.10);
    Map<String, Object> point = compressor.getOperatingPoint();
    assertEquals("1.1", point.get("schemaVersion"));
    assertTrue(point.containsKey("surgeControlMargin"));
    assertTrue(point.containsKey("controlLineFlow_m3hr"));
    assertTrue(point.containsKey("distanceToControlLine"));
    assertEquals(0.10, ((Number) point.get("surgeControlMargin")).doubleValue(), 1e-12);
  }

  /**
   * The calibrator fits a surge curve, computes the MW correction factor, and recommends a margin (NIP-04).
   */
  @Test
  public void testChartCalibrator() {
    CompressorChartCalibrator cal = new CompressorChartCalibrator(compressor);

    double headOp = compressor.getPolytropicFluidHead();
    double surgeFlowOp = compressor.getSurgeFlowRate();
    // Fit a surge curve slightly to the right of the current one (measured surge test points).
    double[] flow = new double[] { surgeFlowOp * 0.9, surgeFlowOp * 1.05, surgeFlowOp * 1.2 };
    double[] head = new double[] { headOp * 1.2, headOp, headOp * 0.8 };
    cal.fitSurgeCurve(flow, head);
    compressor.run();
    assertNotNull(compressor.getCompressorChart().getSurgeCurve());
    double newSurge = compressor.getSurgeFlowRate();
    assertTrue(newSurge > 0, "fitted surge flow should be positive");

    // MW correction: heavier gas -> lower head -> factor < 1.
    assertEquals(20.0 / 24.0, CompressorChartCalibrator.molarMassHeadCorrectionFactor(20.0, 24.0), 1e-9);
    assertThrows(IllegalArgumentException.class,
        () -> CompressorChartCalibrator.molarMassHeadCorrectionFactor(20.0, 0.0));

    // Control-margin recommendation widens with scatter.
    double tight = cal.recommendControlMargin(0.10, new double[] { 1000.0, 1000.0, 1000.0 });
    double noisy = cal.recommendControlMargin(0.10, new double[] { 800.0, 1000.0, 1200.0 });
    assertEquals(0.10, tight, 1e-9, "no scatter -> base margin");
    assertTrue(noisy > tight, "scatter should widen the recommended margin");
  }

  /**
   * The calibrator rejects mismatched or empty input.
   */
  @Test
  public void testChartCalibratorInputValidation() {
    CompressorChartCalibrator cal = new CompressorChartCalibrator(compressor);
    assertThrows(IllegalArgumentException.class,
        () -> cal.fitSurgeCurve(new double[] { 1.0, 2.0 }, new double[] { 1.0 }));
    assertThrows(IllegalArgumentException.class, () -> cal.fitSurgeCurve(new double[] {}, new double[] {}));
    assertThrows(IllegalArgumentException.class, () -> new CompressorChartCalibrator(null));
    assertFalse(Double.isNaN(cal.recommendControlMargin(0.1, new double[] { 1000.0 })));
  }
}
