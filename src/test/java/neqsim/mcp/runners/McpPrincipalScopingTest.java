package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests that server state is scoped to the authenticated principal.
 *
 * <p>
 * Ownership used to be whatever the client claimed, and one-shot approvals were global, so any caller could adopt
 * another user's session or consume an approval granted to someone else. These tests pin the principal-derived
 * behaviour that replaces it.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class McpPrincipalScopingTest {

  /** System property holding the admin token. */
  private static final String ADMIN_TOKEN_PROPERTY = "neqsim.mcp.adminToken";

  /**
   * Starts each test from a clean, unauthenticated state in the default profile.
   */
  @BeforeEach
  void reset() {
    McpRequestContext.clear();
    IndustrialProfile.setActiveMode(IndustrialProfile.DeploymentMode.DESKTOP_ENGINEER);
  }

  /**
   * Clears identity and admin configuration after each test.
   */
  @AfterEach
  void clear() {
    McpRequestContext.clear();
    IndustrialProfile.setActiveMode(IndustrialProfile.DeploymentMode.DESKTOP_ENGINEER);
    System.clearProperty(ADMIN_TOKEN_PROPERTY);
  }

  /**
   * Binds an authenticated principal.
   *
   * @param subject the subject identifier
   * @param tenant the tenant scope
   */
  private void authenticateAs(String subject, String tenant) {
    McpRequestContext
        .set(McpRequestContext.Principal.ofClaims(subject, tenant, Collections.<String>emptySet(), "test-issuer"));
  }

  /**
   * Creates a session and returns its identifier.
   *
   * @param name the session name
   * @return the session id
   */
  private String createSession(String name) {
    String response = SessionRunner.run("{\"action\": \"create\", \"name\": \"" + name + "\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString(), response);
    return root.get("sessionId").getAsString();
  }

  /**
   * A session created by an authenticated caller must be owned by that principal, not by a client-supplied label.
   */
  @Test
  @DisplayName("Session ownership is derived from the authenticated principal")
  void testSessionOwnerComesFromPrincipal() {
    authenticateAs("alice", "tenant-a");
    String response = SessionRunner.run("{\"action\": \"create\", \"name\": \"alice-model\", \"ownerId\": \"bob\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString(), response);
    assertEquals("alice", root.get("ownerId").getAsString(),
        "A client-supplied ownerId must not override the authenticated principal");
  }

  /**
   * Another principal must not be able to read a session it does not own, even when naming the owner explicitly.
   */
  @Test
  @DisplayName("A session cannot be adopted by another principal")
  void testSessionIsolatedBetweenPrincipals() {
    authenticateAs("alice", "tenant-a");
    String sessionId = createSession("alice-model");

    authenticateAs("bob", "tenant-b");
    String stolen = SessionRunner.run("{\"action\": \"status\", \"sessionId\": \"" + sessionId + "\"}");
    assertFalse(stolen.contains("\"status\": \"success\""),
        "Another principal must not resolve the session: " + stolen);

    String claimed = SessionRunner
        .run("{\"action\": \"status\", \"sessionId\": \"" + sessionId + "\", \"ownerId\": \"alice\"}");
    assertFalse(claimed.contains("\"status\": \"success\""),
        "Claiming the owner name must not grant access: " + claimed);
  }

  /**
   * Listing must show only the caller's own sessions.
   */
  @Test
  @DisplayName("Session listing is scoped to the caller")
  void testSessionListingIsScoped() {
    authenticateAs("alice", "tenant-a");
    createSession("alice-model");

    authenticateAs("bob", "tenant-b");
    String response = SessionRunner.run("{\"action\": \"list\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    JsonArray sessions = root.getAsJsonArray("sessions");

    for (int i = 0; i < sessions.size(); i++) {
      assertEquals("bob", sessions.get(i).getAsJsonObject().get("ownerId").getAsString(),
          "Listing must not expose another principal's sessions");
    }
    assertEquals(sessions.size(), root.get("count").getAsInt(), "Reported count must match the visible sessions");
  }

  /**
   * An approval granted to one principal must not be usable by another.
   */
  @Test
  @DisplayName("One-shot approvals are bound to the principal they were granted for")
  void testApprovalsAreBoundToPrincipal() {
    System.setProperty(ADMIN_TOKEN_PROPERTY, "admin-token");
    IndustrialProfile.setActiveMode(IndustrialProfile.DeploymentMode.ENTERPRISE);

    authenticateAs("alice", "tenant-a");
    assertNotNull(IndustrialProfile.enforceAccess("manageModel"),
        "Governed execution tool must require approval before one is granted");
    assertNotNull(IndustrialProfile.approveNextInvocation("manageModel", "admin-token"));

    authenticateAs("bob", "tenant-b");
    String bobBlocked = IndustrialProfile.enforceAccess("manageModel");
    assertNotNull(bobBlocked, "Another principal must not consume Alice's approval");
    assertTrue(bobBlocked.contains("approval_required"), bobBlocked);

    authenticateAs("alice", "tenant-a");
    assertEquals(null, IndustrialProfile.enforceAccess("manageModel"),
        "The approved principal must be allowed exactly once");
    assertNotNull(IndustrialProfile.enforceAccess("manageModel"),
        "The approval must be consumed by the first invocation");
  }

  /**
   * The execution policy must publish bounded, configurable limits rather than a fixed pool with no timeout.
   */
  @Test
  @DisplayName("Execution policy publishes bounded worker, timeout and concurrency limits")
  void testExecutionPolicyIsBounded() {
    assertTrue(McpExecutionPolicy.getWorkerCount() >= 1, "Worker count must be positive");
    assertTrue(McpExecutionPolicy.getOperationTimeoutSeconds() > 0, "Operations must have a wall-clock timeout");
    assertTrue(McpExecutionPolicy.getMaxOperationsPerPrincipal() >= 1, "A per-principal limit must be set");

    JsonObject described = McpExecutionPolicy.describe();
    assertTrue(described.has("workerThreads"));
    assertTrue(described.has("operationTimeoutSeconds"));
    assertTrue(described.has("maxOperationsPerPrincipal"));
  }

  /**
   * Concurrency slots must be accounted per principal so one caller cannot exhaust the pool.
   */
  @Test
  @DisplayName("Concurrency slots are counted per principal and released")
  void testConcurrencySlotsArePerPrincipal() {
    authenticateAs("alice", "tenant-a");
    int limit = McpExecutionPolicy.getMaxOperationsPerPrincipal();
    for (int i = 0; i < limit; i++) {
      assertTrue(McpExecutionPolicy.tryAcquireSlot(), "Slot " + i + " must be granted within the limit");
    }
    assertFalse(McpExecutionPolicy.tryAcquireSlot(), "Exceeding the per-principal limit must be refused");

    authenticateAs("bob", "tenant-b");
    assertTrue(McpExecutionPolicy.tryAcquireSlot(), "A different principal must not be blocked by Alice's usage");
    McpExecutionPolicy.releaseSlot("bob");

    for (int i = 0; i < limit; i++) {
      McpExecutionPolicy.releaseSlot("alice");
    }
    assertEquals(0, McpExecutionPolicy.activeOperations("alice"), "Slots must be released");
  }

  /**
   * Streaming operations must not be visible to, or cancellable by, another principal.
   */
  @Test
  @DisplayName("Streaming operations are scoped to their owner")
  void testStreamingOperationsAreScoped() {
    authenticateAs("alice", "tenant-a");
    String start = StreamingRunner.run("{\"action\": \"startSweep\", \"components\": {\"methane\": 1.0},"
        + " \"sweepVariable\": \"temperature\", \"from\": 0, \"to\": 20, \"points\": 3}");
    JsonObject started = JsonParser.parseString(start).getAsJsonObject();
    assertTrue(started.has("operationId"), "Sweep must start: " + start);
    String operationId = started.get("operationId").getAsString();

    authenticateAs("bob", "tenant-b");
    String poll = StreamingRunner.run("{\"action\": \"poll\", \"operationId\": \"" + operationId + "\"}");
    assertTrue(poll.contains("NOT_FOUND"), "Another principal must not poll the operation: " + poll);

    String cancel = StreamingRunner.run("{\"action\": \"cancel\", \"operationId\": \"" + operationId + "\"}");
    assertTrue(cancel.contains("not_found"), "Another principal must not cancel the operation: " + cancel);

    String list = StreamingRunner.run("{\"action\": \"list\"}");
    assertFalse(list.contains(operationId), "Another principal must not see the operation in listings: " + list);

    authenticateAs("alice", "tenant-a");
    String ownerPoll = StreamingRunner.run("{\"action\": \"poll\", \"operationId\": \"" + operationId + "\"}");
    assertFalse(ownerPoll.contains("NOT_FOUND"), "The owner must still see the operation: " + ownerPoll);
    StreamingRunner.run("{\"action\": \"cancel\", \"operationId\": \"" + operationId + "\"}");
  }
}
