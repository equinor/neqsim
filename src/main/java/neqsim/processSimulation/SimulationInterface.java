package neqsim.processSimulation;

import java.util.UUID;
import neqsim.util.NamedInterface;

/**
 * Interface for simulation objects.
 */
public interface SimulationInterface extends NamedInterface, Runnable {
  /**
   * Getter for property calcIdentifier.
   *
   * @return Value of calcIdentifier.
   */
  public UUID getCalculationIdentifier();

  /**
   * Setter for property calcIdentifier.
   *
   * @param value Value to set.
   */
  public void setCalculationIdentifier(UUID value);

  /**
   * Getter for property calculateSteadyState.
   *
   * @return Value of property calculateSteadyState
   */
  public boolean getCalculateSteadyState();

  /**
   * Setter for property calculateSteadyState.
   *
   * @param steady Set true to do steady state calculation when calling runTransient.
   */
  public void setCalculateSteadyState(boolean steady);

  /**
   * Getter for property time.
   *
   * @return Value of property time.
   */
  public double getTime();

  /**
   * Setter for property time.
   *
   * @param value Value to set.
   */
  public void setTime(double value);

  /**
   * Method to increase parameter time by a given value.
   *
   * @param dt Value to increase time by.
   */
  public void increaseTime(double dt);

  /**
   * <p>
   * run
   * </p>
   * In this method all thermodynamic and unit operations will be calculated in a steady state
   * calculation.
   */
  @Override
  public default void run() {
    run(UUID.randomUUID());
  }

  /**
   * <p>
   * run
   * </p>
   * In this method all thermodynamic and unit operations will be calculated in a steady state
   * calculation.
   *
   * @param value Calc identifier UUID to set.
   */
  public void run(UUID value);

  /**
   * <p>
   * runTransient
   * </p>
   * This method calculates thermodynamic and unit operations using difference equations if
   * available and calculateSteadyState is true. Use setCalculateSteadyState to set the parameter.
   *
   * @param dt is the delta time step (seconds)
   */
  public default void runTransient(double dt) {
    if (getCalculateSteadyState()) {
      run();
      increaseTime(dt);
      this.setCalculationIdentifier(UUID.randomUUID());
      return;
    }

    throw new UnsupportedOperationException(
        "RunTransient using difference equations is not supported yet.");
  }

  /**
   * <p>
   * solved.
   * </p>
   *
   * @return a boolean
   */
  public boolean solved();
}
