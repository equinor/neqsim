/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package neqsim.thermodynamicOperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class OLGApropGeneratorPH {
    static Logger logger = LogManager.getLogger(OLGApropGeneratorPH.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(383.15, 1.0);
        // testSystem.addComponent("ethane", 10.0);
        testSystem.addComponent("water", 10.0);
        // testSystem.addComponent("n-heptane", 1.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        testSystem.setMultiPhaseCheck(true);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.dewPointTemperatureFlash();
            // testOps.TPflash();
            testSystem.display();
            double maxEnthalpy = testSystem.getEnthalpy();
            logger.info(" maxEnthalpy " + maxEnthalpy);
            testOps.bubblePointTemperatureFlash();
            testSystem.display();
            double minEnthalpy = testSystem.getEnthalpy();

            // testOps.PHflash(maxEnthalpy + 49560, 0);
            String fileName = "c:/Appl/OLGAneqsim.tab";
            testOps.OLGApropTablePH(minEnthalpy, maxEnthalpy, 41, testSystem.getPressure(), 2, 41,
                    fileName, 0);
            testOps.displayResult();
        } catch (Exception e) {
            testSystem.display();
            logger.error(e.toString());
        }
    }
}
