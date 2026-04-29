package neqsim.process.maintenance;

import java.io.Serializable;
import com.google.gson.GsonBuilder;

/**
 * Decision output from a maintenance deferral assessment. Contains the recommendation (defer,
 * proceed, emergency), risk level, temporary operating constraints, and supporting rationale.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DeferralDecision implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /**
   * The recommended action from the deferral assessment.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public enum Recommendation {
    /** Maintenance can be safely deferred to the proposed date. */
    DEFER,
    /** Maintenance should proceed as originally scheduled. */
    PROCEED_AS_PLANNED,
    /** Reduced scope maintenance is recommended (interim mitigation). */
    REDUCED_SCOPE,
    /** Emergency action required — do not defer. */
    EMERGENCY
  }

  /**
   * Risk level associated with deferral.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public enum RiskLevel {
    /** Negligible risk from deferral. */
    LOW,
    /** Moderate risk, acceptable with mitigations. */
    MEDIUM,
    /** High risk, deferral not recommended without strong business case. */
    HIGH,
    /** Unacceptable risk, cannot defer. */
    VERY_HIGH
  }

  private Recommendation recommendation = Recommendation.PROCEED_AS_PLANNED;
  private RiskLevel riskLevel = RiskLevel.MEDIUM;
  private double confidenceLevel = 0.0;
  private double deferralPeriodHours = 0.0;
  private double productionLossRiskPercent = 0.0;
  private double safetyRiskScore = 0.0;
  private double economicBenefitUSD = 0.0;
  private double economicRiskUSD = 0.0;
  private String rationale = "";
  private TemporaryOperatingEnvelope operatingEnvelope = null;

  /**
   * Constructs a deferral decision with the given recommendation.
   *
   * @param recommendation the recommended action
   * @param riskLevel the assessed risk level
   */
  public DeferralDecision(Recommendation recommendation, RiskLevel riskLevel) {
    this.recommendation = recommendation;
    this.riskLevel = riskLevel;
  }

  /**
   * Gets the recommendation.
   *
   * @return the recommendation
   */
  public Recommendation getRecommendation() {
    return recommendation;
  }

  /**
   * Gets the risk level.
   *
   * @return the risk level
   */
  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  /**
   * Gets the confidence level of this assessment (0.0 to 1.0).
   *
   * @return confidence level
   */
  public double getConfidenceLevel() {
    return confidenceLevel;
  }

  /**
   * Sets the confidence level.
   *
   * @param confidenceLevel value from 0.0 to 1.0
   */
  public void setConfidenceLevel(double confidenceLevel) {
    this.confidenceLevel = confidenceLevel;
  }

  /**
   * Gets the assessed deferral period in hours.
   *
   * @return deferral period in hours
   */
  public double getDeferralPeriodHours() {
    return deferralPeriodHours;
  }

  /**
   * Sets the deferral period.
   *
   * @param hours deferral period in hours
   */
  public void setDeferralPeriodHours(double hours) {
    this.deferralPeriodHours = hours;
  }

  /**
   * Gets the estimated production loss risk as percentage.
   *
   * @return production loss risk percent
   */
  public double getProductionLossRiskPercent() {
    return productionLossRiskPercent;
  }

  /**
   * Sets the production loss risk.
   *
   * @param percent production loss risk as percentage
   */
  public void setProductionLossRiskPercent(double percent) {
    this.productionLossRiskPercent = percent;
  }

  /**
   * Gets the safety risk score (0=negligible, 10=extreme).
   *
   * @return safety risk score
   */
  public double getSafetyRiskScore() {
    return safetyRiskScore;
  }

  /**
   * Sets the safety risk score.
   *
   * @param score safety risk score (0-10 scale)
   */
  public void setSafetyRiskScore(double score) {
    this.safetyRiskScore = score;
  }

  /**
   * Gets the economic benefit of deferral in USD.
   *
   * @return economic benefit
   */
  public double getEconomicBenefitUSD() {
    return economicBenefitUSD;
  }

  /**
   * Sets the economic benefit of deferral.
   *
   * @param usd economic benefit in USD
   */
  public void setEconomicBenefitUSD(double usd) {
    this.economicBenefitUSD = usd;
  }

  /**
   * Gets the economic risk (cost of failure) in USD.
   *
   * @return economic risk
   */
  public double getEconomicRiskUSD() {
    return economicRiskUSD;
  }

  /**
   * Sets the economic risk.
   *
   * @param usd economic risk (expected cost of failure) in USD
   */
  public void setEconomicRiskUSD(double usd) {
    this.economicRiskUSD = usd;
  }

  /**
   * Gets the rationale text explaining the decision.
   *
   * @return rationale string
   */
  public String getRationale() {
    return rationale;
  }

  /**
   * Sets the rationale text.
   *
   * @param rationale explanation of the decision
   */
  public void setRationale(String rationale) {
    this.rationale = rationale;
  }

  /**
   * Gets the temporary operating envelope during deferral.
   *
   * @return operating envelope, or null if not applicable
   */
  public TemporaryOperatingEnvelope getOperatingEnvelope() {
    return operatingEnvelope;
  }

  /**
   * Sets the temporary operating envelope.
   *
   * @param envelope operating envelope constraints
   */
  public void setOperatingEnvelope(TemporaryOperatingEnvelope envelope) {
    this.operatingEnvelope = envelope;
  }

  /**
   * Checks if deferral is recommended (DEFER or REDUCED_SCOPE).
   *
   * @return true if deferral is viable
   */
  public boolean isDeferralViable() {
    return recommendation == Recommendation.DEFER || recommendation == Recommendation.REDUCED_SCOPE;
  }

  /**
   * Generates a JSON representation of the decision.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
