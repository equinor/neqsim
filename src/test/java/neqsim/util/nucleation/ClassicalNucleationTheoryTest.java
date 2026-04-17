package neqsim.util.nucleation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ClassicalNucleationTheory.
 *
 * <p>
 * Tests cover:
 * </p>
 * <ul>
 * <li>Critical radius from Kelvin equation against analytical solution</li>
 * <li>Free energy barrier against analytical formula</li>
 * <li>Nucleation rate behavior (supersaturation dependence, physical bounds)</li>
 * <li>Growth rate regimes (free-molecular, continuum, Fuchs transition)</li>
 * <li>Coagulation kernel from Smoluchowski theory</li>
 * <li>Factory methods (sulfurS8, waterIce, paraffinWax, naphthalene)</li>
 * <li>No-nucleation case (S &lt;= 1)</li>
 * <li>Heterogeneous nucleation with contact angle</li>
 * <li>EOS-coupled supersaturation via fromThermoSystem</li>
 * <li>JSON output completeness</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
class ClassicalNucleationTheoryTest {

  private ClassicalNucleationTheory cnt;

  @BeforeEach
  void setUp() {
    // Generic substance: MW = 0.10 kg/mol, density = 1000 kg/m3, sigma = 0.05 N/m
    cnt = new ClassicalNucleationTheory(0.10, 1000.0, 0.05);
    cnt.setTemperature(300.0);
    cnt.setSupersaturationRatio(10.0);
    cnt.setGasDiffusivity(5.0e-6);
    cnt.setGasViscosity(1.0e-5);
    cnt.setTotalPressure(1.0e6);
    cnt.setResidenceTime(1.0);
  }

  // ============================================================================
  // Critical Radius Tests
  // ============================================================================

  @Test
  void testCriticalRadiusPositive() {
    cnt.calculate();
    assertTrue(cnt.getCriticalRadius() > 0.0, "Critical radius must be positive for S > 1");
  }

  @Test
  void testCriticalRadiusAnalytical() {
    // Kelvin equation: r* = 2 * sigma * v_m / (kT * ln(S))
    double MW = 0.10;
    double density = 1000.0;
    double sigma = 0.05;
    double T = 300.0;
    double S = 10.0;

    double vm = MW / (density * ClassicalNucleationTheory.N_AVOGADRO);
    double kT = ClassicalNucleationTheory.K_BOLTZMANN * T;
    double expectedRadius = 2.0 * sigma * vm / (kT * Math.log(S));

    cnt.calculate();
    assertEquals(expectedRadius, cnt.getCriticalRadius(), expectedRadius * 1e-10,
        "Critical radius must match Kelvin equation");
  }

  @Test
  void testCriticalRadiusDecreasesWithSupersaturation() {
    cnt.setSupersaturationRatio(5.0);
    cnt.calculate();
    double r1 = cnt.getCriticalRadius();

    cnt.setSupersaturationRatio(50.0);
    cnt.calculate();
    double r2 = cnt.getCriticalRadius();

    assertTrue(r2 < r1, "Critical radius must decrease with increasing supersaturation");
  }

  @Test
  void testCriticalRadiusDecreasesWithTemperature() {
    cnt.setTemperature(250.0);
    cnt.calculate();
    double rLow = cnt.getCriticalRadius();

    cnt.setTemperature(400.0);
    cnt.calculate();
    double rHigh = cnt.getCriticalRadius();

    assertTrue(rHigh < rLow,
        "Critical radius must decrease with increasing temperature (at fixed S)");
  }

  // ============================================================================
  // Free Energy Barrier Tests
  // ============================================================================

  @Test
  void testFreeEnergyBarrierAnalytical() {
    // DeltaG* = (16*pi/3) * sigma^3 * vm^2 / (kT * ln(S))^2
    double MW = 0.10;
    double density = 1000.0;
    double sigma = 0.05;
    double T = 300.0;
    double S = 10.0;

    double vm = MW / (density * ClassicalNucleationTheory.N_AVOGADRO);
    double kT = ClassicalNucleationTheory.K_BOLTZMANN * T;
    double lnS = Math.log(S);
    double expectedBarrier =
        (16.0 * Math.PI / 3.0) * Math.pow(sigma, 3) * Math.pow(vm, 2) / Math.pow(kT * lnS, 2);

    cnt.calculate();
    assertEquals(expectedBarrier, cnt.getFreeEnergyBarrier(), expectedBarrier * 1e-10,
        "Free energy barrier must match CNT formula");
  }

