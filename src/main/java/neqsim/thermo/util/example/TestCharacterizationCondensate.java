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
        SystemInterface testSystem = new SystemSrkEos(273.15 + 121.0, 250.0);

        testSystem.setFluidName("AsgardB");

        //testSystem.getCharacterization().setTBPModel("PedersenSRKHeavyOil");//(RiaziDaubert  PedersenPR  PedersenSRK
      //  testSystem.getCharacterization().setPlusFractionModel("heavyOil");
        testSystem.getCharacterization().setLumpingModel("no"); //"abLumping";
        testSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(12);

        testSystem.addComponent("nitrogen", 0.87);
        testSystem.addComponent("CO2", 2.769723);
        testSystem.addComponent("methane", 68.30317);
        testSystem.addComponent("ethane", 9.519049);
        testSystem.addComponent("propane", 5.769423);
        testSystem.addComponent("n-butane", 2.749725);
        testSystem.addComponent("n-pentane", 1.449855);

      //  testSystem.addTBPfraction("C6", 1.49985, 86.3 / 1000.0, 0.7432);
        testSystem.addTBPfraction("C7", 1.0, 187.0 / 1000.0, 0.84738);
   //   testSystem.addTBPfraction("C8", 0.939906, 107.0 / 1000.0, 0.765);
    //    testSystem.addTBPfraction("C9", 0.879912, 121.0 / 1000.0, 0.781);
   //     testSystem.addTBPfraction("C10", 1.45, 200.0 / 1000.0, .8948);

        testSystem.addComponent("water", 10.87);
        //  testSystem.addPlusFraction("C11", 1.44, 231.0 / 1000, 0.87);
        testSystem.setHeavyTBPfractionAsPlusFraction();
        testSystem.getCharacterization().characterisePlusFraction();

//testSystem.setHydrateCheck(true);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.setMultiPhaseCheck(true);
        testSystem.initPhysicalProperties();
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        try {
            testOps.TPflash();
            //           testOps.hydrateFormationTemperature();
//            testOps.dewPointTemperatureFlash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
    }
}
