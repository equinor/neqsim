package neqsim.process.fielddevelopment.reservoir;

/**
 * Turns any forward pressure model into a {@link VlpProvider} by solving the inlet (datum) pressure that delivers the
 * requested outlet pressure.
 *
 * <p>
 * A forward model gives the outlet pressure for a known inlet pressure, which is what chains of
 * {@code PipeBeggsAndBrills} or {@code TubingPerformance} instances, valves and whole {@code ProcessSystem} objects
 * naturally compute. Full-system lift curves (outlet pressure fixed at the host inlet) and flowline-plus-tubing curves
 * are built this way. The outlet pressure must be non-decreasing in the inlet pressure; the solver brackets the root
 * with a unit-slope step and finishes with secant and bisection.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ForwardVlpProvider implements VlpProvider {

  /**
   * Forward pressure model: outlet pressure for a given inlet pressure and surface rates.
   */
  @FunctionalInterface
  public interface ForwardPressureModel {
    /**
     * Calculates the outlet pressure.
     *
     * @param rate surface rate in Sm3/day of the table rate basis
     * @param inletPressure inlet (datum) pressure in bara
     * @param waterRatio water ratio of the table
     * @param gasRatio gas ratio of the table
     * @param alq artificial lift quantity
     * @return outlet pressure in bara; NaN when the model fails
     */
    double outletPressure(double rate, double inletPressure, double waterRatio, double gasRatio, double alq);
  }

  private final ForwardPressureModel model;
  private double toleranceBar = 0.02;
  private double minInletBar = 1.0;
  private double maxInletBar = 2000.0;
  private int evaluations = 0;

  /**
   * Creates a provider.
   *
   * @param model forward pressure model
   */
  public ForwardVlpProvider(ForwardPressureModel model) {
    this.model = model;
  }

  /**
   * Sets the pressure tolerance of the inverse solve.
   *
   * @param bar absolute tolerance in bar
   * @return this provider
   */
  public ForwardVlpProvider setTolerance(double bar) {
    this.toleranceBar = bar;
    return this;
  }

  /**
   * Sets the search bounds for the inlet pressure.
   *
   * @param minBar lower bound in bara
   * @param maxBar upper bound in bara
   * @return this provider
   */
  public ForwardVlpProvider setInletBounds(double minBar, double maxBar) {
    this.minInletBar = minBar;
    this.maxInletBar = maxBar;
    return this;
  }

  /**
   * Number of forward-model evaluations used so far.
   *
   * @return evaluation count
   */
  public int getEvaluations() {
    return evaluations;
  }

  /** {@inheritDoc} */
  @Override
  public double bottomHolePressure(double rate, double outletPressure, double waterRatio, double gasRatio, double alq) {
    double lo = Double.NaN;
    double flo = Double.NaN;
    double hi = Double.NaN;
    double fhi = Double.NaN;
    double p = Math.min(maxInletBar, Math.max(minInletBar, outletPressure * 1.2 + 10.0));
    for (int iter = 0; iter < 80; iter++) {
      double out = model.outletPressure(rate, p, waterRatio, gasRatio, alq);
      evaluations++;
      if (Double.isNaN(out)) {
        return Double.NaN;
      }
      double f = out - outletPressure;
      if (Math.abs(f) < toleranceBar) {
        return p;
      }
      if (f < 0.0) {
        lo = p;
        flo = f;
      } else {
        hi = p;
        fhi = f;
      }
      double next;
      if (!Double.isNaN(lo) && !Double.isNaN(hi)) {
        next = hi - fhi * (hi - lo) / (fhi - flo);
        if (!(next > lo && next < hi) || iter % 4 == 3) {
          next = 0.5 * (lo + hi);
        }
      } else {
        next = p - f;
        if (f < 0.0 && next <= p) {
          next = p * 1.05;
        }
        if (f > 0.0 && next >= p) {
          next = p * 0.95;
        }
      }
      if (next >= maxInletBar && p >= maxInletBar) {
        return Double.NaN;
      }
      if (next <= minInletBar && p <= minInletBar) {
        return Double.NaN;
      }
      p = Math.min(maxInletBar, Math.max(minInletBar, next));
    }
    return Double.NaN;
  }
}
