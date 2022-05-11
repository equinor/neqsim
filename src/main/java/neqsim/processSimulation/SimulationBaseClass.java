package neqsim.processSimulation;

import neqsim.util.NamedBaseClass;

public abstract class SimulationBaseClass extends NamedBaseClass implements SimulationInterface {
  private static final long serialVersionUID = 1L;

  public SimulationBaseClass(String name) {
    super(name);
  }
}
