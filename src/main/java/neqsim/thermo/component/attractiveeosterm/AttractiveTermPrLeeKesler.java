package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * Alternative alpha function for the Peng-Robinson EOS using a Soave-form m-factor.
 *
 * <p>
 * Uses the Soave (SRK) m-factor correlation applied within the PR EOS framework:
 *
 * <pre>
 *   m = 0.480 + 1.574 \omega - 0.176 \omega^2
 * </pre>
 *
 * <p>
 * <b>Note:</b> Despite the class name, this implements the Soave m-factor — NOT the standard
 * PR76 m-factor and NOT the Lee-Kesler BWR enthalpy method. {@link SystemPrLeeKeslerEos}
 * uses {@code attractiveTermNumber=1} (standard PR76 alpha), not this class. This class is
 * retained at dispatch index 21 for backward compatibility and research purposes.
 *
 * <p>
 * The alpha function form is:
 *
 * <pre>
 *   \alpha(T) = \left[1 + m \left(1 - \sqrt{T / T_c}\right)\right]^2
 * </pre>
 *
 * @author Even Solbraa
 */
public class AttractiveTermPrLeeKesler extends AttractiveTermPr {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructs a Lee-Kesler alpha term for the given PR component.
   *
   * @param component the EOS component holding critical properties and acentric factor
   */
  public AttractiveTermPrLeeKesler(ComponentEosInterface component) {
    super(component);
    m = 0.480 + 1.574 * component.getAcentricFactor()
        - 0.176 * component.getAcentricFactor() * component.getAcentricFactor();
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermPrLeeKesler clone() {
    AttractiveTermPrLeeKesler attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermPrLeeKesler) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    m = 0.480 + 1.574 * getComponent().getAcentricFactor()
        - 0.176 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor();
  }

  /**
   * Back-calculates the acentric factor from m using the LK/Soave quadratic.
   *
   * <p>
   * Solves: -0.176 * omega^2 + 1.574 * omega + (0.480 - m) = 0
   */
  @Override
  public void setm(double val) {
    this.m = val;
    neqsim.mathlib.nonlinearsolver.NewtonRhapson solve =
        new neqsim.mathlib.nonlinearsolver.NewtonRhapson();
    solve.setOrder(2);
    double[] acentricConstants = {-0.176, 1.574, (0.480 - this.m)};
    solve.setConstants(acentricConstants);
    getComponent().setAcentricFactor(solve.solve(0.2));
  }
}
