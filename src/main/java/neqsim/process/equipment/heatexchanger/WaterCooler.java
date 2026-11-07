package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.util.steam.SeawaterProperties;

/**
 * Process cooler with a fresh/seawater utility demand calculated from reference enthalpy differences.
 *
 * <p>
 * The process fluid retains its own thermodynamic and physical-property models. The utility has specified inlet/outlet
 * temperatures and pressure, and optional Reference-Composition Salinity in g/kg solution. Water uses IF97; seawater
 * adds the IAPWS-08 saline Gibbs contribution. This class sizes utility flow from a specified duty; use HeatExchanger
 * with an explicit SystemWaterIF97 or SystemSeawater stream for a coupled two-stream model.
 * </p>
 *
 * @author esol
 * @version 1.1
 */
public class WaterCooler extends Cooler {
  private static final long serialVersionUID = 1000;

  private double waterInletTemperature = 298.15; // K
  private double waterOutletTemperature = 308.15; // K
  private double waterPressure = 1.0; // bara
  private double coolingWaterFlowRate = 0.0; // kg/s
  /** Reference-composition salinity [g/kg solution], zero for fresh water. */
  private double coolingWaterSalinity = 0.0;

  /**
   * Constructor for WaterCooler.
   *
   * @param name a {@link java.lang.String} object
   */
  public WaterCooler(String name) {
    super(name);
  }

  /**
   * Constructor for WaterCooler.
   *
   * @param name a {@link java.lang.String} object
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public WaterCooler(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Sets the water inlet temperature.
   *
   * @param temperature the water inlet temperature
   * @param unit the unit of temperature
   */
  public void setWaterInletTemperature(double temperature, String unit) {
    coolingWaterFlowRate = Double.NaN;
    this.waterInletTemperature = new neqsim.util.unit.TemperatureUnit(temperature, unit).getValue("K");
  }

  /**
   * Sets the water outlet temperature.
   *
   * @param temperature the water outlet temperature
   * @param unit the unit of temperature
   */
  public void setWaterOutletTemperature(double temperature, String unit) {
    coolingWaterFlowRate = Double.NaN;
    this.waterOutletTemperature = new neqsim.util.unit.TemperatureUnit(temperature, unit).getValue("K");
  }

  /**
   * Sets the water pressure.
   *
   * @param pressure the water pressure
   * @param unit the unit of pressure
   */
  public void setWaterPressure(double pressure, String unit) {
    coolingWaterFlowRate = Double.NaN;
    this.waterPressure = new neqsim.util.unit.PressureUnit(pressure, unit).getValue("bara");
  }

  /**
   * Getter for the field <code>coolingWaterFlowRate</code>.
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getCoolingWaterFlowRate(String unit) {
    if (unit == null
        || !(unit.startsWith("kg/") || "lb/hr".equals(unit) || unit.startsWith("m3/") || unit.startsWith("Am3/"))) {
      throw new IllegalArgumentException("Cooling water flow supports mass units and actual m3/time at the inlet");
    }
    double density = 1.0
        / SeawaterProperties.gibbsDerivative(waterInletTemperature, waterPressure, coolingWaterSalinity, 0, 1);
    return new neqsim.util.unit.RateUnit(coolingWaterFlowRate, "kg/sec", 1.0, density, 373.15).getValue(unit);
  }

  /**
   * Selects seawater through its bulk reference-composition salinity.
   *
   * @param salinity salinity [g/kg solution], zero selects fresh water
   * @throws IllegalArgumentException if salinity is non-finite or outside 0-120 g/kg
   */
  public void setCoolingWaterSalinity(double salinity) {
    if (!Double.isFinite(salinity) || salinity < 0.0 || salinity > 120.0) {
      throw new IllegalArgumentException("Cooling water salinity must be within 0-120 g/kg solution");
    }
    coolingWaterSalinity = salinity;
    coolingWaterFlowRate = Double.NaN;
  }

  /**
   * Returns the configured utility salinity.
   *
   * @return reference-composition salinity [g/kg solution]
   */
  public double getCoolingWaterSalinity() {
    return coolingWaterSalinity;
  }

  /** {@inheritDoc} */
  @Override
  public boolean needRecalculation() {
    return !Double.isFinite(coolingWaterFlowRate) || super.needRecalculation();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    coolingWaterFlowRate = Double.NaN;
    SeawaterProperties.validate(waterInletTemperature, waterPressure, coolingWaterSalinity);
    SeawaterProperties.validate(waterOutletTemperature, waterPressure, coolingWaterSalinity);
    if (waterOutletTemperature <= waterInletTemperature) {
      throw new IllegalArgumentException("Cooling water outlet temperature must exceed its inlet temperature");
    }
    super.run(id);
    calculateCoolingWaterFlowRate();
  }

  /**
   * Sizes utility mass flow from the process duty and solution enthalpy rise.
   *
   * @throws IllegalArgumentException if this operating point heats the process fluid
   */
  private void calculateCoolingWaterFlowRate() {
    double hin = SeawaterProperties.specificEnthalpy(waterInletTemperature, waterPressure, coolingWaterSalinity);
    double hout = SeawaterProperties.specificEnthalpy(waterOutletTemperature, waterPressure, coolingWaterSalinity);
    if (!Double.isFinite(getDuty()) || getDuty() > 1e-8) {
      throw new IllegalArgumentException("WaterCooler requires a finite non-positive process duty");
    }
    coolingWaterFlowRate = Math.max(0.0, -getDuty()) / (hout - hin);
  }
}
