package neqsim.PVTsimulation.modelTuning;

import neqsim.PVTsimulation.simulation.SimulationInterface;

/**
 * <p>TuningInterface interface.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface TuningInterface {
    /**
     * <p>getSimulation.</p>
     *
     * @return a {@link neqsim.PVTsimulation.simulation.SimulationInterface} object
     */
    public SimulationInterface getSimulation();

    /**
     * <p>setSaturationConditions.</p>
     *
     * @param temperature a double
     * @param pressure a double
     */
    public void setSaturationConditions(double temperature, double pressure);

    /**
     * <p>run.</p>
     */
    public void run();
}
