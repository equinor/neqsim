package neqsim.process.mechanicaldesign.valve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Valve characteristic defined by measured or vendor points of flow coefficient against opening.
 *
 * <p>
 * The points give the relative flow coefficient {@code Cv(opening) / Cv(last point)}. Between the points a monotone
 * cubic Hermite interpolation (Fritsch-Carlson) is used, so the curve never overshoots the data and never decreases.
 * That keeps the inverse problem (opening for a required flow coefficient) single valued, which pressure and flow
 * controllers and optimisers rely on.
 * </p>
 * <ul>
 * <li>Below the first point the factor falls linearly to zero at zero opening.</li>
 * <li>Above the last point the factor stays at one; the valve flow coefficient set on the valve is the value at the
 * last point (normally 100 % opening).</li>
 * <li>{@link #fromMeasurements(double[], double[])} sorts, merges repeated openings and enforces a non-decreasing curve
 * with pool-adjacent-violators regression, for noisy plant data such as a choke curve from a historian.</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 * @see ValveCharacteristic
 */
public class TabulatedValveCharacteristic implements ValveCharacteristic {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Maximum opening in percent. */
  private static final double MAX_OPENING = 100.0;
  /** Tolerance for treating two openings as equal [percent]. */
  private static final double OPENING_TOLERANCE = 1.0e-9;

  /** Opening points [percent], strictly increasing. */
  private final double[] opening;
  /** Relative flow coefficient at each point, non-decreasing, last value 1. */
  private final double[] fraction;
  /** Curve slopes at the points for the Hermite interpolation. */
  private final double[] slope;

  /**
   * Creates a characteristic from points that already form a non-decreasing curve.
   *
   * @param openingPercent opening points [percent], strictly increasing, within 0 to 100, at least two
   * @param flowCoefficient flow coefficient at each point (Cv or Kv, one unit), non-negative and non-decreasing, the
   * last value positive
   * @throws IllegalArgumentException if the arrays differ in length, hold fewer than two points, contain non-finite
   * values, or the openings are not strictly increasing or the coefficients decrease
   */
  public TabulatedValveCharacteristic(double[] openingPercent, double[] flowCoefficient) {
    if (openingPercent == null || flowCoefficient == null || openingPercent.length != flowCoefficient.length
        || openingPercent.length < 2) {
      throw new IllegalArgumentException("at least two matching opening and flow coefficient points are required");
    }
    int n = openingPercent.length;
    for (int i = 0; i < n; i++) {
      if (!Double.isFinite(openingPercent[i]) || !Double.isFinite(flowCoefficient[i]) || openingPercent[i] < 0.0
          || openingPercent[i] > MAX_OPENING || flowCoefficient[i] < 0.0) {
        throw new IllegalArgumentException("points must be finite, opening within 0 to 100 and coefficient >= 0");
      }
      if (i > 0 && openingPercent[i] <= openingPercent[i - 1]) {
        throw new IllegalArgumentException("openings must be strictly increasing");
      }
      if (i > 0 && flowCoefficient[i] < flowCoefficient[i - 1]) {
        throw new IllegalArgumentException("flow coefficient must not decrease with opening; use fromMeasurements");
      }
    }
    double top = flowCoefficient[n - 1];
    if (top <= 0.0) {
      throw new IllegalArgumentException("the flow coefficient at the last point must be positive");
    }
    this.opening = openingPercent.clone();
    this.fraction = new double[n];
    for (int i = 0; i < n; i++) {
      fraction[i] = flowCoefficient[i] / top;
    }
    this.slope = monotoneSlopes(opening, fraction);
  }

  /**
   * Builds a characteristic from noisy points: drops non-finite and out-of-range points, sorts by opening, averages
   * repeated openings and makes the curve non-decreasing by pool-adjacent-violators regression.
   *
   * @param openingPercent measured opening [percent]
   * @param flowCoefficient measured flow coefficient (one unit)
   * @return characteristic through the cleaned points
   * @throws IllegalArgumentException if fewer than two distinct openings remain or the largest coefficient is not
   * positive
   */
  public static TabulatedValveCharacteristic fromMeasurements(double[] openingPercent, double[] flowCoefficient) {
    if (openingPercent == null || flowCoefficient == null || openingPercent.length != flowCoefficient.length) {
      throw new IllegalArgumentException("opening and flow coefficient arrays must have the same length");
    }
    List<double[]> pts = new ArrayList<double[]>();
    for (int i = 0; i < openingPercent.length; i++) {
      double o = openingPercent[i];
      double c = flowCoefficient[i];
      if (Double.isFinite(o) && Double.isFinite(c) && o >= 0.0 && o <= MAX_OPENING && c >= 0.0) {
        pts.add(new double[] {o, c});
      }
    }
    Collections.sort(pts, new Comparator<double[]>() {
      @Override
      public int compare(double[] a, double[] b) {
        return Double.compare(a[0], b[0]);
      }
    });
    List<double[]> merged = new ArrayList<double[]>();
    for (double[] p : pts) {
      double[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
      if (last != null && Math.abs(last[0] - p[0]) <= OPENING_TOLERANCE) {
        last[1] = (last[1] * last[2] + p[1]) / (last[2] + 1.0);
        last[2] += 1.0;
      } else {
        merged.add(new double[] {p[0], p[1], 1.0});
      }
    }
    if (merged.size() < 2) {
      throw new IllegalArgumentException("at least two distinct openings are required");
    }
    int m = merged.size();
    double[] o = new double[m];
    double[] v = new double[m];
    double[] w = new double[m];
    for (int i = 0; i < m; i++) {
      o[i] = merged.get(i)[0];
      v[i] = merged.get(i)[1];
      w[i] = merged.get(i)[2];
    }
    return new TabulatedValveCharacteristic(o, isotonic(v, w));
  }

  /**
   * Pool-adjacent-violators regression to a non-decreasing sequence.
   *
   * @param values values in opening order
   * @param weights weight of each value
   * @return non-decreasing sequence closest to the values in the weighted least-squares sense
   */
  private static double[] isotonic(double[] values, double[] weights) {
    int n = values.length;
    double[] level = new double[n];
    double[] weight = new double[n];
    int[] count = new int[n];
    int blocks = 0;
    for (int i = 0; i < n; i++) {
      level[blocks] = values[i];
      weight[blocks] = weights[i];
      count[blocks] = 1;
      blocks++;
      while (blocks > 1 && level[blocks - 2] > level[blocks - 1]) {
        double w = weight[blocks - 2] + weight[blocks - 1];
        level[blocks - 2] = (level[blocks - 2] * weight[blocks - 2] + level[blocks - 1] * weight[blocks - 1]) / w;
        weight[blocks - 2] = w;
        count[blocks - 2] += count[blocks - 1];
        blocks--;
      }
    }
    double[] out = new double[n];
    int k = 0;
    for (int b = 0; b < blocks; b++) {
      for (int j = 0; j < count[b]; j++) {
        out[k++] = level[b];
      }
    }
    return out;
  }

  /**
   * Fritsch-Carlson slopes of a monotone cubic Hermite interpolant.
   *
   * @param x strictly increasing abscissae
   * @param y non-decreasing ordinates
   * @return slope at each point
   */
  private static double[] monotoneSlopes(double[] x, double[] y) {
    int n = x.length;
    double[] d = new double[n - 1];
    double[] m = new double[n];
    for (int i = 0; i < n - 1; i++) {
      d[i] = (y[i + 1] - y[i]) / (x[i + 1] - x[i]);
    }
    m[0] = d[0];
    m[n - 1] = d[n - 2];
    for (int i = 1; i < n - 1; i++) {
      m[i] = d[i - 1] * d[i] <= 0.0 ? 0.0 : 0.5 * (d[i - 1] + d[i]);
    }
    for (int i = 0; i < n - 1; i++) {
      if (d[i] == 0.0) {
        m[i] = 0.0;
        m[i + 1] = 0.0;
        continue;
      }
      double a = m[i] / d[i];
      double b = m[i + 1] / d[i];
      double s = a * a + b * b;
      if (s > 9.0) {
        double t = 3.0 / Math.sqrt(s);
        m[i] = t * a * d[i];
        m[i + 1] = t * b * d[i];
      }
    }
    return m;
  }

  /** {@inheritDoc} */
  @Override
  public double getActualKv(double Kv, double percentOpening) {
    return Kv * getOpeningFactor(percentOpening);
  }

  /** {@inheritDoc} */
  @Override
  public double getOpeningFactor(double percentOpening) {
    int n = opening.length;
    double x = Math.max(0.0, Math.min(MAX_OPENING, percentOpening));
    if (x <= opening[0]) {
      return opening[0] > 0.0 ? fraction[0] * x / opening[0] : fraction[0];
    }
    if (x >= opening[n - 1]) {
      return 1.0;
    }
    int lo = 0;
    int hi = n - 1;
    while (hi - lo > 1) {
      int mid = (lo + hi) >>> 1;
      if (opening[mid] <= x) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    double h = opening[hi] - opening[lo];
    double t = (x - opening[lo]) / h;
    double t2 = t * t;
    double t3 = t2 * t;
    return (2.0 * t3 - 3.0 * t2 + 1.0) * fraction[lo] + (t3 - 2.0 * t2 + t) * h * slope[lo]
        + (-2.0 * t3 + 3.0 * t2) * fraction[hi] + (t3 - t2) * h * slope[hi];
  }

  /**
   * Opening that gives a required relative flow coefficient.
   *
   * @param flowFraction required flow coefficient divided by the value at the last point
   * @return opening [percent]; zero for a non-positive fraction and the last tabulated opening for a fraction of one or
   * more
   */
  public double openingForFlowFraction(double flowFraction) {
    if (flowFraction <= 0.0) {
      return 0.0;
    }
    double top = opening[opening.length - 1];
    if (flowFraction >= 1.0) {
      return top;
    }
    double lo = 0.0;
    double hi = top;
    for (int i = 0; i < 80; i++) {
      double mid = 0.5 * (lo + hi);
      if (getOpeningFactor(mid) < flowFraction) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Opening points.
   *
   * @return copy of the opening points [percent]
   */
  public double[] getOpeningPoints() {
    return Arrays.copyOf(opening, opening.length);
  }

  /**
   * Relative flow coefficient at the points.
   *
   * @return copy of the relative flow coefficients [-]
   */
  public double[] getFlowFractionPoints() {
    return Arrays.copyOf(fraction, fraction.length);
  }
}
