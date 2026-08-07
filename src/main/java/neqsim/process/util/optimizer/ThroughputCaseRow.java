package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One evaluated case in a process-model throughput-to-bottleneck study.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ThroughputCaseRow implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Case sequence number. */
  private final int caseNumber;

  /** Scalar throughput multiplier used for this case. */
  private final double throughputMultiplier;

  /** Producer multipliers used for this case. */
  private final Map<String, Double> producerMultipliers;

  /** Raw objective value reported by the evaluator. */
  private final double objectiveValue;

  /** Feasibility flag. */
  private final boolean feasible;

  /** Simulation convergence flag. */
  private final boolean simulationConverged;

  /** Active bottleneck area name. */
  private final String activeArea;

  /** Active bottleneck equipment name. */
  private final String activeEquipment;

  /** Active bottleneck constraint name. */
  private final String activeConstraint;

  /** Immutable ranked capacity-constraint snapshots for this evaluated case. */
  private List<ProcessModelSimulationEvaluator.BottleneckStatus> rankedCapacityConstraints = Collections.emptyList();

  /** Active bottleneck utilization. */
  private final double utilization;

  /** Current bottleneck load. */
  private final double currentValue;

  /** Bottleneck design value. */
  private final double designValue;

  /** Whether the active bottleneck is a minimum-directed constraint. */
  private final boolean minimumConstraint;

  /** Provenance of the active bottleneck limit. */
  private final String dataSource;

  /** Whether confidence was explicitly assigned to the active bottleneck. */
  private final boolean confidenceSet;

  /** Evidence-quality confidence of the active bottleneck. */
  private final double confidence;

  /** Whether a scalar validity range was assigned to the active bottleneck. */
  private final boolean validityRangeSet;

  /** Lower inclusive validity bound in the bottleneck unit. */
  private final double validityMinimum;

  /** Upper inclusive validity bound in the bottleneck unit. */
  private final double validityMaximum;

  /** Whether the snapshotted current value lies inside the validity range. */
  private final boolean currentValueWithinValidityRange;

  /** Remaining capacity in engineering units. */
  private final double capacityMargin;

  /** Remaining utilization margin. */
  private final double utilizationMargin;

  /** Bottleneck unit. */
  private final String unit;

  /** Error message if the case failed. */
  private final String errorMessage;

  /** Evaluation wall-clock time in milliseconds. */
  private final long evaluationTimeMs;

  /**
   * Creates a throughput case row.
   *
   * @param caseNumber case sequence number
   * @param throughputMultiplier scalar throughput multiplier
   * @param producerMultipliers producer multipliers used in the case
   * @param objectiveValue raw objective value
   * @param feasible true when all hard constraints are satisfied
   * @param simulationConverged true when the model converged
   * @param activeArea active bottleneck area name
   * @param activeEquipment active bottleneck equipment name
   * @param activeConstraint active bottleneck constraint name
   * @param utilization active bottleneck utilization
   * @param currentValue current bottleneck load
   * @param designValue bottleneck design value
   * @param capacityMargin remaining capacity in engineering units
   * @param utilizationMargin remaining utilization margin
   * @param unit bottleneck unit
   * @param errorMessage error message if the case failed
   * @param evaluationTimeMs evaluation wall-clock time in milliseconds
   */
  public ThroughputCaseRow(int caseNumber, double throughputMultiplier, Map<String, Double> producerMultipliers,
      double objectiveValue, boolean feasible, boolean simulationConverged, String activeArea, String activeEquipment,
      String activeConstraint, double utilization, double currentValue, double designValue, double capacityMargin,
      double utilizationMargin, String unit, String errorMessage, long evaluationTimeMs) {
    this(caseNumber, throughputMultiplier, producerMultipliers, objectiveValue, feasible, simulationConverged,
        activeArea, activeEquipment, activeConstraint, utilization, currentValue, designValue, false, "not_set",
        capacityMargin, utilizationMargin, unit, errorMessage, evaluationTimeMs);
  }

  /**
   * Creates a throughput case row with explicit capacity-limit direction.
   *
   * @param caseNumber case sequence number
   * @param throughputMultiplier scalar throughput multiplier
   * @param producerMultipliers producer multipliers used in the case
   * @param objectiveValue raw objective value
   * @param feasible true when all hard constraints are satisfied
   * @param simulationConverged true when the model converged
   * @param activeArea active bottleneck area name
   * @param activeEquipment active bottleneck equipment name
   * @param activeConstraint active bottleneck constraint name
   * @param utilization active bottleneck utilization
   * @param currentValue current bottleneck load
   * @param designValue bottleneck design or minimum limit
   * @param minimumConstraint true when values below the limit are worse
   * @param capacityMargin signed remaining capacity in engineering units
   * @param utilizationMargin remaining utilization margin
   * @param unit bottleneck unit
   * @param errorMessage error message if the case failed
   * @param evaluationTimeMs evaluation wall-clock time in milliseconds
   */
  public ThroughputCaseRow(int caseNumber, double throughputMultiplier, Map<String, Double> producerMultipliers,
      double objectiveValue, boolean feasible, boolean simulationConverged, String activeArea, String activeEquipment,
      String activeConstraint, double utilization, double currentValue, double designValue, boolean minimumConstraint,
      double capacityMargin, double utilizationMargin, String unit, String errorMessage, long evaluationTimeMs) {
    this(caseNumber, throughputMultiplier, producerMultipliers, objectiveValue, feasible, simulationConverged,
        activeArea, activeEquipment, activeConstraint, utilization, currentValue, designValue, minimumConstraint,
        "not_set", capacityMargin, utilizationMargin, unit, errorMessage, evaluationTimeMs);
  }

  /**
   * Creates a throughput case row with explicit capacity-limit direction and provenance.
   *
   * @param caseNumber case sequence number
   * @param throughputMultiplier scalar throughput multiplier
   * @param producerMultipliers producer multipliers used in the case
   * @param objectiveValue raw objective value
   * @param feasible true when all hard constraints are satisfied
   * @param simulationConverged true when the model converged
   * @param activeArea active bottleneck area name
   * @param activeEquipment active bottleneck equipment name
   * @param activeConstraint active bottleneck constraint name
   * @param utilization active bottleneck utilization
   * @param currentValue current bottleneck load
   * @param designValue bottleneck design or minimum limit
   * @param minimumConstraint true when values below the limit are worse
   * @param dataSource provenance of the bottleneck limit
   * @param capacityMargin signed remaining capacity in engineering units
   * @param utilizationMargin remaining utilization margin
   * @param unit bottleneck unit
   * @param errorMessage error message if the case failed
   * @param evaluationTimeMs evaluation wall-clock time in milliseconds
   */
  public ThroughputCaseRow(int caseNumber, double throughputMultiplier, Map<String, Double> producerMultipliers,
      double objectiveValue, boolean feasible, boolean simulationConverged, String activeArea, String activeEquipment,
      String activeConstraint, double utilization, double currentValue, double designValue, boolean minimumConstraint,
      String dataSource, double capacityMargin, double utilizationMargin, String unit, String errorMessage,
      long evaluationTimeMs) {
    this(caseNumber, throughputMultiplier, producerMultipliers, objectiveValue, feasible, simulationConverged,
        activeArea, activeEquipment, activeConstraint, utilization, currentValue, designValue, minimumConstraint,
        dataSource, false, Double.NaN, false, Double.NaN, Double.NaN, capacityMargin, utilizationMargin, unit,
        errorMessage, evaluationTimeMs);
  }

  /**
   * Creates a throughput case row with capacity evidence-quality and scalar-validity metadata. Enabled metadata that is
   * non-finite, outside the confidence range, or has reversed bounds is normalized to the explicit unset state.
   * Applicability is derived from the snapshotted current value and retained bounds.
   *
   * @param caseNumber case sequence number
   * @param throughputMultiplier scalar throughput multiplier
   * @param producerMultipliers producer multipliers used in the case
   * @param objectiveValue raw objective value
   * @param feasible true when all hard constraints are satisfied
   * @param simulationConverged true when the model converged
   * @param activeArea active bottleneck area name
   * @param activeEquipment active bottleneck equipment name
   * @param activeConstraint active bottleneck constraint name
   * @param utilization active bottleneck utilization
   * @param currentValue current bottleneck load
   * @param designValue bottleneck design or minimum limit
   * @param minimumConstraint true when values below the limit are worse
   * @param dataSource provenance of the bottleneck limit
   * @param confidenceSet true to retain a finite confidence in the range [0, 1]
   * @param confidence evidence-quality confidence, or NaN when unset
   * @param validityRangeSet true to retain finite, ordered scalar validity bounds
   * @param validityMinimum lower inclusive validity bound, or NaN when unset
   * @param validityMaximum upper inclusive validity bound, or NaN when unset
   * @param capacityMargin signed remaining capacity in engineering units
   * @param utilizationMargin remaining utilization margin
   * @param unit bottleneck unit
   * @param errorMessage error message if the case failed
   * @param evaluationTimeMs evaluation wall-clock time in milliseconds
   */
  public ThroughputCaseRow(int caseNumber, double throughputMultiplier, Map<String, Double> producerMultipliers,
      double objectiveValue, boolean feasible, boolean simulationConverged, String activeArea, String activeEquipment,
      String activeConstraint, double utilization, double currentValue, double designValue, boolean minimumConstraint,
      String dataSource, boolean confidenceSet, double confidence, boolean validityRangeSet, double validityMinimum,
      double validityMaximum, double capacityMargin, double utilizationMargin, String unit, String errorMessage,
      long evaluationTimeMs) {
    this.caseNumber = caseNumber;
    this.throughputMultiplier = throughputMultiplier;
    this.producerMultipliers = new LinkedHashMap<String, Double>(producerMultipliers);
    this.objectiveValue = objectiveValue;
    this.feasible = feasible;
    this.simulationConverged = simulationConverged;
    this.activeArea = activeArea;
    this.activeEquipment = activeEquipment;
    this.activeConstraint = activeConstraint;
    this.utilization = utilization;
    this.currentValue = currentValue;
    this.designValue = designValue;
    this.minimumConstraint = minimumConstraint;
    this.dataSource = dataSource == null ? "not_set" : dataSource;
    this.confidenceSet = confidenceSet && !Double.isNaN(confidence) && !Double.isInfinite(confidence)
        && confidence >= 0.0 && confidence <= 1.0;
    this.confidence = this.confidenceSet ? confidence : Double.NaN;
    this.validityRangeSet = validityRangeSet && !Double.isNaN(validityMinimum) && !Double.isInfinite(validityMinimum)
        && !Double.isNaN(validityMaximum) && !Double.isInfinite(validityMaximum) && validityMinimum <= validityMaximum;
    this.validityMinimum = this.validityRangeSet ? validityMinimum : Double.NaN;
    this.validityMaximum = this.validityRangeSet ? validityMaximum : Double.NaN;
    this.currentValueWithinValidityRange = this.validityRangeSet && currentValue >= this.validityMinimum
        && currentValue <= this.validityMaximum;
    this.capacityMargin = capacityMargin;
    this.utilizationMargin = utilizationMargin;
    this.unit = unit;
    this.errorMessage = errorMessage;
    this.evaluationTimeMs = evaluationTimeMs;
  }

  /**
   * Creates a row from a process-model evaluator result.
   *
   * @param caseNumber case sequence number
   * @param throughputMultiplier scalar throughput multiplier
   * @param producerMultipliers producer multipliers used in the case
   * @param evaluation evaluation result
   * @return populated throughput case row
   */
  public static ThroughputCaseRow fromEvaluation(int caseNumber, double throughputMultiplier,
      Map<String, Double> producerMultipliers, ProcessModelSimulationEvaluator.EvaluationResult evaluation) {
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = evaluation.getActiveBottleneck();
    double objectiveValue = Double.NaN;
    if (evaluation.getObjectivesRaw() != null && evaluation.getObjectivesRaw().length > 0) {
      objectiveValue = evaluation.getObjectivesRaw()[0];
    }
    double currentValue = bottleneck.getCurrentValue();
    double designValue = bottleneck.getDesignValue();
    boolean minimumConstraint = bottleneck.isMinimumConstraint();
    double capacityMargin = minimumConstraint ? currentValue - designValue : designValue - currentValue;
    double utilization = bottleneck.getUtilization();
    ThroughputCaseRow row = new ThroughputCaseRow(caseNumber, throughputMultiplier, producerMultipliers, objectiveValue,
        evaluation.isFeasible(), evaluation.isSimulationConverged(), bottleneck.getAreaName(),
        bottleneck.getEquipmentName(), bottleneck.getConstraintName(), utilization, currentValue, designValue,
        minimumConstraint, bottleneck.getDataSource(), bottleneck.hasConfidence(), bottleneck.getConfidence(),
        bottleneck.hasValidityRange(), bottleneck.getValidityMinimum(), bottleneck.getValidityMaximum(), capacityMargin,
        1.0 - utilization, bottleneck.getUnit(), evaluation.getErrorMessage(), evaluation.getEvaluationTimeMs());
    row.setRankedCapacityConstraints(evaluation.getRankedCapacityConstraints());
    return row;
  }

  /**
   * Retains a defensive immutable copy of the case-specific capacity ranking.
   *
   * @param rankedCapacityConstraints ranked capacity snapshots
   */
  private void setRankedCapacityConstraints(
      List<ProcessModelSimulationEvaluator.BottleneckStatus> rankedCapacityConstraints) {
    if (rankedCapacityConstraints == null || rankedCapacityConstraints.isEmpty()) {
      this.rankedCapacityConstraints = Collections.emptyList();
      return;
    }
    this.rankedCapacityConstraints = Collections
        .unmodifiableList(new ArrayList<ProcessModelSimulationEvaluator.BottleneckStatus>(rankedCapacityConstraints));
  }

  /**
   * Gets the case number.
   *
   * @return case number
   */
  public int getCaseNumber() {
    return caseNumber;
  }

  /**
   * Gets the throughput multiplier.
   *
   * @return throughput multiplier
   */
  public double getThroughputMultiplier() {
    return throughputMultiplier;
  }

  /**
   * Gets producer multipliers.
   *
   * @return copy of producer multiplier map
   */
  public Map<String, Double> getProducerMultipliers() {
    return new LinkedHashMap<String, Double>(producerMultipliers);
  }

  /**
   * Gets the raw objective value.
   *
   * @return raw objective value
   */
  public double getObjectiveValue() {
    return objectiveValue;
  }

  /**
   * Checks feasibility.
   *
   * @return true when the case is feasible
   */
  public boolean isFeasible() {
    return feasible;
  }

  /**
   * Checks simulation convergence.
   *
   * @return true when the simulation converged
   */
  public boolean isSimulationConverged() {
    return simulationConverged;
  }

  /**
   * Gets the active bottleneck area.
   *
   * @return active bottleneck area
   */
  public String getActiveArea() {
    return activeArea;
  }

  /**
   * Gets the active bottleneck equipment.
   *
   * @return active bottleneck equipment
   */
  public String getActiveEquipment() {
    return activeEquipment;
  }

  /**
   * Gets the active bottleneck constraint.
   *
   * @return active bottleneck constraint
   */
  public String getActiveConstraint() {
    return activeConstraint;
  }

  /**
   * Gets all capacity constraints snapshotted for this throughput case.
   *
   * @return immutable descending-utilization ranking
   */
  public List<ProcessModelSimulationEvaluator.BottleneckStatus> getRankedCapacityConstraints() {
    return rankedCapacityConstraints == null ? Collections.<ProcessModelSimulationEvaluator.BottleneckStatus>emptyList()
        : rankedCapacityConstraints;
  }

  /**
   * Gets bottleneck utilization.
   *
   * @return bottleneck utilization fraction
   */
  public double getUtilization() {
    return utilization;
  }

  /**
   * Gets current bottleneck value.
   *
   * @return current bottleneck value
   */
  public double getCurrentValue() {
    return currentValue;
  }

  /**
   * Gets bottleneck design value.
   *
   * @return bottleneck design value
   */
  public double getDesignValue() {
    return designValue;
  }

  /**
   * Checks whether the active bottleneck is a minimum-directed constraint.
   *
   * @return true when values below the design value are worse
   */
  public boolean isMinimumConstraint() {
    return minimumConstraint;
  }

  /**
   * Gets the provenance of the active bottleneck limit.
   *
   * @return source tag from the underlying capacity constraint
   */
  public String getDataSource() {
    return dataSource == null ? "not_set" : dataSource;
  }

  /**
   * Checks whether confidence was explicitly assigned to the active bottleneck.
   *
   * @return true when confidence is available
   */
  public boolean hasConfidence() {
    return confidenceSet;
  }

  /**
   * Gets evidence-quality confidence for the active bottleneck.
   *
   * @return confidence from zero to one, or NaN when unset
   */
  public double getConfidence() {
    return confidenceSet ? confidence : Double.NaN;
  }

  /**
   * Checks whether a scalar validity range was assigned to the active bottleneck.
   *
   * @return true when validity bounds are available
   */
  public boolean hasValidityRange() {
    return validityRangeSet;
  }

  /**
   * Gets the lower inclusive validity bound.
   *
   * @return lower bound in the bottleneck unit, or NaN when unset
   */
  public double getValidityMinimum() {
    return validityRangeSet ? validityMinimum : Double.NaN;
  }

  /**
   * Gets the upper inclusive validity bound.
   *
   * @return upper bound in the bottleneck unit, or NaN when unset
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
   * Gets remaining capacity.
   *
   * @return remaining capacity in engineering units
   */
  public double getCapacityMargin() {
    return capacityMargin;
  }

  /**
   * Gets remaining utilization margin.
   *
   * @return remaining utilization margin
   */
  public double getUtilizationMargin() {
    return utilizationMargin;
  }

  /**
   * Gets bottleneck unit.
   *
   * @return bottleneck unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Gets error message.
   *
   * @return error message, or null when no error occurred
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Gets evaluation time.
   *
   * @return evaluation time in milliseconds
   */
  public long getEvaluationTimeMs() {
    return evaluationTimeMs;
  }

  /**
   * Converts this row to a JSON-friendly map.
   *
   * @return map representation of this row
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("caseNumber", caseNumber);
    map.put("throughputMultiplier", throughputMultiplier);
    map.put("producerMultipliers", getProducerMultipliers());
    map.put("objectiveValue", objectiveValue);
    map.put("feasible", feasible);
    map.put("simulationConverged", simulationConverged);
    map.put("activeArea", activeArea);
    map.put("activeEquipment", activeEquipment);
    map.put("activeConstraint", activeConstraint);
    List<Map<String, Object>> rankedConstraints = new ArrayList<Map<String, Object>>();
    for (ProcessModelSimulationEvaluator.BottleneckStatus bottleneck : getRankedCapacityConstraints()) {
      rankedConstraints.add(toBottleneckMap(bottleneck));
    }
    map.put("rankedCapacityConstraints", rankedConstraints);
    map.put("utilization", utilization);
    map.put("currentValue", currentValue);
    map.put("designValue", designValue);
    map.put("minimumConstraint", minimumConstraint);
    map.put("dataSource", getDataSource());
    map.put("hasConfidence", hasConfidence());
    map.put("confidence", hasConfidence() ? Double.valueOf(getConfidence()) : null);
    map.put("hasValidityRange", hasValidityRange());
    map.put("validityMinimum", hasValidityRange() ? Double.valueOf(getValidityMinimum()) : null);
    map.put("validityMaximum", hasValidityRange() ? Double.valueOf(getValidityMaximum()) : null);
    map.put("currentValueWithinValidityRange",
        hasValidityRange() ? Boolean.valueOf(isCurrentValueWithinValidityRange()) : null);
    map.put("capacityMargin", capacityMargin);
    map.put("utilizationMargin", utilizationMargin);
    map.put("unit", unit);
    map.put("errorMessage", errorMessage);
    map.put("evaluationTimeMs", evaluationTimeMs);
    return map;
  }

  /**
   * Converts one capacity snapshot to a JSON-friendly map.
   *
   * @param bottleneck capacity snapshot
   * @return map containing engineering values and evidence metadata
   */
  private Map<String, Object> toBottleneckMap(ProcessModelSimulationEvaluator.BottleneckStatus bottleneck) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("areaName", bottleneck.getAreaName());
    map.put("equipmentName", bottleneck.getEquipmentName());
    map.put("constraintName", bottleneck.getConstraintName());
    map.put("utilization", bottleneck.getUtilization());
    map.put("currentValue", bottleneck.getCurrentValue());
    map.put("designValue", bottleneck.getDesignValue());
    map.put("minimumConstraint", bottleneck.isMinimumConstraint());
    map.put("dataSource", bottleneck.getDataSource());
    map.put("hasConfidence", bottleneck.hasConfidence());
    map.put("confidence", bottleneck.hasConfidence() ? Double.valueOf(bottleneck.getConfidence()) : null);
    map.put("hasValidityRange", bottleneck.hasValidityRange());
    map.put("validityMinimum", bottleneck.hasValidityRange() ? Double.valueOf(bottleneck.getValidityMinimum()) : null);
    map.put("validityMaximum", bottleneck.hasValidityRange() ? Double.valueOf(bottleneck.getValidityMaximum()) : null);
    map.put("evidenceApplicability", bottleneck.getEvidenceApplicability().name());
    map.put("unit", bottleneck.getUnit());
    map.put("feasible", bottleneck.isFeasible());
    return map;
  }
}
