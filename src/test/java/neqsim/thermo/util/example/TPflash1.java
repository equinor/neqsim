package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * TPflash1 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflash1 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflash1.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    // SystemInterface testSystem = new SystemSrkEos(288.15 + 5, 165.01325);
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 100.0, 0.5);
    // testSystem.addComponent("CO2", 10.01);
    testSystem.addComponent("water", 10.000083156844);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testOps.TPflash();
    // long time = System.currentTimeMillis();
    // testOps.TPflash();
    for (int i = 0; i < 1; i++) {
      // testOps.TPflash();
      try {
        testOps.bubblePointPressureFlash(false);
        // testOps.waterDewPointTemperatureMultiphaseFlash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      // testSystem.init(0);
      // testSystem.init(1);
    }

    // testSystem.init(2);
    // System.out.print("gas enthalpy " +
    // testSystem.getPhase(0).getEnthalpy("kJ/kg"));
    // System.out.print("liquid enthalpy " +
    // testSystem.getPhase(1).getEnthalpy("kJ/kg"));
    testSystem.display();
  }
}
