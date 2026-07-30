package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashWaterRichEndpointTest {
  private static final String[] COMPONENTS = { "nitrogen", "methane", "ethane", "propane", "nC10", "water" };
  private static final double[] FEED = { 0.01, 0.75, 0.08, 0.04, 0.02, 0.10 };

  @Test
  void ordinaryAndMultiphaseFlashSelectSameGasOilEquilibrium() {
    SystemInterface ordinary = createAndFlash(false);
    SystemInterface multiphase = createAndFlash(true);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertTrue(ordinary.hasPhaseType(PhaseType.GAS));
    assertTrue(ordinary.hasPhaseType(PhaseType.OIL));
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);
    assertEquals(multiphase.getEnthalpy(), ordinary.getEnthalpy(), 1.0e-8);
    double firstOrdinaryGibbsEnergy = ordinary.getGibbsEnergy();
    double firstOrdinaryOilBeta = ordinary.getPhase(0).getType() == PhaseType.OIL ? ordinary.getBeta(0)
        : ordinary.getBeta(1);
    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.initProperties();
    assertEquals(firstOrdinaryGibbsEnergy, ordinary.getGibbsEnergy(), 1.0e-8);
    double repeatedOrdinaryOilBeta = ordinary.getPhase(0).getType() == PhaseType.OIL ? ordinary.getBeta(0)
        : ordinary.getBeta(1);
    assertEquals(firstOrdinaryOilBeta, repeatedOrdinaryOilBeta, 1.0e-12);

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(multiphase.getPhase(phaseIndex).getType(), ordinary.getPhase(phaseIndex).getType());
      assertEquals(multiphase.getBeta(phaseIndex), ordinary.getBeta(phaseIndex), 1.0e-12);
      assertEquals(multiphase.getPhase(phaseIndex).getDensity(), ordinary.getPhase(phaseIndex).getDensity(), 1.0e-8);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-12);
      }
    }

    assertFlashClosure(ordinary);
    assertFlashClosure(multiphase);
  }

  private SystemInterface createAndFlash(boolean multiphaseCheck) {
    SystemInterface system = new SystemSrkCPAstatoil(323.15, 1.0);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule(10);
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
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

      double firstPhaseLogFugacity = Math
          .log(Math.max(system.getPhase(0).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(system.getPhase(0).getComponent(componentIndex).getFugacityCoefficient());
      double secondPhaseLogFugacity = Math
          .log(Math.max(system.getPhase(1).getComponent(componentIndex).getx(), Double.MIN_NORMAL))
          + Math.log(system.getPhase(1).getComponent(componentIndex).getFugacityCoefficient());
      maximumLogFugacityResidual = Math.max(maximumLogFugacityResidual,
          Math.abs(firstPhaseLogFugacity - secondPhaseLogFugacity));
    }
    assertTrue(maximumLogFugacityResidual < 1.0e-8, "maximum log fugacity residual was " + maximumLogFugacityResidual);
  }
}
