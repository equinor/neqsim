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
import neqsim.process.processmodel.ProcessModule;
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
 * <li>Gradient-based optimization for faster convergence</li>
 * <li>Sensitivity analysis for constraint insights</li>
 * </ul>
 *
 * <p>
 * <strong>Optimization Levels:</strong>
 * </p>
 * <ul>
 * <li><strong>Low-level:</strong> Single equipment constraint evaluation</li>
 * <li><strong>Mid-level:</strong> Process segment optimization (inlet to outlet)</li>
 * <li><strong>High-level:</strong> Full process system optimization with recycles</li>
 * </ul>
 *
 * <p>
 * <strong>Example Usage:</strong>
 * </p>
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

  /** The process module to optimize (alternative to processSystem). */
  private ProcessModule processModule;

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

  // Armijo-Wolfe line search parameters
  private double armijoC1 = 1e-4; // Sufficient decrease parameter
  private double wolfeC2 = 0.9; // Curvature condition parameter
  private int maxLineSearchIterations = 20;

  // BFGS parameters
  private double bfgsGradientTolerance = 1e-6;

  /** Name of the feed stream to vary during optimization. */
  private String feedStreamName = null;

  /** Name of the outlet stream to monitor during optimization. */
  private String outletStreamName = null;

  /**
   * Search algorithm options.
   */
  public enum SearchAlgorithm {
    /** Simple binary search for monotonic objectives. */
    BINARY_SEARCH,
    /** Golden section search for unimodal objectives. */
    GOLDEN_SECTION,
    /** Nelder-Mead simplex method for multi-dimensional. */
    NELDER_MEAD,
    /** Particle swarm optimization for global search. */
    PARTICLE_SWARM,
    /** Gradient descent with adaptive step size. */
    GRADIENT_DESCENT,
    /** Gradient descent with Armijo-Wolfe line search for guaranteed convergence. */
    GRADIENT_DESCENT_ARMIJO_WOLFE,
    /** BFGS quasi-Newton method for fast convergence. */
    BFGS
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
   * Constructor with process module.
   *
   * @param processModule the process module to optimize
   */
  public ProcessOptimizationEngine(ProcessModule processModule) {
    this();
    this.processModule = processModule;
  }

  // ==================== Helper Methods for ProcessSystem/ProcessModule ====================

  /**
   * Run the simulation (either ProcessSystem or ProcessModule).
   */
  private void runSimulation() {
    if (processSystem != null) {
      processSystem.run();
    } else if (processModule != null) {
      processModule.run();
    }
  }

  /**
   * Get all unit operations from either ProcessSystem or ProcessModule.
   *
   * @return list of all unit operations
   */
  private List<ProcessEquipmentInterface> getAllUnitOperations() {
    List<ProcessEquipmentInterface> allUnits = new ArrayList<>();

    if (processSystem != null) {
      allUnits.addAll(processSystem.getUnitOperations());
    } else if (processModule != null) {
      for (ProcessSystem sys : processModule.getAllProcessSystems()) {
        allUnits.addAll(sys.getUnitOperations());
      }
    }

    return allUnits;
  }

  /**
   * Check if a process (system or module) is configured.
   *
   * @return true if either processSystem or processModule is set
   */
  private boolean hasProcess() {
    return processSystem != null || processModule != null;
  }

  /**
   * Get the primary ProcessSystem (first one if using module).
   *
   * @return the ProcessSystem or null
   */
  private ProcessSystem getPrimaryProcessSystem() {
    if (processSystem != null) {
      return processSystem;
    }
    if (processModule != null) {
      List<ProcessSystem> systems = processModule.getAllProcessSystems();
      if (!systems.isEmpty()) {
        return systems.get(0);
      }
    }
    return null;
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
        case GRADIENT_DESCENT_ARMIJO_WOLFE:
          // Gradient descent with Armijo-Wolfe line search
          double initFlowAW = (minFlow + maxFlow) / 2.0;
          optimalFlow = gradientDescentArmijoWolfeSearch(inletPressure, outletPressure, initFlowAW);
          optimalFlow = Math.max(minFlow, Math.min(maxFlow, optimalFlow));
          break;
        case BFGS:
          // BFGS quasi-Newton method
          double initFlowBFGS = (minFlow + maxFlow) / 2.0;
          optimalFlow = bfgsSearch(inletPressure, outletPressure, initFlowBFGS, minFlow, maxFlow);
          break;
        default:
          optimalFlow = goldenSectionSearch(inletPressure, outletPressure, minFlow, maxFlow);
      }

      result.setOptimalValue(optimalFlow);
      result.setConverged(true);
      result.setInletPressure(inletPressure);
      result.setOutletPressure(outletPressure);

      // Run at optimal and collect metrics
      if (hasProcess()) {
        setFeedFlowRate(optimalFlow);
        runSimulation();
        result.setConstraintViolations(evaluateAllConstraintViolations());
        result.setBottleneck(findBottleneckEquipment());

        // Auto-generate sensitivity analysis
        try {
          SensitivityResult sensitivity =
              analyzeSensitivity(optimalFlow, inletPressure, outletPressure);
          result.setSensitivity(sensitivity);
        } catch (Exception sensEx) {
          logger.debug("Sensitivity analysis failed: {}", sensEx.getMessage());
          // Non-fatal - optimization result is still valid
        }
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
    if (!hasProcess()) {
      return true;
    }

    try {
      setFeedFlowRate(flow);
      setInletPressure(inletPressure);
      runSimulation();

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
    if (!hasProcess()) {
      return true;
    }

    try {
      setFeedFlowRate(flow);
      setInletPressure(inletPressure);
      runSimulation();

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
    if (!hasProcess()) {
      return true;
    }

    List<ProcessEquipmentInterface> units = getAllUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equipment = units.get(i);
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

    if (!hasProcess()) {
      return violations;
    }

    List<ProcessEquipmentInterface> units = getAllUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equipment = units.get(i);
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

    if (!hasProcess()) {
      return 0.0;
    }

    List<ProcessEquipmentInterface> units = getAllUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equipment = units.get(i);
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
  // Advanced Optimization Methods: Armijo-Wolfe Line Search and BFGS
  // ==========================================================================

  /**
   * Performs gradient descent with Armijo-Wolfe line search conditions.
   *
   * <p>
   * The Armijo-Wolfe conditions ensure:
   * </p>
   * <ul>
   * <li><strong>Sufficient decrease (Armijo):</strong> f(x + alpha*d) &lt;= f(x) + c1*alpha*grad'*d
   * </li>
   * <li><strong>Curvature condition (Wolfe):</strong> |grad(x + alpha*d)'*d| &lt;=
   * c2*|grad'*d|</li>
   * </ul>
   * <p>
   * These conditions guarantee convergence and avoid too-small or too-large steps.
   * </p>
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param initialFlow starting flow rate in kg/hr
   * @return optimal flow rate in kg/hr
   */
  public double gradientDescentArmijoWolfeSearch(double inletPressure, double outletPressure,
      double initialFlow) {
    double flow = initialFlow;
    double alpha = Math.max(initialFlow * 0.1, 100.0); // Initial step size based on flow scale

    for (int iter = 0; iter < maxIterations; iter++) {
      double f0 = evaluateConstrainedObjective(inletPressure, outletPressure, flow);

      // Handle invalid objective values
      if (Double.isNaN(f0) || Double.isInfinite(f0)) {
        logger.debug("Invalid objective at flow {}", flow);
        return flow;
      }

      double grad = estimateGradient(inletPressure, outletPressure, flow);

      // Handle invalid gradient
      if (Double.isNaN(grad) || Double.isInfinite(grad)) {
        logger.debug("Invalid gradient at flow {}", flow);
        return flow;
      }

      // Check convergence
      if (Math.abs(grad) < bfgsGradientTolerance) {
        logger.debug("Armijo-Wolfe converged at iter {} with flow {}", iter, flow);
        break;
      }

      // Search direction (maximize, so use positive gradient)
      double direction = Math.signum(grad);
      double directionalDerivative = Math.abs(grad); // Always positive for line search

      // Armijo-Wolfe line search
      alpha = armijoWolfeLineSearch(inletPressure, outletPressure, flow, direction, f0,
          directionalDerivative, alpha);

      if (alpha <= 0 || Double.isNaN(alpha)) {
        logger.debug("Line search failed at iter {}", iter);
        // Fall back to simple step
        alpha = Math.max(flow * 0.01, 10.0);
      }

      // Update flow
      double newFlow = flow + alpha * direction;
      newFlow = Math.max(newFlow, 1.0); // Ensure positive

      // Check if we made progress
      if (Math.abs(newFlow - flow) < 1.0) {
        logger.debug("Armijo-Wolfe: step too small at iter {}", iter);
        break;
      }

      flow = newFlow;
    }

    return flow;
  }

  /**
   * Performs Armijo-Wolfe line search to find step size satisfying both conditions.
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param flow current flow rate
   * @param direction search direction
   * @param f0 objective at current point
   * @param directionalDerivative gradient dot direction
   * @param initialAlpha initial step size guess
   * @return step size satisfying Armijo-Wolfe conditions, or initial alpha if failed
   */
  private double armijoWolfeLineSearch(double inletPressure, double outletPressure, double flow,
      double direction, double f0, double directionalDerivative, double initialAlpha) {
    double alphaLo = 0.0;
    double alphaHi = initialAlpha * 10.0; // Bounded upper limit instead of MAX_VALUE
    double alpha = initialAlpha;
    double bestAlpha = initialAlpha;
    double bestObjective = f0;

    for (int i = 0; i < maxLineSearchIterations; i++) {
      double newFlow = flow + alpha * direction;

      // Ensure positive flow
      if (newFlow <= 0) {
        alphaHi = alpha;
        alpha = (alphaLo + alphaHi) / 2.0;
        if (alpha < 1e-10) {
          break;
        }
        continue;
      }

      double fNew = evaluateConstrainedObjective(inletPressure, outletPressure, newFlow);

      // Handle invalid values
      if (Double.isNaN(fNew) || Double.isInfinite(fNew)) {
        alphaHi = alpha;
        alpha = (alphaLo + alphaHi) / 2.0;
        continue;
      }

      // Track best found
      if (fNew > bestObjective) {
        bestAlpha = alpha;
        bestObjective = fNew;
      }

      // Check Armijo condition (sufficient decrease)
      // For maximization: f(new) >= f0 + c1*alpha*grad
      boolean armijoSatisfied = fNew >= f0 + armijoC1 * alpha * directionalDerivative;

      if (!armijoSatisfied) {
        // Step too large, reduce
        alphaHi = alpha;
        alpha = (alphaLo + alphaHi) / 2.0;
        continue;
      }

      // Check Wolfe curvature condition
      double gradNew = estimateGradient(inletPressure, outletPressure, newFlow);

      // Handle invalid gradient
      if (Double.isNaN(gradNew) || Double.isInfinite(gradNew)) {
        return bestAlpha; // Return best found so far
      }

      double newDirectionalDerivative = gradNew * direction;

      // For maximization: |grad_new| <= c2 * |grad_old|
      boolean wolfeSatisfied =
          Math.abs(newDirectionalDerivative) <= wolfeC2 * Math.abs(directionalDerivative);

      if (wolfeSatisfied) {
        return alpha; // Found acceptable step
      }

      // Curvature not satisfied
      if (newDirectionalDerivative * direction > 0) {
        // Gradient still positive (for maximization), try larger step
        alphaLo = alpha;
        alpha = Math.min(2.0 * alpha, alphaHi);
      } else {
        // Overshot, reduce step
        alphaHi = alpha;
        alpha = (alphaLo + alphaHi) / 2.0;
      }

      // Check for convergence
      if (alphaHi - alphaLo < 1e-10) {
        break;
      }
    }

    // Return best alpha found
    return bestAlpha > 0 ? bestAlpha : initialAlpha * 0.1;
  }

  /**
   * Performs BFGS (Broyden-Fletcher-Goldfarb-Shanno) quasi-Newton optimization.
   *
   * <p>
   * BFGS is a quasi-Newton method that approximates the inverse Hessian matrix using gradient
   * information. This provides superlinear convergence near the optimum, typically much faster than
   * steepest descent.
   * </p>
   *
   * <p>
   * For the 1D flow optimization problem, this simplifies to a scalar version that maintains an
   * approximation of the inverse second derivative.
   * </p>
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param initialFlow starting flow rate in kg/hr
   * @param minFlow minimum allowed flow in kg/hr
   * @param maxFlow maximum allowed flow in kg/hr
   * @return optimal flow rate in kg/hr
   */
  public double bfgsSearch(double inletPressure, double outletPressure, double initialFlow,
      double minFlow, double maxFlow) {
    double flow = initialFlow;
    double H = 1000.0; // Initial inverse Hessian approximation (larger for faster initial steps)
    double bestFlow = flow;
    double bestObjective = evaluateConstrainedObjective(inletPressure, outletPressure, flow);

    double grad = estimateGradient(inletPressure, outletPressure, flow);

    for (int iter = 0; iter < maxIterations; iter++) {
      // Handle invalid gradient
      if (Double.isNaN(grad) || Double.isInfinite(grad)) {
        logger.debug("BFGS: invalid gradient at iter {}", iter);
        return bestFlow;
      }

      // Check convergence
      if (Math.abs(grad) < bfgsGradientTolerance) {
        logger.debug("BFGS converged at iter {} with flow {}", iter, flow);
        break;
      }

      // Compute search direction: d = H * grad (for maximization)
      // Use gradient direction directly scaled by H
      double stepSize = H * Math.abs(grad);
      stepSize = Math.min(stepSize, (maxFlow - minFlow) * 0.5); // Limit step size
      stepSize = Math.max(stepSize, 1.0); // Minimum step

      double direction = Math.signum(grad);
      double newFlow = flow + stepSize * direction;

      // Enforce bounds
      newFlow = Math.max(minFlow, Math.min(maxFlow, newFlow));

      // Evaluate new point
      double newObjective = evaluateConstrainedObjective(inletPressure, outletPressure, newFlow);

      // Track best solution
      if (newObjective > bestObjective) {
        bestFlow = newFlow;
        bestObjective = newObjective;
      }

      // Compute actual step taken
      double s = newFlow - flow;

      if (Math.abs(s) < 1e-6) {
        logger.debug("BFGS: step too small at iter {}", iter);
        break;
      }

      // Compute new gradient
      double newGrad = estimateGradient(inletPressure, outletPressure, newFlow);

      // Handle invalid new gradient
      if (Double.isNaN(newGrad) || Double.isInfinite(newGrad)) {
        logger.debug("BFGS: invalid new gradient at iter {}", iter);
        return bestFlow;
      }

      // Gradient difference
      double y = newGrad - grad;

      // BFGS update for inverse Hessian (1D scalar version)
      // Standard BFGS: H_new = s / y for 1D
      double sy = s * y;

      if (Math.abs(sy) > 1e-10) {
        // Secant update
        double newH = Math.abs(s / y);
        // Blend with previous to avoid oscillation
        H = 0.8 * newH + 0.2 * H;
      }

      // Safeguard: bound H to prevent numerical issues
      H = Math.max(1.0, Math.min(H, 1e5));

      // Backtracking if no improvement
      if (newObjective < bestObjective - 1e-6) {
        // Not improving, reduce H
        H *= 0.5;
      }

      // Update state
      flow = newFlow;
      grad = newGrad;
    }

    return bestFlow;
  }

  /**
   * Sets the Armijo sufficient decrease parameter (c1).
   *
   * <p>
   * Typical values: 1e-4 (default). Must satisfy 0 &lt; c1 &lt; c2 &lt; 1.
   * </p>
   *
   * @param c1 the Armijo parameter (default 1e-4)
   */
  public void setArmijoC1(double c1) {
    if (c1 <= 0 || c1 >= 1) {
      throw new IllegalArgumentException("Armijo c1 must be in (0, 1)");
    }
    this.armijoC1 = c1;
  }

  /**
   * Sets the Wolfe curvature condition parameter (c2).
   *
   * <p>
   * Typical values: 0.9 for quasi-Newton methods (default), 0.1 for conjugate gradient. Must
   * satisfy 0 &lt; c1 &lt; c2 &lt; 1.
   * </p>
   *
   * @param c2 the Wolfe curvature parameter (default 0.9)
   */
  public void setWolfeC2(double c2) {
    if (c2 <= 0 || c2 >= 1) {
      throw new IllegalArgumentException("Wolfe c2 must be in (0, 1)");
    }
    this.wolfeC2 = c2;
  }

  /**
   * Sets the BFGS gradient tolerance for convergence.
   *
   * @param tolerance gradient tolerance (default 1e-6)
   */
  public void setBfgsGradientTolerance(double tolerance) {
    this.bfgsGradientTolerance = tolerance;
  }

  /**
   * Sets the maximum number of line search iterations.
   *
   * @param iterations maximum iterations (default 20)
   */
  public void setMaxLineSearchIterations(int iterations) {
    this.maxLineSearchIterations = iterations;
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
    if (hasProcess()) {
      setFeedFlowRate(optimalFlow);
      runSimulation();

      Map<String, Double> margins = new HashMap<String, Double>();
      String tightestConstraint = null;
      double smallestMargin = Double.MAX_VALUE;

      List<ProcessEquipmentInterface> units = getAllUnitOperations();
      for (int i = 0; i < units.size(); i++) {
        ProcessEquipmentInterface equipment = units.get(i);
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

    if (!hasProcess()) {
      return shadowPrices;
    }

    // Run at optimal
    setFeedFlowRate(optimalFlow);
    runSimulation();

    // For each equipment, estimate how much relaxing its constraint would help
    List<ProcessEquipmentInterface> units = getAllUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equipment = units.get(i);
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
    ProcessSystem primarySystem = getPrimaryProcessSystem();
    if (primarySystem == null) {
      throw new IllegalStateException("Process system must be set before creating optimizer");
    }

    // Find first and last stream names
    String inletName = "FeedStream";
    String outletName = "OutletStream";

    List<ProcessEquipmentInterface> units = getAllUnitOperations();
    if (units.size() > 0) {
      inletName = units.get(0).getName();
      int lastIdx = units.size() - 1;
      outletName = units.get(lastIdx).getName();
    }

    FlowRateOptimizer optimizer = new FlowRateOptimizer(primarySystem, inletName, outletName);
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
    List<ProcessEquipmentInterface> units = getAllUnitOperations();
    if (units.size() > 0) {
      int lastIdx = units.size() - 1;
      outletName = units.get(lastIdx).getName();
    }

    ProcessSystem primarySystem = getPrimaryProcessSystem();
    FlowRateOptimizer optimizer = new FlowRateOptimizer(primarySystem, feedStreamName, outletName);
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
   *
   * <p>
   * If a feed stream name is specified via {@link #setFeedStreamName(String)}, that stream will be
   * used. Otherwise, the first unit operation is assumed to be the feed stream.
   * </p>
   *
   * @param flowKgPerHr flow rate in kg/hr
   */
  private void setFeedFlowRate(double flowKgPerHr) {
    ProcessEquipmentInterface feedUnit = getFeedStream();
    if (feedUnit != null && feedUnit.getFluid() != null) {
      feedUnit.getFluid().setTotalFlowRate(flowKgPerHr, "kg/hr");
    }
  }

  /**
   * Sets the inlet pressure.
   *
   * @param pressureBara pressure in bara
   */
  private void setInletPressure(double pressureBara) {
    ProcessEquipmentInterface feedUnit = getFeedStream();
    if (feedUnit != null && feedUnit.getFluid() != null) {
      feedUnit.getFluid().setPressure(pressureBara, "bara");
    }
  }

  /**
   * Gets the feed stream being used for optimization.
   *
   * <p>
   * If a feed stream name is specified, finds that stream by name. Otherwise, returns the first
   * unit operation.
   * </p>
   *
   * @return the feed stream, or null if not found
   */
  private ProcessEquipmentInterface getFeedStream() {
    List<ProcessEquipmentInterface> units = getAllUnitOperations();
    if (units.isEmpty()) {
      return null;
    }

    // If feed stream name is specified, find it
    if (feedStreamName != null && !feedStreamName.isEmpty()) {
      for (ProcessEquipmentInterface unit : units) {
        if (feedStreamName.equals(unit.getName())) {
          return unit;
        }
      }
      logger.warn("Feed stream '{}' not found, using first unit operation", feedStreamName);
    }

    // Default to first unit operation
    return units.get(0);
  }

  /**
   * Sets the name of the feed stream to vary during optimization.
   *
   * <p>
   * Use this method to explicitly specify which stream should have its flow rate varied. If not
   * set, the first unit operation in the process is used by default.
   * </p>
   *
   * @param name the name of the feed stream
   * @return this engine for method chaining
   */
  public ProcessOptimizationEngine setFeedStreamName(String name) {
    this.feedStreamName = name;
    return this;
  }

  /**
   * Gets the name of the feed stream being varied.
   *
   * @return the feed stream name, or null if using default (first unit)
   */
  public String getFeedStreamName() {
    if (feedStreamName != null) {
      return feedStreamName;
    }
    // Return name of default feed stream
    ProcessEquipmentInterface feedUnit = getFeedStream();
    return feedUnit != null ? feedUnit.getName() : null;
  }

  /**
   * Sets inlet conditions.
   */
  private void setInletConditions(double pressure, double temperature, double waterCut,
      double gor) {
    // Simplified - actual implementation would modify fluid composition
    setInletPressure(pressure);
    // Additional composition changes based on waterCut and GOR would go here
  }

  /**
   * Gets the outlet stream used for optimization.
   *
   * <p>
   * If {@link #setOutletStreamName(String)} was called, returns that stream. Otherwise returns the
   * last unit operation in the process.
   * </p>
   *
   * @return the outlet stream, or null if not found
   */
  private ProcessEquipmentInterface getOutletStream() {
    List<ProcessEquipmentInterface> units = getAllUnitOperations();
    if (units.isEmpty()) {
      return null;
    }

    // If outlet stream name is specified, find it
    if (outletStreamName != null && !outletStreamName.isEmpty()) {
      for (ProcessEquipmentInterface unit : units) {
        if (outletStreamName.equals(unit.getName())) {
          return unit;
        }
      }
      logger.warn("Outlet stream '{}' not found, using last unit operation", outletStreamName);
    }

    // Default to last unit operation
    return units.get(units.size() - 1);
  }

  /**
   * Sets the name of the outlet stream to monitor during optimization.
   *
   * <p>
   * Use this method to explicitly specify which stream should be monitored for outlet conditions
   * (pressure, temperature, flow rate). If not set, the last unit operation in the process is used
   * by default.
   * </p>
   *
   * @param name the name of the outlet stream
   * @return this engine for method chaining
   */
  public ProcessOptimizationEngine setOutletStreamName(String name) {
    this.outletStreamName = name;
    return this;
  }

  /**
   * Gets the name of the outlet stream being monitored.
   *
   * @return the outlet stream name, or null if using default (last unit)
   */
  public String getOutletStreamName() {
    if (outletStreamName != null) {
      return outletStreamName;
    }
    // Return name of default outlet stream
    ProcessEquipmentInterface outletUnit = getOutletStream();
    return outletUnit != null ? outletUnit.getName() : null;
  }

  /**
   * Gets the outlet pressure from the configured outlet stream.
   *
   * @return outlet pressure in bara, or 0.0 if no outlet stream found
   */
  private double getOutletPressure() {
    ProcessEquipmentInterface outletUnit = getOutletStream();
    if (outletUnit != null && outletUnit.getFluid() != null) {
      return outletUnit.getFluid().getPressure("bara");
    }
    return 0.0;
  }

  /**
   * Gets the outlet temperature from the configured outlet stream.
   *
   * @return outlet temperature in Kelvin, or 0.0 if no outlet stream found
   */
  public double getOutletTemperature() {
    ProcessEquipmentInterface outletUnit = getOutletStream();
    if (outletUnit != null && outletUnit.getFluid() != null) {
      return outletUnit.getFluid().getTemperature();
    }
    return 0.0;
  }

  /**
   * Gets the outlet temperature from the configured outlet stream in specified unit.
   *
   * @param unit temperature unit ("K", "C", "R", "F")
   * @return outlet temperature in specified unit, or 0.0 if no outlet stream found
   */
  public double getOutletTemperature(String unit) {
    ProcessEquipmentInterface outletUnit = getOutletStream();
    if (outletUnit != null && outletUnit.getFluid() != null) {
      return outletUnit.getFluid().getTemperature(unit);
    }
    return 0.0;
  }

  /**
   * Gets the outlet flow rate from the configured outlet stream.
   *
   * @param flowUnit flow rate unit (e.g., "kg/hr", "MSm3/day")
   * @return outlet flow rate in specified unit, or 0.0 if no outlet stream found
   */
  public double getOutletFlowRate(String flowUnit) {
    ProcessEquipmentInterface outletUnit = getOutletStream();
    if (outletUnit != null && outletUnit.getFluid() != null) {
      return outletUnit.getFluid().getFlowRate(flowUnit);
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
    private static final long serialVersionUID = 2L;
    private String objective;
    private double optimalValue;
    private boolean converged;
    private String errorMessage;
    private String bottleneck;
    private List<String> constraintViolations = new ArrayList<String>();
    private SensitivityResult sensitivity;
    private double inletPressure;
    private double outletPressure;

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

    /**
     * Gets the sensitivity analysis result.
     *
     * <p>
     * The sensitivity result is automatically generated when optimization completes, providing
     * information about constraint margins, flow gradients, and bottleneck equipment.
     * </p>
     *
     * @return the sensitivity result, or null if not available
     */
    public SensitivityResult getSensitivity() {
      return sensitivity;
    }

    /**
     * Sets the sensitivity analysis result.
     *
     * @param sensitivity the sensitivity result
     */
    public void setSensitivity(SensitivityResult sensitivity) {
      this.sensitivity = sensitivity;
    }

    /**
     * Gets the inlet pressure used in optimization.
     *
     * @return inlet pressure in bara
     */
    public double getInletPressure() {
      return inletPressure;
    }

    /**
     * Sets the inlet pressure.
     *
     * @param inletPressure inlet pressure in bara
     */
    public void setInletPressure(double inletPressure) {
      this.inletPressure = inletPressure;
    }

    /**
     * Gets the outlet pressure used in optimization.
     *
     * @return outlet pressure in bara
     */
    public double getOutletPressure() {
      return outletPressure;
    }

    /**
     * Sets the outlet pressure.
     *
     * @param outletPressure outlet pressure in bara
     */
    public void setOutletPressure(double outletPressure) {
      this.outletPressure = outletPressure;
    }

    /**
     * Checks if the system is near capacity at the optimal flow rate.
     *
     * <p>
     * Convenience method that delegates to the sensitivity result.
     * </p>
     *
     * @return true if utilization exceeds 95%
     */
    public boolean isNearCapacity() {
      return sensitivity != null && sensitivity.isAtCapacity();
    }

    /**
     * Gets the available margin before hitting capacity.
     *
     * <p>
     * Convenience method that delegates to the sensitivity result.
     * </p>
     *
     * @return margin as a fraction (0.05 = 5% headroom), or 1.0 if sensitivity not available
     */
    public double getAvailableMargin() {
      return sensitivity != null ? sensitivity.getTightestMargin() : 1.0;
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

  // ==========================================================================
  // ADJUSTER INTEGRATION
  // ==========================================================================

  /**
   * Gets all Adjuster units in the process system.
   *
   * @return list of Adjuster units
   */
  public List<neqsim.process.equipment.util.Adjuster> getAdjusters() {
    List<neqsim.process.equipment.util.Adjuster> adjusters =
        new ArrayList<neqsim.process.equipment.util.Adjuster>();
    if (processSystem == null) {
      return adjusters;
    }
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit instanceof neqsim.process.equipment.util.Adjuster) {
        adjusters.add((neqsim.process.equipment.util.Adjuster) unit);
      }
    }
    return adjusters;
  }

  /**
   * Temporarily disables all Adjusters during optimization.
   *
   * <p>
   * Adjusters can interfere with optimization by trying to converge to their own targets. This
   * method disables them and returns a list of the disabled adjusters for later re-enabling.
   * </p>
   *
   * @return list of adjusters that were disabled
   */
  public List<neqsim.process.equipment.util.Adjuster> disableAdjusters() {
    List<neqsim.process.equipment.util.Adjuster> disabled =
        new ArrayList<neqsim.process.equipment.util.Adjuster>();
    for (neqsim.process.equipment.util.Adjuster adj : getAdjusters()) {
      if (adj.isActive()) {
        adj.setActive(false);
        disabled.add(adj);
      }
    }
    return disabled;
  }

  /**
   * Re-enables previously disabled Adjusters.
   *
   * @param adjusters list of adjusters to re-enable
   */
  public void enableAdjusters(List<neqsim.process.equipment.util.Adjuster> adjusters) {
    for (neqsim.process.equipment.util.Adjuster adj : adjusters) {
      adj.setActive(true);
    }
  }

  /**
   * Optimizes with Adjusters temporarily disabled.
   *
   * <p>
   * This method disables all Adjusters during optimization, runs the optimization, and then
   * re-enables them. This prevents the Adjusters from interfering with the optimization search.
   * </p>
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param minFlow minimum flow rate in kg/hr
   * @param maxFlow maximum flow rate in kg/hr
   * @return optimization result
   */
  public OptimizationResult optimizeWithAdjustersDisabled(double inletPressure,
      double outletPressure, double minFlow, double maxFlow) {
    List<neqsim.process.equipment.util.Adjuster> disabled = disableAdjusters();
    try {
      return findMaximumThroughput(inletPressure, outletPressure, minFlow, maxFlow);
    } finally {
      enableAdjusters(disabled);
    }
  }

  /**
   * Creates an Adjuster to optimize flow rate for a target variable.
   *
   * <p>
   * This method creates a new Adjuster that adjusts the feed stream flow rate to achieve a target
   * value for a specified variable (e.g., outlet pressure, temperature).
   * </p>
   *
   * @param name name for the new Adjuster
   * @param feedStreamName name of the feed stream to adjust
   * @param targetEquipmentName name of the equipment with the target variable
   * @param targetVariable name of the target variable (e.g., "pressure", "temperature")
   * @param targetValue target value for the variable
   * @param targetUnit unit for the target value
   * @return the created Adjuster, or null if creation fails
   */
  public neqsim.process.equipment.util.Adjuster createFlowAdjuster(String name,
      String feedStreamName, String targetEquipmentName, String targetVariable, double targetValue,
      String targetUnit) {
    if (processSystem == null) {
      return null;
    }

    ProcessEquipmentInterface feedStream = processSystem.getUnit(feedStreamName);
    ProcessEquipmentInterface targetEquipment = processSystem.getUnit(targetEquipmentName);

    if (feedStream == null || targetEquipment == null) {
      logger.warn("Could not find feed stream '{}' or target equipment '{}'", feedStreamName,
          targetEquipmentName);
      return null;
    }

    neqsim.process.equipment.util.Adjuster adjuster =
        new neqsim.process.equipment.util.Adjuster(name);
    adjuster.setAdjustedVariable(feedStream, "flow rate", "kg/hr");
    adjuster.setTargetVariable(targetEquipment, targetVariable, targetValue, targetUnit);

    return adjuster;
  }

  /**
   * Coordinates optimization with existing Adjusters.
   *
   * <p>
   * This method performs optimization while respecting the targets set by existing Adjusters. It
   * finds the maximum flow rate that still allows all Adjusters to converge to their targets.
   * </p>
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param minFlow minimum flow rate in kg/hr
   * @param maxFlow maximum flow rate in kg/hr
   * @return optimization result with Adjuster-compatible flow rate
   */
  public OptimizationResult optimizeWithAdjusterTargets(double inletPressure, double outletPressure,
      double minFlow, double maxFlow) {
    OptimizationResult result = new OptimizationResult();
    result.setObjective("Maximum Throughput with Adjuster Targets");

    // First, find the unconstrained maximum
    OptimizationResult unconstrained =
        findMaximumThroughput(inletPressure, outletPressure, minFlow, maxFlow);

    // Now check if Adjusters can converge at this flow rate
    double testFlow = unconstrained.getOptimalValue();
    boolean allAdjustersConverge = checkAdjusterConvergence(testFlow);

    if (allAdjustersConverge) {
      // Good, Adjusters can handle this flow rate
      result.setOptimalValue(testFlow);
      result.setConverged(true);
    } else {
      // Need to reduce flow until Adjusters can converge
      double lowFlow = minFlow;
      double highFlow = testFlow;

      for (int i = 0; i < maxIterations; i++) {
        double midFlow = (lowFlow + highFlow) / 2.0;
        if (checkAdjusterConvergence(midFlow)) {
          lowFlow = midFlow;
        } else {
          highFlow = midFlow;
        }
        if ((highFlow - lowFlow) < tolerance) {
          break;
        }
      }
      result.setOptimalValue(lowFlow);
      result.setConverged(true);
    }

    return result;
  }

  /**
   * Checks if all Adjusters can converge at the given flow rate.
   *
   * @param flowRate flow rate to test in kg/hr
   * @return true if all Adjusters converge
   */
  private boolean checkAdjusterConvergence(double flowRate) {
    if (!hasProcess()) {
      return true;
    }

    // Set the flow rate
    setFeedFlowRate(flowRate);

    // Run the process with Adjusters active
    try {
      runSimulation();
    } catch (Exception e) {
      logger.debug("Process failed at flow rate {}: {}", flowRate, e.getMessage());
      return false;
    }

    // Check if all Adjusters converged
    for (neqsim.process.equipment.util.Adjuster adj : getAdjusters()) {
      if (adj.isActive() && !adj.solved()) {
        return false;
      }
    }

    return true;
  }
}
