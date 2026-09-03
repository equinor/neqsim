package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification tests for vapour appearance in water-rich neutral hydrocarbon feeds.
 *
 * <p>
 * The synthetic methane-through-n-octane feed is a numerical regression, not an experimental PVT match. It qualifies
 * the guarded fresh-estimate restart used when a water-dominated overall composition hides the hydrocarbon vapour
 * stationary point from the ordinary multiphase stability trials.
 * </p>
 */
class TPmultiflashWaterRichVapourTest {
  private static final String[] HYDROCARBON_NAMES = { "methane", "ethane", "propane", "n-butane", "n-pentane",
      "n-hexane", "n-heptane", "n-octane" };
  private static final double[] HYDROCARBON_FRACTIONS = { 0.55, 0.08, 0.05, 0.03, 0.02, 0.02, 0.10, 0.15 };
  private static final double REFERENCE_PRESSURE_BARA = 45.62;
  private static final double REFERENCE_TEMPERATURE_K = 273.15 + 30.8;
  private static final double REFERENCE_WATER_FRACTION = 0.83;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double NORMALIZATION_TOLERANCE = 1.0e-12;

  /** Qualifies the full historical SRK water-cut range with strict equilibrium gates. */
  @Test
  void srkWaterCutMatrixHasClosedThreePhaseEquilibrium() {
    double[] waterFractions = { 0.50, 0.70, 0.76, 0.78, 0.80, 0.83, 0.90, 0.95 };
    for (double waterFraction : waterFractions) {
      SystemInterface fluid = flash(false, waterFraction, REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA, true);
      assertClosedThreePhaseEquilibrium(fluid, "SRK water fraction " + waterFraction);
    }
  }

  /** Qualifies nearby pressures with both supported neutral cubic equations of state. */
  @Test
  void srkAndPrNearbyPressureStatesRemainClosedAndThreePhase() {
    for (boolean pengRobinson : new boolean[] { false, true }) {
      String model = pengRobinson ? "PR" : "SRK";
      for (double pressure : new double[] { 44.62, REFERENCE_PRESSURE_BARA, 46.62 }) {
        SystemInterface fluid = flash(pengRobinson, REFERENCE_WATER_FRACTION, REFERENCE_TEMPERATURE_K, pressure, true);
        assertClosedThreePhaseEquilibrium(fluid, model + " at " + pressure + " bara");
      }
    }
  }

  /**
   * A poor beta estimate and an ordinary two-phase warm state must recover the same multiphase endpoint as a fresh
   * public TP flash.
   */
  @Test
  void poorInitializationAndExplicitMultiphaseRestartRecoverFreshReference() {
    for (boolean pengRobinson : new boolean[] { false, true }) {
      String model = pengRobinson ? "PR" : "SRK";
      SystemInterface reference = flash(pengRobinson, REFERENCE_WATER_FRACTION, REFERENCE_TEMPERATURE_K,
          REFERENCE_PRESSURE_BARA, true);

      SystemInterface poorGuess = buildFluid(pengRobinson, REFERENCE_WATER_FRACTION, REFERENCE_TEMPERATURE_K,
          REFERENCE_PRESSURE_BARA, true);
      poorGuess.init(0);
      poorGuess.setBeta(0, 1.0e-12);
      poorGuess.setBeta(1, 1.0 - 1.0e-12);
      runPublicFlash(poorGuess);
      assertEquivalentEquilibrium(reference, poorGuess, 1.0e-8, model + " poor beta initialization");

      SystemInterface explicitMultiphase = buildFluid(pengRobinson, REFERENCE_WATER_FRACTION, REFERENCE_TEMPERATURE_K,
          REFERENCE_PRESSURE_BARA, false);
      runPublicFlash(explicitMultiphase);
      explicitMultiphase.setMultiPhaseCheck(true);
      new TPmultiflash(explicitMultiphase, false).run();
      explicitMultiphase.init(3);
      assertEquivalentEquilibrium(reference, explicitMultiphase, 1.0e-8,
          model + " ordinary-to-explicit-multiphase recovery");
    }
  }

