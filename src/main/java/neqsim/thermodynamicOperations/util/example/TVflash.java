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

/*
 *
 * @author esol
 * 
 * @version
 */
public class TVflash {
    static Logger logger = LogManager.getLogger(TVflash.class);

    public static void main(String args[]) {
        // SystemInterface testSystem2 = (SystemInterface)
        // util.serialization.SerializationManager.open("c:/test.fluid");
        // testSystem2.display();
        SystemInterface testSystem = new SystemSrkEos(273.15 + 5, 1.0);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("nitrogen", 90.0);
        // testSystem.addComponent("ethane", 4.0);
        testSystem.addComponent("oxygen", 10.2);
        testSystem.addComponent("water", 1.5);

        // 1500 m3 vann

        // testSystem.addComponent("water", 1.0);
        testSystem.createDatabase(true);
        // 1- orginal no interaction 2- classic w interaction
        // 3- Huron-Vidal 4- Wong-Sandler
        testSystem.setMixingRule(2);
        // testSystem.setMultiPhaseCheck(true);
        testSystem.init(0);
        try {
            testOps.TPflash();
            testSystem.display();
            testSystem.setTemperature(273.15 + 55.1);
            testOps.TVflash(testSystem.getVolume());
            testSystem.display();
            // testOps.PVrefluxFlash(0.05, 1);
        } catch (Exception e) {
            logger.error(e.toString());
        }
    }
}
