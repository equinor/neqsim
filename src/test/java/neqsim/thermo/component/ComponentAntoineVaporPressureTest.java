package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for vapor-pressure correlation selection and its temperature derivative. */
class ComponentAntoineVaporPressureTest extends neqsim.NeqSimTest {
  /**
   * Load a component through the public fluid API and the shipped database.
   *
   * @param name database component name
   * @return the component on the first phase
   */
  private Component component(String name) {
    SystemSrkEos system = new SystemSrkEos(298.15, 1.0);
    system.addComponent(name, 1.0);
    return (Component) system.getPhase(0).getComponent(0);
  }

  @ParameterizedTest
  @CsvSource({"i-pentane, 0.91800973328146", "propanePVTsim, 9.5325713901722", "nbutanePVTsim, 2.43660679085882",
      "Piperazine, 0.000503106903946668"})
  void dipprDatabaseRowsReturnPressureInBar(String name, double expected) {
    Component component = component(name);
    assertEquals(expected, component.getAntoineVaporPressure(298.15), expected * 1.0e-10);
    assertTrue(component.getAntoineVaporPressure(298.15) < component.getPC());
  }

  @ParameterizedTest
  @ValueSource(doubles = {290.0, 298.15, 301.0})
  void isopentaneAgreesWithIndependentNistCorrelation(double temperature) {
    // NIST WebBook, CAS 78-78-4, Willingham et al. (1945), valid 289.44-301.74 K.
    // https://webbook.nist.gov/cgi/cbook.cgi?ID=C78784&Mask=4
    double referenceBar = Math.pow(10.0, 3.91457 - 1020.012 / (temperature - 40.053));
    assertEquals(referenceBar, component("i-pentane").getAntoineVaporPressure(temperature), referenceBar * 0.01);
  }

  @ParameterizedTest
  @CsvSource({"i-pentane, 280.0", "i-pentane, 298.15", "i-pentane, 320.0", "propanePVTsim, 280.0",
      "propanePVTsim, 298.15", "propanePVTsim, 320.0", "nbutanePVTsim, 280.0", "nbutanePVTsim, 298.15",
      "nbutanePVTsim, 320.0", "Piperazine, 400.0"})
  void dipprDerivativeMatchesFiniteDifference(String name, double temperature) {
    Component component = component(name);
    double step = 1.0e-3;
    double numerical = (component.getAntoineVaporPressure(temperature + step)
        - component.getAntoineVaporPressure(temperature - step)) / (2.0 * step);
    assertTrue(numerical > 0.0);
    assertEquals(numerical, component.getAntoineVaporPressuredT(temperature), numerical * 1.0e-7);
  }

  @ParameterizedTest
  @CsvSource({"i-pentane, 280.0", "i-pentane, 298.15", "i-pentane, 320.0", "propanePVTsim, 280.0",
      "propanePVTsim, 298.15", "propanePVTsim, 320.0", "nbutanePVTsim, 280.0", "nbutanePVTsim, 298.15",
      "nbutanePVTsim, 320.0"})
  void dipprPressureTemperatureRoundTrip(String name, double temperature) {
    Component component = component(name);
    double pressure = component.getAntoineVaporPressure(temperature);
    assertEquals(temperature, component.getAntoineVaporTemperature(pressure), 1.0e-4);
  }

  @ParameterizedTest
  @ValueSource(strings = {"log", "exp", "legacy-dippr"})
  void dipprExponentTakesPrecedenceOverExponentialLabels(String label) {
    Component component = component("i-pentane");
    component.antoineLiqVapPresType = label;
    assertEquals(0.91800973328146, component.getAntoineVaporPressure(298.15), 1.0e-12);
    double step = 1.0e-3;
    double numerical = (component.getAntoineVaporPressure(298.15 + step)
        - component.getAntoineVaporPressure(298.15 - step)) / (2.0 * step);
    assertEquals(numerical, component.getAntoineVaporPressuredT(298.15), numerical * 1.0e-7);
  }

  @ParameterizedTest
  @ValueSource(strings = {"log", "exp"})
  void zeroExponentRetainsLegacyExponentialCorrelation(String label) {
    Component component = component("i-pentane");
    component.antoineLiqVapPresType = label;
    component.AntoineA = Math.log(2.0) + 10.0;
    component.AntoineB = 3000.0;
    component.AntoineC = 0.0;
    component.AntoineE = 0.0;
    assertEquals(2.0, component.getAntoineVaporPressure(300.0), 1.0e-12);
    assertEquals(1.0 / 15.0, component.getAntoineVaporPressuredT(300.0), 1.0e-12);
  }

  @ParameterizedTest
  @ValueSource(strings = {"pow10", "pow10KPa"})
  void explicitBaseTenLabelsRetainPrecedence(String label) {
    Component component = component("i-pentane");
    component.antoineLiqVapPresType = label;
    component.AntoineA = 6.0;
    component.AntoineB = 300.0;
    component.AntoineC = "pow10".equals(label) ? 273.15 : 0.0;
    // Retain the nonzero E to guard the existing priority of explicit base-ten labels.
    double expected = "pow10".equals(label) ? 1.0e5 : 1.0;
    assertEquals(expected, component.getAntoineVaporPressure(300.0), expected * 1.0e-12);
    double expectedDerivative = expected * Math.log(10.0) / 300.0;
    assertEquals(expectedDerivative, component.getAntoineVaporPressuredT(300.0), expectedDerivative * 1.0e-12);
  }

  @Test
  void zeroExponentRetainsWagnerFallback() {
    Component component = component("i-pentane");
    component.antoineLiqVapPresType = "loglog";
    component.AntoineE = 0.0;
    assertEquals(component.getPC(), component.getAntoineVaporPressure(component.getTC()), 1.0e-12);
  }
}
