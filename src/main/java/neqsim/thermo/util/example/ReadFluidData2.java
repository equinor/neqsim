package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
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
public class ReadFluidData2 {

	private static final long serialVersionUID = 1000;
	static Logger logger = Logger.getLogger(ReadFluidData.class);

	/**
	 * Creates new TPflash
	 */
	public ReadFluidData2() {
	}

	public static void main(String args[]) {

		SystemInterface testSystem = new SystemSrkEos(273.15 + 30.0, 10.0);//
		// testSystem =
		// testSystem.readObjectFromFile("C:/temp/neqsimfluids/-65919.68493879325.neqsim",
		testSystem = testSystem.readObjectFromFile("c:/temp/neqsimfluidwater.neqsim","");
		testSystem.setPressure(100.0);
		testSystem.setTemperature(273.15 + 15.0);
	//	// "");
	//	testSystem.addComponent("water", 1.0);
	//	testSystem.setMixingRule(2);
	//	testSystem.setMultiPhaseCheck(true);
		//testSystem.setMultiPhaseCheck(false);
		ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

		try {
			testOps.TPflash();
			testSystem.display();
			testOps.PSflash(-123.108602625942);
			testSystem.display();
			testSystem.setPressure(100.0);
			testOps.PSflash(-119.003271056256);
			testSystem.display();
			System.out.println("entropy " + testSystem.getEntropy());
			//testSystem.setPressure(100.0);
			//testOps.PSflash(-1.503016881785468e+02);
			//testSystem.display();
			//testSystem.setPressure(100.0);
		//	testOps.PSflash(-1.266377583884310e+02);
		} catch (Exception e) {
			logger.error(e.toString());
		}

	}
}
