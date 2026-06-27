package neqsim.process.safety.overpressure;

import java.io.Serializable;

/**
 * Evaluates whether a peak (relieving) pressure is acceptable against the ASME VIII Div 1 / TR3001 section 2 maximum
 * allowable accumulated-pressure limits.
 *
 * <p>
 * The maximum allowable accumulated pressure is the protected item MAWP multiplied by an accumulation factor that
 * depends on the relief contingency:
 * </p>
 *
 * <table>
 * <caption>Accumulation factors for pressure relief device sizing</caption>
 * <tr>
 * <th>Contingency</th>
 * <th>Accumulation factor</th>
 * </tr>
 * <tr>
 * <td>Single relief device, non-fire</td>
 * <td>1.10 (110% MAWP)</td>
 * </tr>
 * <tr>
 * <td>Multiple relief devices, non-fire</td>
 * <td>1.16 (116% MAWP)</td>
 * </tr>
 * <tr>
 * <td>Fire case (any number of devices)</td>
 * <td>1.21 (121% MAWP)</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OverpressureAcceptanceChecker implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Accumulation factor for a single non-fire relief device. */
  public static final double SINGLE_NON_FIRE_FACTOR = 1.10;
  /** Accumulation factor for multiple non-fire relief devices. */
  public static final double MULTIPLE_NON_FIRE_FACTOR = 1.16;
  /** Accumulation factor for the fire contingency. */
  public static final double FIRE_FACTOR = 1.21;

  /**
   * Returns the accumulation factor that applies to the supplied contingency.
   *
   * @param cause the overpressure cause; not null
   * @param multipleDevices true if more than one relief device protects the item
   * @return the accumulation factor (dimensionless)
   */
  public double accumulationFactor(ReliefCause cause, boolean multipleDevices) {
    if (cause == ReliefCause.FIRE) {
      return FIRE_FACTOR;
    }
    return multipleDevices ? MULTIPLE_NON_FIRE_FACTOR : SINGLE_NON_FIRE_FACTOR;
  }

  /**
   * Checks a peak (relieving) pressure against the maximum allowable accumulated pressure for the protected item and
   * contingency.
   *
   * @param peakPressureBara the peak (relieving) pressure in bara
   * @param item the protected item supplying the MAWP; not null
   * @param cause the overpressure cause determining the accumulation factor; not null
   * @param multipleDevices true if more than one relief device protects the item
   * @return an {@link AcceptanceResult} describing the comparison
   */
  public AcceptanceResult check(double peakPressureBara, ProtectedItem item, ReliefCause cause,
      boolean multipleDevices) {
    double factor = accumulationFactor(cause, multipleDevices);
    double allowable = item.getMaximumAllowableWorkingPressureBara() * factor;
    double margin = allowable - peakPressureBara;
    boolean accepted = peakPressureBara <= allowable;
    String basis = String.format("%.0f%% of MAWP (%.2f bara x %.2f) for %s%s", factor * 100.0,
        item.getMaximumAllowableWorkingPressureBara(), factor, cause.getLabel(),
        cause == ReliefCause.FIRE ? "" : (multipleDevices ? ", multiple devices" : ", single device"));
    return new AcceptanceResult(peakPressureBara, allowable, factor, accepted, margin, basis);
  }
}
