package neqsim.thermo.component.attractiveeosterm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrk;

/**
 * Regression tests for inversion of the SRK acentric-factor polynomial.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class AttractiveTermSrkParameterTest {
  /**
   * Preserve conventional roots, including values outside the former solver clamps.
   */
  @Test
  void parameterSurvivesInitializationOnConventionalBranch() {
    ComponentSrk component = newComponent(0);
    AttractiveTermInterface term = component.getAttractiveTerm();
    for (double acentricFactor : new double[] {-1.0, -0.99, -0.5, 0.0, 1.0e-12, 0.25, 1.5, 2.0, 4.0}) {
      double expectedM = parameter(acentricFactor);
      term.setm(expectedM);
      assertEquals(acentricFactor, component.getAcentricFactor(), 1.0e-12);
      assertEquals(expectedM, term.getm(), 1.0e-12);
      term.init();
      term.init();
      assertEquals(expectedM, term.getm(), 1.0e-12);
    }
  }

  /**
   * Accept the stationary root where the quadratic discriminant is zero.
   */
  @Test
  void maximumParameterHasARealRepeatedRoot() {
    ComponentSrk component = newComponent(0);
    double maximumM = 0.48 + 1.574 * 1.574 / (4.0 * 0.176);
    component.getAttractiveTerm().setm(maximumM);
    assertEquals(1.574 / (2.0 * 0.176), component.getAcentricFactor(), 1.0e-7);
    component.getAttractiveTerm().init();
    assertEquals(maximumM, component.getAttractiveTerm().getm(), 1.0e-12);
  }

  /**
   * Refuse corrupt fits and non-finite parameters without replacing prior component data.
   */
  @Test
  void invalidParameterLeavesComponentAndTermUnchanged() {
    ComponentSrk component = newComponent(0);
    AttractiveTermInterface term = component.getAttractiveTerm();
    double originalAcentricFactor = component.getAcentricFactor();
    double originalM = term.getm();
    double minimumM = 0.48 - 1.574 - 0.176;
    double maximumM = 0.48 + 1.574 * 1.574 / (4.0 * 0.176);
    for (double invalidM : new double[] {-7165.19, Math.nextDown(minimumM), Math.nextUp(maximumM), Double.NaN,
        Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> term.setm(invalidM));
      assertEquals(originalAcentricFactor, component.getAcentricFactor());
      assertEquals(originalM, term.getm());
    }
  }

  /**
   * Keep inherited CPA and Mathias-Copeman initialization synchronized with the fitted parameter.
   */
  @Test
  void inheritedSrkSettersPreserveTheParameter() {
    for (int attractiveTermNumber : new int[] {4, 15}) {
      ComponentSrk component = newComponent(attractiveTermNumber);
      AttractiveTermInterface term = component.getAttractiveTerm();
      double expectedM = parameter(2.0);
      term.setm(expectedM);
      term.init();
      assertEquals(2.0, component.getAcentricFactor(), 1.0e-12);
      assertEquals(expectedM, term.getm(), 1.0e-12);
      assertEquals(expectedM, term.getParameters(0), 1.0e-12);
    }
  }

  /**
   * Create a synthetic component without accessing the component database.
   *
   * @param attractiveTermNumber registered attractive-term formulation
   * @return component with an attached attractive term
   */
  private ComponentSrk newComponent(int attractiveTermNumber) {
    ComponentSrk component = new ComponentSrk(0, 600.0, 20.0, 0.2, 0.25, 1.0);
    component.setComponentName("synthetic");
    component.setAttractiveTerm(attractiveTermNumber);
    return component;
  }

  /**
   * Evaluate the forward SRK parameter polynomial independently of its inverse.
   *
   * @param acentricFactor conventional acentric factor
   * @return SRK parameter
   */
  private double parameter(double acentricFactor) {
    return 0.48 + 1.574 * acentricFactor - 0.176 * acentricFactor * acentricFactor;
  }
}
