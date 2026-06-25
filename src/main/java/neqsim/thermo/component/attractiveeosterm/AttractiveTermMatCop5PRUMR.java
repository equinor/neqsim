package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * AttractiveTermMatCop5PRUMR class.
 *
 * <p>
 * Five-parameter Mathias-Copeman alpha function for the Peng-Robinson based UMR-CPA equation of state. The alpha
 * function reads
 * </p>
 *
 * <p>
 * alpha(T) = [1 + c1 (1 - sqrt(Tr)) + c2 (1 - sqrt(Tr))^2 + c3 (1 - sqrt(Tr))^3 + c4 (1 - sqrt(Tr))^4 + c5 (1 -
 * sqrt(Tr))^5]^2
 * </p>
 *
 * <p>
 * The five-parameter form is used by Tasios et al. (Fluid Phase Equilibria, 2025, doi:10.1016/j.fluid.2024.114241) for
 * the non-self-associating compounds in the UMR-CPA model. The five coefficients are dimensionless and are stored in
 * the UMRCPA_MC1..UMRCPA_MC5 columns of COMP.csv.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermMatCop5PRUMR extends AttractiveTermPr {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Five Mathias-Copeman coefficients c1..c5. */
  private double[] mcParams = new double[5];
  /**
   * Use the standard PR alpha for supercritical temperatures. The UMR-PRU/UMR-CPA family extrapolates the
   * Mathias-Copeman polynomial above the critical temperature (consistent with {@code AtractiveTermMatCopPRUMRNew}), so
   * this defaults to false.
   */
  private boolean useStandardAlphaForSupercritical = false;

  /**
   * Constructor for AttractiveTermMatCop5PRUMR.
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermMatCop5PRUMR(ComponentEosInterface component) {
    super(component);
    m = (0.37464 + 1.54226 * component.getAcentricFactor()
        - 0.26992 * component.getAcentricFactor() * component.getAcentricFactor());
  }

  /**
   * Constructor for AttractiveTermMatCop5PRUMR.
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @param params an array of type double with up to five Mathias-Copeman coefficients
   */
  public AttractiveTermMatCop5PRUMR(ComponentEosInterface component, double[] params) {
    this(component);
    int n = Math.min(params.length, mcParams.length);
    System.arraycopy(params, 0, this.mcParams, 0, n);
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermMatCop5PRUMR clone() {
    AttractiveTermMatCop5PRUMR attractiveTerm;
    try {
      attractiveTerm = (AttractiveTermMatCop5PRUMR) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
      throw new IllegalStateException("Unable to clone AttractiveTermMatCop5PRUMR", ex);
    }
    attractiveTerm.mcParams = this.mcParams.clone();
    return attractiveTerm;
  }

  /**
   * Returns true when the standard PR alpha function should be used instead of the Mathias-Copeman form (supercritical
   * temperatures or unset coefficients).
   *
   * @param temperature temperature in Kelvin
   * @return true if the standard PR alpha should be used
   */
  private boolean useStandardAlpha(double temperature) {
    return (useStandardAlphaForSupercritical && temperature / getComponent().getTC() > 1.0) || allCoefficientsZero();
  }

  /**
   * Checks whether all five Mathias-Copeman coefficients are effectively zero.
   *
   * @return true if every coefficient has an absolute value below 1e-20
   */
  private boolean allCoefficientsZero() {
    for (int k = 0; k < mcParams.length; k++) {
      if (Math.abs(mcParams[k]) > 1e-20) {
        return false;
      }
    }
    return true;
  }

  /**
   * Computes the inner Mathias-Copeman polynomial S = 1 + sum c_k (1 - sqrt(Tr))^k and its first two temperature
   * derivatives.
   *
   * @param temperature temperature in Kelvin
   * @return an array {S, dS/dT, d2S/dT2}
   */
  private double[] polynomial(double temperature) {
    double tc = getComponent().getTC();
    double tr = temperature / tc;
    double sqrtTr = Math.sqrt(tr);
    double u = 1.0 - sqrtTr;
    double dudt = -1.0 / (2.0 * tc * sqrtTr);
    double d2udt2 = 1.0 / (4.0 * tc * tc * Math.pow(tr, 1.5));

    double s = 1.0;
    double dsdt = 0.0;
    double d2sdt2 = 0.0;
    for (int k = 1; k <= mcParams.length; k++) {
      double ck = mcParams[k - 1];
      double upowKm1 = Math.pow(u, k - 1);
      double upowK = upowKm1 * u;
      s += ck * upowK;
      dsdt += ck * k * upowKm1 * dudt;
      double upowKm2 = (k >= 2) ? Math.pow(u, k - 2) : 0.0;
      d2sdt2 += ck * (k * (k - 1) * upowKm2 * dudt * dudt + k * upowKm1 * d2udt2);
    }
    return new double[] { s, dsdt, d2sdt2 };
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    if (useStandardAlpha(temperature)) {
      return super.alpha(temperature);
    }
    double s = polynomial(temperature)[0];
    return s * s;
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    if (useStandardAlpha(temperature)) {
      return super.aT(temperature);
    }
    return getComponent().geta() * alpha(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffalphaT(double temperature) {
    if (useStandardAlpha(temperature)) {
      return super.diffalphaT(temperature);
    }
    double[] poly = polynomial(temperature);
    return 2.0 * poly[0] * poly[1];
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffalphaT(double temperature) {
    if (useStandardAlpha(temperature)) {
      return super.diffdiffalphaT(temperature);
    }
    double[] poly = polynomial(temperature);
    return 2.0 * poly[1] * poly[1] + 2.0 * poly[0] * poly[2];
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    if (useStandardAlpha(temperature)) {
      return super.diffaT(temperature);
    }
    return getComponent().geta() * diffalphaT(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    if (useStandardAlpha(temperature)) {
      return super.diffdiffaT(temperature);
    }
    return getComponent().geta() * diffdiffalphaT(temperature);
  }
}
