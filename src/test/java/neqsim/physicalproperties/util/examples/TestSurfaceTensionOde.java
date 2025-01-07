package neqsim.physicalproperties.util.examples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestSurfaceTensionOde class.
 * </p>
 *
 * @author oberg
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestSurfaceTensionOde {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestSurfaceTensionOde.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    double yscale;
    SystemInterface testSystem = new SystemSrkEos(273.15, 1.0);

    testSystem.addComponent("methane", 5.0);
    // testSystem.addComponent("TEG", 5.0);
    testSystem.addComponent("n-heptane", 2.01);
    // testSystem.addComponent("n-heptane", 112.0);
    // testSystem.addComponent("water", 10.0);
    // testSystem.addComponent("water", 50.0);
    // testSystem.addComponent("MEG", 50.0);

    // testSystem.addComponent("water", 100);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    // testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);
    testSystem.getInterphaseProperties().setInterfacialTensionModel(1);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
  }
}
