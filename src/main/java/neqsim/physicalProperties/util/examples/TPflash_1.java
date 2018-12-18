package neqsim.physicalProperties.util.examples;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
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
public class TPflash_1 {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(TPflash_1.class);

    /** Creates new TPflash */
    public TPflash_1() {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 0, 190.0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
       testSystem.addComponent("methane", 64.6);
       testSystem.addComponent("ethane", 11.4);
     //   testSystem.addComponent("n-pentane", 24.0);
       // testSystem.addComponent("c-hexane", 1.0);
       // testSystem.addComponent("nC10", 1.0);
        // testSystem.addComponent("water", 12);
        testSystem.addComponent("TEG", 12);
        // testSystem.addComponent("MDEA", 1);
        //        testSystem.addComponent("CO2", 90.0);
        //   testSystem.addComponent("propane", 20.0);
        //testSystem.addComponent("water", 10.0);

        //        testSystem.addTBPfraction("C6",1.0, 86.178/1000.0, 0.664);
        //       testSystem.addTBPfraction("C7",1.0, 96.0/1000.0, 0.738);
        //        testSystem.addTBPfraction("C8",1.0, 107.0/1000.0, 0.765);
        //        testSystem.addTBPfraction("C9",1.0, 121.0/1000.0, 0.781);
        //testSystem.useVolumeCorrection(true);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.setMultiPhaseCheck(true);
        testSystem.init(0);
        //testSystem.initPhysicalProperties();
        //testSystem.setPhysicalPropertyModel(6);
        //testSystem.getInterphaseProperties().setInterfacialTensionModel(1);
        try {
            testOps.TPflash();
            //   testOps.bubblePointPressureFlash(true);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
       
        // System.out.println("chem pot  1 " + testSystem.getPhase(0).getComponent(0).getGibbsEnergy(testSystem.getTemperature(),testSystem.getPressure())/testSystem.getPhase(0).getComponent(0).getNumberOfMolesInPhase());
        // System.out.println("chem pot  2 " + testSystem.getPhase(1).getComponent(0).getGibbsEnergy(testSystem.getTemperature(),testSystem.getPressure())/testSystem.getPhase(1).getComponent(0).getNumberOfMolesInPhase());
    }
}
