package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ProcessSimulationSession — session management with templates.
 *
 * @author Even Solbraa
 */
class ProcessSimulationSessionTest {

  private static final String SIMPLE_JSON = "{\"fluid\":{\"model\":\"srk\",\"temperature\":298.15,"
      + "\"pressure\":30.0,\"components\":{\"methane\":0.9,\"ethane\":0.1}},"
      + "\"process\":[{\"type\":\"Stream\",\"name\":\"feed\","
      + "\"properties\":{\"flowRate\":1000.0}}]}";

  private ProcessSimulationSession session;

  @BeforeEach
  void setUp() {
    session = new ProcessSimulationSession();
  }

  @AfterEach
  void tearDown() {
    session.shutdown();
  }

  @Test
  void testRegisterAndCreateFromTemplate() {
    ProcessSystem template = buildSimpleProcess("template-1");
    session.registerTemplate("simple", template);
    String sessionId = session.createSession("simple");
    assertNotNull(sessionId);
    assertFalse(sessionId.isEmpty());
    ProcessSystem ps = session.getSession(sessionId);
    assertNotNull(ps);
  }

  @Test
  void testCreateSessionFromJson() {
    SimulationResult result = session.createSessionFromJson(SIMPLE_JSON);
    assertTrue(result.isSuccess(), "Session creation from JSON should succeed");
    assertNotNull(result.getProcessSystem());
    // Session ID is stored as first entry in warnings list
    assertFalse(result.getWarnings().isEmpty());
    String sessionIdEntry = result.getWarnings().get(0);
    assertTrue(sessionIdEntry.startsWith("sessionId:"));
    String sessionId = sessionIdEntry.substring("sessionId:".length());
    ProcessSystem ps = session.getSession(sessionId);
    assertNotNull(ps);
  }

  @Test
  void testRunSession() {
    SimulationResult createResult = session.createSessionFromJson(SIMPLE_JSON);
    assertTrue(createResult.isSuccess());
    String sessionId = createResult.getWarnings().get(0).substring("sessionId:".length());
    SimulationResult runResult = session.runSession(sessionId);
    assertNotNull(runResult);
  }

  @Test
  void testDestroySession() {
    SimulationResult createResult = session.createSessionFromJson(SIMPLE_JSON);
    String sessionId = createResult.getWarnings().get(0).substring("sessionId:".length());
    assertNotNull(session.getSession(sessionId));
    assertTrue(session.destroySession(sessionId));
    // After destroy, getSession should throw
    assertThrows(IllegalArgumentException.class, () -> session.getSession(sessionId));
  }

  @Test
  void testGetNonExistentSession() {
    assertThrows(IllegalArgumentException.class, () -> session.getSession("does-not-exist"));
  }

  @Test
  void testRunNonExistentSession() {
    SimulationResult result = session.runSession("does-not-exist");
    assertNotNull(result);
    assertTrue(result.isError());
    assertTrue(result.getErrors().get(0).getMessage().contains("not found"));
  }

  @Test
  void testCreateFromNonExistentTemplate() {
    assertThrows(IllegalArgumentException.class, () -> {
      session.createSession("nope");
    });
  }

  @Test
  void testSessionCount() {
    assertEquals(0, session.getActiveSessionCount());
    SimulationResult r1 = session.createSessionFromJson(SIMPLE_JSON);
    assertTrue(r1.isSuccess());
    assertEquals(1, session.getActiveSessionCount());
    SimulationResult r2 = session.createSessionFromJson(SIMPLE_JSON);
    assertTrue(r2.isSuccess());
    assertEquals(2, session.getActiveSessionCount());

    String id1 = r1.getWarnings().get(0).substring("sessionId:".length());
    session.destroySession(id1);
    assertEquals(1, session.getActiveSessionCount());
  }

  @Test
  void testMaxSessionsEnforced() {
    // Constructor: (timeoutMinutes, maxSessions)
    ProcessSimulationSession smallSession = new ProcessSimulationSession(30, 2);
    try {
      SimulationResult r1 = smallSession.createSessionFromJson(SIMPLE_JSON);
      assertTrue(r1.isSuccess());
      SimulationResult r2 = smallSession.createSessionFromJson(SIMPLE_JSON);
      assertTrue(r2.isSuccess());
      // Third session should fail: createSessionFromJson returns error result
      SimulationResult r3 = smallSession.createSessionFromJson(SIMPLE_JSON);
      assertTrue(r3.isError(), "Third session should be rejected due to max limit");
      assertTrue(r3.getErrors().get(0).getCode().contains("MAX_SESSIONS"));
    } finally {
      smallSession.shutdown();
    }
  }

  @Test
  void testTemplateIsolation() {
    ProcessSystem template = buildSimpleProcess("tpl");
    session.registerTemplate("tpl", template);
    String id1 = session.createSession("tpl");
    String id2 = session.createSession("tpl");
    ProcessSystem ps1 = session.getSession(id1);
    ProcessSystem ps2 = session.getSession(id2);
    // They should be distinct objects (deep copies)
    assertFalse(ps1 == ps2, "Sessions should be independent deep copies");
  }

  @Test
  void testDestroyAllSessions() {
    session.createSessionFromJson(SIMPLE_JSON);
    session.createSessionFromJson(SIMPLE_JSON);
    assertEquals(2, session.getActiveSessionCount());
    session.destroyAllSessions();
    assertEquals(0, session.getActiveSessionCount());
  }

  @Test
  void testGetSessionInfo() {
    session.createSessionFromJson(SIMPLE_JSON);
    java.util.Map<String, String> info = session.getSessionInfo();
    assertEquals(1, info.size());
    // JSON-created sessions have templateName "json"
    assertTrue(info.values().iterator().next().equals("json"));
  }

  /**
   * Builds a simple ProcessSystem with a single stream for template use.
   *
   * @param name the process name
   * @return a new ProcessSystem
   */
  private ProcessSystem buildSimpleProcess(String name) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    ProcessSystem process = new ProcessSystem(name);
    process.add(feed);
    return process;
  }
}
