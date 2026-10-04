package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ValveRangeabilityScreening}.
 */
public class ValveRangeabilityScreeningTest {
  private static final double DELTA = 1.0e-3;

  /** Non-finite inputs must not become apparently usable flow fractions. */
  @Test
  public void testRejectsNonFiniteInput() {
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class,
          () -> ValveRangeabilityScreening.flowFraction(ValveTrimCharacteristic.LINEAR, invalid, 50.0));
      assertThrows(IllegalArgumentException.class,
          () -> ValveRangeabilityScreening.flowFraction(ValveTrimCharacteristic.LINEAR, 0.5, invalid));
    }
    assertThrows(IllegalArgumentException.class, () -> ValveRangeabilityScreening.flowFraction(null, 0.5, 50.0));
  }

  @Test
  public void testEqualPercentageFlowRatio() {
    ValveRangeabilityScreening.Result result = ValveRangeabilityScreening
        .evaluate(ValveTrimCharacteristic.EQUAL_PERCENTAGE, 0.012, 0.029, 50.0);
    assertEquals(1.0688, result.getFlowRatio(), DELTA);
    assertTrue(result.getExtraFlowFraction() > 0.0);
  }

  @Test
  public void testLinearFlowRatioExceedsEqualPercentageAtLowTravel() {
    ValveRangeabilityScreening.Result linear = ValveRangeabilityScreening.evaluate(ValveTrimCharacteristic.LINEAR,
        0.012, 0.029, 50.0);
    ValveRangeabilityScreening.Result equalPct = ValveRangeabilityScreening
        .evaluate(ValveTrimCharacteristic.EQUAL_PERCENTAGE, 0.012, 0.029, 50.0);
    assertEquals(1.5246, linear.getFlowRatio(), DELTA);
    assertTrue(linear.getFlowRatio() > equalPct.getFlowRatio(),
        "Linear trim should show higher relative-gain sensitivity near a low clamp than equal-percentage trim");
  }

  @Test
  public void testQuickOpeningIgnoresRangeability() {
    ValveRangeabilityScreening.Result r30 = ValveRangeabilityScreening.evaluate(ValveTrimCharacteristic.QUICK_OPENING,
        0.012, 0.029, 30.0);
    ValveRangeabilityScreening.Result r50 = ValveRangeabilityScreening.evaluate(ValveTrimCharacteristic.QUICK_OPENING,
        0.012, 0.029, 50.0);
    assertEquals(r30.getFlowRatio(), r50.getFlowRatio(), DELTA);
    assertEquals(1.5546, r30.getFlowRatio(), DELTA);
  }

  @Test
  public void testFullTravelGivesFlowFractionOfOne() {
    double f = ValveRangeabilityScreening.flowFraction(ValveTrimCharacteristic.EQUAL_PERCENTAGE, 1.0, 50.0);
    assertEquals(1.0, f, DELTA);
  }

  @Test
  public void testInvalidRangeabilityThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> ValveRangeabilityScreening.flowFraction(ValveTrimCharacteristic.LINEAR, 0.5, 1.0));
  }

  @Test
  public void testTravelClampedToZeroOneRange() {
    double below = ValveRangeabilityScreening.flowFraction(ValveTrimCharacteristic.LINEAR, -0.2, 30.0);
    double atZero = ValveRangeabilityScreening.flowFraction(ValveTrimCharacteristic.LINEAR, 0.0, 30.0);
    double above = ValveRangeabilityScreening.flowFraction(ValveTrimCharacteristic.LINEAR, 1.5, 30.0);
    double atOne = ValveRangeabilityScreening.flowFraction(ValveTrimCharacteristic.LINEAR, 1.0, 30.0);
    assertEquals(atZero, below, DELTA);
    assertEquals(atOne, above, DELTA);
  }
}
