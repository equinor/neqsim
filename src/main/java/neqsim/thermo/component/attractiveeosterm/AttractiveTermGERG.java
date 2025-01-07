package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermGERG class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermGERG extends AttractiveTermPr {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  protected double[] parametersGERG = {0.905436, -0.213781, 0.26005};
  protected double[] parametersSolidGERG = {0.106025, 2.683845, -4.75638};

  /**
   * <p>
   * Constructor for AttractiveTermGERG.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermGERG(ComponentEosInterface component) {
    super(component);
    if (component.getName().equals("water")) {
      System.arraycopy(component.getMatiascopemanParams(), 0, this.parameters, 0,
          component.getMatiascopemanParams().length);
      System.arraycopy(component.getMatiascopemanSolidParams(), 0, this.parametersSolid, 0,
          component.getMatiascopemanSolidParams().length);
    }
  }

  /**
   * <p>
   * AttractiveTermGERG.
   * </p>
   *
   * @return a {@link java.lang.Object} object
   */
  public Object AttractiveTermGERG() {
    AttractiveTermGERG attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermGERG) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    if (getComponent().getName().equals("water")) {
      if (temperature < 273.15) {
        System.arraycopy(parametersSolidGERG, 0, this.parameters, 0, parametersSolidGERG.length);
      } else {
        System.arraycopy(parametersGERG, 0, this.parameters, 0, parametersGERG.length);
      }
      // System.out.println("alpha GERG");
      double Tr = temperature / getComponent().getTC();
      return Math.pow(1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
          + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
          + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 4.0), 2.0);
    } else {
      return super.alpha(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    if (getComponent().getName().equals("water")) {
      return getComponent().geta() * alpha(temperature);
    } else {
      return super.aT(temperature);
    }
  }

  /**
   * <p>
   * diffalphaTGERG.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffalphaTGERG(double temperature) {
    // ikke beregnet riktig
    if (temperature < 273.15) {
      System.arraycopy(parametersSolidGERG, 0, this.parameters, 0, parametersSolidGERG.length);
    } else {
      System.arraycopy(parametersGERG, 0, this.parameters, 0, parametersGERG.length);
    }

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

  /**
   * <p>
   * diffdiffalphaTGERG.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffdiffalphaTGERG(double temperature) {
    // ikke beregnet riktig
    if (temperature < 273.15) {
      System.arraycopy(parametersSolidGERG, 0, this.parameters, 0, parametersSolidGERG.length);
    } else {
      System.arraycopy(parametersGERG, 0, this.parameters, 0, parametersGERG.length);
    }

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
    if (getComponent().getName().equals("water")) {
      return getComponent().geta() * diffalphaTGERG(temperature);
    } else {
      return super.diffaT(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    if (getComponent().getName().equals("water")) {
      return getComponent().geta() * diffdiffalphaTGERG(temperature);
    } else {
      return super.diffdiffaT(temperature);
    }
  }
}
