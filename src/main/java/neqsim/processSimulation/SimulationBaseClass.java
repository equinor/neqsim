package neqsim.processSimulation;

import neqsim.util.NamedBaseClass;

public abstract class SimulationBaseClass extends NamedBaseClass implements SimulationInterface {

    @Deprecated
    public SimulationBaseClass() {
        super("");
    }

    public SimulationBaseClass(String name) {
        super(name);
    }
}
