package neqsim.pvtsimulation.modeltuning;

import neqsim.pvtsimulation.simulation.SimulationInterface;

/**
 * BaseTuningClass class.
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
   * Constructor for BaseTuningClass.
   *
   * @param simulationClass a {@link neqsim.pvtsimulation.simulation.SimulationInterface} object
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
   * isTunePlusMolarMass.
   *
   * @return the tunePlusMolarMass
   */
  public boolean isTunePlusMolarMass() {
    return tunePlusMolarMass;
  }

  /**
   * Setter for the field <code>tunePlusMolarMass</code>.
   *
   * @param tunePlusMolarMass the tunePlusMolarMass to set
   */
  public void setTunePlusMolarMass(boolean tunePlusMolarMass) {
    this.tunePlusMolarMass = tunePlusMolarMass;
  }

  /**
   * isTuneVolumeCorrection.
   *
   * @return the tuneVolumeCorrection
   */
  public boolean isTuneVolumeCorrection() {
    return tuneVolumeCorrection;
  }

  /**
   * Setter for the field <code>tuneVolumeCorrection</code>.
   *
   * @param tuneVolumeCorrection the tuneVolumeCorrection to set
   */
  public void setTuneVolumeCorrection(boolean tuneVolumeCorrection) {
    this.tuneVolumeCorrection = tuneVolumeCorrection;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
  }
}
