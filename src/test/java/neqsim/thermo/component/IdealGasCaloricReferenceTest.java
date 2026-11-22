package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent NIST Shomate comparisons and caloric identities for the existing component polynomials.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class IdealGasCaloricReferenceTest extends neqsim.NeqSimTest {
  /**
   * Checks the shipped coefficients against independent primary-reference values and differential identities.
   *
   * @param name database component name
   * @param temperature target temperature in K
   * @param referenceCp NIST ideal-gas Cp in J/(mol K)
   * @param referenceEnthalpy NIST enthalpy difference from 300 K in J/mol
   * @param referenceEntropy NIST entropy difference from 300 K in J/(mol K)
   */
  @ParameterizedTest
  @CsvFileSource(resources = "/neqsim/thermo/component/ideal_gas_caloric_nist.csv", numLinesToSkip = 1)
  void nistValuesAndDifferentialIdentities(String name, double temperature, double referenceCp,
      double referenceEnthalpy, double referenceEntropy) {
    ComponentInterface component = new ComponentSrk(name, 1, 1, 0);
    double cp = component.getCp0(temperature);
    assertEquals(referenceCp, cp, referenceCp * 0.02, "Cp reference");
    assertEquals(referenceEnthalpy, component.getHID(temperature, false) - component.getHID(300, false),
        referenceEnthalpy * 0.02, "sensible enthalpy difference reference");
    assertEquals(referenceEntropy, component.getIdEntropy(temperature) - component.getIdEntropy(300),
        referenceEntropy * 0.02, "entropy difference reference");
    assertTrue(cp > 0 && component.getCv0(temperature) > 0);
    assertEquals(ThermodynamicConstantsInterface.R, cp - component.getCv0(temperature), 1e-12);
    double step = 0.001;
    double derivativeH = (component.getHID(temperature + step, false) - component.getHID(temperature - step, false))
        / (2 * step);
    double derivativeS = (component.getIdEntropy(temperature + step) - component.getIdEntropy(temperature - step))
        / (2 * step);
    assertEquals(cp, derivativeH, 1e-6, "dH/dT=Cp");
    assertEquals(cp / temperature, derivativeS, 1e-8, "dS/dT=Cp/T");
    // A formation reference changes a constant offset, not the sensible enthalpy difference.
    assertEquals(component.getHID(temperature, false) - component.getHID(300, false),
        component.getHID(temperature, true) - component.getHID(300, true), 1e-8);
  }

  /**
   * Compares analytic H/S differences with independent composite-Simpson integration of the public Cp getter.
   *
   * @param name database component name
   */
  @ParameterizedTest
  @ValueSource(strings = {"methane", "CO2", "nitrogen"})
  void numericalIntegralsAndReferenceOffsets(String name) {
    ComponentInterface component = new ComponentSrk(name, 1, 1, 0);
    assertEquals(0, component.getHID(273.15, false), 1e-8);
    assertEquals(0, component.getIdEntropy(273.15), 1e-10);
    int intervals = 1000;
    double step = (800.0 - 300.0) / intervals;
    double integralH = 0;
    double integralS = 0;
    for (int i = 0; i <= intervals; i++) {
      double temperature = 300 + step * i;
      double weight = i == 0 || i == intervals ? 1 : i % 2 == 0 ? 2 : 4;
      integralH += weight * component.getCp0(temperature);
      integralS += weight * component.getCp0(temperature) / temperature;
    }
    assertEquals(integralH * step / 3, component.getHID(800, false) - component.getHID(300, false), 1e-6);
    assertEquals(integralS * step / 3, component.getIdEntropy(800) - component.getIdEntropy(300), 1e-8);
  }

  /**
   * Confirms component molar caloric properties do not change with fluid amount or clone state changes.
   *
   * @param name database component name
   */
  @ParameterizedTest
  @ValueSource(strings = {"methane", "CO2", "nitrogen"})
  void amountAndCloneIndependence(String name) {
    SystemSrkEos fluid = new SystemSrkEos(400, 1);
    fluid.addComponent(name, 1);
    ComponentInterface component = fluid.getPhase(0).getComponent(0);
    double cp = component.getCp0(400);
    double h = component.getHID(400, false);
    double s = component.getIdEntropy(400);
    fluid.setTotalNumberOfMoles(1000);
    SystemSrkEos copy = fluid.clone();
    copy.setTemperature(500);
    ComponentInterface cloned = copy.getPhase(0).getComponent(0);
    assertEquals(cp, cloned.getCp0(400), 0);
    assertEquals(h, cloned.getHID(400, false), 0);
    assertEquals(s, cloned.getIdEntropy(400), 0);
    assertTrue(cloned.getHID(500, false) > h);
    assertEquals(h, component.getHID(400, false), 0);
  }
}
