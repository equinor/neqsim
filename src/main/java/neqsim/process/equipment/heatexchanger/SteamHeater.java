package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.util.steam.Iapws_if97;

/**
 * Heater for process streams using condensing steam as heating medium.
 */
public class SteamHeater extends Heater {

  private static final Logger logger = LogManager.getLogger(SteamHeater.class);
  private static final long serialVersionUID = 1000L;

  private double steamInletTemperature = 453.15; // K
  private double steamOutletTemperature = 373.15; // K
  private double steamPressure = 1.0; // bara
  private double steamFlowRate = 0.0; // kg/s

  public SteamHeater(String name) {
    super(name);
  }

  public SteamHeater(String name, StreamInterface inStream) {
    super(name, inStream);
    setWaterModel();
  }

  /**
   * Set inlet steam temperature.
   *
   * @param temperature the steam inlet temperature
   * @param unit the unit of temperature
   */
  public void setSteamInletTemperature(double temperature, String unit) {
    steamInletTemperature = new neqsim.util.unit.TemperatureUnit(temperature, unit).getValue("K");
  }

  /**
   * Set outlet condensate temperature.
   *
   * @param temperature the steam outlet temperature
   * @param unit the unit of temperature
   */
  public void setSteamOutletTemperature(double temperature, String unit) {
    steamOutletTemperature = new neqsim.util.unit.TemperatureUnit(temperature, unit).getValue("K");
  }

  /**
   * Set steam pressure.
   *
   * @param pressure the steam pressure
   * @param unit the unit of pressure
   */
  public void setSteamPressure(double pressure, String unit) {
    steamPressure = new neqsim.util.unit.PressureUnit(pressure, unit).getValue("bara");
  }

  /**
   * Get the steam flow rate.
   *
   * @param unit the unit of flow rate
   * @return the steam flow rate in the specified unit
   */
  public double getSteamFlowRate(String unit) {
    return new neqsim.util.unit.RateUnit(steamFlowRate, "kg/sec", 1.0, 1.0, 0.0).getValue(unit);
  }

  private void setWaterModel() {
    if (inStream != null) {
      inStream.getThermoSystem().setPhysicalPropertyModel(PhysicalPropertyModel.WATER);
    }
    if (outStream != null) {
      outStream.getThermoSystem().setPhysicalPropertyModel(PhysicalPropertyModel.WATER);
    }
  }

  @Override
  public void setInletStream(StreamInterface stream) {
    super.setInletStream(stream);
    setWaterModel();
  }

  @Override
  public void run(UUID id) {
    super.run(id);
    calculateSteamFlowRate();
  }

  private void calculateSteamFlowRate() {
    double pinMPa = steamPressure / 10.0; // bara -> MPa
    double hin = Iapws_if97.h_pt(pinMPa, steamInletTemperature);
    double hout = Iapws_if97.h_pt(pinMPa, steamOutletTemperature);
    double deltaH = hin - hout; // kJ/kg released per kg steam
    logger.debug("DEBUG SteamHeater: getEnergyInput()=" + getEnergyInput() + ", hin=" + hin
        + ", hout=" + hout + ", deltaH=" + deltaH);
    if (Math.abs(deltaH) < 1e-6) {
      steamFlowRate = 0.0;
      return;
    }
    steamFlowRate = getEnergyInput() / (deltaH * 1000.0);
  }
}
