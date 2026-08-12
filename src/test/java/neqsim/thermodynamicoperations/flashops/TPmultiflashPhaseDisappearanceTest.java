package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPmultiflashPhaseDisappearanceTest {
  private static final String[] COMPONENTS = { "CO2", "methane", "ethane", "water" };
  private static final double[] FEED = { 0.543865141103918, 0.2937712952303271, 0.07010605470616459,
      0.09225750895959021 };
  private static final String[] TRACE_DUPLICATE_COMPONENTS = { "nitrogen", "CO2", "methane", "ethane", "propane",
      "nC10", "water" };
  private static final double[] TRACE_DUPLICATE_FEED = { 0.01, 0.05, 0.70, 0.08, 0.04, 0.02, 0.10 };

  @Test
  void stalledThreePhaseTrialReturnsStableTwoPhaseEndpoint() {
    SystemInterface ordinary = createAndFlash(false);
    SystemInterface multiphase = createAndFlash(true);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertFlashClosure(ordinary);
    assertFlashClosure(multiphase);
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      assertEquals(multiphase.getBeta(phaseIndex), ordinary.getBeta(phaseIndex), 1.0e-12);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(multiphase.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            ordinary.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-12);
      }
    }

    double firstGibbsEnergy = multiphase.getGibbsEnergy();
    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(1);
    assertEquals(firstGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-8);
    assertFlashClosure(multiphase);
  }

  @Test
  void traceDuplicatePhaseDisappearsForCubicEos() {
    SystemInterface ordinary = createTraceDuplicateCase(false);
    SystemInterface multiphase = createTraceDuplicateCase(true);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(2, multiphase.getNumberOfPhases());
    assertTrue(ordinary.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(multiphase.hasPhaseType(PhaseType.AQUEOUS));
    assertFlashClosure(ordinary, TRACE_DUPLICATE_COMPONENTS, TRACE_DUPLICATE_FEED);
    assertFlashClosure(multiphase, TRACE_DUPLICATE_COMPONENTS, TRACE_DUPLICATE_FEED);
    assertEquivalentPhaseState(ordinary, multiphase, PhaseType.AQUEOUS, TRACE_DUPLICATE_COMPONENTS.length);
    assertEquivalentPhaseState(ordinary, multiphase, PhaseType.OIL, TRACE_DUPLICATE_COMPONENTS.length);
    assertEquals(multiphase.getGibbsEnergy(), ordinary.getGibbsEnergy(), 1.0e-8);

    double firstGibbsEnergy = multiphase.getGibbsEnergy();
    new ThermodynamicOperations(multiphase).TPflash();
    multiphase.init(1);
    assertEquals(2, multiphase.getNumberOfPhases());
    assertEquals(firstGibbsEnergy, multiphase.getGibbsEnergy(), 1.0e-8);
    assertFlashClosure(multiphase, TRACE_DUPLICATE_COMPONENTS, TRACE_DUPLICATE_FEED);
  }

  private SystemInterface createAndFlash(boolean multiphaseCheck) {
    SystemInterface system = new SystemPrEos(250.70511924703197, 74.76182177756704);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private SystemInterface createTraceDuplicateCase(boolean multiphaseCheck) {
    SystemInterface system = new SystemSrkEos(250.70511924703197, 300.0);
    for (int componentIndex = 0; componentIndex < TRACE_DUPLICATE_COMPONENTS.length; componentIndex++) {
      system.addComponent(TRACE_DUPLICATE_COMPONENTS[componentIndex], TRACE_DUPLICATE_FEED[componentIndex]);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private void assertFlashClosure(SystemInterface system) {
    assertFlashClosure(system, COMPONENTS, FEED);
  }

  private void assertFlashClosure(SystemInterface system, String[] components, double[] feed) {
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      betaTotal += system.getBeta(phaseIndex);
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < components.length; componentIndex++) {
        double moleFraction = system.getPhase(phaseIndex).getComponent(componentIndex).getx();
        assertTrue(Double.isFinite(moleFraction));
        assertTrue(moleFraction >= 0.0 && moleFraction <= 1.0);
        compositionTotal += moleFraction;
      }
      assertEquals(1.0, compositionTotal, 1.0e-12);
    }
    assertEquals(1.0, betaTotal, 1.0e-12);

    double maximumLogFugacityResidual = 0.0;
    for (int componentIndex = 0; componentIndex < components.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(feed[componentIndex], recoveredFeed, 1.0e-10);

      double firstLogFugacity = logFugacity(system, 0, componentIndex);
      double secondLogFugacity = logFugacity(system, 1, componentIndex);
      maximumLogFugacityResidual = Math.max(maximumLogFugacityResidual, Math.abs(firstLogFugacity - secondLogFugacity));
    }
    assertTrue(maximumLogFugacityResidual < 1.0e-8);
  }

  private void assertEquivalentPhaseState(SystemInterface ordinary, SystemInterface multiphase, PhaseType phaseType,
      int numberOfComponents) {
    int ordinaryPhase = ordinary.getPhaseNumberOfPhase(phaseType.getDesc());
    int multiphasePhase = multiphase.getPhaseNumberOfPhase(phaseType.getDesc());
    assertEquals(multiphase.getBeta(multiphasePhase), ordinary.getBeta(ordinaryPhase), 1.0e-12);
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      assertEquals(multiphase.getPhase(multiphasePhase).getComponent(componentIndex).getx(),
          ordinary.getPhase(ordinaryPhase).getComponent(componentIndex).getx(), 1.0e-12);
    }
  }

  private double logFugacity(SystemInterface system, int phaseIndex, int componentIndex) {
    double composition = system.getPhase(phaseIndex).getComponent(componentIndex).getx();
    double fugacityCoefficient = system.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient();
    return Math.log(Math.max(composition, Double.MIN_NORMAL)) + Math.log(fugacityCoefficient);
  }
}
