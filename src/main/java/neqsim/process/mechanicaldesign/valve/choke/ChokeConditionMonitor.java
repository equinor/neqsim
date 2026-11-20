package neqsim.process.mechanicaldesign.valve.choke;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import neqsim.process.mechanicaldesign.valve.ValveCharacteristic;

/**
 * Condition monitoring of a choke by comparing the observed flow coefficient with the calibrated theoretical Cv at the
 * actual opening.
 *
 * <p>
 * Erosion of the trim opens the flow area, so the observed Cv rises above the theoretical curve. Plugging or deposits
 * lower it. A choke that is commanded closed but still passes flow, or whose Cv does not follow an opening change, has
 * a leaking seat or a stuck or disconnected stem. The observed Cv comes from measured flow and pressure drop, see
 * {@link ChokeFlowCoefficient#observedCv(double, double, double)}; the theoretical Cv comes from a vendor curve or from
 * a characteristic calibrated on a healthy choke.
 * </p>
 *
 * <p>
 * The same ratio gives the flow capacity of the real choke relative to its design: where the flow of a collapsed choke
 * dimensions the pressure-protection capacity, scale the design collapse flow with
 * {@link Assessment#getCapacityRatio()} and keep the deviation inside the accepted limit. The default tolerances are
 * screening values and should be replaced by the acceptance criteria of the asset.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ChokeConditionMonitor implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Default accepted relative Cv deviation. */
  public static final double DEFAULT_DEVIATION_TOLERANCE = 0.10;
  /** Default accepted closed-position Cv as a fraction of maximum Cv. */
  public static final double DEFAULT_CLOSED_LEAK_FRACTION = 0.005;
  /** Default opening change that must show up as a Cv change [percentage points]. */
  public static final double DEFAULT_MIN_OPENING_STEP = 5.0;
  /** Default fraction of the expected Cv change that counts as a response. */
  public static final double DEFAULT_MIN_RESPONSE_FRACTION = 0.2;

  /** Observed condition of the choke. */
  public enum Condition {
    /** Observed Cv agrees with the theoretical Cv. */
    NORMAL,
    /** Observed Cv is above the theoretical Cv; erosion or a bypass is suspected. */
    EROSION_SUSPECTED,
    /** Observed Cv is below the theoretical Cv; plugging, deposits or a restricted trim is suspected. */
    PLUGGING_SUSPECTED,
    /** Flow in the closed position; seat leakage is suspected. */
    CLOSED_LEAK_SUSPECTED,
    /** Cv does not follow the opening; a stuck or disconnected stem is suspected. */
    NOT_RESPONDING,
    /** Not enough valid data. */
    INSUFFICIENT_DATA
  }

  /** Accepted relative Cv deviation. */
  private double deviationTolerance = DEFAULT_DEVIATION_TOLERANCE;
  /** Accepted closed-position Cv as a fraction of maximum Cv. */
  private double closedLeakFraction = DEFAULT_CLOSED_LEAK_FRACTION;
  /** Opening change that must show up as a Cv change [percentage points]. */
  private double minOpeningStep = DEFAULT_MIN_OPENING_STEP;
  /** Fraction of the expected Cv change that counts as a response. */
  private double minResponseFraction = DEFAULT_MIN_RESPONSE_FRACTION;

  /**
   * Sets the accepted relative deviation between observed and theoretical Cv.
   *
   * @param tolerance relative deviation, greater than zero and below one (0.10 is 10 %)
   * @return this monitor
   * @throws IllegalArgumentException if the tolerance is outside (0, 1)
   */
  public ChokeConditionMonitor setDeviationTolerance(double tolerance) {
    if (!(tolerance > 0.0 && tolerance < 1.0)) {
      throw new IllegalArgumentException("tolerance must be within (0, 1)");
    }
    this.deviationTolerance = tolerance;
    return this;
  }

  /**
   * Sets the accepted closed-position Cv.
   *
   * @param fractionOfMaximumCv accepted Cv in the closed position as a fraction of maximum Cv, within (0, 1)
   * @return this monitor
   * @throws IllegalArgumentException if the fraction is outside (0, 1)
   */
  public ChokeConditionMonitor setClosedLeakFraction(double fractionOfMaximumCv) {
    if (!(fractionOfMaximumCv > 0.0 && fractionOfMaximumCv < 1.0)) {
      throw new IllegalArgumentException("fractionOfMaximumCv must be within (0, 1)");
    }
    this.closedLeakFraction = fractionOfMaximumCv;
    return this;
  }

  /**
   * Sets the response test used on opening series.
   *
   * @param minOpeningStepPercent opening range that must produce a Cv response [percentage points], positive
   * @param minResponseFraction fraction of the expected Cv change that counts as a response, within (0, 1]
   * @return this monitor
   * @throws IllegalArgumentException if an argument is outside its range
   */
  public ChokeConditionMonitor setResponseTest(double minOpeningStepPercent, double minResponseFraction) {
    if (!(minOpeningStepPercent > 0.0) || !(minResponseFraction > 0.0 && minResponseFraction <= 1.0)) {
      throw new IllegalArgumentException("step must be positive and response fraction within (0, 1]");
    }
    this.minOpeningStep = minOpeningStepPercent;
    this.minResponseFraction = minResponseFraction;
    return this;
  }

  /**
   * Compares one observed Cv with the theoretical Cv at the same opening.
   *
   * @param observedCv Cv from measured flow and pressure drop [US Cv], not negative
   * @param theoreticalCv calibrated theoretical Cv at the actual opening [US Cv], positive
   * @return assessment with the deviation and the capacity ratio
   * @throws IllegalArgumentException if a value is non-finite, observedCv is negative or theoreticalCv is not positive
   */
  public Assessment assessCv(double observedCv, double theoreticalCv) {
    if (!Double.isFinite(observedCv) || observedCv < 0.0 || !Double.isFinite(theoreticalCv) || theoreticalCv <= 0.0) {
      throw new IllegalArgumentException("observedCv must be not negative and theoreticalCv positive");
    }
    double ratio = observedCv / theoreticalCv;
    double deviation = ratio - 1.0;
    if (deviation > deviationTolerance) {
      return new Assessment(Condition.EROSION_SUSPECTED, deviation, ratio,
          String.format("observed Cv is %.1f %% above theory (limit %.0f %%); inspect the trim for erosion",
              100.0 * deviation, 100.0 * deviationTolerance));
    }
    if (deviation < -deviationTolerance) {
      return new Assessment(Condition.PLUGGING_SUSPECTED, deviation, ratio,
          String.format("observed Cv is %.1f %% below theory (limit %.0f %%); check for plugging or deposits",
              -100.0 * deviation, 100.0 * deviationTolerance));
    }
    return new Assessment(Condition.NORMAL, deviation, ratio, String
        .format("observed Cv is within %.0f %% of theory (%.1f %%)", 100.0 * deviationTolerance, 100.0 * deviation));
  }

  /**
   * Assesses the closed position from the flow and pressure drop measured with the choke commanded closed.
   *
   * @param observedCv Cv from measured flow and pressure drop [US Cv], not negative; zero when no flow is measured
   * @param maximumCv Cv at full opening [US Cv], positive
   * @return assessment; the capacity ratio is the observed Cv over the maximum Cv
   * @throws IllegalArgumentException if observedCv is negative or maximumCv is not positive
   */
  public Assessment assessClosedPosition(double observedCv, double maximumCv) {
    if (!Double.isFinite(observedCv) || observedCv < 0.0 || !Double.isFinite(maximumCv) || maximumCv <= 0.0) {
      throw new IllegalArgumentException("observedCv must be not negative and maximumCv positive");
    }
    double fraction = observedCv / maximumCv;
    if (fraction > closedLeakFraction) {
      return new Assessment(Condition.CLOSED_LEAK_SUSPECTED, Double.NaN, fraction,
          String.format("closed choke passes %.2f %% of maximum Cv (limit %.2f %%); inspect the seat", 100.0 * fraction,
              100.0 * closedLeakFraction));
    }
    return new Assessment(Condition.NORMAL, Double.NaN, fraction,
        String.format("closed choke passes %.2f %% of maximum Cv", 100.0 * fraction));
  }

  /**
   * Assesses a series of operating points against the trim characteristic.
   *
   * <p>
   * Points with non-finite values, a negative observed Cv or a theoretical Cv of zero are skipped. The deviation is the
   * median of the point deviations, which is robust against a few transients. If the openings span at least the
   * configured step but the observed Cv moves less than the configured fraction of the theoretical change, the choke is
   * reported as not responding before the deviation is judged.
   * </p>
   *
   * @param openingPercent opening at each point [percent]
   * @param observedCv observed Cv at each point [US Cv]
   * @param maximumCv Cv at full opening [US Cv], positive
   * @param characteristic calibrated trim characteristic
   * @return assessment of the series; the deviation and capacity ratio are medians
   * @throws IllegalArgumentException if the arrays are null or differ in length, maximumCv is not positive or the
   * characteristic is null
   */
  public Assessment assessSeries(double[] openingPercent, double[] observedCv, double maximumCv,
      ValveCharacteristic characteristic) {
    if (openingPercent == null || observedCv == null || openingPercent.length != observedCv.length) {
      throw new IllegalArgumentException("opening and observed Cv arrays must have the same length");
    }
    List<Double> openings = new ArrayList<Double>();
    List<Double> observed = new ArrayList<Double>();
    List<Double> theory = new ArrayList<Double>();
    for (int i = 0; i < openingPercent.length; i++) {
      if (!Double.isFinite(openingPercent[i]) || !Double.isFinite(observedCv[i]) || observedCv[i] < 0.0) {
        continue;
      }
      double expected = ChokeFlowCoefficient.cvAtOpening(maximumCv, openingPercent[i], characteristic);
      if (expected > 0.0) {
        openings.add(openingPercent[i]);
        observed.add(observedCv[i]);
        theory.add(expected);
      }
    }
    if (openings.size() < 2) {
      return new Assessment(Condition.INSUFFICIENT_DATA, Double.NaN, Double.NaN,
          "fewer than two valid operating points");
    }
    double minOpening = Double.POSITIVE_INFINITY;
    double maxOpening = Double.NEGATIVE_INFINITY;
    double minObserved = Double.POSITIVE_INFINITY;
    double maxObserved = Double.NEGATIVE_INFINITY;
    double minTheory = Double.POSITIVE_INFINITY;
    double maxTheory = Double.NEGATIVE_INFINITY;
    double[] ratios = new double[openings.size()];
    for (int i = 0; i < ratios.length; i++) {
      minOpening = Math.min(minOpening, openings.get(i));
      maxOpening = Math.max(maxOpening, openings.get(i));
      minObserved = Math.min(minObserved, observed.get(i));
      maxObserved = Math.max(maxObserved, observed.get(i));
      minTheory = Math.min(minTheory, theory.get(i));
      maxTheory = Math.max(maxTheory, theory.get(i));
      ratios[i] = observed.get(i) / theory.get(i);
    }
    Arrays.sort(ratios);
    int mid = ratios.length / 2;
    double medianRatio = ratios.length % 2 == 1 ? ratios[mid] : 0.5 * (ratios[mid - 1] + ratios[mid]);
    double expectedSpan = maxTheory - minTheory;
    if (maxOpening - minOpening >= minOpeningStep && expectedSpan > 0.0
        && (maxObserved - minObserved) < minResponseFraction * expectedSpan) {
      return new Assessment(Condition.NOT_RESPONDING, medianRatio - 1.0, medianRatio,
          String.format(
              "Cv moved %.1f while %.1f was expected for an opening change of %.1f points; check the stem and actuator",
              maxObserved - minObserved, expectedSpan, maxOpening - minOpening));
    }
    return assessCv(medianRatio, 1.0);
  }

  /** Result of a condition assessment. */
  public static final class Assessment implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Condition. */
    private final Condition condition;
    /** Relative deviation of observed from theoretical Cv, NaN if not applicable. */
    private final double deviation;
    /** Observed over theoretical Cv, or the closed-position Cv over maximum Cv. */
    private final double capacityRatio;
    /** Explanation with the numbers behind the condition. */
    private final String message;

    /**
     * Creates an assessment.
     *
     * @param condition condition
     * @param deviation relative deviation, NaN if not applicable
     * @param capacityRatio observed over theoretical Cv
     * @param message explanation
     */
    Assessment(Condition condition, double deviation, double capacityRatio, String message) {
      this.condition = condition;
      this.deviation = deviation;
      this.capacityRatio = capacityRatio;
      this.message = message;
    }

    /**
     * Gets the condition.
     *
     * @return condition
     */
    public Condition getCondition() {
      return condition;
    }

    /**
     * Gets the relative deviation of the observed from the theoretical Cv.
     *
     * @return (observed - theoretical) / theoretical, NaN for a closed-position or insufficient-data assessment
     */
    public double getDeviation() {
      return deviation;
    }

    /**
     * Gets the flow-capacity ratio of the real choke. Multiply the design collapse flow by this value to check the
     * pressure-protection capacity against the actual, eroded or plugged choke.
     *
     * @return observed over theoretical Cv; for a closed-position assessment the observed Cv over the maximum Cv
     */
    public double getCapacityRatio() {
      return capacityRatio;
    }

    /**
     * Gets the explanation.
     *
     * @return message with the numbers behind the condition
     */
    public String getMessage() {
      return message;
    }
  }
}
