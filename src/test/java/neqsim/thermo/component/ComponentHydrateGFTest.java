/**
 * 
 */
package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author ESOL
 *
 */
class ComponentHydrateGFTest {
	static SystemInterface thermoSystem = null;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		 thermoSystem = new SystemSrkCPAstatoil(298.0, 100.0);
	        thermoSystem.addComponent("methane", 11.0);
	        thermoSystem.addComponent("CO2", 1.0);
	        thermoSystem.addComponent("water", 11.0);
	        thermoSystem.setMixingRule(10);
	        thermoSystem.setHydrateCheck(true);
	        
	}

	/**
	 * Test method for {@link neqsim.thermo.component.ComponentHydrateGF#ComponentHydrateGF(java.lang.String, double, double, int)}.
	 */
	@Test
	void testComponentHydrateGFStringDoubleDoubleInt() {
		 ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
		 try {
	        testOps.hydrateFormationTemperature();
		 }
		 catch(Exception e) {
			 e.printStackTrace();
		 }
	}

}
