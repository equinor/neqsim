package neqsim.process.safety.reaction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.reaction.RunawayReactionAnalyzer.RunawayVerdict;

/**
 * Immutable result of a runaway (exothermic) reaction excursion screening.
 *
 * <p>
 * Captures the adiabatic temperature rise, the maximum temperature of the synthesis reaction (MTSR), the margin to a
 * supplied maximum-allowable temperature, an optional adiabatic time-to-maximum-rate (TMR), and a screening verdict.
 * The result is a <b>screening</b> artefact intended to flag scenarios that require a full reaction-calorimetry study
 * and DIERS two-phase relief design; it is not a finished thermal-hazard assessment.
 *
 * @author ESOL
 * @version 1.0
 */
public class RunawayReactionResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double processTemperatureK;
  private final double adiabaticTemperatureRiseK;
  private final double mtsrK;
  private final double maxAllowableTemperatureK;
  private final double temperatureMarginK;
  private final boolean maxAllowableProvided;
  private final boolean marginExceeded;
  private final double totalHeatReleaseJ;
  private final double totalMassKg;
  private final double specificHeatJPerKgK;
  private final boolean timeToMaxRateProvided;
  private final double timeToMaxRateS;
  private final boolean twoPhaseReliefScreeningRequired;
  private final RunawayVerdict verdict;
  private final List<String> warnings;

  /**
   * Construct an immutable runaway-reaction screening result.
   *
   * @param processTemperatureK initial process temperature [K]
   * @param adiabaticTemperatureRiseK adiabatic temperature rise dT_ad [K]
   * @param mtsrK maximum temperature of the synthesis reaction (adiabatic) [K]
   * @param maxAllowableTemperatureK maximum-allowable temperature (decomposition onset or material limit) [K], NaN if
   * not supplied
   * @param temperatureMarginK margin = MAT - MTSR [K], NaN if no MAT supplied
   * @param maxAllowableProvided true if a maximum-allowable temperature was supplied
   * @param marginExceeded true if MTSR exceeds the maximum-allowable temperature
   * @param totalHeatReleaseJ total adiabatic heat release [J]
   * @param totalMassKg total reacting mass [kg]
   * @param specificHeatJPerKgK specific heat used [J/(kg·K)]
   * @param timeToMaxRateProvided true if an adiabatic TMR was computed
   * @param timeToMaxRateS adiabatic time-to-maximum-rate [s], NaN if not computable
   * @param twoPhaseReliefScreeningRequired true if DIERS two-phase relief screening is recommended
   * @param verdict the screening verdict
   * @param warnings non-fatal warnings raised during analysis
   */
  public RunawayReactionResult(double processTemperatureK, double adiabaticTemperatureRiseK, double mtsrK,
      double maxAllowableTemperatureK, double temperatureMarginK, boolean maxAllowableProvided, boolean marginExceeded,
      double totalHeatReleaseJ, double totalMassKg, double specificHeatJPerKgK, boolean timeToMaxRateProvided,
      double timeToMaxRateS, boolean twoPhaseReliefScreeningRequired, RunawayVerdict verdict, List<String> warnings) {
    this.processTemperatureK = processTemperatureK;
    this.adiabaticTemperatureRiseK = adiabaticTemperatureRiseK;
    this.mtsrK = mtsrK;
    this.maxAllowableTemperatureK = maxAllowableTemperatureK;
    this.temperatureMarginK = temperatureMarginK;
    this.maxAllowableProvided = maxAllowableProvided;
    this.marginExceeded = marginExceeded;
    this.totalHeatReleaseJ = totalHeatReleaseJ;
    this.totalMassKg = totalMassKg;
    this.specificHeatJPerKgK = specificHeatJPerKgK;
    this.timeToMaxRateProvided = timeToMaxRateProvided;
    this.timeToMaxRateS = timeToMaxRateS;
    this.twoPhaseReliefScreeningRequired = twoPhaseReliefScreeningRequired;
    this.verdict = verdict;
    this.warnings = warnings == null ? new ArrayList<String>() : new ArrayList<String>(warnings);
  }

  /**
   * @return initial process temperature [K]
   */
  public double getProcessTemperatureK() {
    return processTemperatureK;
  }

  /**
   * @return adiabatic temperature rise dT_ad [K]
   */
  public double getAdiabaticTemperatureRiseK() {
    return adiabaticTemperatureRiseK;
  }

  /**
   * @return maximum temperature of the synthesis reaction (adiabatic) [K]
   */
  public double getMtsrK() {
    return mtsrK;
  }

  /**
   * @return maximum-allowable temperature [K], NaN if not supplied
   */
  public double getMaxAllowableTemperatureK() {
    return maxAllowableTemperatureK;
  }

  /**
   * @return margin = MAT - MTSR [K], NaN if no MAT supplied
   */
  public double getTemperatureMarginK() {
    return temperatureMarginK;
  }

  /**
   * @return true if a maximum-allowable temperature was supplied
   */
  public boolean isMaxAllowableProvided() {
    return maxAllowableProvided;
  }

  /**
   * @return true if MTSR exceeds the maximum-allowable temperature
   */
  public boolean isMarginExceeded() {
    return marginExceeded;
  }

  /**
   * @return total adiabatic heat release [J]
   */
  public double getTotalHeatReleaseJ() {
    return totalHeatReleaseJ;
  }

  /**
   * @return total reacting mass [kg]
   */
  public double getTotalMassKg() {
    return totalMassKg;
  }

  /**
   * @return specific heat used [J/(kg·K)]
   */
  public double getSpecificHeatJPerKgK() {
    return specificHeatJPerKgK;
  }

  /**
   * @return true if an adiabatic time-to-maximum-rate was computed
   */
  public boolean isTimeToMaxRateProvided() {
    return timeToMaxRateProvided;
  }

  /**
   * @return adiabatic time-to-maximum-rate [s], NaN if not computable
   */
  public double getTimeToMaxRateS() {
    return timeToMaxRateS;
  }

  /**
   * @return true if DIERS two-phase relief screening is recommended
   */
  public boolean isTwoPhaseReliefScreeningRequired() {
    return twoPhaseReliefScreeningRequired;
  }

  /**
   * @return the screening verdict
   */
  public RunawayVerdict getVerdict() {
    return verdict;
  }

  /**
   * @return non-fatal warnings raised during analysis
   */
  public List<String> getWarnings() {
    return new ArrayList<String>(warnings);
  }

  /**
   * Serialise this result to pretty-printed JSON. Special floating-point values (NaN for an absent margin or TMR) are
   * permitted so that a result without a temperature rating still serialises.
   *
   * @return JSON representation of this result
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
