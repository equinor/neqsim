package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * PR-CPA component with the shared PR-based CPA association derivatives and generic PR-CPA parameters.
 *
 * <p>
 * Reuses the association implementation of {@link ComponentUMRCPA} without selecting its dedicated UMR parameter or
 * alpha-function families. Components with valid fitted PR-CPA energy and co-volume parameters use those parameters;
 * other components retain their ordinary PR physical term.
 * </p>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public class ComponentPrCPA extends ComponentUMRCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Creates a component using generic PR-CPA component-database parameters.
   *
   * @param name component name
   * @param moles total component amount in mol
   * @param molesInPhase component amount in this phase in mol
   * @param compIndex component index
   */
  public ComponentPrCPA(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    setAttractiveTerm(1);
  }

  /**
   * Creates a component from critical properties using the ordinary PR physical term.
   *
   * @param number unused legacy component number
   * @param TC critical temperature in K
   * @param PC critical pressure in bara
   * @param M molar mass in kg/mol
   * @param a acentric factor
   * @param moles component amount in mol
   */
  public ComponentPrCPA(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
    setAttractiveTerm(1);
  }

  /** {@inheritDoc} */
  @Override
  protected boolean useUmrParameters() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public ComponentPrCPA clone() {
    return (ComponentPrCPA) super.clone();
  }

  /**
   * Retains the legacy whole-array site-fraction setter with owned storage.
   *
   * @param fractions unbonded association-site fractions
   */
  public void setXsite(double[] fractions) {
    xsite = fractions.clone();
  }

  /**
   * Retains the legacy Carnahan-Starling composition-derivative alias.
   *
   * @param phase phase providing volume and co-volume
   * @return logarithmic radial-distribution composition derivative
   */
  public double calc_lngi2(PhaseInterface phase) {
    return calc_lngi(phase);
  }
}
