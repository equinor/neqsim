package neqsim.process.mechanicaldesign.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for valve characteristic implementations.
 */
public class ValveCharacteristicTest {
  // ========== Linear Characteristic Tests ==========

  @Test
  void testLinearCharacteristicAtFullOpen() {
    LinearCharacteristic linear = new LinearCharacteristic();
    assertEquals(1.0, linear.getOpeningFactor(100.0), 1e-10);
  }

  @Test
  void testLinearCharacteristicAtClosed() {
    LinearCharacteristic linear = new LinearCharacteristic();
    assertEquals(0.0, linear.getOpeningFactor(0.0), 1e-10);
  }

  @Test
  void testLinearCharacteristicAtHalfOpen() {
    LinearCharacteristic linear = new LinearCharacteristic();
    assertEquals(0.5, linear.getOpeningFactor(50.0), 1e-10);
  }

  @Test
  void testLinearKvCalculation() {
    LinearCharacteristic linear = new LinearCharacteristic();
    double Kv = 100.0;
    assertEquals(50.0, linear.getActualKv(Kv, 50.0), 1e-10);
    assertEquals(100.0, linear.getActualKv(Kv, 100.0), 1e-10);
    assertEquals(0.0, linear.getActualKv(Kv, 0.0), 1e-10);
  }

  // ========== Equal Percentage Characteristic Tests ==========

  @Test
  void testEqualPercentageAtFullOpen() {
    EqualPercentageCharacteristic eqPct = new EqualPercentageCharacteristic();
    assertEquals(1.0, eqPct.getOpeningFactor(100.0), 1e-10);
  }

  @Test
  void testEqualPercentageAtClosed() {
    EqualPercentageCharacteristic eqPct = new EqualPercentageCharacteristic();
    // At 0% opening, factor should be 1/rangeability = 1/50 = 0.02
    assertEquals(1.0 / 50.0, eqPct.getOpeningFactor(0.0), 1e-10);
  }

  @Test
  void testEqualPercentageAtHalfOpen() {
    EqualPercentageCharacteristic eqPct = new EqualPercentageCharacteristic(50.0);
    // At 50% opening: R^(0.5-1) = 50^(-0.5) = 1/sqrt(50) ≈ 0.1414
    double expected = Math.pow(50.0, -0.5);
    assertEquals(expected, eqPct.getOpeningFactor(50.0), 1e-6);
  }

  @Test
  void testEqualPercentageWithCustomRangeability() {
    EqualPercentageCharacteristic eqPct = new EqualPercentageCharacteristic(100.0);
    assertEquals(100.0, eqPct.getRangeability(), 1e-10);
    // At 0%: 1/100 = 0.01
    assertEquals(0.01, eqPct.getOpeningFactor(0.0), 1e-10);
  }

  @Test
  void testEqualPercentageInvalidRangeability() {
    assertThrows(IllegalArgumentException.class, () -> {
      new EqualPercentageCharacteristic(0.5);
    });
  }

  @Test
  void testEqualPercentageKvCalculation() {
    EqualPercentageCharacteristic eqPct = new EqualPercentageCharacteristic(50.0);
    double Kv = 100.0;
    // At 100%: Kv * 1.0 = 100
    assertEquals(100.0, eqPct.getActualKv(Kv, 100.0), 1e-10);
    // At 50%: Kv * 50^(-0.5) ≈ 14.14
    assertEquals(100.0 * Math.pow(50.0, -0.5), eqPct.getActualKv(Kv, 50.0), 1e-6);
  }

  @Test
  void testEqualPercentageLogarithmicBehavior() {
    // Equal increments in opening should produce equal percentage changes in flow
    EqualPercentageCharacteristic eqPct = new EqualPercentageCharacteristic(50.0);

    double flow20 = eqPct.getOpeningFactor(20.0);
    double flow40 = eqPct.getOpeningFactor(40.0);
    double flow60 = eqPct.getOpeningFactor(60.0);
    double flow80 = eqPct.getOpeningFactor(80.0);

    // Ratio between consecutive 20% increments should be constant
    double ratio1 = flow40 / flow20;
    double ratio2 = flow60 / flow40;
    double ratio3 = flow80 / flow60;

    assertEquals(ratio1, ratio2, 1e-6);
    assertEquals(ratio2, ratio3, 1e-6);
  }

  // ========== Quick Opening Characteristic Tests ==========

  @Test
  void testQuickOpeningAtFullOpen() {
    QuickOpeningCharacteristic quickOpen = new QuickOpeningCharacteristic();
    assertEquals(1.0, quickOpen.getOpeningFactor(100.0), 1e-10);
  }

  @Test
  void testQuickOpeningAtClosed() {
    QuickOpeningCharacteristic quickOpen = new QuickOpeningCharacteristic();
    assertEquals(0.0, quickOpen.getOpeningFactor(0.0), 1e-10);
  }

  @Test
  void testQuickOpeningAtQuarterOpen() {
    QuickOpeningCharacteristic quickOpen = new QuickOpeningCharacteristic();
    // At 25% opening: sqrt(0.25) = 0.5
    assertEquals(0.5, quickOpen.getOpeningFactor(25.0), 1e-10);
  }

