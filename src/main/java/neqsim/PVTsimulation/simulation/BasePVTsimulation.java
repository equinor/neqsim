/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.PVTsimulation.simulation;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author esol
 */
public class BasePVTsimulation implements SimulationInterface {

    private static final long serialVersionUID = 1000;

    private SystemInterface thermoSystem = null;
    private SystemInterface baseThermoSystem = null;
    public ThermodynamicOperations thermoOps = null;
    private double pressure;
    public double[] pressures = {381.5, 338.9, 290.6, 242.3, 194.1, 145.8, 145.8, 97.5, 49.3};
    public double temperature = 289.0;
    double[][] experimentalData = null;
    double saturationVolume = 0, saturationPressure = 0;
    double Zsaturation = 0;
    public LevenbergMarquardt optimizer = new LevenbergMarquardt();
       

    public BasePVTsimulation(SystemInterface tempSystem) {
        thermoSystem = tempSystem;//(SystemInterface) tempSystem.clone();
        thermoOps = new ThermodynamicOperations(getThermoSystem());
        baseThermoSystem = (SystemInterface) thermoSystem.clone();
    }

    public void setExperimentalData(double[][] expData) {
        experimentalData = expData;
    }

      public double getSaturationPressure(){
        return saturationPressure;
    }
    /**
     * @return the thermoSystem
     */
    public SystemInterface getThermoSystem() {
        return thermoSystem;
    }

    public void run() {
        thermoOps = new ThermodynamicOperations(getThermoSystem());

    }

    /**
     * @return the baseThermoSystem
     */
    public SystemInterface getBaseThermoSystem() {
        return baseThermoSystem;
    }

    /**
     * @param thermoSystem the thermoSystem to set
     */
    public void setThermoSystem(SystemInterface thermoSystem) {
        this.thermoSystem = thermoSystem;
    }

    /**
     * @return the pressure
     */
    public double getPressure() {
        return pressure;
    }

    /**
     * @param pressure the pressure to set
     */
    public void setPressure(double pressure) {
        this.pressure = pressure;
    }

    /**
     * @return the temperature
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * @param temperature the temperature to set
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    /**
     * @return the pressures
     */
    public double[] getPressures() {
        return pressures;
    }

    /**
     * @param pressures the pressures to set
     */
    public void setPressures(double[] pressures) {
        this.pressures = pressures;
    }

    /**
     * @return the optimizer
     */
    public LevenbergMarquardt getOptimizer() {
        return optimizer;
    }

    /**
     * @return the Zsaturation
     */
    public double getZsaturation() {
        return Zsaturation;
    }
    

}
