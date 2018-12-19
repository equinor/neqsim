package neqsim.physicalProperties.util.examples;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
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
public class KvitebjornCondensate {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(KvitebjornCondensate.class);

    /**
     * Creates new TPflash
     */
    public KvitebjornCondensate() {
    }

    public static void main(String args[]) {
        long time = System.currentTimeMillis();
        // SystemInterface testSystem = new SystemSrkEos(273.15 + 15.0, 1.01325);
        SystemInterface testSystem = new SystemPrEos(273.15 + 15.0, 1.01325);
     //   testSystem.getCharacterization().setTBPModel("PedersenSRKHeavyOil");
        //SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 15.0, 1.01325);
        testSystem.setFluidName("KvitebjornCondensate");
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("methane", 12.2);
    //    testSystem.addComponent("ethane", 0.027435983);
        //     testSystem.addComponent("propane", 0.497619048);
        //    testSystem.addComponent("i-butane", 0.0528045423);
        //    testSystem.addComponent("n-butane", 0.0666465933);
        //    testSystem.addComponent("22-dim-C3", 0.017033723);
        //   testSystem.addComponent("i-pentane", 0.00904989605);
        //    testSystem.addComponent("n-hexane",0.392099792);

              testSystem.addTBPfraction("C16",6.159226714,1454.276/1000.0,1.07);
        //    testSystem.addTBPfraction("C7", 0.38667029, 91.9 / 1000.0, 0.7375);
       // testSystem.addTBPfraction("C8", 0.7518732, 104.1 / 1000.0, 0.7652);
      //  testSystem.addTBPfraction("C9", 0.7518732, 404.1 / 1000.0, 0.91652);
    //          testSystem.addTBPfraction("C9",0.08020478,117.2/1000.0,0.7825);
        //         testSystem.addTBPfraction("C10", 0.064547368, 237.5/1000, 0.8379);
        //     testSystem.addTBPfraction("C11", 24.64547368, 237.5/1000, 0.8379);
        // testSystem.addPlusFraction("C10+", 48.64547368, 237.5/1000.0,0.8379);

        //testSystem.getCharacterization().characterisePlusFraction();
        testSystem.createDatabase(true);
        testSystem.setMixingRule("classic");
        testSystem.init(0);
        testSystem.init(1);
    //    testSystem.setPhysicalPropertyModel(6);
        try {
            testOps.TPflash();
            // System.out.println("critical viscosity " + testSystem.getPhase(0).getComponent("C7_PC").getCriticalViscosity());
            //testOps.calcPTphaseEnvelope(false);
            //testOps.displayResult();
            // testOps.bubblePointPressureFlash(false);
            // testOps.dewPointPressureFlash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        //testSystem.initPhysicalProperties();
        testSystem.display();
        logger.info("Time taken for benchmark flash = " + (System.currentTimeMillis() - time));

    }
}
