package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;

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
    assertTrue(obj.has("implementationInventory"));
    assertTrue(obj.has("phase0EvidenceInventory"));
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
    assertTrue(toolCapabilities.has("runChemistry"));
    assertTrue(toolCapabilities.has("runWaterHammer"));
    assertTrue(toolCapabilities.has("runRootCauseAnalysis"));
    assertTrue(toolCapabilities.has("runDynamic"));
    assertTrue(toolCapabilities.has("runSafetySystemPerformance"));
    assertTrue(toolCapabilities.has("solveTask"));
    assertTrue(toolCapabilities.has("runAgenticEngineering"));
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
    JsonObject pipelineDescriptor = toolCapabilities.getAsJsonObject("runPipeline");
    assertTrue(pipelineDescriptor.get("purpose").getAsString().contains("two-fluid"));
    assertTrue(pipelineDescriptor.getAsJsonArray("optionalFields").toString().contains("sectionLengths_m"));
    assertTrue(pipelineDescriptor.getAsJsonArray("knownLimitations").toString().contains("mesh"));

    JsonObject pipelineTrust = JsonParser.parseString(BenchmarkTrust.getToolTrust("runPipeline")).getAsJsonObject()
        .getAsJsonObject("trust");
    assertTrue(pipelineTrust.get("description").getAsString().contains("two-fluid"));
    assertTrue(pipelineTrust.getAsJsonArray("knownLimitations").toString().contains("closure correlations"));

    // Trust model should describe provenance
    JsonObject trust = obj.getAsJsonObject("trustModel");
    assertTrue(trust.get("provenanceIncluded").getAsBoolean());

    JsonObject processContract = obj.getAsJsonObject("processJsonContract");
    assertTrue(processContract.has("supportedEquipmentTypes"));
    assertTrue(processContract.getAsJsonArray("supportedEquipmentTypes").toString().contains("ElectricMotor"));
    assertTrue(
        processContract.getAsJsonArray("supportedEquipmentTypes").toString().contains("ClausCatalyticConverter"));
    assertFalse(processContract.getAsJsonArray("supportedEquipmentTypes").toString().contains("Ejector"));
    assertTrue(processContract.has("equipmentTypeDiscovery"));
    assertTrue(processContract.has("equipmentSupportScope"));
    assertTrue(processContract.has("commonPropertiesByEquipment"));
    assertTrue(processContract.has("units"));
    assertTrue(processContract.getAsJsonArray("rootFields").toString().contains("interAreaLinks"));
    assertTrue(processContract.getAsJsonArray("streamReferencePorts").toString().contains("splitStream_0"));
    assertTrue(processContract.has("recommendedWorkflow"));

    JsonObject graph = obj.getAsJsonObject("capabilityGraph");
    assertTrue(graph.get("nodeCount").getAsInt() > 50);
    assertTrue(graph.get("edgeCount").getAsInt() > 50);

    JsonObject safetyGate = obj.getAsJsonObject("safetyGatePolicy");
    assertTrue(safetyGate.get("engineeringReviewRequired").getAsBoolean());
  }

  @Test
  void testImplementationInventoryIsCompleteAndResolvable() throws ClassNotFoundException {
    JsonObject root = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject inventory = root.getAsJsonObject("implementationInventory");
    JsonObject bindings = inventory.getAsJsonObject("toolImplementationBindings");

    assertTrue(inventory.get("complete").getAsBoolean());
    assertEquals(71, inventory.get("toolBindingCount").getAsInt());
    assertEquals(60, inventory.get("implementationClassCount").getAsInt());
    assertEquals(71, bindings.size());
    assertEquals(IndustrialProfile.getAllKnownTools(), McpImplementationInventory.getToolImplementations().keySet());
    assertEquals(0, inventory.getAsJsonArray("missingToolBindings").size());
    assertEquals(0, inventory.getAsJsonArray("mismatchedCapabilityBindings").size());
    assertEquals(0, inventory.getAsJsonArray("undeclaredToolBindings").size());

    JsonObject toolCapabilities = root.getAsJsonObject("toolCapabilities");
    for (Map.Entry<String, JsonElement> entry : bindings.entrySet()) {
      String toolName = entry.getKey();
      String implementationClass = entry.getValue().getAsString();
      assertEquals(implementationClass,
          toolCapabilities.getAsJsonObject(toolName).get("implementationClass").getAsString());
      assertNotNull(Class.forName(implementationClass), "Implementation class does not resolve for " + toolName);
    }

    JsonArray equipmentTypes = inventory.getAsJsonArray("supportedEquipmentTypes");
    JsonArray contractEquipment = root.getAsJsonObject("processJsonContract").getAsJsonArray("supportedEquipmentTypes");
    assertEquals(205, inventory.get("equipmentTypeCount").getAsInt());
    assertEquals(contractEquipment, equipmentTypes);
    assertTrue(equipmentTypes.toString().contains("Compressor"));
    assertTrue(equipmentTypes.toString().contains("ThreePhaseSeparator"));
    assertTrue(equipmentTypes.toString().contains("ThrottlingValve"));

    JsonArray reportPaths = inventory.getAsJsonArray("reportPaths");
    assertEquals(2, inventory.get("reportPathCount").getAsInt());
    assertEquals("generateReport", reportPaths.get(0).getAsJsonObject().get("tool").getAsString());
    assertEquals("neqsim.mcp.runners.ReportRunner",
        reportPaths.get(0).getAsJsonObject().get("implementationClass").getAsString());
    assertEquals("bridgeTaskWorkflow", reportPaths.get(1).getAsJsonObject().get("tool").getAsString());
    assertEquals("neqsim.mcp.runners.TaskWorkflowBridge",
        reportPaths.get(1).getAsJsonObject().get("implementationClass").getAsString());
  }

  @Test
  void testPhase0EvidenceInventoryMakesTrustGapsExplicit() {
    JsonObject root = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject inventory = root.getAsJsonObject("phase0EvidenceInventory");

    JsonObject tests = inventory.getAsJsonObject("tests");
    assertEquals(67, tests.get("javaTestClassCount").getAsInt());
    assertEquals(94, tests.get("protocolScenarioCount").getAsInt());

    JsonObject guides = inventory.getAsJsonObject("guides");
    assertEquals(4, guides.get("guideCount").getAsInt());
    assertEquals(4, guides.getAsJsonArray("entries").size());

    JsonObject limitations = inventory.getAsJsonObject("knownLimitations");
    assertEquals(71, limitations.get("publishedToolCount").getAsInt());
    assertEquals(20, limitations.get("explicitTrustToolCount").getAsInt());
    assertEquals(51, limitations.get("genericTrustToolCount").getAsInt());
    assertEquals(64, limitations.get("knownLimitationCount").getAsInt());
    assertEquals(0, limitations.get("unsupportedConditionCount").getAsInt());
    assertEquals(30, limitations.get("validationCaseCount").getAsInt());
    assertEquals(5, limitations.get("verifiedValidationCaseCount").getAsInt());
    assertEquals(20, limitations.getAsJsonArray("explicitTrustTools").size());
    assertEquals(51, limitations.getAsJsonArray("genericTrustTools").size());
    assertFalse(limitations.get("complete").getAsBoolean());
    assertFalse(inventory.get("complete").getAsBoolean());
  }

  @Test
  void testEverySchemaBackedToolHasCapabilityDescriptor() {
    JsonObject root = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject toolCapabilities = root.getAsJsonObject("toolCapabilities");

    Set<String> advertisedSchemaTools = new HashSet<String>();
    for (Map.Entry<String, JsonElement> entry : toolCapabilities.entrySet()) {
      JsonObject descriptor = entry.getValue().getAsJsonObject();
      advertisedSchemaTools.add(descriptor.get("schemaToolName").getAsString());
    }

    for (String schemaToolName : SchemaCatalog.getToolNames()) {
      assertTrue(advertisedSchemaTools.contains(schemaToolName), "Missing capability descriptor for " + schemaToolName);
    }
  }

  @Test
  void testAdvertisedCapabilitiesResolveSchemasExamplesAndTemplates() {
    JsonObject root = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject toolCapabilities = root.getAsJsonObject("toolCapabilities");
    JsonObject setupTemplates = root.getAsJsonObject("setupTemplates");

    for (Map.Entry<String, JsonElement> entry : toolCapabilities.entrySet()) {
      JsonObject descriptor = entry.getValue().getAsJsonObject();
      String schemaToolName = descriptor.get("schemaToolName").getAsString();

      assertNotNull(SchemaCatalog.getSchema(schemaToolName, "input"), "Missing input schema for " + schemaToolName);
      assertNotNull(SchemaCatalog.getSchema(schemaToolName, "output"), "Missing output schema for " + schemaToolName);

      JsonArray examples = descriptor.getAsJsonArray("examples");
      assertTrue(examples.size() > 0, "Missing example reference for " + schemaToolName);
      JsonObject example = examples.get(0).getAsJsonObject();
      assertNotNull(ExampleCatalog.getExample(example.get("category").getAsString(), example.get("name").getAsString()),
          "Example reference does not resolve for " + schemaToolName);

      JsonArray templates = descriptor.getAsJsonArray("setupTemplates");
      assertTrue(templates.size() > 0, "Missing setup template reference for " + schemaToolName);
      String templateId = templates.get(0).getAsJsonObject().get("id").getAsString();
      assertTrue(setupTemplates.has(templateId), "Setup template does not resolve: " + templateId);

      assertTrue(descriptor.has("validationCoverage"));
      assertTrue(descriptor.has("responseContractCoverage"));
      assertTrue(descriptor.getAsJsonObject("validationCoverage").get("inputSchema").getAsBoolean());
      assertTrue(descriptor.getAsJsonObject("responseContractCoverage").get("outputSchema").getAsBoolean());
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
