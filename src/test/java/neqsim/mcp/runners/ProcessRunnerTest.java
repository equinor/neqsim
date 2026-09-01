package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link ProcessRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ProcessRunnerTest {

  @Test
  void testRun_simpleProcess() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"mixingRule\": \"classic\","
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"," + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}" + "]" + "}";

    String result = ProcessRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.has("report"));
    assertEquals("1.0", root.get("apiVersion").getAsString());
    assertTrue(root.has("data"));
    assertTrue(root.has("provenance"));
    assertTrue(root.has("validation"));
    assertTrue(root.has("qualityGate"));
    assertTrue(root.has("processDefinition"));
    assertTrue(root.getAsJsonObject("data").has("processDefinition"));
    assertTrue(root.has("pythonScript"));
    assertTrue(root.getAsJsonObject("data").has("pythonScript"));
    assertEquals("HP Sep", root.getAsJsonObject("processDefinition").getAsJsonArray("process").get(1).getAsJsonObject()
        .get("name").getAsString());
    assertTrue(root.get("pythonScript").getAsString().contains("ProcessSystem.fromJsonAndRun(PROCESS_JSON)"));
    assertTrue(root.getAsJsonObject("validation").get("valid").getAsBoolean());
    assertEquals("VALIDATED", root.getAsJsonObject("provenance").get("benchmarkTrustLevel").getAsString());
  }

  @Test
  void testRun_nullInput() {
    String result = ProcessRunner.run(null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("1.0", root.get("apiVersion").getAsString());
    assertTrue(root.has("validation"));
    assertTrue(root.has("qualityGate"));
    assertEquals("INPUT_ERROR", root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testRun_emptyInput() {
    String result = ProcessRunner.run("");
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
  }

  @Test
  void testValidateAndRun_validProcess() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}" + "]" + "}";

    String result = ProcessRunner.validateAndRun(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testValidateAndRun_invalidComponents() {
    String json = "{" + "\"fluid\": {" + "  \"components\": {\"fakey\": 1.0}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"}" + "]" + "}";

    String result = ProcessRunner.validateAndRun(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("validation", root.get("phase").getAsString());
    assertEquals("1.0", root.get("apiVersion").getAsString());
    assertTrue(root.has("validation"));
    assertTrue(root.has("qualityGate"));
    assertTrue(root.has("processDefinition"));
    assertTrue(root.has("pythonScript"));
    assertFalse(root.getAsJsonObject("validation").get("valid").getAsBoolean());
  }

  @Test
  void testValidateAndRun_nullInput() {
    String result = ProcessRunner.validateAndRun(null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
  }

  @Test
  void testRun_processWithCompressor() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 20.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"," + "   \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Compressor\", \"name\": \"Comp\", \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": [60.0, \"bara\"]}}" + "]" + "}";

    String result = ProcessRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testAutoSizingPreservesExplicitValveAndSizesCompressor() {
    String json = "{" + "\"autoSizing\": {\"enabled\": true, \"safetyFactor\": 1.15}," + "\"fluid\": {"
        + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15," + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"," + "   \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"ThrottlingValve\", \"name\": \"Valve\", \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": [40.0, \"bara\"], \"cv\": 5000.0}},"
        + "  {\"type\": \"Compressor\", \"name\": \"Comp\", \"inlet\": \"Valve.out\","
        + "   \"properties\": {\"outletPressure\": [60.0, \"bara\"]}}" + "]" + "}";

    JsonObject root = JsonParser.parseString(ProcessRunner.run(json)).getAsJsonObject();
    JsonObject autoSizing = root.getAsJsonObject("autoSizing");

    assertEquals("success", root.get("status").getAsString());
    assertEquals(1, autoSizing.get("sizedCount").getAsInt());
    assertEquals(1, autoSizing.get("preservedCount").getAsInt());
    assertEquals("preserved",
        autoSizing.getAsJsonArray("equipment").get(0).getAsJsonObject().get("status").getAsString());
    assertEquals("cv", autoSizing.getAsJsonArray("equipment").get(0).getAsJsonObject().get("evidence").getAsString());
    assertEquals("sized", autoSizing.getAsJsonArray("equipment").get(1).getAsJsonObject().get("status").getAsString());
    assertEquals("generated_screening",
        autoSizing.getAsJsonArray("equipment").get(1).getAsJsonObject().get("provenance").getAsString());
    JsonObject processDefinition = root.getAsJsonObject("processDefinition");
    JsonObject valveProperties = processDefinition.getAsJsonArray("process").get(1).getAsJsonObject()
        .getAsJsonObject("properties");
    JsonObject compressorProperties = processDefinition.getAsJsonArray("process").get(2).getAsJsonObject()
        .getAsJsonObject("properties");
    assertEquals(5000.0, valveProperties.get("cv").getAsDouble(), 1.0e-12);
    assertTrue(compressorProperties.has("compressorChart"));
    assertTrue(compressorProperties.getAsJsonObject("compressorChart").get("useCompressorChart").getAsBoolean());
    assertTrue(root.get("pythonScript").getAsString().contains("compressorChart"));
    assertTrue(root.has("designReport"));
    assertTrue(root.has("utilizationSnapshot"));
    assertTrue(root.has("bottleneckRanking"));
    assertTrue(root.getAsJsonObject("data").has("designReport"));
    assertTrue(root.getAsJsonObject("data").has("utilizationSnapshot"));
    assertTrue(root.getAsJsonObject("data").has("bottleneckRanking"));
  }

  @Test
  void testAntiSurgeSystemBindsPhysicalColdRecycleTopology() {
    String json = "{" + "\"autoSizing\": {\"enabled\": true},"
        + "\"antiSurgeSystems\": [{\"name\": \"Export anti-surge\", \"stages\": [{"
        + "\"name\": \"Stage 1\", \"compressor\": \"Comp\", \"suctionMixer\": \"Suction Mixer\","
        + "\"aftercooler\": \"Aftercooler\", \"coldRecycleValve\": \"Cold ASV\"," + "\"coldRecycle\": \"Cold Recycle\","
        + "\"recycleDesign\": {\"controlMargin\": 0.1, \"valvePressureDrop\": 10.0,"
        + "\"pipingVolume\": 1.0, \"requiredResponseTime\": 5.0},"
        + "\"speedControl\": {\"dischargePressureSetPoint\": 40.0, \"minimumSpeed\": 5000.0,"
        + "\"maximumSpeed\": 15000.0, \"speedGain\": 40.0, \"recycleRunbackRate\": 80.0}}]}],"
        + "\"fluid\": {\"model\": \"SRK\", \"temperature\": 298.15, \"pressure\": 20.0,"
        + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1}}, \"process\": ["
        + "{\"type\": \"Stream\", \"name\": \"feed\"," + "\"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "{\"type\": \"Mixer\", \"name\": \"Suction Mixer\"," + "\"inlets\": [\"feed\", \"Cold Recycle.out\"]},"
        + "{\"type\": \"Compressor\", \"name\": \"Comp\", \"inlet\": \"Suction Mixer.out\","
        + "\"properties\": {\"outletPressure\": [40.0, \"bara\"]}},"
        + "{\"type\": \"Cooler\", \"name\": \"Aftercooler\", \"inlet\": \"Comp.out\","
        + "\"properties\": {\"outTemperature\": [303.15, \"K\"]}},"
        + "{\"type\": \"Splitter\", \"name\": \"Discharge Splitter\","
        + "\"inlet\": \"Aftercooler.out\", \"properties\": {\"splitFactors\": [0.95, 0.05]}},"
        + "{\"type\": \"ThrottlingValve\", \"name\": \"Cold ASV\"," + "\"inlet\": \"Discharge Splitter.splitStream_1\","
        + "\"properties\": {\"outletPressure\": [20.0, \"bara\"], \"cv\": 5000.0}},"
        + "{\"type\": \"Recycle\", \"name\": \"Cold Recycle\", \"inlet\": \"Cold ASV.out\"}]}";

    JsonObject root = JsonParser.parseString(ProcessRunner.run(json)).getAsJsonObject();
    JsonObject antiSurge = root.getAsJsonObject("antiSurgeSystems");
    JsonObject system = antiSurge.getAsJsonArray("systems").get(0).getAsJsonObject();
    JsonObject stage = system.getAsJsonArray("stages").get(0).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString(), root.toString());
    assertEquals(1, antiSurge.get("configuredCount").getAsInt());
    assertEquals(0, antiSurge.get("failedCount").getAsInt());
    assertEquals(1, antiSurge.get("generatedScreeningMapCount").getAsInt());
    assertEquals("NOT_CERTIFIED_FOR_PROTECTION", antiSurge.get("certificationStatus").getAsString());
    assertEquals("configured", system.get("status").getAsString());
    assertTrue(stage.get("physicalTopologyBound").getAsBoolean());
    assertTrue(stage.get("speedControlEnabled").getAsBoolean());
    assertTrue(stage.get("screeningGradeMap").getAsBoolean());
    assertEquals("auto_sized_screening", stage.get("mapProvenance").getAsString());
    assertEquals("Cold ASV", stage.get("coldRecycleValve").getAsString());
    assertEquals("Cold Recycle", stage.get("coldRecycle").getAsString());
    assertTrue(system.getAsJsonObject("commissioning").has("checks"));
    assertTrue(root.getAsJsonObject("processDefinition").has("antiSurgeSystems"));
    assertTrue(root.get("pythonScript").getAsString().contains("antiSurgeSystems"));

    JsonObject canonical = root.getAsJsonObject("processDefinition").deepCopy();
    canonical.remove("autoSizing");
    JsonObject replay = JsonParser.parseString(ProcessRunner.run(canonical.toString())).getAsJsonObject();
    JsonObject replayAntiSurge = replay.getAsJsonObject("antiSurgeSystems");
    JsonObject replayStage = replayAntiSurge.getAsJsonArray("systems").get(0).getAsJsonObject().getAsJsonArray("stages")
        .get(0).getAsJsonObject();
    assertEquals("success", replay.get("status").getAsString(), replay.toString());
    assertEquals(0, replayAntiSurge.get("generatedScreeningMapCount").getAsInt());
    assertFalse(replayStage.get("screeningGradeMap").getAsBoolean());
    assertEquals("submitted", replayStage.get("mapProvenance").getAsString());
  }

  @Test
  void testAntiSurgeSystemReportsWrongTopologyType() {
    String json = "{" + "\"antiSurgeSystems\": [{\"name\": \"Invalid anti-surge\", \"stages\": [{"
        + "\"compressor\": \"Comp\", \"suctionMixer\": \"Not a mixer\","
        + "\"coldRecycleValve\": \"Cold ASV\", \"coldRecycle\": \"Cold Recycle\","
        + "\"generateScreeningMap\": true}]}],"
        + "\"fluid\": {\"model\": \"SRK\", \"temperature\": 298.15, \"pressure\": 20.0,"
        + "\"components\": {\"methane\": 1.0}}, \"process\": [" + "{\"type\": \"Stream\", \"name\": \"feed\","
        + "\"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "{\"type\": \"Compressor\", \"name\": \"Comp\", \"inlet\": \"feed\","
        + "\"properties\": {\"outletPressure\": [40.0, \"bara\"]}},"
        + "{\"type\": \"Cooler\", \"name\": \"Not a mixer\", \"inlet\": \"Comp.out\","
        + "\"properties\": {\"outTemperature\": [303.15, \"K\"]}},"
        + "{\"type\": \"ThrottlingValve\", \"name\": \"Cold ASV\", \"inlet\": \"Comp.out\","
        + "\"properties\": {\"outletPressure\": [20.0, \"bara\"]}},"
        + "{\"type\": \"Recycle\", \"name\": \"Cold Recycle\", \"inlet\": \"Cold ASV.out\"}]}";

    JsonObject root = JsonParser.parseString(ProcessRunner.run(json)).getAsJsonObject();
    JsonObject antiSurge = root.getAsJsonObject("antiSurgeSystems");
    JsonObject system = antiSurge.getAsJsonArray("systems").get(0).getAsJsonObject();
    JsonObject stage = system.getAsJsonArray("stages").get(0).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString(), root.toString());
    assertEquals(1, antiSurge.get("failedCount").getAsInt());
    assertEquals("failed", system.get("status").getAsString());
    assertTrue(stage.get("error").getAsString().contains("must be Mixer"));
  }

  @Test
  void testProcessModelAntiSurgeSystemUsesAreaQualification() {
    String json = processModelJson().substring(0, processModelJson().length() - 1) + ","
        + "\"antiSurgeSystems\": [{\"name\": \"Compression anti-surge\", \"area\": \"compression\","
        + "\"stages\": [{\"compressor\": \"Comp\", \"suctionMixer\": \"Comp suction\","
        + "\"coldRecycleValve\": \"Cold ASV\", \"coldRecycle\": \"Cold Recycle\","
        + "\"generateScreeningMap\": true}]}]}";
    JsonObject rootDefinition = JsonParser.parseString(json).getAsJsonObject();
    JsonObject compression = rootDefinition.getAsJsonObject("areas").getAsJsonObject("compression");
    JsonArray process = compression.getAsJsonArray("process");
    process.remove(1);
    process.add(JsonParser
        .parseString(
            "{\"type\": \"Mixer\", \"name\": \"Comp suction\"," + "\"inlets\": [\"compFeed\", \"Cold Recycle.out\"]}")
        .getAsJsonObject());
    process.add(JsonParser
        .parseString("{\"type\": \"Compressor\", \"name\": \"Comp\","
            + "\"inlet\": \"Comp suction.out\", \"properties\": {\"outletPressure\": [80.0, \"bara\"]}}")
        .getAsJsonObject());
    process
        .add(JsonParser
            .parseString("{\"type\": \"ThrottlingValve\", \"name\": \"Cold ASV\","
                + "\"inlet\": \"Comp.out\", \"properties\": {\"outletPressure\": [50.0, \"bara\"]}}")
            .getAsJsonObject());
    process.add(
        JsonParser.parseString("{\"type\": \"Recycle\", \"name\": \"Cold Recycle\"," + "\"inlet\": \"Cold ASV.out\"}")
            .getAsJsonObject());

    JsonObject root = JsonParser.parseString(ProcessRunner.run(rootDefinition.toString())).getAsJsonObject();
    JsonObject antiSurge = root.getAsJsonObject("antiSurgeSystems");
    JsonObject system = antiSurge.getAsJsonArray("systems").get(0).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString(), root.toString());
    assertEquals(1, antiSurge.get("configuredCount").getAsInt());
    assertEquals(1, antiSurge.get("generatedScreeningMapCount").getAsInt());
    assertEquals("compression", system.get("area").getAsString());
    assertEquals("configured", system.get("status").getAsString());
    assertTrue(root.getAsJsonObject("processDefinition").has("antiSurgeSystems"));
  }

  @Test
  void testProcessModelAntiSurgeSystemReportsUnknownArea() {
    String json = processModelJson().substring(0, processModelJson().length() - 1) + ","
        + "\"antiSurgeSystems\": [{\"name\": \"Unknown area anti-surge\", \"area\": \"missing\"," + "\"stages\": []}]}";

    JsonObject root = JsonParser.parseString(ProcessRunner.run(json)).getAsJsonObject();
    JsonObject antiSurge = root.getAsJsonObject("antiSurgeSystems");
    JsonObject system = antiSurge.getAsJsonArray("systems").get(0).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString(), root.toString());
    assertEquals(0, antiSurge.get("configuredCount").getAsInt());
    assertEquals(1, antiSurge.get("failedCount").getAsInt());
    assertEquals("failed", system.get("status").getAsString());
    assertTrue(system.get("error").getAsString().contains("does not exist"));
  }

  @Test
  void testAutoSizingPreservesAndCanOverwriteSeparatorGeometry() {
    String processJson = "{" + "\"autoSizing\": {\"enabled\": true}," + "\"fluid\": {" + "  \"model\": \"SRK\","
        + "  \"temperature\": 298.15," + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 1.0}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"Separator\", \"inlet\": \"feed\","
        + "   \"properties\": {\"internalDiameter\": 2.4, \"separatorLength\": 8.5}}" + "]" + "}";

    JsonObject preserved = JsonParser.parseString(ProcessRunner.run(processJson)).getAsJsonObject();
    JsonObject preservedSizing = preserved.getAsJsonObject("autoSizing");
    JsonObject preservedProperties = preserved.getAsJsonObject("processDefinition").getAsJsonArray("process").get(1)
        .getAsJsonObject().getAsJsonObject("properties");
    assertEquals("preserved",
        preservedSizing.getAsJsonArray("equipment").get(0).getAsJsonObject().get("status").getAsString());
    assertEquals("internalDiameter",
        preservedSizing.getAsJsonArray("equipment").get(0).getAsJsonObject().get("evidence").getAsString());
    assertEquals(2.4, preservedProperties.get("internalDiameter").getAsDouble(), 1.0e-12);
    assertEquals(8.5, preservedProperties.get("separatorLength").getAsDouble(), 1.0e-12);

    String overwriteJson = processJson.replace("\"enabled\": true", "\"enabled\": true, \"overwriteExplicit\": true");
    JsonObject overwritten = JsonParser.parseString(ProcessRunner.run(overwriteJson)).getAsJsonObject();
    JsonObject overwrittenSizing = overwritten.getAsJsonObject("autoSizing");
    JsonObject overwrittenProperties = overwritten.getAsJsonObject("processDefinition").getAsJsonArray("process").get(1)
        .getAsJsonObject().getAsJsonObject("properties");
    assertEquals("sized",
        overwrittenSizing.getAsJsonArray("equipment").get(0).getAsJsonObject().get("status").getAsString());
    assertTrue(Math.abs(overwrittenProperties.get("internalDiameter").getAsDouble() - 2.4) > 1.0e-6);

    String replaceJson = processJson.replace("\"enabled\": true", "\"enabled\": true, \"preserveExplicit\": false");
    JsonObject replaced = JsonParser.parseString(ProcessRunner.run(replaceJson)).getAsJsonObject();
    JsonObject replacedSizing = replaced.getAsJsonObject("autoSizing");
    JsonObject replacedProperties = replaced.getAsJsonObject("processDefinition").getAsJsonArray("process").get(1)
        .getAsJsonObject().getAsJsonObject("properties");
    assertEquals("sized",
        replacedSizing.getAsJsonArray("equipment").get(0).getAsJsonObject().get("status").getAsString());
    assertTrue(Math.abs(replacedProperties.get("internalDiameter").getAsDouble() - 2.4) > 1.0e-6);
  }

  @Test
  void testValidateAndRun_catalogMixerSplitterRecycleExample() {
    String result = ProcessRunner.validateAndRun(ExampleCatalog.processMixerSplitterRecycle());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString(), result);
    assertTrue(root.has("report"));
    assertFalse(result.contains("Unresolved inlet"), result);
  }

  @Test
  void testRun_processModelAreas() {
    String result = ProcessRunner.run(processModelJson());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertEquals("json-process-model", root.get("processModelName").getAsString());
    assertEquals(2, root.get("areaCount").getAsInt());
    assertTrue(root.has("areas"));
    assertTrue(root.has("report"));
    assertTrue(root.has("convergenceSummary"));
    assertTrue(root.get("convergenceSummary").getAsString().contains("ProcessModel"));
    assertTrue(root.get("pythonScript").getAsString().contains("ProcessRunner.validateAndRun(PROCESS_JSON)"));
  }

  @Test
  void testRun_processModelAutosizesEachAreaAndExportsLiveModel() {
    String json = processModelJson().replace("{\"areas\":", "{\"autoSizing\": {\"enabled\": true},\"areas\":").replace(
        "{\"type\": \"Separator\", \"name\": \"Sep\", \"inlet\": \"feed\"}",
        "{\"type\": \"Separator\", \"name\": \"Sep\", \"inlet\": \"feed\","
            + "\"properties\": {\"internalDiameter\": 2.4, \"separatorLength\": 8.5}}");

    JsonObject root = JsonParser.parseString(ProcessRunner.run(json)).getAsJsonObject();
    JsonObject autoSizing = root.getAsJsonObject("autoSizing");
    JsonObject processDefinition = root.getAsJsonObject("processDefinition");
    JsonArray separationUnits = processDefinition.getAsJsonObject("areas").getAsJsonObject("separation")
        .getAsJsonArray("process");
    JsonArray compressionUnits = processDefinition.getAsJsonObject("areas").getAsJsonObject("compression")
        .getAsJsonArray("process");

    assertEquals("success", root.get("status").getAsString());
    assertEquals(1, autoSizing.get("preservedCount").getAsInt());
    assertEquals(1, autoSizing.get("sizedCount").getAsInt());
    assertEquals("separation",
        autoSizing.getAsJsonArray("equipment").get(0).getAsJsonObject().get("area").getAsString());
    assertEquals("compression",
        autoSizing.getAsJsonArray("equipment").get(1).getAsJsonObject().get("area").getAsString());
    assertEquals(2.4,
        separationUnits.get(1).getAsJsonObject().getAsJsonObject("properties").get("internalDiameter").getAsDouble(),
        1.0e-12);
    JsonObject exportedCompressor = findProcessUnit(compressionUnits, "Comp");
    assertTrue(exportedCompressor.getAsJsonObject("properties").has("compressorChart"), processDefinition.toString());
    assertTrue(root.getAsJsonObject("designReport").getAsJsonObject("areas").has("separation"));
    assertTrue(root.getAsJsonObject("designReport").getAsJsonObject("areas").has("compression"));
    assertTrue(
        root.getAsJsonObject("utilizationSnapshot").getAsJsonArray("units").get(0).getAsJsonObject().has("area"));
    assertTrue(root.getAsJsonObject("data").has("bottleneckRanking"));
  }

  @Test
  void testRun_processModelHonorsExecutionSettings() {
    String json = processModelJson().replace("{\"areas\":",
        "{\"runStep\": true," + "\"maxIterations\": 7,\"flowTolerance\": 0.02,\"temperatureTolerance\": 0.03,"
            + "\"pressureTolerance\": 0.04,\"areas\":");

    String result = ProcessRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.getAsJsonObject("provenance").get("converged").getAsBoolean());
    assertTrue(root.get("convergenceSummary").getAsString().contains("Iterations: 1 / 7"));
    assertTrue(root.get("convergenceSummary").getAsString().contains("Flow rate:    0.00e+00"));
  }

  @Test
  void testValidateAndRun_processModelAreas() {
    String result = ProcessRunner.validateAndRun(processModelJson());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertEquals(2, root.get("areaCount").getAsInt());
  }

  @Test
  void testValidateAndRun_processWithExpander() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 60.0," + "  \"components\": {\"methane\": 1.0}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"," + "   \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Expander\", \"name\": \"Expander-1\", \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": [20.0, \"bara\"]}}" + "]" + "}";

    String result = ProcessRunner.validateAndRun(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testValidateAndRun_fromJsonFilePath(@TempDir Path tempDir) throws Exception {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}" + "]" + "}";
    Path file = tempDir.resolve("process_def.json");
    Files.write(file, json.getBytes(StandardCharsets.UTF_8));

    String result = ProcessRunner.validateAndRun(file.toAbsolutePath().toString());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.has("report"));
  }

  @Test
  void testRun_fromJsonFilePath(@TempDir Path tempDir) throws Exception {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 20.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"," + "   \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Compressor\", \"name\": \"Comp\", \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": [60.0, \"bara\"]}}" + "]" + "}";
    Path file = tempDir.resolve("compression.json");
    Files.write(file, json.getBytes(StandardCharsets.UTF_8));

    String result = ProcessRunner.run(file.toAbsolutePath().toString());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testValidateAndRun_missingJsonFilePath() {
    String result = ProcessRunner.validateAndRun("C:/does/not/exist/process_def.json");
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("INPUT_ERROR", root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testResolveJsonInput_inlineJsonUnchanged() {
    String inline = "{\"fluid\": {}}";
    assertEquals(inline, ProcessRunner.resolveJsonInput(inline));
  }

  @Test
  void testValidateAndRun_pseudoComponentFluidFromFilePath(@TempDir Path tempDir) throws Exception {
    // A fluid with database light ends plus a characterized (pseudo) heavy component
    // defined by Tc/Pc/acentricFactor/MW/density, run through the MCP runner by file path.
    // This locks the characterizedComponents fluid path together with .json file input,
    // which together let a UniSim/E300-derived pseudo-component fluid run via run_process.
    String json = "{" + "\"fluid\": {" + "  \"model\": \"PR\"," + "  \"temperature\": 313.15," + "  \"pressure\": 30.0,"
        + "  \"mixingRule\": \"classic\","
        + "  \"components\": {\"methane\": 0.6, \"ethane\": 0.05, \"propane\": 0.05, \"n-pentane\": 0.05},"
        + "  \"characterizedComponents\": [{" + "    \"name\": \"PC1\", \"moleFraction\": 0.25,"
        + "    \"molarMass\": 0.20, \"density\": 0.78, \"Tc\": 700.0, \"Pc\": 17.0,"
        + "    \"acentricFactor\": 0.72, \"isPlusFraction\": false}]" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"Well feed\","
        + "   \"properties\": {\"flowRate\": [100000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"Well feed\"},"
        + "  {\"type\": \"Compressor\"," + "   \"name\": \"Export compressor\", \"inlet\": \"HP Sep.gasOutStream\","
        + "   \"properties\": {\"outletPressure\": [120.0, \"bara\"]}}" + "]" + "}";
    Path file = tempDir.resolve("pseudo_component_process.json");
    Files.write(file, json.getBytes(StandardCharsets.UTF_8));

    String result = ProcessRunner.validateAndRun(file.toAbsolutePath().toString());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.has("report"));
  }

  private static String processModelJson() {
    String fluid = "\"fluid\": {" + "\"model\": \"SRK\"," + "\"temperature\": 298.15," + "\"pressure\": 50.0,"
        + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}";
    String separation = "{" + fluid + "," + "\"process\": [" + "{\"type\": \"Stream\", \"name\": \"feed\","
        + "\"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "{\"type\": \"Separator\", \"name\": \"Sep\", \"inlet\": \"feed\"}" + "]}";
    String compression = "{" + fluid + "," + "\"process\": [" + "{\"type\": \"Stream\", \"name\": \"compFeed\","
        + "\"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "{\"type\": \"Compressor\", \"name\": \"Comp\", \"inlet\": \"compFeed\","
        + "\"properties\": {\"outletPressure\": [80.0, \"bara\"]}}" + "]}";
    String interAreaLinks = "\"interAreaLinks\": [{\"sourceArea\": \"separation\","
        + "\"source\": \"Sep.gasOut\", \"targetArea\": \"compression\","
        + "\"targetUnit\": \"Comp\", \"targetInletIndex\": 0}]";
    return "{\"areas\": {\"separation\": " + separation + ", \"compression\": " + compression + "}," + interAreaLinks
        + "}";
  }

  /**
   * Finds a named process unit in exported canonical JSON.
   *
   * @param units exported process unit array
   * @param name required unit name
   * @return matching process unit
   * @throws AssertionError if no matching unit exists
   */
  private static JsonObject findProcessUnit(JsonArray units, String name) {
    for (int i = 0; i < units.size(); i++) {
      JsonObject unit = units.get(i).getAsJsonObject();
      if (name.equals(unit.get("name").getAsString())) {
        return unit;
      }
    }
    throw new AssertionError("Process unit not found: " + name);
  }
}
