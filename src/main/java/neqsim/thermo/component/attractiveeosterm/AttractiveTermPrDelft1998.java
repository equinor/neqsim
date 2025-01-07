package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermPrDelft1998 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermPrDelft1998 extends AttractiveTermPr1978 {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  boolean isMethane = false;

  /**
   * <p>
   * Constructor for AttractiveTermPrDelft1998.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermPrDelft1998(ComponentEosInterface component) {
    super(component);
    if (component.getName().equals("methane")) {
      isMethane = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermPrDelft1998 clone() {
    AttractiveTermPrDelft1998 attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermPrDelft1998) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    if (isMethane) {
      return 0.969617 + 0.20089 * temperature / getComponent().getTC()
          - 0.3256987 * Math.pow(temperature / getComponent().getTC(), 2.0)
          + 0.06653 * Math.pow(temperature / getComponent().getTC(), 3.0);
    } else {
      return Math.pow(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC())), 2.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    return getComponent().geta() * alpha(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    if (isMethane) {
      return 0.20089 / getComponent().getTC()
          - 2.0 * 0.3256987 * temperature / Math.pow(getComponent().getTC(), 2.0)
          + 3.0 * 0.06653 * Math.pow(temperature, 2.0) / Math.pow(getComponent().getTC(), 3.0);
    } else {
      return -(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
          / Math.sqrt(temperature / getComponent().getTC()) / getComponent().getTC();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    if (isMethane) {
      return -2.0 * 0.3256987 / Math.pow(getComponent().getTC(), 2.0)
          + 6.0 * 0.06653 * temperature / Math.pow(getComponent().getTC(), 3.0);
    } else {
      return m * m / temperature / getComponent().getTC() / 2.0
          + (1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
              / Math.sqrt(
                  temperature * temperature * temperature / (Math.pow(getComponent().getTC(), 3.0)))
              / (getComponent().getTC() * getComponent().getTC()) / 2.0;
    }
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
