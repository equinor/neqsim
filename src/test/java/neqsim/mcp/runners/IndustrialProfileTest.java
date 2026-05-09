package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.runners.IndustrialProfile.DeploymentMode;
import neqsim.mcp.runners.IndustrialProfile.ToolCategory;
import neqsim.mcp.runners.IndustrialProfile.ToolTier;

/**
 * Tests for {@link IndustrialProfile} — proves governance enforcement in code.
 *
 * <p>
 * Verifies that tier boundaries, access control, and enforcement produce the correct behavior for
 * every deployment mode. This is the "prove it works" test suite that backs the MCP_CONTRACT.md
 * claims.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class IndustrialProfileTest {

  /** Reset to default mode after each test to prevent side effects. */
  @AfterEach
  void resetMode() {
    IndustrialProfile.setActiveMode(DeploymentMode.DESKTOP_ENGINEER);
  }

  // ─── Tier set sizes and disjointness ───────────────────────────────────────

  @Test
  void tierSizes_matchContract() {
    assertEquals(21, IndustrialProfile.getIndustrialCore().size(),
        "Tier 1 (TRUSTED_CORE) should have 21 tools");
    assertEquals(22, IndustrialProfile.getEngineeringAdvanced().size(),
        "Tier 2 (ENGINEERING_ADVANCED) should have 22 tools");
    assertEquals(14, IndustrialProfile.getExperimentalTools().size(),
        "Tier 3 (EXPERIMENTAL) should have 14 tools");
  }

  @Test
  void tiers_areDisjoint() {
    Set<String> overlap12 = new HashSet<>(IndustrialProfile.getIndustrialCore());
    overlap12.retainAll(IndustrialProfile.getEngineeringAdvanced());
    assertTrue(overlap12.isEmpty(), "Tier 1 and Tier 2 must not overlap: " + overlap12);

    Set<String> overlap13 = new HashSet<>(IndustrialProfile.getIndustrialCore());
    overlap13.retainAll(IndustrialProfile.getExperimentalTools());
    assertTrue(overlap13.isEmpty(), "Tier 1 and Tier 3 must not overlap: " + overlap13);

    Set<String> overlap23 = new HashSet<>(IndustrialProfile.getEngineeringAdvanced());
    overlap23.retainAll(IndustrialProfile.getExperimentalTools());
    assertTrue(overlap23.isEmpty(), "Tier 2 and Tier 3 must not overlap: " + overlap23);
  }

  // ─── getToolTier classification ────────────────────────────────────────────

  @Test
  void getToolTier_coreTools_returnTrustedCore() {
    assertEquals(ToolTier.TRUSTED_CORE, IndustrialProfile.getToolTier("runFlash"));
    assertEquals(ToolTier.TRUSTED_CORE, IndustrialProfile.getToolTier("runProcess"));
    assertEquals(ToolTier.TRUSTED_CORE, IndustrialProfile.getToolTier("validateInput"));
    assertEquals(ToolTier.TRUSTED_CORE, IndustrialProfile.getToolTier("getPhaseEnvelope"));
    assertEquals(ToolTier.TRUSTED_CORE, IndustrialProfile.getToolTier("getBenchmarkTrust"));
  }

  @Test
  void getToolTier_advancedTools_returnEngineeringAdvanced() {
    assertEquals(ToolTier.ENGINEERING_ADVANCED, IndustrialProfile.getToolTier("runPVT"));
    assertEquals(ToolTier.ENGINEERING_ADVANCED, IndustrialProfile.getToolTier("runPipeline"));
    assertEquals(ToolTier.ENGINEERING_ADVANCED, IndustrialProfile.getToolTier("runFlowAssurance"));
    assertEquals(ToolTier.ENGINEERING_ADVANCED,
        IndustrialProfile.getToolTier("runMaterialsReview"));
    assertEquals(ToolTier.ENGINEERING_ADVANCED, IndustrialProfile.getToolTier("sizeEquipment"));
    assertEquals(ToolTier.ENGINEERING_ADVANCED,
        IndustrialProfile.getToolTier("setSimulationVariable"));
    assertEquals(ToolTier.ENGINEERING_ADVANCED,
        IndustrialProfile.getToolTier("saveSimulationState"));
    assertEquals(ToolTier.ENGINEERING_ADVANCED,
        IndustrialProfile.getToolTier("runSafetySystemPerformance"));
  }

  @Test
  void getToolTier_experimentalTools_returnExperimental() {
    assertEquals(ToolTier.EXPERIMENTAL, IndustrialProfile.getToolTier("solveTask"));
    assertEquals(ToolTier.EXPERIMENTAL, IndustrialProfile.getToolTier("composeWorkflow"));
    assertEquals(ToolTier.EXPERIMENTAL, IndustrialProfile.getToolTier("runReservoir"));
    assertEquals(ToolTier.EXPERIMENTAL, IndustrialProfile.getToolTier("runDynamic"));
    assertEquals(ToolTier.EXPERIMENTAL, IndustrialProfile.getToolTier("runPlugin"));
  }

  @Test
  void getToolTier_unknownTool_returnsNull() {
    assertNull(IndustrialProfile.getToolTier("nonExistentTool"));
  }

  // ─── DESKTOP_ENGINEER — all tools allowed ──────────────────────────────────

  @Test
  void desktopMode_allowsAllTiers() {
    IndustrialProfile.setActiveMode(DeploymentMode.DESKTOP_ENGINEER);
    assertTrue(IndustrialProfile.isToolAllowed("runFlash"), "Desktop: Tier 1 allowed");
    assertTrue(IndustrialProfile.isToolAllowed("runPVT"), "Desktop: Tier 2 allowed");
    assertTrue(IndustrialProfile.isToolAllowed("solveTask"), "Desktop: Tier 3 allowed");
    assertTrue(IndustrialProfile.isToolAllowed("runPlugin"), "Desktop: Platform allowed");
  }

  @Test
  void desktopMode_enforceAccess_returnsNull() {
    IndustrialProfile.setActiveMode(DeploymentMode.DESKTOP_ENGINEER);
    assertNull(IndustrialProfile.enforceAccess("runFlash"));
    assertNull(IndustrialProfile.enforceAccess("runPVT"));
    assertNull(IndustrialProfile.enforceAccess("solveTask"));
  }

  @Test
  void desktopMode_noApprovalGate() {
    IndustrialProfile.setActiveMode(DeploymentMode.DESKTOP_ENGINEER);
    assertFalse(IndustrialProfile.requiresApproval("solveTask"));
    assertFalse(IndustrialProfile.requiresApproval("composeWorkflow"));
  }

  // ─── STUDY_TEAM — Tier 1 + Tier 2 only ─────────────────────────────────

  @Test
  void studyTeamMode_allowsTier1() {
    IndustrialProfile.setActiveMode(DeploymentMode.STUDY_TEAM);
    assertTrue(IndustrialProfile.isToolAllowed("runFlash"));
    assertTrue(IndustrialProfile.isToolAllowed("runProcess"));
    assertTrue(IndustrialProfile.isToolAllowed("validateInput"));
    assertTrue(IndustrialProfile.isToolAllowed("getPhaseEnvelope"));
  }

  @Test
  void studyTeamMode_allowsTier2() {
    IndustrialProfile.setActiveMode(DeploymentMode.STUDY_TEAM);
    assertTrue(IndustrialProfile.isToolAllowed("runPVT"));
    assertTrue(IndustrialProfile.isToolAllowed("runPipeline"));
    assertTrue(IndustrialProfile.isToolAllowed("runFlowAssurance"));
    assertTrue(IndustrialProfile.isToolAllowed("sizeEquipment"));
  }

  @Test
  void studyTeamMode_blocksTier3() {
    IndustrialProfile.setActiveMode(DeploymentMode.STUDY_TEAM);
    assertFalse(IndustrialProfile.isToolAllowed("solveTask"), "Study team must block solveTask");
    assertFalse(IndustrialProfile.isToolAllowed("composeWorkflow"),
        "Study team must block composeWorkflow");
    assertFalse(IndustrialProfile.isToolAllowed("runReservoir"),
        "Study team must block runReservoir");
    assertFalse(IndustrialProfile.isToolAllowed("runDynamic"), "Study team must block runDynamic");
    assertFalse(IndustrialProfile.isToolAllowed("runPlugin"), "Study team must block runPlugin");
  }

  @Test
  void studyTeamMode_enforceAccess_blockedTool_returnsErrorJson() {
    IndustrialProfile.setActiveMode(DeploymentMode.STUDY_TEAM);
    String result = IndustrialProfile.enforceAccess("solveTask");
    assertNotNull(result, "enforceAccess must return error JSON for blocked tool");

    JsonObject error = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("blocked", error.get("status").getAsString());
    assertEquals("solveTask", error.get("tool").getAsString());
    assertEquals("STUDY_TEAM", error.get("mode").getAsString());
    assertEquals("EXPERIMENTAL", error.get("tier").getAsString());
    assertTrue(error.has("reason"), "Error JSON must include 'reason'");
    assertTrue(error.has("remediation"), "Error JSON must include 'remediation'");
  }

  @Test
  void studyTeamMode_enforceAccess_allowedTool_returnsNull() {
    IndustrialProfile.setActiveMode(DeploymentMode.STUDY_TEAM);
    assertNull(IndustrialProfile.enforceAccess("runFlash"), "Tier 1 should pass enforcement");
    assertNull(IndustrialProfile.enforceAccess("runPVT"), "Tier 2 should pass enforcement");
  }

  // ─── DIGITAL_TWIN — Tier 1 advisory + calculation only ────────────────────

  @Test
  void digitalTwinMode_allowsTier1Advisory() {
    IndustrialProfile.setActiveMode(DeploymentMode.DIGITAL_TWIN);
    // Advisory tools in Tier 1
    assertTrue(IndustrialProfile.isToolAllowed("getCapabilities"));
    assertTrue(IndustrialProfile.isToolAllowed("searchComponents"));
    assertTrue(IndustrialProfile.isToolAllowed("validateInput"));
    assertTrue(IndustrialProfile.isToolAllowed("getSchema"));
    assertTrue(IndustrialProfile.isToolAllowed("getExample"));
  }

  @Test
  void digitalTwinMode_allowsTier1Calculation() {
    IndustrialProfile.setActiveMode(DeploymentMode.DIGITAL_TWIN);
    // Calculation tools in Tier 1
    assertTrue(IndustrialProfile.isToolAllowed("runFlash"));
    assertTrue(IndustrialProfile.isToolAllowed("runProcess"));
    assertTrue(IndustrialProfile.isToolAllowed("getPropertyTable"));
    assertTrue(IndustrialProfile.isToolAllowed("getPhaseEnvelope"));
    assertTrue(IndustrialProfile.isToolAllowed("calculateStandard"));
  }

  @Test
  void digitalTwinMode_blocksTier2() {
    IndustrialProfile.setActiveMode(DeploymentMode.DIGITAL_TWIN);
    assertFalse(IndustrialProfile.isToolAllowed("runPVT"), "Digital twin must block Tier 2");
    assertFalse(IndustrialProfile.isToolAllowed("runPipeline"));
    assertFalse(IndustrialProfile.isToolAllowed("runFlowAssurance"));
    assertFalse(IndustrialProfile.isToolAllowed("sizeEquipment"));
    assertFalse(IndustrialProfile.isToolAllowed("setSimulationVariable"));
    assertFalse(IndustrialProfile.isToolAllowed("saveSimulationState"));
  }

  @Test
  void digitalTwinMode_blocksTier3() {
    IndustrialProfile.setActiveMode(DeploymentMode.DIGITAL_TWIN);
    assertFalse(IndustrialProfile.isToolAllowed("solveTask"), "Digital twin must block Tier 3");
    assertFalse(IndustrialProfile.isToolAllowed("composeWorkflow"));
    assertFalse(IndustrialProfile.isToolAllowed("runReservoir"));
    assertFalse(IndustrialProfile.isToolAllowed("runPlugin"));
  }

  @Test
  void digitalTwinMode_requiresApproval_forExecution() {
    IndustrialProfile.setActiveMode(DeploymentMode.DIGITAL_TWIN);
    // Even though these are blocked, the approval flag is set per category
    assertTrue(IndustrialProfile.requiresApproval("solveTask"),
        "Digital twin: Execution tools require approval");
    assertTrue(IndustrialProfile.requiresApproval("composeWorkflow"),
        "Digital twin: Execution tools require approval");
  }

  @Test
  void digitalTwinMode_enforceAccess_tier2Blocked() {
    IndustrialProfile.setActiveMode(DeploymentMode.DIGITAL_TWIN);
    String result = IndustrialProfile.enforceAccess("runPVT");
    assertNotNull(result);
    JsonObject error = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("blocked", error.get("status").getAsString());
    assertEquals("ENGINEERING_ADVANCED", error.get("tier").getAsString());
    assertEquals("DIGITAL_TWIN", error.get("mode").getAsString());
  }

  // ─── ENTERPRISE — Tier 1 only ──────────────────────────────────────────────

  @Test
  void enterpriseMode_allowsTier1() {
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    assertTrue(IndustrialProfile.isToolAllowed("runFlash"));
    assertTrue(IndustrialProfile.isToolAllowed("runProcess"));
    assertTrue(IndustrialProfile.isToolAllowed("validateInput"));
    assertTrue(IndustrialProfile.isToolAllowed("getBenchmarkTrust"));
    assertTrue(IndustrialProfile.isToolAllowed("checkToolAccess"));
  }

  @Test
  void enterpriseMode_blocksTier2() {
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    assertFalse(IndustrialProfile.isToolAllowed("runPVT"));
    assertFalse(IndustrialProfile.isToolAllowed("runPipeline"));
    assertFalse(IndustrialProfile.isToolAllowed("runFlowAssurance"));
    assertFalse(IndustrialProfile.isToolAllowed("compareProcesses"));
  }

  @Test
  void enterpriseMode_blocksTier3() {
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    assertFalse(IndustrialProfile.isToolAllowed("solveTask"));
    assertFalse(IndustrialProfile.isToolAllowed("composeWorkflow"));
    assertFalse(IndustrialProfile.isToolAllowed("runReservoir"));
    assertFalse(IndustrialProfile.isToolAllowed("manageSecurity"));
    assertFalse(IndustrialProfile.isToolAllowed("runPlugin"));
  }

  @Test
  void enterpriseMode_enforceAccess_blockedReturnsStructuredError() {
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);

    // Block a Tier 2 tool
    String result = IndustrialProfile.enforceAccess("runPVT");
    assertNotNull(result);
    JsonObject error = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("blocked", error.get("status").getAsString());
    assertEquals("runPVT", error.get("tool").getAsString());
    assertEquals("ENTERPRISE", error.get("mode").getAsString());
    assertEquals("ENGINEERING_ADVANCED", error.get("tier").getAsString());
    assertTrue(error.get("reason").getAsString().contains("ENTERPRISE"),
        "Reason should mention ENTERPRISE mode");
    assertTrue(error.get("reason").getAsString().contains("Tier 1"),
        "Reason should reference Tier 1 restriction");
    assertTrue(error.get("remediation").getAsString().contains("DESKTOP_ENGINEER"),
        "Remediation should suggest DESKTOP_ENGINEER");

    // Block a Tier 3 tool
    String result3 = IndustrialProfile.enforceAccess("solveTask");
    assertNotNull(result3);
    JsonObject error3 = JsonParser.parseString(result3).getAsJsonObject();
    assertEquals("EXPERIMENTAL", error3.get("tier").getAsString());
  }

  @Test
  void enterpriseMode_enforceAccess_tier1PassesThrough() {
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    assertNull(IndustrialProfile.enforceAccess("runFlash"));
    assertNull(IndustrialProfile.enforceAccess("validateInput"));
    assertNull(IndustrialProfile.enforceAccess("getBenchmarkTrust"));
  }

  @Test
  void enterpriseMode_requiresApproval_executionTools() {
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    assertTrue(IndustrialProfile.requiresApproval("solveTask"));
    assertTrue(IndustrialProfile.requiresApproval("composeWorkflow"));
    assertTrue(IndustrialProfile.requiresApproval("manageSession"));
    assertTrue(IndustrialProfile.requiresApproval("setSimulationVariable"));
    assertTrue(IndustrialProfile.requiresApproval("saveSimulationState"));
  }

  @Test
  void enterpriseMode_noApproval_advisoryTools() {
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    assertFalse(IndustrialProfile.requiresApproval("searchComponents"));
    assertFalse(IndustrialProfile.requiresApproval("getCapabilities"));
  }

  // ─── setActiveMode correctly toggles flags ─────────────────────────────────

  @Test
  void setActiveMode_desktop_validationOn_approvalOff() {
    IndustrialProfile.setActiveMode(DeploymentMode.DESKTOP_ENGINEER);
    assertTrue(IndustrialProfile.isAutoValidationEnabled());
    assertFalse(IndustrialProfile.requiresApproval("solveTask"), "Desktop: no approval gate");
  }

  @Test
  void setActiveMode_enterprise_approvalOn() {
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    assertTrue(IndustrialProfile.isAutoValidationEnabled());
    assertTrue(IndustrialProfile.requiresApproval("solveTask"),
        "Enterprise: approval gate enabled for execution tools");
  }

  // ─── solveTask is explicitly classified as EXPERIMENTAL ─────────────────────

  @Test
  void solveTask_isExperimental() {
    assertEquals(ToolTier.EXPERIMENTAL, IndustrialProfile.getToolTier("solveTask"),
        "solveTask MUST be Tier 3 EXPERIMENTAL");
  }

  @Test
  void solveTask_isExecution() {
    assertEquals(ToolCategory.EXECUTION, IndustrialProfile.getToolCategory("solveTask"),
        "solveTask MUST be classified as EXECUTION");
  }

  @Test
  void solveTask_blockedInAllRestrictedModes() {
    // STUDY_TEAM blocks Tier 3
    IndustrialProfile.setActiveMode(DeploymentMode.STUDY_TEAM);
    assertFalse(IndustrialProfile.isToolAllowed("solveTask"));
    assertNotNull(IndustrialProfile.enforceAccess("solveTask"));

    // DIGITAL_TWIN blocks Tier 3
    IndustrialProfile.setActiveMode(DeploymentMode.DIGITAL_TWIN);
    assertFalse(IndustrialProfile.isToolAllowed("solveTask"));
    assertNotNull(IndustrialProfile.enforceAccess("solveTask"));

    // ENTERPRISE blocks Tier 3
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    assertFalse(IndustrialProfile.isToolAllowed("solveTask"));
    assertNotNull(IndustrialProfile.enforceAccess("solveTask"));
  }

  // ─── describeProfiles returns valid JSON with all tiers ────────────────────

  @Test
  void describeProfiles_containsAllTiers() {
    String json = IndustrialProfile.describeProfiles();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.has("tier1_trustedCore"), "Must include tier1_trustedCore array");
    assertTrue(root.has("tier2_engineeringAdvanced"), "Must include tier2_engineeringAdvanced");
    assertTrue(root.has("tier3_experimental"), "Must include tier3_experimental");

    assertEquals(21, root.getAsJsonArray("tier1_trustedCore").size());
    assertEquals(22, root.getAsJsonArray("tier2_engineeringAdvanced").size());
    assertEquals(14, root.getAsJsonArray("tier3_experimental").size());
  }

  @Test
  void describeProfiles_reportsActiveMode() {
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    String json = IndustrialProfile.describeProfiles();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("ENTERPRISE", root.get("activeMode").getAsString());
  }

  // ─── Complete tier enforcement matrix ──────────────────────────────────────

  @Test
  void enforcementMatrix_allCombinations() {
    // Define representative tools from each tier
    String tier1 = "runFlash";
    String tier2 = "runPVT";
    String tier3 = "solveTask";

    // DESKTOP: all pass
    IndustrialProfile.setActiveMode(DeploymentMode.DESKTOP_ENGINEER);
    assertNull(IndustrialProfile.enforceAccess(tier1), "Desktop + Tier1 = pass");
    assertNull(IndustrialProfile.enforceAccess(tier2), "Desktop + Tier2 = pass");
    assertNull(IndustrialProfile.enforceAccess(tier3), "Desktop + Tier3 = pass");

    // STUDY_TEAM: 1+2 pass, 3 blocked
    IndustrialProfile.setActiveMode(DeploymentMode.STUDY_TEAM);
    assertNull(IndustrialProfile.enforceAccess(tier1), "StudyTeam + Tier1 = pass");
    assertNull(IndustrialProfile.enforceAccess(tier2), "StudyTeam + Tier2 = pass");
    assertNotNull(IndustrialProfile.enforceAccess(tier3), "StudyTeam + Tier3 = blocked");

    // DIGITAL_TWIN: only Tier1 advisory+calc pass
    IndustrialProfile.setActiveMode(DeploymentMode.DIGITAL_TWIN);
    assertNull(IndustrialProfile.enforceAccess(tier1), "DigitalTwin + Tier1 calc = pass");
    assertNotNull(IndustrialProfile.enforceAccess(tier2), "DigitalTwin + Tier2 = blocked");
    assertNotNull(IndustrialProfile.enforceAccess(tier3), "DigitalTwin + Tier3 = blocked");

    // ENTERPRISE: only Tier1 pass
    IndustrialProfile.setActiveMode(DeploymentMode.ENTERPRISE);
    assertNull(IndustrialProfile.enforceAccess(tier1), "Enterprise + Tier1 = pass");
    assertNotNull(IndustrialProfile.enforceAccess(tier2), "Enterprise + Tier2 = blocked");
    assertNotNull(IndustrialProfile.enforceAccess(tier3), "Enterprise + Tier3 = blocked");
  }
}
