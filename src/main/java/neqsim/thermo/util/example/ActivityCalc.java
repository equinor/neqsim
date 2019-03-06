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
 * ActivityCalc.java
 *
 * Created on 5. mars 2002, 15:17
 */
package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author  esol
 * @version
 */
public class ActivityCalc {

    private static final long serialVersionUID = 1000;

    /** Creates new ActivityCalc */
    public ActivityCalc() {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 42, 1.01325);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
     
        testSystem.addComponent("TEG", 0.99);
        testSystem.addComponent("water", 0.01);

        testSystem.createDatabase(true);

        testSystem.setMixingRule(10);
        testSystem.init(0);
        testSystem.init(1);

        try {
            testOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
        }

        testSystem.display();
        System.out.println("activity water " + testSystem.getPhase(1).getActivityCoefficient(1));

    }
}
