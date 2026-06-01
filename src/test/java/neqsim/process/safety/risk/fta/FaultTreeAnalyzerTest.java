package neqsim.process.safety.risk.fta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FaultTreeAnalyzerTest {

  @Test
  void andGateIsProductOfBasics() {
    FaultTreeNode a = FaultTreeNode.basic("A", 0.1);
    FaultTreeNode b = FaultTreeNode.basic("B", 0.2);
    FaultTreeNode and = FaultTreeNode.and("Both fail", a, b);
    FaultTreeAnalyzer fa = new FaultTreeAnalyzer();
    assertEquals(0.02, fa.topEventProbability(and), 1.0e-9);
  }

  @Test
  void orGateIsRareEventComplement() {
    FaultTreeNode a = FaultTreeNode.basic("A", 0.1);
    FaultTreeNode b = FaultTreeNode.basic("B", 0.2);
    FaultTreeNode or = FaultTreeNode.or("Either fail", a, b);
    FaultTreeAnalyzer fa = new FaultTreeAnalyzer();
    // 1 - (1-0.1)*(1-0.2) = 1 - 0.72 = 0.28
    assertEquals(0.28, fa.topEventProbability(or), 1.0e-9);
  }

  @Test
  void votingTwoOfThree() {
    FaultTreeNode a = FaultTreeNode.basic("A", 0.1);
    FaultTreeNode b = FaultTreeNode.basic("B", 0.1);
    FaultTreeNode c = FaultTreeNode.basic("C", 0.1);
    FaultTreeNode g = FaultTreeNode.voting("2oo3", 2, a, b, c);
    FaultTreeAnalyzer fa = new FaultTreeAnalyzer();
    // P(>=2 of 3) = 3*0.01*0.9 + 0.001 = 0.028
    assertEquals(0.028, fa.topEventProbability(g), 1.0e-9);
  }

  @Test
  void betaFactorChangesORProbability() {
    // β-factor model on an OR gate decomposes the failure rate into independent and
    // common-mode contributions. For an OR (single-failure) configuration the resulting
    // probability is a convex combination of the independent disjunction and the
    // common-mode (basic) probability — i.e. it is always lower than pure independent
    // disjunction. The test verifies that CCF measurably alters the result.
    FaultTreeNode a = FaultTreeNode.basic("A", 0.01);
    FaultTreeNode b = FaultTreeNode.basic("B", 0.01);
    FaultTreeAnalyzer fa = new FaultTreeAnalyzer();
    double pInd = fa.topEventProbability(FaultTreeNode.or("any", a, b));
    double pCcf = fa.topEventProbability(
        FaultTreeNode.or("any", a, b).withCCF(0.10));
    assertTrue(Math.abs(pCcf - pInd) > 1.0e-6);
  }

  @Test
  void minimalCutSets() {
    FaultTreeNode a = FaultTreeNode.basic("A", 0.1);
    FaultTreeNode b = FaultTreeNode.basic("B", 0.1);
    FaultTreeNode c = FaultTreeNode.basic("C", 0.1);
    // top = (A AND B) OR C → cut sets {C}, {A, B}
    FaultTreeNode top = FaultTreeNode.or("top",
        FaultTreeNode.and("ab", a, b), c);
    FaultTreeAnalyzer fa = new FaultTreeAnalyzer();
    Set<List<String>> cs = fa.minimalCutSets(top, 3);
    assertEquals(2, cs.size());
  }
}
