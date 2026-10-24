package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * AttractiveTermSrk class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermSrk extends AttractiveTermBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for AttractiveTermSrk.
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermSrk(ComponentEosInterface component) {
    super(component);
    m = (0.48 + 1.574 * component.getAcentricFactor()
        - 0.176 * component.getAcentricFactor() * component.getAcentricFactor());
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermSrk clone() {
    AttractiveTermSrk attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermSrk) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return attractiveTerm;
  }

  /**
   * Set the SRK parameter and its corresponding acentric factor.
   *
   * <p>
   * The lower root of {@code m = 0.48 + 1.574 * omega - 0.176 * omega * omega} is used, consistent with the
   * conventional SRK branch. The admissible range is from {@code omega = -1} to the quadratic maximum at
   * {@code omega = 1.574 / (2 * 0.176)}. Invalid parameters leave both this term and its component unchanged.
   * </p>
   *
   * @param val finite SRK parameter on the conventional branch
   * @throws IllegalArgumentException if the parameter has no real root on the admissible branch
   */
  @Override
  public void setm(double val) {
    double minimumM = 0.48 - 1.574 - 0.176;
    double maximumM = 0.48 + 1.574 * 1.574 / (4.0 * 0.176);
    if (!Double.isFinite(val) || val < minimumM || val > maximumM) {
      throw new IllegalArgumentException("SRK m must be finite and between " + minimumM + " and " + maximumM
          + " to imply an acentric factor on the conventional branch: " + val);
    }
    double shiftedM = val - 0.48;
    // Rationalizing the smaller root avoids cancellation near m = 0.48. The maximum
    // can produce a slightly negative discriminant through floating-point rounding.
    double discriminant = Math.max(0.0, 1.574 * 1.574 - 4.0 * 0.176 * shiftedM);
    double acentricFactor = 2.0 * shiftedM / (1.574 + Math.sqrt(discriminant));
    this.m = val;
    getComponent().setAcentricFactor(acentricFactor);
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    m = (0.48 + 1.574 * getComponent().getAcentricFactor()
        - 0.176 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor());
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    // System.out.println("m " + m);
    // System.out.println("TC " + component.getTC());
    double temp = 1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()));
    return temp * temp;
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    return getComponent().geta() * alpha(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    // System.out.println("m " + m);
    double temp = Math.sqrt(temperature / getComponent().getTC());
    return -(1.0 + m * (1.0 - temp)) * m / temp / getComponent().getTC();
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    double tr = temperature / getComponent().getTC();
    return m * m / temperature / getComponent().getTC() / 2.0 + (1.0 + m * (1.0 - Math.sqrt(tr))) * m
        / Math.sqrt(tr * tr * tr) / (getComponent().getTC() * getComponent().getTC()) / 2.0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    return getComponent().geta() * diffalphaT(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    return getComponent().geta() * diffdiffalphaT(temperature);
  }
}
