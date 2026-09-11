package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Qualification of the historical four-component SRK hydrocarbon TP-flash case. */
class TPFlashLegacySrkLifecycleTest {
  private static final double REFERENCE_TEMPERATURE_K = 298.0;
  private static final double REFERENCE_PRESSURE_BARA = 10.0;

  /**
   * Qualifies ordinary and explicit-multiphase paths, nearby states, reuse, and repeatability.
   */
  @Test
  void legacySrkHydrocarbonFlashIsClosedAndRepeatable() {
    SystemInterface ordinary = flash(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA, false, false);
    SystemInterface multiphase = flash(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA, true, false);
    SystemInterface poorGuess = flash(REFERENCE_TEMPERATURE_K, REFERENCE_PRESSURE_BARA, true, true);

    assertGasOilEquilibrium(ordinary, "ordinary reference");
    assertGasOilEquilibrium(multiphase, "multiphase reference");
    assertEquivalentState(ordinary, multiphase, 1.0e-10, "ordinary versus multiphase");
    assertEquivalentState(multiphase, poorGuess, 1.0e-10, "poor beta initialization");

    for (double pressure : new double[] { 9.5, 10.5 }) {
      SystemInterface nearbyOrdinary = flash(REFERENCE_TEMPERATURE_K, pressure, false, false);
      SystemInterface nearbyMultiphase = flash(REFERENCE_TEMPERATURE_K, pressure, true, false);
      assertGasOilEquilibrium(nearbyOrdinary, "ordinary at " + pressure + " bara");
      assertGasOilEquilibrium(nearbyMultiphase, "multiphase at " + pressure + " bara");
      assertEquivalentState(nearbyOrdinary, nearbyMultiphase, 1.0e-10, "path agreement at " + pressure + " bara");
    }

    SystemInterface reused = multiphase.clone();
    reused.setPressure(10.5, "bara");
    runFlash(reused);
    assertEquivalentState(flash(REFERENCE_TEMPERATURE_K, 10.5, true, false), reused, 1.0e-10, "changed state");

    reused.setPressure(REFERENCE_PRESSURE_BARA, "bara");
    runFlash(reused);
    assertEquivalentState(multiphase, reused, 1.0e-10, "returned state");

    SystemInterface repeated = reused.clone();
    runFlash(reused);
    assertEquivalentState(repeated, reused, 1.0e-12, "deterministic repeat");
  }

  /**
   * Creates and flashes one state from the historical SRK example.
   *
   * @param temperatureK temperature in kelvin
   * @param pressureBara pressure in bara
   * @param multiphaseCheck whether to use explicit multiphase checking
   * @param poorGuess whether to initialize phase fractions close to a bound
   * @return initialized flashed system
   */
  private SystemInterface flash(double temperatureK, double pressureBara, boolean multiphaseCheck, boolean poorGuess) {
    SystemInterface system = createSystem(temperatureK, pressureBara);
    system.setMultiPhaseCheck(multiphaseCheck);
    if (poorGuess) {
      system.setBeta(0, 1.0e-12);
      system.setBeta(1, 1.0 - 1.0e-12);
    }
    runFlash(system);
    return system;
  }

  /**
   * Creates the historical methane/ethane/propane/n-heptane SRK fluid.
   *
   * @param temperatureK temperature in kelvin
   * @param pressureBara pressure in bara
   * @return configured fluid
   */
  private SystemInterface createSystem(double temperatureK, double pressureBara) {
    SystemInterface system = new SystemSrkEos(temperatureK, pressureBara);
    system.addComponent("methane", 10.0);
    system.addComponent("ethane", 1.0);
    system.addComponent("propane", 0.1);
    system.addComponent("n-heptane", 10.1);
    system.setMixingRule("classic");
    return system;
  }

  /** Runs a TP flash and initializes properties required by the qualification. */
  private void runFlash(SystemInterface system) {
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
  }

  /**
   * Verifies topology, normalization, material balance, fugacity equality, and finite properties.
   *
   * @param system flashed system
   * @param label assertion label
   */
  private void assertGasOilEquilibrium(SystemInterface system, String label) {
    assertEquals(2, system.getNumberOfPhases(), label + " phase count");
    assertTrue(system.hasPhaseType("gas"), label + " gas phase");
    assertTrue(system.hasPhaseType("oil"), label + " oil phase");

    double betaSum = 0.0;
    int componentCount = system.getPhase(0).getNumberOfComponents();
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0, label + " beta");
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < componentCount; component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0, label + " composition");
        compositionSum += composition;
      }
      assertEquals(1.0, compositionSum, 1.0e-12, label + " phase normalization");
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0,
          label + " compressibility");
    }
    assertEquals(1.0, betaSum, 1.0e-12, label + " beta normalization");

    double maximumMaterialResidual = 0.0;
    double maximumFugacityResidual = 0.0;
    int fugacityComparisons = 0;
    for (int component = 0; component < componentCount; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximumMaterialResidual = Math.max(maximumMaterialResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));

      double gasComposition = system.getPhase("gas").getComponent(component).getx();
      double oilComposition = system.getPhase("oil").getComponent(component).getx();
      double gasCoefficient = system.getPhase("gas").getComponent(component).getFugacityCoefficient();
      double oilCoefficient = system.getPhase("oil").getComponent(component).getFugacityCoefficient();
      if (gasComposition > 1.0e-20 && oilComposition > 1.0e-20 && Double.isFinite(gasCoefficient)
          && gasCoefficient > 0.0 && Double.isFinite(oilCoefficient) && oilCoefficient > 0.0) {
        maximumFugacityResidual = Math.max(maximumFugacityResidual,
            Math.abs(Math.log(gasComposition * gasCoefficient) - Math.log(oilComposition * oilCoefficient)));
        fugacityComparisons++;
      }
    }
    assertTrue(maximumMaterialResidual < 1.0e-10, label + " material residual " + maximumMaterialResidual);
    assertTrue(fugacityComparisons > 0, label + " fugacity comparisons");
    assertTrue(maximumFugacityResidual < 1.0e-8, label + " fugacity residual " + maximumFugacityResidual);
    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(system.getEnthalpy()), label + " enthalpy");
  }

  /**
   * Compares equilibria after ordering phases by n-heptane content.
   *
   * @param expected reference state
   * @param actual state under comparison
   * @param tolerance absolute state tolerance
   * @param label assertion label
   */
  private void assertEquivalentState(SystemInterface expected, SystemInterface actual, double tolerance, String label) {
    assertGasOilEquilibrium(expected, label + " expected");
    assertGasOilEquilibrium(actual, label + " actual");
    Integer[] expectedOrder = phaseOrder(expected);
    Integer[] actualOrder = phaseOrder(actual);
    for (int orderedPhase = 0; orderedPhase < expectedOrder.length; orderedPhase++) {
      int expectedPhase = expectedOrder[orderedPhase];
      int actualPhase = actualOrder[orderedPhase];
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance, label + " beta");
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), tolerance,
          label + " compressibility");
      for (int component = 0; component < expected.getPhase(expectedPhase).getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), tolerance, label + " composition");
      }
    }
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(),
        Math.max(1.0e-8, tolerance * Math.abs(expected.getGibbsEnergy())), label + " Gibbs energy");
    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(),
        Math.max(1.0e-8, tolerance * Math.abs(expected.getEnthalpy())), label + " enthalpy");
  }

  /** Orders phases from light to heavy using n-heptane mole fraction. */
  private Integer[] phaseOrder(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order,
        Comparator.comparingDouble((Integer index) -> system.getPhase(index).getComponent("n-heptane").getx()));
    return order;
  }
}
