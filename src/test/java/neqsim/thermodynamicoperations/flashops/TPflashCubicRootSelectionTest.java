package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashCubicRootSelectionTest {
  private static final double CROSS_ALGORITHM_STATE_TOLERANCE = 1.0e-8;
  private static final String[] COMPONENTS = { "methane", "ethane", "propane", "n-heptane", "nC10" };
  private static final double[] FEED = { 0.72, 0.08, 0.05, 0.10, 0.05 };
  private static final String[] NEAR_CRITICAL_COMPONENTS = { "methane", "ethane", "propane", "n-butane" };
  private static final double[] NEAR_CRITICAL_FEED = { 0.5833884211682981, 0.16475359157041228, 0.19866217294783825,
      0.053195814313451245 };
  private static final String[] CO2_RICH_COMPONENTS = { "CO2", "methane", "nC10" };
  private static final double[] CO2_RICH_FEED = { 0.80, 0.15, 0.05 };

  @Test
  void ordinaryAndMultiphaseFlashSelectSameLowestGibbsCubicRoots() {
    SystemInterface ordinary = createAndFlash(false);
    SystemInterface multiphase = createAndFlash(true);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(multiphase.getPhase(phaseIndex).getType(), ordinary.getPhase(phaseIndex).getType());
      assertEquals(multiphase.getBeta(phaseIndex), ordinary.getBeta(phaseIndex), CROSS_ALGORITHM_STATE_TOLERANCE);
      assertEquals(multiphase.getPhase(phaseIndex).getDensity(), ordinary.getPhase(phaseIndex).getDensity(),
          Math.max(1.0e-8, CROSS_ALGORITHM_STATE_TOLERANCE * Math.abs(multiphase.getPhase(phaseIndex).getDensity())));
      assertEquals(multiphase.getPhase(phaseIndex).getZ(), ordinary.getPhase(phaseIndex).getZ(),
          CROSS_ALGORITHM_STATE_TOLERANCE);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getx(), CROSS_ALGORITHM_STATE_TOLERANCE);
      }
    }

    assertFlashClosure(ordinary, FEED);
    assertFlashClosure(multiphase, FEED);
  }

  @Test
  void ordinaryNearCriticalFlashDoesNotInvertVaporAndLiquidRoots() {
    SystemInterface ordinary = createAndFlashNearCritical(false);
    SystemInterface multiphase = createAndFlashNearCritical(true);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertEquals(PhaseType.GAS, ordinary.getPhase(0).getType());
    assertEquals(PhaseType.OIL, ordinary.getPhase(1).getType());
    assertTrue(ordinary.getPhase(0).getMolarMass() < ordinary.getPhase(1).getMolarMass());
    assertEquals(4766.143859623306, ordinary.getGibbsEnergy(), 1.0e-8);
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(multiphase.getPhase(phaseIndex).getType(), ordinary.getPhase(phaseIndex).getType());
      assertEquals(multiphase.getBeta(phaseIndex), ordinary.getBeta(phaseIndex), 1.0e-12);
      assertEquals(multiphase.getPhase(phaseIndex).getZ(), ordinary.getPhase(phaseIndex).getZ(), 1.0e-12);
      for (int componentIndex = 0; componentIndex < NEAR_CRITICAL_COMPONENTS.length; componentIndex++) {
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-12);
      }
    }

    assertFlashClosure(ordinary, NEAR_CRITICAL_FEED);
    assertFlashClosure(multiphase, NEAR_CRITICAL_FEED);
  }

  @Test
  void ordinaryCo2RichFlashReinitializesGasCubicRoot() {
    SystemInterface ordinary = createAndFlashCo2Rich(false, false);
    SystemInterface multiphase = createAndFlashCo2Rich(true, false);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-7);
    assertEquals(multiphase.getEnthalpy(), ordinary.getEnthalpy(), 1.0e-7);

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(multiphase.getPhase(phaseIndex).getType(), ordinary.getPhase(phaseIndex).getType());
      assertEquals(multiphase.getBeta(phaseIndex), ordinary.getBeta(phaseIndex), 1.0e-12);
      assertEquals(multiphase.getPhase(phaseIndex).getDensity(), ordinary.getPhase(phaseIndex).getDensity(), 1.0e-8);
      for (int componentIndex = 0; componentIndex < CO2_RICH_COMPONENTS.length; componentIndex++) {
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-12);
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient(), 1.0e-10);
      }
    }

    assertFlashClosure(ordinary, CO2_RICH_FEED);
    assertFlashClosure(multiphase, CO2_RICH_FEED);
  }

  @Test
  void nearbyCo2RichStatesPreserveCubicRootAcrossPhaseBoundary() {
    double[][] states = { { 275.0, 75.0, 0.78, 0.17, 0.05 }, { 285.0, 85.0, 0.82, 0.13, 0.05 },
        { 300.0, 100.0, 0.80, 0.15, 0.05 }, { 320.0, 150.0, 0.80, 0.15, 0.05 }, { 280.0, 250.0, 0.80, 0.15, 0.05 } };
    boolean observedSinglePhase = false;
    boolean observedTwoPhase = false;

    for (double[] state : states) {
      double[] feed = { state[2], state[3], state[4] };
      SystemInterface ordinary = createAndFlashCo2Rich(state[0], state[1], feed, false, false);
      SystemInterface multiphase = createAndFlashCo2Rich(state[0], state[1], feed, true, false);

      assertEquivalentCo2RichState(multiphase, ordinary, feed);
      observedSinglePhase |= ordinary.getNumberOfPhases() == 1;
      observedTwoPhase |= ordinary.getNumberOfPhases() == 2;
    }

    assertTrue(observedSinglePhase, "nearby-state matrix must exercise a single-phase endpoint");
    assertTrue(observedTwoPhase, "nearby-state matrix must exercise a two-phase endpoint");
  }

  @Test
  void co2RichRootSelectionSurvivesPoorBetaGuessAndRepeatedFlash() {
    SystemInterface reference = createAndFlashCo2Rich(false, false);
    SystemInterface poorGuess = createAndFlashCo2Rich(false, true);

    assertEquivalentCo2RichState(reference, poorGuess, CO2_RICH_FEED);
    new ThermodynamicOperations(poorGuess).TPflash();
    poorGuess.init(3);
    assertEquivalentCo2RichState(reference, poorGuess, CO2_RICH_FEED);
  }

  private SystemInterface createAndFlash(boolean multiphaseCheck) {
    SystemInterface system = new SystemPrEos(220.0, 100.0);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private SystemInterface createAndFlashNearCritical(boolean multiphaseCheck) {
    SystemInterface system = new SystemSrkEos(253.46685189059752, 77.15006534170463);
    for (int componentIndex = 0; componentIndex < NEAR_CRITICAL_COMPONENTS.length; componentIndex++) {
      system.addComponent(NEAR_CRITICAL_COMPONENTS[componentIndex], NEAR_CRITICAL_FEED[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private SystemInterface createAndFlashCo2Rich(boolean multiphaseCheck, boolean poorGuess) {
    return createAndFlashCo2Rich(280.0, 80.0, CO2_RICH_FEED, multiphaseCheck, poorGuess);
  }

  private SystemInterface createAndFlashCo2Rich(double temperature, double pressure, double[] feed,
      boolean multiphaseCheck, boolean poorGuess) {
    SystemInterface system = new SystemPrEos(temperature, pressure);
    for (int componentIndex = 0; componentIndex < CO2_RICH_COMPONENTS.length; componentIndex++) {
      system.addComponent(CO2_RICH_COMPONENTS[componentIndex], feed[componentIndex]);
    }
    system.setMixingRule(2);
    system.setMultiPhaseCheck(multiphaseCheck);
    if (poorGuess) {
      system.setBeta(0, 1.0e-9);
      system.setBeta(1, 1.0 - 1.0e-9);
    }
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private void assertEquivalentCo2RichState(SystemInterface expected, SystemInterface actual, double[] feed) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-6);
    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(), 1.0e-6);
    for (int phaseIndex = 0; phaseIndex < expected.getNumberOfPhases(); phaseIndex++) {
      assertEquals(expected.getPhase(phaseIndex).getType(), actual.getPhase(phaseIndex).getType());
      assertEquals(expected.getBeta(phaseIndex), actual.getBeta(phaseIndex), 1.0e-10);
      assertEquals(expected.getPhase(phaseIndex).getDensity(), actual.getPhase(phaseIndex).getDensity(), 1.0e-7);
      assertEquals(expected.getPhase(phaseIndex).getZ(), actual.getPhase(phaseIndex).getZ(), 1.0e-10);
      for (int componentIndex = 0; componentIndex < feed.length; componentIndex++) {
        assertEquals(expected.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            actual.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-10);
        assertEquals(expected.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient(),
            actual.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient(), 1.0e-8);
      }
    }
    assertMaterialBalanceAndEquilibrium(expected, feed);
    assertMaterialBalanceAndEquilibrium(actual, feed);
  }

  private void assertMaterialBalanceAndEquilibrium(SystemInterface system, double[] feed) {
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      betaTotal += system.getBeta(phaseIndex);
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < feed.length; componentIndex++) {
        compositionTotal += system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(1.0, compositionTotal, 1.0e-8);
    }
    assertEquals(1.0, betaTotal, 1.0e-8);

    for (int componentIndex = 0; componentIndex < feed.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(feed[componentIndex], recoveredFeed, 1.0e-8);
      if (system.getNumberOfPhases() == 2) {
        double firstPhaseFugacity = system.getPhase(0).getComponent(componentIndex).getx()
            * system.getPhase(0).getComponent(componentIndex).getFugacityCoefficient();
        double secondPhaseFugacity = system.getPhase(1).getComponent(componentIndex).getx()
            * system.getPhase(1).getComponent(componentIndex).getFugacityCoefficient();
        assertTrue(Math.abs(Math.log(firstPhaseFugacity / secondPhaseFugacity)) < 1.0e-8);
      }
    }
  }

  private void assertFlashClosure(SystemInterface system, double[] feed) {
    assertEquals(1.0, system.getBeta(0) + system.getBeta(1), 1.0e-12);
    double maximumLogFugacityResidual = 0.0;
    for (int componentIndex = 0; componentIndex < feed.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(feed[componentIndex], recoveredFeed, 1.0e-12);

      double firstPhaseFugacity = system.getPhase(0).getComponent(componentIndex).getx()
          * system.getPhase(0).getComponent(componentIndex).getFugacityCoefficient();
      double secondPhaseFugacity = system.getPhase(1).getComponent(componentIndex).getx()
          * system.getPhase(1).getComponent(componentIndex).getFugacityCoefficient();
      maximumLogFugacityResidual = Math.max(maximumLogFugacityResidual,
          Math.abs(Math.log(firstPhaseFugacity / secondPhaseFugacity)));
    }
    assertTrue(maximumLogFugacityResidual < 1.0e-8, "maximum log fugacity residual was " + maximumLogFugacityResidual);
  }
}
