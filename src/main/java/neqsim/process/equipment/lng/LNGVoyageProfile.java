package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines the environmental and operational profile for an LNG voyage.
 *
 * <p>
 * A voyage is divided into segments, each with its own ambient temperature, sea state, wind speed,
 * and solar radiation. This allows the simulation to vary heat ingress and sloshing effects along
 * the route (e.g., cold departure from Arctic terminal, warm transit through equatorial waters).
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * LNGVoyageProfile profile = new LNGVoyageProfile("Qatar to Japan");
 * profile.addSegment(new LNGVoyageProfile.Segment(0, 48, 308.15, 1.0, 5.0, 200.0));
 * profile.addSegment(new LNGVoyageProfile.Segment(48, 240, 303.15, 1.5, 8.0, 250.0));
 * profile.addSegment(new LNGVoyageProfile.Segment(240, 480, 288.15, 2.0, 12.0, 150.0));
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGVoyageProfile implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  /** Voyage description or route name. */
  private String voyageName;

  /** Total voyage duration (hours). */
  private double totalDurationHours;

  /** List of voyage segments in chronological order. */
  private List<Segment> segments;

  /** Default ambient temperature if no segments defined (K). */
  private double defaultAmbientTemperature = 273.15 + 25.0;

  /**
   * Constructor for LNGVoyageProfile.
   *
   * @param voyageName descriptive name of the voyage route
   */
  public LNGVoyageProfile(String voyageName) {
    this.voyageName = voyageName;
    this.segments = new ArrayList<Segment>();
  }

  /**
   * Add a voyage segment.
   *
   * @param segment voyage segment to add
   */
  public void addSegment(Segment segment) {
    segments.add(segment);
    recalculateTotalDuration();
  }

  /**
   * Get the voyage environment conditions at a given time.
   *
   * <p>
   * Linearly interpolates between segment boundaries. If time is before the first segment or after
   * the last, the nearest segment's conditions are used.
   * </p>
   *
   * @param timeHours elapsed time since voyage start (hours)
   * @return environment conditions at the given time
   */
  public EnvironmentConditions getConditionsAt(double timeHours) {
    if (segments.isEmpty()) {
      return new EnvironmentConditions(defaultAmbientTemperature, 1.0, 5.0, 200.0);
    }

    // Find the segment containing this time
    for (Segment seg : segments) {
      if (timeHours >= seg.startTimeHours && timeHours < seg.endTimeHours) {
        return new EnvironmentConditions(seg.ambientTemperature, seg.significantWaveHeight,
            seg.windSpeed, seg.solarRadiation);
      }
    }

    // If past last segment, use last segment's conditions
    Segment last = segments.get(segments.size() - 1);
    return new EnvironmentConditions(last.ambientTemperature, last.significantWaveHeight,
        last.windSpeed, last.solarRadiation);
  }

  /**
   * Get the ambient temperature at a given time.
   *
   * @param timeHours elapsed time (hours)
   * @return ambient temperature (K)
   */
  public double getAmbientTemperatureAt(double timeHours) {
    return getConditionsAt(timeHours).ambientTemperature;
  }

  /**
   * Get the significant wave height at a given time.
   *
   * @param timeHours elapsed time (hours)
   * @return significant wave height (m)
   */
  public double getWaveHeightAt(double timeHours) {
    return getConditionsAt(timeHours).significantWaveHeight;
  }

  /**
   * Get voyage name.
   *
   * @return voyage name
   */
  public String getVoyageName() {
    return voyageName;
  }

  /**
   * Set voyage name.
   *
   * @param voyageName voyage name
   */
  public void setVoyageName(String voyageName) {
    this.voyageName = voyageName;
  }

  /**
   * Get total voyage duration.
   *
   * @return total duration (hours)
   */
  public double getTotalDurationHours() {
    return totalDurationHours;
  }

  /**
   * Set total voyage duration manually (overrides segment-based calculation).
   *
   * @param totalDurationHours total duration (hours)
   */
  public void setTotalDurationHours(double totalDurationHours) {
    this.totalDurationHours = totalDurationHours;
  }

  /**
   * Get all voyage segments.
   *
   * @return unmodifiable list of segments
   */
  public List<Segment> getSegments() {
    return Collections.unmodifiableList(segments);
  }

  /**
   * Get the default ambient temperature.
   *
   * @return default ambient temperature (K)
   */
  public double getDefaultAmbientTemperature() {
    return defaultAmbientTemperature;
  }

  /**
   * Set the default ambient temperature used when no segments are defined.
   *
   * @param temperature default ambient temperature (K)
   */
  public void setDefaultAmbientTemperature(double temperature) {
    this.defaultAmbientTemperature = temperature;
  }

  /**
   * Create a uniform voyage with constant conditions.
   *
   * @param name voyage name
   * @param durationHours total voyage duration (hours)
   * @param ambientTemperatureK constant ambient temperature (K)
   * @return configured voyage profile
   */
  public static LNGVoyageProfile createUniform(String name, double durationHours,
      double ambientTemperatureK) {
    LNGVoyageProfile profile = new LNGVoyageProfile(name);
    profile.addSegment(new Segment(0, durationHours, ambientTemperatureK, 1.5, 8.0, 200.0));
    return profile;
  }

  /**
   * Recalculate total duration from segments.
   */
  private void recalculateTotalDuration() {
    double maxEnd = 0;
    for (Segment seg : segments) {
      if (seg.endTimeHours > maxEnd) {
        maxEnd = seg.endTimeHours;
      }
    }
    this.totalDurationHours = maxEnd;
  }

  /**
   * A single segment of the voyage with constant environmental conditions.
   */
  public static class Segment implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1003L;

    /** Segment start time relative to voyage start (hours). */
    private double startTimeHours;

    /** Segment end time relative to voyage start (hours). */
    private double endTimeHours;

    /** Ambient air/sea temperature for this segment (K). */
    private double ambientTemperature;

    /** Significant wave height Hs (m). Affects sloshing-induced mixing. */
    private double significantWaveHeight;

    /** Wind speed (m/s). Affects convective heat transfer on tank outer surfaces. */
    private double windSpeed;

    /** Solar radiation (W/m2). Affects roof heat ingress. */
    private double solarRadiation;

    /**
     * Constructor for voyage Segment.
     *
     * @param startTimeHours start time (hours from voyage start)
     * @param endTimeHours end time (hours from voyage start)
     * @param ambientTemperature ambient temperature (K)
     * @param significantWaveHeight significant wave height (m)
     * @param windSpeed wind speed (m/s)
     * @param solarRadiation solar radiation (W/m2)
     */
    public Segment(double startTimeHours, double endTimeHours, double ambientTemperature,
        double significantWaveHeight, double windSpeed, double solarRadiation) {
      this.startTimeHours = startTimeHours;
      this.endTimeHours = endTimeHours;
      this.ambientTemperature = ambientTemperature;
      this.significantWaveHeight = significantWaveHeight;
      this.windSpeed = windSpeed;
      this.solarRadiation = solarRadiation;
    }

    /**
     * Get segment start time.
     *
     * @return start time (hours)
     */
    public double getStartTimeHours() {
      return startTimeHours;
    }

    /**
     * Get segment end time.
     *
     * @return end time (hours)
     */
    public double getEndTimeHours() {
      return endTimeHours;
    }

    /**
     * Get ambient temperature.
     *
     * @return ambient temperature (K)
     */
    public double getAmbientTemperature() {
      return ambientTemperature;
    }

    /**
     * Get significant wave height.
     *
     * @return wave height (m)
     */
    public double getSignificantWaveHeight() {
      return significantWaveHeight;
    }

    /**
     * Get wind speed.
     *
     * @return wind speed (m/s)
     */
    public double getWindSpeed() {
      return windSpeed;
    }

    /**
     * Get solar radiation.
     *
     * @return solar radiation (W/m2)
     */
    public double getSolarRadiation() {
      return solarRadiation;
    }

    /**
     * Get the duration of this segment.
     *
     * @return duration (hours)
     */
    public double getDurationHours() {
      return endTimeHours - startTimeHours;
    }
  }

  /**
   * Environmental conditions at a specific point in the voyage.
   */
  public static class EnvironmentConditions implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1004L;

    /** Ambient temperature (K). */
    public final double ambientTemperature;

    /** Significant wave height (m). */
    public final double significantWaveHeight;

    /** Wind speed (m/s). */
    public final double windSpeed;

    /** Solar radiation (W/m2). */
    public final double solarRadiation;

    /**
     * Constructor for EnvironmentConditions.
     *
     * @param ambientTemperature ambient temperature (K)
     * @param significantWaveHeight wave height (m)
     * @param windSpeed wind speed (m/s)
     * @param solarRadiation solar radiation (W/m2)
     */
    public EnvironmentConditions(double ambientTemperature, double significantWaveHeight,
        double windSpeed, double solarRadiation) {
      this.ambientTemperature = ambientTemperature;
      this.significantWaveHeight = significantWaveHeight;
      this.windSpeed = windSpeed;
      this.solarRadiation = solarRadiation;
    }
  }
}
