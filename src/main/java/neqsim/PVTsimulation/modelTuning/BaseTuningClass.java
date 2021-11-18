package neqsim.PVTsimulation.modelTuning;

import neqsim.PVTsimulation.simulation.SimulationInterface;

/**
 *
 * @author esol
 */
public class BaseTuningClass implements TuningInterface {

    private static final long serialVersionUID = 1000;

    private SimulationInterface simulation = null;
    private boolean tunePlusMolarMass = false;
    private boolean tuneVolumeCorrection = false;
    public double saturationTemperature = 273.15;
    public double saturationPressure = 273.15;

    public BaseTuningClass() {}

    public BaseTuningClass(SimulationInterface simulationClass) {
        this.simulation = simulationClass;
    }

    /**
     * @return the simulationClass
     */
    @Override
    public SimulationInterface getSimulation() {
        return simulation;
    }

    @Override
    public void setSaturationConditions(double temperature, double pressure) {
        saturationTemperature = temperature;
        saturationPressure = pressure;
    }

    /**
     * @return the tunePlusMolarMass
     */
    public boolean isTunePlusMolarMass() {
        return tunePlusMolarMass;
    }

    /**
     * @param tunePlusMolarMass the tunePlusMolarMass to set
     */
    public void setTunePlusMolarMass(boolean tunePlusMolarMass) {
        this.tunePlusMolarMass = tunePlusMolarMass;
    }

    /**
     * @return the tuneVolumeCorrection
     */
    public boolean isTuneVolumeCorrection() {
        return tuneVolumeCorrection;
    }

    /**
     * @param tuneVolumeCorrection the tuneVolumeCorrection to set
     */
    public void setTuneVolumeCorrection(boolean tuneVolumeCorrection) {
        this.tuneVolumeCorrection = tuneVolumeCorrection;
    }

    @Override
    public void run() {}
}
