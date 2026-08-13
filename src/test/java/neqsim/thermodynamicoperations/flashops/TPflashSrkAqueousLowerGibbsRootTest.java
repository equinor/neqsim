package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashSrkAqueousLowerGibbsRootTest {
  private static final String[] COMPONENTS = { "CO2", "methane", "ethane", "water" };
  private static final double[] FEED = { 0.543865141103918, 0.2937712952303271, 0.07010605470616459,
      0.09225750895959021 };

  @Test
  void multiphaseSrkSelectsSameLowerGibbsGasAqueousRootAsOrdinaryFlash() {
    SystemInterface ordinary = createAndFlash(new SystemSrkEos(260.0, 100.0), false);
    SystemInterface multiphase = createAndFlash(new SystemSrkEos(260.0, 100.0), true);

    assertEquivalentGasAqueousEquilibrium(ordinary, multiphase);
    assertEquals(0.907958440381479, multiphase.getBeta(0), 1.0e-10);
    assertEquals(0.301266657120460, multiphase.getPhase(0).getZ(), 1.0e-10);
    assertEquals(4446.598527443467, multiphase.getGibbsEnergy(), 1.0e-7);

    double firstGibbsEnergy = multiphase.getGibbsEnergy();
    double firstGasFraction = multiphase.getBeta(0);
    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(3);
    assertEquals(firstGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-8);
    assertEquals(firstGasFraction, multiphase.getBeta(0), 1.0e-12);
    assertEquivalentGasAqueousEquilibrium(ordinary, multiphase);
  }

  @Test
  void lowerGibbsRootSelectionRemainsContinuousAcrossNearbySrkStates() {
    double[][] states = { { 260.0, 100.0 }, { 262.5, 105.0 }, { 265.0, 110.0 }, { 260.0, 100.0 } };
    SystemInterface multiphase = createSystem(new SystemSrkEos(states[0][0], states[0][1]), true);

    for (double[] state : states) {
      multiphase.setTemperature(state[0]);
      multiphase.setPressure(state[1]);
      new ThermodynamicOperations(multiphase).TPflash();
      multiphase.init(3);

      SystemInterface ordinary = createAndFlash(new SystemSrkEos(state[0], state[1]), false);
      assertEquivalentGasAqueousEquilibrium(ordinary, multiphase);
    }
  }

  @Test
  void genuineThreePhaseSrkStateIsNotCollapsedByTwoPhaseRootRefinement() {
    SystemInterface multiphase = createAndFlash(new SystemSrkEos(255.0, 90.0), true);

    assertEquals(3, multiphase.getNumberOfPhases());
    assertTrue(multiphase.hasPhaseType(PhaseType.GAS));
    assertTrue(multiphase.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(maximumComponentMaterialBalanceResidual(multiphase) < 1.0e-10);
    assertTrue(maximumLogFugacityResidual(multiphase) < 1.0e-8);
    double firstGibbsEnergy = multiphase.getGibbsEnergy();

    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(3);

    assertEquals(3, multiphase.getNumberOfPhases());
    assertEquals(firstGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-8);
    assertTrue(maximumComponentMaterialBalanceResidual(multiphase) < 1.0e-10);
    assertTrue(maximumLogFugacityResidual(multiphase) < 1.0e-8);
  }

  @Test
  void prGasAqueousControlRetainsExistingCrossAlgorithmAgreement() {
    SystemInterface ordinary = createAndFlash(new SystemPrEos(260.0, 100.0), false);
    SystemInterface multiphase = createAndFlash(new SystemPrEos(260.0, 100.0), true);

    assertEquivalentGasAqueousEquilibrium(ordinary, multiphase);
  }

  private SystemInterface createAndFlash(SystemInterface system, boolean multiphaseCheck) {
    configure(system, multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private SystemInterface createSystem(SystemInterface system, boolean multiphaseCheck) {
    configure(system, multiphaseCheck);
    return system;
  }

  private void configure(SystemInterface system, boolean multiphaseCheck) {
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
  }

  private void assertEquivalentGasAqueousEquilibrium(SystemInterface expected, SystemInterface actual) {
    assertEquals(2, expected.getNumberOfPhases());
    assertEquals(2, actual.getNumberOfPhases());
    assertEquals(PhaseType.GAS, expected.getPhase(0).getType());
    assertEquals(PhaseType.AQUEOUS, expected.getPhase(1).getType());
    assertEquals(expected.getPhase(0).getType(), actual.getPhase(0).getType());
    assertEquals(expected.getPhase(1).getType(), actual.getPhase(1).getType());
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-7);
    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(), 1.0e-7);

    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(expected.getBeta(phaseIndex), actual.getBeta(phaseIndex), 1.0e-10);
      assertTrue(Double.isFinite(actual.getBeta(phaseIndex)) && actual.getBeta(phaseIndex) > 0.0
          && actual.getBeta(phaseIndex) < 1.0);
      assertEquals(expected.getPhase(phaseIndex).getZ(), actual.getPhase(phaseIndex).getZ(), 1.0e-10);
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
    assertTrue(maximumComponentMaterialBalanceResidual(actual) < 1.0e-10);
    assertTrue(maximumLogFugacityResidual(actual) < 1.0e-8);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(FEED[componentIndex] - recoveredFeed));
    }
    return maximumResidual;
  }

  private double maximumLogFugacityResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int firstPhase = 0; firstPhase < system.getNumberOfPhases(); firstPhase++) {
      for (int secondPhase = firstPhase + 1; secondPhase < system.getNumberOfPhases(); secondPhase++) {
        for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
          double firstLogFugacity = Math
              .log(Math.max(system.getPhase(firstPhase).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
              + Math.log(system.getPhase(firstPhase).getComponent(componentIndex).getFugacityCoefficient());
          double secondLogFugacity = Math
              .log(Math.max(system.getPhase(secondPhase).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
              + Math.log(system.getPhase(secondPhase).getComponent(componentIndex).getFugacityCoefficient());
          maximumResidual = Math.max(maximumResidual, Math.abs(firstLogFugacity - secondLogFugacity));
        }
      }
    }
    return maximumResidual;
  }
}
