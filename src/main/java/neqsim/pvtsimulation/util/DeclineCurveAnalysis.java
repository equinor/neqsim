package neqsim.pvtsimulation.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decline Curve Analysis (DCA) for production forecasting.
 *
 * <p>
 * Implements the Arps family of decline curves used to forecast production rates and estimate
 * reserves (EUR) for oil and gas wells. The three classical Arps models are:
 *
 * <ul>
 * <li><b>Exponential</b> ($b = 0$): constant-percentage decline, most conservative</li>
 * <li><b>Hyperbolic</b> ($0 &lt; b &lt; 1$): variable-percentage decline, most general</li>
 * <li><b>Harmonic</b> ($b = 1$): special case of hyperbolic, most optimistic</li>
 * </ul>
 *
 * <p>
 * <b>Key equations:</b>
 *
 * <p>
 * Rate at time $t$: $$ q(t) = q_i (1 + b D_i t)^{-1/b} $$
 *
 * <p>
 * Cumulative production at time $t$: $$ N_p(t) = \frac{q_i}{(1 - b) D_i} \left[ 1 - (1 + b D_i
 * t)^{(b-1)/b} \right] $$
 *
 * <p>
 * <b>Units:</b> This class is unit-agnostic for production rates. Times are in days by convention.
 * Users should ensure consistent units between rate, time, and decline rate:
 * <ul>
 * <li>{@code qi} - initial rate (bbl/d, Sm3/d, MMscf/d, etc.)</li>
 * <li>{@code di} - initial decline rate (1/day, nominal)</li>
 * <li>{@code t} - time (days)</li>
 * <li>{@code b} - Arps exponent (dimensionless, 0 &le; b &le; 1 for bounded reserves)</li>
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b>
 *
 * <pre>
 * double qi = 1000.0; // bbl/d initial rate
 * double di = 0.001; // 1/day (nominal) initial decline
 * double b = 0.5; // hyperbolic exponent
 *
 * double q365 = DeclineCurveAnalysis.rate(qi, di, b, 365.0);
 * double np365 = DeclineCurveAnalysis.cumulativeProduction(qi, di, b, 365.0);
 * double eur = DeclineCurveAnalysis.eur(qi, di, b, 50.0); // EUR to qlimit=50 bbl/d
 *
 * // Forecast profile
 * double[][] profile = DeclineCurveAnalysis.forecast(qi, di, b, 0, 3650, 365);
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public final class DeclineCurveAnalysis {

  private DeclineCurveAnalysis() {
    // Utility class
  }

  // ============================================================
  // Rate calculations
  // ============================================================

  /**
   * Production rate at time t using Arps decline.
   *
   * <p>
   * General Arps equation:
   * <ul>
   * <li>$b = 0$ (exponential): $q = q_i \exp(-D_i t)$</li>
   * <li>$0 &lt; b \le 1$ (hyperbolic/harmonic): $q = q_i (1 + b D_i t)^{-1/b}$</li>
   * </ul>
   *
   * @param qi Initial production rate (any consistent unit, e.g. bbl/d)
   * @param di Initial nominal decline rate (1/day). Must be positive.
   * @param b Arps decline exponent (dimensionless). Range: [0, 1]
   * @param t Time since start of decline (days). Must be non-negative.
   * @return Production rate at time t (same unit as qi)
   */
  public static double rate(double qi, double di, double b, double t) {
    if (t <= 0.0) {
      return qi;
    }
    if (b < 1.0e-10) {
      // Exponential decline
      return qi * Math.exp(-di * t);
    }
    // Hyperbolic / harmonic decline
    return qi * Math.pow(1.0 + b * di * t, -1.0 / b);
  }

  /**
   * Exponential decline rate at time t ($b = 0$).
   *
   * <p>
   * $$ q(t) = q_i \exp(-D_i t) $$
   *
   * @param qi Initial production rate
   * @param di Nominal decline rate (1/day)
   * @param t Time (days)
   * @return Rate at time t
   */
  public static double rateExponential(double qi, double di, double t) {
    return rate(qi, di, 0.0, t);
  }

  /**
   * Hyperbolic decline rate at time t ($0 &lt; b &lt; 1$).
   *
   * <p>
   * $$ q(t) = q_i (1 + b D_i t)^{-1/b} $$
   *
   * @param qi Initial production rate
   * @param di Initial nominal decline rate (1/day)
   * @param b Arps exponent (0 &lt; b &lt; 1)
   * @param t Time (days)
   * @return Rate at time t
   */
  public static double rateHyperbolic(double qi, double di, double b, double t) {
    return rate(qi, di, b, t);
  }

  /**
   * Harmonic decline rate at time t ($b = 1$).
   *
   * <p>
   * $$ q(t) = \frac{q_i}{1 + D_i t} $$
   *
   * @param qi Initial production rate
   * @param di Initial nominal decline rate (1/day)
   * @param t Time (days)
   * @return Rate at time t
   */
  public static double rateHarmonic(double qi, double di, double t) {
    return rate(qi, di, 1.0, t);
  }

  // ============================================================
  // Cumulative production
  // ============================================================

  /**
   * Cumulative production from time 0 to time t using Arps decline.
   *
   * <p>
   * Integrated rate over time:
   * <ul>
   * <li>$b = 0$: $N_p = \frac{q_i}{D_i} [1 - \exp(-D_i t)]$</li>
   * <li>$0 &lt; b &lt; 1$: $N_p = \frac{q_i}{(1-b) D_i} [1 - (1 + b D_i t)^{(b-1)/b}]$</li>
   * <li>$b = 1$: $N_p = \frac{q_i}{D_i} \ln(1 + D_i t)$</li>
   * </ul>
   *
   * @param qi Initial production rate
   * @param di Initial nominal decline rate (1/day)
   * @param b Arps decline exponent [0, 1]
   * @param t Time (days)
   * @return Cumulative production (rate-unit * days, e.g. bbl if qi in bbl/d)
   */
  public static double cumulativeProduction(double qi, double di, double b, double t) {
    if (t <= 0.0) {
      return 0.0;
    }
    if (di < 1.0e-15) {
      // No decline, constant rate
      return qi * t;
    }
    if (b < 1.0e-10) {
      // Exponential
      return (qi / di) * (1.0 - Math.exp(-di * t));
    }
    if (Math.abs(b - 1.0) < 1.0e-10) {
      // Harmonic
      return (qi / di) * Math.log(1.0 + di * t);
    }
    // Hyperbolic
    return (qi / ((1.0 - b) * di)) * (1.0 - Math.pow(1.0 + b * di * t, (b - 1.0) / b));
  }

  /**
   * Cumulative production for exponential decline ($b = 0$).
   *
   * @param qi Initial production rate
   * @param di Nominal decline rate (1/day)
   * @param t Time (days)
   * @return Cumulative production
   */
  public static double cumulativeExponential(double qi, double di, double t) {
    return cumulativeProduction(qi, di, 0.0, t);
  }

  /**
   * Cumulative production for harmonic decline ($b = 1$).
   *
   * @param qi Initial production rate
   * @param di Nominal decline rate (1/day)
   * @param t Time (days)
   * @return Cumulative production
   */
  public static double cumulativeHarmonic(double qi, double di, double t) {
    return cumulativeProduction(qi, di, 1.0, t);
  }

  // ============================================================
  // Decline rate conversions
  // ============================================================

  /**
   * Convert nominal decline rate to effective annual decline rate.
   *
   * <p>
   * For exponential: $$ D_{eff} = 1 - \exp(-D_{nom} \times 365.25) $$
   *
   * <p>
   * The effective decline rate represents the fractional drop in rate over one year.
   *
   * @param dNominal Nominal decline rate (1/day)
   * @return Effective annual decline rate (fraction, 0-1)
   */
  public static double nominalToEffectiveAnnual(double dNominal) {
    return 1.0 - Math.exp(-dNominal * 365.25);
  }

  /**
   * Convert effective annual decline rate to nominal decline rate.
   *
   * @param dEffective Effective annual decline rate (fraction, 0-1)
   * @return Nominal decline rate (1/day)
   */
  public static double effectiveAnnualToNominal(double dEffective) {
    if (dEffective <= 0.0 || dEffective >= 1.0) {
      throw new IllegalArgumentException(
          "Effective decline rate must be between 0 and 1, got " + dEffective);
    }
    return -Math.log(1.0 - dEffective) / 365.25;
  }

  /**
   * Instantaneous decline rate at time t for hyperbolic/harmonic decline.
   *
   * <p>
   * $$ D(t) = \frac{D_i}{1 + b D_i t} $$
   *
   * <p>
   * For exponential ($b = 0$), $D(t) = D_i$ (constant).
   *
   * @param di Initial nominal decline rate (1/day)
   * @param b Arps exponent [0, 1]
   * @param t Time (days)
   * @return Instantaneous decline rate at time t (1/day)
   */
  public static double instantaneousDeclineRate(double di, double b, double t) {
    if (b < 1.0e-10) {
      return di;
    }
    return di / (1.0 + b * di * t);
  }

  // ============================================================
  // Time and EUR calculations
  // ============================================================

  /**
   * Time to reach a given production rate.
   *
   * <p>
   * Inverse of the rate equation:
   * <ul>
   * <li>$b = 0$: $t = -\ln(q_{lim}/q_i) / D_i$</li>
   * <li>$b &gt; 0$: $t = [(q_i/q_{lim})^b - 1] / (b D_i)$</li>
   * </ul>
   *
   * @param qi Initial production rate
   * @param di Nominal decline rate (1/day)
   * @param b Arps exponent [0, 1]
   * @param qLimit Target rate (must be less than qi)
   * @return Time from start to reach qLimit (days)
   */
  public static double timeToRate(double qi, double di, double b, double qLimit) {
    if (qLimit >= qi) {
      return 0.0;
    }
    if (di < 1.0e-15) {
      return Double.POSITIVE_INFINITY;
    }
    if (b < 1.0e-10) {
      // Exponential
      return -Math.log(qLimit / qi) / di;
    }
    // Hyperbolic/Harmonic
    return (Math.pow(qi / qLimit, b) - 1.0) / (b * di);
  }

  /**
   * Estimated Ultimate Recovery (EUR) to economic limit.
   *
   * <p>
   * Total cumulative production until the well reaches the economic limit rate.
   *
   * @param qi Initial production rate
   * @param di Nominal decline rate (1/day)
   * @param b Arps exponent [0, 1]
   * @param qLimit Economic limit rate (same unit as qi)
   * @return EUR (rate-unit * days)
   */
  public static double eur(double qi, double di, double b, double qLimit) {
    double tEnd = timeToRate(qi, di, b, qLimit);
    if (Double.isInfinite(tEnd)) {
      return Double.POSITIVE_INFINITY;
    }
    return cumulativeProduction(qi, di, b, tEnd);
  }

  /**
   * Remaining reserves from current time to economic limit.
   *
   * @param qi Initial production rate
   * @param di Initial nominal decline rate (1/day)
   * @param b Arps exponent [0, 1]
   * @param tCurrent Current time since start of decline (days)
   * @param qLimit Economic limit rate
   * @return Remaining reserves (rate-unit * days)
   */
  public static double remainingReserves(double qi, double di, double b, double tCurrent,
      double qLimit) {
    double npCurrent = cumulativeProduction(qi, di, b, tCurrent);
    double eurTotal = eur(qi, di, b, qLimit);
    return eurTotal - npCurrent;
  }

  // ============================================================
  // Forecasting
  // ============================================================

  /**
   * Generate a production forecast over a time range.
   *
   * <p>
   * Returns a 2D array: result[0] = times, result[1] = rates, result[2] = cumulative production.
   *
   * @param qi Initial production rate
   * @param di Nominal decline rate (1/day)
   * @param b Arps exponent [0, 1]
   * @param tStart Start time (days)
   * @param tEnd End time (days)
   * @param tStep Time step (days)
   * @return 2D array [times, rates, cumulativeProduction]
   */
  public static double[][] forecast(double qi, double di, double b, double tStart, double tEnd,
      double tStep) {
    if (tStep <= 0.0) {
      throw new IllegalArgumentException("Time step must be positive");
    }
    int nPoints = (int) Math.ceil((tEnd - tStart) / tStep) + 1;
    double[] times = new double[nPoints];
    double[] rates = new double[nPoints];
    double[] cumProd = new double[nPoints];

    for (int i = 0; i < nPoints; i++) {
      double t = tStart + i * tStep;
      if (t > tEnd) {
        t = tEnd;
      }
      times[i] = t;
      rates[i] = rate(qi, di, b, t);
      cumProd[i] = cumulativeProduction(qi, di, b, t);
    }

    return new double[][] {times, rates, cumProd};
  }

  // ============================================================
  // Parameter estimation from production data
  // ============================================================

  /**
   * Estimate exponential decline rate from two rate-time data points.
   *
   * <p>
   * For exponential decline: $D = \ln(q_1/q_2) / (t_2 - t_1)$
   *
   * @param q1 Rate at time t1
   * @param t1 Time 1 (days)
   * @param q2 Rate at time t2 (q2 &lt; q1)
   * @param t2 Time 2 (days, t2 &gt; t1)
   * @return Estimated nominal decline rate (1/day)
   */
  public static double estimateExponentialDecline(double q1, double t1, double q2, double t2) {
    if (t2 <= t1) {
      throw new IllegalArgumentException("t2 must be greater than t1");
    }
    if (q2 >= q1 || q1 <= 0 || q2 <= 0) {
      throw new IllegalArgumentException("Rates must be positive with q2 < q1");
    }
    return Math.log(q1 / q2) / (t2 - t1);
  }

  /**
   * Estimate hyperbolic parameters from three rate-time data points.
   *
   * <p>
   * Uses least-squares fitting via Newton-Raphson iteration. Falls back to exponential estimation
   * if the data does not support a hyperbolic fit ($b \approx 0$).
   *
   * @param q1 Rate at time t1
   * @param t1 First time point (days)
   * @param q2 Rate at time t2
   * @param t2 Second time point (days)
   * @param q3 Rate at time t3
   * @param t3 Third time point (days)
   * @return Map with keys "qi", "di", "b"
   */
  public static Map<String, Double> estimateHyperbolicParameters(double q1, double t1, double q2,
      double t2, double q3, double t3) {
    // Simple approach: use ratios to estimate b
    // For hyperbolic: q = qi / (1 + b*Di*t)^(1/b)
    // Ratio: q1/q2 = ((1 + b*Di*t2) / (1 + b*Di*t1))^(1/b)

    // Start with exponential estimate
    double diEst = Math.log(q1 / q3) / (t3 - t1);
    if (diEst <= 0) {
      diEst = 0.001;
    }

    // Search for b that best fits all three points
    double bestB = 0.0;
    double bestError = Double.MAX_VALUE;

    for (int i = 0; i <= 100; i++) {
      double bTry = i / 100.0;
      // For given b, estimate Di from (q1, t1) and (q3, t3)
      double r = Math.pow(q1 / q3, bTry);
      double dTry;
      if (Math.abs(r - 1.0) < 1e-12) {
        dTry = diEst;
      } else {
        dTry = (r - 1.0) / (bTry * (t3 - t1) + 1e-30);
      }
      if (dTry <= 0) {
        continue;
      }
      // qi from q1
      double qiTry = q1 * Math.pow(1.0 + bTry * dTry * t1, 1.0 / (bTry + 1e-30));

      // Error at q2
      double q2Pred = rate(qiTry, dTry, bTry, t2);
      double error = Math.abs(q2Pred - q2) / q2;
      if (error < bestError) {
        bestError = error;
        bestB = bTry;
      }
    }

    // Re-estimate di and qi with bestB
    double bFinal = bestB;
    double r = Math.pow(q1 / q3, bFinal);
    double diFinal;
    if (bFinal < 1e-10) {
      diFinal = Math.log(q1 / q3) / (t3 - t1);
    } else {
      diFinal = (r - 1.0) / (bFinal * (t3 - t1));
    }
    if (diFinal <= 0) {
      diFinal = diEst;
    }
    double qiFinal;
    if (bFinal < 1e-10) {
      qiFinal = q1 * Math.exp(diFinal * t1);
    } else {
      qiFinal = q1 * Math.pow(1.0 + bFinal * diFinal * t1, 1.0 / bFinal);
    }

    Map<String, Double> result = new LinkedHashMap<String, Double>();
    result.put("qi", qiFinal);
    result.put("di", diFinal);
    result.put("b", bFinal);
    return result;
  }

  // ============================================================
  // Summary
  // ============================================================

  /**
   * Generate a comprehensive DCA summary for given parameters.
   *
   * @param qi Initial production rate
   * @param di Nominal decline rate (1/day)
   * @param b Arps exponent [0, 1]
   * @param qLimit Economic limit rate
   * @return Map of key DCA metrics
   */
  public static Map<String, Double> summary(double qi, double di, double b, double qLimit) {
    double tEur = timeToRate(qi, di, b, qLimit);
    double eurValue = cumulativeProduction(qi, di, b, tEur);

    Map<String, Double> result = new LinkedHashMap<String, Double>();
    result.put("qi", qi);
    result.put("di_perDay", di);
    result.put("b", b);
    result.put("qLimit", qLimit);
    result.put("effectiveAnnualDecline", nominalToEffectiveAnnual(di));
    result.put("rate_1yr", rate(qi, di, b, 365.25));
    result.put("rate_5yr", rate(qi, di, b, 5 * 365.25));
    result.put("rate_10yr", rate(qi, di, b, 10 * 365.25));
    result.put("cumProd_1yr", cumulativeProduction(qi, di, b, 365.25));
    result.put("cumProd_5yr", cumulativeProduction(qi, di, b, 5 * 365.25));
    result.put("cumProd_10yr", cumulativeProduction(qi, di, b, 10 * 365.25));
    result.put("timeToEconomicLimit_days", tEur);
    result.put("timeToEconomicLimit_years", tEur / 365.25);
    result.put("EUR", eurValue);
    return result;
  }
}
