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

package neqsim.thermo.util.benchmark;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

public class TPflash_benchmark_UMR {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TPflash_benchmark_UMR.class);

    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        
        double[][] points;
        
        SystemInterface testSystem = new SystemUMRPRUMCEos(273.15 - 5.0, 10.0);
        
        testSystem.addComponent("CO2", 2.1);
        testSystem.addComponent("nitrogen", 1.16);
        testSystem.addComponent("methane", 26.19);
        testSystem.addComponent("propane", 8.27);
        
        testSystem.addComponent("propane", 7.5);
        testSystem.addComponent("i-butane", 1.83);
        testSystem.addComponent("n-butane", 4.05);
        testSystem.addComponent("i-pentane", 1.85);
        testSystem.addComponent("n-pentane", 2.45);
        testSystem.addComponent("n-hexane", 40.6);
        testSystem.addComponent("water", 40.6);
        testSystem.createDatabase(true);
        testSystem.setMultiPhaseCheck(true);
        long time = System.currentTimeMillis();
        testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
        logger.info("Time taken for reading parameters = " + (System.currentTimeMillis() - time));
        
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        time = System.currentTimeMillis();
        
        for (int i = 0; i < 1; i++) {
            testOps.TPflash();
           // testSystem.init(3);
            try {
                //    testOps.hydrateFormationTemperature();
                //    testOps.calcTOLHydrateFormationTemperature();
            } catch (Exception e) {
                logger.error("error",e);
            }
        }
        logger.info("Time taken for benchmark flash = " + (System.currentTimeMillis() - time));
        testSystem.display();

        // base case - 31/8-2013 8:37  reading parameters 19312  flash 702  - running on AC
        // base case - 1/9-2013 8:37  reading parameters 19298  flash 452 - running on AC
    }
}
