package neqsim.process.measurementdevice;

/**
 * The three expansibility [expansion] factor families used across ISO 5167-2, -3, -4, -5 and -6.
 *
 * <p>
 * ISO 5167-1:2022 Formula (1) is shared by every differential-pressure primary device; the parts differ only in the
 * discharge coefficient <i>C</i> and the expansibility factor <i>epsilon</i>. Three <i>epsilon</i> forms cover all six
 * parts:
 * </p>
 *
 * <ul>
 * <li>{@link #ORIFICE} &mdash; ISO 5167-2 orifice plates.</li>
 * <li>{@link #ISENTROPIC} &mdash; ISO 5167-3 nozzles (all four sub-types), ISO 5167-4 classical Venturi tubes and ISO
 * 5167-6 wedge meters.</li>
 * <li>{@link #CONE} &mdash; ISO 5167-5 cone meters.</li>
 * </ul>
 *
 * <p>
 * All three formulas are only applicable for a pressure-drop ratio <i>p2 / p1 &gt;= 0.75</i>. This is a validity range,
 * not a hard limit: {@link #calculate(double, double, double, double)} does not enforce or reject it, since the ISO
 * standards themselves only require it to be checked and reported, not used to block a measurement. Each concrete meter
 * reports whether the current operating point is inside this window via {@code isWithinExpansibilityPressureRatio()}
 * (included in its {@code getValidityViolations()} list) &mdash; it is the caller's responsibility to check that before
 * trusting an out-of-range result.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public enum ExpansibilityModel {
  /**
   * ISO 5167-2 orifice plate expansibility, Formula (5):
   *
   * <pre>
   * epsilon = 1 - (0.351 + 0.256 beta^4 + 0.93 beta^8) * (1 - (p2 / p1) ^ (1 / kappa))
   * </pre>
   */
  ORIFICE {
    @Override
    public double calculate(double dp, double p1, double beta, double kappa) {
      if (dp <= 0.0 || p1 <= 0.0 || beta <= 0.0 || beta >= 1.0 || kappa <= 1.0) {
        return Double.NaN;
      }
      double tau = (p1 - dp) / p1;
      if (tau <= 0.0) {
        return Double.NaN;
      }
      double beta4 = Math.pow(beta, 4.0);
      double beta8 = beta4 * beta4;
      return 1.0 - (0.351 + 0.256 * beta4 + 0.93 * beta8) * (1.0 - Math.pow(tau, 1.0 / kappa));
    }
  },

  /**
   * The isentropic expansibility factor shared by ISO 5167-3 nozzles, ISO 5167-4 classical Venturi tubes and ISO 5167-6
   * wedge meters:
   *
   * <pre>
   * epsilon = sqrt(kappa * tau ^ (2 / kappa) / (kappa - 1) * (1 - beta ^ 4) / (1 - beta ^ 4 * tau ^ (2 / kappa))
   *     * (1 - tau ^ ((kappa - 1) / kappa)) / (1 - tau))
   * </pre>
   *
   * <p>
   * with <i>tau = p2 / p1</i>.
   * </p>
   */
  ISENTROPIC {
    @Override
    public double calculate(double dp, double p1, double beta, double kappa) {
      if (dp <= 0.0 || p1 <= 0.0 || beta <= 0.0 || beta >= 1.0 || kappa <= 1.0) {
        return Double.NaN;
      }
      double tau = (p1 - dp) / p1;
      if (tau <= 0.0) {
        return Double.NaN;
      }
      if (Math.abs(1.0 - tau) < 1e-12) {
        // Low-dP limit: (1 - tau^((kappa-1)/kappa)) / (1 - tau) tends to (kappa-1)/kappa, which makes the
        // overall epsilon tend to 1.0 (not the indeterminate term itself).
        return 1.0;
      }
      double beta4 = Math.pow(beta, 4.0);
      double tauPow = Math.pow(tau, 2.0 / kappa);
      double denominator = 1.0 - beta4 * tauPow;
      if (Math.abs(denominator) < 1e-12) {
        return Double.NaN;
      }
      double term = kappa * tauPow / (kappa - 1.0) * (1.0 - beta4) / denominator
          * (1.0 - Math.pow(tau, (kappa - 1.0) / kappa)) / (1.0 - tau);
      return term > 0.0 ? Math.sqrt(term) : Double.NaN;
    }
  },

  /**
   * ISO 5167-5 cone meter expansibility, Formula (4):
   *
   * <pre>
   * epsilon = 1 - (0.649 + 0.696 beta^4) * dP / (kappa * p1)
   * </pre>
   */
  CONE {
    @Override
    public double calculate(double dp, double p1, double beta, double kappa) {
      if (dp <= 0.0 || p1 <= 0.0 || beta <= 0.0 || beta >= 1.0 || kappa <= 1.0) {
        return Double.NaN;
      }
      double beta4 = Math.pow(beta, 4.0);
      return 1.0 - (0.649 + 0.696 * beta4) * dp / (kappa * p1);
    }
  };

  /**
   * Calculates the expansibility [expansion] factor.
   *
   * @param dp differential pressure [Pa], must be positive
   * @param p1 upstream static pressure [Pa], must be positive
   * @param beta diameter ratio d/D [-]
   * @param kappa isentropic exponent, must be greater than 1
   * @return expansibility factor epsilon [-], or NaN when the inputs are not physically valid. Does not check the p2 /
   * p1 &gt;= 0.75 validity range described in the class JavaDoc &mdash; callers must check that separately.
   */
  public abstract double calculate(double dp, double p1, double beta, double kappa);
}
