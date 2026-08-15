package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashSourGasConsistencyTest {
  private static final String[] COMPONENTS = { "methane", "CO2", "H2S" };
  private static final double[] FEED = { 49.88 / 99.97, 9.87 / 99.97, 40.22 / 99.97 };

  @Test
  void ordinaryAndMultiphaseFlashReachSameStableOneOrTwoPhaseState() {
    double[][] conditions = { { 145.0, 10.98 }, { 170.0, 20.96 }, { 170.0, 380.24 }, { 200.0, 50.90 },
        { 210.0, 145.71 }, { 220.0, 100.80 }, { 225.0, 105.79 }, { 250.0, 105.79 }, { 255.0, 105.79 },
        { 270.0, 120.76 }, { 280.0, 120.76 } };

    for (double[] condition : conditions) {
      SystemInterface ordinary = flash(condition[0], condition[1], false, false);
      SystemInterface multiphase = flash(condition[0], condition[1], true, false);

      assertEquivalent(ordinary, multiphase, 2.0e-6, "T=" + condition[0] + " K, P=" + condition[1] + " bara");
    }
  }

  @Test
  void enhancedMultiphaseFlashRepairsInvalidOrCollapsedEndpoints() {
    double[][] conditions = { { 240.0, 100.80 }, { 260.0, 20.96 }, { 285.0, 45.91 } };

    for (double[] condition : conditions) {
      SystemInterface ordinary = flash(condition[0], condition[1], false, false);
      SystemInterface enhanced = flash(condition[0], condition[1], true, true);

      assertEquivalent(ordinary, enhanced, 2.0e-6, "T=" + condition[0] + " K, P=" + condition[1] + " bara");
    }
  }

  @Test
  void ordinaryBoundaryFlashRecoversStableSplitFromColdFeedSeed() {
    SystemInterface ordinary = flash(245.0, 100.80, false, false);
    SystemInterface multiphase = flash(245.0, 100.80, true, false);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquivalent(multiphase, ordinary, 1.0e-10, "cold boundary flash");
    assertEquals(5204.65719950532, ordinary.getGibbsEnergy(), 1.0e-6);
    assertEquals(0.330402477242444, ordinary.getBeta(phaseOrder(ordinary)[0]), 1.0e-10);

    SystemInterface repeatedReference = ordinary.clone();
    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.init(1);
    assertEquivalent(repeatedReference, ordinary, 1.0e-10, "repeated boundary flash");
  }

  @Test
  void ordinaryLiquidLiquidBoundaryRetainsAcceptedCandidateRoot() {
    SystemInterface ordinary = flash(225.0, 95.81, false, false);
    SystemInterface multiphase = flash(225.0, 95.81, true, false);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquivalent(multiphase, ordinary, 1.0e-10, "liquid-liquid root boundary");
    assertEquals(3877.865927361861, ordinary.getGibbsEnergy(), 1.0e-6);
    assertEquals(0.572813112145268, ordinary.getBeta(phaseOrder(ordinary)[0]), 1.0e-10);

    double[][] nearbyConditions = { { 220.0, 95.81 }, { 225.0, 100.80 }, { 230.0, 95.81 } };
    for (double[] condition : nearbyConditions) {
      SystemInterface nearbyOrdinary = flash(condition[0], condition[1], false, false);
      SystemInterface nearbyMultiphase = flash(condition[0], condition[1], true, false);
      assertEquivalent(nearbyMultiphase, nearbyOrdinary, 2.0e-6,
          "near liquid-liquid root boundary T=" + condition[0] + " K, P=" + condition[1] + " bara");
    }

    SystemInterface changedState = flash(225.0, 90.82, false, false);
    changedState.setPressure(95.81, "bara");
    new ThermodynamicOperations(changedState).TPflash();
    changedState.init(1);
    assertEquivalent(multiphase, changedState, 5.0e-6, "changed pressure at liquid-liquid root boundary");

    SystemInterface repeatedReference = changedState.clone();
    new ThermodynamicOperations(changedState).TPflash();
    changedState.init(1);
    assertEquivalent(repeatedReference, changedState, 1.0e-10, "repeated changed-state liquid-liquid boundary");
  }

  private SystemInterface flash(double temperature, double pressure, boolean multiphase, boolean enhanced) {
    SystemInterface system = new SystemPrEos(temperature, pressure);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphase);
    system.setEnhancedMultiPhaseCheck(enhanced);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private void assertEquivalent(SystemInterface reference, SystemInterface candidate, double tolerance,
      String condition) {
    assertEquals(reference.getNumberOfPhases(), candidate.getNumberOfPhases(), condition);
    assertTrue(reference.getNumberOfPhases() <= 2, condition);
    assertClosure(reference, condition + " reference");
    assertClosure(candidate, condition + " candidate");

    Integer[] referenceOrder = phaseOrder(reference);
    Integer[] candidateOrder = phaseOrder(candidate);
    for (int orderedPhase = 0; orderedPhase < referenceOrder.length; orderedPhase++) {
      int referencePhase = referenceOrder[orderedPhase];
      int candidatePhase = candidateOrder[orderedPhase];
      assertEquals(reference.getBeta(referencePhase), candidate.getBeta(candidatePhase), tolerance, condition);
      assertEquals(reference.getPhase(referencePhase).getZ(), candidate.getPhase(candidatePhase).getZ(), tolerance,
          condition);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(reference.getPhase(referencePhase).getComponent(componentIndex).getx(),
            candidate.getPhase(candidatePhase).getComponent(componentIndex).getx(), tolerance, condition);
      }
    }
  }

  private Integer[] phaseOrder(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order,
        Comparator.comparingDouble(phaseIndex -> -system.getPhase(phaseIndex).getComponent("methane").getx()));
    return order;
  }

  private void assertClosure(SystemInterface system, String condition) {
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      betaTotal += system.getBeta(phaseIndex);
    }
    assertEquals(1.0, betaTotal, 1.0e-10, condition);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(FEED[componentIndex], recoveredFeed, 1.0e-10, condition);
    }
    if (system.getNumberOfPhases() == 2) {
      double maximumFugacityResidual = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        double firstLogFugacity = Math.log(system.getPhase(0).getComponent(componentIndex).getx())
            + Math.log(system.getPhase(0).getComponent(componentIndex).getFugacityCoefficient());
        double secondLogFugacity = Math.log(system.getPhase(1).getComponent(componentIndex).getx())
            + Math.log(system.getPhase(1).getComponent(componentIndex).getFugacityCoefficient());
        maximumFugacityResidual = Math.max(maximumFugacityResidual, Math.abs(firstLogFugacity - secondLogFugacity));
      }
      assertTrue(maximumFugacityResidual < 1.0e-8,
          condition + " maximum log fugacity residual was " + maximumFugacityResidual);
    }
  }
}
