package neqsim.process.safety.overpressure;

import java.io.Serializable;

/**
 * Result of an overpressure accumulation acceptance check against the ASME VIII Div 1 / TR3001 section 2
 * accumulated-pressure limits.
 *
 * <p>
 * The check compares a peak (relieving) pressure against the maximum allowable accumulated pressure, which is the
 * protected item MAWP multiplied by the applicable accumulation factor (110% single non-fire device, 116% multiple
 * non-fire devices, 121% fire case).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class AcceptanceResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double peakPressureBara;
  private final double allowableAccumulatedPressureBara;
  private final double accumulationFraction;
  private final boolean accepted;
  private final double marginBara;
  private final String basis;

  /**
   * Creates an immutable acceptance result.
   *
   * @param peakPressureBara the peak (relieving) pressure in bara
   * @param allowableAccumulatedPressureBara the maximum allowable accumulated pressure in bara
   * @param accumulationFraction the accumulation factor applied (for example 1.10, 1.16, 1.21)
   * @param accepted true if the peak pressure does not exceed the allowable accumulated pressure
   * @param marginBara the margin (allowable minus peak) in bara; negative when exceeded
   * @param basis a short text describing the acceptance basis
   */
  public AcceptanceResult(double peakPressureBara, double allowableAccumulatedPressureBara, double accumulationFraction,
      boolean accepted, double marginBara, String basis) {
    this.peakPressureBara = peakPressureBara;
    this.allowableAccumulatedPressureBara = allowableAccumulatedPressureBara;
    this.accumulationFraction = accumulationFraction;
    this.accepted = accepted;
    this.marginBara = marginBara;
    this.basis = basis;
  }

  /**
   * Gets the peak (relieving) pressure used in the check.
   *
   * @return the peak pressure in bara
   */
  public double getPeakPressureBara() {
    return peakPressureBara;
  }

  /**
   * Gets the maximum allowable accumulated pressure.
   *
   * @return the allowable accumulated pressure in bara
   */
  public double getAllowableAccumulatedPressureBara() {
    return allowableAccumulatedPressureBara;
  }

  /**
   * Gets the accumulation factor applied to the MAWP.
   *
   * @return the accumulation factor (dimensionless)
   */
  public double getAccumulationFraction() {
    return accumulationFraction;
  }

  /**
   * Indicates whether the peak pressure is acceptable.
   *
   * @return true if accepted
   */
  public boolean isAccepted() {
    return accepted;
  }

  /**
   * Gets the acceptance margin (allowable minus peak).
   *
   * @return the margin in bara; negative when the limit is exceeded
   */
  public double getMarginBara() {
    return marginBara;
  }

  /**
   * Gets the short text describing the acceptance basis.
   *
   * @return the acceptance basis string
   */
  public String getBasis() {
    return basis;
  }
}
