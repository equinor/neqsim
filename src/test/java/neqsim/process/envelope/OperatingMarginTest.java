package neqsim.process.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OperatingMargin}.
 */
class OperatingMarginTest {

  @Test
  void testNormalMarginHighDirection() {
    OperatingMargin margin = new OperatingMargin("Sep-1", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");

    assertEquals("Sep-1", margin.getEquipmentName());
    assertEquals("pressure", margin.getVariableName());
    assertEquals(OperatingMargin.MarginType.PRESSURE, margin.getMarginType());
    assertEquals(OperatingMargin.Direction.HIGH, margin.getDirection());
    assertEquals(50.0, margin.getCurrentValue(), 1e-6);
    assertEquals(100.0, margin.getLimitValue(), 1e-6);
    assertEquals(50.0, margin.getAbsoluteMargin(), 1e-6);
    assertEquals(0.5, margin.getMarginFraction(), 1e-6);
    assertEquals(50.0, margin.getMarginPercent(), 1e-6);
    assertEquals(OperatingMargin.Status.NORMAL, margin.getStatus());
    assertEquals("bara", margin.getUnit());
  }

  @Test
  void testLowDirectionMargin() {
    // Current = 15, limit = 10 (low direction: margin = current - limit)
    OperatingMargin margin = new OperatingMargin("Sep-1", "levelLow",
        OperatingMargin.MarginType.LEVEL, OperatingMargin.Direction.LOW, 15.0, 10.0, "m");

    assertEquals(5.0, margin.getAbsoluteMargin(), 1e-6);
    assertEquals(OperatingMargin.Status.NORMAL, margin.getStatus());
  }

  @Test
  void testAdvisoryStatus() {
    // 18% margin -> ADVISORY (10-20%)
    OperatingMargin margin = new OperatingMargin("Comp-1", "surge",
        OperatingMargin.MarginType.SURGE, OperatingMargin.Direction.LOW, 118.0, 100.0, "m3/hr");

    double pct = margin.getMarginPercent();
    assertTrue(pct > 10 && pct <= 20, "Should be in advisory range, was: " + pct);
    assertEquals(OperatingMargin.Status.ADVISORY, margin.getStatus());
  }

  @Test
  void testWarningStatus() {
    // 8% margin -> WARNING (5-10%)
    OperatingMargin margin = new OperatingMargin("V-100", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 92.0, 100.0, "bara");

    assertEquals(OperatingMargin.Status.WARNING, margin.getStatus());
  }

  @Test
  void testCriticalStatus() {
    // 3% margin -> CRITICAL (0-5%)
    OperatingMargin margin = new OperatingMargin("V-100", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 97.0, 100.0, "bara");

    assertEquals(OperatingMargin.Status.CRITICAL, margin.getStatus());
  }

  @Test
  void testViolatedStatus() {
    // Exceeded limit -> VIOLATED
    OperatingMargin margin = new OperatingMargin("V-100", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 105.0, 100.0, "bara");

    assertEquals(OperatingMargin.Status.VIOLATED, margin.getStatus());
  }

  @Test
  void testGetKey() {
    OperatingMargin margin = new OperatingMargin("Sep-1", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");

    assertEquals("Sep-1.pressure.HIGH", margin.getKey());
  }

  @Test
  void testUpdateCurrentValue() {
    OperatingMargin margin = new OperatingMargin("Sep-1", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");

    margin.updateCurrentValue(95.0);
    assertEquals(95.0, margin.getCurrentValue(), 1e-6);
    assertEquals(OperatingMargin.Status.CRITICAL, margin.getStatus());
  }

  @Test
  void testCompareTo() {
    OperatingMargin normal = new OperatingMargin("A", "x", OperatingMargin.MarginType.PRESSURE,
        OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");
    OperatingMargin critical = new OperatingMargin("B", "y", OperatingMargin.MarginType.PRESSURE,
        OperatingMargin.Direction.HIGH, 97.0, 100.0, "bara");

    assertTrue(critical.compareTo(normal) < 0, "Critical should sort before normal");
  }

  @Test
  void testSeverityScore() {
    OperatingMargin violated = new OperatingMargin("A", "x", OperatingMargin.MarginType.PRESSURE,
        OperatingMargin.Direction.HIGH, 105.0, 100.0, "bara");
    OperatingMargin normal = new OperatingMargin("B", "y", OperatingMargin.MarginType.PRESSURE,
        OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");

    assertTrue(violated.getSeverityScore() > normal.getSeverityScore());
  }

  @Test
  void testToString() {
    OperatingMargin margin = new OperatingMargin("Sep-1", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");

    String s = margin.toString();
    assertNotNull(s);
    assertTrue(s.contains("Sep-1"));
    assertTrue(s.contains("pressure"));
  }
}
