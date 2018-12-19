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
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

/**
 *
 * @author ESOL
 */
public class VLSolidTPFLash {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(VLSolidTPFLash.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemPrEos(208.2 , 18.34);
        testSystem.addComponent("nitrogen", 0.379);
        testSystem.addComponent("CO2", 100);
        testSystem.addComponent("methane", 85.299);
        testSystem.addComponent("ethane", 7.359);
        testSystem.addComponent("propane", 3.1);
        testSystem.addComponent("i-butane", 0.504);
        testSystem.addComponent("n-butane", 0.85);
        testSystem.addComponent("i-pentane", 0.323);
        testSystem.addComponent("n-pentane", 0.231);
        testSystem.addComponent("n-hexane", 0.173);
        testSystem.addComponent("n-heptane", 0.078);

        testSystem.createDatabase(true);
        // 1- orginal no interaction 2- classic w interaction
        // 3- Huron-Vidal 4- Wong-Sandler
        testSystem.setMixingRule(2);
        //  testSystem.setMultiPhaseCheck(true);
        testSystem.init(0);
        testSystem.setSolidPhaseCheck("CO2");
//testSystem.display();
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        double entalp = 0;
        try {
            testOps.TPflash();
        //    testOps.dewPointTemperatureFlash();
            testSystem.display();



            //testOps.freezingPointTemperatureFlash();
            //testSystem.display();
            testSystem.init(3);
            entalp = testSystem.getEnthalpy();
            testSystem.setNumberOfPhases(3);
            testSystem.setPressure(18.0);
            //testOps.TPflash();
            //testSystem.display();
         //    testOps.PHsolidFlash(entalp);
            //testOps.PHflash(entalp, 0);
           // testSystem.display();
           // testOps.freezingPointTemperatureFlash();
            //testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.init(3);
        //  testSystem.display();
        //    logger.info("enthalpy CO2 solid " + testSystem.getPhase(2).getEnthalpy() + " index " + testSystem.getPhaseIndex(2));
        logger.info("total enthalpy " + (testSystem.getEnthalpy() - entalp));
        logger.info("out temperature " + (testSystem.getTemperature() - 273.15));
        //    testSystem.display();
    }
}
