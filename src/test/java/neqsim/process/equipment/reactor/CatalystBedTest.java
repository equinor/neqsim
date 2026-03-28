package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CatalystBed class.
 */
public class CatalystBedTest extends neqsim.NeqSimTest {

  @Test
  public void testErgunPressureDrop() {
    // Standard Ergun equation test with known values
    CatalystBed bed = new CatalystBed(3.0, 0.40, 800.0);
    // dp=3mm, eps=0.40, rho_bulk=800 kg/m3

    double velocity = 1.0; // m/s
    double gasDensity = 10.0; // kg/m3
    double gasViscosity = 2.0e-5; // Pa*s

    double dPdz = bed.calculatePressureDrop(velocity, gasDensity, gasViscosity);

    // Ergun: dP/dz = 150 * mu * u * (1-eps)^2 / (dp^2 * eps^3)
    // + 1.75 * rho * u^2 * (1-eps) / (dp * eps^3)
    double dp = 0.003;
    double eps = 0.40;
    double viscousTerm =
        150.0 * gasViscosity * velocity * Math.pow(1 - eps, 2) / (dp * dp * Math.pow(eps, 3));
    double inertialTerm =
        1.75 * gasDensity * velocity * velocity * (1 - eps) / (dp * Math.pow(eps, 3));
    double expected = viscousTerm + inertialTerm;

    assertEquals(expected, dPdz, expected * 0.01, "Ergun pressure drop should match formula");
    assertTrue(dPdz > 0, "Pressure drop should be positive");
  }

  @Test
  public void testTotalPressureDrop() {
    CatalystBed bed = new CatalystBed(5.0, 0.45, 750.0);

    double velocity = 0.5;
    double gasDensity = 5.0;
    double gasViscosity = 1.5e-5;
    double bedLength = 3.0;

    double dPdz = bed.calculatePressureDrop(velocity, gasDensity, gasViscosity);
    double totalDPbar =
        bed.calculateTotalPressureDrop(velocity, gasDensity, gasViscosity, bedLength);

    // Total dP (bar) = dPdz (Pa/m) * length(m) / 1e5
    assertEquals(dPdz * bedLength / 1.0e5, totalDPbar, totalDPbar * 0.01);
  }

  @Test
  public void testThieleModulus() {
    CatalystBed bed = new CatalystBed(6.0, 0.40, 800.0);

    // Thiele modulus = (dp/6) * sqrt(k_v / D_eff)
    double kv = 100.0; // 1/s volumetric rate constant
    double Deff = 1.0e-6; // m2/s effective diffusivity

    double phi = bed.calculateThieleModulus(kv, Deff);

    double expectedPhi = (0.006 / 6.0) * Math.sqrt(kv / Deff);
    assertEquals(expectedPhi, phi, expectedPhi * 0.01);
    assertTrue(phi > 0, "Thiele modulus should be positive");
  }

  @Test
  public void testEffectivenessFactorSmallPhi() {
    CatalystBed bed = new CatalystBed();
    // For small phi, eta -> 1.0
    double eta = bed.calculateEffectivenessFactor(0.01);
    assertEquals(1.0, eta, 0.05, "Effectiveness should approach 1 for small Thiele modulus");
  }

  @Test
  public void testEffectivenessFactorLargePhi() {
    CatalystBed bed = new CatalystBed();
    // For generalized Thiele modulus, asymptotic: eta -> 1/(3*phi)
    double phi = 50.0;
    double eta = bed.calculateEffectivenessFactor(phi);
    // Full formula: eta = (1/phi) * (coth(3*phi) - 1/(3*phi))
    double threePhi = 3.0 * phi;
    double expectedEta = (1.0 / phi) * (1.0 / Math.tanh(threePhi) - 1.0 / threePhi);
    assertEquals(expectedEta, eta, expectedEta * 0.01,
        "Effectiveness should match generalized Thiele formula");
  }

  @Test
  public void testEffectiveDiffusivity() {
    CatalystBed bed = new CatalystBed();
    bed.setParticlePorosity(0.50);
    bed.setTortuosity(3.0);

    double Dmol = 1.0e-5; // m2/s
    double Deff = bed.getEffectiveDiffusivity(Dmol);

    // Deff = Dmol * eps_p / tau
    double expected = 1.0e-5 * 0.50 / 3.0;
    assertEquals(expected, Deff, expected * 1e-6);
  }

  @Test
  public void testReynoldsNumber() {
    CatalystBed bed = new CatalystBed(4.0, 0.40, 800.0);

    double velocity = 2.0;
    double gasDensity = 8.0;
    double gasViscosity = 2.0e-5;

    double Re = bed.calculateReynoldsNumber(velocity, gasDensity, gasViscosity);

    // Re = rho * u * dp / (mu * (1 - eps))
    double expected = 8.0 * 2.0 * 0.004 / (2.0e-5 * (1 - 0.40));
    assertEquals(expected, Re, expected * 0.01);
    assertTrue(Re > 0, "Reynolds number should be positive");
  }

  @Test
  public void testSettersAndGetters() {
    CatalystBed bed = new CatalystBed();

    bed.setParticleDiameter(5.0, "mm");
    assertEquals(0.005, bed.getParticleDiameter(), 1e-10);

    bed.setParticleDiameter(10.0, "m");
    assertEquals(10.0, bed.getParticleDiameter(), 1e-10);

    bed.setVoidFraction(0.42);
    assertEquals(0.42, bed.getVoidFraction(), 1e-10);

    bed.setBulkDensity(900.0);
    assertEquals(900.0, bed.getBulkDensity(), 1e-10);

    bed.setParticleDensity(1500.0);
    assertEquals(1500.0, bed.getParticleDensity(), 1e-10);

    bed.setActivityFactor(0.85);
    assertEquals(0.85, bed.getActivityFactor(), 1e-10);

    bed.setSpecificSurfaceArea(200.0, "m2/g");
    assertEquals(200000.0, bed.getSpecificSurfaceArea(), 1e-3);
  }

  @Test
  public void testConstructorConvenience() {
    // dp=3mm, eps=0.40, rho_bulk=800
    CatalystBed bed = new CatalystBed(3.0, 0.40, 800.0);
    assertEquals(0.003, bed.getParticleDiameter(), 1e-10);
    assertEquals(0.40, bed.getVoidFraction(), 1e-10);
    assertEquals(800.0, bed.getBulkDensity(), 1e-10);
  }
}
