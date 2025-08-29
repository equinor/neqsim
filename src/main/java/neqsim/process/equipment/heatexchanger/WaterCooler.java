package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.util.steam.Iapws_if97;

/**
 * Cooler for water streams using the water property package.
 *
 * @author esol
 */
public class WaterCooler extends Cooler {
  private static final long serialVersionUID = 1000;

  private double waterInletTemperature = 298.15; // K
  private double waterOutletTemperature = 308.15; // K
  private double waterPressure = 1.0; // bara
  private double coolingWaterFlowRate = 0.0; // kg/s

  /**
   * <p>
   * Constructor for WaterCooler.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public WaterCooler(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for WaterCooler.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public WaterCooler(String name, StreamInterface inStream) {
    super(name, inStream);
    setWaterModel();
  }

  /**
   * Sets the water inlet temperature.
   *
   * @param temperature the water inlet temperature
   * @param unit the unit of temperature
   */
  public void setWaterInletTemperature(double temperature, String unit) {
    this.waterInletTemperature =
        new neqsim.util.unit.TemperatureUnit(temperature, unit).getValue("K");
  }

  /**
   * Sets the water outlet temperature.
   *
   * @param temperature the water outlet temperature
   * @param unit the unit of temperature
   */
  public void setWaterOutletTemperature(double temperature, String unit) {
    this.waterOutletTemperature =
        new neqsim.util.unit.TemperatureUnit(temperature, unit).getValue("K");
  }

  /**
   * Sets the water pressure.
   *
   * @param pressure the water pressure
   * @param unit the unit of pressure
   */
  public void setWaterPressure(double pressure, String unit) {
    this.waterPressure = new neqsim.util.unit.PressureUnit(pressure, unit).getValue("bara");
  }

  /**
   * <p>
   * Getter for the field <code>coolingWaterFlowRate</code>.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getCoolingWaterFlowRate(String unit) {
    return new neqsim.util.unit.RateUnit(coolingWaterFlowRate, "kg/sec", 1.0, 1.0, 0.0)
        .getValue(unit);
  }

  private void setWaterModel() {
    if (inStream != null) {
      inStream.getThermoSystem().setPhysicalPropertyModel(PhysicalPropertyModel.WATER);
    }
    if (outStream != null) {
      outStream.getThermoSystem().setPhysicalPropertyModel(PhysicalPropertyModel.WATER);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface stream) {
    super.setInletStream(stream);
    setWaterModel();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    super.run(id);
    calculateCoolingWaterFlowRate();
  }

  private void calculateCoolingWaterFlowRate() {
    double pinMPa = waterPressure / 10.0; // convert from bara to MPa
    double hin = Iapws_if97.h_pt(pinMPa, waterInletTemperature);
    double hout = Iapws_if97.h_pt(pinMPa, waterOutletTemperature);
    double deltaH = hout - hin; // kJ/kg
    if (Math.abs(deltaH) < 1e-6) {
      coolingWaterFlowRate = 0.0;
      return;
    }
    coolingWaterFlowRate = -getEnergyInput() / (deltaH * 1000.0);
  }
}