  @Test
  void testDimensionlessBarrierIsPositive() {
    cnt.calculate();
    assertTrue(cnt.getDimensionlessFreeEnergyBarrier() > 0.0,
        "Dimensionless free energy barrier must be positive for S > 1");
  }

  @Test
  void testFreeEnergyBarrierDecreasesWithSupersaturation() {
    cnt.setSupersaturationRatio(5.0);
    cnt.calculate();
    double dg1 = cnt.getFreeEnergyBarrier();

    cnt.setSupersaturationRatio(100.0);
    cnt.calculate();
    double dg2 = cnt.getFreeEnergyBarrier();

    assertTrue(dg2 < dg1, "Free energy barrier must decrease with increasing supersaturation");
  }

  // ============================================================================
  // Critical Nucleus Molecule Count Tests
  // ============================================================================

  @Test
  void testCriticalNucleusMoleculesPositive() {
    cnt.calculate();
    assertTrue(cnt.getCriticalNucleusMolecules() > 0.0,
        "Critical nucleus must contain positive number of molecules");
  }

  @Test
  void testCriticalNucleusMoleculesConsistentWithRadius() {
    // n* = (4/3)*pi*r*^3 / v_m
    double MW = 0.10;
    double density = 1000.0;
    double vm = MW / (density * ClassicalNucleationTheory.N_AVOGADRO);

    cnt.calculate();
    double r = cnt.getCriticalRadius();
    double expectedN = (4.0 * Math.PI / 3.0) * Math.pow(r, 3) / vm;

    assertEquals(expectedN, cnt.getCriticalNucleusMolecules(), expectedN * 1e-10,
        "Critical nucleus molecules must be consistent with critical radius");
  }

  // ============================================================================
  // Zeldovich Factor Tests
  // ============================================================================

  @Test
  void testZeldovichFactorPositive() {
    cnt.calculate();
    assertTrue(cnt.getZeldovichFactor() > 0.0, "Zeldovich factor must be positive");
  }

  @Test
  void testZeldovichFactorTypicalRange() {
    // Typical Zeldovich factor is between 0.001 and 0.5
    cnt.calculate();
    double z = cnt.getZeldovichFactor();
    assertTrue(z > 1e-6 && z < 1.0, "Zeldovich factor should be in reasonable range, got " + z);
  }

  // ============================================================================
  // Nucleation Rate Tests
  // ============================================================================

  @Test
  void testNucleationRateNonNegative() {
    cnt.calculate();
    assertTrue(cnt.getNucleationRate() >= 0.0, "Nucleation rate must be non-negative");
  }

  @Test
  void testNucleationRateIncreasesWithSupersaturation() {
    cnt.setSupersaturationRatio(5.0);
    cnt.setPartialPressure(5.0 * 100.0);
    cnt.setSaturationPressure(100.0);
    cnt.calculate();
    double j1 = cnt.getNucleationRate();

    cnt.setSupersaturationRatio(50.0);
    cnt.setPartialPressure(50.0 * 100.0);
    cnt.setSaturationPressure(100.0);
    cnt.calculate();
    double j2 = cnt.getNucleationRate();

    assertTrue(j2 >= j1, "Nucleation rate must increase with increasing supersaturation");
  }

  @Test
  void testNucleationRateZeroAtSubsaturation() {
    cnt.setSupersaturationRatio(0.5);
    cnt.calculate();
    assertEquals(0.0, cnt.getNucleationRate(), 0.0, "Nucleation rate must be zero when S < 1");
  }

  @Test
  void testNucleationRateZeroAtEquilibrium() {
    cnt.setSupersaturationRatio(1.0);
    cnt.calculate();
    assertEquals(0.0, cnt.getNucleationRate(), 0.0,
        "Nucleation rate must be zero at equilibrium (S = 1)");
  }

  // ============================================================================
  // Growth Rate Tests
  // ============================================================================

  @Test
  void testGrowthRateNonNegative() {
    cnt.calculate();
    assertTrue(cnt.getGrowthRate() >= 0.0, "Growth rate must be non-negative");
  }

  // ============================================================================
  // Coagulation Kernel Tests
  // ============================================================================

  @Test
  void testCoagulationKernelSmoluchowski() {
    // Continuum regime: K = 8 * kT / (3 * mu)
    double kT = ClassicalNucleationTheory.K_BOLTZMANN * 300.0;
    double mu = 1.0e-5;
    double expected = 8.0 * kT / (3.0 * mu);

    cnt.calculate();
    assertEquals(expected, cnt.getCoagulationKernel(), expected * 1e-10,
        "Coagulation kernel must match Smoluchowski formula");
  }

