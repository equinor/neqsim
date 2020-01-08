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

		SystemInterface testSystem = new SystemSrkEos(273.15 + 36.0, 120.0);//
		testSystem.addComponent("methane", 82.71604938);
		testSystem.addComponent("ethane", 6.172839506);
		testSystem.addComponent("propane", 4.938271605);
		testSystem.addComponent("i-butane", 3.703703704);
		testSystem.addComponent("n-butane", 1.234567901);
		testSystem.addComponent("n-hexane", 1.234567901);
		testSystem.createDatabase(true);
		testSystem.setMixingRule(2);
	 //   testSystem.setMultiPhaseCheck(true);

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
