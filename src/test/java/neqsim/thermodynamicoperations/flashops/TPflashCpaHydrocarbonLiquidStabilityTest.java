package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashCpaHydrocarbonLiquidStabilityTest {
  private static final String[] COMPONENTS = { "methane", "ethane", "propane", "n-heptane", "nC10", "water" };
  private static final double[] BASE_FEED = { 0.12, 0.03, 0.03, 0.08, 0.09, 0.65 };

  @Test
  void ordinaryHighWaterCpaFlashRecoversLowerGibbsOilAqueousState() {
    SystemInterface ordinary = createAndFlash(300.0, 200.0, 0.65, false, false);
    SystemInterface multiphase = createAndFlash(300.0, 200.0, 0.65, true, false);
    SystemInterface poorGuess = createAndFlash(300.0, 200.0, 0.65, false, true);

    assertOilAqueousEquilibrium(ordinary);
    assertEquals(0.3488579645, ordinary.getBeta(findPhase(ordinary, PhaseType.OIL)), 1.0e-10);
    assertEquals(-5702.35947620, ordinary.getGibbsEnergy(), 1.0e-6);
    assertEquivalentEquilibrium(multiphase, ordinary);
    assertEquivalentEquilibrium(ordinary, poorGuess);

    SystemInterface repeatedReference = ordinary.clone();
    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.init(3);
    assertEquivalentEquilibrium(repeatedReference, ordinary);
  }

  @Test
  void nearbyStatesCompositionsAndChangedPressureRemainConsistent() {
    double[][] conditions = { { 280.0, 100.0 }, { 280.0, 200.0 }, { 300.0, 150.0 }, { 300.0, 175.0 }, { 313.15, 200.0 },
        { 325.0, 225.0 }, { 350.0, 225.0 } };
    for (double[] condition : conditions) {
      SystemInterface ordinary = createAndFlash(condition[0], condition[1], 0.65, false, false);
      SystemInterface multiphase = createAndFlash(condition[0], condition[1], 0.65, true, false);
      assertOilAqueousEquilibrium(ordinary);
      assertEquivalentEquilibrium(multiphase, ordinary);
    }

    for (double waterFraction : new double[] { 0.50, 0.60, 0.65, 0.70, 0.80 }) {
      SystemInterface ordinary = createAndFlash(300.0, 200.0, waterFraction, false, false);
      SystemInterface multiphase = createAndFlash(300.0, 200.0, waterFraction, true, false);
      assertOilAqueousEquilibrium(ordinary);
      assertEquivalentEquilibrium(multiphase, ordinary);
    }

    SystemInterface changedState = createAndFlash(300.0, 100.0, 0.65, false, false);
    changedState.setPressure(200.0, "bara");
    new ThermodynamicOperations(changedState).TPflash();
    changedState.init(3);
    SystemInterface target = createAndFlash(300.0, 200.0, 0.65, true, false);
    assertEquivalentEquilibrium(target, changedState);

    changedState.setPressure(100.0, "bara");
    new ThermodynamicOperations(changedState).TPflash();
    changedState.init(3);
    SystemInterface reverseTarget = createAndFlash(300.0, 100.0, 0.65, true, false);
    assertEquivalentEquilibrium(reverseTarget, changedState);
  }

  @Test
  void stablePolarAqueousEndpointOutsideHydrocarbonScreenIsUnchanged() {
    SystemInterface ordinary = new SystemSrkCPAstatoil(300.0, 200.0);
    ordinary.addComponent("methanol", 0.35);
    ordinary.addComponent("water", 0.65);
    ordinary.setMixingRule(10);
    ordinary.setMultiPhaseCheck(false);
    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.init(3);

    SystemInterface multiphase = ordinary.clone();
    multiphase.setMultiPhaseCheck(true);
    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(3);

    assertEquals(1, ordinary.getNumberOfPhases());
    assertEquals(PhaseType.AQUEOUS, ordinary.getPhase(0).getType());
    assertEquivalentEquilibrium(ordinary, multiphase);

    SystemInterface repeatedReference = ordinary.clone();
    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.init(3);
    assertEquivalentEquilibrium(repeatedReference, ordinary);
  }

  private SystemInterface createAndFlash(double temperature, double pressure, double waterFraction,
      boolean multiphaseCheck, boolean poorGuess) {
    SystemInterface system = new SystemSrkCPAstatoil(temperature, pressure);
    double hydrocarbonScale = (1.0 - waterFraction) / (1.0 - BASE_FEED[BASE_FEED.length - 1]);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length - 1; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], BASE_FEED[componentIndex] * hydrocarbonScale);
    }
    system.addComponent("water", waterFraction);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(multiphaseCheck);
    if (poorGuess) {
      system.setBeta(0, 1.0e-10);
      system.setBeta(1, 1.0 - 1.0e-10);
    }
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private void assertOilAqueousEquilibrium(SystemInterface system) {
    assertEquals(2, system.getNumberOfPhases());
    assertTrue(system.hasPhaseType(PhaseType.OIL));
    assertTrue(system.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(maximumComponentMaterialBalanceResidual(system) < 1.0e-10);
    assertTrue(maximumLogFugacityResidual(system) < 1.0e-8);

    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      double beta = system.getBeta(phaseIndex);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0);
      betaTotal += beta;
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        double composition = system.getPhase(phaseIndex).getComponent(componentIndex).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0);
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, 1.0e-10);
    }
    assertEquals(1.0, betaTotal, 1.0e-12);
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 2.0e-6);
    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(), 2.0e-6);
    for (int expectedPhase = 0; expectedPhase < expected.getNumberOfPhases(); expectedPhase++) {
      PhaseType phaseType = expected.getPhase(expectedPhase).getType();
      int actualPhase = findPhase(actual, phaseType);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), 1.0e-10);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), 1.0e-10);
      assertEquals(expected.getPhase(expectedPhase).getDensity(), actual.getPhase(actualPhase).getDensity(), 1.0e-7);
      for (int componentIndex = 0; componentIndex < expected.getPhase(expectedPhase)
          .getNumberOfComponents(); componentIndex++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(componentIndex).getx(),
            actual.getPhase(actualPhase).getComponent(componentIndex).getx(), 1.0e-10);
      }
    }
  }

  private int findPhase(SystemInterface system, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getType() == phaseType) {
        return phaseIndex;
      }
    }
    throw new AssertionError("Missing phase " + phaseType);
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
    if (system.getNumberOfPhases() < 2) {
      return 0.0;
    }
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
