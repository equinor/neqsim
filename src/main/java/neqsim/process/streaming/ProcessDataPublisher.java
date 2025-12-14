package neqsim.process.streaming;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Implementation of StreamingDataInterface for publishing process simulation data.
 *
 * <p>
 * This class bridges NeqSim process simulations with AI-based production optimization platforms and
 * real-time digital twin systems.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic tag discovery from ProcessSystem measurement devices</li>
 * <li>Configurable history buffer for ML training data</li>
 * <li>Thread-safe subscription management</li>
 * <li>Batch publishing for high-throughput scenarios</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessDataPublisher implements StreamingDataInterface {
  private static final int DEFAULT_HISTORY_SIZE = 10000;

  private final Map<String, List<Consumer<TimestampedValue>>> subscribers =
      new ConcurrentHashMap<>();
  private final Map<String, LinkedList<TimestampedValue>> history = new ConcurrentHashMap<>();
  private final Map<String, TimestampedValue> currentValues = new ConcurrentHashMap<>();
  private final List<String> stateVectorTags = new CopyOnWriteArrayList<>();

  private int historyBufferSize = DEFAULT_HISTORY_SIZE;
  private ProcessSystem processSystem;

  /**
   * Creates a new ProcessDataPublisher.
   */
  public ProcessDataPublisher() {}

  /**
   * Creates a new ProcessDataPublisher linked to a ProcessSystem.
   *
   * @param processSystem the process system to monitor
   */
  public ProcessDataPublisher(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    discoverTags();
  }

  /**
   * Discovers tags from the linked ProcessSystem's unit operations.
   */
  private void discoverTags() {
    if (processSystem == null) {
      return;
    }
    // Discover from streams and other equipment
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      String tagId = equipment.getName();
      if (tagId != null && !stateVectorTags.contains(tagId)) {
        stateVectorTags.add(tagId);
      }
    }
  }

  /**
   * Links this publisher to a ProcessSystem.
   *
   * @param processSystem the process system to monitor
   */
  public void setProcessSystem(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    discoverTags();
  }

  /**
   * Adds a tag to the state vector.
   *
   * @param tagId the tag identifier to add
   */
  public void addToStateVector(String tagId) {
    if (!stateVectorTags.contains(tagId)) {
      stateVectorTags.add(tagId);
    }
  }

  /**
   * Publishes current values from all unit operations in the ProcessSystem.
   */
  public void publishFromProcessSystem() {
    if (processSystem == null) {
      return;
    }

    Map<String, TimestampedValue> batch = new HashMap<>();
    Instant now = Instant.now();

    // Publish from streams and other equipment that have meaningful values
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      String tagId = equipment.getName();
      if (tagId != null) {
        // For streams, publish pressure and temperature
        if (equipment instanceof neqsim.process.equipment.stream.StreamInterface) {
          neqsim.process.equipment.stream.StreamInterface stream =
              (neqsim.process.equipment.stream.StreamInterface) equipment;
          batch.put(tagId + ".pressure", new TimestampedValue(stream.getPressure(), "bara", now,
              TimestampedValue.Quality.SIMULATED));
          batch.put(tagId + ".temperature", new TimestampedValue(stream.getTemperature() - 273.15,
              "C", now, TimestampedValue.Quality.SIMULATED));
          batch.put(tagId + ".flowrate", new TimestampedValue(stream.getFlowRate("kg/hr"), "kg/hr",
              now, TimestampedValue.Quality.SIMULATED));
        }
      }
    }

    publishBatch(batch);
  }

  @Override
  public void subscribeToUpdates(String tagId, Consumer<TimestampedValue> callback) {
    subscribers.computeIfAbsent(tagId, k -> new CopyOnWriteArrayList<>()).add(callback);
    history.computeIfAbsent(tagId, k -> new LinkedList<>());
  }

  @Override
  public void unsubscribeFromUpdates(String tagId) {
    subscribers.remove(tagId);
  }

  @Override
  public void publishBatch(Map<String, TimestampedValue> values) {
    for (Map.Entry<String, TimestampedValue> entry : values.entrySet()) {
      String tagId = entry.getKey();
      TimestampedValue value = entry.getValue();

      // Update current value
      currentValues.put(tagId, value);

      // Add to history
      LinkedList<TimestampedValue> tagHistory =
          history.computeIfAbsent(tagId, k -> new LinkedList<>());
      synchronized (tagHistory) {
        tagHistory.addLast(value);
        while (tagHistory.size() > historyBufferSize) {
          tagHistory.removeFirst();
        }
      }

      // Notify subscribers
      List<Consumer<TimestampedValue>> tagSubscribers = subscribers.get(tagId);
      if (tagSubscribers != null) {
        for (Consumer<TimestampedValue> callback : tagSubscribers) {
          try {
            callback.accept(value);
          } catch (Exception e) {
            // Log but don't propagate subscriber errors
          }
        }
      }
    }
  }

  @Override
  public double[] getStateVector() {
    double[] state = new double[stateVectorTags.size()];
    for (int i = 0; i < stateVectorTags.size(); i++) {
      TimestampedValue tv = currentValues.get(stateVectorTags.get(i));
      state[i] = (tv != null) ? tv.getValue() : Double.NaN;
    }
    return state;
  }

  @Override
  public String[] getStateVectorLabels() {
    return stateVectorTags.toArray(new String[0]);
  }

  @Override
  public List<TimestampedValue> getHistory(String tagId, Duration lookback) {
    LinkedList<TimestampedValue> tagHistory = history.get(tagId);
    if (tagHistory == null) {
      return Collections.emptyList();
    }

    Instant cutoff = Instant.now().minus(lookback);
    List<TimestampedValue> result = new ArrayList<>();

    synchronized (tagHistory) {
      for (TimestampedValue tv : tagHistory) {
        if (tv.getTimestamp().isAfter(cutoff)) {
          result.add(tv);
        }
      }
    }
    return result;
  }

  @Override
  public Map<String, List<TimestampedValue>> getHistoryBatch(List<String> tagIds,
      Duration lookback) {
    Map<String, List<TimestampedValue>> result = new HashMap<>();
    for (String tagId : tagIds) {
      result.put(tagId, getHistory(tagId, lookback));
    }
    return result;
  }

  @Override
  public TimestampedValue getCurrentValue(String tagId) {
    return currentValues.get(tagId);
  }

  @Override
  public boolean isMonitored(String tagId) {
    return subscribers.containsKey(tagId) || currentValues.containsKey(tagId);
  }

  @Override
  public List<String> getMonitoredTags() {
    return new ArrayList<>(currentValues.keySet());
  }

  @Override
  public void setHistoryBufferSize(int maxSamples) {
    this.historyBufferSize = maxSamples;
  }

  @Override
  public void clearHistory() {
    history.clear();
  }

  /**
   * Gets the number of stored samples for a tag.
   *
   * @param tagId the tag identifier
   * @return number of samples in history
   */
  public int getHistorySize(String tagId) {
    LinkedList<TimestampedValue> tagHistory = history.get(tagId);
    return tagHistory != null ? tagHistory.size() : 0;
  }

  /**
   * Exports history as a 2D array for ML training.
   *
   * @param tagIds tags to include
   * @return 2D array where rows are time samples and columns are tags
   */
  public double[][] exportHistoryMatrix(List<String> tagIds) {
    if (tagIds.isEmpty()) {
      return new double[0][0];
    }

    // Find minimum history length
    int minLength = Integer.MAX_VALUE;
    for (String tagId : tagIds) {
      LinkedList<TimestampedValue> tagHistory = history.get(tagId);
      if (tagHistory != null) {
        minLength = Math.min(minLength, tagHistory.size());
      } else {
        minLength = 0;
      }
    }

    if (minLength == 0 || minLength == Integer.MAX_VALUE) {
      return new double[0][tagIds.size()];
    }

    double[][] matrix = new double[minLength][tagIds.size()];

    for (int col = 0; col < tagIds.size(); col++) {
      LinkedList<TimestampedValue> tagHistory = history.get(tagIds.get(col));
      if (tagHistory != null) {
        synchronized (tagHistory) {
          int row = 0;
          int skipCount = tagHistory.size() - minLength;
          for (TimestampedValue tv : tagHistory) {
            if (skipCount > 0) {
              skipCount--;
              continue;
            }
            matrix[row++][col] = tv.getValue();
          }
        }
      }
    }

    return matrix;
  }
}
