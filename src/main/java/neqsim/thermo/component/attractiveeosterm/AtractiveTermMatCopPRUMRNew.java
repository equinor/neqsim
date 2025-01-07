package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AtractiveTermMatCopPRUMRNew class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AtractiveTermMatCopPRUMRNew extends AttractiveTermMatCopPRUMR {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  double orgpar = 0.0;
  boolean useStandardAlphaForSupercritical = false;

  /**
   * <p>
   * Constructor for AtractiveTermMatCopPRUMRNew.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AtractiveTermMatCopPRUMRNew(ComponentEosInterface component) {
    super(component);
    m = (0.384401 + 1.52276 * component.getAcentricFactor()
        - 0.213808 * component.getAcentricFactor() * component.getAcentricFactor()
        + 0.034616 * Math.pow(component.getAcentricFactor(), 3.0)
        - 0.001976 * Math.pow(component.getAcentricFactor(), 4.0));
  }

  /**
   * <p>
   * Constructor for AtractiveTermMatCopPRUMRNew.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @param params an array of type double
   */
  public AtractiveTermMatCopPRUMRNew(ComponentEosInterface component, double[] params) {
    this(component);
    parameters = new double[params.length];
    System.arraycopy(params, 0, this.parameters, 0, params.length);
    orgpar = parameters[0];
    if (Math.abs(parameters[0]) < 1e-12) {
      parameters[0] = m;
    }

    // add MC parameters manually
    if (component.getName().equals("ethane")) {
      parameters[0] = 0.498809;
      parameters[1] = 0.115568;
      parameters[2] = -0.040775;
      parameters[3] = -0.057788;
      parameters[4] = 0.017702;
    }
    // if (component.getName().equals("water")) {
    // parameters[0] = 0.91256735118818810000000000;
    // parameters[1] = -0.2872243639795234400000000;
    // parameters[2] = 0.239526763058374250000000000;
    // parameters[3] = 0.0;
    // parameters[4] = 0.0;
    // }

    if (component.getName().equals("water")) {
      parameters[0] = 0.9130000000000;
      parameters[1] = -0.2870000000;
      parameters[2] = 0.239500000000;
      parameters[3] = -4.0;
      parameters[4] = 7.0;
    }

    if (component.getName().equals("nitrogen")) {
      parameters[0] = 0.43635;
      parameters[1] = 0;
      parameters[2] = 0;
      parameters[3] = 0.0;
      parameters[4] = 0.0;
    }

    if (component.getName().equals("methane")) {
      parameters[0] = 0.386575;
      parameters[1] = 0.016011;
      parameters[2] = -0.017371;
      parameters[3] = 0.011761;
      parameters[4] = 0.020786;
    }
  }

  /** {@inheritDoc} */
  @Override
  public AtractiveTermMatCopPRUMRNew clone() {
    AtractiveTermMatCopPRUMRNew atractiveTerm = null;
    try {
      atractiveTerm = (AtractiveTermMatCopPRUMRNew) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return atractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.alpha(temperature);
    } else {
      double Tr = temperature / getComponent().getTC();
      return Math.pow(1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
          + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
          + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0)
          + parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 4.0)
          + parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 5.0), 2.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.aT(temperature);
    } else {
      return getComponent().geta() * alpha(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.diffalphaT(temperature);
    }

    double Tr = temperature / getComponent().getTC();
    double TC = getComponent().getTC();
    double tcrizatr = TC * Math.sqrt(Tr);
    return 2.0
        * (1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
            + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
            + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0)
            + parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 4.0)
            + parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 5.0))
        * (-1.0 / 2.0 * parameters[0] / tcrizatr - parameters[1] * (1.0 - Math.sqrt(Tr)) / tcrizatr
            - 3.0 / 2.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) / tcrizatr
            - 2.0 * parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 3.0) / tcrizatr
            - 5.0 / 2.0 * parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 4.0) / tcrizatr);
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.diffdiffalphaT(temperature);
    }

    double Tr = temperature / getComponent().getTC();
    double TC = getComponent().getTC();
    double tcrizatr = TC * Math.sqrt(Tr);
    double TcT = TC * temperature;
    double Tc2Trpower32 = TC * TC * Math.pow(Tr, 1.5);
    return 2.0
        * Math.pow(parameters[0] / tcrizatr / 2.0 + parameters[1] * (1.0 - Math.sqrt(Tr)) / tcrizatr
            + 3.0 / 2.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) / tcrizatr
            + 2.0 * parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 3.0) / tcrizatr
            + 5.0 / 2.0 * parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 4.0) / tcrizatr, 2.0)
        + 2.0
            * (1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
                + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
                + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0)
                + parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 4.0)
                + parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 5.0))
            * (parameters[0] / Tc2Trpower32 / 4.0 + parameters[1] / TcT / 2.0
                + parameters[1] * (1.0 - Math.sqrt(Tr)) / Tc2Trpower32 / 2.0
                + 3.0 / 2.0 * parameters[2] * (1.0 - Math.sqrt(Tr)) / TcT
                + 3.0 / 4.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) / Tc2Trpower32
                + 3 * parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) / TcT
                + parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 3.0) / Tc2Trpower32
                + 5 * parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 3.0) / TcT
                + 5.0 / 4.0 * parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 4.0) / Tc2Trpower32);
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.diffaT(temperature);
    } else {
      return getComponent().geta() * diffalphaT(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.diffdiffaT(temperature);
    } else {
      return getComponent().geta() * diffdiffalphaT(temperature);
    }
  }
}
