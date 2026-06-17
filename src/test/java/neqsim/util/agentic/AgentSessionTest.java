package neqsim.util.agentic;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for AgentSession — tracks agent workflow sessions.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AgentSessionTest {

  @Test
  @DisplayName("Create session and complete successfully")
  void testSessionLifecycle() {
    AgentSession session = AgentSession.start("solve.task", "TEG dehydration sizing");

    assertEquals("solve.task", session.getAgentName());
    assertEquals("TEG dehydration sizing", session.getTaskDescription());
    assertNotNull(session.getSessionId());
    assertNull(session.getOutcome());

    session.beginPhase(AgentSession.Phase.SCOPE);
    session.recordToolUse("thermo.fluid", "Create SRK fluid with water");
    session.endPhase(AgentSession.Phase.SCOPE);

    session.beginPhase(AgentSession.Phase.ANALYSIS);
    session.recordSimulationRun("process.run()", true, 2.3);
    session.recordSimulationRun("flash recalculation", false, 0.5);
    session.endPhase(AgentSession.Phase.ANALYSIS);

    session.complete(AgentSession.Outcome.SUCCESS);

    assertEquals(AgentSession.Outcome.SUCCESS, session.getOutcome());
    assertEquals(2, session.getSimulationRunCount());
    assertEquals(1, session.getSuccessfulSimulationCount());
    assertEquals(1, session.getToolInvocationCount());
    assertEquals(2, session.getPhases().size());
    assertTrue(session.getDurationSeconds() >= 0.0);
  }

  @Test
  @DisplayName("Session failure with reason")
  void testSessionFailure() {
    AgentSession session = AgentSession.start("process.model", "3-stage compression");

    session.beginPhase(AgentSession.Phase.ANALYSIS);
    session.recordSimulationRun("compressor run", false, 1.0);
    session.fail("Flash convergence failure at high pressure");

    assertEquals(AgentSession.Outcome.FAILED, session.getOutcome());
    assertEquals("Flash convergence failure at high pressure", session.getFailureReason());
  }

  @Test
  @DisplayName("Session serializes to valid JSON")
  void testToJson() {
    AgentSession session = AgentSession.start("solve.task", "JT cooling");
    session.addMetadata("taskType", "A");
    session.addMetadata("eosModel", "SRK");

    session.beginPhase(AgentSession.Phase.SCOPE);
    session.endPhase(AgentSession.Phase.SCOPE);
    session.recordSimulationRun("TPflash", true, 0.1);
    session.complete(AgentSession.Outcome.SUCCESS);

    String json = session.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"agentName\": \"solve.task\""));
    assertTrue(json.contains("\"outcome\": \"SUCCESS\""));
    assertTrue(json.contains("\"taskType\""));
    assertTrue(json.contains("\"eosModel\""));
  }

  @Test
  @DisplayName("Phase records track timing independently")
  void testPhaseTiming() {
    AgentSession session = AgentSession.start("test.agent", "test task");

    session.beginPhase(AgentSession.Phase.SCOPE);
    session.endPhase(AgentSession.Phase.SCOPE);

    session.beginPhase(AgentSession.Phase.ANALYSIS);
    session.endPhase(AgentSession.Phase.ANALYSIS);

    assertEquals(2, session.getPhases().size());
    assertEquals(AgentSession.Phase.SCOPE, session.getPhases().get(0).phase);
    assertEquals(AgentSession.Phase.ANALYSIS, session.getPhases().get(1).phase);
    assertTrue(session.getPhases().get(0).getDurationSeconds() >= 0.0);
  }

  @Test
  @DisplayName("Partial outcome for mixed results")
  void testPartialOutcome() {
    AgentSession session = AgentSession.start("solve.task", "complex task");
    session.recordSimulationRun("main sim", true, 5.0);
    session.recordSimulationRun("benchmark", false, 2.0);
    session.complete(AgentSession.Outcome.PARTIAL);

    assertEquals(AgentSession.Outcome.PARTIAL, session.getOutcome());
    assertEquals(2, session.getSimulationRunCount());
    assertEquals(1, session.getSuccessfulSimulationCount());
  }

  @Test
  @DisplayName("Unmodifiable collections from getters")
  void testUnmodifiableCollections() {
    AgentSession session = AgentSession.start("test", "test");
    session.beginPhase(AgentSession.Phase.SCOPE);
    session.recordToolUse("tool1", "desc");
    session.recordSimulationRun("sim1", true, 1.0);

    assertThrows(UnsupportedOperationException.class, () -> {
      session.getPhases().clear();
    });
    assertThrows(UnsupportedOperationException.class, () -> {
      session.getToolInvocations().clear();
    });
    assertThrows(UnsupportedOperationException.class, () -> {
      session.getSimulationRuns().clear();
    });
  }
}
