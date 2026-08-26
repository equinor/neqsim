package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification coverage for repeated and changed-state neutral SRK-CPA three-phase flashes.
 *
 * <p>
 * {@code Component.K} is a single scalar per (phase, component), so after a GAS/OIL/AQUEOUS
 * equilibrium it describes only one phase pair. Reusing those values in the next two-phase loop
 * is blind to water and MEG partitioning into the third phase. These tests qualify the complete
 * equilibrium returned by the warm-start guard rather than checking only one phase fraction.
 * </p>
 *
 * <p>
 * The synthetic natural-gas/water/MEG case is a numerical regression, not independent
 * experimental validation of the CPA parameters.
 * </p>
 */
class TPflashWarmStartThreePhaseTest {
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double PHASE_FRACTION_TOLERANCE = 1.0e-12;
  private static final double COMPOSITION_TOLERANCE = 1.0e-12;
  private static final double EQUIVALENCE_TOLERANCE = 1.0e-10;

  private boolean originalWarmStart;

  @BeforeEach
  void enableWarmStart() {
    originalWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    ThermodynamicModelSettings.setUseWarmStartKValues(true);
  }

  @AfterEach
  void restoreWarmStart() {
    ThermodynamicModelSettings.setUseWarmStartKValues(originalWarmStart);
  }

  @Test
  void repeatedThreePhaseFlashIsFullyConvergedAndDeterministic() {
    SystemInterface fluid = buildCpaFluid(298.15, 60.0);
    flash(fluid);
    assertThreePhaseEquilibrium(fluid);

    SystemInterface firstEquilibrium = fluid.clone();
    flash(fluid);

    assertThreePhaseEquilibrium(fluid);
    assertEquivalentEquilibrium(firstEquilibrium, fluid);
  }

  @Test
  void perturbedRecycleStepMatchesFreshColdFlashAndReturnState() {
    SystemInterface changedState = buildCpaFluid(298.15, 60.0);
    flash(changedState);

    changedState.setTemperature(300.15);
    changedState.setPressure(59.5);
    flash(changedState);

    SystemInterface freshChangedState = coldFlash(buildCpaFluid(300.15, 59.5));
    assertThreePhaseEquilibrium(changedState);
    assertThreePhaseEquilibrium(freshChangedState);
    assertEquivalentEquilibrium(freshChangedState, changedState);

    changedState.setTemperature(298.15);
    changedState.setPressure(60.0);
    flash(changedState);

    SystemInterface freshOriginalState = coldFlash(buildCpaFluid(298.15, 60.0));
    assertThreePhaseEquilibrium(changedState);
    assertEquivalentEquilibrium(freshOriginalState, changedState);
  }

  @Test
  void poorPhaseFractionGuessRecoversFreshThreePhaseEquilibrium() {
    SystemInterface poorGuess = buildCpaFluid(298.15, 60.0);
    poorGuess.init(0);
    poorGuess.setBeta(0, 1.0e-10);
    poorGuess.setBeta(1, 1.0 - 1.0e-10);
    flash(poorGuess);

    SystemInterface freshReference = coldFlash(buildCpaFluid(298.15, 60.0));
    assertThreePhaseEquilibrium(poorGuess);
    assertEquivalentEquilibrium(freshReference, poorGuess);
  }

  @Test
  void warmStartFlagIsRestoredAfterThreePhaseFlash() {
    SystemInterface fluid = buildCpaFluid(298.15, 60.0);
    assertTrue(ThermodynamicModelSettings.isUseWarmStartKValues(),
        "test precondition: warm-start enabled by @BeforeEach");

    flash(fluid);

    assertTrue(ThermodynamicModelSettings.isUseWarmStartKValues(),
        "warm-start flag must be restored after TPflash on a three-phase system");
  }

