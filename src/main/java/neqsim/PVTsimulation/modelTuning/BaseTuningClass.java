package neqsim.PVTsimulation.modelTuning;

import neqsim.PVTsimulation.simulation.SimulationInterface;

/**
 * <p>
 * BaseTuningClass class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class BaseTuningClass implements TuningInterface {
  private SimulationInterface simulation = null;
  private boolean tunePlusMolarMass = false;
  private boolean tuneVolumeCorrection = false;
  public double saturationTemperature = 273.15;
  public double saturationPressure = 273.15;

  /**
   * <p>
   * Constructor for BaseTuningClass.
   * </p>
   *
   * @param simulationClass a {@link neqsim.PVTsimulation.simulation.SimulationInterface} object
   */
  public BaseTuningClass(SimulationInterface simulationClass) {
    this.simulation = simulationClass;
  }

  /** {@inheritDoc} */
  @Override
  public SimulationInterface getSimulation() {
    return simulation;
  }

  /** {@inheritDoc} */
  @Override
  public void setSaturationConditions(double temperature, double pressure) {
    saturationTemperature = temperature;
    saturationPressure = pressure;
  }

  /**
   * <p>
   * isTunePlusMolarMass.
   * </p>
   *
   * @return the tunePlusMolarMass
   */
  public boolean isTunePlusMolarMass() {
    return tunePlusMolarMass;
  }

  /**
   * <p>
   * Setter for the field <code>tunePlusMolarMass</code>.
   * </p>
   *
   * @param tunePlusMolarMass the tunePlusMolarMass to set
   */
  public void setTunePlusMolarMass(boolean tunePlusMolarMass) {
    this.tunePlusMolarMass = tunePlusMolarMass;
  }

  /**
   * <p>
   * isTuneVolumeCorrection.
   * </p>
   *
   * @return the tuneVolumeCorrection
   */
  public boolean isTuneVolumeCorrection() {
    return tuneVolumeCorrection;
  }

  /**
   * <p>
   * Setter for the field <code>tuneVolumeCorrection</code>.
   * </p>
   *
   * @param tuneVolumeCorrection the tuneVolumeCorrection to set
   */
  public void setTuneVolumeCorrection(boolean tuneVolumeCorrection) {
    this.tuneVolumeCorrection = tuneVolumeCorrection;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {}
}
