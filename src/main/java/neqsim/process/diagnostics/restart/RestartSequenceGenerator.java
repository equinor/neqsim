package neqsim.process.diagnostics.restart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.diagnostics.FailurePropagationTracer;
import neqsim.process.diagnostics.TripEvent;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.topology.ProcessTopologyAnalyzer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generates an optimised restart sequence after a trip or shutdown.
 *
 * <p>
 * Given a process system and a failure propagation trace, this generator determines the safe order
 * in which to restart equipment, what preconditions must be met for each step, and the recommended
 * timing. It answers the question: <em>"How do we restart safely and quickly?"</em>
 * </p>
 *
 * <p>
 * The restart strategy follows these principles:
 * </p>
 * <ol>
 * <li><b>Safety first</b>: Verify all safety systems are ready before restarting any equipment</li>
 * <li><b>Upstream before downstream</b>: Start feed systems before consumers</li>
 * <li><b>Utilities first</b>: Ensure cooling water, lube oil, instrument air are available</li>
 * <li><b>Gradual ramp-up</b>: Use staged load increases to avoid process upsets</li>
 * <li><b>Root cause cleared</b>: Verify the initiating cause has been resolved</li>
 * </ol>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * FailurePropagationTracer tracer = new FailurePropagationTracer(process);
 * FailurePropagationTracer.PropagationResult propagation = tracer.trace("Compressor-1");
 *
 * RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
 * RestartPlan plan = generator.generate(propagation);
 *
 * System.out.println(plan.toTextReport());
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see FailurePropagationTracer
 * @see RestartStep
 */
