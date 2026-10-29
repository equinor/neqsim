package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.Arrays;
import neqsim.process.mechanicaldesign.valve.TabulatedValveCharacteristic;

/**
 * Immutable prescribed actual valve travel and liquid capacity for a single-line acoustic boundary.
 *
 * <p>
 * Travel is linearly interpolated in physical seconds after a command plus measured delay. The first and last positions
 * are held outside the table. Capacity reuses the strict, monotone cubic tabulated valve characteristic; no
 * measurements are cleaned or fitted. This value object has no clock, actuator integration or event scheduler. The
 * liquid law excludes choking, flashing, cavitation and reverse receiver flow.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class AcousticValveBoundary implements Serializable {
  /** Serialization version. */
  private static final long serialVersionUID = 1L;

  /**
   * Capacity unit, matching the existing throttling-valve conversion convention.
   *
   * @author NeqSim
   * @version 1.0
   */
  public enum CapacityUnit {
    /** Metric capacity: m3/hour of reference water at one bar pressure drop. */
    KV,
    /** US capacity: US gallons/minute of reference water at one psi pressure drop. */
    CV_US
  }

  /** Strict normalized capacity curve. */
  private final TabulatedValveCharacteristic characteristic;
  /** Capacity at the last opening point in metric Kv units. */
  private final double fullKv;
  /** Seconds after travel starts, beginning at zero. */
  private final double[] travelTime;
  /** Actual opening fractions at the travel knots. */
  private final double[] position;
  /** Absolute pipe time at which travel starts, in seconds. */
  private final double travelStart;
  /** Absolute command time on the pipe clock, seconds. */
  private final double commandTime;

  /**
   * Define absolute liquid capacity versus position and a prescribed actual travel record.
   *
   * @param openingFraction strictly increasing capacity positions spanning zero to one
   * @param capacity nonnegative, nondecreasing capacity at those positions, final value positive
   * @param unit KV or CV_US; US Cv is divided by 1.156, as in ThrottlingValve
   * @param timeSeconds strictly increasing travel times, first exactly zero, at least two points
   * @param actualOpening actual opening fractions, each within zero to one; reopening is permitted
   * @param commandTime nonnegative command time on the existing pipe clock, seconds
   * @param delay nonnegative measured command-to-travel delay, seconds
   * @throws IllegalArgumentException if any table, unit, time or capacity is invalid
   */
  public AcousticValveBoundary(double[] openingFraction, double[] capacity, CapacityUnit unit, double[] timeSeconds,
      double[] actualOpening, double commandTime, double delay) {
    if (unit == null || openingFraction == null || capacity == null || openingFraction.length < 2
        || openingFraction.length != capacity.length || openingFraction[0] != 0.0
        || openingFraction[openingFraction.length - 1] != 1.0) {
      throw new IllegalArgumentException("Capacity positions must span [0,1] with matching values and explicit units");
    }
    double[] percent = openingFraction.clone();
    for (int i = 0; i < percent.length; i++) {
      percent[i] *= 100.0;
    }
    characteristic = new TabulatedValveCharacteristic(percent, capacity);
    fullKv = capacity[capacity.length - 1] / (unit == CapacityUnit.CV_US ? 1.156 : 1.0);
    if (timeSeconds == null || actualOpening == null || timeSeconds.length < 2
        || timeSeconds.length != actualOpening.length || timeSeconds[0] != 0.0) {
      throw new IllegalArgumentException("Travel requires matching time/actual-opening arrays starting at zero");
    }
    for (int i = 0; i < timeSeconds.length; i++) {
      if (!Double.isFinite(timeSeconds[i]) || !Double.isFinite(actualOpening[i]) || actualOpening[i] < 0.0
          || actualOpening[i] > 1.0 || (i > 0 && timeSeconds[i] <= timeSeconds[i - 1])) {
        throw new IllegalArgumentException("Travel times must increase; actual opening must be finite within [0,1]");
      }
    }
    if (!Double.isFinite(commandTime) || !Double.isFinite(delay) || commandTime < 0.0 || delay < 0.0
        || !Double.isFinite(commandTime + delay + timeSeconds[timeSeconds.length - 1])) {
      throw new IllegalArgumentException("Command, delay and final travel time must be finite and nonnegative");
    }
    travelStart = commandTime + delay;
    this.commandTime = commandTime;
    for (int i = 1; i < timeSeconds.length; i++) {
      if (travelStart + timeSeconds[i] <= travelStart + timeSeconds[i - 1]) {
        throw new IllegalArgumentException("Travel knots must remain distinguishable on the physical time axis");
      }
    }
    travelTime = timeSeconds.clone();
    position = actualOpening.clone();
  }

  /**
   * Evaluate prescribed actual position, independent of call order.
   *
   * @param time physical pipe time in seconds, finite and nonnegative
   * @return actual opening fraction, zero to one
   * @throws IllegalArgumentException if time is invalid
   */
  public double getOpening(double time) {
    validateTime(time);
    double elapsed = time - travelStart;
    if (elapsed <= 0.0) {
      return position[0];
    }
    int index = Arrays.binarySearch(travelTime, elapsed);
    if (index >= 0) {
      return position[index];
    }
    int upper = -index - 1;
    if (upper == travelTime.length) {
      return position[position.length - 1];
    }
    double fraction = (elapsed - travelTime[upper - 1]) / (travelTime[upper] - travelTime[upper - 1]);
    return position[upper - 1] + fraction * (position[upper] - position[upper - 1]);
  }

  /**
   * Evaluate absolute metric liquid capacity; it already includes position.
   *
   * @param time physical pipe time in seconds
   * @return effective Kv, m3/hour reference-water capacity at one bar drop
   */
  public double getKv(double time) {
    return characteristic.getActualKv(fullKv, 100.0 * getOpening(time));
  }

  /**
   * Find the next change of travel slope for event-localized acoustic substeps.
   *
   * @param time current physical pipe time in seconds
   * @return next knot time strictly after time, or positive infinity after all knots
   * @throws IllegalArgumentException if time is invalid
   */
  public double nextKnotAfter(double time) {
    validateTime(time);
    if (commandTime > time) {
      return commandTime;
    }
    for (double knot : travelTime) {
      double absolute = travelStart + knot;
      if (absolute > time) {
        return absolute;
      }
    }
    return Double.POSITIVE_INFINITY;
  }

  /**
   * Validate a physical time without changing any state.
   *
   * @param time time in seconds
   * @throws IllegalArgumentException if time is nonfinite or negative
   */
  private static void validateTime(double time) {
    if (!Double.isFinite(time) || time < 0.0) {
      throw new IllegalArgumentException("Physical time must be finite and nonnegative");
    }
  }
}
