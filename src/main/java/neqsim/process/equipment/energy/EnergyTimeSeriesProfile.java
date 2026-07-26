package neqsim.process.equipment.energy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable scalar time series with stepwise or linear interpolation.
 *
 * <p>
 * Times are expressed in seconds from the start of a study. Values may represent power, price, emission intensity,
 * setpoints, or any other scalar input consumed by an {@link EnergyTimeSeriesSimulator.ProfileTarget}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class EnergyTimeSeriesProfile implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Interpolation used between profile points. */
  public enum Interpolation {
    /** Hold the previous point until the next timestamp. */
    STEP,
    /** Linearly interpolate between adjacent points. */
    LINEAR
  }

  /** Immutable point in one profile. */
  public static final class Point implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double timeSeconds;
    private final double value;

    /**
     * Creates a profile point.
     *
     * @param timeSeconds non-negative time from study start
     * @param value finite scalar value
     */
    public Point(double timeSeconds, double value) {
      if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
        throw new IllegalArgumentException("Profile time must be non-negative and finite");
      }
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Profile value must be finite");
      }
      this.timeSeconds = timeSeconds;
      this.value = value;
    }

    /** @return time from study start in seconds */
    public double getTimeSeconds() {
      return timeSeconds;
    }

    /** @return scalar profile value */
    public double getValue() {
      return value;
    }
  }

  private final String name;
  private final Interpolation interpolation;
  private final List<Point> points;

  /**
   * Creates an immutable profile.
   *
   * @param name profile name
   * @param interpolation interpolation rule
   * @param points strictly increasing profile points
   */
  public EnergyTimeSeriesProfile(String name, Interpolation interpolation, List<Point> points) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Profile name is required");
    }
    if (interpolation == null) {
      throw new IllegalArgumentException("Profile interpolation is required");
    }
    if (points == null || points.isEmpty()) {
      throw new IllegalArgumentException("At least one profile point is required");
    }
    List<Point> copy = new ArrayList<Point>(points.size());
    double previousTime = -1.0;
    for (Point point : points) {
      if (point == null) {
        throw new IllegalArgumentException("Profile points cannot contain null");
      }
      if (point.getTimeSeconds() <= previousTime) {
        throw new IllegalArgumentException("Profile times must be strictly increasing");
      }
      copy.add(point);
      previousTime = point.getTimeSeconds();
    }
    this.name = name;
    this.interpolation = interpolation;
    this.points = Collections.unmodifiableList(copy);
  }

  /** Convenience factory for a stepwise profile. */
  public static EnergyTimeSeriesProfile step(String name, double[] timesSeconds, double[] values) {
    return fromArrays(name, Interpolation.STEP, timesSeconds, values);
  }

  /** Convenience factory for a linearly interpolated profile. */
  public static EnergyTimeSeriesProfile linear(String name, double[] timesSeconds, double[] values) {
    return fromArrays(name, Interpolation.LINEAR, timesSeconds, values);
  }

  private static EnergyTimeSeriesProfile fromArrays(String name, Interpolation interpolation, double[] timesSeconds,
      double[] values) {
    if (timesSeconds == null || values == null || timesSeconds.length != values.length || timesSeconds.length == 0) {
      throw new IllegalArgumentException("Profile time and value arrays must have equal positive length");
    }
    List<Point> points = new ArrayList<Point>(timesSeconds.length);
    for (int index = 0; index < timesSeconds.length; index++) {
      points.add(new Point(timesSeconds[index], values[index]));
    }
    return new EnergyTimeSeriesProfile(name, interpolation, points);
  }

  /** @return profile name */
  public String getName() {
    return name;
  }

  /** @return interpolation rule */
  public Interpolation getInterpolation() {
    return interpolation;
  }

  /** @return immutable profile points */
  public List<Point> getPoints() {
    return points;
  }

  /** @return final profile timestamp in seconds */
  public double getEndTimeSeconds() {
    return points.get(points.size() - 1).getTimeSeconds();
  }

  /**
   * Evaluates the profile. Values are clamped to the first and last point outside the profile range.
   *
   * <p>
   * Step profiles are right-continuous: the value attached to a timestamp becomes active exactly at that timestamp.
   * </p>
   *
   * @param timeSeconds study time
   * @return interpolated value
   */
  public double getValue(double timeSeconds) {
    if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
      throw new IllegalArgumentException("Profile evaluation time must be non-negative and finite");
    }
    if (timeSeconds <= points.get(0).getTimeSeconds()) {
      return points.get(0).getValue();
    }
    int lastIndex = points.size() - 1;
    if (timeSeconds >= points.get(lastIndex).getTimeSeconds()) {
      return points.get(lastIndex).getValue();
    }
    for (int index = 1; index < points.size(); index++) {
      Point upper = points.get(index);
      Point lower = points.get(index - 1);
      if (interpolation == Interpolation.STEP) {
        if (timeSeconds < upper.getTimeSeconds()) {
          return lower.getValue();
        }
        if (timeSeconds == upper.getTimeSeconds()) {
          return upper.getValue();
        }
      } else if (timeSeconds <= upper.getTimeSeconds()) {
        double fraction = (timeSeconds - lower.getTimeSeconds()) / (upper.getTimeSeconds() - lower.getTimeSeconds());
        return lower.getValue() + fraction * (upper.getValue() - lower.getValue());
      }
    }
    return points.get(lastIndex).getValue();
  }
}
