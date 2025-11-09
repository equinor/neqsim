package neqsim.process.util.scenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.logic.LogicState;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.process.util.monitor.ScenarioKPI;

/**
 * Summary of scenario execution results.
 * 
 * <p>
 * Captures:
 * <ul>
 * <li>Scenario name and configuration</li>
 * <li>Logic execution results</li>
 * <li>Errors and warnings</li>
 * <li>Performance metrics</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ScenarioExecutionSummary {
  private final String scenarioName;
  private ProcessSafetyScenario scenario;
  private final List<String> errors = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();
  private final Map<String, LogicResult> logicResults = new HashMap<>();
  private long executionTimeMs;
  private boolean successful = true;
  private ScenarioKPI kpi;

  /**
   * Creates a new scenario execution summary.
   *
   * @param scenarioName name of the scenario
   */
  public ScenarioExecutionSummary(String scenarioName) {
    this.scenarioName = scenarioName;
  }

  /**
   * Gets the scenario name.
   *
   * @return scenario name
   */
  public String getScenarioName() {
    return scenarioName;
  }

  /**
   * Sets the scenario configuration.
   *
   * @param scenario scenario configuration
   */
  public void setScenario(ProcessSafetyScenario scenario) {
    this.scenario = scenario;
  }

  /**
   * Gets the scenario configuration.
   *
   * @return scenario configuration
   */
  public ProcessSafetyScenario getScenario() {
    return scenario;
  }

  /**
   * Adds an error message.
   *
   * @param error error description
   */
  public void addError(String error) {
    errors.add(error);
    successful = false;
  }

  /**
   * Adds a warning message.
   *
   * @param warning warning description
   */
  public void addWarning(String warning) {
    warnings.add(warning);
  }

  /**
   * Gets all error messages.
   *
   * @return list of errors
   */
  public List<String> getErrors() {
    return new ArrayList<>(errors);
  }

  /**
   * Gets all warning messages.
   *
   * @return list of warnings
   */
  public List<String> getWarnings() {
    return new ArrayList<>(warnings);
  }

  /**
   * Adds a logic execution result.
   *
   * @param logicName name of the logic sequence
   * @param finalState final state of the logic
   * @param statusDescription detailed status description
   */
  public void addLogicResult(String logicName, LogicState finalState, String statusDescription) {
    logicResults.put(logicName, new LogicResult(finalState, statusDescription));
  }

  /**
   * Gets logic execution results.
   *
   * @return map of logic name to result
   */
  public Map<String, LogicResult> getLogicResults() {
    return new HashMap<>(logicResults);
  }

  /**
   * Sets the execution time.
   *
   * @param executionTimeMs execution time in milliseconds
   */
  public void setExecutionTime(long executionTimeMs) {
    this.executionTimeMs = executionTimeMs;
  }

  /**
   * Gets the execution time.
   *
   * @return execution time in milliseconds
   */
  public long getExecutionTime() {
    return executionTimeMs;
  }

  /**
   * Checks if the scenario executed successfully.
   *
   * @return true if successful (no errors)
   */
  public boolean isSuccessful() {
    return successful;
  }

  /**
   * Sets the KPI metrics for this scenario.
   *
   * @param kpi scenario KPI metrics
   */
  public void setKPI(ScenarioKPI kpi) {
    this.kpi = kpi;
  }

  /**
   * Gets the KPI metrics for this scenario.
   *
   * @return scenario KPI metrics
   */
  public ScenarioKPI getKPI() {
    return kpi;
  }

  /**
   * Prints detailed scenario results to console.
   * 
   * <p>
   * This is a convenience method for consistent result formatting. It displays:
   * <ul>
   * <li>Overall status (success or completed with issues)</li>
   * <li>All errors (if any)</li>
   * <li>All warnings (if any)</li>
   * <li>Logic execution results</li>
   * </ul>
   */
  public void printResults() {
    System.out.println("\n--- SCENARIO RESULTS ---");
    System.out.println("Status: " + (isSuccessful() ? "SUCCESS" : "COMPLETED WITH ISSUES"));

    if (!errors.isEmpty()) {
      System.out.println("Errors:");
      for (String error : errors) {
        System.out.println("  - " + error);
      }
    }

    if (!warnings.isEmpty()) {
      System.out.println("Warnings:");
      for (String warning : warnings) {
        System.out.println("  - " + warning);
      }
    }

    System.out.println("Logic Results:");
    for (var entry : logicResults.entrySet()) {
      var result = entry.getValue();
      System.out.println("  " + entry.getKey() + ": " + result.getFinalState() + " ("
          + result.getStatusDescription() + ")");
    }
    System.out.println();
  }

  /**
   * Result of a logic sequence execution.
   */
  public static class LogicResult {
    private final LogicState finalState;
    private final String statusDescription;

    /**
     * Creates a logic result.
     *
     * @param finalState final state of the logic
     * @param statusDescription status description
     */
    public LogicResult(LogicState finalState, String statusDescription) {
      this.finalState = finalState;
      this.statusDescription = statusDescription;
    }

    /**
     * Gets the final state.
     *
     * @return final logic state
     */
    public LogicState getFinalState() {
      return finalState;
    }

    /**
     * Gets the status description.
     *
     * @return status description
     */
    public String getStatusDescription() {
      return statusDescription;
    }
  }
}