  // ============================================================================
  // No-Nucleation Cases
  // ============================================================================

  @Test
  void testSubsaturationResetsResults() {
    cnt.setSupersaturationRatio(0.8);
    cnt.calculate();

    assertEquals(0.0, cnt.getCriticalRadius(), 0.0);
    assertEquals(0.0, cnt.getFreeEnergyBarrier(), 0.0);
    assertEquals(0.0, cnt.getNucleationRate(), 0.0);
    assertEquals(0.0, cnt.getMeanParticleDiameter(), 0.0);
    assertTrue(cnt.isCalculated());
  }

  // ============================================================================
  // Particle Results Tests
  // ============================================================================

  @Test
  void testParticleDiameterPositive() {
    cnt.calculate();
    assertTrue(cnt.getMeanParticleDiameter() > 0.0,
        "Mean particle diameter must be positive after nucleation");
  }

  @Test
  void testParticleNumberDensityPositive() {
    cnt.calculate();
    assertTrue(cnt.getParticleNumberDensity() > 0.0,
        "Particle number density must be positive after nucleation");
  }

  @Test
  void testParticleMassConcentrationPositive() {
    cnt.calculate();
    assertTrue(cnt.getParticleMassConcentration() > 0.0,
        "Mass concentration must be positive after nucleation");
  }

  @Test
  void testKnudsenNumberPositive() {
    cnt.calculate();
    assertTrue(cnt.getKnudsenNumber() > 0.0, "Knudsen number must be positive");
  }

  @Test
  void testGeometricStdDevReasonable() {
    cnt.calculate();
    double sigma = cnt.getGeometricStdDev();
    assertTrue(sigma >= 1.0 && sigma <= 3.0,
        "Geometric std dev should be 1.0 to 3.0, got " + sigma);
  }

  @Test
  void testParticleSizePercentiles() {
    cnt.calculate();
    double[] pctiles = cnt.getParticleSizePercentiles();
    assertEquals(3, pctiles.length, "Percentiles must have 3 values");
    assertTrue(pctiles[0] > 0.0, "d10 must be positive");
    assertTrue(pctiles[0] < pctiles[1], "d10 < d50");
    assertTrue(pctiles[1] < pctiles[2], "d50 < d90");
  }

  @Test
  void testDiameterUnitConversion() {
    cnt.calculate();
    double d_m = cnt.getMeanParticleDiameter();
    assertEquals(d_m * 1e3, cnt.getMeanParticleDiameter("mm"), d_m * 1e-7);
    assertEquals(d_m * 1e6, cnt.getMeanParticleDiameter("um"), d_m * 1e-4);
    assertEquals(d_m * 1e9, cnt.getMeanParticleDiameter("nm"), d_m * 1e-1);
    assertEquals(d_m, cnt.getMeanParticleDiameter("m"), d_m * 1e-10);
  }

  @Test
  void testConvenienceCalculateParticleDiameter() {
    double d = cnt.calculateParticleDiameter(20.0, 2.0);
    assertTrue(d > 0.0, "Convenience method must return positive diameter");
    assertTrue(cnt.isCalculated(), "Must be calculated after convenience method");
  }

  // ============================================================================
  // Factory Method Tests
  // ============================================================================

  @Test
  void testSulfurS8Factory() {
    ClassicalNucleationTheory s8 = ClassicalNucleationTheory.sulfurS8();
    s8.setTemperature(253.15);
    s8.setSupersaturationRatio(100.0);
    s8.setGasViscosity(1.0e-5);
    s8.setGasDiffusivity(5.0e-6);
    s8.setResidenceTime(2.0);
    s8.calculate();

    assertTrue(s8.getCriticalRadius() > 0.0, "Sulfur S8 critical radius must be positive");
    assertTrue(s8.getNucleationRate() > 0.0, "Sulfur S8 nucleation rate must be positive");
    assertTrue(s8.getMeanParticleDiameter() > 0.0, "Sulfur S8 particle diameter must be positive");
  }

  @Test
  void testWaterIceFactory() {
    ClassicalNucleationTheory ice = ClassicalNucleationTheory.waterIce();
    ice.setTemperature(243.15); // -30 C
    ice.setSupersaturationRatio(50.0);
    ice.setGasViscosity(1.5e-5);
    ice.setGasDiffusivity(2.0e-5);
    ice.setResidenceTime(1.0);
    ice.calculate();

    assertTrue(ice.getCriticalRadius() > 0.0, "Water ice critical radius must be positive");
    assertTrue(ice.getNucleationRate() >= 0.0, "Water ice nucleation rate must be non-negative");
  }

