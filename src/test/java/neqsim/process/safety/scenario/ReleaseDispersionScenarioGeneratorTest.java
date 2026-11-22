package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.safety.release.SourceTermResult;
import neqsim.process.safety.cfd.CfdSourceTermExporter;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.BoundaryConditions;
import neqsim.process.safety.cfd.CfdSourceTermCase;
import neqsim.process.safety.inventory.TrappedInventoryCalculator;
import neqsim.process.safety.inventory.TrappedInventoryCalculator.InventoryResult;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator.ReleaseCase;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator.ReleaseDispersionScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests automatic release source-term and gas dispersion scenario generation.
 */
class ReleaseDispersionScenarioGeneratorTest {

  @Test
  void generatedScenariosIncludeSourceTermAndFlammableEndpoint() {
    ProcessSystem process = createGasProcess(false);

    List<ReleaseDispersionScenario> scenarios = new ReleaseDispersionScenarioGenerator(process)
        .boundaryConditions(standardWeather()).holeDiameter(50.0, "mm").inventoryVolume(3.0).releaseDuration(0.5, 0.1)
        .generateScenarios();

    assertFalse(scenarios.isEmpty());
    ReleaseDispersionScenario scenario = findFlammableScenario(scenarios);
    assertNotNull(scenario.getScenarioName());
    assertNotNull(scenario.getEquipmentName());
    assertNotNull(scenario.getStreamName());
    assertTrue(scenario.getStreamPressureBara() > 1.0);
    assertTrue(scenario.getStreamMassFlowRateKgPerS() > 0.0);
    assertEquals(0.05, scenario.getHoleDiameterM(), 1.0e-12);
    assertEquals(3.0, scenario.getInventoryVolumeM3(), 1.0e-12);
    assertEquals(0.5, scenario.getReleaseDurationSeconds(), 1.0e-12);
    assertTrue(scenario.getSourceTerm().getPeakMassFlowRate() > 0.0);
    assertTrue(scenario.getDispersionResult().getDistanceToLflM() > 0.0);
    assertTrue(scenario.hasFlammableCloud());
    assertTrue(scenario.toJson().contains("dispersionResult"));
  }

  @Test
  void toxicEndpointConfigurationIsAppliedToGeneratedScenarios() {
    ProcessSystem process = createGasProcess(true);

    List<ReleaseDispersionScenario> scenarios = new ReleaseDispersionScenarioGenerator(process)
        .boundaryConditions(standardWeather()).holeDiameter(10.0, "mm").inventoryVolume(2.0).releaseDuration(20.0, 5.0)
        .toxicEndpoint("H2S", 100.0).generateScenarios();

    assertFalse(scenarios.isEmpty());
    ReleaseDispersionScenario scenario = findToxicScenario(scenarios);
    assertEquals("H2S", scenario.getDispersionResult().getToxicComponentName());
    assertTrue(scenario.getDispersionResult().getToxicDistanceM() > 0.0);
  }

  @Test
  void minimumPressureFiltersLowPressureStreams() {
    ProcessSystem process = createGasProcess(false);

    List<ReleaseDispersionScenario> scenarios = new ReleaseDispersionScenarioGenerator(process).minimumPressure(200.0)
        .generateScenarios();

    assertTrue(scenarios.isEmpty());
  }

  @Test
  void emptyProcessReturnsNoScenarios() {
    ProcessSystem process = new ProcessSystem();
    process.setName("empty");

    List<ReleaseDispersionScenario> scenarios = new ReleaseDispersionScenarioGenerator(process).generateScenarios();

    assertTrue(scenarios.isEmpty());
  }

