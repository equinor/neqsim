package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Regression tests for MCP security enforcement.
 *
 * <p>
 * Enforcement previously evaluated a hard-coded null credential, so enabling it denied every tool call — including the
 * management tool needed to disable it again. These tests pin the fixed behaviour: an authenticated principal is
 * honoured, an unauthenticated caller is denied, the bootstrap tool stays reachable, and privileged security actions
 * require the admin token.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class McpSecurityEnforcementTest {

  /** System property holding the admin token. */
  private static final String ADMIN_TOKEN_PROPERTY = "neqsim.mcp.adminToken";

  /**
   * Pins the deployment mode so tool-tier filtering cannot vary with test execution order.
   */
  @BeforeEach
  void useDesktopProfile() {
    SecurityRunner.resetForTests();
    McpRequestContext.clear();
    IndustrialProfile.setActiveMode(IndustrialProfile.DeploymentMode.DESKTOP_ENGINEER);
  }

  /**
   * Restores global state so enabling security in one test cannot affect others.
   */
  @AfterEach
  void resetSecurityState() {
    McpRequestContext.clear();
    SecurityRunner.resetForTests();
    System.clearProperty(ADMIN_TOKEN_PROPERTY);
  }

  /**
   * Enables enforcement using the admin token.
   *
   * @param adminToken the token to configure and use
   */
  private void enableSecurity(String adminToken) {
    System.setProperty(ADMIN_TOKEN_PROPERTY, adminToken);
    String response = SecurityRunner
        .run("{\"action\": \"setConfig\", \"enabled\": true, \"adminToken\": \"" + adminToken + "\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString(), "Enabling security must succeed: " + response);
  }

  /**
   * Creates an API key using the admin token.
   *
   * @param adminToken the configured admin token
   * @param userId the user the key belongs to
   * @return the issued API key
   */
  private String createKey(String adminToken, String userId) {
    String response = SecurityRunner
        .run("{\"action\": \"createApiKey\", \"userId\": \"" + userId + "\", \"adminToken\": \"" + adminToken + "\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString(), "Key creation must succeed: " + response);
    return root.get("apiKey").getAsString();
  }

  /**
   * With security disabled the server behaves as a single-user desktop tool and nothing is blocked.
   */
  @Test
  @DisplayName("Security disabled: tools are not blocked")
  void testDisabledSecurityAllowsTools() {
    assertNull(SecurityRunner.checkAccess(null, "runFlash"), "Disabled security must not block tools");
  }

  /**
   * An authenticated principal bound by the transport must be accepted. This is the case that was broken: the
   * credential never reached the check, so every call was denied.
   */
  @Test
  @DisplayName("Security enabled: transport-bound principal is accepted")
  void testBoundPrincipalIsAccepted() {
    String adminToken = "test-admin-token";
    enableSecurity(adminToken);
    String apiKey = createKey(adminToken, "engineer-1");

    McpRequestContext.set(McpRequestContext.Principal.ofApiKey(apiKey));
    try {
      assertNull(IndustrialProfile.enforceAccess("runFlash"),
          "An authenticated caller must not be blocked when security is enabled");
    } finally {
      McpRequestContext.clear();
    }
  }

  /**
   * An unauthenticated caller must be denied when enforcement is on.
   */
  @Test
  @DisplayName("Security enabled: anonymous caller is denied")
  void testAnonymousCallerIsDenied() {
    String adminToken = "test-admin-token";
    enableSecurity(adminToken);
    McpRequestContext.clear();

    String blocked = IndustrialProfile.enforceAccess("runFlash");
    assertNotNull(blocked, "Anonymous caller must be denied when security is enabled");
    JsonObject root = JsonParser.parseString(blocked).getAsJsonObject();
    assertEquals("blocked", root.get("status").getAsString());
  }

  /**
   * The bootstrap tool must stay reachable so an operator can recover, otherwise enabling security is a one-way
   * lockout.
   */
  @Test
  @DisplayName("Security enabled: manageSecurity stays reachable for recovery")
  void testBootstrapToolIsNotLockedOut() {
    String adminToken = "test-admin-token";
    enableSecurity(adminToken);
    McpRequestContext.clear();

    assertNull(IndustrialProfile.enforceAccess("manageSecurity"),
        "manageSecurity must stay reachable so enforcement can be inspected and disabled");

    String status = SecurityRunner.run("{\"action\": \"getStatus\"}");
    JsonObject root = JsonParser.parseString(status).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString(), "getStatus must work without credentials");
  }

  /**
   * Because manageSecurity is bootstrap-exempt, privileged actions inside it must require the admin token — otherwise
   * any caller could mint an admin key for itself.
   */
  @Test
  @DisplayName("Security enabled: minting an API key requires the admin token")
  void testKeyCreationRequiresAdminToken() {
    String adminToken = "test-admin-token";
    enableSecurity(adminToken);

    String response = SecurityRunner.run("{\"action\": \"createApiKey\", \"userId\": \"attacker\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("error", root.get("status").getAsString(), "Unauthenticated key creation must fail: " + response);
    assertTrue(response.contains("ADMIN_REQUIRED"), "Denial must state that admin authorization is required");
  }

  /**
   * Enabling enforcement without a configured admin token must not silently create an unrecoverable or ungoverned
   * state.
   */
  @Test
  @DisplayName("Security enabled without admin token: privileged actions fail closed")
  void testPrivilegedActionsFailClosedWithoutAdminConfig() {
    System.clearProperty(ADMIN_TOKEN_PROPERTY);
    SecurityRunner.run("{\"action\": \"setConfig\", \"enabled\": true}");

    String response = SecurityRunner.run("{\"action\": \"createApiKey\", \"userId\": \"anyone\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("error", root.get("status").getAsString());
    assertTrue(response.contains("ADMIN_NOT_CONFIGURED") || response.contains("ADMIN_REQUIRED"),
        "Must refuse privileged actions when no admin token is configured: " + response);
  }
}
