package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests binding the published MCP tool surface to the governance and discovery layers.
 *
 * <p>
 * The capability catalog used to be hand-maintained, which let {@code designUtilities} and {@code runHazopScenario}
 * exist as published tools while being absent from discovery. These tests make that class of drift a build failure: the
 * {@code @Tool} methods on the server facade, the governance tier classification, and the capability catalog must
 * describe exactly the same set.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class McpToolSurfaceContractTest {

  /** Path to the MCP server tool facade, relative to the repository root. */
  private static final String TOOLS_SOURCE = "neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java";

  /** Matches a published tool method declaration. */
  private static final Pattern TOOL_METHOD = Pattern.compile("^\\s*public\\s+String\\s+(\\w+)\\s*\\(");

  /**
   * Extracts the tool names published by the server facade.
   *
   * <p>
   * The facade lives in a separate Maven module that is not on this module's classpath, so the tool names are read from
   * source rather than by reflection. When the module is not checked out the test is skipped instead of failing.
   * </p>
   *
   * @return the set of published tool names, or an empty set when the source is unavailable
   * @throws IOException if the source file cannot be read
   */
  private static Set<String> readPublishedToolNames() throws IOException {
    Path source = Paths.get(TOOLS_SOURCE);
    if (!Files.exists(source)) {
      return new LinkedHashSet<String>();
    }
    List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
    Set<String> tools = new LinkedHashSet<String>();
    boolean pendingTool = false;
    for (String line : lines) {
      if (line.contains("@Tool(")) {
        pendingTool = true;
        continue;
      }
      if (!pendingTool) {
        continue;
      }
      Matcher matcher = TOOL_METHOD.matcher(line);
      if (matcher.find()) {
        tools.add(matcher.group(1));
        pendingTool = false;
      }
    }
    return tools;
  }

  /**
   * Every published tool must be classified by the governance layer, and every classified tool must exist. A mismatch
   * means either a tool ships ungoverned or governance references a dead name.
   *
   * @throws IOException if the facade source cannot be read
   */
  @Test
  @DisplayName("Published @Tool methods match the governed tool surface exactly")
  void testPublishedToolsMatchGovernance() throws IOException {
    Set<String> published = readPublishedToolNames();
    Assumptions.assumeFalse(published.isEmpty(), "neqsim-mcp-server module not present — skipping");

    Set<String> governed = IndustrialProfile.getAllKnownTools();

    List<String> ungoverned = new ArrayList<String>(new TreeSet<String>(published));
    ungoverned.removeAll(governed);
    assertTrue(ungoverned.isEmpty(), "Published tools missing a trust tier in IndustrialProfile: " + ungoverned);

    List<String> stale = new ArrayList<String>(new TreeSet<String>(governed));
    stale.removeAll(published);
    assertTrue(stale.isEmpty(), "IndustrialProfile classifies tools that are no longer published: " + stale);

    assertEquals(published.size(), governed.size(), "Published and governed tool counts must agree");
  }

  /**
   * Each tool must carry both a trust tier and a risk category, since deployment profiles filter on both.
   */
  @Test
  @DisplayName("Every governed tool has a trust tier and a risk category")
  void testEveryToolIsFullyClassified() {
    List<String> missingCategory = new ArrayList<String>();
    for (String tool : IndustrialProfile.getAllKnownTools()) {
      assertNotNull(IndustrialProfile.getToolTier(tool), "Tool without a trust tier: " + tool);
      if (IndustrialProfile.getToolCategory(tool) == null) {
        missingCategory.add(tool);
      }
    }
    assertTrue(missingCategory.isEmpty(), "Tools without a risk category: " + missingCategory);
  }

  /**
   * The capability manifest must describe every governed tool, so an agent that discovers capabilities sees the
   * complete surface.
   */
  @Test
  @DisplayName("Capability catalog covers every governed tool")
  void testCapabilityCatalogCoverage() {
    JsonObject manifest = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject coverage = manifest.getAsJsonObject("toolCatalogCoverage");
    assertNotNull(coverage, "getCapabilities must report toolCatalogCoverage");

    assertEquals(0, coverage.getAsJsonArray("missingDescriptors").size(),
        "Governed tools missing from the capability catalog: " + coverage.getAsJsonArray("missingDescriptors"));
    assertEquals(0, coverage.getAsJsonArray("undeclaredDescriptors").size(),
        "Catalog describes tools that are not governed: " + coverage.getAsJsonArray("undeclaredDescriptors"));
    assertTrue(coverage.get("complete").getAsBoolean(), "Capability catalog coverage must be complete");

    JsonObject tools = manifest.getAsJsonObject("toolCapabilities");
    assertTrue(tools.has("designUtilities"), "designUtilities regression: must be present in the catalog");
    assertTrue(tools.has("runHazopScenario"), "runHazopScenario regression: must be present in the catalog");
  }

  /**
   * Pins the published tier sizes. These numbers appear in README.md and MCP_CONTRACT.md, and drifted from the code
   * before (documented 21 trusted-core versus 22 in code). Changing the surface must be a deliberate act that also
   * updates the documentation.
   */
  @Test
  @DisplayName("Tier sizes match the documented tool counts")
  void testDocumentedTierCounts() {
    assertEquals(24, IndustrialProfile.getIndustrialCore().size(),
        "Trusted-core tier size changed — update README.md and MCP_CONTRACT.md");
    assertEquals(32, IndustrialProfile.getEngineeringAdvanced().size(),
        "Engineering-advanced tier size changed — update README.md");
    assertEquals(71, IndustrialProfile.getAllKnownTools().size(),
        "Published tool count changed — update README.md and MCP_CONTRACT.md");
  }

  /**
   * Every catalog descriptor must expose the fields a deployment profile filters on.
   */
  @Test
  @DisplayName("Catalog descriptors expose trust tier, risk category and profile availability")
  void testDescriptorsExposeGovernanceFields() {
    JsonObject manifest = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject tools = manifest.getAsJsonObject("toolCapabilities");
    for (String tool : IndustrialProfile.getAllKnownTools()) {
      JsonObject descriptor = tools.getAsJsonObject(tool);
      assertNotNull(descriptor, "Missing descriptor for " + tool);
      assertTrue(descriptor.has("trustTier"), tool + " descriptor must declare trustTier");
      assertTrue(descriptor.has("riskCategory"), tool + " descriptor must declare riskCategory");
      assertTrue(descriptor.has("allowedInActiveProfile"), tool + " descriptor must declare allowedInActiveProfile");
      assertFalse("UNCLASSIFIED".equals(descriptor.get("trustTier").getAsString()),
          tool + " must have a real trust tier");
    }
  }
}
