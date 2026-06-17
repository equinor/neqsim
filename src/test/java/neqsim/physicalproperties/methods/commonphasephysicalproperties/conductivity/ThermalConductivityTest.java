package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for thermal conductivity methods: PFCT, Chung dense, friction theory, Filippov, and model
 * switching via setConductivityModel().
 *
 * <p>
 * Reference data from NIST WebBook (Huber et al. correlations) and Poling et al. (2001) "The
 * Properties of Gases and Liquids", 5th edition.
 * </p>
 */
public class ThermalConductivityTest {

  /**
   * Helper: set conductivity model and recalculate physical properties.
   */
  private void switchAndRecalc(SystemInterface system, int phaseIdx, String model) {
    system.getPhase(phaseIdx).getPhysicalProperties().setConductivityModel(model);
    system.getPhase(phaseIdx).initPhysicalProperties();
  }

  private void switchAndRecalc(SystemInterface system, String phaseName, String model) {
    system.getPhase(phaseName).getPhysicalProperties().setConductivityModel(model);
    system.getPhase(phaseName).initPhysicalProperties();
  }

  /**
   * Test that the default PFCT method gives reasonable methane gas conductivity at 300 K, 1 bar.
   * NIST reference: methane at 300 K, 1 bar -> ~0.0343 W/(m*K).
   */
  @Test
  void testPFCTMethaneGas() {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double lambda = system.getPhase("gas").getPhysicalProperties().getConductivity();
    assertTrue(lambda > 0.02, "Methane gas conductivity too low: " + lambda);
    assertTrue(lambda < 0.06, "Methane gas conductivity too high: " + lambda);
  }

  /**
   * Test PFCT for a natural gas mixture at moderate pressure.
   */
  @Test
  void testPFCTNaturalGasMixture() {
    SystemInterface system = new SystemSrkEos(300.0, 50.0);
    system.addComponent("methane", 0.90);
    system.addComponent("ethane", 0.10);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double lambda = system.getPhase("gas").getPhysicalProperties().getConductivity();
    assertTrue(lambda > 0.02, "Natural gas mix conductivity too low: " + lambda);
    assertTrue(lambda < 0.10, "Natural gas mix conductivity too high: " + lambda);
  }

  /**
   * Test PFCT for liquid n-heptane at 300 K, 10 bar. NIST: n-heptane liquid at 300 K -> ~0.124
   * W/(m*K).
   */
  @Test
  void testPFCTLiquidHeptane() {
    SystemInterface system = new SystemSrkEos(300.0, 10.0);
    system.addComponent("n-heptane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double lambda = system.getPhase("oil").getPhysicalProperties().getConductivity();
    assertTrue(lambda > 0.05, "Heptane liquid conductivity too low: " + lambda);
    assertTrue(lambda < 0.20, "Heptane liquid conductivity too high: " + lambda);
  }

  /**
   * Test Chung dense method for methane gas at 300 K, 1 bar.
   */
  @Test
  void testChungDenseMethaneGas() {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    switchAndRecalc(system, "gas", "Chung-dense");
    double lambda = system.getPhase("gas").getPhysicalProperties().getConductivity();

    assertTrue(lambda > 0.02, "Chung-dense methane gas conductivity too low: " + lambda);
    assertTrue(lambda < 0.06, "Chung-dense methane gas conductivity too high: " + lambda);
  }

  /**
   * Test Chung dense method for high-pressure methane gas (dense gas). At 300 K, 200 bar methane is
   * a dense gas with conductivity ~0.055-0.085 W/(m*K).
   */
  @Test
  void testChungDenseHighPressureMethane() {
    SystemInterface system = new SystemSrkEos(300.0, 200.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    switchAndRecalc(system, 0, "Chung-dense");
    double lambda = system.getPhase(0).getPhysicalProperties().getConductivity();

    assertTrue(lambda > 0.03, "Chung-dense high-P methane conductivity too low: " + lambda);
    assertTrue(lambda < 0.15, "Chung-dense high-P methane conductivity too high: " + lambda);
  }

  /**
   * Test friction theory conductivity for methane gas.
   */
  @Test
  void testFrictionTheoryMethaneGas() {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    switchAndRecalc(system, "gas", "friction theory");
    double lambda = system.getPhase("gas").getPhysicalProperties().getConductivity();

    assertTrue(lambda > 0.01, "FT methane gas conductivity too low: " + lambda);
    assertTrue(lambda < 0.08, "FT methane gas conductivity too high: " + lambda);
  }

  /**
   * Test friction theory with Peng-Robinson EOS.
   */
  @Test
  void testFrictionTheoryPengRobinson() {
    SystemInterface system = new SystemPrEos(300.0, 50.0);
    system.addComponent("methane", 0.85);
    system.addComponent("ethane", 0.10);
    system.addComponent("propane", 0.05);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    switchAndRecalc(system, "gas", "friction theory");
    double lambda = system.getPhase("gas").getPhysicalProperties().getConductivity();

    assertTrue(lambda > 0.01, "FT PR natural gas conductivity too low: " + lambda);
    assertTrue(lambda < 0.10, "FT PR natural gas conductivity too high: " + lambda);
  }

  /**
   * Test Filippov method for a binary liquid mixture. Uses methane+ethane at cryogenic conditions
   * where the liquid conductivity database parameters are well-calibrated.
   */
  @Test
  void testFilippovLiquidMixture() {
    SystemInterface system = new SystemSrkEos(120.0, 50.0);
    system.addComponent("methane", 0.5);
    system.addComponent("ethane", 0.5);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Get the liquid phase index
    int liqIdx = -1;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.LIQUID
          || system.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.OIL) {
        liqIdx = i;
        break;
      }
    }

    if (liqIdx >= 0) {
      switchAndRecalc(system, liqIdx, "Filippov");
      double lambda = system.getPhase(liqIdx).getPhysicalProperties().getConductivity();

      assertTrue(lambda > 0.01, "Filippov liquid conductivity too low: " + lambda);
      assertTrue(lambda < 0.50, "Filippov liquid conductivity too high: " + lambda);
    }
  }

