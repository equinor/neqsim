package neqsim.process.operations;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result from executing an {@link OperationalScenario}.
 *
 * <p>
 * The result records action-level log messages, errors, warnings, and before/after values for
 * manipulated variables so reports can trace what changed in the process model.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class OperationalScenarioResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String scenarioName;
  private final List<String> actionLog = new ArrayList<String>();
  private final List<String> warnings = new ArrayList<String>();
  private final List<String> errors = new ArrayList<String>();
  private final Map<String, Double> beforeValues = new LinkedHashMap<String, Double>();
  private final Map<String, Double> afterValues = new LinkedHashMap<String, Double>();

  /**
   * Creates a scenario result.
   *
   * @param scenarioName scenario name
   */
  public OperationalScenarioResult(String scenarioName) {
    this.scenarioName = scenarioName == null ? "" : scenarioName;
  }

  /**
   * Adds an action log entry.
   *
   * @param message log message
   */
  public void addActionLog(String message) {
    actionLog.add(message);
  }

  /**
   * Adds a warning.
   *
   * @param warning warning message
   */
  public void addWarning(String warning) {
    warnings.add(warning);
  }

  /**
   * Adds an error.
   *
   * @param error error message
   */
  public void addError(String error) {
    errors.add(error);
  }

  /**
   * Stores a before-action value.
   *
   * @param address variable address
   * @param value variable value
   */
  public void putBeforeValue(String address, double value) {
    beforeValues.put(address, value);
  }

  /**
   * Stores an after-action value.
   *
   * @param address variable address
   * @param value variable value
   */
  public void putAfterValue(String address, double value) {
    afterValues.put(address, value);
  }

  /**
   * Returns the scenario name.
   *
   * @return scenario name
   */
  public String getScenarioName() {
    return scenarioName;
  }

  /**
   * Returns action log entries.
   *
   * @return unmodifiable action log
   */
  public List<String> getActionLog() {
    return Collections.unmodifiableList(actionLog);
  }

  /**
   * Returns warnings.
   *
   * @return unmodifiable warning list
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Returns errors.
   *
   * @return unmodifiable error list
   */
  public List<String> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  /**
   * Returns before-action values.
   *
   * @return unmodifiable map of values before manipulation
   */
  public Map<String, Double> getBeforeValues() {
    return Collections.unmodifiableMap(beforeValues);
  }

  /**
   * Returns after-action values.
   *
   * @return unmodifiable map of values after manipulation
   */
  public Map<String, Double> getAfterValues() {
    return Collections.unmodifiableMap(afterValues);
  }

  /**
   * Checks whether scenario execution had no errors.
   *
   * @return true when execution was successful
   */
  public boolean isSuccessful() {
    return errors.isEmpty();
  }

  /**
   * Serializes the result to formatted JSON.
   *
   * @return JSON result
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}