package neqsim.processSimulation;

import java.util.UUID;
import neqsim.util.NamedBaseClass;

/**
 * Base class for process simulation objects.
 *
 * @author ASMF
 * @version $Id: $Id
 */
public abstract class SimulationBaseClass extends NamedBaseClass implements SimulationInterface {
  private static final long serialVersionUID = 1L;

  /**
   * Unique identifier of which solve/run call was last called successfully.
   */
  protected UUID calcIdentifier;
  protected boolean calculateSteadyState = true;
  protected double time = 0;

  /**
   * <p>Constructor for SimulationBaseClass.</p>
   *
   * @param name a {@link java.lang.String} object
   */
  public SimulationBaseClass(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  public UUID getCalculationIdentifier() {
    return calcIdentifier;
  }

  /** {@inheritDoc} */
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
  public double getTime() {
    return this.time;
  }

  /** {@inheritDoc} */
  public void setTime(double value) {
    this.time = value;
  }

  /** {@inheritDoc} */
  public void increaseTime(double dt) {
    if (dt < 0) {
      throw new UnsupportedOperationException(
          "Input dt is negative - not allowed to go backwards in time.");
    }
    this.time = this.time + dt;
  }
}
