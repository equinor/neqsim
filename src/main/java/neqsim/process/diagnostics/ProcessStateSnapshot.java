package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.processmodel.lifecycle.ProcessSystemState;

/**
 * Captures and compares two process states — the last known good state and the trip state.
 *
 * <p>
 * This class wraps two {@link ProcessSystemState} snapshots and provides structured comparison via
 * the {@link #diff()} method, identifying which equipment parameters changed, which variables
 * deviated significantly, and what the magnitude of changes were.
 * </p>
 *
 * <p>
 * Used by {@link RootCauseAnalyzer} to evaluate hypotheses against the evidence of what changed
 * between steady-state operation and the trip condition.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ProcessStateSnapshot implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  /** Default relative threshold for flagging significant deviations. */
  private static final double DEFAULT_RELATIVE_THRESHOLD = 0.05;
  /** Default absolute threshold for flagging significant temperature deviations (K). */
  private static final double DEFAULT_TEMP_ABS_THRESHOLD = 2.0;
  /** Default absolute threshold for flagging significant pressure deviations (bar). */
  private static final double DEFAULT_PRESSURE_ABS_THRESHOLD = 0.5;

  private final ProcessSystemState lastGoodState;
  private final ProcessSystemState tripState;
  private final double tripTimestamp;
  private StateDiff cachedDiff;

  /**
   * Constructs a snapshot from two process states.
   *
   * @param lastGoodState the last known good state before the trip
   * @param tripState the state at or immediately after the trip
   * @param tripTimestamp simulation time in seconds when the trip occurred
   */
  public ProcessStateSnapshot(ProcessSystemState lastGoodState, ProcessSystemState tripState,
      double tripTimestamp) {
    this.lastGoodState = lastGoodState;
    this.tripState = tripState;
    this.tripTimestamp = tripTimestamp;
  }

  /**
   * Returns the last known good process state.
   *
   * @return the pre-trip state
   */
  public ProcessSystemState getLastGoodState() {
    return lastGoodState;
  }

  /**
   * Returns the process state at trip time.
   *
   * @return the trip state
   */
  public ProcessSystemState getTripState() {
    return tripState;
  }

  /**
   * Returns the simulation timestamp of the trip.
   *
   * @return timestamp in seconds
   */
  public double getTripTimestamp() {
    return tripTimestamp;
  }

  /**
   * Computes the structured difference between the last good state and the trip state.
   *
   * <p>
   * Results are cached after first computation.
   * </p>
   *
   * @return a {@link StateDiff} summarising all deviations
   */
  public StateDiff diff() {
    if (cachedDiff != null) {
      return cachedDiff;
    }
    cachedDiff = computeDiff();
    return cachedDiff;
  }

  /**
   * Internal diff computation.
   *
   * @return computed state diff
   */
  private StateDiff computeDiff() {
    StateDiff result = new StateDiff();

    if (lastGoodState == null || tripState == null) {
      return result;
    }

    // Compare equipment states
    Map<String, ProcessSystemState.EquipmentState> goodEquipment = buildEquipmentMap(lastGoodState);
    Map<String, ProcessSystemState.EquipmentState> tripEquipment = buildEquipmentMap(tripState);

    for (Map.Entry<String, ProcessSystemState.EquipmentState> entry : goodEquipment.entrySet()) {
      String name = entry.getKey();
      ProcessSystemState.EquipmentState goodEq = entry.getValue();
      ProcessSystemState.EquipmentState tripEq = tripEquipment.get(name);

      if (tripEq == null) {
        result.addDeviation(new Deviation(name, "equipment", "Equipment missing in trip state", 0.0,
            0.0, DeviationSignificance.HIGH));
        continue;
      }

      // Compare numeric properties
      Map<String, Double> goodNums = goodEq.getNumericProperties();
      Map<String, Double> tripNums = tripEq.getNumericProperties();
      if (goodNums != null && tripNums != null) {
        for (Map.Entry<String, Double> numEntry : goodNums.entrySet()) {
          String propName = numEntry.getKey();
          Double goodVal = numEntry.getValue();
          Double tripVal = tripNums.get(propName);
          if (goodVal != null && tripVal != null && !goodVal.equals(tripVal)) {
            double absChange = tripVal - goodVal;
            double relChange =
                Math.abs(goodVal) > 1e-10 ? Math.abs(absChange / goodVal) : Math.abs(absChange);
            DeviationSignificance significance = classifySignificance(propName, relChange);
            if (significance != DeviationSignificance.NEGLIGIBLE) {
              result.addDeviation(new Deviation(name + "." + propName, "numericProperty",
                  String.format("Changed from %.4f to %.4f (%.1f%%)", goodVal, tripVal,
                      relChange * 100.0),
                  goodVal, tripVal, significance));
            }
          }
        }
      }
    }

    // Compare stream states
    Map<String, ProcessSystemState.StreamState> goodStreams = getStreamMap(lastGoodState);
    Map<String, ProcessSystemState.StreamState> tripStreams = getStreamMap(tripState);

    if (goodStreams != null && tripStreams != null) {
      for (Map.Entry<String, ProcessSystemState.StreamState> entry : goodStreams.entrySet()) {
        String streamName = entry.getKey();
        ProcessSystemState.StreamState goodStream = entry.getValue();
        ProcessSystemState.StreamState tripStream = tripStreams.get(streamName);
        if (tripStream != null) {
          compareStreamStates(streamName, goodStream, tripStream, result);
        }
      }
    }

    return result;
  }

  /**
   * Compares two stream states and adds deviations to the result.
   *
   * @param streamName name of the stream
   * @param good the good state
   * @param trip the trip state
   * @param result the diff to populate
   */
  private void compareStreamStates(String streamName, ProcessSystemState.StreamState good,
      ProcessSystemState.StreamState trip, StateDiff result) {
    // Temperature
    double goodT = good.getTemperature();
    double tripT = trip.getTemperature();
    if (Math.abs(tripT - goodT) > DEFAULT_TEMP_ABS_THRESHOLD) {
      result.addDeviation(new Deviation(streamName + ".temperature", "stream",
          String.format("Temperature changed from %.2f to %.2f K", goodT, tripT), goodT, tripT,
          Math.abs(tripT - goodT) > 10.0 ? DeviationSignificance.HIGH
              : DeviationSignificance.MEDIUM));
    }

    // Pressure
    double goodP = good.getPressure();
    double tripP = trip.getPressure();
    if (Math.abs(tripP - goodP) > DEFAULT_PRESSURE_ABS_THRESHOLD) {
      double relP = Math.abs(goodP) > 1e-10 ? Math.abs((tripP - goodP) / goodP) : 0.0;
      result.addDeviation(new Deviation(streamName + ".pressure", "stream",
          String.format("Pressure changed from %.2f to %.2f bar", goodP, tripP), goodP, tripP,
          relP > 0.10 ? DeviationSignificance.HIGH : DeviationSignificance.MEDIUM));
    }

    // Flow rate
    double goodFlow = good.getMolarFlowRate();
    double tripFlow = trip.getMolarFlowRate();
    if (Math.abs(goodFlow) > 1e-10) {
      double relFlow = Math.abs((tripFlow - goodFlow) / goodFlow);
      if (relFlow > DEFAULT_RELATIVE_THRESHOLD) {
        result.addDeviation(new Deviation(streamName + ".flowRate", "stream",
            String.format("Flow changed from %.4f to %.4f (%.1f%%)", goodFlow, tripFlow,
                relFlow * 100.0),
            goodFlow, tripFlow,
            relFlow > 0.20 ? DeviationSignificance.HIGH : DeviationSignificance.MEDIUM));
      }
    }
  }

  /**
   * Classifies the significance of a deviation based on its property name and relative change.
   *
   * @param propName property name
   * @param relChange relative change magnitude
   * @return significance level
   */
  private DeviationSignificance classifySignificance(String propName, double relChange) {
    if (relChange < 0.01) {
      return DeviationSignificance.NEGLIGIBLE;
    } else if (relChange < 0.05) {
      return DeviationSignificance.LOW;
    } else if (relChange < 0.15) {
      return DeviationSignificance.MEDIUM;
    } else {
      return DeviationSignificance.HIGH;
    }
  }

  /**
   * Builds a map of equipment name to equipment state from a ProcessSystemState.
   *
   * @param state the process system state
   * @return map of equipment name to EquipmentState
   */
  private Map<String, ProcessSystemState.EquipmentState> buildEquipmentMap(
      ProcessSystemState state) {
    Map<String, ProcessSystemState.EquipmentState> map = new LinkedHashMap<>();
    List<ProcessSystemState.EquipmentState> eqStates = state.getEquipmentStates();
    if (eqStates != null) {
      for (ProcessSystemState.EquipmentState eq : eqStates) {
        map.put(eq.getName(), eq);
      }
    }
    return map;
  }

  /**
   * Gets the stream state map from a ProcessSystemState.
   *
   * @param state the process system state
   * @return map of stream name to StreamState, or null if not available
   */
  private Map<String, ProcessSystemState.StreamState> getStreamMap(ProcessSystemState state) {
    return state.getStreamStates();
  }

  /**
   * Serialises the snapshot summary to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("tripTimestamp", tripTimestamp);
    map.put("hasLastGoodState", lastGoodState != null);
    map.put("hasTripState", tripState != null);
    StateDiff d = diff();
    map.put("totalDeviations", d.getDeviations().size());
    map.put("highSignificanceCount",
        d.getDeviationsBySignificance(DeviationSignificance.HIGH).size());
    map.put("deviations", d.getDeviations());
    return GSON.toJson(map);
  }

  /**
   * Significance level for a state deviation.
   */
  public enum DeviationSignificance {
    /** Change too small to be meaningful. */
    NEGLIGIBLE,
    /** Minor change, unlikely to be trip-related. */
    LOW,
    /** Moderate change, potentially relevant. */
    MEDIUM,
    /** Large change, likely relevant to the trip. */
    HIGH
  }

  /**
   * A single measured deviation between the last good state and the trip state.
   *
   * @author esol
   * @version 1.0
   */
  public static class Deviation implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String variableAddress;
    private final String category;
    private final String description;
    private final double goodValue;
    private final double tripValue;
    private final DeviationSignificance significance;

    /**
     * Constructs a deviation record.
     *
     * @param variableAddress the variable or property path that changed
     * @param category the category (e.g. "stream", "numericProperty", "equipment")
     * @param description human-readable description of the change
     * @param goodValue value in the last good state
     * @param tripValue value in the trip state
     * @param significance how significant the change is
     */
    public Deviation(String variableAddress, String category, String description, double goodValue,
        double tripValue, DeviationSignificance significance) {
      this.variableAddress = variableAddress;
      this.category = category;
      this.description = description;
      this.goodValue = goodValue;
      this.tripValue = tripValue;
      this.significance = significance;
    }

    /**
     * Returns the variable address.
     *
     * @return variable address string
     */
    public String getVariableAddress() {
      return variableAddress;
    }

    /**
     * Returns the category.
     *
     * @return category string
     */
    public String getCategory() {
      return category;
    }

    /**
     * Returns the description.
     *
     * @return description string
     */
    public String getDescription() {
      return description;
    }

    /**
     * Returns the value in the last good state.
     *
     * @return good state value
     */
    public double getGoodValue() {
      return goodValue;
    }

    /**
     * Returns the value in the trip state.
     *
     * @return trip state value
     */
    public double getTripValue() {
      return tripValue;
    }

    /**
     * Returns the significance level.
     *
     * @return significance enum value
     */
    public DeviationSignificance getSignificance() {
      return significance;
    }

    /**
     * Returns the absolute change.
     *
     * @return tripValue - goodValue
     */
    public double getAbsoluteChange() {
      return tripValue - goodValue;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s: %s", significance, variableAddress, description);
    }
  }

  /**
   * Structured result of comparing two process states.
   *
   * @author esol
   * @version 1.0
   */
  public static class StateDiff implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<Deviation> deviations = new ArrayList<>();

    /**
     * Constructs an empty state diff.
     */
    public StateDiff() {}

    /**
     * Adds a deviation to the diff.
     *
     * @param deviation the deviation to add
     */
    public void addDeviation(Deviation deviation) {
      deviations.add(deviation);
    }

    /**
     * Returns all deviations.
     *
     * @return unmodifiable list of deviations
     */
    public List<Deviation> getDeviations() {
      return Collections.unmodifiableList(deviations);
    }

    /**
     * Returns deviations filtered by significance level.
     *
     * @param significance the significance to filter by
     * @return list of deviations at that significance level
     */
    public List<Deviation> getDeviationsBySignificance(DeviationSignificance significance) {
      List<Deviation> result = new ArrayList<>();
      for (Deviation d : deviations) {
        if (d.getSignificance() == significance) {
          result.add(d);
        }
      }
      return result;
    }

    /**
     * Returns deviations filtered by variable address substring.
     *
     * @param addressSubstring substring to match against variable addresses
     * @return list of matching deviations
     */
    public List<Deviation> getDeviationsMatching(String addressSubstring) {
      List<Deviation> result = new ArrayList<>();
      for (Deviation d : deviations) {
        if (d.getVariableAddress().contains(addressSubstring)) {
          result.add(d);
        }
      }
      return result;
    }

    /**
     * Returns whether there are any significant (MEDIUM or HIGH) deviations.
     *
     * @return true if significant deviations exist
     */
    public boolean hasSignificantDeviations() {
      for (Deviation d : deviations) {
        if (d.getSignificance() == DeviationSignificance.MEDIUM
            || d.getSignificance() == DeviationSignificance.HIGH) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns the total number of deviations.
     *
     * @return count
     */
    public int size() {
      return deviations.size();
    }

    @Override
    public String toString() {
      return "StateDiff{deviations=" + deviations.size() + ", highCount="
          + getDeviationsBySignificance(DeviationSignificance.HIGH).size() + "}";
    }
  }
}
