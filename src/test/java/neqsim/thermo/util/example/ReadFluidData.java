package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ReadFluidData class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class ReadFluidData {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ReadFluidData.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 25.0, 1.8);
    // testSystem.addComponent("nitrogen", 12.681146444);
    testSystem.addComponent("methane", 90.681146444);
    testSystem.addComponent("CO2", 12.185242497);
    testSystem.addComponent("n-hexane", 100.681146444);
    // testSystem.addComponent("water", 78.0590685);
    testSystem.createDatabase(true);
    // testSystem.init(0);
    // testSystem.init(1);
    testSystem.setMixingRule(2);

    // testSystem.saveFluid(55);
    // testSystem.readFluid("AsgardB");
    // testSystem = testSystem.readObject(55);
    // testSystem.setMultiPhaseCheck(true);
    // testSystem.getCharacterization().characterisePlusFraction();

    // testSystem.createDatabase(true);
    // testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    try {
      testOps.calcPTphaseEnvelope(true);
      testOps.displayResult();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