  @Test
  void testQuickOpeningHighGainAtLowOpening() {
    QuickOpeningCharacteristic quickOpen = new QuickOpeningCharacteristic();
    // Quick opening should give 50% flow at 25% opening (sqrt(0.25) = 0.5)
    assertTrue(quickOpen.getOpeningFactor(25.0) > 0.25);
    // At high openings, flow is still above linear due to sqrt characteristic
    // sqrt(0.75) ≈ 0.866 which is > 0.75
    assertTrue(quickOpen.getOpeningFactor(75.0) > 0.75);
  }

  @Test
  void testQuickOpeningKvCalculation() {
    QuickOpeningCharacteristic quickOpen = new QuickOpeningCharacteristic();
    double Kv = 100.0;
    assertEquals(100.0, quickOpen.getActualKv(Kv, 100.0), 1e-10);
    assertEquals(50.0, quickOpen.getActualKv(Kv, 25.0), 1e-10); // sqrt(0.25) * 100
  }

  // ========== Modified Parabolic Characteristic Tests ==========

  @Test
  void testModifiedParabolicAtFullOpen() {
    ModifiedParabolicCharacteristic parabolic = new ModifiedParabolicCharacteristic();
    assertEquals(1.0, parabolic.getOpeningFactor(100.0), 1e-10);
  }

  @Test
  void testModifiedParabolicAtClosed() {
    ModifiedParabolicCharacteristic parabolic = new ModifiedParabolicCharacteristic();
    assertEquals(0.0, parabolic.getOpeningFactor(0.0), 1e-10);
  }

  @Test
  void testModifiedParabolicAtHalfOpen() {
    ModifiedParabolicCharacteristic parabolic = new ModifiedParabolicCharacteristic(2.0);
    // At 50% opening: 0.5^2 = 0.25
    assertEquals(0.25, parabolic.getOpeningFactor(50.0), 1e-10);
  }

  @Test
  void testModifiedParabolicWithCustomExponent() {
    ModifiedParabolicCharacteristic parabolic = new ModifiedParabolicCharacteristic(1.5);
    assertEquals(1.5, parabolic.getExponent(), 1e-10);
    // At 50%: 0.5^1.5 ≈ 0.3536
    assertEquals(Math.pow(0.5, 1.5), parabolic.getOpeningFactor(50.0), 1e-6);
  }

  @Test
  void testModifiedParabolicInvalidExponent() {
    assertThrows(IllegalArgumentException.class, () -> {
      new ModifiedParabolicCharacteristic(-1.0);
    });
    assertThrows(IllegalArgumentException.class, () -> {
      new ModifiedParabolicCharacteristic(0.0);
    });
  }

  @Test
  void testModifiedParabolicKvCalculation() {
    ModifiedParabolicCharacteristic parabolic = new ModifiedParabolicCharacteristic(2.0);
    double Kv = 100.0;
    assertEquals(100.0, parabolic.getActualKv(Kv, 100.0), 1e-10);
    assertEquals(25.0, parabolic.getActualKv(Kv, 50.0), 1e-10); // 0.5^2 * 100
  }

  // ========== Comparison Tests ==========

  @Test
  void testCharacteristicComparisonAtMidRange() {
    LinearCharacteristic linear = new LinearCharacteristic();
    EqualPercentageCharacteristic eqPct = new EqualPercentageCharacteristic(50.0);
    QuickOpeningCharacteristic quickOpen = new QuickOpeningCharacteristic();
    ModifiedParabolicCharacteristic parabolic = new ModifiedParabolicCharacteristic(2.0);

    double opening = 50.0;

    double linearFlow = linear.getOpeningFactor(opening);
    double eqPctFlow = eqPct.getOpeningFactor(opening);
    double quickFlow = quickOpen.getOpeningFactor(opening);
    double parabolicFlow = parabolic.getOpeningFactor(opening);

    // At 50% opening, ordering should be: equal% < parabolic < linear < quick opening
    assertTrue(eqPctFlow < parabolicFlow);
    assertTrue(parabolicFlow < linearFlow);
    assertTrue(linearFlow < quickFlow);
  }

  @Test
  void testAllCharacteristicsConvergeAtFullOpen() {
    LinearCharacteristic linear = new LinearCharacteristic();
    EqualPercentageCharacteristic eqPct = new EqualPercentageCharacteristic();
    QuickOpeningCharacteristic quickOpen = new QuickOpeningCharacteristic();
    ModifiedParabolicCharacteristic parabolic = new ModifiedParabolicCharacteristic();

    assertEquals(1.0, linear.getOpeningFactor(100.0), 1e-10);
    assertEquals(1.0, eqPct.getOpeningFactor(100.0), 1e-10);
    assertEquals(1.0, quickOpen.getOpeningFactor(100.0), 1e-10);
    assertEquals(1.0, parabolic.getOpeningFactor(100.0), 1e-10);
  }
}
