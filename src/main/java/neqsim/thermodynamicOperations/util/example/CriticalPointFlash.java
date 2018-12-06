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
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermodynamicOperations.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author esol
 */
public class CriticalPointFlash {

    private static final long serialVersionUID = 1000;

    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkEos(273.15 + 15, 10.01325);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("water", 0.9);
        //testSystem.addComponent("n-butane", 0.1);
        testSystem.addComponent("CO2", 0.1);
      //  testSystem.addComponent("i-butane", 0.1);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.init(0);
        try {
            testOps.criticalPointFlash();
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        testSystem.display();
    }
}
