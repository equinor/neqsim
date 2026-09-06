package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRCPAEoS;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification tests for the UMR-CPA methane-water dehydration flash lifecycle.
 *
 * <p>
 * The synthetic binary uses the public UMR-CPA model and its HV/UNIFAC_UMRPRU mixing rule. It
 * retains the existing paper-facing gas-water envelope while adding closure, algorithm agreement,
 * poor-initialization, nearby-state, and reused-state contracts. This is numerical qualification,
 * not an independent revalidation of UMR-CPA parameters.
 * </p>
 */
class TPflashUMRCPADehydrationLifecycleTest {
  private static final double METHANE_FEED = 0.98;
  private static final double WATER_FEED = 0.02;
  private static final double REFERENCE_TEMPERATURE_K = 298.15;
  private static final double REFERENCE_PRESSURE_BARA = 70.0;

  private static final double NORMALIZATION_TOLERANCE = 3.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;

  /**
   * Temperature and pressure matrices must retain closed gas-aqueous equilibrium and the expected
   * dehydration trends.
   */
  @Test
  void temperatureAndPressureMatricesRemainClosedAndMonotonic() {
    double previousWaterInGas = -1.0;
    for (double temperatureK : new double[] {283.15, REFERENCE_TEMPERATURE_K, 313.15}) {
      SystemInterface ordinary = flash(createSystem(temperatureK, REFERENCE_PRESSURE_BARA, false));
      SystemInterface multiphase = flash(createSystem(temperatureK, REFERENCE_PRESSURE_BARA, true));
      assertQualifiedState(ordinary, "ordinary temperature " + temperatureK);
      assertQualifiedState(multiphase, "multiphase temperature " + temperatureK);
      assertEquivalentState(ordinary, multiphase, 1.0e-8,
          "algorithm agreement at " + temperatureK + " K");

      double waterInGas = gasWaterMoleFraction(ordinary);
      assertTrue(waterInGas > previousWaterInGas,
          "gas water content must increase with temperature: " + previousWaterInGas + " -> "
              + waterInGas);
      previousWaterInGas = waterInGas;
    }

    double previousWaterInGasAtPressure = Double.POSITIVE_INFINITY;
    for (double pressureBara : new double[] {30.0, REFERENCE_PRESSURE_BARA, 120.0}) {
      SystemInterface ordinary = flash(createSystem(REFERENCE_TEMPERATURE_K, pressureBara, false));
      SystemInterface multiphase = flash(createSystem(REFERENCE_TEMPERATURE_K, pressureBara, true));
      assertQualifiedState(ordinary, "ordinary pressure " + pressureBara);
      assertQualifiedState(multiphase, "multiphase pressure " + pressureBara);
      assertEquivalentState(ordinary, multiphase, 1.0e-8,
          "algorithm agreement at " + pressureBara + " bara");

      double waterInGas = gasWaterMoleFraction(ordinary);
      assertTrue(waterInGas < previousWaterInGasAtPressure,
          "gas water content must decrease with pressure: " + previousWaterInGasAtPressure + " -> "
              + waterInGas);
      previousWaterInGasAtPressure = waterInGas;
    }
  }

  /**
   * The established 298.15 K and 70 bara water-content envelope must remain visible.
   */
  @Test
  void referenceStateRetainsPaperFacingWaterContentEnvelope() {
    SystemInterface system =
        flash(createSystem(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA, true));
    assertQualifiedState(system, "reference state");

    double waterInGas = gasWaterMoleFraction(system);
    assertTrue(waterInGas > 1.0e-5 && waterInGas < 5.0e-3,
        "gas-phase water mole fraction outside the established model envelope: " + waterInGas);
  }

  /**
   * Beta values near a bound must recover the same closed reference equilibrium.
   */
  @Test
  void poorInitializationRecoversReferenceState() {
    SystemInterface reference =
        flash(createSystem(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA, true));
    SystemInterface poor =
        createSystem(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA, true);
    poor.init(0);
    assertTrue(poor.getNumberOfPhases() >= 2, "poor initialization requires two phase slots");
    poor.setBeta(0, 1.0e-12);
    poor.setBeta(1, 1.0 - 1.0e-12);
    flash(poor);

    assertEquivalentState(reference, poor, 1.0e-8, "poor initialization");
  }

  /**
   * A reused system must match fresh changed and returned states and settle deterministically.
   */
  @Test
  void changedReturnedAndRepeatedStatesRemainEquivalent() {
    SystemInterface reference =
        flash(createSystem(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA, true));
    SystemInterface reused = reference.clone();

    reused.setTemperature(313.15, "K");
    reused.setPressure(120.0, "bara");
    flash(reused);
    SystemInterface freshChanged = flash(createSystem(313.15, 120.0, true));
    assertEquivalentState(freshChanged, reused, 1.0e-8, "changed state");

    reused.setTemperature(REFERENCE_TEMPERATURE_K, "K");
    reused.setPressure(REFERENCE_PRESSURE_BARA, "bara");
    flash(reused);
    assertEquivalentState(reference, reused, 1.0e-8, "returned state");

    SystemInterface previous = reused.clone();
    flash(reused);
    assertEquivalentState(previous, reused, 1.0e-10, "deterministic repeat");
  }

