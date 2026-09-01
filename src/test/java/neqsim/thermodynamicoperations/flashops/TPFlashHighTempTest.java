package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification tests for high-temperature rich-fluid TP flashes.
 *
 * <p>
 * The historical version of this class was a logger-only {@code main} diagnostic. These tests turn the same
 * 24-component synthetic fluid into deterministic SRK and PR regressions with enforceable equilibrium, initialization,
 * and stale-state contracts.
 * </p>
 */
class TPFlashHighTempTest {
  private static final double REFERENCE_TEMPERATURE_C = 268.0;
  private static final double REFERENCE_PRESSURE_BARA = 88.0;
  private static final double REFERENCE_SRK_GAS_BETA = 0.00698;
  private static final double REFERENCE_SRK_GAS_BETA_TOLERANCE = 0.001;

  /** Qualifies the SRK ordinary and explicit-multiphase paths. */
  @Test
  void srkHighTemperatureRichFluidFlashIsClosedAndRepeatable() {
    qualifyHighTemperatureRichFluid(false, "SRK");
  }

  /** Qualifies the PR ordinary and explicit-multiphase paths. */
  @Test
  void prHighTemperatureRichFluidFlashIsClosedAndRepeatable() {
    qualifyHighTemperatureRichFluid(true, "PR");
  }

  /**
   * Runs the complete qualification matrix for one cubic equation of state.
   *
   * @param pengRobinson true for PR, false for SRK
   * @param modelLabel model name used in assertion messages
   */
  private void qualifyHighTemperatureRichFluid(boolean pengRobinson, String modelLabel) {
    SystemInterface ordinary = flash(pengRobinson, REFERENCE_TEMPERATURE_C, false, false);
    SystemInterface reference = flash(pengRobinson, REFERENCE_TEMPERATURE_C, true, false);
    SystemInterface poorGuess = flash(pengRobinson, REFERENCE_TEMPERATURE_C, true, true);

    int expectedReferencePhases = pengRobinson ? 1 : 2;
    assertEquals(expectedReferencePhases, reference.getNumberOfPhases(), modelLabel + " reference topology");
    if (!pengRobinson) {
      assertEquals(REFERENCE_SRK_GAS_BETA, reference.getPhaseFraction("gas", "mole"), REFERENCE_SRK_GAS_BETA_TOLERANCE,
          modelLabel + " historical gas beta");
    }
    assertClosedEquilibrium(ordinary, modelLabel + " ordinary reference");
    assertClosedEquilibrium(reference, modelLabel + " multiphase reference");
    assertEquivalentState(ordinary, reference, 1.0e-8, modelLabel + " ordinary versus multiphase");
    assertEquivalentState(reference, poorGuess, 1.0e-8, modelLabel + " poor beta initialization");

    for (double temperatureC : new double[] { 267.0, 269.0 }) {
      SystemInterface nearbyOrdinary = flash(pengRobinson, temperatureC, false, false);
      SystemInterface nearbyMultiphase = flash(pengRobinson, temperatureC, true, false);
      assertClosedEquilibrium(nearbyOrdinary, modelLabel + " ordinary at " + temperatureC + " C");
      assertClosedEquilibrium(nearbyMultiphase, modelLabel + " multiphase at " + temperatureC + " C");
      assertEquivalentState(nearbyOrdinary, nearbyMultiphase, 1.0e-8,
          modelLabel + " path agreement at " + temperatureC + " C");
    }

    SystemInterface reused = reference.clone();
    reused.setTemperature(269.0, "C");
    new ThermodynamicOperations(reused).TPflash();
    reused.initProperties();
    assertEquivalentState(flash(pengRobinson, 269.0, true, false), reused, 1.0e-8, modelLabel + " changed state");

    reused.setTemperature(REFERENCE_TEMPERATURE_C, "C");
    new ThermodynamicOperations(reused).TPflash();
    reused.initProperties();
    assertEquivalentState(reference, reused, 1.0e-8, modelLabel + " return state");

    SystemInterface repeated = reused.clone();
    new ThermodynamicOperations(reused).TPflash();
    reused.initProperties();
    assertEquivalentState(repeated, reused, 1.0e-10, modelLabel + " deterministic repeat");
  }

  /**
   * Creates and flashes one synthetic rich-fluid state.
   *
   * @param pengRobinson true for PR, false for SRK
   * @param temperatureC temperature in degrees Celsius
   * @param multiphaseCheck whether to run the explicit multiphase path
   * @param poorGuess whether to initialize phase fractions near a bound
   * @return initialized flashed system
   */
  private SystemInterface flash(boolean pengRobinson, double temperatureC, boolean multiphaseCheck, boolean poorGuess) {
    SystemInterface system = createSystem(pengRobinson);
    system.setMultiPhaseCheck(multiphaseCheck);
    system.setPressure(REFERENCE_PRESSURE_BARA, "bara");
    system.setTemperature(temperatureC, "C");
    if (poorGuess) {
      system.setBeta(0, 1.0e-12);
      system.setBeta(1, 1.0 - 1.0e-12);
    }
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
    return system;
  }