  private SystemInterface buildCpaFluid(double temperature, double pressure) {
    SystemInterface fluid = new SystemSrkCPAstatoil(temperature, pressure);
    fluid.addComponent("nitrogen", 1.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-hexane", 1.0);
    fluid.addComponent("nC10", 1.0);
    fluid.addComponent("MEG", 2.0);
    fluid.addComponent("water", 5.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private void flash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
  }

  private SystemInterface coldFlash(SystemInterface fluid) {
    boolean previousWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    ThermodynamicModelSettings.setUseWarmStartKValues(false);
    try {
      flash(fluid);
      return fluid;
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
    }
  }

  private void assertThreePhaseEquilibrium(SystemInterface fluid) {
    assertEquals(3, fluid.getNumberOfPhases(), "expected GAS/OIL/AQUEOUS equilibrium");
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.OIL));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));

    double betaTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      double beta = fluid.getBeta(phaseIndex);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0,
          "phase fraction must be finite and bounded");
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (int componentIndex = 0;
          componentIndex < fluid.getPhase(phaseIndex).getNumberOfComponents();
          componentIndex++) {
        double composition = fluid.getPhase(phaseIndex).getComponent(componentIndex).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
            "phase composition must be finite and bounded");
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, COMPOSITION_TOLERANCE,
          "phase composition must be normalized");
    }
    assertEquals(1.0, betaTotal, PHASE_FRACTION_TOLERANCE,
        "phase fractions must be normalized");

    double materialResidual = maximumComponentMaterialBalanceResidual(fluid);
    assertTrue(materialResidual < MATERIAL_BALANCE_TOLERANCE,
        "maximum component material-balance residual was " + materialResidual);

    double fugacityResidual = maximumLogFugacityResidual(fluid);
    assertTrue(fugacityResidual < FUGACITY_TOLERANCE,
        "maximum cross-phase log-fugacity residual was " + fugacityResidual);
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), "Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), "enthalpy");

    for (int expectedPhase = 0; expectedPhase < expected.getNumberOfPhases(); expectedPhase++) {
      PhaseType phaseType = expected.getPhase(expectedPhase).getType();
      int actualPhase = findPhase(actual, phaseType);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase),
          EQUIVALENCE_TOLERANCE, "phase fraction for " + phaseType);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(),
          1.0e-8, "compressibility factor for " + phaseType);
      assertEquals(expected.getPhase(expectedPhase).getDensity(),
          actual.getPhase(actualPhase).getDensity(),
          Math.max(1.0e-8, 1.0e-8 * Math.abs(expected.getPhase(expectedPhase).getDensity())),
          "density for " + phaseType);

      for (int componentIndex = 0;
          componentIndex < expected.getPhase(expectedPhase).getNumberOfComponents();
          componentIndex++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(componentIndex).getx(),
            actual.getPhase(actualPhase).getComponent(componentIndex).getx(),
            EQUIVALENCE_TOLERANCE,
            "composition for component " + componentIndex + " in " + phaseType);
      }
    }
  }

  private void assertExtensiveEquals(double expected, double actual, String property) {
    assertEquals(expected, actual, Math.max(1.0e-6, 1.0e-8 * Math.abs(expected)), property);
  }

  private int findPhase(SystemInterface fluid, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      if (fluid.getPhase(phaseIndex).getType() == phaseType) {
        return phaseIndex;
      }
    }
    throw new AssertionError("missing phase " + phaseType);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface fluid) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0;
        componentIndex < fluid.getPhase(0).getNumberOfComponents();
        componentIndex++) {
      double recoveredFeed = 0.0;
      for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
        recoveredFeed +=
            fluid.getBeta(phaseIndex)
                * fluid.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      maximumResidual =
          Math.max(maximumResidual,
              Math.abs(fluid.getPhase(0).getComponent(componentIndex).getz() - recoveredFeed));
    }
    return maximumResidual;
  }

  private double maximumLogFugacityResidual(SystemInterface fluid) {
    double maximumResidual = 0.0;
    for (int componentIndex = 0;
        componentIndex < fluid.getPhase(0).getNumberOfComponents();
        componentIndex++) {
      double referenceLogFugacity = componentLogFugacity(fluid, 0, componentIndex);
      for (int phaseIndex = 1; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
        maximumResidual =
            Math.max(maximumResidual,
                Math.abs(referenceLogFugacity
                    - componentLogFugacity(fluid, phaseIndex, componentIndex)));
      }
    }
    return maximumResidual;
  }

  private double componentLogFugacity(
      SystemInterface fluid, int phaseIndex, int componentIndex) {
    double composition = fluid.getPhase(phaseIndex).getComponent(componentIndex).getx();
    double fugacityCoefficient =
        fluid.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient();
    assertTrue(Double.isFinite(fugacityCoefficient) && fugacityCoefficient > 0.0,
        "fugacity coefficient must be finite and positive");
    return Math.log(Math.max(composition, Double.MIN_NORMAL))
        + Math.log(fugacityCoefficient);
  }
}
