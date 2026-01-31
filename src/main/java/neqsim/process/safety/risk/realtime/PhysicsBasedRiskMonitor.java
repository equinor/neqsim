package neqsim.process.safety.risk.realtime;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.risk.condition.ProcessEquipmentMonitor;

/**
 * Physics-based risk monitor that directly integrates with NeqSim process simulation.
 *
 * <p>
 * This class provides risk assessment that is directly derived from the actual process physics:
 * </p>
 * <ul>
 * <li>Capacity utilization from NeqSim equipment (compressor speed, separator loading, etc.)</li>
 * <li>Bottleneck detection using ProcessSystem.findBottleneck()</li>
 * <li>Temperature/pressure deviations from equipment operating conditions</li>
 * <li>Equipment health based on physics-based condition monitoring</li>
 * </ul>
 *
 * <p>
 * Unlike generic risk frameworks, this class leverages NeqSim's built-in physics calculations to
 * provide more accurate and meaningful risk assessments.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * ProcessSystem process = new ProcessSystem();
 * // ... add equipment and configure process ...
 * process.run();
 * 
 * PhysicsBasedRiskMonitor monitor = new PhysicsBasedRiskMonitor(process);
 * monitor.setBaseFailureRates("Compressor1", 0.0001); // failures/hour
 * 
 * PhysicsBasedRiskAssessment assessment = monitor.assess();
 * System.out.println("Overall risk score: " + assessment.getOverallRiskScore());
 * System.out.println("Bottleneck: " + assessment.getBottleneckEquipment());
 * System.out.println("Highest risk equipment: " + assessment.getHighestRiskEquipment());
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PhysicsBasedRiskMonitor implements Serializable {
  private static final long serialVersionUID = 1000L;

  private ProcessSystem processSystem;
  private Map<String, ProcessEquipmentMonitor> equipmentMonitors;
  private Map<String, Double> baseFailureRates;
  private Instant lastAssessment;

  /**
   * Risk assessment result derived from physics-based calculations.
   */
  public static class PhysicsBasedRiskAssessment implements Serializable {
    private static final long serialVersionUID = 1L;

    private Instant timestamp;
    private double overallRiskScore;
    private String bottleneckEquipment;
    private String bottleneckConstraint;
    private double bottleneckUtilization;
    private String highestRiskEquipment;
    private double highestRiskScore;
    private Map<String, Double> equipmentUtilizations;
    private Map<String, Double> equipmentRiskScores;
    private Map<String, Double> equipmentHealthIndices;
    private double systemCapacityMargin;
    private List<String> criticalEquipment;
    private List<String> warnings;

    /**
     * Creates a new assessment.
     */
    public PhysicsBasedRiskAssessment() {
      this.timestamp = Instant.now();
      this.equipmentUtilizations = new HashMap<>();
      this.equipmentRiskScores = new HashMap<>();
      this.equipmentHealthIndices = new HashMap<>();
      this.criticalEquipment = new ArrayList<>();
      this.warnings = new ArrayList<>();
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public double getOverallRiskScore() {
      return overallRiskScore;
    }

    public void setOverallRiskScore(double score) {
      this.overallRiskScore = score;
    }

    public String getBottleneckEquipment() {
      return bottleneckEquipment;
    }

    public void setBottleneckEquipment(String equipment) {
      this.bottleneckEquipment = equipment;
    }

    public String getBottleneckConstraint() {
      return bottleneckConstraint;
    }

    public void setBottleneckConstraint(String constraint) {
      this.bottleneckConstraint = constraint;
    }

    public double getBottleneckUtilization() {
      return bottleneckUtilization;
    }

    public void setBottleneckUtilization(double utilization) {
      this.bottleneckUtilization = utilization;
    }

    public String getHighestRiskEquipment() {
      return highestRiskEquipment;
    }

    public void setHighestRiskEquipment(String equipment) {
      this.highestRiskEquipment = equipment;
    }

    public double getHighestRiskScore() {
      return highestRiskScore;
    }

    public void setHighestRiskScore(double score) {
      this.highestRiskScore = score;
    }

    public Map<String, Double> getEquipmentUtilizations() {
      return equipmentUtilizations;
    }

    public Map<String, Double> getEquipmentRiskScores() {
      return equipmentRiskScores;
    }

    public Map<String, Double> getEquipmentHealthIndices() {
      return equipmentHealthIndices;
    }

    public double getSystemCapacityMargin() {
      return systemCapacityMargin;
    }

    public void setSystemCapacityMargin(double margin) {
      this.systemCapacityMargin = margin;
    }

    public List<String> getCriticalEquipment() {
      return criticalEquipment;
    }

    public List<String> getWarnings() {
      return warnings;
    }

    /**
     * Converts assessment to map for JSON serialization.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("timestamp", timestamp.toString());
      map.put("overallRiskScore", overallRiskScore);
      map.put("bottleneckEquipment", bottleneckEquipment);
      map.put("bottleneckConstraint", bottleneckConstraint);
      map.put("bottleneckUtilization", bottleneckUtilization);
      map.put("highestRiskEquipment", highestRiskEquipment);
      map.put("highestRiskScore", highestRiskScore);
      map.put("systemCapacityMargin", systemCapacityMargin);
      map.put("equipmentUtilizations", equipmentUtilizations);
      map.put("equipmentRiskScores", equipmentRiskScores);
      map.put("equipmentHealthIndices", equipmentHealthIndices);
      map.put("criticalEquipment", criticalEquipment);
      map.put("warnings", warnings);
      return map;
    }
  }

  /**
   * Creates a physics-based risk monitor for a process system.
   *
   * @param processSystem NeqSim process system to monitor
   */
  public PhysicsBasedRiskMonitor(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.equipmentMonitors = new HashMap<>();
    this.baseFailureRates = new HashMap<>();
    initializeMonitors();
  }

  /**
   * Initializes equipment monitors from the process system.
   */
  private void initializeMonitors() {
    if (processSystem == null) {
      return;
    }

    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      String name = unit.getName();
      ProcessEquipmentMonitor monitor = new ProcessEquipmentMonitor(unit);
      monitor.setBaseFailureRate(0.0001); // Default rate
      equipmentMonitors.put(name, monitor);
    }
  }

  /**
   * Sets base failure rate for specific equipment.
   *
   * @param equipmentName equipment name
   * @param failureRate failure rate in failures per hour
   */
  public void setBaseFailureRate(String equipmentName, double failureRate) {
    baseFailureRates.put(equipmentName, failureRate);
    ProcessEquipmentMonitor monitor = equipmentMonitors.get(equipmentName);
    if (monitor != null) {
      monitor.setBaseFailureRate(failureRate);
    }
  }

  /**
   * Sets design limits for equipment temperature monitoring.
   *
   * @param equipmentName equipment name
   * @param minTemp minimum design temperature in Kelvin
   * @param maxTemp maximum design temperature in Kelvin
   */
  public void setDesignTemperatureRange(String equipmentName, double minTemp, double maxTemp) {
    ProcessEquipmentMonitor monitor = equipmentMonitors.get(equipmentName);
    if (monitor != null) {
      monitor.setDesignTemperatureRange(minTemp, maxTemp);
    }
  }

  /**
   * Sets design limits for equipment pressure monitoring.
   *
   * @param equipmentName equipment name
   * @param minPressure minimum design pressure in bara
   * @param maxPressure maximum design pressure in bara
   */
  public void setDesignPressureRange(String equipmentName, double minPressure, double maxPressure) {
    ProcessEquipmentMonitor monitor = equipmentMonitors.get(equipmentName);
    if (monitor != null) {
      monitor.setDesignPressureRange(minPressure, maxPressure);
    }
  }

  /**
   * Performs a physics-based risk assessment.
   *
   * <p>
   * This method:
   * </p>
   * <ol>
   * <li>Reads current T, P, and capacity utilization from all equipment</li>
   * <li>Uses ProcessSystem.findBottleneck() to identify limiting equipment</li>
   * <li>Calculates health indices based on physics deviations</li>
   * <li>Computes risk scores weighted by utilization and consequence</li>
   * </ol>
   *
   * @return physics-based risk assessment
   */
  public PhysicsBasedRiskAssessment assess() {
    PhysicsBasedRiskAssessment assessment = new PhysicsBasedRiskAssessment();

    // Update all equipment monitors from process physics
    updateEquipmentMonitors();

    // Get bottleneck from NeqSim physics
    analyzeBottleneck(assessment);

    // Get capacity utilizations from NeqSim
    analyzeCapacityUtilizations(assessment);

    // Calculate risk scores
    calculateRiskScores(assessment);

    // Calculate overall system risk
    calculateOverallRisk(assessment);

    lastAssessment = Instant.now();
    return assessment;
  }

  /**
   * Updates all equipment monitors from current process state.
   */
  private void updateEquipmentMonitors() {
    for (ProcessEquipmentMonitor monitor : equipmentMonitors.values()) {
      monitor.update();
    }
  }

  /**
   * Analyzes system bottleneck using NeqSim's physics-based bottleneck detection.
   *
   * @param assessment assessment to populate
   */
  private void analyzeBottleneck(PhysicsBasedRiskAssessment assessment) {
    BottleneckResult bottleneck = processSystem.findBottleneck();

    if (bottleneck != null && bottleneck.getEquipment() != null) {
      assessment.setBottleneckEquipment(bottleneck.getEquipment().getName());
      assessment.setBottleneckConstraint(bottleneck.getConstraintName());
      assessment.setBottleneckUtilization(bottleneck.getUtilization());

      // Calculate system capacity margin (how much room before bottleneck)
      double margin = 1.0 - bottleneck.getUtilization();
      assessment.setSystemCapacityMargin(margin);

      if (margin < 0.1) {
        assessment.getWarnings().add("System near capacity limit - bottleneck at "
            + bottleneck.getEquipment().getName() + " (" + bottleneck.getConstraintName() + ")");
      }
    } else {
      assessment.setSystemCapacityMargin(1.0);
    }
  }

  /**
   * Analyzes capacity utilizations from NeqSim equipment.
   *
   * @param assessment assessment to populate
   */
  private void analyzeCapacityUtilizations(PhysicsBasedRiskAssessment assessment) {
    Map<String, Double> utilizations = processSystem.getCapacityUtilizationSummary();
    assessment.getEquipmentUtilizations().putAll(utilizations);

    // Check for equipment near capacity
    for (Map.Entry<String, Double> entry : utilizations.entrySet()) {
      if (entry.getValue() > 0.9) {
        assessment.getWarnings().add(entry.getKey() + " at "
            + String.format("%.1f%%", entry.getValue() * 100) + " utilization");
      }
    }
  }

  /**
   * Calculates physics-weighted risk scores for each equipment.
   *
   * @param assessment assessment to populate
   */
  private void calculateRiskScores(PhysicsBasedRiskAssessment assessment) {
    double maxRisk = 0;
    String maxRiskEquipment = "";

    for (Map.Entry<String, ProcessEquipmentMonitor> entry : equipmentMonitors.entrySet()) {
      String name = entry.getKey();
      ProcessEquipmentMonitor monitor = entry.getValue();

      // Health index from physics (T, P deviations)
      double health = monitor.getHealthIndex();
      assessment.getEquipmentHealthIndices().put(name, health);

      // Failure probability based on condition
      double failureProb = monitor.getFailureProbability(24); // 24-hour probability

      // Get capacity utilization from NeqSim
      double utilization = assessment.getEquipmentUtilizations().getOrDefault(name,
          monitor.getCurrentCapacityUtilization());

      // Risk score = f(failure probability, utilization, consequence)
      // Higher utilization = higher consequence of failure (system impact)
      double consequenceWeight = 1.0 + utilization * 2.0; // 1x at 0%, 3x at 100%

      // Bottleneck equipment has higher consequence
      if (name.equals(assessment.getBottleneckEquipment())) {
        consequenceWeight *= 2.0;
      }

      double riskScore = failureProb * consequenceWeight * 10; // Scale to 0-10
      assessment.getEquipmentRiskScores().put(name, riskScore);

      if (riskScore > maxRisk) {
        maxRisk = riskScore;
        maxRiskEquipment = name;
      }

      // Track critical equipment
      if (riskScore > 5.0 || health < 0.5) {
        assessment.getCriticalEquipment().add(name);
      }
    }

    assessment.setHighestRiskEquipment(maxRiskEquipment);
    assessment.setHighestRiskScore(maxRisk);
  }

  /**
   * Calculates overall system risk score.
   *
   * @param assessment assessment to populate
   */
  private void calculateOverallRisk(PhysicsBasedRiskAssessment assessment) {
    // Overall risk is based on:
    // 1. How close we are to capacity (bottleneck utilization)
    // 2. Equipment health indices
    // 3. Individual risk scores

    double capacityRisk = assessment.getBottleneckUtilization() * 3; // 0-3 scale

    double avgHealth = assessment.getEquipmentHealthIndices().values().stream()
        .mapToDouble(Double::doubleValue).average().orElse(1.0);
    double healthRisk = (1.0 - avgHealth) * 4; // 0-4 scale

    double maxEquipmentRisk = assessment.getHighestRiskScore() * 0.3; // 0-3 scale

    double overall = Math.min(10, capacityRisk + healthRisk + maxEquipmentRisk);
    assessment.setOverallRiskScore(overall);
  }

  /**
   * Gets the process system being monitored.
   *
   * @return process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Gets equipment monitor for specific equipment.
   *
   * @param equipmentName equipment name
   * @return equipment monitor or null
   */
  public ProcessEquipmentMonitor getEquipmentMonitor(String equipmentName) {
    return equipmentMonitors.get(equipmentName);
  }

  /**
   * Gets all equipment monitors.
   *
   * @return map of equipment name to monitor
   */
  public Map<String, ProcessEquipmentMonitor> getEquipmentMonitors() {
    return new HashMap<>(equipmentMonitors);
  }

  /**
   * Gets last assessment time.
   *
   * @return last assessment instant
   */
  public Instant getLastAssessment() {
    return lastAssessment;
  }
}
