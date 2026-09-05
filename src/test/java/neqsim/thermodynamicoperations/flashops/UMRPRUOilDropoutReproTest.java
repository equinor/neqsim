package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification tests for UMR-PRU trace oil dropout.
 *
 * <p>
 * A synthetic lean gas with trace heavy ends drops a small amount of retrograde oil below its dew point. The historical
 * regression reproduced a duplicate-oil-phase loss in the multiphase flash. This class also qualifies equilibrium
 * closure and lifecycle behavior after component and UNIFAC-table repairs. It is deterministic numerical evidence, not
 * experimental PVT validation.
 * </p>
 */
class UMRPRUOilDropoutReproTest {
  private static final String[] NAMES = { "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane",
      "i-pentane", "n-pentane", "2-m-C5", "3-m-C5", "n-hexane", "c-hexane", "n-heptane", "benzene", "n-octane", "c-C7",
      "toluene", "n-nonane", "c-C8", "m-Xylene", "nC10", "nC11", "nC12" };
  private static final double[] FEED = { 0.00959, 0.00634, 0.946, 0.0265, 0.00416, 0.00159, 0.00103, 0.000842, 0.000268,
      0.000418, 0.000127, 0.000216, 0.000857, 0.00016, 2.14e-05, 4.92e-05, 0.000575, 5.5e-05, 4.17e-05, 7.85e-05,
      3.73e-05, 4.69e-05 * 2, 7.61e-06 * 2, 1e-6 * 2 };
  private static final double REFERENCE_TEMPERATURE_C = 18.0;
  private static final double REFERENCE_PRESSURE_BARA = 78.0;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double NORMALIZATION_TOLERANCE = 2.0e-12;

  /**
   * The trace oil dropout must increase smoothly as temperature decreases below the dew point.
   */
  @Test
  void traceOilDropoutIsMonotonicAndClosed() {
    double previous = Double.NaN;
    for (int temperatureC = 20; temperatureC >= 8; temperatureC--) {
      SystemInterface fluid = flash(temperatureC, REFERENCE_PRESSURE_BARA, true);
      String label = temperatureC + " C";
      assertClosedGasOilEquilibrium(fluid, label);
      double oil = oilBeta(fluid);
      if (!Double.isNaN(previous)) {
        assertTrue(oil >= previous - 1.0e-9,
            "oil dropout decreased as temperature fell to " + label + ": " + oil + " < " + previous);
      }
      previous = oil;
    }
  }

  /**
   * Retains the smooth-curve reference values established by the duplicate-phase repair.
   */
  @Test
  void traceOilDropoutMatchesReference() {
    assertEquals(9.144142e-04, oilBeta(flash(8.0, REFERENCE_PRESSURE_BARA, true)), 5.0e-6, "oil dropout at 8 C");
    assertEquals(3.502455e-04, oilBeta(flash(REFERENCE_TEMPERATURE_C, REFERENCE_PRESSURE_BARA, true)), 5.0e-6,
        "oil dropout at 18 C");
    assertEquals(2.582079e-04, oilBeta(flash(20.0, REFERENCE_PRESSURE_BARA, true)), 5.0e-6, "oil dropout at 20 C");
  }

  /**
   * Ordinary and multiphase public TP flashes must recover the same closed trace-oil solution.
   */
  @Test
  void ordinaryAndMultiphaseFlashesAgreeAtQualifiedStates() {
    for (double temperatureC : new double[] { 8.0, REFERENCE_TEMPERATURE_C, 20.0 }) {
      SystemInterface ordinary = flash(temperatureC, REFERENCE_PRESSURE_BARA, false);
      SystemInterface multiphase = flash(temperatureC, REFERENCE_PRESSURE_BARA, true);
      assertEquivalentEquilibrium(ordinary, multiphase, 1.0e-8, "algorithm agreement at " + temperatureC + " C");
    }
  }

  /**
   * Nearby pressures and an intentionally poor beta estimate must recover closed equilibrium.
   */
  @Test
  void poorInitializationAndNearbyPressuresRecoverClosedEquilibrium() {
    SystemInterface reference = null;
    for (double pressureBara : new double[] { 77.0, REFERENCE_PRESSURE_BARA, 79.0 }) {
      SystemInterface fluid = flash(REFERENCE_TEMPERATURE_C, pressureBara, true);
      assertClosedGasOilEquilibrium(fluid, pressureBara + " bara");
      if (pressureBara == REFERENCE_PRESSURE_BARA) {
        reference = fluid;
      }
    }

    SystemInterface poorGuess = buildFluid(REFERENCE_TEMPERATURE_C, REFERENCE_PRESSURE_BARA, true);
    poorGuess.init(0);
    poorGuess.setBeta(0, 1.0e-12);
    poorGuess.setBeta(1, 1.0 - 1.0e-12);
    runPublicFlash(poorGuess);
    assertEquivalentEquilibrium(reference, poorGuess, 1.0e-8, "poor beta initialization");
  }

