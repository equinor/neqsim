package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Checks interpolation against prescribed speed curves, independently of a process solve. */
class CompressorChartSpeedInterpolationTest extends NeqSimTest {
  private CompressorChartAlternativeMapLookupExtrapolate createChart() {
    CompressorChartAlternativeMapLookupExtrapolate chart = new CompressorChartAlternativeMapLookupExtrapolate();
    double[] flow = {1000.0, 2000.0, 3000.0};
    double[] efficiency = {75.0, 80.0, 75.0};
    chart.addCurve(6000.0, flow, new double[] {65.0, 60.0, 50.0}, efficiency);
    chart.addCurve(7000.0, flow, new double[] {85.0, 80.0, 70.0}, efficiency);
    chart.addCurve(8000.0, flow, new double[] {105.0, 100.0, 90.0}, efficiency);
    return chart;
  }

  @Test
  void headIsLinearBetweenTheSuppliedSpeedCurves() {
    CompressorChartAlternativeMapLookupExtrapolate chart = createChart();
    for (double speed : new double[] {6250.0, 6500.0, 6750.0, 7250.0, 7500.0, 7750.0}) {
      double expectedHead = 60.0 + (speed - 6000.0) * 0.02;
      assertEquals(expectedHead, chart.getPolytropicHead(2000.0, speed), 1.0e-12,
          "Interpolation must not apply a second speed correction");
    }
  }

  @Test
  void headIsContinuousOnBothSidesOfEveryReferenceSpeed() {
    CompressorChartAlternativeMapLookupExtrapolate chart = createChart();
    for (double speed : new double[] {6000.0, 7000.0, 8000.0}) {
      double expectedHead = 60.0 + (speed - 6000.0) * 0.02;
      assertEquals(expectedHead, chart.getPolytropicHead(2000.0, speed), 1.0e-12);
      assertEquals(expectedHead, chart.getPolytropicHead(2000.0, speed - 1.0e-6), 3.0e-8,
          "Head must approach the reference curve from below");
      assertEquals(expectedHead, chart.getPolytropicHead(2000.0, speed + 1.0e-6), 3.0e-8,
          "Head must approach the reference curve from above");
    }
  }

  @Test
  void increasingReferenceHeadsHaveNoDownwardJumpsWithSpeed() {
    CompressorChartAlternativeMapLookupExtrapolate chart = createChart();
    double previousHead = chart.getPolytropicHead(2000.0, 6000.0);
    for (int speed = 6001; speed <= 8000; speed++) {
      double head = chart.getPolytropicHead(2000.0, speed);
      assertTrue(head > previousHead, "Increasing speed must increase this prescribed map's head at " + speed);
      previousHead = head;
    }
  }

  @Test
  void speedSolveRecoversReferenceHeadFromEitherSideOfTheCurve() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(2000.0, "m3/hr");
    feed.run();
    feed.getFluid().init(3);
    double kappa = feed.getFluid().getGamma2();
    double exponent = 1.0 / (1.0 - (kappa - 1.0) / kappa / 0.8);
    // Prescribed map head at 7000 RPM is 80 kJ/kg, with 80% efficiency at this flow.
    double pressureRatio = Math.pow(
        1.0 + 80000.0 * (exponent - 1.0) / exponent * feed.getFluid().getMolarMass()
            / (feed.getFluid().getZ() * ThermodynamicConstantsInterface.R * feed.getTemperature()),
        exponent / (exponent - 1.0));
    for (double initialSpeed : new double[] {6500.0, 6999.0, 7001.0, 7500.0}) {
      Compressor compressor = new Compressor("compressor", feed);
      compressor.setCompressorChart(createChart());
      compressor.getCompressorChart().setUseCompressorChart(true);
      compressor.getCompressorChart().setHeadUnit("kJ/kg");
      compressor.setUsePolytropicCalc(true);
      compressor.setOutletPressure(50.0 * pressureRatio, "bara");
      compressor.setSpeed(initialSpeed);
      compressor.setSolveSpeed(true);
      compressor.run();
      assertEquals(7000.0, compressor.getSpeed(), 0.1, "Speed must not depend on the initial guess");
      assertEquals(80.0, compressor.getPolytropicFluidHead(), 0.001);
      double solvedSpeed = compressor.getSpeed();
      double solvedHead = compressor.getPolytropicFluidHead();
      double solvedPower = compressor.getPower();
      for (int replay = 0; replay < 3; replay++) {
        compressor.run();
        assertEquals(solvedSpeed, compressor.getSpeed(), 1.0e-10,
            "An accepted speed must remain unchanged when the same operating point is replayed");
        assertEquals(solvedHead, compressor.getPolytropicFluidHead(), 1.0e-10);
        assertEquals(solvedPower, compressor.getPower(), 1.0e-6);
      }
    }
  }

  @Test
  void zeroSpeedAndSingleReferenceExtrapolationRemainUnchanged() {
    CompressorChartAlternativeMapLookupExtrapolate chart = createChart();
    assertEquals(0.0, chart.getPolytropicHead(2000.0, 0.0), 0.0);
    assertEquals(50.0, chart.getPolytropicHead(2000.0, 5000.0), 1.0e-12);
    assertEquals(112.5, chart.getPolytropicHead(2000.0, 9000.0), 1.0e-12);
    CompressorChartAlternativeMapLookupExtrapolate single = new CompressorChartAlternativeMapLookupExtrapolate();
    single.addCurve(6000.0, new double[] {1000.0, 2000.0, 3000.0}, new double[] {65.0, 60.0, 50.0},
        new double[] {75.0, 80.0, 75.0});
    assertEquals(70.0, single.getPolytropicHead(2000.0, 7000.0), 1.0e-12);
  }
}
