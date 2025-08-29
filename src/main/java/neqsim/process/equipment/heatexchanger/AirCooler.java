package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.util.humidair.HumidAir;

/**
 * Air cooler using humid air properties to estimate required air flow.
 *
 * @author esol
 */
public class AirCooler extends Cooler {
  private static final long serialVersionUID = 1000L;

  private double airInletTemperature = 298.15; // K
  private double airOutletTemperature = 308.15; // K
  private double relativeHumidity = 0.6; // [-]
  private double pressure = 101325.0; // Pa

  private double airMassFlow = 0.0; // kg dry air/s
  private double airVolumeFlow = 0.0; // m3/s at inlet conditions

  /**
   * <p>
   * Constructor for AirCooler.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public AirCooler(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for AirCooler.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public AirCooler(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * <p>
   * Setter for the field <code>airInletTemperature</code>.
   * </p>
   *
   * @param temperature a double
   * @param unit a {@link java.lang.String} object
   */
  public void setAirInletTemperature(double temperature, String unit) {
    airInletTemperature = unit.equalsIgnoreCase("C") ? temperature + 273.15 : temperature;
  }

  /**
   * <p>
   * Setter for the field <code>airOutletTemperature</code>.
   * </p>
   *
   * @param temperature a double
   * @param unit a {@link java.lang.String} object
   */
  public void setAirOutletTemperature(double temperature, String unit) {
    airOutletTemperature = unit.equalsIgnoreCase("C") ? temperature + 273.15 : temperature;
  }

  /**
   * <p>
   * Setter for the field <code>relativeHumidity</code>.
   * </p>
   *
   * @param rh a double
   */
  public void setRelativeHumidity(double rh) {
    relativeHumidity = rh;
  }

  /** {@inheritDoc} */
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * <p>
   * Getter for the field <code>airMassFlow</code>.
   * </p>
   *
   * @return a double
   */
  public double getAirMassFlow() {
    return airMassFlow;
  }

  /**
   * <p>
   * Getter for the field <code>airVolumeFlow</code>.
   * </p>
   *
   * @return a double
   */
  public double getAirVolumeFlow() {
    return airVolumeFlow;
  }

  private void calcAirFlow(double duty) {
    double W = HumidAir.humidityRatioFromRH(airInletTemperature, pressure, relativeHumidity);
    double hin = HumidAir.enthalpy(airInletTemperature, W); // kJ/kg dry air
    double hout = HumidAir.enthalpy(airOutletTemperature, W); // kJ/kg dry air
    double dh = hout - hin; // kJ/kg dry air
    if (Math.abs(dh) < 1e-6) {
      airMassFlow = 0.0;
      airVolumeFlow = 0.0;
      return;
    }
    airMassFlow = duty / (dh * 1000.0); // kg dry air/s
    // volume per kg dry air at inlet conditions
    double Mda = 28.965e-3; // kg/mol
    double Mw = 18.01528e-3; // kg/mol
    double volumePerKgDryAir = ((1.0 / Mda) + (W / Mw)) * 8.314 * airInletTemperature / pressure;
    airVolumeFlow = airMassFlow * volumePerKgDryAir;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    super.run(id);
    double duty = -getDuty();
    calcAirFlow(duty);
  }
}
