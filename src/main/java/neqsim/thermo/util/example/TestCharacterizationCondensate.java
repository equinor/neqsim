package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
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
 * @author esol @version
 */
public class TestCharacterizationCondensate {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(TestCharacterizationCondensate.class);

    /**
     * Creates new TPflash
     */
    public TestCharacterizationCondensate() {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(273.15 + 25.0, 50.0);

        testSystem.setFluidName("AsgardB");

        //testSystem.getCharacterization().setTBPModel("PedersenSRKHeavyOil");//(RiaziDaubert  PedersenPR  PedersenSRK
      //  testSystem.getCharacterization().setPlusFractionModel("heavyOil");
        testSystem.getCharacterization().setLumpingModel("PVTlumpingModel"); //"abLumping";
        testSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(13);
     //   testSystem.addComponent("water", 0.5);
        testSystem.addComponent("nitrogen", 0.002);
        testSystem.addComponent("CO2", 0.005);
        testSystem.addComponent("methane", 0.4);
        testSystem.addComponent("ethane", 0.03);
        testSystem.addComponent("propane", 0.01);
        testSystem.addComponent("n-butane", 0.002);
        testSystem.addComponent("i-butane", 0.006);
        testSystem.addComponent("n-pentane", 0.004);
        testSystem.addComponent("i-pentane", 0.005);

        testSystem.addTBPfraction("C6", 0.004, 85.0253 / 1000.0, 0.667229);
        testSystem.addTBPfraction("C7", 0.001, 90.3717 / 1000.0, 0.7463691);
        testSystem.addTBPfraction("C8", 0.001, 102.46950 / 1000.0, 0.7709114);
        testSystem.addTBPfraction("C9", 0.001, 115.6 / 1000.0, 0.7901);
        testSystem.addTBPfraction("C10", 0.02, 225.5046 / 1000.0, 0.8411014);

      //  testSystem.addComponent("water", 10.87);
        //  testSystem.addPlusFraction("C11", 1.44, 231.0 / 1000, 0.87);
        testSystem.setHeavyTBPfractionAsPlusFraction();
        testSystem.getCharacterization().characterisePlusFraction();

//testSystem.setHydrateCheck(true);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.setMultiPhaseCheck(true);
        testSystem.setTotalFlowRate(0.0, "kg/sec");
     //   testSystem.initPhysicalProperties();
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        try {
            testOps.TPflash();
            //           testOps.hydrateFormationTemperature();
//            testOps.dewPointTemperatureFlash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
        
        
        /*
        System.out.println("molar mass " +testSystem.getPhase(0).getComponent("PC4_PC").getMolarMass() );
        
        testSystem.setMolarCompositionOfPlusFluid(new double[]{0.02, 0.005, 0.4, 0.01, 0.01, 0.02, 0.02, 0.01 ,0.01, 0.01, 0.01 ,0.01, 0.01, 0.2 });
        try {
            testOps.TPflash();
            //           testOps.hydrateFormationTemperature();
//            testOps.dewPointTemperatureFlash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
        */
    }
}
