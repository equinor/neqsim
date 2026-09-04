package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the surveyed-route resampler shared by the pipe models. */
public class RouteProfileTest {
  private static double[] kp() {
    return new double[] { 0.0, 1000.0, 2000.0, 3000.0 };
  }

  private static double[] depths() {
    return new double[] { 260.0, 250.0, 270.0, 240.0 };
  }

  @Test
  void depthsAreNegatedIntoTheModelElevationConvention() {
    RouteProfile route = RouteProfile.fromDepths(kp(), depths());
    assertArrayEquals(new double[] { -260.0, -250.0, -270.0, -240.0 }, route.getElevationProfile(), 1.0e-9);
  }

  @Test
  void elevationProfileHasOneMoreEntryThanSectionLengths() {
    RouteProfile route = RouteProfile.fromDepths(kp(), depths());
    assertEquals(3, route.getNumberOfSections());
    assertEquals(3, route.getSectionLengths().length);
    assertEquals(4, route.getElevationProfile().length);
  }

  @Test
  void totalLengthAndNetElevationChangeAreConsistentWithTheSurvey() {
    RouteProfile route = RouteProfile.fromDepths(kp(), depths());
    assertEquals(3000.0, route.getTotalLength(), 1.0e-9);
    assertEquals(20.0, route.getNetElevationChange(), 1.0e-9);
  }

  @Test
  void interpolationIsLinearBetweenStationsAndClampedOutside() {
    RouteProfile route = RouteProfile.fromDepths(kp(), depths());
    assertEquals(-255.0, route.elevationAt(500.0), 1.0e-9);
    assertEquals(-260.0, route.elevationAt(-100.0), 1.0e-9);
    assertEquals(-240.0, route.elevationAt(9999.0), 1.0e-9);
  }

  @Test
  void resamplingPreservesLengthAndEndElevations() {
    RouteProfile route = RouteProfile.fromDepths(kp(), depths()).resample(30);
    assertEquals(30, route.getNumberOfSections());
    assertEquals(3000.0, route.getTotalLength(), 1.0e-6);
    assertEquals(-260.0, route.getElevationProfile()[0], 1.0e-9);
    assertEquals(-240.0, route.getElevationProfile()[30], 1.0e-9);
    double sum = 0.0;
    for (double length : route.getSectionLengths()) {
      sum += length;
    }
    assertEquals(3000.0, sum, 1.0e-6);
  }

  @Test
  void riserAddsTheFullRiseAndEndsAboveSeaLevel() {
    RouteProfile route = RouteProfile.fromDepths(kp(), depths()).withRiser(240.0, 25.0);
    double[] elevations = route.getElevationProfile();
    assertEquals(25.0, elevations[elevations.length - 1], 1.0e-9);
    assertTrue(route.getTotalLength() > 3000.0);
    // Net change is measured from the route start at -260 m, not from the riser base.
    assertEquals(285.0, route.getNetElevationChange(), 1.0e-9);
  }

  @Test
  void lowPointsAreTheLocalMinimaWhereLiquidCollects() {
    // Station 2 sits at -270 m between -250 and -240, so it is the trap.
    List<Double> lows = RouteProfile.fromDepths(kp(), depths()).getLowPointKp();
    assertEquals(1, lows.size());
    assertEquals(2000.0, lows.get(0).doubleValue(), 1.0e-9);
  }

  @Test
  void aMonotonicRouteHasNoLowPoints() {
    RouteProfile route = RouteProfile.fromDepths(new double[] { 0.0, 100.0, 200.0 },
        new double[] { 300.0, 200.0, 100.0 });
    assertTrue(route.getLowPointKp().isEmpty());
  }

  @Test
  void averageAngleFollowsTheNetRiseAndMaximumFollowsTheSteepestSection() {
    RouteProfile route = RouteProfile.fromDepths(kp(), depths());
    assertEquals(Math.toDegrees(Math.asin(20.0 / 3000.0)), route.getAverageAngleDegrees(), 1.0e-9);
    assertEquals(Math.toDegrees(Math.atan2(30.0, 1000.0)), route.getMaximumAngleDegrees(), 1.0e-9);
  }

  @Test
  void mismatchedOrTooShortOrNonMonotonicInputIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new RouteProfile(new double[] { 0.0, 1.0 }, new double[] { 0.0 }));
    assertThrows(IllegalArgumentException.class, () -> new RouteProfile(new double[] { 0.0 }, new double[] { 0.0 }));
    assertThrows(IllegalArgumentException.class,
        () -> new RouteProfile(new double[] { 0.0, 100.0, 50.0 }, new double[] { 0.0, 1.0, 2.0 }));
  }

  @Test
  void resamplingRejectsANonPositiveSectionCount() {
    RouteProfile route = RouteProfile.fromDepths(kp(), depths());
    assertThrows(IllegalArgumentException.class, () -> route.resample(0));
  }

  @Test
  void aRiserThatDoesNotRiseIsRejected() {
    RouteProfile route = RouteProfile.fromDepths(kp(), depths());
    assertThrows(IllegalArgumentException.class, () -> route.withRiser(-30.0, 10.0));
  }
}
