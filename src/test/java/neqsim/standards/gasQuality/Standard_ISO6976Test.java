/**
 * 
 */
package neqsim.standards.gasQuality;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author ESOL
 *
 */
class Standard_ISO6976Test {
	static SystemInterface testSystem = null;
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
        testSystem = new SystemSrkEos(273.15 + 20.0, 1.0);
        testSystem.addComponent("methane", 0.931819);
        testSystem.addComponent("ethane", 0.025618);
        testSystem.addComponent("nitrogen", 0.010335);
        testSystem.addComponent("CO2", 0.015391);
        testSystem.setMixingRule("classic");
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();
	}

	/**
	 * Test method for {@link neqsim.standards.gasQuality.Standard_ISO6976#calculate()}.
	 */
	@Test
	void testCalculate() {
		 Standard_ISO6976 standard = new Standard_ISO6976(testSystem, 0, 15.55, "volume");
         standard.setReferenceState("real");
         standard.setReferenceType("volume");
         standard.calculate();
         double GCV = standard.getValue("GCV");
         double WI = standard.getValue("WI");
         assertEquals(39614.56783352743, GCV, 0.01);
         assertEquals(44.61477915805513, WI, 0.01);
	}

}
