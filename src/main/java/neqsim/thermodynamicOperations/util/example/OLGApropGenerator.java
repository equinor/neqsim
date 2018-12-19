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
package neqsim.thermodynamicOperations.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

/**
 *
 * @author ESOL
 */
public class OLGApropGenerator {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(OLGApropGenerator.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(273.15, 10.0);
        testSystem.addComponent("methane", 10.0);
        testSystem.addComponent("ethane", 1.0);
        testSystem.addComponent("nC10", 5.0);
        //testSystem.addComponent("MEG", 1.0);
        testSystem.addComponent("water", 10.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        testSystem.setMultiPhaseCheck(true);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testSystem.setTemperature(380.0);
            testSystem.setPressure(80.0);
            testOps.TPflash();
            testSystem.display();

            testSystem.setTemperature(273.15 + 141.85);
            testSystem.setPressure(143);
            testOps.TPflash();
            testSystem.display();

            String fileName = "c:/Appl/OLGAneqsim.tab";
            testOps.OLGApropTable(273.15 + 26.85, 273.15 + 156.85, 41, 80.0, 200.0, 41, fileName, 0);
            testOps.displayResult();

        } catch (Exception e) {
            testSystem.display();
            logger.error(e.toString());
        }

    }
}
