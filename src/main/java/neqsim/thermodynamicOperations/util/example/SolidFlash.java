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

package neqsim.thermodynamicOperations.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/**
 *
 * @author ESOL
 */
public class SolidFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(SolidFlash.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemUMRPRUMCEos(273.15 - 30, 18.0);
        // testSystem.addComponent("nitrogen", 83.33);
        // testSystem.addComponent("oxygen", 8.49);
        // testSystem.addComponent("argon", 0.87);
        // testSystem.addComponent("CO2", 7.3);

        // testSystem.addComponent("nitrogen", 8.33);
        // testSystem.addComponent("methane", 0.17);
        // testSystem.addComponent("ethane", 0.87);
        testSystem.addComponent("CO2", 1.83);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        // testSystem.setMixingRule("HV", null);
        testSystem.setSolidPhaseCheck("CO2");

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.TPflash();
            testSystem.display();
            testSystem.initProperties();

            double enthalpy = testSystem.getEnthalpy();

            testSystem.setPressure(1.0);

            testOps.PHflash(enthalpy);
            // testOps.TPflash();
            // testOps.PHsolidFlash(enthalpy);
            // // testOps.TPSolidflash();
            // testOps.freezingPointTemperatureFlash();
            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }

    }
}
