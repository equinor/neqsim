package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashLiquidLiquidConsistencyTest {
  private static final String[] COMPONENTS = { "methane", "CO2", "n-heptane" };
  private static final double[] FEED = { 0.15, 0.65, 0.20 };

  @Test
  void ordinaryFlashRefinesLowerGibbsLiquidLiquidEndpoint() {
    SystemInterface ordinary = createAndFlash(false);
    SystemInterface multiphase = createAndFlash(true);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(multiphase.getBeta(phaseIndex), ordinary.getBeta(phaseIndex), 1.0e-8);
      assertEquals(multiphase.getPhase(phaseIndex).getDensity(), ordinary.getPhase(phaseIndex).getDensity(), 1.0e-6);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-8);
      }
    }

    assertFlashClosure(ordinary);
    assertFlashClosure(multiphase);
  }

  @Test
  void ordinaryHydrocarbonLiquidRemainsOnSinglePhasePath() {
    SystemInterface system = new SystemPrEos(298.15, 50.0);
    system.addComponent("n-heptane", 0.70);
    system.addComponent("nC10", 0.30);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(false);

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    double firstGibbsEnergy = system.getGibbsEnergy();
    operations.TPflash();

    assertEquals(1, system.getNumberOfPhases());
    assertEquals(firstGibbsEnergy, system.getGibbsEnergy(), 1.0e-8);
  }

  private SystemInterface createAndFlash(boolean multiphaseCheck) {
    SystemInterface system = new SystemPrEos(215.0, 50.0);
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
      assertEquals(FEED[componentIndex], recoveredFeed, 1.0e-10);

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
