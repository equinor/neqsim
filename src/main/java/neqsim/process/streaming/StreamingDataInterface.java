package neqsim.process.streaming;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interface for high-frequency real-time data streaming.
 *
 * <p>
 * Designed for integration with AI-powered production optimization platforms that require
 * continuous data feeds at high rates (millions of data points per hour).
 * </p>
 *
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li>Subscribe to real-time updates via callbacks</li>
 * <li>Batch publishing for efficiency</li>
 * <li>State vector extraction for ML model input</li>
 * <li>Historical data access for training</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public interface StreamingDataInterface {

  /**
   * Subscribe to real-time updates for a specific tag.
   *
   * @param tagId the tag identifier (e.g., "PT-101", "FT-200")
   * @param callback function to call when new values arrive
   */
  void subscribeToUpdates(String tagId, Consumer<TimestampedValue> callback);

  /**
   * Unsubscribe from updates for a specific tag.
   *
   * @param tagId the tag identifier
   */
  void unsubscribeFromUpdates(String tagId);

  /**
   * Publish a batch of values for multiple tags efficiently.
   *
   * <p>
   * This method is optimized for high-throughput scenarios where many values need to be published
   * simultaneously.
   * </p>
   *
   * @param values map of tag IDs to their timestamped values
   */
  void publishBatch(Map<String, TimestampedValue> values);

  /**
   * Publish a single value for a tag.
   *
   * @param tagId the tag identifier
   * @param value the timestamped value
   */
  default void publish(String tagId, TimestampedValue value) {
    Map<String, TimestampedValue> singleValue = new HashMap<>();
    singleValue.put(tagId, value);
    publishBatch(singleValue);
  }

  /**
   * Get current state vector for ML model input.
   *
   * <p>
   * Returns a numeric array representing the current state of all monitored variables. The order
   * and meaning of elements is defined by {@link #getStateVectorLabels()}.
   * </p>
   *
   * @return array of current state values
   */
  double[] getStateVector();

  /**
   * Get labels for state vector elements.
   *
   * @return array of tag IDs corresponding to state vector positions
   */
  String[] getStateVectorLabels();

  /**
   * Get historical values for a tag.
   *
   * @param tagId the tag identifier
   * @param lookback how far back to retrieve data
   * @return list of timestamped values, ordered oldest to newest
   */
  List<TimestampedValue> getHistory(String tagId, Duration lookback);

  /**
   * Get historical values for multiple tags aligned by timestamp.
   *
   * @param tagIds list of tag identifiers
   * @param lookback how far back to retrieve data
   * @return map of tag IDs to their historical values
   */
  Map<String, List<TimestampedValue>> getHistoryBatch(List<String> tagIds, Duration lookback);

  /**
   * Get the current value for a tag.
   *
   * @param tagId the tag identifier
   * @return the most recent value, or null if not available
   */
  TimestampedValue getCurrentValue(String tagId);

  /**
   * Check if a tag is being monitored.
   *
   * @param tagId the tag identifier
   * @return true if the tag has subscribers or is being tracked
   */
  boolean isMonitored(String tagId);

  /**
   * Get all currently monitored tag IDs.
   *
   * @return list of tag identifiers
   */
  List<String> getMonitoredTags();

  /**
   * Set the buffer size for historical data.
   *
   * @param maxSamples maximum number of samples to retain per tag
   */
  void setHistoryBufferSize(int maxSamples);

  /**
   * Clear all historical data.
   */
  void clearHistory();
}
