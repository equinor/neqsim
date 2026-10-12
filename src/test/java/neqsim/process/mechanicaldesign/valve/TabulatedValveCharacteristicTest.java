package neqsim.process.mechanicaldesign.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TabulatedValveCharacteristic}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class TabulatedValveCharacteristicTest {
  /** The curve passes through the points and scales the full-open flow coefficient. */
  @Test
  void reproducesTheTabulatedPoints() {
    double[] o = {10.0, 30.0, 50.0, 70.0, 100.0};
    double[] cv = {2.0, 12.0, 30.0, 52.0, 80.0};
    TabulatedValveCharacteristic c = new TabulatedValveCharacteristic(o, cv);
    for (int i = 0; i < o.length; i++) {
      assertEquals(cv[i] / 80.0, c.getOpeningFactor(o[i]), 1.0e-12);
    }
    assertEquals(1.0, c.getOpeningFactor(100.0), 0.0);
    assertEquals(40.0, c.getActualKv(80.0, 100.0) * 0.5, 1.0e-12);
    assertEquals(30.0, c.getActualKv(80.0, 50.0), 1.0e-9);
    assertEquals(0.0, c.getOpeningFactor(0.0), 0.0);
    assertEquals(2.0 / 80.0 * 0.5, c.getOpeningFactor(5.0), 1.0e-12);
    assertEquals(1.0, c.getOpeningFactor(150.0), 0.0);
  }

  /** The interpolant never decreases and never leaves the data range, also across a flat stretch. */
  @Test
  void interpolationIsMonotoneWithoutOvershoot() {
    double[] o = {0.0, 20.0, 40.0, 60.0, 80.0, 100.0};
    double[] cv = {0.0, 1.0, 1.0, 1.1, 40.0, 100.0};
    TabulatedValveCharacteristic c = new TabulatedValveCharacteristic(o, cv);
    double previous = -1.0;
    for (double x = 0.0; x <= 100.0; x += 0.1) {
      double f = c.getOpeningFactor(x);
      assertTrue(f >= previous - 1.0e-12, "factor decreased at " + x);
      assertTrue(f >= 0.0 && f <= 1.0 + 1.0e-12, "factor outside 0..1 at " + x);
      previous = f;
    }
    assertEquals(0.01, c.getOpeningFactor(30.0), 1.0e-9);
  }

  /** A table sampled from the built-in equal-percentage characteristic is reproduced between the points. */
  @Test
  void followsEqualPercentageBetweenPoints() {
    EqualPercentageCharacteristic ep = new EqualPercentageCharacteristic();
    double[] o = new double[21];
    double[] cv = new double[21];
    for (int i = 0; i < o.length; i++) {
      o[i] = 5.0 * i;
      cv[i] = ep.getOpeningFactor(o[i]);
    }
    TabulatedValveCharacteristic c = new TabulatedValveCharacteristic(o, cv);
    for (double x = 2.5; x < 100.0; x += 5.0) {
      double exact = ep.getOpeningFactor(x);
      assertEquals(exact, c.getOpeningFactor(x), 0.02 * exact, "opening " + x);
    }
  }

  /** The opening for a required fraction inverts the curve. */
  @Test
  void inverseRecoversTheOpening() {
    double[] o = {10.0, 30.0, 50.0, 70.0, 100.0};
    double[] cv = {2.0, 12.0, 30.0, 52.0, 80.0};
    TabulatedValveCharacteristic c = new TabulatedValveCharacteristic(o, cv);
    for (double x = 15.0; x <= 95.0; x += 10.0) {
      assertEquals(x, c.openingForFlowFraction(c.getOpeningFactor(x)), 1.0e-6);
    }
    assertEquals(0.0, c.openingForFlowFraction(0.0), 0.0);
    assertEquals(100.0, c.openingForFlowFraction(1.5), 0.0);
  }

  /** Noisy plant data is sorted, averaged and made monotone. */
  @Test
  void measurementsAreCleaned() {
    double[] o = {50.0, 10.0, 30.0, 30.0, 70.0, 90.0, 100.0, 60.0, Double.NaN, 120.0};
    double[] cv = {30.0, 2.0, 10.0, 14.0, 45.0, 70.0, 80.0, 58.0, 5.0, 90.0};
    TabulatedValveCharacteristic c = TabulatedValveCharacteristic.fromMeasurements(o, cv);
    double[] pts = c.getFlowFractionPoints();
    double[] op = c.getOpeningPoints();
    assertEquals(7, op.length);
    for (int i = 1; i < pts.length; i++) {
      assertTrue(op[i] > op[i - 1]);
      assertTrue(pts[i] >= pts[i - 1]);
    }
    assertEquals(1.0, pts[pts.length - 1], 0.0);
    double previous = 0.0;
    for (double x = 0.0; x <= 100.0; x += 1.0) {
      assertTrue(c.getOpeningFactor(x) >= previous - 1.0e-12);
      previous = c.getOpeningFactor(x);
    }
  }

  /** Decreasing data is pooled to a flat stretch, not rejected, by the measurement route. */
  @Test
  void decreasingMeasurementsArePooled() {
    TabulatedValveCharacteristic c = TabulatedValveCharacteristic
        .fromMeasurements(new double[] {10.0, 40.0, 70.0, 100.0}, new double[] {10.0, 50.0, 30.0, 100.0});
    assertEquals(c.getOpeningFactor(40.0), c.getOpeningFactor(70.0), 1.0e-12);
    assertEquals(0.4, c.getOpeningFactor(40.0), 1.0e-12);
  }

  /** Invalid input is rejected. */
  @Test
  void rejectsInvalidInput() {
    assertThrows(IllegalArgumentException.class,
        () -> new TabulatedValveCharacteristic(new double[] {10.0}, new double[] {1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> new TabulatedValveCharacteristic(new double[] {10.0, 10.0}, new double[] {1.0, 2.0}));
    assertThrows(IllegalArgumentException.class,
        () -> new TabulatedValveCharacteristic(new double[] {10.0, 20.0}, new double[] {2.0, 1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> new TabulatedValveCharacteristic(new double[] {10.0, 20.0}, new double[] {0.0, 0.0}));
    assertThrows(IllegalArgumentException.class,
        () -> new TabulatedValveCharacteristic(new double[] {10.0, 120.0}, new double[] {1.0, 2.0}));
    assertThrows(IllegalArgumentException.class,
        () -> TabulatedValveCharacteristic.fromMeasurements(new double[] {10.0, 10.0}, new double[] {1.0, 2.0}));
  }
}