  /**
   * Test that setConductivityModel correctly switches between methods.
   */
  @Test
  void testModelSwitching() {
    SystemInterface system = new SystemSrkEos(300.0, 50.0);
    system.addComponent("methane", 0.90);
    system.addComponent("ethane", 0.10);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Default should be PFCT
    double lambdaPFCT = system.getPhase("gas").getPhysicalProperties().getConductivity();
    assertTrue(lambdaPFCT > 0.0, "PFCT conductivity should be positive");

    // Switch to Chung-dense
    switchAndRecalc(system, "gas", "Chung-dense");
    double lambdaChung = system.getPhase("gas").getPhysicalProperties().getConductivity();
    assertTrue(lambdaChung > 0.0, "Chung-dense conductivity should be positive");

    // Switch to friction theory
    switchAndRecalc(system, "gas", "friction theory");
    double lambdaFT = system.getPhase("gas").getPhysicalProperties().getConductivity();
    assertTrue(lambdaFT > 0.0, "Friction theory conductivity should be positive");

    // Switch back to PFCT
    switchAndRecalc(system, "gas", "PFCT");
    double lambdaPFCT2 = system.getPhase("gas").getPhysicalProperties().getConductivity();
    assertEquals(lambdaPFCT, lambdaPFCT2, 1e-10, "PFCT result should be reproducible");
  }

  /**
   * Test that all methods give physically reasonable values for a multicomponent natural gas at a
   * range of conditions.
   */
  @Test
  void testAllMethodsPhysicallyReasonable() {
    double[][] conditions = {{250.0, 10.0}, {300.0, 50.0}, {350.0, 100.0}, {400.0, 200.0}};
    String[] methods = {"PFCT", "Chung-dense", "friction theory"};

    for (double[] cond : conditions) {
      SystemInterface system = new SystemSrkEos(cond[0], cond[1]);
      system.addComponent("methane", 0.80);
      system.addComponent("ethane", 0.10);
      system.addComponent("propane", 0.05);
      system.addComponent("n-butane", 0.03);
      system.addComponent("nitrogen", 0.02);
      system.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initProperties();

      for (String method : methods) {
        switchAndRecalc(system, 0, method);
        double lambda = system.getPhase(0).getPhysicalProperties().getConductivity();

        assertTrue(lambda > 0.005,
            method + " at T=" + cond[0] + ",P=" + cond[1] + " too low: " + lambda);
        assertTrue(lambda < 0.5,
            method + " at T=" + cond[0] + ",P=" + cond[1] + " too high: " + lambda);
      }
    }
  }

