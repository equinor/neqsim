package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
