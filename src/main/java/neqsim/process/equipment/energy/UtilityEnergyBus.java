package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.UtilityLevel;

/**
 * Thermal {@link EnergyBus} carrying a standard utility grade and supply/return temperatures.
 *
 * @author NeqSim
 * @version 1.0
 */
public class UtilityEnergyBus extends EnergyBus {
  private static final long serialVersionUID = 1000L;

  private double returnTemperature = Double.NaN;

  /**
   * Creates a utility bus.
   *
   * @param name bus name
   * @param utilityLevel utility grade
   */
  public UtilityEnergyBus(String name, UtilityLevel utilityLevel) {
    super(name, EnergyType.HEAT);
    getQuality().setUtilityLevel(utilityLevel);
  }

  /**
   * Creates a utility bus with explicit supply and return temperatures.
   *
   * @param name bus name
   * @param utilityLevel utility grade
   * @param supplyTemperature supply temperature in K
   * @param returnTemperature return temperature in K
   */
  public UtilityEnergyBus(String name, UtilityLevel utilityLevel, double supplyTemperature, double returnTemperature) {
    this(name, utilityLevel);
    getQuality().setTemperature(supplyTemperature);
    setReturnTemperature(returnTemperature);
  }

  /**
   * Gets supply temperature.
   *
   * @return temperature in K, or {@link Double#NaN} when unspecified
   */
  public double getSupplyTemperature() {
    return getQuality().getTemperature();
  }

  /**
   * Sets supply temperature.
   *
   * @param supplyTemperature temperature in K
   */
  public void setSupplyTemperature(double supplyTemperature) {
    getQuality().setTemperature(supplyTemperature);
  }

  /**
   * Gets return temperature.
   *
   * @return temperature in K, or {@link Double#NaN} when unspecified
   */
  public double getReturnTemperature() {
    return returnTemperature;
  }

  /**
   * Sets return temperature.
   *
   * @param returnTemperature temperature in K
   */
  public void setReturnTemperature(double returnTemperature) {
    if (!Double.isFinite(returnTemperature) || returnTemperature <= 0.0) {
      throw new IllegalArgumentException("Return temperature must be positive and finite");
    }
    this.returnTemperature = returnTemperature;
  }

  /**
   * Gets utility grade.
   *
   * @return utility grade
   */
  public UtilityLevel getUtilityLevel() {
    return getQuality().getUtilityLevel();
  }
}
