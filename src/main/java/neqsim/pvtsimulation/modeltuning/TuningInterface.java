opackage neqsim.pvtsimulation.modeltuning;

import neqsim.pvtsimulation.simulation.SimulationInterface;

/**
 * TuningInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface TuningInterface {
  /**
   * getSimulation.
   *
   * @return a {@link neqsim.pvtsimulation.simulation.SimulationInterface} object
   */
  public SimulationInterface getSimulation();

  /**
   * setSaturationConditions.
   *
   * @param temperature a double
   * @param pressure a double
   */
  public void setSaturationConditions(double temperature, double pressure);

  /**
   * run.
   */
  public void run();
}
