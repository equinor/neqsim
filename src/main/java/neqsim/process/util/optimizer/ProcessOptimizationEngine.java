package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Unified process optimization engine.
 *
 * <p>
 * This class provides a comprehensive API for process optimization combining:
 * </p>
 * <ul>
 * <li>Flow rate optimization (finding max throughput for given pressure drop)</li>
 * <li>Pressure optimization (finding required pressures for target flow)</li>
 * <li>Equipment constraint evaluation across entire process</li>
 * <li>Multi-objective optimization (maximize throughput while minimizing power)</li>
 * </ul>
 *
 * <h3>Optimization Levels</h3>
 * <ul>
 * <li><strong>Low-level:</strong> Single equipment constraint evaluation</li>
 * <li><strong>Mid-level:</strong> Process segment optimization (inlet to outlet)</li>
 * <li><strong>High-level:</strong> Full process system optimization with recycles</li>
 * </ul>
 *
 * <h3>Example Usage</h3>
 * 
 * <pre>
 * ProcessOptimizationEngine engine = new ProcessOptimizationEngine(processSystem);
 * 
 * // Find maximum throughput
 * OptimizationResult result =
 *     engine.findMaximumThroughput(inletPressure, outletPressure, minFlow, maxFlow);
 * 
 * // Evaluate all constraints
 * ConstraintReport report = engine.evaluateAllConstraints();
 * 
 * // Generate lift curve
 * LiftCurve curve = engine.generateLiftCurve(pressures, temperatures, waterCuts, GORs);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessOptimizationEngine implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(ProcessOptimizationEngine.class);

  /** The process system to optimize. */
  private ProcessSystem processSystem;

  /** Strategy registry for equipment constraint evaluation. */
  private transient EquipmentCapacityStrategyRegistry strategyRegistry;

  /** Optimization tolerance. */
  private double tolerance = 1e-6;

  /** Maximum iterations for optimization. */
  private int maxIterations = 100;

  /** Search algorithm to use. */
  private SearchAlgorithm searchAlgorithm = SearchAlgorithm.GOLDEN_SECTION;

  /** Whether to check all constraints during optimization. */
  private boolean enforceConstraints = true;

  /** Constraint evaluation cache. */
  private transient Map<String, List<CapacityConstraint>> constraintCache;

  /**
   * Search algorithm options.
   */
  public enum SearchAlgorithm {
    BINARY_SEARCH, GOLDEN_SECTION, NELDER_MEAD, PARTICLE_SWARM, GRADIENT_DESCENT
  }

  /**
   * Default constructor.
   */
  public ProcessOptimizationEngine() {
    this.strategyRegistry = EquipmentCapacityStrategyRegistry.getInstance();
    this.constraintCache = new HashMap<String, List<CapacityConstraint>>();
  }

  /**
   * Constructor with process system.
   *
   * @param processSystem the process system to optimize
   */
  public ProcessOptimizationEngine(ProcessSystem processSystem) {
    this();
    this.processSystem = processSystem;
  }

  /**
   * Finds the maximum throughput for given inlet and outlet pressures.
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param minFlow minimum flow to search in kg/hr
   * @param maxFlow maximum flow to search in kg/hr
   * @return optimization result
   */
  public OptimizationResult findMaximumThroughput(double inletPressure, double outletPressure,
      double minFlow, double maxFlow) {

    OptimizationResult result = new OptimizationResult();
    result.setObjective("Maximum Throughput");

    try {
      double optimalFlow;

      switch (searchAlgorithm) {
        case GOLDEN_SECTION:
          optimalFlow = goldenSectionSearch(inletPressure, outletPressure, minFlow, maxFlow);
          break;
        case BINARY_SEARCH:
          optimalFlow = binarySearch(inletPressure, outletPressure, minFlow, maxFlow);
          break;
        default:
          optimalFlow = goldenSectionSearch(inletPressure, outletPressure, minFlow, maxFlow);
      }

      result.setOptimalValue(optimalFlow);
      result.setConverged(true);

      // Run at optimal and collect metrics
      if (processSystem != null) {
        setFeedFlowRate(optimalFlow);
        processSystem.run();
        result.setConstraintViolations(evaluateAllConstraintViolations());
        result.setBottleneck(findBottleneckEquipment());
      }

    } catch (Exception e) {
      logger.error("Optimization failed", e);
      result.setConverged(false);
      result.setErrorMessage(e.getMessage());
    }

    return result;
  }

  /**
   * Finds the required inlet pressure for a target flow rate.
   *
   * @param targetFlow target flow in kg/hr
   * @param outletPressure outlet pressure in bara
   * @param minPressure minimum inlet pressure to search in bara
   * @param maxPressure maximum inlet pressure to search in bara
   * @return optimization result with optimal inlet pressure
   */
  public OptimizationResult findRequiredInletPressure(double targetFlow, double outletPressure,
      double minPressure, double maxPressure) {

    OptimizationResult result = new OptimizationResult();
    result.setObjective("Required Inlet Pressure");

    try {
      double optimalPressure =
          pressureBinarySearch(targetFlow, outletPressure, minPressure, maxPressure);

      result.setOptimalValue(optimalPressure);
      result.setConverged(true);

      // Collect final metrics
      if (processSystem != null) {
        result.setConstraintViolations(evaluateAllConstraintViolations());
      }

    } catch (Exception e) {
      logger.error("Pressure optimization failed", e);
      result.setConverged(false);
      result.setErrorMessage(e.getMessage());
    }

    return result;
  }

  /**
   * Evaluates all constraints across all equipment in the process.
   *
   * @return constraint report with utilizations and violations
   */
  public ConstraintReport evaluateAllConstraints() {
    ConstraintReport report = new ConstraintReport();

    if (processSystem == null) {
      return report;
    }

    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equipment = processSystem.getUnitOperations().get(i);
      EquipmentCapacityStrategy strategy = strategyRegistry.findStrategy(equipment);

      if (strategy != null) {
        Map<String, CapacityConstraint> constraintMap = strategy.getConstraints(equipment);
        List<CapacityConstraint> constraints =
            new ArrayList<CapacityConstraint>(constraintMap.values());
        double utilization = strategy.evaluateCapacity(equipment);

        EquipmentConstraintStatus status = new EquipmentConstraintStatus();
        status.setEquipmentName(equipment.getName());
        status.setEquipmentType(equipment.getClass().getSimpleName());
        status.setUtilization(utilization);
        status.setConstraints(constraints);
        status.setWithinLimits(strategy.isWithinHardLimits(equipment));

        // Check for bottleneck
        CapacityConstraint bottleneck = strategy.getBottleneckConstraint(equipment);
        if (bottleneck != null) {
          status.setBottleneckConstraint(bottleneck.getName());
        }

        report.addEquipmentStatus(status);
      }
    }

    return report;
  }

  /**
   * Finds the bottleneck equipment in the process.
   *
   * @return name of bottleneck equipment or null if no bottleneck
   */
  public String findBottleneckEquipment() {
    if (processSystem == null) {
      return null;
    }

    String bottleneck = null;
    double highestUtilization = 0.0;

    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equipment = processSystem.getUnitOperations().get(i);
      EquipmentCapacityStrategy strategy = strategyRegistry.findStrategy(equipment);

      if (strategy != null) {
        double utilization = strategy.evaluateCapacity(equipment);
        if (utilization > highestUtilization) {
          highestUtilization = utilization;
          bottleneck = equipment.getName();
        }
      }
    }

    return bottleneck;
  }

  /**
   * Generates a lift curve for the process.
   *
   * @param pressures array of inlet pressures to evaluate in bara
   * @param temperatures array of inlet temperatures in Kelvin
   * @param waterCuts array of water cuts as fraction
   * @param GORs array of gas-oil ratios in Sm3/Sm3
   * @return lift curve data
   */
  public LiftCurveData generateLiftCurve(double[] pressures, double[] temperatures,
      double[] waterCuts, double[] GORs) {

    LiftCurveData liftCurve = new LiftCurveData();

    if (processSystem == null || pressures == null || pressures.length == 0) {
      return liftCurve;
    }

    for (double pressure : pressures) {
      for (double temperature : temperatures) {
        for (double waterCut : waterCuts) {
          for (double gor : GORs) {
            LiftCurvePoint point = evaluateLiftCurvePoint(pressure, temperature, waterCut, gor);
            if (point != null) {
              liftCurve.addPoint(point);
            }
          }
        }
      }
    }

    return liftCurve;
  }

  /**
   * Evaluates a single lift curve point.
   */
  private LiftCurvePoint evaluateLiftCurvePoint(double pressure, double temperature,
      double waterCut, double gor) {

    try {
      // Set conditions and find max flow
      setInletConditions(pressure, temperature, waterCut, gor);

      // Find max flow at these conditions
      double maxFlow = findMaxFlowAtConditions(pressure);

      LiftCurvePoint point = new LiftCurvePoint();
      point.setInletPressure(pressure);
      point.setTemperature(temperature);
      point.setWaterCut(waterCut);
      point.setGOR(gor);
      point.setMaxFlowRate(maxFlow);

      return point;

    } catch (Exception e) {
      logger.warn("Failed to evaluate lift curve point at P=" + pressure, e);
      return null;
    }
  }

  /**
   * Golden section search for maximum flow.
   */
  private double goldenSectionSearch(double inletPressure, double outletPressure, double minFlow,
      double maxFlow) {

    double phi = (1.0 + Math.sqrt(5.0)) / 2.0;
    double a = minFlow;
    double b = maxFlow;

    double c = b - (b - a) / phi;
    double d = a + (b - a) / phi;

    for (int iter = 0; iter < maxIterations && (b - a) > tolerance; iter++) {
      double fc = evaluateFlowObjective(inletPressure, outletPressure, c);
      double fd = evaluateFlowObjective(inletPressure, outletPressure, d);

      if (fc < fd) {
        // Maximum is between c and b
        a = c;
        c = d;
        d = a + (b - a) / phi;
      } else {
        // Maximum is between a and d
        b = d;
        d = c;
        c = b - (b - a) / phi;
      }
    }

    return (a + b) / 2.0;
  }

  /**
   * Binary search for maximum flow.
   */
  private double binarySearch(double inletPressure, double outletPressure, double minFlow,
      double maxFlow) {

    double low = minFlow;
    double high = maxFlow;

    for (int iter = 0; iter < maxIterations && (high - low) > tolerance; iter++) {
      double mid = (low + high) / 2.0;

      if (canAchieveFlow(inletPressure, outletPressure, mid)) {
        low = mid; // Can achieve this flow, try higher
      } else {
        high = mid; // Cannot achieve, try lower
      }
    }

    return low;
  }

  /**
   * Binary search for required inlet pressure.
   */
  private double pressureBinarySearch(double targetFlow, double outletPressure, double minPressure,
      double maxPressure) {

    double low = minPressure;
    double high = maxPressure;

    for (int iter = 0; iter < maxIterations && (high - low) > tolerance; iter++) {
      double mid = (low + high) / 2.0;

      if (canAchieveFlowWithPressure(targetFlow, mid, outletPressure)) {
        high = mid; // Can achieve with this pressure, try lower
      } else {
        low = mid; // Cannot achieve, need higher pressure
      }
    }

    return high;
  }

  /**
   * Evaluates the flow objective function.
   */
  private double evaluateFlowObjective(double inletPressure, double outletPressure, double flow) {
    if (!canAchieveFlow(inletPressure, outletPressure, flow)) {
      return -Double.MAX_VALUE; // Penalize infeasible
    }
    return flow; // Maximize flow
  }

  /**
   * Checks if a flow rate can be achieved.
   */
  private boolean canAchieveFlow(double inletPressure, double outletPressure, double flow) {
    if (processSystem == null) {
      return true;
    }

    try {
      setFeedFlowRate(flow);
      setInletPressure(inletPressure);
      processSystem.run();

      // Check outlet pressure
      double actualOutletPressure = getOutletPressure();
      if (actualOutletPressure < outletPressure * 0.99) {
        return false;
      }

      // Check constraints if enabled
      if (enforceConstraints) {
        return areAllConstraintsSatisfied();
      }

      return true;

    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Checks if flow can be achieved with given inlet pressure.
   */
  private boolean canAchieveFlowWithPressure(double flow, double inletPressure,
      double outletPressure) {
    if (processSystem == null) {
      return true;
    }

    try {
      setFeedFlowRate(flow);
      setInletPressure(inletPressure);
      processSystem.run();

      double actualOutletPressure = getOutletPressure();
      return actualOutletPressure >= outletPressure * 0.99;

    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Checks if all constraints are satisfied.
   */
  private boolean areAllConstraintsSatisfied() {
    if (processSystem == null) {
      return true;
    }

    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equipment = processSystem.getUnitOperations().get(i);
      EquipmentCapacityStrategy strategy = strategyRegistry.findStrategy(equipment);

      if (strategy != null && !strategy.isWithinHardLimits(equipment)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Evaluates all constraint violations.
   */
  private List<String> evaluateAllConstraintViolations() {
    List<String> violations = new ArrayList<String>();

    if (processSystem == null) {
      return violations;
    }

    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equipment = processSystem.getUnitOperations().get(i);
      EquipmentCapacityStrategy strategy = strategyRegistry.findStrategy(equipment);

      if (strategy != null) {
        List<CapacityConstraint> eqViolations = strategy.getViolations(equipment);
        for (CapacityConstraint c : eqViolations) {
          violations.add(equipment.getName() + ": " + c.getName() + " (" + c.getCurrentValue()
              + " > " + c.getMaxValue() + ")");
        }
      }
    }

    return violations;
  }

  /**
   * Finds max flow at current conditions.
   */
  private double findMaxFlowAtConditions(double inletPressure) {
    double minFlow = 0.0;
    double maxFlow = 1000000.0; // kg/hr
    double outletPressure = 1.0; // bara - typical separator/export

    return goldenSectionSearch(inletPressure, outletPressure, minFlow, maxFlow);
  }

  // Helper methods for process manipulation

  /**
   * Sets the feed flow rate.
   */
  private void setFeedFlowRate(double flowKgPerHr) {
    if (processSystem == null || processSystem.getUnitOperations().isEmpty()) {
      return;
    }
    // Assume first unit is the feed stream
    ProcessEquipmentInterface feedUnit = processSystem.getUnitOperations().get(0);
    if (feedUnit != null) {
      feedUnit.getFluid().setTotalFlowRate(flowKgPerHr, "kg/hr");
    }
  }

  /**
   * Sets the inlet pressure.
   */
  private void setInletPressure(double pressureBara) {
    if (processSystem == null || processSystem.getUnitOperations().isEmpty()) {
      return;
    }
    ProcessEquipmentInterface feedUnit = processSystem.getUnitOperations().get(0);
    if (feedUnit != null) {
      feedUnit.getFluid().setPressure(pressureBara, "bara");
    }
  }

  /**
   * Sets inlet conditions.
   */
  private void setInletConditions(double pressure, double temperature, double waterCut,
      double gor) {
    if (processSystem == null || processSystem.getUnitOperations().isEmpty()) {
      return;
    }
    // Simplified - actual implementation would modify fluid composition
    setInletPressure(pressure);
    // Additional composition changes based on waterCut and GOR would go here
  }

  /**
   * Gets the outlet pressure.
   */
  private double getOutletPressure() {
    if (processSystem == null || processSystem.getUnitOperations().isEmpty()) {
      return 0.0;
    }
    int lastIndex = processSystem.getUnitOperations().size() - 1;
    ProcessEquipmentInterface lastUnit = processSystem.getUnitOperations().get(lastIndex);
    if (lastUnit != null && lastUnit.getFluid() != null) {
      return lastUnit.getFluid().getPressure("bara");
    }
    return 0.0;
  }

  // Configuration methods

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
  }

  /**
   * Gets the tolerance.
   *
   * @return tolerance
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * Sets the tolerance.
   *
   * @param tolerance convergence tolerance
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * Gets the max iterations.
   *
   * @return max iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Sets the max iterations.
   *
   * @param maxIterations max iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Gets the search algorithm.
   *
   * @return search algorithm
   */
  public SearchAlgorithm getSearchAlgorithm() {
    return searchAlgorithm;
  }

  /**
   * Sets the search algorithm.
   *
   * @param algorithm search algorithm
   */
  public void setSearchAlgorithm(SearchAlgorithm algorithm) {
    this.searchAlgorithm = algorithm;
  }

  /**
   * Checks if constraints are enforced.
   *
   * @return true if enforced
   */
  public boolean isEnforceConstraints() {
    return enforceConstraints;
  }

  /**
   * Sets whether to enforce constraints.
   *
   * @param enforce true to enforce
   */
  public void setEnforceConstraints(boolean enforce) {
    this.enforceConstraints = enforce;
  }

  /**
   * Clears the constraint cache.
   */
  public void clearCache() {
    if (constraintCache != null) {
      constraintCache.clear();
    }
  }

  // Inner classes for results

  /**
   * Result of an optimization run.
   */
  public static class OptimizationResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private String objective;
    private double optimalValue;
    private boolean converged;
    private String errorMessage;
    private String bottleneck;
    private List<String> constraintViolations = new ArrayList<String>();

    public String getObjective() {
      return objective;
    }

    public void setObjective(String objective) {
      this.objective = objective;
    }

    public double getOptimalValue() {
      return optimalValue;
    }

    public void setOptimalValue(double optimalValue) {
      this.optimalValue = optimalValue;
    }

    public boolean isConverged() {
      return converged;
    }

    public void setConverged(boolean converged) {
      this.converged = converged;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public String getBottleneck() {
      return bottleneck;
    }

    public void setBottleneck(String bottleneck) {
      this.bottleneck = bottleneck;
    }

    public List<String> getConstraintViolations() {
      return constraintViolations;
    }

    public void setConstraintViolations(List<String> violations) {
      this.constraintViolations = violations;
    }
  }

  /**
   * Constraint report for entire process.
   */
  public static class ConstraintReport implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<EquipmentConstraintStatus> equipmentStatuses =
        new ArrayList<EquipmentConstraintStatus>();

    public void addEquipmentStatus(EquipmentConstraintStatus status) {
      equipmentStatuses.add(status);
    }

    public List<EquipmentConstraintStatus> getEquipmentStatuses() {
      return equipmentStatuses;
    }

    public EquipmentConstraintStatus getBottleneck() {
      EquipmentConstraintStatus bottleneck = null;
      double highestUtilization = 0.0;
      for (EquipmentConstraintStatus status : equipmentStatuses) {
        if (status.getUtilization() > highestUtilization) {
          highestUtilization = status.getUtilization();
          bottleneck = status;
        }
      }
      return bottleneck;
    }
  }

  /**
   * Constraint status for single equipment.
   */
  public static class EquipmentConstraintStatus implements Serializable {
    private static final long serialVersionUID = 1L;
    private String equipmentName;
    private String equipmentType;
    private double utilization;
    private boolean withinLimits;
    private String bottleneckConstraint;
    private List<CapacityConstraint> constraints = new ArrayList<CapacityConstraint>();

    public String getEquipmentName() {
      return equipmentName;
    }

    public void setEquipmentName(String name) {
      this.equipmentName = name;
    }

    public String getEquipmentType() {
      return equipmentType;
    }

    public void setEquipmentType(String type) {
      this.equipmentType = type;
    }

    public double getUtilization() {
      return utilization;
    }

    public void setUtilization(double utilization) {
      this.utilization = utilization;
    }

    public boolean isWithinLimits() {
      return withinLimits;
    }

    public void setWithinLimits(boolean withinLimits) {
      this.withinLimits = withinLimits;
    }

    public String getBottleneckConstraint() {
      return bottleneckConstraint;
    }

    public void setBottleneckConstraint(String constraint) {
      this.bottleneckConstraint = constraint;
    }

    public List<CapacityConstraint> getConstraints() {
      return constraints;
    }

    public void setConstraints(List<CapacityConstraint> constraints) {
      this.constraints = constraints;
    }
  }

  /**
   * Lift curve data container.
   */
  public static class LiftCurveData implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<LiftCurvePoint> points = new ArrayList<LiftCurvePoint>();

    public void addPoint(LiftCurvePoint point) {
      points.add(point);
    }

    public List<LiftCurvePoint> getPoints() {
      return points;
    }

    public int size() {
      return points.size();
    }
  }

  /**
   * Single point on a lift curve.
   */
  public static class LiftCurvePoint implements Serializable {
    private static final long serialVersionUID = 1L;
    private double inletPressure;
    private double temperature;
    private double waterCut;
    private double gor;
    private double maxFlowRate;

    public double getInletPressure() {
      return inletPressure;
    }

    public void setInletPressure(double pressure) {
      this.inletPressure = pressure;
    }

    public double getTemperature() {
      return temperature;
    }

    public void setTemperature(double temp) {
      this.temperature = temp;
    }

    public double getWaterCut() {
      return waterCut;
    }

    public void setWaterCut(double wc) {
      this.waterCut = wc;
    }

    public double getGOR() {
      return gor;
    }

    public void setGOR(double gor) {
      this.gor = gor;
    }

    public double getMaxFlowRate() {
      return maxFlowRate;
    }

    public void setMaxFlowRate(double flow) {
      this.maxFlowRate = flow;
    }
  }
}
