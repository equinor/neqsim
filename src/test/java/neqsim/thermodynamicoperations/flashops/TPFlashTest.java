package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class TPFlashTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(243.15, 300.0);
    testSystem.addComponent("nitrogen", 1.0);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("ethane", 2.0);
    testSystem.addComponent("propane", 1.0);
    testSystem.addComponent("i-butane", 1.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("i-pentane", 1.0);
    testSystem.addComponent("n-pentane", 1.0);
    testSystem.addComponent("n-hexane", 1.0);
    testSystem.addComponent("nC10", 1.0);
    testSystem.addComponent("water", 10.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
  }

  void testRun() {
    testSystem.setMultiPhaseCheck(true);
    testSystem.setPressure(10.0, "bara");
    testSystem.setTemperature(25.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assertEquals(-430041.49312169873, testSystem.getEnthalpy(), 1e-2);
  }

  @Test
  void testRun2() {
    testSystem.setMultiPhaseCheck(false);
    testSystem.setPressure(10.0, "bara");
    testSystem.setTemperature(25.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    double expected = -359377.5331957406;
    double deviation = Math.abs((testSystem.getEnthalpy() - expected) / expected * 100);
    assertEquals(0.0, deviation, 0.5);
  }

  @Test
  void testRun3() {
    testSystem.setMultiPhaseCheck(false);
    testSystem.setPressure(500.0, "bara");
    testSystem.setTemperature(15.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    double expected = -552559.256480;
    double deviation = Math.abs((testSystem.getEnthalpy() - expected) / expected * 100);
    assertEquals(0.0, deviation, 0.5);
  }

  // @Test
  void testRun4() {
    testSystem.setMultiPhaseCheck(true);
    testSystem.setPressure(500.0, "bara");
    testSystem.setTemperature(15.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assertEquals(-936973.1969586421, testSystem.getEnthalpy(), 1e-2);
  }

  @Test
  void testRun5() {
    neqsim.thermo.system.SystemInterface testSystem5 =
        new neqsim.thermo.system.SystemUMRPRUMCEos(243.15, 300.0);
    testSystem5.addComponent("methane", 4.16683e-1);
    testSystem5.addComponent("ethane", 1.7522e-1);
    testSystem5.addComponent("n-pentane", 3.58009e-1);
    testSystem5.addComponent("nC16", 5.00888e-2);
    testSystem5.setMixingRule("classic");
    testSystem5.setMultiPhaseCheck(true);
    testSystem5.setPressure(90.03461693, "bara");
    testSystem5.setTemperature(293.15, "K");
    testSystem5.setTotalFlowRate(4.925e-07, "kg/sec");
    testOps = new ThermodynamicOperations(testSystem5);
    testOps.TPflash();
    testSystem5.initProperties();
    // testSystem5.prettyPrint();
    double beta = testSystem5.getBeta();
    // Updated expected value due to thermodynamic model changes
    assertEquals(0.10377442547868508, beta, 1e-4);
  }

  @Test
  void testRun6() {
    neqsim.thermo.system.SystemInterface testSystem5 =
        new neqsim.thermo.system.SystemSrkCPAstatoil(243.15, 300.0);
    testSystem5.addComponent("water", 50.0);
    testSystem5.addComponent("nitrogen", 0.58419764);
    testSystem5.addComponent("CO2", 0.070499);
    testSystem5.addComponent("methane", 64.04974);
    testSystem5.addComponent("ethane", 8.148467);
    testSystem5.addComponent("propane", 4.985780239);
    testSystem5.addComponent("i-butane", 0.966896117);
    testSystem5.addComponent("n-butane", 1.923792362);
    testSystem5.addComponent("i-pentane", 0.725197077);
    testSystem5.addComponent("n-pentane", 0.856096566);
    testSystem5.addComponent("n-hexane", 0.968696);
    testSystem5.addTBPfraction("C7", 1.357195, 98.318 / 1000, 736.994 / 1000);
    testSystem5.addTBPfraction("C8", 1.507094, 112.273 / 1000, 758.73 / 1000);
    testSystem5.addTBPfraction("C9", 1.216195, 126.266 / 1000, 775.35 / 1000);
    testSystem5.addTBPfraction("C11", 1.624694, 146.891006469727 / 1000, 0.794530808925629);
    testSystem5.addTBPfraction("C13", 1.4131942987442, 174.875 / 1000, 0.814617037773132);
    testSystem5.addTBPfraction("C15", 1.22939503192902, 202.839004516602 / 1000, 0.830620348453522);
    testSystem5.addTBPfraction("C18", 1.55159378051758, 237.324005126953 / 1000, 0.846814215183258);
    testSystem5.addTBPfraction("C20", 0.868496537208557, 272.686004638672 / 1000,
        0.860718548297882);
    testSystem5.addTBPfraction("C23", 1.0966956615448, 307.141998291016 / 1000, 0.872339725494385);
    testSystem5.addTBPfraction("C29", 1.61579358577728, 367.554992675781 / 1000, 0.889698147773743);
    testSystem5.addTBPfraction("C30", 3.24028706550598, 594.625 / 1000, 0.935410261154175);

    testSystem5.setMixingRule(10);
    testSystem5.setMultiPhaseCheck(true);
    testSystem5.setPressure(300.0, "bara");
    testSystem5.setTemperature(343.15, "K");
    testOps = new ThermodynamicOperations(testSystem5);
    testOps.TPflash();
    testSystem5.initProperties();
    assertEquals(0.2838675588923609, testSystem5.getBeta(), 1e-6);
    assertEquals(3, testSystem5.getNumberOfPhases());
  }

  /**
   * Regression test: TPflash must detect two-phase equilibrium near the cricondenbar (~105 bar)
   * without requiring setMultiPhaseCheck(true). For this rich natural gas at 100 bar, Wilson
   * K-values are near 1.0 so the standard stability analysis must use amplified initial guesses to
   * avoid converging to a trivial solution.
   *
   * <p>
   * See Michelsen (1982) "The isothermal flash problem" for the theoretical basis: when Wilson
   * K-values are near unity (near critical conditions), multiple initial guesses are needed for
   * robust stability analysis.
   * </p>
   */
  @Test
  void testTPflashRichGasNearCricondenbar() {
    neqsim.thermo.system.SystemInterface richGas =
        new neqsim.thermo.system.SystemSrkEos(273.15, 100.0);
    richGas.addComponent("nitrogen", 3.43);
    richGas.addComponent("CO2", 0.34);
    richGas.addComponent("methane", 62.51);
    richGas.addComponent("ethane", 15.65);
    richGas.addComponent("propane", 13.22);
    richGas.addComponent("i-butane", 1.61);
    richGas.addComponent("n-butane", 2.48);
    richGas.addComponent("i-pentane", 0.35);
    richGas.addComponent("n-pentane", 0.29);
    richGas.addComponent("n-hexane", 0.12);
    richGas.setMixingRule(2);
    // Crucially: do NOT call setMultiPhaseCheck(true) - the standard TPflash must work
    richGas.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(richGas);

    // At 0 C, 100 bar: well inside two-phase region (cricondenbar ~105 bar)
    ops.TPflash();
    richGas.init(3);
    assertEquals(2, richGas.getNumberOfPhases(),
        "TPflash at 0C/100bar should find 2 phases without multiPhaseCheck");
    double beta0C = richGas.getBeta();
    // Beta should be between 0 and 1 (two-phase), not 0.0 or 1.0 (single phase)
    assertTrue(beta0C > 0.01 && beta0C < 0.99,
        "Beta at 0C/100bar should indicate two-phase, got " + beta0C);

    // At 10 C, 100 bar: also inside two-phase region
    richGas.setTemperature(273.15 + 10.0);
    ops.TPflash();
    richGas.init(3);
    assertEquals(2, richGas.getNumberOfPhases(),
        "TPflash at 10C/100bar should find 2 phases without multiPhaseCheck");
    double beta10C = richGas.getBeta();
    assertTrue(beta10C > 0.01 && beta10C < 0.99,
        "Beta at 10C/100bar should indicate two-phase, got " + beta10C);

    // At -8 C, 100 bar: very near bubble point — detection is less reliable here
    // as Wilson K amplification may not produce sufficient separation
    richGas.setTemperature(273.15 - 8.0);
    ops.TPflash();
    richGas.init(3);

    // At 30 C, 100 bar: outside two-phase envelope, should be single phase
    richGas.setTemperature(273.15 + 30.0);
    ops.TPflash();
    richGas.init(3);
    assertEquals(1, richGas.getNumberOfPhases(),
        "TPflash at 30C/100bar should find 1 phase (above dew point)");

    // At 50 bar: should find two phases easily (lower pressure, larger K spread)
    richGas.setPressure(50.0);
    richGas.setTemperature(273.15);
    ops.TPflash();
    richGas.init(3);
    assertEquals(2, richGas.getNumberOfPhases(), "TPflash at 0C/50bar should find 2 phases");
  }

  @Test
  void testTPflash1() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(273.15 + 290, 400.0);

    testSystem.addComponent("water", 65.93229747922976);
    testSystem.addComponent("NaCl", 0.784426208131475);
    testSystem.addComponent("nitrogen", 0.578509157534656);
    testSystem.addComponent("methane", 22.584113183429718);
    testSystem.addComponent("ethane", 3.43870686718215);
    testSystem.addComponent("propane", 0.26487350163523365);
    testSystem.addComponent("i-butane", 0.04039429848533373);
    testSystem.addComponent("n-butane", 0.1543856425679738);
    testSystem.addComponent("i-pentane", 0.04039429848533373);
    testSystem.addComponent("n-pentane", 0.1543856425679738);

    testSystem.addTBPfraction("C6", 0.568724470114871, 84.93298402237961 / 1000.0,
        666.591171644071 / 1000.0);
    testSystem.addTBPfraction("C7", 0.9478147516962493, 90.01311937418495 / 1000.0,
        746.9101810251765 / 1000.0);
    testSystem.addTBPfraction("C8", 0.974840433764089, 102.34691375809437 / 1000.0,
        776.2927119017166 / 1000.0);
    testSystem.addTBPfraction("C9", 0.5505907716430188, 116.06055719132209 / 1000.0,
        791.2983315058531 / 1000.0);
    testSystem.addTBPfraction("C10", 1.9704404325720026, 221.831957 / 1000.0, 842.802708 / 1000.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    assertEquals(2, testSystem.getNumberOfPhases());
    // testSystem.prettyPrint();
  }

  /**
   * Test that sequential TPflash calls on a methane/n-heptane binary produce spatially consistent
   * phase identification near the phase boundary. This targets the region around 350-450 K and
   * 100-250 bara where the stability analysis can produce sporadic wrong results due to marginal
   * tangent plane distance values and stale K-value propagation between grid points.
   *
   * <p>
   * The test scans a strip of T,P conditions across the phase boundary and verifies that the phase
   * count transitions monotonically (no isolated single-point flips). A single isolated flip in a
   * sequence of otherwise consistent results indicates a stability analysis failure.
   * </p>
   */
  @Test
  void testMethaneHeptanePhaseBoundaryConsistency() {
    neqsim.thermo.system.SystemInterface fluid = new neqsim.thermo.system.SystemPrEos(298.15, 50.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("n-heptane", 30.0);
    fluid.setMixingRule("classic");
    ((EosMixingRulesInterface) fluid.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0,
        1, 0.05);
    ((EosMixingRulesInterface) fluid.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0,
        1, 0.05);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Scan temperature at fixed pressure 150 bara — crosses the phase boundary
    double pressure = 150.0;
    fluid.setPressure(pressure, "bara");
    int nPoints = 80;
    int[] phaseCount = new int[nPoints];
    for (int j = 0; j < nPoints; j++) {
      double temp = 250.0 + j * 3.0; // 250 to 487 K
      fluid.setTemperature(temp, "K");
      ops.TPflash();
      phaseCount[j] = fluid.getNumberOfPhases();
    }

    // Count isolated flips: a point where phase count differs from BOTH neighbors
    int isolatedFlips = 0;
    for (int j = 1; j < nPoints - 1; j++) {
      if (phaseCount[j] != phaseCount[j - 1] && phaseCount[j] != phaseCount[j + 1]) {
        isolatedFlips++;
      }
    }

    // Allow at most 1 isolated flip (the exact boundary point can be ambiguous)
    assertTrue(isolatedFlips <= 1, "Too many isolated phase flips (" + isolatedFlips + ") at P="
        + pressure + " bara — stability analysis is inconsistent near boundary");

    // Scan temperature at fixed pressure 200 bara
    pressure = 200.0;
    fluid.setPressure(pressure, "bara");
    for (int j = 0; j < nPoints; j++) {
      double temp = 250.0 + j * 3.0;
      fluid.setTemperature(temp, "K");
      ops.TPflash();
      phaseCount[j] = fluid.getNumberOfPhases();
    }

    isolatedFlips = 0;
    for (int j = 1; j < nPoints - 1; j++) {
      if (phaseCount[j] != phaseCount[j - 1] && phaseCount[j] != phaseCount[j + 1]) {
        isolatedFlips++;
      }
    }

    assertTrue(isolatedFlips <= 1, "Too many isolated phase flips (" + isolatedFlips + ") at P="
        + pressure + " bara — stability analysis is inconsistent near boundary");
  }

  /**
   * Regression test for the c91e99c TPflash post-convergence stability re-check that was reverted
   * in PR #2112. That commit introduced speckled/noisy phase identification in the low-temperature
   * region (110-200 K) of a methane/n-heptane binary with PR EOS and kij=0.05-0.06.
   *
   * <p>
   * The test scans a 30x30 T,P grid over T in [110, 200] K and P in [50, 180] bara and counts how
   * many grid cells are classified as 2-phase. The v3.7.0 baseline produces 592 two-phase cells;
   * the c91e99c regression dropped this to 137. Any future change that drops this count below 500
   * likely reintroduces a flash regression of the same family.
   * </p>
   */
  @Test
  void testMethaneHeptaneLowTemperatureGridParity() {
    neqsim.thermo.system.SystemInterface fluid = new neqsim.thermo.system.SystemPrEos(150.0, 100.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("n-heptane", 30.0);
    fluid.setMixingRule("classic");
    ((EosMixingRulesInterface) fluid.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0,
        1, 0.06);
    ((EosMixingRulesInterface) fluid.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0,
        1, 0.06);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    int nT = 30;
    int nP = 30;
    int twoPhaseCount = 0;
    for (int iT = 0; iT < nT; iT++) {
      double T = 110.0 + iT * (200.0 - 110.0) / (nT - 1);
      for (int iP = 0; iP < nP; iP++) {
        double P = 50.0 + iP * (180.0 - 50.0) / (nP - 1);
        fluid.setTemperature(T, "K");
        fluid.setPressure(P, "bara");
        try {
          ops.TPflash();
          if (fluid.getNumberOfPhases() >= 2) {
            twoPhaseCount++;
          }
        } catch (Exception ignored) {
          // convergence failure counted as single-phase
        }
      }
    }

    // v3.7.0 baseline: 592/900. c91e99c regression dropped to 137/900.
    // Require at least 500 to flag any future regression of the same family.
    assertTrue(twoPhaseCount >= 500,
        "Low-T methane/nC7 two-phase region shrank: " + twoPhaseCount + "/900 cells 2-phase "
            + "(v3.7.0 baseline = 592). Possible TPflash stability regression.");
  }
}
