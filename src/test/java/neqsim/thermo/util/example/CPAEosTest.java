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

import static org.junit.jupiter.api.Assertions.*;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class CPAEosTest {

    static SystemInterface thermoSystem = null;

    
	@BeforeAll
	public static void setUp(){
	        thermoSystem = new SystemSrkCPAstatoil(298.0, 10.0);
	        thermoSystem.addComponent("methane", 11.0);
	        thermoSystem.addComponent("CO2", 1.0);
	        thermoSystem.addComponent("MEG", 1.0);
	        thermoSystem.addComponent("water", 11.0);
	        thermoSystem.createDatabase(true);
	        thermoSystem.setMixingRule(10);
	}
	

    @Test
    public void testTPflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        assertEquals(thermoSystem.getNumberOfPhases(), 2);
    }
    
    @Test
    public void initPhysicalProperties() {
    	thermoSystem.initPhysicalProperties();
        assertEquals(thermoSystem.getPhase(0).getPhysicalProperties().getDensity(), thermoSystem.getPhase(0).getPhysicalProperties().getDensity());
    }
    
    @Test
    public void testPHflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        thermoSystem.init(3);
        double enthalpy = thermoSystem.getEnthalpy();
        testOps.PHflash(enthalpy+10.0);
        thermoSystem.init(3);
        
        double enthalpy2 = thermoSystem.getEnthalpy();
        	
        assertEquals(Math.round(enthalpy+10.0), Math.round(enthalpy2));
    }
    
    @Test
    public void testPSflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        thermoSystem.init(3);
        double entropy = thermoSystem.getEntropy();
        testOps.PSflash(entropy+10.0);
        thermoSystem.init(3);
        
        double entropy2 = thermoSystem.getEntropy();
        	
        assertEquals(Math.round(entropy+10.0), Math.round(entropy2));
    }

    

}
