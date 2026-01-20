package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Composite constraint evaluator for process-wide constraint evaluation.
 *
 * <p>
 * This class provides:
 * </p>
 * <ul>
 * <li>Unified evaluation of all constraints across a process system</li>
 * <li>Constraint normalization for comparable utilization metrics</li>
 * <li>Caching of constraint values for performance</li>
 * <li>Sensitivity analysis through finite-difference calculations</li>
 * <li>Bottleneck identification across all equipment</li>
 * </ul>
 *
 * <p><strong>Example Usage</strong></p>
 * 
 * <pre>
 * ProcessConstraintEvaluator evaluator = new ProcessConstraintEvaluator(processSystem);
 * 
 * // Evaluate all constraints
 * ConstraintEvaluationResult result = evaluator.evaluate();
 * System.out.println("Bottleneck: " + result.getBottleneckEquipment());
 * System.out.println("Utilization: " + result.getOverallUtilization());
 * 
 * // Calculate sensitivities
 * Map&lt;String, Double&gt; sensitivities = evaluator.calculateFlowSensitivities(5000.0, "kg/hr");
 * 
 * // Estimate max flow
 * double maxFlow = evaluator.estimateMaxFlow(5000.0, "kg/hr");
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessConstraintEvaluator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(ProcessConstraintEvaluator.class);

  /** Strategy registry for equipment constraint evaluation. */
  private transient EquipmentCapacityStrategyRegistry strategyRegistry;

  /** Cache for constraint values. */
  private transient Map<String, CachedConstraintsInternal> constraintCache;

  /** The process system to evaluate. */
  private ProcessSystem processSystem;

  /** Cache TTL in milliseconds. */
  private long cacheTTLMillis = 10000;

  /** Whether caching is enabled. */
  private boolean cachingEnabled = true;

  /** Step size for finite difference sensitivity calculations. */
  private double sensitivityStepSize = 0.01;

  // ============================================================================
  // Inner Classes
  // ============================================================================

  /**
   * Internal cached constraint data for an equipment.
   */
  private static class CachedConstraintsInternal {
    Map<String, CapacityConstraint> constraints;
    double utilization;
    long timestamp;
    int processRunCount;

    CachedConstraintsInternal(Map<String, CapacityConstraint> constraints, double utilization,
        int processRunCount) {
      this.constraints = constraints;
      this.utilization = utilization;
      this.timestamp = System.currentTimeMillis();
      this.processRunCount = processRunCount;
    }

    boolean isValid(long timeoutMs, int currentRunCount) {
      return (System.currentTimeMillis() - timestamp) < timeoutMs
          && processRunCount == currentRunCount;
    }
  }

  /**
   * Public cached constraints class for external use and testing.
   */
  public static class CachedConstraints implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean valid = false;
    private double flowRate = 0.0;
    private long timestamp = 0;
    private long ttlMillis = 10000;
    private Map<String, Double> cachedResults = new HashMap<String, Double>();

    /** Default constructor. */
    public CachedConstraints() {}

    /**
     * Checks if cache is valid.
     *
     * @return true if valid
     */
    public boolean isValid() {
      return valid;
    }

    /**
     * Sets validity flag.
     *
     * @param valid validity status
     */
    public void setValid(boolean valid) {
      this.valid = valid;
    }

    /**
     * Gets the cached flow rate.
     *
     * @return flow rate
     */
    public double getFlowRate() {
      return flowRate;
    }

    /**
     * Sets the flow rate.
     *
     * @param flowRate flow rate
     */
    public void setFlowRate(double flowRate) {
      this.flowRate = flowRate;
    }

    /**
     * Gets the timestamp.
     *
     * @return timestamp in milliseconds
     */
    public long getTimestamp() {
      return timestamp;
    }

    /**
     * Sets the timestamp.
     *
     * @param timestamp timestamp in milliseconds
     */
    public void setTimestamp(long timestamp) {
      this.timestamp = timestamp;
    }

    /**
     * Gets the TTL.
     *
     * @return TTL in milliseconds
     */
    public long getTtlMillis() {
      return ttlMillis;
    }

    /**
     * Sets the TTL.
     *
     * @param ttlMillis TTL in milliseconds
     */
    public void setTtlMillis(long ttlMillis) {
      this.ttlMillis = ttlMillis;
    }

    /**
     * Gets the cached results.
     *
     * @return map of cached results
     */
    public Map<String, Double> getCachedResults() {
      return cachedResults;
    }

    /**
     * Invalidates the cache.
     */
    public void invalidate() {
      this.valid = false;
    }

    /**
     * Checks if cache is expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
      return (System.currentTimeMillis() - timestamp) > ttlMillis;
    }
  }

  /**
   * Result of constraint evaluation.
   */
  public static class ConstraintEvaluationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private double overallUtilization;
    private String bottleneckEquipment = "none";
    private String bottleneckConstraint;
    private double bottleneckUtilization;
    private boolean feasible = true;
    private int totalViolationCount = 0;
    private boolean allHardConstraintsSatisfied = true;
    private boolean allSoftConstraintsSatisfied = true;
    private Map<String, EquipmentConstraintSummary> equipmentSummaries =
        new HashMap<String, EquipmentConstraintSummary>();
    private Map<String, Double> normalizedUtilizations = new HashMap<String, Double>();

    /** Default constructor. */
    public ConstraintEvaluationResult() {}

    /**
     * Gets the overall utilization.
     *
     * @return overall utilization (0-1, can exceed 1 if over capacity)
     */
    public double getOverallUtilization() {
      return overallUtilization;
    }

    /**
     * Sets the overall utilization.
     *
     * @param overallUtilization utilization value
     */
    public void setOverallUtilization(double overallUtilization) {
      this.overallUtilization = overallUtilization;
    }

    /**
     * Gets the bottleneck equipment name.
     *
     * @return equipment name
     */
    public String getBottleneckEquipment() {
      return bottleneckEquipment;
    }

    /**
     * Sets the bottleneck equipment name.
     *
     * @param bottleneckEquipment equipment name
     */
    public void setBottleneckEquipment(String bottleneckEquipment) {
      this.bottleneckEquipment = bottleneckEquipment;
    }

    /**
     * Gets the bottleneck constraint name.
     *
     * @return constraint name
     */
    public String getBottleneckConstraint() {
      return bottleneckConstraint;
    }

    /**
     * Sets the bottleneck constraint name.
     *
     * @param bottleneckConstraint constraint name
     */
    public void setBottleneckConstraint(String bottleneckConstraint) {
      this.bottleneckConstraint = bottleneckConstraint;
    }

    /**
     * Gets the bottleneck utilization.
     *
     * @return bottleneck utilization
     */
    public double getBottleneckUtilization() {
      return bottleneckUtilization;
    }

    /**
     * Sets the bottleneck utilization.
     *
     * @param bottleneckUtilization utilization value
     */
    public void setBottleneckUtilization(double bottleneckUtilization) {
      this.bottleneckUtilization = bottleneckUtilization;
    }

    /**
     * Checks if the solution is feasible.
     *
     * @return true if all hard constraints satisfied
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Sets the feasibility flag.
     *
     * @param feasible feasibility status
     */
    public void setFeasible(boolean feasible) {
      this.feasible = feasible;
    }

    /**
     * Gets the total violation count.
     *
     * @return number of violated constraints
     */
    public int getTotalViolationCount() {
      return totalViolationCount;
    }

    /**
     * Sets the total violation count.
     *
     * @param totalViolationCount number of violations
     */
    public void setTotalViolationCount(int totalViolationCount) {
      this.totalViolationCount = totalViolationCount;
    }

    /**
     * Checks if all hard constraints are satisfied.
     *
     * @return true if satisfied
     */
    public boolean isAllHardConstraintsSatisfied() {
      return allHardConstraintsSatisfied;
    }

    /**
     * Sets the hard constraints satisfied flag.
     *
     * @param satisfied satisfaction status
     */
    public void setAllHardConstraintsSatisfied(boolean satisfied) {
      this.allHardConstraintsSatisfied = satisfied;
    }

    /**
     * Checks if all soft constraints are satisfied.
     *
     * @return true if satisfied
     */
    public boolean isAllSoftConstraintsSatisfied() {
      return allSoftConstraintsSatisfied;
    }

    /**
     * Sets the soft constraints satisfied flag.
     *
     * @param satisfied satisfaction status
     */
    public void setAllSoftConstraintsSatisfied(boolean satisfied) {
      this.allSoftConstraintsSatisfied = satisfied;
    }

    /**
     * Gets the equipment summaries.
     *
     * @return unmodifiable map of equipment summaries
     */
    public Map<String, EquipmentConstraintSummary> getEquipmentSummaries() {
      return Collections.unmodifiableMap(equipmentSummaries);
    }

    /**
     * Adds an equipment summary.
     *
     * @param summary equipment summary
     */
    public void addEquipmentSummary(EquipmentConstraintSummary summary) {
      this.equipmentSummaries.put(summary.getEquipmentName(), summary);
    }

    /**
     * Gets the normalized utilizations.
     *
     * @return unmodifiable map of utilizations
     */
    public Map<String, Double> getNormalizedUtilizations() {
      return Collections.unmodifiableMap(normalizedUtilizations);
    }

    /**
     * Adds a normalized utilization.
     *
     * @param key utilization key
     * @param value utilization value
     */
    public void addNormalizedUtilization(String key, double value) {
      this.normalizedUtilizations.put(key, value);
    }

    /**
     * Gets the margin to the bottleneck constraint.
     *
     * @return margin as fraction (0.1 = 10% headroom)
     */
    public double getBottleneckMargin() {
      return Math.max(0, 1.0 - bottleneckUtilization);
    }
  }

  /**
   * Summary of constraints for a single equipment.
   */
  public static class EquipmentConstraintSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    private String equipmentName;
    private String equipmentType;
    private double utilization;
    private double marginToLimit;
    private String bottleneckConstraint;
    private boolean withinLimits = true;
    private int constraintCount = 0;
    private int violationCount = 0;
    private Map<String, Double> constraintDetails = new HashMap<String, Double>();

    /** Default constructor. */
    public EquipmentConstraintSummary() {}

    /**
     * Gets equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Sets equipment name.
     *
     * @param equipmentName equipment name
     */
    public void setEquipmentName(String equipmentName) {
      this.equipmentName = equipmentName;
    }

    /**
     * Gets equipment type.
     *
     * @return equipment type
     */
    public String getEquipmentType() {
      return equipmentType;
    }

    /**
     * Sets equipment type.
     *
     * @param equipmentType equipment type
     */
    public void setEquipmentType(String equipmentType) {
      this.equipmentType = equipmentType;
    }

    /**
     * Gets utilization.
     *
     * @return utilization value
     */
    public double getUtilization() {
      return utilization;
    }

    /**
     * Sets utilization.
     *
     * @param utilization utilization value
     */
    public void setUtilization(double utilization) {
      this.utilization = utilization;
    }

    /**
     * Gets margin to limit.
     *
     * @return margin value
     */
    public double getMarginToLimit() {
      return marginToLimit;
    }

    /**
     * Sets margin to limit.
     *
     * @param marginToLimit margin value
     */
    public void setMarginToLimit(double marginToLimit) {
      this.marginToLimit = marginToLimit;
    }

    /**
     * Gets bottleneck constraint.
     *
     * @return constraint name
     */
    public String getBottleneckConstraint() {
      return bottleneckConstraint;
    }

    /**
     * Sets bottleneck constraint.
     *
     * @param bottleneckConstraint constraint name
     */
    public void setBottleneckConstraint(String bottleneckConstraint) {
      this.bottleneckConstraint = bottleneckConstraint;
    }

    /**
     * Checks if within limits.
     *
     * @return true if within limits
     */
    public boolean isWithinLimits() {
      return withinLimits;
    }

    /**
     * Sets within limits flag.
     *
     * @param withinLimits limits status
     */
    public void setWithinLimits(boolean withinLimits) {
      this.withinLimits = withinLimits;
    }

    /**
     * Gets constraint count.
     *
     * @return number of constraints
     */
    public int getConstraintCount() {
      return constraintCount;
    }

    /**
     * Sets constraint count.
     *
     * @param constraintCount number of constraints
     */
    public void setConstraintCount(int constraintCount) {
      this.constraintCount = constraintCount;
    }

    /**
     * Gets violation count.
     *
     * @return number of violations
     */
    public int getViolationCount() {
      return violationCount;
    }

    /**
     * Sets violation count.
     *
     * @param violationCount number of violations
     */
    public void setViolationCount(int violationCount) {
      this.violationCount = violationCount;
    }

    /**
     * Gets constraint details.
     *
     * @return map of constraint details
     */
    public Map<String, Double> getConstraintDetails() {
      return constraintDetails;
    }

    /**
     * Adds a constraint detail.
     *
     * @param name constraint name
     * @param value constraint value
     */
    public void addConstraintDetail(String name, double value) {
      this.constraintDetails.put(name, value);
    }
  }

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public ProcessConstraintEvaluator() {
    this.strategyRegistry = EquipmentCapacityStrategyRegistry.getInstance();
    this.constraintCache = new ConcurrentHashMap<String, CachedConstraintsInternal>();
  }

  /**
   * Constructor with process system.
   *
   * @param processSystem the process system to evaluate
   */
  public ProcessConstraintEvaluator(ProcessSystem processSystem) {
    this();
    this.processSystem = processSystem;
  }

  // ============================================================================
  // Main Evaluation Methods
  // ============================================================================

  /**
   * Evaluates all constraints using the stored process system.
   *
   * @return constraint evaluation result
   */
  public ConstraintEvaluationResult evaluate() {
    return evaluate(this.processSystem);
  }

  /**
   * Evaluates all constraints across the process system.
   *
   * @param processSystem the process system to evaluate
   * @return constraint evaluation result
   */
  public ConstraintEvaluationResult evaluate(ProcessSystem processSystem) {
    ConstraintEvaluationResult result = new ConstraintEvaluationResult();

    if (processSystem == null) {
      result.setFeasible(true);
      result.setAllHardConstraintsSatisfied(true);
      result.setAllSoftConstraintsSatisfied(true);
      result.setBottleneckEquipment("none");
      return result;
    }

    double maxUtilization = 0.0;
    String bottleneckEquipment = "none";
    String bottleneckConstraint = null;
    boolean allHardSatisfied = true;
    boolean allSoftSatisfied = true;
    int totalViolations = 0;

    int processRunCount = getProcessRunCount(processSystem);

    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equipment = processSystem.getUnitOperations().get(i);

      EquipmentCapacityStrategy strategy = strategyRegistry.findStrategy(equipment);
      if (strategy == null) {
        continue;
      }

      // Get constraints (possibly from cache)
      Map<String, CapacityConstraint> constraints =
          getConstraintsWithCaching(equipment, strategy, processRunCount);

      // Calculate equipment summary
      EquipmentConstraintSummary summary = new EquipmentConstraintSummary();
      summary.setEquipmentName(equipment.getName());
      summary.setEquipmentType(equipment.getClass().getSimpleName());
      summary.setConstraintCount(constraints.size());

      double equipmentMaxUtil = 0.0;
      String equipmentLimitingConstraint = null;
      int equipmentViolations = 0;

      for (Map.Entry<String, CapacityConstraint> entry : constraints.entrySet()) {
        CapacityConstraint constraint = entry.getValue();
        double util = constraint.getUtilization();

        if (!Double.isNaN(util) && !Double.isInfinite(util)) {
          summary.addConstraintDetail(entry.getKey(), util);

          // Normalized utilization key
          String normalizedKey = equipment.getName() + "/" + entry.getKey();
          result.addNormalizedUtilization(normalizedKey, util);

          if (util > equipmentMaxUtil) {
            equipmentMaxUtil = util;
            equipmentLimitingConstraint = entry.getKey();
          }

          if (util > 1.0) {
            equipmentViolations++;
          }

          // Track overall bottleneck
          if (util > maxUtilization) {
            maxUtilization = util;
            bottleneckEquipment = equipment.getName();
            bottleneckConstraint = entry.getKey();
          }
        }
      }

      summary.setUtilization(equipmentMaxUtil);
      summary.setMarginToLimit(Math.max(0, 1.0 - equipmentMaxUtil));
      summary.setBottleneckConstraint(equipmentLimitingConstraint);
      summary.setWithinLimits(strategy.isWithinHardLimits(equipment));
      summary.setViolationCount(equipmentViolations);
      totalViolations += equipmentViolations;

      if (!summary.isWithinLimits()) {
        allHardSatisfied = false;
      }
      if (!strategy.isWithinSoftLimits(equipment)) {
        allSoftSatisfied = false;
      }

      result.addEquipmentSummary(summary);
    }

    result.setOverallUtilization(maxUtilization);
    result.setBottleneckEquipment(bottleneckEquipment);
    result.setBottleneckConstraint(bottleneckConstraint);
    result.setBottleneckUtilization(maxUtilization);
    result.setTotalViolationCount(totalViolations);
    result.setFeasible(allHardSatisfied);
    result.setAllHardConstraintsSatisfied(allHardSatisfied);
    result.setAllSoftConstraintsSatisfied(allSoftSatisfied);

    return result;
  }

  // ============================================================================
  // Sensitivity Analysis Methods
  // ============================================================================

  /**
   * Calculates sensitivity of constraints to flow rate changes.
   *
   * @param flowRateKgPerHr current flow rate in kg/hr
   * @param flowUnit flow unit (for documentation purposes)
   * @return map of constraint name to sensitivity (d_utilization / d_flow)
   */
  public Map<String, Double> calculateFlowSensitivities(double flowRateKgPerHr, String flowUnit) {
    return calculateFlowSensitivities(this.processSystem, flowRateKgPerHr);
  }

  /**
   * Calculates sensitivity of constraints to flow rate changes.
   *
   * @param processSystem the process system
   * @param flowRateKgPerHr current flow rate in kg/hr
   * @return map of constraint name to sensitivity (d_utilization / d_flow)
   */
  public Map<String, Double> calculateFlowSensitivities(ProcessSystem processSystem,
      double flowRateKgPerHr) {

    Map<String, Double> sensitivities = new HashMap<String, Double>();

    if (processSystem == null || processSystem.getUnitOperations().isEmpty()) {
      return sensitivities;
    }

    if (flowRateKgPerHr <= 0) {
      return sensitivities;
    }

    // Store original flow rate
    double originalFlow = flowRateKgPerHr;

    // Calculate step size
    double step = flowRateKgPerHr * sensitivityStepSize;
    if (step < 1.0) {
      step = 1.0;
    }

    // Evaluate at current point
    ConstraintEvaluationResult baseResult = evaluate(processSystem);

    // Evaluate at perturbed point
    try {
      setFeedFlowRate(processSystem, flowRateKgPerHr + step);
      processSystem.run();
      ConstraintEvaluationResult perturbedResult = evaluate(processSystem);

      // Calculate sensitivities
      Map<String, Double> baseUtils = baseResult.getNormalizedUtilizations();
      Map<String, Double> perturbedUtils = perturbedResult.getNormalizedUtilizations();

      for (String key : baseUtils.keySet()) {
        double baseUtil = baseUtils.get(key);
        Double perturbedUtil = perturbedUtils.get(key);

        if (perturbedUtil != null && !Double.isNaN(baseUtil) && !Double.isNaN(perturbedUtil)) {
          double sensitivity = (perturbedUtil - baseUtil) / step;
          sensitivities.put(key, sensitivity);
        }
      }

    } finally {
      // Restore original flow rate
      setFeedFlowRate(processSystem, originalFlow);
      try {
        processSystem.run();
      } catch (Exception e) {
        logger.warn("Failed to restore original flow rate", e);
      }
    }

    return sensitivities;
  }

  /**
   * Estimates the maximum flow rate before hitting the bottleneck constraint.
   *
   * @param currentFlowKgPerHr current flow rate
   * @param flowUnit flow unit (for documentation purposes)
   * @return estimated maximum flow rate in kg/hr
   */
  public double estimateMaxFlow(double currentFlowKgPerHr, String flowUnit) {
    return estimateMaxFlow(this.processSystem, currentFlowKgPerHr);
  }

  /**
   * Estimates the maximum flow rate before hitting the bottleneck constraint.
   *
   * @param processSystem the process system
   * @param currentFlowKgPerHr current flow rate
   * @return estimated maximum flow rate in kg/hr
   */
  public double estimateMaxFlow(ProcessSystem processSystem, double currentFlowKgPerHr) {
    ConstraintEvaluationResult result = evaluate(processSystem);

    if (result.getBottleneckUtilization() <= 0) {
      return currentFlowKgPerHr * 10.0; // Return a high estimate
    }

    // Linear extrapolation: current_util / current_flow = 1.0 / max_flow
    // max_flow = current_flow / current_util
    return currentFlowKgPerHr / result.getBottleneckUtilization();
  }

  // ============================================================================
  // Cache Management
  // ============================================================================

  /**
   * Gets constraints with caching support.
   */
  private Map<String, CapacityConstraint> getConstraintsWithCaching(
      ProcessEquipmentInterface equipment, EquipmentCapacityStrategy strategy,
      int processRunCount) {

    if (!cachingEnabled) {
      return strategy.getConstraints(equipment);
    }

    String cacheKey = equipment.getName();
    CachedConstraintsInternal cached = constraintCache.get(cacheKey);

    if (cached != null && cached.isValid(cacheTTLMillis, processRunCount)) {
      return cached.constraints;
    }

    // Calculate fresh
    Map<String, CapacityConstraint> constraints = strategy.getConstraints(equipment);
    double utilization = strategy.evaluateCapacity(equipment);

    // Cache it
    constraintCache.put(cacheKey,
        new CachedConstraintsInternal(constraints, utilization, processRunCount));

    return constraints;
  }

  /**
   * Gets process run count (approximate) for cache invalidation.
   */
  private int getProcessRunCount(ProcessSystem processSystem) {
    return processSystem.hashCode();
  }

  /**
   * Sets the feed flow rate on the process system.
   */
  private void setFeedFlowRate(ProcessSystem processSystem, double flowKgPerHr) {
    if (processSystem == null || processSystem.getUnitOperations().isEmpty()) {
      return;
    }

    ProcessEquipmentInterface feedUnit = processSystem.getUnitOperations().get(0);
    if (feedUnit != null && feedUnit.getFluid() != null) {
      feedUnit.getFluid().setTotalFlowRate(flowKgPerHr, "kg/hr");
    }
  }

  /**
   * Clears the constraint cache.
   */
  public void clearCache() {
    if (constraintCache != null) {
      constraintCache.clear();
    }
  }

  // ============================================================================
  // Configuration Getters/Setters
  // ============================================================================

  /**
   * Gets the process system.
   *
   * @return process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Sets the process system.
   *
   * @param processSystem process system
   */
  public void setProcessSystem(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    clearCache();
  }

  /**
   * Gets the cache TTL in milliseconds.
   *
   * @return TTL in milliseconds
   */
  public long getCacheTTLMillis() {
    return cacheTTLMillis;
  }

  /**
   * Sets the cache TTL in milliseconds.
   *
   * @param cacheTTLMillis TTL in milliseconds
   */
  public void setCacheTTLMillis(long cacheTTLMillis) {
    this.cacheTTLMillis = cacheTTLMillis;
  }

  /**
   * Checks if caching is enabled.
   *
   * @return true if caching enabled
   */
  public boolean isCachingEnabled() {
    return cachingEnabled;
  }

  /**
   * Sets whether caching is enabled.
   *
   * @param cachingEnabled caching flag
   */
  public void setCachingEnabled(boolean cachingEnabled) {
    this.cachingEnabled = cachingEnabled;
  }

  /**
   * Gets the sensitivity step size.
   *
   * @return step size as fraction
   */
  public double getSensitivityStepSize() {
    return sensitivityStepSize;
  }

  /**
   * Sets the sensitivity step size.
   *
   * @param sensitivityStepSize step size as fraction
   */
  public void setSensitivityStepSize(double sensitivityStepSize) {
    this.sensitivityStepSize = sensitivityStepSize;
  }
}
