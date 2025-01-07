package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class TPgradientFlashTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPgradientFlashTest.class);

  @Test
  void testRun() {
    SystemInterface testSystem = new SystemSrkEos(345, 80.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("hydrogen", 1.0);
    testSystem.addComponent("methane", 8.0);
    testSystem.addComponent("propane", 1.0);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    SystemInterface newSystem = null;
    try {
      newSystem = testOps.TPgradientFlash(1000, 355).phaseToSystem(0);
      // newSystem.prettyPrint();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    assertEquals(0.095513700959, newSystem.getComponent("hydrogen").getx(), 1e-4);
  }

  @Test
  void testGradient() {

    double depth = 1000.0;
    double temperature = 273.15 + 70;
    double pressure = 80.0;

    ArrayList<Double> x_h2 = new ArrayList<>();
    ArrayList<Double> p_depth = new ArrayList<>();

    SystemInterface testSystem = new SystemSrkEos(temperature, pressure);
    testSystem.addComponent("hydrogen", 1.0);
    testSystem.addComponent("methane", 8.0);
    testSystem.addComponent("propane", 1.0);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    SystemInterface newSystem = null;
    double deltaHeight = 100.0;
    double deltaT = 0.5;
    for (int i = 0; i < 10; i++) {
      try {
        newSystem = testOps.TPgradientFlash(i * deltaHeight, temperature + i * deltaT);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      x_h2.add(newSystem.getComponent("hydrogen").getx());
      p_depth.add(newSystem.getPressure());
      // System.out
      // .println(newSystem.getComponent("hydrogen").getx() + " " + newSystem.getPressure());
    }
    assertEquals(0.0964169380341, x_h2.get(6), 1e-4);

  }

}