  /**
   * Creates the historical 24-component high-temperature diagnostic fluid.
   *
   * @param pengRobinson true for PR, false for SRK
   * @return configured cubic-EOS system
   */
  private SystemInterface createSystem(boolean pengRobinson) {
    SystemInterface system = pengRobinson ? new neqsim.thermo.system.SystemPrEos(243.15, 300.0)
        : new neqsim.thermo.system.SystemSrkEos(243.15, 300.0);
    system.addComponent("nitrogen", 1.64e-3);
    system.addComponent("CO2", 1.64e-3);
    system.addComponent("H2S", 1.64e-3);
    system.addComponent("methane", 90.0);
    system.addComponent("ethane", 2.0);
    system.addComponent("propane", 1.0);
    system.addComponent("i-butane", 1.0);
    system.addComponent("n-butane", 1.0);
    system.addComponent("i-pentane", 1.0);
    system.addComponent("n-pentane", 1.0);
    system.addComponent("n-hexane", 1.0);
    system.addComponent("n-heptane", 1.0);
    system.addComponent("n-octane", 1.0);
    system.addComponent("n-nonane", 1.0);
    system.addComponent("nC10", 1.0);
    system.addComponent("nC11", 1.0);
    system.addComponent("nC12", 1.0);
    system.addComponent("nC13", 1.0);
    system.addComponent("nC14", 1.0);
    system.addComponent("nC15", 1.0);
    system.addComponent("nC16", 1.0);
    system.addComponent("nC17", 1.0);
    system.addComponent("nC18", 1.0);
    system.addComponent("nC19", 1.0);
    system.setMixingRule("classic");
    system.setMolarComposition(
        new double[] { 1.63e-3, 3.23e-3, 0.0, 3.0e-1, 4.6e-2, 1.4e-2, 2.2e-2, 3.9e-3, 8.8e-3, 2.6e-3, 3.2e-2, 1.2e-1,
            1.5e-1, 9.8e-2, 7.6e-2, 4.1e-2, 2.5e-2, 1.6e-2, 1.0e-2, 5.6e-3, 2.7e-3, 1.3e-3, 8.7e-4, 3.8e-4 });
    return system;
  }

  /**
   * Verifies phase normalization, component material balance, fugacity equality, and finite properties.
   *
   * @param system flashed system
   * @param label assertion label
   */
  private void assertClosedEquilibrium(SystemInterface system, String label) {
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

      for (int firstPhase = 0; firstPhase < system.getNumberOfPhases(); firstPhase++) {
        for (int secondPhase = firstPhase + 1; secondPhase < system.getNumberOfPhases(); secondPhase++) {
          double firstComposition = system.getPhase(firstPhase).getComponent(component).getx();
          double secondComposition = system.getPhase(secondPhase).getComponent(component).getx();
          double firstCoefficient = system.getPhase(firstPhase).getComponent(component).getFugacityCoefficient();
          double secondCoefficient = system.getPhase(secondPhase).getComponent(component).getFugacityCoefficient();
          if (firstComposition > 1.0e-20 && secondComposition > 1.0e-20 && Double.isFinite(firstCoefficient)
              && firstCoefficient > 0.0 && Double.isFinite(secondCoefficient) && secondCoefficient > 0.0) {
            maximumFugacityResidual = Math.max(maximumFugacityResidual, Math
                .abs(Math.log(firstComposition * firstCoefficient) - Math.log(secondComposition * secondCoefficient)));
            fugacityComparisons++;
          }
        }
      }
    }
    assertTrue(maximumMaterialResidual < 1.0e-10, label + " material-balance residual " + maximumMaterialResidual);
    if (system.getNumberOfPhases() >= 2) {
      assertTrue(fugacityComparisons > 0, label + " fugacity comparisons");
      assertTrue(maximumFugacityResidual < 1.0e-8, label + " fugacity residual " + maximumFugacityResidual);
    }
    assertTrue(Double.isFinite(system.getGibbsEnergy()), label + " Gibbs energy");
    assertTrue(Double.isFinite(system.getEnthalpy()), label + " enthalpy");
  }

  /**
   * Compares two equilibria after sorting phases by nC19 content.
   *
   * @param expected reference state
   * @param actual state under comparison
   * @param tolerance absolute beta, composition, and compressibility tolerance
   * @param label assertion label
   */
  private void assertEquivalentState(SystemInterface expected, SystemInterface actual, double tolerance, String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label);
    assertClosedEquilibrium(expected, label + " expected");
    assertClosedEquilibrium(actual, label + " actual");
    Integer[] expectedOrder = phaseOrderByHeavyFraction(expected);
    Integer[] actualOrder = phaseOrderByHeavyFraction(actual);
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

  /**
   * Orders active phases from lightest to heaviest using nC19 mole fraction.
   *
   * @param system flashed system
   * @return phase indices sorted by nC19 composition
   */
  private Integer[] phaseOrderByHeavyFraction(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order,
        Comparator.comparingDouble((Integer index) -> system.getPhase(index).getComponent("nC19").getx()));
    return order;
  }
}
