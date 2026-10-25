package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Qualifies fixed-speed chart rejection before invalid map values reach a caloric flash.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class CompressorChartOperatingPointTest extends neqsim.NeqSimTest {
  /**
   * Creates a flowing gas compressor with a prescribed map operating point.
   *
   * @param prescribedHead map head in kJ/kg
   * @param efficiency map efficiency in percent
   * @return compressor ready for a fixed-speed calculation
   */
  private Compressor createCompressor(final double prescribedHead, final double efficiency) {
    SystemPrEos fluid = new SystemPrEos(303.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream inlet = new Stream("map inlet", fluid);
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();
    CompressorChart chart = new CompressorChart() {
      /** Serialization version UID. */
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public double getPolytropicHead(double flow, double speed) {
        return prescribedHead;
      }

      /** {@inheritDoc} */
      @Override
      public double getPolytropicEfficiency(double flow, double speed) {
        return efficiency;
      }
    };
    chart.setUseCompressorChart(true);
    chart.setHeadUnit("kJ/kg");
    Compressor compressor = new Compressor("map compressor", inlet);
    compressor.setCompressorChart(chart);
    compressor.setUsePolytropicCalc(true);
    compressor.setSpeed(8000.0);
    return compressor;
  }

  /** Checks that negative or nonfinite head cannot become a negative or nonfinite PH target. */
  @Test
  void rejectsInvalidHeadBeforeCaloricFlash() {
    for (double head : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertInvalidOperatingPoint(head, 75.0);
    }
  }

  /** Checks that map efficiencies are validated before the public setter can clamp them. */
  @Test
  void rejectsInvalidEfficiencyBeforeClamping() {
    for (double efficiency : new double[] {-1.0, 0.0, 100.1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertInvalidOperatingPoint(50.0, efficiency);
    }
  }

  /**
   * Verifies an invalid map is rejected with operating-point evidence and preserves the inlet.
   *
   * @param head invalid or otherwise valid map head in kJ/kg
   * @param efficiency invalid or otherwise valid map efficiency in percent
   */
  private void assertInvalidOperatingPoint(double head, double efficiency) {
    Compressor compressor = createCompressor(head, efficiency);
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, compressor::run);
    assertTrue(failure.getMessage().contains("map compressor"));
    assertTrue(failure.getMessage().contains("head="));
    assertTrue(failure.getMessage().contains("efficiency="));
    assertTrue(failure.getMessage().contains("flow="));
    assertTrue(failure.getMessage().contains("speed="));
    assertEquals(303.15, compressor.getInletStream().getTemperature(), 0.0);
    assertEquals(50.0, compressor.getInletStream().getPressure(), 0.0);
    assertEquals(1000.0, compressor.getInletStream().getFlowRate("kg/hr"), 1.0e-9);
    assertEquals(303.15, compressor.getThermoSystem().getTemperature(), 0.0);
    assertEquals(50.0, compressor.getThermoSystem().getPressure(), 0.0);
    assertTrue(compressor.getThermoSystem().doMultiPhaseCheck(),
        "Rejection must restore the inlet's phase-check policy");
  }

  /** Checks that rejecting a new map point does not overwrite a previously accepted material outlet. */
  @Test
  void rejectedRerunPreservesAcceptedOutlet() {
    Compressor compressor = createCompressor(50.0, 75.0);
    compressor.run();
    SystemInterface accepted = compressor.getOutletStream().getFluid();
    double acceptedTemperature = accepted.getTemperature();
    double acceptedPressure = accepted.getPressure();
    compressor.setCompressorChart(createCompressor(-1.0, 75.0).getCompressorChart());
    assertThrows(IllegalArgumentException.class, compressor::run);
    assertSame(accepted, compressor.getOutletStream().getFluid());
    assertEquals(acceptedTemperature, accepted.getTemperature(), 0.0);
    assertEquals(acceptedPressure, accepted.getPressure(), 0.0);
  }

  /** Checks that a physical chart point closes shaft power against the material enthalpy increase. */
  @Test
  void validOperatingPointClosesEnergyAndPressureRise() {
    Compressor compressor = createCompressor(50.0, 75.0);
    compressor.run();
    assertTrue(compressor.getOutletStream().getPressure() > compressor.getInletStream().getPressure());
    assertTrue(compressor.getOutletStream().getTemperature() > compressor.getInletStream().getTemperature());
    compressor.getInletStream().getFluid().init(2);
    compressor.getOutletStream().getFluid().init(2);
    double enthalpyRise = compressor.getOutletStream().getFluid().getEnthalpy()
        - compressor.getInletStream().getFluid().getEnthalpy();
    assertEquals(compressor.getPower(), enthalpyRise, 1.0e-3);
    assertEquals(1000.0, compressor.getOutletStream().getFlowRate("kg/hr"), 1.0e-9);
  }
}
