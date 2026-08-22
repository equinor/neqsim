package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
import neqsim.process.equipment.network.NetworkDecisionVariable;
import neqsim.process.equipment.network.NetworkNomination;
import neqsim.process.equipment.network.NetworkQualityResult;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Black-box evaluator for large multi-area {@link ProcessModel} optimization problems.
 *
 * <p>
 * This evaluator fills the gap between the single-flowsheet {@link ProcessSimulationEvaluator} and integrated models
 * composed of several named {@link ProcessSystem} areas. Decision variables are addressed with the existing
 * {@link ProcessAutomation} syntax, for example {@code "Wells::Producer A.flowRate"}. This keeps external optimizers
 * independent of the internal Java object graph while still allowing objective and constraint functions to inspect the
 * full model.
 * </p>
 *
 * <p>
 * The intended use case is fixed-equipment throughput optimization: vary producer/feed rates, run the full
 * {@link ProcessModel}, and stop when installed capacity constraints reveal the active bottleneck. Capacity constraints
 * can be added explicitly to equipment or discovered through the {@link EquipmentCapacityStrategyRegistry}.
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

  /** Deterministic terminal penalty for an invalid external-optimizer candidate. */
  private static final double INVALID_CANDIDATE_PENALTY = Double.MAX_VALUE / 2.0;

  /** Serializable callback that returns one structured boundary sample after a model run. */
  public interface BoundarySampleEvaluator extends Serializable {
    /**
     * Samples one boundary observable.
     *
     * @param model completed process-model operating point
     * @return structured sample, or null to report missing evidence
     */
    ProcessBoundaryConstraintEvidence.Sample evaluate(ProcessModel model);
  }

  /** Finite-difference stencil used for objective gradients and constraint Jacobians. */
  public enum FiniteDifferenceMethod {
    /** One forward evaluation, or a backward evaluation when the upper bound is active. */
    FORWARD,
    /** Two-sided central difference where bounds allow, with a one-sided boundary fallback. */
    CENTRAL
  }

  /** Actual bounded stencil used for one decision variable. */
  public enum AppliedFiniteDifferenceStencil {
    /** Positive one-sided perturbations. */
    FORWARD,
    /** Negative one-sided perturbations. */
    BACKWARD,
    /** Symmetric positive and negative perturbations. */
    CENTRAL,
    /** No feasible perturbation because the parameter is fixed. */
    FIXED
  }

  /** Immutable evidence from one finite-difference perturbation. */
  public static final class SensitivityPerturbation implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Signed parameter displacement from the bounded base point. */
    private final double signedStep;

    /** Actual perturbed parameter value. */
    private final double parameterValue;

    /** Whether the perturbed process simulation converged. */
    private final boolean simulationConverged;

    /** Whether the perturbed point satisfied the registered hard constraints. */
    private final boolean feasible;

    /** Evaluation error message, or null when none was reported. */
    private final String errorMessage;

    /**
     * Creates immutable perturbation evidence.
     *
     * @param signedStep signed displacement from the base point
     * @param parameterValue actual perturbed parameter value
     * @param result process evaluation result
     */
    private SensitivityPerturbation(double signedStep, double parameterValue, EvaluationResult result) {
      this.signedStep = signedStep;
      this.parameterValue = parameterValue;
      this.simulationConverged = result.isSimulationConverged();
      this.feasible = result.isFeasible();
      this.errorMessage = result.getErrorMessage();
    }

    /**
     * Gets the signed parameter displacement.
     *
     * @return signed step in the parameter unit
     */
    public double getSignedStep() {
      return signedStep;
    }

    /**
     * Gets the actual perturbed parameter value.
     *
     * @return parameter value in the parameter unit
     */
    public double getParameterValue() {
      return parameterValue;
    }

    /**
     * Checks convergence of the perturbed process simulation.
     *
     * @return true when the process model converged
     */
    public boolean isSimulationConverged() {
      return simulationConverged;
    }

    /**
     * Checks registered hard-constraint feasibility at the perturbation.
     *
     * @return true when the perturbed point was feasible
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Gets the evaluation error message.
     *
     * @return error message, or null when none was reported
     */
    public String getErrorMessage() {
      return errorMessage;
    }
  }

  /** Immutable step-halving quality evidence for one decision variable. */
  public static final class ParameterSensitivityQuality implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Decision-variable name. */
    private final String parameterName;

    /** Decision-variable unit. */
    private final String parameterUnit;

    /** Actual bounded stencil. */
    private final AppliedFiniteDifferenceStencil stencil;

    /** Requested finite-difference step before applying bounds. */
    private final double requestedStep;

    /** Actual coarse step magnitude. */
    private final double coarseStep;

    /** Actual fine step magnitude after halving. */
    private final double fineStep;

    /** Relative disagreement between coarse and fine objective derivatives. */
    private final double objectiveRelativeDisagreement;

    /** Relative disagreement for every constraint-margin derivative. */
    private final double[] constraintRelativeDisagreement;

    /** Largest finite relative disagreement across objective and constraint derivatives. */
    private final double maximumRelativeDisagreement;

    /** Whether the base and every perturbation converged. */
    private final boolean allEvaluationsConverged;

    /** Whether the base and every perturbation satisfied registered hard constraints. */
    private final boolean allEvaluationsFeasible;

    /** Immutable perturbation evidence in evaluation order. */
    private final List<SensitivityPerturbation> perturbations;

    /**
     * Creates immutable parameter-level sensitivity evidence.
     *
     * @param parameter parameter definition
     * @param stencil actual bounded stencil
     * @param requestedStep requested step
     * @param coarseStep actual coarse step
     * @param fineStep actual fine step
     * @param objectiveRelativeDisagreement objective derivative disagreement
     * @param constraintRelativeDisagreement constraint derivative disagreements
     * @param baseResult base process evaluation
     * @param perturbations perturbation evidence
     */
    private ParameterSensitivityQuality(ParameterDefinition parameter, AppliedFiniteDifferenceStencil stencil,
        double requestedStep, double coarseStep, double fineStep, double objectiveRelativeDisagreement,
        double[] constraintRelativeDisagreement, EvaluationResult baseResult,
        List<SensitivityPerturbation> perturbations) {
      this.parameterName = parameter.getName();
      this.parameterUnit = parameter.getUnit();
      this.stencil = stencil;
      this.requestedStep = requestedStep;
      this.coarseStep = coarseStep;
      this.fineStep = fineStep;
      this.objectiveRelativeDisagreement = objectiveRelativeDisagreement;
      this.constraintRelativeDisagreement = Arrays.copyOf(constraintRelativeDisagreement,
          constraintRelativeDisagreement.length);
      this.maximumRelativeDisagreement = maximumFiniteDisagreement(objectiveRelativeDisagreement,
          constraintRelativeDisagreement);
      boolean converged = baseResult.isSimulationConverged();
      boolean feasible = baseResult.isFeasible();
      for (SensitivityPerturbation perturbation : perturbations) {
        converged = converged && perturbation.isSimulationConverged();
        feasible = feasible && perturbation.isFeasible();
      }
      this.allEvaluationsConverged = converged;
      this.allEvaluationsFeasible = feasible;
      this.perturbations = Collections.unmodifiableList(new ArrayList<SensitivityPerturbation>(perturbations));
    }

    /**
     * Gets the decision-variable name.
     *
     * @return parameter name
     */
    public String getParameterName() {
      return parameterName;
    }

    /**
     * Gets the decision-variable unit.
     *
     * @return parameter unit, or null when unspecified
     */
    public String getParameterUnit() {
      return parameterUnit;
    }

    /**
     * Gets the actual bounded stencil.
     *
     * @return applied stencil
     */
    public AppliedFiniteDifferenceStencil getStencil() {
      return stencil;
    }

    /**
     * Gets the requested step before applying decision-variable bounds.
     *
     * @return requested step magnitude in the parameter unit
     */
    public double getRequestedStep() {
      return requestedStep;
    }

    /**
     * Gets the actual coarse step.
     *
     * @return coarse step magnitude in the parameter unit
     */
    public double getCoarseStep() {
      return coarseStep;
    }

    /**
     * Gets the actual fine step.
     *
     * @return fine step magnitude in the parameter unit
     */
    public double getFineStep() {
      return fineStep;
    }

    /**
     * Gets the objective derivative's scale-independent coarse/fine disagreement.
     *
     * @return relative disagreement, or NaN when either derivative is non-finite
     */
    public double getObjectiveRelativeDisagreement() {
      return objectiveRelativeDisagreement;
    }

    /**
     * Gets coarse/fine disagreements for the constraint-margin derivatives.
     *
     * @return defensive array ordered like the registered constraints
     */
    public double[] getConstraintRelativeDisagreement() {
      return Arrays.copyOf(constraintRelativeDisagreement, constraintRelativeDisagreement.length);
    }

    /**
     * Gets the largest relative disagreement.
     *
     * @return maximum disagreement, or NaN when any derivative comparison is non-finite
     */
    public double getMaximumRelativeDisagreement() {
      return maximumRelativeDisagreement;
    }

    /**
     * Checks whether the base and every perturbation converged.
     *
     * @return true when all process evaluations converged
     */
    public boolean isAllEvaluationsConverged() {
      return allEvaluationsConverged;
    }

    /**
     * Checks whether the base and every perturbation satisfied hard constraints.
     *
     * <p>
     * An infeasible perturbation does not by itself make a finite-difference derivative numerically invalid. It is
     * retained as explicit engineering evidence for callers that require sensitivities wholly inside the feasible
     * process region.
     * </p>
     *
     * @return true when all evaluations were feasible
     */
    public boolean isAllEvaluationsFeasible() {
      return allEvaluationsFeasible;
    }

    /**
     * Gets immutable perturbation evidence.
     *
     * @return perturbations in evaluation order
     */
    public List<SensitivityPerturbation> getPerturbations() {
      return perturbations;
    }

    /**
     * Checks step-halving consistency against a caller-selected tolerance.
     *
     * <p>
     * This is a numerical consistency check, not a physical-validity or shadow-price certificate. Feasibility is
     * reported separately by {@link #isAllEvaluationsFeasible()}.
     * </p>
     *
     * @param relativeTolerance finite non-negative maximum relative disagreement
     * @return true when all evaluations converged and every derivative comparison is within tolerance
     */
    public boolean isNumericallyStable(double relativeTolerance) {
      if (!Double.isFinite(relativeTolerance) || relativeTolerance < 0.0) {
        throw new IllegalArgumentException("Relative tolerance must be finite and non-negative");
      }
      return allEvaluationsConverged && Double.isFinite(maximumRelativeDisagreement)
          && maximumRelativeDisagreement <= relativeTolerance;
    }

    /** Returns the largest disagreement, preserving non-finite comparisons as incomplete evidence. */
    private static double maximumFiniteDisagreement(double objectiveDisagreement, double[] constraintDisagreements) {
      if (!Double.isFinite(objectiveDisagreement)) {
        return Double.NaN;
      }
      double maximum = objectiveDisagreement;
      for (double disagreement : constraintDisagreements) {
        if (!Double.isFinite(disagreement)) {
          return Double.NaN;
        }
        maximum = Math.max(maximum, disagreement);
      }
      return maximum;
    }
  }

  /** Immutable identity and base-point snapshot for one decision variable. */
  public static final class SensitivityParameterSnapshot implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Registration index and derivative-matrix column. */
    private final int index;

    /** Human-readable parameter name. */
    private final String name;

    /** Area-qualified automation address. */
    private final String address;

    /** Parameter unit. */
    private final String unit;

    /** Lower optimization bound. */
    private final double lowerBound;

    /** Upper optimization bound. */
    private final double upperBound;

    /** Whether direct evaluator calls clamp this parameter to its declared bounds. */
    private final boolean clampToBounds;

    /** Bounded or strict parameter value used for the base evaluation. */
    private final double baseValue;

    /** Creates an immutable parameter snapshot. */
    private SensitivityParameterSnapshot(int index, ParameterDefinition parameter, double baseValue) {
      this.index = index;
      this.name = parameter.getName();
      this.address = parameter.getAddress();
      this.unit = parameter.getUnit();
      this.lowerBound = parameter.getLowerBound();
      this.upperBound = parameter.getUpperBound();
      this.clampToBounds = parameter.isClampToBounds();
      this.baseValue = baseValue;
    }

    /**
     * Gets the registration index and derivative-matrix column.
     *
     * @return zero-based parameter index
     */
    public int getIndex() {
      return index;
    }

    /**
     * Gets the parameter name.
     *
     * @return human-readable parameter name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the automation address.
     *
     * @return area-qualified address, or the custom-setter name
     */
    public String getAddress() {
      return address;
    }

    /**
     * Gets the parameter unit.
     *
     * @return unit, or null when unspecified
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the lower optimization bound.
     *
     * @return lower bound in the parameter unit
     */
    public double getLowerBound() {
      return lowerBound;
    }

    /**
     * Gets the upper optimization bound.
     *
     * @return upper bound in the parameter unit
     */
    public double getUpperBound() {
      return upperBound;
    }

    /**
     * Checks whether direct evaluator calls clamp this parameter to its bounds.
     *
     * @return true for legacy clamping, false for strict candidate rejection
     */
    public boolean isClampToBounds() {
      return clampToBounds;
    }

    /**
     * Gets the bounded or strict base-point value.
     *
     * @return base value in the parameter unit
     */
    public double getBaseValue() {
      return baseValue;
    }
  }

  /** Immutable identity, base value, and derivative snapshot for the selected objective. */
  public static final class SensitivityObjectiveSnapshot implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Registration index represented by the gradient. */
    private final int index;

    /** Objective name. */
    private final String name;

    /** Optimization direction. */
    private final ObjectiveDefinition.Direction direction;

    /** Objective unit. */
    private final String unit;

    /** Objective weight retained for external scalarization. */
    private final double weight;

    /** Raw base-point objective value. */
    private final double baseRawValue;

    /** Sign-adjusted base-point objective value used by minimizers. */
    private final double baseMinimizerValue;

    /** Fine-step gradient in minimizer sign convention. */
    private final double[] gradient;

    /** Creates an immutable objective snapshot. */
    private SensitivityObjectiveSnapshot(int index, ObjectiveDefinition objective, double baseRawValue,
        double baseMinimizerValue, double[] gradient) {
      this.index = index;
      this.name = objective.getName();
      this.direction = objective.getDirection();
      this.unit = objective.getUnit();
      this.weight = objective.getWeight();
      this.baseRawValue = baseRawValue;
      this.baseMinimizerValue = baseMinimizerValue;
      this.gradient = Arrays.copyOf(gradient, gradient.length);
    }

    /**
     * Gets the registered objective index.
     *
     * @return zero-based objective index
     */
    public int getIndex() {
      return index;
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
     * Gets the optimization direction.
     *
     * @return minimize or maximize direction
     */
    public ObjectiveDefinition.Direction getDirection() {
      return direction;
    }

    /**
     * Gets the objective unit.
     *
     * @return unit, or null when unspecified
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the external scalarization weight.
     *
     * @return objective weight
     */
    public double getWeight() {
      return weight;
    }

    /**
     * Gets the raw objective at the base point.
     *
     * @return raw objective value in the objective unit
     */
    public double getBaseRawValue() {
      return baseRawValue;
    }

    /**
     * Gets the sign-adjusted objective at the base point.
     *
     * @return minimizer-convention objective value
     */
    public double getBaseMinimizerValue() {
      return baseMinimizerValue;
    }

    /**
     * Gets the fine-step gradient in minimizer sign convention.
     *
     * @return defensive gradient ordered like the parameter snapshots
     */
    public double[] getGradient() {
      return Arrays.copyOf(gradient, gradient.length);
    }
  }

  /** Immutable identity, base margin, and derivative snapshot for one constraint. */
  public static final class SensitivityConstraintSnapshot implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Registration index and constraint-Jacobian row. */
    private final int index;

    /** Constraint name. */
    private final String name;

    /** Constraint type. */
    private final ConstraintDefinition.Type type;

    /** Constraint unit. */
    private final String unit;

    /** Whether violation makes the result infeasible. */
    private final boolean hard;

    /** Penalty weight for a violated constraint. */
    private final double penaltyWeight;

    /** Lower bound or equality target. */
    private final double lowerBound;

    /** Upper bound. */
    private final double upperBound;

    /** Equality tolerance. */
    private final double equalityTolerance;

    /** Whether this constraint originates from installed equipment capacity. */
    private final boolean capacityConstraint;

    /** Capacity-origin process area. */
    private final String areaName;

    /** Capacity-origin equipment. */
    private final String equipmentName;

    /** Original equipment capacity-constraint name. */
    private final String equipmentConstraintName;

    /** Sampled constraint value at the base point. */
    private final double baseValue;

    /** Constraint margin at the base point. */
    private final double baseMargin;

    /** Fine-step constraint-margin gradient. */
    private final double[] marginGradient;

    /** Creates an immutable constraint snapshot. */
    private SensitivityConstraintSnapshot(int index, ConstraintDefinition constraint, double baseValue,
        double baseMargin, double[] marginGradient) {
      this.index = index;
      this.name = constraint.getName();
      this.type = constraint.getType();
      this.unit = constraint.getUnit();
      this.hard = constraint.isHard();
      this.penaltyWeight = constraint.getPenaltyWeight();
      this.lowerBound = constraint.getLowerBound();
      this.upperBound = constraint.getUpperBound();
      this.equalityTolerance = constraint.getEqualityTolerance();
      this.capacityConstraint = constraint.isCapacityConstraint();
      this.areaName = constraint.getAreaName();
      this.equipmentName = constraint.getEquipmentName();
      this.equipmentConstraintName = constraint.getEquipmentConstraintName();
      this.baseValue = baseValue;
      this.baseMargin = baseMargin;
      this.marginGradient = Arrays.copyOf(marginGradient, marginGradient.length);
    }

    /**
     * Gets the registration index and constraint-Jacobian row.
     *
     * @return zero-based constraint index
     */
    public int getIndex() {
      return index;
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
     * Gets the constraint type.
     *
     * @return lower, upper, range, or equality type
     */
    public ConstraintDefinition.Type getType() {
      return type;
    }

    /**
     * Gets the constraint unit.
     *
     * @return unit, or null when unspecified
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Checks whether a violation makes the evaluated point infeasible.
     *
     * @return true for a hard constraint
     */
    public boolean isHard() {
      return hard;
    }

    /**
     * Gets the penalty weight.
     *
     * @return constraint penalty weight
     */
    public double getPenaltyWeight() {
      return penaltyWeight;
    }

    /**
     * Gets the lower bound or equality target.
     *
     * @return lower bound in the constraint unit
     */
    public double getLowerBound() {
      return lowerBound;
    }

    /**
     * Gets the upper bound.
     *
     * @return upper bound in the constraint unit
     */
    public double getUpperBound() {
      return upperBound;
    }

    /**
     * Gets the equality tolerance.
     *
     * @return absolute tolerance in the constraint unit
     */
    public double getEqualityTolerance() {
      return equalityTolerance;
    }

    /**
     * Checks whether this constraint came from installed equipment capacity.
     *
     * @return true for an equipment capacity constraint
     */
    public boolean isCapacityConstraint() {
      return capacityConstraint;
    }

    /**
     * Gets the capacity-origin process area.
     *
     * @return area name, or null for a non-capacity constraint
     */
    public String getAreaName() {
      return areaName;
    }

    /**
     * Gets the capacity-origin equipment.
     *
     * @return equipment name, or null for a non-capacity constraint
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the original equipment capacity-constraint name.
     *
     * @return equipment constraint name, or null for a non-capacity constraint
     */
    public String getEquipmentConstraintName() {
      return equipmentConstraintName;
    }

    /**
     * Gets the sampled base-point constraint value.
     *
     * @return constraint value in the constraint unit
     */
    public double getBaseValue() {
      return baseValue;
    }

    /**
     * Gets the base-point constraint margin.
     *
     * @return non-negative for satisfaction and negative for violation
     */
    public double getBaseMargin() {
      return baseMargin;
    }

    /**
     * Gets the fine-step gradient of the constraint margin.
     *
     * @return defensive gradient ordered like the parameter snapshots
     */
    public double[] getMarginGradient() {
      return Arrays.copyOf(marginGradient, marginGradient.length);
    }
  }

  /** Evidence flags retained for one local objective/constraint sensitivity pair. */
  public enum SensitivityEvidenceFlag {
    /** The base process simulation did not converge. */
    BASE_NOT_CONVERGED,
    /** The base point violated at least one registered hard constraint. */
    BASE_INFEASIBLE,
    /** The base evaluation reported an exception or other error. */
    BASE_EVALUATION_ERROR,
    /** At least one finite-difference perturbation did not converge. */
    PERTURBATION_NOT_CONVERGED,
    /** At least one finite-difference perturbation violated a registered hard constraint. */
    PERTURBATION_INFEASIBLE,
    /** At least one finite-difference perturbation reported an evaluation error. */
    PERTURBATION_EVALUATION_ERROR,
    /** The returned objective or constraint-margin derivative is not finite. */
    NON_FINITE_DERIVATIVE,
    /** Coarse and fine derivatives do not meet the declared relative tolerance. */
    NUMERICALLY_UNSTABLE,
    /** A forward or backward stencil was used instead of a two-sided central stencil. */
    ONE_SIDED_STENCIL,
    /** The parameter has equal lower and upper bounds and cannot be perturbed. */
    FIXED_PARAMETER
  }

  /**
   * Immutable policy for qualifying local objective/constraint sensitivity evidence.
   *
   * <p>
   * Convergence, finite derivatives, finite coarse/fine comparisons, numerical stability, and a perturbable decision
   * variable are always required. Callers explicitly decide whether an infeasible base point, infeasible perturbations,
   * or a one-sided stencil is acceptable for their engineering use case.
   * </p>
   */
  public static final class SensitivityQualificationPolicy implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Maximum accepted relative disagreement between coarse and fine derivatives. */
    private final double relativeTolerance;

    /** Whether the sampled base point must satisfy every registered hard constraint. */
    private final boolean requireBaseFeasible;

    /** Whether every perturbation must satisfy every registered hard constraint. */
    private final boolean requirePerturbationsFeasible;

    /** Whether forward and backward stencils are accepted. */
    private final boolean allowOneSidedStencil;

    /**
     * Creates an explicit qualification policy.
     *
     * @param relativeTolerance finite non-negative coarse/fine disagreement limit
     * @param requireBaseFeasible whether the base point must satisfy hard constraints
     * @param requirePerturbationsFeasible whether every perturbation must satisfy hard constraints
     * @param allowOneSidedStencil whether forward/backward stencils are acceptable
     */
    public SensitivityQualificationPolicy(double relativeTolerance, boolean requireBaseFeasible,
        boolean requirePerturbationsFeasible, boolean allowOneSidedStencil) {
      if (!Double.isFinite(relativeTolerance) || relativeTolerance < 0.0) {
        throw new IllegalArgumentException("Relative tolerance must be finite and non-negative");
      }
      this.relativeTolerance = relativeTolerance;
      this.requireBaseFeasible = requireBaseFeasible;
      this.requirePerturbationsFeasible = requirePerturbationsFeasible;
      this.allowOneSidedStencil = allowOneSidedStencil;
    }

    /**
     * Creates a strict feasible-region policy that accepts one-sided stencils.
     *
     * @param relativeTolerance finite non-negative coarse/fine disagreement limit
     * @return strict qualification policy
     */
    public static SensitivityQualificationPolicy strict(double relativeTolerance) {
      return new SensitivityQualificationPolicy(relativeTolerance, true, true, true);
    }

    /**
     * Creates a numerical-evidence policy that retains infeasible samples and one-sided stencils.
     *
     * <p>
     * Infeasibility and stencil evidence remain visible as flags even when this policy does not reject them.
     * </p>
     *
     * @param relativeTolerance finite non-negative coarse/fine disagreement limit
     * @return numerical-only qualification policy
     */
    public static SensitivityQualificationPolicy numericalOnly(double relativeTolerance) {
      return new SensitivityQualificationPolicy(relativeTolerance, false, false, true);
    }

    /** @return maximum accepted relative disagreement */
    public double getRelativeTolerance() {
      return relativeTolerance;
    }

    /** @return true when the base point must satisfy hard constraints */
    public boolean isBaseFeasibleRequired() {
      return requireBaseFeasible;
    }

    /** @return true when every perturbation must satisfy hard constraints */
    public boolean arePerturbationsFeasibleRequired() {
      return requirePerturbationsFeasible;
    }

    /** @return true when forward and backward stencils are allowed */
    public boolean isOneSidedStencilAllowed() {
      return allowOneSidedStencil;
    }
  }

  /**
   * Immutable qualification of one constraint-margin derivative with respect to one decision variable.
   *
   * <p>
   * The assessment retains raw engineering units and is local to the sampled operating point. It is not a ranking,
   * global sensitivity, KKT multiplier, economic shadow price, or process-safety approval.
   * </p>
   */
  public static final class ConstraintSensitivityAssessment implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Immutable constraint identity and base state. */
    private final SensitivityConstraintSnapshot constraint;

    /** Immutable decision-variable identity and base state. */
    private final SensitivityParameterSnapshot parameter;

    /** Immutable selected-objective identity and base state. */
    private final SensitivityObjectiveSnapshot objective;

    /** Actual bounded finite-difference stencil. */
    private final AppliedFiniteDifferenceStencil stencil;

    /** Objective derivative in minimizer sign convention. */
    private final double minimizerObjectiveDerivative;

    /** Objective derivative in the declared raw objective direction. */
    private final double rawObjectiveDerivative;

    /** Constraint-margin derivative. */
    private final double marginDerivative;

    /** Coarse/fine relative disagreement for the selected objective derivative. */
    private final double objectiveRelativeDisagreement;

    /** Coarse/fine relative disagreement for this constraint-margin derivative. */
    private final double constraintRelativeDisagreement;

    /** Complete evidence flags, including policy-accepted cautions. */
    private final List<SensitivityEvidenceFlag> evidenceFlags;

    /** Evidence flags that reject this pair under the selected policy. */
    private final List<SensitivityEvidenceFlag> rejectionReasons;

    /** Human-readable evidence and policy diagnostics. */
    private final List<String> diagnostics;

    /** Creates one immutable local assessment. */
    private ConstraintSensitivityAssessment(SensitivityConstraintSnapshot constraint,
        SensitivityParameterSnapshot parameter, SensitivityObjectiveSnapshot objective,
        AppliedFiniteDifferenceStencil stencil, double minimizerObjectiveDerivative, double rawObjectiveDerivative,
        double marginDerivative, double objectiveRelativeDisagreement, double constraintRelativeDisagreement,
        List<SensitivityEvidenceFlag> evidenceFlags, List<SensitivityEvidenceFlag> rejectionReasons,
        List<String> diagnostics) {
      this.constraint = constraint;
      this.parameter = parameter;
      this.objective = objective;
      this.stencil = stencil;
      this.minimizerObjectiveDerivative = minimizerObjectiveDerivative;
      this.rawObjectiveDerivative = rawObjectiveDerivative;
      this.marginDerivative = marginDerivative;
      this.objectiveRelativeDisagreement = objectiveRelativeDisagreement;
      this.constraintRelativeDisagreement = constraintRelativeDisagreement;
      this.evidenceFlags = Collections.unmodifiableList(new ArrayList<SensitivityEvidenceFlag>(evidenceFlags));
      this.rejectionReasons = Collections.unmodifiableList(new ArrayList<SensitivityEvidenceFlag>(rejectionReasons));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** @return immutable constraint identity and base state */
    public SensitivityConstraintSnapshot getConstraint() {
      return constraint;
    }

    /** @return immutable decision-variable identity and base state */
    public SensitivityParameterSnapshot getParameter() {
      return parameter;
    }

    /** @return immutable selected-objective identity and base state */
    public SensitivityObjectiveSnapshot getObjective() {
      return objective;
    }

    /** @return actual bounded finite-difference stencil */
    public AppliedFiniteDifferenceStencil getStencil() {
      return stencil;
    }

    /** @return objective derivative in minimizer sign convention */
    public double getMinimizerObjectiveDerivative() {
      return minimizerObjectiveDerivative;
    }

    /** @return objective derivative before minimize/maximize sign conversion */
    public double getRawObjectiveDerivative() {
      return rawObjectiveDerivative;
    }

    /** @return derivative of constraint margin with respect to the decision variable */
    public double getMarginDerivative() {
      return marginDerivative;
    }

    /** @return objective derivative coarse/fine relative disagreement */
    public double getObjectiveRelativeDisagreement() {
      return objectiveRelativeDisagreement;
    }

    /** @return this constraint derivative's coarse/fine relative disagreement */
    public double getConstraintRelativeDisagreement() {
      return constraintRelativeDisagreement;
    }

    /**
     * Gets the declared raw-objective derivative unit.
     *
     * @return objective unit per parameter unit, or null when either unit is missing
     */
    public String getRawObjectiveDerivativeUnit() {
      return derivativeUnit(objective.getUnit(), parameter.getUnit());
    }

    /**
     * Gets the declared constraint-margin derivative unit.
     *
     * @return constraint unit per parameter unit, or null when either unit is missing
     */
    public String getMarginDerivativeUnit() {
      return derivativeUnit(constraint.getUnit(), parameter.getUnit());
    }

    /** @return complete evidence flags, including policy-accepted cautions */
    public List<SensitivityEvidenceFlag> getEvidenceFlags() {
      return evidenceFlags;
    }

    /** @return evidence flags that reject this pair under the selected policy */
    public List<SensitivityEvidenceFlag> getRejectionReasons() {
      return rejectionReasons;
    }

    /** @return human-readable evidence and policy diagnostics */
    public List<String> getDiagnostics() {
      return diagnostics;
    }

    /** @return true when no evidence flag rejects this pair under the selected policy */
    public boolean isAccepted() {
      return rejectionReasons.isEmpty();
    }

    /** @return true when increasing the parameter reduces the local constraint margin */
    public boolean isMarginReducedByIncreasingParameter() {
      return Double.isFinite(marginDerivative) && marginDerivative < 0.0;
    }

    /**
     * Checks whether increasing the parameter improves the declared raw objective locally.
     *
     * @return true for a positive derivative of a maximized objective or a negative derivative of a minimized one
     */
    public boolean isRawObjectiveImprovedByIncreasingParameter() {
      if (!Double.isFinite(rawObjectiveDerivative)) {
        return false;
      }
      return objective.getDirection() == ObjectiveDefinition.Direction.MAXIMIZE ? rawObjectiveDerivative > 0.0
          : rawObjectiveDerivative < 0.0;
    }

    /**
     * Builds a readable derivative unit without attempting unit conversion or dimensional simplification.
     */
    private static String derivativeUnit(String numeratorUnit, String denominatorUnit) {
      if (numeratorUnit == null || numeratorUnit.trim().isEmpty() || denominatorUnit == null
          || denominatorUnit.trim().isEmpty()) {
        return null;
      }
      return numeratorUnit + " per " + denominatorUnit;
    }
  }

  /** Immutable self-describing objective gradient, constraint Jacobian, and quality evidence. */
  public static final class SensitivityQualityResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Objective index represented by the gradient. */
    private final int objectiveIndex;

    /** Fine-step objective gradient. */
    private final double[] objectiveGradient;

    /** Fine-step constraint-margin Jacobian. */
    private final double[][] constraintJacobian;

    /** Immutable parameter-level quality records. */
    private final List<ParameterSensitivityQuality> parameterQuality;

    /** Immutable parameter identities and bounded base values. */
    private final List<SensitivityParameterSnapshot> parameterSnapshots;

    /** Immutable selected-objective identity, base values, and gradient. */
    private final SensitivityObjectiveSnapshot objectiveSnapshot;

    /** Immutable constraint identities, base margins, and derivative rows. */
    private final List<SensitivityConstraintSnapshot> constraintSnapshots;

    /** Base-point convergence flag. */
    private final boolean baseSimulationConverged;

    /** Base-point feasibility flag. */
    private final boolean baseFeasible;

    /** Base-point evaluation error, or null. */
    private final String baseErrorMessage;

    /** Creates an immutable sensitivity result. */
    private SensitivityQualityResult(int objectiveIndex, double[] objectiveGradient, double[][] constraintJacobian,
        List<ParameterSensitivityQuality> parameterQuality, List<ParameterDefinition> parameterDefinitions,
        ObjectiveDefinition objectiveDefinition, List<ConstraintDefinition> constraintDefinitions,
        EvaluationResult baseResult) {
      this.objectiveIndex = objectiveIndex;
      this.objectiveGradient = Arrays.copyOf(objectiveGradient, objectiveGradient.length);
      this.constraintJacobian = copyMatrix(constraintJacobian);
      this.parameterQuality = Collections
          .unmodifiableList(new ArrayList<ParameterSensitivityQuality>(parameterQuality));
      List<SensitivityParameterSnapshot> capturedParameters = new ArrayList<SensitivityParameterSnapshot>();
      for (int parameterIndex = 0; parameterIndex < parameterDefinitions.size(); parameterIndex++) {
        capturedParameters.add(new SensitivityParameterSnapshot(parameterIndex,
            parameterDefinitions.get(parameterIndex), baseResult.getParameters()[parameterIndex]));
      }
      this.parameterSnapshots = Collections.unmodifiableList(capturedParameters);
      this.objectiveSnapshot = new SensitivityObjectiveSnapshot(objectiveIndex, objectiveDefinition,
          baseResult.getObjectivesRaw()[objectiveIndex], baseResult.getObjectives()[objectiveIndex], objectiveGradient);
      List<SensitivityConstraintSnapshot> capturedConstraints = new ArrayList<SensitivityConstraintSnapshot>();
      for (int constraintIndex = 0; constraintIndex < constraintDefinitions.size(); constraintIndex++) {
        capturedConstraints.add(new SensitivityConstraintSnapshot(constraintIndex,
            constraintDefinitions.get(constraintIndex), baseResult.getConstraintValues()[constraintIndex],
            baseResult.getConstraintMargins()[constraintIndex], constraintJacobian[constraintIndex]));
      }
      this.constraintSnapshots = Collections.unmodifiableList(capturedConstraints);
      this.baseSimulationConverged = baseResult.isSimulationConverged();
      this.baseFeasible = baseResult.isFeasible();
      this.baseErrorMessage = baseResult.getErrorMessage();
    }

    /**
     * Gets the objective index represented by the gradient.
     *
     * @return registered objective index
     */
    public int getObjectiveIndex() {
      return objectiveIndex;
    }

    /**
     * Gets the fine-step objective gradient.
     *
     * @return defensive gradient array ordered like the decision variables
     */
    public double[] getObjectiveGradient() {
      return Arrays.copyOf(objectiveGradient, objectiveGradient.length);
    }

    /**
     * Gets the fine-step constraint-margin Jacobian.
     *
     * @return defensive matrix with constraints as rows and parameters as columns
     */
    public double[][] getConstraintJacobian() {
      return copyMatrix(constraintJacobian);
    }

    /**
     * Gets immutable parameter-level quality evidence.
     *
     * @return quality records ordered like the decision variables
     */
    public List<ParameterSensitivityQuality> getParameterQuality() {
      return parameterQuality;
    }

    /**
     * Gets immutable decision-variable identities and bounded base values.
     *
     * <p>
     * Snapshot index equals the objective-gradient and constraint-Jacobian column. The records remain unchanged after
     * evaluator definitions mutate or another operating point is evaluated.
     * </p>
     *
     * @return parameter snapshots in registration order
     */
    public List<SensitivityParameterSnapshot> getParameterSnapshots() {
      return parameterSnapshots;
    }

    /**
     * Gets the selected objective identity, base values, and fine-step gradient.
     *
     * @return immutable objective snapshot
     */
    public SensitivityObjectiveSnapshot getObjectiveSnapshot() {
      return objectiveSnapshot;
    }

    /**
     * Gets immutable constraint identities, base margins, and fine-step derivative rows.
     *
     * <p>
     * Snapshot index equals the constraint-Jacobian row. Raw margins and derivatives retain their declared units and
     * must not be ranked across unlike constraints without explicit engineering scaling.
     * </p>
     *
     * @return constraint snapshots in registration order
     */
    public List<SensitivityConstraintSnapshot> getConstraintSnapshots() {
      return constraintSnapshots;
    }

    /**
     * Qualifies every local constraint/parameter sensitivity using an explicit evidence policy.
     *
     * <p>
     * Results are ordered constraint-major and then parameter-major. Every pair retains complete evidence flags even
     * when the policy accepts a caution such as an infeasible perturbation or a bound-driven one-sided stencil. No
     * process simulation is performed by this method; it consumes only this immutable sensitivity result.
     * </p>
     *
     * <p>
     * Accepted pairs remain local derivatives in their declared raw units. The method does not compare unlike
     * constraints, infer an active set, calculate a KKT multiplier or shadow price, or establish engineering validity
     * outside the sampled base and perturbation points.
     * </p>
     *
     * @param policy explicit numerical and feasible-region qualification policy
     * @return immutable assessments for every constraint/parameter pair
     */
    public List<ConstraintSensitivityAssessment> assessConstraintSensitivities(SensitivityQualificationPolicy policy) {
      if (policy == null) {
        throw new IllegalArgumentException("Sensitivity qualification policy must not be null");
      }
      List<ConstraintSensitivityAssessment> assessments = new ArrayList<ConstraintSensitivityAssessment>();
      for (int constraintIndex = 0; constraintIndex < constraintSnapshots.size(); constraintIndex++) {
        SensitivityConstraintSnapshot constraint = constraintSnapshots.get(constraintIndex);
        for (int parameterIndex = 0; parameterIndex < parameterSnapshots.size(); parameterIndex++) {
          SensitivityParameterSnapshot parameter = parameterSnapshots.get(parameterIndex);
          ParameterSensitivityQuality quality = parameterQuality.get(parameterIndex);
          double objectiveDerivative = objectiveGradient[parameterIndex];
          double rawObjectiveDerivative = objectiveSnapshot.getDirection() == ObjectiveDefinition.Direction.MAXIMIZE
              ? -objectiveDerivative
              : objectiveDerivative;
          double marginDerivative = constraintJacobian[constraintIndex][parameterIndex];
          double objectiveDisagreement = quality.getObjectiveRelativeDisagreement();
          double constraintDisagreement = quality.getConstraintRelativeDisagreement()[constraintIndex];
          List<SensitivityEvidenceFlag> flags = new ArrayList<SensitivityEvidenceFlag>();
          List<SensitivityEvidenceFlag> rejections = new ArrayList<SensitivityEvidenceFlag>();
          List<String> diagnostics = new ArrayList<String>();

          if (!baseSimulationConverged) {
            flags.add(SensitivityEvidenceFlag.BASE_NOT_CONVERGED);
            rejections.add(SensitivityEvidenceFlag.BASE_NOT_CONVERGED);
            diagnostics.add("Base process simulation did not converge");
          }
          if (!baseFeasible) {
            flags.add(SensitivityEvidenceFlag.BASE_INFEASIBLE);
            diagnostics.add("Base point violates at least one registered hard constraint");
            if (policy.isBaseFeasibleRequired()) {
              rejections.add(SensitivityEvidenceFlag.BASE_INFEASIBLE);
            }
          }
          if (baseErrorMessage != null) {
            flags.add(SensitivityEvidenceFlag.BASE_EVALUATION_ERROR);
            rejections.add(SensitivityEvidenceFlag.BASE_EVALUATION_ERROR);
            diagnostics.add("Base evaluation error: " + baseErrorMessage);
          }

          boolean perturbationNotConverged = false;
          boolean perturbationInfeasible = false;
          boolean perturbationError = false;
          for (SensitivityPerturbation perturbation : quality.getPerturbations()) {
            perturbationNotConverged = perturbationNotConverged || !perturbation.isSimulationConverged();
            perturbationInfeasible = perturbationInfeasible || !perturbation.isFeasible();
            if (perturbation.getErrorMessage() != null) {
              perturbationError = true;
              diagnostics.add("Perturbation error at " + perturbation.getParameterValue() + " "
                  + safeUnit(parameter.getUnit()) + ": " + perturbation.getErrorMessage());
            }
          }
          if (perturbationNotConverged) {
            flags.add(SensitivityEvidenceFlag.PERTURBATION_NOT_CONVERGED);
            rejections.add(SensitivityEvidenceFlag.PERTURBATION_NOT_CONVERGED);
            diagnostics.add("At least one finite-difference perturbation did not converge");
          }
          if (perturbationInfeasible) {
            flags.add(SensitivityEvidenceFlag.PERTURBATION_INFEASIBLE);
            diagnostics.add("At least one perturbation violates a registered hard constraint");
            if (policy.arePerturbationsFeasibleRequired()) {
              rejections.add(SensitivityEvidenceFlag.PERTURBATION_INFEASIBLE);
            }
          }
          if (perturbationError) {
            flags.add(SensitivityEvidenceFlag.PERTURBATION_EVALUATION_ERROR);
            rejections.add(SensitivityEvidenceFlag.PERTURBATION_EVALUATION_ERROR);
          }

          if (!Double.isFinite(objectiveDerivative) || !Double.isFinite(marginDerivative)) {
            flags.add(SensitivityEvidenceFlag.NON_FINITE_DERIVATIVE);
            rejections.add(SensitivityEvidenceFlag.NON_FINITE_DERIVATIVE);
            diagnostics.add("Objective or constraint-margin derivative is non-finite");
          }
          if (!Double.isFinite(objectiveDisagreement) || !Double.isFinite(constraintDisagreement)
              || objectiveDisagreement > policy.getRelativeTolerance()
              || constraintDisagreement > policy.getRelativeTolerance()) {
            flags.add(SensitivityEvidenceFlag.NUMERICALLY_UNSTABLE);
            rejections.add(SensitivityEvidenceFlag.NUMERICALLY_UNSTABLE);
            diagnostics.add("Coarse/fine relative disagreement exceeds or cannot be compared with tolerance "
                + policy.getRelativeTolerance() + " (objective=" + objectiveDisagreement + ", constraint="
                + constraintDisagreement + ")");
          }

          AppliedFiniteDifferenceStencil stencil = quality.getStencil();
          if (stencil == AppliedFiniteDifferenceStencil.FORWARD || stencil == AppliedFiniteDifferenceStencil.BACKWARD) {
            flags.add(SensitivityEvidenceFlag.ONE_SIDED_STENCIL);
            diagnostics.add("One-sided " + stencil + " finite-difference stencil used");
            if (!policy.isOneSidedStencilAllowed()) {
              rejections.add(SensitivityEvidenceFlag.ONE_SIDED_STENCIL);
            }
          } else if (stencil == AppliedFiniteDifferenceStencil.FIXED) {
            flags.add(SensitivityEvidenceFlag.FIXED_PARAMETER);
            rejections.add(SensitivityEvidenceFlag.FIXED_PARAMETER);
            diagnostics.add("Parameter has equal lower and upper bounds and is not an available operating action");
          }

          if (rejections.isEmpty()) {
            diagnostics.add("Accepted under the declared local sensitivity qualification policy");
          }
          assessments.add(new ConstraintSensitivityAssessment(constraint, parameter, objectiveSnapshot, stencil,
              objectiveDerivative, rawObjectiveDerivative, marginDerivative, objectiveDisagreement,
              constraintDisagreement, flags, rejections, diagnostics));
        }
      }
      return Collections.unmodifiableList(assessments);
    }

    /**
     * Gets only constraint/parameter pairs accepted by an explicit evidence policy.
     *
     * <p>
     * Call {@link #assessConstraintSensitivities(SensitivityQualificationPolicy)} when rejected pairs and their
     * diagnostics must also be retained. Filtering does not perform another process simulation.
     * </p>
     *
     * @param policy explicit numerical and feasible-region qualification policy
     * @return immutable accepted assessments in constraint-major, parameter-major order
     */
    public List<ConstraintSensitivityAssessment> getAcceptedConstraintSensitivities(
        SensitivityQualificationPolicy policy) {
      List<ConstraintSensitivityAssessment> accepted = new ArrayList<ConstraintSensitivityAssessment>();
      for (ConstraintSensitivityAssessment assessment : assessConstraintSensitivities(policy)) {
        if (assessment.isAccepted()) {
          accepted.add(assessment);
        }
      }
      return Collections.unmodifiableList(accepted);
    }

    /** Returns a readable placeholder for a missing unit in diagnostics. */
    private static String safeUnit(String unit) {
      return unit == null || unit.trim().isEmpty() ? "(unit unspecified)" : unit;
    }

    /**
     * Checks convergence of the base process simulation.
     *
     * @return true when the base model converged
     */
    public boolean isBaseSimulationConverged() {
      return baseSimulationConverged;
    }

    /**
     * Checks hard-constraint feasibility of the base point.
     *
     * @return true when the base point was feasible
     */
    public boolean isBaseFeasible() {
      return baseFeasible;
    }

    /**
     * Gets the base evaluation error.
     *
     * @return error message, or null when none was reported
     */
    public String getBaseErrorMessage() {
      return baseErrorMessage;
    }

    /** Returns a defensive rectangular or ragged matrix copy. */
    private static double[][] copyMatrix(double[][] matrix) {
      double[][] copy = new double[matrix.length][];
      for (int row = 0; row < matrix.length; row++) {
        copy[row] = Arrays.copyOf(matrix[row], matrix[row].length);
      }
      return copy;
    }
  }

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

  /** Finite-difference stencil. Forward difference preserves the historical default cost. */
  private FiniteDifferenceMethod finiteDifferenceMethod = FiniteDifferenceMethod.FORWARD;

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

    /** Whether direct evaluator calls clamp requested values to the declared bounds. */
    private boolean clampToBounds = true;

    /** Optional custom setter for non-automation variables. */
    private transient BiConsumer<ProcessModel, Double> setter;

    /** Default constructor for serialization frameworks. */
    public ParameterDefinition() {
    }

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
    public ParameterDefinition(String name, String address, double lowerBound, double upperBound, String unit) {
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
     * Checks whether direct evaluator calls clamp requested values to the declared bounds.
     *
     * @return true when requested values are clamped before the setter is called
     */
    public boolean isClampToBounds() {
      return clampToBounds;
    }

    /**
     * Sets whether direct evaluator calls clamp requested values to the declared bounds.
     *
     * <p>
     * The default is true for compatibility. Set false only when a strict setter must inspect and reject the exact
     * requested value, such as an enumerated operating action.
     * </p>
     *
     * @param clampToBounds true to retain legacy clamping, false to pass the exact value
     */
    public void setClampToBounds(boolean clampToBounds) {
      this.clampToBounds = clampToBounds;
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
    public ObjectiveDefinition() {
    }

    /**
     * Creates an objective definition.
     *
     * @param name objective name
     * @param evaluator model-level evaluator
     * @param direction optimization direction
     */
    public ObjectiveDefinition(String name, ToDoubleFunction<ProcessModel> evaluator, Direction direction) {
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
      return toMinimizerValue(evaluateRaw(model));
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

    /**
     * Applies the minimizer sign convention to an already sampled objective value.
     *
     * @param rawValue raw objective value
     * @return sign-adjusted objective value
     */
    private double toMinimizerValue(double rawValue) {
      return direction == Direction.MAXIMIZE ? -rawValue : rawValue;
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

    /** Structured boundary sampler, mutually exclusive with the scalar evaluator. */
    private transient BoundarySampleEvaluator boundarySampleEvaluator;

    /** Frozen boundary identity and provenance, or null for a general constraint. */
    private ProcessBoundaryConstraintEvidence.Metadata boundaryMetadata;

    /** Physical scale used only for the dimensionless boundary violation. */
    private double boundaryResidualScale = 1.0;

    /** Whether this constraint represents equipment capacity utilization. */
    private boolean capacityConstraint = false;

    /** Area name for capacity constraints. */
    private String areaName;

    /** Equipment name for capacity constraints. */
    private String equipmentName;

    /** Original equipment constraint name for capacity constraints. */
    private String equipmentConstraintName;

    /** Captured capacity constraint for operating-point sampling. */
    private transient CapacityConstraint capturedCapacityConstraint;

    /** Frozen physical unit for normalized installed-capacity definitions. */
    private String capacityPhysicalUnit;

    /** Frozen Java equipment class name. */
    private String capacityEquipmentClassName;

    /** Frozen IEC 81346 reference designation. */
    private String capacityReferenceDesignation;

    /** Frozen direct or strategy-generated origin. */
    private InstalledEquipmentCapacityEvidence.ConstraintOrigin capacityConstraintOrigin = InstalledEquipmentCapacityEvidence.ConstraintOrigin.UNKNOWN;

    /** Default constructor for serialization frameworks. */
    public ConstraintDefinition() {
    }

    /**
     * Creates a lower-bound model constraint.
     *
     * @param name constraint name
     * @param evaluator model-level evaluator
     * @param lowerBound lower bound
     */
    public ConstraintDefinition(String name, ToDoubleFunction<ProcessModel> evaluator, double lowerBound) {
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
    public ConstraintDefinition(String name, ToDoubleFunction<ProcessModel> evaluator, double lowerBound,
        double upperBound) {
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

    /** @return structured boundary sampler, or null for a scalar constraint */
    public BoundarySampleEvaluator getBoundarySampleEvaluator() {
      return boundarySampleEvaluator;
    }

    /**
     * Sets structured boundary sampling metadata.
     *
     * @param metadata immutable boundary identity and provenance
     * @param sampleEvaluator runtime sampler
     * @param residualScale finite positive scale in {@link #getUnit()}
     */
    public void setBoundaryMetadata(ProcessBoundaryConstraintEvidence.Metadata metadata,
        BoundarySampleEvaluator sampleEvaluator, double residualScale) {
      if (metadata == null || sampleEvaluator == null) {
        throw new IllegalArgumentException("Boundary metadata and sampler are required");
      }
      if (!Double.isFinite(residualScale) || residualScale <= 0.0) {
        throw new IllegalArgumentException("Boundary residual scale must be finite and positive");
      }
      this.boundaryMetadata = metadata;
      this.boundarySampleEvaluator = sampleEvaluator;
      this.boundaryResidualScale = residualScale;
    }

    /** @return true when this definition represents a qualified process boundary */
    public boolean isBoundaryConstraint() {
      return boundaryMetadata != null;
    }

    /** @return immutable boundary metadata, or null for a general constraint */
    public ProcessBoundaryConstraintEvidence.Metadata getBoundaryMetadata() {
      return boundaryMetadata;
    }

    /** @return positive physical residual scale */
    public double getBoundaryResidualScale() {
      return boundaryResidualScale;
    }

    /** Samples a structured boundary observable exactly once. */
    private ProcessBoundaryConstraintEvidence.Sample evaluateBoundarySample(ProcessModel model) {
      if (boundarySampleEvaluator == null) {
        return new ProcessBoundaryConstraintEvidence.Sample(null,
            ProcessBoundaryConstraintEvidence.CalculationStatus.NOT_CALCULABLE, null, null,
            "Boundary sampler is unavailable after serialization");
      }
      return boundarySampleEvaluator.evaluate(model);
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
     * Gets the engineering unit of the underlying installed-capacity values.
     *
     * <p>
     * {@link #getUnit()} is {@code "1"} for an installed-capacity definition because its registered value and margin
     * are normalized. This accessor carries the separate physical unit used by the immutable capacity evidence.
     * </p>
     *
     * @return physical engineering unit, or null for a general constraint
     */
    public String getCapacityPhysicalUnit() {
      return capacityPhysicalUnit;
    }

    /** @return frozen Java equipment class name, or null for a general constraint */
    public String getCapacityEquipmentClassName() {
      return capacityEquipmentClassName;
    }

    /** @return frozen IEC 81346 reference designation, or null for a general constraint */
    public String getCapacityReferenceDesignation() {
      return capacityReferenceDesignation;
    }

    /** @return frozen direct or strategy-generated origin */
    public InstalledEquipmentCapacityEvidence.ConstraintOrigin getCapacityConstraintOrigin() {
      return capacityConstraintOrigin;
    }

    /**
     * Marks this definition as an equipment capacity constraint.
     *
     * @param areaName process area name
     * @param equipmentName equipment name
     * @param equipmentConstraintName equipment constraint name
     * @param capacityConstraint captured capacity constraint
     */
    public void setCapacityMetadata(String areaName, String equipmentName, String equipmentConstraintName,
        CapacityConstraint capacityConstraint) {
      setCapacityMetadata(areaName, equipmentName, equipmentConstraintName, null, null,
          InstalledEquipmentCapacityEvidence.ConstraintOrigin.UNKNOWN, capacityConstraint);
    }

    /**
     * Marks this definition as installed equipment capacity with frozen identity metadata.
     *
     * @param areaName process area name
     * @param equipmentName equipment name
     * @param equipmentConstraintName equipment constraint name
     * @param equipmentClassName fully qualified Java equipment class name
     * @param referenceDesignation IEC 81346 reference designation
     * @param origin direct or strategy-generated origin
     * @param capacityConstraint captured capacity constraint
     */
    public void setCapacityMetadata(String areaName, String equipmentName, String equipmentConstraintName,
        String equipmentClassName, String referenceDesignation,
        InstalledEquipmentCapacityEvidence.ConstraintOrigin origin, CapacityConstraint capacityConstraint) {
      this.capacityConstraint = true;
      this.areaName = areaName;
      this.equipmentName = equipmentName;
      this.equipmentConstraintName = equipmentConstraintName;
      this.capacityPhysicalUnit = capacityConstraint == null ? null : capacityConstraint.getUnit();
      this.capacityEquipmentClassName = equipmentClassName;
      this.capacityReferenceDesignation = referenceDesignation;
      this.capacityConstraintOrigin = origin == null ? InstalledEquipmentCapacityEvidence.ConstraintOrigin.UNKNOWN
          : origin;
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
      return marginFromValue(evaluate(model));
    }

    /**
     * Computes the constraint margin from an already sampled value.
     *
     * @param value sampled constraint value
     * @return positive margin when satisfied and negative margin when violated
     */
    double marginFromValue(double value) {
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
      return penaltyFromMargin(margin(model));
    }

    /**
     * Computes the violation penalty from an already derived margin.
     *
     * @param margin sampled constraint margin
     * @return zero when satisfied, otherwise a positive quadratic penalty
     */
    private double penaltyFromMargin(double margin) {
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

    /**
     * Applicability of the evidence basis for a capacity-constraint snapshot.
     */
    public enum EvidenceApplicability {
      /** No scalar validity range was supplied, so applicability was not assessed. */
      NOT_ASSESSED,

      /** The snapshotted value is inside the supplied inclusive validity range. */
      WITHIN_VALIDITY_RANGE,

      /** The snapshotted value is outside the supplied inclusive validity range. */
      OUTSIDE_VALIDITY_RANGE
    }

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

    /** Whether the active constraint is a minimum-directed limit. */
    private boolean minimumConstraint;

    /** Provenance of the active constraint limit. */
    private String dataSource = "not_set";

    /** Whether confidence was explicitly assigned to the active constraint. */
    private boolean confidenceSet;

    /** Evidence-quality confidence of the active constraint. */
    private double confidence = Double.NaN;

    /** Whether a scalar validity range was assigned to the active constraint. */
    private boolean validityRangeSet;

    /** Lower inclusive validity bound in the constraint unit. */
    private double validityMinimum = Double.NaN;

    /** Upper inclusive validity bound in the constraint unit. */
    private double validityMaximum = Double.NaN;

    /** Whether the snapshotted current value lies inside the assigned validity range. */
    private boolean currentValueWithinValidityRange;

    /** Constraint unit. */
    private String unit;

    /** Whether the bottleneck is inside feasible capacity. */
    private boolean feasible;

    /** Default constructor for serialization frameworks. */
    public BottleneckStatus() {
    }

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
    public BottleneckStatus(String areaName, String equipmentName, String constraintName, double utilization,
        double currentValue, double designValue, String unit, boolean feasible) {
      this(areaName, equipmentName, constraintName, utilization, currentValue, designValue, false, "not_set", unit,
          feasible);
    }

    /**
     * Creates a bottleneck status with explicit limit direction.
     *
     * @param areaName process area name
     * @param equipmentName equipment name
     * @param constraintName constraint name
     * @param utilization utilization fraction
     * @param currentValue current constraint value
     * @param designValue reported design or minimum limit
     * @param minimumConstraint true when values below the limit are worse
     * @param unit constraint unit
     * @param feasible true when utilization is less than or equal to one
     */
    public BottleneckStatus(String areaName, String equipmentName, String constraintName, double utilization,
        double currentValue, double designValue, boolean minimumConstraint, String unit, boolean feasible) {
      this(areaName, equipmentName, constraintName, utilization, currentValue, designValue, minimumConstraint,
          "not_set", unit, feasible);
    }

    /**
     * Creates a bottleneck status with explicit limit direction and provenance.
     *
     * @param areaName process area name
     * @param equipmentName equipment name
     * @param constraintName constraint name
     * @param utilization utilization fraction
     * @param currentValue current constraint value
     * @param designValue reported design or minimum limit
     * @param minimumConstraint true when values below the limit are worse
     * @param dataSource provenance of the reported limit
     * @param unit constraint unit
     * @param feasible true when utilization is less than or equal to one
     */
    public BottleneckStatus(String areaName, String equipmentName, String constraintName, double utilization,
        double currentValue, double designValue, boolean minimumConstraint, String dataSource, String unit,
        boolean feasible) {
      this(areaName, equipmentName, constraintName, utilization, currentValue, designValue, minimumConstraint,
          dataSource, false, Double.NaN, false, Double.NaN, Double.NaN, unit, feasible);
    }

    /**
     * Creates a bottleneck status with evidence-quality and scalar-validity metadata. Enabled metadata that is
     * non-finite, outside the confidence range, or has reversed bounds is normalized to the explicit unset state.
     * Applicability is derived from the snapshotted current value and retained bounds.
     *
     * @param areaName process area name
     * @param equipmentName equipment name
     * @param constraintName constraint name
     * @param utilization utilization fraction
     * @param currentValue current constraint value
     * @param designValue reported design or minimum limit
     * @param minimumConstraint true when values below the limit are worse
     * @param dataSource provenance of the reported limit
     * @param confidenceSet true to retain a finite confidence in the range [0, 1]
     * @param confidence evidence-quality confidence, or NaN when unset
     * @param validityRangeSet true to retain finite, ordered scalar validity bounds
     * @param validityMinimum lower inclusive validity bound, or NaN when unset
     * @param validityMaximum upper inclusive validity bound, or NaN when unset
     * @param unit constraint unit
     * @param feasible true when utilization is less than or equal to one
     */
    public BottleneckStatus(String areaName, String equipmentName, String constraintName, double utilization,
        double currentValue, double designValue, boolean minimumConstraint, String dataSource, boolean confidenceSet,
        double confidence, boolean validityRangeSet, double validityMinimum, double validityMaximum, String unit,
        boolean feasible) {
      this.areaName = areaName;
      this.equipmentName = equipmentName;
      this.constraintName = constraintName;
      this.utilization = utilization;
      this.currentValue = currentValue;
      this.designValue = designValue;
      this.minimumConstraint = minimumConstraint;
      this.dataSource = dataSource == null ? "not_set" : dataSource;
      this.confidenceSet = confidenceSet && !Double.isNaN(confidence) && !Double.isInfinite(confidence)
          && confidence >= 0.0 && confidence <= 1.0;
      this.confidence = this.confidenceSet ? confidence : Double.NaN;
      this.validityRangeSet = validityRangeSet && !Double.isNaN(validityMinimum) && !Double.isInfinite(validityMinimum)
          && !Double.isNaN(validityMaximum) && !Double.isInfinite(validityMaximum)
          && validityMinimum <= validityMaximum;
      this.validityMinimum = this.validityRangeSet ? validityMinimum : Double.NaN;
      this.validityMaximum = this.validityRangeSet ? validityMaximum : Double.NaN;
      this.currentValueWithinValidityRange = this.validityRangeSet && currentValue >= this.validityMinimum
          && currentValue <= this.validityMaximum;
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
     * Checks whether the bottleneck is a minimum-directed constraint.
     *
     * @return true when values below the reported design value are worse
     */
    public boolean isMinimumConstraint() {
      return minimumConstraint;
    }

    /**
     * Gets the provenance of the active constraint limit.
     *
     * @return source tag from the underlying capacity constraint
     */
    public String getDataSource() {
      return dataSource == null ? "not_set" : dataSource;
    }

    /**
     * Checks whether confidence was explicitly assigned to the active constraint.
     *
     * @return true when confidence is available
     */
    public boolean hasConfidence() {
      return confidenceSet;
    }

    /**
     * Gets the active constraint's evidence-quality confidence.
     *
     * @return confidence from zero to one, or NaN when unset
     */
    public double getConfidence() {
      return confidenceSet ? confidence : Double.NaN;
    }

    /**
     * Checks whether a scalar validity range was assigned to the active constraint.
     *
     * @return true when validity bounds are available
     */
    public boolean hasValidityRange() {
      return validityRangeSet;
    }

    /**
     * Gets the lower inclusive validity bound.
     *
     * @return lower bound in the constraint unit, or NaN when unset
     */
    public double getValidityMinimum() {
      return validityRangeSet ? validityMinimum : Double.NaN;
    }

    /**
     * Gets the upper inclusive validity bound.
     *
     * @return upper bound in the constraint unit, or NaN when unset
     */
    public double getValidityMaximum() {
      return validityRangeSet ? validityMaximum : Double.NaN;
    }

    /**
     * Checks whether the snapshotted current value is inside the assigned validity range.
     *
     * @return true when a range is assigned and the current value is inside its inclusive bounds
     */
    public boolean isCurrentValueWithinValidityRange() {
      return validityRangeSet && currentValueWithinValidityRange;
    }

    /**
     * Gets the applicability of the evidence basis at the snapshotted operating point.
     *
     * <p>
     * This diagnostic does not alter utilization, feasibility, or ranking. Confidence remains an evidence-quality
     * annotation and is not interpreted as a probability of safe operation.
     * </p>
     *
     * @return applicability of the scalar validity range
     */
    public EvidenceApplicability getEvidenceApplicability() {
      if (!validityRangeSet) {
        return EvidenceApplicability.NOT_ASSESSED;
      }
      return currentValueWithinValidityRange ? EvidenceApplicability.WITHIN_VALIDITY_RANGE
          : EvidenceApplicability.OUTSIDE_VALIDITY_RANGE;
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

    /** Immutable ranked legacy bottleneck snapshots for this evaluated model state. */
    private List<BottleneckStatus> rankedCapacityConstraints = Collections.emptyList();

    /** Immutable unit-safe installed-capacity evidence for this evaluated model state. */
    private List<InstalledEquipmentCapacityEvidence> installedEquipmentCapacityEvidence = Collections.emptyList();

    /** Immutable qualified process-boundary evidence for this evaluated model state. */
    private List<ProcessBoundaryConstraintEvidence> processBoundaryConstraintEvidence = Collections.emptyList();

    /** Additional scalar outputs. */
    private Map<String, Double> additionalOutputs = new LinkedHashMap<String, Double>();

    /** Error message when evaluation fails. */
    private String errorMessage;

    /** Wall-clock evaluation time in milliseconds. */
    private long evaluationTimeMs;

    /** Evaluation sequence number. */
    private int evaluationNumber;

    /** Default constructor. */
    public EvaluationResult() {
    }

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
     * Gets all capacity constraints snapshotted after this model evaluation.
     *
     * <p>
     * The list is immutable and ordered by descending utilization. It remains tied to this evaluation even after the
     * evaluator runs another operating point.
     * </p>
     *
     * @return immutable ranked capacity snapshots
     */
    public List<BottleneckStatus> getRankedCapacityConstraints() {
      if (rankedCapacityConstraints == null || rankedCapacityConstraints.isEmpty()) {
        return Collections.emptyList();
      }
      return Collections.unmodifiableList(new ArrayList<BottleneckStatus>(rankedCapacityConstraints));
    }

    /**
     * Sets ranked capacity snapshots using a defensive immutable copy.
     *
     * @param rankedCapacityConstraints ranked capacity snapshots
     */
    public void setRankedCapacityConstraints(List<BottleneckStatus> rankedCapacityConstraints) {
      if (rankedCapacityConstraints == null || rankedCapacityConstraints.isEmpty()) {
        this.rankedCapacityConstraints = Collections.emptyList();
        return;
      }
      this.rankedCapacityConstraints = Collections
          .unmodifiableList(new ArrayList<BottleneckStatus>(rankedCapacityConstraints));
    }

    /**
     * Gets unit-safe installed-equipment capacity evidence sampled at this completed operating point.
     *
     * <p>
     * The returned list is a fresh immutable copy ordered by descending normalized utilization. Every row separates
     * dimensionless feasibility from physical current, limit, margin, and relief values.
     * </p>
     *
     * @return fresh immutable installed-capacity evidence
     */
    public List<InstalledEquipmentCapacityEvidence> getInstalledEquipmentCapacityEvidence() {
      if (installedEquipmentCapacityEvidence == null || installedEquipmentCapacityEvidence.isEmpty()) {
        return Collections.emptyList();
      }
      return Collections
          .unmodifiableList(new ArrayList<InstalledEquipmentCapacityEvidence>(installedEquipmentCapacityEvidence));
    }

    /**
     * Sets installed-capacity evidence using a defensive immutable copy.
     *
     * @param evidence utilization-ranked installed-capacity evidence
     */
    public void setInstalledEquipmentCapacityEvidence(List<InstalledEquipmentCapacityEvidence> evidence) {
      if (evidence == null || evidence.isEmpty()) {
        installedEquipmentCapacityEvidence = Collections.emptyList();
        return;
      }
      installedEquipmentCapacityEvidence = Collections
          .unmodifiableList(new ArrayList<InstalledEquipmentCapacityEvidence>(evidence));
    }

    /**
     * Gets process-boundary evidence sampled at this completed operating point.
     *
     * @return fresh immutable evidence in constraint registration order
     */
    public List<ProcessBoundaryConstraintEvidence> getProcessBoundaryConstraintEvidence() {
      if (processBoundaryConstraintEvidence == null || processBoundaryConstraintEvidence.isEmpty()) {
        return Collections.emptyList();
      }
      return Collections
          .unmodifiableList(new ArrayList<ProcessBoundaryConstraintEvidence>(processBoundaryConstraintEvidence));
    }

    /** Sets boundary evidence using a defensive immutable copy. */
    public void setProcessBoundaryConstraintEvidence(List<ProcessBoundaryConstraintEvidence> evidence) {
      if (evidence == null || evidence.isEmpty()) {
        processBoundaryConstraintEvidence = Collections.emptyList();
        return;
      }
      processBoundaryConstraintEvidence = Collections
          .unmodifiableList(new ArrayList<ProcessBoundaryConstraintEvidence>(evidence));
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
  public ProcessModelSimulationEvaluator() {
  }

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
  public ProcessModelSimulationEvaluator addParameter(String address, double lowerBound, double upperBound,
      String unit) {
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
  public ProcessModelSimulationEvaluator addParameter(String name, String address, double lowerBound, double upperBound,
      String unit) {
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
  public ProcessModelSimulationEvaluator addParameterWithSetter(String name, BiConsumer<ProcessModel, Double> setter,
      double lowerBound, double upperBound, String unit) {
    ParameterDefinition parameter = new ParameterDefinition(name, name, lowerBound, upperBound, unit);
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
  public ProcessModelSimulationEvaluator addObjective(String name, ToDoubleFunction<ProcessModel> evaluator) {
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
  public ProcessModelSimulationEvaluator addObjective(String name, ToDoubleFunction<ProcessModel> evaluator,
      ObjectiveDefinition.Direction direction) {
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
  public ProcessModelSimulationEvaluator addConstraintLowerBound(String name, ToDoubleFunction<ProcessModel> evaluator,
      double lowerBound) {
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
  public ProcessModelSimulationEvaluator addConstraintUpperBound(String name, ToDoubleFunction<ProcessModel> evaluator,
      double upperBound) {
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
  public ProcessModelSimulationEvaluator addConstraintRange(String name, ToDoubleFunction<ProcessModel> evaluator,
      double lowerBound, double upperBound) {
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
  public ProcessModelSimulationEvaluator addConstraintEquality(String name, ToDoubleFunction<ProcessModel> evaluator,
      double target, double tolerance) {
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
   * Adds a fully qualified process-boundary constraint.
   *
   * <p>
   * Bounds and units are frozen at registration. The structured sampler is invoked once after each completed model run;
   * missing, non-finite, not-calculable, or out-of-validity evidence fails a hard constraint closed.
   * </p>
   *
   * @param name human-readable constraint name
   * @param metadata immutable boundary identity and provenance
   * @param sampleEvaluator structured runtime sampler
   * @param type lower, upper, range, or equality constraint
   * @param lowerBound lower bound or equality target
   * @param upperBound upper bound
   * @param equalityTolerance absolute equality tolerance
   * @param unit physical engineering unit
   * @param hard whether unavailable or violated evidence makes the point infeasible
   * @param penaltyWeight penalty multiplier
   * @param residualScale positive physical scale used for dimensionless violation only
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addBoundaryConstraint(String name,
      ProcessBoundaryConstraintEvidence.Metadata metadata, BoundarySampleEvaluator sampleEvaluator,
      ConstraintDefinition.Type type, double lowerBound, double upperBound, double equalityTolerance, String unit,
      boolean hard, double penaltyWeight, double residualScale) {
    if (name == null || name.trim().length() == 0 || type == null) {
      throw new IllegalArgumentException("Boundary constraint name and type are required");
    }
    if (unit == null || unit.trim().length() == 0) {
      throw new IllegalArgumentException("Boundary constraint unit is required");
    }
    if (!Double.isFinite(penaltyWeight) || penaltyWeight < 0.0) {
      throw new IllegalArgumentException("Penalty weight must be finite and non-negative");
    }
    if (type == ConstraintDefinition.Type.LOWER_BOUND && !Double.isFinite(lowerBound)
        || type == ConstraintDefinition.Type.UPPER_BOUND && !Double.isFinite(upperBound)
        || type == ConstraintDefinition.Type.RANGE
            && (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound) || lowerBound > upperBound)
        || type == ConstraintDefinition.Type.EQUALITY
            && (!Double.isFinite(lowerBound) || !Double.isFinite(equalityTolerance) || equalityTolerance < 0.0)) {
      throw new IllegalArgumentException("Boundary bounds and tolerance must be finite and ordered for the type");
    }
    ConstraintDefinition definition = new ConstraintDefinition();
    definition.setName(name);
    definition.setType(type);
    definition.setLowerBound(lowerBound);
    definition.setUpperBound(upperBound);
    definition.setEqualityTolerance(equalityTolerance);
    definition.setUnit(unit);
    definition.setHard(hard);
    definition.setPenaltyWeight(penaltyWeight);
    definition.setBoundaryMetadata(metadata, sampleEvaluator, residualScale);
    constraints.add(definition);
    return this;
  }

  /**
   * Adds an equality constraint from one period of a {@link NetworkNomination}.
   *
   * @param name constraint name
   * @param areaName process area containing the nominated point
   * @param nomination immutable nomination series
   * @param periodIndex zero-based nomination period
   * @param flowDirection positive-flow direction
   * @param evaluator actual boundary rate sampler
   * @param hard whether unavailable or off-nomination evidence is infeasible
   * @param penaltyWeight penalty multiplier
   * @param residualScale positive scale in the nomination unit
   * @param provenance source of the nomination
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addNominationConstraint(String name, String areaName,
      NetworkNomination nomination, int periodIndex, ProcessBoundaryConstraintEvidence.FlowDirection flowDirection,
      final ToDoubleFunction<ProcessModel> evaluator, boolean hard, double penaltyWeight, double residualScale,
      String provenance) {
    if (nomination == null || periodIndex < 0 || periodIndex >= nomination.size() || evaluator == null) {
      throw new IllegalArgumentException("Nomination, valid period index, and evaluator are required");
    }
    double target = nomination.getValue(periodIndex);
    double tolerance = Math.abs(target) * Math.abs(nomination.getToleranceFraction());
    ProcessBoundaryConstraintEvidence.Metadata metadata = new ProcessBoundaryConstraintEvidence.Metadata(
        areaName + "::" + nomination.getPointName() + "/nomination/" + periodIndex, areaName, nomination.getPointName(),
        ProcessBoundaryConstraintEvidence.Kind.NOMINATION, flowDirection, nomination.getBasis(), provenance, Double.NaN,
        Integer.toString(periodIndex), Integer.toString(periodIndex),
        ProcessBoundaryConstraintEvidence.ApplicabilityStatus.APPLICABLE, "nominated rate", null, null, periodIndex);
    BoundarySampleEvaluator sampler = new BoundarySampleEvaluator() {
      private static final long serialVersionUID = 1L;

      @Override
      public ProcessBoundaryConstraintEvidence.Sample evaluate(ProcessModel model) {
        return ProcessBoundaryConstraintEvidence.Sample.available(evaluator.applyAsDouble(model));
      }
    };
    return addBoundaryConstraint(name, metadata, sampler, ConstraintDefinition.Type.EQUALITY, target,
        Double.POSITIVE_INFINITY, tolerance, nomination.getUnit(), hard, penaltyWeight, residualScale);
  }

  /**
   * Adds a product-quality boundary using fixed limits from a network-quality specification.
   *
   * @param name constraint name
   * @param areaName process area containing the quality point
   * @param pointName named quality point
   * @param specification fixed metric, unit, limits, method, and reference basis
   * @param sampleEvaluator runtime quality sampler
   * @param hard whether unavailable or off-spec evidence is infeasible
   * @param penaltyWeight penalty multiplier
   * @param residualScale positive scale in the specification unit
   * @return this evaluator for chaining
   */
  public ProcessModelSimulationEvaluator addNetworkQualityConstraint(String name, String areaName, String pointName,
      NetworkQualityResult specification, BoundarySampleEvaluator sampleEvaluator, boolean hard, double penaltyWeight,
      double residualScale) {
    if (specification == null || sampleEvaluator == null) {
      throw new IllegalArgumentException("Quality specification and sampler are required");
    }
    Double lower = specification.getLowerLimit();
    Double upper = specification.getUpperLimit();
    ConstraintDefinition.Type type;
    double lowerValue = Double.NEGATIVE_INFINITY;
    double upperValue = Double.POSITIVE_INFINITY;
    if (lower != null && upper != null) {
      type = ConstraintDefinition.Type.RANGE;
      lowerValue = lower.doubleValue();
      upperValue = upper.doubleValue();
    } else if (lower != null) {
      type = ConstraintDefinition.Type.LOWER_BOUND;
      lowerValue = lower.doubleValue();
    } else if (upper != null) {
      type = ConstraintDefinition.Type.UPPER_BOUND;
      upperValue = upper.doubleValue();
    } else {
      throw new IllegalArgumentException("Quality specification must declare at least one limit");
    }
    String observable = specification.getMetricKey();
    if (specification.getAttributeName() != null && specification.getAttributeName().length() > 0) {
      observable += ":" + specification.getAttributeName();
    }
    String referenceJson = specification.getReference() == null ? null : specification.getReference().toJson();
    ProcessBoundaryConstraintEvidence.Metadata metadata = new ProcessBoundaryConstraintEvidence.Metadata(
        areaName + "::" + pointName + "/quality/" + observable, areaName, pointName,
        ProcessBoundaryConstraintEvidence.Kind.PRODUCT_QUALITY,
        ProcessBoundaryConstraintEvidence.FlowDirection.NOT_APPLICABLE, NetworkDecisionVariable.RateBasis.NONE,
        specification.getProvenance(), Double.NaN, null, null,
        ProcessBoundaryConstraintEvidence.ApplicabilityStatus.NOT_ASSESSED, observable, specification.getMethod(),
        referenceJson, -1);
    return addBoundaryConstraint(name, metadata, sampleEvaluator, type, lowerValue, upperValue, 0.0,
        specification.getUnit(), hard, penaltyWeight, residualScale);
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
   * Each enabled equipment capacity constraint becomes an upper-bound constraint where utilization must be less than or
   * equal to 1.0. Constraint names are area-qualified as {@code "area::equipment/constraint"} so bottlenecks can be
   * traced back to the full model.
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
        Map<String, CapacityConstraintRegistration> equipmentConstraints = getAllCapacityConstraints(registry,
            equipment);
        if (equipmentConstraints.isEmpty()) {
          continue;
        }
        for (Map.Entry<String, CapacityConstraintRegistration> entry : equipmentConstraints.entrySet()) {
          CapacityConstraintRegistration registration = entry.getValue();
          if (registration == null || registration.constraint == null || !registration.constraint.isEnabled()) {
            continue;
          }
          addCapacityConstraint(areaName, equipment, entry.getKey(), registration);
        }
      }
    }
    return this;
  }

  /**
   * Frozen registration of one discovered equipment capacity constraint.
   */
  private static final class CapacityConstraintRegistration {
    /** Installed constraint object sampled after each completed process run. */
    private final CapacityConstraint constraint;

    /** Whether the constraint came directly from equipment or from a registered strategy. */
    private final InstalledEquipmentCapacityEvidence.ConstraintOrigin origin;

    /** Creates one discovered registration. */
    private CapacityConstraintRegistration(CapacityConstraint constraint,
        InstalledEquipmentCapacityEvidence.ConstraintOrigin origin) {
      this.constraint = constraint;
      this.origin = origin;
    }
  }

  /**
   * Gets explicit and strategy-generated capacity constraints for equipment.
   *
   * <p>
   * Direct equipment constraints retain precedence over a strategy row with the same name.
   * </p>
   *
   * @param registry capacity strategy registry
   * @param equipment equipment to inspect
   * @return merged registration map in deterministic discovery order
   */
  private Map<String, CapacityConstraintRegistration> getAllCapacityConstraints(
      EquipmentCapacityStrategyRegistry registry, ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraintRegistration> equipmentConstraints = new LinkedHashMap<String, CapacityConstraintRegistration>();

    if (includeStrategyCapacityConstraints) {
      EquipmentCapacityStrategy strategy = registry.findStrategy(equipment);
      if (strategy != null) {
        Map<String, CapacityConstraint> strategyConstraints = strategy.getConstraints(equipment);
        if (strategyConstraints != null) {
          for (Map.Entry<String, CapacityConstraint> entry : strategyConstraints.entrySet()) {
            equipmentConstraints.put(entry.getKey(), new CapacityConstraintRegistration(entry.getValue(),
                InstalledEquipmentCapacityEvidence.ConstraintOrigin.STRATEGY));
          }
        }
      }
    }

    Map<String, CapacityConstraint> directConstraints = equipment.getCapacityConstraints();
    if (directConstraints != null) {
      for (Map.Entry<String, CapacityConstraint> entry : directConstraints.entrySet()) {
        equipmentConstraints.put(entry.getKey(), new CapacityConstraintRegistration(entry.getValue(),
            InstalledEquipmentCapacityEvidence.ConstraintOrigin.DIRECT));
      }
    }

    return equipmentConstraints;
  }

  /**
   * Adds a single capacity constraint definition.
   *
   * @param areaName process area name
   * @param equipment equipment containing the installed constraint
   * @param equipmentConstraintName equipment constraint name
   * @param registration discovered capacity constraint and origin
   */
  private void addCapacityConstraint(String areaName, ProcessEquipmentInterface equipment,
      String equipmentConstraintName, CapacityConstraintRegistration registration) {
    CapacityConstraint capacityConstraint = registration.constraint;
    String constraintName = areaName + ProcessAutomation.AREA_SEPARATOR + equipment.getName() + "/"
        + equipmentConstraintName;
    if (hasConstraint(constraintName)) {
      return;
    }

    final CapacityConstraint capturedCapacityConstraint = capacityConstraint;
    ConstraintDefinition definition = new ConstraintDefinition();
    definition.setName(constraintName);
    definition.setUnit("1");
    definition.setType(ConstraintDefinition.Type.UPPER_BOUND);
    definition.setUpperBound(1.0);
    definition.setEvaluator(new ToDoubleFunction<ProcessModel>() {
      /** {@inheritDoc} */
      @Override
      public double applyAsDouble(ProcessModel ignoredModel) {
        return capturedCapacityConstraint.getUtilization();
      }
    });
    boolean hardConstraint = capacityConstraint.getSeverity() == CapacityConstraint.ConstraintSeverity.CRITICAL
        || capacityConstraint.getSeverity() == CapacityConstraint.ConstraintSeverity.HARD;
    definition.setHard(hardConstraint);
    definition.setCapacityMetadata(areaName, equipment.getName(), equipmentConstraintName,
        equipment.getClass().getName(), safeReferenceDesignation(equipment), registration.origin, capacityConstraint);
    constraints.add(definition);
  }

  /** Returns the equipment reference designation without allowing metadata failure to block optimization. */
  private static String safeReferenceDesignation(ProcessEquipmentInterface equipment) {
    try {
      String designation = equipment.getReferenceDesignationString();
      return designation == null ? "" : designation;
    } catch (RuntimeException exception) {
      return "";
    }
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
      bounds.add(new double[] { parameter.getLowerBound(), parameter.getUpperBound() });
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
   * <p>
   * Each registered objective and constraint callback is sampled exactly once after the model run. Raw and
   * sign-adjusted objectives, and constraint values, margins, feasibility, and penalties, are derived from those same
   * samples.
   * </p>
   *
   * @param parameterValues parameter vector with length equal to {@link #getParameterCount()}
   * @return complete evaluation result
   */
  public EvaluationResult evaluate(double[] parameterValues) {
    ensureProcessModel();
    if (parameterValues == null || parameterValues.length != parameters.size()) {
      throw new IllegalArgumentException(
          "Parameter array length (" + (parameterValues == null ? "null" : Integer.toString(parameterValues.length))
              + ") must match parameter count (" + parameters.size() + ")");
    }
    for (int parameterIndex = 0; parameterIndex < parameterValues.length; parameterIndex++) {
      if (!Double.isFinite(parameterValues[parameterIndex])) {
        throw new IllegalArgumentException(
            "Parameter " + parameterIndex + " must be finite, but was " + parameterValues[parameterIndex]);
      }
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
      List<String> invalidSamples = new ArrayList<String>();
      for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
        ObjectiveDefinition objective = objectives.get(objectiveIndex);
        rawObjectiveValues[objectiveIndex] = objective.evaluateRaw(processModel);
        objectiveValues[objectiveIndex] = objective.toMinimizerValue(rawObjectiveValues[objectiveIndex]);
        if (!Double.isFinite(rawObjectiveValues[objectiveIndex]) || !Double.isFinite(objectiveValues[objectiveIndex])) {
          invalidSamples.add("Non-finite objective '" + objective.getName() + "'");
        }
      }
      result.setObjectives(objectiveValues);
      result.setObjectivesRaw(rawObjectiveValues);

      double[] constraintValues = new double[constraints.size()];
      double[] margins = new double[constraints.size()];
      List<ProcessBoundaryConstraintEvidence> boundaryEvidence = new ArrayList<ProcessBoundaryConstraintEvidence>();
      List<InstalledEquipmentCapacityEvidence> installedCapacityEvidence = new ArrayList<InstalledEquipmentCapacityEvidence>(
          snapshotInstalledEquipmentCapacityEvidence(processModel));
      Map<String, InstalledEquipmentCapacityEvidence> installedCapacityByIdentity = new LinkedHashMap<String, InstalledEquipmentCapacityEvidence>();
      for (InstalledEquipmentCapacityEvidence evidence : installedCapacityEvidence) {
        installedCapacityByIdentity.put(evidence.getQualifiedConstraintName(), evidence);
      }
      double penaltySum = 0.0;
      boolean feasible = processModel.isModelConverged();
      for (int constraintIndex = 0; constraintIndex < constraints.size(); constraintIndex++) {
        ConstraintDefinition constraint = constraints.get(constraintIndex);
        ProcessBoundaryConstraintEvidence evaluatedBoundary = null;
        if (constraint.isBoundaryConstraint()) {
          ProcessBoundaryConstraintEvidence.Sample sample = constraint.evaluateBoundarySample(processModel);
          evaluatedBoundary = new ProcessBoundaryConstraintEvidence(constraint.getBoundaryMetadata(), constraint,
              sample, constraint.getBoundaryResidualScale());
          boundaryEvidence.add(evaluatedBoundary);
          constraintValues[constraintIndex] = evaluatedBoundary.getSampledValue();
          margins[constraintIndex] = evaluatedBoundary.getSignedMargin();
          if (!evaluatedBoundary.isCalculable()) {
            penaltySum += constraint.getPenaltyWeight();
            if (constraint.isHard()) {
              feasible = false;
            }
            continue;
          }
        }
        InstalledEquipmentCapacityEvidence capacityEvidence = null;
        if (constraint.isCapacityConstraint() && "1".equals(constraint.getUnit())) {
          capacityEvidence = installedCapacityByIdentity.get(constraint.getName());
          if (capacityEvidence == null) {
            capacityEvidence = snapshotRegisteredCapacityConstraint(constraint);
            if (capacityEvidence != null) {
              installedCapacityEvidence.add(capacityEvidence);
              installedCapacityByIdentity.put(capacityEvidence.getQualifiedConstraintName(), capacityEvidence);
            }
          }
        }
        if (evaluatedBoundary != null) {
          // Value and margin were derived from the single structured sample above.
        } else if (capacityEvidence == null) {
          constraintValues[constraintIndex] = constraint.evaluate(processModel);
        } else {
          constraintValues[constraintIndex] = capacityEvidence.getNormalizedUtilization();
        }
        if (evaluatedBoundary == null) {
          margins[constraintIndex] = constraint.marginFromValue(constraintValues[constraintIndex]);
        }
        if (!Double.isFinite(constraintValues[constraintIndex]) || !Double.isFinite(margins[constraintIndex])) {
          margins[constraintIndex] = Double.NEGATIVE_INFINITY;
          invalidSamples.add("Non-finite constraint '" + constraint.getName() + "'");
          feasible = false;
          continue;
        }
        if (margins[constraintIndex] < 0.0) {
          if (evaluatedBoundary == null) {
            penaltySum += constraint.penaltyFromMargin(margins[constraintIndex]);
          } else {
            double scaledViolation = evaluatedBoundary.getScaledViolation();
            penaltySum += constraint.getPenaltyWeight() * scaledViolation * scaledViolation;
          }
          if (constraint.isHard()) {
            feasible = false;
          }
        }
      }
      sortInstalledCapacityEvidence(installedCapacityEvidence);
      result.setConstraintValues(constraintValues);
      result.setConstraintMargins(margins);
      result.setPenaltySum(penaltySum);
      result.setInstalledEquipmentCapacityEvidence(installedCapacityEvidence);
      result.setProcessBoundaryConstraintEvidence(boundaryEvidence);
      List<BottleneckStatus> rankedCapacityConstraints = toBottleneckStatuses(installedCapacityEvidence);
      result.setRankedCapacityConstraints(rankedCapacityConstraints);
      result.setActiveBottleneck(selectActiveBottleneck(rankedCapacityConstraints));
      if (!invalidSamples.isEmpty()) {
        feasible = false;
        penaltySum = INVALID_CANDIDATE_PENALTY;
        result.setPenaltySum(penaltySum);
        result.setErrorMessage(String.join("; ", invalidSamples));
      }
      result.setFeasible(feasible);
    } catch (Exception exception) {
      logger.warn("ProcessModel evaluation failed: " + exception.getMessage());
      result.setSimulationConverged(false);
      result.setFeasible(false);
      result.setErrorMessage(exception.getMessage());
      result.setPenaltySum(INVALID_CANDIDATE_PENALTY);
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
   * Selects the leading legacy-eligible bottleneck from a ranked snapshot.
   *
   * <p>
   * Preserve the historical {@link #findActiveBottleneck(ProcessModel)} threshold: utilizations at or below
   * {@code -1.0}, including negative infinity, are retained in the diagnostic ranking but are not exposed as the active
   * bottleneck.
   * </p>
   *
   * @param rankedCapacityConstraints ranked capacity snapshots
   * @return leading eligible bottleneck, or {@link BottleneckStatus#none()} when unavailable
   */
  private BottleneckStatus selectActiveBottleneck(List<BottleneckStatus> rankedCapacityConstraints) {
    if (rankedCapacityConstraints == null) {
      return BottleneckStatus.none();
    }
    for (BottleneckStatus bottleneck : rankedCapacityConstraints) {
      if (bottleneck != null && bottleneck.getUtilization() > -1.0) {
        return bottleneck;
      }
    }
    return BottleneckStatus.none();
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
      double value = parameter.isClampToBounds() ? parameter.clamp(parameterValues[parameterIndex])
          : parameterValues[parameterIndex];
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
    return selectActiveBottleneck(rankCapacityConstraints(model));
  }

  /**
   * Snapshots and ranks all enabled capacity constraints across the process model.
   *
   * <p>
   * Ranking is by descending utilization only. The sort is stable, so equal-utilization constraints preserve process
   * area, equipment, and constraint registration order. Evidence confidence and applicability are retained as
   * diagnostics but never change order or feasibility. Undefined ({@code NaN}) utilizations remain visible at the end
   * of the ranking. Each dynamic value supplier is read exactly once.
   * </p>
   *
   * @param model process model in its current state
   * @return immutable utilization-ranked constraint snapshots, or an empty list when the model is null or has no
   * enabled capacity constraint
   */
  public List<BottleneckStatus> rankCapacityConstraints(ProcessModel model) {
    return toBottleneckStatuses(snapshotInstalledEquipmentCapacityEvidence(model));
  }

  /**
   * Samples, snapshots, and ranks every enabled installed-equipment capacity constraint in a live model.
   *
   * <p>
   * Each dynamic supplier is invoked exactly once. The immutable returned rows separate normalized values from physical
   * values and preserve deterministic area, equipment, and registration order for utilization ties.
   * </p>
   *
   * @param model process model in its current state
   * @return fresh immutable utilization-ranked installed-capacity evidence
   */
  public List<InstalledEquipmentCapacityEvidence> snapshotInstalledEquipmentCapacityEvidence(ProcessModel model) {
    if (model == null) {
      return Collections.emptyList();
    }
    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    List<InstalledEquipmentCapacityEvidence> evidence = new ArrayList<InstalledEquipmentCapacityEvidence>();
    for (String areaName : model.getProcessSystemNames()) {
      ProcessSystem area = model.get(areaName);
      if (area == null) {
        continue;
      }
      for (ProcessEquipmentInterface equipment : area.getUnitOperations()) {
        Map<String, CapacityConstraintRegistration> equipmentConstraints = getAllCapacityConstraints(registry,
            equipment);
        for (Map.Entry<String, CapacityConstraintRegistration> entry : equipmentConstraints.entrySet()) {
          CapacityConstraintRegistration registration = entry.getValue();
          if (registration == null || registration.constraint == null || !registration.constraint.isEnabled()) {
            continue;
          }
          CapacityConstraint constraint = registration.constraint;
          double currentValue = constraint.getCurrentValue();
          evidence
              .add(new InstalledEquipmentCapacityEvidence(areaName, equipment.getName(), equipment.getClass().getName(),
                  safeReferenceDesignation(equipment), entry.getKey(), registration.origin, constraint, currentValue));
        }
      }
    }
    sortInstalledCapacityEvidence(evidence);
    return Collections.unmodifiableList(new ArrayList<InstalledEquipmentCapacityEvidence>(evidence));
  }

  /** Samples one registered installed-capacity constraint exactly once. */
  private InstalledEquipmentCapacityEvidence snapshotRegisteredCapacityConstraint(ConstraintDefinition definition) {
    CapacityConstraint constraint = definition.getCapturedCapacityConstraint();
    if (!definition.isCapacityConstraint() || constraint == null || !constraint.isEnabled()) {
      return null;
    }
    double currentValue = constraint.getCurrentValue();
    return new InstalledEquipmentCapacityEvidence(definition.getAreaName(), definition.getEquipmentName(),
        definition.getCapacityEquipmentClassName(), definition.getCapacityReferenceDesignation(),
        definition.getEquipmentConstraintName(), definition.getCapacityConstraintOrigin(), constraint, currentValue);
  }

  /** Stable descending-utilization ordering with undefined utilization retained last. */
  private static void sortInstalledCapacityEvidence(List<InstalledEquipmentCapacityEvidence> evidence) {
    Collections.sort(evidence, new Comparator<InstalledEquipmentCapacityEvidence>() {
      /** {@inheritDoc} */
      @Override
      public int compare(InstalledEquipmentCapacityEvidence first, InstalledEquipmentCapacityEvidence second) {
        if (Double.isNaN(first.getNormalizedUtilization())) {
          return Double.isNaN(second.getNormalizedUtilization()) ? 0 : 1;
        }
        if (Double.isNaN(second.getNormalizedUtilization())) {
          return -1;
        }
        return Double.compare(second.getNormalizedUtilization(), first.getNormalizedUtilization());
      }
    });
  }

  /** Converts unit-safe evidence to the legacy bottleneck row without resampling. */
  private static List<BottleneckStatus> toBottleneckStatuses(List<InstalledEquipmentCapacityEvidence> evidence) {
    if (evidence == null || evidence.isEmpty()) {
      return Collections.emptyList();
    }
    List<BottleneckStatus> statuses = new ArrayList<BottleneckStatus>();
    for (InstalledEquipmentCapacityEvidence snapshot : evidence) {
      statuses
          .add(new BottleneckStatus(snapshot.getAreaName(), snapshot.getEquipmentName(), snapshot.getConstraintName(),
              snapshot.getNormalizedUtilization(), snapshot.getCurrentValue(), snapshot.getApplicableLimit(),
              snapshot.isMinimumConstraint(), snapshot.getDataSource(), snapshot.hasConfidence(),
              snapshot.getConfidence(), snapshot.hasValidityRange(), snapshot.getValidityMinimum(),
              snapshot.getValidityMaximum(), snapshot.getPhysicalUnit(), snapshot.isFeasible()));
    }
    return Collections.unmodifiableList(statuses);
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

  /** Internal process evaluation at one signed parameter perturbation. */
  private static final class PerturbedEvaluation {
    /** Signed step from the bounded base point. */
    private final double signedStep;

    /** Process evaluation result. */
    private final EvaluationResult result;

    /** Public immutable evidence. */
    private final SensitivityPerturbation evidence;

    /** Creates one internal perturbation record. */
    private PerturbedEvaluation(double signedStep, double parameterValue, EvaluationResult result) {
      this.signedStep = signedStep;
      this.result = result;
      this.evidence = new SensitivityPerturbation(signedStep, parameterValue, result);
    }
  }

  /**
   * Estimates an objective gradient and constraint-margin Jacobian with step-halving quality evidence.
   *
   * <p>
   * The configured finite-difference step is used as the coarse step and is halved once for the returned derivative.
   * Objective and constraint sensitivities share the same base and perturbed process evaluations. Bounds determine the
   * actual central, forward, backward, or fixed stencil independently for every parameter. The result records the
   * actual steps, convergence, hard-constraint feasibility, evaluation errors, and scale-independent disagreement
   * between the coarse and fine derivatives. Immutable parameter, selected-objective, and constraint snapshots bind
   * every derivative column and row to the base-point engineering identity and units.
   * </p>
   *
   * <p>
   * Step-halving agreement only checks local numerical consistency. It does not establish differentiability across
   * equipment/control regime changes and must not be interpreted as a Lagrange multiplier, shadow price, process-safety
   * approval, or validity outside the sampled operating points.
   * </p>
   *
   * @param parameterValues parameter vector
   * @return primary-objective gradient, constraint Jacobian, and immutable quality evidence
   */
  public SensitivityQualityResult estimateSensitivitiesWithQuality(double[] parameterValues) {
    return estimateSensitivitiesWithQuality(parameterValues, 0);
  }

  /**
   * Estimates one objective gradient and constraint-margin Jacobian with step-halving quality evidence.
   *
   * @param parameterValues parameter vector
   * @param objectiveIndex registered objective index
   * @return fine-step derivatives and immutable quality evidence
   */
  public SensitivityQualityResult estimateSensitivitiesWithQuality(double[] parameterValues, int objectiveIndex) {
    ensureProcessModel();
    if (parameterValues == null || parameterValues.length != parameters.size()) {
      throw new IllegalArgumentException(
          "Parameter array length (" + (parameterValues == null ? "null" : Integer.toString(parameterValues.length))
              + ") must match parameter count (" + parameters.size() + ")");
    }
    if (objectiveIndex < 0 || objectiveIndex >= objectives.size()) {
      throw new IllegalArgumentException("Objective index must be between zero and " + (objectives.size() - 1));
    }

    double[] boundedValues = getBoundedParameterValues(parameterValues);
    EvaluationResult baseResult = evaluate(boundedValues);
    double[] gradient = new double[parameters.size()];
    double[][] jacobian = new double[constraints.size()][parameters.size()];
    List<ParameterSensitivityQuality> quality = new ArrayList<ParameterSensitivityQuality>();

    for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
      ParameterDefinition parameter = parameters.get(parameterIndex);
      double baseParameterValue = boundedValues[parameterIndex];
      double requestedStep = getRequestedFiniteDifferenceStep(baseParameterValue);
      double forwardAvailable = Math.max(0.0, parameter.getUpperBound() - baseParameterValue);
      double backwardAvailable = Math.max(0.0, baseParameterValue - parameter.getLowerBound());
      AppliedFiniteDifferenceStencil stencil;
      double coarseStep;

      if (finiteDifferenceMethod == FiniteDifferenceMethod.CENTRAL && forwardAvailable > 0.0
          && backwardAvailable > 0.0) {
        stencil = AppliedFiniteDifferenceStencil.CENTRAL;
        coarseStep = Math.min(requestedStep, Math.min(forwardAvailable, backwardAvailable));
      } else if (forwardAvailable > 0.0) {
        stencil = AppliedFiniteDifferenceStencil.FORWARD;
        coarseStep = Math.min(requestedStep, forwardAvailable);
      } else if (backwardAvailable > 0.0) {
        stencil = AppliedFiniteDifferenceStencil.BACKWARD;
        coarseStep = Math.min(requestedStep, backwardAvailable);
      } else {
        stencil = AppliedFiniteDifferenceStencil.FIXED;
        coarseStep = 0.0;
      }

      double fineStep = coarseStep / 2.0;
      List<SensitivityPerturbation> perturbations = new ArrayList<SensitivityPerturbation>();
      PerturbedEvaluation coarsePositive = null;
      PerturbedEvaluation coarseNegative = null;
      PerturbedEvaluation finePositive = null;
      PerturbedEvaluation fineNegative = null;

      if (stencil == AppliedFiniteDifferenceStencil.CENTRAL || stencil == AppliedFiniteDifferenceStencil.FORWARD) {
        coarsePositive = evaluatePerturbation(boundedValues, parameterIndex, coarseStep);
        addPerturbationEvidence(perturbations, coarsePositive);
        finePositive = evaluatePerturbation(boundedValues, parameterIndex, fineStep);
        addPerturbationEvidence(perturbations, finePositive);
      }
      if (stencil == AppliedFiniteDifferenceStencil.CENTRAL || stencil == AppliedFiniteDifferenceStencil.BACKWARD) {
        coarseNegative = evaluatePerturbation(boundedValues, parameterIndex, -coarseStep);
        addPerturbationEvidence(perturbations, coarseNegative);
        fineNegative = evaluatePerturbation(boundedValues, parameterIndex, -fineStep);
        addPerturbationEvidence(perturbations, fineNegative);
      }

      double coarseObjectiveDerivative = calculateObjectiveDerivative(baseResult, coarsePositive, coarseNegative,
          stencil, coarseStep, objectiveIndex);
      double fineObjectiveDerivative = calculateObjectiveDerivative(baseResult, finePositive, fineNegative, stencil,
          fineStep, objectiveIndex);
      gradient[parameterIndex] = fineObjectiveDerivative;
      double objectiveDisagreement = relativeDerivativeDisagreement(coarseObjectiveDerivative, fineObjectiveDerivative);
      double[] constraintDisagreement = new double[constraints.size()];

      for (int constraintIndex = 0; constraintIndex < constraints.size(); constraintIndex++) {
        double coarseConstraintDerivative = calculateConstraintDerivative(baseResult, coarsePositive, coarseNegative,
            stencil, coarseStep, constraintIndex);
        double fineConstraintDerivative = calculateConstraintDerivative(baseResult, finePositive, fineNegative, stencil,
            fineStep, constraintIndex);
        jacobian[constraintIndex][parameterIndex] = fineConstraintDerivative;
        constraintDisagreement[constraintIndex] = relativeDerivativeDisagreement(coarseConstraintDerivative,
            fineConstraintDerivative);
      }

      quality.add(new ParameterSensitivityQuality(parameter, stencil, requestedStep, coarseStep, fineStep,
          objectiveDisagreement, constraintDisagreement, baseResult, perturbations));
    }
    return new SensitivityQualityResult(objectiveIndex, gradient, jacobian, quality, parameters,
        objectives.get(objectiveIndex), constraints, baseResult);
  }

  /** Evaluates one signed perturbation, returning null when the step is not representable. */
  private PerturbedEvaluation evaluatePerturbation(double[] baseValues, int parameterIndex,
      double requestedSignedStep) {
    double[] shiftedValues = Arrays.copyOf(baseValues, baseValues.length);
    shiftedValues[parameterIndex] += requestedSignedStep;
    double actualSignedStep = shiftedValues[parameterIndex] - baseValues[parameterIndex];
    if (actualSignedStep == 0.0) {
      return null;
    }
    EvaluationResult result = evaluate(shiftedValues);
    return new PerturbedEvaluation(actualSignedStep, shiftedValues[parameterIndex], result);
  }

  /** Adds public perturbation evidence when a representable evaluation was made. */
  private static void addPerturbationEvidence(List<SensitivityPerturbation> perturbations,
      PerturbedEvaluation evaluation) {
    if (evaluation != null) {
      perturbations.add(evaluation.evidence);
    }
  }

  /** Calculates an objective derivative from one bounded stencil. */
  private static double calculateObjectiveDerivative(EvaluationResult baseResult, PerturbedEvaluation positive,
      PerturbedEvaluation negative, AppliedFiniteDifferenceStencil stencil, double nominalStep, int objectiveIndex) {
    if (stencil == AppliedFiniteDifferenceStencil.FIXED) {
      return 0.0;
    }
    if (nominalStep <= 0.0) {
      return Double.NaN;
    }
    if (stencil == AppliedFiniteDifferenceStencil.CENTRAL) {
      if (positive == null || negative == null) {
        return Double.NaN;
      }
      double denominator = positive.signedStep - negative.signedStep;
      return denominator == 0.0 ? Double.NaN
          : (positive.result.getObjectives()[objectiveIndex] - negative.result.getObjectives()[objectiveIndex])
              / denominator;
    }
    if (stencil == AppliedFiniteDifferenceStencil.FORWARD) {
      return positive == null ? Double.NaN
          : (positive.result.getObjectives()[objectiveIndex] - baseResult.getObjectives()[objectiveIndex])
              / positive.signedStep;
    }
    return negative == null ? Double.NaN
        : (negative.result.getObjectives()[objectiveIndex] - baseResult.getObjectives()[objectiveIndex])
            / negative.signedStep;
  }

  /** Calculates a constraint-margin derivative from one bounded stencil. */
  private static double calculateConstraintDerivative(EvaluationResult baseResult, PerturbedEvaluation positive,
      PerturbedEvaluation negative, AppliedFiniteDifferenceStencil stencil, double nominalStep, int constraintIndex) {
    if (stencil == AppliedFiniteDifferenceStencil.FIXED) {
      return 0.0;
    }
    if (nominalStep <= 0.0) {
      return Double.NaN;
    }
    if (stencil == AppliedFiniteDifferenceStencil.CENTRAL) {
      if (positive == null || negative == null) {
        return Double.NaN;
      }
      double denominator = positive.signedStep - negative.signedStep;
      return denominator == 0.0 ? Double.NaN
          : (positive.result.getConstraintMargins()[constraintIndex]
              - negative.result.getConstraintMargins()[constraintIndex]) / denominator;
    }
    if (stencil == AppliedFiniteDifferenceStencil.FORWARD) {
      return positive == null ? Double.NaN
          : (positive.result.getConstraintMargins()[constraintIndex]
              - baseResult.getConstraintMargins()[constraintIndex]) / positive.signedStep;
    }
    return negative == null ? Double.NaN
        : (negative.result.getConstraintMargins()[constraintIndex] - baseResult.getConstraintMargins()[constraintIndex])
            / negative.signedStep;
  }

  /** Calculates scale-independent disagreement between coarse and fine derivatives. */
  private static double relativeDerivativeDisagreement(double coarseDerivative, double fineDerivative) {
    if (!Double.isFinite(coarseDerivative) || !Double.isFinite(fineDerivative)) {
      return Double.NaN;
    }
    double scale = Math.max(Math.abs(coarseDerivative), Math.abs(fineDerivative));
    return scale == 0.0 ? 0.0 : Math.abs(coarseDerivative - fineDerivative) / scale;
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
    double[] boundedValues = getBoundedParameterValues(parameterValues);
    for (int parameterIndex = 0; parameterIndex < parameterValues.length; parameterIndex++) {
      ParameterDefinition parameter = parameters.get(parameterIndex);
      double requestedStep = getRequestedFiniteDifferenceStep(boundedValues[parameterIndex]);
      double forwardStep = Math.min(requestedStep, parameter.getUpperBound() - boundedValues[parameterIndex]);
      double backwardStep = Math.min(requestedStep, boundedValues[parameterIndex] - parameter.getLowerBound());

      if (finiteDifferenceMethod == FiniteDifferenceMethod.CENTRAL && forwardStep > 0.0 && backwardStep > 0.0) {
        double centralStep = Math.min(forwardStep, backwardStep);
        double[] upperValues = Arrays.copyOf(boundedValues, boundedValues.length);
        double[] lowerValues = Arrays.copyOf(boundedValues, boundedValues.length);
        upperValues[parameterIndex] += centralStep;
        lowerValues[parameterIndex] -= centralStep;
        double upperValue = evaluate(upperValues).getObjectives()[objectiveIndex];
        double lowerValue = evaluate(lowerValues).getObjectives()[objectiveIndex];
        gradient[parameterIndex] = (upperValue - lowerValue) / (2.0 * centralStep);
      } else if (forwardStep > 0.0) {
        double[] shiftedValues = Arrays.copyOf(boundedValues, boundedValues.length);
        shiftedValues[parameterIndex] += forwardStep;
        double shiftedValue = evaluate(shiftedValues).getObjectives()[objectiveIndex];
        gradient[parameterIndex] = (shiftedValue - baseValue) / forwardStep;
      } else if (backwardStep > 0.0) {
        double[] shiftedValues = Arrays.copyOf(boundedValues, boundedValues.length);
        shiftedValues[parameterIndex] -= backwardStep;
        double shiftedValue = evaluate(shiftedValues).getObjectives()[objectiveIndex];
        gradient[parameterIndex] = (baseValue - shiftedValue) / backwardStep;
      } else {
        gradient[parameterIndex] = 0.0;
      }
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
    double[] boundedValues = getBoundedParameterValues(parameterValues);
    for (int parameterIndex = 0; parameterIndex < parameterValues.length; parameterIndex++) {
      ParameterDefinition parameter = parameters.get(parameterIndex);
      double requestedStep = getRequestedFiniteDifferenceStep(boundedValues[parameterIndex]);
      double forwardStep = Math.min(requestedStep, parameter.getUpperBound() - boundedValues[parameterIndex]);
      double backwardStep = Math.min(requestedStep, boundedValues[parameterIndex] - parameter.getLowerBound());

      if (finiteDifferenceMethod == FiniteDifferenceMethod.CENTRAL && forwardStep > 0.0 && backwardStep > 0.0) {
        double centralStep = Math.min(forwardStep, backwardStep);
        double[] upperValues = Arrays.copyOf(boundedValues, boundedValues.length);
        double[] lowerValues = Arrays.copyOf(boundedValues, boundedValues.length);
        upperValues[parameterIndex] += centralStep;
        lowerValues[parameterIndex] -= centralStep;
        double[] upperMargins = evaluate(upperValues).getConstraintMargins();
        double[] lowerMargins = evaluate(lowerValues).getConstraintMargins();
        for (int constraintIndex = 0; constraintIndex < constraints.size(); constraintIndex++) {
          jacobian[constraintIndex][parameterIndex] = (upperMargins[constraintIndex] - lowerMargins[constraintIndex])
              / (2.0 * centralStep);
        }
      } else if (forwardStep > 0.0) {
        double[] shiftedValues = Arrays.copyOf(boundedValues, boundedValues.length);
        shiftedValues[parameterIndex] += forwardStep;
        double[] shiftedMargins = evaluate(shiftedValues).getConstraintMargins();
        for (int constraintIndex = 0; constraintIndex < constraints.size(); constraintIndex++) {
          jacobian[constraintIndex][parameterIndex] = (shiftedMargins[constraintIndex] - baseMargins[constraintIndex])
              / forwardStep;
        }
      } else if (backwardStep > 0.0) {
        double[] shiftedValues = Arrays.copyOf(boundedValues, boundedValues.length);
        shiftedValues[parameterIndex] -= backwardStep;
        double[] shiftedMargins = evaluate(shiftedValues).getConstraintMargins();
        for (int constraintIndex = 0; constraintIndex < constraints.size(); constraintIndex++) {
          jacobian[constraintIndex][parameterIndex] = (baseMargins[constraintIndex] - shiftedMargins[constraintIndex])
              / backwardStep;
        }
      }
    }
    return jacobian;
  }

  /**
   * Returns a parameter vector limited to the declared optimization bounds.
   *
   * @param parameterValues requested parameter vector
   * @return defensive bounded parameter vector
   */
  private double[] getBoundedParameterValues(double[] parameterValues) {
    double[] boundedValues = Arrays.copyOf(parameterValues, parameterValues.length);
    for (int parameterIndex = 0; parameterIndex < boundedValues.length; parameterIndex++) {
      ParameterDefinition parameter = parameters.get(parameterIndex);
      if (parameter.isClampToBounds()) {
        boundedValues[parameterIndex] = parameter.clamp(boundedValues[parameterIndex]);
      }
    }
    return boundedValues;
  }

  /**
   * Calculates the requested positive perturbation magnitude.
   *
   * @param parameterValue bounded parameter value
   * @return requested positive step before applying parameter bounds
   */
  private double getRequestedFiniteDifferenceStep(double parameterValue) {
    return useRelativeStep ? finiteDifferenceStep * Math.max(Math.abs(parameterValue), 1.0) : finiteDifferenceStep;
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
    if (!Double.isFinite(finiteDifferenceStep) || finiteDifferenceStep <= 0.0) {
      throw new IllegalArgumentException("Finite-difference step must be finite and greater than zero");
    }
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
   * Gets the finite-difference stencil.
   *
   * @return configured finite-difference method
   */
  public FiniteDifferenceMethod getFiniteDifferenceMethod() {
    return finiteDifferenceMethod;
  }

  /**
   * Sets the finite-difference stencil.
   *
   * <p>
   * {@link FiniteDifferenceMethod#FORWARD} retains the historical one-perturbation cost per parameter. Central
   * differences use symmetric in-bounds perturbations at interior points and fall back to a one-sided difference at an
   * active bound.
   * </p>
   *
   * @param finiteDifferenceMethod finite-difference method, not null
   */
  public void setFiniteDifferenceMethod(FiniteDifferenceMethod finiteDifferenceMethod) {
    if (finiteDifferenceMethod == null) {
      throw new IllegalArgumentException("Finite-difference method cannot be null");
    }
    this.finiteDifferenceMethod = finiteDifferenceMethod;
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
   * Direct constraints attached to equipment are always included. Disable this option for installed capacity studies
   * where only explicit fixed-equipment limits from design data should determine feasibility and active bottleneck
   * reporting.
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
    definition.put("areas", processModel == null ? new ArrayList<String>() : processModel.getProcessSystemNames());

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
