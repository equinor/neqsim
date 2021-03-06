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
package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */
/**
 *
 * @author esol
 * @version
 */
public class AmineFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(AmineFlash.class);

    /**
     * Creates new TPflash
     */
    public AmineFlash() {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemFurstElectrolyteEos(273.15 + 50, 1.01325);
        // SystemInterface testSystem = new SystemElectrolyteCPA(273.15+40, 1.01325);
        double molMDEA = 0.1;
        double loading = 0.4;
        double density = 1088;

        // testSystem.addComponent("methane", loading*molMDEA*0.001);
        testSystem.addComponent("CO2", loading * molMDEA);
        testSystem.addComponent("water", 1.0 - molMDEA);
        // testSystem.addComponent("Piperazine", 0.1*molMDEA);
        testSystem.addComponent("MDEA", molMDEA);
        testSystem.chemicalReactionInit();
        // testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);
        testSystem.init(1);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error("err " + e.toString());
        }
        double molprMDEA = (molMDEA / (1.0 + 0.30 * molMDEA));
        logger.info("mol % MDEA " + molprMDEA);
        logger.info("molCO2/liter " + loading * molprMDEA / testSystem.getPhase(1).getMolarMass() * density / 1e3);
        logger.info("pressure " + testSystem.getPressure());
        logger.info("pH " + testSystem.getPhase(1).getpH());
        logger.info("Henrys Constant CO2 " + testSystem.calcHenrysConstant("CO2"));//
        testSystem.display();
    }
}
