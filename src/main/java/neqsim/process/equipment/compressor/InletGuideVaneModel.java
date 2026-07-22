package neqsim.process.equipment.compressor;

import java.io.Serializable;

/**
 * Parametric inlet-guide-vane (IGV) model for a centrifugal {@link Compressor}.
 *
 * <p>
 * At a fixed shaft speed, closing the inlet guide vanes adds pre-swirl to the flow entering the impeller. Relative to
 * the fully-open reference chart this: reduces the developed head, reduces the polytropic efficiency, and lowers the
 * surge flow (shifts the surge line to the left, which is what extends the machine's turndown). This class captures
 * those three effects as simple, configurable functions of an <em>opening fraction</em> {@code f} in {@code [0, 1]},
 * where {@code f = 1} is fully open (no correction) and {@code f = 0} is fully closed.
 * </p>
 *
 * <p>
 * The defaults are generic, screening-level linear sensitivities of the form {@code value = 1 - k*(1 - f)}. They are
 * <em>not</em> vendor-certified; supply site-specific sensitivities (or, for rigorous work, an IGV-position chart
 * family) when the data exist. With {@code f = 1} every correction is the identity, so an unset IGV leaves the
 * compressor behaviour unchanged.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class InletGuideVaneModel implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Guide-vane angle (deg) at fully open (no pre-swirl). */
  private double openAngleDeg = 0.0;

  /** Guide-vane angle (deg) at fully closed. */
  private double closedAngleDeg = 60.0;

  /** Head sensitivity: head multiplier is {@code 1 - headDrop*(1 - f)}. */
  private double headDrop = 0.60;

  /** Efficiency sensitivity: efficiency delta (fraction) is {@code -efficiencyDrop*(1 - f)}. */
  private double efficiencyDrop = 0.08;

  /** Surge-flow sensitivity: surge-flow multiplier is {@code 1 - surgeFlowDrop*(1 - f)}. */
  private double surgeFlowDrop = 0.50;

  /** Lower clamp applied to the head and surge-flow multipliers. */
  private double minMultiplier = 0.20;

  /** Default constructor with generic screening-level sensitivities. */
  public InletGuideVaneModel() {
  }

  /**
   * Clamp an opening fraction to {@code [0, 1]}.
   *
   * @param opening the requested opening fraction
   * @return the clamped opening fraction
   */
  private double clampOpening(double opening) {
    if (opening < 0.0) {
      return 0.0;
    }
    if (opening > 1.0) {
      return 1.0;
    }
    return opening;
  }

  /**
   * Head multiplier applied to the fully-open chart head at the given opening.
   *
   * @param opening the IGV opening fraction (1 = fully open)
   * @return the head multiplier in {@code [minMultiplier, 1]}
   */
  public double headMultiplier(double opening) {
    double f = clampOpening(opening);
    return Math.max(minMultiplier, 1.0 - headDrop * (1.0 - f));
  }

  /**
   * Polytropic-efficiency delta (as a fraction, e.g. {@code -0.04} = minus 4 points) at the given opening.
   *
   * @param opening the IGV opening fraction (1 = fully open)
   * @return the efficiency delta as a fraction (&le; 0)
   */
  public double efficiencyDelta(double opening) {
    double f = clampOpening(opening);
    return -efficiencyDrop * (1.0 - f);
  }

  /**
   * Surge-flow multiplier applied to the fully-open surge flow at the given opening. Values below 1 move the surge line
   * to the left (extending turndown).
   *
   * @param opening the IGV opening fraction (1 = fully open)
   * @return the surge-flow multiplier in {@code [minMultiplier, 1]}
   */
  public double surgeFlowMultiplier(double opening) {
    double f = clampOpening(opening);
    return Math.max(minMultiplier, 1.0 - surgeFlowDrop * (1.0 - f));
  }

  /**
   * Convert a guide-vane angle (deg) to an opening fraction using the configured open/closed angles.
   *
   * @param angleDeg the guide-vane angle in degrees
   * @return the opening fraction in {@code [0, 1]} (1 = fully open)
   */
  public double openingFromAngle(double angleDeg) {
    double span = closedAngleDeg - openAngleDeg;
    if (Math.abs(span) < 1.0e-9) {
      return 1.0;
    }
    return clampOpening((closedAngleDeg - angleDeg) / span);
  }

  /**
   * Convert an opening fraction to a guide-vane angle (deg) using the configured open/closed angles.
   *
   * @param opening the opening fraction (1 = fully open)
   * @return the guide-vane angle in degrees
   */
  public double angleFromOpening(double opening) {
    double f = clampOpening(opening);
    return closedAngleDeg - f * (closedAngleDeg - openAngleDeg);
  }

  /**
   * Set the head sensitivity coefficient {@code k} in {@code headMultiplier = 1 - k*(1 - f)}.
   *
   * @param headDrop the head sensitivity (typically 0..1)
   */
  public void setHeadDrop(double headDrop) {
    this.headDrop = headDrop;
  }

  /**
   * Get the head sensitivity coefficient.
   *
   * @return the head sensitivity coefficient
   */
  public double getHeadDrop() {
    return headDrop;
  }

  /**
   * Set the efficiency sensitivity coefficient {@code k} in {@code efficiencyDelta = -k*(1 - f)} (fraction).
   *
   * @param efficiencyDrop the efficiency sensitivity (typically 0..0.15)
   */
  public void setEfficiencyDrop(double efficiencyDrop) {
    this.efficiencyDrop = efficiencyDrop;
  }

  /**
   * Get the efficiency sensitivity coefficient.
   *
   * @return the efficiency sensitivity coefficient
   */
  public double getEfficiencyDrop() {
    return efficiencyDrop;
  }

  /**
   * Set the surge-flow sensitivity coefficient {@code k} in {@code surgeFlowMultiplier = 1 - k*(1 - f)}.
   *
   * @param surgeFlowDrop the surge-flow sensitivity (typically 0..1)
   */
  public void setSurgeFlowDrop(double surgeFlowDrop) {
    this.surgeFlowDrop = surgeFlowDrop;
  }

  /**
   * Get the surge-flow sensitivity coefficient.
   *
   * @return the surge-flow sensitivity coefficient
   */
  public double getSurgeFlowDrop() {
    return surgeFlowDrop;
  }

  /**
   * Set the fully-open and fully-closed guide-vane reference angles used by the angle/opening conversions.
   *
   * @param openAngleDeg the fully-open angle in degrees
   * @param closedAngleDeg the fully-closed angle in degrees
   */
  public void setReferenceAngles(double openAngleDeg, double closedAngleDeg) {
    this.openAngleDeg = openAngleDeg;
    this.closedAngleDeg = closedAngleDeg;
  }
}
