package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import org.ejml.data.DMatrixRMaj;
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
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  @Test
  void methaneHeptaneBoundaryClosesAcrossFlashModes() {
    final double binaryInteractionParameter = 0.05;
    SystemInterface ordinary = flashMethaneHeptane(155.1, 84.4, binaryInteractionParameter, false, false, false);
    SystemInterface multiphase = flashMethaneHeptane(155.1, 84.4, binaryInteractionParameter, true, false, false);
    SystemInterface enhanced = flashMethaneHeptane(155.1, 84.4, binaryInteractionParameter, true, true, false);

    assertHydrocarbonTwoPhaseEquilibrium(ordinary, "ordinary");
    assertHydrocarbonTwoPhaseEquilibrium(multiphase, "multiphase");
    assertHydrocarbonTwoPhaseEquilibrium(enhanced, "enhanced");
    assertEquivalentHydrocarbonState(ordinary, multiphase, 1.0e-8, "ordinary versus multiphase");
    assertEquivalentHydrocarbonState(multiphase, enhanced, 1.0e-8, "multiphase versus enhanced");
  }

  @Test
  void methaneHeptaneBoundarySurvivesPoorGuessNearbyStateAndRepeat() {
    final double binaryInteractionParameter = 0.05;
    SystemInterface reference = flashMethaneHeptane(155.1, 84.4, binaryInteractionParameter, true, true, false);
    SystemInterface poorGuess = flashMethaneHeptane(155.1, 84.4, binaryInteractionParameter, true, true, true);
    assertEquivalentHydrocarbonState(reference, poorGuess, 1.0e-8, "poor beta initialization");

    for (double pressure : new double[] { 84.3, 84.4, 84.5 }) {
      SystemInterface ordinary = flashMethaneHeptane(155.1, pressure, binaryInteractionParameter, false, false, false);
      SystemInterface enhanced = flashMethaneHeptane(155.1, pressure, binaryInteractionParameter, true, true, false);
      assertHydrocarbonTwoPhaseEquilibrium(ordinary, "ordinary at " + pressure + " bara");
      assertHydrocarbonTwoPhaseEquilibrium(enhanced, "enhanced at " + pressure + " bara");
      assertEquivalentHydrocarbonState(ordinary, enhanced, 1.0e-8, "nearby pressure " + pressure + " bara");
    }

    SystemInterface changedState = reference.clone();
    changedState.setPressure(84.5, "bara");
    new ThermodynamicOperations(changedState).TPflash();
    changedState.init(3);
    SystemInterface changedReference = flashMethaneHeptane(155.1, 84.5, binaryInteractionParameter, true, true, false);
    assertEquivalentHydrocarbonState(changedReference, changedState, 1.0e-8, "changed pressure");

    changedState.setPressure(84.4, "bara");
    new ThermodynamicOperations(changedState).TPflash();
    changedState.init(3);
    assertEquivalentHydrocarbonState(reference, changedState, 1.0e-8, "return to reference pressure");

    SystemInterface repeatedReference = changedState.clone();
    new ThermodynamicOperations(changedState).TPflash();
    changedState.init(3);
    assertEquivalentHydrocarbonState(repeatedReference, changedState, 1.0e-10, "deterministic repeat");
  }

  /**
   * Verifies that enhanced stability checks do not perturb hydrocarbon-only PR binary flashes.
   */
  @Test
  void testEnhancedHydrocarbonBinaryMatchesOrdinaryMultiphaseCheck() {
    final double binaryInteractionParameter = 0.05;
    double[][] conditions = new double[][] { { 110.0, 264.0 }, { 112.5, 276.0 }, { 70.0, 458.0 }, { 120.0, 200.0 } };

    for (double[] condition : conditions) {
      String label = "P=" + condition[0] + " bara, T=" + condition[1] + " K";
      SystemInterface ordinarySystem = flashMethaneHeptane(condition[1], condition[0], binaryInteractionParameter, true,
          false, false);
      SystemInterface enhancedSystem = flashMethaneHeptane(condition[1], condition[0], binaryInteractionParameter, true,
          true, false);

      assertFlashClosure(ordinarySystem, label + " ordinary");
      assertFlashClosure(enhancedSystem, label + " enhanced");
      assertEquivalentHydrocarbonState(ordinarySystem, enhancedSystem, 1.0e-10, label);
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
    return createMethaneHeptanePrSystem(binaryInteractionParameter, true, enhancedCheck);
  }

  private SystemInterface createMethaneHeptanePrSystem(double binaryInteractionParameter, boolean multiphaseCheck,
      boolean enhancedCheck) {
    SystemInterface methaneHeptaneSystem = new neqsim.thermo.system.SystemPrEos();
    methaneHeptaneSystem.addComponent("methane", 70.0);
    methaneHeptaneSystem.addComponent("n-heptane", 30.0);

    methaneHeptaneSystem.setMixingRule("classic");
    ((EosMixingRulesInterface) methaneHeptaneSystem.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0, 1,
        binaryInteractionParameter);
    ((EosMixingRulesInterface) methaneHeptaneSystem.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0, 1,
        binaryInteractionParameter);

    methaneHeptaneSystem.setMultiPhaseCheck(multiphaseCheck);
    methaneHeptaneSystem.setEnhancedMultiPhaseCheck(enhancedCheck);
    return methaneHeptaneSystem;
  }

  private SystemInterface flashMethaneHeptane(double temperature, double pressure, double binaryInteractionParameter,
      boolean multiphaseCheck, boolean enhancedCheck, boolean poorGuess) {
    SystemInterface system = createMethaneHeptanePrSystem(binaryInteractionParameter, multiphaseCheck, enhancedCheck);
    system.setTemperature(temperature, "K");
    system.setPressure(pressure, "bara");
    if (poorGuess) {
      system.setBeta(0, 1.0e-12);
      system.setBeta(1, 1.0 - 1.0e-12);
    }
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private void assertHydrocarbonTwoPhaseEquilibrium(SystemInterface system, String label) {
    assertEquals(2, system.getNumberOfPhases(), label);
    Integer[] order = phaseOrder(system);
    double methaneRichHeptaneFraction = system.getPhase(order[0]).getComponent(1).getx();
    double heptaneRichHeptaneFraction = system.getPhase(order[1]).getComponent(1).getx();
    assertTrue(methaneRichHeptaneFraction + 1.0e-8 < heptaneRichHeptaneFraction,
        label + " compositionally distinct methane-rich and n-heptane-rich phases");
    assertFlashClosure(system, label);
  }

  private void assertEquivalentHydrocarbonState(SystemInterface expected, SystemInterface actual, double tolerance,
      String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label);
    assertFlashClosure(expected, label + " expected");
    assertFlashClosure(actual, label + " actual");
    Integer[] expectedOrder = phaseOrder(expected);
    Integer[] actualOrder = phaseOrder(actual);
    for (int orderedPhase = 0; orderedPhase < expectedOrder.length; orderedPhase++) {
      int expectedPhase = expectedOrder[orderedPhase];
      int actualPhase = actualOrder[orderedPhase];
      assertEquals(expected.getPhase(expectedPhase).getType(), actual.getPhase(actualPhase).getType(), label);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance, label);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), tolerance, label);
      for (int component = 0; component < 2; component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), tolerance, label);
      }
    }
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(),
        Math.max(1.0e-8, tolerance * Math.abs(expected.getGibbsEnergy())), label);
  }

  private Integer[] phaseOrder(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order, Comparator.comparingDouble(index -> system.getPhase(index).getComponent(1).getx()));
    return order;
  }

  private void assertFlashClosure(SystemInterface system, String label) {
    double betaTotal = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0, label + " beta");
      betaTotal += beta;
      double compositionTotal = 0.0;
      for (int component = 0; component < 2; component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0, label + " composition");
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, 1.0e-12, label + " phase normalization");
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0,
          label + " compressibility");
    }
    assertEquals(1.0, betaTotal, 1.0e-12, label + " beta normalization");

    double maximumMaterialBalanceResidual = 0.0;
    double maximumFugacityResidual = 0.0;
    for (int component = 0; component < 2; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximumMaterialBalanceResidual = Math.max(maximumMaterialBalanceResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));

      if (system.getNumberOfPhases() >= 2) {
        double referenceLogFugacity = Math
            .log(Math.max(system.getPhase(0).getComponent(component).getx(), Double.MIN_NORMAL))
            + Math.log(system.getPhase(0).getComponent(component).getFugacityCoefficient());
        for (int phase = 1; phase < system.getNumberOfPhases(); phase++) {
          double otherLogFugacity = Math
              .log(Math.max(system.getPhase(phase).getComponent(component).getx(), Double.MIN_NORMAL))
              + Math.log(system.getPhase(phase).getComponent(component).getFugacityCoefficient());
          maximumFugacityResidual = Math.max(maximumFugacityResidual,
              Math.abs(referenceLogFugacity - otherLogFugacity));
        }
      }
    }
    assertTrue(maximumMaterialBalanceResidual < 1.0e-10,
        label + " material balance residual " + maximumMaterialBalanceResidual);
    assertTrue(maximumFugacityResidual < 1.0e-8, label + " fugacity residual " + maximumFugacityResidual);
    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
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
