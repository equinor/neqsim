package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashSrkWaterRichSeededPhaseSetTest {
  private static final String[] COMPONENTS = { "CO2", "methane", "ethane", "water" };
  private static final double[] FEED = { 0.543865141103918, 0.2937712952303271, 0.07010605470616459,
      0.09225750895959021 };

  @Test
  void seededPhaseSetRepairsInvalidOrdinaryAndCollapsedMultiphaseEndpoints() {
    SystemInterface ordinary = createAndFlash(220.0, 200.0, false, false);
    SystemInterface multiphase = createAndFlash(220.0, 200.0, true, true);

    assertEquivalentEquilibrium(ordinary, multiphase);
    assertEquals(PhaseType.OIL, ordinary.getPhase(0).getType());
    assertEquals(PhaseType.AQUEOUS, ordinary.getPhase(1).getType());
    assertEquals(0.907774808238, ordinary.getBeta(0), 1.0e-10);
    assertEquals(1925.79587972, ordinary.getGibbsEnergy(), 1.0e-7);

    double firstGibbsEnergy = multiphase.getGibbsEnergy();
    double firstOilFraction = multiphase.getBeta(0);
    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(3);

    assertEquals(firstGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-8);
    assertEquals(firstOilFraction, multiphase.getBeta(0), 1.0e-12);
    assertEquivalentEquilibrium(ordinary, multiphase);
  }

  @Test
  void seededPhaseSetRemainsContinuousAcrossChangedNearbyStates() {
    double[][] states = { { 215.0, 225.0 }, { 220.0, 200.0 }, { 220.0, 225.0 }, { 225.0, 200.0 }, { 230.0, 250.0 },
        { 220.0, 200.0 } };
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

  @Test
  void balancedAqueousEndpointRejectsHigherGibbsThreePhaseTrial() {
    SystemInterface ordinary = createAndFlash(225.0, 200.0, false, false);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(PhaseType.OIL, ordinary.getPhase(0).getType());
    assertEquals(PhaseType.AQUEOUS, ordinary.getPhase(1).getType());
    assertEquals(0.907786822946, ordinary.getBeta(0), 1.0e-10);
    assertEquals(2338.92392890, ordinary.getGibbsEnergy(), 1.0e-7);
    assertTrue(maximumLogFugacityResidual(ordinary) < 1.0e-8);
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
    SystemInterface system = new SystemSrkEos(temperature, pressure);
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
