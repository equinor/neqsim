package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermPr class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermPr extends AttractiveTermBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for AttractiveTermPr.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermPr(ComponentEosInterface component) {
    super(component);
    m = (0.37464 + 1.54226 * component.getAcentricFactor()
        - 0.26992 * component.getAcentricFactor() * component.getAcentricFactor());
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermPr clone() {
    AttractiveTermPr attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermPr) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public void setm(double val) {
    this.m = val;
    neqsim.mathlib.nonlinearsolver.NewtonRhapson solve =
        new neqsim.mathlib.nonlinearsolver.NewtonRhapson();
    solve.setOrder(2);
    double[] acentricConstants = {-0.26992, 1.54226, (0.37464 - this.m)};
    solve.setConstants(acentricConstants);
    getComponent().setAcentricFactor(solve.solve(0.2));
    // System.out.println("solve accen " + component.getAcentricFactor());
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
    return -(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
        / Math.sqrt(temperature / getComponent().getTC()) / getComponent().getTC();
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    double tr = temperature / getComponent().getTC();
    return m * m / temperature / getComponent().getTC() / 2.0 + (1.0 + m * (1.0 - Math.sqrt(tr)))
        * m / Math.sqrt(tr * tr * tr) / (getComponent().getTC() * getComponent().getTC()) / 2.0;
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
