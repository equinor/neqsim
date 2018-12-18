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
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

public class TPflash_benchmark {
    static Logger logger = Logger.getLogger(TPflash_benchmark.class);
    private static final long serialVersionUID = 1000;

    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) { 
        double[][] points;

        SystemInterface testSystem = new SystemSrkEos(283.15, 35.01325);
        //   SystemInterface testSystem = new SystemSrkCPAstatoil(303.15, 10.0);
        // SystemInterface testSystem = new SystemUMRPRUMCEos(303.0, 10.0);
        //SystemInterface testSystem = new SystemSrkSchwartzentruberEos(298.15, 1.01325);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("nitrogen", -0.0028941);
        testSystem.addComponent("CO2", 0.054069291);
        testSystem.addComponent("methane", 0.730570915);
        testSystem.addComponent("ethane", 0.109004002);
        testSystem.addComponent("propane", 0.061518891);
        testSystem.addComponent("n-butane", 0.0164998);
        testSystem.addComponent("i-butane", 0.006585);
        testSystem.addComponent("n-pentane", 0.005953);
        testSystem.addComponent("i-pentane", 0.0040184);
        testSystem.addTBPfraction("C6", 0.006178399, 86.17801 / 1000.0, 0.6639999);
        testSystem.addComponent("water", 0.0027082);
 //       testSystem.addComponent("TEG", 1.0);
   //     testSystem.addTBPfraction("C7",1.0,250.0/1000.0,0.9);

        testSystem.createDatabase(true);
//        testSystem.setMixingRule(2);
        testSystem.autoSelectMixingRule();
        long multTime = System.currentTimeMillis();
         testSystem.setMultiPhaseCheck(true);
        logger.info("Time taken for setMultiPhaseCheck = " + (System.currentTimeMillis() - multTime));
         
        //    testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
        logger.info("start benchmark TPflash......");
        

        testSystem.init(0);
        long time = System.currentTimeMillis();
        //testOps.TPflash();
        for (int i = 0; i < 1; i++) {
            //testSystem.init(3, 0);
            testOps.TPflash();
            //testSystem.init(0);
            //     testSystem.init(1);
        }
        testSystem.initPhysicalProperties();
        logger.info("Viscosity:"+  testSystem.getViscosity());
        
        logger.info("Time taken for benchmark flash = " + (System.currentTimeMillis() - time));
        testSystem.display();
        logger.info("gas " + testSystem.getPhase(0).getNumberOfMolesInPhase() + " oil "+ testSystem.getPhase(1).getNumberOfMolesInPhase() + " water "+ testSystem.getPhase(2).getNumberOfMolesInPhase());
        logger.info(testSystem.getPhase(0).getDensity());
        logger.info(testSystem.getPhase(0).getMolarMass()/testSystem.getPhase(0).getDensity());
        testSystem.display();

       //     testSystem.saveObjectToFile("c:/temp/test2.neqsim", "test2.neqsim");
        //    SystemInterface testSystem2 = testSystem.readObjectFromFile("c:/temp/test2.neqsim", "test2.neqsim");
            
        /// testSystem2.init(3);
     //       testSystem2.display();
        //  testSystem2.init(0);
        // testSystem2.init(3);
        // time for 5000 flash calculations
        // Results Dell Portable PIII 750 MHz - JDK 1.3.1:
        //  mixrule 1 (Classic - no interaction):    6.719 sec
        //  mixrule 2 (Classic):    6.029 sec ny PC 1.108 sec
        // mixrule 4 (Huron-Vidal2):    17.545 sec
        //  mixrule 6 (Wong-Sandler):    12.859 sec
        // test of ijAlgo matrix - before 4134 ms / 3962 ms
        //        // system:
        //        SystemSrkEos testSystem = new SystemSrkEos(303.15, 10.01325);
        //        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        //        testSystem.addComponent("methane", 100.0);
        //        testSystem.addComponent("water", 100.0);
        //        testSystem.setMixingRule(1);
    }
}
