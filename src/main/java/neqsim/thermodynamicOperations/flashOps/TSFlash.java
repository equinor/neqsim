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
* PHflash.java
*
* Created on 8. mars 2001, 10:56
*/
package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author even solbraa
 * @version
 */
public class TSFlash extends QfuncFlash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    double Sspec = 0;
    Flash tpFlash;

    /**
     * Creates new PHflash
     */
    public TSFlash() {
    }

    public TSFlash(SystemInterface system, double Sspec) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.Sspec = Sspec;
    }

    @Override
	public double calcdQdTT() {
        double cP1 = 0.0, cP2 = 0.0;

        if (system.getNumberOfPhases() == 1) {
            return -system.getPhase(0).getCp() / system.getTemperature();
        }

        double dQdTT = 0.0;
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
            dQdTT -= system.getPhase(i).getCp() / system.getPhase(i).getTemperature();
        }
        return dQdTT;
    }

    @Override
	public double calcdQdT() {
        double dQ = -system.getEntropy() + Sspec;
        return dQ;
    }

    @Override
	public double solveQ() {
        // this method is not yet implemented
        double oldTemp = system.getPressure(), nyTemp = system.getPressure();
        int iterations = 1;
        double error = 1.0, erorOld = 10.0e10;
        double factor = 0.8;

        boolean correctFactor = true;
        double newCorr = 1.0;
        do {
            iterations++;
            oldTemp = system.getPressure();
            system.init(2);

            nyTemp = oldTemp - calcdQdT() / 10.0;

            system.setPressure(nyTemp);
            tpFlash.run();
            erorOld = error;
            error = Math.abs(calcdQdT());
        } while (((error + erorOld) > 1e-8 || iterations < 3) && iterations < 200);
        return nyTemp;
    }

    public void onPhaseSolve() {

    }

    @Override
	public void run() {
        tpFlash.run();
        solveQ();
    }

    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkEos(373.15, 45.551793);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 9.4935);
        testSystem.addComponent("ethane", 5.06499);
        testSystem.addComponent("n-heptane", 0.2);
        testSystem.init(0);
        try {
            testOps.TPflash();
            testSystem.display();

            double Sspec = testSystem.getEntropy("kJ/kgK");
            System.out.println("S spec " + Sspec);
            testSystem.setTemperature(293.15);
            testOps.TSflash(Sspec, "kJ/kgK");
            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }

    }
}
