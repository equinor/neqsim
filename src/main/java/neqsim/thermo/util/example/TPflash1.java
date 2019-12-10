package neqsim.thermo.util.example;

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
 * @author esol @version
 */
public class TPflash1 {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(TPflash1.class);

    /**
     * Creates new TPflash
     */
    public TPflash1() {
    }

    public static void main(String[] args) {

        // SystemInterface testSystem = new SystemSrkEos(288.15 + 5, 165.01325);//
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 25.0, 88.8);//
        testSystem.addComponent("nitrogen", 1.681146444);
        testSystem.addComponent("CO2", 2.185242497);
        testSystem.addComponent("methane", 78.0590685);
        testSystem.addComponent("ethane", 10.04443372);
        testSystem.addComponent("propane", 5.588061435);
        testSystem.addComponent("i-butane", 0.647553889);
        testSystem.addComponent("n-butane", 1.386874239);
        testSystem.addComponent("i-pentane", 0.288952839);
        testSystem.addComponent("n-pentane", 1.446888586);
        testSystem.addComponent("n-hexane", 1.446888586);
        testSystem.addComponent("nC10", 1.446888586);
        testSystem.addComponent("water", 10.35509484);
        testSystem.addComponent("MEG", 0.083156844);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.setMultiPhaseCheck(true);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testOps.TPflash();
        long time = System.currentTimeMillis();
        //testOps.TPflash();
        for (int i = 0; i < 1; i++) {
            testOps.TPflash();
            try {
                //   testOps.waterDewPointTemperatureMultiphaseFlash();
            } catch (Exception e) {
                logger.error("error", e);
            }
            //testSystem.init(0);
            //     testSystem.init(1);
        }

        testSystem.display();
        
        neqsim.util.database.NeqSimDataBase.setDataBaseType("Derby");
        neqsim.util.database.NeqSimDataBase.setConnectionString("jdbc:derby:classpath:data/neqsimthermodatabase");
        

        testSystem = new SystemSrkCPAstatoil(273.15 + 25.0, 88.8);//
        testSystem.addComponent("nitrogen", 1.681146444);
        testSystem.addComponent("CO2", 2.185242497);
        testSystem.addComponent("methane", 78.0590685);
        testSystem.addComponent("ethane", 10.04443372);
        testSystem.addComponent("propane", 5.588061435);
        testSystem.addComponent("i-butane", 0.647553889);
        testSystem.addComponent("n-butane", 1.386874239);
        testSystem.addComponent("i-pentane", 0.288952839);
        testSystem.addComponent("n-pentane", 1.446888586);
        testSystem.addComponent("n-hexane", 1.446888586);
        testSystem.addComponent("nC10", 1.446888586);
        testSystem.addComponent("water", 10.35509484);
        testSystem.addComponent("MEG", 0.083156844);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(9);
        testSystem.setMultiPhaseCheck(true);

        testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();
        //testOps.TPflash();
        for (int i = 0; i < 1; i++) {
            testOps.TPflash();
            try {
                //   testOps.waterDewPointTemperatureMultiphaseFlash();
            } catch (Exception e) {
                logger.error("error", e);
            }
            //testSystem.init(0);
            //     testSystem.init(1);
        }
        testSystem.display();
         ((neqsim.thermo.phase.PhaseEosInterface) testSystem.getPhase(0)).displayInteractionCoefficients("");

    }
}
