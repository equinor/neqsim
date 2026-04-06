package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseElectrolyteCPAAdvanced;
import neqsim.thermo.util.constants.IonParametersAdvanced;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the e-CPA-Advanced electrolyte equation of state.
 *
 * @author Even Solbraa
 */
class SystemElectrolyteCPAAdvancedTest {

  @Test
  void testIonParametersAdvanced() {
    // Verify that ion parameters load correctly
    IonParametersAdvanced.AdvancedIonData naData = IonParametersAdvanced.getIonData("Na+");
    assertNotNull(naData, "Na+ data should exist");
    assertEquals(1, naData.charge, "Na+ charge should be +1");
    assertEquals(2.36, naData.sigma, 0.01, "Na+ sigma");
    assertTrue(naData.w0 != 0, "Na+ W0 should be non-zero (fitted to NaCl data)");
    assertTrue(naData.rBorn0 > 0, "Na+ Born radius should be positive");
    assertEquals(-405.0, naData.dGhydration, 1.0, "Na+ hydration Gibbs energy");

    IonParametersAdvanced.AdvancedIonData clData = IonParametersAdvanced.getIonData("Cl-");
    assertNotNull(clData, "Cl- data should exist");
    assertEquals(-1, clData.charge, "Cl- charge should be -1");
    assertTrue(clData.sigma > naData.sigma, "Cl- should be larger than Na+");

    IonParametersAdvanced.AdvancedIonData caData = IonParametersAdvanced.getIonData("Ca++");
    assertNotNull(caData, "Ca++ data should exist");
    assertEquals(2, caData.charge, "Ca++ charge should be +2");

    IonParametersAdvanced.AdvancedIonData so4Data = IonParametersAdvanced.getIonData("SO4--");
    assertNotNull(so4Data, "SO4-- data should exist");
    assertEquals(-2, so4Data.charge, "SO4-- charge should be -2");
  }

  @Test
  void testIonPairData() {
    // Verify ion pair formation data
    IonParametersAdvanced.IonPairData mgso4 = IonParametersAdvanced.getIonPairData("Mg++", "SO4--");
    assertNotNull(mgso4, "MgSO4 ion pair data should exist");
    assertEquals(160.0, mgso4.k0, 1.0, "MgSO4 K_IP at 25C");
    assertTrue(mgso4.dH > 0, "Ion pair formation enthalpy should be positive");

    // Test temperature dependence
    double kIP_25 = IonParametersAdvanced.calcIonPairConstant("Mg++", "SO4--", 298.15);
    double kIP_50 = IonParametersAdvanced.calcIonPairConstant("Mg++", "SO4--", 323.15);
    assertTrue(kIP_50 > kIP_25, "K_IP should increase with temperature (endothermic pairing)");
  }

  @Test
  void testWTemperatureDependence() {
    // Verify W(T) calculation
    double w25 = IonParametersAdvanced.calcW("Na+", 298.15);
    double w50 = IonParametersAdvanced.calcW("Na+", 323.15);
    double w0 = IonParametersAdvanced.calcW("Na+", 273.15);

    // W at reference T reflects fitted value; sign depends on fitting
    assertTrue(Math.abs(w25) > 0, "W should be non-zero at reference temperature");
    // Na+ has negative WT, so W should decrease at higher T
    assertTrue(w50 < w25, "W should decrease at higher T for Na+ (negative WT)");

    // Check derivative
    double dwdT = IonParametersAdvanced.calcWdT("Na+", 298.15);
    double dwdT_numerical =
        (IonParametersAdvanced.calcW("Na+", 298.16) - IonParametersAdvanced.calcW("Na+", 298.14))
            / 0.02;
    assertEquals(dwdT, dwdT_numerical, 1e-12, "Analytical and numerical dW/dT should match");
  }

  @Test
  void testBornRadiusTemperatureDependence() {
    double rB_25 = IonParametersAdvanced.calcBornRadius("Na+", 298.15);
    double rB_100 = IonParametersAdvanced.calcBornRadius("Na+", 373.15);
    assertTrue(rB_100 > rB_25,
        "Born radius should increase with temperature (thermal expansion of cavity)");
    assertTrue(rB_25 > 1.0, "Born radius should be > 1 Angstrom");
    assertTrue(rB_25 < 4.0, "Born radius should be < 4 Angstrom");
  }