  /** Reused changed and returned states must match fresh flashes and remain deterministic. */
  @Test
  void changedReturnedAndRepeatedStatesRemainContinuous() {
    for (boolean pengRobinson : new boolean[] { false, true }) {
      String model = pengRobinson ? "PR" : "SRK";
      SystemInterface reference = flash(pengRobinson, REFERENCE_WATER_FRACTION, REFERENCE_TEMPERATURE_K,
          REFERENCE_PRESSURE_BARA, true);
      SystemInterface reused = reference.clone();

      reused.setPressure(46.12, "bara");
      runPublicFlash(reused);
      SystemInterface freshChanged = flash(pengRobinson, REFERENCE_WATER_FRACTION, REFERENCE_TEMPERATURE_K, 46.12,
          true);
      assertEquivalentEquilibrium(freshChanged, reused, 1.0e-8, model + " changed pressure");

      reused.setPressure(REFERENCE_PRESSURE_BARA, "bara");
      runPublicFlash(reused);
      assertEquivalentEquilibrium(reference, reused, 1.0e-8, model + " returned pressure");

      SystemInterface previous = reused.clone();
      runPublicFlash(reused);
      assertEquivalentEquilibrium(previous, reused, 1.0e-10, model + " deterministic repeat");
    }
  }

  /** Dry feeds are outside the water-rich restart screen and retain the original hydrocarbon split. */
  @Test
  void dryControlRemainsGasOilWithoutAqueousPhase() {
    for (boolean pengRobinson : new boolean[] { false, true }) {
      String model = pengRobinson ? "PR" : "SRK";
      SystemInterface fluid = flash(pengRobinson, 0.0, REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA, true);
      assertEquals(2, fluid.getNumberOfPhases(), model + " dry topology");
      assertTrue(fluid.hasPhaseType(PhaseType.GAS), model + " dry gas phase");
      assertTrue(fluid.hasPhaseType(PhaseType.OIL), model + " dry oil phase");
      assertFalse(fluid.hasPhaseType(PhaseType.AQUEOUS), model + " dry aqueous phase");
      assertClosedEquilibrium(fluid, model + " dry control");
    }
  }

  private SystemInterface flash(boolean pengRobinson, double waterFraction, double temperature, double pressure,
      boolean multiphaseCheck) {
    SystemInterface fluid = buildFluid(pengRobinson, waterFraction, temperature, pressure, multiphaseCheck);
    runPublicFlash(fluid);
    return fluid;
  }

  private SystemInterface buildFluid(boolean pengRobinson, double waterFraction, double temperature, double pressure,
      boolean multiphaseCheck) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(temperature, pressure) : new SystemSrkEos(temperature, pressure);
    for (int component = 0; component < HYDROCARBON_NAMES.length; component++) {
      fluid.addComponent(HYDROCARBON_NAMES[component], HYDROCARBON_FRACTIONS[component] * (1.0 - waterFraction));
    }
    if (waterFraction > 0.0) {
      fluid.addComponent("water", waterFraction);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(multiphaseCheck);
    return fluid;
  }

