package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExactArgumentMemo}.
 *
 * @author ESOL
 * @version 1.0
 */
class ExactArgumentMemoTest {
  /**
   * A remembered result is offered for the same bit patterns and for nothing else.
   */
  @Test
  void matchesOnlyBitwiseIdenticalArguments() {
    ExactArgumentMemo<Double> memo = new ExactArgumentMemo<Double>(Double.valueOf(0.25), 1.0, 2.0, 0.0);
    assertEquals(0.25, memo.getValue().doubleValue());
    assertTrue(memo.matches(1.0, 2.0, 0.0));
    assertFalse(memo.matches(1.0, Math.nextUp(2.0), 0.0));
    assertFalse(memo.matches(1.0, 2.0, -0.0), "a signed zero can change the sign of a derived velocity");
    assertFalse(memo.matches(1.0, 2.0));
    assertFalse(memo.matches(1.0, 2.0, 0.0, 0.0));
  }

  /**
   * A NaN switch value, as used for the automatic front-mixing weight, matches itself.
   */
  @Test
  void nanArgumentMatchesTheSameNan() {
    ExactArgumentMemo<String> memo = new ExactArgumentMemo<String>("automatic", Double.NaN, 3.0);
    assertTrue(memo.matches(Double.NaN, 3.0));
    assertFalse(memo.matches(0.0, 3.0));
  }

  /**
   * Later changes to the caller's argument array do not change what was remembered.
   */
  @Test
  void remembersACopyOfTheArguments() {
    double[] arguments = {4.0, 5.0};
    ExactArgumentMemo<Double> memo = new ExactArgumentMemo<Double>(Double.valueOf(1.0), arguments);
    arguments[0] = 9.0;
    assertTrue(memo.matches(4.0, 5.0));
    assertFalse(memo.matches(9.0, 5.0));
  }
}
