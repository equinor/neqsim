package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

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
public class ReadFluidData {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(ReadFluidData.class);

    /**
     * Creates new TPflash
     */
    public ReadFluidData() {
    }

    public static void main(String args[]) {

        SystemInterface testSystem = new SystemSrkEos(273.15 + 25.0, 1.8);//
        // testSystem.addComponent("nitrogen", 12.681146444);
        testSystem.addComponent("methane", 90.681146444);
        testSystem.addComponent("CO2", 12.185242497);
        testSystem.addComponent("n-hexane", 100.681146444);
        // testSystem.addComponent("water", 78.0590685);
        testSystem.createDatabase(true);
        // testSystem.init(0);
        // testSystem.init(1);
        testSystem.setMixingRule(2);

        // testSystem.saveFluid(55);
        // testSystem.readFluid("AsgardB");
        // testSystem = testSystem.readObject(55);
        // testSystem.setMultiPhaseCheck(true);
        // testSystem.getCharacterization().characterisePlusFraction();

        // testSystem.createDatabase(true);
        // testSystem.setMixingRule(2);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        try {
            testOps.calcPTphaseEnvelope(true);
            testOps.displayResult();
        } catch (Exception e) {
            logger.error(e.toString());
        }

    }
}
