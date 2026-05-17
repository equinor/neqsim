package neqsim.process.equipment.heatexchanger.heatintegration;

import java.io.Serializable;

/**
 * Represents a single process stream for heat integration (pinch) analysis.
 *
 * <p>
 * A heat stream has a supply temperature, target temperature, heat capacity flow rate (MCp), and
 * can be classified as HOT (needs cooling) or COLD (needs heating).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class HeatStream implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Stream type enumeration.
   */
  public enum StreamType {
    /** Hot stream — needs cooling (supply T greater than target T). */
    HOT,
    /** Cold stream — needs heating (target T greater than supply T). */
    COLD
  }

  private String name;
  private double supplyTemperature; // Kelvin
  private double targetTemperature; // Kelvin
  private double heatCapacityFlowRate; // kW/K (MCp = mass_flow * Cp)
  private StreamType type;

  /**
   * Constructor for HeatStream.
   *
   * @param name stream name
   * @param supplyTemperature_C supply temperature in Celsius
   * @param targetTemperature_C target temperature in Celsius
   * @param heatCapacityFlowRate_kWperK heat capacity flow rate in kW/K
   */
  public HeatStream(String name, double supplyTemperature_C, double targetTemperature_C,
      double heatCapacityFlowRate_kWperK) {
    this.name = name;
    this.supplyTemperature = supplyTemperature_C + 273.15;
    this.targetTemperature = targetTemperature_C + 273.15;
    this.heatCapacityFlowRate = heatCapacityFlowRate_kWperK;

    if (supplyTemperature_C > targetTemperature_C) {
      this.type = StreamType.HOT;
    } else {
      this.type = StreamType.COLD;
    }
  }

  /**
   * Get the enthalpy change of this stream in kW.
   *
   * @return enthalpy change in kW (positive for hot streams releasing heat)
   */
  public double getEnthalpyChange() {
    return heatCapacityFlowRate * Math.abs(supplyTemperature - targetTemperature);
  }

  /**
   * Get the stream name.
   *
   * @return name of this heat stream
   */
  public String getName() {
    return name;
  }

  /**
   * Set the stream name.
   *
   * @param name name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Get the supply temperature in Kelvin.
   *
   * @return supply temperature in Kelvin
   */
  public double getSupplyTemperature() {
    return supplyTemperature;
  }

  /**
   * Get the supply temperature in Celsius.
   *
   * @return supply temperature in Celsius
   */
  public double getSupplyTemperatureC() {
    return supplyTemperature - 273.15;
  }

  /**
   * Get the target temperature in Kelvin.
   *
   * @return target temperature in Kelvin
   */
  public double getTargetTemperature() {
    return targetTemperature;
  }

  /**
   * Get the target temperature in Celsius.
   *
   * @return target temperature in Celsius
   */
  public double getTargetTemperatureC() {
    return targetTemperature - 273.15;
  }

  /**
   * Get the heat capacity flow rate (MCp).
   *
   * @return heat capacity flow rate in kW/K
   */
  public double getHeatCapacityFlowRate() {
    return heatCapacityFlowRate;
  }

  /**
   * Set the heat capacity flow rate (MCp).
   *
   * @param heatCapacityFlowRate_kWperK heat capacity flow rate in kW/K
   */
  public void setHeatCapacityFlowRate(double heatCapacityFlowRate_kWperK) {
    this.heatCapacityFlowRate = heatCapacityFlowRate_kWperK;
  }

  /**
   * Get the stream type (HOT or COLD).
   *
   * @return stream type
   */
  public StreamType getType() {
    return type;
  }

  /**
   * Set supply temperature in Celsius.
   *
   * @param supplyTemp_C supply temperature in Celsius
   */
  public void setSupplyTemperatureC(double supplyTemp_C) {
    this.supplyTemperature = supplyTemp_C + 273.15;
    this.type =
        (this.supplyTemperature >= this.targetTemperature) ? StreamType.HOT : StreamType.COLD;
  }

  /**
   * Set target temperature in Celsius.
   *
   * @param targetTemp_C target temperature in Celsius
   */
  public void setTargetTemperatureC(double targetTemp_C) {
    this.targetTemperature = targetTemp_C + 273.15;
    this.type =
        (this.supplyTemperature >= this.targetTemperature) ? StreamType.HOT : StreamType.COLD;
  }
}
