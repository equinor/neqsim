package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GradeEfficiencyCurve}.
 */
class GradeEfficiencyCurveTest {

  @Test
  void testGravityEfficiency() {
    // Cut diameter 200 um: droplets > 200 um are 100% removed
    GradeEfficiencyCurve curve = GradeEfficiencyCurve.gravity(200e-6);

    // At d = 200 um (cut diameter), efficiency = (200/200)^2 = 1.0
    assertEquals(1.0, curve.getEfficiency(200e-6), 0.001);

    // At d = 100 um, efficiency = (100/200)^2 = 0.25
    assertEquals(0.25, curve.getEfficiency(100e-6), 0.001);

    // At d = 50 um, efficiency = (50/200)^2 = 0.0625
    assertEquals(0.0625, curve.getEfficiency(50e-6), 0.001);

    // At d = 0, efficiency = 0
    assertEquals(0.0, curve.getEfficiency(0.0), 1e-10);

    // At d >> cut, efficiency = 1.0 (capped)
    assertEquals(1.0, curve.getEfficiency(500e-6), 1e-10);
  }

  @Test
  void testWireMeshEfficiency() {
    // d_50 = 5 um, sharpness = 2.5, max = 0.998
    GradeEfficiencyCurve curve = GradeEfficiencyCurve.wireMesh(5e-6, 2.5, 0.998);

    // At d = 0, efficiency should be 0
    assertEquals(0.0, curve.getEfficiency(0.0), 1e-10);

    // At d_50, efficiency should be ~50% of max (= 0.998 * (1 - exp(-0.693)) = 0.998 * 0.5)
    double etaAtD50 = curve.getEfficiency(5e-6);
    assertEquals(0.998 * 0.5, etaAtD50, 0.01);

    // Large droplets should approach max efficiency
    double etaLarge = curve.getEfficiency(50e-6);
    assertTrue(etaLarge > 0.99, "Large droplets should be near max efficiency");
  }

  @Test
  void testVanePackEfficiency() {
    GradeEfficiencyCurve curve = GradeEfficiencyCurve.vanePackDefault();

    // At d_50 (12 um), efficiency should be ~50% of max
    double eta = curve.getEfficiency(12e-6);
    assertTrue(eta > 0.45 && eta < 0.55, "At d_50, efficiency should be ~50%");

    // Small droplets should have low efficiency
    double etaSmall = curve.getEfficiency(1e-6);
    assertTrue(etaSmall < 0.1, "Very small droplets should have low efficiency");
  }

  @Test
  void testAxialCycloneEfficiency() {
    GradeEfficiencyCurve curve = GradeEfficiencyCurve.axialCycloneDefault();

    // Cyclones have very steep curves — at 2x d_50, should be near max
    double eta = curve.getEfficiency(6e-6); // 2x d_50 of 3 um
    assertTrue(eta > 0.98, "Cyclone at 2x d_50 should be near max efficiency. Got: " + eta);

    // But at d_50, should be ~50%
    double etaAtD50 = curve.getEfficiency(3e-6);
    assertTrue(etaAtD50 > 0.4 && etaAtD50 < 0.6, "At d_50, efficiency ~50%");
  }

  @Test
  void testCustomCurve() {
    double[] diameters = {1e-6, 5e-6, 10e-6, 20e-6, 50e-6};
    double[] efficiencies = {0.0, 0.1, 0.5, 0.9, 0.99};

    GradeEfficiencyCurve curve = GradeEfficiencyCurve.custom(diameters, efficiencies);

    // Test interpolation at 7.5 um (between 5 um = 0.1 and 10 um = 0.5)
    double eta = curve.getEfficiency(7.5e-6);
    assertEquals(0.3, eta, 0.01); // Linear interpolation

    // Test extrapolation below range
    assertEquals(0.0, curve.getEfficiency(0.5e-6), 0.001);

    // Test extrapolation above range
    assertEquals(0.99, curve.getEfficiency(100e-6), 0.001);
  }

  @Test
  void testOverallEfficiency() {
    // Wire mesh with d_50 = 5 um, DSD with d_63.2 = 100 um (coarse spray)
    GradeEfficiencyCurve curve = GradeEfficiencyCurve.wireMeshDefault();
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    double overallEta = curve.calcOverallEfficiency(dsd);

    // With 100 um characteristic diameter vs 5 um d_50 mesh, most droplets should be captured
    assertTrue(overallEta > 0.99,
        "Wire mesh should capture >99% of coarse spray. Got: " + overallEta);
  }

  @Test
  void testOverallEfficiencyFineDSD() {
    // Wire mesh with d_50 = 5 um, DSD with d_63.2 = 3 um (very fine mist)
    GradeEfficiencyCurve curve = GradeEfficiencyCurve.wireMeshDefault();
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(3e-6, 2.6);

    double overallEta = curve.calcOverallEfficiency(dsd);

    // Very fine mist (3 um) against 5 um d_50 mesh — significant penetration
    assertTrue(overallEta < 0.9,
        "Fine mist should have significant penetration. Got: " + overallEta);
    assertTrue(overallEta > 0.05, "Should still capture some. Got: " + overallEta);
  }

  @Test
  void testPlatePackEfficiency() {
    GradeEfficiencyCurve curve = GradeEfficiencyCurve.platePack(50e-6, 0.98);

    // At d = 50 um (d_50), efficiency ~50% of max
    double eta = curve.getEfficiency(50e-6);
    assertTrue(eta > 0.4 && eta < 0.6, "At d_50, efficiency ~50%");
  }
}