  @Test
  void invalidConfigurationIsRejected() {
    ProcessSystem process = createGasProcess(false);
    ReleaseDispersionScenarioGenerator generator = new ReleaseDispersionScenarioGenerator(process);

    assertThrows(IllegalArgumentException.class, () -> generator.holeDiameter(0.0));
    assertThrows(IllegalArgumentException.class, () -> generator.inventoryVolume(0.0));
    assertThrows(IllegalArgumentException.class, () -> generator.releaseDuration(0.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> generator.releaseDuration(1.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> generator.releaseHeight(-1.0));
    assertThrows(IllegalArgumentException.class, () -> generator.minimumMassFlowRate(-1.0));
    assertThrows(IllegalArgumentException.class, () -> generator.backPressure(0.0));
    assertThrows(IllegalArgumentException.class, () -> generator.toxicEndpoint("H2S", 0.0));
  }

  @Test
  void documentationExampleBuildsReleaseDispersionScenarios() {
    ProcessSystem process = createGasProcess(false);
    BoundaryConditions weather = BoundaryConditions.northSeaWinter();

    ReleaseDispersionScenarioGenerator generator = new ReleaseDispersionScenarioGenerator(process)
        .boundaryConditions(weather).holeDiameter(10.0, "mm").inventoryVolume(5.0).releaseDuration(60.0, 5.0);

    List<ReleaseDispersionScenario> scenarios = generator.generateScenarios();

    assertFalse(scenarios.isEmpty());
    ReleaseDispersionScenario firstScenario = scenarios.get(0);

    double peakReleaseRate = firstScenario.getSourceTerm().getPeakMassFlowRate();
    double distanceToLfl = firstScenario.getDispersionResult().getDistanceToLflM();
    String scenarioJson = firstScenario.toJson();

    assertTrue(peakReleaseRate > 0.0);
    assertTrue(Double.isNaN(distanceToLfl) || distanceToLfl >= 0.0);
    assertTrue(scenarioJson.contains("dispersionResult"));
    assertTrue(firstScenario.getDispersionResult().getMassReleaseRateKgPerS() > 0.0);
  }

  @Test
  void releaseTaxonomyAndWeatherEnvelopeGenerateMatrixMetadata() {
    ProcessSystem process = createSingleStreamProcess();

    // The metadata fixture stays within its initial gas regime; condensation is tested separately.
    List<ReleaseDispersionScenario> scenarios = new ReleaseDispersionScenarioGenerator(process)
        .releaseCases(ReleaseCase.FIVE_MM_HOLE, ReleaseCase.FULL_BORE_RUPTURE).fullBoreDiameter(150.0, "mm")
        .addWeatherCase("stable-D", standardWeather()).releaseDuration(0.05, 0.01).generateScenarios();

    assertEquals(2, scenarios.size());
    assertEquals("5 mm process leak", scenarios.get(0).getReleaseCaseName());
    assertEquals("hole-size", scenarios.get(0).getReleaseCaseCategory());
    assertEquals("stable-D", scenarios.get(0).getWeatherCaseName());
    assertFalse(scenarios.get(0).getConsequenceBranches().isEmpty());
    assertTrue(scenarios.get(0).getComponentMoleFractions().containsKey("methane"));
    assertEquals(0.15, scenarios.get(1).getHoleDiameterM(), 1.0e-12);
  }

  @Test
  void trappedInventoryIsUsedInScenariosAndCfdCases() {
    ProcessSystem process = createSingleStreamProcess();
    InventoryResult inventory = new TrappedInventoryCalculator().setFluid(createLeanGas())
        .setOperatingConditions(55.0, "bara", 25.0, "C").addEquipmentVolume("V-101", 4.0, 0.0, null).calculate();

    ReleaseDispersionScenarioGenerator generator = new ReleaseDispersionScenarioGenerator(process)
        .trappedInventory(inventory).releaseCases(ReleaseCase.TEN_MM_HOLE)
        .addWeatherCase("neutral-D", standardWeather()).releaseDuration(20.0, 5.0);

    List<ReleaseDispersionScenario> scenarios = generator.generateScenarios();
    List<CfdSourceTermCase> cfdCases = generator.generateCfdSourceTermCases();

    assertEquals(1, scenarios.size());
    assertEquals(inventory.getTotalGasVolumeM3(), scenarios.get(0).getInventoryVolumeM3(), 1.0e-12);
    assertNotNull(scenarios.get(0).getTrappedInventoryResult());
    assertEquals(1, cfdCases.size());
    assertTrue(cfdCases.get(0).validate().isValid());
    assertTrue(cfdCases.get(0).toJson().contains("TrappedInventoryCalculator"));
  }

  @Test
  void condensingReleaseFailsInsteadOfExportingAnUnsupportedTrajectory() {
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> new ReleaseDispersionScenarioGenerator(createGasProcess(false)).boundaryConditions(standardWeather())
            .holeDiameter(50.0, "mm").inventoryVolume(3.0).releaseDuration(30.0, 5.0).generateCfdSourceTermCases());
    assertTrue(failure.getMessage().contains("BLOWDOWN_PHASE_BOUNDARY"), failure.getMessage());
  }

  @Test
  void generatorAndJsonExportsPreserveExactFinalTimeAndConservativeMass(@TempDir Path directory) throws Exception {
    SystemInterface nitrogen = new SystemSrkEos(300.0, 10.0);
    nitrogen.addComponent("nitrogen", 1.0);
    nitrogen.setMixingRule("classic");
    Stream stream = new Stream("nitrogen", nitrogen);
    stream.setFlowRate(1.0, "kg/sec");
    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.run();
    ReleaseDispersionScenarioGenerator generator = new ReleaseDispersionScenarioGenerator(process).inventoryVolume(1.0)
        .holeDiameter(0.01).releaseDuration(2.5, 1.0);
    SourceTermResult source = generator.generateScenarios().get(0).getSourceTerm();
    assertEquals(2.5, source.getTime()[3], 0.0);
    assertEquals(source.getInitialInventory().getMass("kg") - source.getFinalInventory().getMass("kg"),
        source.getTotalMassReleased(), 1e-10);
    Path legacyFile = directory.resolve("source.json");
    source.exportToJSON(legacyFile.toString());
    JsonObject legacy = JsonParser.parseString(new String(Files.readAllBytes(legacyFile), StandardCharsets.UTF_8))
        .getAsJsonObject();
    assertEquals(2.5, legacy.getAsJsonArray("timeSeries").get(3).getAsJsonObject().get("t").getAsDouble(), 0.0);
    assertEquals(source.getTotalMassReleased(), legacy.get("totalMassReleased_kg").getAsDouble(), 1e-12);
    CfdSourceTermCase cfd = generator.generateCfdSourceTermCases().get(0);
    assertTrue(cfd.validate().isValid());
    Path cfdFile = directory.resolve("cfd.json");
    new CfdSourceTermExporter().exportJson(cfd, cfdFile.toString());
    JsonObject exported = JsonParser.parseString(new String(Files.readAllBytes(cfdFile), StandardCharsets.UTF_8))
        .getAsJsonObject().getAsJsonObject("sourceTerm");
    JsonArray rows = exported.getAsJsonArray("timeSeries");
    assertEquals(4, rows.size());
    assertEquals(2.5, rows.get(3).getAsJsonObject().get("timeS").getAsDouble(), 0.0);
    assertEquals(source.getTotalMassReleased(), exported.get("totalMassReleasedKg").getAsDouble(), 1e-10);
    assertEquals(source.getPressure()[3], rows.get(3).getAsJsonObject().get("pressurePa").getAsDouble(), 1e-6);
  }

  private static BoundaryConditions standardWeather() {
    return BoundaryConditions.builder().ambientTemperature(15.0, "C").windSpeed(5.0).pasquillStabilityClass('D')
        .isOffshore(false).surfaceRoughness(0.1).build();
  }

  private static ReleaseDispersionScenario findFlammableScenario(List<ReleaseDispersionScenario> scenarios) {
    for (ReleaseDispersionScenario scenario : scenarios) {
      if (scenario.hasFlammableCloud()) {
        return scenario;
      }
    }
    throw new AssertionError(
        "Expected at least one flammable release-dispersion scenario: " + summarizeScenarios(scenarios));
  }

  private static ReleaseDispersionScenario findToxicScenario(List<ReleaseDispersionScenario> scenarios) {
    for (ReleaseDispersionScenario scenario : scenarios) {
      if (scenario.hasToxicEndpoint()) {
        return scenario;
      }
    }
    throw new AssertionError(
        "Expected at least one toxic release-dispersion scenario: " + summarizeScenarios(scenarios));
  }

  private static String summarizeScenarios(List<ReleaseDispersionScenario> scenarios) {
    StringBuilder summary = new StringBuilder();
    for (ReleaseDispersionScenario scenario : scenarios) {
      summary.append(scenario.toJson()).append('\n');
    }
    return summary.toString();
  }

  private static ProcessSystem createGasProcess(boolean sourGas) {
    SystemInterface fluid = new SystemSrkEos(298.15, 55.0);
    fluid.addComponent("methane", sourGas ? 0.96 : 0.90);
    fluid.addComponent("ethane", sourGas ? 0.02 : 0.10);
    if (sourGas) {
      fluid.addComponent("H2S", 0.02);
    }
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed gas", fluid);
    feed.setFlowRate(500.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(55.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("inlet valve", feed);
    valve.setOutletPressure(40.0, "bara");

    Separator separator = new Separator("hp separator", valve.getOutletStream());

    Cooler cooler = new Cooler("export cooler", separator.getGasOutStream());
    cooler.setOutTemperature(20.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.setName("release dispersion test process");
    process.add(feed);
    process.add(valve);
    process.add(separator);
    process.add(cooler);
    process.run();
    return process;
  }

  private static ProcessSystem createSingleStreamProcess() {
    Stream feed = new Stream("export gas", createLeanGas());
    feed.setFlowRate(500.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(55.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.setName("single stream process");
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
