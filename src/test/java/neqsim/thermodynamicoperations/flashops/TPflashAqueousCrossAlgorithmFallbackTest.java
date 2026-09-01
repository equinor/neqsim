package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashAqueousCrossAlgorithmFallbackTest {
  private static final String[] COMPONENTS = { "CO2", "methane", "ethane", "water" };
  private static final double[] FEED = { 0.543865141103918, 0.2937712952303271, 0.07010605470616459,
      0.09225750895959021 };

  @Test
  void multiphaseFlashRejectsInvalidHigherGibbsAqueousEndpoint() {
    SystemInterface ordinary = createAndFlash(260.0, 100.0, false, false);
    SystemInterface multiphase = createAndFlash(260.0, 100.0, true, true);

    assertEquivalentEquilibrium(ordinary, multiphase);
    double firstGibbsEnergy = multiphase.getGibbsEnergy();

    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(3);

    assertEquals(firstGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-8);
    assertEquivalentEquilibrium(ordinary, multiphase);
  }

  @Test
  void multiphaseFallbackRemainsContinuousAcrossNearbyStates() {
    double[][] states = { { 260.0, 100.0 }, { 255.0, 100.0 }, { 260.0, 90.0 }, { 260.0, 110.0 }, { 265.0, 100.0 },
        { 260.0, 100.0 } };
    SystemInterface multiphase = createSystem(states[0][0], states[0][1], true, true);

    for (double[] state : states) {
      multiphase.setTemperature(state[0]);
      multiphase.setPressure(state[1]);
      new ThermodynamicOperations(multiphase).TPflash();
      multiphase.init(3);

      SystemInterface ordinary = createAndFlash(state[0], state[1], false, false);
      assertEquivalentEquilibrium(ordinary, multiphase);
    }
  }

  private SystemInterface createAndFlash(double temperature, double pressure, boolean multiphaseCheck,
      boolean poorBetaGuess) {
    SystemInterface system = createSystem(temperature, pressure, multiphaseCheck, poorBetaGuess);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private SystemInterface createSystem(double temperature, double pressure, boolean multiphaseCheck,
      boolean poorBetaGuess) {
    SystemInterface system = new SystemPrEos(temperature, pressure);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
    if (poorBetaGuess) {
      system.setBeta(0, 1.0e-9);
      system.setBeta(1, 1.0 - 1.0e-9);
    }
    return system;
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual) {
    assertEquals(2, expected.getNumberOfPhases());
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(PhaseType.GAS, actual.getPhase(0).getType());
    assertEquals(PhaseType.AQUEOUS, actual.getPhase(1).getType());
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-7);
    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(), 1.0e-7);

    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(expected.getPhase(phaseIndex).getType(), actual.getPhase(phaseIndex).getType());
      assertEquals(expected.getBeta(phaseIndex), actual.getBeta(phaseIndex), 1.0e-10);
      assertTrue(actual.getBeta(phaseIndex) > 0.0 && actual.getBeta(phaseIndex) < 1.0);
      assertEquals(expected.getPhase(phaseIndex).getZ(), actual.getPhase(phaseIndex).getZ(), 1.0e-10);
      assertEquals(expected.getPhase(phaseIndex).getDensity(), actual.getPhase(phaseIndex).getDensity(), 1.0e-7);
      betaTotal += actual.getBeta(phaseIndex);

      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        double composition = actual.getPhase(phaseIndex).getComponent(componentIndex).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0);
        assertEquals(expected.getPhase(phaseIndex).getComponent(componentIndex).getx(), composition, 1.0e-10);
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, 1.0e-10);
    }
    assertEquals(1.0, betaTotal, 1.0e-12);

    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
        recoveredFeed += actual.getBeta(phaseIndex) * actual.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(FEED[componentIndex], recoveredFeed, 1.0e-10);
    }
    assertTrue(maximumLogFugacityResidual(actual) < 1.0e-8);
  }

  private double maximumLogFugacityResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double firstLogFugacity = Math
          .log(Math.max(system.getPhase(0).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(system.getPhase(0).getComponent(componentIndex).getFugacityCoefficient());
      double secondLogFugacity = Math
          .log(Math.max(system.getPhase(1).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(system.getPhase(1).getComponent(componentIndex).getFugacityCoefficient());
      maximumResidual = Math.max(maximumResidual, Math.abs(firstLogFugacity - secondLogFugacity));
    }
    return maximumResidual;
  }
}
