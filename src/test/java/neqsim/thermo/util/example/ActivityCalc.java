/*
 * ActivityCalc.java
 *
 * Created on 5. mars 2002, 15:17
 */

package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * ActivityCalc class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class ActivityCalc {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 42, 1.01325);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("TEG", 0.99);
    testSystem.addComponent("water", 0.01);

    testSystem.createDatabase(true);

    testSystem.setMixingRule(10);
    testSystem.init(0);
    testSystem.init(1);

    try {
      testOps.bubblePointPressureFlash(false);
    } catch (Exception e) {
    }

    // testSystem.display();
    System.out.println("activity water " + testSystem.getPhase(1).getActivityCoefficient(1));
  }
}
