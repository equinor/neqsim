package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashAqueousRootConsistencyTest {
  private static final double[] FEED = { 0.90, 0.10 };

  @Test
  void ordinaryAndMultiphaseFlashesRetainSameStableCubicRoot() {
    assertStableRootConsistency(false);
    assertStableRootConsistency(true);
  }

  private void assertStableRootConsistency(boolean pengRobinson) {
    SystemInterface ordinary = createAndFlash(pengRobinson, false);
    SystemInterface multiphase = createAndFlash(pengRobinson, true);

    assertEquivalentEquilibrium(multiphase, ordinary);
    double firstGibbsEnergy = ordinary.getGibbsEnergy();
    double firstEnthalpy = ordinary.getEnthalpy();

    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.init(3);
    ordinary.initProperties();

    assertEquals(firstGibbsEnergy, ordinary.getGibbsEnergy(), 1.0e-8);
    assertEquals(firstEnthalpy, ordinary.getEnthalpy(), 1.0e-8);
    assertEquivalentEquilibrium(multiphase, ordinary);
  }

  private SystemInterface createAndFlash(boolean pengRobinson, boolean multiphaseCheck) {
    SystemInterface system = pengRobinson ? new SystemPrEos(273.15, 50.0) : new SystemSrkEos(273.15, 50.0);
    system.addComponent("CO2", FEED[0]);
    system.addComponent("water", FEED[1]);
    system.setMixingRule(2);
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    system.initProperties();
    return system;
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual) {
    assertEquals(2, expected.getNumberOfPhases());
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-8);
    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(), 1.0e-8);

    for (int phaseIndex = 0; phaseIndex < expected.getNumberOfPhases(); phaseIndex++) {
      assertEquals(expected.getPhase(phaseIndex).getType(), actual.getPhase(phaseIndex).getType());
      assertEquals(expected.getBeta(phaseIndex), actual.getBeta(phaseIndex), 1.0e-12);
      assertEquals(expected.getPhase(phaseIndex).getDensity(), actual.getPhase(phaseIndex).getDensity(), 1.0e-8);
      assertTrue(actual.getBeta(phaseIndex) > 0.0);
      assertTrue(actual.getBeta(phaseIndex) < 1.0);
      assertTrue(Double.isFinite(actual.getPhase(phaseIndex).getZ()));
      assertTrue(actual.getPhase(phaseIndex).getZ() > 0.0);

      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < expected.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        double expectedComposition = expected.getPhase(phaseIndex).getComponent(componentIndex).getx();
        double actualComposition = actual.getPhase(phaseIndex).getComponent(componentIndex).getx();
        assertEquals(expectedComposition, actualComposition, 1.0e-12);
        compositionTotal += actualComposition;
      }
      assertEquals(1.0, compositionTotal, 1.0e-12);
    }

    assertEquals(1.0, actual.getBeta(0) + actual.getBeta(1), 1.0e-12);
    for (int componentIndex = 0; componentIndex < FEED.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < actual.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += actual.getBeta(phaseIndex) * actual.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(FEED[componentIndex], recoveredFeed, 1.0e-11);
    }
    assertTrue(maximumLogFugacityResidual(actual) < 1.0e-8);
  }

  private double maximumLogFugacityResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < FEED.length; componentIndex++) {
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
