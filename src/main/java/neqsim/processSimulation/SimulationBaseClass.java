package neqsim.processSimulation;

import neqsim.util.NamedBaseClass;

public abstract class SimulationBaseClass extends NamedBaseClass implements SimulationInterface {
  private static final long serialVersionUID = 1L;
  protected boolean calculateSteadyState = true;

  public SimulationBaseClass(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt) {
    if (getCalculateSteadyState()) {
      run();
      return;
    }

    throw new UnsupportedOperationException("RunTransient using difference equations is not supported yet.");
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
}
