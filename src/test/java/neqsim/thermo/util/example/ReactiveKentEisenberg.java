package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ReactiveKentEisenberg class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class ReactiveKentEisenberg {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ReactiveKentEisenberg.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemKentEisenberg(326.0, 1.1);
    SystemInterface testSystem = new SystemFurstElectrolyteEos(326.0, 0.1);

    // testSystem.addComponent("methane", 0.01);
    testSystem.addComponent("H2S", 0.01);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 0.1);
    // testSystem.addComponent("Piperazine", 0.1);

    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);
    // testSystem.setPhysicalPropertyModel(PhysicalPropertyModel.AMINE);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);

    try {
      for (int i = 0; i < 1; i++) {
        testSystem.addComponent("H2S", 0.01);
        ops.bubblePointPressureFlash(false);
        logger.info("pres H2S "
            + testSystem.getPressure() * testSystem.getPhase(0).getComponent("H2S").getx());
      }
      // ops.TPflash();
    } catch (Exception ex) {
    }
    testSystem.display();
    logger.info("pH " + testSystem.getPhase(1).getpH());
  }
}
