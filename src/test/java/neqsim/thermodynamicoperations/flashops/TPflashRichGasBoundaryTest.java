package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualification of the rich-gas cubic-EOS phase boundary near the cricondenbar.
 *
 * <p>
 * The legacy regression executed the SRK state at -8 degrees Celsius without an assertion. This class makes that
 * endpoint, its neighboring topology, and its initialization/history contracts enforceable for both SRK and PR.
 * </p>
 */
class TPflashRichGasBoundaryTest {
  private static final double REFERENCE_PRESSURE_BARA = 100.0;

  /** Qualifies the SRK phase boundary and known topology anchors. */
  @Test
  void srkBoundaryIsClosedAndHistoryIndependent() {
    qualifyBoundary(false, "SRK", true);
  }

  /** Qualifies the PR phase boundary without imposing SRK-specific topology. */
  @Test
  void prBoundaryIsClosedAndHistoryIndependent() {
    qualifyBoundary(true, "PR", false);
  }

  /**
   * Runs the complete boundary matrix for one cubic equation of state.
   *
   * @param pengRobinson true for PR, false for SRK
   * @param modelLabel model name used in assertion messages
   * @param requireSrkTopology whether to enforce the established SRK topology anchors
   */
  private void qualifyBoundary(boolean pengRobinson, String modelLabel, boolean requireSrkTopology) {
    double[] temperaturesC = { -8.0, 0.0, 10.0, 30.0 };
    for (double temperatureC : temperaturesC) {
      SystemInterface ordinary = flash(pengRobinson, temperatureC, REFERENCE_PRESSURE_BARA, false, false);
      SystemInterface multiphase = flash(pengRobinson, temperatureC, REFERENCE_PRESSURE_BARA, true, false);
      assertClosedEquilibrium(ordinary, modelLabel + " ordinary at " + temperatureC + " C");
      assertClosedEquilibrium(multiphase, modelLabel + " multiphase at " + temperatureC + " C");
      assertEquivalentState(ordinary, multiphase, 1.0e-8, modelLabel + " path agreement at " + temperatureC + " C");
    }

    if (requireSrkTopology) {
      assertEquals(2, flash(false, 0.0, REFERENCE_PRESSURE_BARA, false, false).getNumberOfPhases(),
          "SRK at 0 C and 100 bara");
      assertEquals(2, flash(false, 10.0, REFERENCE_PRESSURE_BARA, false, false).getNumberOfPhases(),
          "SRK at 10 C and 100 bara");
      assertEquals(1, flash(false, 30.0, REFERENCE_PRESSURE_BARA, false, false).getNumberOfPhases(),
          "SRK at 30 C and 100 bara");
    }

    SystemInterface boundaryReference = flash(pengRobinson, -8.0, REFERENCE_PRESSURE_BARA, true, false);
    SystemInterface boundaryPoorGuess = flash(pengRobinson, -8.0, REFERENCE_PRESSURE_BARA, true, true);
    assertEquivalentState(boundaryReference, boundaryPoorGuess, 1.0e-8, modelLabel + " -8 C poor initialization");

    SystemInterface interiorReference = flash(pengRobinson, 0.0, REFERENCE_PRESSURE_BARA, true, false);
    SystemInterface interiorPoorGuess = flash(pengRobinson, 0.0, REFERENCE_PRESSURE_BARA, true, true);
    assertEquivalentState(interiorReference, interiorPoorGuess, 1.0e-8, modelLabel + " 0 C poor initialization");

    SystemInterface lowerPressure = flash(pengRobinson, 0.0, 50.0, true, false);
    SystemInterface lowerPressureOrdinary = flash(pengRobinson, 0.0, 50.0, false, false);
    assertClosedEquilibrium(lowerPressure, modelLabel + " 50 bara multiphase");
    assertEquivalentState(lowerPressureOrdinary, lowerPressure, 1.0e-8, modelLabel + " 50 bara path agreement");

    SystemInterface reused = interiorReference.clone();
    reused.setTemperature(30.0, "C");
    new ThermodynamicOperations(reused).TPflash();
    reused.initProperties();
    assertEquivalentState(flash(pengRobinson, 30.0, REFERENCE_PRESSURE_BARA, true, false), reused, 1.0e-8,
        modelLabel + " phase disappearance");

    reused.setTemperature(-8.0, "C");
    new ThermodynamicOperations(reused).TPflash();
    reused.initProperties();
    assertEquivalentState(boundaryReference, reused, 1.0e-8, modelLabel + " boundary return");

    reused.setTemperature(0.0, "C");
    new ThermodynamicOperations(reused).TPflash();
    reused.initProperties();
    assertEquivalentState(interiorReference, reused, 1.0e-8, modelLabel + " phase reappearance");

    SystemInterface repeated = reused.clone();
    new ThermodynamicOperations(reused).TPflash();
    reused.initProperties();
    assertEquivalentState(repeated, reused, 1.0e-10, modelLabel + " deterministic repeat");
  }

  /**
   * Creates and flashes the synthetic rich-gas state.
   *
   * @param pengRobinson true for PR, false for SRK
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBara absolute pressure in bar
   * @param multiphaseCheck whether to run the explicit multiphase path
   * @param poorGuess whether to initialize phase fractions near a bound
   * @return initialized flashed system
   */
  private SystemInterface flash(boolean pengRobinson, double temperatureC, double pressureBara, boolean multiphaseCheck,
      boolean poorGuess) {
    SystemInterface system = createSystem(pengRobinson);
    system.setTemperature(temperatureC, "C");
    system.setPressure(pressureBara, "bara");
    system.setMultiPhaseCheck(multiphaseCheck);
    if (poorGuess) {
      system.setBeta(0, 1.0e-12);
      system.setBeta(1, 1.0 - 1.0e-12);
    }
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
    return system;
  }

  /**
   * Creates the established ten-component rich-gas regression fluid.
   *
   * @param pengRobinson true for PR, false for SRK
   * @return configured cubic-EOS system
   */
  private SystemInterface createSystem(boolean pengRobinson) {
    SystemInterface system = pengRobinson ? new neqsim.thermo.system.SystemPrEos(273.15, REFERENCE_PRESSURE_BARA)
        : new neqsim.thermo.system.SystemSrkEos(273.15, REFERENCE_PRESSURE_BARA);
    system.addComponent("nitrogen", 3.43);
    system.addComponent("CO2", 0.34);
    system.addComponent("methane", 62.51);
    system.addComponent("ethane", 15.65);
    system.addComponent("propane", 13.22);
    system.addComponent("i-butane", 1.61);
    system.addComponent("n-butane", 2.48);
    system.addComponent("i-pentane", 0.35);
    system.addComponent("n-pentane", 0.29);
    system.addComponent("n-hexane", 0.12);
    system.setMixingRule(2);
    return system;
  }

  /**
   * Verifies normalization, material balance, fugacity equality, and finite properties.
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
   * Compares equilibria after ordering phases by n-hexane mole fraction.
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
   * Orders active phases by n-hexane content.
   *
   * @param system flashed system
   * @return phase indices from leanest to richest in n-hexane
   */
  private Integer[] phaseOrderByHeavyFraction(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order,
        Comparator.comparingDouble((Integer index) -> system.getPhase(index).getComponent("n-hexane").getx()));
    return order;
  }
}
