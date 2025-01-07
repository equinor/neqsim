package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermTwu class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermTwu extends AttractiveTermSrk {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for AttractiveTermTwu.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermTwu(ComponentEosInterface component) {
    super(component);
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermTwu clone() {
    AttractiveTermTwu attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermTwu) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    m = (0.48 + 1.574 * getComponent().getAcentricFactor()
        - 0.175 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor());
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    return Math.pow(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC())), 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    return getComponent().geta() * alpha(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    return -(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
        / Math.sqrt(temperature / getComponent().getTC()) / getComponent().getTC();
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    return m * m / temperature / getComponent().getTC() / 2.0
        + (1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
            / Math.sqrt(
                temperature * temperature * temperature / (Math.pow(getComponent().getTC(), 3.0)))
            / (getComponent().getTC() * getComponent().getTC()) / 2.0;
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
