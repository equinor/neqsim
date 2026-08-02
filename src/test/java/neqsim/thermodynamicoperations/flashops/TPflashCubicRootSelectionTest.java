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
  private static final String[] COMPONENTS = { "methane", "ethane", "propane", "n-heptane", "nC10" };
  private static final double[] FEED = { 0.72, 0.08, 0.05, 0.10, 0.05 };
  private static final String[] NEAR_CRITICAL_COMPONENTS = { "methane", "ethane", "propane", "n-butane" };
  private static final double[] NEAR_CRITICAL_FEED = { 0.5833884211682981, 0.16475359157041228, 0.19866217294783825,
      0.053195814313451245 };

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
