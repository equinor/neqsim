package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermSrk class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermSrk extends AttractiveTermBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for AttractiveTermSrk.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermSrk(ComponentEosInterface component) {
    super(component);
    m = (0.48 + 1.574 * component.getAcentricFactor()
        - 0.176 * component.getAcentricFactor() * component.getAcentricFactor());
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermSrk clone() {
    AttractiveTermSrk attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermSrk) super.clone();
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
    double[] acentricConstants = {-0.176, 1.574, (0.48 - this.m)};
    solve.setConstants(acentricConstants);
    getComponent().setAcentricFactor(solve.solve(0.25));
    // System.out.println("solve accen " + getComponent().getAcentricFactor());
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    m = (0.48 + 1.574 * getComponent().getAcentricFactor()
        - 0.176 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor());
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    // System.out.println("m " + m);
    // System.out.println("TC " + component.getTC());
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
    // System.out.println("m " + m);
    double temp = Math.sqrt(temperature / getComponent().getTC());
    return -(1.0 + m * (1.0 - temp)) * m / temp / getComponent().getTC();
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
