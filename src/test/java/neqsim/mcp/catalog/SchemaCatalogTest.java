package neqsim.mcp.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SchemaCatalog}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SchemaCatalogTest {

  @Test
  void testFlashInputSchema_isValidJson() {
    String schema = SchemaCatalog.flashInputSchema();
    JsonObject root = JsonParser.parseString(schema).getAsJsonObject();

    assertEquals("FlashInput", root.get("title").getAsString());
    assertEquals("object", root.get("type").getAsString());
    assertTrue(root.has("properties"));

    JsonObject props = root.getAsJsonObject("properties");
    assertTrue(props.has("model"));
    assertTrue(props.has("temperature"));
    assertTrue(props.has("pressure"));
    assertTrue(props.has("flashType"));
    assertTrue(props.has("components"));
    assertTrue(props.has("mixingRule"));
  }

  @Test
  void testFlashOutputSchema_isValidJson() {
    String schema = SchemaCatalog.flashOutputSchema();
    JsonObject root = JsonParser.parseString(schema).getAsJsonObject();

    assertEquals("FlashOutput", root.get("title").getAsString());
    assertTrue(root.has("properties"));
    JsonObject props = root.getAsJsonObject("properties");
    assertTrue(props.has("status"));
    assertTrue(props.has("flash"));
    assertTrue(props.has("fluid"));
  }

  @Test
  void testProcessInputSchema_isValidJson() {
    String schema = SchemaCatalog.processInputSchema();
    JsonObject root = JsonParser.parseString(schema).getAsJsonObject();

    assertEquals("ProcessInput", root.get("title").getAsString());
    assertTrue(root.has("properties"));
    JsonObject props = root.getAsJsonObject("properties");
    assertTrue(props.has("fluid"));
    assertTrue(props.has("process"));
  }

  @Test
  void testProcessOutputSchema_isValidJson() {
    String schema = SchemaCatalog.processOutputSchema();
    JsonObject root = JsonParser.parseString(schema).getAsJsonObject();

    assertEquals("ProcessOutput", root.get("title").getAsString());
    assertTrue(root.has("properties"));
  }

  @Test
  void testValidateInputSchema_isValidJson() {
    String schema = SchemaCatalog.validateInputSchema();
    JsonObject root = JsonParser.parseString(schema).getAsJsonObject();

    assertEquals("ValidateInput", root.get("title").getAsString());
  }

  @Test
  void testValidateOutputSchema_isValidJson() {
    String schema = SchemaCatalog.validateOutputSchema();
    JsonObject root = JsonParser.parseString(schema).getAsJsonObject();

    assertEquals("ValidateOutput", root.get("title").getAsString());
    assertTrue(root.has("properties"));

    JsonObject props = root.getAsJsonObject("properties");
    assertTrue(props.has("valid"));
    assertTrue(props.has("issues"));
  }

  @Test
  void testComponentSearchOutputSchema_isValidJson() {
    String schema = SchemaCatalog.componentSearchOutputSchema();
    JsonObject root = JsonParser.parseString(schema).getAsJsonObject();

    assertEquals("ComponentSearchOutput", root.get("title").getAsString());
    assertTrue(root.has("properties"));
  }

  @Test
  void testGetToolNames() {
    List<String> tools = SchemaCatalog.getToolNames();

    assertTrue(tools.size() >= 8);
    assertTrue(tools.contains("run_flash"));
    assertTrue(tools.contains("run_process"));
    assertTrue(tools.contains("validate_input"));
    assertTrue(tools.contains("list_components"));
    assertTrue(tools.contains("run_batch"));
    assertTrue(tools.contains("get_property_table"));
    assertTrue(tools.contains("get_phase_envelope"));
    assertTrue(tools.contains("get_capabilities"));
    assertTrue(tools.contains("run_root_cause_analysis"));
    assertTrue(tools.contains("run_open_drain_review"));
    assertTrue(tools.contains("run_norsok_s001_clause10_review"));
  }

  @Test
  void testGetSchema_flash() {
    String input = SchemaCatalog.getSchema("run_flash", "input");
    assertNotNull(input);
    assertTrue(input.contains("FlashInput"));

    String output = SchemaCatalog.getSchema("run_flash", "output");
    assertNotNull(output);
    assertTrue(output.contains("FlashOutput"));
  }

  @Test
  void testGetSchema_process() {
    String input = SchemaCatalog.getSchema("run_process", "input");
    assertNotNull(input);

    String output = SchemaCatalog.getSchema("run_process", "output");
    assertNotNull(output);
  }

  @Test
  void testGetSchema_unknown() {
    String result = SchemaCatalog.getSchema("unknown_tool", "input");
    assertNull(result);
  }

  @Test
  void testRootCauseSchemas() {
    String input = SchemaCatalog.getSchema("run_root_cause_analysis", "input");
    assertNotNull(input);
    JsonObject inputRoot = JsonParser.parseString(input).getAsJsonObject();
    assertEquals("RootCauseAnalysisInput", inputRoot.get("title").getAsString());
    JsonObject props = inputRoot.getAsJsonObject("properties");
    assertTrue(props.has("equipmentName"));
    assertTrue(props.has("symptom"));
    assertTrue(props.has("processJson"));
    assertTrue(props.has("historianCsv"));

    String output = SchemaCatalog.getSchema("run_root_cause_analysis", "output");
    assertNotNull(output);
    JsonObject outputRoot = JsonParser.parseString(output).getAsJsonObject();
    assertEquals("RootCauseAnalysisOutput", outputRoot.get("title").getAsString());
    assertTrue(outputRoot.getAsJsonObject("properties").has("hypotheses"));
  }

  @Test
  void testGetCatalogJson() {
    String json = SchemaCatalog.getCatalogJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertTrue(root.has("run_flash"));
    assertTrue(root.has("run_process"));
    assertTrue(root.has("validate_input"));
    assertTrue(root.has("list_components"));
    assertTrue(root.has("run_batch"));
    assertTrue(root.has("get_property_table"));
    assertTrue(root.has("get_phase_envelope"));
    assertTrue(root.has("get_capabilities"));

    // Each tool should have input and output URIs
    JsonObject flash = root.getAsJsonObject("run_flash");
    assertTrue(flash.get("inputSchemaUri").getAsString().contains("run_flash"));
    assertTrue(flash.get("outputSchemaUri").getAsString().contains("run_flash"));
    assertTrue(root.has("run_root_cause_analysis"));
    assertTrue(root.has("run_open_drain_review"));
    assertTrue(root.has("run_norsok_s001_clause10_review"));
  }

  @Test
  void testBatchSchemas() {
    String input = SchemaCatalog.getSchema("run_batch", "input");
    assertNotNull(input);
    assertTrue(input.contains("BatchInput"));

    String output = SchemaCatalog.getSchema("run_batch", "output");
    assertNotNull(output);
    assertTrue(output.contains("BatchOutput"));
  }

  @Test
  void testPropertyTableSchemas() {
    String input = SchemaCatalog.getSchema("get_property_table", "input");
    assertNotNull(input);
    assertTrue(input.contains("PropertyTableInput"));

    String output = SchemaCatalog.getSchema("get_property_table", "output");
    assertNotNull(output);
    assertTrue(output.contains("PropertyTableOutput"));
  }

  @Test
  void testPhaseEnvelopeSchemas() {
    String input = SchemaCatalog.getSchema("get_phase_envelope", "input");
    assertNotNull(input);
    assertTrue(input.contains("PhaseEnvelopeInput"));

    String output = SchemaCatalog.getSchema("get_phase_envelope", "output");
    assertNotNull(output);
    assertTrue(output.contains("PhaseEnvelopeOutput"));
  }

  @Test
  void testCapabilitiesSchema() {
    assertNull(SchemaCatalog.getSchema("get_capabilities", "input"));
    String output = SchemaCatalog.getSchema("get_capabilities", "output");
    assertNotNull(output);
    assertTrue(output.contains("CapabilitiesOutput"));
  }

  @Test
  void testOpenDrainReviewSchemas() {
    String input = SchemaCatalog.getSchema("run_open_drain_review", "input");
    assertNotNull(input);
    JsonObject inputRoot = JsonParser.parseString(input).getAsJsonObject();
    assertEquals("OpenDrainReviewInput", inputRoot.get("title").getAsString());

    String output = SchemaCatalog.getSchema("run_open_drain_review", "output");
    assertNotNull(output);
    JsonObject outputRoot = JsonParser.parseString(output).getAsJsonObject();
    assertEquals("OpenDrainReviewOutput", outputRoot.get("title").getAsString());
  }

  @Test
  void testNorsokS001Clause10ReviewSchemas() {
    String input = SchemaCatalog.getSchema("run_norsok_s001_clause10_review", "input");
    assertNotNull(input);
    JsonObject inputRoot = JsonParser.parseString(input).getAsJsonObject();
    assertEquals("NorsokS001Clause10ReviewInput", inputRoot.get("title").getAsString());

    String output = SchemaCatalog.getSchema("run_norsok_s001_clause10_review", "output");
    assertNotNull(output);
    JsonObject outputRoot = JsonParser.parseString(output).getAsJsonObject();
    assertEquals("NorsokS001Clause10ReviewOutput", outputRoot.get("title").getAsString());
  }
}
