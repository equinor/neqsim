package neqsim.process.util.export;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.streaming.TimestampedValue;

/**
 * Exports process simulation data as time series for external ML/AI platforms.
 *
 * <p>
 * This class provides standardized export formats compatible with AI-based production optimization
 * platforms and digital twin systems.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TimeSeriesExporter {

  private final ProcessSystem processSystem;
  private final List<TimeSeriesPoint> collectedData;
  private final Gson gson;

  /**
   * Represents a single time series data point.
   */
  public static class TimeSeriesPoint {
    private final long timestampMillis;
    private final Map<String, Double> values;
    private final Map<String, String> units;
    private final Map<String, String> qualities;

    public TimeSeriesPoint(Instant timestamp) {
      this.timestampMillis = timestamp.toEpochMilli();
      this.values = new HashMap<>();
      this.units = new HashMap<>();
      this.qualities = new HashMap<>();
    }

    public void addValue(String tag, double value, String unit, String quality) {
      values.put(tag, value);
      units.put(tag, unit);
      qualities.put(tag, quality);
    }

    public long getTimestampMillis() {
      return timestampMillis;
    }

    public Map<String, Double> getValues() {
      return values;
    }

    public Map<String, String> getUnits() {
      return units;
    }

    public Map<String, String> getQualities() {
      return qualities;
    }
  }

  /**
   * Creates a new time series exporter.
   *
   * @param processSystem the process system to export from
   */
  public TimeSeriesExporter(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.collectedData = new ArrayList<>();
    this.gson = new GsonBuilder().setPrettyPrinting().create();
  }

  /**
   * Collects a snapshot of current values.
   */
  public void collectSnapshot() {
    TimeSeriesPoint point = new TimeSeriesPoint(Instant.now());

    // Collect from streams and other equipment
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem
        .getUnitOperations()) {
      String tag = equipment.getName();
      if (tag == null) {
        continue;
      }

      // For streams, collect key values
      if (equipment instanceof neqsim.process.equipment.stream.StreamInterface) {
        neqsim.process.equipment.stream.StreamInterface stream =
            (neqsim.process.equipment.stream.StreamInterface) equipment;
        point.addValue(tag + ".pressure", stream.getPressure(), "bara", "GOOD");
        point.addValue(tag + ".temperature", stream.getTemperature() - 273.15, "C", "GOOD");
        point.addValue(tag + ".flowrate", stream.getFlowRate("kg/hr"), "kg/hr", "GOOD");
      }
    }

    collectedData.add(point);
  }

  /**
   * Exports collected data in JSON format compatible with AI platforms.
   *
   * @return JSON string
   */
  public String exportToJson() {
    Map<String, Object> export = new HashMap<>();
    export.put("version", "1.0");
    export.put("source", "neqsim");
    export.put("exportTime", Instant.now().toString());
    export.put("pointCount", collectedData.size());
    export.put("data", collectedData);

    return gson.toJson(export);
  }

  /**
   * Exports data for specific tags in AI platform format.
   *
   * @param tags list of tag names to export
   * @param startTime start of time range
   * @return JSON string
   */
  public String exportForAIPlatform(List<String> tags, Instant startTime) {
    Map<String, Object> export = new HashMap<>();
    export.put("format", "neqsim-ai-v1");
    export.put("startTime", startTime.toEpochMilli());
    export.put("tags", tags);

    // Build time series per tag
    Map<String, List<Map<String, Object>>> tagData = new HashMap<>();
    for (String tag : tags) {
      tagData.put(tag, new ArrayList<>());
    }

    for (TimeSeriesPoint point : collectedData) {
      if (point.getTimestampMillis() >= startTime.toEpochMilli()) {
        for (String tag : tags) {
          if (point.getValues().containsKey(tag)) {
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("t", point.getTimestampMillis());
            dataPoint.put("v", point.getValues().get(tag));
            dataPoint.put("q", point.getQualities().get(tag));
            tagData.get(tag).add(dataPoint);
          }
        }
      }
    }

    export.put("series", tagData);
    return gson.toJson(export);
  }

  /**
   * Exports data as CSV format.
   *
   * @return CSV string
   */
  public String exportToCsv() {
    if (collectedData.isEmpty()) {
      return "";
    }

    StringBuilder csv = new StringBuilder();

    // Header
    TimeSeriesPoint first = collectedData.get(0);
    csv.append("timestamp");
    for (String tag : first.getValues().keySet()) {
      csv.append(",").append(tag);
    }
    csv.append("\n");

    // Data rows
    for (TimeSeriesPoint point : collectedData) {
      csv.append(point.getTimestampMillis());
      for (String tag : first.getValues().keySet()) {
        Double value = point.getValues().get(tag);
        csv.append(",").append(value != null ? value : "");
      }
      csv.append("\n");
    }

    return csv.toString();
  }

  /**
   * Exports data as a 2D matrix for ML training.
   *
   * @param tags tags to include as columns
   * @return 2D array (rows = time points, columns = tags)
   */
  public double[][] exportMatrix(List<String> tags) {
    double[][] matrix = new double[collectedData.size()][tags.size()];

    for (int i = 0; i < collectedData.size(); i++) {
      TimeSeriesPoint point = collectedData.get(i);
      for (int j = 0; j < tags.size(); j++) {
        Double value = point.getValues().get(tags.get(j));
        matrix[i][j] = (value != null) ? value : Double.NaN;
      }
    }

    return matrix;
  }

  /**
   * Imports historian data from JSON.
   *
   * @param json JSON string with time series data
   */
  public void importFromHistorian(String json) {
    // Parse and add to collected data
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> imported = gson.fromJson(json, Map.class);

      if (imported.containsKey("data")) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) imported.get("data");

        for (Map<String, Object> dp : dataPoints) {
          long timestamp = ((Number) dp.get("timestampMillis")).longValue();
          TimeSeriesPoint point = new TimeSeriesPoint(Instant.ofEpochMilli(timestamp));

          @SuppressWarnings("unchecked")
          Map<String, Double> values = (Map<String, Double>) dp.get("values");
          @SuppressWarnings("unchecked")
          Map<String, String> units = (Map<String, String>) dp.get("units");
          @SuppressWarnings("unchecked")
          Map<String, String> qualities = (Map<String, String>) dp.get("qualities");

          for (String tag : values.keySet()) {
            point.addValue(tag, values.get(tag), units.getOrDefault(tag, ""),
                qualities.getOrDefault(tag, "GOOD"));
          }

          collectedData.add(point);
        }
      }
    } catch (Exception e) {
      // Log error
    }
  }

  /**
   * Creates a ProcessSnapshot from the latest collected data.
   *
   * @param snapshotId ID for the snapshot
   * @return process snapshot
   */
  public ProcessSnapshot createSnapshot(String snapshotId) {
    ProcessSnapshot snapshot = new ProcessSnapshot(snapshotId);

    // Create snapshot from streams and other equipment
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem
        .getUnitOperations()) {
      String name = equipment.getName();
      if (name == null) {
        continue;
      }

      if (equipment instanceof neqsim.process.equipment.stream.StreamInterface) {
        neqsim.process.equipment.stream.StreamInterface stream =
            (neqsim.process.equipment.stream.StreamInterface) equipment;
        snapshot.setMeasurement(name + ".pressure", stream.getPressure(), "bara");
        snapshot.setMeasurement(name + ".temperature", stream.getTemperature() - 273.15, "C");
        snapshot.setMeasurement(name + ".flowrate", stream.getFlowRate("kg/hr"), "kg/hr");
      }
    }

    return snapshot;
  }

  /**
   * Clears collected data.
   */
  public void clearData() {
    collectedData.clear();
  }

  /**
   * Gets the number of collected data points.
   *
   * @return count of time series points
   */
  public int getDataPointCount() {
    return collectedData.size();
  }

  /**
   * Gets all collected data points.
   *
   * @return list of time series points
   */
  public List<TimeSeriesPoint> getCollectedData() {
    return new ArrayList<>(collectedData);
  }
}
