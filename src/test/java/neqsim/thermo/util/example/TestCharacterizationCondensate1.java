package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestCharacterizationCondensate1 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestCharacterizationCondensate1 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestCharacterizationCondensate1.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 30, 50);
    // SystemInterface testSystem = new SystemSrkCPAs(293.65, 79.3);
    testSystem.getCharacterization().setTBPModel("PedersenSRK"); // (RiaziDaubert PedersenPR
                                                                 // PedersenSRK
    testSystem.getCharacterization().setPlusFractionModel("heavyOil");
    // testSystem.getCharacterization().setTBPModel("PedersenSRK"); //(RiaziDaubert
    // PedersenPR PedersenSRK

    // testSystem.addComponent("C20", 0.006, 430, 12, 0.9); //TC PC OMEGA
    // Haklang
    testSystem.addComponent("methane", 50);
    // testSystem.addComponent("PC-C6", 0.13);
    // testSystem.addComponent("PC-C7", 0.22);
    // testSystem.addComponent("PC-C8", 0.213);
    // testSystem.addComponent("PC-C9", 0.096);
    // testSystem.addComponent("PC-C10", 0.04);
    /*
     * testSystem.addComponent("PC-C11", 0.031); testSystem.addComponent("PC-C12", 0.025);
     * testSystem.addComponent("PC-C13", 0.02); testSystem.addComponent("PC-C14", 0.016);
     * testSystem.addComponent("PC-C15-C16", 0.023); testSystem.addComponent("PC-C17-C18", 0.014);
     * testSystem.addComponent("PC-C19-C22", 0.015); testSystem.addComponent("PC-C23-C58", 0.01);
     */
    testSystem.addTBPfraction("C7", 5, 100.0 / 1000.0, 0.72);
    testSystem.addPlusFraction("C8", 50, 230.0 / 1000.0, 0.84);
    // testSystem.addPlusFraction("C8", 34, 200.0 / 1000.0, 0.82);
    testSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(12);
    testSystem.getCharacterization().characterisePlusFraction();
    /*
     * testSystem.getInterphaseProperties().setInterfacialTensionModel(0);
     */
    // System.out.println("number of components " + testSystem.getNumberOfComponents());
    testSystem.useVolumeCorrection(true);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);
    // System.out.println("number of components " + testSystem.getNumberOfComponents());
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    try {
      testOps.TPflash();
      testSystem.display();

      testSystem.resetCharacterisation();
      testSystem.createDatabase(true);
      testSystem.setMixingRule(2);
      testSystem.setMultiPhaseCheck(true);
      testOps = new ThermodynamicOperations(testSystem);

      testOps.TPflash();
      testSystem.display();
      // testOps.hydrateFormationTemperature();
      // testOps.dewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    System.out.println("activity coefficient " + testSystem.getPhase(1).getActivityCoefficient(1));
    testSystem.display();
    ((PhaseEosInterface) testSystem.getPhase(0)).displayInteractionCoefficients("");
    testSystem.getPhase(0).getComponent(1).getAcentricFactor();
  }
}