  /**
   * Reused changed and returned states must match fresh flashes and remain deterministic.
   */
  @Test
  void changedReturnedAndRepeatedStatesRemainContinuous() {
    SystemInterface reference = flash(REFERENCE_TEMPERATURE_C, REFERENCE_PRESSURE_BARA, true);
    SystemInterface reused = reference.clone();

    reused.setTemperature(273.15 + 19.0);
    reused.setPressure(79.0, "bara");
    runPublicFlash(reused);
    SystemInterface freshChanged = flash(19.0, 79.0, true);
    assertEquivalentEquilibrium(freshChanged, reused, 1.0e-8, "changed state");

    reused.setTemperature(273.15 + REFERENCE_TEMPERATURE_C);
    reused.setPressure(REFERENCE_PRESSURE_BARA, "bara");
    runPublicFlash(reused);
    assertEquivalentEquilibrium(reference, reused, 1.0e-8, "returned state");

    SystemInterface previous = reused.clone();
    runPublicFlash(reused);
    assertEquivalentEquilibrium(previous, reused, 1.0e-10, "deterministic repeat");
  }

  private SystemInterface flash(double temperatureC, double pressureBara, boolean multiphaseCheck) {
    SystemInterface fluid = buildFluid(temperatureC, pressureBara, multiphaseCheck);
    runPublicFlash(fluid);
    return fluid;
  }

  private SystemInterface buildFluid(double temperatureC, double pressureBara, boolean multiphaseCheck) {
    SystemInterface fluid = new SystemUMRPRUMCEos(273.15 + temperatureC, pressureBara);
    for (int component = 0; component < NAMES.length; component++) {
      fluid.addComponent(NAMES[component], FEED[component]);
    }
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    fluid.setMultiPhaseCheck(multiphaseCheck);
    return fluid;
  }

  private void runPublicFlash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
  }

  private void assertClosedGasOilEquilibrium(SystemInterface fluid, String label) {
    assertEquals(2, fluid.getNumberOfPhases(), label + " topology");
    assertTrue(fluid.hasPhaseType(PhaseType.GAS), label + " gas phase");
    assertTrue(fluid.hasPhaseType(PhaseType.OIL) || fluid.hasPhaseType(PhaseType.LIQUID), label + " liquid phase");
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
    assertTrue(materialResidual < MATERIAL_BALANCE_TOLERANCE, label + " material-balance residual " + materialResidual);

    double fugacityResidual = maximumComparableLogFugacityResidual(fluid);
    assertTrue(fugacityResidual < FUGACITY_TOLERANCE, label + " fugacity residual " + fugacityResidual);
    assertTrue(Double.isFinite(fluid.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(fluid.getEnthalpy()), label + " enthalpy");
  }

  private void assertEquivalentEquilibrium(SystemInterface expected, SystemInterface actual, double tolerance,
      String label) {
    assertClosedGasOilEquilibrium(expected, label + " expected");
    assertClosedGasOilEquilibrium(actual, label + " actual");

    int expectedGas = findPhase(expected, PhaseType.GAS);
    int actualGas = findPhase(actual, PhaseType.GAS);
    assertEquivalentPhase(expected, expectedGas, actual, actualGas, tolerance, label + " gas");

    int expectedLiquid = findLiquidPhase(expected);
    int actualLiquid = findLiquidPhase(actual);
    assertEquivalentPhase(expected, expectedLiquid, actual, actualLiquid, tolerance, label + " liquid");

    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), tolerance, label + " Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), tolerance, label + " enthalpy");
  }

  private void assertEquivalentPhase(SystemInterface expected, int expectedPhase, SystemInterface actual,
      int actualPhase, double tolerance, String label) {
    assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance, label + " beta");
    assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), tolerance,
        label + " compressibility");
    for (int component = 0; component < expected.getPhase(expectedPhase).getNumberOfComponents(); component++) {
      assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
          actual.getPhase(actualPhase).getComponent(component).getx(), tolerance, label + " composition " + component);
    }
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

  private int findLiquidPhase(SystemInterface fluid) {
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseType type = fluid.getPhase(phase).getType();
      if (type == PhaseType.OIL || type == PhaseType.LIQUID) {
        return phase;
      }
    }
    throw new AssertionError("missing oil/liquid phase");
  }

  private double oilBeta(SystemInterface fluid) {
    double oil = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseType type = fluid.getPhase(phase).getType();
      if (type == PhaseType.OIL || type == PhaseType.LIQUID) {
        oil += fluid.getBeta(phase);
      }
    }
    return oil;
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
