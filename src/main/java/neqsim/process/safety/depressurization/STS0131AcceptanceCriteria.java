package neqsim.process.safety.depressurization;

import java.io.Serializable;

/**
 * Acceptance criteria for STS0131-style fire escalation screening of blowdown results.
 *
 * <p>
 * The criteria are evaluated against the time series produced by
 * {@link DepressurizationSimulator.DepressurizationResult}. The class is intentionally simple:
 * project-specific rupture calculations, fire loads, and escape analyses provide the acceptance
 * thresholds; this class performs a traceable comparison of pressure, remaining mass, and peak
 * discharge rate at the limiting time.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class STS0131AcceptanceCriteria implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Personnel escape time in seconds. */
  private double timeToEscapeS = Double.NaN;
  /** Estimated time to rupture in seconds for the exposed item. */
  private double estimatedTimeToRuptureS = Double.NaN;
  /** Maximum acceptable pressure at the limiting time in bara. */
  private double maximumPressureAtRuptureBara = Double.NaN;
  /** Maximum acceptable remaining inventory at the limiting time in kg. */
  private double maximumRemainingMassKg = Double.NaN;
  /** Maximum acceptable escalated fire rate in kg/s. */
  private double maximumEscalatedFireRateKgPerS = Double.NaN;

  /**
   * Sets the personnel escape time used as one possible limiting time.
   *
   * @param timeToEscapeS time to escape in s; use a positive finite value
   * @return this criteria object for chaining
   */
  public STS0131AcceptanceCriteria setTimeToEscapeS(double timeToEscapeS) {
    this.timeToEscapeS = timeToEscapeS;
    return this;
  }

  /**
   * Sets the estimated time to rupture for the fire-exposed item.
   *
   * @param estimatedTimeToRuptureS estimated time to rupture in s; use a positive finite value
   * @return this criteria object for chaining
   */
  public STS0131AcceptanceCriteria setEstimatedTimeToRuptureS(double estimatedTimeToRuptureS) {
    this.estimatedTimeToRuptureS = estimatedTimeToRuptureS;
    return this;
  }

  /**
   * Sets the maximum acceptable pressure at the limiting time.
   *
   * @param maximumPressureAtRuptureBara maximum pressure in bara
   * @return this criteria object for chaining
   */
  public STS0131AcceptanceCriteria setMaximumPressureAtRuptureBara(
      double maximumPressureAtRuptureBara) {
    this.maximumPressureAtRuptureBara = maximumPressureAtRuptureBara;
    return this;
  }

  /**
   * Sets the maximum acceptable remaining inventory at the limiting time.
   *
   * @param maximumRemainingMassKg maximum remaining inventory in kg
   * @return this criteria object for chaining
   */
  public STS0131AcceptanceCriteria setMaximumRemainingMassKg(double maximumRemainingMassKg) {
    this.maximumRemainingMassKg = maximumRemainingMassKg;
    return this;
  }

  /**
   * Sets the maximum acceptable escalated fire rate represented by the discharge rate.
   *
   * @param maximumEscalatedFireRateKgPerS maximum discharge or escalated fire rate in kg/s
   * @return this criteria object for chaining
   */
  public STS0131AcceptanceCriteria setMaximumEscalatedFireRateKgPerS(
      double maximumEscalatedFireRateKgPerS) {
    this.maximumEscalatedFireRateKgPerS = maximumEscalatedFireRateKgPerS;
    return this;
  }

  /**
   * Gets the configured personnel escape time.
   *
   * @return escape time in s, or NaN when not configured
   */
  public double getTimeToEscapeS() {
    return timeToEscapeS;
  }

  /**
   * Gets the configured estimated time to rupture.
   *
   * @return estimated time to rupture in s, or NaN when not configured
   */
  public double getEstimatedTimeToRuptureS() {
    return estimatedTimeToRuptureS;
  }

  /**
   * Gets the configured maximum pressure at the limiting time.
   *
   * @return maximum pressure in bara, or NaN when not configured
   */
  public double getMaximumPressureAtRuptureBara() {
    return maximumPressureAtRuptureBara;
  }

  /**
   * Gets the configured maximum remaining mass.
   *
   * @return maximum remaining mass in kg, or NaN when not configured
   */
  public double getMaximumRemainingMassKg() {
    return maximumRemainingMassKg;
  }

  /**
   * Gets the configured maximum escalated fire rate.
   *
   * @return maximum escalated fire rate in kg/s, or NaN when not configured
   */
  public double getMaximumEscalatedFireRateKgPerS() {
    return maximumEscalatedFireRateKgPerS;
  }

  /**
   * Evaluates criteria against a depressurization result.
   *
   * @param result depressurization time-series result
   * @return STS0131 acceptance result
   * @throws IllegalArgumentException if {@code result} is null or has no time samples
   */
  public STS0131AcceptanceResult evaluate(
      DepressurizationSimulator.DepressurizationResult result) {
    if (result == null) {
      throw new IllegalArgumentException("result must not be null");
    }
    if (result.time.isEmpty()) {
      throw new IllegalArgumentException("result must contain at least one time sample");
    }

    double limitingTime = getLimitingTime(result);
    double pressureBara = interpolate(result.time, result.pressureBara, limitingTime);
    double remainingMassKg = interpolate(result.time, result.massKg, limitingTime);
    double peakDischargeRateKgPerS = maxUpToTime(result, limitingTime);

    boolean pressureConfigured = Double.isFinite(maximumPressureAtRuptureBara);
    boolean massConfigured = Double.isFinite(maximumRemainingMassKg);
    boolean fireRateConfigured = Double.isFinite(maximumEscalatedFireRateKgPerS);

    boolean pressureCriterionMet = !pressureConfigured || pressureBara <= maximumPressureAtRuptureBara;
    boolean massCriterionMet = !massConfigured || remainingMassKg <= maximumRemainingMassKg;
    boolean fireRateCriterionMet = !fireRateConfigured
        || peakDischargeRateKgPerS <= maximumEscalatedFireRateKgPerS;
    boolean acceptable = pressureCriterionMet && massCriterionMet && fireRateCriterionMet;

    return new STS0131AcceptanceResult(limitingTime, pressureBara, remainingMassKg,
        peakDischargeRateKgPerS, pressureConfigured, pressureCriterionMet, massConfigured,
        massCriterionMet, fireRateConfigured, fireRateCriterionMet, acceptable);
  }

  /**
   * Gets the earliest configured limiting time bounded by the result duration.
   *
   * @param result depressurization result
   * @return limiting time in s
   */
  private double getLimitingTime(DepressurizationSimulator.DepressurizationResult result) {
    double limitingTime = result.time.get(result.time.size() - 1);
    if (Double.isFinite(timeToEscapeS) && timeToEscapeS > 0.0) {
      limitingTime = Math.min(limitingTime, timeToEscapeS);
    }
    if (Double.isFinite(estimatedTimeToRuptureS) && estimatedTimeToRuptureS > 0.0) {
      limitingTime = Math.min(limitingTime, estimatedTimeToRuptureS);
    }
    return Math.max(0.0, limitingTime);
  }

  /**
   * Interpolates a time-series value.
   *
   * @param time time vector in s
   * @param values value vector
   * @param targetTime target time in s
   * @return interpolated value, or the nearest endpoint value outside the vector range
   */
  private double interpolate(java.util.List<Double> time, java.util.List<Double> values,
      double targetTime) {
    if (time.isEmpty()) {
      return Double.NaN;
    }
    if (targetTime <= time.get(0)) {
      return values.get(0);
    }
    for (int i = 1; i < time.size(); i++) {
      double upperTime = time.get(i);
      if (targetTime <= upperTime) {
        double lowerTime = time.get(i - 1);
        double lowerValue = values.get(i - 1);
        double upperValue = values.get(i);
        double fraction = upperTime > lowerTime ? (targetTime - lowerTime) / (upperTime - lowerTime)
            : 0.0;
        return lowerValue + fraction * (upperValue - lowerValue);
      }
    }
    return values.get(values.size() - 1);
  }

  /**
   * Finds the maximum discharge rate up to the limiting time.
   *
   * @param result depressurization result
   * @param limitingTime limiting time in s
   * @return maximum discharge rate in kg/s
   */
  private double maxUpToTime(DepressurizationSimulator.DepressurizationResult result,
      double limitingTime) {
    double maxRate = 0.0;
    for (int i = 0; i < result.time.size(); i++) {
      if (result.time.get(i) <= limitingTime) {
        maxRate = Math.max(maxRate, result.massFlowKgPerS.get(i));
      }
    }
    return maxRate;
  }
}