  private SystemInterface createSystem(
      double temperatureK, double pressureBara, boolean multiphaseCheck) {
    SystemInterface system = new SystemUMRCPAEoS(temperatureK, pressureBara);
    system.addComponent("methane", METHANE_FEED);
    system.addComponent("water", WATER_FEED);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    system.setMultiPhaseCheck(multiphaseCheck);
    return system;
  }

  private SystemInterface flash(SystemInterface system) {
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  private double gasWaterMoleFraction(SystemInterface system) {
    return system.getPhase(findPhase(system, PhaseType.GAS)).getComponent("water").getx();
  }

  private void assertQualifiedState(SystemInterface system, String label) {
    assertEquals(2, system.getNumberOfPhases(), label + " phase count");
    assertTrue(system.hasPhaseType(PhaseType.GAS), label + " gas phase");
    assertTrue(system.hasPhaseType(PhaseType.AQUEOUS), label + " aqueous phase");

    int componentCount = system.getPhase(0).getNumberOfComponents();
    double betaTotal = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0,
          label + " beta " + phase + ": " + beta);
      betaTotal += beta;

      double compositionTotal = 0.0;
      for (int component = 0; component < componentCount; component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0,
            label + " composition " + phase + "/" + component + ": " + composition);
        compositionTotal += composition;
      }
      assertEquals(1.0, compositionTotal, NORMALIZATION_TOLERANCE,
          label + " composition normalization " + phase);
      assertTrue(Double.isFinite(system.getPhase(phase).getZ())
          && system.getPhase(phase).getZ() > 0.0, label + " compressibility " + phase);
    }
    assertEquals(1.0, betaTotal, NORMALIZATION_TOLERANCE, label + " beta normalization");

    double materialResidual = maximumComponentMaterialBalanceResidual(system);
    assertTrue(materialResidual < MATERIAL_BALANCE_TOLERANCE,
        label + " material-balance residual " + materialResidual);

    double fugacityResidual = maximumComparableLogFugacityResidual(system);
    assertTrue(fugacityResidual < FUGACITY_TOLERANCE,
        label + " fugacity residual " + fugacityResidual);

    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(system.getEnthalpy()), label + " enthalpy");
  }

  private void assertEquivalentState(
      SystemInterface expected, SystemInterface actual, double tolerance, String label) {
    assertQualifiedState(expected, label + " expected");
    assertQualifiedState(actual, label + " actual");

    for (PhaseType type : new PhaseType[] {PhaseType.GAS, PhaseType.AQUEOUS}) {
      int expectedPhase = findPhase(expected, type);
      int actualPhase = findPhase(actual, type);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance,
          label + " beta " + type);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(),
          tolerance, label + " compressibility " + type);
      for (int component = 0;
          component < expected.getPhase(expectedPhase).getNumberOfComponents();
          component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), tolerance,
            label + " composition " + type + "/" + component);
      }
    }
    assertExtensiveEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(), tolerance,
        label + " Gibbs energy");
    assertExtensiveEquals(expected.getEnthalpy(), actual.getEnthalpy(), tolerance,
        label + " enthalpy");
  }

  private void assertExtensiveEquals(
      double expected, double actual, double relativeTolerance, String label) {
    assertEquals(expected, actual, Math.max(1.0e-8, relativeTolerance * Math.abs(expected)), label);
  }

  private int findPhase(SystemInterface system, PhaseType type) {
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == type) {
        return phase;
      }
    }
    throw new AssertionError("missing phase " + type);
  }

  private double maximumComponentMaterialBalanceResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    for (int component = 0;
        component < system.getPhase(0).getNumberOfComponents();
        component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered +=
            system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximumResidual = Math.max(maximumResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));
    }
    return maximumResidual;
  }

  private double maximumComparableLogFugacityResidual(SystemInterface system) {
    double maximumResidual = 0.0;
    int comparisons = 0;
    for (int component = 0;
        component < system.getPhase(0).getNumberOfComponents();
        component++) {
      double gasComposition =
          system.getPhase(findPhase(system, PhaseType.GAS)).getComponent(component).getx();
      double aqueousComposition =
          system.getPhase(findPhase(system, PhaseType.AQUEOUS)).getComponent(component).getx();
      double gasCoefficient = system.getPhase(findPhase(system, PhaseType.GAS))
          .getComponent(component).getFugacityCoefficient();
      double aqueousCoefficient = system.getPhase(findPhase(system, PhaseType.AQUEOUS))
          .getComponent(component).getFugacityCoefficient();
      if (gasComposition > 1.0e-20 && aqueousComposition > 1.0e-20
          && Double.isFinite(gasCoefficient) && gasCoefficient > 0.0
          && Double.isFinite(aqueousCoefficient) && aqueousCoefficient > 0.0) {
        double gasLogFugacity = Math.log(gasComposition * gasCoefficient);
        double aqueousLogFugacity = Math.log(aqueousComposition * aqueousCoefficient);
        maximumResidual =
            Math.max(maximumResidual, Math.abs(gasLogFugacity - aqueousLogFugacity));
        comparisons++;
      }
    }
    assertEquals(2, comparisons, "both methane and water must expose comparable fugacities");
    return maximumResidual;
  }
}
