package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashIncipientPhaseStabilityTest {
  private static final String[] COMPONENTS = { "methane", "ethane", "propane", "n-butane" };
  private static final double[] FEED = { 0.5833884211682981, 0.16475359157041228, 0.19866217294783825,
      0.053195814313451245 };

  @Test
  void supplementaryStabilityTrialRetainsIncipientVapor() {
    SystemInterface ordinary = createSystem(false);
    TPflash ordinaryFlash = new TPflash(ordinary);
    ordinaryFlash.run();
    ordinary.init(1);

    SystemInterface multiphase = createSystem(true);
    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(1);

    assertEquals("unstable - supplementary stability trial", ordinaryFlash.getLastStabilityOutcome());
    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertEquals(PhaseType.GAS, ordinary.getPhase(0).getType());
    assertEquals(PhaseType.OIL, ordinary.getPhase(1).getType());
    assertEquals(3.50882832337307e-5, ordinary.getBeta(0), 1.0e-10);
    assertFlashClosure(ordinary);
    assertFlashClosure(multiphase);
    assertEquivalent(ordinary, multiphase);

    double firstGibbsEnergy = ordinary.getGibbsEnergy();
    ordinaryFlash.run();
    ordinary.init(1);
    assertEquals(firstGibbsEnergy, ordinary.getGibbsEnergy(), 1.0e-8);
    assertFlashClosure(ordinary);
  }

  @Test
  void subResidualTpdDoesNotOverrideExistingUmrPruSolution() {
    SystemInterface system = new neqsim.thermo.system.SystemUMRPRUMCEos(243.15, 300.0);
    system.addComponent("methane", 0.416683);
    system.addComponent("ethane", 0.17522);
    system.addComponent("n-pentane", 0.358009);
    system.addComponent("nC16", 0.0500888);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    system.setPressure(90.03461693, "bara");
    system.setTemperature(293.15, "K");
    system.setTotalFlowRate(4.925e-07, "kg/sec");

    new ThermodynamicOperations(system).TPflash();

    assertEquals(1, system.getNumberOfPhases());
    assertEquals(0.10377442547868508, system.getBeta(), 1.0e-4);
  }

  private SystemInterface createSystem(boolean multiphaseCheck) {
    SystemInterface system = new SystemSrkEos(253.46685189059752, 77.53775411226596);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
    return system;
  }

  private void assertEquivalent(SystemInterface ordinary, SystemInterface multiphase) {
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(multiphase.getPhase(phaseIndex).getType(), ordinary.getPhase(phaseIndex).getType());
      assertEquals(multiphase.getBeta(phaseIndex), ordinary.getBeta(phaseIndex), 1.0e-10);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-10);
      }
    }
  }

  private void assertFlashClosure(SystemInterface system) {
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      betaTotal += system.getBeta(phaseIndex);
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        compositionTotal += system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(1.0, compositionTotal, 1.0e-11);
    }
    assertEquals(1.0, betaTotal, 1.0e-12);

    double maximumLogFugacityResidual = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(FEED[componentIndex], recoveredFeed, 1.0e-10);
      maximumLogFugacityResidual = Math.max(maximumLogFugacityResidual,
          Math.abs(logFugacity(system, 0, componentIndex) - logFugacity(system, 1, componentIndex)));
    }
    assertTrue(maximumLogFugacityResidual < 1.0e-8);
  }

  private double logFugacity(SystemInterface system, int phaseIndex, int componentIndex) {
    double composition = system.getPhase(phaseIndex).getComponent(componentIndex).getx();
    double fugacityCoefficient = system.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient();
    return Math.log(Math.max(composition, Double.MIN_NORMAL)) + Math.log(fugacityCoefficient);
  }
}