  @Test
  void testBasicTPflashNaCl() {
    SystemInterface system = new SystemElectrolyteCPAAdvanced(298.15, 1.01325);
    system.addComponent("water", 1.0);
    system.addComponent("Na+", 0.01);
    system.addComponent("Cl-", 0.01);
    system.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Single-phase aqueous system (no gas component), so phase 0 is the aqueous phase
    double density = system.getPhase(0).getDensity("kg/m3");
    assertTrue(density > 900.0 && density < 1200.0,
        "Aqueous phase density should be 900-1200 kg/m3, got " + density);

    // Check fugacity coefficients are reasonable
    double fugCoeffWater = system.getPhase(0).getComponent("water").getFugacityCoefficient();
    assertTrue(fugCoeffWater > 0.0 && fugCoeffWater < 100.0,
        "Water fugacity coefficient should be reasonable, got " + fugCoeffWater);
  }

  @Test
  void testNaClWithGas() {
    SystemInterface system = new SystemElectrolyteCPAAdvanced(298.15, 10.0);
    system.addComponent("methane", 0.1);
    system.addComponent("water", 1.0);
    system.addComponent("Na+", 0.01);
    system.addComponent("Cl-", 0.01);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Should have at least one phase
    assertTrue(system.getNumberOfPhases() >= 1, "Should have at least 1 phase");

    // Aqueous phase density should be reasonable
    if (system.hasPhaseType("aqueous")) {
      double density = system.getPhase("aqueous").getDensity("kg/m3");
      assertTrue(density > 950.0 && density < 1200.0,
          "Brine density should be reasonable, got " + density);
    }
  }

  @Test
  void testCaCl2System() {
    SystemInterface system = new SystemElectrolyteCPAAdvanced(298.15, 1.01325);
    system.addComponent("water", 1.0);
    system.addComponent("Ca++", 0.005);
    system.addComponent("Cl-", 0.010);
    system.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Single-phase aqueous system, use phase 0
    double density = system.getPhase(0).getDensity("kg/m3");
    assertTrue(density > 950.0 && density < 1300.0,
        "CaCl2 brine density should be reasonable, got " + density);
  }

  @Test
  void testPhaseCloning() {
    SystemInterface system = new SystemElectrolyteCPAAdvanced(298.15, 1.01325);
    system.addComponent("water", 1.0);
    system.addComponent("Na+", 0.01);
    system.addComponent("Cl-", 0.01);
    system.setMixingRule(10);

    SystemInterface clone = system.clone();
    assertNotNull(clone, "Cloned system should not be null");
    assertEquals(system.getNumberOfComponents(), clone.getNumberOfComponents(),
        "Component count should match");
  }

  @Test
  void testAdvancedBornEnergy() {
    SystemInterface system = new SystemElectrolyteCPAAdvanced(298.15, 1.01325);
    system.addComponent("water", 1.0);
    system.addComponent("Na+", 0.01);
    system.addComponent("Cl-", 0.01);
    system.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Access advanced phase (single-phase system, index 0)
    if (system.getPhase(0) instanceof PhaseElectrolyteCPAAdvanced) {
      PhaseElectrolyteCPAAdvanced advPhase = (PhaseElectrolyteCPAAdvanced) system.getPhase(0);
      double bornEnergy = advPhase.getAdvancedBornEnergy();
      assertTrue(bornEnergy < 0, "Born solvation energy should be negative (stabilizing)");
    }
  }

  @Test
  void testModelNameAndType() {
    SystemElectrolyteCPAAdvanced system = new SystemElectrolyteCPAAdvanced(298.15, 1.0);
    assertEquals("Electrolyte-CPA-Advanced", system.getModelName(), "Model name should be correct");
  }

