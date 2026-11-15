package neqsim.process.safety.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.safety.fire.PfpDemandCalculator.FireType;
import neqsim.process.safety.fire.PfpDemandCalculator.PfpDemandResult;
import neqsim.process.safety.fire.PfpDemandCalculator.PfpRating;

/**
 * Unit tests for {@link PfpDemandCalculator}.
 *
 * @author ESOL
 * @version 1.0
 */
public class PfpDemandCalculatorTest {

  /**
   * A thin wall in a pool fire reaches critical temperature quickly, so PFP is required and an H-rating is selected.
   */
  @Test
  public void testPoolFireRequiresPfp() {
    PfpDemandResult res = new PfpDemandCalculator(100.0e3, 0.012).setFireType(FireType.POOL).evaluate(3600.0);

    assertTrue(res.getBareSteelTimeToCriticalS() < 3600.0);
    assertTrue(res.isPfpRequired());
    assertEquals(28.057, res.getRequiredPfpThicknessMm(), 0.001);
    assertEquals(PfpRating.H60, res.getRating());
  }

  /**
   * A jet fire over 120 min should select a J120 rating.
   */
  @Test
  public void testJetFireLongDurationRating() {
    PfpDemandResult res = new PfpDemandCalculator(250.0e3, 0.012).setFireType(FireType.JET).evaluate(7200.0);
    assertTrue(res.isPfpRequired());
    assertEquals(PfpRating.J120, res.getRating());
  }

  /**
   * A very thick wall with a short survival requirement may not need PFP.
   */
  @Test
  public void testThickWallNoPfp() {
    PfpDemandResult res = new PfpDemandCalculator(100.0e3, 0.20).setFireType(FireType.POOL).evaluate(600.0);
    assertFalse(res.isPfpRequired());
    assertEquals(PfpRating.NONE, res.getRating());
    assertEquals(0.0, res.getRequiredPfpThicknessMm(), 1.0e-9);
  }

  /**
   * Reject a critical steel temperature at or below the initial temperature in both calculation methods.
   *
   * @param criticalTemperatureK critical steel temperature in K
   */
  @ParameterizedTest
  @ValueSource(doubles = {288.15, 273.15})
  public void testRejectsCriticalTemperatureAtOrBelowInitial(double criticalTemperatureK) {
    PfpDemandCalculator calculator = new PfpDemandCalculator(100.0e3, 0.012)
        .setCriticalTemperatureK(criticalTemperatureK);

    assertThrows(IllegalArgumentException.class, calculator::bareSteelTimeToCriticalS);
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> calculator.evaluate(3600.0));
    assertTrue(exception.getMessage().contains("criticalTemperatureK"));
    assertTrue(exception.getMessage().contains("initialTemperatureK"));
  }

  /**
   * A positive initial steel temperature is required for calculations in kelvin.
   */
  @Test
  public void testRejectsNonpositiveInitialTemperature() {
    PfpDemandCalculator calculator = new PfpDemandCalculator(100.0e3, 0.012).setInitialTemperatureK(0.0);

    assertThrows(IllegalArgumentException.class, calculator::bareSteelTimeToCriticalS);
  }

  /**
   * Reject nonfinite values for each of the three configurable temperatures.
   *
   * @param temperatureK nonfinite temperature in K
   */
  @ParameterizedTest
  @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY})
  public void testRejectsNonfiniteTemperatures(double temperatureK) {
    PfpDemandCalculator calculator = new PfpDemandCalculator(100.0e3, 0.012);

    calculator.setInitialTemperatureK(temperatureK);
    assertThrows(IllegalArgumentException.class, calculator::bareSteelTimeToCriticalS);
    calculator.setInitialTemperatureK(288.15).setCriticalTemperatureK(temperatureK);
    assertThrows(IllegalArgumentException.class, calculator::bareSteelTimeToCriticalS);
    calculator.setCriticalTemperatureK(673.15).setFireTemperatureK(temperatureK);
    assertThrows(IllegalArgumentException.class, () -> calculator.evaluate(3600.0));
  }

  /**
   * Reject a fire at or below the mean steel temperature, including when the bare wall would survive.
   *
   * @param fireTemperatureK effective fire temperature in K
   */
  @ParameterizedTest
  @ValueSource(doubles = {480.65, 400.0})
  public void testRejectsFireTemperatureAtOrBelowSteelMean(double fireTemperatureK) {
    PfpDemandCalculator calculator = new PfpDemandCalculator(100.0e3, 0.012).setFireTemperatureK(fireTemperatureK);

    assertTrue(calculator.bareSteelTimeToCriticalS() > 60.0);
    assertThrows(IllegalArgumentException.class, () -> calculator.evaluate(60.0));
    assertThrows(IllegalArgumentException.class, () -> calculator.evaluate(3600.0));
  }

  /**
   * The calculated thickness scales with the actual driving temperature below 1 K.
   *
   * @param fireTemperatureK effective fire temperature in K
   */
  @ParameterizedTest
  @ValueSource(doubles = {480.9, 481.0})
  public void testUsesPositiveDrivingTemperatureBelowOneKelvin(double fireTemperatureK) {
    PfpDemandResult result = new PfpDemandCalculator(100.0e3, 0.012).setFireTemperatureK(fireTemperatureK)
        .evaluate(3600.0);
    double allowableFlux = 7850.0 * 0.012 * 490.0 * 385.0 / 3600.0;
    double expectedThicknessM = 0.2 * (fireTemperatureK - 480.65) / allowableFlux;

    assertTrue(result.isPfpRequired());
    assertEquals(expectedThicknessM, result.getRequiredPfpThicknessM(), 1.0e-12);
  }
}
