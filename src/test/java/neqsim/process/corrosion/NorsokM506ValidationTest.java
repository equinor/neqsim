package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Validation and regression baselines for {@link NorsokM506CorrosionRate}.
 *
 * <p>
 * These tests anchor the model to the published de Waard-Milliams baseline equation and to the qualitative trends
 * required by NORSOK M-506, so that a change to the correlation or its constants is caught immediately.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class NorsokM506ValidationTest {

  /**
   * Verifies the baseline rate reproduces the de Waard-Milliams equation
   * {@code log10(CR) = 5.8 - 1710/T + 0.67*log10(fCO2)} at the model's own CO2 fugacity, and that near 20 C with about
   * 1 bar CO2 the classic ~1 mm/yr figure is recovered.
   */
  @Test
  void baselineMatchesDeWaardMilliams() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(20.0);
    model.setTotalPressureBara(10.0);
    model.setCO2MoleFraction(0.10); // partial pressure ~1 bar
    model.calculate();

    double fCO2 = model.calculateCO2Fugacity();
    double tempK = 20.0 + 273.15;
    double expected = Math.pow(10.0, 5.8 - 1710.0 / tempK + 0.67 * Math.log10(fCO2));

    double baseline = model.getBaselineCorrosionRate();
    double relError = Math.abs(baseline - expected) / expected;
    assertTrue(relError < 0.01,
        "baseline should match de Waard-Milliams within 1%, was " + baseline + " vs " + expected);

    // Classic literature anchor: ~1 mm/yr at 20 C, ~1 bar CO2.
    assertTrue(baseline > 0.6 && baseline < 1.3, "baseline near 20 C / 1 bar CO2 should be ~1 mm/yr, was " + baseline);
  }

  /**
   * Verifies the corrosion rate increases with CO2 partial pressure (higher fCO2 lowers pH and raises the rate).
   */
  @Test
  void rateIncreasesWithCO2PartialPressure() {
    double lowRate = rateAt(0.02);
    double highRate = rateAt(0.10);
    assertTrue(highRate > lowRate,
        "corrosion rate should increase with CO2 partial pressure: " + highRate + " vs " + lowRate);
  }

  /**
   * Builds a standard case at the given CO2 mole fraction and returns the corrected rate.
   *
   * @param co2MoleFraction CO2 mole fraction
   * @return the corrected corrosion rate in mm/yr
   */
  private double rateAt(double co2MoleFraction) {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(40.0);
    model.setTotalPressureBara(50.0);
    model.setCO2MoleFraction(co2MoleFraction);
    model.setFlowVelocityMs(2.0);
    model.setPipeDiameterM(0.2);
    model.calculate();
    return model.getCorrectedCorrosionRate();
  }

  /**
   * Verifies that lowering the pH below the CO2-saturation value increases the corrosion rate (Fpht behaviour).
   */
  @Test
  void lowerPHIncreasesRate() {
    NorsokM506CorrosionRate high = new NorsokM506CorrosionRate();
    high.setTemperatureCelsius(40.0);
    high.setTotalPressureBara(50.0);
    high.setCO2MoleFraction(0.05);
    high.setActualPH(5.5);
    high.calculate();

    NorsokM506CorrosionRate low = new NorsokM506CorrosionRate();
    low.setTemperatureCelsius(40.0);
    low.setTotalPressureBara(50.0);
    low.setCO2MoleFraction(0.05);
    low.setActualPH(3.8);
    low.calculate();

    assertTrue(low.getCorrectedCorrosionRate() > high.getCorrectedCorrosionRate(),
        "lower pH should give a higher corrosion rate");
  }

  /**
   * Verifies that the FeCO3 scaling temperature decreases as CO2 fugacity increases, per the NORSOK M-506 Tscale
   * relation.
   */
  @Test
  void scalingTemperatureFallsWithCO2() {
    NorsokM506CorrosionRate lowCO2 = new NorsokM506CorrosionRate();
    lowCO2.setTemperatureCelsius(60.0);
    lowCO2.setTotalPressureBara(50.0);
    lowCO2.setCO2MoleFraction(0.01);
    lowCO2.calculate();

    NorsokM506CorrosionRate highCO2 = new NorsokM506CorrosionRate();
    highCO2.setTemperatureCelsius(60.0);
    highCO2.setTotalPressureBara(50.0);
    highCO2.setCO2MoleFraction(0.20);
    highCO2.calculate();

    assertTrue(highCO2.calculateScalingTemperature() < lowCO2.calculateScalingTemperature(),
        "higher CO2 fugacity should lower the FeCO3 scaling temperature");
  }
}
