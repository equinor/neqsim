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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

//import junit.framework.TestCase;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class VapourPressureTTest {

    static SystemInterface thermoSystem = null;

    
	@BeforeAll
	public static void setUp(){
	        thermoSystem = new SystemSrkEos(128.0, 10.0);
	        thermoSystem.addComponent("methane", 10.0);
	        thermoSystem.createDatabase(true);
	        thermoSystem.setMixingRule(2);
	}
	
	@Disabled
    @Test
    public void testDewBubblePointT() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        double startTemp = thermoSystem.getTemperature();
        double bubblePointT=0.0, dewPointT=10.0;
        thermoSystem.setPressure(10.0);
        try {
        	testOps.bubblePointTemperatureFlash();
        	bubblePointT = thermoSystem.getTemperature();
        	thermoSystem.setTemperature(startTemp);
        	testOps.dewPointTemperatureFlash(false);
        	dewPointT = thermoSystem.getTemperature();
        	
        }
        catch(Exception e) {
        }
        
        assertTrue(Math.abs(bubblePointT-dewPointT) < 1e-2);
    }
    
    @Disabled
    @Test
    public void testSaturateWIthWater() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.saturateWithWater();
        assertTrue(thermoSystem.getPhase(0).hasComponent("water"));
    }
    
 

}
