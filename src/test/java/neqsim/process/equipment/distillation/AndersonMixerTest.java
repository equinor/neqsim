package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Anderson mixing helper used by the sequential distillation solver.
 */
public class AndersonMixerTest {
  /**
   * Evaluate a linear contraction with one slow, one oscillating and one fast mode.
   *
   * @param x state
   * @return mapped state with fixed point (1, 2, 3)
   */
  private static double[] map(double[] x) {
    return new double[] {1.0 + 0.99 * (x[0] - 1.0), 2.0 - 0.9 * (x[1] - 2.0), 3.0 + 0.2 * (x[2] - 3.0)};
  }

  /** Mixing removes slow and oscillating linear modes in a few evaluations. */
  @Test
  public void convergesLinearMapInFewEvaluations() {
    double[] weights = {1.0, 1.0, 1.0};
    AndersonMixer mixer = new AndersonMixer(5);
    double[] x = {10.0, -5.0, 0.0};
    for (int i = 0; i < 8; i++) {
      double[] g = map(x);
      mixer.add(x, g, weights);
      double[] mixed = mixer.mix();
      x = mixed == null ? g : mixed;
    }
    assertEquals(1.0, x[0], 1.0e-6);
    assertEquals(2.0, x[1], 1.0e-6);
    assertEquals(3.0, x[2], 1.0e-6);

    double[] plain = {10.0, -5.0, 0.0};
    for (int i = 0; i < 8; i++) {
      plain = map(plain);
    }
    assertTrue(Math.abs(plain[0] - 1.0) > 1.0, "Plain substitution must still be far from the fixed point");
  }

  /** A single stored pair or a reset history gives no mixed state. */
  @Test
  public void needsTwoPairs() {
    AndersonMixer mixer = new AndersonMixer(3);
    assertNull(mixer.mix());
    mixer.add(new double[] {0.0}, new double[] {1.0}, new double[] {1.0});
    assertNull(mixer.mix());
    assertEquals(1.0, mixer.latestResidualNorm(), 0.0);
    mixer.add(new double[] {1.0}, new double[] {1.5}, new double[] {1.0});
    assertEquals(2.0, mixer.mix()[0], 1.0e-9);
    mixer.reset();
    assertEquals(0, mixer.size());
    assertNull(mixer.mix());
  }

  /** Identical residuals carry no direction and must not produce a mixed state. */
  @Test
  public void rejectsDegenerateHistory() {
    AndersonMixer mixer = new AndersonMixer(3);
    mixer.add(new double[] {0.0, 0.0}, new double[] {1.0, 1.0}, new double[] {1.0, 1.0});
    mixer.add(new double[] {1.0, 1.0}, new double[] {2.0, 2.0}, new double[] {1.0, 1.0});
    assertNull(mixer.mix());
  }
}
