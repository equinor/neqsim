/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
public class BaseTuningClass implements TuningInterface {

    private static final long serialVersionUID = 1000;

    private SimulationInterface simulation = null;
    private boolean tunePlusMolarMass = false;
    private boolean tuneVolumeCorrection = false;
    public double saturationTemperature = 273.15;
    public double saturationPressure = 273.15;

    public BaseTuningClass() {
    }

    public BaseTuningClass(SimulationInterface simulationClass) {
        this.simulation = simulationClass;
    }

    /**
     * @return the simulationClass
     */
    public SimulationInterface getSimulation() {
        return simulation;
    }

    public void setSaturationConditions(double temperature, double pressure)
    {
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

    public void run() {
    }
}
