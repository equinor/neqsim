package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * Caloric-data-fitted five-parameter Mathias-Copeman alpha function for SRK-CPA water.
 *
 * <p>
 * This opt-in NeqSim calibration retains the standard water a0, b, association energy and association volume. Only the
 * attraction temperature dependence changes. The functional form follows the flexible-alpha approach of Palma, Queimada
 * and Coutinho (2017), doi:10.1021/acs.iecr.7b03522; the coefficients below are a new fit, not that paper's parameter
 * set. See docs/thermo/cpa_water_caloric.md for calibration and validation details.
 * </p>
 *
 * <p>
 * The inherited five-term polynomial and its analytic derivatives are independent of the cubic pressure form. The
 * parent class is reused for that algebra only; the enclosing phase remains SRK-CPA. Calibration covers pure liquid
 * water at 278.15-333.15 K near 1 bar. Independent caloric checks extend to 423.15 K and 100 bar; mixture,
 * near-critical and supercritical accuracy are not established. There is no temperature switch.
 * </p>
 */
public class AttractiveTermCPAWaterCaloric extends AttractiveTermMatCop5PRUMR {
  private static final long serialVersionUID = 1000;

  /**
   * Construct the calibrated water alpha function.
   *
   * @param component water component with the standard SRK-CPA pure-component parameters
   * @throws IllegalArgumentException if the component is not water
   */
  public AttractiveTermCPAWaterCaloric(ComponentEosInterface component) {
    super(component, new double[] {0.6670190973128074, -0.25656681951681287, 1.3687767086793399, -3.1891110889699097,
        5.218580308820144});
    if (!"water".equals(component.getComponentName())) {
      throw new IllegalArgumentException("The caloric CPA alpha calibration is only defined for water");
    }
  }

  /**
   * Store the legacy CPA parameter without changing the component's acentric factor or calibrated coefficients.
   *
   * @param value legacy single-parameter CPA alpha coefficient
   */
  @Override
  public void setm(double value) {
    m = value;
  }
}
