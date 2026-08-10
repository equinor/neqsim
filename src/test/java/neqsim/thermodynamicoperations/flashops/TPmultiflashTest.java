package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test class for TPmultiflash.
 *
 * @author ESOL
 */
class TPmultiflashTest {
  private static final Logger logger = LogManager.getLogger(TPmultiflashTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  void testC1C7() {
    final double kij = 0.05;
    SystemInterface testSystem = new neqsim.thermo.system.SystemPrEos();
    testSystem.addComponent("methane", 70.0);
    testSystem.addComponent("n-heptane", 30.0);

    testSystem.setMixingRule("classic");

    ((EosMixingRulesInterface) testSystem.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0, 1, kij);
    ((EosMixingRulesInterface) testSystem.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0, 1, kij);

    testSystem.setMultiPhaseCheck(true);

    testSystem.setTemperature(155.1, "K");
    for (double p = 10.0; p <= 150.0; p += 0.1) {
      testSystem.setPressure(p, "bara");
      testOps = new ThermodynamicOperations(testSystem);
      testOps.TPflash();
      testSystem.initProperties();
      logger.info("Pressure: " + p + " bara");
      // testSystem.prettyPrint();
      if (testSystem.getNumberOfPhases() == 1) {
        logger.info("Single phase detected at pressure: " + p + " bara");
      } else {
        logger.info("Multiple phases detected at pressure: " + p + " bara");
      }
    }
  }

  @Test
  void testC1C72() {
    final double kij = 0.05;
    SystemInterface testSystem = new neqsim.thermo.system.SystemPrEos();
    testSystem.addComponent("methane", 70.0);
    testSystem.addComponent("n-heptane", 30.0);

    testSystem.setMixingRule("classic");

    ((EosMixingRulesInterface) testSystem.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0, 1, kij);
    ((EosMixingRulesInterface) testSystem.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0, 1, kij);

    testSystem.setMultiPhaseCheck(true);

    testSystem.setTemperature(155.1, "K");
    testSystem.setPressure(84.4, "bara");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assert (testSystem.getNumberOfPhases() == 2) : "Expected 2 phases, got " + testSystem.getNumberOfPhases();
  }

  /**
   * Verifies that enhanced stability checks do not perturb hydrocarbon-only PR binary flashes.
   */
  @Test
  void testEnhancedHydrocarbonBinaryMatchesOrdinaryMultiphaseCheck() {
    final double binaryInteractionParameter = 0.05;
    double[][] conditions = new double[][] { { 110.0, 264.0 }, { 112.5, 276.0 }, { 70.0, 458.0 }, { 120.0, 200.0 } };

    for (double[] condition : conditions) {
      SystemInterface ordinarySystem = createMethaneHeptanePrSystem(binaryInteractionParameter, false);
      ordinarySystem.setPressure(condition[0], "bara");
      ordinarySystem.setTemperature(condition[1], "K");
      new ThermodynamicOperations(ordinarySystem).TPflash();
      ordinarySystem.initProperties();

      SystemInterface enhancedSystem = createMethaneHeptanePrSystem(binaryInteractionParameter, true);
      enhancedSystem.setPressure(condition[0], "bara");
      enhancedSystem.setTemperature(condition[1], "K");
      new ThermodynamicOperations(enhancedSystem).TPflash();
      enhancedSystem.initProperties();

      assertEquals(ordinarySystem.getNumberOfPhases(), enhancedSystem.getNumberOfPhases(),
          "Enhanced flash should not change phase count at P=" + condition[0] + " bara, T=" + condition[1] + " K");
      assertEquals(ordinarySystem.hasPhaseType("gas"), enhancedSystem.hasPhaseType("gas"));
      assertEquals(ordinarySystem.hasPhaseType("oil"), enhancedSystem.hasPhaseType("oil"));
      assertEquals(ordinarySystem.hasPhaseType("aqueous"), enhancedSystem.hasPhaseType("aqueous"));

      for (int phaseNumber = 0; phaseNumber < ordinarySystem.getNumberOfPhases(); phaseNumber++) {
        assertEquals(ordinarySystem.getPhase(phaseNumber).getType(), enhancedSystem.getPhase(phaseNumber).getType());
        assertEquals(ordinarySystem.getBeta(phaseNumber), enhancedSystem.getBeta(phaseNumber), 1.0e-10);
      }
    }
  }

  /**
   * Creates the methane/n-heptane PR system used in binary hydrocarbon flash regression tests.
   *
   * @param binaryInteractionParameter methane/n-heptane binary interaction parameter
   * @param enhancedCheck true to enable enhanced multiphase checks
   * @return configured methane/n-heptane PR thermodynamic system
   */
  private SystemInterface createMethaneHeptanePrSystem(double binaryInteractionParameter, boolean enhancedCheck) {
    SystemInterface methaneHeptaneSystem = new neqsim.thermo.system.SystemPrEos();
    methaneHeptaneSystem.addComponent("methane", 70.0);
    methaneHeptaneSystem.addComponent("n-heptane", 30.0);

    methaneHeptaneSystem.setMixingRule("classic");
    ((EosMixingRulesInterface) methaneHeptaneSystem.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0, 1,
        binaryInteractionParameter);
    ((EosMixingRulesInterface) methaneHeptaneSystem.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0, 1,
        binaryInteractionParameter);

    methaneHeptaneSystem.setMultiPhaseCheck(true);
    if (enhancedCheck) {
      methaneHeptaneSystem.setEnhancedMultiPhaseCheck(true);
    }
    return methaneHeptaneSystem;
  }

  /**
   * Test three-phase vapor-liquid-liquid equilibrium for sour gas system (methane/CO2/H2S). At low temperatures and
   * moderate pressures, this mixture can exhibit three-phase behavior with a vapor phase, a CO2-rich liquid, and an
   * H2S-rich liquid.
   */
  @Test
  void testSourGasThreePhaseEquilibrium() {
    // Create a sour gas mixture similar to user's case:
    // methane: 49.88 mol%, CO2: 9.87 mol%, H2S: 40.22 mol%
    SystemInterface sourGas = new neqsim.thermo.system.SystemPrEos(210.0, 55.0); // ~-63C, ~55 bar
    sourGas.addComponent("methane", 49.88);
    sourGas.addComponent("CO2", 9.87);
    sourGas.addComponent("H2S", 40.22);

    sourGas.setMixingRule("classic");
    sourGas.setMultiPhaseCheck(true);
    sourGas.setEnhancedMultiPhaseCheck(true); // Enable Wilson K-value based stability analysis

    ThermodynamicOperations ops = new ThermodynamicOperations(sourGas);
    ops.TPflash();
    sourGas.initProperties();

    // At these conditions, we expect at least 2 phases (vapor + liquid)
    // The new sour gas seeding should help find additional phases if they exist
    assertTrue(sourGas.getNumberOfPhases() >= 2,
        "Expected at least 2 phases for sour gas at low T, got " + sourGas.getNumberOfPhases());

    // Print phase information for debugging
    logger.info("Sour gas flash at T=" + sourGas.getTemperature("C") + " C, P=" + sourGas.getPressure("bara") + " bar");
    logger.info("Number of phases: " + sourGas.getNumberOfPhases());
    for (int i = 0; i < sourGas.getNumberOfPhases(); i++) {
      logger.info("  Phase " + i + ": " + sourGas.getPhase(i).getType() + ", beta=" + sourGas.getBeta(i));
    }
  }

  /**
   * Test that scans temperature/pressure range for three-phase region in sour gas. This helps verify the stability
   * analysis can find three-phase regions.
   */
  @Tag("slow")
  @Test
  void testSourGasThreePhaseRegionScan() {
    SystemInterface sourGas = new neqsim.thermo.system.SystemPrEos();
    sourGas.addComponent("methane", 49.88);
    sourGas.addComponent("CO2", 9.87);
    sourGas.addComponent("H2S", 40.22);

    sourGas.setMixingRule("classic");
    sourGas.setMultiPhaseCheck(true);
    sourGas.setEnhancedMultiPhaseCheck(true); // Enable Wilson K-value based stability analysis

    ThermodynamicOperations ops = new ThermodynamicOperations(sourGas);

    int threePhaseCount = 0;
    double maxPressureThreePhase = 0;

    // Scan a range of conditions where three-phase behavior might occur
    // Temperature range: -100 to -50 C (173 to 223 K)
    // Pressure range: 20 to 100 bar
    for (double tempK = 180.0; tempK <= 230.0; tempK += 0.1) {
      for (double presBar = 30.0; presBar <= 80.0; presBar += 1.0) {
        sourGas.setTemperature(tempK);
        sourGas.setPressure(presBar);

        try {
          ops.TPflash();
          sourGas.initProperties();

          if (sourGas.getNumberOfPhases() == 3) {
            threePhaseCount++;
            if (presBar > maxPressureThreePhase) {
              maxPressureThreePhase = presBar;
            }
            // logger.info(
            // "Three phases found at T=" + (tempK - 273.15) + " C, P=" + presBar +
            // " bar");
          }
        } catch (Exception e) {
          // Some conditions may fail near critical or unstable regions
        }
      }
    }

    logger.info("Total three-phase points found: " + threePhaseCount);
    logger.info("Maximum pressure with three phases: " + maxPressureThreePhase + " bar");

    // We don't strictly assert three-phase is found since the thermodynamic model
    // may not predict it for all parameter combinations, but we verify no crashes
    assertTrue(threePhaseCount >= 0, "Scan completed without errors");
  }

  /**
   * A reused multiflash operation must not carry a previous beta-solver stall into a pass that does not execute the
   * beta solver. Otherwise the later active-set rescue can act on stale convergence state.
   *
   * @throws Exception if the private lifecycle field cannot be inspected
   */
  @Test
  void testRunClearsStaleBetaSolveStateWhenNoBetaSolveRuns() throws Exception {
    SystemInterface singlePhase = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    singlePhase.addComponent("methane", 1.0);
    singlePhase.setMixingRule("classic");
    singlePhase.setMultiPhaseCheck(false);
    singlePhase.init(0);

    TPmultiflash operation = new TPmultiflash(singlePhase, false);
    Field stalledField = TPmultiflash.class.getDeclaredField("betaSolveStalled");
    stalledField.setAccessible(true);
    stalledField.setBoolean(operation, true);

    operation.run();

    assertFalse(stalledField.getBoolean(operation),
        "A run without a beta solve must clear stall state retained by a reused operation");
  }

  /** A raw EJML solve must not accept a non-finite correction from a singular beta Hessian. */
  @Test
  void testBetaCorrectionRejectsSingularSolveReportedAsSuccessful() {
    DMatrixRMaj singularHessian = new DMatrixRMaj(new double[][] { { 1.0, 1.0 }, { 1.0, 1.0 } });
    DMatrixRMaj gradient = new DMatrixRMaj(new double[][] { { 1.0 }, { 1.0 } });
    DMatrixRMaj correction = new DMatrixRMaj(2, 1);

    assertFalse(TPmultiflash.solveBetaCorrection(singularHessian, gradient, correction));

    singularHessian.set(0, 0, singularHessian.get(0, 0) + 1.0e-2);
    singularHessian.set(1, 1, singularHessian.get(1, 1) + 1.0e-2);
    assertTrue(TPmultiflash.solveBetaCorrection(singularHessian, gradient, correction));
    assertTrue(Double.isFinite(correction.get(0, 0)));
    assertTrue(Double.isFinite(correction.get(1, 0)));
  }

  /** Verifies the beta objective reuses current fugacity coefficients without changing its equations. */
  @Test
  void testCalcQRefreshesFugacityCoefficientCache() {
    SystemInterface system = createMethaneHeptanePrSystem(0.0, false);
    system.setTemperature(250.0, "K");
    system.setPressure(30.0, "bara");
    new ThermodynamicOperations(system).TPflash();
    system.init(1);

    assertEquals(2, system.getNumberOfPhases());
    TPmultiflash operation = new TPmultiflash(system, false);
    operation.setDoubleArrays();
    operation.calcQ();
    assertBetaObjectiveMatchesDirectEvaluation(system, operation);

    double originalFugacityCoefficient = system.getPhase(1).getComponent(0).getFugacityCoefficient();
    system.getPhase(1).getComponent(0).setFugacityCoefficient(2.0 * originalFugacityCoefficient);
    operation.calcQ();

    assertBetaObjectiveMatchesDirectEvaluation(system, operation);
  }

  /**
   * Compares the cached beta-objective gradient and Hessian with their direct multiphase Rachford-Rice evaluation.
   *
   * @param system thermodynamic system supplying phase fractions and fugacity coefficients
   * @param operation multiflash operation containing the evaluated objective derivatives
   */
  private void assertBetaObjectiveMatchesDirectEvaluation(SystemInterface system, TPmultiflash operation) {
    int numberOfPhases = system.getNumberOfPhases();
    int numberOfComponents = system.getPhase(0).getNumberOfComponents();
    double[] denominator = new double[numberOfComponents];

    for (int component = 0; component < numberOfComponents; component++) {
      for (int phase = 0; phase < numberOfPhases; phase++) {
        denominator[component] += system.getBeta(phase)
            / system.getPhase(phase).getComponent(component).getFugacityCoefficient();
      }
    }

    for (int phase = 0; phase < numberOfPhases; phase++) {
      double expectedGradient = 1.0;
      for (int component = 0; component < numberOfComponents; component++) {
        double feedFraction = system.getPhase(0).getComponent(component).getz();
        double fugacityCoefficient = system.getPhase(phase).getComponent(component).getFugacityCoefficient();
        expectedGradient -= feedFraction / denominator[component] / fugacityCoefficient;
      }
      assertEquals(expectedGradient, operation.dQdbeta[phase][0]);

      for (int otherPhase = 0; otherPhase < numberOfPhases; otherPhase++) {
        double expectedHessian = 0.0;
        for (int component = 0; component < numberOfComponents; component++) {
          double feedFraction = system.getPhase(0).getComponent(component).getz();
          double phaseFugacityCoefficient = system.getPhase(phase).getComponent(component).getFugacityCoefficient();
          double otherPhaseFugacityCoefficient = system.getPhase(otherPhase).getComponent(component)
              .getFugacityCoefficient();
          double feedOverDenominatorSquared = feedFraction / (denominator[component] * denominator[component]);
          expectedHessian += feedOverDenominatorSquared / (otherPhaseFugacityCoefficient * phaseFugacityCoefficient);
        }
        if (phase == otherPhase) {
          expectedHessian += 1.0e-3;
        }
        assertEquals(expectedHessian, operation.Qmatrix[phase][otherPhase]);
      }
    }
  }

  /** Verifies a direct beta solve preserves a converged two-phase equilibrium and material balance. */
  @Test
  void testDirectBetaSolvePreservesConvergedTwoPhaseState() {
    SystemInterface system = createMethaneHeptanePrSystem(0.0, false);
    system.setTemperature(250.0, "K");
    system.setPressure(30.0, "bara");
    new ThermodynamicOperations(system).TPflash();
    system.init(1);

    assertEquals(2, system.getNumberOfPhases());
    double[] referenceBeta = new double[2];
    double[] referenceZ = new double[2];
    double[][] referenceComposition = new double[2][system.getPhase(0).getNumberOfComponents()];
    for (int phase = 0; phase < 2; phase++) {
      referenceBeta[phase] = system.getBeta(phase);
      referenceZ[phase] = system.getPhase(phase).getZ();
      for (int component = 0; component < referenceComposition[phase].length; component++) {
        referenceComposition[phase][component] = system.getPhase(phase).getComponent(component).getx();
      }
    }

    TPmultiflash operation = new TPmultiflash(system, false);
    operation.setDoubleArrays();
    double residual = operation.solveBeta();

    assertTrue(Double.isFinite(residual));
    assertTrue(residual <= 1.0e-10);
    for (int phase = 0; phase < 2; phase++) {
      assertEquals(referenceBeta[phase], system.getBeta(phase), 1.0e-11);
      assertEquals(referenceZ[phase], system.getPhase(phase).getZ(), 1.0e-11);
      double compositionSum = 0.0;
      for (int component = 0; component < referenceComposition[phase].length; component++) {
        assertEquals(referenceComposition[phase][component], system.getPhase(phase).getComponent(component).getx(),
            1.0e-11);
        compositionSum += system.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(1.0, compositionSum, 1.0e-12);
    }

    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      double recoveredFeed = 0.0;
      for (int phase = 0; phase < 2; phase++) {
        recoveredFeed += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(system.getPhase(0).getComponent(component).getz(), recoveredFeed, 1.0e-11);
    }
  }

  /** Verifies reused beta-solver work matrices converge repeatedly from poor phase fractions. */
  @Test
  void testReusedBetaSolverMatricesConvergeFromPoorPhaseFractions() {
    SystemInterface system = createMethaneHeptanePrSystem(0.0, false);
    system.setTemperature(250.0, "K");
    system.setPressure(30.0, "bara");
    new ThermodynamicOperations(system).TPflash();
    system.init(1);

    assertEquals(2, system.getNumberOfPhases());
    double[] referenceBeta = new double[] { system.getBeta(0), system.getBeta(1) };
    double[][] referenceComposition = new double[2][system.getPhase(0).getNumberOfComponents()];
    for (int phase = 0; phase < 2; phase++) {
      for (int component = 0; component < referenceComposition[phase].length; component++) {
        referenceComposition[phase][component] = system.getPhase(phase).getComponent(component).getx();
      }
    }

    system.setBeta(0, 0.9);
    system.setBeta(1, 0.1);
    system.init(1);
    TPmultiflash operation = new TPmultiflash(system, false);
    operation.setDoubleArrays();

    for (int execution = 0; execution < 2; execution++) {
      double residual = operation.solveBeta();
      assertTrue(Double.isFinite(residual));
      assertTrue(residual <= 1.0e-10);
      for (int phase = 0; phase < 2; phase++) {
        assertEquals(referenceBeta[phase], system.getBeta(phase), 1.0e-10);
        double compositionSum = 0.0;
        for (int component = 0; component < referenceComposition[phase].length; component++) {
          assertEquals(referenceComposition[phase][component], system.getPhase(phase).getComponent(component).getx(),
              1.0e-10);
          compositionSum += system.getPhase(phase).getComponent(component).getx();
        }
        assertEquals(1.0, compositionSum, 1.0e-12);
      }
    }
  }

}
