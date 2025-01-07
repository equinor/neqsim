package neqsim.process;

import java.util.UUID;
import neqsim.util.NamedBaseClass;
import neqsim.util.exception.InvalidInputException;

/**
 * Base class for process simulation objects.
 *
 * @author ASMF
 * @version $Id: $Id
 */
public abstract class SimulationBaseClass extends NamedBaseClass implements SimulationInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Unique identifier of which solve/run call was last called successfully.
   */
  protected UUID calcIdentifier;
  protected boolean calculateSteadyState = true;
  protected double time = 0;
  private boolean runInSteps = false;

  /**
   * <p>
   * Constructor for SimulationBaseClass.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public SimulationBaseClass(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public UUID getCalculationIdentifier() {
    return calcIdentifier;
  }

  /** {@inheritDoc} */
  @Override
  public void setCalculationIdentifier(UUID value) {
    if (this.calcIdentifier == null || this.calcIdentifier != value) {
      this.calcIdentifier = value;
    } else {
      this.calcIdentifier = value;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean getCalculateSteadyState() {
    return calculateSteadyState;
  }

  /** {@inheritDoc} */
  @Override
  public void setCalculateSteadyState(boolean steady) {
    this.calculateSteadyState = steady;
  }

  /** {@inheritDoc} */
  @Override
  public double getTime() {
    return this.time;
  }

  /** {@inheritDoc} */
  @Override
  public void setTime(double value) {
    this.time = value;
  }

  /** {@inheritDoc} */
  @Override
  public void increaseTime(double dt) {
    if (dt < 0) {
      throw new RuntimeException(new InvalidInputException(this, "increaseTime", "dt",
          "Negative values are not allowed. Not possible to go backwards in time."));
    }
    this.time = this.time + dt;
  }

  /** {@inheritDoc} */
  @Override
  public void setRunInSteps(boolean setRunSteps) {
    runInSteps = setRunSteps;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRunInSteps() {
    return runInSteps;
  }
}
