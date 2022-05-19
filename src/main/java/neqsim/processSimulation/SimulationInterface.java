package neqsim.processSimulation;

import neqsim.util.NamedInterface;

public interface SimulationInterface extends NamedInterface, Runnable {
  /**
   * Get calculateSteadyState
   * 
   * @return Value of property calculateSteadyState
   */
  public boolean getCalculateSteadyState();

  /**
   * Setter for property calculateSteadyState
   * 
   * @param steady Set true to do steady state calculation when calling runTransient.
   */
  public void setCalculateSteadyState(boolean steady);

  /**
   * <p>
   * run
   * </p>
   * In this method all thermodynamic and unit operations will be calculated in a steady state
   * calculation.
   *
   * @return void
   */
  @Override
  public void run();

  /**
   * <p>
   * runTransient
   * </p>
   * In this method all thermodynamic and unit operations will be calculated using difference
   * equations if available. It is also possible to specify per element if steady state calculations
   * shall be called using setCalculateSteadyState(true).
   * 
   * @param dt is the delta time step (seconds)
   *
   * @return void
   */
  public void runTransient(double dt);

  /**
   * <p>
   * solved.
   * </p>
   *
   * @return a boolean
   */
  public boolean solved();
}
