package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PigMotion.Step;
import neqsim.process.equipment.pipeline.PigMotion.Trajectory;

/**
 * Analytical mechanics, event and replay evidence; not a coupled hydraulic pigging benchmark.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PigMotionTest {
  /**
   * Validates the exact documented schedule and its independently calculated endpoints.
   *
   * @throws Exception if documentation cannot be read
   */
  @Test
  void documentedStartStopRestartSchedule() throws Exception {
    // DOC_START
    PigMotion pig = new PigMotion(100.0, 0.01, 100.0, 50.0);
    PigMotion.Trajectory result = pig.simulate(0.0, 0.0, new double[] {2.0, 10.0, 1.0},
        new double[] {20000.0, 0.0, 25000.0}, new double[] {0.0, 0.0, 0.0});
    // DOC_END
    assertArrayEquals(new double[] {0.0, 2.0, 12.0, 13.0}, result.getTimesSeconds(), 0.0);
    assertArrayEquals(new double[] {0.0, 3.0, 12.0, 13.0}, result.getPositionsM(), 1.0e-12);
    assertArrayEquals(new double[] {0.0, 3.0, 0.0, 2.0}, result.getVelocitiesMps(), 1.0e-12);
    assertEquals(6.0, result.getSteps()[1].getStopTimeSeconds(), 1.0e-12);
    for (Step step : result.getSteps()) {
      assertEquals(0.0, step.getEnergyResidualJ(), 1.0e-12);
    }
    String source = new String(
        Files.readAllBytes(Paths.get("src/test/java/neqsim/process/equipment/pipeline/PigMotionTest.java")),
        StandardCharsets.UTF_8);
    String snippet = source.substring(source.indexOf("// DOC_START") + "// DOC_START".length(),
        source.indexOf("// DOC_END"));
    String guide = new String(Files.readAllBytes(Paths.get("docs/process/pig-motion.md")), StandardCharsets.UTF_8);
    String fence = guide.substring(guide.indexOf("```java") + "```java".length());
    fence = fence.substring(0, fence.indexOf("```"));
    assertEquals(snippet.replaceAll("\\s+", " ").trim(), fence.replaceAll("\\s+", " ").trim());
  }

  /** Checks Newton's constant-acceleration solution and pressure-work balance. */
  @Test
  void constantPressureHasAnalyticalAccelerationAndWork() {
    Step step = new PigMotion(100.0, 0.01, 100.0, 50.0).advance(0.0, 2.0, 20000.0, 0.0, 4.0);
    assertEquals(20.0, step.getPositionM(), 1.0e-12);
    assertEquals(8.0, step.getVelocityMps(), 1.0e-12);
    assertEquals(4000.0, step.getPressureWorkJ(), 1.0e-12);
    assertEquals(1000.0, step.getFrictionHeatJ(), 1.0e-12);
    assertEquals(3000.0, step.getKineticEnergyChangeJ(), 1.0e-12);
    assertEquals(0.0, step.getEnergyResidualJ(), 1.0e-12);
    assertEquals(-1.0, step.getStopTimeSeconds(), 0.0);
  }

  /** Checks equality at static friction and one representable increment above it. */
  @Test
  void staticThresholdIsInclusiveAndSigned() {
    PigMotion pig = new PigMotion(100.0, 0.01, 100.0, 50.0);
    for (double direction : new double[] {-1.0, 1.0}) {
      Step held = pig.advance(7.0, 0.0, direction * 10000.0, 0.0, 100.0);
      assertEquals(7.0, held.getPositionM(), 0.0);
      assertEquals(0.0, held.getVelocityMps(), 0.0);
      assertEquals(0.0, held.getPressureWorkJ(), 0.0);
      Step released = pig.advance(7.0, 0.0, direction * Math.nextUp(10000.0), 0.0, 1.0);
      assertTrue(direction * released.getVelocityMps() > 0.0);
    }
  }

  /** Checks the distinct static and moving-friction branches without a velocity threshold. */
  @Test
  void movingPigDoesNotUseBreakawayResistance() {
    PigMotion pig = new PigMotion(100.0, 0.01, 100.0, 50.0);
    assertEquals(0.0, pig.advance(0.0, 0.0, 7500.0, 0.0, 1.0).getVelocityMps(), 0.0);
    assertEquals(1.25, pig.advance(0.0, 1.0, 7500.0, 0.0, 1.0).getVelocityMps(), 1.0e-12);
    assertTrue(pig.advance(0.0, 1.0e-14, 7500.0, 0.0, 1.0).getVelocityMps() > 0.24);
  }

  /** Checks stop time, stopping distance and dissipated energy against elementary mechanics. */
  @Test
  void stopsInsideLargeStepWithoutSpuriousReversal() {
    Step step = new PigMotion(100.0, 0.01, 100.0, 50.0).advance(0.0, 2.0, 0.0, 0.0, 10.0);
    assertEquals(4.0, step.getPositionM(), 1.0e-12);
    assertEquals(4.0, step.getStopTimeSeconds(), 1.0e-12);
    assertEquals(4.0, step.getMovingSeconds(), 1.0e-12);
    assertEquals(0.0, step.getVelocityMps(), 0.0);
    assertEquals(200.0, step.getFrictionHeatJ(), 1.0e-12);
    assertEquals(0.0, step.getEnergyResidualJ(), 1.0e-12);
  }

  /** Checks both legs of a reversal; net displacement alone cannot measure friction work. */
  @Test
  void reversesAfterExactZeroVelocityEvent() {
    Step step = new PigMotion(100.0, 0.01, 100.0, 50.0).advance(0.0, 2.0, -20000.0, 0.0, 2.0);
    assertEquals(0.8, step.getStopTimeSeconds(), 1.0e-12);
    assertEquals(-0.28, step.getPositionM(), 1.0e-12);
    assertEquals(-1.8, step.getVelocityMps(), 1.0e-12);
    assertEquals(1.88, step.getDistanceM(), 1.0e-12);
    assertEquals(94.0, step.getFrictionHeatJ(), 1.0e-12);
    assertEquals(0.0, step.getEnergyResidualJ(), 1.0e-12);
  }

  /** Checks gravity sign, downhill motion and an exactly pressure-balanced vertical pig. */
  @Test
  void gravityAndPressureBalanceHaveCorrectSigns() {
    PigMotion pig = new PigMotion(10.0, 1.0, 0.0, 0.0);
    Step falling = pig.advance(0.0, 0.0, 0.0, Math.PI / 2.0, 2.0);
    assertEquals(-19.6133, falling.getPositionM(), 1.0e-12);
    assertEquals(-19.6133, falling.getVelocityMps(), 1.0e-12);
    assertEquals(0.0, falling.getEnergyResidualJ(), 1.0e-10);
    Step balanced = pig.advance(0.0, 0.0, 10.0 * 9.80665, Math.PI / 2.0, 2.0);
    assertEquals(0.0, balanced.getVelocityMps(), 0.0);
  }

  /** Checks frictionless inertia and a zero-duration identity. */
  @Test
  void zeroFrictionAndZeroTimeRetainInertia() {
    PigMotion pig = new PigMotion(100.0, 0.01, 0.0, 0.0);
    Step moving = pig.advance(3.0, -2.0, 0.0, 0.0, 5.0);
    assertEquals(-7.0, moving.getPositionM(), 0.0);
    assertEquals(-2.0, moving.getVelocityMps(), 0.0);
    Step identity = pig.advance(3.0, -2.0, 1000.0, 0.3, 0.0);
    assertEquals(3.0, identity.getPositionM(), 0.0);
    assertEquals(-2.0, identity.getVelocityMps(), 0.0);
    assertEquals(0.0, identity.getEnergyResidualJ(), 0.0);
  }

  /** Checks partition invariance, sign symmetry, nonnegative dissipation and work at nearby conditions. */
  @Test
  void refinementAndReversalSymmetryAcrossForceMatrix() {
    PigMotion pig = new PigMotion(100.0, 0.01, 100.0, 50.0);
    Random random = new Random(3298L);
    for (int sample = 0; sample < 200; sample++) {
      double velocity = (random.nextDouble() - 0.5) * 8.0;
      double pressure = (random.nextDouble() - 0.5) * 60000.0;
      double angle = (random.nextDouble() - 0.5) * 0.2;
      Step whole = pig.advance(0.0, velocity, pressure, angle, 7.0);
      Step mirror = pig.advance(0.0, -velocity, -pressure, -angle, 7.0);
      assertEquals(-whole.getPositionM(), mirror.getPositionM(), 1.0e-11);
      assertEquals(-whole.getVelocityMps(), mirror.getVelocityMps(), 1.0e-11);
      double position = 0.0;
      double refinedVelocity = velocity;
      double heat = 0.0;
      for (int i = 0; i < 70; i++) {
        Step small = pig.advance(position, refinedVelocity, pressure, angle, 0.1);
        position = small.getPositionM();
        refinedVelocity = small.getVelocityMps();
        heat += small.getFrictionHeatJ();
        assertTrue(small.getFrictionHeatJ() >= 0.0);
      }
      assertEquals(whole.getPositionM(), position, 1.0e-10);
      assertEquals(whole.getVelocityMps(), refinedVelocity, 1.0e-11);
      assertEquals(whole.getFrictionHeatJ(), heat, 1.0e-9);
      assertEquals(0.0, whole.getEnergyResidualJ(), 1.0e-9);
    }
  }

  /**
   * Verifies that saved model and result objects retain state and protect their arrays.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void serializationReplayAndDefensiveArrays() throws Exception {
    PigMotion original = new PigMotion(100.0, 0.01, 100.0, 50.0);
    double[] durations = {2.0, 10.0, 1.0};
    double[] pressures = {20000.0, 0.0, 25000.0};
    double[] angles = {0.0, 0.0, 0.0};
    Trajectory expected = original.simulate(0.0, 0.0, durations, pressures, angles);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
      output.writeObject(expected);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      PigMotion restored = (PigMotion) input.readObject();
      Trajectory saved = (Trajectory) input.readObject();
      Trajectory replay = restored.simulate(0.0, 0.0, durations, pressures, angles);
      assertArrayEquals(expected.getPositionsM(), replay.getPositionsM(), 0.0);
      assertArrayEquals(expected.getVelocitiesMps(), saved.getVelocitiesMps(), 0.0);
    }
    durations[0] = 99.0;
    pressures[0] = 0.0;
    angles[0] = 1.0;
    expected.getTimesSeconds()[1] = 99.0;
    expected.getPositionsM()[1] = 99.0;
    expected.getVelocitiesMps()[1] = 99.0;
    expected.getSteps()[0] = null;
    assertEquals(2.0, expected.getTimesSeconds()[1], 0.0);
    assertEquals(3.0, expected.getPositionsM()[1], 0.0);
    assertEquals(3.0, expected.getVelocitiesMps()[1], 0.0);
    assertEquals(3.0, expected.getSteps()[0].getPositionM(), 0.0);
  }

  /** Rejects invalid physical inputs and arithmetic overflow instead of producing invalid trajectories. */
  @Test
  void invalidInputsFailWithoutPoisoningReuse() {
    assertThrows(IllegalArgumentException.class, () -> new PigMotion(0.0, 1.0, 1.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new PigMotion(1.0, Double.NaN, 1.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new PigMotion(1.0, 1.0, 1.0, 2.0));
    assertThrows(IllegalArgumentException.class, () -> new PigMotion(1.0, 1.0, -1.0, -2.0));
    PigMotion pig = new PigMotion(100.0, 0.01, 100.0, 50.0);
    assertThrows(IllegalArgumentException.class, () -> pig.advance(0.0, 0.0, Double.NaN, 0.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> pig.advance(0.0, 0.0, 0.0, 2.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> pig.advance(0.0, 0.0, 0.0, 0.0, -1.0));
    assertThrows(IllegalArgumentException.class, () -> pig.advance(0.0, 2.0, 20000.0, 0.0, Double.MAX_VALUE));
    assertThrows(IllegalArgumentException.class,
        () -> pig.simulate(0.0, 0.0, new double[0], new double[0], new double[0]));
    assertThrows(IllegalArgumentException.class,
        () -> pig.simulate(0.0, 0.0, new double[] {1.0}, new double[0], new double[] {0.0}));
    assertThrows(IllegalArgumentException.class,
        () -> pig.simulate(0.0, 0.0, new double[] {0.0}, new double[] {0.0}, new double[] {0.0}));
    assertThrows(IllegalArgumentException.class,
        () -> pig.simulate(0.0, 0.0, new double[] {1.0e20, 1.0}, new double[] {0.0, 0.0}, new double[] {0.0, 0.0}));
    assertEquals(3.0, pig.advance(0.0, 0.0, 20000.0, 0.0, 2.0).getPositionM(), 0.0);
  }
}
