package neqsim.physicalproperties.util.examples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflash_1 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflash_1 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflash_1.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 55, 100.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("nC14", 64.6);
    testSystem.addComponent("methane", 38.99);
    testSystem.addComponent("ethane", 11.4);
    testSystem.addComponent("propane", 11.4);
    // testSystem.addComponent("n-pentane", 24.0);
    // testSystem.addComponent("c-hexane", 1.0);
    // testSystem.addComponent("nC10", 1.0);
    // testSystem.addComponent("water", 12);
    // testSystem.addComponent("TEG", 12);
    // testSystem.addComponent("MDEA", 1);
    // testSystem.addComponent("CO2", 90.0);
    // testSystem.addComponent("propane", 20.0);
    // testSystem.addComponent("water", 10.0);

    // testSystem.addTBPfraction("C6",1.0, 86.178/1000.0, 0.664);
    // testSystem.addTBPfraction("C7",1.0, 96.0/1000.0, 0.738);
    // testSystem.addTBPfraction("C8",1.0, 107.0/1000.0, 0.765);
    testSystem.addTBPfraction("C9", 56.0, 296.0 / 1000.0, 0.955);
    // testSystem.useVolumeCorrection(true);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);
    // testSystem.initPhysicalProperties();
    // testSystem.setPhysicalPropertyModel(6);
    // testSystem.getInterphaseProperties().setInterfacialTensionModel(1);
    try {
      testOps.TPflash();
      // testOps.bubblePointPressureFlash(true);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();

    // System.out.println("chem pot 1 " +
    // testSystem.getPhase(0).getComponent(0).getGibbsEnergy(testSystem.getTemperature(),testSystem.getPressure())/testSystem.getPhase(0).getComponent(0).getNumberOfMolesInPhase());
    // System.out.println("chem pot 2 " +
    // testSystem.getPhase(1).getComponent(0).getGibbsEnergy(testSystem.getTemperature(),testSystem.getPressure())/testSystem.getPhase(1).getComponent(0).getNumberOfMolesInPhase());
  }
}
