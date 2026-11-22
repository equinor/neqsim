package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentUMRCPAvolcor;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRCPAEoS;
import neqsim.thermo.system.SystemUMRCPAvolcor;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the volume-translated UMR-CPA equation of state ({@link SystemUMRCPAvolcor} /
 * {@link PhaseUMRCPAvolcor} / {@link ComponentUMRCPAvolcor}).
 *
 * <p>
 * The tests check that (1) the extensive translation {@code C} and its temperature derivative are aggregated
 * consistently from the per-component Peneloux shifts (the new {@code C}-parameter plumbing in the Helmholtz
 * F-function), (2) the translated densities of non-associating components are positive and physically reasonable and
 * the translation actually shifts them relative to the untranslated UMR-CPA model, (3) the density-derived caloric and
 * acoustic properties (which use the temperature and volume derivatives of F) remain finite and physical, confirming
 * the derivatives are consistent, and (4) a water/methane mixture still flashes into the correct phases.
 * </p>
 *
 * <p>
 * Note: unlike a pure cubic EOS, the exact Peneloux identity {@code v_translated = v_base - c} does <em>not</em> hold
 * for the volume-translated CPA model, because the association term is evaluated at the physical (translated) volume;
 * the association contribution therefore perturbs the volume root. The tests below validate the model through robust
 * physical invariants instead of that identity.
 * </p>
 */
public class PhaseUMRCPAvolcorTest {
  @Test
  void testAddComponent() {
    PhaseUMRCPAvolcor phase = new PhaseUMRCPAvolcor();
    Assertions.assertEquals(0, phase.getNumberOfComponents());
    phase.addComponent("methane", 0, 0, 0);
    Assertions.assertEquals(1, phase.getNumberOfComponents());
    phase.addComponent("ethane", 0, 0, 1);
    Assertions.assertEquals(2, phase.getNumberOfComponents());
  }

  @Test
  void translationAggregationIsConsistent() {
    SystemInterface system = new SystemUMRCPAvolcor(300.0, 50.0);
    system.addComponent("methane", 0.7);
    system.addComponent("ethane", 0.3);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(3);

    PhaseUMRCPAvolcor phase = (PhaseUMRCPAvolcor) system.getPhase(0);
    int numberOfComponents = phase.getNumberOfComponents();

    // With the linear translation mixing rule c_ij = (c_i + c_j)/2 the extensive translation
    // reduces
    // to C = sum_i n_i c_i and dC/dT = sum_i n_i (dc_i/dT). Verify the phase aggregation matches.
    double expectedC = 0.0;
    double expectedCT = 0.0;
    double totalMoles = phase.getNumberOfMolesInPhase();
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentUMRCPAvolcor component = (ComponentUMRCPAvolcor) phase.getComponent(i);
      expectedC += component.getNumberOfMolesInPhase() * component.getc();
      expectedCT += component.getNumberOfMolesInPhase() * component.getcT();
    }

