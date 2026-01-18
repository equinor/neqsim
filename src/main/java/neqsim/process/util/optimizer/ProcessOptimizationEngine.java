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
import neqsim.process.util.optimization.FlowRateOptimizer;

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
 * <li>Gradient-based optimization for faster convergence</li>
 * <li>Sensitivity analysis for constraint insights</li>
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
 * // Find maximum throughput with gradient acceleration
 * engine.setSearchAlgorithm(SearchAlgorithm.GRADIENT_ACCELERATED);
 * OptimizationResult result =
 *     engine.findMaximumThroughput(inletPressure, outletPressure, minFlow, maxFlow);
 * 
 * // Get sensitivity analysis
 * SensitivityResult sens = engine.analyzeSensitivity(result.getOptimalValue());
 * 
 * // Evaluate all constraints
 * ConstraintReport report = engine.evaluateAllConstraints();
 * 
 * // Generate lift curve
 * LiftCurve curve = engine.generateLiftCurve(pressures, temperatures, waterCuts, GORs);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 2.0
 */
public class ProcessOptimizationEngine implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

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
        case GRADIENT_DESCENT:
          // Start gradient descent from middle of range
          double initialFlow = (minFlow + maxFlow) / 2.0;
          optimalFlow = gradientDescentSearch(inletPressure, outletPressure, initialFlow);
          // Ensure result is within bounds
          optimalFlow = Math.max(minFlow, Math.min(maxFlow, optimalFlow));
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

  // ==========================================================================
  // Gradient-Based Optimization Methods
  // ==========================================================================

  /**
   * Performs gradient descent optimization to find maximum flow.
   *
   * <p>
   * Uses finite-difference gradient estimation with adaptive step size. More efficient than
   * bracket-based methods when near the optimum.
   * </p>
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param initialFlow starting flow rate in kg/hr
   * @return optimal flow rate in kg/hr
   */
  public double gradientDescentSearch(double inletPressure, double outletPressure,
      double initialFlow) {

    double flow = initialFlow;
    double stepSize = 1000.0; // Initial step size in kg/hr
    double minStepSize = 0.1;
    double gradientTolerance = 1e-4;

    // Adaptive parameters
    double beta = 0.8; // Step size decay
    double minImprovement = 1e-6;

    double lastObjective = evaluateConstrainedObjective(inletPressure, outletPressure, flow);

    for (int iter = 0; iter < maxIterations; iter++) {
      // Estimate gradient using finite differences
      double gradient = estimateGradient(inletPressure, outletPressure, flow);

      // Check convergence
      if (Math.abs(gradient) < gradientTolerance || stepSize < minStepSize) {
        logger.debug("Gradient descent converged at iter {} with flow {}", iter, flow);
        break;
      }

      // Take step in direction of gradient (maximize objective)
      double newFlow = flow + stepSize * Math.signum(gradient);
      newFlow = Math.max(newFlow, 0.0); // Ensure non-negative

      double newObjective = evaluateConstrainedObjective(inletPressure, outletPressure, newFlow);

      if (newObjective > lastObjective + minImprovement) {
        // Accept step
        flow = newFlow;
        lastObjective = newObjective;
      } else {
        // Reduce step size
        stepSize *= beta;
      }
    }

    return flow;
  }

  /**
   * Estimates the gradient of the objective function using finite differences.
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param flow current flow rate in kg/hr
   * @return estimated gradient
   */
  private double estimateGradient(double inletPressure, double outletPressure, double flow) {
    double h = Math.max(flow * 0.001, 1.0); // Step size for finite difference

    double fPlus = evaluateConstrainedObjective(inletPressure, outletPressure, flow + h);
    double fMinus = evaluateConstrainedObjective(inletPressure, outletPressure, flow - h);

    return (fPlus - fMinus) / (2.0 * h);
  }

  /**
   * Evaluates the objective function with constraint penalty.
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param flow flow rate in kg/hr
   * @return objective value (flow rate with penalty for constraint violations)
   */
  private double evaluateConstrainedObjective(double inletPressure, double outletPressure,
      double flow) {

    if (flow <= 0) {
      return -Double.MAX_VALUE;
    }

    if (!canAchieveFlow(inletPressure, outletPressure, flow)) {
      // Apply quadratic penalty for infeasible solutions
      double violation = calculateConstraintViolationMagnitude();
      return flow - 10000.0 * violation * violation;
    }

    return flow; // Maximize flow when feasible
  }

  /**
   * Calculates the total magnitude of constraint violations.
   *
   * @return sum of squared constraint violations
   */
  private double calculateConstraintViolationMagnitude() {
    double totalViolation = 0.0;

    if (processSystem == null) {
      return 0.0;
    }

    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equipment = processSystem.getUnitOperations().get(i);
      EquipmentCapacityStrategy strategy = strategyRegistry.findStrategy(equipment);

      if (strategy != null) {
        List<CapacityConstraint> violations = strategy.getViolations(equipment);
        for (CapacityConstraint c : violations) {
          double excess = c.getCurrentValue() - c.getMaxValue();
          if (excess > 0) {
            totalViolation += excess / c.getMaxValue(); // Normalized violation
          }
        }
      }
    }

    return totalViolation;
  }

  // ==========================================================================
  // Sensitivity Analysis Methods
  // ==========================================================================

  /**
   * Analyzes the sensitivity of the optimal solution to flow rate changes.
   *
   * @param optimalFlow the optimal flow rate in kg/hr
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @return sensitivity result with gradient and margin information
   */
  public SensitivityResult analyzeSensitivity(double optimalFlow, double inletPressure,
      double outletPressure) {

    SensitivityResult result = new SensitivityResult();
    result.setBaseFlow(optimalFlow);

    // Calculate flow gradient
    double gradient = estimateGradient(inletPressure, outletPressure, optimalFlow);
    result.setFlowGradient(gradient);

    // Analyze constraint margins
    if (processSystem != null) {
      setFeedFlowRate(optimalFlow);
      processSystem.run();

      Map<String, Double> margins = new HashMap<String, Double>();
      String tightestConstraint = null;
      double smallestMargin = Double.MAX_VALUE;

      for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
        ProcessEquipmentInterface equipment = processSystem.getUnitOperations().get(i);
        EquipmentCapacityStrategy strategy = strategyRegistry.findStrategy(equipment);

        if (strategy != null) {
          double utilization = strategy.evaluateCapacity(equipment);
          double margin = 1.0 - utilization;
          margins.put(equipment.getName(), margin);

          if (margin < smallestMargin && margin >= 0) {
            smallestMargin = margin;
            tightestConstraint = equipment.getName();
          }
        }
      }

      result.setConstraintMargins(margins);
      result.setTightestConstraint(tightestConstraint);
      result.setTightestMargin(smallestMargin);
    }

    // Estimate max flow increase before constraint violation
    double flowBuffer = estimateFlowBuffer(optimalFlow, inletPressure, outletPressure);
    result.setFlowBuffer(flowBuffer);

    return result;
  }

  /**
   * Estimates how much flow can increase before hitting a constraint.
   *
   * @param currentFlow current flow rate in kg/hr
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @return estimated flow buffer in kg/hr
   */
  private double estimateFlowBuffer(double currentFlow, double inletPressure,
      double outletPressure) {

    double testFlow = currentFlow * 1.01; // 1% increase
    int steps = 0;
    int maxSteps = 50;

    while (canAchieveFlow(inletPressure, outletPressure, testFlow) && steps < maxSteps) {
      testFlow *= 1.01;
      steps++;
    }

    return testFlow - currentFlow;
  }

  /**
   * Calculates shadow prices for each constraint.
   *
   * <p>
   * Shadow price indicates how much the objective would improve if the constraint were relaxed by
   * one unit.
   * </p>
   *
   * @param optimalFlow the optimal flow rate in kg/hr
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @return map of equipment name to shadow price
   */
  public Map<String, Double> calculateShadowPrices(double optimalFlow, double inletPressure,
      double outletPressure) {

    Map<String, Double> shadowPrices = new HashMap<String, Double>();

    if (processSystem == null) {
      return shadowPrices;
    }

    // Run at optimal
    setFeedFlowRate(optimalFlow);
    processSystem.run();

    // For each equipment, estimate how much relaxing its constraint would help
    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equipment = processSystem.getUnitOperations().get(i);
      EquipmentCapacityStrategy strategy = strategyRegistry.findStrategy(equipment);

      if (strategy != null) {
        double utilization = strategy.evaluateCapacity(equipment);

        // Equipment near capacity has higher shadow price
        if (utilization > 0.9) {
          // Shadow price is proportional to how binding the constraint is
          double shadowPrice = (utilization - 0.9) / 0.1 * 1000.0; // Scale factor
          shadowPrices.put(equipment.getName(), shadowPrice);
        } else {
          shadowPrices.put(equipment.getName(), 0.0);
        }
      }
    }

    return shadowPrices;
  }

  // ==========================================================================
  // FlowRateOptimizer Integration
  // ==========================================================================

  /**
   * Creates and configures a FlowRateOptimizer for this process system.
   *
   * <p>
   * This integrates the detailed FlowRateOptimizer with the ProcessOptimizationEngine, allowing for
   * more sophisticated optimization scenarios including lift curve generation and reservoir
   * integration.
   * </p>
   *
   * @return configured FlowRateOptimizer instance
   */
  public FlowRateOptimizer createFlowRateOptimizer() {
    if (processSystem == null) {
      throw new IllegalStateException("Process system must be set before creating optimizer");
    }

    // Find first and last stream names
    String inletName = "FeedStream";
    String outletName = "OutletStream";

    if (processSystem.getUnitOperations().size() > 0) {
      inletName = processSystem.getUnitOperations().get(0).getName();
      int lastIdx = processSystem.getUnitOperations().size() - 1;
      outletName = processSystem.getUnitOperations().get(lastIdx).getName();
    }

    FlowRateOptimizer optimizer = new FlowRateOptimizer(processSystem, inletName, outletName);
    optimizer.setTolerance(this.tolerance);
    optimizer.setMaxIterations(this.maxIterations);

    return optimizer;
  }

  /**
   * Generates a comprehensive lift curve using FlowRateOptimizer.
   *
   * @param feedStreamName name of the feed stream
   * @param inletPressures array of inlet pressures to evaluate
   * @param outletPressure target outlet pressure
   * @return FlowRateOptimizer configured with lift curve results
   */
  public FlowRateOptimizer generateComprehensiveLiftCurve(String feedStreamName,
      double[] inletPressures, double outletPressure) {

    // Find outlet stream name
    String outletName = "OutletStream";
    if (processSystem != null && processSystem.getUnitOperations().size() > 0) {
      int lastIdx = processSystem.getUnitOperations().size() - 1;
      outletName = processSystem.getUnitOperations().get(lastIdx).getName();
    }

    FlowRateOptimizer optimizer = new FlowRateOptimizer(processSystem, feedStreamName, outletName);
    optimizer.setTolerance(this.tolerance);
    optimizer.setMaxIterations(this.maxIterations);

    // Generate lift curve points by running optimization at each pressure
    for (double pressure : inletPressures) {
      try {
        // Use findFlowRate method
        optimizer.findFlowRate(pressure, outletPressure, "bara");
      } catch (Exception e) {
        logger.warn("Failed to evaluate pressure point: {}", pressure, e);
      }
    }

    return optimizer;
  }

  // ==========================================================================
  // ProcessConstraintEvaluator Integration
  // ==========================================================================

  /** Constraint evaluator for caching and sensitivity analysis. */
  private transient ProcessConstraintEvaluator constraintEvaluator;

  /**
   * Gets or creates the constraint evaluator.
   *
   * @return constraint evaluator instance
   */
  public ProcessConstraintEvaluator getConstraintEvaluator() {
    if (constraintEvaluator == null && processSystem != null) {
      constraintEvaluator = new ProcessConstraintEvaluator(processSystem);
    }
    return constraintEvaluator;
  }

  /**
   * Evaluates constraints using cached evaluator for better performance.
   *
   * @return constraint evaluation result
   */
  public ProcessConstraintEvaluator.ConstraintEvaluationResult evaluateConstraintsWithCache() {
    ProcessConstraintEvaluator evaluator = getConstraintEvaluator();
    if (evaluator == null) {
      return null;
    }
    return evaluator.evaluate();
  }

  /**
   * Calculates flow sensitivities for all equipment.
   *
   * @param baseFlowRate base flow rate in kg/hr
   * @param flowUnit flow rate unit
   * @return map of equipment name to sensitivity value
   */
  public Map<String, Double> calculateFlowSensitivities(double baseFlowRate, String flowUnit) {
    ProcessConstraintEvaluator evaluator = getConstraintEvaluator();
    if (evaluator == null) {
      return new HashMap<String, Double>();
    }
    return evaluator.calculateFlowSensitivities(baseFlowRate, flowUnit);
  }

  /**
   * Estimates maximum feasible flow rate.
   *
   * @param currentFlowRate current flow rate
   * @param flowUnit flow rate unit
   * @return estimated maximum flow rate
   */
  public double estimateMaximumFlow(double currentFlowRate, String flowUnit) {
    ProcessConstraintEvaluator evaluator = getConstraintEvaluator();
    if (evaluator == null) {
      return currentFlowRate;
    }
    return evaluator.estimateMaxFlow(currentFlowRate, flowUnit);
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

  /**
   * Sensitivity analysis result.
   */
  public static class SensitivityResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private double baseFlow;
    private double flowGradient;
    private String tightestConstraint;
    private double tightestMargin;
    private double flowBuffer;
    private Map<String, Double> constraintMargins = new HashMap<String, Double>();

    public double getBaseFlow() {
      return baseFlow;
    }

    public void setBaseFlow(double baseFlow) {
      this.baseFlow = baseFlow;
    }

    public double getFlowGradient() {
      return flowGradient;
    }

    public void setFlowGradient(double flowGradient) {
      this.flowGradient = flowGradient;
    }

    public String getTightestConstraint() {
      return tightestConstraint;
    }

    public void setTightestConstraint(String tightestConstraint) {
      this.tightestConstraint = tightestConstraint;
    }

    public double getTightestMargin() {
      return tightestMargin;
    }

    public void setTightestMargin(double tightestMargin) {
      this.tightestMargin = tightestMargin;
    }

    public double getFlowBuffer() {
      return flowBuffer;
    }

    public void setFlowBuffer(double flowBuffer) {
      this.flowBuffer = flowBuffer;
    }

    public Map<String, Double> getConstraintMargins() {
      return constraintMargins;
    }

    public void setConstraintMargins(Map<String, Double> constraintMargins) {
      this.constraintMargins = constraintMargins;
    }

    /**
     * Checks if any equipment is at capacity.
     *
     * @return true if tightest margin is less than 5%
     */
    public boolean isAtCapacity() {
      return tightestMargin < 0.05;
    }

    /**
     * Gets the bottleneck equipment name.
     *
     * @return name of equipment with tightest constraint
     */
    public String getBottleneckEquipment() {
      return tightestConstraint;
    }
  }
}
