package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Regression tests for the legacy pow10KPa pressure scale and its temperature derivative. */
class ComponentPow10KPaVaporPressureTest extends neqsim.NeqSimTest {
  /**
   * Build a prescribed analytical correlation, not a physical fit for the database component.
   *
   * @param offset temperature offset in K
   * @param exponent unused DIPPR exponent that must not override the explicit base-ten label
   * @return component with pressure 2 bar at T + offset = 300 K
   */
  private Component correlation(double offset, double exponent) {
    Component component = new ComponentSrk("i-pentane", 1.0, 1.0, 0);
    component.antoineLiqVapPresType = "pow10KPa";
    component.AntoineA = Math.log10(2.0) + 6.0;
    component.AntoineB = 300.0;
    component.AntoineC = offset;
    component.AntoineD = 19.0;
    component.AntoineE = exponent;
    return component;
  }

  @ParameterizedTest
  @CsvSource({ "260.0, 0.0, 0.0", "300.0, 0.0, 0.0", "350.0, 0.0, 0.0", "260.0, 25.0, 2.0", "300.0, 25.0, 2.0",
      "350.0, 25.0, 2.0" })
  void derivativeMatchesAnalyticalValueAndFiniteDifference(double temperature, double offset, double exponent) {
    Component component = correlation(offset, exponent);
    assertTrue(component.hasAntoineVaporPressureCorrelation());
    // Independent normalization: P = 2 bar when T + C = 300 K.
    double expectedPressure = 2.0 * Math.pow(10.0, 1.0 - 300.0 / (temperature + offset));
    double expectedDerivative = expectedPressure * Math.log(10.0) * 300.0 / Math.pow(temperature + offset, 2.0);
    assertEquals(expectedPressure, component.getAntoineVaporPressure(temperature), expectedPressure * 1.0e-12);
    double derivative = component.getAntoineVaporPressuredT(temperature);
    assertTrue(Double.isFinite(derivative) && derivative > 0.0);
    assertEquals(expectedDerivative, derivative, expectedDerivative * 1.0e-12);
    double step = 1.0e-3;
    double numerical = (component.getAntoineVaporPressure(temperature + step)
        - component.getAntoineVaporPressure(temperature - step)) / (2.0 * step);
    assertEquals(numerical, derivative, expectedDerivative * 1.0e-7);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 260.0, 300.0, 350.0 })
  void inverseTemperatureRecoversTarget(double temperature) {
    Component component = correlation(0.0, 0.0);
    double pressure = component.getAntoineVaporPressure(temperature);
    double recovered = component.getAntoineVaporTemperature(pressure);
    assertEquals(temperature, recovered, 1.0e-4);
    assertEquals(pressure, component.getAntoineVaporPressure(recovered), pressure * 1.0e-7);
  }

  @Test
  void derivativePreservesUnavailableAndInapplicableResults() {
    Component component = correlation(0.0, 2.0);
    for (double temperature : new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        component.getTC() + 1.0 }) {
      assertTrue(Double.isNaN(component.getAntoineVaporPressuredT(temperature)));
    }
    component.antoineLiqVapPresType = "none";
    assertTrue(Double.isNaN(component.getAntoineVaporPressuredT(300.0)));
    component = new ComponentSrk("Na+", 1.0, 1.0, 0);
    component.antoineLiqVapPresType = "pow10KPa";
    assertTrue(Double.isNaN(component.getAntoineVaporPressuredT(300.0)));
  }
}
