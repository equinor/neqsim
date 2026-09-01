package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashTraceAqueousRefinementTest {
  private static final String[] COMPONENTS = { "nitrogen", "CO2", "methane", "ethane", "propane", "nC10", "water" };
  private static final double[] WATER_FEED_FRACTIONS = { 1.0e-4, 1.0e-3, 9.0e-3 };

  @Test
  void traceAqueousEndpointsRemainBalancedAndMatchMultiphaseFlash() {
    for (boolean usePr : new boolean[] { false, true }) {
      for (double waterFeedFraction : WATER_FEED_FRACTIONS) {
        SystemInterface ordinary = createAndFlash(usePr, waterFeedFraction, false);
        SystemInterface multiphase = createAndFlash(usePr, waterFeedFraction, true);

        assertBalancedEquilibrium(ordinary);
        assertEquivalentEquilibrium(multiphase, ordinary);

        SystemInterface repeated = ordinary.clone();
        new ThermodynamicOperations(repeated).TPflash();
        repeated.init(1);
        assertEquivalentEquilibrium(ordinary, repeated);
      }
    }
  }

  @Test
  void invalidTraceWaterGasOilEndpointSelectsLowerGibbsAqueousSplit() {
    SystemInterface ordinary = createAndFlashAtPhaseSelectionBoundary(false);
    SystemInterface multiphase = createAndFlashAtPhaseSelectionBoundary(true);

    assertBalancedEquilibrium(ordinary);
    assertEquivalentEquilibrium(multiphase, ordinary);
  }

  @Test
  void convergedTraceWaterGasOilEndpointSelectsStableAqueousSplit() {
    SystemInterface ordinary = createAndFlashMissedAqueousCase(true, 275.7756311717352, 200.0, 0.001, 0.009, false,
        false);
    SystemInterface multiphase = createAndFlashMissedAqueousCase(true, 275.7756311717352, 200.0, 0.001, 0.009, true,
        false);
    SystemInterface poorGuess = createAndFlashMissedAqueousCase(true, 275.7756311717352, 200.0, 0.001, 0.009, false,
        true);

    assertBalancedEquilibrium(ordinary);
    assertEquivalentEquilibrium(multiphase, ordinary);
    assertEquivalentEquilibrium(ordinary, poorGuess);
    assertEquals(9.436497907e-4, ordinary.getBeta(findPhase(ordinary, PhaseType.AQUEOUS)), 1.0e-10);
  }

  @Test
  void enrichedMinorHydrocarbonLiquidUsesAqueousStabilityTrial() {
    SystemInterface ordinary = createAndFlashMissedAqueousCase(false, 270.0, 220.0, 0.003, 0.012, false, false);
    SystemInterface multiphase = createAndFlashMissedAqueousCase(false, 270.0, 220.0, 0.003, 0.012, true, false);

    assertBalancedEquilibrium(ordinary);
    assertEquivalentEquilibrium(multiphase, ordinary);
  }

  private SystemInterface createAndFlash(boolean usePr, double waterFeedFraction, boolean multiphaseCheck) {
    SystemInterface system = usePr ? new SystemPrEos(230.0, 200.0) : new SystemSrkEos(230.0, 200.0);
    double[] feed = { 0.02, 0.03, 0.859 - waterFeedFraction, 0.06, 0.03, 0.001, waterFeedFraction };
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], feed[componentIndex]);
    }
    system.setMixingRule(2);
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private SystemInterface createAndFlashAtPhaseSelectionBoundary(boolean multiphaseCheck) {
    SystemInterface system = new SystemSrkEos(260.0, 200.0);
    double[] feed = { 0.02, 0.03, 0.85, 0.06, 0.03, 0.009, 0.001 };
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], feed[componentIndex]);
    }
    system.setMixingRule(2);
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private SystemInterface createAndFlashMissedAqueousCase(boolean usePr, double temperature, double pressure,
      double waterFeedFraction, double nC10FeedFraction, boolean multiphaseCheck, boolean poorGuess) {
    SystemInterface system = usePr ? new SystemPrEos(temperature, pressure) : new SystemSrkEos(temperature, pressure);
    double[] feed = { 0.02, 0.03, 0.86 - waterFeedFraction - nC10FeedFraction, 0.06, 0.03, nC10FeedFraction,
        waterFeedFraction };
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], feed[componentIndex]);
    }
    system.setMixingRule(2);
    system.setMultiPhaseCheck(multiphaseCheck);
    if (poorGuess) {
      system.setBeta(0, 1.0e-9);
      system.setBeta(1, 1.0 - 1.0e-9);
    }
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private void assertBalancedEquilibrium(SystemInterface system) {
    assertEquals(2, system.getNumberOfPhases());
    assertTrue(system.hasPhaseType(PhaseType.GAS));
    assertTrue(system.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(maximumComponentMaterialBalanceResidual(system) < 1.0e-8);
    assertTrue(maximumLogFugacityResidual(system) < 1.0e-8);

    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      assertTrue(system.getBeta(phaseIndex) > 0.0);
      assertTrue(system.getBeta(phaseIndex) < 1.0);
      betaTotal += system.getBeta(phaseIndex);

      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        compositionTotal += system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(1.0, compositionTotal, 1.0e-10);
    }
    assertEquals(1.0, betaTotal, 1.0e-12);
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    for (int phaseIndex = 0; phaseIndex < expected.getNumberOfPhases(); phaseIndex++) {
      PhaseType phaseType = expected.getPhase(phaseIndex).getType();
      int actualPhaseIndex = findPhase(actual, phaseType);
      assertEquals(expected.getBeta(phaseIndex), actual.getBeta(actualPhaseIndex), 1.0e-10);
      assertEquals(expected.getPhase(phaseIndex).getZ(), actual.getPhase(actualPhaseIndex).getZ(), 1.0e-10);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(expected.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            actual.getPhase(actualPhaseIndex).getComponent(componentIndex).getx(), 1.0e-10);
      }
    }
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-7);
  }

  private int findPhase(SystemInterface system, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getType() == phaseType) {
        return phaseIndex;
      }
    }
    throw new AssertionError("Missing phase " + phaseType);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      maximumResidual = Math.max(maximumResidual,
          Math.abs(system.getPhase(0).getComponent(componentIndex).getz() - recoveredFeed));
    }
    return maximumResidual;
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
