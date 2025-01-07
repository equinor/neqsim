package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestCharacterizationCondensate class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestCharacterizationCondensate {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestCharacterizationCondensate.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 25.0, 50.0);

    testSystem.setFluidName("AsgardB");

    // testSystem.getCharacterization().setTBPModel("PedersenSRKHeavyOil"); //(RiaziDaubert
    // PedersenPR PedersenSRK
    // testSystem.getCharacterization().setPlusFractionModel("heavyOil");
    // testSystem.getCharacterization().setLumpingModel("PVTlumpingModel");
    // //"abLumping";
    testSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    // testSystem.addComponent("water", 0.5);
    // testSystem.addComponent("TEG", 0.5);
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
    testSystem.addTBPfraction("C10", 0.001, 125.5046 / 1000.0, 0.80411014);

    testSystem.addTBPfraction("C11", 0.004, 135.0253 / 1000.0, 0.8167229);
    testSystem.addTBPfraction("C12", 0.001, 145.3717 / 1000.0, 0.8263691);
    testSystem.addTBPfraction("C13", 0.001, 158.46950 / 1000.0, 0.827709114);
    testSystem.addTBPfraction("C14", 0.001, 168.6 / 1000.0, 0.827901);
    testSystem.addTBPfraction("C15", 0.001, 172.5046 / 1000.0, 0.8311014);

    testSystem.addTBPfraction("C16", 0.004, 190.0253 / 1000.0, 0.83667229);
    testSystem.addTBPfraction("C17", 0.001, 211.3717 / 1000.0, 0.8363691);
    testSystem.addTBPfraction("C18", 0.001, 220.46950 / 1000.0, 0.827709114);
    testSystem.addTBPfraction("C19", 0.001, 245.6 / 1000.0, 0.8401);
    testSystem.addTBPfraction("C20", 0.03, 391.5046 / 1000.0, 0.8617411014);

    // testSystem.addComponent("water", 10.87);
    // testSystem.addPlusFraction("C11", 1.44, 231.0 / 1000, 0.87);
    testSystem.setHeavyTBPfractionAsPlusFraction();
    testSystem.getCharacterization().characterisePlusFraction();
    System.out.println("number of components " + testSystem.getNumberOfComponents());
    // testSystem.setHydrateCheck(true);
    testSystem.createDatabase(true);
    logger.info("start benchmark TPflash......");
    long time = System.currentTimeMillis();
    testSystem.setMixingRule(2);
    logger.info("Time taken for benchmark flash = " + (System.currentTimeMillis() - time));
    testSystem.setMultiPhaseCheck(true);
    testSystem.setTotalFlowRate(1.0, "kg/sec");
    // testSystem.initPhysicalProperties();
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    try {
      testOps.TPflash();
      // testOps.hydrateFormationTemperature();
      // testOps.dewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();

    // double[] molaFrac = new double[]{0.01, 0.01, 0.6, 0.1, 0.02, 0.02, 0.01,
    // 0.001, 0.002, 0.01, 0.001, 0.001,0.001, 0.4};
    // testSystem.setMolarCompositionPlus(molaFrac);
    try {
      testOps.TPflash();
      // testOps.hydrateFormationTemperature();
      // testOps.dewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
    // System.out.println("number of lumped components " +
    // testSystem.getCharacterization().getLumpingModel().getNumberOfLumpedComponents());
    // System.out.println("number of pseudo components " +
    // testSystem.getCharacterization().getLumpingModel().getNumberOfPseudoComponents());
    // System.out.println("lumped component " +
    // testSystem.getCharacterization().getLumpingModel().getLumpedComponentName(3));

    /*
     * System.out.println("molar mass "
     * +testSystem.getPhase(0).getComponent("PC4_PC").getMolarMass() );
     *
     * testSystem.setMolarCompositionOfPlusFluid(new double[]{0.02, 0.005, 0.4, 0.01, 0.01, 0.02,
     * 0.02, 0.01 ,0.01, 0.01, 0.01 ,0.01, 0.01, 0.2 }); try { testOps.TPflash(); //
     * testOps.hydrateFormationTemperature(); // testOps.dewPointTemperatureFlash(); } catch
     * (Exception ex) { logger.error(ex.toString()); } testSystem.display();
     */
  }
}
