package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEosNew;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualifies the translated UMR-PRU Mathias-Copeman TP-flash lifecycle after the translated-root repair.
 *
 * <p>
 * The public synthetic lean-gas fluid is shared with {@link UMRPRUOilDropoutReproTest}. These tests verify numerical
 * closure, stable phase selection, initialization independence, and state reuse. They do not validate the alternative
 * pure-component alpha parameters against experimental PVT data.
 * </p>
 */
class UMRPRUMCNewOilDropoutLifecycleTest {
  private static final String[] NAMES = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane",
      "i-pentane", "n-pentane", "2-m-C5", "3-m-C5", "n-hexane", "c-hexane", "n-heptane", "benzene", "n-octane", "c-C7",
      "toluene", "n-nonane", "c-C8", "m-Xylene", "nC10", "nC11", "nC12"};
  private static final double[] FEED = {0.00959, 0.00634, 0.946, 0.0265, 0.00416, 0.00159, 0.00103, 0.000842, 0.000268,
      0.000418, 0.000127, 0.000216, 0.000857, 0.00016, 2.14e-5, 4.92e-5, 0.000575, 5.5e-5, 4.17e-5, 7.85e-5, 3.73e-5,
      4.69e-5 * 2, 7.61e-6 * 2, 1.0e-6 * 2};
  private static final double TEMPERATURE_C = 18.0;
  private static final double PRESSURE_BARA = 78.0;
  private static final double NORMALIZATION_TOLERANCE = 5.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;

  /** Ordinary and multiphase algorithms must recover the same closed gas-oil state. */
  @Test
  void ordinaryAndMultiphaseAlgorithmsAgree() {
    SystemInterface ordinary = flash(TEMPERATURE_C, PRESSURE_BARA, false);
    SystemInterface multiphase = flash(TEMPERATURE_C, PRESSURE_BARA, true);

    assertEquivalentEquilibrium(ordinary, multiphase, 1.0e-8, "algorithm agreement");
  }

  /** An intentionally poor phase-fraction estimate must recover the reference equilibrium. */
  @Test
  void poorInitializationRecoversReferenceState() {
    SystemInterface reference = flash(TEMPERATURE_C, PRESSURE_BARA, true);
    SystemInterface poor = createFluid(TEMPERATURE_C, PRESSURE_BARA, true);
    poor.init(0);
    assertTrue(poor.getNumberOfPhases() >= 2, "poor initialization requires two phase slots");
    poor.setBeta(0, 1.0e-12);
    poor.setBeta(1, 1.0 - 1.0e-12);
    runFlash(poor);

    assertEquivalentEquilibrium(reference, poor, 1.0e-8, "poor initialization");
  }

  /** Reused changed, returned, and repeated states must match independently constructed flashes. */
  @Test
  void reusedStateRemainsContinuousAndDeterministic() {
    SystemInterface reference = flash(TEMPERATURE_C, PRESSURE_BARA, true);
    SystemInterface reused = reference.clone();

    reused.setTemperature(273.15 + 19.0);
    reused.setPressure(79.0, "bara");
    runFlash(reused);
    SystemInterface freshChanged = flash(19.0, 79.0, true);
    assertEquivalentEquilibrium(freshChanged, reused, 1.0e-8, "changed state");

    reused.setTemperature(273.15 + TEMPERATURE_C);
    reused.setPressure(PRESSURE_BARA, "bara");
    runFlash(reused);
    assertEquivalentEquilibrium(reference, reused, 1.0e-8, "returned state");

    SystemInterface previous = reused.clone();
    runFlash(reused);
    assertEquivalentEquilibrium(previous, reused, 1.0e-10, "deterministic repeat");
  }

  /** Nearby temperatures must retain a closed gas-oil topology with monotonic oil dropout. */
  @Test
  void nearbyTemperatureSweepIsClosedAndMonotonic() {
    double previousOil = Double.NaN;
    for (double temperatureC : new double[] {20.0, 18.0, 12.0, 8.0}) {
      SystemInterface fluid = flash(temperatureC, PRESSURE_BARA, true);
      assertClosedGasOilEquilibrium(fluid, temperatureC + " C");
      double oil = oilBeta(fluid);
      if (Double.isFinite(previousOil)) {
        assertTrue(oil >= previousOil - 1.0e-9, "oil dropout decreased as temperature fell to " + temperatureC + " C");
      }
      previousOil = oil;
    }
  }

  private SystemInterface flash(double temperatureC, double pressureBara, boolean multiphaseCheck) {
    SystemInterface fluid = createFluid(temperatureC, pressureBara, multiphaseCheck);
    runFlash(fluid);
    return fluid;
  }

  private SystemInterface createFluid(double temperatureC, double pressureBara, boolean multiphaseCheck) {
    SystemInterface fluid = new SystemUMRPRUMCEosNew(273.15 + temperatureC, pressureBara);
    for (int component = 0; component < NAMES.length; component++) {
      fluid.addComponent(NAMES[component], FEED[component]);
    }
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    fluid.setMultiPhaseCheck(multiphaseCheck);
    return fluid;
  }

  private void runFlash(SystemInterface fluid) {
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
