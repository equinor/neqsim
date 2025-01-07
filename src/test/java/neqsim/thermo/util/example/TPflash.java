package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflash class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 42.498, 19.2875);
    testSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(5);
    testSystem.addComponent("water", 40.0);
    testSystem.addComponent("TEG", 40.0);
    testSystem.addComponent("nitrogen", 40.0);
    testSystem.addComponent("CO2", 40.0);
    testSystem.addComponent("methane", 14);
    testSystem.addComponent("ethane", 14);
    testSystem.addComponent("propane", 14);
    testSystem.addComponent("n-butane", 14);
    testSystem.addComponent("i-butane", 14);
    testSystem.addComponent("ethane", 14);
    testSystem.addComponent("n-pentane", 14);
    testSystem.addComponent("i-pentane", 14);
    /*
     * testSystem.addTBPfraction("C6", 1.0, 85.0253 / 1000.0, 0.667229);
     * testSystem.addTBPfraction("C7", 1.0, 90.3717 / 1000.0, 746.3681 / 1000.0);
     * testSystem.addTBPfraction("C8", 1.0, 102.4695 / 1000.0, 770.9114 / 1000.0);
     * testSystem.addTBPfraction("C9", 1.0, 115.6 / 1000.0, 790.1 / 1000.0);
     * testSystem.addTBPfraction("C10", 1.0, 225.5046 / 1000.0, 841.1014 / 1000.0);
     * testSystem.setMolarComposition(new double[] { 0.829, 0, 0.0007, 0.002, 0.0707, 0.0072,
     * 0.0051, 0.0019, 0.0062, 0.0048, 0.006, 0.0074, 0.0109, 0.0109, 0.0062, 0.031 });
     *
     * testSystem.setHeavyTBPfractionAsPlusFraction();
     * testSystem.getCharacterization().characterisePlusFraction();
     */
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    try {
      testOps.TPflash();
    } catch (Exception ex) {
    }

    // ObjectMapper om = new ObjectMapper(new YAMLFactory());
    // om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    try {
      // om.writeValue(new File("c:/temp/person2.yaml"),
      // testSystem.getPhase(0).getComponent(0));
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    testSystem.display();
    // testSystem.displayPDF();
  }
}
