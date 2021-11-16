package neqsim.PVTsimulation.simulation;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 */
public interface SimulationInterface {
    public SystemInterface getThermoSystem();

    public void setThermoSystem(SystemInterface thermoSystem);

    public SystemInterface getBaseThermoSystem();

    public void run();

    public LevenbergMarquardt getOptimizer();
}
