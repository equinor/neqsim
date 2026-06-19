package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Well deliverability curve - a fast, monotone surrogate of a well's combined IPR + VLP behaviour.
 *
 * <p>
 * Commercial integrated-production tools (Petex GAP, Schlumberger Pipesim) decouple the expensive inflow/lift physics
 * from the network pressure-flow solve by precomputing a deliverability curve (a VFP table reduced to wellhead pressure
 * vs. surface rate). This class plays the same role inside {@link NetworkNewtonSolver}: it stores rate as a strictly
 * decreasing function of wellhead (or sandface) back-pressure so the network solver can evaluate flows and derivatives
 * cheaply and robustly, without re-running a full {@code WellSystem} flash inside every Jacobian perturbation.
 * </p>
 *
 * <h2>Conventions</h2>
 * <ul>
 * <li>Pressures are in bara (ascending order).</li>
 * <li>Rates are surface volumetric rate in Sm3/day (non-increasing as pressure rises).</li>
 * <li>At or above the shut-in (no-flow) pressure the rate is zero.</li>
 * <li>Below the lowest tabulated pressure the curve is extrapolated towards the absolute open-flow potential
 * (AOFP).</li>
 * </ul>
 *
 * <h2>Construction</h2>
 * <ul>
 * <li>{@link #fromArrays(double[], double[])} - from sampled or measured (pressure, rate) pairs.</li>
 * <li>{@link #fromVogel(double, double)} - from an absolute open-flow potential and shut-in pressure using a Vogel-like
 * quadratic shape.</li>
 * <li>{@link #fromWellSystem(neqsim.process.equipment.reservoir.WellSystem, double, double, int)} - by sampling a fully
 * configured integrated well model.</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 * @see NetworkNewtonSolver
 * @see WellBranch
 */
public class WellDeliverabilityCurve implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Wellhead/back pressures in bara, strictly ascending. */
  private final double[] pressure;
  /** Surface rate in Sm3/day, non-increasing with pressure. */
  private final double[] rate;
  /** Shut-in (no-flow) pressure in bara. */
  private final double shutInPressure;
  /** Absolute open-flow potential (rate at zero back-pressure) in Sm3/day. */
  private final double aofp;

  /**
   * Creates a deliverability curve from tabulated (pressure, rate) points.
   *
   * @param pressureBara  wellhead/back pressures in bara, ascending; length &ge; 2
   * @param rateSm3PerDay surface rate in Sm3/day for each pressure; non-increasing
   */
  public WellDeliverabilityCurve(double[] pressureBara, double[] rateSm3PerDay) {
    if (pressureBara == null || rateSm3PerDay == null) {
      throw new IllegalArgumentException("pressure and rate arrays must not be null");
    }
    if (pressureBara.length != rateSm3PerDay.length) {
      throw new IllegalArgumentException("pressure and rate arrays must have equal length");
    }
    if (pressureBara.length < 2) {
      throw new IllegalArgumentException("at least two points are required");
    }
    this.pressure = Arrays.copyOf(pressureBara, pressureBara.length);
    this.rate = Arrays.copyOf(rateSm3PerDay, rateSm3PerDay.length);
    for (int i = 1; i < this.pressure.length; i++) {
      if (this.pressure[i] <= this.pressure[i - 1]) {
	throw new IllegalArgumentException("pressure array must be strictly ascending");
      }
    }
    // Shut-in pressure: first pressure where rate reaches zero, else top of table.
    double shutIn = this.pressure[this.pressure.length - 1];
    for (int i = 0; i < this.rate.length; i++) {
      if (this.rate[i] <= 0.0) {
	shutIn = this.pressure[i];
	break;
      }
    }
    this.shutInPressure = shutIn;
    // AOFP: extrapolate to zero pressure from the two lowest points.
    double slope = (this.rate[1] - this.rate[0]) / (this.pressure[1] - this.pressure[0]);
    this.aofp = Math.max(this.rate[0] - slope * this.pressure[0], this.rate[0]);
  }

  /**
   * Builds a curve from tabulated points.
   *
   * @param pressureBara  wellhead/back pressures in bara, ascending
   * @param rateSm3PerDay surface rate in Sm3/day, non-increasing
   * @return a new deliverability curve
   */
  public static WellDeliverabilityCurve fromArrays(double[] pressureBara, double[] rateSm3PerDay) {
    return new WellDeliverabilityCurve(pressureBara, rateSm3PerDay);
  }

  /**
   * Builds a Vogel-like deliverability curve from an open-flow potential and a shut-in pressure.
   *
   * <p>
   * Uses the Vogel inflow shape q/qmax = 1 - 0.2 (p/ps) - 0.8 (p/ps)^2 mapped to wellhead back-pressure, producing a
   * smooth strictly decreasing curve that is convenient for screening studies and tests.
   * </p>
   *
   * @param aofpSm3PerDay      absolute open-flow potential (rate at zero back-pressure) in Sm3/day
   * @param shutInPressureBara shut-in (no-flow) wellhead pressure in bara
   * @return a new deliverability curve with 11 sample points
   */
  public static WellDeliverabilityCurve fromVogel(double aofpSm3PerDay, double shutInPressureBara) {
    if (aofpSm3PerDay <= 0.0 || shutInPressureBara <= 0.0) {
      throw new IllegalArgumentException("aofp and shut-in pressure must be positive");
    }
    int n = 11;
    double[] p = new double[n];
    double[] q = new double[n];
    for (int i = 0; i < n; i++) {
      double frac = (double) i / (n - 1);
      p[i] = frac * shutInPressureBara;
      double ratio = p[i] / shutInPressureBara;
      q[i] = aofpSm3PerDay * (1.0 - 0.2 * ratio - 0.8 * ratio * ratio);
      if (q[i] < 0.0) {
	q[i] = 0.0;
      }
    }
    return new WellDeliverabilityCurve(p, q);
  }

  /**
   * Samples a configured {@link neqsim.process.equipment.reservoir.WellSystem} to build a deliverability curve.
   *
   * <p>
   * The well is run at a range of wellhead pressures and the resulting operating rate recorded. The full IPR + VLP
   * physics is therefore captured once, then reused cheaply by the network solver. Non-converged or non-physical
   * samples are clamped to zero.
   * </p>
   *
   * @param well                    a fully configured integrated well model
   * @param minWellheadPressureBara lowest wellhead pressure to sample in bara
   * @param maxWellheadPressureBara highest wellhead pressure to sample in bara
   * @param nPoints                 number of sample points (&ge; 2)
   * @return a deliverability curve representing the sampled well
   */
  public static WellDeliverabilityCurve fromWellSystem(neqsim.process.equipment.reservoir.WellSystem well,
      double minWellheadPressureBara, double maxWellheadPressureBara, int nPoints) {
    if (nPoints < 2) {
      throw new IllegalArgumentException("nPoints must be at least 2");
    }
    double[] p = new double[nPoints];
    double[] q = new double[nPoints];
    for (int i = 0; i < nPoints; i++) {
      double frac = (double) i / (nPoints - 1);
      double whp = minWellheadPressureBara + frac * (maxWellheadPressureBara - minWellheadPressureBara);
      p[i] = whp;
      double rateSample = 0.0;
      try {
	well.setWellheadPressure(whp, "bara");
	well.run();
	rateSample = well.getOperatingFlowRate("Sm3/day");
	if (Double.isNaN(rateSample) || rateSample < 0.0) {
	  rateSample = 0.0;
	}
      } catch (RuntimeException ex) {
	rateSample = 0.0;
      }
      q[i] = rateSample;
    }
    // Enforce monotone non-increasing rate with pressure to keep the network solve well-posed.
    for (int i = 1; i < nPoints; i++) {
      if (q[i] > q[i - 1]) {
	q[i] = q[i - 1];
      }
    }
    return new WellDeliverabilityCurve(p, q);
  }

  /**
   * Returns the surface rate delivered at a given wellhead/back pressure.
   *
   * @param pressureBara back pressure in bara
   * @return surface rate in Sm3/day (zero at or above shut-in pressure, never negative)
   */
  public double rateAt(double pressureBara) {
    if (pressureBara >= shutInPressure) {
      return 0.0;
    }
    if (pressureBara <= pressure[0]) {
      // Linear extrapolation towards AOFP, clamped to be non-negative and finite.
      double slope = (rate[1] - rate[0]) / (pressure[1] - pressure[0]);
      double q = rate[0] + slope * (pressureBara - pressure[0]);
      return Math.max(0.0, Math.min(aofp, q));
    }
    int hi = Arrays.binarySearch(pressure, pressureBara);
    if (hi >= 0) {
      return Math.max(0.0, rate[hi]);
    }
    hi = -hi - 1;
    int lo = hi - 1;
    double frac = (pressureBara - pressure[lo]) / (pressure[hi] - pressure[lo]);
    double q = rate[lo] + frac * (rate[hi] - rate[lo]);
    return Math.max(0.0, q);
  }

  /**
   * Returns the derivative of rate with respect to back pressure (Sm3/day per bar, negative).
   *
   * @param pressureBara back pressure in bara
   * @return d(rate)/d(pressure) in Sm3/day/bar
   */
  public double slopeAt(double pressureBara) {
    double dp = 0.01 * Math.max(1.0, shutInPressure);
    double qPlus = rateAt(pressureBara + dp);
    double qMinus = rateAt(Math.max(0.0, pressureBara - dp));
    return (qPlus - qMinus) / (pressureBara + dp - Math.max(0.0, pressureBara - dp));
  }

  /**
   * Returns the shut-in (no-flow) pressure.
   *
   * @return shut-in pressure in bara
   */
  public double getShutInPressure() {
    return shutInPressure;
  }

  /**
   * Returns the absolute open-flow potential.
   *
   * @return AOFP in Sm3/day
   */
  public double getAbsoluteOpenFlowPotential() {
    return aofp;
  }
}
