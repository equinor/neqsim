package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PigMotion.Trajectory;

/**
 * Public sensor-timing evidence for prescribed-pressure pig mechanics.
 *
 * <p>
 * The Lima et al. case verifies sampled trajectory and event-timing arithmetic only. It does not validate a
 * pressure-force closure, bypass, liquid sweep or coupled pipeline hydraulics.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PigMotionPositionEventTest {
  /**
   * Reproduces the public Lima et al. sensor-spacing and sample-timing calculation.
   *
   * <p>
   * Source: Sensors 17 (2017) 2119, doi:10.3390/s17092119, CC BY 4.0. The reported pressure-sensor spacing is 5.90 m
   * and the crossing interval is 272 samples at 50 ms. Reported supervisory and odometer speeds are 0.43 and 0.45 m/s,
   * with a reported 4.44% difference.
   * </p>
   */
  @Test
  void limaPublicSensorTimingBenchmark() {
    double sensorSpacingM = 5.90;
    double samplePeriodSeconds = 0.050;
    int sampleCount = 272;
    double publishedCrossingSeconds = sampleCount * samplePeriodSeconds;
    double eventDerivedSpeedMps = sensorSpacingM / publishedCrossingSeconds;
    PigMotion pig = new PigMotion(1.0, 1.0, 0.0, 0.0);

    double[] durations = new double[sampleCount];
    double[] pressures = new double[sampleCount];
    double[] inclinations = new double[sampleCount];
    Arrays.fill(durations, samplePeriodSeconds);
    Trajectory sampled = pig.simulate(0.0, eventDerivedSpeedMps, durations, pressures, inclinations);
    double[] sampledTimes = sampled.getTimesSeconds();
    double[] sampledPositions = sampled.getPositionsM();
    double positionRoundoffM = 1.0e-12;
    int firstCrossingSample = firstSampleAtOrBeyond(sampledPositions, sensorSpacingM, positionRoundoffM);

    assertEquals(13.6, publishedCrossingSeconds, 1.0e-12);
    assertEquals(sampleCount, firstCrossingSample);
    assertEquals(publishedCrossingSeconds, sampledTimes[firstCrossingSample], 1.0e-12);
    assertEquals(sensorSpacingM, sampledPositions[firstCrossingSample], 1.0e-12);
    assertTrue(sampledPositions[firstCrossingSample - 1] < sensorSpacingM - positionRoundoffM);
    assertTrue(sensorSpacingM - sampledPositions[firstCrossingSample - 1] <= eventDerivedSpeedMps * samplePeriodSeconds
        + positionRoundoffM);

    Trajectory singleInterval = pig.simulate(0.0, eventDerivedSpeedMps, new double[] {publishedCrossingSeconds},
        new double[] {0.0}, new double[] {0.0});
    assertEquals(sampledPositions[firstCrossingSample], singleInterval.getPositionsM()[1], 1.0e-12);
    assertEquals(eventDerivedSpeedMps, singleInterval.getVelocitiesMps()[1], 0.0);
    assertEquals(0.43, roundToTwoDecimals(eventDerivedSpeedMps), 0.0);

    double odometerSpeedMps = 5.70 / 12.53;
    assertEquals(0.45, roundToTwoDecimals(odometerSpeedMps), 0.0);
    double publishedRoundedDifferencePercent = 100.0 * (0.45 - 0.43) / 0.45;
    assertEquals(4.44, publishedRoundedDifferencePercent, 0.01);
  }

  /**
   * Finds the first sampled position at or beyond a forward sensor position.
   *
   * @param positions sampled axial positions in m
   * @param target sensor position in m
   * @param tolerance numerical position tolerance in m
   * @return first crossing sample index
   */
  private static int firstSampleAtOrBeyond(double[] positions, double target, double tolerance) {
    for (int i = 0; i < positions.length; i++) {
      if (positions[i] >= target - tolerance) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Rounds a value to the two decimal places used for the published sensor speeds.
   *
   * @param value value to round
   * @return value rounded to two decimal places
   */
  private static double roundToTwoDecimals(double value) {
    return Math.round(100.0 * value) / 100.0;
  }
}
