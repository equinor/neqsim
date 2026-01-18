package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Black-box evaluator for external optimization algorithms.
 *
 * <p>
 * This class provides a unified interface for using NeqSim process simulations with external
 * optimization libraries such as SciPy, NLopt, Pyomo, or any gradient-based/derivative-free
 * optimizer.
 * </p>
 *
 * <h3>Key Features</h3>
 * <ul>
 * <li>Single-point evaluation: {@code evaluate(double[] x)} runs simulation and returns all
 * outputs</li>
 * <li>Parameter definition with bounds for decision variables</li>
 * <li>Multiple objectives support for multi-objective optimization</li>
 * <li>Constraint handling with automatic feasibility checking</li>
 * <li>Gradient estimation via finite differences for gradient-based optimizers</li>
 * <li>Thread-safe evaluation with process cloning option</li>
 * </ul>
 *
 * <h3>Python Integration Example</h3>
 * 
 * <pre>
 * # Using with SciPy
 * from neqsim import jneqsim
 * from scipy.optimize import minimize
 *
 * # Setup evaluator
 * evaluator = jneqsim.process.util.optimizer.ProcessSimulationEvaluator(process)
 * evaluator.addParameter("feed", "flowRate", 1000.0, 100000.0, "kg/hr")
 * evaluator.addObjective("power", lambda p: p.getUnit("Compressor").getPower("kW"))
 * evaluator.addConstraint("surge", lambda p: p.getUnit("Compressor").getSurgeMargin(), 0.1, 1.0)
 *
 * # Optimize
 * def objective(x):
 *     result = evaluator.evaluate(x)
 *     return result.getObjectives()[0]
 *
 * def constraint(x):
 *     result = evaluator.evaluate(x)
 *     return result.getConstraintMargins()
 *
 * result = minimize(objective, x0=[50000], bounds=evaluator.getBoundsAsList(),
 *                   constraints={'type': 'ineq', 'fun': constraint})
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessSimulationEvaluator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(ProcessSimulationEvaluator.class);

  /** The process system to evaluate. */
  private ProcessSystem processSystem;

  /** List of parameter definitions. */
  private List<ParameterDefinition> parameters = new ArrayList<ParameterDefinition>();

  /** List of objective functions. */
  private List<ObjectiveDefinition> objectives = new ArrayList<ObjectiveDefinition>();

  /** List of constraints. */
  private List<ConstraintDefinition> constraints = new ArrayList<ConstraintDefinition>();

  /** Step size for finite difference gradient estimation. */
  private double finiteDifferenceStep = 1e-4;

  /** Whether to use relative step size for finite differences. */
  private boolean useRelativeStep = true;

  /** Whether to clone process for each evaluation (thread-safe but slower). */
  private boolean cloneForEvaluation = false;

  /** Counter for number of evaluations. */
  private int evaluationCount = 0;

  /** Last evaluation result for caching. */
  private transient EvaluationResult lastResult;

  /** Last parameter values for cache validation. */
  private double[] lastParameters;

  // ============================================================================
  // Inner Classes
  // ============================================================================

  /**
   * Definition of a decision variable (parameter) for optimization.
   */
  public static class ParameterDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String equipmentName;
    private String propertyName;
    private double lowerBound;
    private double upperBound;
    private String unit;
    private double initialValue;
    private transient java.util.function.BiConsumer<ProcessSystem, Double> setter;

    /**
     * Default constructor.
     */
    public ParameterDefinition() {}

    /**
     * Constructor with all fields.
     *
     * @param name parameter name
     * @param equipmentName equipment name
     * @param propertyName property name
     * @param lowerBound lower bound
     * @param upperBound upper bound
     * @param unit unit of measurement
     */
    public ParameterDefinition(String name, String equipmentName, String propertyName,
        double lowerBound, double upperBound, String unit) {
      this.name = name;
      this.equipmentName = equipmentName;
      this.propertyName = propertyName;
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.unit = unit;
      this.initialValue = (lowerBound + upperBound) / 2.0;
    }

    // Getters and setters
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getEquipmentName() {
      return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
      this.equipmentName = equipmentName;
    }

    public String getPropertyName() {
      return propertyName;
    }

    public void setPropertyName(String propertyName) {
      this.propertyName = propertyName;
    }

    public double getLowerBound() {
      return lowerBound;
    }

    public void setLowerBound(double lowerBound) {
      this.lowerBound = lowerBound;
    }

    public double getUpperBound() {
      return upperBound;
    }

    public void setUpperBound(double upperBound) {
      this.upperBound = upperBound;
    }

    public String getUnit() {
      return unit;
    }

    public void setUnit(String unit) {
      this.unit = unit;
    }

    public double getInitialValue() {
      return initialValue;
    }

    public void setInitialValue(double initialValue) {
      this.initialValue = initialValue;
    }

    public java.util.function.BiConsumer<ProcessSystem, Double> getSetter() {
      return setter;
    }

    public void setSetter(java.util.function.BiConsumer<ProcessSystem, Double> setter) {
      this.setter = setter;
    }

    /**
     * Checks if value is within bounds.
     *
     * @param value value to check
     * @return true if within bounds
     */
    public boolean isWithinBounds(double value) {
      return value >= lowerBound && value <= upperBound;
    }

    /**
     * Clamps value to bounds.
     *
     * @param value value to clamp
     * @return clamped value
     */
    public double clamp(double value) {
      return Math.max(lowerBound, Math.min(upperBound, value));
    }
  }

  /**
   * Definition of an objective function.
   */
  public static class ObjectiveDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Optimization direction. */
    public enum Direction {
      MINIMIZE, MAXIMIZE
    }

    private String name;
    private Direction direction = Direction.MINIMIZE;
    private String unit;
    private double weight = 1.0;
    private transient ToDoubleFunction<ProcessSystem> evaluator;

    /**
     * Default constructor.
     */
    public ObjectiveDefinition() {}

    /**
     * Constructor with evaluator.
     *
     * @param name objective name
     * @param evaluator evaluation function
     * @param direction optimization direction
     */
    public ObjectiveDefinition(String name, ToDoubleFunction<ProcessSystem> evaluator,
        Direction direction) {
      this.name = name;
      this.evaluator = evaluator;
      this.direction = direction;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Direction getDirection() {
      return direction;
    }

    public void setDirection(Direction direction) {
      this.direction = direction;
    }

    public String getUnit() {
      return unit;
    }

    public void setUnit(String unit) {
      this.unit = unit;
    }

    public double getWeight() {
      return weight;
    }

    public void setWeight(double weight) {
      this.weight = weight;
    }

    public ToDoubleFunction<ProcessSystem> getEvaluator() {
      return evaluator;
    }

    public void setEvaluator(ToDoubleFunction<ProcessSystem> evaluator) {
      this.evaluator = evaluator;
    }

    /**
     * Evaluates the objective for given process.
     *
     * @param process the process system
     * @return objective value (sign-adjusted for minimization)
     */
    public double evaluate(ProcessSystem process) {
      double value = evaluator.applyAsDouble(process);
      return direction == Direction.MAXIMIZE ? -value : value;
    }

    /**
     * Gets the raw (non-sign-adjusted) objective value.
     *
     * @param process the process system
     * @return raw objective value
     */
    public double evaluateRaw(ProcessSystem process) {
      return evaluator.applyAsDouble(process);
    }
  }

  /**
   * Definition of a constraint.
   */
  public static class ConstraintDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Constraint type. */
    public enum Type {
      /** g(x) &gt;= lowerBound. */
      LOWER_BOUND,
      /** g(x) &lt;= upperBound. */
      UPPER_BOUND,
      /** lowerBound &lt;= g(x) &lt;= upperBound. */
      RANGE,
      /** g(x) == target (with tolerance). */
      EQUALITY
    }

    private String name;
    private Type type = Type.LOWER_BOUND;
    private double lowerBound = Double.NEGATIVE_INFINITY;
    private double upperBound = Double.POSITIVE_INFINITY;
    private double equalityTolerance = 1e-6;
    private String unit;
    private boolean isHard = true;
    private double penaltyWeight = 1000.0;
    private transient ToDoubleFunction<ProcessSystem> evaluator;

    /**
     * Default constructor.
     */
    public ConstraintDefinition() {}

    /**
     * Constructor for lower bound constraint (g(x) &gt;= bound).
     *
     * @param name constraint name
     * @param evaluator evaluation function
     * @param lowerBound lower bound value
     */
    public ConstraintDefinition(String name, ToDoubleFunction<ProcessSystem> evaluator,
        double lowerBound) {
      this.name = name;
      this.evaluator = evaluator;
      this.lowerBound = lowerBound;
      this.type = Type.LOWER_BOUND;
    }

    /**
     * Constructor for range constraint.
     *
     * @param name constraint name
     * @param evaluator evaluation function
     * @param lowerBound lower bound
     * @param upperBound upper bound
     */
    public ConstraintDefinition(String name, ToDoubleFunction<ProcessSystem> evaluator,
        double lowerBound, double upperBound) {
      this.name = name;
      this.evaluator = evaluator;
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.type = Type.RANGE;
    }

    // Getters and setters
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Type getType() {
      return type;
    }

    public void setType(Type type) {
      this.type = type;
    }

    public double getLowerBound() {
      return lowerBound;
    }

    public void setLowerBound(double lowerBound) {
      this.lowerBound = lowerBound;
    }

    public double getUpperBound() {
      return upperBound;
    }

    public void setUpperBound(double upperBound) {
      this.upperBound = upperBound;
    }

    public double getEqualityTolerance() {
      return equalityTolerance;
    }

    public void setEqualityTolerance(double equalityTolerance) {
      this.equalityTolerance = equalityTolerance;
    }

    public String getUnit() {
      return unit;
    }

    public void setUnit(String unit) {
      this.unit = unit;
    }

    public boolean isHard() {
      return isHard;
    }

    public void setHard(boolean hard) {
      isHard = hard;
    }

    public double getPenaltyWeight() {
      return penaltyWeight;
    }

    public void setPenaltyWeight(double penaltyWeight) {
      this.penaltyWeight = penaltyWeight;
    }

    public ToDoubleFunction<ProcessSystem> getEvaluator() {
      return evaluator;
    }

    public void setEvaluator(ToDoubleFunction<ProcessSystem> evaluator) {
      this.evaluator = evaluator;
    }

    /**
     * Evaluates the constraint value.
     *
     * @param process the process system
     * @return constraint value g(x)
     */
    public double evaluate(ProcessSystem process) {
      return evaluator.applyAsDouble(process);
    }

    /**
     * Calculates the constraint margin (positive = satisfied, negative = violated).
     *
     * @param process the process system
     * @return constraint margin
     */
    public double margin(ProcessSystem process) {
      double value = evaluate(process);
      switch (type) {
        case LOWER_BOUND:
          return value - lowerBound;
        case UPPER_BOUND:
          return upperBound - value;
        case RANGE:
          return Math.min(value - lowerBound, upperBound - value);
        case EQUALITY:
          return equalityTolerance - Math.abs(value - lowerBound);
        default:
          return 0.0;
      }
    }

    /**
     * Checks if constraint is satisfied.
     *
     * @param process the process system
     * @return true if satisfied
     */
    public boolean isSatisfied(ProcessSystem process) {
      return margin(process) >= 0;
    }

    /**
     * Calculates penalty for constraint violation.
     *
     * @param process the process system
     * @return penalty (0 if satisfied, positive if violated)
     */
    public double penalty(ProcessSystem process) {
      double m = margin(process);
      if (m >= 0) {
        return 0.0;
      }
      return penaltyWeight * m * m;
    }
  }

  /**
   * Result of a single evaluation.
   */
  public static class EvaluationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private double[] parameters;
    private double[] objectives;
    private double[] objectivesRaw;
    private double[] constraintValues;
    private double[] constraintMargins;
    private boolean feasible;
    private boolean simulationConverged;
    private double penaltySum;
    private Map<String, Double> additionalOutputs = new HashMap<String, Double>();
    private String errorMessage;
    private long evaluationTimeMs;
    private int evaluationNumber;

    /**
     * Default constructor.
     */
    public EvaluationResult() {}

    // Getters and setters
    public double[] getParameters() {
      return parameters;
    }

    public void setParameters(double[] parameters) {
      this.parameters = parameters;
    }

    public double[] getObjectives() {
      return objectives;
    }

    public void setObjectives(double[] objectives) {
      this.objectives = objectives;
    }

    public double[] getObjectivesRaw() {
      return objectivesRaw;
    }

    public void setObjectivesRaw(double[] objectivesRaw) {
      this.objectivesRaw = objectivesRaw;
    }

    public double[] getConstraintValues() {
      return constraintValues;
    }

    public void setConstraintValues(double[] constraintValues) {
      this.constraintValues = constraintValues;
    }

    public double[] getConstraintMargins() {
      return constraintMargins;
    }

    public void setConstraintMargins(double[] constraintMargins) {
      this.constraintMargins = constraintMargins;
    }

    public boolean isFeasible() {
      return feasible;
    }

    public void setFeasible(boolean feasible) {
      this.feasible = feasible;
    }

    public boolean isSimulationConverged() {
      return simulationConverged;
    }

    public void setSimulationConverged(boolean simulationConverged) {
      this.simulationConverged = simulationConverged;
    }

    public double getPenaltySum() {
      return penaltySum;
    }

    public void setPenaltySum(double penaltySum) {
      this.penaltySum = penaltySum;
    }

    public Map<String, Double> getAdditionalOutputs() {
      return additionalOutputs;
    }

    public void setAdditionalOutputs(Map<String, Double> additionalOutputs) {
      this.additionalOutputs = additionalOutputs;
    }

    public void addOutput(String name, double value) {
      this.additionalOutputs.put(name, value);
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public long getEvaluationTimeMs() {
      return evaluationTimeMs;
    }

    public void setEvaluationTimeMs(long evaluationTimeMs) {
      this.evaluationTimeMs = evaluationTimeMs;
    }

    public int getEvaluationNumber() {
      return evaluationNumber;
    }

    public void setEvaluationNumber(int evaluationNumber) {
      this.evaluationNumber = evaluationNumber;
    }

    /**
     * Gets the primary (first) objective value.
     *
     * @return primary objective
     */
    public double getObjective() {
      return objectives != null && objectives.length > 0 ? objectives[0] : Double.NaN;
    }

    /**
     * Gets the penalized objective (objective + constraint penalties).
     *
     * @return penalized objective
     */
    public double getPenalizedObjective() {
      return getObjective() + penaltySum;
    }

    /**
     * Gets weighted sum of objectives.
     *
     * @param weights array of weights
     * @return weighted sum
     */
    public double getWeightedObjective(double[] weights) {
      if (objectives == null || weights == null) {
        return Double.NaN;
      }
      double sum = 0.0;
      for (int i = 0; i < Math.min(objectives.length, weights.length); i++) {
        sum += weights[i] * objectives[i];
      }
      return sum;
    }
  }

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public ProcessSimulationEvaluator() {}

  /**
   * Constructor with process system.
   *
   * @param processSystem the process system to evaluate
   */
  public ProcessSimulationEvaluator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  // ============================================================================
  // Parameter Definition Methods
  // ============================================================================

  /**
   * Adds a parameter (decision variable) for optimization.
   *
   * @param equipmentName name of the equipment
   * @param propertyName property to vary (flowRate, pressure, temperature, etc.)
   * @param lowerBound lower bound
   * @param upperBound upper bound
   * @param unit unit of measurement
   * @return this evaluator for chaining
   */
  public ProcessSimulationEvaluator addParameter(String equipmentName, String propertyName,
      double lowerBound, double upperBound, String unit) {
    String name = equipmentName + "." + propertyName;
    ParameterDefinition param =
        new ParameterDefinition(name, equipmentName, propertyName, lowerBound, upperBound, unit);
    parameters.add(param);
    return this;
  }

  /**
   * Adds a parameter with a custom setter function.
   *
   * @param name parameter name
   * @param setter function to set the parameter value
   * @param lowerBound lower bound
   * @param upperBound upper bound
   * @param unit unit of measurement
   * @return this evaluator for chaining
   */
  public ProcessSimulationEvaluator addParameterWithSetter(String name,
      java.util.function.BiConsumer<ProcessSystem, Double> setter, double lowerBound,
      double upperBound, String unit) {
    ParameterDefinition param = new ParameterDefinition();
    param.setName(name);
    param.setSetter(setter);
    param.setLowerBound(lowerBound);
    param.setUpperBound(upperBound);
    param.setUnit(unit);
    param.setInitialValue((lowerBound + upperBound) / 2.0);
    parameters.add(param);
    return this;
  }

  /**
   * Gets all parameter definitions.
   *
   * @return list of parameters
   */
  public List<ParameterDefinition> getParameters() {
    return parameters;
  }

  /**
   * Gets number of parameters.
   *
   * @return parameter count
   */
  public int getParameterCount() {
    return parameters.size();
  }

  /**
   * Gets parameter bounds as 2D array [[lb1,ub1], [lb2,ub2], ...].
   *
   * @return bounds array
   */
  public double[][] getBounds() {
    double[][] bounds = new double[parameters.size()][2];
    for (int i = 0; i < parameters.size(); i++) {
      bounds[i][0] = parameters.get(i).getLowerBound();
      bounds[i][1] = parameters.get(i).getUpperBound();
    }
    return bounds;
  }

  /**
   * Gets lower bounds array.
   *
   * @return lower bounds
   */
  public double[] getLowerBounds() {
    double[] lb = new double[parameters.size()];
    for (int i = 0; i < parameters.size(); i++) {
      lb[i] = parameters.get(i).getLowerBound();
    }
    return lb;
  }

  /**
   * Gets upper bounds array.
   *
   * @return upper bounds
   */
  public double[] getUpperBounds() {
    double[] ub = new double[parameters.size()];
    for (int i = 0; i < parameters.size(); i++) {
      ub[i] = parameters.get(i).getUpperBound();
    }
    return ub;
  }

  /**
   * Gets initial values array.
   *
   * @return initial values
   */
  public double[] getInitialValues() {
    double[] x0 = new double[parameters.size()];
    for (int i = 0; i < parameters.size(); i++) {
      x0[i] = parameters.get(i).getInitialValue();
    }
    return x0;
  }

  // ============================================================================
  // Objective Definition Methods
  // ============================================================================

  /**
   * Adds an objective function to minimize.
   *
   * @param name objective name
   * @param evaluator function that evaluates the objective
   * @return this evaluator for chaining
   */
  public ProcessSimulationEvaluator addObjective(String name,
      ToDoubleFunction<ProcessSystem> evaluator) {
    objectives
        .add(new ObjectiveDefinition(name, evaluator, ObjectiveDefinition.Direction.MINIMIZE));
    return this;
  }

  /**
   * Adds an objective function with direction.
   *
   * @param name objective name
   * @param evaluator function that evaluates the objective
   * @param direction MINIMIZE or MAXIMIZE
   * @return this evaluator for chaining
   */
  public ProcessSimulationEvaluator addObjective(String name,
      ToDoubleFunction<ProcessSystem> evaluator, ObjectiveDefinition.Direction direction) {
    objectives.add(new ObjectiveDefinition(name, evaluator, direction));
    return this;
  }

  /**
   * Adds a weighted objective function.
   *
   * @param name objective name
   * @param evaluator function that evaluates the objective
   * @param direction MINIMIZE or MAXIMIZE
   * @param weight weight for multi-objective optimization
   * @return this evaluator for chaining
   */
  public ProcessSimulationEvaluator addObjective(String name,
      ToDoubleFunction<ProcessSystem> evaluator, ObjectiveDefinition.Direction direction,
      double weight) {
    ObjectiveDefinition obj = new ObjectiveDefinition(name, evaluator, direction);
    obj.setWeight(weight);
    objectives.add(obj);
    return this;
  }

  /**
   * Gets all objective definitions.
   *
   * @return list of objectives
   */
  public List<ObjectiveDefinition> getObjectives() {
    return objectives;
  }

  /**
   * Gets number of objectives.
   *
   * @return objective count
   */
  public int getObjectiveCount() {
    return objectives.size();
  }

  // ============================================================================
  // Constraint Definition Methods
  // ============================================================================

  /**
   * Adds a constraint: g(x) >= lowerBound.
   *
   * @param name constraint name
   * @param evaluator function that evaluates g(x)
   * @param lowerBound minimum allowed value
   * @return this evaluator for chaining
   */
  public ProcessSimulationEvaluator addConstraintLowerBound(String name,
      ToDoubleFunction<ProcessSystem> evaluator, double lowerBound) {
    ConstraintDefinition c = new ConstraintDefinition(name, evaluator, lowerBound);
    c.setType(ConstraintDefinition.Type.LOWER_BOUND);
    constraints.add(c);
    return this;
  }

  /**
   * Adds a constraint: g(x) <= upperBound.
   *
   * @param name constraint name
   * @param evaluator function that evaluates g(x)
   * @param upperBound maximum allowed value
   * @return this evaluator for chaining
   */
  public ProcessSimulationEvaluator addConstraintUpperBound(String name,
      ToDoubleFunction<ProcessSystem> evaluator, double upperBound) {
    ConstraintDefinition c = new ConstraintDefinition();
    c.setName(name);
    c.setEvaluator(evaluator);
    c.setUpperBound(upperBound);
    c.setType(ConstraintDefinition.Type.UPPER_BOUND);
    constraints.add(c);
    return this;
  }

  /**
   * Adds a range constraint: lowerBound <= g(x) <= upperBound.
   *
   * @param name constraint name
   * @param evaluator function that evaluates g(x)
   * @param lowerBound minimum value
   * @param upperBound maximum value
   * @return this evaluator for chaining
   */
  public ProcessSimulationEvaluator addConstraintRange(String name,
      ToDoubleFunction<ProcessSystem> evaluator, double lowerBound, double upperBound) {
    ConstraintDefinition c = new ConstraintDefinition(name, evaluator, lowerBound, upperBound);
    constraints.add(c);
    return this;
  }

  /**
   * Adds an equality constraint: g(x) == target (within tolerance).
   *
   * @param name constraint name
   * @param evaluator function that evaluates g(x)
   * @param target target value
   * @param tolerance allowed deviation
   * @return this evaluator for chaining
   */
  public ProcessSimulationEvaluator addConstraintEquality(String name,
      ToDoubleFunction<ProcessSystem> evaluator, double target, double tolerance) {
    ConstraintDefinition c = new ConstraintDefinition();
    c.setName(name);
    c.setEvaluator(evaluator);
    c.setLowerBound(target);
    c.setEqualityTolerance(tolerance);
    c.setType(ConstraintDefinition.Type.EQUALITY);
    constraints.add(c);
    return this;
  }

  /**
   * Gets all constraint definitions.
   *
   * @return list of constraints
   */
  public List<ConstraintDefinition> getConstraints() {
    return constraints;
  }

  /**
   * Gets number of constraints.
   *
   * @return constraint count
   */
  public int getConstraintCount() {
    return constraints.size();
  }

  // ============================================================================
  // Evaluation Methods
  // ============================================================================

  /**
   * Evaluates the process for given parameter values.
   *
   * <p>
   * This is the main entry point for external optimizers. It:
   * </p>
   * <ol>
   * <li>Sets all parameter values on the process</li>
   * <li>Runs the simulation</li>
   * <li>Evaluates all objectives and constraints</li>
   * <li>Returns a complete result object</li>
   * </ol>
   *
   * @param x array of parameter values (length must match parameter count)
   * @return evaluation result with objectives, constraints, and feasibility
   */
  public EvaluationResult evaluate(double[] x) {
    if (x == null || x.length != parameters.size()) {
      throw new IllegalArgumentException(
          "Parameter array length (" + (x == null ? "null" : x.length)
              + ") must match parameter count (" + parameters.size() + ")");
    }

    long startTime = System.currentTimeMillis();
    evaluationCount++;

    EvaluationResult result = new EvaluationResult();
    result.setParameters(Arrays.copyOf(x, x.length));
    result.setEvaluationNumber(evaluationCount);

    try {
      // Get process to evaluate (clone if configured)
      ProcessSystem process = cloneForEvaluation ? processSystem.copy() : processSystem;

      // Set parameter values
      setParameterValues(process, x);

      // Run simulation
      process.run();
      result.setSimulationConverged(true);

      // Evaluate objectives
      double[] objValues = new double[objectives.size()];
      double[] objRaw = new double[objectives.size()];
      for (int i = 0; i < objectives.size(); i++) {
        objRaw[i] = objectives.get(i).evaluateRaw(process);
        objValues[i] = objectives.get(i).evaluate(process);
      }
      result.setObjectives(objValues);
      result.setObjectivesRaw(objRaw);

      // Evaluate constraints
      double[] constraintVals = new double[constraints.size()];
      double[] margins = new double[constraints.size()];
      double penaltySum = 0.0;
      boolean feasible = true;
      for (int i = 0; i < constraints.size(); i++) {
        constraintVals[i] = constraints.get(i).evaluate(process);
        margins[i] = constraints.get(i).margin(process);
        if (margins[i] < 0) {
          feasible = false;
          penaltySum += constraints.get(i).penalty(process);
        }
      }
      result.setConstraintValues(constraintVals);
      result.setConstraintMargins(margins);
      result.setFeasible(feasible);
      result.setPenaltySum(penaltySum);

    } catch (Exception e) {
      logger.warn("Evaluation failed: " + e.getMessage());
      result.setSimulationConverged(false);
      result.setFeasible(false);
      result.setErrorMessage(e.getMessage());
      result.setPenaltySum(Double.MAX_VALUE / 2);

      // Set NaN for objectives and constraints
      double[] nanObj = new double[objectives.size()];
      Arrays.fill(nanObj, Double.NaN);
      result.setObjectives(nanObj);
      result.setObjectivesRaw(nanObj);

      double[] nanCon = new double[constraints.size()];
      Arrays.fill(nanCon, Double.NEGATIVE_INFINITY);
      result.setConstraintMargins(nanCon);
    }

    result.setEvaluationTimeMs(System.currentTimeMillis() - startTime);
    lastResult = result;
    lastParameters = Arrays.copyOf(x, x.length);

    return result;
  }

  /**
   * Sets parameter values on the process.
   *
   * @param process the process system
   * @param x parameter values
   */
  private void setParameterValues(ProcessSystem process, double[] x) {
    for (int i = 0; i < parameters.size(); i++) {
      ParameterDefinition param = parameters.get(i);
      double value = param.clamp(x[i]); // Enforce bounds

      if (param.getSetter() != null) {
        // Use custom setter
        param.getSetter().accept(process, value);
      } else {
        // Use equipment property
        setEquipmentProperty(process, param.getEquipmentName(), param.getPropertyName(), value,
            param.getUnit());
      }
    }
  }

  /**
   * Sets a property on equipment by name.
   *
   * @param process the process system
   * @param equipmentName equipment name
   * @param propertyName property name
   * @param value value to set
   * @param unit unit of measurement
   */
  private void setEquipmentProperty(ProcessSystem process, String equipmentName,
      String propertyName, double value, String unit) {
    ProcessEquipmentInterface equipment = process.getUnit(equipmentName);
    if (equipment == null) {
      throw new IllegalArgumentException("Equipment not found: " + equipmentName);
    }

    String propLower = propertyName.toLowerCase();

    if (equipment instanceof StreamInterface) {
      StreamInterface stream = (StreamInterface) equipment;
      if (propLower.contains("flow")) {
        stream.setFlowRate(value, unit);
      } else if (propLower.contains("press")) {
        stream.setPressure(value, unit);
      } else if (propLower.contains("temp")) {
        stream.setTemperature(value, unit);
      } else {
        throw new IllegalArgumentException("Unknown property: " + propertyName);
      }
    } else {
      // Try reflection for other equipment types
      try {
        String methodName =
            "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        java.lang.reflect.Method method =
            equipment.getClass().getMethod(methodName, double.class, String.class);
        method.invoke(equipment, value, unit);
      } catch (NoSuchMethodException e) {
        // Try without unit
        try {
          String methodName =
              "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
          java.lang.reflect.Method method =
              equipment.getClass().getMethod(methodName, double.class);
          method.invoke(equipment, value);
        } catch (Exception ex) {
          throw new IllegalArgumentException(
              "Cannot set property " + propertyName + " on " + equipmentName + ": " + ex);
        }
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Cannot set property " + propertyName + " on " + equipmentName + ": " + e);
      }
    }
  }

  // ============================================================================
  // Gradient Estimation Methods
  // ============================================================================

  /**
   * Estimates the gradient of the primary objective using finite differences.
   *
   * @param x parameter values
   * @return gradient array
   */
  public double[] estimateGradient(double[] x) {
    return estimateGradient(x, 0);
  }

  /**
   * Estimates the gradient of a specific objective using finite differences.
   *
   * @param x parameter values
   * @param objectiveIndex which objective (0-based)
   * @return gradient array
   */
  public double[] estimateGradient(double[] x, int objectiveIndex) {
    double[] gradient = new double[x.length];
    double baseValue = evaluate(x).getObjectives()[objectiveIndex];

    for (int i = 0; i < x.length; i++) {
      double h = useRelativeStep ? finiteDifferenceStep * Math.max(Math.abs(x[i]), 1.0)
          : finiteDifferenceStep;

      double[] xPlus = Arrays.copyOf(x, x.length);
      xPlus[i] += h;

      // Enforce bounds
      if (xPlus[i] > parameters.get(i).getUpperBound()) {
        xPlus[i] = x[i];
        h = -h;
        xPlus[i] += h;
      }

      double valuePlus = evaluate(xPlus).getObjectives()[objectiveIndex];
      gradient[i] = (valuePlus - baseValue) / h;
    }

    return gradient;
  }

  /**
   * Estimates the Jacobian of all constraints using finite differences.
   *
   * @param x parameter values
   * @return Jacobian matrix [constraints x parameters]
   */
  public double[][] estimateConstraintJacobian(double[] x) {
    double[][] jacobian = new double[constraints.size()][x.length];
    double[] baseMargins = evaluate(x).getConstraintMargins();

    for (int i = 0; i < x.length; i++) {
      double h = useRelativeStep ? finiteDifferenceStep * Math.max(Math.abs(x[i]), 1.0)
          : finiteDifferenceStep;

      double[] xPlus = Arrays.copyOf(x, x.length);
      xPlus[i] += h;

      // Enforce bounds
      if (xPlus[i] > parameters.get(i).getUpperBound()) {
        xPlus[i] = x[i];
        h = -h;
        xPlus[i] += h;
      }

      double[] marginsPlus = evaluate(xPlus).getConstraintMargins();

      for (int j = 0; j < constraints.size(); j++) {
        jacobian[j][i] = (marginsPlus[j] - baseMargins[j]) / h;
      }
    }

    return jacobian;
  }

  // ============================================================================
  // Convenience Methods for External Optimizers
  // ============================================================================

  /**
   * Evaluates just the primary objective (for simple scalar optimizers).
   *
   * @param x parameter values
   * @return objective value (for minimization)
   */
  public double evaluateObjective(double[] x) {
    return evaluate(x).getObjective();
  }

  /**
   * Evaluates the penalized objective (objective + constraint penalties).
   *
   * @param x parameter values
   * @return penalized objective
   */
  public double evaluatePenalizedObjective(double[] x) {
    return evaluate(x).getPenalizedObjective();
  }

  /**
   * Checks if a point is feasible.
   *
   * @param x parameter values
   * @return true if all constraints satisfied
   */
  public boolean isFeasible(double[] x) {
    return evaluate(x).isFeasible();
  }

  /**
   * Gets constraint margins (positive = satisfied).
   *
   * @param x parameter values
   * @return array of constraint margins
   */
  public double[] getConstraintMargins(double[] x) {
    return evaluate(x).getConstraintMargins();
  }

  // ============================================================================
  // Configuration Methods
  // ============================================================================

  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  public void setProcessSystem(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  public double getFiniteDifferenceStep() {
    return finiteDifferenceStep;
  }

  public void setFiniteDifferenceStep(double finiteDifferenceStep) {
    this.finiteDifferenceStep = finiteDifferenceStep;
  }

  public boolean isUseRelativeStep() {
    return useRelativeStep;
  }

  public void setUseRelativeStep(boolean useRelativeStep) {
    this.useRelativeStep = useRelativeStep;
  }

  public boolean isCloneForEvaluation() {
    return cloneForEvaluation;
  }

  public void setCloneForEvaluation(boolean cloneForEvaluation) {
    this.cloneForEvaluation = cloneForEvaluation;
  }

  public int getEvaluationCount() {
    return evaluationCount;
  }

  public void resetEvaluationCount() {
    this.evaluationCount = 0;
  }

  public EvaluationResult getLastResult() {
    return lastResult;
  }

  // ============================================================================
  // Problem Definition Export
  // ============================================================================

  /**
   * Gets problem definition as a map (for JSON export).
   *
   * @return map representation of the problem
   */
  public Map<String, Object> getProblemDefinition() {
    Map<String, Object> definition = new HashMap<String, Object>();

    // Parameters
    List<Map<String, Object>> paramList = new ArrayList<Map<String, Object>>();
    for (ParameterDefinition p : parameters) {
      Map<String, Object> param = new HashMap<String, Object>();
      param.put("name", p.getName());
      param.put("equipmentName", p.getEquipmentName());
      param.put("propertyName", p.getPropertyName());
      param.put("lowerBound", p.getLowerBound());
      param.put("upperBound", p.getUpperBound());
      param.put("unit", p.getUnit());
      param.put("initialValue", p.getInitialValue());
      paramList.add(param);
    }
    definition.put("parameters", paramList);

    // Objectives
    List<Map<String, Object>> objList = new ArrayList<Map<String, Object>>();
    for (ObjectiveDefinition o : objectives) {
      Map<String, Object> obj = new HashMap<String, Object>();
      obj.put("name", o.getName());
      obj.put("direction", o.getDirection().toString());
      obj.put("weight", o.getWeight());
      obj.put("unit", o.getUnit());
      objList.add(obj);
    }
    definition.put("objectives", objList);

    // Constraints
    List<Map<String, Object>> conList = new ArrayList<Map<String, Object>>();
    for (ConstraintDefinition c : constraints) {
      Map<String, Object> con = new HashMap<String, Object>();
      con.put("name", c.getName());
      con.put("type", c.getType().toString());
      con.put("lowerBound", c.getLowerBound());
      con.put("upperBound", c.getUpperBound());
      con.put("isHard", c.isHard());
      con.put("unit", c.getUnit());
      conList.add(con);
    }
    definition.put("constraints", conList);

    definition.put("finiteDifferenceStep", finiteDifferenceStep);
    definition.put("cloneForEvaluation", cloneForEvaluation);

    return definition;
  }

  /**
   * Gets problem definition as JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    Map<String, Object> def = getProblemDefinition();
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");

    // Parameters
    sb.append("  \"parameters\": [\n");
    List<?> params = (List<?>) def.get("parameters");
    for (int i = 0; i < params.size(); i++) {
      Map<?, ?> p = (Map<?, ?>) params.get(i);
      sb.append("    {");
      sb.append("\"name\": \"").append(p.get("name")).append("\", ");
      sb.append("\"bounds\": [").append(p.get("lowerBound")).append(", ")
          .append(p.get("upperBound")).append("], ");
      sb.append("\"unit\": \"").append(p.get("unit")).append("\"");
      sb.append("}");
      if (i < params.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ],\n");

    // Objectives
    sb.append("  \"objectives\": [\n");
    List<?> objs = (List<?>) def.get("objectives");
    for (int i = 0; i < objs.size(); i++) {
      Map<?, ?> o = (Map<?, ?>) objs.get(i);
      sb.append("    {");
      sb.append("\"name\": \"").append(o.get("name")).append("\", ");
      sb.append("\"direction\": \"").append(o.get("direction")).append("\"");
      sb.append("}");
      if (i < objs.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ],\n");

    // Constraints
    sb.append("  \"constraints\": [\n");
    List<?> cons = (List<?>) def.get("constraints");
    for (int i = 0; i < cons.size(); i++) {
      Map<?, ?> c = (Map<?, ?>) cons.get(i);
      sb.append("    {");
      sb.append("\"name\": \"").append(c.get("name")).append("\", ");
      sb.append("\"type\": \"").append(c.get("type")).append("\"");
      sb.append("}");
      if (i < cons.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");

    sb.append("}");
    return sb.toString();
  }
}
