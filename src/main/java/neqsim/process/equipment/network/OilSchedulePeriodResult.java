package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Terminal inventories, movements, cargoes, and residuals for one period.
 */
public class OilSchedulePeriodResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final NetworkPeriod period;
  private final boolean feasible;
  private final Map<String, TankInventoryState> openingInventories;
  private final Map<String, TankInventoryState> closingInventories;
  private final List<String> receivedParcels;
  private final List<CargoLoadingResult> loadedCargoes;
  private final Map<String, Double> constraintResiduals;

  /**
   * Create a period result.
   *
   * @param period period
   * @param feasible feasibility
   * @param openingInventories opening tanks
   * @param closingInventories closing tanks
   * @param receivedParcels receipt identifiers
   * @param loadedCargoes cargo results
   * @param constraintResiduals residuals
   */
  public OilSchedulePeriodResult(NetworkPeriod period, boolean feasible,
      Map<String, TankInventoryState> openingInventories, Map<String, TankInventoryState> closingInventories,
      List<String> receivedParcels, List<CargoLoadingResult> loadedCargoes, Map<String, Double> constraintResiduals) {
    this.period = period;
    this.feasible = feasible;
    this.openingInventories = new LinkedHashMap<String, TankInventoryState>(openingInventories);
    this.closingInventories = new LinkedHashMap<String, TankInventoryState>(closingInventories);
    this.receivedParcels = new ArrayList<String>(receivedParcels);
    this.loadedCargoes = new ArrayList<CargoLoadingResult>(loadedCargoes);
    this.constraintResiduals = new LinkedHashMap<String, Double>(constraintResiduals);
  }

  /** @return period */
  public NetworkPeriod getPeriod() {
    return period;
  }

  /** @return feasibility */
  public boolean isFeasible() {
    return feasible;
  }

  /** @return opening inventories */
  public Map<String, TankInventoryState> getOpeningInventories() {
    return Collections.unmodifiableMap(openingInventories);
  }

  /** @return closing inventories */
  public Map<String, TankInventoryState> getClosingInventories() {
    return Collections.unmodifiableMap(closingInventories);
  }

  /** @return received parcel identifiers */
  public List<String> getReceivedParcels() {
    return Collections.unmodifiableList(receivedParcels);
  }

  /** @return loaded cargoes */
  public List<CargoLoadingResult> getLoadedCargoes() {
    return Collections.unmodifiableList(loadedCargoes);
  }

  /** @return residuals */
  public Map<String, Double> getConstraintResiduals() {
    return Collections.unmodifiableMap(constraintResiduals);
  }
}
