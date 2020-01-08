/*
 * ScalePotentialCheck.java
 *
 * Created on 19. desember 2005, 14:39
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/**
 *
 * @author ESOL
 */
public class ScalePotentialCheck {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(ScalePotentialCheck.class);

    /**
     * Creates a new instance of ScalePotentialCheck
     */
    public ScalePotentialCheck() {

    }

    public static void main(String[] args) {
        // SystemInterface testSystem = new Ele(273.15+10.0,10.0);
        SystemInterface testSystem = new SystemElectrolyteCPAstatoil(273.15 + 25.0, 10.0);
        // SystemInterface testSystem = new SystemFurstElectrolyteEosMod2004(273.15 + 15.0, 1.0);
        // SystemInterface testSystem = new SystemSrkEos(273.15 + 50.0, 10.0);
//        testSystem.addComponent("Cl-",0.001);
//        testSystem.addComponent("CO3--",0.00000095);
        // testSystem.addComponent("HCO3-",0.0001);
        testSystem.addComponent("methane", 90);
        //testSystem.addComponent("ethane", 10.5);
        testSystem.addComponent("CO2",0.1);
        testSystem.addComponent("H2S", 0.01);
     //      testSystem.addComponent("n-heptane", 15.2);
        //   testSystem.addComponent("nC10", 0.52);
        //  testSystem.addComponent("MEG", 0.1);
        //    testSystem.addComponent("MDEA", 1);
        testSystem.addComponent("water", 1, "kg/sec");
  //      testSystem.addComponent("Mg++", 0.07);// * 24.31);
        testSystem.addComponent("Na+", 4e-5);
        testSystem.addComponent("Cl-", 4e-5);
       //  testSystem.addComponent("Hg++", 4e-7);
           testSystem.addComponent("OH-", 220e-5);
            testSystem.addComponent("Fe++", 110.1e-5);
  //      testSystem.addComponent("OH-", 0.07*2);// * 17.001);
        //  testSystem.addComponent("Cl-", (1000 - 100) * 1e-3);
        //testSystem.addComponent("Ca++",0.002);
        //testSystem.addComponent("CO3--",14.0E-6);
//        testSystem = testSystem.autoSelectModel();
        testSystem.chemicalReactionInit();
        //  testSystem.isChemicalSystem(false);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.setMultiPhaseCheck(true);
        //  testSystem.isChemicalSystem(false);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.TPflash();
            testSystem.display();

           testOps.calcIonComposition(testSystem.getPhaseNumberOfPhase("aqueous"));
           testOps.display();

            testOps.checkScalePotential(testSystem.getPhaseNumberOfPhase("aqueous"));
            testOps.display();
            // testOps.addIonToScaleSaturation(1,"FeCO3","Fe++");
           // testOps.display();
            logger.info(testOps.getResultTable());
        } catch (Exception e) {
            logger.error("error",e);
        }
        logger.info("pH " + testSystem.getPhase("aqueous").getpH());
        // testSystem.display();

    }

}
