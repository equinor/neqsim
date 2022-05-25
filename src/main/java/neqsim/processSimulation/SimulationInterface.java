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
   * This method calculates thermodynamic and unit operations using
   * difference equations if available and calculateSteadyState is true.
   * Use setCalculateSteadyState to set the parameter.
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
