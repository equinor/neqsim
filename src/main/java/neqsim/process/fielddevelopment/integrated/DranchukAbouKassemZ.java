package neqsim.process.fielddevelopment.integrated;

/**
 * Dranchuk-Abou-Kassem gas deviation factor for sweet lean gas, solved for reduced density by bisection.
 *
 * <p>
 * Pseudo-critical constants are supplied by the caller (for example from Kay's rule on the dry-gas composition). The
 * correlation is valid for pseudo-reduced temperature 1.0-3.0 and pseudo-reduced pressure up to about 30. Outside that
 * range, or when the density equation has no root, the ideal-gas value 1.0 is returned.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see GasZFactor
 */
public class DranchukAbouKassemZ implements GasZFactor {
  private static final long serialVersionUID = 1000L;
  private static final double[] A = {0.3265, -1.0700, -0.5339, 0.01569, -0.05165, 0.5475, -0.7361, 0.1844, 0.1056,
      0.6134, 0.7210};

  private final double pseudoCriticalTemperatureK;
  private final double pseudoCriticalPressureBara;

  /**
   * Creates the correlation.
   *
   * @param pseudoCriticalTemperatureK pseudo-critical temperature in K
   * @param pseudoCriticalPressureBara pseudo-critical pressure in bara
   */
  public DranchukAbouKassemZ(double pseudoCriticalTemperatureK, double pseudoCriticalPressureBara) {
    if (pseudoCriticalTemperatureK <= 0.0 || pseudoCriticalPressureBara <= 0.0) {
      throw new IllegalArgumentException("pseudo-critical constants must be positive");
    }
    this.pseudoCriticalTemperatureK = pseudoCriticalTemperatureK;
    this.pseudoCriticalPressureBara = pseudoCriticalPressureBara;
  }

  /** {@inheritDoc} */
  @Override
  public double z(double pressureBara, double temperatureK) {
    double ppr = Math.max(pressureBara / pseudoCriticalPressureBara, 1.0e-6);
    double tpr = temperatureK / pseudoCriticalTemperatureK;
    double c1 = A[0] + A[1] / tpr + A[2] / Math.pow(tpr, 3) + A[3] / Math.pow(tpr, 4) + A[4] / Math.pow(tpr, 5);
    double c2 = A[5] + A[6] / tpr + A[7] / (tpr * tpr);
    double c3 = A[8] * (A[6] / tpr + A[7] / (tpr * tpr));
    double lo = 1.0e-6;
    double hi = 3.0;
    double flo = residual(lo, ppr, tpr, c1, c2, c3);
    double fhi = residual(hi, ppr, tpr, c1, c2, c3);
    if (flo * fhi > 0.0) {
      return 1.0;
    }
    for (int i = 0; i < 80; i++) {
      double mid = 0.5 * (lo + hi);
      double fm = residual(mid, ppr, tpr, c1, c2, c3);
      if (flo * fm <= 0.0) {
        hi = mid;
      } else {
        lo = mid;
        flo = fm;
      }
    }
    double rho = 0.5 * (lo + hi);
    return 0.27 * ppr / (rho * tpr);
  }

  /**
   * Residual of the DAK equation of state in reduced density.
   *
   * @param rho pseudo-reduced density
   * @param ppr pseudo-reduced pressure
   * @param tpr pseudo-reduced temperature
   * @param c1 temperature coefficient of the linear term
   * @param c2 temperature coefficient of the quadratic term
   * @param c3 temperature coefficient of the fifth-power term
   * @return residual (zero at the solution)
   */
  private static double residual(double rho, double ppr, double tpr, double c1, double c2, double c3) {
    double c4 = A[9] * (1.0 + A[10] * rho * rho) * rho * rho / Math.pow(tpr, 3) * Math.exp(-A[10] * rho * rho);
    return 1.0 + c1 * rho + c2 * rho * rho - c3 * Math.pow(rho, 5) + c4 - 0.27 * ppr / (rho * tpr);
  }
}
