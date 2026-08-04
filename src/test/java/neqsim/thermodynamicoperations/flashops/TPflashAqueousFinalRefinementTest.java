package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashAqueousFinalRefinementTest {
  private static final String[] COMPONENTS = { "CO2", "methane", "ethane", "water" };
  private static final double[] REGRESSION_FEED = { 0.543865141103918, 0.2937712952303271, 0.07010605470616459,
      0.09225750895959021 };
  private static final double[] CO2_RICH_FEED = { 0.75, 0.15, 0.02, 0.08 };

  @Test
  void finalRefinementRestoresAqueousFugacityEquality() {
    SystemInterface ordinary = createAndFlash(REGRESSION_FEED, 275.7756311717352, 74.76182177756704, false);
    SystemInterface multiphase = createAndFlash(REGRESSION_FEED, 275.7756311717352, 74.76182177756704, true);

    assertEquivalentBalancedEquilibrium(ordinary, multiphase, REGRESSION_FEED);
    assertTrue(maximumLogFugacityResidual(ordinary) < 1.0e-8);
    assertTrue(maximumLogFugacityResidual(multiphase) < 1.0e-8);
  }

  @Test
  void finalRefinementRestoresAqueousMaterialBalance() {
    SystemInterface ordinary = createAndFlash(CO2_RICH_FEED, 250.70511924703197, 149.52364355513407, false);
    SystemInterface multiphase = createAndFlash(CO2_RICH_FEED, 250.70511924703197, 149.52364355513407, true);

    assertEquivalentBalancedEquilibrium(ordinary, multiphase, CO2_RICH_FEED);
    assertTrue(maximumLogFugacityResidual(ordinary) < 1.0e-8);
    assertTrue(maximumLogFugacityResidual(multiphase) < 1.0e-8);
  }

  /** A candidate whose beta solve changes a selected phase type must be rejected before it can replace the snapshot. */
  @Test
  void activeSetGuardRejectsPhaseTypeMutation() {
    SystemInterface candidate = new SystemSrkEos(275.0, 75.0);
    candidate.addComponent("CO2", 0.8);
    candidate.addComponent("water", 0.2);
    candidate.setNumberOfPhases(2);
    candidate.getPhase(0).setType(PhaseType.GAS);
    candidate.getPhase(1).setType(PhaseType.AQUEOUS);

    PhaseType[] referenceTypes = new PhaseType[] { candidate.getPhase(0).getType(), candidate.getPhase(1).getType() };
    assertTrue(TPflash.preservesTwoPhaseActiveSet(candidate, referenceTypes));

    candidate.getPhase(0).setType(PhaseType.LIQUID);

    assertFalse(TPflash.preservesTwoPhaseActiveSet(candidate, referenceTypes),
        "A GAS-to-LIQUID mutation must not satisfy the selected active-set contract");
  }

  private SystemInterface createAndFlash(double[] feed, double temperature, double pressure, boolean multiphaseCheck) {
    SystemInterface system = new SystemSrkEos(temperature, pressure);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], feed[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private void assertEquivalentBalancedEquilibrium(SystemInterface ordinary, SystemInterface multiphase,
      double[] feed) {
    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    int ordinaryAqueousPhase = phaseIndexOf(ordinary, PhaseType.AQUEOUS);
    int multiphaseAqueousPhase = phaseIndexOf(multiphase, PhaseType.AQUEOUS);
    assertTrue(ordinaryAqueousPhase >= 0);
    assertTrue(multiphaseAqueousPhase >= 0);
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);

    int ordinaryOtherPhase = ordinaryAqueousPhase == 0 ? 1 : 0;
    int multiphaseOtherPhase = multiphaseAqueousPhase == 0 ? 1 : 0;
    assertEquivalentPhaseState(ordinary, ordinaryAqueousPhase, multiphase, multiphaseAqueousPhase);
    assertEquivalentPhaseState(ordinary, ordinaryOtherPhase, multiphase, multiphaseOtherPhase);

    assertEquals(1.0, ordinary.getBeta(0) + ordinary.getBeta(1), 1.0e-12);
    assertEquals(1.0, multiphase.getBeta(0) + multiphase.getBeta(1), 1.0e-12);
    assertTrue(maximumMaterialBalanceResidual(ordinary, feed) < 1.0e-8);
    assertTrue(maximumMaterialBalanceResidual(multiphase, feed) < 1.0e-8);
  }

  private int phaseIndexOf(SystemInterface system, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getType() == phaseType) {
        return phaseIndex;
      }
    }
    return -1;
  }

  private void assertEquivalentPhaseState(SystemInterface ordinary, int ordinaryPhaseIndex, SystemInterface multiphase,
      int multiphasePhaseIndex) {
    assertEquals(multiphase.getPhase(multiphasePhaseIndex).getType(), ordinary.getPhase(ordinaryPhaseIndex).getType());
    assertEquals(multiphase.getBeta(multiphasePhaseIndex), ordinary.getBeta(ordinaryPhaseIndex), 1.0e-12);
    double ordinaryCompositionTotal = 0.0;
    double multiphaseCompositionTotal = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double ordinaryMoleFraction = ordinary.getPhase(ordinaryPhaseIndex).getComponent(componentIndex).getx();
      double multiphaseMoleFraction = multiphase.getPhase(multiphasePhaseIndex).getComponent(componentIndex).getx();
      assertTrue(Double.isFinite(ordinaryMoleFraction) && ordinaryMoleFraction >= 0.0 && ordinaryMoleFraction <= 1.0);
      assertTrue(
          Double.isFinite(multiphaseMoleFraction) && multiphaseMoleFraction >= 0.0 && multiphaseMoleFraction <= 1.0);
      ordinaryCompositionTotal += ordinaryMoleFraction;
      multiphaseCompositionTotal += multiphaseMoleFraction;
      assertEquals(multiphaseMoleFraction, ordinaryMoleFraction, 1.0e-12);
    }
    assertEquals(1.0, ordinaryCompositionTotal, 1.0e-12);
    assertEquals(1.0, multiphaseCompositionTotal, 1.0e-12);
  }

  private double maximumMaterialBalanceResidual(SystemInterface system, double[] feed) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(feed[componentIndex] - recoveredFeed));
    }
    return maximumResidual;
  }

  private double maximumLogFugacityResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
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
