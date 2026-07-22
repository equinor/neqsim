package neqsim.process.chemistry.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.scale.BrineMixingScaleEvaluator.BrineComposition;

/**
 * Tests for {@link BrineMixingScaleEvaluator} — mixing-induced sulphate scale from incompatible brines.
 *
 * @author ESOL
 * @version 1.0
 */
public class BrineMixingScaleEvaluatorTest {

  /**
   * Verifies that mixing a barium/strontium-rich formation water with a sulphate-rich seawater produces a sulphate
   * scale, and that the sweep spans the full mixing range.
   */
  @Test
  void mixingProducesSulphateScale() {
    // Formation water: high Ba/Sr/Ca, essentially no sulphate.
    BrineComposition formation = new BrineComposition(5000.0, 300.0, 800.0, 500.0, 30000.0, 400.0, 55000.0, 0.0, 400.0);
    // Injection seawater: high sulphate, low Ba/Sr.
    BrineComposition seawater = new BrineComposition(400.0, 0.0, 8.0, 1300.0, 11000.0, 400.0, 19000.0, 2700.0, 140.0);

    BrineMixingScaleEvaluator evaluator = new BrineMixingScaleEvaluator(formation, seawater);
    evaluator.setConditions(60.0, 100.0).setPH(6.0).setSteps(11).evaluate();

    assertEquals(11, evaluator.getPoints().size(), "should evaluate the requested number of mixing points");
    assertEquals(0.0, evaluator.getPoints().get(0).getFractionA(), 1.0e-9);
    assertEquals(1.0, evaluator.getPoints().get(evaluator.getPoints().size() - 1).getFractionA(), 1.0e-9);

    // A sulphate scale (BaSO4 or SrSO4 or CaSO4) should be the worst mineral when the two mix.
    assertTrue(evaluator.getWorstMineral().endsWith("SO4"),
        "worst mineral should be a sulphate scale, was " + evaluator.getWorstMineral());
    assertNotNull(evaluator.toJson());
  }

  /**
   * Verifies that the worst mixing fraction is at an intermediate blend, not a pure end member, for a classic
   * barium-sulphate incompatibility.
   */
  @Test
  void worstCaseIsAtIntermediateMix() {
    BrineComposition formation = new BrineComposition(2000.0, 500.0, 200.0, 300.0, 25000.0, 300.0, 45000.0, 0.0, 300.0);
    BrineComposition seawater = new BrineComposition(400.0, 0.0, 8.0, 1300.0, 11000.0, 400.0, 19000.0, 2800.0, 140.0);

    BrineMixingScaleEvaluator evaluator = new BrineMixingScaleEvaluator(formation, seawater);
    evaluator.setConditions(80.0, 100.0).setPH(6.2).setSteps(21).evaluate();

    double worstFraction = evaluator.getWorstFractionA();
    // The peak BaSO4 supersaturation from Ba (brine A) meeting SO4 (brine B) occurs between the ends.
    assertTrue(worstFraction > 0.0 && worstFraction < 1.0,
        "worst mixing fraction should be intermediate, was " + worstFraction);
    assertTrue(evaluator.getWorstSI() > Double.NEGATIVE_INFINITY);
  }
}
