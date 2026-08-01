package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashTinyAqueousSeedRejectionTest {
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;

  @Test
  void infeasibleTinyAqueousSeedCannotReplaceStableGas() {
    SystemInterface ordinary = createAndFlash(1.0, false);
    SystemInterface multiphase = createAndFlash(1.0, true);

    assertStableGasEquivalent(ordinary, multiphase);
    double firstGibbsEnergy = multiphase.getGibbsEnergy();

    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(1);

    assertEquals(firstGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-8);
    assertStableGasEquivalent(ordinary, multiphase);
  }

  @Test
  void feasibleNearbyAqueousSplitIsRetained() {
    SystemInterface ordinary = createAndFlash(3.0, false);
    SystemInterface multiphase = createAndFlash(3.0, true);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(ordinary.getNumberOfPhases(), multiphase.getNumberOfPhases());
    assertEquals(ordinary.getGibbsEnergy(), multiphase.getGibbsEnergy(), 1.0e-8);
    assertTrue(maximumComponentMaterialBalanceResidual(multiphase) < MATERIAL_BALANCE_TOLERANCE);
    assertTrue(maximumLogFugacityResidual(multiphase) < 1.0e-8);
  }

  private SystemInterface createAndFlash(double pressure, boolean multiphaseCheck) {
    SystemInterface system = new SystemSrkEos(400.0, pressure);
    system.addComponent("methane", 0.02);
    system.addComponent("water", 0.98);
    system.setMixingRule(2);
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private void assertStableGasEquivalent(SystemInterface expected, SystemInterface actual) {
    assertEquals(1, expected.getNumberOfPhases());
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(PhaseType.GAS, actual.getPhase(0).getType());
    assertEquals(1.0, actual.getBeta(0), 1.0e-12);
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-8);
    assertEquals(expected.getPhase(0).getZ(), actual.getPhase(0).getZ(), 1.0e-12);
    assertTrue(maximumComponentMaterialBalanceResidual(actual) < MATERIAL_BALANCE_TOLERANCE);

    double compositionTotal = 0.0;
    for (int componentIndex = 0; componentIndex < actual.getPhase(0).getNumberOfComponents(); componentIndex++) {
      assertEquals(expected.getPhase(0).getComponent(componentIndex).getx(),
          actual.getPhase(0).getComponent(componentIndex).getx(), 1.0e-12);
      compositionTotal += actual.getPhase(0).getComponent(componentIndex).getx();
    }
    assertEquals(1.0, compositionTotal, 1.0e-12);
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
