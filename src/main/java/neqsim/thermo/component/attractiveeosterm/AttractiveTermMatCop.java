package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermMatCop class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermMatCop extends AttractiveTermSrk {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double orgpar = 0.0;

  /**
   * <p>
   * Constructor for AttractiveTermMatCop.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermMatCop(ComponentEosInterface component) {
    super(component);
    m = (0.48 + 1.574 * component.getAcentricFactor()
        - 0.175 * component.getAcentricFactor() * component.getAcentricFactor());
  }

  /**
   * <p>
   * Constructor for AttractiveTermMatCop.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @param params an array of type double
   */
  public AttractiveTermMatCop(ComponentEosInterface component, double[] params) {
    this(component);
    System.arraycopy(params, 0, this.parameters, 0, params.length);
    orgpar = parameters[0];
    if (Math.abs(parameters[0]) < 1e-12) {
      parameters[0] = m;
    }
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermMatCop clone() {
    AttractiveTermMatCop attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermMatCop) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    super.init();
    parameters[0] = m;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    double Tr = temperature / getComponent().getTC();
    return Math.pow(1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
        + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
        + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0), 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    if (temperature / getComponent().getTC() > 10000.0) {
      return super.aT(temperature);
    } else {
      return getComponent().geta() * alpha(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    double Tr = temperature / getComponent().getTC();
    double TC = getComponent().getTC();
    return 2.0
        * (1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
            + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
            + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0))
        * (-parameters[0] / Math.sqrt(Tr) / TC / 2.0
            - parameters[1] * (1.0 - Math.sqrt(Tr)) / Math.sqrt(Tr) / TC
            - 3.0 / 2.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) / Math.sqrt(Tr) / TC);
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    double Tr = temperature / getComponent().getTC();
    double TC = getComponent().getTC();
    return 2.0
        * Math.pow(-parameters[0] / Math.sqrt(Tr) / TC / 2.0
            - parameters[1] * (1.0 - Math.sqrt(Tr)) / Math.sqrt(Tr) / TC
            - 3.0 / 2.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) / Math.sqrt(Tr) / TC,
            2.0)
        + 2.0 * (1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
            + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
            + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0))
            * (parameters[0] / Math.sqrt(Tr * Tr * Tr) / (TC * TC) / 4.0
                + parameters[1] / temperature / TC / 2.0
                + parameters[1] * (1.0 - Math.sqrt(Tr)) / Math.sqrt(Tr * Tr * Tr) / (TC * TC) / 2.0
                + 3.0 / 2.0 * parameters[2] * (1.0 - Math.sqrt(Tr)) / temperature / TC
                + 3.0 / 4.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
                    / Math.sqrt(Tr * Tr * Tr) / (TC * TC));
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    if (temperature / getComponent().getTC() > 10000.0) {
      return super.diffaT(temperature);
    } else {
      return getComponent().geta() * diffalphaT(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    if (temperature / getComponent().getTC() > 10000.0) {
      return super.diffdiffaT(temperature);
    } else {
      return getComponent().geta() * diffdiffalphaT(temperature);
    }
  }
}
