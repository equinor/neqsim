package neqsim.process.equipment.watertreatment;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Deterministic oil-in-water analyzer drift and bias model.
 *
 * <p>
 * The model represents zero offset, span bias, drift rates, and a conservative uncertainty margin. It is intended for
 * data-quality screening, corrected OIW estimates, and warning calculations for offshore produced-water analyzers.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class OilInWaterAnalyzerDriftModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Analyzer zero offset at calibration in mg/L. */
  private double zeroOffsetMgL = 0.0;

  /** Analyzer span factor at calibration. */
  private double spanFactor = 1.0;

  /** Zero drift rate in mg/L per day. */
  private double zeroDriftMgLPerDay = 0.0;

  /** Span drift rate as fraction per day. */
  private double spanDriftFractionPerDay = 0.0;

  /** One-standard-deviation analyzer noise in mg/L. */
  private double noiseStandardDeviationMgL = 0.0;

  /** Recommended calibration interval in days. */
  private double calibrationIntervalDays = 30.0;

  /** Last measured OIW value in mg/L. */
  private double lastMeasuredOilInWaterMgL = Double.NaN;

  /**
   * Creates an analyzer drift model with no bias or drift.
   */
  public OilInWaterAnalyzerDriftModel() {
  }

  /**
   * Calculates the expected analyzer reading for a true OIW concentration.
   *
   * @param trueOilInWaterMgL true OIW concentration in mg/L
   * @param daysSinceCalibration days since analyzer calibration
   * @return expected analyzer reading in mg/L
   */
  public double measure(double trueOilInWaterMgL, double daysSinceCalibration) {
    double safeDays = Math.max(0.0, daysSinceCalibration);
    double effectiveSpan = spanFactor + spanDriftFractionPerDay * safeDays;
    double effectiveOffset = zeroOffsetMgL + zeroDriftMgLPerDay * safeDays;
    lastMeasuredOilInWaterMgL = Math.max(0.0, trueOilInWaterMgL * effectiveSpan + effectiveOffset);
    return lastMeasuredOilInWaterMgL;
  }

  /**
   * Calculates a conservative analyzer reading including a deterministic uncertainty margin.
   *
   * @param trueOilInWaterMgL true OIW concentration in mg/L
   * @param daysSinceCalibration days since analyzer calibration
   * @param confidenceMultiplier multiplier on noise standard deviation
   * @return conservative analyzer reading in mg/L
   */
  public double measureConservative(double trueOilInWaterMgL, double daysSinceCalibration,
      double confidenceMultiplier) {
    return measure(trueOilInWaterMgL, daysSinceCalibration)
	+ Math.max(0.0, confidenceMultiplier) * noiseStandardDeviationMgL;
  }

  /**
   * Corrects a measured OIW value back to estimated true OIW.
   *
   * @param measuredOilInWaterMgL measured OIW concentration in mg/L
   * @param daysSinceCalibration days since analyzer calibration
   * @return corrected OIW concentration in mg/L
   */
  public double correctMeasuredValue(double measuredOilInWaterMgL, double daysSinceCalibration) {
    double safeDays = Math.max(0.0, daysSinceCalibration);
    double effectiveSpan = spanFactor + spanDriftFractionPerDay * safeDays;
    double effectiveOffset = zeroOffsetMgL + zeroDriftMgLPerDay * safeDays;
    if (Math.abs(effectiveSpan) < 1.0e-12) {
      return Math.max(0.0, measuredOilInWaterMgL);
    }
    return Math.max(0.0, (measuredOilInWaterMgL - effectiveOffset) / effectiveSpan);
  }

  /**
   * Updates zero and span factors from a two-point field calibration.
   *
   * @param measuredZeroMgL analyzer reading for a zero reference in mg/L
   * @param measuredSpanMgL analyzer reading for a span reference in mg/L
   * @param referenceSpanMgL true span reference concentration in mg/L
   * @throws IllegalArgumentException if referenceSpanMgL is not positive
   */
  public void calibrate(double measuredZeroMgL, double measuredSpanMgL, double referenceSpanMgL) {
    if (referenceSpanMgL <= 0.0) {
      throw new IllegalArgumentException("Reference span must be positive");
    }
    zeroOffsetMgL = measuredZeroMgL;
    spanFactor = (measuredSpanMgL - measuredZeroMgL) / referenceSpanMgL;
  }

  /**
   * Checks if the analyzer is beyond the recommended calibration interval.
   *
   * @param daysSinceCalibration days since analyzer calibration
   * @return true if calibration is due
   */
  public boolean isCalibrationDue(double daysSinceCalibration) {
    return daysSinceCalibration >= calibrationIntervalDays;
  }

  /**
   * Serializes the analyzer model state to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("zeroOffsetMgL", zeroOffsetMgL);
    data.put("spanFactor", spanFactor);
    data.put("zeroDriftMgLPerDay", zeroDriftMgLPerDay);
    data.put("spanDriftFractionPerDay", spanDriftFractionPerDay);
    data.put("noiseStandardDeviationMgL", noiseStandardDeviationMgL);
    data.put("calibrationIntervalDays", calibrationIntervalDays);
    data.put("lastMeasuredOilInWaterMgL", lastMeasuredOilInWaterMgL);
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(data);
  }

  /**
   * Sets zero offset.
   *
   * @param zeroOffsetMgL zero offset in mg/L
   */
  public void setZeroOffsetMgL(double zeroOffsetMgL) {
    this.zeroOffsetMgL = zeroOffsetMgL;
  }

  /**
   * Gets zero offset.
   *
   * @return zero offset in mg/L
   */
  public double getZeroOffsetMgL() {
    return zeroOffsetMgL;
  }

  /**
   * Sets span factor.
   *
   * @param spanFactor span factor
   */
  public void setSpanFactor(double spanFactor) {
    this.spanFactor = spanFactor;
  }

  /**
   * Gets span factor.
   *
   * @return span factor
   */
  public double getSpanFactor() {
    return spanFactor;
  }

  /**
   * Sets zero drift rate.
   *
   * @param zeroDriftMgLPerDay zero drift in mg/L per day
   */
  public void setZeroDriftMgLPerDay(double zeroDriftMgLPerDay) {
    this.zeroDriftMgLPerDay = zeroDriftMgLPerDay;
  }

  /**
   * Gets zero drift rate.
   *
   * @return zero drift in mg/L per day
   */
  public double getZeroDriftMgLPerDay() {
    return zeroDriftMgLPerDay;
  }

  /**
   * Sets span drift rate.
   *
   * @param spanDriftFractionPerDay span drift fraction per day
   */
  public void setSpanDriftFractionPerDay(double spanDriftFractionPerDay) {
    this.spanDriftFractionPerDay = spanDriftFractionPerDay;
  }

  /**
   * Gets span drift rate.
   *
   * @return span drift fraction per day
   */
  public double getSpanDriftFractionPerDay() {
    return spanDriftFractionPerDay;
  }

  /**
   * Sets analyzer noise standard deviation.
   *
   * @param noiseStandardDeviationMgL noise standard deviation in mg/L
   */
  public void setNoiseStandardDeviationMgL(double noiseStandardDeviationMgL) {
    this.noiseStandardDeviationMgL = Math.max(0.0, noiseStandardDeviationMgL);
  }

  /**
   * Gets analyzer noise standard deviation.
   *
   * @return noise standard deviation in mg/L
   */
  public double getNoiseStandardDeviationMgL() {
    return noiseStandardDeviationMgL;
  }

  /**
   * Sets calibration interval.
   *
   * @param calibrationIntervalDays calibration interval in days
   */
  public void setCalibrationIntervalDays(double calibrationIntervalDays) {
    this.calibrationIntervalDays = Math.max(0.0, calibrationIntervalDays);
  }

  /**
   * Gets calibration interval.
   *
   * @return calibration interval in days
   */
  public double getCalibrationIntervalDays() {
    return calibrationIntervalDays;
  }

  /**
   * Gets last measured OIW value.
   *
   * @return last measured OIW in mg/L
   */
  public double getLastMeasuredOilInWaterMgL() {
    return lastMeasuredOilInWaterMgL;
  }
}
