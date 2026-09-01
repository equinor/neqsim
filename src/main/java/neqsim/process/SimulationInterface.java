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
   * setRunInSteps.
   *
   * @param setRunSteps boolean set true to run in steps
   */
  public void setRunInSteps(boolean setRunSteps);

  /**
   * isRunInSteps.
   *
   * @return boolean
   */
  public boolean isRunInSteps();

  /**
   * In this method all thermodynamic and unit operations will be calculated in a steady state calculation.
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
   * In this method all thermodynamic and unit operations will be calculated in a steady state calculation. It does not
   * solve recycles - only calculates one step.
   */
  public default void run_step() {
    run_step(UUID.randomUUID());
  }

  /**
   * run_step In this method all thermodynamic and unit operations will be calculated in a steady state calculation.
   * Sets calc identifier UUID. It does not solve recycles - only calculates one step
   *
   * @param value Calc identifier UUID to set.
   */
  public void run_step(UUID value);

  /**
   * runTransient This method calculates thermodynamic and unit operations using difference equations if available and
   * calculateSteadyState is true. Use setCalculateSteadyState to set the parameter. Sets calc identifier UUID.
   *
   * @param dt Delta time [s]
   */
  public default void runTransient(double dt) {
    runTransient(dt, UUID.randomUUID());
  }

  /**
   * runTransient This method calculates thermodynamic and unit operations using difference equations if available and
   * calculateSteadyState is true. Use setCalculateSteadyState to set the parameter. Sets calc identifier UUID.
   *
   * <p>
   * Steady-state equipment may be evaluated repeatedly with the same calculation identifier while a transient solver
   * refines one physical timestep. Every evaluation still calls {@link #run(UUID)}, but the equipment clock advances
   * only for the first successful evaluation of a non-null identifier. For a null identifier, every successful
   * evaluation advances the clock. The default transient boundary does not set an identifier in that case, so any
   * identifier mutation performed by {@code run(null)} is retained.
   * </p>
   *
   * @param dt Delta time [s]
   * @param id Calculation identifier
   */
  public default void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      boolean alreadyEvaluatedForStep = id != null && id.equals(getCalculationIdentifier());
      run(id);
      if (id != null) {
        setCalculationIdentifier(id);
      }
      if (!alreadyEvaluatedForStep) {
        increaseTime(dt);
      }
      return;
    }

    throw new UnsupportedOperationException("RunTransient using difference equations is not supported yet.");
  }

  /**
   * Returns whether or not the module has been solved.
   *
   * @return a boolean
   */
  public boolean solved();

  /**
   * getReport_json. Return results of simulation in json format
   *
   * @return a String
   */
  public String getReport_json();
}
