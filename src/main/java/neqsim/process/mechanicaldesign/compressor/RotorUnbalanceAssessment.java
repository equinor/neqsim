package neqsim.process.mechanicaldesign.compressor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Screening assessment of rotor and coupling unbalance for high-speed rotating equipment.
 *
 * <p>
 * Converts a lost or added mass on a rotor or coupling (for example a broken coupling drive bolt, a missing balance
 * weight or a deposit) into a residual unbalance, compares it with the API 617 / API 671 and ISO 21940-11 allowable
 * residual unbalance, and relates a measured shaft-vibration change to that unbalance through an influence coefficient.
 * It also gives the API 617 shop-test shaft-vibration limit, the ISO 7919-3 shaft relative displacement zone boundaries
 * and the ISO 20816-1 significant-change criterion, so a vibration step can be judged against both the absolute level
 * and the change.
 * </p>
 *
 * <table>
 * <caption>Relations used</caption>
 * <tr>
 * <th>Quantity</th>
 * <th>Relation</th>
 * <th>Basis</th>
 * </tr>
 * <tr>
 * <td>Unbalance of a lost mass</td>
 * <td>U = m r (g mm)</td>
 * <td>definition</td>
 * </tr>
 * <tr>
 * <td>Allowable residual unbalance per plane</td>
 * <td>U = 6350 W / N (g mm), API 671 floor 7.2 g mm</td>
 * <td>API 617 / API 671 (4W/N oz in)</td>
 * </tr>
 * <tr>
 * <td>Balance-grade unbalance</td>
 * <td>U = 1000 G m / omega (g mm)</td>
 * <td>ISO 21940-11</td>
 * </tr>
 * <tr>
 * <td>Rotating force</td>
 * <td>F = U omega^2</td>
 * <td>definition</td>
 * </tr>
 * <tr>
 * <td>Shop shaft-vibration limit</td>
 * <td>A = 25.4 sqrt(12000 / N) micrometre pp, at most 25.4</td>
 * <td>API 617</td>
 * </tr>
 * <tr>
 * <td>Zone boundaries A/B, B/C, C/D</td>
 * <td>4800, 9000, 13200 / sqrt(n) micrometre pp</td>
 * <td>ISO 7919-3 shaft relative displacement</td>
 * </tr>
 * <tr>
 * <td>Significant change</td>
 * <td>25 % of the B/C boundary</td>
 * <td>ISO 20816-1</td>
 * </tr>
 * </table>
 *
 * <p>
 * The influence coefficient is machine-specific; this class only makes the relation between a mass change and a
 * vibration change explicit so that a candidate cause can be checked for consistency. It does not replace a
 * rotordynamic analysis.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class RotorUnbalanceAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Conversion from API 4W/N (oz in, lb) to SI (g mm, kg). */
  public static final double API_UNBALANCE_CONSTANT_GMM = 6350.0;

  /** API 671 minimum residual unbalance, 0.01 oz in expressed in g mm. */
  public static final double API671_MINIMUM_UNBALANCE_GMM = 0.01 * 28.349523125 * 25.4;

  /** ISO 20816-1 significant change as a fraction of the B/C zone boundary. */
  public static final double SIGNIFICANT_CHANGE_FRACTION = 0.25;

  /**
   * Private constructor; use {@link #evaluate(double, double, double, double)} or the static helpers.
   */
  private RotorUnbalanceAssessment() {
  }

  /**
   * Angular speed from rotational speed.
   *
   * @param speedRpm rotational speed in r/min, must be positive
   * @return angular speed in rad/s
   * @throws IllegalArgumentException if speed is not positive
   */
  public static double angularSpeed(double speedRpm) {
    requirePositive(speedRpm, "speedRpm");
    return 2.0 * Math.PI * speedRpm / 60.0;
  }

  /**
   * Unbalance created by a lost or added mass at a radius.
   *
   * @param massG mass in grams, zero or positive
   * @param radiusMm radius of the mass from the rotation axis in millimetres, zero or positive
   * @return unbalance in g mm
   * @throws IllegalArgumentException if a value is negative
   */
  public static double unbalanceFromMass(double massG, double radiusMm) {
    requireNonNegative(massG, "massG");
    requireNonNegative(radiusMm, "radiusMm");
    return massG * radiusMm;
  }

  /**
   * API 617 / API 671 maximum allowable residual unbalance per correction plane.
   *
   * @param planeMassKg rotor or coupling mass supported by (or assigned to) the plane in kg, must be positive
   * @param maxContinuousSpeedRpm maximum continuous speed in r/min, must be positive
   * @param applyApi671Floor true to apply the API 671 minimum of 0.01 oz in (7.2 g mm) used for couplings
   * @return allowable residual unbalance in g mm
   * @throws IllegalArgumentException if an input is not positive
   */
  public static double apiAllowableUnbalance(double planeMassKg, double maxContinuousSpeedRpm,
      boolean applyApi671Floor) {
    requirePositive(planeMassKg, "planeMassKg");
    requirePositive(maxContinuousSpeedRpm, "maxContinuousSpeedRpm");
    double u = API_UNBALANCE_CONSTANT_GMM * planeMassKg / maxContinuousSpeedRpm;
    return applyApi671Floor ? Math.max(u, API671_MINIMUM_UNBALANCE_GMM) : u;
  }

  /**
   * ISO 21940-11 permissible residual unbalance for a balance quality grade.
   *
   * @param gradeMmPerS balance quality grade G in mm/s (for example 2.5 for turbomachinery), must be positive
   * @param rotorMassKg rotor mass in kg, must be positive
   * @param speedRpm maximum service speed in r/min, must be positive
   * @return permissible residual unbalance in g mm
   * @throws IllegalArgumentException if an input is not positive
   */
  public static double isoPermissibleUnbalance(double gradeMmPerS, double rotorMassKg, double speedRpm) {
    requirePositive(gradeMmPerS, "gradeMmPerS");
    requirePositive(rotorMassKg, "rotorMassKg");
    return 1000.0 * gradeMmPerS * rotorMassKg / angularSpeed(speedRpm);
  }

  /**
   * Rotating centrifugal force produced by an unbalance.
   *
   * @param unbalanceGmm unbalance in g mm, zero or positive
   * @param speedRpm rotational speed in r/min, must be positive
   * @return force amplitude in N
   * @throws IllegalArgumentException if unbalance is negative or speed is not positive
   */
  public static double centrifugalForce(double unbalanceGmm, double speedRpm) {
    requireNonNegative(unbalanceGmm, "unbalanceGmm");
    double omega = angularSpeed(speedRpm);
    return unbalanceGmm * 1.0e-6 * omega * omega;
  }

  /**
   * API 617 unfiltered shop-test shaft-vibration limit.
   *
   * @param maxContinuousSpeedRpm maximum continuous speed in r/min, must be positive
   * @return limit in micrometre peak-to-peak
   * @throws IllegalArgumentException if speed is not positive
   */
  public static double apiShaftVibrationLimit(double maxContinuousSpeedRpm) {
    requirePositive(maxContinuousSpeedRpm, "maxContinuousSpeedRpm");
    return Math.min(25.4 * Math.sqrt(12000.0 / maxContinuousSpeedRpm), 25.4);
  }

  /**
   * ISO 7919-3 shaft relative displacement zone boundaries.
   *
   * @param speedRpm rotational speed in r/min, must be positive
   * @return array of the A/B, B/C and C/D boundaries in micrometre peak-to-peak
   * @throws IllegalArgumentException if speed is not positive
   */
  public static double[] isoZoneBoundaries(double speedRpm) {
    requirePositive(speedRpm, "speedRpm");
    double root = Math.sqrt(speedRpm);
    return new double[] {4800.0 / root, 9000.0 / root, 13200.0 / root};
  }

  /**
   * ISO 20816-1 significant change: 25 % of the B/C zone boundary.
   *
   * @param speedRpm rotational speed in r/min, must be positive
   * @return change threshold in micrometre peak-to-peak
   * @throws IllegalArgumentException if speed is not positive
   */
  public static double significantChangeThreshold(double speedRpm) {
    return SIGNIFICANT_CHANGE_FRACTION * isoZoneBoundaries(speedRpm)[1];
  }

  /**
   * Magnitude of the vector change between two synchronous (1X) vibration readings.
   *
   * @param amplitude1 first 1X amplitude, zero or positive
   * @param phase1Deg first 1X phase angle in degrees
   * @param amplitude2 second 1X amplitude, zero or positive
   * @param phase2Deg second 1X phase angle in degrees
   * @return magnitude of the vector difference, in the unit of the amplitudes
   * @throws IllegalArgumentException if an amplitude is negative
   */
  public static double vectorChange(double amplitude1, double phase1Deg, double amplitude2, double phase2Deg) {
    requireNonNegative(amplitude1, "amplitude1");
    requireNonNegative(amplitude2, "amplitude2");
    double dphi = Math.toRadians(phase2Deg - phase1Deg);
    double sq = amplitude1 * amplitude1 + amplitude2 * amplitude2 - 2.0 * amplitude1 * amplitude2 * Math.cos(dphi);
    return Math.sqrt(Math.max(sq, 0.0));
  }

  /**
   * Influence coefficient implied by a vibration change and the unbalance change that caused it.
   *
   * @param vibrationChangeUm magnitude of the 1X vector change in micrometre, zero or positive
   * @param unbalanceChangeGmm unbalance change in g mm, must be positive
   * @return influence coefficient in micrometre per g mm
   * @throws IllegalArgumentException if the unbalance change is not positive or the vibration change is negative
   */
  public static double influenceCoefficient(double vibrationChangeUm, double unbalanceChangeGmm) {
    requireNonNegative(vibrationChangeUm, "vibrationChangeUm");
    requirePositive(unbalanceChangeGmm, "unbalanceChangeGmm");
    return vibrationChangeUm / unbalanceChangeGmm;
  }

  /**
   * Assess a lost or added mass against the API allowable residual unbalance.
   *
   * @param massG lost or added mass in grams, zero or positive
   * @param radiusMm radius of that mass in millimetres, zero or positive
   * @param planeMassKg mass assigned to the correction plane in kg, must be positive
   * @param speedRpm maximum continuous speed in r/min, must be positive
   * @return the assessment result
   * @throws IllegalArgumentException if an input is out of range
   */
  public static Result evaluate(double massG, double radiusMm, double planeMassKg, double speedRpm) {
    double u = unbalanceFromMass(massG, radiusMm);
    double allowable = apiAllowableUnbalance(planeMassKg, speedRpm, true);
    double[] zones = isoZoneBoundaries(speedRpm);
    return new Result(massG, radiusMm, planeMassKg, speedRpm, u, allowable, centrifugalForce(u, speedRpm),
        apiShaftVibrationLimit(speedRpm), zones, significantChangeThreshold(speedRpm));
  }

  /**
   * Throw if a value is not strictly positive or not finite.
   *
   * @param value value to check
   * @param name parameter name used in the message
   * @throws IllegalArgumentException if the value is not positive and finite
   */
  private static void requirePositive(double value, String name) {
    if (!(value > 0.0) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite, was " + value);
    }
  }

  /**
   * Throw if a value is negative or not finite.
   *
   * @param value value to check
   * @param name parameter name used in the message
   * @throws IllegalArgumentException if the value is negative or not finite
   */
  private static void requireNonNegative(double value, String name) {
    if (!(value >= 0.0) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be zero or positive and finite, was " + value);
    }
  }

  /**
   * Result of an unbalance assessment.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double massG;
    private final double radiusMm;
    private final double planeMassKg;
    private final double speedRpm;
    private final double unbalanceGmm;
    private final double allowableUnbalanceGmm;
    private final double centrifugalForceN;
    private final double apiShaftVibrationLimitUm;
    private final double[] isoZoneBoundariesUm;
    private final double significantChangeUm;

    /**
     * Create a result.
     *
     * @param massG lost or added mass in g
     * @param radiusMm radius of the mass in mm
     * @param planeMassKg mass assigned to the plane in kg
     * @param speedRpm speed in r/min
     * @param unbalanceGmm unbalance in g mm
     * @param allowableUnbalanceGmm API allowable residual unbalance in g mm
     * @param centrifugalForceN rotating force in N
     * @param apiShaftVibrationLimitUm API 617 shaft-vibration limit in micrometre pp
     * @param isoZoneBoundariesUm ISO 7919-3 A/B, B/C, C/D boundaries in micrometre pp
     * @param significantChangeUm ISO 20816-1 significant-change threshold in micrometre pp
     */
    Result(double massG, double radiusMm, double planeMassKg, double speedRpm, double unbalanceGmm,
        double allowableUnbalanceGmm, double centrifugalForceN, double apiShaftVibrationLimitUm,
        double[] isoZoneBoundariesUm, double significantChangeUm) {
      this.massG = massG;
      this.radiusMm = radiusMm;
      this.planeMassKg = planeMassKg;
      this.speedRpm = speedRpm;
      this.unbalanceGmm = unbalanceGmm;
      this.allowableUnbalanceGmm = allowableUnbalanceGmm;
      this.centrifugalForceN = centrifugalForceN;
      this.apiShaftVibrationLimitUm = apiShaftVibrationLimitUm;
      this.isoZoneBoundariesUm = isoZoneBoundariesUm.clone();
      this.significantChangeUm = significantChangeUm;
    }

    /**
     * Get the unbalance created by the mass.
     *
     * @return unbalance in g mm
     */
    public double getUnbalanceGmm() {
      return unbalanceGmm;
    }

    /**
     * Get the API allowable residual unbalance per plane.
     *
     * @return allowable unbalance in g mm
     */
    public double getAllowableUnbalanceGmm() {
      return allowableUnbalanceGmm;
    }

    /**
     * Get the ratio of the unbalance to the API allowable residual unbalance.
     *
     * @return unbalance divided by the allowable value
     */
    public double getUnbalanceRatio() {
      return unbalanceGmm / allowableUnbalanceGmm;
    }

    /**
     * Get the rotating force of the unbalance.
     *
     * @return force amplitude in N
     */
    public double getCentrifugalForceN() {
      return centrifugalForceN;
    }

    /**
     * Get the API 617 shop-test shaft-vibration limit.
     *
     * @return limit in micrometre peak-to-peak
     */
    public double getApiShaftVibrationLimitUm() {
      return apiShaftVibrationLimitUm;
    }

    /**
     * Get the ISO 7919-3 zone boundaries.
     *
     * @return copy of the A/B, B/C and C/D boundaries in micrometre peak-to-peak
     */
    public double[] getIsoZoneBoundariesUm() {
      return isoZoneBoundariesUm.clone();
    }

    /**
     * Get the ISO 20816-1 significant-change threshold.
     *
     * @return threshold in micrometre peak-to-peak
     */
    public double getSignificantChangeUm() {
      return significantChangeUm;
    }

    /**
     * Check whether the unbalance exceeds the API allowable residual unbalance.
     *
     * @return true if the unbalance is larger than the allowable value
     */
    public boolean exceedsAllowable() {
      return unbalanceGmm > allowableUnbalanceGmm;
    }

    /**
     * Serialise the result to JSON.
     *
     * @return pretty-printed JSON string
     */
    public String toJson() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("massG", massG);
      map.put("radiusMm", radiusMm);
      map.put("planeMassKg", planeMassKg);
      map.put("speedRpm", speedRpm);
      map.put("unbalanceGmm", unbalanceGmm);
      map.put("allowableUnbalanceGmm", allowableUnbalanceGmm);
      map.put("unbalanceRatio", getUnbalanceRatio());
      map.put("exceedsAllowable", exceedsAllowable());
      map.put("centrifugalForceN", centrifugalForceN);
      map.put("apiShaftVibrationLimitUm", apiShaftVibrationLimitUm);
      map.put("isoZoneAB_Um", isoZoneBoundariesUm[0]);
      map.put("isoZoneBC_Um", isoZoneBoundariesUm[1]);
      map.put("isoZoneCD_Um", isoZoneBoundariesUm[2]);
      map.put("significantChangeUm", significantChangeUm);
      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(map);
    }
  }
}
