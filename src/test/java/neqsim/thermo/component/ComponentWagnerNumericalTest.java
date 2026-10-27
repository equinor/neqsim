package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Numerical API checks with prescribed Wagner coefficients, not a fitted substance model.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ComponentWagnerNumericalTest extends neqsim.NeqSimTest {
  /**
   * Build through the public fluid route and prescribe a synthetic correlation.
   *
   * @param amount component amount in mol
   * @return component with Tc=500 K, Pc=50 bar and triple point 100 K
   */
  private Component correlation(double amount) {
    SystemSrkEos fluid = new SystemSrkEos(350, 1);
    fluid.addComponent("i-pentane", amount);
    fluid.setMixingRule("classic");
    Component component = (Component) fluid.getPhase(0).getComponent(0);
    component.antoineLiqVapPresType = "loglog";
    component.AntoineA = -7;
    component.AntoineB = 1;
    component.AntoineC = -2;
    component.AntoineD = -3;
    component.AntoineE = 0;
    component.setTC(500);
    component.setPC(50);
    component.setTriplePointTemperature(100);
    return component;
  }

  /**
   * Compare independent 80-digit Decimal evaluations and numerical derivatives; inverse targets are frozen.
   *
   * @param temperature target K
   * @param pressure reference bar
   * @param slope reference bar/K
   */
  @ParameterizedTest
  @CsvSource({"150, 8.89807877062441314e-7, 1.75319161790374349e-7",
      "200, 0.00105311589441933540, 0.000106184675133528184", "250, 0.0510668248182563771, 0.00304123022228978546",
      "350, 2.90517637138374536, 0.0797486275493922320", "450, 23.7398742106103048, 0.386733379748037291",
      "490, 43.4684629701862495, 0.614790913751241838", "499.5, 49.6524458370646737, 0.691808888660374114",
      "500, 50, 0.7"})
  void independentPressureDerivativeAndInverse(double temperature, double pressure, double slope) {
    Component component = correlation(1);
    assertEquals(pressure, component.getAntoineVaporPressure(temperature), pressure * 2e-13);
    assertEquals(slope, component.getAntoineVaporPressuredT(temperature), slope * 2e-13);
    assertEquals(temperature, component.getAntoineVaporTemperature(pressure), 1e-3);
  }

  /**
   * Verify central-difference refinement away from the critical endpoint.
   *
   * @param temperature target K
   */
  @ParameterizedTest
  @ValueSource(doubles = {200, 350, 450})
  void derivativeRefinement(double temperature) {
    Component component = correlation(1);
    double derivative = component.getAntoineVaporPressuredT(temperature);
    double previous = Double.POSITIVE_INFINITY;
    for (double step : new double[] {1, 0.1, 0.01, 0.001}) {
      double numerical = (component.getAntoineVaporPressure(temperature + step)
          - component.getAntoineVaporPressure(temperature - step)) / (2 * step);
      double error = Math.abs(numerical - derivative);
      assertTrue(error < previous);
      previous = error;
    }
    assertTrue(previous < Math.abs(derivative) * 1e-7);
  }

  /**
   * Check intensive values, isolated clone coefficients and repeated/state-order evaluation.
   *
   * @param amount component amount in mol
   */
  @ParameterizedTest
  @ValueSource(doubles = {0.001, 1, 1000})
  void amountAndCloneLifecycle(double amount) {
    Component original = correlation(amount);
    Component reference = correlation(1);
    double initial = original.getAntoineVaporPressure(350);
    Component copy = original.clone();
    copy.AntoineA = -6;
    assertTrue(Math.abs(copy.getAntoineVaporPressure(350) - initial) > 0.1);
    for (double temperature : new double[] {450, 150, 350, 150, 450}) {
      assertEquals(reference.getAntoineVaporPressure(temperature), original.getAntoineVaporPressure(temperature), 0);
      assertEquals(reference.getAntoineVaporPressuredT(temperature), original.getAntoineVaporPressuredT(temperature),
          0);
    }
    assertEquals(initial, original.getAntoineVaporPressure(350), 0);
  }

  /** Check the analytical critical limits and the adjacent representable states. */
  @Test
  void criticalEndpoint() {
    Component component = correlation(1);
    assertEquals(50, component.getAntoineVaporPressure(500), 0);
    assertEquals(0.7, component.getAntoineVaporPressuredT(500), 1e-15);
    assertTrue(Double.isFinite(component.getAntoineVaporPressuredT(Math.nextDown(500.0))));
    assertTrue(Double.isNaN(component.getAntoineVaporPressure(Math.nextUp(500.0))));
    assertTrue(Double.isNaN(component.getAntoineVaporPressuredT(Math.nextUp(500.0))));
  }

  /** Check dimensional pressure/derivative scaling and inverse identity. */
  @Test
  void pressureScale() {
    Component component = correlation(1);
    double pressure = component.getAntoineVaporPressure(350);
    double derivative = component.getAntoineVaporPressuredT(350);
    component.setPC(100);
    assertEquals(2 * pressure, component.getAntoineVaporPressure(350), 0);
    assertEquals(2 * derivative, component.getAntoineVaporPressuredT(350), 0);
    assertEquals(350, component.getAntoineVaporTemperature(2 * pressure), 1e-3);
  }

  /** Check invalid states/targets and an unattainable sub-triple-point inverse target. */
  @Test
  void refusedInputs() {
    Component component = correlation(1);
    for (double temperature : new double[] {0, -1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertTrue(Double.isNaN(component.getAntoineVaporPressure(temperature)));
      assertTrue(Double.isNaN(component.getAntoineVaporPressuredT(temperature)));
    }
    for (double pressure : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Math.nextUp(50.0),
        Double.MIN_VALUE}) {
      assertTrue(Double.isNaN(component.getAntoineVaporTemperature(pressure)));
    }
  }
}
