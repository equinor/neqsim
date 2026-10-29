package neqsim.process.equipment.compressor;

import java.io.Serializable;

/**
 * Outcome of {@link CompressorFixedSpeedControl#evaluate(Compressor, double)}: how a constant-speed compressor member
 * meets a required discharge pressure and what that costs.
 *
 * <p>
 * A constant-speed machine (electric motor on a fixed gear, no variable-speed drive) cannot change rpm. Its chart gives
 * a fixed head at each flow, so the discharge pressure is controlled by moving the operating point along the curve:
 * recycle raises the through-flow and lowers the head, a throttle burns the surplus head, and a head shortfall cannot
 * be recovered at all. This result classifies the case, gives the controlled operating point (through-flow, recycle
 * fraction, shaft power and discharge temperature at the real head) and the margins to the surge control line and the
 * stonewall limit.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class FixedSpeedControlResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** How the required discharge pressure is met at the fixed speed. */
  public enum Status {
    /** The chart delivers the required pressure at the process flow: no recycle and no throttling. */
    ON_CURVE,
    /** The chart has surplus head and a higher through-flow (recycle) brings the head down to the requirement. */
    RECYCLE_CONTROLLED,
    /**
     * The chart still has surplus head at the stonewall flow, so recycle cannot remove it: the surplus must be
     * throttled (discharge or suction) or the speed lowered.
     */
    THROTTLE_REQUIRED,
    /** The chart cannot deliver the required pressure at the fixed speed even without recycle (head deficit). */
    HEAD_DEFICIT,
    /** The member could not be evaluated (idle, no chart, no required pressure or the chart run failed). */
    NOT_EVALUATED
  }

  private final String memberName;
  private final Status status;
  private final double speedRpm;
  private final double requiredDischargeBara;
  private final double deliverableDischargeBara;
  private final double processFlowM3PerHr;
  private final double throughFlowM3PerHr;
  private final double recycleFraction;
  private final double surgeControlFlowM3PerHr;
  private final double stoneWallFlowM3PerHr;
  private final double throttlePressureDropBar;
  private final double shaftPowerKW;
  private final double dischargeTemperatureC;
  private final double polytropicEfficiency;
  private final String message;

  /**
   * Constructor.
   *
   * @param memberName compressor name
   * @param status how the requirement is met
   * @param speedRpm fixed member speed in rpm
   * @param requiredDischargeBara required discharge pressure in bara
   * @param deliverableDischargeBara highest discharge pressure the chart reaches at the fixed speed without recycling
   * below the surge control line (the pressure at the process flow, or at the control-line flow if that is higher), in
   * bara
   * @param processFlowM3PerHr actual inlet flow of the process in m3/hr
   * @param throughFlowM3PerHr actual flow through the machine at the controlled operating point (process flow plus
   * recycle) in m3/hr
   * @param recycleFraction recycle share of the through-flow, 0 to 1
   * @param surgeControlFlowM3PerHr flow of the anti-surge control line at this speed in m3/hr (0 if unknown)
   * @param stoneWallFlowM3PerHr stonewall flow at this speed in m3/hr ({@code NaN} if unknown)
   * @param throttlePressureDropBar discharge pressure that has to be throttled away at the controlled point, in bar
   * @param shaftPowerKW shaft power at the controlled operating point (through-flow at the real chart head) in kW
   * @param dischargeTemperatureC machine discharge temperature at the controlled operating point in C
   * @param polytropicEfficiency chart polytropic efficiency at the controlled operating point (0 to 1)
   * @param message short explanation
   */
  public FixedSpeedControlResult(String memberName, Status status, double speedRpm, double requiredDischargeBara,
      double deliverableDischargeBara, double processFlowM3PerHr, double throughFlowM3PerHr, double recycleFraction,
      double surgeControlFlowM3PerHr, double stoneWallFlowM3PerHr, double throttlePressureDropBar, double shaftPowerKW,
      double dischargeTemperatureC, double polytropicEfficiency, String message) {
    this.memberName = memberName;
    this.status = status;
    this.speedRpm = speedRpm;
    this.requiredDischargeBara = requiredDischargeBara;
    this.deliverableDischargeBara = deliverableDischargeBara;
    this.processFlowM3PerHr = processFlowM3PerHr;
    this.throughFlowM3PerHr = throughFlowM3PerHr;
    this.recycleFraction = recycleFraction;
    this.surgeControlFlowM3PerHr = surgeControlFlowM3PerHr;
    this.stoneWallFlowM3PerHr = stoneWallFlowM3PerHr;
    this.throttlePressureDropBar = throttlePressureDropBar;
    this.shaftPowerKW = shaftPowerKW;
    this.dischargeTemperatureC = dischargeTemperatureC;
    this.polytropicEfficiency = polytropicEfficiency;
    this.message = message;
  }

  /**
   * Create a result for a member that could not be evaluated.
   *
   * @param memberName compressor name
   * @param speedRpm member speed in rpm (may be 0)
   * @param requiredDischargeBara required discharge pressure in bara ({@code NaN} if not set)
   * @param message why the member was not evaluated
   * @return a {@link Status#NOT_EVALUATED} result with NaN numbers
   */
  public static FixedSpeedControlResult notEvaluated(String memberName, double speedRpm, double requiredDischargeBara,
      String message) {
    double nan = Double.NaN;
    return new FixedSpeedControlResult(memberName, Status.NOT_EVALUATED, speedRpm, requiredDischargeBara, nan, nan, nan,
        nan, nan, nan, nan, nan, nan, nan, message);
  }

  /**
   * Get the compressor name.
   *
   * @return the member name
   */
  public String getMemberName() {
    return memberName;
  }

  /**
   * Get the status.
   *
   * @return how the required pressure is met
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Whether the member can deliver the required pressure at its fixed speed (recycle or throttling allowed).
   *
   * @return {@code true} unless the status is {@link Status#HEAD_DEFICIT} or {@link Status#NOT_EVALUATED}
   */
  public boolean isFeasible() {
    return status != Status.HEAD_DEFICIT && status != Status.NOT_EVALUATED;
  }

  /**
   * Get the fixed member speed.
   *
   * @return speed in rpm
   */
  public double getSpeedRpm() {
    return speedRpm;
  }

  /**
   * Get the required discharge pressure.
   *
   * @return pressure in bara
   */
  public double getRequiredDischargeBara() {
    return requiredDischargeBara;
  }

  /**
   * Get the highest discharge pressure reachable at the fixed speed.
   *
   * @return pressure in bara
   */
  public double getDeliverableDischargeBara() {
    return deliverableDischargeBara;
  }

  /**
   * Get the process inlet flow.
   *
   * @return actual flow in m3/hr
   */
  public double getProcessFlowM3PerHr() {
    return processFlowM3PerHr;
  }

  /**
   * Get the flow through the machine at the controlled operating point.
   *
   * @return actual flow in m3/hr (process plus recycle)
   */
  public double getThroughFlowM3PerHr() {
    return throughFlowM3PerHr;
  }

  /**
   * Get the recycle fraction at the controlled operating point.
   *
   * @return recycle share of the through-flow (0 to 1)
   */
  public double getRecycleFraction() {
    return recycleFraction;
  }

  /**
   * Get the surge control line flow at this speed.
   *
   * @return flow in m3/hr (0 if unknown)
   */
  public double getSurgeControlFlowM3PerHr() {
    return surgeControlFlowM3PerHr;
  }

  /**
   * Get the stonewall flow at this speed.
   *
   * @return flow in m3/hr ({@code NaN} if unknown)
   */
  public double getStoneWallFlowM3PerHr() {
    return stoneWallFlowM3PerHr;
  }

  /**
   * Get the pressure that has to be throttled away.
   *
   * @return pressure drop in bar (0 when recycle or the curve meets the requirement)
   */
  public double getThrottlePressureDropBar() {
    return throttlePressureDropBar;
  }

  /**
   * Get the shaft power at the controlled operating point.
   *
   * @return power in kW
   */
  public double getShaftPowerKW() {
    return shaftPowerKW;
  }

  /**
   * Get the machine discharge temperature at the controlled operating point.
   *
   * @return temperature in C
   */
  public double getDischargeTemperatureC() {
    return dischargeTemperatureC;
  }

  /**
   * Get the chart polytropic efficiency at the controlled operating point.
   *
   * @return efficiency (0 to 1)
   */
  public double getPolytropicEfficiency() {
    return polytropicEfficiency;
  }

  /**
   * Get the explanation.
   *
   * @return short message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Serialize the result to a compact JSON object.
   *
   * @return a JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"member\":\"").append(memberName == null ? "" : memberName.replace("\"", "'")).append("\"");
    sb.append(",\"status\":\"").append(status).append("\"");
    sb.append(",\"feasible\":").append(isFeasible());
    sb.append(",\"speedRpm\":").append(num(speedRpm));
    sb.append(",\"requiredDischargeBara\":").append(num(requiredDischargeBara));
    sb.append(",\"deliverableDischargeBara\":").append(num(deliverableDischargeBara));
    sb.append(",\"processFlowM3PerHr\":").append(num(processFlowM3PerHr));
    sb.append(",\"throughFlowM3PerHr\":").append(num(throughFlowM3PerHr));
    sb.append(",\"recycleFraction\":").append(num(recycleFraction));
    sb.append(",\"surgeControlFlowM3PerHr\":").append(num(surgeControlFlowM3PerHr));
    sb.append(",\"stoneWallFlowM3PerHr\":").append(num(stoneWallFlowM3PerHr));
    sb.append(",\"throttlePressureDropBar\":").append(num(throttlePressureDropBar));
    sb.append(",\"shaftPowerKW\":").append(num(shaftPowerKW));
    sb.append(",\"dischargeTemperatureC\":").append(num(dischargeTemperatureC));
    sb.append(",\"polytropicEfficiency\":").append(num(polytropicEfficiency));
    sb.append(",\"message\":\"").append(message == null ? "" : message.replace("\"", "'")).append("\"}");
    return sb.toString();
  }

  /**
   * Format a number for JSON (NaN and infinity become null).
   *
   * @param value the value
   * @return JSON number text or {@code null}
   */
  private static String num(double value) {
    return Double.isNaN(value) || Double.isInfinite(value) ? "null" : Double.toString(value);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return memberName + " " + status + " (" + message + ")";
  }
}
