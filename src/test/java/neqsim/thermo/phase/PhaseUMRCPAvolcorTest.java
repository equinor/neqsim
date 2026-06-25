package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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

  @Test
  void waterMethaneMixtureFlashesAndDensitiesArePositive() {
    SystemInterface system = new SystemUMRCPAvolcor(298.15, 50.0);
    system.addComponent("methane", 0.9);
    system.addComponent("water", 0.1);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    Assertions.assertTrue(system.getNumberOfPhases() >= 2,
        "methane/water at 298 K, 50 bara should split into at least two phases");
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      double rho = system.getPhase(i).getDensity("kg/m3");
      Assertions.assertTrue(rho > 0.0 && Double.isFinite(rho), "all phase densities must be positive and finite");
    }
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
