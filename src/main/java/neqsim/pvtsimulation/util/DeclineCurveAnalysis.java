package neqsim.pvtsimulation.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decline Curve Analysis (DCA) for production forecasting.
 *
 * <p>
 * Implements the Arps family of decline curves used to forecast production rates and estimate reserves (EUR) for oil
 * and gas wells. The three classical Arps models are:
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
 * Cumulative production at time $t$: $$ N_p(t) = \frac{q_i}{(1 - b) D_i} \left[ 1 - (1 + b D_i t)^{(b-1)/b} \right] $$
 *
 * <p>
 * <b>Units:</b> This class is unit-agnostic for production rates. Times are in days by convention. Users should ensure
 * consistent units between rate, time, and decline rate:
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
      throw new IllegalArgumentException("Effective decline rate must be between 0 and 1, got " + dEffective);
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
  public static double remainingReserves(double qi, double di, double b, double tCurrent, double qLimit) {
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
  public static double[][] forecast(double qi, double di, double b, double tStart, double tEnd, double tStep) {
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

    return new double[][] { times, rates, cumProd };
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
   * Uses least-squares fitting via Newton-Raphson iteration. Falls back to exponential estimation if the data does not
   * support a hyperbolic fit ($b \approx 0$).
   *
   * @param q1 Rate at time t1
   * @param t1 First time point (days)
   * @param q2 Rate at time t2
   * @param t2 Second time point (days)
   * @param q3 Rate at time t3
   * @param t3 Third time point (days)
   * @return Map with keys "qi", "di", "b"
   */
  public static Map<String, Double> estimateHyperbolicParameters(double q1, double t1, double q2, double t2, double q3,
      double t3) {
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
  // Least-squares fitting of production data
  // ============================================================

  /**
   * Fit Arps decline parameters to a rate-time production history by least squares.
   *
   * <p>
   * Performs a grid search over the Arps exponent $b \in [0, 1]$. For each candidate $b$ the rate equation is
   * linearised ($\ln q$ versus $t$ for $b = 0$; $q^{-b}$ versus $t$ otherwise) and solved analytically for $q_i$ and
   * $D_i$; the sum of squared residuals is evaluated in rate space and the best $b$ is refined on a finer grid.
   * </p>
   *
   * @param t array of times (days), strictly increasing
   * @param q array of production rates at each time (same unit as returned qi), all positive
   * @return map with keys "qi", "di", "b" and "rSquared"
   */
  public static Map<String, Double> fitArps(double[] t, double[] q) {
    return fitArps(t, q, 0, t == null ? 0 : t.length - 1);
  }

  /**
   * Fit Arps decline parameters over a window of a rate-time production history.
   *
   * <p>
   * Only the samples with index in {@code [startIndex, endIndex]} are used, allowing the transient early-time data to
   * be excluded so that only the established (boundary-dominated) decline is fitted.
   * </p>
   *
   * @param t array of times (days), strictly increasing
   * @param q array of production rates at each time, all positive
   * @param startIndex first sample index to include (inclusive)
   * @param endIndex last sample index to include (inclusive)
   * @return map with keys "qi", "di", "b" and "rSquared"
   */
  public static Map<String, Double> fitArps(double[] t, double[] q, int startIndex, int endIndex) {
    if (t == null || q == null || t.length != q.length) {
      throw new IllegalArgumentException("t and q must be non-null and of equal length");
    }
    if (startIndex < 0 || endIndex >= t.length || startIndex >= endIndex) {
      throw new IllegalArgumentException("invalid window indices");
    }
    int n = endIndex - startIndex + 1;
    if (n < 3) {
      throw new IllegalArgumentException("at least three points are required to fit Arps parameters");
    }
    double[] tw = new double[n];
    double[] qw = new double[n];
    double t0 = t[startIndex];
    for (int i = 0; i < n; i++) {
      tw[i] = t[startIndex + i] - t0;
      qw[i] = q[startIndex + i];
      if (qw[i] <= 0.0) {
        throw new IllegalArgumentException("all rates must be positive");
      }
    }

    double bestB = 0.0;
    double bestQi = qw[0];
    double bestDi = 0.001;
    double bestSse = Double.MAX_VALUE;

    // Coarse then fine grid search over b.
    for (int pass = 0; pass < 2; pass++) {
      double bLo = pass == 0 ? 0.0 : Math.max(0.0, bestB - 0.05);
      double bHi = pass == 0 ? 1.0 : Math.min(1.0, bestB + 0.05);
      int steps = pass == 0 ? 100 : 100;
      for (int k = 0; k <= steps; k++) {
        double bTry = bLo + (bHi - bLo) * k / steps;
        double[] qiDi = linearizeArps(tw, qw, bTry);
        if (qiDi == null) {
          continue;
        }
        double sse = 0.0;
        for (int i = 0; i < n; i++) {
          double pred = rate(qiDi[0], qiDi[1], bTry, tw[i]);
          double res = pred - qw[i];
          sse += res * res;
        }
        if (sse < bestSse) {
          bestSse = sse;
          bestB = bTry;
          bestQi = qiDi[0];
          bestDi = qiDi[1];
        }
      }
    }

    double meanQ = 0.0;
    for (int i = 0; i < n; i++) {
      meanQ += qw[i];
    }
    meanQ /= n;
    double ssTot = 0.0;
    for (int i = 0; i < n; i++) {
      ssTot += (qw[i] - meanQ) * (qw[i] - meanQ);
    }
    double rSquared = ssTot > 1.0e-30 ? 1.0 - bestSse / ssTot : 1.0;

    Map<String, Double> result = new LinkedHashMap<String, Double>();
    result.put("qi", bestQi);
    result.put("di", bestDi);
    result.put("b", bestB);
    result.put("rSquared", rSquared);
    return result;
  }

  /**
   * Estimate ultimate recovery from a fitted Arps decline to an economic-limit rate.
   *
   * @param fit map returned by {@link #fitArps(double[], double[])} (keys "qi", "di", "b")
   * @param qLimit economic-limit rate (same unit as the fitted qi)
   * @return EUR (rate-unit * days)
   */
  public static double eurFromFit(Map<String, Double> fit, double qLimit) {
    double qi = fit.get("qi");
    double di = fit.get("di");
    double b = fit.get("b");
    return eur(qi, di, b, qLimit);
  }

  /**
   * Solve for the Arps qi and Di at a fixed exponent b using an analytic linearisation.
   *
   * @param t window times relative to window start (days)
   * @param q window rates
   * @param b Arps exponent
   * @return two-element array {qi, di}, or {@code null} if the linearisation is not admissible
   */
  private static double[] linearizeArps(double[] t, double[] q, double b) {
    int n = t.length;
    double sx = 0.0;
    double sy = 0.0;
    double sxx = 0.0;
    double sxy = 0.0;
    if (b < 1.0e-6) {
      // Exponential: ln q = ln qi - di t
      for (int i = 0; i < n; i++) {
        double y = Math.log(q[i]);
        sx += t[i];
        sy += y;
        sxx += t[i] * t[i];
        sxy += t[i] * y;
      }
      double denom = n * sxx - sx * sx;
      if (Math.abs(denom) < 1.0e-30) {
        return null;
      }
      double slope = (n * sxy - sx * sy) / denom;
      double intercept = (sy - slope * sx) / n;
      double di = -slope;
      double qi = Math.exp(intercept);
      if (di <= 0.0 || qi <= 0.0) {
        return null;
      }
      return new double[] { qi, di };
    }
    // Hyperbolic/harmonic: q^(-b) = qi^(-b) (1 + b di t) = A + B t
    for (int i = 0; i < n; i++) {
      double y = Math.pow(q[i], -b);
      sx += t[i];
      sy += y;
      sxx += t[i] * t[i];
      sxy += t[i] * y;
    }
    double denom = n * sxx - sx * sx;
    if (Math.abs(denom) < 1.0e-30) {
      return null;
    }
    double slopeB = (n * sxy - sx * sy) / denom;
    double interceptA = (sy - slopeB * sx) / n;
    if (interceptA <= 0.0 || slopeB <= 0.0) {
      return null;
    }
    double qi = Math.pow(interceptA, -1.0 / b);
    double di = slopeB / (interceptA * b);
    if (qi <= 0.0 || di <= 0.0) {
      return null;
    }
    return new double[] { qi, di };
  }

  // ============================================================
  // Duong (2011) decline model (tight/unconventional reservoirs)
  // ============================================================

  /**
   * Duong (2011) time function $t(a, m)$.
   *
   * <p>
   * $$ t(a,m) = t^{-m}\exp\!\left[\frac{a}{1-m}\left(t^{1-m} - 1\right)\right] $$
   * </p>
   *
   * @param a Duong intercept parameter (1/day), positive
   * @param m Duong slope parameter (dimensionless), typically greater than 1
   * @param t time (days), positive
   * @return the Duong time function value
   */
  public static double duongTimeFunction(double a, double m, double t) {
    if (t <= 0.0) {
      return 1.0;
    }
    double exponent = a / (1.0 - m) * (Math.pow(t, 1.0 - m) - 1.0);
    return Math.pow(t, -m) * Math.exp(exponent);
  }

  /**
   * Duong (2011) production rate.
   *
   * <p>
   * $$ q(t) = q_1\, t(a,m) $$ where $q_1$ is the rate at unit time.
   * </p>
   *
   * @param q1 rate at unit time (same unit as the returned rate)
   * @param a Duong intercept parameter (1/day)
   * @param m Duong slope parameter (dimensionless)
   * @param t time (days)
   * @return production rate at time t
   */
  public static double rateDuong(double q1, double a, double m, double t) {
    return q1 * duongTimeFunction(a, m, t);
  }

  /**
   * Duong (2011) cumulative production.
   *
   * <p>
   * $$ G_p(t) = \frac{q_1}{a}\exp\!\left[\frac{a}{1-m}\left(t^{1-m} - 1\right)\right] $$
   * </p>
   *
   * @param q1 rate at unit time
   * @param a Duong intercept parameter (1/day), positive
   * @param m Duong slope parameter (dimensionless)
   * @param t time (days), positive
   * @return cumulative production at time t (rate-unit * days)
   */
  public static double cumulativeDuong(double q1, double a, double m, double t) {
    if (t <= 0.0) {
      return 0.0;
    }
    double exponent = a / (1.0 - m) * (Math.pow(t, 1.0 - m) - 1.0);
    return q1 / a * Math.exp(exponent);
  }

  /**
   * Fit the Duong (2011) decline model to a rate-time production history.
   *
   * <p>
   * The Duong model is defined by the log-log straight line $q/G_p = a\,t^{-m}$. Cumulative production is estimated by
   * trapezoidal integration of the rate history, the slope $-m$ and intercept $a$ are obtained from a least-squares fit
   * of $\ln(q/G_p)$ against $\ln t$, and $q_1$ is obtained from a through-origin fit of $q$ against $t(a,m)$.
   * </p>
   *
   * @param t array of times (days), strictly increasing and positive
   * @param q array of production rates at each time, all positive
   * @return map with keys "q1", "a", "m" and "rSquared"
   */
  public static Map<String, Double> fitDuong(double[] t, double[] q) {
    if (t == null || q == null || t.length != q.length) {
      throw new IllegalArgumentException("t and q must be non-null and of equal length");
    }
    int n = t.length;
    if (n < 3) {
      throw new IllegalArgumentException("at least three points are required to fit the Duong model");
    }
    for (int i = 0; i < n; i++) {
      if (t[i] <= 0.0 || q[i] <= 0.0) {
        throw new IllegalArgumentException("times and rates must be strictly positive");
      }
      if (i > 0 && t[i] <= t[i - 1]) {
        throw new IllegalArgumentException("times must be strictly increasing");
      }
    }

    // Cumulative production by trapezoidal integration (initial ramp from 0 to t[0]).
    double[] gp = new double[n];
    gp[0] = q[0] * t[0];
    for (int i = 1; i < n; i++) {
      gp[i] = gp[i - 1] + 0.5 * (q[i] + q[i - 1]) * (t[i] - t[i - 1]);
    }

    // Fit ln(q/Gp) = ln a - m ln t.
    double sx = 0.0;
    double sy = 0.0;
    double sxx = 0.0;
    double sxy = 0.0;
    for (int i = 0; i < n; i++) {
      double x = Math.log(t[i]);
      double y = Math.log(q[i] / gp[i]);
      sx += x;
      sy += y;
      sxx += x * x;
      sxy += x * y;
    }
    double denom = n * sxx - sx * sx;
    if (Math.abs(denom) < 1.0e-30) {
      throw new IllegalArgumentException("Duong regression is singular");
    }
    double slope = (n * sxy - sx * sy) / denom;
    double intercept = (sy - slope * sx) / n;
    double m = -slope;
    double a = Math.exp(intercept);

    // Fit q1 through the origin of q vs t(a,m).
    double stt = 0.0;
    double stq = 0.0;
    for (int i = 0; i < n; i++) {
      double tf = duongTimeFunction(a, m, t[i]);
      stt += tf * tf;
      stq += tf * q[i];
    }
    double q1 = stt > 1.0e-30 ? stq / stt : q[0];

    // R^2 in rate space.
    double meanQ = 0.0;
    for (int i = 0; i < n; i++) {
      meanQ += q[i];
    }
    meanQ /= n;
    double ssRes = 0.0;
    double ssTot = 0.0;
    for (int i = 0; i < n; i++) {
      double pred = rateDuong(q1, a, m, t[i]);
      ssRes += (q[i] - pred) * (q[i] - pred);
      ssTot += (q[i] - meanQ) * (q[i] - meanQ);
    }
    double rSquared = ssTot > 1.0e-30 ? 1.0 - ssRes / ssTot : 1.0;

    Map<String, Double> result = new LinkedHashMap<String, Double>();
    result.put("q1", q1);
    result.put("a", a);
    result.put("m", m);
    result.put("rSquared", rSquared);
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
