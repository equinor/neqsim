package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPmultiflashPhaseDisappearanceTest {
  private static final String[] COMPONENTS = {"CO2", "methane", "ethane", "water"};
  private static final double[] FEED = {0.543865141103918, 0.2937712952303271, 0.07010605470616459,
      0.09225750895959021};

  @Test
  void stalledThreePhaseTrialReturnsStableTwoPhaseEndpoint() {
    SystemInterface ordinary = createAndFlash(false);
    SystemInterface multiphase = createAndFlash(true);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertFlashClosure(ordinary);
    assertFlashClosure(multiphase);
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(multiphase.getBeta(phaseIndex), ordinary.getBeta(phaseIndex), 1.0e-12);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-12);
      }
    }

    double firstGibbsEnergy = multiphase.getGibbsEnergy();
    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(1);
    assertEquals(firstGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-8);
    assertFlashClosure(multiphase);
  }

  private SystemInterface createAndFlash(boolean multiphaseCheck) {
    SystemInterface system = new SystemPrEos(250.70511924703197, 74.76182177756704);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private void assertFlashClosure(SystemInterface system) {
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      betaTotal += system.getBeta(phaseIndex);
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        compositionTotal += system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(1.0, compositionTotal, 1.0e-12);
    }
    assertEquals(1.0, betaTotal, 1.0e-12);

    double maximumLogFugacityResidual = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex)
            * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(FEED[componentIndex], recoveredFeed, 1.0e-10);

      double firstLogFugacity = logFugacity(system, 0, componentIndex);
      double secondLogFugacity = logFugacity(system, 1, componentIndex);
      maximumLogFugacityResidual = Math.max(maximumLogFugacityResidual,
          Math.abs(firstLogFugacity - secondLogFugacity));
    }
    assertTrue(maximumLogFugacityResidual < 1.0e-8);
  }

  private double logFugacity(SystemInterface system, int phaseIndex, int componentIndex) {
    double composition = system.getPhase(phaseIndex).getComponent(componentIndex).getx();
    double fugacityCoefficient = system.getPhase(phaseIndex).getComponent(componentIndex)
        .getFugacityCoefficient();
    return Math.log(Math.max(composition, Double.MIN_NORMAL)) + Math.log(fugacityCoefficient);
  }
}
