package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Black-box evaluator for large multi-area {@link ProcessModel} optimization problems.
 *
 * <p>
 * This evaluator fills the gap between the single-flowsheet {@link ProcessSimulationEvaluator} and
 * integrated models composed of several named {@link ProcessSystem} areas. Decision variables are
 * addressed with the existing {@link ProcessAutomation} syntax, for example
 * {@code "Wells::Producer A.flowRate"}. This keeps external optimizers independent of the internal
 * Java object graph while still allowing objective and constraint functions to inspect the full
 * model.
 * </p>
 *
 * <p>
 * The intended use case is fixed-equipment throughput optimization: vary producer/feed rates, run
 * the full {@link ProcessModel}, and stop when installed capacity constraints reveal the active
 * bottleneck. Capacity constraints can be added explicitly to equipment or discovered through the
 * {@link EquipmentCapacityStrategyRegistry}.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessModelSimulationEvaluator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(ProcessModelSimulationEvaluator.class);

  /** Process model evaluated by this instance. */
  private ProcessModel processModel;

  /** Decision variables. */
  private List<ParameterDefinition> parameters = new ArrayList<ParameterDefinition>();

  /** Objective functions. */
  private List<ObjectiveDefinition> objectives = new ArrayList<ObjectiveDefinition>();

  /** Constraint definitions. */
  private List<ConstraintDefinition> constraints = new ArrayList<ConstraintDefinition>();

  /** Step size for finite-difference sensitivities. */
  private double finiteDifferenceStep = 1e-4;

  /** Whether finite-difference steps are relative to the decision variable magnitude. */
  private boolean useRelativeStep = true;

  /** Whether strategy-generated equipment capacity constraints are included. */
  private boolean includeStrategyCapacityConstraints = true;

  /** Number of completed evaluation attempts. */
  private int evaluationCount = 0;

  /** Last evaluation result for inspection by optimizers and scripts. */
  private transient EvaluationResult lastResult;

  /** Last parameter vector evaluated. */
  private double[] lastParameters;

  /**
   * Definition of a process-model decision variable.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ParameterDefinition implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Human readable parameter name. */
    private String name;

    /** Area-qualified automation address. */
    private String address;

    /** Lower optimization bound. */
    private double lowerBound;

    /** Upper optimization bound. */
    private double upperBound;

    /** Unit of measure used when setting the value. */
    private String unit;

    /** Initial value for optimizers that need a starting point. */
    private double initialValue;

    /** Optional custom setter for non-automation variables. */
    private transient BiConsumer<ProcessModel, Double> setter;

    /** Default constructor for serialization frameworks. */
    public ParameterDefinition() {}

    /**
     * Creates a decision variable whose name is the same as its address.
     *
     * @param address the area-qualified automation address
     * @param lowerBound lower optimization bound
     * @param upperBound upper optimization bound
     * @param unit unit of measure used when setting the value
     */
    public ParameterDefinition(String address, double lowerBound, double upperBound, String unit) {
      this(address, address, lowerBound, upperBound, unit);
    }

    /**
     * Creates a decision variable.
     *
     * @param name human readable parameter name
     * @param address the area-qualified automation address
     * @param lowerBound lower optimization bound
     * @param upperBound upper optimization bound
     * @param unit unit of measure used when setting the value
     */
    public ParameterDefinition(String name, String address, double lowerBound, double upperBound,
        String unit) {
      this.name = name;
      this.address = address;
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.unit = unit;
      this.initialValue = (lowerBound + upperBound) / 2.0;
    }

    /**
     * Gets the parameter name.
     *
     * @return parameter name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the parameter name.
     *
     * @param name parameter name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets the automation address.
     *
     * @return area-qualified automation address
     */
    public String getAddress() {
      return address;
    }

    /**
     * Sets the automation address.
     *
     * @param address area-qualified automation address
     */
    public void setAddress(String address) {
      this.address = address;
    }

    /**
     * Gets the lower bound.
     *
     * @return lower bound
     */
    public double getLowerBound() {
      return lowerBound;
    }

    /**
     * Sets the lower bound.
     *
     * @param lowerBound lower bound
     */
    public void setLowerBound(double lowerBound) {
      this.lowerBound = lowerBound;
    }

    /**
     * Gets the upper bound.
     *
     * @return upper bound
     */
    public double getUpperBound() {
      return upperBound;
    }

    /**
     * Sets the upper bound.
     *
     * @param upperBound upper bound
     */
    public void setUpperBound(double upperBound) {
      this.upperBound = upperBound;
    }

    /**
     * Gets the unit of measure.
     *
     * @return unit of measure
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Sets the unit of measure.
     *
     * @param unit unit of measure
     */
    public void setUnit(String unit) {
      this.unit = unit;
    }

    /**
     * Gets the initial value.
     *
     * @return initial value
     */
    public double getInitialValue() {
      return initialValue;
    }

    /**
     * Sets the initial value.
     *
     * @param initialValue initial value
     */
    public void setInitialValue(double initialValue) {
      this.initialValue = initialValue;
    }

    /**
     * Gets the optional custom setter.
     *
     * @return custom setter, or null when automation should be used
     */
    public BiConsumer<ProcessModel, Double> getSetter() {
      return setter;
    }

    /**
     * Sets the optional custom setter.
     *
     * @param setter custom setter for this decision variable
     */
    public void setSetter(BiConsumer<ProcessModel, Double> setter) {
      this.setter = setter;
    }

    /**
     * Checks whether a value is inside the declared bounds.
     *
     * @param value value to test
     * @return true when the value is inside the declared bounds
     */
    public boolean isWithinBounds(double value) {
      return value >= lowerBound && value <= upperBound;
    }

    /**
     * Clamps a value to the declared bounds.
     *
     * @param value value to clamp
     * @return value limited to the inclusive lower and upper bounds
     */
    public double clamp(double value) {
      return Math.max(lowerBound, Math.min(upperBound, value));
    }
  }

  /**
   * Definition of a model-level objective function.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ObjectiveDefinition implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Optimization direction. */
    public enum Direction {
      /** Minimize the raw objective value. */
      MINIMIZE,
      /** Maximize the raw objective value by returning a sign-adjusted value to minimizers. */
      MAXIMIZE
    }

    /** Objective name. */
    private String name;

    /** Optimization direction. */
    private Direction direction = Direction.MINIMIZE;

    /** Unit of measure. */
    private String unit;

    /** Objective weight for external scalarization. */
    private double weight = 1.0;

    /** Objective evaluator. */
    private transient ToDoubleFunction<ProcessModel> evaluator;

    /** Default constructor for serialization frameworks. */
    public ObjectiveDefinition() {}

    /**
     * Creates an objective definition.
     *
     * @param name objective name
     * @param evaluator model-level evaluator
     * @param direction optimization direction
     */
    public ObjectiveDefinition(String name, ToDoubleFunction<ProcessModel> evaluator,
        Direction direction) {
      this.name = name;
      this.evaluator = evaluator;
      this.direction = direction;
    }

    /**
     * Gets the objective name.
     *
     * @return objective name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the objective name.
     *
     * @param name objective name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets the optimization direction.
     *
     * @return optimization direction
     */
    public Direction getDirection() {
      return direction;
    }

    /**
     * Sets the optimization direction.
     *
     * @param direction optimization direction
     */
    public void setDirection(Direction direction) {
      this.direction = direction;
    }

    /**
     * Gets the objective unit.
     *
     * @return objective unit
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Sets the objective unit.
     *
     * @param unit objective unit
     */
    public void setUnit(String unit) {
      this.unit = unit;
    }

    /**
     * Gets the objective weight.
     *
     * @return objective weight
     */
    public double getWeight() {
      return weight;
    }

    /**
     * Sets the objective weight.
     *
     * @param weight objective weight
     */
    public void setWeight(double weight) {
      this.weight = weight;
    }

    /**
     * Gets the objective evaluator.
     *
     * @return objective evaluator
     */
    public ToDoubleFunction<ProcessModel> getEvaluator() {
      return evaluator;
    }

    /**
     * Sets the objective evaluator.
     *
     * @param evaluator objective evaluator
     */
    public void setEvaluator(ToDoubleFunction<ProcessModel> evaluator) {
      this.evaluator = evaluator;
    }

    /**
     * Evaluates the objective using minimizer sign convention.
     *
     * @param model process model in its current state
     * @return sign-adjusted objective value
     */
    public double evaluate(ProcessModel model) {
      double value = evaluateRaw(model);
      return direction == Direction.MAXIMIZE ? -value : value;
    }

    /**
     * Evaluates the objective without sign adjustment.
     *
     * @param model process model in its current state
     * @return raw objective value
     */
    public double evaluateRaw(ProcessModel model) {
      if (evaluator == null) {
        throw new IllegalStateException("Objective evaluator is not set for " + name);
      }
      return evaluator.applyAsDouble(model);
    }
  }

  /**
   * Definition of a model-level constraint.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ConstraintDefinition implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Constraint type. */
    public enum Type {
      /** Constraint value must be greater than or equal to lower bound. */
      LOWER_BOUND,
      /** Constraint value must be less than or equal to upper bound. */
      UPPER_BOUND,
      /** Constraint value must lie inside lower and upper bounds. */
      RANGE,
      /** Constraint value must match the target within tolerance. */
      EQUALITY
    }

    /** Constraint name. */
    private String name;

    /** Constraint type. */
    private Type type = Type.LOWER_BOUND;

    /** Lower bound or equality target. */
    private double lowerBound = Double.NEGATIVE_INFINITY;

    /** Upper bound. */
    private double upperBound = Double.POSITIVE_INFINITY;

    /** Equality tolerance. */
    private double equalityTolerance = 1e-6;

    /** Unit of measure. */
    private String unit;

    /** Whether violation makes the solution infeasible. */
    private boolean hard = true;

    /** Penalty weight for violated constraints. */
    private double penaltyWeight = 1000.0;

    /** Model-level constraint evaluator. */
    private transient ToDoubleFunction<ProcessModel> evaluator;

    /** Whether this constraint represents equipment capacity utilization. */
    private boolean capacityConstraint = false;

    /** Area name for capacity constraints. */
    private String areaName;

    /** Equipment name for capacity constraints. */
    private String equipmentName;

    /** Original equipment constraint name for capacity constraints. */
    private String equipmentConstraintName;

    /** Captured capacity constraint for bottleneck metadata. */
    private transient CapacityConstraint capturedCapacityConstraint;

    /** Default constructor for serialization frameworks. */
    public ConstraintDefinition() {}

    /**
     * Creates a lower-bound model constraint.
     *
     * @param name constraint name
     * @param evaluator model-level evaluator
     * @param lowerBound lower bound
     */
    public ConstraintDefinition(String name, ToDoubleFunction<ProcessModel> evaluator,
        double lowerBound) {
      this.name = name;
      this.evaluator = evaluator;
      this.lowerBound = lowerBound;
      this.type = Type.LOWER_BOUND;
    }

    /**
     * Creates a range model constraint.
     *
     * @param name constraint name
     * @param evaluator model-level evaluator
     * @param lowerBound lower bound
     * @param upperBound upper bound
     */
    public ConstraintDefinition(String name, ToDoubleFunction<ProcessModel> evaluator,
        double lowerBound, double upperBound) {
      this.name = name;
      this.evaluator = evaluator;
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.type = Type.RANGE;
    }

    /**
     * Gets the constraint name.
     *
     * @return constraint name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the constraint name.
     *
     * @param name constraint name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets the constraint type.
     *
     * @return constraint type
     */
    public Type getType() {
      return type;
    }

    /**
     * Sets the constraint type.
     *
     * @param type constraint type
     */
    public void setType(Type type) {
      this.type = type;
    }

    /**
     * Gets the lower bound or equality target.
     *
     * @return lower bound or equality target
     */
    public double getLowerBound() {
      return lowerBound;
    }

    /**
     * Sets the lower bound or equality target.
     *
     * @param lowerBound lower bound or equality target
     */
    public void setLowerBound(double lowerBound) {
      this.lowerBound = lowerBound;
    }

    /**
     * Gets the upper bound.
     *
     * @return upper bound
     */
    public double getUpperBound() {
      return upperBound;
    }

    /**
     * Sets the upper bound.
     *
     * @param upperBound upper bound
     */
    public void setUpperBound(double upperBound) {
      this.upperBound = upperBound;
    }

    /**
     * Gets the equality tolerance.
     *
     * @return equality tolerance
     */
    public double getEqualityTolerance() {
      return equalityTolerance;
    }

    /**
     * Sets the equality tolerance.
     *
     * @param equalityTolerance equality tolerance
     */
    public void setEqualityTolerance(double equalityTolerance) {
      this.equalityTolerance = equalityTolerance;
    }

    /**
     * Gets the unit of measure.
     *
     * @return unit of measure
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Sets the unit of measure.
     *
     * @param unit unit of measure
     */
    public void setUnit(String unit) {
      this.unit = unit;
    }

    /**
     * Checks whether this is a hard constraint.
     *
     * @return true when violations make the solution infeasible
     */
    public boolean isHard() {
      return hard;
    }

    /**
     * Sets whether this is a hard constraint.
     *
     * @param hard true when violations make the solution infeasible
     */
    public void setHard(boolean hard) {
      this.hard = hard;
    }

    /**
     * Gets the penalty weight.
     *
     * @return penalty weight
     */
    public double getPenaltyWeight() {
      return penaltyWeight;
    }

    /**
     * Sets the penalty weight.
     *
     * @param penaltyWeight penalty weight
     */
    public void setPenaltyWeight(double penaltyWeight) {
      this.penaltyWeight = penaltyWeight;
    }

    /**
     * Gets the model-level evaluator.
     *
     * @return model-level evaluator
     */
    public ToDoubleFunction<ProcessModel> getEvaluator() {
      return evaluator;
    }

    /**
     * Sets the model-level evaluator.
     *
     * @param evaluator model-level evaluator
     */
    public void setEvaluator(ToDoubleFunction<ProcessModel> evaluator) {
      this.evaluator = evaluator;
    }

    /**
     * Checks whether this is an equipment capacity constraint.
     *
     * @return true when generated from a {@link CapacityConstraint}
     */
    public boolean isCapacityConstraint() {
      return capacityConstraint;
    }

    /**
     * Gets the process area name for capacity constraints.
     *
     * @return process area name, or null for non-capacity constraints
     */
    public String getAreaName() {
      return areaName;
    }

    /**
     * Gets the equipment name for capacity constraints.
     *
     * @return equipment name, or null for non-capacity constraints
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the original equipment constraint name.
     *
     * @return equipment constraint name, or null for non-capacity constraints
     */
    public String getEquipmentConstraintName() {
      return equipmentConstraintName;
    }

    /**
     * Gets the captured equipment capacity constraint.
     *
     * @return captured capacity constraint, or null when unavailable after serialization
     */
    public CapacityConstraint getCapturedCapacityConstraint() {
      return capturedCapacityConstraint;
    }

    /**
     * Marks this definition as an equipment capacity constraint.
     *
     * @param areaName process area name
     * @param equipmentName equipment name
     * @param equipmentConstraintName equipment constraint name
     * @param capacityConstraint captured capacity constraint
     */
    public void setCapacityMetadata(String areaName, String equipmentName,
        String equipmentConstraintName, CapacityConstraint capacityConstraint) {
      this.capacityConstraint = true;
      this.areaName = areaName;
      this.equipmentName = equipmentName;
      this.equipmentConstraintName = equipmentConstraintName;
      this.capturedCapacityConstraint = capacityConstraint;
    }

    /**
     * Evaluates the constraint value.
     *
     * @param model process model in its current state
     * @return constraint value
     */
    public double evaluate(ProcessModel model) {
      if (evaluator == null) {
        throw new IllegalStateException("Constraint evaluator is not set for " + name);
      }
      return evaluator.applyAsDouble(model);
    }

    /**
     * Computes the constraint margin.
     *
     * @param model process model in its current state
     * @return positive margin when satisfied and negative margin when violated
     */
    public double margin(ProcessModel model) {
      double value = evaluate(model);
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
     * Checks whether the constraint is satisfied.
     *
     * @param model process model in its current state
     * @return true when the margin is non-negative
     */
    public boolean isSatisfied(ProcessModel model) {
      return margin(model) >= 0.0;
    }

    /**
     * Computes the penalty for a constraint violation.
     *
     * @param model process model in its current state
     * @return zero when satisfied, otherwise a positive quadratic penalty
     */
    public double penalty(ProcessModel model) {
      double margin = margin(model);
      if (margin >= 0.0) {
        return 0.0;
      }
      return penaltyWeight * margin * margin;
    }

    /**
     * Gets the unified severity level.
     *
     * @return hard or soft severity level
     */
    public ConstraintSeverityLevel getSeverityLevel() {
      return ConstraintSeverityLevel.fromIsHard(hard);
    }
  }

  /**
   * Active bottleneck metadata for a process-model evaluation.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class BottleneckStatus implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Process area name. */
    private String areaName;

    /** Equipment name. */
    private String equipmentName;

    /** Constraint name. */
    private String constraintName;

    /** Utilization fraction. */
    private double utilization;

    /** Current constraint value. */
    private double currentValue;

    /** Design constraint value. */
    private double designValue;

    /** Constraint unit. */
    private String unit;

    /** Whether the bottleneck is inside feasible capacity. */
    private boolean feasible;

    /** Default constructor for serialization frameworks. */
    public BottleneckStatus() {}

    /**
     * Creates a bottleneck status.
     *
     * @param areaName process area name
     * @param equipmentName equipment name
     * @param constraintName constraint name
     * @param utilization utilization fraction
     * @param currentValue current constraint value
     * @param designValue design constraint value
     * @param unit constraint unit
     * @param feasible true when utilization is less than or equal to one
     */
    public BottleneckStatus(String areaName, String equipmentName, String constraintName,
        double utilization, double currentValue, double designValue, String unit,
        boolean feasible) {
      this.areaName = areaName;
      this.equipmentName = equipmentName;
      this.constraintName = constraintName;
      this.utilization = utilization;
      this.currentValue = currentValue;
      this.designValue = designValue;
      this.unit = unit;
      this.feasible = feasible;
    }

    /**
     * Creates an empty bottleneck status.
     *
     * @return status with no active equipment
     */
    public static BottleneckStatus none() {
      return new BottleneckStatus("", "", "", 0.0, 0.0, 0.0, "", true);
    }

    /**
     * Gets the area name.
     *
     * @return area name
     */
    public String getAreaName() {
      return areaName;
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
     * Gets the area-qualified equipment name.
     *
     * @return area-qualified equipment name, or empty string when not available
     */
    public String getQualifiedEquipmentName() {
      if (!isPresent()) {
        return "";
      }
      return areaName + ProcessAutomation.AREA_SEPARATOR + equipmentName;
    }

    /**
     * Gets the constraint name.
     *
     * @return constraint name
     */
    public String getConstraintName() {
      return constraintName;
    }

    /**
     * Gets the utilization fraction.
     *
     * @return utilization fraction
     */
    public double getUtilization() {
      return utilization;
    }

    /**
     * Gets the current value.
     *
     * @return current constraint value
     */
    public double getCurrentValue() {
      return currentValue;
    }

    /**
     * Gets the design value.
     *
     * @return design constraint value
     */
    public double getDesignValue() {
      return designValue;
    }

    /**
     * Gets the unit of measure.
     *
     * @return unit of measure
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Checks whether the bottleneck is feasible.
     *
     * @return true when utilization is less than or equal to one
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Checks whether this status contains an equipment reference.
     *
     * @return true when equipment name is available
     */
    public boolean isPresent() {
      return equipmentName != null && equipmentName.length() > 0;
    }
  }

  /**
   * Result of a single process-model evaluation.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class EvaluationResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Parameter vector used for the evaluation. */
    private double[] parameters;

    /** Sign-adjusted objective values. */
    private double[] objectives;

    /** Raw objective values. */
    private double[] objectivesRaw;

    /** Constraint values. */
    private double[] constraintValues;

    /** Constraint margins. */
    private double[] constraintMargins;

    /** Feasibility flag. */
    private boolean feasible;

    /** Process-model convergence flag. */
    private boolean simulationConverged;

    /** Sum of constraint penalties. */
    private double penaltySum;

    /** Active bottleneck metadata. */
    private BottleneckStatus activeBottleneck = BottleneckStatus.none();

    /** Additional scalar outputs. */
    private Map<String, Double> additionalOutputs = new LinkedHashMap<String, Double>();

    /** Error message when evaluation fails. */
    private String errorMessage;

    /** Wall-clock evaluation time in milliseconds. */
    private long evaluationTimeMs;

    /** Evaluation sequence number. */
    private int evaluationNumber;

    /** Default constructor. */
    public EvaluationResult() {}

    /**
     * Gets the evaluated parameters.
     *
     * @return parameter vector
     */
    public double[] getParameters() {
      return parameters;
    }

    /**
     * Sets the evaluated parameters.
     *
     * @param parameters parameter vector
     */
    public void setParameters(double[] parameters) {
      this.parameters = parameters;
    }

    /**
     * Gets sign-adjusted objectives.
     *
     * @return objective values
     */
    public double[] getObjectives() {
      return objectives;
    }

    /**
     * Sets sign-adjusted objectives.
     *
     * @param objectives objective values
     */
    public void setObjectives(double[] objectives) {
      this.objectives = objectives;
    }

    /**
     * Gets raw objective values.
     *
     * @return raw objective values
     */
    public double[] getObjectivesRaw() {
      return objectivesRaw;
    }

    /**
     * Sets raw objective values.
     *
     * @param objectivesRaw raw objective values
     */
    public void setObjectivesRaw(double[] objectivesRaw) {
      this.objectivesRaw = objectivesRaw;
    }

    /**
     * Gets constraint values.
     *
     * @return constraint values
     */
    public double[] getConstraintValues() {
      return constraintValues;
    }

    /**
     * Sets constraint values.
     *
     * @param constraintValues constraint values
     */
    public void setConstraintValues(double[] constraintValues) {
      this.constraintValues = constraintValues;
    }

    /**
     * Gets constraint margins.
     *
     * @return constraint margins
     */
    public double[] getConstraintMargins() {
      return constraintMargins;
    }

    /**
     * Sets constraint margins.
     *
     * @param constraintMargins constraint margins
     */
    public void setConstraintMargins(double[] constraintMargins) {
      this.constraintMargins = constraintMargins;
    }

    /**
     * Checks whether the point is feasible.
     *
     * @return true when simulation converged and hard constraints are satisfied
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Sets feasibility.
     *
     * @param feasible feasibility flag
     */
    public void setFeasible(boolean feasible) {
      this.feasible = feasible;
    }

    /**
     * Checks whether the simulation converged.
     *
     * @return true when the model reported convergence
     */
    public boolean isSimulationConverged() {
      return simulationConverged;
    }

    /**
     * Sets the convergence flag.
     *
     * @param simulationConverged convergence flag
     */
    public void setSimulationConverged(boolean simulationConverged) {
      this.simulationConverged = simulationConverged;
    }

    /**
     * Gets the penalty sum.
     *
     * @return penalty sum
     */
    public double getPenaltySum() {
      return penaltySum;
    }

    /**
     * Sets the penalty sum.
     *
     * @param penaltySum penalty sum
     */
    public void setPenaltySum(double penaltySum) {
      this.penaltySum = penaltySum;
    }

    /**
     * Gets the active bottleneck.
     *
     * @return active bottleneck status
     */
    public BottleneckStatus getActiveBottleneck() {
      return activeBottleneck;
    }

    /**
     * Sets the active bottleneck.
     *
     * @param activeBottleneck active bottleneck status
     */
    public void setActiveBottleneck(BottleneckStatus activeBottleneck) {
      this.activeBottleneck = activeBottleneck == null ? BottleneckStatus.none() : activeBottleneck;
    }

    /**
     * Gets additional scalar outputs.
     *
     * @return additional scalar outputs
     */
    public Map<String, Double> getAdditionalOutputs() {
      return additionalOutputs;
    }

    /**
     * Sets additional scalar outputs.
     *
     * @param additionalOutputs additional scalar outputs
     */
    public void setAdditionalOutputs(Map<String, Double> additionalOutputs) {
      this.additionalOutputs = additionalOutputs;
    }

    /**
     * Gets the error message.
     *
     * @return error message, or null when the evaluation succeeded
     */
    public String getErrorMessage() {
      return errorMessage;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage error message
     */
    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    /**
     * Gets the evaluation time.
     *
     * @return evaluation time in milliseconds
     */
    public long getEvaluationTimeMs() {
      return evaluationTimeMs;
    }

    /**
     * Sets the evaluation time.
     *
     * @param evaluationTimeMs evaluation time in milliseconds
     */
    public void setEvaluationTimeMs(long evaluationTimeMs) {
      this.evaluationTimeMs = evaluationTimeMs;
    }

    /**
     * Gets the evaluation sequence number.
     *
     * @return evaluation sequence number
     */
    public int getEvaluationNumber() {
      return evaluationNumber;
    }

    /**
     * Sets the evaluation sequence number.
     *
     * @param evaluationNumber evaluation sequence number
     */
    public void setEvaluationNumber(int evaluationNumber) {
      this.evaluationNumber = evaluationNumber;
    }

    /**
     * Gets the primary objective value.
     *
     * @return first sign-adjusted objective value, or NaN when no objective exists
     */
    public double getObjective() {
      return objectives != null && objectives.length > 0 ? objectives[0] : Double.NaN;
    }

    /**
     * Gets the penalized primary objective value.
     *
     * @return primary objective plus penalty sum
     */
    public double getPenalizedObjective() {
      double objective = getObjective();
      if (Double.isNaN(objective)) {
        return penaltySum;
      }
      return objective + penaltySum;
    }
  }

  /** Default constructor. */
  public ProcessModelSimulationEvaluator() {}

  /**
   * Creates an evaluator for a process model.
   *
   * @param processModel process model to evaluate
   */
  public ProcessModelSimulationEvaluator(ProcessModel processModel) {
    this.processModel = processModel;
  }

  /**
   * Gets the process model.
   *
   * @return process model
   */
  public ProcessModel getProcessModel() {
    return processModel;
  }

  /**
   * Sets the process model.
   *
   * @param processModel process model
   */
  public void setProcessModel(ProcessModel processModel) {
    this.processModel = processModel;
  }

  /**
   * Adds an automation-addressed decision variable.
   *
   * @param address area-qualified automation address
   * @param lowerBound lower optimization bound
   * @param upperBound upper optimization bound
   * @param unit unit used when setting the variable
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addParameter(String address, double lowerBound,
      double upperBound, String unit) {
    parameters.add(new ParameterDefinition(address, lowerBound, upperBound, unit));
    return this;
  }

  /**
   * Adds an automation-addressed decision variable with an explicit display name.
   *
   * @param name human readable parameter name
   * @param address area-qualified automation address
   * @param lowerBound lower optimization bound
   * @param upperBound upper optimization bound
   * @param unit unit used when setting the variable
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addParameter(String name, String address,
      double lowerBound, double upperBound, String unit) {
    parameters.add(new ParameterDefinition(name, address, lowerBound, upperBound, unit));
    return this;
  }

  /**
   * Adds a decision variable controlled by a custom setter.
   *
   * @param name human readable parameter name
   * @param setter custom setter receiving the model and the bounded value
   * @param lowerBound lower optimization bound
   * @param upperBound upper optimization bound
   * @param unit unit used for reporting the parameter
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addParameterWithSetter(String name,
      BiConsumer<ProcessModel, Double> setter, double lowerBound, double upperBound, String unit) {
    ParameterDefinition parameter =
        new ParameterDefinition(name, name, lowerBound, upperBound, unit);
    parameter.setSetter(setter);
    parameters.add(parameter);
    return this;
  }

  /**
   * Gets all parameters.
   *
   * @return parameter definitions
   */
  public List<ParameterDefinition> getParameters() {
    return parameters;
  }

  /**
   * Gets the number of parameters.
   *
   * @return parameter count
   */
  public int getParameterCount() {
    return parameters.size();
  }

  /**
   * Adds a minimization objective.
   *
   * @param name objective name
   * @param evaluator model-level objective evaluator
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addObjective(String name,
      ToDoubleFunction<ProcessModel> evaluator) {
    return addObjective(name, evaluator, ObjectiveDefinition.Direction.MINIMIZE);
  }

  /**
   * Adds an objective with explicit direction.
   *
   * @param name objective name
   * @param evaluator model-level objective evaluator
   * @param direction optimization direction
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addObjective(String name,
      ToDoubleFunction<ProcessModel> evaluator, ObjectiveDefinition.Direction direction) {
    objectives.add(new ObjectiveDefinition(name, evaluator, direction));
    return this;
  }

  /**
   * Gets all objectives.
   *
   * @return objective definitions
   */
  public List<ObjectiveDefinition> getObjectives() {
    return objectives;
  }

  /**
   * Gets the number of objectives.
   *
   * @return objective count
   */
  public int getObjectiveCount() {
    return objectives.size();
  }

  /**
   * Adds a lower-bound constraint.
   *
   * @param name constraint name
   * @param evaluator model-level constraint evaluator
   * @param lowerBound lower bound
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addConstraintLowerBound(String name,
      ToDoubleFunction<ProcessModel> evaluator, double lowerBound) {
    constraints.add(new ConstraintDefinition(name, evaluator, lowerBound));
    return this;
  }

  /**
   * Adds an upper-bound constraint.
   *
   * @param name constraint name
   * @param evaluator model-level constraint evaluator
   * @param upperBound upper bound
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addConstraintUpperBound(String name,
      ToDoubleFunction<ProcessModel> evaluator, double upperBound) {
    ConstraintDefinition constraint = new ConstraintDefinition();
    constraint.setName(name);
    constraint.setEvaluator(evaluator);
    constraint.setUpperBound(upperBound);
    constraint.setType(ConstraintDefinition.Type.UPPER_BOUND);
    constraints.add(constraint);
    return this;
  }

  /**
   * Adds a range constraint.
   *
   * @param name constraint name
   * @param evaluator model-level constraint evaluator
   * @param lowerBound lower bound
   * @param upperBound upper bound
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addConstraintRange(String name,
      ToDoubleFunction<ProcessModel> evaluator, double lowerBound, double upperBound) {
    constraints.add(new ConstraintDefinition(name, evaluator, lowerBound, upperBound));
    return this;
  }

  /**
   * Adds an equality constraint.
   *
   * @param name constraint name
   * @param evaluator model-level constraint evaluator
   * @param target target value
   * @param tolerance allowed absolute deviation from target
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addConstraintEquality(String name,
      ToDoubleFunction<ProcessModel> evaluator, double target, double tolerance) {
    ConstraintDefinition constraint = new ConstraintDefinition();
    constraint.setName(name);
    constraint.setEvaluator(evaluator);
    constraint.setLowerBound(target);
    constraint.setEqualityTolerance(tolerance);
    constraint.setType(ConstraintDefinition.Type.EQUALITY);
    constraints.add(constraint);
    return this;
  }

  /**
   * Gets all constraints.
   *
   * @return constraint definitions
   */
  public List<ConstraintDefinition> getConstraints() {
    return constraints;
  }

  /**
   * Gets the number of constraints.
   *
   * @return constraint count
   */
  public int getConstraintCount() {
    return constraints.size();
  }

  /**
   * Adds installed equipment capacity constraints from all process areas.
   *
   * <p>
   * Each enabled equipment capacity constraint becomes an upper-bound constraint where utilization
   * must be less than or equal to 1.0. Constraint names are area-qualified as
   * {@code "area::equipment/constraint"} so bottlenecks can be traced back to the full model.
   * </p>
   *
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addEquipmentCapacityConstraints() {
    ensureProcessModel();

    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    for (String areaName : processModel.getProcessSystemNames()) {
      ProcessSystem area = processModel.get(areaName);
      if (area == null) {
        continue;
      }
      for (ProcessEquipmentInterface equipment : area.getUnitOperations()) {
        Map<String, CapacityConstraint> equipmentConstraints =
            getAllCapacityConstraints(registry, equipment);
        if (equipmentConstraints.isEmpty()) {
          continue;
        }
        for (Map.Entry<String, CapacityConstraint> entry : equipmentConstraints.entrySet()) {
          CapacityConstraint capacityConstraint = entry.getValue();
          if (capacityConstraint == null || !capacityConstraint.isEnabled()) {
            continue;
          }
          addCapacityConstraint(areaName, equipment.getName(), entry.getKey(), capacityConstraint);
        }
      }
    }
    return this;
  }

  /**
   * Gets explicit and strategy-generated capacity constraints for equipment.
   *
   * @param registry capacity strategy registry
   * @param equipment equipment to inspect
   * @return merged constraint map with explicit equipment constraints taking precedence
   */
  private Map<String, CapacityConstraint> getAllCapacityConstraints(
      EquipmentCapacityStrategyRegistry registry, ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> equipmentConstraints =
        new LinkedHashMap<String, CapacityConstraint>();

    if (includeStrategyCapacityConstraints) {
      EquipmentCapacityStrategy strategy = registry.findStrategy(equipment);
      if (strategy != null) {
        equipmentConstraints.putAll(strategy.getConstraints(equipment));
      }
    }

    Map<String, CapacityConstraint> directConstraints = equipment.getCapacityConstraints();
    if (directConstraints != null) {
      equipmentConstraints.putAll(directConstraints);
    }

    return equipmentConstraints;
  }

  /**
   * Adds a single capacity constraint definition.
   *
   * @param areaName process area name
   * @param equipmentName equipment name
   * @param equipmentConstraintName equipment constraint name
   * @param capacityConstraint capacity constraint
   */
  private void addCapacityConstraint(String areaName, String equipmentName,
      String equipmentConstraintName, CapacityConstraint capacityConstraint) {
    String constraintName =
        areaName + ProcessAutomation.AREA_SEPARATOR + equipmentName + "/" + equipmentConstraintName;
    if (hasConstraint(constraintName)) {
      return;
    }

    final CapacityConstraint capturedCapacityConstraint = capacityConstraint;
    ConstraintDefinition definition = new ConstraintDefinition();
    definition.setName(constraintName);
    definition.setUnit(capacityConstraint.getUnit());
    definition.setType(ConstraintDefinition.Type.UPPER_BOUND);
    definition.setUpperBound(1.0);
    definition.setEvaluator(new ToDoubleFunction<ProcessModel>() {
      /** {@inheritDoc} */
      @Override
      public double applyAsDouble(ProcessModel ignoredModel) {
        return capturedCapacityConstraint.getUtilization();
      }
    });
    boolean hardConstraint =
        capacityConstraint.getSeverity() == CapacityConstraint.ConstraintSeverity.CRITICAL
            || capacityConstraint.getSeverity() == CapacityConstraint.ConstraintSeverity.HARD;
    definition.setHard(hardConstraint);
    definition.setCapacityMetadata(areaName, equipmentName, equipmentConstraintName,
        capacityConstraint);
    constraints.add(definition);
  }

  /**
   * Checks whether a constraint with the specified name already exists.
   *
   * @param constraintName constraint name
   * @return true when an existing constraint has the same name
   */
  private boolean hasConstraint(String constraintName) {
    for (ConstraintDefinition constraint : constraints) {
      if (constraintName.equals(constraint.getName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets optimization bounds as a matrix.
   *
   * @return matrix with lower and upper bound for each parameter
   */
  public double[][] getBounds() {
    double[][] bounds = new double[parameters.size()][2];
    for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
      bounds[parameterIndex][0] = parameters.get(parameterIndex).getLowerBound();
      bounds[parameterIndex][1] = parameters.get(parameterIndex).getUpperBound();
    }
    return bounds;
  }

  /**
   * Gets optimization bounds as a list for Python callers.
   *
   * @return list of two-element arrays with lower and upper bounds
   */
  public List<double[]> getBoundsAsList() {
    List<double[]> bounds = new ArrayList<double[]>();
    for (ParameterDefinition parameter : parameters) {
      bounds.add(new double[] {parameter.getLowerBound(), parameter.getUpperBound()});
    }
    return bounds;
  }

  /**
   * Gets lower bounds.
   *
   * @return lower bound vector
   */
  public double[] getLowerBounds() {
    double[] lowerBounds = new double[parameters.size()];
    for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
      lowerBounds[parameterIndex] = parameters.get(parameterIndex).getLowerBound();
    }
    return lowerBounds;
  }

  /**
   * Gets upper bounds.
   *
   * @return upper bound vector
   */
  public double[] getUpperBounds() {
    double[] upperBounds = new double[parameters.size()];
    for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
      upperBounds[parameterIndex] = parameters.get(parameterIndex).getUpperBound();
    }
    return upperBounds;
  }

  /**
   * Gets initial parameter values.
   *
   * @return initial parameter vector
   */
  public double[] getInitialValues() {
    double[] initialValues = new double[parameters.size()];
    for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
      initialValues[parameterIndex] = parameters.get(parameterIndex).getInitialValue();
    }
    return initialValues;
  }

  /**
   * Evaluates the process model at the supplied parameter values.
   *
   * @param parameterValues parameter vector with length equal to {@link #getParameterCount()}
   * @return complete evaluation result
   */
  public EvaluationResult evaluate(double[] parameterValues) {
    ensureProcessModel();
    if (parameterValues == null || parameterValues.length != parameters.size()) {
      throw new IllegalArgumentException("Parameter array length ("
          + (parameterValues == null ? "null" : Integer.toString(parameterValues.length))
          + ") must match parameter count (" + parameters.size() + ")");
    }

    long startTime = System.currentTimeMillis();
    evaluationCount++;

    EvaluationResult result = new EvaluationResult();
    result.setParameters(Arrays.copyOf(parameterValues, parameterValues.length));
    result.setEvaluationNumber(evaluationCount);

    try {
      setParameterValues(processModel, parameterValues);
      processModel.run();
      result.setSimulationConverged(processModel.isModelConverged());

      double[] objectiveValues = new double[objectives.size()];
      double[] rawObjectiveValues = new double[objectives.size()];
      for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
        rawObjectiveValues[objectiveIndex] =
            objectives.get(objectiveIndex).evaluateRaw(processModel);
        objectiveValues[objectiveIndex] = objectives.get(objectiveIndex).evaluate(processModel);
      }
      result.setObjectives(objectiveValues);
      result.setObjectivesRaw(rawObjectiveValues);

      double[] constraintValues = new double[constraints.size()];
      double[] margins = new double[constraints.size()];
      double penaltySum = 0.0;
      boolean feasible = processModel.isModelConverged();
      for (int constraintIndex = 0; constraintIndex < constraints.size(); constraintIndex++) {
        ConstraintDefinition constraint = constraints.get(constraintIndex);
        constraintValues[constraintIndex] = constraint.evaluate(processModel);
        margins[constraintIndex] = constraint.margin(processModel);
        if (margins[constraintIndex] < 0.0) {
          penaltySum += constraint.penalty(processModel);
          if (constraint.isHard()) {
            feasible = false;
          }
        }
      }
      result.setConstraintValues(constraintValues);
      result.setConstraintMargins(margins);
      result.setPenaltySum(penaltySum);
      result.setActiveBottleneck(findActiveBottleneck(processModel));
      result.setFeasible(feasible);
    } catch (Exception exception) {
      logger.warn("ProcessModel evaluation failed: " + exception.getMessage());
      result.setSimulationConverged(false);
      result.setFeasible(false);
      result.setErrorMessage(exception.getMessage());
      result.setPenaltySum(Double.MAX_VALUE / 2.0);
      double[] objectiveValues = new double[objectives.size()];
      Arrays.fill(objectiveValues, Double.NaN);
      result.setObjectives(objectiveValues);
      result.setObjectivesRaw(objectiveValues);
      double[] constraintValues = new double[constraints.size()];
      Arrays.fill(constraintValues, Double.NaN);
      result.setConstraintValues(constraintValues);
      double[] margins = new double[constraints.size()];
      Arrays.fill(margins, Double.NEGATIVE_INFINITY);
      result.setConstraintMargins(margins);
    }

    result.setEvaluationTimeMs(System.currentTimeMillis() - startTime);
    lastResult = result;
    lastParameters = Arrays.copyOf(parameterValues, parameterValues.length);
    return result;
  }

  /**
   * Sets bounded parameter values on the model.
   *
   * @param model process model
   * @param parameterValues parameter values
   */
  private void setParameterValues(ProcessModel model, double[] parameterValues) {
    for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
      ParameterDefinition parameter = parameters.get(parameterIndex);
      double value = parameter.clamp(parameterValues[parameterIndex]);
      if (parameter.getSetter() != null) {
        parameter.getSetter().accept(model, value);
      } else {
        model.setVariableValue(parameter.getAddress(), value, parameter.getUnit());
      }
    }
  }

  /**
   * Finds the active bottleneck across all process areas.
   *
   * @param model process model in its current state
   * @return active bottleneck status, or {@link BottleneckStatus#none()} when no constraint exists
   */
  public BottleneckStatus findActiveBottleneck(ProcessModel model) {
    if (model == null) {
      return BottleneckStatus.none();
    }
    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    BottleneckStatus active = BottleneckStatus.none();
    double highestUtilization = -1.0;

    for (String areaName : model.getProcessSystemNames()) {
      ProcessSystem area = model.get(areaName);
      if (area == null) {
        continue;
      }
      for (ProcessEquipmentInterface equipment : area.getUnitOperations()) {
        Map<String, CapacityConstraint> equipmentConstraints =
            getAllCapacityConstraints(registry, equipment);
        if (equipmentConstraints.isEmpty()) {
          continue;
        }
        for (Map.Entry<String, CapacityConstraint> entry : equipmentConstraints.entrySet()) {
          CapacityConstraint capacityConstraint = entry.getValue();
          if (capacityConstraint == null || !capacityConstraint.isEnabled()) {
            continue;
          }
          double utilization = capacityConstraint.getUtilization();
          if (!Double.isNaN(utilization) && utilization > highestUtilization) {
            highestUtilization = utilization;
            active =
                new BottleneckStatus(areaName, equipment.getName(), entry.getKey(), utilization,
                    capacityConstraint.getCurrentValue(), capacityConstraint.getDesignValue(),
                    capacityConstraint.getUnit(), utilization <= 1.0);
          }
        }
      }
    }
    return active;
  }

  /**
   * Evaluates all constraints and returns the margin vector for external solvers.
   *
   * @param model process model in its current state
   * @return constraint margins in registration order
   */
  public double[] getConstraintMarginVector(ProcessModel model) {
    double[] margins = new double[constraints.size()];
    for (int constraintIndex = 0; constraintIndex < constraints.size(); constraintIndex++) {
      margins[constraintIndex] = constraints.get(constraintIndex).margin(model);
    }
    return margins;
  }

  /**
   * Evaluates only the primary objective.
   *
   * @param parameterValues parameter vector
   * @return primary objective value using minimizer sign convention
   */
  public double evaluateObjective(double[] parameterValues) {
    return evaluate(parameterValues).getObjective();
  }

  /**
   * Evaluates the primary objective plus constraint penalties.
   *
   * @param parameterValues parameter vector
   * @return penalized objective value
   */
  public double evaluatePenalizedObjective(double[] parameterValues) {
    return evaluate(parameterValues).getPenalizedObjective();
  }

  /**
   * Checks feasibility at a parameter point.
   *
   * @param parameterValues parameter vector
   * @return true when the model converges and hard constraints are satisfied
   */
  public boolean isFeasible(double[] parameterValues) {
    return evaluate(parameterValues).isFeasible();
  }

  /**
   * Estimates the primary objective gradient by finite differences.
   *
   * @param parameterValues parameter vector
   * @return gradient vector
   */
  public double[] estimateGradient(double[] parameterValues) {
    return estimateGradient(parameterValues, 0);
  }

  /**
   * Estimates an objective gradient by finite differences.
   *
   * @param parameterValues parameter vector
   * @param objectiveIndex objective index
   * @return gradient vector
   */
  public double[] estimateGradient(double[] parameterValues, int objectiveIndex) {
    double[] gradient = new double[parameterValues.length];
    double baseValue = evaluate(parameterValues).getObjectives()[objectiveIndex];
    for (int parameterIndex = 0; parameterIndex < parameterValues.length; parameterIndex++) {
      double step = useRelativeStep
          ? finiteDifferenceStep * Math.max(Math.abs(parameterValues[parameterIndex]), 1.0)
          : finiteDifferenceStep;
      double[] shiftedValues = Arrays.copyOf(parameterValues, parameterValues.length);
      shiftedValues[parameterIndex] += step;
      if (shiftedValues[parameterIndex] > parameters.get(parameterIndex).getUpperBound()) {
        shiftedValues[parameterIndex] = parameterValues[parameterIndex] - step;
        step = -step;
      }
      double shiftedValue = evaluate(shiftedValues).getObjectives()[objectiveIndex];
      gradient[parameterIndex] = (shiftedValue - baseValue) / step;
    }
    return gradient;
  }

  /**
   * Estimates the constraint Jacobian by finite differences.
   *
   * @param parameterValues parameter vector
   * @return matrix with constraints as rows and parameters as columns
   */
  public double[][] estimateConstraintJacobian(double[] parameterValues) {
    double[][] jacobian = new double[constraints.size()][parameterValues.length];
    double[] baseMargins = evaluate(parameterValues).getConstraintMargins();
    for (int parameterIndex = 0; parameterIndex < parameterValues.length; parameterIndex++) {
      double step = useRelativeStep
          ? finiteDifferenceStep * Math.max(Math.abs(parameterValues[parameterIndex]), 1.0)
          : finiteDifferenceStep;
      double[] shiftedValues = Arrays.copyOf(parameterValues, parameterValues.length);
      shiftedValues[parameterIndex] += step;
      if (shiftedValues[parameterIndex] > parameters.get(parameterIndex).getUpperBound()) {
        shiftedValues[parameterIndex] = parameterValues[parameterIndex] - step;
        step = -step;
      }
      double[] shiftedMargins = evaluate(shiftedValues).getConstraintMargins();
      for (int constraintIndex = 0; constraintIndex < constraints.size(); constraintIndex++) {
        jacobian[constraintIndex][parameterIndex] =
            (shiftedMargins[constraintIndex] - baseMargins[constraintIndex]) / step;
      }
    }
    return jacobian;
  }

  /**
   * Gets the finite-difference step.
   *
   * @return finite-difference step
   */
  public double getFiniteDifferenceStep() {
    return finiteDifferenceStep;
  }

  /**
   * Sets the finite-difference step.
   *
   * @param finiteDifferenceStep finite-difference step
   */
  public void setFiniteDifferenceStep(double finiteDifferenceStep) {
    this.finiteDifferenceStep = finiteDifferenceStep;
  }

  /**
   * Checks whether relative finite-difference steps are used.
   *
   * @return true when finite-difference steps are relative
   */
  public boolean isUseRelativeStep() {
    return useRelativeStep;
  }

  /**
   * Sets whether finite-difference steps are relative.
   *
   * @param useRelativeStep true to scale finite-difference steps by parameter magnitude
   */
  public void setUseRelativeStep(boolean useRelativeStep) {
    this.useRelativeStep = useRelativeStep;
  }

  /**
   * Checks whether strategy-generated equipment capacity constraints are included.
   *
   * @return true when strategy-generated constraints are included with direct equipment constraints
   */
  public boolean isIncludeStrategyCapacityConstraints() {
    return includeStrategyCapacityConstraints;
  }

  /**
   * Sets whether strategy-generated equipment capacity constraints are included.
   *
   * <p>
   * Direct constraints attached to equipment are always included. Disable this option for installed
   * capacity studies where only explicit fixed-equipment limits from design data should determine
   * feasibility and active bottleneck reporting.
   * </p>
   *
   * @param includeStrategyCapacityConstraints true to include strategy-generated constraints
   */
  public void setIncludeStrategyCapacityConstraints(boolean includeStrategyCapacityConstraints) {
    this.includeStrategyCapacityConstraints = includeStrategyCapacityConstraints;
  }

  /**
   * Gets the evaluation count.
   *
   * @return number of evaluation attempts
   */
  public int getEvaluationCount() {
    return evaluationCount;
  }

  /**
   * Gets the last evaluation result.
   *
   * @return last evaluation result, or null before the first evaluation
   */
  public EvaluationResult getLastResult() {
    return lastResult;
  }

  /**
   * Gets the last evaluated parameter vector.
   *
   * @return last parameter vector, or null before the first evaluation
   */
  public double[] getLastParameters() {
    return lastParameters == null ? null : Arrays.copyOf(lastParameters, lastParameters.length);
  }

  /**
   * Gets a JSON-friendly problem definition.
   *
   * @return map containing parameters, objectives, constraints, and model areas
   */
  public Map<String, Object> getProblemDefinition() {
    Map<String, Object> definition = new LinkedHashMap<String, Object>();
    definition.put("type", "ProcessModelSimulationEvaluator");
    definition.put("areaCount", processModel == null ? 0 : processModel.size());
    definition.put("areas",
        processModel == null ? new ArrayList<String>() : processModel.getProcessSystemNames());

    List<Map<String, Object>> parameterDefinitions = new ArrayList<Map<String, Object>>();
    for (ParameterDefinition parameter : parameters) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("name", parameter.getName());
      item.put("address", parameter.getAddress());
      item.put("lowerBound", parameter.getLowerBound());
      item.put("upperBound", parameter.getUpperBound());
      item.put("initialValue", parameter.getInitialValue());
      item.put("unit", parameter.getUnit());
      parameterDefinitions.add(item);
    }
    definition.put("parameters", parameterDefinitions);

    List<Map<String, Object>> objectiveDefinitions = new ArrayList<Map<String, Object>>();
    for (ObjectiveDefinition objective : objectives) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("name", objective.getName());
      item.put("direction", objective.getDirection().name());
      item.put("unit", objective.getUnit());
      item.put("weight", objective.getWeight());
      objectiveDefinitions.add(item);
    }
    definition.put("objectives", objectiveDefinitions);

    List<Map<String, Object>> constraintDefinitions = new ArrayList<Map<String, Object>>();
    for (ConstraintDefinition constraint : constraints) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("name", constraint.getName());
      item.put("type", constraint.getType().name());
      item.put("lowerBound", constraint.getLowerBound());
      item.put("upperBound", constraint.getUpperBound());
      item.put("unit", constraint.getUnit());
      item.put("hard", constraint.isHard());
      item.put("capacityConstraint", constraint.isCapacityConstraint());
      item.put("area", constraint.getAreaName());
      item.put("equipment", constraint.getEquipmentName());
      item.put("equipmentConstraint", constraint.getEquipmentConstraintName());
      constraintDefinitions.add(item);
    }
    definition.put("constraints", constraintDefinitions);
    return definition;
  }

  /**
   * Serializes the problem definition as JSON.
   *
   * @return JSON problem definition
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getProblemDefinition());
  }

  /**
   * Ensures a process model has been configured.
   *
   * @throws IllegalStateException when no process model has been set
   */
  private void ensureProcessModel() {
    if (processModel == null) {
      throw new IllegalStateException("ProcessModel must be set before evaluation");
    }
  }
}
