package neqsim.process.fielddevelopment.wellplanning;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Nodal (inflow times outflow) analysis at the bottom-hole node with stability classification and sensitivity sweeps.
 *
 * <p>
 * The inflow curve gives the available flowing bottom-hole pressure as a function of rate (decreasing with rate) and
 * the outflow curve the required bottom-hole pressure for a given wellhead pressure (tubing performance, often U-shaped
 * in gas wells with liquid loading). The rate range is scanned for sign changes of inflow minus outflow and every
 * intersection is refined by bisection. An intersection is stable when the outflow curve rises faster with rate than
 * the inflow curve (the net slope of inflow minus outflow is negative); the highest-rate stable intersection is the
 * operating point. Rates may be in any consistent unit (for example Sm3/day or kg/s).
 * </p>
 * <p>
 * Ready adapters: linear productivity index, Vogel, tabulated curves, the gas-condensate inflow model and the
 * drift-flux wellbore hydraulics. Sensitivities on tubing size, wellhead pressure, skin, GOR, water cut or depletion
 * are run by supplying a factory that builds the inflow or outflow curve for each parameter value.
 * </p>
 * <p>
 * Limitations: single-node (bottom hole), one well, no choke or network back-pressure coupling (use the existing
 * WellSystem or production-network classes for that). Validation is by closed-form intersections of linear and Vogel
 * curves.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class NodalAnalysis {
  /** Inflow performance: available bottom-hole pressure versus rate. */
  public interface InflowCurve {
    /**
     * Returns the flowing bottom-hole pressure the reservoir can sustain at a rate.
     *
     * @param rate rate in the analysis unit
     * @return pressure in bara
     */
    double bottomholePressureBara(double rate);
  }

  /** Outflow performance: required bottom-hole pressure versus rate. */
  public interface OutflowCurve {
    /**
     * Returns the bottom-hole pressure required to lift the rate to the wellhead.
     *
     * @param rate rate in the analysis unit
     * @return pressure in bara
     */
    double requiredBottomholePressureBara(double rate);
  }

  /** Factory building an outflow curve for a sensitivity parameter value. */
  public interface OutflowFactory {
    /**
     * Creates the outflow curve for a parameter value.
     *
     * @param value parameter value
     * @return outflow curve
     */
    OutflowCurve create(double value);
  }

  /** Factory building an inflow curve for a sensitivity parameter value. */
  public interface InflowFactory {
    /**
     * Creates the inflow curve for a parameter value.
     *
     * @param value parameter value
     * @return inflow curve
     */
    InflowCurve create(double value);
  }

  private final InflowCurve inflow;
  private final OutflowCurve outflow;
  private double minRate = 1.0e-6;
  private double maxRate = 1.0e6;
  private int scanPoints = 200;

  /**
   * Creates a nodal analysis.
   *
   * @param inflow inflow curve
   * @param outflow outflow curve
   */
  public NodalAnalysis(InflowCurve inflow, OutflowCurve outflow) {
    this.inflow = inflow;
    this.outflow = outflow;
  }

  /**
   * Sets the scanned rate range.
   *
   * @param minRate lowest rate (above zero)
   * @param maxRate highest rate
   * @return this analysis
   */
  public NodalAnalysis setRateRange(double minRate, double maxRate) {
    this.minRate = minRate;
    this.maxRate = maxRate;
    return this;
  }

  /**
   * Sets the number of scan points.
   *
   * @param points number of scan points (at least 10)
   * @return this analysis
   */
  public NodalAnalysis setScanPoints(int points) {
    this.scanPoints = Math.max(10, points);
    return this;
  }

  /**
   * Finds all intersections of inflow and outflow in the rate range.
   *
   * @return result with the intersections and the operating point
   */
  public Result solve() {
    Result res = new Result();
    double prevRate = minRate;
    double prevDiff = diff(prevRate);
    for (int i = 1; i <= scanPoints; i++) {
      double rate = minRate + (maxRate - minRate) * i / scanPoints;
      double d = diff(rate);
      if (!Double.isNaN(prevDiff) && !Double.isNaN(d) && prevDiff * d < 0.0) {
        double lo = prevRate;
        double hi = rate;
        double dLo = prevDiff;
        for (int k = 0; k < 60; k++) {
          double mid = 0.5 * (lo + hi);
          double dm = diff(mid);
          if (dm * dLo > 0.0) {
            lo = mid;
            dLo = dm;
          } else {
            hi = mid;
          }
        }
        double q = 0.5 * (lo + hi);
        Intersection x = new Intersection();
        x.rate = q;
        x.bottomholePressureBara = inflow.bottomholePressureBara(q);
        x.stable = prevDiff > 0.0 && d < 0.0;
        res.intersections.add(x);
      }
      prevRate = rate;
      prevDiff = d;
    }
    for (Intersection x : res.intersections) {
      if (x.stable && (res.operating == null || x.rate > res.operating.rate)) {
        res.operating = x;
      }
    }
    return res;
  }

  /**
   * Inflow minus outflow bottom-hole pressure.
   *
   * @param rate rate
   * @return pressure difference in bar
   */
  private double diff(double rate) {
    return inflow.bottomholePressureBara(rate) - outflow.requiredBottomholePressureBara(rate);
  }

  /**
   * Sweeps a parameter that changes the outflow curve and returns the operating rate for each value.
   *
   * @param inflow inflow curve held constant
   * @param factory outflow factory
   * @param values parameter values
   * @param minRate lowest rate
   * @param maxRate highest rate
   * @return rows of {parameter value, operating rate (NaN if none), bottom-hole pressure (NaN if none)}
   */
  public static double[][] sweepOutflow(InflowCurve inflow, OutflowFactory factory, double[] values, double minRate,
      double maxRate) {
    double[][] out = new double[values.length][3];
    for (int i = 0; i < values.length; i++) {
      Result r = new NodalAnalysis(inflow, factory.create(values[i])).setRateRange(minRate, maxRate).solve();
      out[i][0] = values[i];
      out[i][1] = r.getOperatingPoint() == null ? Double.NaN : r.getOperatingPoint().getRate();
      out[i][2] = r.getOperatingPoint() == null ? Double.NaN : r.getOperatingPoint().getBottomholePressureBara();
    }
    return out;
  }

  /**
   * Sweeps a parameter that changes the inflow curve and returns the operating rate for each value.
   *
   * @param factory inflow factory
   * @param outflow outflow curve held constant
   * @param values parameter values
   * @param minRate lowest rate
   * @param maxRate highest rate
   * @return rows of {parameter value, operating rate (NaN if none), bottom-hole pressure (NaN if none)}
   */
  public static double[][] sweepInflow(InflowFactory factory, OutflowCurve outflow, double[] values, double minRate,
      double maxRate) {
    double[][] out = new double[values.length][3];
    for (int i = 0; i < values.length; i++) {
      Result r = new NodalAnalysis(factory.create(values[i]), outflow).setRateRange(minRate, maxRate).solve();
      out[i][0] = values[i];
      out[i][1] = r.getOperatingPoint() == null ? Double.NaN : r.getOperatingPoint().getRate();
      out[i][2] = r.getOperatingPoint() == null ? Double.NaN : r.getOperatingPoint().getBottomholePressureBara();
    }
    return out;
  }

  /**
   * Creates a linear productivity-index inflow, p_wf = p_res - q / PI.
   *
   * @param reservoirPressureBara reservoir pressure in bara
   * @param productivityIndex productivity index in rate unit per bar
   * @return inflow curve
   */
  public static InflowCurve linearInflow(final double reservoirPressureBara, final double productivityIndex) {
    return new InflowCurve() {
      @Override
      public double bottomholePressureBara(double rate) {
        return reservoirPressureBara - rate / productivityIndex;
      }
    };
  }

  /**
   * Creates a Vogel inflow, q = q_max (1 - 0.2 (p/pr) - 0.8 (p/pr)^2), inverted analytically for p_wf.
   *
   * @param reservoirPressureBara reservoir pressure in bara
   * @param absoluteOpenFlow rate at zero flowing pressure
   * @return inflow curve (zero rate returns the reservoir pressure, rates above the maximum return zero)
   */
  public static InflowCurve vogelInflow(final double reservoirPressureBara, final double absoluteOpenFlow) {
    return new InflowCurve() {
      @Override
      public double bottomholePressureBara(double rate) {
        double ratio = Math.min(Math.max(rate / absoluteOpenFlow, 0.0), 1.0);
        double x = (-0.2 + Math.sqrt(0.04 + 3.2 * (1.0 - ratio))) / 1.6;
        return reservoirPressureBara * x;
      }
    };
  }

  /**
   * Creates an inflow curve from a table with linear interpolation.
   *
   * @param rates increasing rates
   * @param pressuresBara bottom-hole pressures in bara
   * @return inflow curve (clamped at the table ends)
   */
  public static InflowCurve tabulatedInflow(final double[] rates, final double[] pressuresBara) {
    final double[] r = rates.clone();
    final double[] p = pressuresBara.clone();
    return new InflowCurve() {
      @Override
      public double bottomholePressureBara(double rate) {
        return interpolate(r, p, rate);
      }
    };
  }

  /**
   * Creates an outflow curve from a table with linear interpolation.
   *
   * @param rates increasing rates
   * @param pressuresBara required bottom-hole pressures in bara
   * @return outflow curve (clamped at the table ends)
   */
  public static OutflowCurve tabulatedOutflow(final double[] rates, final double[] pressuresBara) {
    final double[] r = rates.clone();
    final double[] p = pressuresBara.clone();
    return new OutflowCurve() {
      @Override
      public double requiredBottomholePressureBara(double rate) {
        return interpolate(r, p, rate);
      }
    };
  }

  /**
   * Linear interpolation with clamping.
   *
   * @param x increasing abscissae
   * @param y ordinates
   * @param v value
   * @return interpolated value
   */
  private static double interpolate(double[] x, double[] y, double v) {
    if (v <= x[0]) {
      return y[0];
    }
    for (int i = 1; i < x.length; i++) {
      if (v <= x[i]) {
        return y[i - 1] + (y[i] - y[i - 1]) * (v - x[i - 1]) / (x[i] - x[i - 1]);
      }
    }
    return y[y.length - 1];
  }

  /**
   * Adapts the gas-condensate inflow model: rate is the gas-equivalent rate in Sm3/day.
   *
   * @param model gas-condensate inflow model
   * @return inflow curve
   */
  public static InflowCurve fromGasCondensate(final GasCondensateInflowModel model) {
    return new InflowCurve() {
      @Override
      public double bottomholePressureBara(double rate) {
        return model.flowingPressureForRate(rate);
      }
    };
  }

  /**
   * Adapts the drift-flux hydraulics as an outflow curve at a fixed wellhead pressure. The rate is converted to mass
   * flow by a constant factor.
   *
   * @param model hydraulics model (its mass rate is changed during evaluation and restored)
   * @param wellheadPressureBara wellhead pressure in bara
   * @param kgPerSecondPerRateUnit mass flow in kg/s per rate unit
   * @return outflow curve
   */
  public static OutflowCurve fromDriftFlux(final DriftFluxWellboreHydraulics model, final double wellheadPressureBara,
      final double kgPerSecondPerRateUnit) {
    return new OutflowCurve() {
      @Override
      public double requiredBottomholePressureBara(double rate) {
        double saved = model.getMassFlowRate();
        try {
          model.setMassFlowRate(rate * kgPerSecondPerRateUnit);
          return model.calculateFromWellhead(wellheadPressureBara).getBottomholePressureBara();
        } finally {
          model.setMassFlowRate(saved);
        }
      }
    };
  }

  /** One intersection of the inflow and outflow curves. */
  public static final class Intersection implements Serializable {
    private static final long serialVersionUID = 1000L;
    private double rate;
    private double bottomholePressureBara;
    private boolean stable;

    /**
     * Gets the rate.
     *
     * @return rate in the analysis unit
     */
    public double getRate() {
      return rate;
    }

    /**
     * Gets the bottom-hole pressure.
     *
     * @return pressure in bara
     */
    public double getBottomholePressureBara() {
      return bottomholePressureBara;
    }

    /**
     * Checks stability.
     *
     * @return true if the intersection is stable
     */
    public boolean isStable() {
      return stable;
    }
  }

  /** Result of a nodal analysis. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final List<Intersection> intersections = new ArrayList<Intersection>();
    private Intersection operating;

    /**
     * Gets all intersections in increasing rate.
     *
     * @return list of intersections
     */
    public List<Intersection> getIntersections() {
      return new ArrayList<Intersection>(intersections);
    }

    /**
     * Gets the operating point, the highest-rate stable intersection.
     *
     * @return operating point, or null if the well cannot flow stably in the range
     */
    public Intersection getOperatingPoint() {
      return operating;
    }
  }
}