  @Test
  void testHofmeisterSeries() {
    // Verify that Born radii follow the Hofmeister series
    // For monovalent cations: Li+ < Na+ < K+ < Rb+ < Cs+ (Born radius increases)
    double rLi = IonParametersAdvanced.getIonData("Li+").rBorn0;
    double rNa = IonParametersAdvanced.getIonData("Na+").rBorn0;
    double rK = IonParametersAdvanced.getIonData("K+").rBorn0;
    double rRb = IonParametersAdvanced.getIonData("Rb+").rBorn0;
    double rCs = IonParametersAdvanced.getIonData("Cs+").rBorn0;

    assertTrue(rLi < rNa, "Li+ should have smaller Born radius than Na+ (Hofmeister)");
    assertTrue(rNa < rK, "Na+ should have smaller Born radius than K+ (Hofmeister)");
    assertTrue(rK < rRb, "K+ Born radius should be smaller than Rb+ (Hofmeister)");
    assertTrue(rRb < rCs, "Rb+ Born radius should be smaller than Cs+ (Hofmeister)");

    // Hydration energies should follow: |Li+| > |Na+| > |K+| (stronger hydration)
    double dGLi = Math.abs(IonParametersAdvanced.getIonData("Li+").dGhydration);
    double dGNa = Math.abs(IonParametersAdvanced.getIonData("Na+").dGhydration);
    double dGK = Math.abs(IonParametersAdvanced.getIonData("K+").dGhydration);

    assertTrue(dGLi > dGNa, "Li+ should have stronger hydration than Na+");
    assertTrue(dGNa > dGK, "Na+ should have stronger hydration than K+");

    // Hard-sphere diameters: Li+ < Na+ < K+ (ionic radii increase)
    double sLi = IonParametersAdvanced.getIonData("Li+").sigma;
    double sNa = IonParametersAdvanced.getIonData("Na+").sigma;
    double sK = IonParametersAdvanced.getIonData("K+").sigma;

    assertTrue(sLi < sNa, "Li+ sigma should be smaller than Na+");
    assertTrue(sNa < sK, "Na+ sigma should be smaller than K+");
  }

  @Test
  void testBornRadiiCorrelateWithHydrationEnergy() {
    // Born radii should correlate with hydration energy:
    // stronger hydration (more negative dGhyd) -> smaller Born radius
    IonParametersAdvanced.AdvancedIonData li = IonParametersAdvanced.getIonData("Li+");
    IonParametersAdvanced.AdvancedIonData na = IonParametersAdvanced.getIonData("Na+");
    IonParametersAdvanced.AdvancedIonData k = IonParametersAdvanced.getIonData("K+");

    // Li+ has more negative dGhyd than Na+ -> should have smaller Born radius
    assertTrue(li.rBorn0 < na.rBorn0,
        "Li+ Born radius should be smaller than Na+ (stronger hydration)");
    assertTrue(na.rBorn0 < k.rBorn0,
        "Na+ Born radius should be smaller than K+ (stronger hydration)");

    // Same for anions
    IonParametersAdvanced.AdvancedIonData f = IonParametersAdvanced.getIonData("F-");
    IonParametersAdvanced.AdvancedIonData cl = IonParametersAdvanced.getIonData("Cl-");
    IonParametersAdvanced.AdvancedIonData br = IonParametersAdvanced.getIonData("Br-");

    assertTrue(f.rBorn0 < cl.rBorn0,
        "F- Born radius should be smaller than Cl- (stronger hydration)");
    assertTrue(cl.rBorn0 < br.rBorn0,
        "Cl- Born radius should be smaller than Br- (stronger hydration)");
  }

  @Test
  void testTemperatureSensitivity() {
    // Run flash at two temperatures and check density changes correctly
    SystemInterface system25 = new SystemElectrolyteCPAAdvanced(298.15, 1.01325);
    system25.addComponent("water", 1.0);
    system25.addComponent("Na+", 0.01);
    system25.addComponent("Cl-", 0.01);
    system25.setMixingRule(10);

    SystemInterface system80 = new SystemElectrolyteCPAAdvanced(353.15, 1.01325);
    system80.addComponent("water", 1.0);
    system80.addComponent("Na+", 0.01);
    system80.addComponent("Cl-", 0.01);
    system80.setMixingRule(10);

    ThermodynamicOperations ops25 = new ThermodynamicOperations(system25);
    ops25.TPflash();
    system25.initProperties();

    ThermodynamicOperations ops80 = new ThermodynamicOperations(system80);
    ops80.TPflash();
    system80.initProperties();

    double rho25 = system25.getPhase(0).getDensity("kg/m3");
    double rho80 = system80.getPhase(0).getDensity("kg/m3");

    // Density should decrease with temperature
    assertTrue(rho80 < rho25,
        "Brine density at 80C (" + rho80 + ") should be less than at 25C (" + rho25 + ")");
  }
}