public class RestartSequenceGenerator implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(RestartSequenceGenerator.class);

  /** Default ramp-up time for rotating equipment (seconds). */
  private static final double DEFAULT_RAMP_UP_TIME = 60.0;

  /** Default stabilisation time between steps (seconds). */
  private static final double DEFAULT_STABILISATION_TIME = 30.0;

  /** Process system. */
  private final ProcessSystem processSystem;

  /** Topology analyzer. */
  private transient ProcessTopologyAnalyzer topologyAnalyzer;

  /** Custom ramp-up times keyed by equipment name. */
  private final Map<String, Double> customRampUpTimes;

  /** Custom preconditions keyed by equipment name. */
  private final Map<String, String> customPreconditions;

  /**
   * Creates a restart sequence generator.
   *
   * @param processSystem the process system
   * @throws IllegalArgumentException if processSystem is null
   */
  public RestartSequenceGenerator(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = processSystem;
    this.customRampUpTimes = new LinkedHashMap<>();
    this.customPreconditions = new LinkedHashMap<>();
    initializeTopology();
  }

  /**
   * Initializes the topology analyzer.
   */
  private void initializeTopology() {
    this.topologyAnalyzer = new ProcessTopologyAnalyzer(processSystem);
    topologyAnalyzer.buildTopology();
  }

  /**
   * Sets a custom ramp-up time for a specific equipment.
   *
   * @param equipmentName equipment name
   * @param rampUpSeconds ramp-up time in seconds
   */
  public void setCustomRampUpTime(String equipmentName, double rampUpSeconds) {
    customRampUpTimes.put(equipmentName, rampUpSeconds);
  }

  /**
   * Sets a custom precondition for a specific equipment.
   *
   * @param equipmentName equipment name
   * @param precondition precondition description
   */
  public void setCustomPrecondition(String equipmentName, String precondition) {
    customPreconditions.put(equipmentName, precondition);
  }

  /**
   * Generates a restart plan from a failure propagation result.
   *
   * <p>
   * The restart order is the reverse of the propagation order: the last equipment to be affected
   * is restarted first (it's furthest downstream), and the initially failed equipment is restarted
   * last (after verifying root cause is resolved).
   * </p>
   *
   * @param propagation failure propagation result
   * @return restart plan with ordered steps
   */
  public RestartPlan generate(FailurePropagationTracer.PropagationResult propagation) {
    logger.info("Generating restart sequence for failure at: {}",
        propagation.getInitiatingEquipment());

    RestartPlan plan = new RestartPlan(propagation.getInitiatingEquipment());

    if (propagation.getInitiatingTripEvent() != null) {
      plan.setInitiatingTrip(propagation.getInitiatingTripEvent());
    }

    List<FailurePropagationTracer.PropagationStep> steps = propagation.getSteps();
    if (steps.isEmpty()) {
      return plan;
    }

    int stepNum = 1;

    // Step 1: Safety verification
    plan.addStep(new RestartStep(stepNum++, "Safety Systems", "Verify all safety systems active "
        + "(fire & gas, ESD, HIPPS)", null, 0.0, RestartStep.Priority.CRITICAL,
        "Do not proceed until all safety interlocks are confirmed ready"));

    // Step 2: Root cause verification
    String rootCauseNote = "Confirm root cause has been identified and resolved";
    if (propagation.getInitiatingTripEvent() != null) {
      TripEvent trip = propagation.getInitiatingTripEvent();
      rootCauseNote = String.format(
          "Confirm root cause resolved: %s %s trip on %s (value=%.2f, threshold=%.2f)",
          trip.getParameterName(), trip.isHighTrip() ? "HIGH" : "LOW", trip.getEquipmentName(),
          trip.getActualValue(), trip.getThreshold());
    }
    plan.addStep(new RestartStep(stepNum++, propagation.getInitiatingEquipment(),
        "Verify root cause resolved", null, 0.0, RestartStep.Priority.CRITICAL, rootCauseNote));

    // Step 3: Utility systems check
    plan.addStep(new RestartStep(stepNum++, "Utility Systems",
        "Verify utilities available (cooling water, instrument air, lube oil, seal gas)", null, 0.0,
        RestartStep.Priority.HIGH, "All utility supplies must be confirmed before equipment start"));

    // Build restart order: process from upstream to downstream
    // Get the topological order of affected equipment
    List<String> restartOrder = buildTopologicalRestartOrder(steps);

    // Generate restart steps for each affected equipment
    for (String equipmentName : restartOrder) {
      String action = getRestartAction(equipmentName);
      String precondition = getPrecondition(equipmentName);
      double delay = getRampUpTime(equipmentName);
      RestartStep.Priority priority = getRestartPriority(equipmentName, propagation);
      String notes = getRestartNotes(equipmentName);

      plan.addStep(
          new RestartStep(stepNum++, equipmentName, action, precondition, delay, priority, notes));
    }

    // Final step: system verification
    plan.addStep(new RestartStep(stepNum++, "Process System",
        "Verify system operating normally — check mass balance, temperatures, pressures", null,
        DEFAULT_STABILISATION_TIME * 2, RestartStep.Priority.NORMAL,
        "Monitor for 15 minutes before confirming restart complete"));

    double totalTime = 0.0;
    for (RestartStep step : plan.getSteps()) {
      totalTime += step.getRecommendedDelaySeconds();
    }
    plan.setEstimatedTotalTimeSeconds(totalTime);

    logger.info("Generated restart plan: {} steps, estimated {}s", plan.getSteps().size(),
        String.format("%.0f", totalTime));

    return plan;
  }

  /**
   * Generates a restart plan from a list of tripped equipment names.
   *
   * @param trippedEquipment list of equipment names that tripped
   * @return restart plan
   */
  public RestartPlan generate(List<String> trippedEquipment) {
    if (trippedEquipment == null || trippedEquipment.isEmpty()) {
      return new RestartPlan("unknown");
    }

    RestartPlan plan = new RestartPlan(trippedEquipment.get(0));
    int stepNum = 1;

    // Safety check
    plan.addStep(new RestartStep(stepNum++, "Safety Systems",
        "Verify all safety systems active", null, 0.0, RestartStep.Priority.CRITICAL, null));

    // Root cause
    plan.addStep(new RestartStep(stepNum++, trippedEquipment.get(0), "Verify root cause resolved",
        null, 0.0, RestartStep.Priority.CRITICAL, null));

    // Build upstream-first order
    List<String> ordered = orderByTopology(trippedEquipment);

    for (String equipmentName : ordered) {
      String action = getRestartAction(equipmentName);
      String precondition = getPrecondition(equipmentName);
      double delay = getRampUpTime(equipmentName);

      plan.addStep(new RestartStep(stepNum++, equipmentName, action, precondition, delay,
          RestartStep.Priority.NORMAL, null));
    }

    return plan;
  }

  /**
   * Builds a topological restart order from propagation steps.
   *
   * <p>
   * Restart order is upstream-first: equipment that feeds others is started before consumers.
   * </p>
   *
   * @param propagationSteps failure propagation steps
   * @return ordered list of equipment names for restart
   */
  private List<String> buildTopologicalRestartOrder(
      List<FailurePropagationTracer.PropagationStep> propagationSteps) {
    // Collect all affected equipment names
    List<String> names = new ArrayList<>();
    for (FailurePropagationTracer.PropagationStep step : propagationSteps) {
      names.add(step.getEquipmentName());
    }
    return orderByTopology(names);
  }

  /**
   * Orders equipment by topological position (upstream first).
   *
   * @param equipmentNames equipment names to order
   * @return ordered list
   */
  private List<String> orderByTopology(List<String> equipmentNames) {
    Map<String, Integer> positionMap = new LinkedHashMap<>();
    List<ProcessEquipmentInterface> allOps = processSystem.getUnitOperations();
    for (int i = 0; i < allOps.size(); i++) {
      positionMap.put(allOps.get(i).getName(), i);
    }

    List<String> ordered = new ArrayList<>(equipmentNames);
    Collections.sort(ordered, new java.util.Comparator<String>() {
      @Override
      public int compare(String a, String b) {
        Integer posA = positionMap.get(a);
        Integer posB = positionMap.get(b);
        if (posA == null) {
          posA = Integer.MAX_VALUE;
        }
        if (posB == null) {
          posB = Integer.MAX_VALUE;
        }
        return posA.compareTo(posB);
      }
    });
    return ordered;
  }

  /**
   * Gets the restart action description for an equipment type.
   *
   * @param equipmentName equipment name
   * @return action description
   */
  private String getRestartAction(String equipmentName) {
    ProcessEquipmentInterface equipment = findEquipment(equipmentName);
    if (equipment == null) {
      return "Restart equipment";
    }

    String type = equipment.getClass().getSimpleName().toLowerCase();
    if (type.contains("compressor")) {
      return "Start compressor — verify lube oil pressure, seal gas, then ramp speed gradually";
    } else if (type.contains("pump")) {
      return "Start pump — verify suction pressure adequate, open discharge valve slowly";
    } else if (type.contains("separator")) {
      return "Bring separator online — establish levels, verify instruments, open inlet valve";
    } else if (type.contains("cooler") || type.contains("heater")) {
      return "Start heating/cooling — establish utility flow, ramp to setpoint gradually";
    } else if (type.contains("heatexchanger")) {
      return "Bring heat exchanger online — establish process and utility flows";
    } else if (type.contains("valve")) {
      return "Position valve — set to normal operating position";
    } else if (type.contains("column") || type.contains("distillation")) {
      return "Restart column — establish reflux, ramp feed rate, monitor tray temperatures";
    } else if (type.contains("pipe")) {
      return "Establish pipeline flow — open inlet/outlet valves, verify pressure";
    } else if (type.contains("stream")) {
      return "Establish flow — open valves, verify flow rate and conditions";
    }
    return "Restart equipment per operating procedure";
  }

  /**
   * Gets the precondition for restarting an equipment.
   *
   * @param equipmentName equipment name
   * @return precondition description, or null
   */
  private String getPrecondition(String equipmentName) {
    if (customPreconditions.containsKey(equipmentName)) {
      return customPreconditions.get(equipmentName);
    }

    ProcessEquipmentInterface equipment = findEquipment(equipmentName);
    if (equipment == null) {
      return null;
    }

    String type = equipment.getClass().getSimpleName().toLowerCase();
    if (type.contains("compressor")) {
      return "Lube oil pressure >2 barg, seal gas available, suction pressure stable";
    } else if (type.contains("pump")) {
      return "Suction pressure above NPSH requirement, discharge valve partially open";
    } else if (type.contains("separator")) {
      return "Upstream flow available, downstream equipment ready to receive";
    } else if (type.contains("column") || type.contains("distillation")) {
      return "Reboiler/condenser utilities available, reflux drum level established";
    }
    return null;
  }

  /**
   * Gets the ramp-up time for an equipment.
   *
   * @param equipmentName equipment name
   * @return ramp-up time in seconds
   */
  private double getRampUpTime(String equipmentName) {
    if (customRampUpTimes.containsKey(equipmentName)) {
      return customRampUpTimes.get(equipmentName);
    }

    ProcessEquipmentInterface equipment = findEquipment(equipmentName);
    if (equipment == null) {
      return DEFAULT_STABILISATION_TIME;
    }

    String type = equipment.getClass().getSimpleName().toLowerCase();
    if (type.contains("compressor")) {
      return 120.0; // 2 minutes for compressor ramp-up
    } else if (type.contains("pump")) {
      return 30.0;
    } else if (type.contains("separator")) {
      return 60.0; // time to establish levels
    } else if (type.contains("column") || type.contains("distillation")) {
      return 300.0; // 5 minutes for column
    } else if (type.contains("heatexchanger") || type.contains("cooler")
        || type.contains("heater")) {
      return 60.0;
    }
    return DEFAULT_STABILISATION_TIME;
  }

  /**
   * Gets the restart priority for an equipment based on its role in the propagation.
   *
   * @param equipmentName equipment name
   * @param propagation the failure propagation result
   * @return restart priority
   */
  private RestartStep.Priority getRestartPriority(String equipmentName,
      FailurePropagationTracer.PropagationResult propagation) {
    if (equipmentName.equals(propagation.getInitiatingEquipment())) {
      return RestartStep.Priority.HIGH;
    }
    for (FailurePropagationTracer.PropagationStep step : propagation.getSteps()) {
      if (step.getEquipmentName().equals(equipmentName)) {
        switch (step.getImpactLevel()) {
          case CRITICAL:
            return RestartStep.Priority.CRITICAL;
          case HIGH:
            return RestartStep.Priority.HIGH;
          case MEDIUM:
            return RestartStep.Priority.NORMAL;
          default:
            return RestartStep.Priority.LOW;
        }
      }
    }
    return RestartStep.Priority.NORMAL;
  }

  /**
   * Gets notes for restarting an equipment.
   *
   * @param equipmentName equipment name
   * @return notes, or null
   */
  private String getRestartNotes(String equipmentName) {
    ProcessEquipmentInterface equipment = findEquipment(equipmentName);
    if (equipment == null) {
      return null;
    }

    String type = equipment.getClass().getSimpleName().toLowerCase();
    if (type.contains("compressor")) {
      return "Monitor vibration and discharge temperature during ramp-up. "
          + "Keep anti-surge valve open until stable operation confirmed.";
    } else if (type.contains("pump")) {
      return "Verify no cavitation (check discharge pressure oscillation).";
    } else if (type.contains("separator")) {
      return "Monitor liquid levels closely during initial flow establishment.";
    }
    return null;
  }

  /**
   * Finds equipment by name.
   *
   * @param name equipment name
   * @return equipment or null
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
   * A complete restart plan with ordered steps and metadata.
   */
  public static class RestartPlan implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String initiatingEquipment;
    private final List<RestartStep> steps;
    private double estimatedTotalTimeSeconds;
    private TripEvent initiatingTrip;

    /**
     * Creates a restart plan.
     *
     * @param initiatingEquipment the equipment that caused the shutdown
     */
    public RestartPlan(String initiatingEquipment) {
      this.initiatingEquipment = initiatingEquipment;
      this.steps = new ArrayList<>();
      this.estimatedTotalTimeSeconds = 0.0;
    }

    /**
     * Adds a step to the plan.
     *
     * @param step the restart step
     */
    void addStep(RestartStep step) {
      steps.add(step);
    }

    /**
     * Sets the estimated total time.
     *
     * @param seconds total time in seconds
     */
    void setEstimatedTotalTimeSeconds(double seconds) {
      this.estimatedTotalTimeSeconds = seconds;
    }

    /**
     * Sets the initiating trip event.
     *
     * @param trip the trip event
     */
    void setInitiatingTrip(TripEvent trip) {
      this.initiatingTrip = trip;
    }

    /**
     * Gets the initiating equipment name.
     *
     * @return equipment name
     */
    public String getInitiatingEquipment() {
      return initiatingEquipment;
    }

    /**
     * Gets all restart steps in order.
     *
     * @return unmodifiable list of steps
     */
    public List<RestartStep> getSteps() {
      return Collections.unmodifiableList(steps);
    }

    /**
     * Gets the estimated total restart time in seconds.
     *
     * @return total time in seconds
     */
    public double getEstimatedTotalTimeSeconds() {
      return estimatedTotalTimeSeconds;
    }

    /**
     * Gets the estimated total restart time in minutes.
     *
     * @return total time in minutes
     */
    public double getEstimatedTotalTimeMinutes() {
      return estimatedTotalTimeSeconds / 60.0;
    }

    /**
     * Gets the initiating trip event, if available.
     *
     * @return trip event or null
     */
    public TripEvent getInitiatingTrip() {
      return initiatingTrip;
    }

    /**
     * Gets the number of steps.
     *
     * @return step count
     */
    public int getStepCount() {
      return steps.size();
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
      sb.append("\"estimatedTotalTimeMinutes\": ")
          .append(String.format("%.1f", getEstimatedTotalTimeMinutes())).append(", ");
      sb.append("\"stepCount\": ").append(steps.size()).append(", ");
      sb.append("\"steps\": [");
      for (int i = 0; i < steps.size(); i++) {
        RestartStep step = steps.get(i);
        if (i > 0) {
          sb.append(", ");
        }
        sb.append("{");
        sb.append("\"sequenceNumber\": ").append(step.getSequenceNumber()).append(", ");
        sb.append("\"equipmentName\": \"").append(escapeJson(step.getEquipmentName()))
            .append("\", ");
        sb.append("\"action\": \"").append(escapeJson(step.getAction())).append("\", ");
        if (step.getPrecondition() != null) {
          sb.append("\"precondition\": \"").append(escapeJson(step.getPrecondition()))
              .append("\", ");
        }
        sb.append("\"delaySeconds\": ").append(step.getRecommendedDelaySeconds()).append(", ");
        sb.append("\"priority\": \"").append(step.getPriority().name()).append("\"");
        if (step.getNotes() != null) {
          sb.append(", \"notes\": \"").append(escapeJson(step.getNotes())).append("\"");
        }
        sb.append("}");
      }
      sb.append("]");
      sb.append("}");
      return sb.toString();
    }

    /**
     * Returns a human-readable restart plan.
     *
     * @return text report
     */
    public String toTextReport() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Restart Plan ===\n");
      sb.append("Initiating equipment: ").append(initiatingEquipment).append("\n");
      if (initiatingTrip != null) {
        sb.append("Trip cause: ").append(initiatingTrip.getParameterName()).append(" ")
            .append(initiatingTrip.isHighTrip() ? "HIGH" : "LOW").append("\n");
      }
      sb.append("Estimated total time: ")
          .append(String.format("%.1f minutes\n", getEstimatedTotalTimeMinutes()));
      sb.append("Number of steps: ").append(steps.size()).append("\n\n");

      for (RestartStep step : steps) {
        sb.append(step.toString()).append("\n");
      }
      return sb.toString();
    }

    /**
     * Escapes a string for JSON.
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
}
