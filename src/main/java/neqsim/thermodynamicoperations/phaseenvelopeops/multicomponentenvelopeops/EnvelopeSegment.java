/*
 * EnvelopeSegment.java
 */

package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import java.io.Serializable;

/**
 * One contiguous branch of a PT phase envelope.
 *
 * <p>
 * Modern envelope tracers emit the envelope as a list of segments instead of flat arrays. Each
 * segment represents a polyline with a known phase type (dew or bubble) so downstream code
 * (plotting, flash initialization, critical-point analysis) does not have to guess where one
 * branch ends and another begins.
 * </p>
 *
 * <p>
 * Segments are produced by splitting the internal per-point lists on NaN sentinel values that the
 * tracer inserts at every branch transition (primary-to-restart pass and critical-point
 * crossings). A segment always contains &ge; 1 point and never contains NaN.
 * </p>
 *
 * @author asmund
 * @version 1.0
 */
public final class EnvelopeSegment implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /**
   * Which side of the envelope this segment belongs to.
   */
  public enum PhaseType {
    /** Dew curve (liquid fraction ~ 0). */
    DEW,
    /** Bubble curve (vapor fraction ~ 0). */
    BUBBLE
  }

  private final PhaseType phaseType;
  private final double[] temperatures;
  private final double[] pressures;
  private final double[] enthalpies;
  private final double[] densities;
  private final double[] entropies;

  /**
   * Construct an immutable segment.
   *
   * @param phaseType dew or bubble
   * @param temperatures temperatures in Kelvin (length &ge; 1, no NaN)
   * @param pressures pressures in bara (same length as temperatures)
   * @param enthalpies enthalpies in kJ/kg (same length, may be all-zero if not tracked)
   * @param densities densities in kg/m3 (same length, may be all-zero if not tracked)
   * @param entropies entropies in kJ/kg/K (same length, may be all-zero if not tracked)
   */
  public EnvelopeSegment(PhaseType phaseType, double[] temperatures, double[] pressures,
      double[] enthalpies, double[] densities, double[] entropies) {
    if (phaseType == null) {
      throw new IllegalArgumentException("phaseType must not be null");
    }
    if (temperatures == null || pressures == null) {
      throw new IllegalArgumentException("temperatures and pressures must not be null");
    }
    if (temperatures.length != pressures.length) {
      throw new IllegalArgumentException(
          "temperatures and pressures must have equal length: " + temperatures.length + " vs "
              + pressures.length);
    }
    this.phaseType = phaseType;
    this.temperatures = temperatures.clone();
    this.pressures = pressures.clone();
    this.enthalpies = enthalpies == null ? new double[temperatures.length] : enthalpies.clone();
    this.densities = densities == null ? new double[temperatures.length] : densities.clone();
    this.entropies = entropies == null ? new double[temperatures.length] : entropies.clone();
  }

  /**
   * Segment phase type.
   *
   * @return DEW or BUBBLE
   */
  public PhaseType getPhaseType() {
    return phaseType;
  }

  /**
   * Number of points in this segment.
   *
   * @return length of the arrays
   */
  public int size() {
    return temperatures.length;
  }

  /**
   * Temperatures along this segment.
   *
   * @return defensive copy of the temperature array, in Kelvin
   */
  public double[] getTemperatures() {
    return temperatures.clone();
  }

  /**
   * Pressures along this segment.
   *
   * @return defensive copy of the pressure array, in bara
   */
  public double[] getPressures() {
    return pressures.clone();
  }

  /**
   * Mass enthalpies along this segment.
   *
   * @return defensive copy of the enthalpy array, in kJ/kg
   */
  public double[] getEnthalpies() {
    return enthalpies.clone();
  }

  /**
   * Mass densities along this segment.
   *
   * @return defensive copy of the density array, in kg/m3
   */
  public double[] getDensities() {
    return densities.clone();
  }

  /**
   * Mass entropies along this segment.
   *
   * @return defensive copy of the entropy array, in kJ/kg/K
   */
  public double[] getEntropies() {
    return entropies.clone();
  }
}
