package neqsim.thermodynamicOperations.util.example;

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
public class VUflash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(VUflash.class);

    /** Creates new TPflash */
    public VUflash() {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(273.15 + 15, 10.01325);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 11.0);
       // testSystem.addComponent("ethane", 4.0);
      //  testSystem.addComponent("n-heptane", 10.5);
        testSystem.addComponent("water", 10.5);
       // testSystem.addComponent("TEG", 0.000000);
      //  testSystem.setMultiPhaseCheck(true);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.setMultiPhaseCheck(true);


        testSystem.init(0);
        testSystem.display();
        try {
            testOps.TPflash();
            testSystem.display();

            logger.info("Volume " + testSystem.getVolume()*1.1 + " internalEnergy " + testSystem.getInternalEnergy());
         //   testSystem.setPressure(5);
           // testOps.PHflash(testSystem.getEnthalpy(), 0);
             testOps.VUflash(testSystem.getVolume()*1.1, testSystem.getInternalEnergy());
            logger.info("Volume " + testSystem.getVolume() + " internalEnergy " + testSystem.getInternalEnergy());

            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }
    }
}
