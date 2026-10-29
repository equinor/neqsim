package neqsim.thermo.component.attractiveeosterm;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.standards.gasquality.Standard_ISO18453;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentPR;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent finite-difference checks for both branches of the ISO 18453 water attraction function.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class AttractiveTermGERGTest {
  /** Temperature step in kelvin, kept away from the piecewise-function boundary. */
  private static final double STEP = 0.01;

  /**
   * Checks generic derivative APIs and legacy GERG aliases against values of alpha itself.
   *
   * @param temperature temperature in kelvin
   */
  @ParameterizedTest
  @ValueSource(doubles = {223.2, 240.0, 258.15, 268.15, 273.14, 273.18, 278.15, 298.15, 313.1})
  void alphaDerivativesMatchFiniteDifferences(double temperature) {
    AttractiveTermGERG term = new AttractiveTermGERG(new ComponentPR("water", 1.0, 1.0, 0));
    double center = term.alpha(temperature);
    double above = term.alpha(temperature + STEP);
    double below = term.alpha(temperature - STEP);
    double first = (above - below) / (2.0 * STEP);
    double second = (above - 2.0 * center + below) / (STEP * STEP);
    double firstTolerance = Math.max(1.0e-12, Math.abs(first) * 1.0e-7);
    double secondTolerance = Math.max(3.0e-11, Math.abs(second) * 3.0e-5);
    assertAll(() -> assertEquals(first, term.diffalphaT(temperature), firstTolerance),
        () -> assertEquals(second, term.diffdiffalphaT(temperature), secondTolerance),
        () -> assertEquals(first, term.diffalphaTGERG(temperature), firstTolerance),
        () -> assertEquals(second, term.diffdiffalphaTGERG(temperature), secondTolerance));
  }

  /**
   * Checks the dimensional attraction derivatives independently of the alpha derivative APIs.
   *
   * @param temperature temperature in kelvin
   */
  @ParameterizedTest
  @ValueSource(doubles = {223.2, 240.0, 258.15, 268.15, 273.14, 273.18, 278.15, 298.15, 313.1})
  void attractionDerivativesMatchFiniteDifferences(double temperature) {
    AttractiveTermGERG term = new AttractiveTermGERG(new ComponentPR("water", 1.0, 1.0, 0));
    double center = term.aT(temperature);
    double above = term.aT(temperature + STEP);
    double below = term.aT(temperature - STEP);
    double first = (above - below) / (2.0 * STEP);
    double second = (above - 2.0 * center + below) / (STEP * STEP);
    assertAll(() -> assertEquals(first, term.diffaT(temperature), Math.abs(first) * 1.0e-7),
        () -> assertEquals(second, term.diffdiffaT(temperature), Math.max(3.0e-6, Math.abs(second) * 3.0e-5)));
  }

  /**
   * Uses the published coefficients with ISO Table 2's critical temperature at the branch boundary.
   */
  @Test
  void usesIceUntilIsoTriplePoint() {
    ComponentPR water = new ComponentPR("water", 1.0, 1.0, 0);
    water.setTC(647.14);
    AttractiveTermGERG term = new AttractiveTermGERG(water);
    // Fixtures independently evaluated from ISO 18453:2004, clause 5.1.
    assertEquals(1.6766829230603155, term.alpha(273.155), 1.0e-14);
    assertEquals(1.6766650933988503, term.alpha(273.16), 1.0e-14);
    assertEquals(1.7310808151628914, term.alpha(258.15), 1.0e-14);
    assertEquals(1.6168848519475950, term.alpha(298.15), 1.0e-14);
  }

  /**
   * Preserves ordinary PR behaviour for methane on both sides of the water branch switch.
   */
  @Test
  void nonWaterRetainsPengRobinsonDerivatives() {
    ComponentPR methane = new ComponentPR("methane", 1.0, 1.0, 0);
    AttractiveTermGERG term = new AttractiveTermGERG(methane);
    AttractiveTermPr reference = new AttractiveTermPr(methane);
    for (double temperature : new double[] {240.0, 268.15, 298.15}) {
      assertEquals(reference.alpha(temperature), term.alpha(temperature), 0.0);
      assertEquals(reference.diffalphaT(temperature), term.diffalphaT(temperature), 0.0);
      assertEquals(reference.diffdiffalphaT(temperature), term.diffdiffalphaT(temperature), 0.0);
      assertEquals(reference.aT(temperature), term.aT(temperature), 0.0);
      assertEquals(reference.diffaT(temperature), term.diffaT(temperature), 0.0);
      assertEquals(reference.diffdiffaT(temperature), term.diffdiffaT(temperature), 0.0);
    }
  }

  /**
   * Repeated changes between ice and liquid coefficients must not leave derivatives on the previous branch.
   */
  @Test
  void alternatingBranchesHaveNoDerivativeStateLeak() {
    AttractiveTermGERG reused = new AttractiveTermGERG(new ComponentPR("water", 1.0, 1.0, 0));
    for (double temperature : new double[] {258.15, 298.15, 268.15, 278.15, 258.15}) {
      AttractiveTermGERG fresh = new AttractiveTermGERG(new ComponentPR("water", 1.0, 1.0, 0));
      assertEquals(fresh.diffdiffalphaT(temperature), reused.diffdiffalphaT(temperature), 0.0);
      assertEquals(fresh.diffalphaT(temperature), reused.diffalphaT(temperature), 0.0);
      assertEquals(fresh.alpha(temperature), reused.alpha(temperature), 0.0);
    }
  }

  /**
   * Checks actual gas-mixture thermodynamic identities and independently perturbed fugacity derivatives.
   *
   * <p>
   * Ten ppm water keeps this fixture on a single gas root. The nearly pure aqueous phase at higher moisture has
   * ill-conditioned trace-component mole differences; the separate two-phase test checks its temperature derivatives.
   * </p>
   *
   * @param temperature temperature in kelvin
   */
  @ParameterizedTest
  @ValueSource(doubles = {258.15, 268.15, 278.15, 298.15})
  void mixturePassesThermodynamicDerivativeChecks(double temperature) {
    SystemInterface gas = wetMixture(new SystemGERGwaterEos(temperature, 50.0), 10.0);
    gas.setMixingRule(8);
    new ThermodynamicOperations(gas).TPflash();
    gas.init(3);
    assertEquals(1, gas.getNumberOfPhases(), "The full-checker fixture must remain single-phase gas");
    assertEquals(PhaseType.GAS, gas.getPhase(0).getType(), "The full-checker fixture must use the gas root");
    ThermodynamicModelTest checker = new ThermodynamicModelTest(gas);
    assertAll(() -> assertTrue(checker.checkFugacityCoefficients(), "Residual Gibbs-energy identity"),
        () -> assertTrue(checker.checkFugacityCoefficientsDn(), "Gibbs-Duhem composition identity"),
        () -> assertTrue(checker.checkFugacityCoefficientsDn2(), "Composition-derivative symmetry"),
        () -> assertTrue(checker.checkFugacityCoefficientsDP(), "Pressure-derivative identity"),
        () -> assertTrue(checker.checkFugacityCoefficientsDT(), "Temperature-derivative identity"));
    checker.setMaxError(1.0e-5);
    assertTrue(checker.checkNumerically(), "Independently perturbed T, P and mole derivatives");
  }

  /**
   * Checks temperature derivatives in both phases without the unrelated trace-mole finite-difference conditioning.
   *
   * <p>
   * At 100 ppm water, low-temperature flashing produces a tiny, nearly pure aqueous phase. Its methane and heavier
   * component mole fractions can be below 1e-10, making absolute composition symmetry and numerical mole checks
   * unresolved. Temperature perturbations retain each phase root and composition and therefore test the corrected
   * derivative independently of that conditioning, without changing the native identity tolerance.
   * </p>
   *
   * @param temperature temperature in kelvin
   */
  @ParameterizedTest
  @ValueSource(doubles = {258.15, 268.15})
  void twoPhaseTemperatureDerivativesMatchIndependentPerturbations(double temperature) {
    SystemInterface gas = wetMixture(new SystemGERGwaterEos(temperature, 50.0), 100.0);
    gas.setMixingRule(8);
    new ThermodynamicOperations(gas).TPflash();
    gas.init(3);
    assertEquals(2, gas.getNumberOfPhases(), "The trace-aqueous fixture must retain both phases");
    ThermodynamicModelTest checker = new ThermodynamicModelTest(gas);
    assertAll(() -> assertTrue(checker.checkFugacityCoefficients(), "Residual Gibbs-energy identity in both phases"),
        () -> assertTrue(checker.checkFugacityCoefficientsDn(), "Gibbs-Duhem composition identity in both phases"),
        () -> assertTrue(checker.checkFugacityCoefficientsDP(), "Pressure-derivative identity in both phases"),
        () -> assertTrue(checker.checkFugacityCoefficientsDT(), "Temperature-derivative identity in both phases"));
    double step = 0.001;
    for (int phase = 0; phase < gas.getNumberOfPhases(); phase++) {
      SystemInterface above = SerializationUtils.clone(gas);
      above.getPhase(phase).setTemperature(temperature + step);
      above.init(1, phase);
      SystemInterface below = SerializationUtils.clone(gas);
      below.getPhase(phase).setTemperature(temperature - step);
      below.init(1, phase);
      for (int component = 0; component < gas.getPhase(phase).getNumberOfComponents(); component++) {
        double numerical = (above.getPhase(phase).getComponent(component).getLogFugacityCoefficient()
            - below.getPhase(phase).getComponent(component).getLogFugacityCoefficient()) / (2.0 * step);
        assertEquals(numerical, gas.getPhase(phase).getComponent(component).getdfugdt(), 1.0e-9,
            "Temperature derivative in phase " + phase + " for "
                + gas.getPhase(phase).getComponent(component).getName());
      }
    }
  }

  /**
   * Checks mixtures against an independent PR-root calculation and proves the wrapper supplies the ISO setup.
   *
   * <p>
   * References use ISO 18453:2004 clause 5 and Tables 2-3, dry methane/ethane/propane fractions 0.90/0.08/0.02, PR
   * constants 0.45724/0.07780, the largest physical gas root and the smallest pure-water root. Three cases (20 bara/50
   * ppm, 50 bara/200 ppm, 80 bara/200 ppm) are in the extended temperature range only. The 0.06 K tolerance accounts
   * for different bundled critical constants, not experimental correlation uncertainty.
   * </p>
   *
   * @param pressure absolute pressure in bar
   * @param waterPpm water mole fraction in parts per million of wet gas
   * @param referenceC independently calculated temperature in degrees Celsius
   * @throws Exception if the direct dew-point calculation fails
   */
  @ParameterizedTest
  @CsvSource({"20, 50, -20.384385956", "20, 100, -13.162368153", "20, 200, -5.468697886", "50, 50, -10.991263392",
      "50, 100, -3.449992264", "50, 200, 5.200565827", "80, 50, -6.273210011", "80, 100, 1.431937949",
      "80, 200, 10.576636779"})
  void isoWrapperMatchesExplicitGergSetup(double pressure, double waterPpm, double referenceC) throws Exception {
    SystemInterface explicit = wetMixture(new SystemGERGwaterEos(273.15, pressure), waterPpm);
    explicit.setMixingRule(8);
    new ThermodynamicOperations(explicit).waterDewPointTemperatureFlash();
    double directC = explicit.getTemperature("C");
    assertEquals(referenceC, directC, 0.06, "Independent ISO-model mixture control");

    SystemInterface otherModel = wetMixture(new SystemSrkEos(293.15, 1.0), waterPpm);
    otherModel.setMixingRule("classic");
    Standard_ISO18453 standard = new Standard_ISO18453(otherModel);
    standard.setPressure(pressure);
    standard.calculate();
    assertEquals(directC, standard.getValue("dewPointTemperature", "C"), 1.0e-7);
    assertEquals(pressure, standard.getValue("pressure"), 1.0e-12);
    assertEquals(293.15, otherModel.getTemperature(), 1.0e-12);
    assertEquals(1.0, otherModel.getPressure(), 1.0e-12);
  }

  /**
   * Adds normalized wet-gas composition while retaining a 90/8/2 dry-gas composition.
   *
   * @param gas fluid receiving the components
   * @param waterPpm water mole fraction in parts per million of wet gas
   * @return the supplied fluid with its composition defined
   */
  private SystemInterface wetMixture(SystemInterface gas, double waterPpm) {
    double water = waterPpm * 1.0e-6;
    double dry = 1.0 - water;
    gas.addComponent("methane", 0.90 * dry);
    gas.addComponent("ethane", 0.08 * dry);
    gas.addComponent("propane", 0.02 * dry);
    gas.addComponent("water", water);
    return gas;
  }
}
