package neqsim.process.equipment.powergeneration;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Simple wind turbine model based on actuator disk theory.
 *
 * <p>The power produced is given by
 * 0.5 * rho_air * rotorArea * windSpeed^3 * powerCoefficient.
 * Air density is assumed constant.</p>
 */
public class WindTurbine extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double windSpeed; // m/s
  private double rotorArea; // m^2
  private double powerCoefficient; // 0-1
  private double airDensity = 1.225; // kg/m3
  private double power; // W

  /**
   * Default constructor.
   */
  public WindTurbine() {
    this("WindTurbine");
  }

  /**
   * Construct a wind turbine with a given name.
   *
   * @param name name of the unit
   */
  public WindTurbine(String name) {
    super(name);
  }

  /**
   * Set wind speed in m/s.
   *
   * @param windSpeed wind speed [m/s]
   */
  public void setWindSpeed(double windSpeed) {
    this.windSpeed = windSpeed;
  }

  /**
   * Get wind speed in m/s.
   *
   * @return wind speed [m/s]
   */
  public double getWindSpeed() {
    return windSpeed;
  }

  /**
   * Set rotor swept area in m^2.
   *
   * @param rotorArea swept area [m^2]
   */
  public void setRotorArea(double rotorArea) {
    this.rotorArea = rotorArea;
  }

  /**
   * Get rotor swept area in m^2.
   *
   * @return rotor area [m^2]
   */
  public double getRotorArea() {
    return rotorArea;
  }

  /**
   * Set power coefficient (aerodynamic efficiency).
   *
   * @param powerCoefficient power coefficient [-]
   */
  public void setPowerCoefficient(double powerCoefficient) {
    this.powerCoefficient = powerCoefficient;
  }

  /**
   * Get power coefficient.
   *
   * @return power coefficient [-]
   */
  public double getPowerCoefficient() {
    return powerCoefficient;
  }

  /**
   * Set air density used in the model.
   *
   * @param airDensity air density [kg/m^3]
   */
  public void setAirDensity(double airDensity) {
    this.airDensity = airDensity;
  }

  /**
   * Get air density used in the model.
   *
   * @return air density [kg/m^3]
   */
  public double getAirDensity() {
    return airDensity;
  }

  /**
   * Get calculated power output in W.
   *
   * @return power [W]
   */
  public double getPower() {
    return power;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    power = 0.5 * airDensity * rotorArea * Math.pow(windSpeed, 3.0) * powerCoefficient;
    getEnergyStream().setDuty(-power);
    setCalculationIdentifier(id);
  }
}

