package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.Component;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TPflashAqueousMaterialBalanceRefinementTest {
  private static final String[] COMPONENTS = { "methane", "nC10", "water" };
  private static final double[] FEED = { 0.20, 0.60, 0.20 };

  @Test
  void ordinaryFlashRejectsNonConservativeAqueousEndpoint() {
    SystemInterface ordinary = createAndFlash(false);
    SystemInterface multiphase = createAndFlash(true);

    assertEquivalentEquilibrium(multiphase, ordinary);
    double firstGibbsEnergy = ordinary.getGibbsEnergy();

    new ThermodynamicOperations(ordinary).TPflash();
    ordinary.init(1);

    assertEquals(firstGibbsEnergy, ordinary.getGibbsEnergy(), 1.0e-8);
    assertEquivalentEquilibrium(multiphase, ordinary);
  }

  @Test
  void nonFiniteCandidatesCannotPassFeasibilityGate() throws Exception {
    Method materialBalanceMethod = TPflash.class.getDeclaredMethod("maximumComponentMaterialBalanceResidual",
        SystemInterface.class);
    materialBalanceMethod.setAccessible(true);
    Method feasibilityMethod = TPflash.class.getDeclaredMethod("isBalancedEquilibriumCandidate", SystemInterface.class);
    feasibilityMethod.setAccessible(true);

    SystemInterface invalidPhaseFractionCandidate = createAndFlash(true);
    invalidPhaseFractionCandidate.setBeta(0, Double.NaN);
    TPflash flash = new TPflash(invalidPhaseFractionCandidate);

    assertEquals(Double.POSITIVE_INFINITY,
        ((Double) materialBalanceMethod.invoke(flash, invalidPhaseFractionCandidate)).doubleValue());
    assertFalse(((Boolean) feasibilityMethod.invoke(flash, invalidPhaseFractionCandidate)).booleanValue());

    for (double invalidComposition : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
      SystemInterface invalidCompositionCandidate = createAndFlash(true);
      setRawPhaseComposition(invalidCompositionCandidate, 0, 0, invalidComposition);
      flash = new TPflash(invalidCompositionCandidate);

      assertEquals(Double.POSITIVE_INFINITY,
          ((Double) materialBalanceMethod.invoke(flash, invalidCompositionCandidate)).doubleValue());
      assertFalse(((Boolean) feasibilityMethod.invoke(flash, invalidCompositionCandidate)).booleanValue());
    }

    for (double invalidComposition : new double[] { -1.0e-6, 1.0 + 1.0e-6 }) {
      SystemInterface invalidCompositionCandidate = createAndFlash(true);
      setRawPhaseComposition(invalidCompositionCandidate, 0, 0, invalidComposition);
      flash = new TPflash(invalidCompositionCandidate);

      assertFalse(((Boolean) feasibilityMethod.invoke(flash, invalidCompositionCandidate)).booleanValue());
    }
  }

  @Test
  void nonConservativeReferenceCannotUseGibbsOnlyAcceptance() throws Exception {
    Method acceptanceMethod = TPflash.class.getDeclaredMethod("shouldAcceptWaterRichCandidate", SystemInterface.class,
        double.class, boolean.class);
    acceptanceMethod.setAccessible(true);

    SystemInterface candidate = createAndFlash(true);
    double betaShift = 0.1 * Math.min(candidate.getBeta(0), candidate.getBeta(1));
    candidate.setBeta(0, candidate.getBeta(0) + betaShift);
    candidate.setBeta(1, candidate.getBeta(1) - betaShift);
    assertTrue(maximumComponentMaterialBalanceResidual(candidate) > 1.0e-8);

    TPflash flash = new TPflash(candidate);
    double higherReferenceGibbs = candidate.getGibbsEnergy()
        + Math.max(1.0, Math.abs(candidate.getGibbsEnergy()) * 1.0e-3);
    assertTrue(((Boolean) acceptanceMethod.invoke(flash, candidate, higherReferenceGibbs, false)).booleanValue());
    assertFalse(((Boolean) acceptanceMethod.invoke(flash, candidate, higherReferenceGibbs, true)).booleanValue());
  }

  private void setRawPhaseComposition(SystemInterface system, int phaseIndex, int componentIndex, double value)
      throws Exception {
    Field compositionField = Component.class.getDeclaredField("x");
    compositionField.setAccessible(true);
    Object component = system.getPhase(phaseIndex).getComponent(componentIndex);
    compositionField.setDouble(component, value);
    assertEquals(value, system.getPhase(phaseIndex).getComponent(componentIndex).getx());
  }

  private SystemInterface createAndFlash(boolean multiphaseCheck) {
    SystemInterface system = new SystemSrkEos(230.0, 50.0);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      system.addComponent(COMPONENTS[componentIndex], FEED[componentIndex]);
    }
    system.setMixingRule(2);
    system.setMultiPhaseCheck(multiphaseCheck);
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual) {
    assertEquals(2, expected.getNumberOfPhases());
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(PhaseType.OIL, actual.getPhase(0).getType());
    assertEquals(PhaseType.AQUEOUS, actual.getPhase(1).getType());
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), 1.0e-8);

    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < expected.getNumberOfPhases(); phaseIndex++) {
      assertEquals(expected.getPhase(phaseIndex).getType(), actual.getPhase(phaseIndex).getType());
      assertEquals(expected.getBeta(phaseIndex), actual.getBeta(phaseIndex), 1.0e-12);
      assertTrue(actual.getBeta(phaseIndex) > 0.0);
      assertTrue(actual.getBeta(phaseIndex) < 1.0);
      assertTrue(Double.isFinite(actual.getPhase(phaseIndex).getZ()));
      assertTrue(actual.getPhase(phaseIndex).getZ() > 0.0);
      betaTotal += actual.getBeta(phaseIndex);

      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
        assertEquals(expected.getPhase(phaseIndex).getComponent(componentIndex).getx(),
            actual.getPhase(phaseIndex).getComponent(componentIndex).getx(), 1.0e-12);
        compositionTotal += actual.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(1.0, compositionTotal, 1.0e-12);
    }
    assertEquals(1.0, betaTotal, 1.0e-12);
    assertTrue(maximumComponentMaterialBalanceResidual(actual) < 1.0e-8);
    assertTrue(maximumLogFugacityResidual(actual) < 1.0e-8);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed += system.getBeta(phaseIndex) * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(FEED[componentIndex] - recoveredFeed));
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
