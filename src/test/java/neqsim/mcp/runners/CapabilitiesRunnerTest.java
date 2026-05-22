package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;
import org.junit.jupiter.api.Test;

/**
 * Tests for the CapabilitiesRunner.
 */
class CapabilitiesRunnerTest {

  @Test
  void testGetCapabilities() {
    String result = CapabilitiesRunner.getCapabilities();
    assertNotNull(result);

    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("success".equals(obj.get("status").getAsString()));
    assertTrue(obj.has("engine"));
    assertTrue(obj.has("thermodynamics"));
    assertTrue(obj.has("processSimulation"));
    assertTrue(obj.has("calculationModes"));
    assertTrue(obj.has("toolCapabilities"));
    assertTrue(obj.has("setupTemplates"));
    assertTrue(obj.has("processJsonContract"));
    assertTrue(obj.has("capabilityGraph"));
    assertTrue(obj.has("equipmentPropertyOntology"));
    assertTrue(obj.has("benchmarkRegistry"));
    assertTrue(obj.has("unitSystem"));
    assertTrue(obj.has("automaticFlowsheetBuilder"));
    assertTrue(obj.has("optimizationUncertaintyWorkflows"));
    assertTrue(obj.has("modelLifecycle"));
    assertTrue(obj.has("safetyGatePolicy"));
    assertTrue(obj.has("engineeringDomains"));
    assertTrue(obj.has("trustModel"));
    assertTrue(obj.has("apiVersion"));
    assertTrue(obj.has("tool"));
    assertTrue(obj.has("data"));
    assertTrue(obj.has("validation"));
    assertTrue(obj.has("qualityGate"));
    assertTrue(obj.has("warnings"));

    JsonObject toolCapabilities = obj.getAsJsonObject("toolCapabilities");
    assertTrue(toolCapabilities.has("runFlash"));
    assertTrue(toolCapabilities.has("runProcess"));
    assertTrue(toolCapabilities.has("validateInput"));
    assertTrue(toolCapabilities.has("searchComponents"));
    assertTrue(toolCapabilities.has("getPropertyTable"));
    assertTrue(toolCapabilities.has("getPhaseEnvelope"));
    assertTrue(toolCapabilities.has("runPVT"));
    assertTrue(toolCapabilities.has("runDynamic"));
    assertTrue(toolCapabilities.has("runSafetySystemPerformance"));
    assertTrue(toolCapabilities.has("solveTask"));
    assertTrue(toolCapabilities.has("runRelief"));
    assertTrue(toolCapabilities.has("getBenchmarkTrust"));
    assertTrue(toolCapabilities.has("checkToolAccess"));
    assertTrue(toolCapabilities.entrySet().size() >= SchemaCatalog.getToolNames().size());
    JsonObject flashDescriptor = toolCapabilities.getAsJsonObject("runFlash");
    assertTrue(flashDescriptor.has("requiredFields"));
    assertTrue(flashDescriptor.has("supportedModels"));
    assertTrue(flashDescriptor.has("standardResponseFields"));
    assertTrue(flashDescriptor.has("schemas"));
    assertTrue(flashDescriptor.has("examples"));
    assertTrue(flashDescriptor.has("setupTemplates"));

    // Trust model should describe provenance
    JsonObject trust = obj.getAsJsonObject("trustModel");
    assertTrue(trust.get("provenanceIncluded").getAsBoolean());

    JsonObject processContract = obj.getAsJsonObject("processJsonContract");
    assertTrue(processContract.has("supportedEquipmentTypes"));
    assertTrue(processContract.has("commonPropertiesByEquipment"));
    assertTrue(processContract.has("units"));

    JsonObject graph = obj.getAsJsonObject("capabilityGraph");
    assertTrue(graph.get("nodeCount").getAsInt() > 50);
    assertTrue(graph.get("edgeCount").getAsInt() > 50);

    JsonObject safetyGate = obj.getAsJsonObject("safetyGatePolicy");
    assertTrue(safetyGate.get("engineeringReviewRequired").getAsBoolean());
  }

  @Test
  void testEverySchemaBackedToolHasCapabilityDescriptor() {
    JsonObject root =
        JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject toolCapabilities = root.getAsJsonObject("toolCapabilities");

    Set<String> advertisedSchemaTools = new HashSet<String>();
    for (Map.Entry<String, JsonElement> entry : toolCapabilities.entrySet()) {
      JsonObject descriptor = entry.getValue().getAsJsonObject();
      advertisedSchemaTools.add(descriptor.get("schemaToolName").getAsString());
    }

    for (String schemaToolName : SchemaCatalog.getToolNames()) {
      assertTrue(advertisedSchemaTools.contains(schemaToolName),
          "Missing capability descriptor for " + schemaToolName);
    }
  }

  @Test
  void testAdvertisedCapabilitiesResolveSchemasExamplesAndTemplates() {
    JsonObject root =
        JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject toolCapabilities = root.getAsJsonObject("toolCapabilities");
    JsonObject setupTemplates = root.getAsJsonObject("setupTemplates");

    for (Map.Entry<String, JsonElement> entry : toolCapabilities.entrySet()) {
      JsonObject descriptor = entry.getValue().getAsJsonObject();
      String schemaToolName = descriptor.get("schemaToolName").getAsString();

      assertNotNull(SchemaCatalog.getSchema(schemaToolName, "input"),
          "Missing input schema for " + schemaToolName);
      assertNotNull(SchemaCatalog.getSchema(schemaToolName, "output"),
          "Missing output schema for " + schemaToolName);

      JsonArray examples = descriptor.getAsJsonArray("examples");
      assertTrue(examples.size() > 0, "Missing example reference for " + schemaToolName);
      JsonObject example = examples.get(0).getAsJsonObject();
      assertNotNull(
          ExampleCatalog.getExample(example.get("category").getAsString(),
              example.get("name").getAsString()),
          "Example reference does not resolve for " + schemaToolName);

      JsonArray templates = descriptor.getAsJsonArray("setupTemplates");
      assertTrue(templates.size() > 0, "Missing setup template reference for " + schemaToolName);
      String templateId = templates.get(0).getAsJsonObject().get("id").getAsString();
      assertTrue(setupTemplates.has(templateId), "Setup template does not resolve: " + templateId);

      assertTrue(descriptor.has("validationCoverage"));
      assertTrue(descriptor.has("responseContractCoverage"));
      assertTrue(
          descriptor.getAsJsonObject("validationCoverage").get("inputSchema").getAsBoolean());
      assertTrue(descriptor.getAsJsonObject("responseContractCoverage").get("outputSchema")
          .getAsBoolean());
    }
  }

  @Test
  void testCapabilitiesAreCached() {
    String result1 = CapabilitiesRunner.getCapabilities();
    String result2 = CapabilitiesRunner.getCapabilities();
    // Should return same reference (cached)
    assertTrue(result1 == result2, "Capabilities should be cached");
  }
}
