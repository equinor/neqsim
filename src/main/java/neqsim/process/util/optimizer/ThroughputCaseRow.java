package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.LinkedHashMap;
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

  /** Active bottleneck utilization. */
  private final double utilization;

  /** Current bottleneck load. */
  private final double currentValue;

  /** Bottleneck design value. */
  private final double designValue;

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
  public ThroughputCaseRow(int caseNumber, double throughputMultiplier,
      Map<String, Double> producerMultipliers, double objectiveValue, boolean feasible,
      boolean simulationConverged, String activeArea, String activeEquipment,
      String activeConstraint, double utilization, double currentValue, double designValue,
      double capacityMargin, double utilizationMargin, String unit, String errorMessage,
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
      Map<String, Double> producerMultipliers,
      ProcessModelSimulationEvaluator.EvaluationResult evaluation) {
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = evaluation.getActiveBottleneck();
    double objectiveValue = Double.NaN;
    if (evaluation.getObjectivesRaw() != null && evaluation.getObjectivesRaw().length > 0) {
      objectiveValue = evaluation.getObjectivesRaw()[0];
    }
    double currentValue = bottleneck.getCurrentValue();
    double designValue = bottleneck.getDesignValue();
    double capacityMargin = designValue - currentValue;
    double utilization = bottleneck.getUtilization();
    return new ThroughputCaseRow(caseNumber, throughputMultiplier, producerMultipliers,
        objectiveValue, evaluation.isFeasible(), evaluation.isSimulationConverged(),
        bottleneck.getAreaName(), bottleneck.getEquipmentName(), bottleneck.getConstraintName(),
        utilization, currentValue, designValue, capacityMargin, 1.0 - utilization,
        bottleneck.getUnit(), evaluation.getErrorMessage(), evaluation.getEvaluationTimeMs());
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
    map.put("utilization", utilization);
    map.put("currentValue", currentValue);
    map.put("designValue", designValue);
    map.put("capacityMargin", capacityMargin);
    map.put("utilizationMargin", utilizationMargin);
    map.put("unit", unit);
    map.put("errorMessage", errorMessage);
    map.put("evaluationTimeMs", evaluationTimeMs);
    return map;
  }
}