  private void runPublicFlash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
  }

  private void assertClosedThreePhaseEquilibrium(SystemInterface fluid, String label) {
    assertEquals(3, fluid.getNumberOfPhases(), label + " topology");
    assertTrue(fluid.hasPhaseType(PhaseType.GAS), label + " gas phase");
    assertTrue(fluid.hasPhaseType(PhaseType.OIL), label + " oil phase");
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS), label + " aqueous phase");
    assertClosedEquilibrium(fluid, label);
  }

  private void assertClosedEquilibrium(SystemInterface fluid, String label) {
    int componentCount = fluid.getPhase(0).getNumberOfComponents();
    double betaTotal = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double beta = fluid.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0, label + " beta " + phase);
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (int component = 0; component < componentCount; component++) {
        double composition = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
            label + " composition " + phase + "/" + component);
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, NORMALIZATION_TOLERANCE, label + " composition normalization " + phase);
      assertTrue(Double.isFinite(fluid.getPhase(phase).getZ()) && fluid.getPhase(phase).getZ() > 0.0,
          label + " compressibility " + phase);
    }
    assertEquals(1.0, betaTotal, NORMALIZATION_TOLERANCE, label + " beta normalization");

    double materialResidual = maximumComponentMaterialBalanceResidual(fluid);
    assertTrue(materialResidual < MATERIAL_BALANCE_TOLERANCE,
        label + " material-balance residual " + materialResidual);

    double fugacityResidual = maximumComparableLogFugacityResidual(fluid);
    assertTrue(fugacityResidual < FUGACITY_TOLERANCE, label + " fugacity residual " + fugacityResidual);
    assertTrue(Double.isFinite(fluid.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(fluid.getEnthalpy()), label + " enthalpy");
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual, double tolerance,
      String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label + " phase count");
    assertClosedEquilibrium(expected, label + " expected");
    assertClosedEquilibrium(actual, label + " actual");
    for (int expectedPhase = 0; expectedPhase < expected.getNumberOfPhases(); expectedPhase++) {
      PhaseType phaseType = expected.getPhase(expectedPhase).getType();
      int actualPhase = findPhase(actual, phaseType);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance, label + " beta " + phaseType);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), tolerance,
          label + " compressibility " + phaseType);
      for (int component = 0; component < expected.getPhase(expectedPhase).getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), tolerance,
            label + " composition " + phaseType + "/" + component);
      }
    }
    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), tolerance, label + " Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), tolerance, label + " enthalpy");
  }

  private void assertExtensiveEquals(double expected, double actual, double relativeTolerance, String label) {
    assertEquals(expected, actual, Math.max(1.0e-8, relativeTolerance * Math.abs(expected)), label);
  }

  private int findPhase(SystemInterface fluid, PhaseType phaseType) {
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      if (fluid.getPhase(phase).getType() == phaseType) {
        return phase;
      }
    }
    throw new AssertionError("missing phase " + phaseType);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface fluid) {
    double maximumResidual = 0.0;
    for (int component = 0; component < fluid.getPhase(0).getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
      }
      maximumResidual = Math.max(maximumResidual,
          Math.abs(fluid.getPhase(0).getComponent(component).getz() - recovered));
    }
    return maximumResidual;
  }

  private double maximumComparableLogFugacityResidual(SystemInterface fluid) {
    double maximumResidual = 0.0;
    int comparisons = 0;
    for (int component = 0; component < fluid.getPhase(0).getNumberOfComponents(); component++) {
      for (int firstPhase = 0; firstPhase < fluid.getNumberOfPhases(); firstPhase++) {
        for (int secondPhase = firstPhase + 1; secondPhase < fluid.getNumberOfPhases(); secondPhase++) {
          double firstComposition = fluid.getPhase(firstPhase).getComponent(component).getx();
          double secondComposition = fluid.getPhase(secondPhase).getComponent(component).getx();
          double firstCoefficient = fluid.getPhase(firstPhase).getComponent(component).getFugacityCoefficient();
          double secondCoefficient = fluid.getPhase(secondPhase).getComponent(component).getFugacityCoefficient();
          if (firstComposition > 1.0e-20 && secondComposition > 1.0e-20 && Double.isFinite(firstCoefficient)
              && firstCoefficient > 0.0 && Double.isFinite(secondCoefficient) && secondCoefficient > 0.0) {
            maximumResidual = Math.max(maximumResidual, Math
                .abs(Math.log(firstComposition * firstCoefficient) - Math.log(secondComposition * secondCoefficient)));
            comparisons++;
          }
        }
      }
    }
    assertTrue(comparisons > 0, "expected at least one comparable cross-phase fugacity");
    return maximumResidual;
  }
}
