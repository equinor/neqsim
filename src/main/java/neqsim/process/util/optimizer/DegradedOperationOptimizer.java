package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Optimizer for finding optimal operating conditions during degraded operation.
 *
 * <p>
 * When equipment fails or operates at reduced capacity, this optimizer finds the best operating
 * point for the remaining plant to maximize production while respecting constraints.
 * </p>
 *
 * <h2>Key Capabilities</h2>
 * <ul>
 * <li>Find maximum throughput with failed equipment</li>
 * <li>Optimize setpoints for remaining equipment</li>
 * <li>Compare operating modes (run degraded vs. partial shutdown)</li>
 * <li>Generate recovery plan with recommended actions</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * DegradedOperationOptimizer optimizer = new DegradedOperationOptimizer(processSystem);
 * optimizer.setFeedStreamName("Well Feed");
 * 
 * // Optimize with compressor down
 * DegradedOperationResult result = optimizer.optimizeWithEquipmentDown("HP Compressor");
 * 
 * // Get recommended setpoints
 * Map<String, Double> setpoints = result.getOptimizedSetpoints();
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DegradedOperationOptimizer implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(DegradedOperationOptimizer.class);

  /** The process system to optimize. */
  private ProcessSystem processSystem;

  /** Name of the feed stream. */
  private String feedStreamName;

  /** Name of the product stream. */
  private String productStreamName;

  /** Tolerance for optimization convergence. */
  private double tolerance = 0.01;

  /** Maximum optimization iterations. */
  private int maxIterations = 50;

  /** Minimum flow rate as fraction of baseline. */
  private double minFlowFraction = 0.1;

  /** Flow rate step size for search. */
  private double flowStepFraction = 0.1;

  /** Whether to optimize compressor speeds. */
  private boolean optimizeCompressorSpeeds = true;

  /** Whether to optimize temperatures. */
  private boolean optimizeTemperatures = false;

  /** Baseline production rate (cached). */
  private transient Double baselineProduction = null;

  /** Baseline power consumption (cached). */
  private transient Double baselinePower = null;

  /**
   * Operating mode options during degraded operation.
   */
  public enum OperatingMode {
    /** Normal operation - full capacity. */
    NORMAL,
    /** Reduced throughput to match constraints. */
    REDUCED_CAPACITY,
    /** Partial shutdown - some trains/sections offline. */
    PARTIAL_SHUTDOWN,
    /** Full shutdown of the plant. */
    FULL_SHUTDOWN,
    /** Bypass failed equipment and continue. */
    BYPASS_MODE,
    /** Switch to standby/spare equipment. */
    STANDBY_MODE
  }

  /**
   * Creates a degraded operation optimizer for the given process system.
   *
   * @param processSystem the process system to optimize
   */
  public DegradedOperationOptimizer(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    autoDetectStreams();
  }

  /**
   * Creates optimizer with specified streams.
   *
   * @param processSystem the process system
   * @param feedStreamName name of the feed stream
   * @param productStreamName name of the product stream
   */
  public DegradedOperationOptimizer(ProcessSystem processSystem, String feedStreamName,
      String productStreamName) {
    this.processSystem = processSystem;
    this.feedStreamName = feedStreamName;
    this.productStreamName = productStreamName;
  }

  private void autoDetectStreams() {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof StreamInterface) {
        if (feedStreamName == null) {
          feedStreamName = unit.getName();
        }
        productStreamName = unit.getName();
      }
    }
  }

  // Configuration methods

  /**
   * Sets the feed stream name.
   *
   * @param name the feed stream name
   * @return this optimizer for chaining
   */
  public DegradedOperationOptimizer setFeedStreamName(String name) {
    this.feedStreamName = name;
    return this;
  }

  /**
   * Sets the product stream name.
   *
   * @param name the product stream name
   * @return this optimizer for chaining
   */
  public DegradedOperationOptimizer setProductStreamName(String name) {
    this.productStreamName = name;
    return this;
  }

  /**
   * Sets the optimization tolerance.
   *
   * @param tolerance the tolerance
   * @return this optimizer for chaining
   */
  public DegradedOperationOptimizer setTolerance(double tolerance) {
    this.tolerance = tolerance;
    return this;
  }

  /**
   * Clears the cached baseline values.
   */
  public void clearCache() {
    baselineProduction = null;
    baselinePower = null;
  }

  // Optimization methods

  /**
   * Optimizes operation with a single equipment down.
   *
   * @param equipmentName name of the failed equipment
   * @return optimization result with recommended setpoints
   */
  public DegradedOperationResult optimizeWithEquipmentDown(String equipmentName) {
    return optimizeWithEquipmentDown(equipmentName, EquipmentFailureMode.trip(equipmentName));
  }

  /**
   * Optimizes operation with a specific failure mode.
   *
   * @param equipmentName name of the failed equipment
   * @param failureMode the failure mode
   * @return optimization result
   */
  public DegradedOperationResult optimizeWithEquipmentDown(String equipmentName,
      EquipmentFailureMode failureMode) {
    long startTime = System.currentTimeMillis();

    DegradedOperationResult result = new DegradedOperationResult();
    result.setFailedEquipment(equipmentName);
    result.setFailureMode(failureMode);

    try {
      // Calculate baseline if not cached
      if (baselineProduction == null) {
        processSystem.run();
        baselineProduction = getProductionRate(processSystem);
        baselinePower = getTotalPower(processSystem);
      }
      result.setBaselineProduction(baselineProduction);
      result.setBaselinePower(baselinePower);

      // Create a copy for optimization
      ProcessSystem optimizedProcess = processSystem.copy();

      // Apply failure
      applyFailure(optimizedProcess, equipmentName, failureMode);

      // Find optimal flow rate
      OptimizationPoint optimal = findOptimalFlowRate(optimizedProcess, baselineProduction);

      result.setOptimalFlowRate(optimal.flowRate);
      result.setOptimalProduction(optimal.production);
      result.setOptimalPower(optimal.power);
      result.setOptimizedSetpoints(optimal.setpoints);
      result.setOperatingMode(determineOperatingMode(optimal, baselineProduction));

      // Calculate metrics
      result.calculateMetrics();
      result.setConverged(true);

    } catch (Exception e) {
      logger.error("Optimization failed for {}: {}", equipmentName, e.getMessage());
      result.setConverged(false);
      result.setNotes("Optimization failed: " + e.getMessage());
    }

    result.setComputeTimeMs(System.currentTimeMillis() - startTime);
    return result;
  }

  /**
   * Optimizes operation with multiple equipment failures.
   *
   * @param failedEquipment list of failed equipment names
   * @return optimization result
   */
  public DegradedOperationResult optimizeWithMultipleFailures(List<String> failedEquipment) {
    long startTime = System.currentTimeMillis();

    DegradedOperationResult result = new DegradedOperationResult();
    result.setFailedEquipment(String.join(", ", failedEquipment));

    try {
      // Calculate baseline
      if (baselineProduction == null) {
        processSystem.run();
        baselineProduction = getProductionRate(processSystem);
        baselinePower = getTotalPower(processSystem);
      }
      result.setBaselineProduction(baselineProduction);
      result.setBaselinePower(baselinePower);

      // Create copy and apply all failures
      ProcessSystem optimizedProcess = processSystem.copy();
      for (String equipName : failedEquipment) {
        applyFailure(optimizedProcess, equipName, EquipmentFailureMode.trip(equipName));
      }

      // Find optimal flow rate
      OptimizationPoint optimal = findOptimalFlowRate(optimizedProcess, baselineProduction);

      result.setOptimalFlowRate(optimal.flowRate);
      result.setOptimalProduction(optimal.production);
      result.setOptimalPower(optimal.power);
      result.setOptimizedSetpoints(optimal.setpoints);
      result.setOperatingMode(determineOperatingMode(optimal, baselineProduction));

      result.calculateMetrics();
      result.setConverged(true);

    } catch (Exception e) {
      logger.error("Multi-failure optimization failed: {}", e.getMessage());
      result.setConverged(false);
    }

    result.setComputeTimeMs(System.currentTimeMillis() - startTime);
    return result;
  }

  /**
   * Finds the best operating mode for the given failure scenario.
   *
   * @param failedEquipment name of failed equipment
   * @return map of operating mode to expected production
   */
  public Map<OperatingMode, Double> evaluateOperatingModes(String failedEquipment) {
    Map<OperatingMode, Double> modes = new HashMap<OperatingMode, Double>();

    // Full shutdown always produces 0
    modes.put(OperatingMode.FULL_SHUTDOWN, 0.0);

    // Try reduced capacity
    DegradedOperationResult reduced = optimizeWithEquipmentDown(failedEquipment);
    modes.put(OperatingMode.REDUCED_CAPACITY, reduced.getOptimalProduction());

    // Try bypass mode (if applicable)
    DegradedOperationResult bypass =
        optimizeWithEquipmentDown(failedEquipment, EquipmentFailureMode.bypassed());
    modes.put(OperatingMode.BYPASS_MODE, bypass.getOptimalProduction());

    return modes;
  }

  /**
   * Creates a recovery plan with recommended actions.
   *
   * @param failedEquipment name of failed equipment
   * @return recovery plan
   */
  public RecoveryPlan createRecoveryPlan(String failedEquipment) {
    RecoveryPlan plan = new RecoveryPlan(failedEquipment);

    // Get optimized operation
    DegradedOperationResult optimized = optimizeWithEquipmentDown(failedEquipment);

    // Add immediate actions
    plan.addAction(new RecoveryAction(RecoveryAction.Phase.IMMEDIATE,
        "Reduce feed rate to " + String.format("%.0f", optimized.getOptimalFlowRate()) + " kg/hr",
        0.0));

    // Add setpoint changes
    for (Map.Entry<String, Double> entry : optimized.getOptimizedSetpoints().entrySet()) {
      plan.addAction(new RecoveryAction(RecoveryAction.Phase.IMMEDIATE,
          "Adjust " + entry.getKey() + " to " + String.format("%.2f", entry.getValue()), 0.0));
    }

    // Add monitoring actions
    plan.addAction(new RecoveryAction(RecoveryAction.Phase.STABILIZATION,
        "Monitor process stability for 30 minutes", 0.5));

    // Add repair actions
    EquipmentFailureMode mode = EquipmentFailureMode.trip(failedEquipment);
    plan.addAction(new RecoveryAction(RecoveryAction.Phase.REPAIR,
        "Repair " + failedEquipment + " (estimated " + mode.getMttr() + " hours)", mode.getMttr()));

    // Add restoration actions
    plan.addAction(new RecoveryAction(RecoveryAction.Phase.RESTORATION,
        "Gradually increase feed rate to baseline", 1.0));

    plan.setExpectedProductionDuringRecovery(optimized.getOptimalProduction());
    plan.setEstimatedRecoveryTime(mode.getMttr() + 2.0); // Add buffer

    return plan;
  }

  // Private helper methods

  private void applyFailure(ProcessSystem process, String equipmentName,
      EquipmentFailureMode mode) {
    ProcessEquipmentInterface equipment = process.getUnit(equipmentName);
    if (equipment == null) {
      return;
    }

    if (mode.isCompleteFailure()) {
      equipment.setSpecification("FAILED");

      if (equipment instanceof neqsim.process.equipment.ProcessEquipmentBaseClass) {
        ((neqsim.process.equipment.ProcessEquipmentBaseClass) equipment).isActive(false);
        ((neqsim.process.equipment.ProcessEquipmentBaseClass) equipment)
            .setCapacityAnalysisEnabled(false);
      }

      // Equipment-specific handling
      if (equipment instanceof Compressor) {
        Compressor comp = (Compressor) equipment;
        comp.setOutletPressure(comp.getInletStream().getPressure());
      } else if (equipment instanceof Pump) {
        Pump pump = (Pump) equipment;
        pump.setOutletPressure(pump.getInletStream().getPressure());
      } else if (equipment instanceof Heater) {
        ((Heater) equipment)
            .setOutTemperature(((Heater) equipment).getInletStream().getTemperature());
      } else if (equipment instanceof Cooler) {
        ((Cooler) equipment)
            .setOutTemperature(((Cooler) equipment).getInletStream().getTemperature());
      }
    }
  }

  private OptimizationPoint findOptimalFlowRate(ProcessSystem process, double baselineFlow) {
    OptimizationPoint best = new OptimizationPoint();
    best.flowRate = 0.0;
    best.production = 0.0;
    best.power = 0.0;

    ProcessEquipmentInterface feedUnit = process.getUnit(feedStreamName);
    if (!(feedUnit instanceof StreamInterface)) {
      return best;
    }
    StreamInterface feed = (StreamInterface) feedUnit;

    // Search from high to low flow rates
    double[] flowFactors = {1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1};

    for (double factor : flowFactors) {
      double testFlow = baselineFlow * factor;
      feed.setFlowRate(testFlow, "kg/hr");

      try {
        process.run();
        double production = getProductionRate(process);
        double power = getTotalPower(process);

        if (production > best.production) {
          best.flowRate = testFlow;
          best.production = production;
          best.power = power;
          best.setpoints = captureSetpoints(process);
        }
      } catch (Exception e) {
        // This flow rate doesn't work, continue
        logger.debug("Flow rate {} failed: {}", testFlow, e.getMessage());
      }
    }

    return best;
  }

  private Map<String, Double> captureSetpoints(ProcessSystem process) {
    Map<String, Double> setpoints = new HashMap<String, Double>();

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface) {
        setpoints.put(unit.getName() + " flow (kg/hr)",
            ((StreamInterface) unit).getFlowRate("kg/hr"));
      } else if (unit instanceof Compressor) {
        Compressor comp = (Compressor) unit;
        setpoints.put(unit.getName() + " outlet pressure (bara)",
            comp.getOutletStream().getPressure("bara"));
      } else if (unit instanceof ThrottlingValve) {
        ThrottlingValve valve = (ThrottlingValve) unit;
        setpoints.put(unit.getName() + " outlet pressure (bara)",
            valve.getOutletStream().getPressure("bara"));
      }
    }

    return setpoints;
  }

  private double getProductionRate(ProcessSystem process) {
    if (productStreamName == null) {
      return 0.0;
    }
    ProcessEquipmentInterface unit = process.getUnit(productStreamName);
    if (unit instanceof StreamInterface) {
      return ((StreamInterface) unit).getFlowRate("kg/hr");
    }
    if (unit instanceof neqsim.process.equipment.TwoPortInterface) {
      StreamInterface outlet = ((neqsim.process.equipment.TwoPortInterface) unit).getOutletStream();
      if (outlet != null) {
        return outlet.getFlowRate("kg/hr");
      }
    }
    return 0.0;
  }

  private double getTotalPower(ProcessSystem process) {
    double totalPower = 0.0;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Compressor) {
        totalPower += ((Compressor) unit).getPower("kW");
      } else if (unit instanceof Pump) {
        totalPower += ((Pump) unit).getPower();
      }
    }
    return totalPower;
  }

  private OperatingMode determineOperatingMode(OptimizationPoint optimal, double baseline) {
    if (optimal.production <= 0) {
      return OperatingMode.FULL_SHUTDOWN;
    }
    double ratio = optimal.production / baseline;
    if (ratio > 0.95) {
      return OperatingMode.NORMAL;
    } else if (ratio > 0.5) {
      return OperatingMode.REDUCED_CAPACITY;
    } else if (ratio > 0.1) {
      return OperatingMode.PARTIAL_SHUTDOWN;
    }
    return OperatingMode.FULL_SHUTDOWN;
  }

  // Inner classes

  private static class OptimizationPoint {
    double flowRate;
    double production;
    double power;
    Map<String, Double> setpoints = new HashMap<String, Double>();
  }

  /**
   * Recovery action for the recovery plan.
   */
  public static class RecoveryAction implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Recovery phase. */
    public enum Phase {
      IMMEDIATE, STABILIZATION, REPAIR, RESTORATION
    }

    private final Phase phase;
    private final String description;
    private final double estimatedDuration; // hours

    /**
     * Creates a recovery action.
     *
     * @param phase the recovery phase
     * @param description action description
     * @param estimatedDuration duration in hours
     */
    public RecoveryAction(Phase phase, String description, double estimatedDuration) {
      this.phase = phase;
      this.description = description;
      this.estimatedDuration = estimatedDuration;
    }

    public Phase getPhase() {
      return phase;
    }

    public String getDescription() {
      return description;
    }

    public double getEstimatedDuration() {
      return estimatedDuration;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s (%.1f hrs)", phase, description, estimatedDuration);
    }
  }

  /**
   * Recovery plan for restoring normal operation.
   */
  public static class RecoveryPlan implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String failedEquipment;
    private final List<RecoveryAction> actions = new ArrayList<RecoveryAction>();
    private double expectedProductionDuringRecovery;
    private double estimatedRecoveryTime;

    /**
     * Creates a recovery plan.
     *
     * @param failedEquipment the failed equipment name
     */
    public RecoveryPlan(String failedEquipment) {
      this.failedEquipment = failedEquipment;
    }

    public void addAction(RecoveryAction action) {
      actions.add(action);
    }

    public List<RecoveryAction> getActions() {
      return new ArrayList<RecoveryAction>(actions);
    }

    public String getFailedEquipment() {
      return failedEquipment;
    }

    public double getExpectedProductionDuringRecovery() {
      return expectedProductionDuringRecovery;
    }

    public void setExpectedProductionDuringRecovery(double production) {
      this.expectedProductionDuringRecovery = production;
    }

    public double getEstimatedRecoveryTime() {
      return estimatedRecoveryTime;
    }

    public void setEstimatedRecoveryTime(double hours) {
      this.estimatedRecoveryTime = hours;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Recovery Plan for ").append(failedEquipment).append(" ===\n");
      sb.append(
          String.format("Expected Production: %.0f kg/hr%n", expectedProductionDuringRecovery));
      sb.append(String.format("Estimated Recovery Time: %.1f hours%n%n", estimatedRecoveryTime));
      sb.append("Actions:\n");
      for (RecoveryAction action : actions) {
        sb.append("  ").append(action).append("\n");
      }
      return sb.toString();
    }
  }
}
