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

    public double calcdQdT() {
        double dQ = -system.getEntropy() + Sspec;
        return dQ;
    }

    public double solveQ() {
    	// this method is not yet implemented
        double oldTemp = system.getTemperature(), nyTemp = system.getTemperature();
        int iterations = 1;
        double error = 1.0, erorOld = 10.0e10;
        double factor = 0.8;
        
        boolean correctFactor = true;
        double newCorr = 1.0;
        do {
            if (error > erorOld  && factor>0.1 && correctFactor) {
                factor *= 0.5;
            } else if (error < erorOld && correctFactor) {
                factor = 1.0;
            }
            
            iterations++;
            oldTemp = system.getTemperature();
            system.init(2);
            newCorr = factor * calcdQdT() / calcdQdTT() * system.getPhase(0).getdPdTVn();
            nyTemp = oldTemp - newCorr;
            if (Math.abs(system.getTemperature() - nyTemp) > 10.0) {
                nyTemp = system.getTemperature() - Math.signum(system.getTemperature() - nyTemp) * 10.0;
               correctFactor = false;
            }
            else if (nyTemp < 0) {
                nyTemp = Math.abs(system.getTemperature() - 10.0);
                correctFactor = false;
            }
            else if (Double.isNaN(nyTemp)) {
                nyTemp = oldTemp + 1.0;
                correctFactor = false;
            }
            else{
                correctFactor = true;
            }

            system.setTemperature(nyTemp);
            tpFlash.run();
            erorOld = error;
            error = Math.abs(calcdQdT());//Math.abs((nyTemp - oldTemp) / (nyTemp));
            //if(error>erorOld) factor *= -1.0;
             // System.out.println("temp " + system.getTemperature() + " iter "+ iterations + " error "+ error + " correction " + newCorr + " factor "+ factor);
          //newCorr = Math.abs(factor * calcdQdT() / calcdQdTT());
        } while(((error+erorOld) > 1e-8 || iterations < 3) && iterations < 200);
        return nyTemp;
    }

    public void onPhaseSolve() {

    }

    public void run() {
        tpFlash.run();
        solveQ();
    }
}
