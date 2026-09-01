package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opening or closing terminal tank inventory with component closure.
 */
public class TankInventoryState implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String tankName;
  private final double massKg;
  private final double capacityKg;
  private final double heelKg;
  private final double ullageKg;
  private final boolean available;
  private final String mixingMode;
  private final Map<String, Double> componentMassKg;
  private final Map<String, Double> parcelMassKg;

  /**
   * Create an inventory state.
   *
   * @param tankName tank
   * @param massKg inventory
   * @param capacityKg capacity
   * @param heelKg minimum heel
   * @param available availability
   * @param mixingMode mixing mode
   * @param componentMassKg components
   * @param parcelMassKg parcel identities
   */
  public TankInventoryState(String tankName, double massKg, double capacityKg, double heelKg, boolean available,
      String mixingMode, Map<String, Double> componentMassKg, Map<String, Double> parcelMassKg) {
    this.tankName = tankName;
    this.massKg = massKg;
    this.capacityKg = capacityKg;
    this.heelKg = heelKg;
    this.ullageKg = capacityKg - massKg;
    this.available = available;
    this.mixingMode = mixingMode;
    this.componentMassKg = new LinkedHashMap<String, Double>(componentMassKg);
    this.parcelMassKg = new LinkedHashMap<String, Double>(parcelMassKg);
  }

  /** @return tank name */
  public String getTankName() {
    return tankName;
  }

  /** @return inventory mass */
  public double getMassKg() {
    return massKg;
  }

  /** @return capacity */
  public double getCapacityKg() {
    return capacityKg;
  }

  /** @return minimum heel */
  public double getHeelKg() {
    return heelKg;
  }

  /** @return available ullage */
  public double getUllageKg() {
    return ullageKg;
  }

  /** @return availability */
  public boolean isAvailable() {
    return available;
  }

  /** @return mixing mode */
  public String getMixingMode() {
    return mixingMode;
  }

  /** @return component masses */
  public Map<String, Double> getComponentMassKg() {
    return Collections.unmodifiableMap(componentMassKg);
  }

  /** @return retained parcel identities and masses */
  public Map<String, Double> getParcelMassKg() {
    return Collections.unmodifiableMap(parcelMassKg);
  }
}
