package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CompressorLoadLimiter}.
 *
 * @author ESOL
 * @version 1.0
 */
public class CompressorLoadLimiterTest {
  /** Power model of a compressor at constant head: proportional to speed times suction pressure. */
  private static double power(double speed, double suctionPressure) {
    return 20.0 * speed * suctionPressure / 1.0;
  }

  /**
   * Speed stays at the ceiling while there is power headroom and the speed demand is above the ceiling.
   */
  @Test
  public void holdsSpeedCeilingWithHeadroom() {
    CompressorLoadLimiter lim = new CompressorLoadLimiter(1780.0, 1.0e9);
    double speed = 1700.0;
    for (int i = 0; i < 400; i++) {
      speed = lim.update(2000.0, speed, power(speed, 18.0), 1.0);
    }
    assertEquals(1780.0, speed, 1.0e-9);
    assertTrue(lim.isSpeedLimited());
    assertFalse(lim.isPowerLimited());
  }

  /**
   * A pressure rise at the power limit lowers the speed (droop) and a pressure fall lets it recover.
   */
  @Test
  public void speedDroopsWithSuctionPressureAtPowerLimit() {
    double pLimit = power(1780.0, 18.5);
    CompressorLoadLimiter lim = new CompressorLoadLimiter(1780.0, pLimit);
    lim.setLimiterGain(2000.0);
    lim.setSpeedRampRate(5.0);
    double speed = 1780.0;
    for (int i = 0; i < 100; i++) {
      speed = lim.update(1780.0, speed, power(speed, 18.0), 1.0);
    }
    assertEquals(1780.0, speed, 1.0e-9);
    for (int i = 0; i < 400; i++) {
      speed = lim.update(1780.0, speed, power(speed, 19.5), 1.0);
    }
    assertTrue(lim.isPowerLimited());
    assertEquals(1780.0 * 18.5 / 19.5, speed, 3.0);
    for (int i = 0; i < 600; i++) {
      speed = lim.update(1780.0, speed, power(speed, 18.0), 1.0);
    }
    assertEquals(1780.0, speed, 1.0e-9);
  }

  /**
   * The flow stiffness with the limiter is lower than at fixed speed: speed falls when pressure rises.
   */
  @Test
  public void limiterReducesSpeedForHigherPressure() {
    double pLimit = power(1780.0, 18.5);
    CompressorLoadLimiter lim = new CompressorLoadLimiter(1780.0, pLimit);
    lim.setLimiterGain(2000.0);
    double lowP = 1780.0;
    double highP = 1780.0;
    for (int i = 0; i < 300; i++) {
      lowP = lim.update(1780.0, lowP, power(lowP, 18.0), 1.0);
    }
    for (int i = 0; i < 300; i++) {
      highP = lim.update(1780.0, highP, power(highP, 20.0), 1.0);
    }
    assertTrue(highP < lowP - 100.0);
    assertTrue(lim.getPowerHeadroomFraction() < 0.01);
  }

  /**
   * Speed does not rise inside the hysteresis band.
   */
  @Test
  public void doesNotChatterInsideHysteresis() {
    CompressorLoadLimiter lim = new CompressorLoadLimiter(1780.0, 1000.0);
    lim.setHysteresis(0.01);
    double speed = lim.update(1780.0, 1700.0, 995.0, 1.0);
    assertEquals(1700.0, speed, 1.0e-12);
    speed = lim.update(1780.0, 1700.0, 900.0, 1.0);
    assertTrue(speed > 1700.0);
  }

  /**
   * Invalid configuration is rejected.
   */
  @Test
  public void rejectsInvalidLimits() {
    assertThrows(IllegalArgumentException.class, () -> new CompressorLoadLimiter(0.0, 100.0));
    assertThrows(IllegalArgumentException.class, () -> new CompressorLoadLimiter(100.0, -1.0));
  }
}
