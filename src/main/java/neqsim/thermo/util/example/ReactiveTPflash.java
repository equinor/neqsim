package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemFurstElectrolyteEosMod2004;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/*
 * TPflash.java
 *l
 * Created on 27. september 2001, 09:43
 */

/**
 *
 * @author esol
 * @version
 */
public class ReactiveTPflash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(ReactiveTPflash.class);

    /**
     * Creates new TPflash
     */
    public ReactiveTPflash() {
    }

    public static void main(String args[]) {
        // SystemInterface testSystem = new SystemFurstElectrolyteEosMod2004(423.2,
        // 24.4);
        SystemInterface testSystem = new SystemElectrolyteCPAstatoil(308.3, 3.8);

        testSystem.addComponent("methane", 99.0);
        testSystem.addComponent("CO2", 1.0e-6);
        // testSystem.addComponent("H2S", 0.10);
        // testSystem.addComponent("MDEA", 13.0);
        // testSystem.addComponent("nC10", 1.00);
        testSystem.addComponent("water", 11.00, "kg/sec");
        // testSystem.addComponent("Na+", 1.200);
        // testSystem.addComponent("OH-", 1.200);
        // testSystem.addComponent("MDEA", 1.100, "mol/sec");
        // testSystem.addComponent("HCO3-", 0.100, "mol/sec");
        // testSystem.addComponent("Piperazine", 0.1e-4);

        testSystem.chemicalReactionInit();
        // testSystem.useVolumeCorrection(true);
        testSystem.createDatabase(true);
        testSystem.setMultiPhaseCheck(true);
        testSystem.setMixingRule(10);
        // testSystem.
        ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
        testSystem.init(0);
        testSystem.init(1);
        // testSystem.init(1);
//        System.out.println("wt% MDEA " + 100*testSystem.getPhase(1).getComponent("MDEA").getx()*testSystem.getPhase(1).getComponent("MDEA").getMolarMass()/(testSystem.getPhase(1).getComponent("MDEA").getx()*testSystem.getPhase(1).getComponent("MDEA").getMolarMass()+testSystem.getPhase(1).getComponent("water").getx()*testSystem.getPhase(1).getComponent("water").getMolarMass()));
//        System.out.println("wt% Piperazine " + testSystem.getPhase(1).getComponent("Piperazine").getx()*testSystem.getPhase(1).getComponent("Piperazine").getMolarMass()/testSystem.getPhase(1).getMolarMass());

        try {
            // testSystem.getChemicalReactionOperations().solveChemEq(1, 0);
            // testSystem.getChemicalReactionOperations().solveChemEq(1, 1);
            // ops.bubblePointPressureFlash(false);
            // // ops.bubblePointPressureFlash(false);
            ops.TPflash();
            // ops.dewPointTemperatureFlash();
        } catch (Exception e) {
        }
        testSystem.display();
        System.out.println("pH " + testSystem.getPhase(1).getpH());
        logger.info("pH " + testSystem.getPhase(1).getpH());
        logger.info("activity coefficiet water " + testSystem.getPhase("aqueous").getActivityCoefficient(2));
//        
//        for(int i=0;i<23;i++){
//            try{
//                ops.bubblePointPressureFlash(false);
//               // testSystem.display();
//                //ops.TPflash();
//            } catch(Exception e){}
//            
//            
//            System.out.println("loading " + (0.0005+0.05*i)+ " PCO2 " + testSystem.getPhase(0).getComponent("CO2").getx()*testSystem.getPressure());
//            testSystem.addComponent("CO2", 0.05*(6.45+1.78));
//        }
    }
}
