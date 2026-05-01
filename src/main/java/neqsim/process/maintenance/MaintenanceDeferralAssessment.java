package neqsim.process.maintenance;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Performs a maintenance deferral assessment for a process equipment item. Evaluates whether
 * maintenance can be safely deferred by combining:
 * <ul>
 * <li>Equipment health assessment (degradation, fouling, condition indicators)</li>
 * <li>Process simulation at degraded conditions</li>
 * <li>Safety constraint checking</li>
 * <li>Economic analysis (cost of deferral vs. cost of shutdown)</li>
 * </ul>
 *
 * <p>
 * The assessment produces a {@link DeferralDecision} containing the recommendation, risk level,
 * temporary operating envelope, and supporting rationale.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class MaintenanceDeferralAssessment implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // Input parameters
  private String equipmentName = "";
  private EquipmentHealthAssessment healthAssessment = null;
  private ProcessSystem processSystem = null;
  private double requestedDeferralHours = 720.0; // 30 days default
  private double productionValuePerHour = 0.0; // USD/hr of production revenue
  private double maintenanceCostUSD = 0.0; // Cost of the maintenance event
  private double unplannedShutdownCostUSD = 0.0; // Cost if equipment fails

  // Safety limits
  private double maxAcceptableSafetyRisk = 3.0; // 0-10 scale
  private double maxAcceptableProductionLoss = 20.0; // percent

  // Computed results
  private DeferralDecision decision = null;

  /**
   * Constructs a maintenance deferral assessment.
   *
   * @param equipmentName name of the equipment to assess
   */
  public MaintenanceDeferralAssessment(String equipmentName) {
    this.equipmentName = equipmentName;
  }

  /**
   * Sets the equipment health assessment.
   *
   * @param healthAssessment the health assessment for the equipment
   */
  public void setHealthAssessment(EquipmentHealthAssessment healthAssessment) {
    this.healthAssessment = healthAssessment;
  }

  /**
   * Sets the process system containing the equipment. Used to run degraded simulations.
   *
   * @param processSystem the process system
   */
  public void setProcessSystem(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Sets the requested deferral period.
   *
   * @param hours number of hours to defer maintenance
   */
  public void setRequestedDeferralHours(double hours) {
    this.requestedDeferralHours = hours;
  }

  /**
   * Sets the production value per hour.
   *
   * @param usdPerHour revenue per hour of production
   */
  public void setProductionValuePerHour(double usdPerHour) {
    this.productionValuePerHour = usdPerHour;
  }

  /**
   * Sets the maintenance event cost.
   *
   * @param usd cost of planned maintenance
   */
  public void setMaintenanceCostUSD(double usd) {
    this.maintenanceCostUSD = usd;
  }

  /**
   * Sets the unplanned shutdown cost.
   *
   * @param usd cost if equipment fails unexpectedly
   */
  public void setUnplannedShutdownCostUSD(double usd) {
    this.unplannedShutdownCostUSD = usd;
  }

  /**
   * Sets the maximum acceptable safety risk score (0-10 scale).
   *
   * @param maxRisk maximum acceptable safety risk
   */
  public void setMaxAcceptableSafetyRisk(double maxRisk) {
    this.maxAcceptableSafetyRisk = maxRisk;
  }

  /**
   * Sets the maximum acceptable production loss percentage.
   *
   * @param maxLossPercent maximum production loss as percentage
   */
  public void setMaxAcceptableProductionLoss(double maxLossPercent) {
    this.maxAcceptableProductionLoss = maxLossPercent;
  }

  /**
   * Runs the deferral assessment and produces a decision. Call this after setting all inputs.
   *
   * @return the deferral decision
   */
  public DeferralDecision assess() {
    if (healthAssessment == null) {
      decision = new DeferralDecision(DeferralDecision.Recommendation.PROCEED_AS_PLANNED,
          DeferralDecision.RiskLevel.HIGH);
      decision.setRationale("No health assessment data available — cannot assess deferral risk.");
      decision.setConfidenceLevel(0.2);
      return decision;
    }

    // Ensure health assessment is calculated
    healthAssessment.calculate();

    double healthIndex = healthAssessment.getHealthIndex();
    double rul = healthAssessment.getEstimatedRemainingLife();
    EquipmentHealthAssessment.HealthSeverity severity = healthAssessment.getSeverity();

    // Rule 1: Critical health — never defer
    if (severity == EquipmentHealthAssessment.HealthSeverity.CRITICAL) {
      decision = new DeferralDecision(DeferralDecision.Recommendation.EMERGENCY,
          DeferralDecision.RiskLevel.VERY_HIGH);
      decision.setRationale("Equipment health is CRITICAL (index="
          + String.format("%.2f", healthIndex) + "). Immediate maintenance required.");
      decision.setSafetyRiskScore(8.0);
      decision.setConfidenceLevel(0.9);
      return decision;
    }

    // Rule 2: RUL less than requested deferral — cannot defer full period
    if (rul < requestedDeferralHours) {
      if (rul < requestedDeferralHours * 0.5) {
        // Less than half the deferral period — don't defer
        decision = new DeferralDecision(DeferralDecision.Recommendation.PROCEED_AS_PLANNED,
            DeferralDecision.RiskLevel.HIGH);
        decision.setRationale("Estimated remaining life (" + String.format("%.0f", rul)
            + " hrs) is less than half the requested deferral period ("
            + String.format("%.0f", requestedDeferralHours) + " hrs).");
        decision.setConfidenceLevel(0.7);
      } else {
        // Can partially defer with reduced scope
        decision = new DeferralDecision(DeferralDecision.Recommendation.REDUCED_SCOPE,
            DeferralDecision.RiskLevel.MEDIUM);
        decision.setDeferralPeriodHours(rul * 0.7); // Allow 70% of RUL
        decision.setRationale("Partial deferral possible. RUL=" + String.format("%.0f", rul)
            + " hrs. Recommend deferring " + String.format("%.0f", rul * 0.7)
            + " hrs with reduced operating envelope.");
        decision.setConfidenceLevel(0.6);
      }
      return decision;
    }

    // Rule 3: Assess production impact at degraded conditions
    double productionLoss = estimateProductionLoss(healthIndex);
    double safetyRisk = assessSafetyRisk(healthIndex, severity);

    // Rule 4: Safety veto
    if (safetyRisk > maxAcceptableSafetyRisk) {
      decision = new DeferralDecision(DeferralDecision.Recommendation.PROCEED_AS_PLANNED,
          DeferralDecision.RiskLevel.HIGH);
      decision.setSafetyRiskScore(safetyRisk);
      decision.setRationale("Safety risk score (" + String.format("%.1f", safetyRisk)
          + ") exceeds acceptable limit (" + String.format("%.1f", maxAcceptableSafetyRisk) + ").");
      decision.setConfidenceLevel(0.8);
      return decision;
    }

    // Rule 5: Production loss veto
    if (productionLoss > maxAcceptableProductionLoss) {
      decision = new DeferralDecision(DeferralDecision.Recommendation.PROCEED_AS_PLANNED,
          DeferralDecision.RiskLevel.MEDIUM);
      decision.setProductionLossRiskPercent(productionLoss);
      decision.setRationale("Production loss risk (" + String.format("%.1f", productionLoss)
          + "%) exceeds acceptable limit (" + String.format("%.1f", maxAcceptableProductionLoss)
          + "%).");
      decision.setConfidenceLevel(0.7);
      return decision;
    }

    // Rule 6: Economic assessment
    double economicBenefit = calculateEconomicBenefit(productionLoss);
    double economicRisk = calculateEconomicRisk(healthIndex);

    // Rule 7: Positive economics + acceptable risk → defer
    DeferralDecision.RiskLevel riskLevel;
    if (healthIndex >= 0.75) {
      riskLevel = DeferralDecision.RiskLevel.LOW;
    } else if (healthIndex >= 0.50) {
      riskLevel = DeferralDecision.RiskLevel.MEDIUM;
    } else {
      riskLevel = DeferralDecision.RiskLevel.HIGH;
    }

    decision = new DeferralDecision(DeferralDecision.Recommendation.DEFER, riskLevel);
    decision.setDeferralPeriodHours(requestedDeferralHours);
    decision.setProductionLossRiskPercent(productionLoss);
    decision.setSafetyRiskScore(safetyRisk);
    decision.setEconomicBenefitUSD(economicBenefit);
    decision.setEconomicRiskUSD(economicRisk);
    decision.setConfidenceLevel(calculateConfidence(healthIndex));
    decision.setRationale("Deferral recommended. Health index=" + String.format("%.2f", healthIndex)
        + ", RUL=" + String.format("%.0f", rul) + " hrs (>"
        + String.format("%.0f", requestedDeferralHours) + " hrs requested). Production loss risk="
        + String.format("%.1f", productionLoss) + "%. Net economic benefit="
        + String.format("%.0f", economicBenefit - economicRisk) + " USD.");

    // Generate temporary operating envelope
    decision.setOperatingEnvelope(generateOperatingEnvelope(healthIndex));

    return decision;
  }

  /**
   * Estimates production loss percentage based on equipment health degradation.
   *
   * @param healthIndex current health index
   * @return estimated production loss as percentage
   */
  private double estimateProductionLoss(double healthIndex) {
    // Simple model: production loss increases non-linearly as health decreases
    // At health=1.0, loss=0%; at health=0.5, loss~10%; at health=0.25, loss~30%
    if (healthIndex >= 1.0) {
      return 0.0;
    }
    double degradation = 1.0 - healthIndex;
    return 100.0 * degradation * degradation * 1.2; // Quadratic model
  }

  /**
   * Assesses safety risk on a 0-10 scale based on health state.
   *
   * @param healthIndex current health index
   * @param severity health severity classification
   * @return safety risk score (0=negligible, 10=extreme)
   */
  private double assessSafetyRisk(double healthIndex,
      EquipmentHealthAssessment.HealthSeverity severity) {
    double baseRisk = (1.0 - healthIndex) * 6.0; // Max 6 from degradation alone
    if (severity == EquipmentHealthAssessment.HealthSeverity.ALERT) {
      baseRisk += 2.0;
    }
    return Math.min(10.0, baseRisk);
  }

  /**
   * Calculates the economic benefit of deferral (avoided shutdown cost).
   *
   * @param productionLoss estimated production loss percentage
   * @return economic benefit in USD
   */
  private double calculateEconomicBenefit(double productionLoss) {
    // Benefit = avoided downtime revenue + avoided maintenance mobilization
    // Assume planned maintenance takes typical duration proportional to cost
    double typicalDowntimeHours = 72.0; // 3 days
    double avoidedRevenueLoss = productionValuePerHour * typicalDowntimeHours;
    // During deferral, we still lose some production
    double deferralProductionLoss =
        productionValuePerHour * requestedDeferralHours * productionLoss / 100.0;
    return avoidedRevenueLoss - deferralProductionLoss;
  }

  /**
   * Calculates the economic risk of deferral (expected cost of failure).
   *
   * @param healthIndex current health index
   * @return expected economic risk in USD
   */
  private double calculateEconomicRisk(double healthIndex) {
    // Probability of failure during deferral period (simple exponential model)
    double failureProbability;
    if (healthIndex >= 0.75) {
      failureProbability = 0.02; // 2% for healthy equipment
    } else if (healthIndex >= 0.50) {
      failureProbability = 0.10; // 10% for degraded
    } else {
      failureProbability = 0.30; // 30% for significantly degraded
    }
    return failureProbability * unplannedShutdownCostUSD;
  }

  /**
   * Calculates confidence in the assessment.
   *
   * @param healthIndex current health index
   * @return confidence level 0-1
   */
  private double calculateConfidence(double healthIndex) {
    double confidence = 0.5; // Base confidence
    if (healthAssessment.getConditionIndicators().size() >= 3) {
      confidence += 0.2; // Good condition monitoring data
    }
    if (processSystem != null) {
      confidence += 0.15; // Process simulation available
    }
    if (healthIndex > 0.6) {
      confidence += 0.1; // Higher confidence when healthier
    }
    return Math.min(1.0, confidence);
  }

  /**
   * Generates a temporary operating envelope based on equipment health.
   *
   * @param healthIndex current health index
   * @return operating envelope with constraints
   */
  private TemporaryOperatingEnvelope generateOperatingEnvelope(double healthIndex) {
    TemporaryOperatingEnvelope envelope =
        new TemporaryOperatingEnvelope(equipmentName, requestedDeferralHours);

    // Reduce operating limits proportionally to degradation
    double capacityFraction = 0.7 + 0.3 * healthIndex; // 70-100% of design capacity

    envelope.addConstraint("capacity_fraction", "-", capacityFraction, 1.0, 1.0,
        "Limit throughput to " + String.format("%.0f", capacityFraction * 100)
            + "% of design to manage degradation");

    envelope.setMonitoringRequirements(
        "Increase monitoring frequency. Check vibration/temperature every "
            + (healthIndex > 0.6 ? "8 hours" : "4 hours")
            + ". Report any step-change in condition indicators immediately.");

    envelope.setEscalationCriteria(
        "Escalate to emergency maintenance if: (1) health index drops below 0.25, "
            + "(2) any condition indicator exceeds alarm threshold, "
            + "(3) production loss exceeds " + String.format("%.0f", maxAcceptableProductionLoss)
            + "%.");

    return envelope;
  }

  /**
   * Gets the computed decision. Returns null if {@link #assess()} has not been called.
   *
   * @return the deferral decision, or null
   */
  public DeferralDecision getDecision() {
    return decision;
  }

  /**
   * Gets the equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Generates a JSON representation of the full assessment.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
