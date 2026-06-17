package neqsim.process.mechanicaldesign.heatexchanger;

/**
 * LMTD correction factor (F_t) calculations for multi-pass heat exchangers.
 *
 * <p>
 * For heat exchangers with multiple tube or shell passes, the true mean temperature difference
 * differs from the log-mean temperature difference (LMTD) of a pure counterflow arrangement. The
 * correction factor F_t accounts for this departure:
 * </p>
 *
 * <p>
 * Q = U * A * F_t * LMTD_counterflow
 * </p>
 *
 * <p>
 * The correction depends on the dimensionless parameters R and P:
 * </p>
 * <ul>
 * <li>R = (T_hot_in - T_hot_out) / (T_cold_out - T_cold_in) = capacity ratio</li>
 * <li>P = (T_cold_out - T_cold_in) / (T_hot_in - T_cold_in) = thermal effectiveness</li>
 * </ul>
 *
 * <p>
 * For detailed derivation see: Bowman, Mueller and Nagle (1940), "Mean Temperature Difference in
 * Design", Trans. ASME, Vol. 62, pp. 283-294.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class LMTDcorrectionFactor {

  /**
   * Minimum acceptable F_t value below which a shell configuration should not be used.
   */
  public static final double MIN_ACCEPTABLE_FT = 0.75;

  /**
   * Private constructor to prevent instantiation.
   */
  private LMTDcorrectionFactor() {}

  /**
   * Calculates the LMTD correction factor for a 1-shell-pass, even-number-of-tube-passes (1-2N)
   * TEMA E-shell exchanger.
   *
   * <p>
   * This is the most common configuration (e.g., 1 shell pass, 2 or 4 tube passes).
   * </p>
   *
   * @param tHotIn hot stream inlet temperature (any consistent unit)
   * @param tHotOut hot stream outlet temperature
   * @param tColdIn cold stream inlet temperature
   * @param tColdOut cold stream outlet temperature
   * @return F_t correction factor (0 to 1.0), or 1.0 for pure counterflow
   */
  public static double calcFt1ShellPass(double tHotIn, double tHotOut, double tColdIn,
      double tColdOut) {
    double R = calcR(tHotIn, tHotOut, tColdIn, tColdOut);
    double P = calcP(tHotIn, tHotOut, tColdIn, tColdOut);
    return calcFtFromRP(R, P, 1);
  }

  /**
   * Calculates the LMTD correction factor for a 2-shell-pass, 4-or-more-tube-pass (2-4) TEMA
   * F-shell or two E-shells in series.
   *
   * @param tHotIn hot stream inlet temperature (any consistent unit)
   * @param tHotOut hot stream outlet temperature
   * @param tColdIn cold stream inlet temperature
   * @param tColdOut cold stream outlet temperature
   * @return F_t correction factor (0 to 1.0)
   */
  public static double calcFt2ShellPass(double tHotIn, double tHotOut, double tColdIn,
      double tColdOut) {
    double R = calcR(tHotIn, tHotOut, tColdIn, tColdOut);
    double P = calcP(tHotIn, tHotOut, tColdIn, tColdOut);
    return calcFtFromRP(R, P, 2);
  }

  /**
   * Calculates the LMTD correction factor for N shell passes.
   *
   * <p>
   * Uses the Bowman-Mueller-Nagle method: first compute the per-shell P from the overall P, then
   * apply the single-shell F_t formula.
   * </p>
   *
   * @param tHotIn hot stream inlet temperature (any consistent unit)
   * @param tHotOut hot stream outlet temperature
   * @param tColdIn cold stream inlet temperature
   * @param tColdOut cold stream outlet temperature
   * @param shellPasses number of shell passes (1, 2, 3, ...)
   * @return F_t correction factor (0 to 1.0)
   */
  public static double calcFt(double tHotIn, double tHotOut, double tColdIn, double tColdOut,
      int shellPasses) {
    double R = calcR(tHotIn, tHotOut, tColdIn, tColdOut);
    double P = calcP(tHotIn, tHotOut, tColdIn, tColdOut);
    return calcFtFromRP(R, P, shellPasses);
  }

  /**
   * Calculates the LMTD correction factor from dimensionless R and P parameters.
   *
   * @param R capacity ratio = (T_h_in - T_h_out) / (T_c_out - T_c_in)
   * @param P thermal effectiveness = (T_c_out - T_c_in) / (T_h_in - T_c_in)
   * @param shellPasses number of shell passes (1, 2, 3, ...)
   * @return F_t correction factor (0 to 1.0)
   */
  public static double calcFtFromRP(double R, double P, int shellPasses) {
    // Edge cases: pure counterflow or no heat transfer
    if (P <= 0.0 || P >= 1.0) {
      return 1.0;
    }
    if (R <= 0.0) {
      return 1.0;
    }
    if (shellPasses < 1) {
      shellPasses = 1;
    }

    // For multiple shell passes, convert overall P to per-shell P1
    double P1 = P;
    if (shellPasses > 1) {
      P1 = convertPtoPerShell(P, R, shellPasses);
      if (P1 <= 0.0 || P1 >= 1.0 || Double.isNaN(P1)) {
        return 1.0;
      }
    }

    // Compute F_t for a single shell pass, even tube passes
    return calcFtSingleShell(R, P1);
  }

  /**
   * Calculates the minimum number of shell passes required to achieve F_t above the minimum
   * acceptable value.
   *
   * @param tHotIn hot stream inlet temperature
   * @param tHotOut hot stream outlet temperature
   * @param tColdIn cold stream inlet temperature
   * @param tColdOut cold stream outlet temperature
   * @return minimum number of shell passes (1 to 6), or -1 if not achievable
   */
  public static int requiredShellPasses(double tHotIn, double tHotOut, double tColdIn,
      double tColdOut) {
    for (int n = 1; n <= 6; n++) {
      double ft = calcFt(tHotIn, tHotOut, tColdIn, tColdOut, n);
      if (ft >= MIN_ACCEPTABLE_FT) {
        return n;
      }
    }
    return -1;
  }

  /**
   * Calculates the dimensionless capacity ratio R.
   *
   * @param tHotIn hot stream inlet temperature
   * @param tHotOut hot stream outlet temperature
   * @param tColdIn cold stream inlet temperature
   * @param tColdOut cold stream outlet temperature
   * @return R = (T_h_in - T_h_out) / (T_c_out - T_c_in)
   */
  public static double calcR(double tHotIn, double tHotOut, double tColdIn, double tColdOut) {
    double dTcold = tColdOut - tColdIn;
    if (Math.abs(dTcold) < 1e-10) {
      return 1.0;
    }
    return (tHotIn - tHotOut) / dTcold;
  }

  /**
   * Calculates the dimensionless thermal effectiveness P.
   *
   * @param tHotIn hot stream inlet temperature
   * @param tHotOut hot stream outlet temperature
   * @param tColdIn cold stream inlet temperature
   * @param tColdOut cold stream outlet temperature
   * @return P = (T_c_out - T_c_in) / (T_h_in - T_c_in)
   */
  public static double calcP(double tHotIn, double tHotOut, double tColdIn, double tColdOut) {
    double dTmax = tHotIn - tColdIn;
    if (Math.abs(dTmax) < 1e-10) {
      return 0.0;
    }
    return (tColdOut - tColdIn) / dTmax;
  }

  /**
   * Calculates F_t for a single 1-2N shell (one shell pass, even tube passes).
   *
   * <p>
   * Uses the analytical formula:
   * </p>
   * <ul>
   * <li>When R != 1: F = [sqrt(R²+1) * ln((1-P)/(1-R*P))] / [(R-1) * ln((2-P*(R+1-W)) /
   * (2-P*(R+1+W)))]</li>
   * <li>where W = sqrt(R²+1)</li>
   * <li>When R = 1: F = [P*sqrt(2)] / [(1-P)*ln((2-P*(2-sqrt(2)))/(2-P*(2+sqrt(2))))]</li>
   * </ul>
   *
   * @param R capacity ratio
   * @param P per-shell thermal effectiveness
   * @return F_t correction factor for single shell
   */
  static double calcFtSingleShell(double R, double P) {
    if (P <= 0.0 || P >= 1.0) {
      return 1.0;
    }

    double W = Math.sqrt(R * R + 1.0);

    if (Math.abs(R - 1.0) < 1e-6) {
      // Special case: R = 1
      double sqrt2 = Math.sqrt(2.0);
      double numerator = P * sqrt2;
      double arg1 = 2.0 - P * (2.0 - sqrt2);
      double arg2 = 2.0 - P * (2.0 + sqrt2);
      if (arg1 <= 0.0 || arg2 <= 0.0 || arg1 / arg2 <= 0.0) {
        return 0.0;
      }
      double denominator = (1.0 - P) * Math.log(arg1 / arg2);
      if (Math.abs(denominator) < 1e-15) {
        return 1.0;
      }
      return numerator / denominator;
    }

    // General case
    double RP = R * P;
    if (RP >= 1.0) {
      return 0.0; // Temperature cross
    }

    double logArg1 = (1.0 - P) / (1.0 - RP);
    if (logArg1 <= 0.0) {
      return 0.0;
    }

    double arg2Num = 2.0 - P * (R + 1.0 - W);
    double arg2Den = 2.0 - P * (R + 1.0 + W);
    if (arg2Num <= 0.0 || arg2Den <= 0.0 || arg2Num / arg2Den <= 0.0) {
      return 0.0;
    }

    double numerator = W * Math.log(logArg1);
    double denominator = (R - 1.0) * Math.log(arg2Num / arg2Den);

    if (Math.abs(denominator) < 1e-15) {
      return 1.0;
    }

    double ft = numerator / denominator;
    return Math.max(0.0, Math.min(1.0, ft));
  }

  /**
   * Converts overall P to per-shell P1 for multi-shell arrangements.
   *
   * <p>
   * For N shells in series:
   * </p>
   * <ul>
   * <li>When R != 1: P1 = [(X^(1/N)) - 1] / [(X^(1/N)) - R]</li>
   * <li>where X = (1 - R*P) / (1 - P)</li>
   * <li>When R = 1: P1 = P / [N - (N-1)*P]</li>
   * </ul>
   *
   * @param P overall thermal effectiveness
   * @param R capacity ratio
   * @param N number of shell passes
   * @return per-shell thermal effectiveness P1
   */
  static double convertPtoPerShell(double P, double R, int N) {
    if (N <= 1) {
      return P;
    }
    if (Math.abs(R - 1.0) < 1e-6) {
      // R = 1 special case
      return P / (N - (N - 1.0) * P);
    }

    double X = (1.0 - R * P) / (1.0 - P);
    if (X <= 0.0) {
      return Double.NaN;
    }

    double X1N = Math.pow(X, 1.0 / N);
    double denom = X1N - R;
    if (Math.abs(denom) < 1e-15) {
      return Double.NaN;
    }

    return (X1N - 1.0) / denom;
  }
}