    Assertions.assertEquals(expectedC, phase.getC(), Math.max(1e-10, 1e-9 * Math.abs(expectedC)),
        "phase extensive translation C must equal sum_i n_i c_i");
    Assertions.assertEquals(expectedCT, phase.getCT(), Math.max(1e-10, 1e-9 * Math.abs(expectedCT)),
        "phase translation derivative dC/dT must equal sum_i n_i dc_i/dT");
    Assertions.assertEquals(phase.getC() / totalMoles, phase.getc(), Math.max(1e-12, 1e-9 * Math.abs(phase.getc())),
        "intensive translation c must equal C / n");
  }

  @Test
  void nonAssociatingDensitiesArePhysicalAndTranslationIsActive() {
    // Gas-phase methane: density must be positive and finite.
    SystemInterface gas = prepareSystem("methane", 300.0, 50.0, true);
    double gasDensity = gas.getPhase(0).getDensity("kg/m3");
    Assertions.assertTrue(gasDensity > 0.0 && Double.isFinite(gasDensity),
        "translated methane gas density must be positive and finite, was " + gasDensity);

    // Liquid-phase n-heptane: density must be positive, finite and physically reasonable.
    SystemInterface translated = prepareSystem("n-heptane", 300.0, 1.0, true);
    int translatedLiquidIndex = findLiquidPhaseIndex(translated);
    Assertions.assertTrue(translatedLiquidIndex >= 0, "Expected translated system to contain a liquid phase");
    double translatedDensity = translated.getPhase(translatedLiquidIndex).getDensity("kg/m3");
    Assertions.assertTrue(translatedDensity > 550.0 && translatedDensity < 760.0,
        "translated n-heptane liquid density out of physical range: " + translatedDensity);

    // The volume translation must actually change the predicted density relative to the
    // untranslated UMR-CPA model evaluated at the same conditions.
    SystemInterface base = prepareSystem("n-heptane", 300.0, 1.0, false);
    int baseLiquidIndex = findLiquidPhaseIndex(base);
    double baseDensity = base.getPhase(baseLiquidIndex).getDensity("kg/m3");
    Assertions.assertTrue(Math.abs(translatedDensity - baseDensity) > 1e-6,
        "volume translation should shift the predicted liquid density");
  }

  @Test
  void caloricAndAcousticPropertiesAreFiniteAndPhysical() {
    SystemInterface system = new SystemUMRCPAvolcor(300.0, 50.0);
    system.addComponent("methane", 0.9);
    system.addComponent("ethane", 0.1);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double density = system.getPhase(0).getDensity("kg/m3");
    Assertions.assertTrue(density > 0.0 && Double.isFinite(density), "density must be positive");

    double cp = system.getPhase(0).getCp("J/molK");
    Assertions.assertTrue(cp > 0.0 && Double.isFinite(cp), "Cp must be positive and finite");

    double soundSpeed = system.getPhase(0).getSoundSpeed();
    Assertions.assertTrue(soundSpeed > 0.0 && Double.isFinite(soundSpeed),
        "speed of sound must be positive and finite");

    double jt = system.getPhase(0).getJouleThomsonCoefficient();
    Assertions.assertTrue(Double.isFinite(jt), "Joule-Thomson coefficient must be finite");
  }

  /**
   * Checks phase equilibrium and component conservation through warm temperature changes.
   *
   * @param multiphase whether to run explicit multiphase stability checks
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void waterMethaneMixtureFlashesAndDensitiesArePositive(boolean multiphase) {
    SystemInterface system = new SystemUMRCPAvolcor(298.15, 50.0);
    system.addComponent("methane", 0.9);
    system.addComponent("water", 0.1);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    system.setMultiPhaseCheck(multiphase);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    for (double temperature : new double[] {298.15, 318.15, 278.15, 298.15}) {
      system.setTemperature(temperature);
      double previousGasBeta = Double.NaN;
      for (int repeat = 0; repeat < 2; repeat++) {
        ops.TPflash();
        system.initProperties();
        Assertions.assertEquals(2, system.getNumberOfPhases(), "methane/water must retain gas and aqueous phases");
        Assertions.assertTrue(system.hasPhaseType("gas"));
        Assertions.assertTrue(system.hasPhaseType("aqueous"));
        double betaSum = 0.0;
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
          double rho = system.getPhase(i).getDensity("kg/m3");
          Assertions.assertTrue(rho > 0.0 && Double.isFinite(rho), "all phase densities must be positive and finite");
          Assertions.assertTrue(system.getBeta(i) > 0.0 && system.getBeta(i) < 1.0);
          betaSum += system.getBeta(i);
          double xSum = 0.0;
          for (int component = 0; component < system.getNumberOfComponents(); component++) {
            xSum += system.getPhase(i).getComponent(component).getx();
          }
          Assertions.assertEquals(1.0, xSum, 1.0e-10, "phase compositions must be normalized");
        }
        Assertions.assertEquals(1.0, betaSum, 1.0e-10, "phase fractions must be normalized");
        for (int component = 0; component < system.getNumberOfComponents(); component++) {
          double overall = 0.0;
          for (int i = 0; i < system.getNumberOfPhases(); i++) {
            overall += system.getBeta(i) * system.getPhase(i).getComponent(component).getx();
          }
          Assertions.assertEquals(system.getPhase(0).getComponent(component).getz(), overall, 1.0e-8,
              "each feed component must be conserved");
          double logFugacity0 = Math.log(system.getPhase(0).getComponent(component).getx())
              + system.getPhase(0).getComponent(component).getLogFugacityCoefficient();
          double logFugacity1 = Math.log(system.getPhase(1).getComponent(component).getx())
              + system.getPhase(1).getComponent(component).getLogFugacityCoefficient();
          Assertions.assertEquals(logFugacity0, logFugacity1, 1.0e-8, "phase fugacities must agree");
        }
        double gasBeta = system.getPhase("gas").getBeta();
        if (repeat > 0) {
          Assertions.assertEquals(previousGasBeta, gasBeta, 1.0e-8, "repeated flashes must preserve the phase split");
        }
        previousGasBeta = gasBeta;
      }
    }
  }

  /** Checks translation derivatives after composition, mole scale and initialization-level changes. */
  @Test
  void translationDerivativesFollowCurrentInventory() {
    SystemInterface system = new SystemUMRCPAvolcor(350.0, 30.0);
    system.addComponent("methane", 0.7);
    system.addComponent("ethane", 0.3);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    system.init(0);
    system.setNumberOfPhases(1);
    for (double methaneFraction : new double[] {0.7, 0.2, 0.9, 0.7}) {
      system.setMolarComposition(new double[] {methaneFraction, 1.0 - methaneFraction});
      system.setTotalNumberOfMoles(10.0 * methaneFraction);
      for (int level : new int[] {1, 3, 1}) {
        system.init(level);
        PhaseUMRCPAvolcor phase = (PhaseUMRCPAvolcor) system.getPhase(0);
        for (int component = 0; component < system.getNumberOfComponents(); component++) {
          ComponentUMRCPAvolcor translated = (ComponentUMRCPAvolcor) phase.getComponent(component);
          Assertions.assertEquals(translated.getc(), translated.getCi(), 1.0e-12,
              "linear translation derivative must use the current component inventory");
        }
        Assertions.assertEquals(system.getPressure(), phase.calcPressure(), 1.0e-7,
            "the translated volume must satisfy the current EOS pressure equation");
      }
    }
  }

  /**
   * Checks translated fugacity identities and numerical derivatives in well-conditioned gas states.
   *
   * @param associating whether to include undersaturated water instead of ethane
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void translatedGasFugacityDerivativesAreConsistent(boolean associating) {
    SystemInterface system = new SystemUMRCPAvolcor(350.0, 30.0);
    system.addComponent("methane", associating ? 0.999 : 0.7);
    system.addComponent(associating ? "water" : "ethane", associating ? 0.001 : 0.3);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    system.setMultiPhaseCheck(true);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    Assertions.assertEquals(1, system.getNumberOfPhases());
    ThermodynamicModelTest modelTest = new ThermodynamicModelTest(system);
    Assertions.assertTrue(modelTest.checkFugacityCoefficients());
    Assertions.assertTrue(modelTest.checkFugacityCoefficientsDP());
    Assertions.assertTrue(modelTest.checkFugacityCoefficientsDT());
    Assertions.assertTrue(modelTest.checkFugacityCoefficientsDn2());
    if (!associating) {
      // The stricter CPA composition identity has the existing limitation documented in
      // SystemUMRCPAEoSPaperValidationTest, independent of the volume translation.
      Assertions.assertTrue(modelTest.checkFugacityCoefficientsDn());
    }
    modelTest.setMaxError(1.0e-5);
    Assertions.assertTrue(modelTest.checkNumerically());
  }

  /**
   * Builds and flashes a single-/multi-component UMR-CPA system. When {@code translated} is true the volume-translated
   * {@link SystemUMRCPAvolcor} is used; otherwise the untranslated {@link SystemUMRCPAEoS} with the legacy density-only
   * correction switched off is used as the reference.
   */
  private SystemInterface prepareSystem(String componentName, double temperature, double pressure, boolean translated) {
    SystemInterface system;
    if (translated) {
      system = new SystemUMRCPAvolcor(temperature, pressure);
    } else {
      system = new SystemUMRCPAEoS(temperature, pressure);
      system.useVolumeCorrection(false);
    }
    system.addComponent(componentName, 1.0);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    return system;
  }

  private int findLiquidPhaseIndex(SystemInterface system) {
    int index = findPhaseIndexByType(system, "oil");
    if (index >= 0) {
      return index;
    }
    index = findPhaseIndexByType(system, "aqueous");
    if (index >= 0) {
      return index;
    }
    index = findPhaseIndexByType(system, "liquid");
    if (index >= 0) {
      return index;
    }
    double densestDensity = Double.NEGATIVE_INFINITY;
    int densestIndex = -1;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      double density = system.getPhase(phaseIndex).getDensity();
      if (density > densestDensity) {
        densestDensity = density;
        densestIndex = phaseIndex;
      }
    }
    return densestIndex;
  }

  private int findPhaseIndexByType(SystemInterface system, String phaseTypeName) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getPhaseTypeName().equals(phaseTypeName)) {
        return phaseIndex;
      }
    }
    return -1;
  }
}
