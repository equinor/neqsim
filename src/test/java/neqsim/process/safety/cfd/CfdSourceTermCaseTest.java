package neqsim.process.safety.cfd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.BoundaryConditions;
import neqsim.process.safety.cfd.CfdSourceTermCase.ValidationResult;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator.ReleaseCase;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator.ReleaseDispersionScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the formal CFD source-term handoff schema and exporter.
 */
class CfdSourceTermCaseTest {

  @TempDir
  Path tempDir;

  @Test
  void cfdCaseFromScenarioContainsRequiredSchemaSections() {
    CfdSourceTermCase sourceTermCase = createCfdCase();

    ValidationResult validation = sourceTermCase.validate();
    JsonObject json = JsonParser.parseString(sourceTermCase.toJson()).getAsJsonObject();

    assertTrue(validation.isValid(), validation.toJson());
    assertFalse(sourceTermCase.getQualityWarnings().isEmpty());
    assertEquals(CfdSourceTermCase.SCHEMA_VERSION, json.get("schemaVersion").getAsString());
    assertTrue(json.has("context"));
    assertTrue(json.has("fluid"));
    assertTrue(json.has("release"));
    assertTrue(json.has("sourceTerm"));
    assertTrue(json.has("ambient"));
    assertTrue(json.has("provenance"));
    assertTrue(json.getAsJsonObject("provenance").get("notForFinalLayoutWithoutValidation")
        .getAsBoolean());
    assertTrue(json.getAsJsonArray("consequenceBranches").size() >= 5);
    assertTrue(json.getAsJsonObject("sourceTerm").getAsJsonArray("timeSeries").size() > 1);
  }

  @Test
  void exporterWritesNeutralJsonManifestAndOpenFoamSkeleton() throws Exception {
    CfdSourceTermCase sourceTermCase = createCfdCase();
    CfdSourceTermExporter exporter = new CfdSourceTermExporter();

    Path jsonFile = tempDir.resolve("case.json");
    Path manifestFile = tempDir.resolve("manifest.json");
    Path openFoamRoot = tempDir.resolve("openfoam-case");

    exporter.exportJson(sourceTermCase, jsonFile.toString());
    exporter.exportManifest(java.util.Collections.singletonList(sourceTermCase),
        manifestFile.toString());
    exporter.exportOpenFoamSkeleton(sourceTermCase, openFoamRoot.toString());

    assertTrue(Files.exists(jsonFile));
    assertTrue(Files.exists(manifestFile));
    assertTrue(Files.exists(openFoamRoot.resolve("case.json")));
    assertTrue(Files.exists(openFoamRoot.resolve("constant").resolve("releaseSourceProperties")));
    assertTrue(Files.exists(
        openFoamRoot.resolve("constant").resolve("sourceTimeSeries").resolve("massFlowRate")));

    String releaseSource = new String(
        Files.readAllBytes(openFoamRoot.resolve("constant").resolve("releaseSourceProperties")),
        StandardCharsets.UTF_8);
    String manifest = new String(Files.readAllBytes(manifestFile), StandardCharsets.UTF_8);

    assertTrue(releaseSource.contains("tabulatedMassMomentumTemperature"));
    assertTrue(manifest.contains("caseCount"));
    assertTrue(exporter.toJson(sourceTermCase).contains(CfdSourceTermCase.SCHEMA_VERSION));
  }

  private static CfdSourceTermCase createCfdCase() {
    ProcessSystem process = createProcess();
    List<ReleaseDispersionScenario> scenarios = new ReleaseDispersionScenarioGenerator(process)
        .releaseCases(ReleaseCase.TEN_MM_HOLE).addWeatherCase("neutral-D", standardWeather())
        .releaseDuration(20.0, 5.0).generateScenarios();
    return scenarios.get(0).toCfdSourceTermCase();
  }

  private static BoundaryConditions standardWeather() {
    return BoundaryConditions.builder().ambientTemperature(15.0, "C").windSpeed(5.0)
        .pasquillStabilityClass('D').isOffshore(false).surfaceRoughness(0.1).build();
  }

  private static ProcessSystem createProcess() {
    Stream feed = new Stream("export gas", createLeanGas());
    feed.setFlowRate(500.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(55.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.setName("cfd source term test process");
    process.add(feed);
    process.run();
    return process;
  }

  private static SystemInterface createLeanGas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 55.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
