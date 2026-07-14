package neqsim.util.agentic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SolutionRanker}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class SolutionRankerTest {

  /**
   * A solution that is effective, cheap, low-risk and quick ranks above an expensive, slow, risky one under
   * benefit/cost criteria directions.
   */
  @Test
  void ranksBestBalancedSolutionFirst() {
    SolutionRanker r = new SolutionRanker();
    r.addCriterion("effectiveness", 0.4, true);
    r.addCriterion("cost", 0.3, false);
    r.addCriterion("risk", 0.2, false);
    r.addCriterion("lead_time", 0.1, false);

    r.addSolution("S1", "Operating setpoint change");
    r.setScore("S1", "effectiveness", 0.7);
    r.setScore("S1", "cost", 1.0);
    r.setScore("S1", "risk", 1.0);
    r.setScore("S1", "lead_time", 1.0);

    r.addSolution("S2", "Replace equipment");
    r.setScore("S2", "effectiveness", 0.9);
    r.setScore("S2", "cost", 100.0);
    r.setScore("S2", "risk", 8.0);
    r.setScore("S2", "lead_time", 52.0);

    List<SolutionRanker.RankedSolution> ranked = r.rank();
    assertEquals("S1", ranked.get(0).getId());
    assertEquals(1, ranked.get(0).getRank());
    assertTrue(ranked.get(0).getOverall() > ranked.get(1).getOverall());
  }

  /** The recommended solution id in JSON matches the top-ranked solution. */
  @Test
  void jsonReportsRecommendedSolution() {
    SolutionRanker r = new SolutionRanker();
    r.addCriterion("effectiveness", 1.0, true);
    r.addSolution("A", "low");
    r.setScore("A", "effectiveness", 0.2);
    r.addSolution("B", "high");
    r.setScore("B", "effectiveness", 0.9);
    String json = r.toJson();
    assertTrue(json.contains("\"recommendedSolutionId\": \"B\""));
    assertTrue(json.contains("\"schemaVersion\": \"1.0\""));
  }

  /** A missing criterion score is treated as worst and flagged. */
  @Test
  void missingScoreFlaggedAndTreatedAsWorst() {
    SolutionRanker r = new SolutionRanker();
    r.addCriterion("effectiveness", 1.0, true);
    r.addSolution("A", "complete");
    r.setScore("A", "effectiveness", 0.8);
    r.addSolution("B", "missing score");
    List<SolutionRanker.RankedSolution> ranked = r.rank();
    assertEquals("A", ranked.get(0).getId());
    String json = r.toJson();
    assertTrue(json.contains("\"missingCriteria\""));
    assertTrue(json.contains("effectiveness"));
  }

  /** Invalid configuration is rejected. */
  @Test
  void invalidConfigurationRejected() {
    SolutionRanker r = new SolutionRanker();
    assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        r.rank();
      }
    });
    r.addCriterion("cost", 0.5, false);
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        r.addCriterion("cost", 0.5, false);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        r.setScore("nope", "cost", 1.0);
      }
    });
  }
}
