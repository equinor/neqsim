package neqsim.process.equipment.battery;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Simple battery storage unit maintaining a state of charge.
 *
 * <p>The unit can be charged or discharged by supplying a power level and a
 * duration. The state of charge is updated accordingly and the energy stream of
 * the unit reflects the power usage (positive duty while charging and negative
 * when discharging).</p>
 */
public class BatteryStorage extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double capacity; // [J]
  private double stateOfCharge; // [J]
  private double chargeEfficiency = 0.95;
  private double dischargeEfficiency = 0.95;
  private double currentPower = 0.0; // [W]

  /**
   * Construct a battery with the given name and capacity.
   *
   * @param name name of the unit
   * @param capacity maximum energy capacity [J]
   */
  public BatteryStorage(String name, double capacity) {
    super(name);
    this.capacity = capacity;
    this.stateOfCharge = 0.0;
  }

  /**
   * Default constructor with zero capacity.
   */
  public BatteryStorage() {
    this("BatteryStorage", 0.0);
  }

  /**
   * Construct a battery with a given name and zero capacity.
   *
   * @param name name of the unit
   */
  public BatteryStorage(String name) {
    this(name, 0.0);
  }

  /**
   * Charge the battery.
   *
   * @param power charging power [W]
   * @param hours duration of charging [h]
   */
  public void charge(double power, double hours) {
    double energyIn = power * hours * chargeEfficiency;
    stateOfCharge = Math.min(capacity, stateOfCharge + energyIn);
    currentPower = -power;
  }

  /**
   * Discharge the battery.
   *
   * @param power requested power [W]
   * @param hours duration of discharge [h]
   * @return actual delivered power [W]
   */
  public double discharge(double power, double hours) {
    double energyNeeded = power * hours / dischargeEfficiency;
    double actualPower = power;
    if (energyNeeded > stateOfCharge && hours > 0.0) {
      actualPower = stateOfCharge * dischargeEfficiency / hours;
      energyNeeded = stateOfCharge;
    }
    stateOfCharge = Math.max(0.0, stateOfCharge - energyNeeded);
    currentPower = actualPower;
    return actualPower;
  }

  /**
   * Get state of charge [J].
   *
   * @return current stored energy
   */
  public double getStateOfCharge() {
    return stateOfCharge;
  }

  /**
   * Set state of charge [J].
   *
   * @param soc stored energy
   */
  public void setStateOfCharge(double soc) {
    stateOfCharge = Math.max(0.0, Math.min(capacity, soc));
  }

  /**
   * Get capacity [J].
   *
   * @return maximum storage capacity
   */
  public double getCapacity() {
    return capacity;
  }

  /**
   * Set capacity [J].
   *
   * @param capacity maximum storage capacity
   */
  public void setCapacity(double capacity) {
    this.capacity = capacity;
    stateOfCharge = Math.min(stateOfCharge, capacity);
  }

  /**
   * Get state of charge as a fraction of capacity.
   *
   * @return state of charge [0-1]
   */
  public double getStateOfChargeFraction() {
    return capacity > 0.0 ? stateOfCharge / capacity : 0.0;
  }

  @Override
  public void run(UUID id) {
    getEnergyStream().setDuty(-currentPower);
    setCalculationIdentifier(id);
  }
}

