package neqsim.process.safety.inherent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class InherentSafetyEvaluatorTest {

  @Test
  void overallIndexIsAverage() {
    InherentSafetyEvaluator ev = new InherentSafetyEvaluator("Option A");
    ev.score(InherentSafetyEvaluator.Pillar.SUBSTITUTE, 8.0, "low-tox solvent");
    ev.score(InherentSafetyEvaluator.Pillar.MINIMIZE, 6.0, "smaller buffer");
    ev.score(InherentSafetyEvaluator.Pillar.MODERATE, 4.0, null);
    ev.score(InherentSafetyEvaluator.Pillar.SIMPLIFY, 6.0, null);
    assertEquals(6.0, ev.overallIndex(), 1.0e-9);
    assertTrue(ev.report().contains("OVERALL"));
  }
}
