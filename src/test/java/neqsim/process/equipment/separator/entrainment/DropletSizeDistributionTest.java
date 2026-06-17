package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DropletSizeDistribution}.
 */
class DropletSizeDistributionTest {

  @Test
  void testRosinRammlerCDF() {
    // d_63.2 = 100 um, q = 2.6
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    // At d = 0, CDF = 0
    assertEquals(0.0, dsd.cumulativeFraction(0.0), 1e-10);

    // At d = d_63.2, CDF should be 1 - exp(-1) = 0.6321
    assertEquals(0.6321, dsd.cumulativeFraction(100e-6), 0.001);

    // CDF should be monotonically increasing
    double prev = 0.0;
    for (int i = 1; i <= 10; i++) {
      double d = i * 30e-6;
      double cdf = dsd.cumulativeFraction(d);
      assertTrue(cdf >= prev, "CDF should be monotonically increasing");
      prev = cdf;
    }
  }

  @Test
  void testLogNormalCDF() {
    // d_50 = 80 um, sigma = 0.8
    DropletSizeDistribution dsd = DropletSizeDistribution.logNormal(80e-6, 0.8);

    // At d = d_50, CDF should be ~0.5
    assertEquals(0.5, dsd.cumulativeFraction(80e-6), 0.005);

    // CDF should be monotonic
    double prev = 0.0;
    for (int i = 1; i <= 10; i++) {
      double d = i * 20e-6;
      double cdf = dsd.cumulativeFraction(d);
      assertTrue(cdf >= prev, "CDF should be monotonically increasing");
      prev = cdf;
    }
  }

  @Test
  void testInverseCDF() {
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    // inverseCDF(CDF(d)) = d (round-trip)
    double d = 50e-6;
    double cdf = dsd.cumulativeFraction(d);
    double dRecovered = dsd.inverseCDF(cdf);
    assertEquals(d, dRecovered, d * 0.01); // 1% tolerance
  }

  @Test
  void testD50() {
    DropletSizeDistribution dsd = DropletSizeDistribution.logNormal(80e-6, 0.8);
    assertEquals(80e-6, dsd.getD50(), 1e-6);
  }

  @Test
  void testSauterMeanDiameter() {
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);
    double d32 = dsd.getSauterMeanDiameter();
    // d_32 should be positive and in a reasonable range relative to d0
    assertTrue(d32 > 0 && d32 < 500e-6,
        "Sauter mean diameter should be positive and reasonable, got: " + d32);
  }

  @Test
  void testDiscreteClasses() {
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);
    double[][] classes = dsd.getDiscreteClasses();

    assertEquals(50, classes.length, "Should have 50 size classes by default");

    // Volume fractions should sum to ~1.0
    double totalFrac = 0.0;
    for (double[] cls : classes) {
      totalFrac += cls[2];
      assertTrue(cls[0] > 0, "Lower bound should be positive");
      assertTrue(cls[1] > cls[0], "Midpoint should be > lower bound");
      assertTrue(cls[2] >= 0, "Volume fraction should be non-negative");
    }
    assertEquals(1.0, totalFrac, 0.01); // Should sum to ~1 (0.1%–99.9% range)
  }

  @Test
  void testHinzeCorrelation() {
    // Typical gas-liquid pipe flow conditions
    double gasDensity = 50.0; // kg/m3
    double velocity = 15.0; // m/s
    double pipeDiameter = 0.2; // m
    double surfaceTension = 0.02; // N/m (20 mN/m)

    DropletSizeDistribution dsd = DropletSizeDistribution.fromHinzeCorrelation(gasDensity, velocity,
        pipeDiameter, surfaceTension, 2.6);

    // Should produce a reasonable distribution
    double d50 = dsd.getD50();
    assertTrue(d50 > 1e-6 && d50 < 1e-3,
        "Hinze d_50 should be in the range 1 um to 1 mm. Got: " + d50 * 1e6 + " um");
  }

  @Test
  void testVolumePDF() {
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    // PDF should be zero at d = 0
    assertEquals(0.0, dsd.volumePDF(0.0), 1e-10);

    // PDF should be positive at d_63.2
    assertTrue(dsd.volumePDF(100e-6) > 0, "PDF should be positive at characteristic diameter");
  }
}
