/**
 * 
 */
package neqsim.standards.gasQuality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author ESOL
 *
 */
class Standard_ISO6976Test extends neqsim.NeqSimTest{
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

    @Test
    void testCalculate2() {
        SystemInterface testSystem = new SystemSrkEos(273.15 - 150.0, 1.0);
        testSystem.addComponent("methane", 0.931819);
        testSystem.addComponent("ethane", 0.025618);
        testSystem.addComponent("nitrogen", 0.010335);
        testSystem.addComponent("CO2", 0.015391);

        // ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        /*
         * testSystem.addComponent("methane", 0.922393); testSystem.addComponent("ethane",
         * 0.025358); testSystem.addComponent("propane", 0.01519);
         * testSystem.addComponent("n-butane", 0.000523); testSystem.addComponent("i-butane",
         * 0.001512); testSystem.addComponent("n-pentane", 0.002846);
         * testSystem.addComponent("i-pentane", 0.002832); testSystem.addComponent("22-dim-C3",
         * 0.001015); testSystem.addComponent("n-hexane", 0.002865);
         * testSystem.addComponent("nitrogen", 0.01023); testSystem.addComponent("CO2", 0.015236);
         * 
         */

        /*
         * 
         * testSystem.addComponent("methane", 0.9247); testSystem.addComponent("ethane", 0.035);
         * testSystem.addComponent("propane", 0.0098); testSystem.addComponent("n-butane", 0.0022);
         * testSystem.addComponent("i-butane", 0.0034); testSystem.addComponent("n-pentane",
         * 0.0006); testSystem.addComponent("nitrogen", 0.0175); testSystem.addComponent("CO2",
         * 0.0068);
         * 
         */

        // testSystem.addComponent("water", 0.016837);

        /*
         * testSystem.addComponent("n-hexane", 0.0); testSystem.addComponent("n-heptane", 0.0);
         * testSystem.addComponent("n-octane", 0.0); testSystem.addComponent("n-nonane", 0.0);
         * testSystem.addComponent("nC10", 0.0);
         * 
         * testSystem.addComponent("CO2", 0.68); testSystem.addComponent("H2S", 0.0);
         * testSystem.addComponent("water", 0.0); testSystem.addComponent("oxygen", 0.0);
         * testSystem.addComponent("carbonmonoxide", 0.0); testSystem.addComponent("nitrogen",
         * 1.75);
         */
        // testSystem.addComponent("MEG", 1.75);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        testSystem.init(0);
        Standard_ISO6976 standard = new Standard_ISO6976(testSystem, 0, 15.55, "volume");
        standard.setReferenceState("real");
        standard.setReferenceType("volume");
        standard.calculate();
        Assertions.assertEquals(0.9974432506378011, standard.getValue("CompressionFactor"));
        Assertions.assertEquals(39614.56783352743, standard.getValue("SuperiorCalorificValue"));
        Assertions.assertEquals(35693.92161464964, standard.getValue("InferiorCalorificValue"));
        Assertions.assertEquals(39614.56783352743, standard.getValue("GCV"));

        Assertions.assertEquals(51701.01275822569, standard.getValue("SuperiorWobbeIndex"));
        Assertions.assertEquals(46584.17339159412, standard.getValue("InferiorWobbeIndex"));

        Assertions.assertEquals(0.5870995452263126, standard.getValue("RelativeDensity"));
        Assertions.assertEquals(0.9974432506378011, standard.getValue("CompressionFactor"));
        Assertions.assertEquals(16.972142879156355, standard.getValue("MolarMass"));

        //standard.display("test");
        /*
         * StandardInterface standardUK = new UKspecifications_ICF_SI(testSystem);
         * standardUK.calculate(); System.out.println("ICF " +
         * standardUK.getValue("IncompleteCombustionFactor", ""));
         * 
         * System.out.println("HID " + testSystem.getPhase(0).getComponent("methane").getHID(273.15
         * - 150.0)); System.out.println("Hres " +
         * testSystem.getPhase(0).getComponent("methane").getHresTP(273.15 - 150.0));
         */
    }

}
