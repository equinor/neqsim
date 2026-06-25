package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Fits an inflow-performance relationship (IPR) to measured well-test data and produces a fast
 * {@link WellDeliverabilityCurve} surrogate.
 *
 * <p>
 * Two IPR models are supported, mirroring PROSPER/PIPESIM well-test matching:
 * </p>
 * <ul>
 * <li><b>Productivity index (PI)</b> - linear, single-phase liquid inflow q = J&middot;(p_r - p_wf).</li>
 * <li><b>Vogel</b> - two-phase (solution-gas drive) inflow q/q_max = 1 - 0.2(p_wf/p_r) - 0.8(p_wf/p_r)&sup2;.</li>
 * </ul>
 *
 * <p>
 * The matcher minimises the sum of squared rate residuals over the reservoir pressure and deliverability parameter
 * using a self-contained bounded coordinate search (no external optimiser dependency), so it is safe to call inside
 * agentic loops. The fitted curve can then be dropped straight into an {@link IntegratedProductionModel}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see WellDeliverabilityCurve
 * @see IntegratedProductionModel
 */
public class WellTestMatcher implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** A single well-test observation. */
  private static class TestPoint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double rate; // Sm3/day
    private final double flowingPressure; // bara (Pwf or back pressure)

    TestPoint(double rate, double flowingPressure) {
      this.rate = rate;
      this.flowingPressure = flowingPressure;
    }
  }

  /**
   * Result of a well-test match.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class MatchResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final WellDeliverabilityCurve curve;
    private final double reservoirPressure;
    private final double deliverabilityParameter;
    private final double rmsError;
    private final String model;

    /**
     * Creates a match result.
     *
     * @param curve fitted deliverability curve
     * @param reservoirPressure fitted reservoir (shut-in) pressure in bara
     * @param deliverabilityParameter fitted PI (Sm3/day/bar) or AOFP (Sm3/day)
     * @param rmsError root-mean-square rate residual in Sm3/day
     * @param model model name ("PI" or "Vogel")
     */
    public MatchResult(WellDeliverabilityCurve curve, double reservoirPressure, double deliverabilityParameter,
        double rmsError, String model) {
      this.curve = curve;
      this.reservoirPressure = reservoirPressure;
      this.deliverabilityParameter = deliverabilityParameter;
      this.rmsError = rmsError;
      this.model = model;
    }

    /**
     * Returns the fitted deliverability curve.
     *
     * @return deliverability curve
     */
    public WellDeliverabilityCurve getCurve() {
      return curve;
    }

    /**
     * Returns the fitted reservoir pressure.
     *
     * @return reservoir pressure in bara
     */
    public double getReservoirPressure() {
      return reservoirPressure;
    }

    /**
     * Returns the fitted deliverability parameter (PI or AOFP).
     *
     * @return PI in Sm3/day/bar or AOFP in Sm3/day
     */
    public double getDeliverabilityParameter() {
      return deliverabilityParameter;
    }

    /**
     * Returns the RMS rate residual of the fit.
     *
     * @return RMS error in Sm3/day
     */
    public double getRmsError() {
      return rmsError;
    }

    /**
     * Returns the model name.
     *
     * @return "PI" or "Vogel"
     */
    public String getModel() {
      return model;
    }
  }

  private final List<TestPoint> points = new ArrayList<TestPoint>();

  /**
   * Adds a well-test observation.
   *
   * @param rateSm3PerDay measured rate in Sm3/day
   * @param flowingPressureBara measured flowing pressure (Pwf or back pressure) in bara
   * @return this matcher for chaining
   */
  public WellTestMatcher addTestPoint(double rateSm3PerDay, double flowingPressureBara) {
    points.add(new TestPoint(rateSm3PerDay, flowingPressureBara));
    return this;
  }

  /**
   * Fits a linear productivity-index IPR.
   *
   * @return the match result with a fitted deliverability curve
   */
  public MatchResult fitProductivityIndex() {
    if (points.size() < 1) {
      throw new IllegalStateException("at least one test point is required");
    }
    // For a fixed reservoir pressure pr, the least-squares PI is
    // J = sum[q*(pr-pwf)] / sum[(pr-pwf)^2]. Search pr to minimise residual.
    double prMin = maxFlowingPressure() + 1.0;
    double prMax = prMin + 600.0;
    double bestPr = prMin;
    double bestJ = 0.0;
    double bestErr = Double.MAX_VALUE;
    for (int i = 0; i <= 600; i++) {
      double pr = prMin + (prMax - prMin) * i / 600.0;
      double num = 0.0;
      double den = 0.0;
      for (TestPoint tp : points) {
        double dp = pr - tp.flowingPressure;
        num += tp.rate * dp;
        den += dp * dp;
      }
      double j = den > 0.0 ? num / den : 0.0;
      double err = 0.0;
      for (TestPoint tp : points) {
        double pred = j * (pr - tp.flowingPressure);
        err += (pred - tp.rate) * (pred - tp.rate);
      }
      if (err < bestErr) {
        bestErr = err;
        bestPr = pr;
        bestJ = j;
      }
    }
    // Build a deliverability curve: rate at back pressure p = J*(pr - p).
    int n = 12;
    double[] pressure = new double[n];
    double[] rate = new double[n];
    for (int i = 0; i < n; i++) {
      double p = bestPr * i / (n - 1.0);
      pressure[i] = p;
      rate[i] = Math.max(0.0, bestJ * (bestPr - p));
    }
    WellDeliverabilityCurve curve = WellDeliverabilityCurve.fromArrays(pressure, rate);
    double rms = Math.sqrt(bestErr / points.size());
    return new MatchResult(curve, bestPr, bestJ, rms, "PI");
  }

  /**
   * Fits a Vogel two-phase IPR.
   *
   * @return the match result with a fitted deliverability curve
   */
  public MatchResult fitVogel() {
    if (points.size() < 1) {
      throw new IllegalStateException("at least one test point is required");
    }
    double prMin = maxFlowingPressure() + 1.0;
    double prMax = prMin + 600.0;
    double bestPr = prMin;
    double bestQmax = 0.0;
    double bestErr = Double.MAX_VALUE;
    for (int i = 0; i <= 400; i++) {
      double pr = prMin + (prMax - prMin) * i / 400.0;
      // Closed-form q_max for fixed pr: q = q_max * f(pwf/pr) -> q_max = sum(q*f)/sum(f^2).
      double num = 0.0;
      double den = 0.0;
      for (TestPoint tp : points) {
        double r = tp.flowingPressure / pr;
        double f = 1.0 - 0.2 * r - 0.8 * r * r;
        num += tp.rate * f;
        den += f * f;
      }
      double qmax = den > 0.0 ? num / den : 0.0;
      double err = 0.0;
      for (TestPoint tp : points) {
        double r = tp.flowingPressure / pr;
        double pred = qmax * (1.0 - 0.2 * r - 0.8 * r * r);
        err += (pred - tp.rate) * (pred - tp.rate);
      }
      if (err < bestErr) {
        bestErr = err;
        bestPr = pr;
        bestQmax = qmax;
      }
    }
    WellDeliverabilityCurve curve = WellDeliverabilityCurve.fromVogel(bestQmax, bestPr);
    double rms = Math.sqrt(bestErr / points.size());
    return new MatchResult(curve, bestPr, bestQmax, rms, "Vogel");
  }

  /**
   * Returns the maximum flowing pressure among the test points.
   *
   * @return maximum flowing pressure in bara
   */
  private double maxFlowingPressure() {
    double max = 0.0;
    for (TestPoint tp : points) {
      max = Math.max(max, tp.flowingPressure);
    }
    return max;
  }
}
