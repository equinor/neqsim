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
 * VUflashQfunc.java
 *
 * Created on 8. mars 2001, 10:56
 */
package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author even solbraa
 * @version
 */
public class VUflashQfunc extends Flash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    double Vspec = 0, Uspec = 0.0;
    Flash tpFlash;

    /**
     * Creates new PHflash
     */
    public VUflashQfunc() {
    }

    public VUflashQfunc(SystemInterface system, double Uspec, double Vspec) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.Vspec = Vspec;
    }

    public double calcdQdPP() {
        double dQdVV = (system.getVolume() - Vspec) / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature())+
        system.getPressure() * (system.getdVdPtn()) / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());
        return dQdVV;
    }

    public double calcdQdTT() {

        double dQdTT =  system.getTemperature() * system.getCp()  / neqsim.thermo.ThermodynamicConstantsInterface.R - calcdQdT()/system.getTemperature();
        return dQdTT;
    }

    public double calcdQdT() {
        double dQdT = (Uspec + system.getPressure() * Vspec - system.getEnthalpy()) / (system.getTemperature() * neqsim.thermo.ThermodynamicConstantsInterface.R);
        return dQdT;
    }

    public double calcdQdP() {
        double dQdP = system.getPressure() * (system.getVolume() - Vspec) / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());
        return dQdP;
    }

    public double solveQ() {
        double oldPres = system.getPressure(), nyPres = system.getPressure(), nyTemp = system.getTemperature(), oldTemp = system.getTemperature();
        double iterations = 1;
        do {
            iterations++;
            oldPres = nyPres;
            oldTemp = nyTemp;
            system.init(3);
                        System.out.println("dQdP: " + calcdQdP());
 System.out.println("dQdT: " + calcdQdT());
            nyPres = oldPres - (iterations) / (iterations + 10.0) * calcdQdP() / calcdQdPP();
            nyTemp = oldTemp + (iterations) / (iterations + 10.0) * calcdQdT() / calcdQdTT();
            System.out.println("volume: " + system.getVolume());
             System.out.println("inernaleng: " + system.getInternalEnergy());
            system.setPressure(nyPres);
            system.setTemperature(nyTemp);
            tpFlash.run();
        } while (Math.abs((nyPres - oldPres) / (nyPres)) + Math.abs((nyTemp - oldTemp) / (nyTemp)) > 1e-9 && iterations < 1000);
        return nyPres;
    }

    public void run() {
        tpFlash.run();
        System.out.println("internaleng: " + system.getInternalEnergy());
        System.out.println("volume: " + system.getVolume());
        solveQ();

    }

    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }
}
