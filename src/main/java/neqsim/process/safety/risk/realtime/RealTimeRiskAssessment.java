package neqsim.process.safety.risk.realtime;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.risk.RiskMatrix;

/**
 * Real-time Risk Assessment Result.
 *
 * <p>
 * Contains the results of a single real-time risk assessment, including overall risk metrics,
 * equipment-specific status, and trend information. Designed for integration with dashboards and
 * digital twin platforms.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class RealTimeRiskAssessment implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Assessment timestamp. */
  private Instant timestamp;

  /** Overall risk score (0-10). */
  private double overallRiskScore;

  /** Risk category. */
  private RiskMatrix.RiskLevel riskCategory;

  /** Expected production loss (%). */
  private double expectedProductionLoss;

  /** System availability (0-1). */
  private double availability;

  /** Risk trend. */
  private String riskTrend = "STABLE";

  /** Trend slope. */
  private double trendSlope;

  /** Equipment statuses. */
  private List<RealTimeRiskMonitor.EquipmentRiskStatus> equipmentStatuses;

  /** Key risk indicators. */
  private Map<String, Double> kris;

  /** Process variable deviations. */
  private Map<String, ProcessVariableStatus> processVariables;

  /** Safety system status. */
  private SafetySystemStatus safetyStatus;

  /**
   * Process variable deviation status.
   */
  public static class ProcessVariableStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    private String variableName;
    private double currentValue;
    private double normalValue;
    private double deviation;
    private double deviationPercent;
    private String unit;
    private boolean alarming;

    public ProcessVariableStatus(String name, double current, double normal, String unit) {
      this.variableName = name;
      this.currentValue = current;
      this.normalValue = normal;
      this.unit = unit;
      this.deviation = current - normal;
      this.deviationPercent = normal != 0 ? (deviation / normal) * 100 : 0;
      this.alarming = Math.abs(deviationPercent) > 10;
    }

    public String getVariableName() {
      return variableName;
    }

    public double getCurrentValue() {
      return currentValue;
    }

    public double getNormalValue() {
      return normalValue;
    }

    public double getDeviation() {
      return deviation;
    }

    public double getDeviationPercent() {
      return deviationPercent;
    }

    public String getUnit() {
      return unit;
    }

    public boolean isAlarming() {
      return alarming;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("variableName", variableName);
      map.put("currentValue", currentValue);
      map.put("normalValue", normalValue);
      map.put("deviation", deviation);
      map.put("deviationPercent", deviationPercent);
      map.put("unit", unit);
      map.put("alarming", alarming);
      return map;
    }
  }

  /**
   * Safety system status summary.
   */
  public static class SafetySystemStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    private int totalSIFs;
    private int availableSIFs;
    private int bypassedSIFs;
    private int demandedSIFs;
    private double overallSISHealth;

    public int getTotalSIFs() {
      return totalSIFs;
    }

    public void setTotalSIFs(int total) {
      this.totalSIFs = total;
    }

    public int getAvailableSIFs() {
      return availableSIFs;
    }

    public void setAvailableSIFs(int available) {
      this.availableSIFs = available;
    }

    public int getBypassedSIFs() {
      return bypassedSIFs;
    }

    public void setBypassedSIFs(int bypassed) {
      this.bypassedSIFs = bypassed;
    }

    public int getDemandedSIFs() {
      return demandedSIFs;
    }

    public void setDemandedSIFs(int demanded) {
      this.demandedSIFs = demanded;
    }

    public double getOverallSISHealth() {
      return overallSISHealth;
    }

    public void setOverallSISHealth(double health) {
      this.overallSISHealth = health;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("totalSIFs", totalSIFs);
      map.put("availableSIFs", availableSIFs);
      map.put("bypassedSIFs", bypassedSIFs);
      map.put("demandedSIFs", demandedSIFs);
      map.put("overallSISHealth", overallSISHealth);
      return map;
    }
  }

  /**
   * Creates a real-time risk assessment.
   */
  public RealTimeRiskAssessment() {
    this.timestamp = Instant.now();
    this.equipmentStatuses = new ArrayList<>();
    this.kris = new HashMap<>();
    this.processVariables = new HashMap<>();
    this.safetyStatus = new SafetySystemStatus();
  }

  // Setters

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public void setOverallRiskScore(double score) {
    this.overallRiskScore = score;
    updateRiskCategory();
  }

  private void updateRiskCategory() {
    if (overallRiskScore < 3) {
      riskCategory = RiskMatrix.RiskLevel.LOW;
    } else if (overallRiskScore < 6) {
      riskCategory = RiskMatrix.RiskLevel.MEDIUM;
    } else if (overallRiskScore < 9) {
      riskCategory = RiskMatrix.RiskLevel.HIGH;
    } else {
      riskCategory = RiskMatrix.RiskLevel.CRITICAL;
    }
  }

  public void setExpectedProductionLoss(double loss) {
    this.expectedProductionLoss = loss;
  }

  public void setAvailability(double availability) {
    this.availability = availability;
  }

  public void setRiskTrend(String trend) {
    this.riskTrend = trend;
  }

  public void setTrendSlope(double slope) {
    this.trendSlope = slope;
  }

  public void setEquipmentStatuses(List<RealTimeRiskMonitor.EquipmentRiskStatus> statuses) {
    this.equipmentStatuses = statuses;
  }

  public void addKRI(String name, double value) {
    this.kris.put(name, value);
  }

  public void addProcessVariable(String name, double current, double normal, String unit) {
    this.processVariables.put(name, new ProcessVariableStatus(name, current, normal, unit));
  }

  public void setSafetyStatus(SafetySystemStatus status) {
    this.safetyStatus = status;
  }

  // Getters

  public Instant getTimestamp() {
    return timestamp;
  }

  public double getOverallRiskScore() {
    return overallRiskScore;
  }

  public RiskMatrix.RiskLevel getRiskCategory() {
    return riskCategory;
  }

  public double getExpectedProductionLoss() {
    return expectedProductionLoss;
  }

  public double getAvailability() {
    return availability;
  }

  public String getRiskTrend() {
    return riskTrend;
  }

  public double getTrendSlope() {
    return trendSlope;
  }

  public List<RealTimeRiskMonitor.EquipmentRiskStatus> getEquipmentStatuses() {
    return new ArrayList<>(equipmentStatuses);
  }

  public Map<String, Double> getKRIs() {
    return new HashMap<>(kris);
  }

  public Map<String, ProcessVariableStatus> getProcessVariables() {
    return new HashMap<>(processVariables);
  }

  public SafetySystemStatus getSafetyStatus() {
    return safetyStatus;
  }

  /**
   * Gets list of alarming process variables.
   *
   * @return alarming variables
   */
  public List<ProcessVariableStatus> getAlarmingVariables() {
    List<ProcessVariableStatus> alarming = new ArrayList<>();
    for (ProcessVariableStatus pv : processVariables.values()) {
      if (pv.isAlarming()) {
        alarming.add(pv);
      }
    }
    return alarming;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("timestamp", timestamp.toString());

    // Risk summary
    Map<String, Object> risk = new HashMap<>();
    risk.put("score", overallRiskScore);
    risk.put("category", riskCategory != null ? riskCategory.name() : "UNKNOWN");
    risk.put("trend", riskTrend);
    risk.put("trendSlope", trendSlope);
    map.put("risk", risk);

    // Production
    Map<String, Object> production = new HashMap<>();
    production.put("expectedLossPercent", expectedProductionLoss);
    production.put("availability", availability);
    map.put("production", production);

    // Equipment status
    List<Map<String, Object>> equipment = new ArrayList<>();
    for (RealTimeRiskMonitor.EquipmentRiskStatus status : equipmentStatuses) {
      equipment.add(status.toMap());
    }
    map.put("equipment", equipment);

    // KRIs
    map.put("keyRiskIndicators", kris);

    // Process variables
    List<Map<String, Object>> pvList = new ArrayList<>();
    for (ProcessVariableStatus pv : processVariables.values()) {
      pvList.add(pv.toMap());
    }
    map.put("processVariables", pvList);

    // Safety systems
    map.put("safetySystem", safetyStatus.toMap());

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Generates dashboard-friendly summary.
   *
   * @return summary string
   */
  public String toSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("═══════════════════════════════════════════════════════════\n");
    sb.append("         REAL-TIME RISK ASSESSMENT SUMMARY\n");
    sb.append("═══════════════════════════════════════════════════════════\n");
    sb.append(String.format("Timestamp: %s%n", timestamp));
    sb.append(String.format("Risk Score: %.1f/10 [%s] %s%n", overallRiskScore,
        riskCategory != null ? riskCategory.name() : "N/A",
        riskTrend.equals("INCREASING") ? "↑" : riskTrend.equals("DECREASING") ? "↓" : "→"));
    sb.append(String.format("Availability: %.1f%%%n", availability * 100));
    sb.append(String.format("Expected Production Loss: %.2f%%%n", expectedProductionLoss));
    sb.append("───────────────────────────────────────────────────────────\n");

    if (!getAlarmingVariables().isEmpty()) {
      sb.append("ALARMING VARIABLES:\n");
      for (ProcessVariableStatus pv : getAlarmingVariables()) {
        sb.append(String.format("  • %s: %.1f%s (%.1f%% deviation)%n", pv.getVariableName(),
            pv.getCurrentValue(), pv.getUnit(), pv.getDeviationPercent()));
      }
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("RealTimeRiskAssessment[%s: score=%.1f, category=%s, trend=%s]", timestamp,
        overallRiskScore, riskCategory, riskTrend);
  }
}
