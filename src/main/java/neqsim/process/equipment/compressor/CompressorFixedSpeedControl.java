package neqsim.process.equipment.compressor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Operating point of a constant-speed centrifugal compressor that has to meet a required discharge pressure.
 *
 * <p>
 * Many compressors cannot change speed (motor on a fixed gear, no VFD). The speed solvers of {@link CompressorShaft}
 * then do not apply: the chart gives one head per flow, and the plant meets the pressure set point by moving along the
 * curve. This class reproduces that, on the real chart of the member:
 * </p>
 * <ul>
 * <li>the highest head without leaving the curve is at the process flow, or at the anti-surge control line if the
 * process flow is below it; if that pressure is below the requirement the member has a <b>head deficit</b>;</li>
 * <li>with surplus head, <b>recycle</b> raises the through-flow along the curve until the chart head equals the
 * requirement; the through-flow, recycle fraction, shaft power (through-flow at the real head and chart efficiency) and
 * discharge temperature are those of that point, not of the process flow;</li>
 * <li>if the chart still has surplus head at the stonewall flow, recycle cannot remove it and the surplus has to be
 * <b>throttled</b> ({@link FixedSpeedControlResult.Status#THROTTLE_REQUIRED}); the power is then that of the curve at
 * the lowest allowed flow.</li>
 * </ul>
 *
 * <p>
 * Every chart evaluation runs a deep copy of the member ({@link Compressor#copy()}) at the trial flow, so the molecular
 * weight correction, inlet guide vanes and efficiency model of the member are used exactly as in the flowsheet, and the
 * member and its streams are not changed. The recycle gas is taken to return to the suction at the same state (cooled
 * recycle, same composition), so the through-flow is a pure scaling of the suction stream.
 * </p>
 *
 * <p>
 * Typical use: set the speed of the member (or run a {@link CompressorShaft} at fixed speed), call
 * {@link #evaluate(Compressor, double)} for each stage and each gas (for example a changing flare-gas share), and
 * compare the status, recycle, power and temperature with the limits of the machine.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class CompressorFixedSpeedControl {
  private static final Logger logger = LogManager.getLogger(CompressorFixedSpeedControl.class);

  /** Relative pressure tolerance for "equal" discharge pressures. */
  private static final double PRESSURE_TOLERANCE = 2.0e-3;
  /** Maximum bisection iterations on the through-flow. */
  private static final int MAX_ITERATIONS = 60;
  /** Largest through-flow searched when the chart has no stonewall data, as a multiple of the starting flow. */
  private static final double MAX_FLOW_MULTIPLE = 4.0;

  /** Utility class. */
  private CompressorFixedSpeedControl() {
  }

  /**
   * Evaluate how a constant-speed member meets a required discharge pressure.
   *
   * @param member the compressor, already run, with a performance chart in use and a fixed speed set
   * @param requiredDischargeBara required discharge pressure in bara
   * @return the classification and the controlled operating point; {@link FixedSpeedControlResult.Status#NOT_EVALUATED}
   * (never an exception) when the member has no chart in use, no speed, no flow or the chart run fails
   * @throws IllegalArgumentException if {@code member} is null or the required pressure is not positive
   */
  public static FixedSpeedControlResult evaluate(Compressor member, double requiredDischargeBara) {
    if (member == null) {
      throw new IllegalArgumentException("compressor member must not be null");
    }
    if (!Double.isFinite(requiredDischargeBara) || requiredDischargeBara <= 0.0) {
      throw new IllegalArgumentException("required discharge pressure must be positive");
    }
    String name = member.getName();
    double speed = member.getSpeed();
    CompressorChartInterface chart = member.getCompressorChart();
    if (chart == null || !chart.isUseCompressorChart()) {
      return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara,
          "no compressor chart in use: the fixed-speed operating point needs a chart");
    }
    if (!Double.isFinite(speed) || speed <= 0.0) {
      return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara, "member speed not set");
    }
    StreamInterface inlet = member.getInletStream();
    double q0 = inlet.getFlowRate("m3/hr");
    double m0 = inlet.getFlowRate("kg/hr");
    if (!Double.isFinite(q0) || !Double.isFinite(m0) || q0 <= 0.0 || m0 <= 0.0) {
      return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara, "no inlet flow");
    }

    double surgeFlow = chart.getSurgeFlowAtSpeed(speed);
    double controlFlow = Double.isFinite(surgeFlow) && surgeFlow > 0.0
        ? surgeFlow * (1.0 + member.getSurgeControlMargin())
        : 0.0;
    double stoneWall = chart.getStonewallFlowAtSpeed(speed);
    if (!(stoneWall > 0.0)) {
      stoneWall = chart.getStoneWallFlowAtSpeed(speed);
    }
    boolean hasStoneWall = Double.isFinite(stoneWall) && stoneWall > 0.0;
    double qMin = Math.max(q0, controlFlow);
    if (!Double.isFinite(qMin) || (hasStoneWall && qMin > stoneWall)) {
      return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara,
          "process or anti-surge control flow is outside the valid compressor chart range");
    }

    Compressor probe;
    try {
      probe = member.copy();
      probe.getAntiSurge().setActive(false);
      probe.setSolveSpeed(false);
    } catch (RuntimeException ex) {
      logger.debug("fixed-speed probe copy failed for {}", name, ex);
      return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara, "probe copy failed");
    }

    try {
      double pHigh = deliverable(probe, m0 * qMin / q0);
      if (!Double.isFinite(pHigh) || pHigh <= 0.0) {
        return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara,
            "chart run failed at the lowest flow");
      }
      if (pHigh < requiredDischargeBara * (1.0 - PRESSURE_TOLERANCE)) {
        return result(probe, FixedSpeedControlResult.Status.HEAD_DEFICIT, name, speed, requiredDischargeBara, pHigh, q0,
            qMin, controlFlow, hasStoneWall ? stoneWall : Double.NaN, 0.0,
            "chart head at " + fmt(speed) + " rpm gives " + fmt(pHigh) + " bara, below the required "
                + fmt(requiredDischargeBara) + " bara even without recycle");
      }
      if (pHigh <= requiredDischargeBara * (1.0 + PRESSURE_TOLERANCE)) {
        FixedSpeedControlResult.Status s = qMin > q0 * (1.0 + 1.0e-9)
            ? FixedSpeedControlResult.Status.RECYCLE_CONTROLLED
            : FixedSpeedControlResult.Status.ON_CURVE;
        return result(probe, s, name, speed, requiredDischargeBara, pHigh, q0, qMin, controlFlow,
            hasStoneWall ? stoneWall : Double.NaN, 0.0,
            s == FixedSpeedControlResult.Status.ON_CURVE
                ? "the chart delivers the required pressure at the process flow"
                : "recycle only to hold the anti-surge control line");
      }

      double qUpper = hasStoneWall ? stoneWall : qMin * MAX_FLOW_MULTIPLE;
      if (qUpper <= qMin) {
        return throttled(probe, name, speed, requiredDischargeBara, pHigh, q0, qMin, controlFlow, hasStoneWall,
            stoneWall, m0);
      }
      double pUpper = deliverable(probe, m0 * qUpper / q0);
      if (!Double.isFinite(pUpper) || pUpper <= 0.0) {
        return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara,
            "chart run failed at the highest flow");
      }
      if (!hasStoneWall && pUpper >= requiredDischargeBara * (1.0 - PRESSURE_TOLERANCE)) {
        return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara,
            "no stonewall limit: trial flow cap cannot establish that throttling is required");
      }
      if (pUpper >= requiredDischargeBara * (1.0 - PRESSURE_TOLERANCE)) {
        return throttled(probe, name, speed, requiredDischargeBara, pHigh, q0, qMin, controlFlow, hasStoneWall,
            stoneWall, m0);
      }

      double lo = qMin;
      double hi = qUpper;
      double pMid = pHigh;
      double mid = qMin;
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        mid = 0.5 * (lo + hi);
        pMid = deliverable(probe, m0 * mid / q0);
        if (!Double.isFinite(pMid) || pMid <= 0.0) {
          return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara,
              "chart run failed during the flow search");
        }
        if (Math.abs(pMid - requiredDischargeBara) <= 0.1 * PRESSURE_TOLERANCE * requiredDischargeBara
            || (hi - lo) <= 1.0e-6 * qUpper) {
          break;
        }
        if (pMid > requiredDischargeBara) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
      deliverable(probe, m0 * mid / q0);
      return result(probe, FixedSpeedControlResult.Status.RECYCLE_CONTROLLED, name, speed, requiredDischargeBara, pHigh,
          q0, mid, controlFlow, hasStoneWall ? stoneWall : Double.NaN, 0.0, "surplus head met by recycle: through-flow "
              + fmt(mid) + " m3/hr for a process flow of " + fmt(q0) + " m3/hr");
    } catch (RuntimeException ex) {
      logger.debug("fixed-speed evaluation failed for {}", name, ex);
      return FixedSpeedControlResult.notEvaluated(name, speed, requiredDischargeBara,
          "chart run failed: " + ex.getClass().getSimpleName());
    }
  }

  /**
   * Result for a member whose surplus head cannot be removed by recycle.
   *
   * @param probe the member copy
   * @param name member name
   * @param speed member speed in rpm
   * @param required required discharge pressure in bara
   * @param pHigh discharge pressure at the lowest allowed flow in bara
   * @param q0 process flow in m3/hr
   * @param qMin lowest allowed through-flow in m3/hr
   * @param controlFlow anti-surge control-line flow in m3/hr
   * @param hasStoneWall whether the stonewall flow is known
   * @param stoneWall stonewall flow in m3/hr
   * @param m0 process mass flow in kg/hr
   * @return the throttle-required result at the lowest allowed flow
   */
  private static FixedSpeedControlResult throttled(Compressor probe, String name, double speed, double required,
      double pHigh, double q0, double qMin, double controlFlow, boolean hasStoneWall, double stoneWall, double m0) {
    deliverable(probe, m0 * qMin / q0);
    return result(probe, FixedSpeedControlResult.Status.THROTTLE_REQUIRED, name, speed, required, pHigh, q0, qMin,
        controlFlow, hasStoneWall ? stoneWall : Double.NaN, Math.max(pHigh - required, 0.0),
        "surplus head even at the stonewall flow: " + fmt(pHigh - required)
            + " bar must be throttled or the speed lowered");
  }

  /**
   * Build the result from the state of the probe, which has just been run at the controlled flow.
   *
   * @param probe the member copy at the controlled operating point
   * @param status the classification
   * @param name member name
   * @param speed member speed in rpm
   * @param required required discharge pressure in bara
   * @param deliverable highest discharge pressure reachable in bara
   * @param q0 process flow in m3/hr
   * @param through through-flow in m3/hr
   * @param controlFlow anti-surge control-line flow in m3/hr
   * @param stoneWall stonewall flow in m3/hr ({@code NaN} if unknown)
   * @param throttle pressure to be throttled in bar
   * @param message explanation
   * @return the result
   */
  private static FixedSpeedControlResult result(Compressor probe, FixedSpeedControlResult.Status status, String name,
      double speed, double required, double deliverable, double q0, double through, double controlFlow,
      double stoneWall, double throttle, String message) {
    double recycle = through > 0.0 ? Math.max(0.0, 1.0 - q0 / through) : 0.0;
    double power = safe(probe, "power");
    double temperature = probe.getOutletStream().getTemperature("C");
    double efficiency = safe(probe, "efficiency");
    return new FixedSpeedControlResult(name, status, speed, required, deliverable, q0, through, recycle, controlFlow,
        stoneWall, throttle, power, temperature, efficiency, message);
  }

  /**
   * Run the probe at a trial suction mass flow and return its discharge pressure.
   *
   * @param probe the member copy
   * @param massFlowKgPerHr trial suction mass flow in kg/hr
   * @return discharge pressure in bara, or {@code NaN} if the run failed
   */
  private static double deliverable(Compressor probe, double massFlowKgPerHr) {
    try {
      probe.getInletStream().setFlowRate(massFlowKgPerHr, "kg/hr");
      probe.getInletStream().run();
      probe.run();
      double pressure = probe.getOutletStream().getPressure("bara");
      return Double.isFinite(pressure) && pressure > 0.0 ? pressure : Double.NaN;
    } catch (RuntimeException ex) {
      logger.debug("chart run failed at {} kg/hr", massFlowKgPerHr, ex);
      return Double.NaN;
    }
  }

  /**
   * Read a probe quantity, NaN when unavailable.
   *
   * @param probe the member copy
   * @param what "power" (kW) or "efficiency"
   * @return the value or NaN
   */
  private static double safe(Compressor probe, String what) {
    try {
      if ("power".equals(what)) {
        return probe.getPower("kW");
      }
      return probe.getPolytropicEfficiency();
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  /**
   * Format a number for messages.
   *
   * @param value the value
   * @return text with 4 significant digits
   */
  private static String fmt(double value) {
    return String.format(java.util.Locale.ROOT, "%.4g", value);
  }
}