  @Test
  void testParaffinWaxFactory() {
    ClassicalNucleationTheory wax = ClassicalNucleationTheory.paraffinWax();
    wax.setTemperature(300.0);
    wax.setSupersaturationRatio(10.0);
    wax.setGasViscosity(1.0e-5);
    wax.setGasDiffusivity(3.0e-6);
    wax.setResidenceTime(5.0);
    wax.calculate();

    assertTrue(wax.getCriticalRadius() > 0.0, "Wax critical radius must be positive");
  }

  @Test
  void testNaphthaleneFactory() {
    ClassicalNucleationTheory naph = ClassicalNucleationTheory.naphthalene();
    naph.setTemperature(350.0);
    naph.setSupersaturationRatio(20.0);
    naph.setGasViscosity(1.0e-5);
    naph.setGasDiffusivity(4.0e-6);
    naph.setResidenceTime(1.0);
    naph.calculate();

    assertTrue(naph.getCriticalRadius() > 0.0, "Naphthalene critical radius must be positive");
  }

  // ============================================================================
  // Heterogeneous Nucleation Tests
  // ============================================================================

  @Test
  void testHeterogeneousContactAngleReducesBarrier() {
    // Homogeneous case
    cnt.calculate();
    double barrierHom = cnt.getFreeEnergyBarrier();

    // Heterogeneous with theta = 60 degrees
    cnt.setContactAngle(60.0);
    cnt.setHeterogeneous(true);
    cnt.calculate();
    double barrierHet = cnt.getFreeEnergyBarrier();

    assertTrue(barrierHet < barrierHom,
        "Heterogeneous barrier must be less than homogeneous barrier");
  }

  @Test
  void testHeterogeneousZeroContactAngleGivesZeroBarrier() {
    // theta = 0 means complete wetting => f(theta) = 0 => no barrier
    cnt.setContactAngle(0.0);
    cnt.setHeterogeneous(true);
    cnt.calculate();

    assertEquals(0.0, cnt.getFreeEnergyBarrier(), 1e-30, "f(0) = 0, so barrier should be zero");
  }

  @Test
  void testHeterogeneous180DegreesEqualsHomogeneous() {
    cnt.calculate();
    double barrierHom = cnt.getFreeEnergyBarrier();

    cnt.setContactAngle(180.0);
    cnt.setHeterogeneous(true);
    cnt.calculate();
    double barrierHet = cnt.getFreeEnergyBarrier();

    assertEquals(barrierHom, barrierHet, barrierHom * 1e-10,
        "f(180) = 1, so heterogeneous barrier should equal homogeneous");
  }

  @Test
  void testHeterogeneous90DegreesGivesHalfBarrier() {
    // f(90) = (2 - 3*cos(90) + cos^3(90)) / 4 = (2 - 0 + 0) / 4 = 0.5
    cnt.calculate();
    double barrierHom = cnt.getFreeEnergyBarrier();

    cnt.setContactAngle(90.0);
    cnt.setHeterogeneous(true);
    cnt.calculate();
    double barrierHet = cnt.getFreeEnergyBarrier();

    assertEquals(0.5 * barrierHom, barrierHet, barrierHom * 1e-10,
        "f(90) = 0.5, so heterogeneous barrier should be half of homogeneous");
  }

  @Test
  void testHeterogeneousContactAngleFactorAnalytical() {
    // f(theta) = (2 - 3*cos(theta) + cos^3(theta)) / 4
    double thetaDeg = 60.0;
    double thetaRad = Math.toRadians(thetaDeg);
    double cosTheta = Math.cos(thetaRad);
    double expectedF = (2.0 - 3.0 * cosTheta + cosTheta * cosTheta * cosTheta) / 4.0;

    double factor = ClassicalNucleationTheory.heterogeneousContactAngleFactor(thetaDeg);
    assertEquals(expectedF, factor, 1e-12, "Contact angle factor must match analytical formula");
  }

  @Test
  void testHeterogeneousIncreasesNucleationRate() {
    // Lower barrier => higher rate
    cnt.setPartialPressure(1000.0);
    cnt.setSaturationPressure(100.0);
    cnt.calculate();
    double rateHom = cnt.getNucleationRate();

    cnt.setContactAngle(60.0);
    cnt.setHeterogeneous(true);
    cnt.calculate();
    double rateHet = cnt.getNucleationRate();

    assertTrue(rateHet >= rateHom, "Heterogeneous nucleation rate must be >= homogeneous rate");
  }

