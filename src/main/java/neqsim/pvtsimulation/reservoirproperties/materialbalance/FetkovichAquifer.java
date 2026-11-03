package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import java.io.Serializable;

/**
 * Finite, constant-property aquifer with reversible reservoir-volume water exchange.
 *
 * <p>
 * Uses the rate and material-balance equations of Fetkovich (1971), SPE 2603-PA: {@code dWe/dt = J * (pa - pr)} and
 * {@code pa = pi - We / (ct * Wi)}. Positive influx is water leaving the aquifer and entering the reservoir. Volumes
 * are reservoir m3 on one fixed water-volume basis, pressures are absolute bar, time is seconds and total aquifer
 * compressibility is 1/bar. No surface-volume or formation-volume-factor conversion is performed.
 * </p>
 *
 * <p>
 * Each interval integrates the linear rate law exactly for a linearly varying, prescribed reservoir pressure. This
 * removes timestep error for that prescribed path, not error from an unknown nonlinear reservoir-pressure trajectory. A
 * coupled reservoir calculation must solve its own pressure/volume closure and check interval refinement. Water
 * exchange may reverse during repressurization; cumulative influx is not clipped to zero. The caller must account for
 * both signs consistently and verify the slightly-compressible, constant-property approximation.
 * </p>
 *
 * <p>
 * Zero productivity gives no exchange. Zero storage gives the zero-influx limit, with instantaneous pressure
 * equilibration for positive productivity. Explicitly infinite initial water volume and positive compressibility
 * represent constant-pressure support. These limits do not model radial transient diffusion; use
 * {@link VanEverdingenHurstAquifer} for that distinct approximation.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FetkovichAquifer implements Serializable {
  private static final long serialVersionUID = 1L;
  private final double initialWaterVolumeM3;
  private final double totalCompressibilityPerBar;
  private final double productivityM3PerSecondPerBar;
  private final double initialPressureBara;
  private final double capacityM3PerBar;

  /**
   * Physical finite-water bound reached by an interval, including any interior pressure minimum.
   *
   * <p>
   * A coupled pressure solver may use this precise bound to delimit its admissible pressure domain. It must not accept
   * the exhausted interval or treat unrelated numerical failures as an inventory bound.
   * </p>
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class InventoryExhaustedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /** Creates the physical inventory-bound diagnostic for an internally rejected interval. */
    private InventoryExhaustedException() {
      super("Aquifer interval exhausts the initial water inventory");
    }
  }

  /**
   * Immutable aquifer pressure and exchanged-water inventory.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class State implements Serializable {
    private static final long serialVersionUID = 1L;
    private final FetkovichAquifer aquifer;
    private final double timeSeconds;
    private final double cumulativeInfluxM3;
    private final double pressureBara;
    private final double intervalInfluxM3;
    private final double intervalMinimumCumulativeInfluxM3;
    private final double intervalMaximumCumulativeInfluxM3;

    /**
     * Freezes a validated state without exposing mutable aquifer ownership.
     *
     * @param aquifer immutable originating model
     * @param timeSeconds elapsed time in seconds
     * @param cumulativeInfluxM3 signed cumulative reservoir-volume influx in m3
     * @param pressureBara average aquifer pressure in bara
     * @param intervalInfluxM3 signed influx during the last interval in m3
     * @param intervalMinimumCumulativeInfluxM3 minimum signed cumulative influx throughout the last interval, m3
     * @param intervalMaximumCumulativeInfluxM3 maximum signed cumulative influx throughout the last interval, m3
     */
    private State(FetkovichAquifer aquifer, double timeSeconds, double cumulativeInfluxM3, double pressureBara,
        double intervalInfluxM3, double intervalMinimumCumulativeInfluxM3, double intervalMaximumCumulativeInfluxM3) {
      this.aquifer = aquifer;
      this.timeSeconds = timeSeconds;
      this.cumulativeInfluxM3 = cumulativeInfluxM3;
      this.pressureBara = pressureBara;
      this.intervalInfluxM3 = intervalInfluxM3;
      this.intervalMinimumCumulativeInfluxM3 = intervalMinimumCumulativeInfluxM3;
      this.intervalMaximumCumulativeInfluxM3 = intervalMaximumCumulativeInfluxM3;
    }

    /**
     * Returns elapsed time since the initial aquifer state.
     *
     * @return elapsed time in seconds
     */
    public double getTimeSeconds() {
      return timeSeconds;
    }

    /**
     * Returns signed cumulative water influx into the reservoir.
     *
     * @return cumulative influx in reservoir m3; negative values denote net aquifer recharge
     */
    public double getCumulativeInfluxM3() {
      return cumulativeInfluxM3;
    }

    /**
     * Returns average aquifer pressure.
     *
     * @return aquifer pressure in bara
     */
    public double getPressureBara() {
      return pressureBara;
    }

    /**
     * Returns signed water influx during the most recent interval.
     *
     * @return interval influx in reservoir m3
     */
    public double getIntervalInfluxM3() {
      return intervalInfluxM3;
    }

    /**
     * Returns the lowest cumulative influx attained anywhere in the last prescribed pressure interval.
     *
     * @return signed minimum cumulative influx in reservoir m3, including both interval endpoints
     */
    public double getIntervalMinimumCumulativeInfluxM3() {
      return intervalMinimumCumulativeInfluxM3;
    }

    /**
     * Returns the highest cumulative influx attained anywhere in the last prescribed pressure interval.
     *
     * @return signed maximum cumulative influx in reservoir m3, including both interval endpoints
     */
    public double getIntervalMaximumCumulativeInfluxM3() {
      return intervalMaximumCumulativeInfluxM3;
    }
  }

  /**
   * Defines a fixed-volume, fixed-productivity aquifer on a reservoir-volume water basis.
   *
   * @param initialWaterVolumeM3 nonnegative aquifer water volume in reservoir m3; positive infinity selects infinite
   * storage when compressibility is positive
   * @param totalCompressibilityPerBar finite nonnegative total water/rock compressibility in 1/bar
   * @param productivityM3PerSecondPerBar finite nonnegative aquifer productivity in reservoir m3/(s bar)
   * @param initialPressureBara finite positive initial aquifer pressure in bara
   */
  public FetkovichAquifer(double initialWaterVolumeM3, double totalCompressibilityPerBar,
      double productivityM3PerSecondPerBar, double initialPressureBara) {
    if (Double.isNaN(initialWaterVolumeM3) || initialWaterVolumeM3 < 0.0) {
      throw new IllegalArgumentException("Initial aquifer water volume must be nonnegative or positive infinity");
    }
    requireNonnegative(totalCompressibilityPerBar, "Aquifer compressibility");
    requireNonnegative(productivityM3PerSecondPerBar, "Aquifer productivity");
    requirePositive(initialPressureBara, "Initial aquifer pressure");
    double capacity = totalCompressibilityPerBar == 0.0 ? 0.0 : initialWaterVolumeM3 * totalCompressibilityPerBar;
    if (Double.isFinite(initialWaterVolumeM3) && initialWaterVolumeM3 > 0.0 && totalCompressibilityPerBar > 0.0
        && (!Double.isFinite(capacity) || capacity == 0.0)) {
      throw new IllegalArgumentException(
          "Finite aquifer storage must be representable; use explicit infinity for infinite storage");
    }
    this.initialWaterVolumeM3 = initialWaterVolumeM3;
    this.totalCompressibilityPerBar = totalCompressibilityPerBar;
    this.productivityM3PerSecondPerBar = productivityM3PerSecondPerBar;
    this.initialPressureBara = initialPressureBara;
    this.capacityM3PerBar = capacity;
  }

  /**
   * Returns the unproduced initial state at time zero.
   *
   * @return independent immutable initial state
   */
  public State initialState() {
    return new State(this, 0.0, 0.0, initialPressureBara, 0.0, 0.0, 0.0);
  }

  /**
   * Advances an immutable state for a prescribed linear reservoir-pressure interval.
   *
   * <p>
   * Evaluates the exact constant-capacitance ODE using exponential functions with stable small-step series. Rejected
   * intervals leave the input state unchanged. A state restored by serialization or produced by an identically
   * parameterized model may be resumed; incompatible model states are rejected.
   * </p>
   *
   * @param state previous aquifer state from this parameter set
   * @param durationSeconds finite positive interval duration in seconds
   * @param reservoirPressureStartBara finite positive reservoir pressure at interval start, bara
   * @param reservoirPressureEndBara finite positive reservoir pressure at interval end, bara
   * @return accepted new immutable aquifer state
   * @throws IllegalStateException if the interval exhausts finite water inventory or produces non-finite evidence
   */
  public State advance(State state, double durationSeconds, double reservoirPressureStartBara,
      double reservoirPressureEndBara) {
    if (state == null || !sameParameters(state.aquifer)) {
      throw new IllegalArgumentException("Aquifer state must belong to the same physical parameter set");
    }
    requirePositive(durationSeconds, "Interval duration");
    requirePositive(reservoirPressureStartBara, "Start reservoir pressure");
    requirePositive(reservoirPressureEndBara, "End reservoir pressure");
    double time = state.timeSeconds + durationSeconds;
    double influx = 0.0;
    double pressure = state.pressureBara;
    double minimumInflux = state.cumulativeInfluxM3;
    double maximumInflux = state.cumulativeInfluxM3;
    if (productivityM3PerSecondPerBar > 0.0) {
      if (capacityM3PerBar == 0.0) {
        pressure = reservoirPressureEndBara;
      } else if (Double.isInfinite(capacityM3PerBar)) {
        double meanReservoirPressure = 0.5 * reservoirPressureStartBara + 0.5 * reservoirPressureEndBara;
        double pressureDifference = pressure - meanReservoirPressure;
        if (pressureDifference != 0.0) {
          influx = productivityM3PerSecondPerBar * durationSeconds * pressureDifference;
        }
        double pressureChange = reservoirPressureEndBara - reservoirPressureStartBara;
        double initialDifference = pressure - reservoirPressureStartBara;
        if (sameNonzeroSign(pressureChange, initialDifference)) {
          double fraction = initialDifference / pressureChange;
          if (fraction > 0.0 && fraction < 1.0) {
            double extremum = state.cumulativeInfluxM3
                + productivityM3PerSecondPerBar * durationSeconds * (0.5 * initialDifference * fraction);
            minimumInflux = Math.min(minimumInflux, extremum);
            maximumInflux = Math.max(maximumInflux, extremum);
          }
        }
      } else {
        double exposure = exposure(durationSeconds);
        double pressureChange = reservoirPressureEndBara - reservoirPressureStartBara;
        double loss = -Math.expm1(-exposure);
        double rampWeight;
        if (exposure < 1.0e-4) {
          // (1 - (1 - exp(-x))/x)/x avoids cancellation at small x.
          double rampPerExposure = 0.5
              + exposure * (-1.0 / 6.0 + exposure * (1.0 / 24.0 + exposure * (-1.0 / 120.0 + exposure / 720.0)));
          double decayPerExposure = 1.0
              - exposure * (0.5 - exposure * (1.0 / 6.0 - exposure * (1.0 / 24.0 - exposure / 120.0)));
          influx = productivityM3PerSecondPerBar * durationSeconds
              * ((pressure - reservoirPressureStartBara) * decayPerExposure - pressureChange * rampPerExposure);
          rampWeight = exposure * rampPerExposure;
        } else {
          rampWeight = 1.0 - loss / exposure;
          influx = capacityM3PerBar * ((pressure - reservoirPressureStartBara) * loss - pressureChange * rampWeight);
        }
        pressure = Math.exp(-exposure) * pressure + (loss - rampWeight) * reservoirPressureStartBara
            + rampWeight * reservoirPressureEndBara;
        double initialDifference = state.pressureBara - reservoirPressureStartBara;
        if (sameNonzeroSign(pressureChange, initialDifference)) {
          double fraction = crossingFraction(exposure, initialDifference, pressureChange);
          if (fraction >= 0.0 && fraction <= 1.0) {
            double extremum = state.cumulativeInfluxM3
                + finiteInfluxAtFraction(durationSeconds, exposure, initialDifference, pressureChange, fraction);
            minimumInflux = Math.min(minimumInflux, extremum);
            maximumInflux = Math.max(maximumInflux, extremum);
          }
        }
      }
    }
    double cumulative = state.cumulativeInfluxM3 + influx;
    minimumInflux = Math.min(minimumInflux, cumulative);
    maximumInflux = Math.max(maximumInflux, cumulative);
    if (!Double.isFinite(time) || !Double.isFinite(influx) || !Double.isFinite(cumulative)
        || !Double.isFinite(minimumInflux) || !Double.isFinite(maximumInflux) || !Double.isFinite(pressure)
        || pressure <= 0.0) {
      throw new IllegalStateException("Aquifer interval produced non-finite evidence or nonpositive pressure");
    }
    if (Double.isFinite(initialWaterVolumeM3) && maximumInflux >= initialWaterVolumeM3 && initialWaterVolumeM3 > 0.0) {
      throw new InventoryExhaustedException();
    }
    return new State(this, time, cumulative, pressure, influx, minimumInflux, maximumInflux);
  }

  /**
   * Tests whether a ramp and initial driving pressure can produce a within-interval flow reversal.
   *
   * @param first pressure ramp
   * @param second initial aquifer-minus-reservoir pressure
   * @return true for strictly equal nonzero signs
   */
  private static boolean sameNonzeroSign(double first, double second) {
    return first > 0.0 && second > 0.0 || first < 0.0 && second < 0.0;
  }

  /**
   * Evaluates the possible zero-flow time as a fraction of a prescribed linear-pressure interval.
   *
   * @param exposure nonnegative dimensionless interval duration
   * @param initialDifference initial aquifer-minus-reservoir pressure
   * @param pressureChange reservoir end-minus-start pressure with the same nonzero sign
   * @return fractional zero-flow time; values outside zero to one denote no interior extremum
   */
  private static double crossingFraction(double exposure, double initialDifference, double pressureChange) {
    if (exposure == 0.0) {
      return initialDifference / pressureChange;
    }
    if (Double.isInfinite(exposure)) {
      return 0.0;
    }
    double logRatio = Math.log(exposure) + Math.log(Math.abs(initialDifference)) - Math.log(Math.abs(pressureChange));
    if (logRatio < -40.0) {
      return initialDifference / pressureChange;
    }
    double logOnePlusRatio = logRatio > 40.0 ? logRatio : Math.log1p(Math.exp(logRatio));
    return logOnePlusRatio / exposure;
  }

  /**
   * Evaluates finite-storage influx at a fractional interval time without subtracting almost equal pressures.
   *
   * @param durationSeconds full interval duration, seconds
   * @param exposure full dimensionless interval duration
   * @param initialDifference initial aquifer-minus-reservoir pressure
   * @param pressureChange full reservoir-pressure ramp
   * @param fraction fractional interval time between zero and one
   * @return signed influx since the interval start, reservoir m3
   */
  private double finiteInfluxAtFraction(double durationSeconds, double exposure, double initialDifference,
      double pressureChange, double fraction) {
    if (Double.isInfinite(exposure)) {
      return capacityM3PerBar * (initialDifference - pressureChange * fraction);
    }
    double fractionalExposure = exposure * fraction;
    if (fractionalExposure < 1.0e-4) {
      double ramp = 0.5 + fractionalExposure * (-1.0 / 6.0
          + fractionalExposure * (1.0 / 24.0 + fractionalExposure * (-1.0 / 120.0 + fractionalExposure / 720.0)));
      double decay = 1.0 - fractionalExposure
          * (0.5 - fractionalExposure * (1.0 / 6.0 - fractionalExposure * (1.0 / 24.0 - fractionalExposure / 120.0)));
      return productivityM3PerSecondPerBar * durationSeconds * fraction
          * (initialDifference * decay - pressureChange * fraction * ramp);
    }
    double loss = -Math.expm1(-fractionalExposure);
    double rampWeight = 1.0 - loss / fractionalExposure;
    return capacityM3PerBar * (initialDifference * loss - pressureChange * fraction * rampWeight);
  }

  /**
   * Returns initial water volume on the fixed reservoir-volume basis.
   *
   * @return water volume in reservoir m3, possibly positive infinity
   */
  public double getInitialWaterVolumeM3() {
    return initialWaterVolumeM3;
  }

  /**
   * Returns fixed total water/rock compressibility.
   *
   * @return total compressibility in 1/bar
   */
  public double getTotalCompressibilityPerBar() {
    return totalCompressibilityPerBar;
  }

  /**
   * Returns fixed water productivity.
   *
   * @return productivity in reservoir m3/(s bar)
   */
  public double getProductivityM3PerSecondPerBar() {
    return productivityM3PerSecondPerBar;
  }

  /**
   * Returns initial average aquifer pressure.
   *
   * @return initial aquifer pressure in bara
   */
  public double getInitialPressureBara() {
    return initialPressureBara;
  }

  /**
   * Returns the volume-pressure capacitance, equal to initial water volume times compressibility.
   *
   * @return capacitance in reservoir m3/bar, possibly zero or positive infinity
   */
  public double getCapacityM3PerBar() {
    return capacityM3PerBar;
  }

  /**
   * Computes dimensionless interval exposure while avoiding overflow in an intermediate ratio.
   *
   * @param durationSeconds positive interval duration in seconds
   * @return nonnegative dimensionless productivity-time/storage ratio
   */
  private double exposure(double durationSeconds) {
    double ratio = productivityM3PerSecondPerBar / capacityM3PerBar;
    if (Double.isFinite(ratio)) {
      return ratio * durationSeconds;
    }
    return Math.exp(Math.log(productivityM3PerSecondPerBar) + Math.log(durationSeconds) - Math.log(capacityM3PerBar));
  }

  /**
   * Checks that an immutable state uses the same physical model.
   *
   * @param other originating model
   * @return true for exactly equal physical parameters
   */
  private boolean sameParameters(FetkovichAquifer other) {
    return other != null && initialWaterVolumeM3 == other.initialWaterVolumeM3
        && totalCompressibilityPerBar == other.totalCompressibilityPerBar
        && productivityM3PerSecondPerBar == other.productivityM3PerSecondPerBar
        && initialPressureBara == other.initialPressureBara;
  }

  /**
   * Rejects non-finite or negative model coefficients.
   *
   * @param value coefficient to validate
   * @param label field name for diagnostics
   */
  private static void requireNonnegative(double value, String label) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(label + " must be finite and nonnegative");
    }
  }

  /**
   * Rejects non-finite or nonpositive physical scales.
   *
   * @param value scale to validate
   * @param label field name for diagnostics
   */
  private static void requirePositive(double value, String label) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(label + " must be finite and positive");
    }
  }
}
