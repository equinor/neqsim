package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermRk class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermRk extends AttractiveTermBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for AttractiveTermRk.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermRk(ComponentEosInterface component) {
    super(component);
  }

  /** {@inheritDoc} */
  @Override
  public void init() {}

  /** {@inheritDoc} */
  @Override
  public AttractiveTermRk clone() {
    AttractiveTermRk attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermRk) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    return Math.sqrt(getComponent().getTC() / temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    return getComponent().geta() * alpha(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    return -0.5 * getComponent().getTC()
        / (Math.sqrt(getComponent().getTC() / temperature) * Math.pow(temperature, 2.0));
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    return -0.25 * getComponent().getTC() * getComponent().getTC()
        / (Math.pow(getComponent().getTC() / temperature, 3.0 / 2.0) * Math.pow(temperature, 4.0))
        + getComponent().getTC()
            / (Math.sqrt(getComponent().getTC() / temperature) * Math.pow(temperature, 3.0));
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
