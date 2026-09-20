package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemSAFTVRMie;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification of fresh-feed initialization and the SAFT-VR Mie TP-flash lifecycle.
 *
 * <p>
 * The synthetic methane/n-butane feed checks direct and public dispatch, strict equilibrium closure, poor
 * initialization, state reuse, return continuity, and deterministic repeat. It is numerical qualification of the Java
 * calculation, not independent parameter validation.
 * </p>
 */
class TPflashSaftFeedInitializationTest {
  private static final double NORMALIZATION_TOLERANCE = 5.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;

  private SystemSAFTVRMie feed(double temperature) {
    SystemSAFTVRMie system = new SystemSAFTVRMie(temperature, 30.0);
    system.addComponent("methane", 0.6);
    system.addComponent("n-butane", 0.4);
    system.setMixingRule("classic");
    return system;
  }

  @Test
  void freshFeedMatchesInitializedFeedAtTwoTemperatures() {
    for (double temperature : new double[] {200.0, 250.0}) {
      SystemSAFTVRMie reference = feed(temperature);
      reference.init(0);
      runFlash(reference, true);
      assertQualifiedState(reference, "initialized reference at " + temperature + " K");

      for (boolean direct : new boolean[] {false, true}) {
        SystemSAFTVRMie system = feed(temperature);
        runFlash(system, direct);
        assertEquivalentState(reference, system, 1.0e-8,
            (direct ? "direct" : "public") + " fresh feed at " + temperature + " K");
      }
    }
  }

  @Test
  void poorInitializationRecoversReferenceState() {
    SystemSAFTVRMie reference = feed(200.0);
    runFlash(reference, true);

    for (boolean direct : new boolean[] {false, true}) {
      SystemSAFTVRMie system = feed(200.0);
      system.init(0);
      assertTrue(system.getNumberOfPhases() >= 2, "poor initialization requires two phase slots");
      system.setBeta(0, 1.0e-12);
      system.setBeta(1, 1.0 - 1.0e-12);
      runFlash(system, direct);
      assertEquivalentState(reference, system, 1.0e-8, (direct ? "direct" : "public") + " poor initialization");
    }
  }

  @Test
  void changedReturnedAndRepeatedStatesRemainEquivalent() {
    SystemSAFTVRMie reference = feed(200.0);
    runFlash(reference, false);
    SystemSAFTVRMie reused = reference.clone();

    reused.setTemperature(250.0, "K");
    runFlash(reused, false);
    SystemSAFTVRMie freshChanged = feed(250.0);
    runFlash(freshChanged, false);
    assertEquivalentState(freshChanged, reused, 1.0e-8, "changed state");

    reused.setTemperature(200.0, "K");
    runFlash(reused, false);
    assertEquivalentState(reference, reused, 1.0e-8, "returned state");

    SystemSAFTVRMie previous = reused.clone();
    runFlash(reused, false);
    assertEquivalentState(previous, reused, 1.0e-10, "deterministic repeat");
  }

  private void runFlash(SystemSAFTVRMie system, boolean direct) {
    if (direct) {
      new TPflashSAFT(system).run();
    } else {
      new ThermodynamicOperations(system).TPflash();
    }
    system.init(3);
  }

  private void assertQualifiedState(SystemSAFTVRMie system, String label) {
    assertEquals(2, system.getNumberOfPhases(), label + " phase count");
    assertTrue(system.hasPhaseType(PhaseType.GAS), label + " gas phase");
    assertTrue(system.hasPhaseType(PhaseType.OIL), label + " oil phase");

    double betaTotal = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0, label + " beta " + phase + ": " + beta);
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (int component = 0; component < system.getPhase(phase).getNumberOfComponents(); component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
            label + " composition " + phase + "/" + component + ": " + composition);
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, NORMALIZATION_TOLERANCE, label + " composition normalization " + phase);
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0,
          label + " compressibility " + phase);
    }
    assertEquals(1.0, betaTotal, NORMALIZATION_TOLERANCE, label + " beta normalization");

    double materialResidual = maximumComponentMaterialBalanceResidual(system);
    assertTrue(materialResidual < MATERIAL_BALANCE_TOLERANCE, label + " material-balance residual " + materialResidual);

    double fugacityResidual = maximumComparableLogFugacityResidual(system);
    assertTrue(fugacityResidual < FUGACITY_TOLERANCE, label + " fugacity residual " + fugacityResidual);

    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(system.getEnthalpy()), label + " enthalpy");
  }

  private void assertEquivalentState(SystemSAFTVRMie expected, SystemSAFTVRMie actual, double tolerance, String label) {
    assertQualifiedState(expected, label + " expected");
    assertQualifiedState(actual, label + " actual");

    for (PhaseType type : new PhaseType[] {PhaseType.GAS, PhaseType.OIL}) {
      int expectedPhase = findPhase(expected, type);
      int actualPhase = findPhase(actual, type);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance, label + " beta " + type);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), tolerance,
          label + " compressibility " + type);
      for (int component = 0; component < expected.getPhase(expectedPhase).getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), tolerance,
            label + " composition " + type + "/" + component);
      }
    }
    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), tolerance, label + " Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), tolerance, label + " enthalpy");
  }

  private void assertExtensiveEquals(double expected, double actual, double relativeTolerance, String label) {
    assertEquals(expected, actual, Math.max(1.0e-8, relativeTolerance * Math.abs(expected)), label);
  }

  private int findPhase(SystemSAFTVRMie system, PhaseType type) {
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == type) {
        return phase;
      }
    }
    throw new AssertionError("missing phase " + type);
  }

  private double maximumComponentMaterialBalanceResidual(SystemSAFTVRMie system) {
    double maximumResidual = 0.0;
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximumResidual = Math.max(maximumResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));
    }
    return maximumResidual;
  }

  private double maximumComparableLogFugacityResidual(SystemSAFTVRMie system) {
    int gasPhase = findPhase(system, PhaseType.GAS);
    int oilPhase = findPhase(system, PhaseType.OIL);
    double maximumResidual = 0.0;
    int comparisons = 0;
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      double gasComposition = system.getPhase(gasPhase).getComponent(component).getx();
      double oilComposition = system.getPhase(oilPhase).getComponent(component).getx();
      double gasCoefficient = system.getPhase(gasPhase).getComponent(component).getFugacityCoefficient();
      double oilCoefficient = system.getPhase(oilPhase).getComponent(component).getFugacityCoefficient();
      if (gasComposition > 1.0e-20 && oilComposition > 1.0e-20 && Double.isFinite(gasCoefficient)
          && gasCoefficient > 0.0 && Double.isFinite(oilCoefficient) && oilCoefficient > 0.0) {
        double gasLogFugacity = Math.log(gasComposition * gasCoefficient);
        double oilLogFugacity = Math.log(oilComposition * oilCoefficient);
        maximumResidual = Math.max(maximumResidual, Math.abs(gasLogFugacity - oilLogFugacity));
        comparisons++;
      }
    }
    assertEquals(2, comparisons, "both components must expose comparable fugacities");
    return maximumResidual;
  }
}
