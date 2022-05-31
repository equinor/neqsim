package neqsim.thermo.component.attractiveEosTerm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 *
 * @author esol
 * @version
 */
public class AtractiveTermMatCopPRUMRNew extends AttractiveTermMatCopPRUMR {

  private static final long serialVersionUID = 1000;
  double orgpar = 0.0;
  boolean useStandardAlphaForSupercritical = false;

  public AtractiveTermMatCopPRUMRNew(ComponentEosInterface component) {
    super(component);
    m = (0.384401 + 1.52276 * component.getAcentricFactor()
        - 0.213808 * component.getAcentricFactor() * component.getAcentricFactor()
        + 0.034616 * Math.pow(component.getAcentricFactor(), 3.0)
        - 0.001976 * Math.pow(component.getAcentricFactor(), 4.0));
  }

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
    if (component.getName().equals("water")) {
      parameters[0] = 0.91256735118818810000000000;
      parameters[1] = -0.2872243639795234400000000;
      parameters[2] = 0.239526763058374250000000000;
      parameters[3] = 0.0;
      parameters[4] = 0.0;
    }

    if (component.getName().equals("nitrogen")) {
      parameters[0] = 0.43635	;
      parameters[1] = 0;
      parameters[2] = 0;
      parameters[3] = 0.0;
      parameters[4] = 0.0;
    }

    if (component.getName().equals("methane")) {
      parameters[0] = 0.386575	;
      parameters[1] = 0.016011;
      parameters[2] = -0.017371;
      parameters[3] = 0.011761;
      parameters[4] = 0.020786;
    }
  }

  @Override
  public AtractiveTermMatCopPRUMRNew clone() {
    AtractiveTermMatCopPRUMRNew atractiveTerm = null;
    try {
      atractiveTerm = (AtractiveTermMatCopPRUMRNew) super.clone();
    } catch (Exception e) {
      logger.error("Cloning failed.", e);
    }

    return atractiveTerm;
  }

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

  @Override
  public double aT(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.aT(temperature);
    } else {
      return getComponent().geta() * alpha(temperature);
    }
  }

  @Override
  public double diffalphaT(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.diffalphaT(temperature);
    }

    double Tr = temperature / getComponent().getTC();
    double TC = getComponent().getTC();
    return 2.0
        * (1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
            + +parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
            + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0)
            + parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 4.0)
            + parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 5.0))
        * (-parameters[0] / Math.sqrt(Tr) / TC / 2.0
            - parameters[1] * (1.0 - Math.sqrt(Tr)) / Math.sqrt(Tr) / TC
            - 3.0 / 2.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) / Math.sqrt(Tr) / TC
            - 2.0 * parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 3.0) / Math.sqrt(Tr) / TC
            - 5.0 / 2.0 * parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 4.0));

  }

  @Override
  public double diffdiffalphaT(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.diffdiffalphaT(temperature);
    }

    double Tr = temperature / getComponent().getTC();
    double TC = getComponent().getTC();
    return 2.0
        * Math.pow(parameters[0] / Math.sqrt(Tr) / TC / 2.0
            + parameters[1] * (1.0 - Math.sqrt(Tr)) / Math.sqrt(Tr) / TC
            + 3.0 / 2.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) / Math.sqrt(Tr) / TC
            + 2.0 * parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 3.0) / Math.sqrt(Tr) / TC
            + 5.0 / 2.0 * parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 4.0) / Math.sqrt(Tr) / TC,
            2.0)
        + 2.0
            * (1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
                + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
                + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0)
                + parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 4.0)
                + parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 5.0))
            * (parameters[0] / Math.sqrt(Tr * Tr * Tr) / (TC * TC) / 4.0
                + parameters[1] / temperature / TC / 2.0
                + parameters[1] * (1.0 - Math.sqrt(Tr)) / Math.sqrt(Tr * Tr * Tr) / (TC * TC) / 2.0
                + 3.0 / 2.0 * parameters[2] * (1.0 - Math.sqrt(Tr)) / temperature / TC
                + 3.0 / 4.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
                    / Math.sqrt(Tr * Tr * Tr) / (TC * TC)
                + 3 * parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) / TC / temperature
                + 5 * parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 3.0) / TC / temperature
                + parameters[3] * Math.pow(1.0 - Math.sqrt(Tr), 3.0) / (TC * TC)
                    / Math.sqrt(Tr * Tr * Tr)
                + 5.0 / 4.0 * parameters[4] * Math.pow(1.0 - Math.sqrt(Tr), 4.0) / TC / TC);
  }


  @Override
  public double diffaT(double temperature) {
    if (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0
        || parameters[0] < 1e-20) {
      return super.diffaT(temperature);
    } else {
      return getComponent().geta() * diffalphaT(temperature);
    }
  }

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
