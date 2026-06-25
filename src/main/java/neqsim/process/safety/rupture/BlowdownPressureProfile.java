package neqsim.process.safety.rupture;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;

/**
 * Blowdown pressure profile used by pipe fire-rupture calculations.
 *
 * <p>
 * The profile stores absolute pressure versus time and can evaluate the pressure using either previous-point stepping
 * or linear interpolation. Previous-point stepping matches the legacy spreadsheet behaviour where Excel
 * {@code VLOOKUP(..., TRUE)} is used on the pressure table.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BlowdownPressureProfile implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Pressure interpolation method. */
  public enum InterpolationMode {
    /** Use the latest tabulated pressure at or before the requested time. */
    STEP_PREVIOUS,
    /** Linearly interpolate between the surrounding time points. */
    LINEAR
  }

  private final List<Double> timeSeconds;
  private final List<Double> pressureBara;
  private final InterpolationMode interpolationMode;
  private final double gaugeOffsetBara;

  /**
   * Creates a blowdown pressure profile.
   *
   * @param timeSeconds time points in seconds; must be increasing
   * @param pressureBara absolute pressures in bara; must be same length as time points
   * @param interpolationMode pressure interpolation mode; defaults to previous-point stepping when null
   * @param gaugeOffsetBara absolute-to-gauge pressure offset in bara; normally 1.0 for spreadsheet parity
   * @throws IllegalArgumentException if the profile is invalid
   */
  public BlowdownPressureProfile(List<Double> timeSeconds, List<Double> pressureBara,
      InterpolationMode interpolationMode, double gaugeOffsetBara) {
    validateProfile(timeSeconds, pressureBara, gaugeOffsetBara);
    this.timeSeconds = Collections.unmodifiableList(new ArrayList<Double>(timeSeconds));
    this.pressureBara = Collections.unmodifiableList(new ArrayList<Double>(pressureBara));
    this.interpolationMode = interpolationMode == null ? InterpolationMode.STEP_PREVIOUS : interpolationMode;
    this.gaugeOffsetBara = gaugeOffsetBara;
  }

  /**
   * Creates a previous-point pressure profile from minute and bara arrays.
   *
   * @param timeMinutes profile times in minutes
   * @param pressureBara absolute pressure profile in bara
   * @return pressure profile using previous-point stepping and 1 bara gauge offset
   */
  public static BlowdownPressureProfile fromMinutesAndBara(double[] timeMinutes, double[] pressureBara) {
    Builder builder = builder();
    for (int i = 0; i < timeMinutes.length; i++) {
      builder.addPoint(timeMinutes[i], "min", pressureBara[i], "bara");
    }
    return builder.build();
  }

  /**
   * Creates a pressure profile from a NeqSim depressurization result.
   *
   * <p>
   * This is the standard bridge between the transient VU-flash blowdown calculation and the pipe fire-rupture
   * strain-rate model. The depressurization result stores time in seconds and pressure in bara, so no unit conversion
   * is needed except for applying the profile interpolation mode.
   * </p>
   *
   * @param result depressurization result containing time and pressure trajectories
   * @return pressure profile using previous-point stepping and 1 bara gauge offset
   * @throws IllegalArgumentException if the result or its time/pressure trajectory is invalid
   */
  public static BlowdownPressureProfile fromDepressurizationResult(DepressurizationResult result) {
    return fromDepressurizationResult(result, InterpolationMode.STEP_PREVIOUS);
  }

  /**
   * Creates a pressure profile from a NeqSim depressurization result.
   *
   * @param result depressurization result containing time and pressure trajectories
   * @param interpolationMode pressure interpolation mode to use when sampling between result points
   * @return pressure profile using the supplied interpolation mode and 1 bara gauge offset
   * @throws IllegalArgumentException if the result or its time/pressure trajectory is invalid
   */
  public static BlowdownPressureProfile fromDepressurizationResult(DepressurizationResult result,
      InterpolationMode interpolationMode) {
    if (result == null) {
      throw new IllegalArgumentException("result must not be null");
    }
    Builder builder = builder()
        .interpolationMode(interpolationMode == null ? InterpolationMode.STEP_PREVIOUS : interpolationMode);
    int pointCount = Math.min(result.time.size(), result.pressureBara.size());
    for (int i = 0; i < pointCount; i++) {
      builder.addPoint(result.time.get(i).doubleValue(), "s", result.pressureBara.get(i).doubleValue(), "bara");
    }
    return builder.build();
  }

  /**
   * Creates a profile builder.
   *
   * @return new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets pressure at a specified time.
   *
   * @param timeSecondsRequested requested time in seconds; must be non-negative
   * @return pressure in bara
   * @throws IllegalArgumentException if time is negative or not finite
   */
  public double pressureBaraAt(double timeSecondsRequested) {
    validateNonNegative(timeSecondsRequested, "timeSecondsRequested");
    if (timeSecondsRequested <= timeSeconds.get(0)) {
      return pressureBara.get(0);
    }
    int lastIndex = timeSeconds.size() - 1;
    if (timeSecondsRequested >= timeSeconds.get(lastIndex)) {
      return pressureBara.get(lastIndex);
    }
    for (int i = 1; i < timeSeconds.size(); i++) {
      double upperTime = timeSeconds.get(i);
      if (nearlyEqual(timeSecondsRequested, upperTime)) {
        return pressureBara.get(i);
      }
      if (timeSecondsRequested < upperTime) {
        if (interpolationMode == InterpolationMode.LINEAR) {
          return interpolate(timeSecondsRequested, timeSeconds.get(i - 1), upperTime, pressureBara.get(i - 1),
              pressureBara.get(i));
        }
        return pressureBara.get(i - 1);
      }
    }
    return pressureBara.get(lastIndex);
  }

  /**
   * Gets gauge pressure at a specified time.
   *
   * @param timeSecondsRequested requested time in seconds; must be non-negative
   * @return pressure in barg
   */
  public double pressureBargAt(double timeSecondsRequested) {
    return pressureBaraAt(timeSecondsRequested) - gaugeOffsetBara;
  }

  /**
   * Gets the time points.
   *
   * @return immutable time list in seconds
   */
  public List<Double> getTimeSeconds() {
    return timeSeconds;
  }

  /**
   * Gets the absolute pressures.
   *
   * @return immutable pressure list in bara
   */
  public List<Double> getPressureBara() {
    return pressureBara;
  }

  /**
   * Gets the interpolation mode.
   *
   * @return interpolation mode
   */
  public InterpolationMode getInterpolationMode() {
    return interpolationMode;
  }

  /**
   * Gets the absolute-to-gauge offset.
   *
   * @return gauge offset in bara
   */
  public double getGaugeOffsetBara() {
    return gaugeOffsetBara;
  }

  /**
   * Converts the pressure profile to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("timeSeconds", timeSeconds);
    map.put("pressureBara", pressureBara);
    map.put("interpolationMode", interpolationMode.name());
    map.put("gaugeOffsetBara", gaugeOffsetBara);
    return map;
  }

  /**
   * Linearly interpolates a value.
   *
   * @param x requested x value
   * @param x0 lower x value
   * @param x1 upper x value
   * @param y0 lower y value
   * @param y1 upper y value
   * @return interpolated y value
   */
  private static double interpolate(double x, double x0, double x1, double y0, double y1) {
    return y0 + (y1 - y0) * (x - x0) / (x1 - x0);
  }

  /**
   * Checks whether two time values are nearly equal.
   *
   * @param first first value
   * @param second second value
   * @return true when values are within a small absolute tolerance
   */
  private static boolean nearlyEqual(double first, double second) {
    return Math.abs(first - second) < 1.0e-10;
  }

  /**
   * Validates that a value is non-negative and finite.
   *
   * @param value value to validate
   * @param name parameter name for exception messages
   * @throws IllegalArgumentException if the value is invalid
   */
  private static void validateNonNegative(double value, String name) {
    if (value < 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be non-negative and finite");
    }
  }

  /**
   * Validates pressure profile arrays.
   *
   * @param timeSeconds time points in seconds
   * @param pressureBara pressure points in bara
   * @param gaugeOffsetBara absolute-to-gauge offset in bara
   * @throws IllegalArgumentException if the profile is invalid
   */
  private static void validateProfile(List<Double> timeSeconds, List<Double> pressureBara, double gaugeOffsetBara) {
    if (timeSeconds == null || pressureBara == null || timeSeconds.size() != pressureBara.size()
        || timeSeconds.size() < 2) {
      throw new IllegalArgumentException("profile must contain at least two time/pressure points");
    }
    validateNonNegative(gaugeOffsetBara, "gaugeOffsetBara");
    double previousTime = -1.0;
    for (int i = 0; i < timeSeconds.size(); i++) {
      Double time = timeSeconds.get(i);
      Double pressure = pressureBara.get(i);
      if (time == null || pressure == null) {
        throw new IllegalArgumentException("profile values must not be null");
      }
      validateNonNegative(time.doubleValue(), "timeSeconds");
      if (pressure.doubleValue() <= 0.0 || Double.isNaN(pressure.doubleValue())
          || Double.isInfinite(pressure.doubleValue())) {
        throw new IllegalArgumentException("pressureBara must be positive and finite");
      }
      if (time.doubleValue() <= previousTime) {
        throw new IllegalArgumentException("timeSeconds must be strictly increasing");
      }
      previousTime = time.doubleValue();
    }
  }

  /** Builder for {@link BlowdownPressureProfile}. */
  public static class Builder {
    private final List<Double> timeSeconds = new ArrayList<Double>();
    private final List<Double> pressureBara = new ArrayList<Double>();
    private InterpolationMode interpolationMode = InterpolationMode.STEP_PREVIOUS;
    private double gaugeOffsetBara = 1.0;

    /**
     * Adds one pressure profile point.
     *
     * @param time value of time
     * @param timeUnit time unit, {@code s} or {@code min}
     * @param pressure value of pressure
     * @param pressureUnit pressure unit, {@code bara}, {@code bar}, or {@code barg}
     * @return this builder
     */
    public Builder addPoint(double time, String timeUnit, double pressure, String pressureUnit) {
      timeSeconds.add(Double.valueOf(convertTimeToSeconds(time, timeUnit)));
      pressureBara.add(Double.valueOf(convertPressureToBara(pressure, pressureUnit)));
      return this;
    }

    /**
     * Sets pressure interpolation mode.
     *
     * @param interpolationMode interpolation mode; must not be null
     * @return this builder
     */
    public Builder interpolationMode(InterpolationMode interpolationMode) {
      if (interpolationMode == null) {
        throw new IllegalArgumentException("interpolationMode must not be null");
      }
      this.interpolationMode = interpolationMode;
      return this;
    }

    /**
     * Sets absolute-to-gauge pressure offset.
     *
     * @param gaugeOffsetBara gauge offset in bara; must be non-negative
     * @return this builder
     */
    public Builder gaugeOffsetBara(double gaugeOffsetBara) {
      validateNonNegative(gaugeOffsetBara, "gaugeOffsetBara");
      this.gaugeOffsetBara = gaugeOffsetBara;
      return this;
    }

    /**
     * Builds the pressure profile.
     *
     * @return pressure profile
     */
    public BlowdownPressureProfile build() {
      return new BlowdownPressureProfile(timeSeconds, pressureBara, interpolationMode, gaugeOffsetBara);
    }

    /**
     * Converts time to seconds.
     *
     * @param time time value
     * @param unit time unit text
     * @return time in seconds
     */
    private static double convertTimeToSeconds(double time, String unit) {
      validateNonNegative(time, "time");
      String normalizedUnit = unit == null ? "s" : unit.trim().toLowerCase();
      if ("min".equals(normalizedUnit) || "minute".equals(normalizedUnit) || "minutes".equals(normalizedUnit)) {
        return time * 60.0;
      }
      if ("s".equals(normalizedUnit) || "sec".equals(normalizedUnit) || "second".equals(normalizedUnit)
          || "seconds".equals(normalizedUnit)) {
        return time;
      }
      throw new IllegalArgumentException("Unsupported time unit: " + unit);
    }

    /**
     * Converts pressure to bara.
     *
     * @param pressure pressure value
     * @param unit pressure unit text
     * @return pressure in bara
     */
    private double convertPressureToBara(double pressure, String unit) {
      String normalizedUnit = unit == null ? "bara" : unit.trim().toLowerCase();
      if ("bara".equals(normalizedUnit) || "bar".equals(normalizedUnit)) {
        return pressure;
      }
      if ("barg".equals(normalizedUnit)) {
        return pressure + gaugeOffsetBara;
      }
      throw new IllegalArgumentException("Unsupported pressure unit: " + unit);
    }
  }
}
