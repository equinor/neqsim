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
 * Serializable oil terminal, parcel, blend, and cargo schedule result.
 */
public class OilNetworkScheduleResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final boolean feasible;
  private final List<OilSchedulePeriodResult> periods;
  private final Map<String, CargoLoadingResult> cargoes;
  private final Map<String, TankInventoryState> terminalInventories;
  private final double massBalanceResidualKg;
  private final double maxComponentBalanceResidualKg;
  private final Map<String, Double> activeConstraints;
  private final String message;

  /**
   * Create a schedule result.
   *
   * @param feasible feasibility
   * @param periods period results
   * @param cargoes cargo results
   * @param terminalInventories terminal inventories
   * @param massBalanceResidualKg mass closure
   * @param maxComponentBalanceResidualKg component closure
   * @param activeConstraints residuals
   * @param message diagnostic
   */
  public OilNetworkScheduleResult(boolean feasible, List<OilSchedulePeriodResult> periods,
      Map<String, CargoLoadingResult> cargoes, Map<String, TankInventoryState> terminalInventories,
      double massBalanceResidualKg, double maxComponentBalanceResidualKg, Map<String, Double> activeConstraints,
      String message) {
    this.feasible = feasible;
    this.periods = new ArrayList<OilSchedulePeriodResult>(periods);
    this.cargoes = new LinkedHashMap<String, CargoLoadingResult>(cargoes);
    this.terminalInventories = new LinkedHashMap<String, TankInventoryState>(terminalInventories);
    this.massBalanceResidualKg = massBalanceResidualKg;
    this.maxComponentBalanceResidualKg = maxComponentBalanceResidualKg;
    this.activeConstraints = new LinkedHashMap<String, Double>(activeConstraints);
    this.message = message;
  }

  /** @return feasibility */
  public boolean isFeasible() {
    return feasible;
  }

  /** @return period results */
  public List<OilSchedulePeriodResult> getPeriods() {
    return Collections.unmodifiableList(periods);
  }

  /** @return cargo results */
  public Map<String, CargoLoadingResult> getCargoes() {
    return Collections.unmodifiableMap(cargoes);
  }

  /** @return terminal inventories */
  public Map<String, TankInventoryState> getTerminalInventories() {
    return Collections.unmodifiableMap(terminalInventories);
  }

  /** @return total mass closure residual */
  public double getMassBalanceResidualKg() {
    return massBalanceResidualKg;
  }

  /** @return largest component closure residual */
  public double getMaxComponentBalanceResidualKg() {
    return maxComponentBalanceResidualKg;
  }

  /** @return active/violated constraints */
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
  public static OilNetworkScheduleResult fromJson(String json) {
    return new Gson().fromJson(json, OilNetworkScheduleResult.class);
  }
}