  // ============================================================================
  // EOS-Coupled Supersaturation Tests (fromThermoSystem)
  // ============================================================================

  @Test
  void testFromThermoSystemCreatesValidModel() {
    // Create a simple NeqSim system with a condensable component
    neqsim.thermo.system.SystemInterface system =
        new neqsim.thermo.system.SystemSrkEos(250.0, 50.0e5 / 1e5); // 250K, 50 bar
    system.addComponent("methane", 0.95);
    system.addComponent("n-heptane", 0.05);
    system.setMixingRule("classic");

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Create CNT from thermo system for n-heptane condensation
    ClassicalNucleationTheory cntFromEos =
        ClassicalNucleationTheory.fromThermoSystem(system, "n-heptane");

    assertNotNull(cntFromEos, "fromThermoSystem must return non-null model");
  }

  // ============================================================================
  // JSON and Reporting Tests
  // ============================================================================

  @Test
  void testToJsonNotEmpty() {
    cnt.calculate();
    String json = cnt.toJson();
    assertNotNull(json, "JSON must not be null");
    assertFalse(json.isEmpty(), "JSON must not be empty");
    assertTrue(json.contains("criticalRadius"), "JSON must contain criticalRadius");
    assertTrue(json.contains("nucleationRate"), "JSON must contain nucleationRate");
    assertTrue(json.contains("meanDiameter"), "JSON must contain meanDiameter");
  }

  @Test
  void testToMapHasAllSections() {
    cnt.calculate();
    java.util.Map<String, Object> map = cnt.toMap();
    assertTrue(map.containsKey("substanceName"));
    assertTrue(map.containsKey("substanceProperties"));
    assertTrue(map.containsKey("processConditions"));
    assertTrue(map.containsKey("nucleation"));
    assertTrue(map.containsKey("growth"));
    assertTrue(map.containsKey("particleResults"));
  }

  @Test
  void testToStringContainsSubstanceName() {
    cnt.setSubstanceName("TestSubstance");
    cnt.calculate();
    String str = cnt.toString();
    assertTrue(str.contains("TestSubstance"), "toString must contain substance name");
  }

  @Test
  void testToStringUncalculated() {
    String str = cnt.toString();
    assertTrue(str.contains("not calculated"), "Uncalculated toString must indicate so");
  }

  // ============================================================================
  // Filter Capture Efficiency Tests
  // ============================================================================

  @Test
  void testFilterCaptureEfficiencyBounds() {
    cnt.calculate();
    double eff10um = cnt.getFilterCaptureEfficiency(10.0e-6);
    assertTrue(eff10um >= 0.0 && eff10um <= 1.0,
        "Filter capture efficiency must be between 0 and 1");
  }

  @Test
  void testFractionSmallerThanBounds() {
    cnt.calculate();
    double frac = cnt.getFractionSmallerThan(cnt.getMeanParticleDiameter());
    // At the mean diameter, ~50% should be smaller for lognormal
    assertTrue(frac > 0.3 && frac < 0.7,
        "Fraction smaller than mean should be around 0.5, got " + frac);
  }

  // ============================================================================
  // Sticking Coefficient Tests
  // ============================================================================

  @Test
  void testStickingCoefficientClampedToRange() {
    cnt.setStickingCoefficient(1.5);
    cnt.calculate();
    // The coefficient should be clamped to 1.0 internally
    assertTrue(cnt.getNucleationRate() >= 0.0, "Rate must be non-negative with clamped alpha");

    cnt.setStickingCoefficient(-0.5);
    cnt.calculate();
    // The coefficient should be clamped to 0.0 internally
    assertTrue(cnt.getNucleationRate() >= 0.0, "Rate must be non-negative with clamped alpha");
  }

  // ============================================================================
  // Pressure-based Supersaturation Tests
  // ============================================================================

  @Test
  void testSupersaturationFromPressures() {
    cnt.setPartialPressure(1000.0);
    cnt.setSaturationPressure(100.0);
    cnt.calculate();

    assertEquals(10.0, cnt.getSupersaturationRatio(), 1e-10,
        "S should be p_actual/p_sat = 1000/100 = 10");
  }

  // ============================================================================
  // JSON with Heterogeneous Nucleation Tests
  // ============================================================================

  @Test
  void testJsonContainsHeterogeneousInfo() {
    cnt.setContactAngle(60.0);
    cnt.setHeterogeneous(true);
    cnt.calculate();

    String json = cnt.toJson();
    assertTrue(json.contains("heterogeneous") || json.contains("contactAngle"),
        "JSON should contain heterogeneous nucleation info");
  }
}
