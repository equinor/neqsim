package neqsim.processSimulation;

public abstract class SimulationBaseClass implements SimulationInterface {
    protected String name;

    public SimulationBaseClass(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
