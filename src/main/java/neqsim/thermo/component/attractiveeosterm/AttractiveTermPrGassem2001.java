package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermPrGassem2001 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermPrGassem2001 extends AttractiveTermPr {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected double A = 2.0;
  protected double B = 0.836;
  protected double C = 0.134;
  protected double D = 0.508;
  protected double E = -0.0467;

  /**
   * <p>
   * Constructor for AttractiveTermPrGassem2001.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermPrGassem2001(ComponentEosInterface component) {
    super(component);
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermPrGassem2001 clone() {
    AttractiveTermPrGassem2001 attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermPrGassem2001) super.clone();
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
    // System.out.println("alpha gassem");
    return Math.exp((A + B * temperature / getComponent().getTC()) * (1.0
        - Math.pow(temperature / getComponent().getTC(), C + D * getComponent().getAcentricFactor()
            + E * getComponent().getAcentricFactor() * getComponent().getAcentricFactor())));
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    return getComponent().geta() * alpha(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    return 1.0 / getComponent().getTC() * alpha(temperature)
        * ((B * (1.0 - Math.pow(temperature / getComponent().getTC(),
            C + D * getComponent().getAcentricFactor()
                + E * getComponent().getAcentricFactor() * getComponent().getAcentricFactor())))
            - (A + B * temperature / getComponent().getTC())
                * (C + D * getComponent().getAcentricFactor()
                    + E * getComponent().getAcentricFactor() * getComponent().getAcentricFactor())
                * Math.pow(temperature / getComponent().getTC(),
                    C + D * getComponent().getAcentricFactor() + E
                        * getComponent().getAcentricFactor() * getComponent().getAcentricFactor()
                        - 1.0));
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    // not implemented dubble derivative

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
