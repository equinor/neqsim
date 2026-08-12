package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.runners.IndustrialProfile.DeploymentMode;
import neqsim.mcp.runners.IndustrialProfile.ToolCategory;
import neqsim.mcp.runners.IndustrialProfile.ToolTier;

/**
 * Regression tests for the STUDY_TEAM deployment profile.
 *
 * <p>
 * STUDY_TEAM is the profile hosted deployments run with ({@code NEQSIM_MCP_PROFILE=STUDY_TEAM}), and the governance
 * promise attached to it is explicit: Tier 1 (TRUSTED_CORE) and Tier 2 (ENGINEERING_ADVANCED) tools are available, Tier
 * 3 (EXPERIMENTAL) tools are refused at call time — not merely hidden from discovery. These tests pin that promise,
 * including the shape of the refusal envelope and the tier a tool is actually classified in, so a future retiering
 * cannot silently widen the hosted surface.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class McpSecurityStudyTeamProfileTest {

  /** Deployment mode active before this class ran, restored afterwards. */
  private static DeploymentMode originalMode;

  /**
   * Captures the process-global deployment mode so this class cannot poison other tests.
   */
  @BeforeAll
  static void captureDeploymentMode() {
    originalMode = IndustrialProfile.getActiveMode();
  }

  /**
   * Restores the process-global deployment mode captured before this class ran.
   */
  @AfterAll
  static void restoreDeploymentMode() {
    IndustrialProfile.setActiveMode(originalMode);
  }

  /**
   * Activates the hosted profile with enforcement state reset, so tool-tier filtering is the only thing under test.
   */
  @BeforeEach
  void useStudyTeamProfile() {
    SecurityRunner.resetForTests();
    McpRequestContext.clear();
    IndustrialProfile.setActiveMode(DeploymentMode.STUDY_TEAM);
  }

  /**
   * Restores mutated global state after every test, including the deployment mode.
   */
  @AfterEach
  void resetGlobalState() {
    McpRequestContext.clear();
    SecurityRunner.resetForTests();
    IndustrialProfile.setActiveMode(originalMode);
  }

  /**
   * A Tier 3 tool must be refused at call time, with an envelope that names the mode, the tier and a remediation — this
   * is the governance promise made for hosted deployments.
   */
  @Test
  @DisplayName("STUDY_TEAM: Tier 3 solveTask is blocked at call time")
  void testExperimentalToolIsBlockedAtCallTime() {
    assertEquals(ToolTier.EXPERIMENTAL, IndustrialProfile.getToolTier("solveTask"),
        "solveTask must stay classified as Tier 3");
    assertFalse(IndustrialProfile.isToolAllowed("solveTask"), "solveTask must not be allowed in STUDY_TEAM");

    String blocked = IndustrialProfile.enforceAccess("solveTask");
    assertNotNull(blocked, "STUDY_TEAM must refuse Tier 3 tools at call time, not just hide them");

    JsonObject root = JsonParser.parseString(blocked).getAsJsonObject();
    assertEquals("blocked", root.get("status").getAsString());
    assertEquals("solveTask", root.get("tool").getAsString());
    assertEquals("STUDY_TEAM", root.get("mode").getAsString());
    assertEquals("EXPERIMENTAL", root.get("tier").getAsString());
    assertTrue(root.get("reason").getAsString().contains("not available in STUDY_TEAM mode"),
        "Refusal must state the mode: " + blocked);
    assertTrue(root.has("remediation"), "Refusal must carry a remediation hint");
    JsonObject data = root.getAsJsonObject("data");
    assertEquals("STUDY_TEAM", data.get("mode").getAsString());
    assertEquals("EXPERIMENTAL", data.get("policyCode").getAsString());
    assertFalse(data.get("approvalRequired").getAsBoolean(),
        "A tier refusal is not an approval prompt — it must not look recoverable by approving");
    assertFalse(root.getAsJsonObject("validation").get("valid").getAsBoolean());
    assertEquals("blocked", root.getAsJsonObject("qualityGate").get("verdict").getAsString());
  }

  /**
   * The whole Tier 3 set must be refused, not just the tool this test happened to name.
   */
  @Test
  @DisplayName("STUDY_TEAM: every Tier 3 tool is blocked")
  void testEveryExperimentalToolIsBlocked() {
    for (String tool : IndustrialProfile.getExperimentalTools()) {
      String blocked = IndustrialProfile.enforceAccess(tool);
      assertNotNull(blocked, "Tier 3 tool '" + tool + "' must be blocked in STUDY_TEAM");
      JsonObject root = JsonParser.parseString(blocked).getAsJsonObject();
      assertEquals("blocked", root.get("status").getAsString(), "Tier 3 tool '" + tool + "' must be blocked");
    }
  }

  /**
   * Tier 1 stays available — blocking Tier 3 must not collaterally break the trusted core.
   */
  @Test
  @DisplayName("STUDY_TEAM: Tier 1 runFlash is allowed")
  void testTrustedCoreToolIsAllowed() {
    assertEquals(ToolTier.TRUSTED_CORE, IndustrialProfile.getToolTier("runFlash"),
        "runFlash must stay classified as Tier 1");
    assertNull(IndustrialProfile.enforceAccess("runFlash"), "Tier 1 tools must be callable in STUDY_TEAM");

    for (String tool : IndustrialProfile.getIndustrialCore()) {
      assertNull(IndustrialProfile.enforceAccess(tool), "Tier 1 tool '" + tool + "' must be allowed in STUDY_TEAM");
    }
  }

  /**
   * Tier 2 stays available — STUDY_TEAM is the collaborative engineering profile, not a read-only one.
   */
  @Test
  @DisplayName("STUDY_TEAM: Tier 2 runPVT is allowed")
  void testEngineeringAdvancedToolIsAllowed() {
    assertEquals(ToolTier.ENGINEERING_ADVANCED, IndustrialProfile.getToolTier("runPVT"),
        "runPVT must stay classified as Tier 2");
    assertNull(IndustrialProfile.enforceAccess("runPVT"), "Tier 2 tools must be callable in STUDY_TEAM");

    for (String tool : IndustrialProfile.getEngineeringAdvanced()) {
      assertNull(IndustrialProfile.enforceAccess(tool), "Tier 2 tool '" + tool + "' must be allowed in STUDY_TEAM");
    }
  }

  /**
   * manageModel writes server-side state but is classified Tier 1, so it is reachable in every hosted profile. Pinning
   * the classification here means a later retiering shows up as a failing governance test rather than as a silent
   * change to the hosted tool surface.
   */
  @Test
  @DisplayName("STUDY_TEAM: manageModel is Tier 1 and therefore allowed")
  void testManageModelIsTrustedCoreAndAllowed() {
    assertEquals(ToolTier.TRUSTED_CORE, IndustrialProfile.getToolTier("manageModel"),
        "manageModel is Tier 1 (TRUSTED_CORE); retiering it changes which profiles may call it");
    assertEquals(ToolCategory.EXECUTION, IndustrialProfile.getToolCategory("manageModel"),
        "manageModel is a state-modifying tool, so approval gates apply to it in gated profiles");
    assertTrue(IndustrialProfile.isToolAllowed("manageModel"));
    assertNull(IndustrialProfile.enforceAccess("manageModel"),
        "manageModel must stay callable in STUDY_TEAM as long as it is Tier 1");
    assertFalse(IndustrialProfile.requiresApproval("manageModel"),
        "STUDY_TEAM has no approval gate, so manageModel must not prompt for approval");
  }
}
