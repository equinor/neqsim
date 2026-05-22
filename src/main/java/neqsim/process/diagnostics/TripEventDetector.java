package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Detects trip events during dynamic or steady-state simulation by monitoring process parameters
 * against configured thresholds.
 *
 * <p>
 * The detector maintains a registry of trip conditions per equipment. When a monitored parameter
 * exceeds its threshold, a {@link TripEvent} is generated and stored. The detector can be used in
 * two modes:
 * </p>
 * <ul>
 * <li><b>Dynamic mode</b>: called repeatedly with {@link #check(double)} during transient
 * simulation, tracking simulation time</li>
 * <li><b>Snapshot mode</b>: called once with {@link #checkCurrentState()} after a steady-state
 * run</li>
 * </ul>
 *
 * <p>
 * Trip conditions can be added manually via {@link #addTripCondition} or auto-configured from
 * design limits via {@link #autoConfigureFromDesignLimits(Map)}.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * TripEventDetector detector = new TripEventDetector(processSystem);
 * detector.addTripCondition("Compressor-1", "outletPressure", 120.0, true,
 *     TripEvent.Severity.HIGH);
 * detector.addTripCondition("Separator-1", "liquidLevel", 0.1, false,
 *     TripEvent.Severity.MEDIUM);
 *
 * // During dynamic simulation:
 * for (double t = 0; t &lt; 3600; t += dt) {
 *   process.runTransient(dt);
 *   detector.check(t);
 * }
 *
 * List&lt;TripEvent&gt; trips = detector.getDetectedTrips();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see TripEvent
 * @see RootCauseAnalyzer
 */
public class TripEventDetector implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(TripEventDetector.class);

  /** Process system being monitored. */
  private final ProcessSystem processSystem;

  /** Registered trip conditions keyed by equipment name. */
  private final Map<String, List<TripCondition>> tripConditions;

  /** Detected trip events. */
  private final List<TripEvent> detectedTrips;

  /** Deadband factor to avoid chattering near threshold. */
  private double deadbandFraction;

  /** Whether to stop checking an equipment after its first trip. */
  private boolean firstTripOnly;

  /** Equipment names that have already tripped (used when firstTripOnly is true). */
  private final java.util.Set<String> trippedEquipment;

  /**
   * A single trip condition: monitors one parameter on one equipment against a threshold.
   */
  static class TripCondition implements Serializable {
    private static final long serialVersionUID = 1L;

    final String equipmentName;
    final String parameterName;
    final double threshold;
    final boolean highTrip;
    final TripEvent.Severity severity;

    /**
     * Creates a trip condition.
     *
     * @param equipmentName equipment to monitor
     * @param parameterName parameter to monitor
     * @param threshold threshold value
     * @param highTrip true for high-limit trip, false for low-limit
     * @param severity trip severity
     */
    TripCondition(String equipmentName, String parameterName, double threshold, boolean highTrip,
        TripEvent.Severity severity) {
      this.equipmentName = equipmentName;
      this.parameterName = parameterName;
      this.threshold = threshold;
      this.highTrip = highTrip;
      this.severity = severity;
    }
  }

  /**
   * Creates a trip event detector for the given process system.
   *
   * @param processSystem the process system to monitor
   * @throws IllegalArgumentException if processSystem is null
   */
  public TripEventDetector(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = processSystem;
    this.tripConditions = new LinkedHashMap<>();
    this.detectedTrips = new ArrayList<>();
    this.trippedEquipment = new java.util.HashSet<>();
    this.deadbandFraction = 0.0;
    this.firstTripOnly = true;
  }

  /**
   * Adds a trip condition to monitor.
   *
   * @param equipmentName name of the equipment
   * @param parameterName parameter to monitor (e.g., "outletPressure", "temperature")
   * @param threshold the trip threshold value
   * @param highTrip true if trip triggers when value exceeds threshold, false for below
   * @param severity severity level of this trip
   */
  public void addTripCondition(String equipmentName, String parameterName, double threshold,
      boolean highTrip, TripEvent.Severity severity) {
    TripCondition condition =
        new TripCondition(equipmentName, parameterName, threshold, highTrip, severity);
    List<TripCondition> conditions = tripConditions.get(equipmentName);
    if (conditions == null) {
      conditions = new ArrayList<>();
      tripConditions.put(equipmentName, conditions);
    }
    conditions.add(condition);
  }

  /**
   * Auto-configures trip conditions from a map of design limits.
   *
   * <p>
   * The map keys should be in the format "equipmentName.parameterName" and values should be
   * two-element arrays [lowLimit, highLimit]. Use Double.NaN for no limit.
   * </p>
   *
   * @param designLimits map of "equipment.parameter" to [lowLimit, highLimit]
   */
  public void autoConfigureFromDesignLimits(Map<String, double[]> designLimits) {
    for (Map.Entry<String, double[]> entry : designLimits.entrySet()) {
      String key = entry.getKey();
      double[] limits = entry.getValue();
      int dotIndex = key.indexOf('.');
      if (dotIndex < 0 || dotIndex >= key.length() - 1) {
        logger.warn("Invalid design limit key '{}': expected 'equipment.parameter'", key);
        continue;
      }
      String equipmentName = key.substring(0, dotIndex);
      String parameterName = key.substring(dotIndex + 1);

      if (limits.length >= 2) {
        if (!Double.isNaN(limits[0])) {
          addTripCondition(equipmentName, parameterName, limits[0], false,
              TripEvent.Severity.HIGH);
        }
        if (!Double.isNaN(limits[1])) {
          addTripCondition(equipmentName, parameterName, limits[1], true,
              TripEvent.Severity.HIGH);
        }
      }
    }
  }

  /**
   * Sets the deadband fraction to prevent chattering near the threshold.
   *
   * <p>
   * A deadband of 0.02 means the value must exceed the threshold by 2% before triggering. This
   * prevents trips from fluctuating values near the threshold.
   * </p>
   *
   * @param fraction deadband as fraction of threshold (0.0 = no deadband)
   */
  public void setDeadbandFraction(double fraction) {
    this.deadbandFraction = Math.max(0.0, fraction);
  }

  /**
   * Sets whether each equipment should only generate one trip event.
   *
   * <p>
   * When true (default), once an equipment has tripped, no further trips are recorded for it. When
   * false, every check that exceeds the threshold generates a new event.
   * </p>
   *
   * @param firstOnly true to record only the first trip per equipment
   */
  public void setFirstTripOnly(boolean firstOnly) {
    this.firstTripOnly = firstOnly;
  }

  /**
   * Checks all trip conditions against the current process state.
   *
   * <p>
   * Call this method at each time step during dynamic simulation or once after a steady-state run.
   * </p>
   *
   * @param simulationTimeSeconds current simulation time in seconds
   * @return list of newly detected trip events (empty if no trips)
   */
  public List<TripEvent> check(double simulationTimeSeconds) {
    List<TripEvent> newTrips = new ArrayList<>();

    for (Map.Entry<String, List<TripCondition>> entry : tripConditions.entrySet()) {
      String eqName = entry.getKey();
      if (firstTripOnly && trippedEquipment.contains(eqName)) {
        continue;
      }

      for (TripCondition cond : entry.getValue()) {
        double value = readParameter(cond.equipmentName, cond.parameterName);
        if (Double.isNaN(value)) {
          continue;
        }

        double effectiveThreshold = cond.threshold;
        if (deadbandFraction > 0.0 && cond.threshold != 0.0) {
          if (cond.highTrip) {
            effectiveThreshold = cond.threshold * (1.0 + deadbandFraction);
          } else {
            effectiveThreshold = cond.threshold * (1.0 - deadbandFraction);
          }
        }

        boolean tripped = cond.highTrip
            ? value > effectiveThreshold
            : value < effectiveThreshold;

        if (tripped) {
          TripEvent event = new TripEvent(cond.equipmentName, cond.parameterName, cond.threshold,
              value, cond.highTrip, simulationTimeSeconds, cond.severity);
          newTrips.add(event);
          detectedTrips.add(event);
          trippedEquipment.add(eqName);
          logger.info("Trip detected: {}", event);

          if (firstTripOnly) {
            break;
          }
        }
      }
    }
    return newTrips;
  }

  /**
   * Checks all trip conditions against the current state (convenience method using time = 0).
   *
   * @return list of newly detected trip events
   */
  public List<TripEvent> checkCurrentState() {
    return check(0.0);
  }

  /**
   * Returns all detected trip events.
   *
   * @return unmodifiable list of trip events
   */
  public List<TripEvent> getDetectedTrips() {
    return Collections.unmodifiableList(detectedTrips);
  }

  /**
   * Returns the first trip event, or null if none detected.
   *
   * @return the first trip event, or null
   */
  public TripEvent getFirstTrip() {
    return detectedTrips.isEmpty() ? null : detectedTrips.get(0);
  }

  /**
   * Returns true if any trips have been detected.
   *
   * @return true if at least one trip has been detected
   */
  public boolean hasTrips() {
    return !detectedTrips.isEmpty();
  }

  /**
   * Returns the number of detected trip events.
   *
   * @return trip count
   */
  public int getTripCount() {
    return detectedTrips.size();
  }

  /**
   * Clears all detected trips and resets the tripped equipment set.
   */
  public void reset() {
    detectedTrips.clear();
    trippedEquipment.clear();
  }

  /**
   * Reads a parameter value from the process system.
   *
   * <p>
   * Tries the automation API first for string-addressable access, then falls back to direct
   * equipment access for common parameter names.
   * </p>
   *
   * @param equipmentName equipment name
   * @param parameterName parameter name
   * @return parameter value, or Double.NaN if not found
   */
  private double readParameter(String equipmentName, String parameterName) {
    try {
      ProcessEquipmentInterface equipment = null;
      for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
        if (eq.getName().equals(equipmentName)) {
          equipment = eq;
          break;
        }
      }
      if (equipment == null) {
        return Double.NaN;
      }

      // Try common parameter names via unified outlet property access
      String lowerParam = parameterName.toLowerCase();
      if (lowerParam.contains("pressure")) {
        return equipment.getOutletPressure("bara");
      } else if (lowerParam.contains("temperature")) {
        return equipment.getOutletTemperature("C");
      } else if (lowerParam.contains("flow")) {
        return equipment.getOutletFlowRate("kg/hr");
      }

      return Double.NaN;
    } catch (Exception e) {
      logger.debug("Could not read parameter {}.{}: {}", equipmentName, parameterName,
          e.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Returns a JSON representation of all detected trips.
   *
   * @return JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"tripCount\": ").append(detectedTrips.size()).append(", \"trips\": [");
    for (int i = 0; i < detectedTrips.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(detectedTrips.get(i).toJson());
    }
    sb.append("]}");
    return sb.toString();
  }
}
