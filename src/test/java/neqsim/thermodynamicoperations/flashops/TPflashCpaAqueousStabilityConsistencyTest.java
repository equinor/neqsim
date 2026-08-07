package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashCpaAqueousStabilityConsistencyTest {
  private static final String[] COMPONENTS = { "nitrogen", "CO2", "methane", "ethane", "propane", "nC10", "water" };

  @Test
  void ordinaryCpaFlashFindsStableAqueousPhaseAcrossAppearanceBoundary() {
    double[][] states = { { 230.0, 120.0, 0.01, 0.001 }, { 270.0, 220.0, 0.0002, 0.003 } };

    for (double[] state : states) {
      SystemInterface ordinary = createAndFlash(state[0], state[1], state[2], state[3], false, false);
      SystemInterface multiphase = createAndFlash(state[0], state[1], state[2], state[3], true, false);
      SystemInterface poorGuess = createAndFlash(state[0], state[1], state[2], state[3], false, true);

      assertBalancedGasAqueousEquilibrium(ordinary);
      assertEquivalentEquilibrium(multiphase, ordinary);
      assertEquivalentEquilibrium(ordinary, poorGuess);

      SystemInterface repeated = ordinary.clone();
      new ThermodynamicOperations(repeated).TPflash();
      repeated.init(3);
      assertEquivalentEquilibrium(ordinary, repeated);
    }
  }

  @Test
  void stableCpaGasRemainsSinglePhaseAndDeterministic() {
    SystemInterface ordinary = createAndFlash(320.0, 5.0, 0.001, 0.001, false, false);
    SystemInterface multiphase = createAndFlash(320.0, 5.0, 0.001, 0.001, true, false);
    SystemInterface poorGuess = createAndFlash(320.0, 5.0, 0.001, 0.001, false, true);

    assertEquals(1, ordinary.getNumberOfPhases());
    assertEquals(PhaseType.GAS, ordinary.getPhase(0).getType());
    assertEquivalentEquilibrium(multiphase, ordinary);
    assertEquivalentEquilibrium(ordinary, poorGuess);

    double gibbsEnergy = ordinary.getGibbsEnergy();
    double enthalpy = ordinary.getEnthalpy();
    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.init(3);
    assertEquals(gibbsEnergy, ordinary.getGibbsEnergy(), 1.0e-10);
    assertEquals(enthalpy, ordinary.getEnthalpy(), 1.0e-10);
  }

  private SystemInterface createAndFlash(double temperature, double pressure, double water, double decane,
      boolean multiphaseCheck, boolean poorGuess) {
    SystemInterface system = new SystemSrkCPAstatoil(temperature, pressure);
    double[] amounts = { 0.02, 0.03, 0.85, 0.06, 0.03, decane, water };
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], amounts[componentIndex]);
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

  private void assertBalancedGasAqueousEquilibrium(SystemInterface system) {
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
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-7);
    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(), 1.0e-7);
    for (int expectedPhase = 0; expectedPhase < expected.getNumberOfPhases(); expectedPhase++) {
      int actualPhase = findPhase(actual, expected.getPhase(expectedPhase).getType());
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), 1.0e-10);
      assertEquals(expected.getPhase(expectedPhase).getDensity(), actual.getPhase(actualPhase).getDensity(), 1.0e-7);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(componentIndex).getx(),
            actual.getPhase(actualPhase).getComponent(componentIndex).getx(), 1.0e-10);
      }
    }
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
