package neqsim.process.diagnostics.restart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Result of simulating a restart sequence on a process system.
 *
 * <p>
 * Records the time to complete each step, the overall time to stable operation (MTTR), any issues
 * encountered during restart, and the final process state quality metrics.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class RestartSimulationResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  /**
   * Outcome of the restart simulation.
   */
  public enum Outcome {
    /** Restart succeeded and process reached stable operation. */
    SUCCESS("Restart successful"),
    /** Restart succeeded but with marginal stability. */
    PARTIAL_SUCCESS("Partial restart — marginal stability"),
    /** Restart failed — process did not reach stable state. */
    FAILED("Restart failed"),
    /** Restart triggered another trip during the sequence. */
    RE_TRIPPED("Process re-tripped during restart");

    private final String displayName;

    Outcome(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Returns the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Record of a completed step during simulation.
   *
   * @author esol
   * @version 1.0
   */
  public static class StepRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int stepNumber;
    private final String description;
    private final double startTime;
    private final double endTime;
    private final boolean successful;
    private final String notes;

    /**
     * Constructs a step record.
     *
     * @param stepNumber the step number
     * @param description step description
     * @param startTime start time in seconds
     * @param endTime end time in seconds
     * @param successful whether the step completed successfully
     * @param notes any notes or issues
     */
    public StepRecord(int stepNumber, String description, double startTime, double endTime,
        boolean successful, String notes) {
      this.stepNumber = stepNumber;
      this.description = description;
      this.startTime = startTime;
      this.endTime = endTime;
      this.successful = successful;
      this.notes = notes;
    }

    /**
     * Returns the step number.
     *
     * @return step number
     */
    public int getStepNumber() {
      return stepNumber;
    }

    /**
     * Returns the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Returns the start time.
     *
     * @return start time in seconds
     */
    public double getStartTime() {
      return startTime;
    }

    /**
     * Returns the end time.
     *
     * @return end time in seconds
     */
    public double getEndTime() {
      return endTime;
    }

    /**
     * Returns the step duration.
     *
     * @return duration in seconds
     */
    public double getDuration() {
      return endTime - startTime;
    }

    /**
     * Returns whether the step was successful.
     *
     * @return true if successful
     */
    public boolean isSuccessful() {
      return successful;
    }

    /**
     * Returns notes or issues from the step.
     *
     * @return notes text
     */
    public String getNotes() {
      return notes;
    }
  }

  private Outcome outcome;
  private double totalTimeSeconds;
  private double timeToStableSeconds;
  private final List<StepRecord> stepRecords;
  private final List<String> issues;
  private final Map<String, Double> finalProcessValues;

  /**
   * Constructs an empty simulation result.
   */
  public RestartSimulationResult() {
    this.outcome = Outcome.FAILED;
    this.stepRecords = new ArrayList<>();
    this.issues = new ArrayList<>();
    this.finalProcessValues = new LinkedHashMap<>();
  }

  /**
   * Sets the outcome.
   *
   * @param outcome the outcome
   */
  public void setOutcome(Outcome outcome) {
    this.outcome = outcome;
  }

  /**
   * Returns the outcome.
   *
   * @return outcome
   */
  public Outcome getOutcome() {
    return outcome;
  }

  /**
   * Sets the total simulation time.
   *
   * @param seconds total time in seconds
   */
  public void setTotalTimeSeconds(double seconds) {
    this.totalTimeSeconds = seconds;
  }

  /**
   * Returns the total simulation time.
   *
   * @return total time in seconds
   */
  public double getTotalTimeSeconds() {
    return totalTimeSeconds;
  }

  /**
   * Sets the time to reach stable operation (MTTR).
   *
   * @param seconds time to stable in seconds
   */
  public void setTimeToStableSeconds(double seconds) {
    this.timeToStableSeconds = seconds;
  }

  /**
   * Returns the time to reach stable operation (MTTR metric).
   *
   * @return MTTR in seconds
   */
  public double getTimeToStableSeconds() {
    return timeToStableSeconds;
  }

  /**
   * Returns the MTTR in minutes.
   *
   * @return MTTR in minutes
   */
  public double getMttrMinutes() {
    return timeToStableSeconds / 60.0;
  }

  /**
   * Adds a step record.
   *
   * @param record the step record
   */
  public void addStepRecord(StepRecord record) {
    stepRecords.add(record);
  }

  /**
   * Returns all step records.
   *
   * @return unmodifiable list of step records
   */
  public List<StepRecord> getStepRecords() {
    return Collections.unmodifiableList(stepRecords);
  }

  /**
   * Adds an issue encountered during simulation.
   *
   * @param issue description of the issue
   */
  public void addIssue(String issue) {
    issues.add(issue);
  }

  /**
   * Returns all issues.
   *
   * @return unmodifiable list of issues
   */
  public List<String> getIssues() {
    return Collections.unmodifiableList(issues);
  }

  /**
   * Records a final process value.
   *
   * @param variableName the variable name
   * @param value the value
   */
  public void addFinalProcessValue(String variableName, double value) {
    finalProcessValues.put(variableName, value);
  }

  /**
   * Returns the final process values.
   *
   * @return unmodifiable map of variable names to values
   */
  public Map<String, Double> getFinalProcessValues() {
    return Collections.unmodifiableMap(finalProcessValues);
  }

  /**
   * Serialises to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("outcome", outcome.name());
    map.put("totalTime_s", totalTimeSeconds);
    map.put("totalTime_min", totalTimeSeconds / 60.0);
    map.put("timeToStable_s", timeToStableSeconds);
    map.put("mttr_min", getMttrMinutes());

    List<Map<String, Object>> stepList = new ArrayList<>();
    for (StepRecord sr : stepRecords) {
      Map<String, Object> srMap = new LinkedHashMap<>();
      srMap.put("step", sr.getStepNumber());
      srMap.put("description", sr.getDescription());
      srMap.put("startTime_s", sr.getStartTime());
      srMap.put("endTime_s", sr.getEndTime());
      srMap.put("duration_s", sr.getDuration());
      srMap.put("successful", sr.isSuccessful());
      if (sr.getNotes() != null && !sr.getNotes().isEmpty()) {
        srMap.put("notes", sr.getNotes());
      }
      stepList.add(srMap);
    }
    map.put("steps", stepList);

    if (!issues.isEmpty()) {
      map.put("issues", issues);
    }
    if (!finalProcessValues.isEmpty()) {
      map.put("finalProcessValues", finalProcessValues);
    }
    return GSON.toJson(map);
  }

  @Override
  public String toString() {
    return String.format("RestartSimulationResult{outcome=%s, mttr=%.1f min, steps=%d, issues=%d}",
        outcome.name(), getMttrMinutes(), stepRecords.size(), issues.size());
  }
}
