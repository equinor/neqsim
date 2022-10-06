package neqsim.processSimulation;

import java.io.Serializable;
import java.util.UUID;
import neqsim.util.NamedInterface;

/**
 * Interface for simulation objects.
 */
public interface SimulationInterface extends NamedInterface, Runnable, Serializable {
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
   * calculation. Sets calc identifier UUID.
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
   * calculation. Sets calc identifier UUID.
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
   * Sets calc identifier UUID.
   *
   * @param dt Delta time [s]
   */
  public default void runTransient(double dt) {
    runTransient(dt, UUID.randomUUID());
  }

  /**
   * <p>
   * runTransient
   * </p>
   * This method calculates thermodynamic and unit operations using difference equations if
   * available and calculateSteadyState is true. Use setCalculateSteadyState to set the parameter.
   * Sets calc identifier UUID.
   *
   * @param dt Delta time [s]
   * @param id Calculation identifier
   */
  public default void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
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
