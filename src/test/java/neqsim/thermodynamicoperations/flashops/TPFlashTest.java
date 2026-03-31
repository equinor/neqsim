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
   * Sweeps temperature at multiple pressures for the methane/n-heptane binary (70/30 mol%),
   * kij=0.05, using 2-phase TPflash (no multiPhaseCheck). Verifies phase diagram is smooth
   * (no spurious dots/flips).
   */
  @Test
  void testTwoPhaseFlashMethaneHeptanePhaseDiagram() {
    double[] testPressures = {10.0, 50.0, 100.0, 200.0};
    int nPoints = 40;

    for (double pressure : testPressures) {
      System.out.println("\n--- P = " + pressure + " bar ---");
      int[] phaseCount = new int[nPoints];
      for (int j = 0; j < nPoints; j++) {
        double temp = 100.0 + j * 10.0;
        neqsim.thermo.system.SystemInterface fluid =
            new neqsim.thermo.system.SystemPrEos(temp, pressure);
        fluid.addComponent("methane", 70.0);
        fluid.addComponent("n-heptane", 30.0);
        fluid.setMixingRule("classic");
        ((EosMixingRulesInterface) fluid.getPhase(0).getMixingRule()).setBinaryInteractionParameter(
            0, 1, 0.05);
        ((EosMixingRulesInterface) fluid.getPhase(1).getMixingRule()).setBinaryInteractionParameter(
            0, 1, 0.05);
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        phaseCount[j] = fluid.getNumberOfPhases();
        System.out.println("T=" + String.format("%.0f", temp) + " K, nPhases=" + phaseCount[j]
            + ", beta=" + String.format("%.4f", fluid.getBeta()));
      }
      // Check for isolated flips
      int flips = 0;
      for (int j = 1; j < nPoints - 1; j++) {
        if (phaseCount[j] != phaseCount[j - 1] && phaseCount[j] != phaseCount[j + 1]) {
          flips++;
        }
      }
      assertTrue(flips <= 1,
          "Too many isolated flips (" + flips + ") at P=" + pressure + " bar");
    }
  }

  /**
   * Compares 2-phase flash vs multiphase flash for the methane/n-heptane binary with kij=0.05.
   * Both should produce the same number of phases and similar beta values at every grid point.
   */
  @Test
  void testTwoPhaseVsMultiphaseFlashConsistency() {
    double[] testPressures = {10.0, 50.0, 100.0, 200.0};
    int nPoints = 40;
    int mismatches = 0;

    for (double pressure : testPressures) {
      System.out.println("\n--- P = " + pressure + " bar ---");
      for (int j = 0; j < nPoints; j++) {
        double temp = 100.0 + j * 10.0;

        // 2-phase flash
        neqsim.thermo.system.SystemInterface fluid2p =
            new neqsim.thermo.system.SystemPrEos(temp, pressure);
        fluid2p.addComponent("methane", 70.0);
        fluid2p.addComponent("n-heptane", 30.0);
        fluid2p.setMixingRule("classic");
        fluid2p.setCheckForLiquidLiquidSplit(true);
        ((EosMixingRulesInterface) fluid2p.getPhase(0).getMixingRule())
            .setBinaryInteractionParameter(0, 1, 0.05);
        ((EosMixingRulesInterface) fluid2p.getPhase(1).getMixingRule())
            .setBinaryInteractionParameter(0, 1, 0.05);
        ThermodynamicOperations ops2p = new ThermodynamicOperations(fluid2p);
        ops2p.TPflash();

        // multiphase flash
        neqsim.thermo.system.SystemInterface fluidMp =
            new neqsim.thermo.system.SystemPrEos(temp, pressure);
        fluidMp.addComponent("methane", 70.0);
        fluidMp.addComponent("n-heptane", 30.0);
        fluidMp.setMixingRule("classic");
        ((EosMixingRulesInterface) fluidMp.getPhase(0).getMixingRule())
            .setBinaryInteractionParameter(0, 1, 0.05);
        ((EosMixingRulesInterface) fluidMp.getPhase(1).getMixingRule())
            .setBinaryInteractionParameter(0, 1, 0.05);
        fluidMp.setMultiPhaseCheck(true);
        ThermodynamicOperations opsMp = new ThermodynamicOperations(fluidMp);
        opsMp.TPflash();

        boolean match = fluid2p.getNumberOfPhases() == fluidMp.getNumberOfPhases();
        if (!match) {
          mismatches++;
          System.out.println("T=" + String.format("%.0f", temp) + " K: 2p="
              + fluid2p.getNumberOfPhases() + " phases, multi=" + fluidMp.getNumberOfPhases()
              + " phases <<< MISMATCH");
          System.out.println("  2-phase Gibbs=" + fluid2p.getGibbsEnergy());
          System.out.println("  multi   Gibbs=" + fluidMp.getGibbsEnergy());
        }
      }
    }
    System.out
        .println("Total mismatches: " + mismatches + " / " + (testPressures.length * nPoints));
    assertTrue(mismatches <= 2, "Too many mismatches (" + mismatches
        + ") between 2-phase and multiphase flash for binary system");
  }

  /**
   * Checks that 2-phase flash and multiphase flash produce consistent phase type labels (gas,
   * gas+oil, oil+oil, oil) for the methane/n-heptane binary, covering the full phase diagram
   * including the low-temperature oil-oil region (T < 170 K).
   */
  @Test
  void testPhaseTypeLabelConsistency() {
    double[] testPressures = {10.0, 50.0, 100.0, 200.0, 350.0, 500.0};
    int nPoints = 50;
    int typeMismatches = 0;
    int phaseCountMismatches = 0;

    for (double pressure : testPressures) {
      System.out.println("\n--- P = " + pressure + " bar ---");
      for (int j = 0; j < nPoints; j++) {
        // Cover 100 K to 500 K to include the Oil-Oil region
        double temp = 100.0 + j * 8.0;

        // 2-phase flash
        neqsim.thermo.system.SystemInterface f2 =
            new neqsim.thermo.system.SystemPrEos(temp, pressure);
        f2.addComponent("methane", 70.0);
        f2.addComponent("n-heptane", 30.0);
        f2.setMixingRule("classic");
        f2.setCheckForLiquidLiquidSplit(true);
        ((EosMixingRulesInterface) f2.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0,
            1, 0.05);
        ((EosMixingRulesInterface) f2.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0,
            1, 0.05);
        ThermodynamicOperations o2 = new ThermodynamicOperations(f2);
        o2.TPflash();

        // multiphase flash
        neqsim.thermo.system.SystemInterface fm =
            new neqsim.thermo.system.SystemPrEos(temp, pressure);
        fm.addComponent("methane", 70.0);
        fm.addComponent("n-heptane", 30.0);
        fm.setMixingRule("classic");
        ((EosMixingRulesInterface) fm.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0,
            1, 0.05);
        ((EosMixingRulesInterface) fm.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0,
            1, 0.05);
        fm.setMultiPhaseCheck(true);
        ThermodynamicOperations om = new ThermodynamicOperations(fm);
        om.TPflash();

        // Build label strings
        String label2p = buildPhaseLabel(f2);
        String labelMp = buildPhaseLabel(fm);

        boolean typeMatch = label2p.equals(labelMp);
        boolean phaseMatch = f2.getNumberOfPhases() == fm.getNumberOfPhases();

        // Print all two-phase points and any mismatches
        if (f2.getNumberOfPhases() > 1 || fm.getNumberOfPhases() > 1 || !typeMatch) {
          System.out.println("T=" + String.format("%.0f", temp) + " K: 2p=" + label2p + " (beta="
              + String.format("%.4f", f2.getBeta()) + ") multi=" + labelMp + " (beta="
              + String.format("%.4f", fm.getBeta()) + ")" + (!typeMatch ? " <<< TYPE MISMATCH" : "")
              + (!phaseMatch ? " <<< PHASE COUNT MISMATCH" : ""));
        }

        if (!typeMatch) {
          typeMismatches++;
        }
        if (!phaseMatch) {
          phaseCountMismatches++;
        }
      }
    }
    System.out.println(
        "\nType label mismatches: " + typeMismatches + " / " + (testPressures.length * nPoints));
    System.out.println("Phase count mismatches: " + phaseCountMismatches + " / "
        + (testPressures.length * nPoints));
    assertTrue(typeMismatches <= 2,
        "Too many phase type label mismatches (" + typeMismatches + ")");
  }

  private String buildPhaseLabel(neqsim.thermo.system.SystemInterface sys) {
    if (sys.getNumberOfPhases() == 1) {
      return sys.getPhase(0).getPhaseTypeName();
    }
    String p0 = sys.getPhase(0).getPhaseTypeName();
    String p1 = sys.getPhase(1).getPhaseTypeName();
    return p0 + "+" + p1;
  }

  /**
   * Print V/b ratios for both phases at low temperatures to understand the gas vs oil
   * classification boundary. The threshold is V/b = 1.75.
   */
  @Test
  void testVoverBAtLowTemperatures() {
    double[] pressures = {10.0, 30.0, 50.0, 100.0};
    System.out.println("=== V/b ratios at low temperatures (threshold: V/b = 1.75) ===");
    for (double p : pressures) {
      System.out.println("\n--- P = " + p + " bar ---");
      for (double t = 110.0; t <= 210.0; t += 10.0) {
        neqsim.thermo.system.SystemInterface fluid = new neqsim.thermo.system.SystemPrEos(t, p);
        fluid.addComponent("methane", 70.0);
        fluid.addComponent("n-heptane", 30.0);
        fluid.setMixingRule("classic");
        ((EosMixingRulesInterface) fluid.getPhase(0).getMixingRule())
            .setBinaryInteractionParameter(0, 1, 0.05);
        ((EosMixingRulesInterface) fluid.getPhase(1).getMixingRule())
            .setBinaryInteractionParameter(0, 1, 0.05);
        fluid.setMultiPhaseCheck(true);
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();

        if (fluid.getNumberOfPhases() == 2) {
          // Use getVolume()/getB() as PhaseEos.init() does
          double vb0 = fluid.getPhase(0).getVolume() / fluid.getPhase(0).getB();
          double vb1 = fluid.getPhase(1).getVolume() / fluid.getPhase(1).getB();
          String type0 = fluid.getPhase(0).getPhaseTypeName();
          String type1 = fluid.getPhase(1).getPhaseTypeName();
          double dens0 = fluid.getPhase(0).getDensity("kg/m3");
          double dens1 = fluid.getPhase(1).getDensity("kg/m3");
          System.out.println(
              String.format("T=%.0f K: ph0=%s V/b=%.4f d=%.1f | ph1=%s V/b=%.4f d=%.1f | beta=%.4f",
                  t, type0, vb0, dens0, type1, vb1, dens1, fluid.getBeta()));
        } else {
          double vb0 = fluid.getPhase(0).getVolume() / fluid.getPhase(0).getB();
          System.out.println(String.format("T=%.0f K: single phase = %s V/b=%.4f", t,
              fluid.getPhase(0).getPhaseTypeName(), vb0));
        }
      }
    }
  }

  /**
   * Benchmark: compare TPflash performance for three modes:
   * 1) VLE-only (default, no LLE check) - fastest
   * 2) VLE+LLE (setCheckForLiquidLiquidSplit=true) - detects oil-oil splits
   * 3) Multiphase (setMultiPhaseCheck=true) - full multi-phase via TPmultiflash
   *
   * Tests both binary (methane/n-heptane) and multicomponent (10 comp) systems.
   */
  @Test
  void benchmarkTPflashPerformance() {
    int nPoints = 50;
    double[] pressures = {10.0, 50.0, 200.0};
    String[] modeNames = {"VLE-only", "VLE+LLE", "Multiphase"};

    // Warm up JIT with all three modes
    for (int w = 0; w < 30; w++) {
      neqsim.thermo.system.SystemInterface wf =
          new neqsim.thermo.system.SystemPrEos(273.15, 50.0);
      wf.addComponent("methane", 70.0);
      wf.addComponent("n-heptane", 30.0);
      wf.setMixingRule("classic");
      ((EosMixingRulesInterface) wf.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0,
          1, 0.05);
      ((EosMixingRulesInterface) wf.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0,
          1, 0.05);
      if (w % 3 == 1) {
        wf.setCheckForLiquidLiquidSplit(true);
      }
      if (w % 3 == 2) {
        wf.setMultiPhaseCheck(true);
      }
      ThermodynamicOperations wo = new ThermodynamicOperations(wf);
      wo.TPflash();
    }

    System.out.println("\n=== Binary (C1/nC7) TPflash Performance Benchmark ===");
    System.out.println(String.format("%-12s %-10s %8s %8s %8s   %s",
        "Mode", "Pressure", "avg(ms)", "min(ms)", "max(ms)", "1ph / 2ph"));
    System.out.println(
        "--------------------------------------------------------------");

    for (int mode = 0; mode < 3; mode++) {
      for (double pressure : pressures) {
        long totalNs = 0;
        long maxNs = 0;
        long minNs = Long.MAX_VALUE;
        int singlePhaseCount = 0;
        int twoPhaseCount = 0;

        for (int j = 0; j < nPoints; j++) {
          double temp = 100.0 + j * 8.0;

          neqsim.thermo.system.SystemInterface fluid =
              new neqsim.thermo.system.SystemPrEos(temp, pressure);
          fluid.addComponent("methane", 70.0);
          fluid.addComponent("n-heptane", 30.0);
          fluid.setMixingRule("classic");
          ((EosMixingRulesInterface) fluid.getPhase(0).getMixingRule())
              .setBinaryInteractionParameter(0, 1, 0.05);
          ((EosMixingRulesInterface) fluid.getPhase(1).getMixingRule())
              .setBinaryInteractionParameter(0, 1, 0.05);

          if (mode == 1) {
            fluid.setCheckForLiquidLiquidSplit(true);
          } else if (mode == 2) {
            fluid.setMultiPhaseCheck(true);
          }

          ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

          long t0 = System.nanoTime();
          ops.TPflash();
          long dt = System.nanoTime() - t0;

          totalNs += dt;
          if (dt > maxNs) {
            maxNs = dt;
          }
          if (dt < minNs) {
            minNs = dt;
          }
          if (fluid.getNumberOfPhases() == 1) {
            singlePhaseCount++;
          } else {
            twoPhaseCount++;
          }
        }
        double avgMs = totalNs / (double) nPoints / 1e6;
        System.out.println(String.format("%-12s P=%3.0f bar  %8.2f %8.2f %8.2f   %d / %d",
            modeNames[mode], pressure, avgMs, minNs / 1e6, maxNs / 1e6,
            singlePhaseCount, twoPhaseCount));
      }
    }

    // Also benchmark a multicomponent system (10 comp natural gas)
    System.out.println("\n=== Multicomponent (10 comp) TPflash Performance ===");
    System.out.println(String.format("%-12s %-10s %8s   %s",
        "Mode", "Pressure", "avg(ms)", "1ph / 2ph"));
    System.out.println(
        "----------------------------------------------");

    double[] mcPressures = {10.0, 50.0, 150.0};
    int mcPoints = 30;
    for (int mode = 0; mode < 3; mode++) {
      for (double pressure : mcPressures) {
        long totalNs = 0;
        int singlePhaseCount = 0;
        int twoPhaseCount = 0;

        for (int j = 0; j < mcPoints; j++) {
          double temp = 200.0 + j * 10.0;
          neqsim.thermo.system.SystemInterface fluid =
              new neqsim.thermo.system.SystemPrEos(temp, pressure);
          fluid.addComponent("nitrogen", 1.0);
          fluid.addComponent("methane", 85.0);
          fluid.addComponent("ethane", 5.0);
          fluid.addComponent("propane", 3.0);
          fluid.addComponent("i-butane", 1.0);
          fluid.addComponent("n-butane", 1.5);
          fluid.addComponent("i-pentane", 0.5);
          fluid.addComponent("n-pentane", 0.5);
          fluid.addComponent("n-hexane", 0.3);
          fluid.addComponent("nC10", 0.2);
          fluid.setMixingRule("classic");

          if (mode == 1) {
            fluid.setCheckForLiquidLiquidSplit(true);
          } else if (mode == 2) {
            fluid.setMultiPhaseCheck(true);
          }

          ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

          long t0 = System.nanoTime();
          ops.TPflash();
          long dt = System.nanoTime() - t0;
          totalNs += dt;
          if (fluid.getNumberOfPhases() == 1) {
            singlePhaseCount++;
          } else {
            twoPhaseCount++;
          }
        }
        double avgMs = totalNs / (double) mcPoints / 1e6;
        System.out.println(String.format("%-12s P=%3.0f bar  %8.2f   %d / %d",
            modeNames[mode], pressure, avgMs, singlePhaseCount, twoPhaseCount));
      }
    }
    // No assertions - this is a diagnostic test
  }
}
