package neqsim.PVTsimulation.simulation;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * BasePVTsimulation class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class BasePVTsimulation implements SimulationInterface {
  private SystemInterface thermoSystem = null;
  private SystemInterface baseThermoSystem = null;
  public ThermodynamicOperations thermoOps = null;
  private double pressure;
  public double[] pressures = {381.5, 338.9, 290.6, 242.3, 194.1, 145.8, 145.8, 97.5, 49.3};
  public double temperature = Double.NaN;
  double[][] experimentalData = null;
  double saturationVolume = 0;
  double saturationPressure = 0;
  double saturationTemperature;
  double Zsaturation = 0;
  String temperatureUnit = "K";
  public LevenbergMarquardt optimizer = new LevenbergMarquardt();

  /**
   * <p>
   * Constructor for BasePVTsimulation.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public BasePVTsimulation(SystemInterface tempSystem) {
    thermoSystem = tempSystem; // tempSystem.clone();
    thermoOps = new ThermodynamicOperations(getThermoSystem());
    baseThermoSystem = thermoSystem.clone();
  }

  /**
   * <p>
   * Setter for the field <code>experimentalData</code>.
   * </p>
   *
   * @param expData an array of type double
   */
  public void setExperimentalData(double[][] expData) {
    experimentalData = expData;
  }

  /**
   * <p>
   * Getter for the field <code>saturationPressure</code>.
   * </p>
   *
   * @return a double
   */
  public double getSaturationPressure() {
    return saturationPressure;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    thermoOps = new ThermodynamicOperations(getThermoSystem());
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getBaseThermoSystem() {
    return baseThermoSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void setThermoSystem(SystemInterface thermoSystem) {
    this.thermoSystem = thermoSystem;
  }

  /**
   * <p>
   * Getter for the field <code>pressure</code>.
   * </p>
   *
   * @return the pressure
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure the pressure to set
   */
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * <p>
   * Getter for the field <code>temperature</code>.
   * </p>
   *
   * @return the temperature
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * <p>
   * Setter for the field <code>temperature</code>. NB! Verify unit is correct.
   * </p>
   *
   * @param temperature the temperature to set
   */
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temperature, String temperatureUnit) {
    this.temperature = temperature;
    this.temperatureUnit = temperatureUnit;
  }

  /**
   * <p>
   * Getter for the field <code>pressures</code>.
   * </p>
   *
   * @return the pressures
   */
  public double[] getPressures() {
    return pressures;
  }

  /**
   * <p>
   * Setter for the field <code>pressures</code>.
   * </p>
   *
   * @param pressures the pressures to set
   */
  public void setPressures(double[] pressures) {
    this.pressures = pressures;
  }

  /** {@inheritDoc} */
  @Override
  public LevenbergMarquardt getOptimizer() {
    return optimizer;
  }

  /**
   * <p>
   * getZsaturation.
   * </p>
   *
   * @return the Zsaturation
   */
  public double getZsaturation() {
    return Zsaturation;
  }

  /**
   * <p>
   * Getter for the field <code>saturationTemperature</code>.
   * </p>
   *
   * @return the saturationTemperature
   */
  public double getSaturationTemperature() {
    return saturationTemperature;
  }
}
