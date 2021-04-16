/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.PVTsimulation.modelTuning;

import neqsim.PVTsimulation.simulation.SimulationInterface;

/**
 *
 * @author esol
 */
public interface TuningInterface {
    public SimulationInterface getSimulation();

    public void setSaturationConditions(double temperature, double pressure);

    public void run();
}
