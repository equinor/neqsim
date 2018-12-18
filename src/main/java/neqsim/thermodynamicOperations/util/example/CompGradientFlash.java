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
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/*
 *
 * @author  esol
 * @version
 */
public class CompGradientFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(CompGradientFlash.class);

    /** Creates new TPflash */
    public CompGradientFlash() {
    }

    public static void main(String args[]) {

        SystemInterface testSystem = new SystemSrkEos(273.15 + 0, 80.0);//30.01325);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 11.0);
      //  testSystem.addComponent("ethane", 4.0);
        testSystem.addComponent("n-heptane", 0.03);
        //testSystem.addComponent("n-octane", 0.0001);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        testSystem.init(0);
        testSystem.init(3);
        logger.info("enthalpy " + testSystem.getPhase(1).getEnthalpy());

        SystemInterface newSystem = null;
        try {
            testOps.dewPointTemperatureFlash();
            testSystem.display();
            double dewTemp = testSystem.getTemperature();
            testSystem.setTemperature(dewTemp+10.1);
            testSystem.init(0);
            newSystem = testOps.TPgradientFlash(0.0001, dewTemp).phaseToSystem(0);
            newSystem.init(0);
            ThermodynamicOperations testOps2 = new ThermodynamicOperations(newSystem);
            testOps2.dewPointTemperatureFlash();
            newSystem.display();

            //     testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }

    }
}
