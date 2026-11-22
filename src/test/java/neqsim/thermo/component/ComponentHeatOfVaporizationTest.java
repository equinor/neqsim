package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent equation, units and domain checks for coefficient-based latent heat.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ComponentHeatOfVaporizationTest {
  /**
   * Loads benzene using the public fluid API.
   *
   * @return database-backed component
   */
  private Component component() {
    SystemSrkEos fluid = new SystemSrkEos(300, 1);
    fluid.addComponent("benzene", 1);
    return (Component) fluid.getPhase(0).getComponent(0);
  }

  /** Verifies both API names use the reduced-temperature form and J/mol. */
  @Test
  void bothGettersUseReducedTemperatureAndAllCoefficients() {
    Component component = component();
    component.setTC(500);
    // At Tr=1/2 each exponent term contributes 0.1, giving 40000*(1/2)^0.4 J/mol.
    component.heatOfVaporizationCoefs = new double[] {4e7, 0.1, 0.2, 0.4, 0.8};
    assertEquals(30314.33133020796, component.getHeatOfVapourization(250), 1e-9);
    assertEquals(30314.33133020796, component.getPureComponentHeatOfVaporization(250), 1e-9);
  }

  /** Checks the shipped benzene equation evaluation without claiming its parameters are accurate. */
  @Test
  void benzeneEquationValueAndNearbyTrend() {
    Component component = component();
    // https://webbook.nist.gov/cgi/cbook.cgi?ID=C71432&Mask=4 : 30.72 kJ/mol at 353.3 K.
    double value = component.getHeatOfVapourization(353.3);
    // Independent scalar evaluation of the shipped coefficients at Tc=562.16 K.
    // This is 27.4% ABOVE the NIST value: parameter qualification remains open.
    assertEquals(39147.97348261978, value, 1e-7);
    assertTrue(component.getHeatOfVapourization(350) > value);
    assertTrue(component.getHeatOfVapourization(356) < value);
    assertEquals(value, component.getPureComponentHeatOfVaporization(353.3), 0);
  }

  /** Rejects invalid domains and missing parameters without silently returning a physical zero. */
  @Test
  void invalidOrMissingCorrelationReturnsNaN() {
    Component component = component();
    for (double temperature : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, component.getTC(),
        component.getTC() + 1}) {
      assertTrue(Double.isNaN(component.getHeatOfVapourization(temperature)));
      assertTrue(Double.isNaN(component.getPureComponentHeatOfVaporization(temperature)));
    }
    component.heatOfVaporizationCoefs = new double[5];
    assertTrue(Double.isNaN(component.getHeatOfVapourization(300)));
    component.heatOfVaporizationCoefs = new double[] {4e7, 0.5, 0, 0, Double.NaN};
    assertTrue(Double.isNaN(component.getHeatOfVapourization(300)));
    component.heatOfVaporizationCoefs = new double[] {4e7, -0.5, 0, 0, 0};
    assertTrue(Double.isNaN(component.getHeatOfVapourization(300)));
    component.setTC(Double.NaN);
    assertTrue(Double.isNaN(component.getHeatOfVapourization(300)));
  }

  /** Checks amount independence, cloning and nearby state evaluation. */
  @Test
  void amountAndCloneDoNotChangeMolarValue() {
    SystemSrkEos fluid = new SystemSrkEos(300, 1);
    fluid.addComponent("benzene", 1);
    double before = fluid.getPhase(0).getComponent(0).getHeatOfVapourization(353.3);
    fluid.setTotalNumberOfMoles(1000);
    assertEquals(before, fluid.getPhase(0).getComponent(0).getHeatOfVapourization(353.3), 0);
    SystemSrkEos copy = fluid.clone();
    copy.setTemperature(356);
    assertTrue(copy.getPhase(0).getComponent(0).getHeatOfVapourization(356) < before);
    assertEquals(before, fluid.getPhase(0).getComponent(0).getHeatOfVapourization(353.3), 0);
  }

  /** Checks an independent fixed-exponent equation value and exact reference identity. */
  @Test
  void watsonReferenceAndIndependentValue() {
    assertEquals(31875.128086545672, Component.calculateWatsonHeatOfVaporization(332, 353.3, 30720, 562.1), 1e-9);
    assertEquals(30720, Component.calculateWatsonHeatOfVaporization(353.3, 353.3, 30720, 562.1), 0);
  }

  /**
   * Compares Watson predictions with the complete six-row Dong et al. (1988) benzene subset at NIST.
   *
   * @param temperature target saturation temperature in K
   * @param reference published independent calorimetric value in J/mol
   */
  @ParameterizedTest
  @CsvSource({"307,33400", "314,33100", "324,32400", "332,31900", "344,31400", "353,30600"})
  void watsonBenzeneIndependentCalorimetry(double temperature, double reference) {
    // Fixed 0.38 exponent, Majer/Svoboda boiling anchor 30720 J/mol at 353.3 K; no fit to these six values.
    double estimated = Component.calculateWatsonHeatOfVaporization(temperature, 353.3, 30720, 562.1);
    assertEquals(reference, estimated, reference * 0.01);
  }

  /** Verifies the zero critical limit, representable neighboring point, trends and reciprocal scaling. */
  @Test
  void watsonLimitsAndReciprocity() {
    assertEquals(0, Component.calculateWatsonHeatOfVaporization(562.1, 353.3, 30720, 562.1), 0);
    assertTrue(Component.calculateWatsonHeatOfVaporization(Math.nextDown(562.1), 353.3, 30720, 562.1) > 0);
    double cold = Component.calculateWatsonHeatOfVaporization(300, 353.3, 30720, 562.1);
    double warm = Component.calculateWatsonHeatOfVaporization(400, 353.3, 30720, 562.1);
    assertTrue(cold > 30720 && warm < 30720);
    assertEquals(30720, Component.calculateWatsonHeatOfVaporization(353.3, 400, warm, 562.1), 1e-9);
  }

  /** Rejects malformed inputs and overflow rather than returning a plausible finite placeholder. */
  @Test
  void watsonInvalidInputsAndOverflow() {
    for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, -1}) {
      assertThrows(IllegalArgumentException.class,
          () -> Component.calculateWatsonHeatOfVaporization(bad, 353.3, 30720, 562.1));
      assertThrows(IllegalArgumentException.class,
          () -> Component.calculateWatsonHeatOfVaporization(332, bad, 30720, 562.1));
      assertThrows(IllegalArgumentException.class,
          () -> Component.calculateWatsonHeatOfVaporization(332, 353.3, bad, 562.1));
      assertThrows(IllegalArgumentException.class,
          () -> Component.calculateWatsonHeatOfVaporization(332, 353.3, 30720, bad));
    }
    assertThrows(IllegalArgumentException.class,
        () -> Component.calculateWatsonHeatOfVaporization(563, 353.3, 30720, 562.1));
    assertThrows(IllegalArgumentException.class,
        () -> Component.calculateWatsonHeatOfVaporization(332, 562.1, 30720, 562.1));
    assertThrows(IllegalArgumentException.class,
        () -> Component.calculateWatsonHeatOfVaporization(332, 600, 30720, 562.1));
    assertThrows(IllegalArgumentException.class,
        () -> Component.calculateWatsonHeatOfVaporization(100, 500, Double.MAX_VALUE, 562.1));
    assertThrows(IllegalArgumentException.class,
        () -> Component.calculateWatsonHeatOfVaporization(560, 300, Double.MIN_VALUE, 562.1));
  }

  /** Exercises the documented Java/Python-facing component adapter without changing coefficient-based behavior. */
  @Test
  void watsonComponentAdapterAndClone() {
    SystemSrkEos fluid = new SystemSrkEos(332, 1);
    fluid.addComponent("benzene", 1);
    ComponentInterface component = fluid.getPhase(0).getComponent(0);
    double before = component.getPureComponentHeatOfVaporization(353.3);
    double estimate = component.getHeatOfVaporizationWatson(332, 353.3, 30720);
    assertEquals(Component.calculateWatsonHeatOfVaporization(332, 353.3, 30720, component.getTC()), estimate, 0);
    fluid.setTotalNumberOfMoles(1000);
    assertEquals(estimate, fluid.getPhase(0).getComponent(0).getHeatOfVaporizationWatson(332, 353.3, 30720), 0);
    SystemSrkEos copy = fluid.clone();
    copy.setTemperature(400);
    assertTrue(copy.getPhase(0).getComponent(0).getHeatOfVaporizationWatson(400, 353.3, 30720) < estimate);
    assertEquals(before, component.getPureComponentHeatOfVaporization(353.3), 0);
  }

}
