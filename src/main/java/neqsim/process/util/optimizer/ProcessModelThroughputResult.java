package neqsim.process.util.optimizer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Result object for a full process-model throughput-to-bottleneck study.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessModelThroughputResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Study name. */
  private String studyName;

  /** Objective name. */
  private String objectiveName;

  /** Objective unit. */
  private String objectiveUnit;

  /** Evaluated cases. */
  private final List<ThroughputCaseRow> caseRows = new ArrayList<ThroughputCaseRow>();

  /** Best feasible row found. */
  private ThroughputCaseRow bestFeasibleCase;

  /** First or lowest infeasible row found. */
  private ThroughputCaseRow firstInfeasibleCase;

  /** Whether the lower bound case was feasible. */
  private boolean lowerBoundFeasible;

  /** Whether the upper bound case was feasible. */
  private boolean upperBoundFeasible;

  /**
   * Creates a throughput result.
   *
   * @param studyName study name
   * @param objectiveName objective name
   * @param objectiveUnit objective unit
   */
  public ProcessModelThroughputResult(String studyName, String objectiveName,
      String objectiveUnit) {
    this.studyName = studyName;
    this.objectiveName = objectiveName;
    this.objectiveUnit = objectiveUnit;
  }

  /**
   * Adds an evaluated case row.
   *
   * @param row evaluated case row
   */
  public void addCase(ThroughputCaseRow row) {
    if (row == null) {
      return;
    }
    caseRows.add(row);
    if (row.isFeasible()) {
      if (bestFeasibleCase == null
          || row.getThroughputMultiplier() > bestFeasibleCase.getThroughputMultiplier()) {
        bestFeasibleCase = row;
      }
    } else if (firstInfeasibleCase == null
        || row.getThroughputMultiplier() < firstInfeasibleCase.getThroughputMultiplier()) {
      firstInfeasibleCase = row;
    }
  }

  /**
   * Gets the study name.
   *
   * @return study name
   */
  public String getStudyName() {
    return studyName;
  }

  /**
   * Sets the study name.
   *
   * @param studyName study name
   */
  public void setStudyName(String studyName) {
    this.studyName = studyName;
  }

  /**
   * Gets the objective name.
   *
   * @return objective name
   */
  public String getObjectiveName() {
    return objectiveName;
  }

  /**
   * Sets the objective name.
   *
   * @param objectiveName objective name
   */
  public void setObjectiveName(String objectiveName) {
    this.objectiveName = objectiveName;
  }

  /**
   * Gets the objective unit.
   *
   * @return objective unit
   */
  public String getObjectiveUnit() {
    return objectiveUnit;
  }

  /**
   * Sets the objective unit.
   *
   * @param objectiveUnit objective unit
   */
  public void setObjectiveUnit(String objectiveUnit) {
    this.objectiveUnit = objectiveUnit;
  }

  /**
   * Gets evaluated case rows.
   *
   * @return immutable list of case rows
   */
  public List<ThroughputCaseRow> getCaseRows() {
    return Collections.unmodifiableList(caseRows);
  }

  /**
   * Gets the best feasible case.
   *
   * @return best feasible case, or null when none was found
   */
  public ThroughputCaseRow getBestFeasibleCase() {
    return bestFeasibleCase;
  }

  /**
   * Gets the first infeasible case.
   *
   * @return first infeasible case, or null when all evaluated cases were feasible
   */
  public ThroughputCaseRow getFirstInfeasibleCase() {
    return firstInfeasibleCase;
  }

  /**
   * Gets the optimal throughput multiplier.
   *
   * @return best feasible multiplier, or NaN when no feasible case was found
   */
  public double getOptimalMultiplier() {
    return bestFeasibleCase == null ? Double.NaN : bestFeasibleCase.getThroughputMultiplier();
  }

  /**
   * Gets the optimal objective value.
   *
   * @return objective value at the best feasible case, or NaN when none exists
   */
  public double getOptimalObjectiveValue() {
    return bestFeasibleCase == null ? Double.NaN : bestFeasibleCase.getObjectiveValue();
  }

  /**
   * Checks whether the lower bound was feasible.
   *
   * @return true when the lower bound case was feasible
   */
  public boolean isLowerBoundFeasible() {
    return lowerBoundFeasible;
  }

  /**
   * Sets lower-bound feasibility.
   *
   * @param lowerBoundFeasible true when the lower bound case was feasible
   */
  public void setLowerBoundFeasible(boolean lowerBoundFeasible) {
    this.lowerBoundFeasible = lowerBoundFeasible;
  }

  /**
   * Checks whether the upper bound was feasible.
   *
   * @return true when the upper bound case was feasible
   */
  public boolean isUpperBoundFeasible() {
    return upperBoundFeasible;
  }

  /**
   * Sets upper-bound feasibility.
   *
   * @param upperBoundFeasible true when the upper bound case was feasible
   */
  public void setUpperBoundFeasible(boolean upperBoundFeasible) {
    this.upperBoundFeasible = upperBoundFeasible;
  }

  /**
   * Converts the result to a JSON-friendly map.
   *
   * @return result map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("studyName", studyName);
    map.put("objectiveName", objectiveName);
    map.put("objectiveUnit", objectiveUnit);
    map.put("optimalMultiplier", getOptimalMultiplier());
    map.put("optimalObjectiveValue", getOptimalObjectiveValue());
    map.put("lowerBoundFeasible", lowerBoundFeasible);
    map.put("upperBoundFeasible", upperBoundFeasible);
    map.put("bestFeasibleCase", bestFeasibleCase == null ? null : bestFeasibleCase.toMap());
    map.put("firstInfeasibleCase",
        firstInfeasibleCase == null ? null : firstInfeasibleCase.toMap());

    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (ThroughputCaseRow row : caseRows) {
      rows.add(row.toMap());
    }
    map.put("caseRows", rows);
    return map;
  }

  /**
   * Serializes the result as JSON.
   *
   * @return JSON result string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Exports the case table to CSV.
   *
   * @param filePath output file path
   * @throws IOException if writing fails
   */
  public void exportToCSV(String filePath) throws IOException {
    exportToCSV(Paths.get(filePath));
  }

  /**
   * Exports the case table to CSV.
   *
   * @param filePath output file path
   * @throws IOException if writing fails
   */
  public void exportToCSV(Path filePath) throws IOException {
    BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8);
    try {
      writer.write("caseNumber,throughputMultiplier,objectiveValue,feasible,"
          + "simulationConverged,activeArea,activeEquipment,activeConstraint,"
          + "utilization,currentValue,designValue,capacityMargin,utilizationMargin,unit,"
          + "errorMessage,evaluationTimeMs");
      writer.newLine();
      for (ThroughputCaseRow row : caseRows) {
        writer.write(Integer.toString(row.getCaseNumber()));
        writer.write(",");
        writer.write(Double.toString(row.getThroughputMultiplier()));
        writer.write(",");
        writer.write(Double.toString(row.getObjectiveValue()));
        writer.write(",");
        writer.write(Boolean.toString(row.isFeasible()));
        writer.write(",");
        writer.write(Boolean.toString(row.isSimulationConverged()));
        writer.write(",");
        writer.write(csvEscape(row.getActiveArea()));
        writer.write(",");
        writer.write(csvEscape(row.getActiveEquipment()));
        writer.write(",");
        writer.write(csvEscape(row.getActiveConstraint()));
        writer.write(",");
        writer.write(Double.toString(row.getUtilization()));
        writer.write(",");
        writer.write(Double.toString(row.getCurrentValue()));
        writer.write(",");
        writer.write(Double.toString(row.getDesignValue()));
        writer.write(",");
        writer.write(Double.toString(row.getCapacityMargin()));
        writer.write(",");
        writer.write(Double.toString(row.getUtilizationMargin()));
        writer.write(",");
        writer.write(csvEscape(row.getUnit()));
        writer.write(",");
        writer.write(csvEscape(row.getErrorMessage()));
        writer.write(",");
        writer.write(Long.toString(row.getEvaluationTimeMs()));
        writer.newLine();
      }
    } finally {
      writer.close();
    }
  }

  /**
   * Escapes a value for CSV output.
   *
   * @param value value to escape
   * @return escaped CSV value
   */
  private String csvEscape(String value) {
    if (value == null) {
      return "";
    }
    if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0
        && value.indexOf('\r') < 0) {
      return value;
    }
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
