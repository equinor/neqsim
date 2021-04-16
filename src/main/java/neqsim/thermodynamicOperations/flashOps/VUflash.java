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
 * VUflash.java
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
public class VUflash extends Flash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    double Uspec = 0;
    double Vspec = 0;
    Flash pHFlash;

    /** Creates new PHflash */
    public VUflash() {
    }

    public VUflash(SystemInterface system, double Vspec, double Uspec) {
        this.system = system;
        this.pHFlash = new PHflash(system, Uspec, 0);
        this.Uspec = Uspec;
        this.Vspec = Vspec;
//        System.out.println("entalpy " + Hspec);
//        System.out.println("volume " + Vspec);
    }

    public void run() {
        double oldVol = system.getVolume(), newVol = system.getVolume();
        double pNew = system.getPressure(), pOld = system.getPressure(), pOldOld = 0.0;
        double err = 0.0;
        int iterations = 0;
//\\        System.out.println("entalpy start " + system.getEnthalpy());
        double dPdV = 0.0;
        double wallHeat = 0.0;
        for (int i = 0; i < 21; i++) {
            wallHeat = 0 * i / 20.0 * 400.0 * 1295.0 * 1000.0 * (293.15 - system.getTemperature());
            // System.out.println("Hwall " + wallHeat + " i " + i);
            iterations = 1;
            do {
                iterations++;

                this.pHFlash = new PHflash(system, Uspec + wallHeat + system.getPressure() * system.getVolume(), 0);
//            System.out.println("Hspec " + Hspec);
                this.pHFlash.run();
                pOldOld = pOld;
                pOld = system.getPressure();
                oldVol = newVol;
                newVol = system.getVolume();

                err = (newVol - Vspec) / Vspec;

                // System.out.println("err................................................................................
                // " + err);
                if (iterations < -5) {
                    system.setPressure(system.getPressure() + err / 10.0);
                } else {
                    // System.out.println("pres " + (system.getPressure()+0.1*dPdV*(newVol-Vspec)));
                    system.setPressure(system.getPressure() - 0.6 * 1.0 / system.getdVdPtn() * (newVol - Vspec));// system.getdVdPtn()*(newVol-Vspec));//dPdV*(newVol-Vspec));
                }
                pNew = system.getPressure();
                dPdV = (pOld - pOldOld) / (newVol - oldVol);
                // System.out.println("pressure " + system.getPressure());
            } while ((Math.abs(err) > 1e-10 && iterations < 1000) || iterations < 7);
        }
//        System.out.println("entalpy end " + system.getEnthalpy());
        // System.out.println("iterations " + iterations);
    }

    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

}
