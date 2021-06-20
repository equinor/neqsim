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
 * TVflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;

/**
 * @author  even solbraa
 * @version
 */
public class TVflash extends Flash {

    private static final long serialVersionUID = 1000;

    double Vspec = 0;
    Flash tpFlash;

    /** Creates new TVflash */
    public TVflash() {
    }

    public TVflash(SystemInterface system, double Vspec) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.Vspec = Vspec;
    }

    public double calcdQdVV() {
        double dQdVV = 0.0;
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
            dQdVV += 1.0 / (system.getPhase(i).getVolume() / system.getVolume()) * 1.0 / system.getPhase(i).getdPdVTn();// *system.getPhase(i).getdVdP();system.getPhase(i).getVolume()/system.getVolume()*
        }
        return dQdVV;
    }

    public double calcdQdV() {
        double dQ = system.getVolume() - Vspec;
        return dQ;
    }

    public double solveQ() {
        double oldPres = system.getPressure(), nyPres = system.getPressure();
        double iterations = 1;
        do {
            iterations++;
            oldPres = nyPres;
            system.init(3);
            nyPres = oldPres - (iterations) / (iterations + 10.0) * calcdQdV() / calcdQdVV();
            if (nyPres <= 0.0 || Math.abs(oldPres - nyPres) > 10.0) {
                nyPres = Math.abs(oldPres - 1.0);
            }
            system.setPressure(nyPres);
            tpFlash.run();
            // System.out.println(" dQdv " + calcdQdV() + " new pressure " + nyPres + "
            // error " + Math.abs((nyPres-oldPres)/(nyPres)) + "
            // numberofphases"+system.getNumberOfPhases());
        } while (Math.abs((nyPres - oldPres) / (nyPres)) > 1e-9 && iterations < 1000 || iterations < 3);
        return nyPres;
    }

    @Override
    public void run() {
        tpFlash.run();
        // System.out.println("enthalpy: " + system.getEnthalpy());
        solveQ();

        // System.out.println("volume: " + system.getVolume());
        // System.out.println("Temperature: " + system.getTemperature());
    }

    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }
}
