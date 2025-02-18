package neqsim.process;

import java.io.Serializable;
import java.util.UUID;
import neqsim.util.NamedInterface;

/**
 * Interface for simulation objects.
 *
 * @author ASMF
 * @version $Id: $Id
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
   * Getter for the field <code>time</code>.
   *
   * @return Value of property time.
   */
  public double getTime();

  /**
   * Setter for field <code>time</code>.
   *
   * @param value Value to set.
   */
  public void setTime(double value);

  /**
   * Method to increase field <code>time</code> by a given value.
   *
   * @param dt Value to increase time by.
   */
  public void increaseTime(double dt);

  /**
   * <p>
   * setRunInSteps.
   * </p>
   *
   * @param setRunSteps boolean set true to run in steps
   */
  public void setRunInSteps(boolean setRunSteps);

  /**
   * <p>
   * isRunInSteps.
   * </p>
   *
   * @return boolean
   */
  public boolean isRunInSteps();

  /**
   * <p>
   * In this method all thermodynamic and unit operations will be calculated in a steady state
   * calculation.
   * </p>
   *
   * @param id UUID
   */
  public void run(UUID id);

  /** {@inheritDoc} */
  @Override
  public default void run() {
    if (isRunInSteps()) {
      run_step(UUID.randomUUID());
    } else {
      run(UUID.randomUUID());
    }
  }

  /**
   * <p>
   * In this method all thermodynamic and unit operations will be calculated in a steady state
   * calculation. It does not solve recycles - only calculates one step.
   * </p>
   */
  public default void run_step() {
    run_step(UUID.randomUUID());
  }

  /**
   * <p>
   * run_step
   * </p>
   * In this method all thermodynamic and unit operations will be calculated in a steady state
   * calculation. Sets calc identifier UUID. It does not solve recycles - only calculates one step
   *
   * @param value Calc identifier UUID to set.
   */
  public void run_step(UUID value);

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
   * Returns whether or not the module has been solved.
   * </p>
   *
   * @return a boolean
   */
  public boolean solved();

  /**
   * <p>
   * getReport_json.
   * </p>
   * Return results of simulation in json format
   *
   * @return a String
   */
  public String getReport_json();
}
