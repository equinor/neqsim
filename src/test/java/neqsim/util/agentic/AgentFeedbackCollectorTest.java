package neqsim.util.agentic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for AgentFeedbackCollector — aggregates agent session metrics.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AgentFeedbackCollectorTest {

  @Test
  @DisplayName("Record sessions and compute success rate")
  void testSuccessRate() {
    AgentFeedbackCollector collector = new AgentFeedbackCollector();

    // Record 3 successful sessions
    for (int i = 0; i < 3; i++) {
      AgentSession session = AgentSession.start("solve.task", "task " + i);
      session.complete(AgentSession.Outcome.SUCCESS);
      collector.recordSession(session);
    }

    // Record 1 failed session
    AgentSession failed = AgentSession.start("solve.task", "failing task");
    failed.fail("Flash convergence failure");
    collector.recordSession(failed);

    assertEquals(4, collector.getSessionCount());
    assertEquals(0.75, collector.getOverallSuccessRate(), 0.001);
    assertEquals(0.75, collector.getSuccessRate("solve.task"), 0.001);
  }

  @Test
  @DisplayName("Success rate for unknown agent returns 0")
  void testSuccessRateUnknownAgent() {
    AgentFeedbackCollector collector = new AgentFeedbackCollector();
    assertEquals(0.0, collector.getSuccessRate("unknown.agent"), 0.001);
  }

  @Test
  @DisplayName("Partial outcomes count as success")
  void testPartialCountsAsSuccess() {
    AgentFeedbackCollector collector = new AgentFeedbackCollector();

    AgentSession partial = AgentSession.start("solve.task", "partial task");
    partial.complete(AgentSession.Outcome.PARTIAL);
    collector.recordSession(partial);

    assertEquals(1.0, collector.getOverallSuccessRate(), 0.001);
  }

  @Test
  @DisplayName("Failure classification works correctly")
  void testFailureClassification() {
    assertEquals(AgentFeedbackCollector.FailureCategory.CONVERGENCE,
        AgentFeedbackCollector.classifyFailure("Flash convergence failed after 100 iterations"));

    assertEquals(AgentFeedbackCollector.FailureCategory.MISSING_API,
        AgentFeedbackCollector.classifyFailure("Method not found: getColumnDiameter"));

    assertEquals(AgentFeedbackCollector.FailureCategory.INVALID_INPUT,
        AgentFeedbackCollector.classifyFailure("Invalid input: negative pressure -5 bara"));

    assertEquals(AgentFeedbackCollector.FailureCategory.CODE_ERROR,
        AgentFeedbackCollector.classifyFailure("NullPointerException in separator"));

    assertEquals(AgentFeedbackCollector.FailureCategory.TIMEOUT,
        AgentFeedbackCollector.classifyFailure("Operation timeout after 300s"));

    assertEquals(AgentFeedbackCollector.FailureCategory.OTHER,
        AgentFeedbackCollector.classifyFailure("Something unexpected happened"));

    assertEquals(AgentFeedbackCollector.FailureCategory.OTHER,
        AgentFeedbackCollector.classifyFailure(null));
  }

  @Test
  @DisplayName("Failure category counts are tracked")
  void testFailureCategoryCounts() {
    AgentFeedbackCollector collector = new AgentFeedbackCollector();

    AgentSession s1 = AgentSession.start("solve.task", "task 1");
    s1.fail("Flash convergence failure");
    collector.recordSession(s1);

    AgentSession s2 = AgentSession.start("solve.task", "task 2");
    s2.fail("convergence did not converge");
    collector.recordSession(s2);

    AgentSession s3 = AgentSession.start("process.model", "task 3");
    s3.fail("Missing API: method not found");
    collector.recordSession(s3);

    Map<String, Integer> counts = collector.getFailureCategoryCounts();
    assertEquals(2, counts.get("CONVERGENCE").intValue());
    assertEquals(1, counts.get("MISSING_API").intValue());
  }

  @Test
  @DisplayName("API gap recording works")
  void testAPIGapRecording() {
    AgentFeedbackCollector collector = new AgentFeedbackCollector();

    collector.recordAPIGap("No ejector equipment class", "process.equipment.ejector", "critical");
    collector.recordAPIGap("Missing corrosion rate model", "physicalproperties", "important");

    assertEquals(2, collector.getDiscoveredAPIGaps().size());
    assertEquals("No ejector equipment class", collector.getDiscoveredAPIGaps().get(0).description);
    assertEquals("critical", collector.getDiscoveredAPIGaps().get(0).priority);
  }

  @Test
  @DisplayName("Summary report generates valid JSON")
  void testSummaryReport() {
    AgentFeedbackCollector collector = new AgentFeedbackCollector();

    AgentSession s1 = AgentSession.start("solve.task", "task 1");
    s1.recordSimulationRun("flash", true, 0.5);
    s1.complete(AgentSession.Outcome.SUCCESS);
    collector.recordSession(s1);

    AgentSession s2 = AgentSession.start("process.model", "task 2");
    s2.recordSimulationRun("process.run()", false, 2.0);
    s2.fail("Timeout after 300s");
    collector.recordSession(s2);

    String report = collector.getSummaryReport();
    assertNotNull(report);
    assertTrue(report.contains("\"totalSessions\": 2"));
    assertTrue(report.contains("\"overallSuccessRate\""));
    assertTrue(report.contains("\"agentBreakdown\""));
  }

  @Test
  @DisplayName("Average simulation success rate computed correctly")
  void testAverageSimulationSuccessRate() {
    AgentFeedbackCollector collector = new AgentFeedbackCollector();

    // Session 1: 2/2 = 100%
    AgentSession s1 = AgentSession.start("solve.task", "task 1");
    s1.recordSimulationRun("sim1", true, 1.0);
    s1.recordSimulationRun("sim2", true, 1.0);
    s1.complete(AgentSession.Outcome.SUCCESS);
    collector.recordSession(s1);

    // Session 2: 1/2 = 50%
    AgentSession s2 = AgentSession.start("solve.task", "task 2");
    s2.recordSimulationRun("sim1", true, 1.0);
    s2.recordSimulationRun("sim2", false, 1.0);
    s2.complete(AgentSession.Outcome.PARTIAL);
    collector.recordSession(s2);

    // Average: (100% + 50%) / 2 = 75%
    assertEquals(0.75, collector.getAverageSimulationSuccessRate(), 0.001);
  }

  @Test
  @DisplayName("Empty collector returns zero rates")
  void testEmptyCollector() {
    AgentFeedbackCollector collector = new AgentFeedbackCollector();
    assertEquals(0.0, collector.getOverallSuccessRate(), 0.001);
    assertEquals(0.0, collector.getAverageSimulationSuccessRate(), 0.001);
    assertEquals(0, collector.getSessionCount());
    assertTrue(collector.getDiscoveredAPIGaps().isEmpty());
    assertTrue(collector.getFailureCategoryCounts().isEmpty());
  }
}
