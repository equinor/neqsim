package neqsim.process.diagnostics;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Represents a detected trip event on a piece of process equipment.
 *
 * <p>
 * A trip event captures the equipment name, the parameter that triggered it, the threshold that was
 * exceeded, and the actual value at the time of the trip. It also records a timestamp and a severity
 * classification.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class TripEvent implements Serializable {

  private static final long serialVersionUID = 1000L;

  /**
   * Severity level of a trip event.
   */
  public enum Severity {
    /** Low severity — alarm only, no automatic shutdown. */
    LOW,
    /** Medium severity — partial shutdown or load reduction. */
    MEDIUM,
    /** High severity — full equipment trip. */
    HIGH,
    /** Critical severity — emergency shutdown of multiple equipment. */
    CRITICAL
  }

  private final String equipmentName;
  private final String parameterName;
  private final double threshold;
  private final double actualValue;
  private final boolean highTrip;
  private final long timestampMillis;
  private final double simulationTimeSeconds;
  private final Severity severity;

  /**
   * Creates a trip event.
   *
   * @param equipmentName name of the equipment that tripped
   * @param parameterName name of the parameter that triggered the trip
   * @param threshold the threshold value that was exceeded
   * @param actualValue the actual measured value at the time of the trip
   * @param highTrip true if this was a high-limit trip, false for low-limit
   * @param simulationTimeSeconds time in the simulation when the trip occurred
   * @param severity severity level of the trip
   */
  public TripEvent(String equipmentName, String parameterName, double threshold,
      double actualValue, boolean highTrip, double simulationTimeSeconds, Severity severity) {
    this.equipmentName = equipmentName;
    this.parameterName = parameterName;
    this.threshold = threshold;
    this.actualValue = actualValue;
    this.highTrip = highTrip;
    this.timestampMillis = System.currentTimeMillis();
    this.simulationTimeSeconds = simulationTimeSeconds;
    this.severity = severity;
  }

  /**
   * Gets the equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Gets the parameter name that triggered the trip.
   *
   * @return parameter name
   */
  public String getParameterName() {
    return parameterName;
  }

  /**
   * Gets the threshold value that was exceeded.
   *
   * @return threshold value
   */
  public double getThreshold() {
    return threshold;
  }

  /**
   * Gets the actual value at the time of the trip.
   *
   * @return actual measured value
   */
  public double getActualValue() {
    return actualValue;
  }

  /**
   * Checks if this was a high-limit trip.
   *
   * @return true if high-limit trip, false if low-limit trip
   */
  public boolean isHighTrip() {
    return highTrip;
  }

  /**
   * Gets the wall-clock timestamp when the trip was detected.
   *
   * @return timestamp in milliseconds since epoch
   */
  public long getTimestampMillis() {
    return timestampMillis;
  }

  /**
   * Gets the simulation time when the trip occurred.
   *
   * @return simulation time in seconds
   */
  public double getSimulationTimeSeconds() {
    return simulationTimeSeconds;
  }

  /**
   * Gets the severity level.
   *
   * @return severity
   */
  public Severity getSeverity() {
    return severity;
  }

  /**
   * Gets the deviation from the threshold.
   *
   * @return absolute deviation from the threshold
   */
  public double getDeviation() {
    return Math.abs(actualValue - threshold);
  }

  /**
   * Returns a JSON representation of this trip event.
   *
   * @return JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    sb.append("\"equipmentName\": \"").append(escapeJson(equipmentName)).append("\", ");
    sb.append("\"parameterName\": \"").append(escapeJson(parameterName)).append("\", ");
    sb.append("\"threshold\": ").append(threshold).append(", ");
    sb.append("\"actualValue\": ").append(actualValue).append(", ");
    sb.append("\"highTrip\": ").append(highTrip).append(", ");
    sb.append("\"simulationTimeSeconds\": ").append(simulationTimeSeconds).append(", ");
    sb.append("\"severity\": \"").append(severity.name()).append("\", ");
    sb.append("\"timestamp\": \"")
        .append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date(timestampMillis)))
        .append("\"");
    sb.append("}");
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("TripEvent[%s: %s=%s %.2f (threshold %.2f) at t=%.1fs, severity=%s]",
        equipmentName, parameterName, highTrip ? "HIGH" : "LOW", actualValue, threshold,
        simulationTimeSeconds, severity.name());
  }

  /**
   * Escapes a string for JSON output.
   *
   * @param s string to escape
   * @return escaped string
   */
  private static String escapeJson(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        .replace("\r", "\\r").replace("\t", "\\t");
  }
}
