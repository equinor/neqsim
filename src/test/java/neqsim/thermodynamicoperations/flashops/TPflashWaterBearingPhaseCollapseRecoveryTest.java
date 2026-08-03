package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashWaterBearingPhaseCollapseRecoveryTest {
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;

  @Test
  void co2WaterSplitSurvivesMultiphaseCleanup() {
    assertEquivalentCo2WaterSplit(false, 160.0);
    assertEquivalentCo2WaterSplit(true, 160.0);
    assertEquivalentCo2WaterSplit(true, 320.0);
  }

  private void assertEquivalentCo2WaterSplit(boolean pengRobinson, double pressureBara) {
    SystemInterface ordinary = createCo2WaterSystem(pengRobinson, pressureBara, false);
    SystemInterface multiphase = createCo2WaterSystem(pengRobinson, pressureBara, true);

    assertEquivalentTwoPhaseState(ordinary, multiphase);
    assertDeterministicRepeat(multiphase);
  }

  private SystemInterface createCo2WaterSystem(boolean pengRobinson, double pressureBara, boolean multiphaseCheck) {
    SystemInterface system = pengRobinson ? new SystemPrEos(250.0, pressureBara)
        : new SystemSrkEos(250.0, pressureBara);
    system.addComponent("CO2", 0.75);
    system.addComponent("methane", 0.15);
    system.addComponent("ethane", 0.05);
    system.addComponent("water", 0.05);
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

    for (int phaseIndex = 0; phaseIndex < expected.getNumberOfPhases(); phaseIndex++) {
      assertEquals(expected.getPhase(phaseIndex).getType(), actual.getPhase(phaseIndex).getType());
      assertEquals(expected.getBeta(phaseIndex), actual.getBeta(phaseIndex), 1.0e-12);
      for (int componentIndex = 0; componentIndex < expected.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        assertEquals(expected.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            actual.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-12);
      }
    }
  }

  private void assertDeterministicRepeat(SystemInterface system) {
    double firstGibbsEnergy = system.getGibbsEnergy();
    double firstPhaseFraction = system.getBeta(0);

    new ThermodynamicOperations(system).TPflash();
    system.init(1);

    assertEquals(2, system.getNumberOfPhases());
    assertEquals(firstGibbsEnergy, system.getGibbsEnergy(), 1.0e-8);
    assertEquals(firstPhaseFraction, system.getBeta(0), 1.0e-12);
    assertTrue(maximumComponentMaterialBalanceResidual(system) < MATERIAL_BALANCE_TOLERANCE);
    assertTrue(maximumLogFugacityResidual(system) < 1.0e-8);
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
