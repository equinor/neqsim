package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class TPgradientFlashTest {
  private static final Logger logger = LogManager.getLogger(TPgradientFlashTest.class);

  /** Logger object for class. */

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
    // double depth = 1000.0;
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

  /**
   * Checks the five-component gas against independent 60/80-digit cubic-EOS references.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reservoirGasGradientMatchesIndependentReference(boolean pr) {
    SystemInterface testSystem = TPgradientFlashJacobianTest.reservoirGas(pr);
    SystemInterface result = new ThermodynamicOperations(testSystem).TPgradientFlash(3000.0, 390.0);

    // Reproduce using test/resources/.../gradient-cubic-reference/reproduce.py.
    double expectedPressure = pr ? 280.0017890516890655786 : 279.7907442694809963308;
    assertEquals(expectedPressure, result.getPressure(), 1.0e-12);
    double[] expected = pr
        ? new double[] {0.01151613994142086504, 0.02458625611273295182, 0.83738905902534626105, 0.07198234381927365000,
            0.05452620110122627208}
        : new double[] {0.01154066937745200692, 0.02455233209587859621, 0.83782739542774358252, 0.07181519967234902144,
            0.05426440342657679290};
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], result.getPhase(0).getComponent(i).getx(), 1.0e-14);
    }
    double compositionSum = 0.0;
    for (int i = 0; i < expected.length; i++) {
      double composition = result.getPhase(0).getComponent(i).getx();
      assertTrue(composition > 0.0);
      compositionSum += composition;
    }
    assertEquals(1.0, compositionSum, 1.0e-14);
    assertEquals(390.0, result.getTemperature(), 0.0);
    assertEquals(result.getPressure(), ((PhaseEos) result.getPhase(0)).calcPressure(), 1.0e-8);
    assertEquals(360.0, testSystem.getTemperature(), 0.0);
    assertEquals(250.0, testSystem.getPressure(), 0.0);
  }
}
