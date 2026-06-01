package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.topology.DependencyAnalyzer;
import neqsim.process.util.topology.ProcessTopologyAnalyzer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Traces how a failure propagates through a process flowsheet.
 *
 * <p>
 * Given an initial trip event on a piece of equipment, this class determines the sequence of
 * downstream equipment that will be affected, estimates the propagation timing, and builds a
 * complete failure cascade chain. It answers the question: <em>"How did the failure propagate
 * through the plant?"</em>
 * </p>
 *
 * <p>
 * The tracer uses the {@link ProcessTopologyAnalyzer} for flowsheet connectivity and the
 * {@link DependencyAnalyzer} for cascade impact analysis. It extends the static dependency analysis
 * with time-ordered propagation and severity assessment.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * FailurePropagationTracer tracer = new FailurePropagationTracer(processSystem);
 * PropagationResult result = tracer.trace("Compressor-1");
 *
 * for (PropagationStep step : result.getSteps()) {
 *   System.out.printf("%.0fs: %s (%s)%n", step.getEstimatedDelaySeconds(),
 *       step.getEquipmentName(), step.getEffect());
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see TripEventDetector
 * @see RootCauseAnalyzer
 * @see DependencyAnalyzer
 */
public class FailurePropagationTracer implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(FailurePropagationTracer.class);

  /** Default propagation delay between directly connected equipment (seconds). */
  private static final double DEFAULT_DIRECT_DELAY = 5.0;

  /** Default propagation delay for indirect effects (seconds). */
  private static final double DEFAULT_INDIRECT_DELAY = 30.0;

  /** Process system to trace through. */
  private final ProcessSystem processSystem;

  /** Topology analyzer for connectivity. */
  private transient ProcessTopologyAnalyzer topologyAnalyzer;

  /** Dependency analyzer for cascade effects. */
  private transient DependencyAnalyzer dependencyAnalyzer;

  /** Custom propagation delays keyed by "source-&gt;target". */
  private final Map<String, Double> customDelays;

  /** Maximum cascade depth to prevent runaway tracing. */
  private int maxCascadeDepth;

  /**
   * Creates a failure propagation tracer.
   *
   * @param processSystem the process system to trace through
   * @throws IllegalArgumentException if processSystem is null
   */
  public FailurePropagationTracer(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = processSystem;
    this.customDelays = new LinkedHashMap<>();
    this.maxCascadeDepth = 20;
    initializeAnalyzers();
  }

  /**
   * Initializes the topology and dependency analyzers.
   */
  private void initializeAnalyzers() {
    this.topologyAnalyzer = new ProcessTopologyAnalyzer(processSystem);
    topologyAnalyzer.buildTopology();
    this.dependencyAnalyzer = new DependencyAnalyzer(processSystem, topologyAnalyzer);
  }

  /**
   * Sets a custom propagation delay between two equipment items.
   *
   * @param sourceEquipment source equipment name
   * @param targetEquipment target equipment name
   * @param delaySeconds propagation delay in seconds
   */
  public void setCustomDelay(String sourceEquipment, String targetEquipment, double delaySeconds) {
    customDelays.put(sourceEquipment + "->" + targetEquipment, delaySeconds);
  }

  /**
   * Sets the maximum cascade depth.
   *
   * @param depth maximum depth (default 20)
   */
  public void setMaxCascadeDepth(int depth) {
    this.maxCascadeDepth = Math.max(1, depth);
  }

  /**
   * Traces the failure propagation from a given equipment.
   *
   * @param failedEquipmentName name of the initially failed equipment
   * @return propagation result with ordered steps
   */
  public PropagationResult trace(String failedEquipmentName) {
    logger.info("Tracing failure propagation from: {}", failedEquipmentName);

    PropagationResult result = new PropagationResult(failedEquipmentName);

    // Find the initial equipment
    ProcessEquipmentInterface failedEquipment = findEquipment(failedEquipmentName);
    if (failedEquipment == null) {
      logger.warn("Equipment '{}' not found in process system", failedEquipmentName);
      return result;
    }

    // Use dependency analyzer for cascade analysis
    DependencyAnalyzer.DependencyResult depResult =
        dependencyAnalyzer.analyzeFailure(failedEquipmentName);

    // Build propagation chain with estimated timing
    Set<String> visited = new LinkedHashSet<>();
    visited.add(failedEquipmentName);

    // Add the initial failure as step 0
    result.addStep(new PropagationStep(failedEquipmentName, 0.0, 0,
        "Initial equipment failure/trip", PropagationStep.ImpactLevel.CRITICAL));

    // Trace direct downstream effects
    for (String directName : depResult.getDirectlyAffected()) {
      if (visited.contains(directName)) {
        continue;
      }
      visited.add(directName);
      double delay = getDelay(failedEquipmentName, directName, true);
      String effect = classifyEffect(failedEquipmentName, directName);
      PropagationStep.ImpactLevel impact = assessImpact(directName);
      result.addStep(new PropagationStep(directName, delay, 1, effect, impact));
    }

    // Trace indirect downstream effects
    double cumulativeDelay = DEFAULT_DIRECT_DELAY;
    for (String indirectName : depResult.getIndirectlyAffected()) {
      if (visited.contains(indirectName)) {
        continue;
      }
      visited.add(indirectName);
      cumulativeDelay += DEFAULT_INDIRECT_DELAY;
      String effect = classifyEffect(failedEquipmentName, indirectName);
      PropagationStep.ImpactLevel impact = assessImpact(indirectName);
      result.addStep(new PropagationStep(indirectName, cumulativeDelay, 2, effect, impact));
    }

    // Add production impact
    result.setTotalProductionLossPercent(depResult.getTotalProductionLoss());

    // Add equipment to monitor
    for (String watchEquip : depResult.getEquipmentToWatch()) {
      if (!visited.contains(watchEquip)) {
        result.addEquipmentToMonitor(watchEquip);
      }
    }

    logger.info("Failure propagation from '{}': {} steps, {}% production impact",
        failedEquipmentName, result.getSteps().size(),
        String.format("%.1f", result.getTotalProductionLossPercent()));

    return result;
  }

  /**
   * Traces propagation from a trip event.
   *
   * @param tripEvent the trip event that initiated the failure
   * @return propagation result
   */
  public PropagationResult trace(TripEvent tripEvent) {
    PropagationResult result = trace(tripEvent.getEquipmentName());
    result.setInitiatingTripEvent(tripEvent);
    return result;
  }

  /**
   * Gets the propagation delay between two equipment items.
   *
   * @param source source equipment name
   * @param target target equipment name
   * @param direct true if directly connected
   * @return delay in seconds
   */
  private double getDelay(String source, String target, boolean direct) {
    String key = source + "->" + target;
    if (customDelays.containsKey(key)) {
      return customDelays.get(key);
    }
    return direct ? DEFAULT_DIRECT_DELAY : DEFAULT_INDIRECT_DELAY;
  }

  /**
   * Classifies the effect of a failure propagation on a downstream equipment.
   *
   * @param source failed equipment name
   * @param target affected equipment name
   * @return description of the effect
   */
  private String classifyEffect(String source, String target) {
    ProcessEquipmentInterface targetEquip = findEquipment(target);
    if (targetEquip == null) {
      return "Unknown effect";
    }

    String type = targetEquip.getClass().getSimpleName().toLowerCase();
    if (type.contains("compressor")) {
      return "Loss of feed supply — compressor may surge or trip on low suction pressure";
    } else if (type.contains("separator")) {
      return "Feed disruption — separator levels will change, possible high/low level trip";
    } else if (type.contains("cooler") || type.contains("heater")
        || type.contains("heatexchanger")) {
      return "Flow disruption — heat duty mismatch, possible temperature excursion";
    } else if (type.contains("valve")) {
      return "Flow disruption — valve may need to close/reposition";
    } else if (type.contains("pump")) {
      return "Loss of feed — pump may run dry, risk of mechanical damage";
    } else if (type.contains("mixer") || type.contains("splitter")) {
      return "Flow redistribution — downstream composition and flow changes";
    } else if (type.contains("stream")) {
      return "Flow disruption — stream conditions will change";
    } else if (type.contains("pipe")) {
      return "Flow disruption — pipeline pressure/flow transient";
    }
    return "Affected by upstream failure — process conditions will deviate";
  }

  /**
   * Assesses the impact level on a given equipment.
   *
   * @param equipmentName equipment name
   * @return impact level
   */
  private PropagationStep.ImpactLevel assessImpact(String equipmentName) {
    ProcessEquipmentInterface equipment = findEquipment(equipmentName);
    if (equipment == null) {
      return PropagationStep.ImpactLevel.LOW;
    }

    String type = equipment.getClass().getSimpleName().toLowerCase();
    if (type.contains("compressor") || type.contains("pump")) {
      return PropagationStep.ImpactLevel.HIGH;
    } else if (type.contains("separator") || type.contains("column")) {
      return PropagationStep.ImpactLevel.HIGH;
    } else if (type.contains("heatexchanger") || type.contains("cooler")
        || type.contains("heater")) {
      return PropagationStep.ImpactLevel.MEDIUM;
    } else if (type.contains("valve") || type.contains("pipe")) {
      return PropagationStep.ImpactLevel.LOW;
    }
    return PropagationStep.ImpactLevel.MEDIUM;
  }

  /**
   * Finds equipment by name in the process system.
   *
   * @param name equipment name
   * @return equipment, or null if not found
   */
  private ProcessEquipmentInterface findEquipment(String name) {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq.getName().equals(name)) {
        return eq;
      }
    }
    return null;
  }

  /**
   * A single step in the failure propagation chain.
   */
  public static class PropagationStep implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Impact level of a propagation step.
     */
    public enum ImpactLevel {
      /** Low impact — minor process deviation. */
      LOW,
      /** Medium impact — significant process deviation, possible alarm. */
      MEDIUM,
      /** High impact — potential equipment trip or safety concern. */
      HIGH,
      /** Critical impact — equipment damage or safety shutdown. */
      CRITICAL
    }

    private final String equipmentName;
    private final double estimatedDelaySeconds;
    private final int cascadeDepth;
    private final String effect;
    private final ImpactLevel impactLevel;

    /**
     * Creates a propagation step.
     *
     * @param equipmentName name of the affected equipment
     * @param estimatedDelaySeconds estimated delay from the initial failure
     * @param cascadeDepth depth in the cascade chain (0 = initial failure)
     * @param effect description of the effect on this equipment
     * @param impactLevel impact level
     */
    public PropagationStep(String equipmentName, double estimatedDelaySeconds, int cascadeDepth,
        String effect, ImpactLevel impactLevel) {
      this.equipmentName = equipmentName;
      this.estimatedDelaySeconds = estimatedDelaySeconds;
      this.cascadeDepth = cascadeDepth;
      this.effect = effect;
      this.impactLevel = impactLevel;
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
     * Gets the estimated delay from the initial failure in seconds.
     *
     * @return delay in seconds
     */
    public double getEstimatedDelaySeconds() {
      return estimatedDelaySeconds;
    }

    /**
     * Gets the cascade depth.
     *
     * @return depth (0 = initial failure)
     */
    public int getCascadeDepth() {
      return cascadeDepth;
    }

    /**
     * Gets the effect description.
     *
     * @return effect description
     */
    public String getEffect() {
      return effect;
    }

    /**
     * Gets the impact level.
     *
     * @return impact level
     */
    public ImpactLevel getImpactLevel() {
      return impactLevel;
    }

    @Override
    public String toString() {
      return String.format("[%.0fs] %s (depth=%d, impact=%s): %s", estimatedDelaySeconds,
          equipmentName, cascadeDepth, impactLevel, effect);
    }
  }

  /**
   * Result of a failure propagation trace.
   */
  public static class PropagationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String initiatingEquipment;
    private final List<PropagationStep> steps;
    private final List<String> equipmentToMonitor;
    private double totalProductionLossPercent;
    private TripEvent initiatingTripEvent;

    /**
     * Creates a propagation result.
     *
     * @param initiatingEquipment the equipment that failed first
     */
    public PropagationResult(String initiatingEquipment) {
      this.initiatingEquipment = initiatingEquipment;
      this.steps = new ArrayList<>();
      this.equipmentToMonitor = new ArrayList<>();
      this.totalProductionLossPercent = 0.0;
    }

    /**
     * Adds a propagation step.
     *
     * @param step the step to add
     */
    void addStep(PropagationStep step) {
      steps.add(step);
    }

    /**
     * Adds an equipment name to the monitoring list.
     *
     * @param equipmentName equipment to monitor
     */
    void addEquipmentToMonitor(String equipmentName) {
      if (!equipmentToMonitor.contains(equipmentName)) {
        equipmentToMonitor.add(equipmentName);
      }
    }

    /**
     * Sets the total production loss percentage.
     *
     * @param loss production loss as percentage (0-100)
     */
    void setTotalProductionLossPercent(double loss) {
      this.totalProductionLossPercent = loss;
    }

    /**
     * Sets the initiating trip event.
     *
     * @param event the trip event
     */
    void setInitiatingTripEvent(TripEvent event) {
      this.initiatingTripEvent = event;
    }

    /**
     * Gets the name of the equipment that initiated the failure.
     *
     * @return initiating equipment name
     */
    public String getInitiatingEquipment() {
      return initiatingEquipment;
    }

    /**
     * Gets the ordered list of propagation steps.
     *
     * @return unmodifiable list of steps, ordered by estimated delay
     */
    public List<PropagationStep> getSteps() {
      return Collections.unmodifiableList(steps);
    }

    /**
     * Gets equipment that should be monitored but may not be directly affected.
     *
     * @return unmodifiable list of equipment names
     */
    public List<String> getEquipmentToMonitor() {
      return Collections.unmodifiableList(equipmentToMonitor);
    }

    /**
     * Gets the total production loss as a percentage.
     *
     * @return production loss percentage
     */
    public double getTotalProductionLossPercent() {
      return totalProductionLossPercent;
    }

    /**
     * Gets the initiating trip event, if set.
     *
     * @return trip event, or null
     */
    public TripEvent getInitiatingTripEvent() {
      return initiatingTripEvent;
    }

    /**
     * Gets the number of affected equipment items (excluding the initial failure).
     *
     * @return count of affected equipment
     */
    public int getAffectedCount() {
      return Math.max(0, steps.size() - 1);
    }

    /**
     * Gets the maximum cascade depth.
     *
     * @return maximum depth
     */
    public int getMaxCascadeDepth() {
      int max = 0;
      for (PropagationStep step : steps) {
        if (step.getCascadeDepth() > max) {
          max = step.getCascadeDepth();
        }
      }
      return max;
    }

    /**
     * Returns a JSON representation.
     *
     * @return JSON string
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{");
      sb.append("\"initiatingEquipment\": \"").append(initiatingEquipment).append("\", ");
      if (initiatingTripEvent != null) {
        sb.append("\"initiatingTrip\": ").append(initiatingTripEvent.toJson()).append(", ");
      }
      sb.append("\"totalProductionLossPercent\": ").append(totalProductionLossPercent).append(", ");
      sb.append("\"affectedCount\": ").append(getAffectedCount()).append(", ");
      sb.append("\"maxCascadeDepth\": ").append(getMaxCascadeDepth()).append(", ");
      sb.append("\"steps\": [");
      for (int i = 0; i < steps.size(); i++) {
        PropagationStep step = steps.get(i);
        if (i > 0) {
          sb.append(", ");
        }
        sb.append("{");
        sb.append("\"equipmentName\": \"").append(step.getEquipmentName()).append("\", ");
        sb.append("\"delaySeconds\": ").append(step.getEstimatedDelaySeconds()).append(", ");
        sb.append("\"cascadeDepth\": ").append(step.getCascadeDepth()).append(", ");
        sb.append("\"impactLevel\": \"").append(step.getImpactLevel().name()).append("\", ");
        sb.append("\"effect\": \"").append(escapeJson(step.getEffect())).append("\"");
        sb.append("}");
      }
      sb.append("], ");
      sb.append("\"equipmentToMonitor\": [");
      for (int i = 0; i < equipmentToMonitor.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append("\"").append(equipmentToMonitor.get(i)).append("\"");
      }
      sb.append("]");
      sb.append("}");
      return sb.toString();
    }

    /**
     * Returns a human-readable text summary.
     *
     * @return text summary
     */
    public String toTextSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Failure Propagation Trace ===\n");
      sb.append("Initiating equipment: ").append(initiatingEquipment).append("\n");
      if (initiatingTripEvent != null) {
        sb.append("Trip cause: ").append(initiatingTripEvent.getParameterName()).append(" ")
            .append(initiatingTripEvent.isHighTrip() ? "HIGH" : "LOW").append(" (")
            .append(String.format("%.2f", initiatingTripEvent.getActualValue()))
            .append(" vs threshold ")
            .append(String.format("%.2f", initiatingTripEvent.getThreshold())).append(")\n");
      }
      sb.append("Total affected: ").append(getAffectedCount()).append(" equipment\n");
      sb.append("Production impact: ")
          .append(String.format("%.1f%%", totalProductionLossPercent)).append("\n");
      sb.append("\nPropagation sequence:\n");
      for (PropagationStep step : steps) {
        sb.append(step.toString()).append("\n");
      }
      if (!equipmentToMonitor.isEmpty()) {
        sb.append("\nEquipment to monitor:\n");
        for (String eq : equipmentToMonitor) {
          sb.append("  - ").append(eq).append("\n");
        }
      }
      return sb.toString();
    }
  }

  /**
   * Escapes a string for JSON output.
   *
   * @param s string to escape
   * @return escaped string
   */
  private static String escapeJson(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        .replace("\r", "\\r").replace("\t", "\\t");
  }
}
