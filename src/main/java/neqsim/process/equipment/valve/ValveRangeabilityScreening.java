package neqsim.process.equipment.valve;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Screening helper for control-valve installed-flow sensitivity at low travel.
 *
 * <p>
 * A recycle / anti-surge valve is sometimes constrained to a minimum opening above its true desired setpoint (e.g. a
 * positioner "low clamp" added to avoid seat wear or perceived instability). This helper quantifies, for a given ISA-75
 * style inherent trim characteristic and rangeability, how much extra flow such a forced minimum-opening clamp lets
 * through relative to the desired opening - a first screening step before committing to a detailed valve signature test
 * or a full process simulation of the resulting recompression power penalty. It performs no thermodynamics and no
 * network or database access; it is pure ISA-75 trim-curve arithmetic.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ValveRangeabilityScreening {
  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(ValveRangeabilityScreening.class);

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private ValveRangeabilityScreening() {
  }

  /**
   * Immutable result of a valve rangeability screening evaluation.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Trim characteristic used for the evaluation. */
    private final ValveTrimCharacteristic trim;
    /** Rangeability (Cv_max / Cv_min) used for the evaluation. */
    private final double rangeability;
    /** First (typically desired) travel fraction, 0-1. */
    private final double travelFraction1;
    /** Second (typically actual/clamped) travel fraction, 0-1. */
    private final double travelFraction2;
    /** Normalized flow fraction (Cv/Cv_max) at travelFraction1. */
    private final double flowFraction1;
    /** Normalized flow fraction (Cv/Cv_max) at travelFraction2. */
    private final double flowFraction2;

    /**
     * Constructor for Result.
     *
     * @param trim trim characteristic used
     * @param rangeability rangeability (Cv_max / Cv_min) used
     * @param travelFraction1 first travel fraction, 0-1
     * @param travelFraction2 second travel fraction, 0-1
     * @param flowFraction1 normalized flow fraction at travelFraction1
     * @param flowFraction2 normalized flow fraction at travelFraction2
     */
    public Result(ValveTrimCharacteristic trim, double rangeability, double travelFraction1, double travelFraction2,
        double flowFraction1, double flowFraction2) {
      this.trim = trim;
      this.rangeability = rangeability;
      this.travelFraction1 = travelFraction1;
      this.travelFraction2 = travelFraction2;
      this.flowFraction1 = flowFraction1;
      this.flowFraction2 = flowFraction2;
    }

    /**
     * Getter for the trim characteristic used.
     *
     * @return trim characteristic
     */
    public ValveTrimCharacteristic getTrim() {
      return trim;
    }

    /**
     * Getter for the rangeability used.
     *
     * @return rangeability (Cv_max / Cv_min)
     */
    public double getRangeability() {
      return rangeability;
    }

    /**
     * Getter for the first travel fraction.
     *
     * @return travel fraction, 0-1
     */
    public double getTravelFraction1() {
      return travelFraction1;
    }

    /**
     * Getter for the second travel fraction.
     *
     * @return travel fraction, 0-1
     */
    public double getTravelFraction2() {
      return travelFraction2;
    }

    /**
     * Getter for the normalized flow fraction at the first travel fraction.
     *
     * @return flow fraction (Cv/Cv_max), 0-1
     */
    public double getFlowFraction1() {
      return flowFraction1;
    }

    /**
     * Getter for the normalized flow fraction at the second travel fraction.
     *
     * @return flow fraction (Cv/Cv_max), 0-1
     */
    public double getFlowFraction2() {
      return flowFraction2;
    }

    /**
     * Ratio of the flow fraction at the second travel point to the flow fraction at the first travel point, i.e. how
     * many times more (or less) flow passes at travelFraction2 compared to travelFraction1. A value of 1.10 means 10 %
     * more flow.
     *
     * @return flowFraction2 / flowFraction1, or {@link Double#NaN} if flowFraction1 is zero
     */
    public double getFlowRatio() {
      if (flowFraction1 == 0.0) {
        return Double.NaN;
      }
      return flowFraction2 / flowFraction1;
    }

    /**
     * Extra flow at the second travel point relative to the first, as a fraction (0.10 = +10 %).
     *
     * @return getFlowRatio() - 1.0
     */
    public double getExtraFlowFraction() {
      return getFlowRatio() - 1.0;
    }
  }

  /**
   * Normalized flow fraction (Cv/Cv_max) for the given trim characteristic at the given travel fraction and
   * rangeability.
   *
   * @param trim trim characteristic
   * @param travelFraction travel fraction, 0-1 (values are clamped to this range)
   * @param rangeability rangeability (Cv_max / Cv_min), must be greater than 1
   * @return normalized flow fraction, in {@code [1/rangeability, 1]} for LINEAR and EQUAL_PERCENTAGE, or {@code [0, 1]}
   * for QUICK_OPENING
   */
  public static double flowFraction(ValveTrimCharacteristic trim, double travelFraction, double rangeability) {
    double x = Math.max(0.0, Math.min(1.0, travelFraction));
    if (rangeability <= 1.0) {
      throw new IllegalArgumentException("rangeability must be greater than 1.0");
    }
    switch (trim) {
    case EQUAL_PERCENTAGE:
      return Math.pow(rangeability, x - 1.0);
    case QUICK_OPENING:
      return Math.sqrt(x);
    case LINEAR:
    default:
      double minFraction = 1.0 / rangeability;
      return minFraction + x * (1.0 - minFraction);
    }
  }

  /**
   * Evaluate the extra flow passed at a second (typically forced/clamped) travel fraction relative to a first
   * (typically desired) travel fraction, for the given ISA-75 style trim characteristic and rangeability.
   *
   * @param trim trim characteristic
   * @param travelFraction1 first (desired) travel fraction, 0-1
   * @param travelFraction2 second (actual/clamped) travel fraction, 0-1
   * @param rangeability rangeability (Cv_max / Cv_min), must be greater than 1 (typical control valves: 30-50)
   * @return a {@link Result} carrying both flow fractions and the resulting flow ratio
   */
  public static Result evaluate(ValveTrimCharacteristic trim, double travelFraction1, double travelFraction2,
      double rangeability) {
    double f1 = flowFraction(trim, travelFraction1, rangeability);
    double f2 = flowFraction(trim, travelFraction2, rangeability);
    logger.debug("Valve rangeability screening: trim={} travel1={} travel2={} R={} f1={} f2={}", trim, travelFraction1,
        travelFraction2, rangeability, f1, f2);
    return new Result(trim, rangeability, travelFraction1, travelFraction2, f1, f2);
  }
}