  /**
   * Test that conductivity increases with pressure at constant temperature (dense gas behavior).
   * This is a basic physical consistency check.
   */
  @Test
  void testConductivityIncreaseWithPressure() {
    String[] methods = {"PFCT", "Chung-dense"};

    for (String method : methods) {
      double prevLambda = 0.0;
      double[] pressures = {1.0, 50.0, 100.0, 200.0};

      for (double p : pressures) {
        SystemInterface system = new SystemSrkEos(300.0, p);
        system.addComponent("methane", 1.0);
        system.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        ops.TPflash();
        system.initProperties();

        switchAndRecalc(system, 0, method);
        double lambda = system.getPhase(0).getPhysicalProperties().getConductivity();

        if (p > 1.0) {
          assertTrue(lambda >= prevLambda * 0.95,
              method + ": conductivity should not decrease with pressure. P=" + p + " lambda="
                  + lambda + " prev=" + prevLambda);
        }
        prevLambda = lambda;
      }
    }
  }

  /**
   * Verify liquid n-heptane conductivity against NIST. NIST reference: n-heptane at 300 K,
   * saturated liquid -> 0.1232 W/(m*K). Tests that the updated database parameters (replacing
   * water-placeholder values) give correct results.
   */
  @Test
  void testLiquidHeptaneAccuracy() {
    SystemInterface system = new SystemSrkEos(300.0, 10.0);
    system.addComponent("n-heptane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double lambda = system.getPhase("oil").getPhysicalProperties().getConductivity();
    assertEquals(0.123, lambda, 0.020,
        "n-Heptane liquid conductivity should be near NIST 0.123 W/(m*K), got " + lambda);
  }

  /**
   * Verify liquid n-octane conductivity against NIST. NIST: n-octane at 300 K -> 0.127 W/(m*K).
   */
  @Test
  void testLiquidOctaneAccuracy() {
    SystemInterface system = new SystemSrkEos(300.0, 10.0);
    system.addComponent("n-octane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double lambda = system.getPhase("oil").getPhysicalProperties().getConductivity();
    assertEquals(0.127, lambda, 0.020,
        "n-Octane liquid conductivity should be near NIST 0.127 W/(m*K), got " + lambda);
  }

  /**
   * Verify liquid n-hexane conductivity against NIST. NIST: n-hexane at 300 K -> 0.119 W/(m*K).
   */
  @Test
  void testLiquidHexaneAccuracy() {
    SystemInterface system = new SystemSrkEos(300.0, 10.0);
    system.addComponent("n-hexane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double lambda = system.getPhase("oil").getPhysicalProperties().getConductivity();
    assertEquals(0.119, lambda, 0.020,
        "n-Hexane liquid conductivity should be near NIST 0.119 W/(m*K), got " + lambda);
  }

  /**
   * Verify Chung-dense gas conductivity for methane against NIST. NIST: methane at 300 K, 1 bar ->
   * 0.0343 W/(m*K).
   */
  @Test
  void testChungDenseMethaneAccuracy() {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    switchAndRecalc(system, "gas", "Chung-dense");
    double lambda = system.getPhase("gas").getPhysicalProperties().getConductivity();
    assertEquals(0.034, lambda, 0.010,
        "Chung-dense methane gas should be near NIST 0.034 W/(m*K), got " + lambda);
  }

  /**
   * Verify liquid water conductivity. NIST: water at 300 K, 1 bar -> 0.610 W/(m*K).
   */
  @Test
  void testLiquidWaterConductivity() {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("water", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Water should be in the aqueous phase
    if (system.hasPhaseType("aqueous")) {
      double lambda = system.getPhase("aqueous").getPhysicalProperties().getConductivity();
      assertEquals(0.61, lambda, 0.05,
          "Water liquid conductivity should be near NIST 0.610 W/(m*K), got " + lambda);
    }
  }
}
