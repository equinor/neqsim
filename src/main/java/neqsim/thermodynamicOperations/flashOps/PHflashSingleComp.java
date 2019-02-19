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
public class PHflashSingleComp extends Flash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    double Hspec = 0;

    /**
     * Creates new PHflash
     */
    public PHflashSingleComp() {
    }

    public PHflashSingleComp(SystemInterface system, double Hspec, int type) {
        this.system = system;
        this.Hspec = Hspec;
    }

    public void run() {
        neqsim.thermodynamicOperations.ThermodynamicOperations bubOps = new neqsim.thermodynamicOperations.ThermodynamicOperations(system);
        double initTemp = system.getTemperature();

        if (system.getPressure() < system.getPhase(0).getComponent(0).getPC()) {
            try {
                bubOps.TPflash();
                if (system.getPhase(0).getPhaseTypeName().equals("gas")) {
                    bubOps.dewPointTemperatureFlash();
                } else {
                    bubOps.bubblePointTemperatureFlash();
                }
            } catch (Exception e) {
                system.setTemperature(initTemp);
                logger.error("error", e);
            }
        } else {
            bubOps.PHflash2(Hspec, 0);
            return;
        }

        system.init(
                3);
        double gasEnthalpy = system.getPhase(0).getEnthalpy() / system.getPhase(0).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
        double liqEnthalpy = system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
        
        if (Hspec < liqEnthalpy || Hspec > gasEnthalpy) {
            system.setTemperature(initTemp);
            bubOps.PHflash2(Hspec, 0);
            return;
        }
        double beta = (Hspec - liqEnthalpy) / (gasEnthalpy - liqEnthalpy);

        system.setBeta(beta);

        system.init(
                3);
    }

    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }
}
