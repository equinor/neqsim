package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashAqueousPhaseRemovalRecoveryTest {
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;

  @Test
  void failedThirdPhaseTrialCannotReplaceBalancedAqueousEquilibrium() {
    for (boolean pengRobinson : new boolean[] { false, true }) {
      SystemInterface ordinary = createAndFlash(pengRobinson, 16.0, false);
      SystemInterface multiphase = createAndFlash(pengRobinson, 16.0, true);

      assertEquivalentTwoPhaseState(ordinary, multiphase);
      double firstGibbsEnergy = multiphase.getGibbsEnergy();
      double firstGasBeta = multiphase.getBeta(0);

      new ThermodynamicOperations(multiphase).TPflash();
      multiphase.init(1);

      assertEquals(firstGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-8);
      assertEquals(firstGasBeta, multiphase.getBeta(0), 1.0e-12);
      assertEquivalentTwoPhaseState(ordinary, multiphase);
    }
  }

  @Test
  void nearbyGenuineThreePhaseEquilibriumIsRetained() {
    for (boolean pengRobinson : new boolean[] { false, true }) {
      SystemInterface multiphase = createAndFlash(pengRobinson, 24.0, true);

      assertEquals(3, multiphase.getNumberOfPhases());
      assertTrue(maximumComponentMaterialBalanceResidual(multiphase) < MATERIAL_BALANCE_TOLERANCE);
      assertBetaAndCompositionClosure(multiphase);
    }
  }

  private SystemInterface createAndFlash(boolean pengRobinson, double pressureBara, boolean multiphaseCheck) {
    SystemInterface system = pengRobinson ? new SystemPrEos(250.0, pressureBara)
        : new SystemSrkEos(250.0, pressureBara);
    system.addComponent("CO2", 0.7894736842105263);
    system.addComponent("methane", 0.15789473684210525);
    system.addComponent("water", 0.05263157894736842);
    system.setMixingRule(2);
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private void assertEquivalentTwoPhaseState(SystemInterface expected, SystemInterface actual) {
    assertEquals(2, expected.getNumberOfPhases());
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-8);
    assertTrue(maximumComponentMaterialBalanceResidual(actual) < MATERIAL_BALANCE_TOLERANCE);
    assertTrue(maximumLogFugacityResidual(actual) < 1.0e-8);
    assertBetaAndCompositionClosure(actual);

    for (int phaseIndex = 0; phaseIndex < expected.getNumberOfPhases(); phaseIndex++) {
      assertEquals(expected.getPhase(phaseIndex).getType(), actual.getPhase(phaseIndex).getType());
      assertEquals(expected.getBeta(phaseIndex), actual.getBeta(phaseIndex), 1.0e-12);
      assertEquals(expected.getPhase(phaseIndex).getZ(), actual.getPhase(phaseIndex).getZ(), 1.0e-12);
      for (int componentIndex = 0; componentIndex < expected.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        assertEquals(expected.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            actual.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-12);
      }
    }
  }

  private void assertBetaAndCompositionClosure(SystemInterface system) {
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      betaTotal += system.getBeta(phaseIndex);
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < system.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        compositionTotal += system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(1.0, compositionTotal, 1.0e-12);
    }
    assertEquals(1.0, betaTotal, 1.0e-12);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
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
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      double reference = logFugacity(system, 0, componentIndex);
      for (int phaseIndex = 1; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        maximumResidual = Math.max(maximumResidual,
            Math.abs(reference - logFugacity(system, phaseIndex, componentIndex)));
      }
    }
    return maximumResidual;
  }

  private double logFugacity(SystemInterface system, int phaseIndex, int componentIndex) {
    return Math.log(Math.max(system.getPhase(phaseIndex).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
        + Math.log(system.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient());
  }
}
