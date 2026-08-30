package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Phase 0 software-contract qualification for the stateful MCP session lifecycle.
 *
 * <p>
 * These tests qualify caller ownership and lifecycle isolation only. They do not establish numerical accuracy,
 * convergence quality, persistence across server restart, distributed-session coherence, or engineering approval.
 * </p>
 */
class SessionRunnerContractTest {

  /** Clear thread-local identity after every scenario. */
  @AfterEach
  void clearRequestContext() {
    McpRequestContext.clear();
  }

  @Test
  void authenticatedCallerOwnsSessionAndOtherCallerFailsClosed() {
    McpRequestContext.set(McpRequestContext.Principal.ofClaims("phase0-owner-a", "tenant-a",
        Collections.<String>emptySet(), "phase0-test"));

    JsonObject created = parse(SessionRunner.run("{\"action\":\"create\",\"name\":\"phase0-owned-session\","
        + "\"fluid\":{\"model\":\"SRK\",\"temperature\":298.15,\"pressure\":50.0,"
        + "\"components\":{\"methane\":0.9,\"ethane\":0.1}}}"));
    assertEquals("success", created.get("status").getAsString());
    assertEquals("phase0-owner-a", created.get("ownerId").getAsString());
    String sessionId = created.get("sessionId").getAsString();

    try {
      McpRequestContext.set(McpRequestContext.Principal.ofClaims("phase0-owner-b", "tenant-a",
          Collections.<String>emptySet(), "phase0-test"));

      JsonObject deniedState = parse(SessionRunner.run(
          "{\"action\":\"getState\",\"sessionId\":\"" + sessionId + "\"}"));
      assertEquals("error", deniedState.get("status").getAsString());
      assertEquals("SESSION_NOT_FOUND", deniedState.get("code").getAsString());

      JsonObject visibleToOtherCaller = parse(SessionRunner.run("{\"action\":\"list\"}"));
      JsonArray otherSessions = visibleToOtherCaller.getAsJsonArray("sessions");
      assertFalse(containsSession(otherSessions, sessionId));

      JsonObject deniedClose = parse(
          SessionRunner.run("{\"action\":\"close\",\"sessionId\":\"" + sessionId + "\"}"));
      assertEquals("error", deniedClose.get("status").getAsString());

      McpRequestContext.set(McpRequestContext.Principal.ofClaims("phase0-owner-a", "tenant-a",
          Collections.<String>emptySet(), "phase0-test"));
      JsonObject ownerState = parse(SessionRunner.run(
          "{\"action\":\"getState\",\"sessionId\":\"" + sessionId + "\"}"));
      assertEquals("success", ownerState.get("status").getAsString());
      assertEquals("phase0-owner-a", ownerState.get("ownerId").getAsString());

      JsonObject closed = parse(
          SessionRunner.run("{\"action\":\"close\",\"sessionId\":\"" + sessionId + "\"}"));
      assertEquals("success", closed.get("status").getAsString());

      JsonObject stale = parse(SessionRunner.run(
          "{\"action\":\"getState\",\"sessionId\":\"" + sessionId + "\"}"));
      assertEquals("error", stale.get("status").getAsString());
      assertEquals("SESSION_NOT_FOUND", stale.get("code").getAsString());
    } finally {
      McpRequestContext.set(McpRequestContext.Principal.ofClaims("phase0-owner-a", "tenant-a",
          Collections.<String>emptySet(), "phase0-test"));
      SessionRunner.run("{\"action\":\"close\",\"sessionId\":\"" + sessionId + "\"}");
    }
  }

  /** Parse one SessionRunner response as a JSON object. */
  private static JsonObject parse(String response) {
    return JsonParser.parseString(response).getAsJsonObject();
  }

  /** Return whether a caller-visible session list contains the supplied session identifier. */
  private static boolean containsSession(JsonArray sessions, String sessionId) {
    for (int i = 0; i < sessions.size(); i++) {
      JsonObject session = sessions.get(i).getAsJsonObject();
      if (sessionId.equals(session.get("sessionId").getAsString())) {
        return true;
      }
    }
    return false;
  }
}
