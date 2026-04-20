package neqsim.process.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MarginTracker}.
 */
class MarginTrackerTest {

  private OperatingMargin margin;
  private MarginTracker tracker;

  @BeforeEach
  void setUp() {
    margin = new OperatingMargin("Sep-1", "pressure", OperatingMargin.MarginType.PRESSURE,
        OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");
    tracker = new MarginTracker(margin, 10);
  }

  @Test
  void testInitialState() {
    assertEquals(0, tracker.getSampleCount());
    assertFalse(tracker.isTrendValid());
    assertEquals(MarginTracker.TrendDirection.UNKNOWN, tracker.getTrendDirection());
  }

  @Test
  void testRecordSamples() {
    margin.updateCurrentValue(50.0);
    tracker.recordSample(0.0);
    assertEquals(1, tracker.getSampleCount());

    margin.updateCurrentValue(55.0);
    tracker.recordSample(1.0);
    assertEquals(2, tracker.getSampleCount());
  }

  @Test
  void testDegradingTrend() {
    // Simulate pressure increasing toward limit (margin decreasing)
    for (int i = 0; i < 10; i++) {
      margin.updateCurrentValue(50.0 + i * 5.0); // 50, 55, 60, ..., 95
      tracker.recordSample(i * 10.0);
    }

    assertTrue(tracker.isTrendValid());
    MarginTracker.TrendDirection dir = tracker.getTrendDirection();
    assertTrue(
        dir == MarginTracker.TrendDirection.DEGRADING
            || dir == MarginTracker.TrendDirection.RAPIDLY_DEGRADING,
        "Expected degrading, got: " + dir);
  }

  @Test
  void testImprovingTrend() {
    // Simulate pressure decreasing from limit (margin increasing)
    for (int i = 0; i < 10; i++) {
      margin.updateCurrentValue(95.0 - i * 5.0); // 95, 90, 85, ..., 50
      tracker.recordSample(i * 10.0);
    }

    assertTrue(tracker.isTrendValid());
    assertEquals(MarginTracker.TrendDirection.IMPROVING, tracker.getTrendDirection());
  }

  @Test
  void testStableTrend() {
    // Simulate constant pressure
    for (int i = 0; i < 10; i++) {
      margin.updateCurrentValue(50.0);
      tracker.recordSample(i * 10.0);
    }

    assertTrue(tracker.isTrendValid());
    assertEquals(MarginTracker.TrendDirection.STABLE, tracker.getTrendDirection());
  }

  @Test
  void testTimeToBreachDegrading() {
    for (int i = 0; i < 10; i++) {
      margin.updateCurrentValue(50.0 + i * 5.0);
      tracker.recordSample(i * 10.0);
    }

    double ttb = tracker.getTimeToBreachSeconds();
    assertTrue(ttb > 0, "Time to breach should be positive");
    assertFalse(Double.isInfinite(ttb), "Should not be infinite when margin is degrading");
  }

  @Test
  void testTimeToBreachMinutes() {
    for (int i = 0; i < 10; i++) {
      margin.updateCurrentValue(50.0 + i * 5.0);
      tracker.recordSample(i * 10.0);
    }

    double ttbSec = tracker.getTimeToBreachSeconds();
    double ttbMin = tracker.getTimeToBreachMinutes();
    assertEquals(ttbSec / 60.0, ttbMin, 1e-6);
  }

  @Test
  void testCircularBufferOverflow() {
    // Window size is 10, add 15 samples
    for (int i = 0; i < 15; i++) {
      margin.updateCurrentValue(50.0 + i);
      tracker.recordSample(i * 1.0);
    }

    assertEquals(10, tracker.getSampleCount(), "Should be capped at window size");
  }

  @Test
  void testMarginHistory() {
    for (int i = 0; i < 5; i++) {
      margin.updateCurrentValue(50.0 + i * 10.0);
      tracker.recordSample(i * 1.0);
    }

    List<Double> history = tracker.getMarginHistory();
    assertNotNull(history);
    assertEquals(5, history.size());
  }

  @Test
  void testTimestampHistory() {
    for (int i = 0; i < 5; i++) {
      margin.updateCurrentValue(50.0);
      tracker.recordSample(i * 10.0);
    }

    List<Double> timestamps = tracker.getTimestampHistory();
    assertNotNull(timestamps);
    assertEquals(5, timestamps.size());
    assertEquals(0.0, timestamps.get(0), 1e-6);
    assertEquals(40.0, timestamps.get(4), 1e-6);
  }

  @Test
  void testReset() {
    for (int i = 0; i < 5; i++) {
      margin.updateCurrentValue(50.0);
      tracker.recordSample(i);
    }
    assertTrue(tracker.getSampleCount() > 0);

    tracker.reset();
    assertEquals(0, tracker.getSampleCount());
    assertFalse(tracker.isTrendValid());
  }

  @Test
  void testRSquared() {
    // Perfect linear trend should give high R-squared
    for (int i = 0; i < 10; i++) {
      margin.updateCurrentValue(50.0 + i * 5.0);
      tracker.recordSample(i * 10.0);
    }

    double rSquared = tracker.getTrendRSquared();
    assertTrue(rSquared > 0.95, "Perfect linear should give R^2 > 0.95, got: " + rSquared);
  }
}
