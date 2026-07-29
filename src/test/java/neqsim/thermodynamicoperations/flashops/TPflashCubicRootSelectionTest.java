package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashCubicRootSelectionTest {
  private static final String[] COMPONENTS = { "methane", "ethane", "propane", "n-heptane", "nC10" };
  private static final double[] FEED = { 0.72, 0.08, 0.05, 0.10, 0.05 };

  @Test
  void ordinaryAndMultiphaseFlashSelectSameLowestGibbsCubicRoots() {
    SystemInterface ordinary = createAndFlash(false);
    SystemInterface multiphase = createAndFlash(true);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(multiphase.getBeta(phaseIndex), ordinary.getBeta(phaseIndex), 1.0e-12);
      assertEquals(multiphase.getPhase(phaseIndex).getDensity(), ordinary.getPhase(phaseIndex).getDensity(), 1.0e-8);
      assertEquals(multiphase.getPhase(phaseIndex).getZ(), ordinary.getPhase(phaseIndex).getZ(), 1.0e-12);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-12);
      }
    }

    assertFlashClosure(ordinary);
    assertFlashClosure(multiphase);
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

  private void assertFlashClosure(SystemInterface system) {
    assertEquals(1.0, system.getBeta(0) + system.getBeta(1), 1.0e-12);
    double maximumLogFugacityResidual = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(FEED[componentIndex], recoveredFeed, 1.0e-12);

      double firstPhaseFugacity = system.getPhase(0).getComponent(componentIndex).getx()
          * system.getPhase(0).getComponent(componentIndex).getFugacityCoefficient();
      double secondPhaseFugacity = system.getPhase(1).getComponent(componentIndex).getx()
          * system.getPhase(1).getComponent(componentIndex).getFugacityCoefficient();
      maximumLogFugacityResidual = Math.max(maximumLogFugacityResidual,
          Math.abs(Math.log(firstPhaseFugacity / secondPhaseFugacity)));
    }
    assertTrue(maximumLogFugacityResidual < 1.0e-10);
  }
}
