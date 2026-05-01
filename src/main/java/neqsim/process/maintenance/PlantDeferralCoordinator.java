package neqsim.process.maintenance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Coordinates maintenance deferral decisions across an entire plant or process system. Considers
 * interactions between equipment items, cumulative risk, and system-wide production impact.
 *
 * <p>
 * The coordinator:
 * </p>
 * <ul>
 * <li>Collects health assessments from all relevant equipment</li>
 * <li>Identifies which deferrals can be combined safely</li>
 * <li>Checks for cascading failure risks from multiple degraded items</li>
 * <li>Produces a plant-level deferral plan with prioritized recommendations</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PlantDeferralCoordinator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /**
   * Summary of a single equipment deferral assessment within the plant context.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class EquipmentDeferralSummary implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1001L;

    private final String equipmentName;
    private final EquipmentHealthAssessment healthAssessment;
    private final DeferralDecision decision;

    /**
     * Constructs an equipment deferral summary.
     *
     * @param equipmentName equipment name
     * @param healthAssessment the health assessment
     * @param decision the deferral decision
     */
    public EquipmentDeferralSummary(String equipmentName,
        EquipmentHealthAssessment healthAssessment, DeferralDecision decision) {
      this.equipmentName = equipmentName;
      this.healthAssessment = healthAssessment;
      this.decision = decision;
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
     * Gets the health assessment.
     *
     * @return health assessment
     */
    public EquipmentHealthAssessment getHealthAssessment() {
      return healthAssessment;
    }

    /**
     * Gets the deferral decision.
     *
     * @return deferral decision
     */
    public DeferralDecision getDecision() {
      return decision;
    }
  }

  private ProcessSystem processSystem;
  private double defaultDeferralHours = 720.0;
  private double productionValuePerHour = 0.0;
  private double maxCumulativeRisk = 5.0; // Max combined safety risk across all equipment
  private double maxSimultaneousDeferrals = 3; // Max items deferred at once

  private final List<EquipmentDeferralSummary> summaries =
      new ArrayList<EquipmentDeferralSummary>();
  private final Map<String, EquipmentHealthAssessment> healthAssessments =
      new LinkedHashMap<String, EquipmentHealthAssessment>();

  private double plantHealthIndex = 1.0;
  private double cumulativeSafetyRisk = 0.0;
  private boolean assessmentComplete = false;

  /**
   * Constructs a plant deferral coordinator.
   *
   * @param processSystem the process system to coordinate
   */
  public PlantDeferralCoordinator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Sets the default deferral period for all equipment.
   *
   * @param hours default deferral period in hours
   */
  public void setDefaultDeferralHours(double hours) {
    this.defaultDeferralHours = hours;
  }

  /**
   * Sets the production value per hour.
   *
   * @param usdPerHour revenue per hour
   */
  public void setProductionValuePerHour(double usdPerHour) {
    this.productionValuePerHour = usdPerHour;
  }

  /**
   * Sets the maximum cumulative safety risk.
   *
   * @param maxRisk maximum combined safety risk score
   */
  public void setMaxCumulativeRisk(double maxRisk) {
    this.maxCumulativeRisk = maxRisk;
  }

  /**
   * Registers a health assessment for a specific equipment item.
   *
   * @param equipmentName name of the equipment
   * @param assessment the health assessment
   */
  public void addHealthAssessment(String equipmentName, EquipmentHealthAssessment assessment) {
    healthAssessments.put(equipmentName, assessment);
  }

  /**
   * Auto-generates health assessments for compressors in the process system by reading their
   * current degradation and fouling factors.
   */
  public void autoAssessCompressors() {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equip = units.get(i);
      if (equip instanceof Compressor) {
        Compressor comp = (Compressor) equip;
        EquipmentHealthAssessment assessment =
            new EquipmentHealthAssessment(comp.getName(), "compressor");
        assessment.setDegradationFactor(comp.getDegradationFactor());
        assessment.setFoulingFactor(comp.getFoulingFactor());
        assessment.setOperatingHours(comp.getOperatingHours());
        assessment.setHoursSinceOverhaul(comp.getOperatingHours());
        healthAssessments.put(comp.getName(), assessment);
      }
    }
  }

  /**
   * Runs the coordinated deferral assessment across all registered equipment.
   */
  public void assess() {
    summaries.clear();
    cumulativeSafetyRisk = 0.0;
    double healthSum = 0.0;
    int deferralCount = 0;

    for (Map.Entry<String, EquipmentHealthAssessment> entry : healthAssessments.entrySet()) {
      String name = entry.getKey();
      EquipmentHealthAssessment health = entry.getValue();

      MaintenanceDeferralAssessment assessment = new MaintenanceDeferralAssessment(name);
      assessment.setHealthAssessment(health);
      assessment.setProcessSystem(processSystem);
      assessment.setRequestedDeferralHours(defaultDeferralHours);
      assessment.setProductionValuePerHour(productionValuePerHour);

      DeferralDecision decision = assessment.assess();
      summaries.add(new EquipmentDeferralSummary(name, health, decision));

      healthSum += health.getHealthIndex();
      cumulativeSafetyRisk += decision.getSafetyRiskScore();
      if (decision.isDeferralViable()) {
        deferralCount++;
      }
    }

    // Plant-level health index
    if (!healthAssessments.isEmpty()) {
      plantHealthIndex = healthSum / healthAssessments.size();
    }

    // Apply plant-level constraints: revoke deferrals if cumulative risk too high
    if (cumulativeSafetyRisk > maxCumulativeRisk || deferralCount > maxSimultaneousDeferrals) {
      applyPlantLevelConstraints();
    }

    assessmentComplete = true;
  }

  /**
   * Applies plant-level constraints by revoking the riskiest deferrals when cumulative risk is too
   * high or too many items are deferred simultaneously.
   */
  private void applyPlantLevelConstraints() {
    // Sort by risk (highest first) and revoke until constraints are met
    List<EquipmentDeferralSummary> deferred = new ArrayList<EquipmentDeferralSummary>();
    for (EquipmentDeferralSummary s : summaries) {
      if (s.getDecision().isDeferralViable()) {
        deferred.add(s);
      }
    }

    // Sort by safety risk descending
    Collections.sort(deferred, new java.util.Comparator<EquipmentDeferralSummary>() {
      @Override
      public int compare(EquipmentDeferralSummary a, EquipmentDeferralSummary b) {
        return Double.compare(b.getDecision().getSafetyRiskScore(),
            a.getDecision().getSafetyRiskScore());
      }
    });

    // Revoke highest-risk deferrals until cumulative risk is acceptable
    double runningRisk = cumulativeSafetyRisk;
    int runningCount = deferred.size();

    for (EquipmentDeferralSummary s : deferred) {
      if (runningRisk <= maxCumulativeRisk && runningCount <= maxSimultaneousDeferrals) {
        break;
      }
      // Replace the decision with "proceed as planned" due to plant-level constraint
      int idx = summaries.indexOf(s);
      if (idx >= 0) {
        DeferralDecision revised = new DeferralDecision(
            DeferralDecision.Recommendation.PROCEED_AS_PLANNED, DeferralDecision.RiskLevel.HIGH);
        revised.setRationale("Deferral revoked due to plant-level cumulative risk constraint. "
            + "Original assessment recommended deferral but combined risk exceeds plant limit.");
        revised.setSafetyRiskScore(s.getDecision().getSafetyRiskScore());
        revised.setConfidenceLevel(0.8);
        summaries.set(idx,
            new EquipmentDeferralSummary(s.getEquipmentName(), s.getHealthAssessment(), revised));
        runningRisk -= s.getDecision().getSafetyRiskScore();
        runningCount--;
      }
    }
    cumulativeSafetyRisk = runningRisk;
  }

  /**
   * Gets the deferral summaries for all assessed equipment.
   *
   * @return unmodifiable list of equipment deferral summaries
   */
  public List<EquipmentDeferralSummary> getSummaries() {
    return Collections.unmodifiableList(summaries);
  }

  /**
   * Gets the plant-level average health index.
   *
   * @return plant health index (0-1)
   */
  public double getPlantHealthIndex() {
    return plantHealthIndex;
  }

  /**
   * Gets the cumulative safety risk across all equipment.
   *
   * @return cumulative safety risk score
   */
  public double getCumulativeSafetyRisk() {
    return cumulativeSafetyRisk;
  }

  /**
   * Gets the number of equipment items where deferral is recommended.
   *
   * @return count of viable deferrals
   */
  public int getDeferralCount() {
    int count = 0;
    for (EquipmentDeferralSummary s : summaries) {
      if (s.getDecision().isDeferralViable()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Checks if the overall assessment is complete.
   *
   * @return true if assess() has been called
   */
  public boolean isAssessmentComplete() {
    return assessmentComplete;
  }

  /**
   * Generates a JSON representation of the full plant deferral assessment.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
