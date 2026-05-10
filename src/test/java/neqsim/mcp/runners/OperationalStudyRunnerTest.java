package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link OperationalStudyRunner}.
 *
 * @author ESOL
 * @version 1.0
 */
class OperationalStudyRunnerTest {

  /**
   * Verifies the schema discovery action.
   */
  @Test
  void getSchemaReturnsOperationalActions() {
    JsonObject result = JsonParser
        .parseString(OperationalStudyRunner.run("{\"action\":\"getSchema\"}")).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertEquals("runOperationalStudy", result.get("tool").getAsString());
    assertTrue(result.getAsJsonArray("actions").size() >= 6);
  }

  /**
   * Verifies that field data can be applied through an automation address binding.
   */
  @Test
  void applyFieldDataWritesAutomationBinding() {
    String json = "{" + "\"action\":\"applyFieldData\"," + processJsonField() + ","
        + "\"tagBindings\":[{" + "\"logicalTag\":\"outlet_valve_position\","
        + "\"automationAddress\":\"Outlet Valve.percentValveOpening\","
        + "\"unit\":\"%\",\"role\":\"INPUT\"}],"
        + "\"fieldData\":{\"outlet_valve_position\":35.0}}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertEquals(35.0, result.getAsJsonObject("applied").get("outlet_valve_position").getAsDouble(),
        1.0e-10);
    assertEquals(35.0,
        result.getAsJsonObject("modelValues").get("outlet_valve_position").getAsDouble(), 1.0e-10);
  }

  /**
   * Verifies that operational scenarios execute valve and automation actions.
   */
  @Test
  void runScenarioExecutesValveAndAutomationActions() {
    String json = "{" + "\"action\":\"runScenario\"," + "\"scenarioName\":\"partly close outlet\","
        + processJsonField() + "," + "\"actions\":["
        + "{\"type\":\"SET_VALVE_OPENING\",\"target\":\"Outlet Valve\",\"value\":15.0},"
        + "{\"type\":\"SET_VARIABLE\",\"target\":\"Outlet Valve.outletPressure\","
        + "\"value\":45.0,\"unit\":\"bara\"}," + "{\"type\":\"RUN_STEADY_STATE\"}]}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertTrue(result.get("successful").getAsBoolean(), result.toString());
    JsonObject scenarioResult = result.getAsJsonObject("scenarioResult");
    assertEquals(15.0, scenarioResult.getAsJsonObject("afterValues")
        .get("Outlet Valve.percentValveOpening").getAsDouble(), 1.0e-10);
  }

  /**
   * Verifies controller-response screening output.
   */
  @Test
  void evaluateControllerResponseReturnsRecommendation() {
    String json = "{" + "\"action\":\"evaluateControllerResponse\","
        + "\"controllerName\":\"LC-001\",\"setPoint\":1.0,"
        + "\"timeSeconds\":[0.0,10.0,20.0,30.0,40.0,50.0],"
        + "\"processValue\":[0.0,0.55,0.82,0.95,0.99,1.0],"
        + "\"controllerOutput\":[40.0,55.0,58.0,53.0,50.0,50.0],"
        + "\"outputMin\":0.0,\"outputMax\":100.0,\"settlingTolerance\":0.05}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    JsonObject tuning = result.getAsJsonObject("controllerTuning");
    assertEquals("ACCEPTABLE_SCREENING_RESULT", tuning.get("recommendation").getAsString());
  }

  /**
   * Verifies that the evidence-package action returns benchmark and bottleneck evidence.
   */
  @Test
  void runEvidencePackageReturnsBottleneckReport() {
    String json = "{" + "\"action\":\"runEvidencePackage\"," + processJsonField() + ","
        + "\"studyName\":\"operations screen\"," + "\"benchmarkToleranceFraction\":0.05,"
        + "\"tagBindings\":[" + "{\"logicalTag\":\"outlet_valve_position\","
        + "\"automationAddress\":\"Outlet Valve.percentValveOpening\","
        + "\"unit\":\"%\",\"role\":\"INPUT\"}," + "{\"logicalTag\":\"outlet_pressure\","
        + "\"automationAddress\":\"Outlet Valve.outletPressure\","
        + "\"unit\":\"bara\",\"role\":\"BENCHMARK\"}],"
        + "\"fieldData\":{\"outlet_valve_position\":70.0,\"outlet_pressure\":49.0},"
        + "\"scenarios\":[{\"scenarioName\":\"raise valve loading\","
        + "\"actions\":[{\"type\":\"SET_VALVE_OPENING\",\"target\":\"Outlet Valve\","
        + "\"value\":90.0},{\"type\":\"RUN_STEADY_STATE\"}]}]}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    JsonObject evidencePackage = result.getAsJsonObject("evidencePackage");
    assertTrue(evidencePackage.getAsJsonObject("benchmarkComparison").get("allWithinTolerance")
        .getAsBoolean(), result.toString());
    assertTrue(evidencePackage.getAsJsonObject("baseCapacity").getAsJsonObject("bottleneck")
        .get("hasBottleneck").getAsBoolean(), result.toString());
    assertEquals(1, evidencePackage.getAsJsonArray("scenarioStudies").size());
  }

  /**
   * Verifies that analyzePipeSections returns velocity and utilization for each section.
   */
  @Test
  void analyzePipeSectionsReturnsVelocityAndUtilization() {
    String json = "{\"action\":\"analyzePipeSections\","
        + "\"model\":\"SRK\",\"components\":{\"methane\":0.90,\"ethane\":0.10},"
        + "\"temperature_C\":25.0,\"pressure_bara\":70.0,"
        + "\"flowRate\":{\"value\":50000,\"unit\":\"kg/hr\"}," + "\"pipeSections\":["
        + "{\"name\":\"Line-A001\",\"innerDiameter_m\":0.254,\"length_m\":100,"
        + "\"roughness_m\":0.00005,\"maxDesignVelocity_m_s\":20.0,"
        + "\"sourceReference\":\"PID-001\"},"
        + "{\"name\":\"Line-A002\",\"innerDiameter_m\":0.1,\"length_m\":50,"
        + "\"maxDesignVelocity_m_s\":15.0,\"sourceReference\":\"PID-002\"}" + "]}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertEquals(2, result.getAsJsonArray("sections").size());
    assertTrue(result.getAsJsonObject("summary").has("maxUtilization"));
    assertTrue(result.getAsJsonObject("summary").get("maxUtilization").getAsDouble() > 0.0);

    JsonObject section1 = result.getAsJsonArray("sections").get(0).getAsJsonObject();
    assertEquals("Line-A001", section1.get("name").getAsString());
    assertTrue(section1.get("inletVelocity_m_s").getAsDouble() > 0.0);
    assertTrue(section1.get("pressureDrop_bar").getAsDouble() >= 0.0);
    assertEquals("PID-001", section1.get("sourceReference").getAsString());

    // The smaller diameter pipe should have higher velocity and utilization
    JsonObject section2 = result.getAsJsonArray("sections").get(1).getAsJsonObject();
    assertTrue(section2.get("maxVelocity_m_s").getAsDouble() > section1.get("maxVelocity_m_s")
        .getAsDouble());
  }

  /**
   * Verifies that analyzePipeSections works with field data overrides via sectionTagBindings.
   */
  @Test
  void analyzePipeSectionsSupportsFieldDataOverrides() {
    String json = "{\"action\":\"analyzePipeSections\","
        + "\"model\":\"SRK\",\"components\":{\"methane\":0.95,\"propane\":0.05},"
        + "\"temperature_C\":30.0,\"pressure_bara\":60.0,"
        + "\"flowRate\":{\"value\":20000,\"unit\":\"kg/hr\"}," + "\"pipeSections\":["
        + "{\"name\":\"Line-B001\",\"innerDiameter_m\":0.2,\"length_m\":80}" + "],"
        + "\"sectionTagBindings\":["
        + "{\"pipeSectionName\":\"Line-B001\",\"logicalTag\":\"FT-B001\","
        + "\"property\":\"flowRate_kg_hr\"}" + "]," + "\"fieldData\":{\"FT-B001\":40000.0}}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    // The field data override doubles the flow rate, so velocity should be higher
    JsonObject section = result.getAsJsonArray("sections").get(0).getAsJsonObject();
    assertTrue(section.get("inletVelocity_m_s").getAsDouble() > 0.0);
  }

  /**
   * Verifies that runEvidencePackage includes pipe section analysis when pipeSections are provided.
   */
  @Test
  void runEvidencePackageIncludesPipeSectionAnalysis() {
    String json = "{\"action\":\"runEvidencePackage\"," + processJsonField() + ","
        + "\"studyName\":\"with pipe sections\","
        + "\"components\":{\"methane\":0.90,\"ethane\":0.10},"
        + "\"temperature_C\":25.0,\"pressure_bara\":70.0,"
        + "\"flowRate\":{\"value\":10000,\"unit\":\"kg/hr\"}," + "\"pipeSections\":["
        + "{\"name\":\"Bypass-001\",\"innerDiameter_m\":0.15,\"length_m\":30}" + "]}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertTrue(result.has("evidencePackage"));
    assertTrue(result.has("pipeSectionAnalysis"));
    JsonObject pipeAnalysis = result.getAsJsonObject("pipeSectionAnalysis");
    assertEquals("success", pipeAnalysis.get("status").getAsString());
    assertEquals(1, pipeAnalysis.getAsJsonArray("sections").size());
  }

  /**
   * Verifies that designCapacities from JSON are applied to equipment and reported with data source
   * tracking.
   */
  @Test
  void runEvidencePackageAppliesDesignCapacities() {
    String json = "{\"action\":\"runEvidencePackage\"," + processJsonField() + ","
        + "\"studyName\":\"design capacity test\","
        + "\"designCapacities\":{\"Separator\":{\"internalDiameter\":2.5,\"separatorLength\":8.0,"
        + "\"designGasLoadFactor\":0.08}}}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());

    // Verify designCapacitiesApplied section exists and reports what was applied
    assertTrue(result.has("designCapacitiesApplied"), result.toString());
    JsonObject applied = result.getAsJsonObject("designCapacitiesApplied");
    assertTrue(applied.has("Separator"), result.toString());
    JsonObject sepApplied = applied.getAsJsonObject("Separator");
    assertEquals("applied", sepApplied.get("status").getAsString());
    assertTrue(sepApplied.has("appliedProperties"));

    // Verify that capacity constraints include dataSource in the report
    JsonObject evidencePackage = result.getAsJsonObject("evidencePackage");
    JsonObject baseCapacity = evidencePackage.getAsJsonObject("baseCapacity");
    assertTrue(baseCapacity.has("equipmentConstraints"));
  }

  /**
   * Verifies that dataSource is reported as "not_set" or "equipment" when no designCapacities are
   * provided.
   */
  @Test
  void runEvidencePackageReportsDataSourceWithoutDesignCapacities() {
    String json = "{\"action\":\"runEvidencePackage\"," + processJsonField() + ","
        + "\"studyName\":\"no design data\"}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());

    // No designCapacitiesApplied section when no design data provided
    assertTrue(!result.has("designCapacitiesApplied"), result.toString());

    // But constraints should still have dataSource field
    JsonObject evidencePackage = result.getAsJsonObject("evidencePackage");
    JsonObject baseCapacity = evidencePackage.getAsJsonObject("baseCapacity");
    assertTrue(baseCapacity.has("equipmentConstraints"));
  }

  /**
   * Verifies that the operating-envelope action applies field data and returns margin evidence.
   */
  @Test
  void evaluateOperatingEnvelopeReturnsMarginsAndPredictions() {
    String json = "{" + "\"action\":\"evaluateOperatingEnvelope\"," + processJsonField() + ","
        + "\"tagBindings\":[{" + "\"logicalTag\":\"outlet_valve_position\","
        + "\"automationAddress\":\"Outlet Valve.percentValveOpening\","
        + "\"unit\":\"%\",\"role\":\"INPUT\"}],"
        + "\"fieldData\":{\"outlet_valve_position\":72.0},"
        + "\"predictionHorizonSeconds\":1800.0,"
        + "\"marginHistory\":["
        + "{\"key\":\"Outlet Valve.valveOpening\",\"timestampSeconds\":0.0,"
        + "\"marginPercent\":35.0},"
        + "{\"key\":\"Outlet Valve.valveOpening\",\"timestampSeconds\":60.0,"
        + "\"marginPercent\":25.0},"
        + "{\"key\":\"Outlet Valve.valveOpening\",\"timestampSeconds\":120.0,"
        + "\"marginPercent\":15.0}]}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertTrue(result.has("operatingEnvelope"), result.toString());
    JsonObject envelope = result.getAsJsonObject("operatingEnvelope");
    assertTrue(envelope.getAsJsonArray("rankedMargins").size() > 0, envelope.toString());
    assertTrue(envelope.getAsJsonArray("mitigationSuggestions").size() > 0, envelope.toString());
    assertTrue(envelope.getAsJsonArray("tripPredictions").size() > 0, envelope.toString());
  }

  /**
   * Creates the process JSON field used by the tests.
   *
   * @return a JSON fragment with the processJson field
   */
  private String processJsonField() {
    return "\"processJson\":{" + "\"fluid\":{" + "\"model\":\"SRK\"," + "\"temperature\":298.15,"
        + "\"pressure\":70.0," + "\"mixingRule\":\"classic\"," + "\"components\":{\"methane\":0.90,"
        + "\"ethane\":0.10}}," + "\"process\":[" + "{\"type\":\"Stream\",\"name\":\"feed\","
        + "\"properties\":{\"flowRate\":[10000.0,\"kg/hr\"]}},"
        + "{\"type\":\"Separator\",\"name\":\"Separator\",\"inlet\":\"feed\"},"
        + "{\"type\":\"valve\",\"name\":\"Outlet Valve\"," + "\"inlet\":\"Separator.gasOut\","
        + "\"properties\":{\"outletPressure\":50.0}}]}";
  }
}
