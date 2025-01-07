package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermPrDanesh class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermPrDanesh extends AttractiveTermPr1978 {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double mMod = 0;

  /**
   * <p>
   * Constructor for AttractiveTermPrDanesh.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermPrDanesh(ComponentEosInterface component) {
    super(component);
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermPrDanesh clone() {
    AttractiveTermPrDanesh attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermPrDanesh) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    m = (0.37464 + 1.54226 * getComponent().getAcentricFactor()
        - 0.26992 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor());
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    if (temperature > getComponent().getTC()) {
      mMod = m * 1.21;
    } else {
      mMod = m;
    }
    return Math.pow(1.0 + mMod * (1.0 - Math.sqrt(temperature / getComponent().getTC())), 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    return getComponent().geta() * alpha(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    if (temperature > getComponent().getTC()) {
      mMod = m * 1.21;
    } else {
      mMod = m;
    }
    return -(1.0 + mMod * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * mMod
        / Math.sqrt(temperature / getComponent().getTC()) / getComponent().getTC();
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    if (temperature > getComponent().getTC()) {
      mMod = m * 1.21;
    } else {
      mMod = m;
    }

    return mMod * mMod / temperature / getComponent().getTC() / 2.0
        + (1.0 + mMod * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
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
