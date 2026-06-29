package neqsim.process.safety.overpressure;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;

/**
 * Pipeline overpressure-protection screening using a two-barrier philosophy.
 *
 * <p>
 * Evaluates whether a pipeline or piping segment is protected against a high-pressure source. The segment may be:
 * </p>
 *
 * <ul>
 * <li><b>fully rated</b> &mdash; the maximum source pressure is at or below the design pressure, so no independent
 * protection is strictly required;</li>
 * <li><b>protected by barriers</b> &mdash; one or two independent pressure-protection barriers (for example PSV and
 * HIPPS) keep the pressure within the design and maximum incidental pressure (MIP);</li>
 * <li><b>insufficient</b> &mdash; the source can overpressure the segment beyond the protection provided.</li>
 * </ul>
 *
 * <p>
 * The maximum incidental pressure is MIP = incidental factor &middot; design pressure (a factor of 1.1 is commonly
 * used). Each barrier set point is checked against the design pressure (barrier 1) and the MIP (barrier 2).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PipelinePressureProtectionCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(PipelinePressureProtectionCalculator.class);

  // ===== Inputs =====
  /** Maximum credible source pressure in bar. */
  private double maxSourcePressure = 250.0;
  /** Segment design pressure in bar. */
  private double designPressure = 150.0;
  /** Incidental factor used to compute the maximum incidental pressure. */
  private double incidentalFactor = 1.1;
  /** First barrier set point in bar. */
  private double barrier1Setpoint = 145.0;
  /** Second barrier set point in bar. */
  private double barrier2Setpoint = 160.0;

  // ===== Results =====
  /** Maximum incidental pressure in bar. */
  private double maxIncidentalPressure;
  /** True when the segment is fully rated for the source. */
  private boolean fullyRated;
  /** True when barrier 1 set point is within the design pressure. */
  private boolean barrier1Adequate;
  /** True when barrier 2 set point is within the maximum incidental pressure. */
  private boolean barrier2Adequate;
  /** True when overall protection is adequate. */
  private boolean protectionAdequate;
  /** Margin of design pressure over the source pressure in bar (negative when under-rated). */
  private double designMargin;
  /** Verdict (FULLY_RATED, TWO_BARRIERS_REQUIRED, INSUFFICIENT). */
  private String verdict;

  /**
   * Default constructor for PipelinePressureProtectionCalculator.
   */
  public PipelinePressureProtectionCalculator() {
  }

  /**
   * Sets the pressure basis.
   *
   * @param maxSourcePressureBar maximum credible source pressure in bar (must be &gt; 0)
   * @param designPressureBar segment design pressure in bar (must be &gt; 0)
   * @param incidentalFactorValue incidental factor (typically 1.1, must be &ge; 1)
   */
  public void setPressureBasis(double maxSourcePressureBar, double designPressureBar, double incidentalFactorValue) {
    this.maxSourcePressure = maxSourcePressureBar;
    this.designPressure = designPressureBar;
    this.incidentalFactor = incidentalFactorValue;
  }

  /**
   * Sets the protection-barrier set points.
   *
   * @param barrier1SetpointBar first barrier set point in bar (must be &gt; 0)
   * @param barrier2SetpointBar second barrier set point in bar (must be &gt; 0)
   */
  public void setBarriers(double barrier1SetpointBar, double barrier2SetpointBar) {
    this.barrier1Setpoint = barrier1SetpointBar;
    this.barrier2Setpoint = barrier2SetpointBar;
  }

  /**
   * Runs the two-barrier overpressure-protection screening.
   */
  public void calcProtection() {
    maxIncidentalPressure = incidentalFactor * designPressure;
    designMargin = designPressure - maxSourcePressure;
    fullyRated = maxSourcePressure <= designPressure;
    barrier1Adequate = barrier1Setpoint <= designPressure;
    barrier2Adequate = barrier2Setpoint <= maxIncidentalPressure;

    if (fullyRated) {
      protectionAdequate = true;
      verdict = "FULLY_RATED";
    } else if (barrier1Adequate && barrier2Adequate) {
      protectionAdequate = true;
      verdict = "TWO_BARRIERS_REQUIRED";
    } else {
      protectionAdequate = false;
      verdict = "INSUFFICIENT";
    }

    logger.debug("Pipeline protection: MIP={} bar, fullyRated={}, b1ok={}, b2ok={}, verdict={}", maxIncidentalPressure,
        fullyRated, barrier1Adequate, barrier2Adequate, verdict);
  }

  /**
   * Returns the maximum incidental pressure.
   *
   * @return maximum incidental pressure in bar
   */
  public double getMaxIncidentalPressure() {
    return maxIncidentalPressure;
  }

  /**
   * Returns whether the segment is fully rated for the source.
   *
   * @return true when fully rated
   */
  public boolean isFullyRated() {
    return fullyRated;
  }

  /**
   * Returns whether barrier 1 is adequate.
   *
   * @return true when barrier 1 set point is within the design pressure
   */
  public boolean isBarrier1Adequate() {
    return barrier1Adequate;
  }

  /**
   * Returns whether barrier 2 is adequate.
   *
   * @return true when barrier 2 set point is within the maximum incidental pressure
   */
  public boolean isBarrier2Adequate() {
    return barrier2Adequate;
  }

  /**
   * Returns whether overall protection is adequate.
   *
   * @return true when protection is adequate
   */
  public boolean isProtectionAdequate() {
    return protectionAdequate;
  }

  /**
   * Returns the design margin over the source pressure.
   *
   * @return design margin in bar (negative when under-rated)
   */
  public double getDesignMargin() {
    return designMargin;
  }

  /**
   * Returns the protection verdict.
   *
   * @return verdict (FULLY_RATED, TWO_BARRIERS_REQUIRED, INSUFFICIENT)
   */
  public String getVerdict() {
    return verdict;
  }

  /**
   * Serializes the calculation results to a pretty-printed JSON string.
   *
   * @return JSON representation of the results
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
