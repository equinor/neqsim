package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the Ramey temperature profile of the reservoir-package {@link TubingPerformance}.
 *
 * <p>
 * The closed-form profile for upward flow is T(z) = Tf(z) + g A + (Tbh - Tf(L) - g A) exp(-(L - z) / A). At very high
 * rate (A to infinity) the fluid stays at bottom-hole temperature; at very low rate (A to zero) it follows the
 * formation temperature.
 * </p>
 *
 * @author NeqSim development team
 */
class TubingPerformanceRameyTest {

  /**
   * Runs a single-phase gas tubing with the Ramey model.
   *
   * @param massRateKgPerSec flow rate in kg/s
   * @return tubing after the run
   */
  private TubingPerformance runRamey(double massRateKgPerSec) {
    SystemInterface gas = new SystemSrkEos(373.15, 300.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    gas.init(0);
    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(massRateKgPerSec, "kg/sec");
    inlet.setTemperature(100.0, "C");
    inlet.setPressure(300.0, "bara");
    inlet.run();

    TubingPerformance tubing = new TubingPerformance("tubing", inlet);
    tubing.setTubingLength(3000.0, "m");
    tubing.setTubingDiameter(0.1, "m");
    tubing.setNumberOfSegments(30);
    tubing.setTemperatureModel(TubingPerformance.TemperatureModel.RAMEY);
    tubing.setBottomHoleTemperature(100.0, "C");
    tubing.setWellheadTemperature(10.0, "C");
    tubing.setGeothermalGradient(0.03, "K/m");
    tubing.setOverallHeatTransferCoefficient(10.0);
    tubing.run();
    return tubing;
  }

  /**
   * At high rate the profile must stay close to the bottom-hole temperature, not fall by about 2 g (L - z) as it did
   * with the reversed-sign formula.
   */
  @Test
  void highRateStaysNearBottomHoleTemperature() {
    TubingPerformance tubing = runRamey(200.0);
    double[] t = tubing.getTemperatureProfile();
    double bh = 100.0 + 273.15;
    double top = t[t.length - 1];
    assertTrue(top > bh - 8.0, "wellhead T " + top + " should stay near BHT " + bh);
    assertTrue(top <= bh + 1e-6, "fluid cannot be hotter than the bottom hole");
  }

  /** Temperature must fall monotonically towards the wellhead and never below formation. */
  @Test
  void profileIsMonotonicAndAboveFormationTemperature() {
    TubingPerformance tubing = runRamey(5.0);
    double[] t = tubing.getTemperatureProfile();
    double[] z = tubing.getDepthProfile();
    for (int i = 1; i < t.length; i++) {
      assertTrue(t[i] <= t[i - 1] + 1e-9, "temperature must not rise towards the surface");
      double formation = 10.0 + 273.15 + 0.03 * z[i];
      assertTrue(t[i] >= formation - 1e-6, "fluid cannot be colder than the formation when producing");
    }
  }

  /** Higher rate gives a warmer wellhead. */
  @Test
  void wellheadTemperatureIncreasesWithRate() {
    double low = runRamey(2.0).getTemperatureProfile()[30];
    double high = runRamey(100.0).getTemperatureProfile()[30];
    assertTrue(high > low + 1.0, "high rate " + high + " should be warmer than low rate " + low);
  }
}
