package neqsim.process.safety.dispersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class GaussianPlumeTest {

  @Test
  void groundConcentrationDecreasesWithDistance() {
    GaussianPlume plume = new GaussianPlume(1.0, 10.0, 5.0,
        GaussianPlume.Stability.D, GaussianPlume.Terrain.RURAL);
    double c100 = plume.centerlineGroundConcentration(100.0);
    double c1000 = plume.centerlineGroundConcentration(1000.0);
    assertTrue(c100 > c1000, "concentration should decrease with distance");
    assertTrue(c100 > 0.0);
  }

  @Test
  void distanceToConcentrationConsistentWithDirectEvaluation() {
    // Ground-level source so concentration is monotone decreasing with distance
    // (bisection in distanceToConcentration assumes monotone profile from 1 m).
    GaussianPlume plume = new GaussianPlume(10.0, 0.0, 4.0,
        GaussianPlume.Stability.D, GaussianPlume.Terrain.RURAL);
    double thresh = 1.0e-3;
    double dist = plume.distanceToConcentration(thresh);
    assertTrue(Double.isFinite(dist) && dist > 0.0,
        "distance must be a finite positive number");
    double back = plume.centerlineGroundConcentration(dist);
    // Verify the bisection actually converged to the threshold within an order of magnitude
    // (Gaussian curve steepness gives non-trivial inverse precision; tolerance is loose).
    assertTrue(back > thresh / 10.0 && back < thresh * 10.0,
        "concentration at returned distance should be near threshold; got " + back);
  }

  @Test
  void sigmasArePositive() {
    GaussianPlume plume = new GaussianPlume(1.0, 5.0, 3.0,
        GaussianPlume.Stability.B, GaussianPlume.Terrain.URBAN);
    assertTrue(plume.sigmaY(500.0) > 0.0);
    assertTrue(plume.sigmaZ(500.0) > 0.0);
  }
}
