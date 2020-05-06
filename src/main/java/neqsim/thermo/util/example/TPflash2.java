package neqsim.thermo.util.example;

import neqsim.thermo.system.*;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/*
*
* @author esol @version
*/
public class TPflash2 {

	private static final long serialVersionUID = 1000;
	static Logger logger = LogManager.getLogger(TPflash2.class);

	/**
	 * Creates new TPflash
	 */
	public TPflash2() {
	}

	public static void main(String[] args) {

		SystemInterface testSystem = new SystemSrkCPAstatoil(273.15+80.0, 1.01325);//
		testSystem.addComponent("nitrogen", 8.71604938);
		//testSystem.addComponent("oxygen", 22.71604938);
		testSystem.addComponent("water", 	110.234567901);
		testSystem.createDatabase(true);
		testSystem.setMixingRule(10);
	//    testSystem.setMultiPhaseCheck(true);

		ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

		// testOps.TPflash();

		try {
			testOps.TPflash();
			// testOps.waterDewPointTemperatureMultiphaseFlash();
		} catch (Exception e) {
			logger.error("error", e);
		}
		// testSystem.init(0);
		// testSystem.init(1);

		testSystem.display();

	}
}
