package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests comparing the fully implicit CPA algorithm against the standard nested approach. Verifies
 * that both produce identical thermodynamic results.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PhaseSrkCPAfullyImplicitTest extends neqsim.NeqSimTest {

  /**
   * Test pure water TP flash at liquid conditions.
   */
  @Test
  void testPureWaterLiquid() {
    double T = 273.15 + 25.0;
    double P = 1.0;

    SystemInterface standard = new SystemSrkCPAstatoil(T, P);
    standard.addComponent("water", 1.0);
    standard.setMixingRule(10);

    SystemInterface implicit = new SystemSrkCPAstatoilFullyImplicit(T, P);
    implicit.addComponent("water", 1.0);
    implicit.setMixingRule(10);

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsImpl = new ThermodynamicOperations(implicit);
    opsImpl.TPflash();
    implicit.initProperties();

    double densStd = standard.getDensity("kg/m3");
    double densImpl = implicit.getDensity("kg/m3");

    assertEquals(densStd, densImpl, densStd * 0.001,
        "Pure water density should match within 0.1%");
  }

  /**
   * Test pure water at high pressure.
   */
  @Test
  void testPureWaterHighPressure() {
    double T = 273.15 + 80.0;
    double P = 100.0;

    SystemInterface standard = new SystemSrkCPAstatoil(T, P);
    standard.addComponent("water", 1.0);
    standard.setMixingRule(10);

    SystemInterface implicit = new SystemSrkCPAstatoilFullyImplicit(T, P);
    implicit.addComponent("water", 1.0);
    implicit.setMixingRule(10);

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsImpl = new ThermodynamicOperations(implicit);
    opsImpl.TPflash();
    implicit.initProperties();

    double densStd = standard.getDensity("kg/m3");
    double densImpl = implicit.getDensity("kg/m3");

    assertEquals(densStd, densImpl, densStd * 0.001,
        "Water density at 100 bar should match within 0.1%");
  }

  /**
   * Test water-methane binary system.
   */
  @Test
  void testWaterMethane() {
    double T = 273.15 + 30.0;
    double P = 50.0;

    SystemInterface standard = new SystemSrkCPAstatoil(T, P);
    standard.addComponent("methane", 0.9);
    standard.addComponent("water", 0.1);
    standard.setMixingRule(10);
    standard.setMultiPhaseCheck(true);

    SystemInterface implicit = new SystemSrkCPAstatoilFullyImplicit(T, P);
    implicit.addComponent("methane", 0.9);
    implicit.addComponent("water", 0.1);
    implicit.setMixingRule(10);
    implicit.setMultiPhaseCheck(true);

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsImpl = new ThermodynamicOperations(implicit);
    opsImpl.TPflash();
    implicit.initProperties();

    assertEquals(standard.getNumberOfPhases(), implicit.getNumberOfPhases(),
        "Number of phases should match");

    double zStd = standard.getPhase(0).getZ();
    double zImpl = implicit.getPhase(0).getZ();

    assertEquals(zStd, zImpl, Math.max(Math.abs(zStd) * 0.01, 1e-6),
        "Compressibility factor should match within 1%");
  }

  /**
   * Test water-methanol binary at various conditions.
   */
  @Test
  void testWaterMethanol() {
    double T = 273.15 + 60.0;
    double P = 5.0;

    SystemInterface standard = new SystemSrkCPAstatoil(T, P);
    standard.addComponent("methanol", 0.5);
    standard.addComponent("water", 0.5);
    standard.setMixingRule(10);

    SystemInterface implicit = new SystemSrkCPAstatoilFullyImplicit(T, P);
    implicit.addComponent("methanol", 0.5);
    implicit.addComponent("water", 0.5);
    implicit.setMixingRule(10);

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsImpl = new ThermodynamicOperations(implicit);
    opsImpl.TPflash();
    implicit.initProperties();

    double densStd = standard.getDensity("kg/m3");
    double densImpl = implicit.getDensity("kg/m3");

    assertEquals(densStd, densImpl, densStd * 0.01,
        "Water-methanol density should match within 1%");
  }

  /**
   * Test MEG-water system (common in flow assurance).
   */
  @Test
  void testMEGWater() {
    double T = 273.15 + 20.0;
    double P = 50.0;

    SystemInterface standard = new SystemSrkCPAstatoil(T, P);
    standard.addComponent("MEG", 0.3);
    standard.addComponent("water", 0.7);
    standard.setMixingRule(10);

    SystemInterface implicit = new SystemSrkCPAstatoilFullyImplicit(T, P);
    implicit.addComponent("MEG", 0.3);
    implicit.addComponent("water", 0.7);
    implicit.setMixingRule(10);

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsImpl = new ThermodynamicOperations(implicit);
    opsImpl.TPflash();
    implicit.initProperties();

    double densStd = standard.getDensity("kg/m3");
    double densImpl = implicit.getDensity("kg/m3");

    assertEquals(densStd, densImpl, densStd * 0.01,
        "MEG-water density should match within 1%");
  }

  /**
   * Simple timing comparison for pure water over a range of conditions.
   */
  @Test
  void testPerformanceComparison() {
    int nTests = 20;
    double[] temps = new double[nTests];
    double[] pressures = new double[nTests];

    for (int i = 0; i < nTests; i++) {
      temps[i] = 273.15 + 20.0 + i * 30.0;
      pressures[i] = 1.0 + i * 50.0;
    }

    long startStd = System.nanoTime();
    for (int i = 0; i < nTests; i++) {
      SystemInterface standard = new SystemSrkCPAstatoil(temps[i], pressures[i]);
      standard.addComponent("water", 1.0);
      standard.setMixingRule(10);
      ThermodynamicOperations ops = new ThermodynamicOperations(standard);
      ops.TPflash();
    }
    long timeStd = System.nanoTime() - startStd;

    long startImpl = System.nanoTime();
    for (int i = 0; i < nTests; i++) {
      SystemInterface implicit =
          new SystemSrkCPAstatoilFullyImplicit(temps[i], pressures[i]);
      implicit.addComponent("water", 1.0);
      implicit.setMixingRule(10);
      ThermodynamicOperations ops = new ThermodynamicOperations(implicit);
      ops.TPflash();
    }
    long timeImpl = System.nanoTime() - startImpl;

    System.out.println("=== CPA Performance Comparison ===");
    System.out.println("Standard nested:     " + (timeStd / 1_000_000) + " ms");
    System.out.println("Fully implicit:      " + (timeImpl / 1_000_000) + " ms");
    double ratio = (double) timeImpl / timeStd;
    System.out.println("Implicit/Standard ratio: " + String.format("%.2f", ratio));

    // Just verify both complete without error - timing comparison is informational
    assertTrue(timeStd > 0, "Standard timing should be positive");
    assertTrue(timeImpl > 0, "Implicit timing should be positive");
  }
}
