package neqsim.process.envelope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Full digital twin monitoring loop that connects plant data, process model, and envelope agent.
 *
 * <p>
 * The {@code ProcessDigitalTwinLoop} implements the complete monitoring cycle:
 * </p>
 * <ol>
 * <li>Read live process data from a {@link DataProvider} (plant historian, OPC, SCADA, etc.)</li>
 * <li>Update the NeqSim process model with actual operating conditions</li>
 * <li>Run the {@link OperatingEnvelopeAgent} evaluation</li>
 * <li>Publish results to any registered {@link ResultConsumer}</li>
 * </ol>
 *
 * <p>
 * The loop is driven externally (via {@link #executeCycle()}) rather than running its own thread,
 * making it compatible with any scheduling framework (Timer, ScheduledExecutor, cron, etc.).
 * </p>
 *
 * <p>
 * <strong>IMPORTANT: This is advisory-only infrastructure.</strong> It reads plant data and runs
 * simulations but never writes to the control system.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * ProcessSystem process = buildProcess();
 * OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
 *
 * ProcessDigitalTwinLoop loop = new ProcessDigitalTwinLoop(process, agent);
 *
 * // Register data provider (e.g., PI historian reader)
 * loop.setDataProvider(new MyHistorianDataProvider(tagMap));
 *
 * // Register result consumers (e.g., dashboard, logger, MQTT publisher)
 * loop.addResultConsumer(new DashboardPublisher());
 * loop.addResultConsumer(new LogFileWriter("envelope.log"));
 *
 * // Execute periodically (called by external scheduler)
 * loop.executeCycle();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ProcessDigitalTwinLoop implements Serializable {
  private static final long serialVersionUID = 1L;

  private final ProcessSystem processSystem;
  private final OperatingEnvelopeAgent agent;
  private DataProvider dataProvider;
  private final List<ResultConsumer> resultConsumers;

  private int cycleCount;
  private long lastCycleTimeMillis;
  private double lastCycleDurationSeconds;
  private AgentEvaluationResult lastResult;
  private String lastErrorMessage;
  private boolean enabled;

  /**
   * Creates a digital twin loop.
   *
   * @param processSystem the process system (model)
   * @param agent the operating envelope agent
   */
  public ProcessDigitalTwinLoop(ProcessSystem processSystem, OperatingEnvelopeAgent agent) {
    this.processSystem = processSystem;
    this.agent = agent;
    this.resultConsumers = new ArrayList<ResultConsumer>();
    this.cycleCount = 0;
    this.lastCycleTimeMillis = 0;
    this.lastCycleDurationSeconds = 0;
    this.lastResult = null;
    this.lastErrorMessage = null;
    this.enabled = true;
  }

  /**
   * Executes a single monitoring cycle.
   *
   * <p>
   * Call this method at regular intervals from your scheduler. Each call:
   * </p>
   * <ol>
   * <li>Reads data from the {@link DataProvider} (if configured)</li>
   * <li>Updates the process model with live values</li>
   * <li>Runs the process model</li>
   * <li>Evaluates the operating envelope via the agent</li>
   * <li>Publishes results to all registered consumers</li>
   * </ol>
   *
   * @return the evaluation result for this cycle, or null if the loop is disabled
   */
  public AgentEvaluationResult executeCycle() {
    if (!enabled) {
      return null;
    }

    long startTime = System.currentTimeMillis();
    cycleCount++;
    lastErrorMessage = null;

    try {
      // Step 1: Read plant data
      if (dataProvider != null) {
        Map<String, Double> liveData = dataProvider.readCurrentValues();
        if (liveData != null && !liveData.isEmpty()) {
          applyDataToModel(liveData);
        }
      }

      // Step 2: Run process model
      processSystem.run();

      // Step 3: Evaluate envelope
      AgentEvaluationResult result = agent.evaluate();
      lastResult = result;

      // Step 4: Publish results
      for (ResultConsumer consumer : resultConsumers) {
        try {
          consumer.consume(result);
        } catch (Exception e) {
          // Consumer failure — non-fatal, log but continue
          lastErrorMessage = "Consumer error: " + e.getMessage();
        }
      }

      lastCycleTimeMillis = startTime;
      lastCycleDurationSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
      return result;

    } catch (Exception e) {
      lastErrorMessage = "Cycle failed: " + e.getMessage();
      lastCycleDurationSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
      return null;
    }
  }

  /**
   * Applies live data to the process model.
   *
   * <p>
   * Data keys follow the ProcessAutomation address format: {@code "Equipment.stream.property"}.
   * This method uses reflection-free direct equipment updates for common patterns.
   * </p>
   *
   * @param liveData map of address to value
   */
  private void applyDataToModel(Map<String, Double> liveData) {
    // Use ProcessAutomation if available
    try {
      neqsim.process.automation.ProcessAutomation auto = processSystem.getAutomation();
      for (Map.Entry<String, Double> entry : liveData.entrySet()) {
        try {
          auto.setVariableValue(entry.getKey(), entry.getValue(), "");
        } catch (Exception e) {
          // Individual value set failure — non-fatal
        }
      }
    } catch (Exception e) {
      // Automation not available — skip data application
    }
  }

  /**
   * Sets the data provider for reading live process values.
   *
   * @param provider the data provider implementation
   */
  public void setDataProvider(DataProvider provider) {
    this.dataProvider = provider;
  }

  /**
   * Adds a result consumer to receive evaluation outputs.
   *
   * @param consumer the consumer
   */
  public void addResultConsumer(ResultConsumer consumer) {
    if (consumer != null) {
      resultConsumers.add(consumer);
    }
  }

  /**
   * Removes a result consumer.
   *
   * @param consumer the consumer to remove
   * @return true if removed
   */
  public boolean removeResultConsumer(ResultConsumer consumer) {
    return resultConsumers.remove(consumer);
  }

  /**
   * Enables or disables the monitoring loop.
   *
   * @param enabled true to enable
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns whether the loop is enabled.
   *
   * @return true if enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns the total number of cycles executed.
   *
   * @return cycle count
   */
  public int getCycleCount() {
    return cycleCount;
  }

  /**
   * Returns the time of the last cycle execution.
   *
   * @return timestamp in milliseconds
   */
  public long getLastCycleTimeMillis() {
    return lastCycleTimeMillis;
  }

  /**
   * Returns the duration of the last cycle.
   *
   * @return duration in seconds
   */
  public double getLastCycleDurationSeconds() {
    return lastCycleDurationSeconds;
  }

  /**
   * Returns the result of the last cycle.
   *
   * @return last result, or null if no cycle has been executed
   */
  public AgentEvaluationResult getLastResult() {
    return lastResult;
  }

  /**
   * Returns the last error message, if any.
   *
   * @return error message, or null if last cycle succeeded
   */
  public String getLastErrorMessage() {
    return lastErrorMessage;
  }

  /**
   * Returns the underlying agent.
   *
   * @return the operating envelope agent
   */
  public OperatingEnvelopeAgent getAgent() {
    return agent;
  }

  /**
   * Returns a status summary of the loop.
   *
   * @return status map with key metrics
   */
  public Map<String, Object> getStatusSummary() {
    Map<String, Object> status = new HashMap<String, Object>();
    status.put("enabled", enabled);
    status.put("cycleCount", cycleCount);
    status.put("lastCycleDurationSec", lastCycleDurationSeconds);
    status.put("hasDataProvider", dataProvider != null);
    status.put("consumerCount", resultConsumers.size());
    status.put("lastError", lastErrorMessage);
    if (lastResult != null) {
      status.put("lastOverallStatus", lastResult.getOverallStatus().name());
      status.put("lastCriticalCount", lastResult.getCriticalMarginCount());
      status.put("lastTripPredictions", lastResult.getTripPredictions().size());
    }
    return status;
  }

  /**
   * Resets cycle statistics (does not affect the agent or model state).
   */
  public void resetStatistics() {
    cycleCount = 0;
    lastCycleTimeMillis = 0;
    lastCycleDurationSeconds = 0;
    lastResult = null;
    lastErrorMessage = null;
  }

  // ── Interfaces for extensibility ──

  /**
   * Interface for providing live process data to the digital twin loop.
   *
   * <p>
   * Implementations connect to plant data sources such as OSIsoft PI, Aspen IP.21, OPC-UA, MQTT, or
   * file-based data. The returned map uses ProcessAutomation address format as keys.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public interface DataProvider extends Serializable {

    /**
     * Reads the current values of all monitored tags.
     *
     * <p>
     * Keys should follow the ProcessAutomation address convention:
     * {@code "EquipmentName.stream.property"} for single-system models, or
     * {@code "AreaName::EquipmentName.stream.property"} for multi-area models.
     * </p>
     *
     * @return map of address to current value, or empty map if data unavailable
     */
    Map<String, Double> readCurrentValues();

    /**
     * Returns whether the data source is connected and healthy.
     *
     * @return true if data source is available
     */
    boolean isHealthy();
  }

  /**
   * Interface for consuming evaluation results from the digital twin loop.
   *
   * <p>
   * Implementations handle the output — sending to dashboards, writing to logs, publishing to
   * message queues, storing in databases, etc.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public interface ResultConsumer extends Serializable {

    /**
     * Consumes an evaluation result.
     *
     * @param result the evaluation result from this cycle
     */
    void consume(AgentEvaluationResult result);
  }

  /**
   * Simple implementation of {@link DataProvider} using a fixed map of values.
   *
   * <p>
   * Useful for testing and simulation scenarios where live data is not available.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class StaticDataProvider implements DataProvider {
    private static final long serialVersionUID = 1L;
    private final Map<String, Double> values;

    /**
     * Creates a static data provider with fixed values.
     *
     * @param values the fixed values map
     */
    public StaticDataProvider(Map<String, Double> values) {
      this.values = new HashMap<String, Double>(values);
    }

    /**
     * Returns the fixed values.
     *
     * @return map of address to value
     */
    @Override
    public Map<String, Double> readCurrentValues() {
      return new HashMap<String, Double>(values);
    }

    /**
     * Always returns true.
     *
     * @return true
     */
    @Override
    public boolean isHealthy() {
      return true;
    }

    /**
     * Updates a value.
     *
     * @param address the variable address
     * @param value the new value
     */
    public void setValue(String address, double value) {
      values.put(address, value);
    }
  }
}
