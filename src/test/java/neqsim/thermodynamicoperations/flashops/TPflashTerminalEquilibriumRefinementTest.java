package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashTerminalEquilibriumRefinementTest {
  private static final String[] COMPONENTS = { "nitrogen", "CO2", "methane", "ethane", "propane", "nC10", "water" };
  private static final double[] FEED = { 0.02, 0.05, 0.55, 0.18, 0.12, 0.06, 0.02 };

  @Test
  void boundedFinalSsiRestoresNeutralGasOilEquilibrium() {
    SystemInterface ordinary = createAndFlash(false, false);
    SystemInterface poorGuess = createAndFlash(false, true);
    SystemInterface multiphase = createAndFlash(true, false);

    assertEquals(2, ordinary.getNumberOfPhases());
    assertTrue(ordinary.hasPhaseType(PhaseType.GAS));
    assertTrue(ordinary.hasPhaseType(PhaseType.OIL));
    assertClosure(ordinary);
    assertClosure(poorGuess);
    assertEquivalentEndpoint(ordinary, poorGuess);

    assertEquals(3, multiphase.getNumberOfPhases());
    assertTrue(multiphase.hasPhaseType(PhaseType.AQUEOUS));
    assertClosure(multiphase);
    assertTrue(multiphase.getGibbsEnergy() < ordinary.getGibbsEnergy(),
        "The ordinary two-phase endpoint is a converged metastable split, not the stable three-phase state");

    double referenceGibbsEnergy = ordinary.getGibbsEnergy();
    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.init(1);
    assertEquals(2, ordinary.getNumberOfPhases());
    assertEquals(referenceGibbsEnergy, ordinary.getGibbsEnergy(), 1.0e-8);
    assertClosure(ordinary);
  }

  private SystemInterface createAndFlash(boolean multiphaseCheck, boolean poorGuess) {
    SystemInterface system = new SystemSrkCPAstatoil(275.7756311717352, 20.0);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule(10);
    system.setMultiPhaseCheck(multiphaseCheck);
    if (poorGuess) {
      system.setBeta(1.0e-12);
    }
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private void assertClosure(SystemInterface system) {
    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      double beta = system.getBeta(phaseIndex);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0);
      betaTotal += beta;
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        double moleFraction = system.getPhase(phaseIndex).getComponent(componentIndex).getx();
        assertTrue(Double.isFinite(moleFraction) && moleFraction >= 0.0 && moleFraction <= 1.0);
        compositionTotal += moleFraction;
      }
      assertEquals(1.0, compositionTotal, 1.0e-10);
    }
    assertEquals(1.0, betaTotal, 1.0e-10);
    assertTrue(maximumMaterialResidual(system) < 1.0e-8);
    assertTrue(maximumLogFugacityResidual(system) < 1.0e-8);
  }

  private void assertEquivalentEndpoint(SystemInterface reference, SystemInterface candidate) {
    assertEquals(reference.getNumberOfPhases(), candidate.getNumberOfPhases());
    assertEquals(reference.getGibbsEnergy(), candidate.getGibbsEnergy(), 1.0e-8);
    for (PhaseType phaseType : new PhaseType[] { PhaseType.GAS, PhaseType.OIL }) {
      int referencePhase = reference.getPhaseNumberOfPhase(phaseType.getDesc());
      int candidatePhase = candidate.getPhaseNumberOfPhase(phaseType.getDesc());
      assertEquals(reference.getBeta(referencePhase), candidate.getBeta(candidatePhase), 1.0e-10);
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(reference.getPhase(referencePhase).getComponent(componentIndex).getx(),
            candidate.getPhase(candidatePhase).getComponent(componentIndex).getx(), 1.0e-10);
      }
    }
  }

  private double maximumMaterialResidual(SystemInterface system) {
    double maximum = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recovered = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recovered += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      maximum = Math.max(maximum, Math.abs(FEED[componentIndex] - recovered));
    }
    return maximum;
  }

  private double maximumLogFugacityResidual(SystemInterface system) {
    if (system.getNumberOfPhases() < 2) {
      return 0.0;
    }
    double maximum = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      for (int phaseIndex = 1; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        maximum = Math.max(maximum,
            Math.abs(logFugacity(system, 0, componentIndex) - logFugacity(system, phaseIndex, componentIndex)));
      }
    }
    return maximum;
  }

  private double logFugacity(SystemInterface system, int phaseIndex, int componentIndex) {
    double composition = Math.max(system.getPhase(phaseIndex).getComponent(componentIndex).getx(), Double.MIN_NORMAL);
    double fugacityCoefficient = system.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient();
    return Math.log(composition) + Math.log(fugacityCoefficient);
  }
}
