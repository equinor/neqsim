package neqsim.process.equipment.heatexchanger;

import java.io.Serializable;
import neqsim.util.unit.TemperatureUnit;

/**
 * Specification object describing the utility side of a single-stream heater or cooler.
 *
 * <p>This class stores the supply and return temperatures together with optional minimum
 * approach, heat-capacity rate and assumed overall heat-transfer coefficient so the mechanical
 * design package can derive an approximate size when only the process stream has been simulated.</p>
 */
public class UtilityStreamSpecification implements Serializable {
  private static final long serialVersionUID = 1000L;

  private double supplyTemperature = Double.NaN; // Kelvin
  private double returnTemperature = Double.NaN; // Kelvin
  private double approachTemperature = Double.NaN; // Kelvin difference
  private double heatCapacityRate = Double.NaN; // W/K
  private double overallHeatTransferCoefficient = Double.NaN; // W/(m^2*K)

  /**
   * Returns the utility supply temperature in Kelvin.
   *
   * @return supply temperature in Kelvin
   */
  public double getSupplyTemperature() {
    return supplyTemperature;
  }

  /**
   * Returns the utility return temperature in Kelvin.
   *
   * @return return temperature in Kelvin
   */
  public double getReturnTemperature() {
    return returnTemperature;
  }

  /**
   * Returns the minimum approach temperature in Kelvin.
   *
   * @return approach temperature in Kelvin
   */
  public double getApproachTemperature() {
    return approachTemperature;
  }

  /**
   * Returns the utility heat-capacity rate in W/K.
   *
   * @return heat-capacity rate in W/K
   */
  public double getHeatCapacityRate() {
    return heatCapacityRate;
  }

  /**
   * Returns the assumed overall heat-transfer coefficient in W/(m^2*K).
   *
   * @return overall heat-transfer coefficient in W/(m^2*K)
   */
  public double getOverallHeatTransferCoefficient() {
    return overallHeatTransferCoefficient;
  }

  /**
   * Set the utility supply temperature in Kelvin.
   *
   * @param temperature supply temperature in Kelvin
   */
  public void setSupplyTemperature(double temperature) {
    this.supplyTemperature = temperature;
  }

  /**
   * Set the utility supply temperature using the specified unit.
   *
   * @param temperature supply temperature value
   * @param unit unit of the supplied temperature
   */
  public void setSupplyTemperature(double temperature, String unit) {
    this.supplyTemperature = new TemperatureUnit(temperature, unit).getValue("K");
  }

  /**
   * Set the utility return temperature in Kelvin.
   *
   * @param temperature return temperature in Kelvin
   */
  public void setReturnTemperature(double temperature) {
    this.returnTemperature = temperature;
  }

  /**
   * Set the utility return temperature using the specified unit.
   *
   * @param temperature return temperature value
   * @param unit unit of the supplied temperature
   */
  public void setReturnTemperature(double temperature, String unit) {
    this.returnTemperature = new TemperatureUnit(temperature, unit).getValue("K");
  }

  /**
   * Set the minimum approach temperature (absolute difference) in Kelvin.
   *
   * @param approach approach temperature in Kelvin
   */
  public void setApproachTemperature(double approach) {
    this.approachTemperature = approach;
  }

  /**
   * Set the minimum approach temperature (absolute difference) using the specified unit.
   *
   * @param approach approach temperature value
   * @param unit unit of the supplied temperature difference
   */
  public void setApproachTemperature(double approach, String unit) {
    switch (unit) {
      case "K":
        this.approachTemperature = approach;
        break;
      case "C":
        this.approachTemperature = approach;
        break;
      case "F":
        this.approachTemperature = approach * 5.0 / 9.0;
        break;
      case "R":
        this.approachTemperature = approach * 5.0 / 9.0;
        break;
      default:
        throw new IllegalArgumentException("Unsupported unit for temperature difference: " + unit);
    }
  }

  /**
   * Set the utility heat-capacity rate in W/K.
   *
   * @param heatCapacityRate heat-capacity rate in W/K
   */
  public void setHeatCapacityRate(double heatCapacityRate) {
    this.heatCapacityRate = heatCapacityRate;
  }

  /**
   * Set the assumed overall heat-transfer coefficient in W/(m^2*K).
   *
   * @param overallHeatTransferCoefficient overall heat-transfer coefficient in W/(m^2*K)
   */
  public void setOverallHeatTransferCoefficient(double overallHeatTransferCoefficient) {
    this.overallHeatTransferCoefficient = overallHeatTransferCoefficient;
  }

  /** Returns true if a supply temperature has been specified. */
  public boolean hasSupplyTemperature() {
    return !Double.isNaN(supplyTemperature);
  }

  /** Returns true if a return temperature has been specified. */
  public boolean hasReturnTemperature() {
    return !Double.isNaN(returnTemperature);
  }

  /** Returns true if a minimum approach temperature has been specified. */
  public boolean hasApproachTemperature() {
    return !Double.isNaN(approachTemperature);
  }

  /** Returns true if a heat-capacity rate has been specified. */
  public boolean hasHeatCapacityRate() {
    return !Double.isNaN(heatCapacityRate) && heatCapacityRate > 0.0;
  }

  /** Returns true if an overall heat-transfer coefficient has been specified. */
  public boolean hasOverallHeatTransferCoefficient() {
    return !Double.isNaN(overallHeatTransferCoefficient) && overallHeatTransferCoefficient > 0.0;
  }
}
