package neqsim.process.safety.depressurization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result from evaluating a depressurization run against STS0131 acceptance criteria.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class STS0131AcceptanceResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Limiting evaluation time in seconds. */
  private final double limitingTimeS;
  /** Pressure at limiting time in bara. */
  private final double pressureAtLimitingTimeBara;
  /** Remaining mass at limiting time in kg. */
  private final double remainingMassAtLimitingTimeKg;
  /** Peak discharge rate up to limiting time in kg/s. */
  private final double peakDischargeRateKgPerS;
  /** True when pressure criterion was configured. */
  private final boolean pressureCriterionConfigured;
  /** True when pressure criterion was met. */
  private final boolean pressureCriterionMet;
  /** True when remaining mass criterion was configured. */
  private final boolean massCriterionConfigured;
  /** True when remaining mass criterion was met. */
  private final boolean massCriterionMet;
  /** True when escalated fire rate criterion was configured. */
  private final boolean fireRateCriterionConfigured;
  /** True when escalated fire rate criterion was met. */
  private final boolean fireRateCriterionMet;
  /** True when all configured criteria were met. */
  private final boolean acceptable;

  /**
   * Creates an STS0131 acceptance result.
   *
   * @param limitingTimeS limiting evaluation time in s
   * @param pressureAtLimitingTimeBara pressure at limiting time in bara
   * @param remainingMassAtLimitingTimeKg remaining mass at limiting time in kg
   * @param peakDischargeRateKgPerS peak discharge rate up to limiting time in kg/s
   * @param pressureCriterionConfigured true if the pressure criterion was configured
   * @param pressureCriterionMet true if the pressure criterion was met
   * @param massCriterionConfigured true if the mass criterion was configured
   * @param massCriterionMet true if the remaining mass criterion was met
   * @param fireRateCriterionConfigured true if the fire-rate criterion was configured
   * @param fireRateCriterionMet true if the fire-rate criterion was met
   * @param acceptable true if all configured criteria were met
   */
  public STS0131AcceptanceResult(double limitingTimeS, double pressureAtLimitingTimeBara,
      double remainingMassAtLimitingTimeKg, double peakDischargeRateKgPerS,
      boolean pressureCriterionConfigured, boolean pressureCriterionMet,
      boolean massCriterionConfigured, boolean massCriterionMet,
      boolean fireRateCriterionConfigured, boolean fireRateCriterionMet, boolean acceptable) {
    this.limitingTimeS = limitingTimeS;
    this.pressureAtLimitingTimeBara = pressureAtLimitingTimeBara;
    this.remainingMassAtLimitingTimeKg = remainingMassAtLimitingTimeKg;
    this.peakDischargeRateKgPerS = peakDischargeRateKgPerS;
    this.pressureCriterionConfigured = pressureCriterionConfigured;
    this.pressureCriterionMet = pressureCriterionMet;
    this.massCriterionConfigured = massCriterionConfigured;
    this.massCriterionMet = massCriterionMet;
    this.fireRateCriterionConfigured = fireRateCriterionConfigured;
    this.fireRateCriterionMet = fireRateCriterionMet;
    this.acceptable = acceptable;
  }

  /**
   * Gets the limiting evaluation time.
   *
   * @return limiting evaluation time in s
   */
  public double getLimitingTimeS() {
    return limitingTimeS;
  }

  /**
   * Gets pressure at the limiting time.
   *
   * @return pressure in bara
   */
  public double getPressureAtLimitingTimeBara() {
    return pressureAtLimitingTimeBara;
  }

  /**
   * Gets remaining inventory at the limiting time.
   *
   * @return remaining inventory in kg
   */
  public double getRemainingMassAtLimitingTimeKg() {
    return remainingMassAtLimitingTimeKg;
  }

  /**
   * Gets peak discharge rate up to the limiting time.
   *
   * @return peak discharge rate in kg/s
   */
  public double getPeakDischargeRateKgPerS() {
    return peakDischargeRateKgPerS;
  }

  /**
   * Checks if the pressure criterion was configured.
   *
   * @return true if the pressure criterion was configured
   */
  public boolean isPressureCriterionConfigured() {
    return pressureCriterionConfigured;
  }

  /**
   * Checks if the pressure criterion was met.
   *
   * @return true if pressure was acceptable at the limiting time
   */
  public boolean isPressureCriterionMet() {
    return pressureCriterionMet;
  }

  /**
   * Checks if the remaining mass criterion was configured.
   *
   * @return true if the mass criterion was configured
   */
  public boolean isMassCriterionConfigured() {
    return massCriterionConfigured;
  }

  /**
   * Checks if the remaining mass criterion was met.
   *
   * @return true if remaining mass was acceptable at the limiting time
   */
  public boolean isMassCriterionMet() {
    return massCriterionMet;
  }

  /**
   * Checks if the escalated fire-rate criterion was configured.
   *
   * @return true if the fire-rate criterion was configured
   */
  public boolean isFireRateCriterionConfigured() {
    return fireRateCriterionConfigured;
  }

  /**
   * Checks if the escalated fire-rate criterion was met.
   *
   * @return true if the fire-rate criterion was met
   */
  public boolean isFireRateCriterionMet() {
    return fireRateCriterionMet;
  }

  /**
   * Checks if all configured criteria were met.
   *
   * @return true if the scenario is acceptable against the configured criteria
   */
  public boolean isAcceptable() {
    return acceptable;
  }

  /**
   * Convert the result to a map for report generation.
   *
   * @return ordered map of result fields
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("standard", "STS0131");
    map.put("limitingTimeS", limitingTimeS);
    map.put("pressureAtLimitingTimeBara", pressureAtLimitingTimeBara);
    map.put("remainingMassAtLimitingTimeKg", remainingMassAtLimitingTimeKg);
    map.put("peakDischargeRateKgPerS", peakDischargeRateKgPerS);
    map.put("pressureCriterionConfigured", pressureCriterionConfigured);
    map.put("pressureCriterionMet", pressureCriterionMet);
    map.put("massCriterionConfigured", massCriterionConfigured);
    map.put("massCriterionMet", massCriterionMet);
    map.put("fireRateCriterionConfigured", fireRateCriterionConfigured);
    map.put("fireRateCriterionMet", fireRateCriterionMet);
    map.put("acceptable", acceptable);
    return map;
  }

  /**
   * Convert the result to pretty-printed JSON.
   *
   * @return JSON representation of the result
   */
  public String toJson() {
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting()
        .create();
    return gson.toJson(toMap());
  }
}