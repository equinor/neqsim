package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Serializable multi-period gas network schedule result.
 */
public class NetworkScheduleResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final boolean feasible;
  private final double objectiveValue;
  private final List<NetworkSchedulePeriodResult> periods;
  private final Map<String, GasLinepackState> initialLinepack;
  private final Map<String, GasLinepackState> terminalLinepack;
  private final Map<String, Double> activeConstraints;
  private final String message;

  /**
   * Create a schedule result.
   *
   * @param feasible feasibility
   * @param objectiveValue objective
   * @param periods period results
   * @param initialLinepack initial states
   * @param terminalLinepack terminal states
   * @param activeConstraints binding/violated residuals
   * @param message diagnostic
   */
  public NetworkScheduleResult(boolean feasible, double objectiveValue, List<NetworkSchedulePeriodResult> periods,
      Map<String, GasLinepackState> initialLinepack, Map<String, GasLinepackState> terminalLinepack,
      Map<String, Double> activeConstraints, String message) {
    this.feasible = feasible;
    this.objectiveValue = objectiveValue;
    this.periods = new ArrayList<NetworkSchedulePeriodResult>(periods);
    this.initialLinepack = new LinkedHashMap<String, GasLinepackState>(initialLinepack);
    this.terminalLinepack = new LinkedHashMap<String, GasLinepackState>(terminalLinepack);
    this.activeConstraints = new LinkedHashMap<String, Double>(activeConstraints);
    this.message = message;
  }

  /** @return schedule feasibility */
  public boolean isFeasible() {
    return feasible;
  }

  /** @return scalar objective value */
  public double getObjectiveValue() {
    return objectiveValue;
  }

  /** @return immutable period results */
  public List<NetworkSchedulePeriodResult> getPeriods() {
    return Collections.unmodifiableList(periods);
  }

  /** @return explicit initial linepack */
  public Map<String, GasLinepackState> getInitialLinepack() {
    return Collections.unmodifiableMap(initialLinepack);
  }

  /** @return terminal linepack */
  public Map<String, GasLinepackState> getTerminalLinepack() {
    return Collections.unmodifiableMap(terminalLinepack);
  }

  /** @return active or violated constraints */
  public Map<String, Double> getActiveConstraints() {
    return Collections.unmodifiableMap(activeConstraints);
  }

  /** @return diagnostic */
  public String getMessage() {
    return message;
  }

  /** @return stable JSON */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }

  /**
   * Restore a result from JSON.
   *
   * @param json serialized result
   * @return result
   */
  public static NetworkScheduleResult fromJson(String json) {
    return new Gson().fromJson(json, NetworkScheduleResult.class);
  }
}
