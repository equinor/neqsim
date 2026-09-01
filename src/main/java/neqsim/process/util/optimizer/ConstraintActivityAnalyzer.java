package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ConstraintSensitivityAssessment;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.SensitivityConstraintSnapshot;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.SensitivityEvidenceFlag;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.SensitivityQualificationPolicy;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.SensitivityQualityResult;

/**
 * Builds dimensionless constraint-margin and local activity diagnostics from a qualified
 * {@link SensitivityQualityResult}.
 *
 * <p>
 * Every constraint requires an explicit positive reference value in the constraint's declared unit and a non-empty
 * provenance string. Results preserve constraint registration order and retain the complete unscaled sensitivity
 * assessment. The analyzer performs no process simulation and makes no optimizer-independent active-set,
 * KKT-multiplier, shadow-price, or engineering-approval claim.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ConstraintActivityAnalyzer {

  /** Conservative classification of one constraint at the sampled base point. */
  public enum ActivityStatus {
    /** Base convergence, evaluation, or margin evidence is unavailable. */
    UNAVAILABLE,
    /** The constraint has a negative base margin. */
    VIOLATED,
    /** The non-negative normalized margin is within the declared activity tolerance. */
    CANDIDATE_ACTIVE,
    /** The normalized margin is outside the declared activity tolerance. */
    INACTIVE,
    /** A soft constraint was excluded by policy. */
    EXCLUDED_SOFT
  }

  /**
   * Immutable reference scale tied to the exact identity of one constraint snapshot.
   *
   * <p>
   * The reference value has the same unit as the constraint. Identity includes bounds, hardness, type, and capacity
   * origin so a scale cannot silently be reused after the engineering constraint definition changes.
   * </p>
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class ConstraintScale implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Constraint registration index. */
    private final int constraintIndex;

    /** Constraint name. */
    private final String constraintName;

    /** Constraint type. */
    private final ProcessModelSimulationEvaluator.ConstraintDefinition.Type constraintType;

    /** Constraint unit and reference-value unit. */
    private final String unit;

    /** Hard/soft semantics. */
    private final boolean hard;

    /** Penalty weight for a violated soft constraint. */
    private final double penaltyWeight;

    /** Lower bound or equality target. */
    private final double lowerBound;

    /** Upper bound. */
    private final double upperBound;

    /** Equality tolerance. */
    private final double equalityTolerance;

    /** Whether the constraint originated from equipment capacity. */
    private final boolean capacityConstraint;

    /** Capacity-origin area. */
    private final String areaName;

    /** Capacity-origin equipment. */
    private final String equipmentName;

    /** Capacity-origin constraint. */
    private final String equipmentConstraintName;

    /** Positive engineering reference value. */
    private final double referenceValue;

    /** Human-readable reference-value provenance. */
    private final String provenance;

    /**
     * Creates a reference scale from an immutable constraint snapshot.
     *
     * @param snapshot exact constraint identity and base-state snapshot
     * @param referenceValue positive finite reference in the constraint unit
     * @param provenance non-empty source or engineering rationale for the reference
     * @return immutable constraint scale
     * @throws IllegalArgumentException if any argument is invalid or the constraint unit is absent
     */
    public static ConstraintScale fromSnapshot(SensitivityConstraintSnapshot snapshot, double referenceValue,
        String provenance) {
      if (snapshot == null) {
        throw new IllegalArgumentException("Constraint snapshot must not be null");
      }
      String unit = snapshot.getUnit();
      if (unit == null || unit.trim().isEmpty()) {
        throw new IllegalArgumentException(
            "Constraint " + snapshot.getName() + " requires a declared unit before scaling");
      }
      if (!Double.isFinite(referenceValue) || referenceValue <= 0.0) {
        throw new IllegalArgumentException("Constraint reference value must be finite and positive");
      }
      if (provenance == null || provenance.trim().isEmpty()) {
        throw new IllegalArgumentException("Constraint scale provenance must not be empty");
      }
      return new ConstraintScale(snapshot, referenceValue, provenance.trim());
    }

    /**
     * Captures one scale and its exact constraint identity.
     *
     * @param snapshot exact constraint snapshot
     * @param referenceValue positive reference value
     * @param provenance non-empty scale provenance
     */
    private ConstraintScale(SensitivityConstraintSnapshot snapshot, double referenceValue, String provenance) {
      this.constraintIndex = snapshot.getIndex();
      this.constraintName = snapshot.getName();
      this.constraintType = snapshot.getType();
      this.unit = snapshot.getUnit();
      this.hard = snapshot.isHard();
      this.penaltyWeight = snapshot.getPenaltyWeight();
      this.lowerBound = snapshot.getLowerBound();
      this.upperBound = snapshot.getUpperBound();
      this.equalityTolerance = snapshot.getEqualityTolerance();
      this.capacityConstraint = snapshot.isCapacityConstraint();
      this.areaName = snapshot.getAreaName();
      this.equipmentName = snapshot.getEquipmentName();
      this.equipmentConstraintName = snapshot.getEquipmentConstraintName();
      this.referenceValue = referenceValue;
      this.provenance = provenance;
    }

    /** @return constraint registration index */
    public int getConstraintIndex() {
      return constraintIndex;
    }

    /** @return constraint name */
    public String getConstraintName() {
      return constraintName;
    }

    /** @return constraint type */
    public ProcessModelSimulationEvaluator.ConstraintDefinition.Type getConstraintType() {
      return constraintType;
    }

    /** @return constraint and reference-value unit */
    public String getUnit() {
      return unit;
    }

    /** @return true when violation makes the evaluated point infeasible */
    public boolean isHard() {
      return hard;
    }

    /** @return penalty weight for a violated constraint */
    public double getPenaltyWeight() {
      return penaltyWeight;
    }

    /** @return lower bound or equality target in the constraint unit */
    public double getLowerBound() {
      return lowerBound;
    }

    /** @return upper bound in the constraint unit */
    public double getUpperBound() {
      return upperBound;
    }

    /** @return equality tolerance in the constraint unit */
    public double getEqualityTolerance() {
      return equalityTolerance;
    }

    /** @return true when the constraint originated from equipment capacity */
    public boolean isCapacityConstraint() {
      return capacityConstraint;
    }

    /** @return capacity-origin area, or null */
    public String getAreaName() {
      return areaName;
    }

    /** @return capacity-origin equipment, or null */
    public String getEquipmentName() {
      return equipmentName;
    }

    /** @return capacity-origin constraint, or null */
    public String getEquipmentConstraintName() {
      return equipmentConstraintName;
    }

    /** @return positive reference value in {@link #getUnit()} */
    public double getReferenceValue() {
      return referenceValue;
    }

    /** @return source or engineering rationale for the reference value */
    public String getProvenance() {
      return provenance;
    }

    /**
     * Checks that this scale still belongs to an exact constraint identity.
     *
     * @param snapshot constraint snapshot to compare
     * @return true when all identity and engineering-definition fields match
     */
    private boolean matches(SensitivityConstraintSnapshot snapshot) {
      return snapshot != null && constraintIndex == snapshot.getIndex()
          && safeEquals(constraintName, snapshot.getName()) && constraintType == snapshot.getType()
          && safeEquals(unit, snapshot.getUnit()) && hard == snapshot.isHard()
          && sameDouble(penaltyWeight, snapshot.getPenaltyWeight()) && sameDouble(lowerBound, snapshot.getLowerBound())
          && sameDouble(upperBound, snapshot.getUpperBound())
          && sameDouble(equalityTolerance, snapshot.getEqualityTolerance())
          && capacityConstraint == snapshot.isCapacityConstraint() && safeEquals(areaName, snapshot.getAreaName())
          && safeEquals(equipmentName, snapshot.getEquipmentName())
          && safeEquals(equipmentConstraintName, snapshot.getEquipmentConstraintName());
    }
  }

  /**
   * Immutable policy for candidate-active classification and sensitivity qualification.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class ActivityPolicy implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Maximum non-negative normalized margin classified as candidate active. */
    private final double activeNormalizedMarginTolerance;

    /** Evidence policy applied to every constraint/parameter derivative. */
    private final SensitivityQualificationPolicy sensitivityPolicy;

    /** Whether soft constraints are included in candidate activity diagnostics. */
    private final boolean includeSoftConstraints;

    /**
     * Creates an explicit activity policy.
     *
     * @param activeNormalizedMarginTolerance finite non-negative dimensionless tolerance
     * @param sensitivityPolicy explicit local derivative evidence policy
     * @param includeSoftConstraints whether soft constraints receive activity classifications
     * @throws IllegalArgumentException if the tolerance or sensitivity policy is invalid
     */
    public ActivityPolicy(double activeNormalizedMarginTolerance, SensitivityQualificationPolicy sensitivityPolicy,
        boolean includeSoftConstraints) {
      if (!Double.isFinite(activeNormalizedMarginTolerance) || activeNormalizedMarginTolerance < 0.0) {
        throw new IllegalArgumentException("Active normalized-margin tolerance must be finite and non-negative");
      }
      if (sensitivityPolicy == null) {
        throw new IllegalArgumentException("Sensitivity qualification policy must not be null");
      }
      this.activeNormalizedMarginTolerance = activeNormalizedMarginTolerance;
      this.sensitivityPolicy = sensitivityPolicy;
      this.includeSoftConstraints = includeSoftConstraints;
    }

    /**
     * Creates a policy for hard constraints only.
     *
     * @param activeNormalizedMarginTolerance finite non-negative dimensionless tolerance
     * @param sensitivityPolicy explicit local derivative evidence policy
     * @return immutable hard-constraint activity policy
     */
    public static ActivityPolicy hardConstraints(double activeNormalizedMarginTolerance,
        SensitivityQualificationPolicy sensitivityPolicy) {
      return new ActivityPolicy(activeNormalizedMarginTolerance, sensitivityPolicy, false);
    }

    /** @return dimensionless candidate-active margin tolerance */
    public double getActiveNormalizedMarginTolerance() {
      return activeNormalizedMarginTolerance;
    }

    /** @return retained local derivative qualification policy */
    public SensitivityQualificationPolicy getSensitivityPolicy() {
      return sensitivityPolicy;
    }

    /** @return true when soft constraints are included */
    public boolean areSoftConstraintsIncluded() {
      return includeSoftConstraints;
    }
  }

  /**
   * Immutable normalized derivative retaining its complete unscaled evidence assessment.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class ScaledSensitivity implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Complete unscaled identity and evidence assessment. */
    private final ConstraintSensitivityAssessment sensitivityAssessment;

    /** Constraint-margin derivative divided by the explicit reference value. */
    private final double normalizedMarginDerivative;

    /**
     * Creates one normalized derivative.
     *
     * @param sensitivityAssessment complete unscaled derivative assessment
     * @param referenceValue positive constraint reference value
     */
    private ScaledSensitivity(ConstraintSensitivityAssessment sensitivityAssessment, double referenceValue) {
      this.sensitivityAssessment = sensitivityAssessment;
      this.normalizedMarginDerivative = sensitivityAssessment.getMarginDerivative() / referenceValue;
    }

    /** @return complete unscaled sensitivity identity and evidence */
    public ConstraintSensitivityAssessment getSensitivityAssessment() {
      return sensitivityAssessment;
    }

    /**
     * Gets the normalized margin derivative.
     *
     * <p>
     * Call {@link #isUsable()} before consuming this value. A rejected derivative is retained for auditability but is
     * not qualified for an operating-action calculation.
     * </p>
     *
     * @return derivative of normalized margin with respect to the parameter
     */
    public double getNormalizedMarginDerivative() {
      return normalizedMarginDerivative;
    }

    /**
     * Gets the descriptive normalized derivative unit.
     *
     * @return one per parameter unit, or null when the parameter unit is absent
     */
    public String getNormalizedMarginDerivativeUnit() {
      String parameterUnit = sensitivityAssessment.getParameter().getUnit();
      if (parameterUnit == null || parameterUnit.trim().isEmpty()) {
        return null;
      }
      return "1 per " + parameterUnit;
    }

    /** @return true when the retained local derivative passed its evidence policy */
    public boolean isUsable() {
      return sensitivityAssessment.isAccepted();
    }

    /** @return immutable rejection reasons from the unscaled evidence assessment */
    public List<SensitivityEvidenceFlag> getRejectionReasons() {
      return sensitivityAssessment.getRejectionReasons();
    }
  }

  /**
   * Immutable dimensionless activity assessment for one constraint.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class ConstraintActivityAssessment implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Exact constraint identity and base state. */
    private final SensitivityConstraintSnapshot constraint;

    /** Exact engineering reference scale and provenance. */
    private final ConstraintScale scale;

    /** Explicit activity and derivative qualification policy. */
    private final ActivityPolicy policy;

    /** Dimensionless base margin. */
    private final double normalizedMargin;

    /** Conservative activity classification. */
    private final ActivityStatus status;

    /** Normalized parameter sensitivities in parameter registration order. */
    private final List<ScaledSensitivity> sensitivities;

    /** Human-readable scaling and classification diagnostics. */
    private final List<String> diagnostics;

    /**
     * Creates one immutable activity assessment.
     *
     * @param constraint exact constraint identity and base state
     * @param scale exact reference scale and provenance
     * @param policy retained activity policy
     * @param normalizedMargin dimensionless base margin
     * @param status conservative activity status
     * @param sensitivities normalized parameter sensitivities
     * @param diagnostics scaling and classification diagnostics
     */
    private ConstraintActivityAssessment(SensitivityConstraintSnapshot constraint, ConstraintScale scale,
        ActivityPolicy policy, double normalizedMargin, ActivityStatus status, List<ScaledSensitivity> sensitivities,
        List<String> diagnostics) {
      this.constraint = constraint;
      this.scale = scale;
      this.policy = policy;
      this.normalizedMargin = normalizedMargin;
      this.status = status;
      this.sensitivities = Collections.unmodifiableList(new ArrayList<ScaledSensitivity>(sensitivities));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** @return exact immutable constraint identity and base state */
    public SensitivityConstraintSnapshot getConstraint() {
      return constraint;
    }

    /** @return exact immutable reference scale and provenance */
    public ConstraintScale getScale() {
      return scale;
    }

    /** @return retained activity and sensitivity qualification policy */
    public ActivityPolicy getPolicy() {
      return policy;
    }

    /** @return base margin divided by the explicit reference value */
    public double getNormalizedMargin() {
      return normalizedMargin;
    }

    /** @return conservative activity classification */
    public ActivityStatus getStatus() {
      return status;
    }

    /** @return normalized sensitivities in parameter registration order */
    public List<ScaledSensitivity> getSensitivities() {
      return sensitivities;
    }

    /**
     * Gets normalized sensitivities accepted by the retained evidence policy.
     *
     * @return immutable accepted sensitivities in parameter registration order
     */
    public List<ScaledSensitivity> getUsableSensitivities() {
      List<ScaledSensitivity> usable = new ArrayList<ScaledSensitivity>();
      for (ScaledSensitivity sensitivity : sensitivities) {
        if (sensitivity.isUsable()) {
          usable.add(sensitivity);
        }
      }
      return Collections.unmodifiableList(usable);
    }

    /** @return immutable human-readable scaling and classification diagnostics */
    public List<String> getDiagnostics() {
      return diagnostics;
    }

    /** @return true only for a non-violated candidate-active classification */
    public boolean isCandidateActive() {
      return status == ActivityStatus.CANDIDATE_ACTIVE;
    }

    /** @return true when the base margin is negative */
    public boolean isViolated() {
      return status == ActivityStatus.VIOLATED;
    }
  }

  /** Utility class; instances are not required. */
  private ConstraintActivityAnalyzer() {
  }

  /**
   * Builds dimensionless activity and sensitivity diagnostics without rerunning the process model.
   *
   * <p>
   * Scales may be supplied in any order. Exactly one scale must match every constraint snapshot; outputs remain in
   * constraint registration order. A violated constraint is reported separately from a feasible candidate-active
   * constraint. Soft constraints are excluded unless the policy explicitly includes them.
   * </p>
   *
   * @param result immutable self-describing sensitivity result
   * @param scales exact reference scales, one per constraint
   * @param policy explicit activity and derivative qualification policy
   * @return immutable activity assessments in constraint registration order
   * @throws IllegalArgumentException if inputs, scale coverage, or scale identity are invalid
   */
  public static List<ConstraintActivityAssessment> assess(SensitivityQualityResult result, List<ConstraintScale> scales,
      ActivityPolicy policy) {
    if (result == null) {
      throw new IllegalArgumentException("Sensitivity quality result must not be null");
    }
    if (scales == null) {
      throw new IllegalArgumentException("Constraint scales must not be null");
    }
    if (policy == null) {
      throw new IllegalArgumentException("Constraint activity policy must not be null");
    }

    List<SensitivityConstraintSnapshot> constraints = result.getConstraintSnapshots();
    Map<Integer, ConstraintScale> scalesByIndex = indexAndValidateScales(constraints, scales);
    List<ConstraintSensitivityAssessment> qualified = result
        .assessConstraintSensitivities(policy.getSensitivityPolicy());
    Map<Integer, List<ConstraintSensitivityAssessment>> sensitivitiesByConstraint = new LinkedHashMap<Integer, List<ConstraintSensitivityAssessment>>();
    for (SensitivityConstraintSnapshot constraint : constraints) {
      sensitivitiesByConstraint.put(constraint.getIndex(), new ArrayList<ConstraintSensitivityAssessment>());
    }
    for (ConstraintSensitivityAssessment sensitivity : qualified) {
      sensitivitiesByConstraint.get(sensitivity.getConstraint().getIndex()).add(sensitivity);
    }

    List<ConstraintActivityAssessment> assessments = new ArrayList<ConstraintActivityAssessment>();
    for (SensitivityConstraintSnapshot constraint : constraints) {
      ConstraintScale scale = scalesByIndex.get(constraint.getIndex());
      double normalizedMargin = constraint.getBaseMargin() / scale.getReferenceValue();
      List<String> diagnostics = new ArrayList<String>();
      diagnostics.add("Constraint margin scaled by " + scale.getReferenceValue() + " " + scale.getUnit() + " from "
          + scale.getProvenance());
      ActivityStatus status = classify(result, constraint, normalizedMargin, policy, diagnostics);
      List<ScaledSensitivity> scaledSensitivities = new ArrayList<ScaledSensitivity>();
      for (ConstraintSensitivityAssessment sensitivity : sensitivitiesByConstraint.get(constraint.getIndex())) {
        scaledSensitivities.add(new ScaledSensitivity(sensitivity, scale.getReferenceValue()));
      }
      assessments.add(new ConstraintActivityAssessment(constraint, scale, policy, normalizedMargin, status,
          scaledSensitivities, diagnostics));
    }
    return Collections.unmodifiableList(assessments);
  }

  /**
   * Filters feasible candidate-active constraints without changing registration order.
   *
   * @param assessments complete activity assessments
   * @return immutable candidate-active subset
   * @throws IllegalArgumentException if assessments is null
   */
  public static List<ConstraintActivityAssessment> getCandidateActiveConstraints(
      List<ConstraintActivityAssessment> assessments) {
    return filterByStatus(assessments, ActivityStatus.CANDIDATE_ACTIVE);
  }

  /**
   * Filters violated constraints without changing registration order.
   *
   * @param assessments complete activity assessments
   * @return immutable violated subset
   * @throws IllegalArgumentException if assessments is null
   */
  public static List<ConstraintActivityAssessment> getViolatedConstraints(
      List<ConstraintActivityAssessment> assessments) {
    return filterByStatus(assessments, ActivityStatus.VIOLATED);
  }

  /**
   * Indexes scales and verifies complete exact-identity coverage.
   *
   * @param constraints immutable constraint snapshots
   * @param scales supplied scales
   * @return scales keyed by constraint index
   * @throws IllegalArgumentException for missing, duplicate, out-of-range, or stale scales
   */
  private static Map<Integer, ConstraintScale> indexAndValidateScales(List<SensitivityConstraintSnapshot> constraints,
      List<ConstraintScale> scales) {
    if (scales.size() != constraints.size()) {
      throw new IllegalArgumentException("Exactly one scale is required for every constraint");
    }
    Map<Integer, ConstraintScale> indexed = new LinkedHashMap<Integer, ConstraintScale>();
    for (ConstraintScale scale : scales) {
      if (scale == null) {
        throw new IllegalArgumentException("Constraint scale must not be null");
      }
      int index = scale.getConstraintIndex();
      if (index < 0 || index >= constraints.size()) {
        throw new IllegalArgumentException("Constraint scale index is outside the result: " + index);
      }
      if (indexed.containsKey(index)) {
        throw new IllegalArgumentException("Duplicate constraint scale index: " + index);
      }
      SensitivityConstraintSnapshot constraint = constraints.get(index);
      if (!scale.matches(constraint)) {
        throw new IllegalArgumentException("Constraint scale identity no longer matches result row " + index);
      }
      indexed.put(index, scale);
    }
    return indexed;
  }

  /**
   * Classifies one constraint conservatively from base-state evidence.
   *
   * @param result complete sensitivity result
   * @param constraint constraint snapshot
   * @param normalizedMargin dimensionless base margin
   * @param policy activity policy
   * @param diagnostics mutable diagnostics populated by this method
   * @return conservative activity status
   */
  private static ActivityStatus classify(SensitivityQualityResult result, SensitivityConstraintSnapshot constraint,
      double normalizedMargin, ActivityPolicy policy, List<String> diagnostics) {
    if (!result.isBaseSimulationConverged() || result.getBaseErrorMessage() != null
        || !Double.isFinite(normalizedMargin)) {
      diagnostics.add("Activity unavailable because base convergence, evaluation, or margin evidence is invalid");
      return ActivityStatus.UNAVAILABLE;
    }
    if (!constraint.isHard() && !policy.areSoftConstraintsIncluded()) {
      diagnostics.add("Soft constraint excluded by activity policy");
      return ActivityStatus.EXCLUDED_SOFT;
    }
    if (!result.isBaseFeasible()) {
      diagnostics.add("Base point violates at least one hard constraint; inspect the complete violated set");
    }
    if (normalizedMargin < 0.0) {
      diagnostics.add("Constraint is violated; a violated point is not labelled candidate active");
      return ActivityStatus.VIOLATED;
    }
    if (normalizedMargin <= policy.getActiveNormalizedMarginTolerance()) {
      diagnostics.add("Feasible normalized margin is within the declared candidate-active tolerance");
      return ActivityStatus.CANDIDATE_ACTIVE;
    }
    diagnostics.add("Normalized margin is outside the declared candidate-active tolerance");
    return ActivityStatus.INACTIVE;
  }

  /**
   * Filters assessments by an exact status.
   *
   * @param assessments complete assessments
   * @param status requested status
   * @return immutable filtered assessments
   * @throws IllegalArgumentException if assessments is null
   */
  private static List<ConstraintActivityAssessment> filterByStatus(List<ConstraintActivityAssessment> assessments,
      ActivityStatus status) {
    if (assessments == null) {
      throw new IllegalArgumentException("Constraint activity assessments must not be null");
    }
    List<ConstraintActivityAssessment> filtered = new ArrayList<ConstraintActivityAssessment>();
    for (ConstraintActivityAssessment assessment : assessments) {
      if (assessment != null && assessment.getStatus() == status) {
        filtered.add(assessment);
      }
    }
    return Collections.unmodifiableList(filtered);
  }

  /**
   * Compares nullable strings.
   *
   * @param first first string
   * @param second second string
   * @return true when both are null or equal
   */
  private static boolean safeEquals(String first, String second) {
    return first == null ? second == null : first.equals(second);
  }

  /**
   * Compares doubles exactly, including infinities and canonical NaN values.
   *
   * @param first first value
   * @param second second value
   * @return true when the exact bit representations match
   */
  private static boolean sameDouble(double first, double second) {
    return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
  }
}
