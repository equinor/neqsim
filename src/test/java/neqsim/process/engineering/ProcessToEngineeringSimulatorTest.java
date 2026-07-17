package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.engineering.deliverables.EngineeringDeliverableCompiler;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.production.EngineeringAutoConfigurationPolicy;
import neqsim.process.engineering.production.EngineeringAutoConfigurator;
import neqsim.process.engineering.production.ProcessModelEngineeringSimulator;
import neqsim.process.engineering.production.EngineeringSharedSystemPolicy;
import neqsim.process.engineering.production.ProcessModelEngineeringPackageValidator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end test of the inlet-separation, compression, and export engineering slice. */
class ProcessToEngineeringSimulatorTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void designsACompleteInletCompressionExportSlice() throws Exception {
    ProcessSystem process = process();
    double originalLineDiameter = ((AdiabaticPipe) process.getUnit("EXPORT-LINE")).getDiameter();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Process-to-engineering test", process)
        .projectId("pte-test").build();
    project.addDesignCase(flowCase("normal", 8000.0, 10));
    project.addDesignCase(flowCase("maximum", 12000.0, 20));
    EngineeringAutoConfigurationPolicy policy = new EngineeringAutoConfigurationPolicy("offshore-gas", "A")
        .addInletCompressionExportSlice("INLET-SEP", "EXPORT-COMP", "EXPORT-LINE", "", "PIT-100", 800.0, 0.107, 120.0,
            25.0, 5.0, 0.10, 500.0, 1000.0, 2000.0, 3000.0, 5000.0, 7500.0, 10000.0);
    EngineeringSimulationResult result = ProcessToEngineeringSimulator.run(project, policy, 2);
    assertTrue(project.getProductionReadinessBasis().getAutoConfigurationResult().isComplete());
    assertTrue(project.getProductionReadinessBasis().getAutoConfigurationResult().isExecutionReady());
    assertFalse(project.getProductionReadinessBasis().getAutoConfigurationResult().getModuleDependencies().isEmpty());
    assertFalse(project.getProductionReadinessBasis().getAutoConfigurationResult().getEquipmentInventory().isEmpty());
    assertEquals(64,
        project.getProductionReadinessBasis().getAutoConfigurationResult().getConfigurationFingerprint().length());
    EngineeringAutoConfigurator.Result hiddenDefaultConfiguration = EngineeringAutoConfigurator.configure(project,
        new EngineeringAutoConfigurationPolicy("legacy-policy", "A").addInletCompressionExportSlice("INLET-SEP",
            "EXPORT-COMP", "EXPORT-LINE", "", "PIT-100"));
    assertFalse(hiddenDefaultConfiguration.isComplete());
    assertEquals("CONFIGURATION_CHANGED", hiddenDefaultConfiguration
        .compareWith(project.getProductionReadinessBasis().getAutoConfigurationResult()).getStatus());
    assertFalse(
        hiddenDefaultConfiguration.compareWith(project.getProductionReadinessBasis().getAutoConfigurationResult())
            .getInvalidatedArtifacts().isEmpty());

    assertNotNull(result.getEngineeringDesignLoopResult());
    assertTrue(result.getEngineeringDesignLoopResult().isConverged());
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("INLET-SEP.insideDiameter"));
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("EXPORT-COMP.driverRatedPower"));
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("EXPORT-LINE.insideDiameter"));
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("PIT-100.upperRangeValue"));
    assertEquals(1.01325,
        result.getEngineeringDesignLoopResult().getState().requireValue("INLET-SEP.designPressure")
            - result.getEngineeringDesignLoopResult().getState().requireValue("INLET-SEP.proposedPsvSetPressure"),
        1.0e-9);
    assertTrue(result.toJson().contains("preliminaryMaterialClass"));
    assertEquals(originalLineDiameter, ((AdiabaticPipe) process.getUnit("EXPORT-LINE")).getDiameter(), 1.0e-12);

    EngineeringDeliverableCompiler.CompilationResult compilation = EngineeringDeliverableCompiler.compile(project,
        temporaryDirectory);
    assertTrue(Files.isRegularFile(compilation.getProductionReadinessFile()));
    assertTrue(Files.isRegularFile(compilation.getQualificationPlanFile()));
    assertTrue(Files.isRegularFile(temporaryDirectory.resolve("engineering-discipline-orchestration.json")));
    String[] coordinatedArtifacts = new String[] { "process-design-basis.json", "equipment-datasheets.json",
        "valve-list.json", "io-list.json", "alarm-trip-schedule.json", "shutdown-narratives.json",
        "psv-datasheets.json", "flare-blowdown-report.json", "utility-summary.json", "materials-selection-report.json",
        "engineering-diagram-layout.json", "unresolved-engineering-actions.json", "revision-impact-report.json",
        "engineering-production-readiness.json", "engineering-qualification-plan.json" };
    for (String artifact : coordinatedArtifacts) {
      assertTrue(Files.exists(temporaryDirectory.resolve(artifact)), artifact);
    }
    String nativeDexpi = new String(Files.readAllBytes(temporaryDirectory.resolve("plant.dexpi.xml")),
        StandardCharsets.UTF_8);
    assertTrue(nativeDexpi.contains("NeqSim governed PFD"));
    assertTrue(nativeDexpi.contains("NeqSim governed P&amp;ID") || nativeDexpi.contains("NeqSim governed P&ID"));
    String dexpiValidation = new String(Files.readAllBytes(temporaryDirectory.resolve("dexpi-validation.json")),
        StandardCharsets.UTF_8);
    assertTrue(dexpiValidation.contains("\"valid\": true"));
    String packageManifest = new String(Files.readAllBytes(temporaryDirectory.resolve("package-manifest.json")),
        StandardCharsets.UTF_8);
    assertTrue(packageManifest.contains("equipment-datasheets.json"));
    assertTrue(packageManifest.contains("engineering-validation-report.json"));
    String productionReadiness = new String(
        Files.readAllBytes(temporaryDirectory.resolve("engineering-production-readiness.json")),
        StandardCharsets.UTF_8);
    assertTrue(productionReadiness.contains("\"fitnessForConstruction\": false"));
    String qualificationPlan = new String(
        Files.readAllBytes(temporaryDirectory.resolve("engineering-qualification-plan.json")), StandardCharsets.UTF_8);
    assertTrue(qualificationPlan.contains("\"externalEvidenceMayNotBeGeneratedBySimulator\": true"));
    assertTrue(qualificationPlan.contains("\"METHOD_BENCHMARK\""));
  }

  @Test
  void failsClosedForImplicitDefaultsAndMissingAreaPolicies() {
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Fail closed", process()).build();
    project.addDesignCase(flowCase("normal", 8000.0, 10));
    EngineeringAutoConfigurationPolicy implicit = new EngineeringAutoConfigurationPolicy("legacy", "A")
        .addInletCompressionExportSlice("INLET-SEP", "EXPORT-COMP", "EXPORT-LINE", "", "PIT-100");
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> ProcessToEngineeringSimulator.run(project, implicit));
    assertTrue(exception.getMessage().contains("HIDDEN_SCREENING_DEFAULTS_USED"));

    ProcessModel model = new ProcessModel();
    model.add("compression", process());
    ProcessModelEngineeringSimulator.Result result = ProcessModelEngineeringSimulator.run("multi-area", model,
        Collections.<String, ProcessModelEngineeringSimulator.AreaConfiguration>emptyMap(), 1);
    assertFalse(result.isComplete());
    assertTrue(result.getBlockers().contains("MISSING_AREA_CONFIGURATION:compression"));
  }

  @Test
  void reusesUnchangedAreaAndValidatesCoordinatedManifest() {
    ProcessModel model = new ProcessModel();
    model.add("compression", process());
    EngineeringAutoConfigurationPolicy policy = new EngineeringAutoConfigurationPolicy("offshore-gas", "A")
        .addInletCompressionExportSlice("INLET-SEP", "EXPORT-COMP", "EXPORT-LINE", "", "PIT-100", 800.0, 0.107, 120.0,
            25.0, 5.0, 0.10, 500.0, 1000.0, 2000.0, 3000.0, 5000.0, 7500.0, 10000.0);
    Map<String, ProcessModelEngineeringSimulator.AreaConfiguration> configurations = new LinkedHashMap<String, ProcessModelEngineeringSimulator.AreaConfiguration>();
    configurations.put("compression", new ProcessModelEngineeringSimulator.AreaConfiguration(policy)
        .addDesignCase(flowCase("normal", 8000.0, 10)).addDesignCase(flowCase("maximum", 12000.0, 20)));
    EngineeringSharedSystemPolicy coordination = new EngineeringSharedSystemPolicy("plant-coordination", "A");

    ProcessModelEngineeringSimulator.Result initial = ProcessModelEngineeringSimulator.run("incremental", model,
        configurations, coordination, 1);
    assertTrue(initial.isComplete());
    assertTrue(initial.getExecutedAreas().contains("compression"));
    ProcessModelEngineeringSimulator.Result unchanged = ProcessModelEngineeringSimulator.runIncremental("incremental",
        model, configurations, coordination, initial, 1);
    assertTrue(unchanged.isComplete());
    assertTrue(unchanged.getExecutedAreas().isEmpty());
    assertTrue(unchanged.getReusedAreas().contains("compression"));
    assertEquals(initial.getFingerprint(), unchanged.getFingerprint());

    Map<String, Object> manifest = new LinkedHashMap<String, Object>(unchanged.toMap());
    manifest.put("areaPackages",
        Collections.singletonMap("compression", Collections.singletonMap("directory", "compression")));
    assertTrue(ProcessModelEngineeringPackageValidator.validate(manifest).isEmpty());
  }

  private EngineeringDesignCase flowCase(String id, final double flowKgHr, int priority) {
    return new EngineeringDesignCase(id, id, EngineeringDesignCase.Type.CUSTOM,
        new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setFlowRate(flowKgHr, "kg/hr");
          }
        }).setPriority(priority).addInput(new EngineeringDesignCase.Input("feedFlow", flowKgHr, "kg/hr", "DB-1"));
  }

  private ProcessSystem process() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("n-heptane", 0.04);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(8000.0, "kg/hr");
    Separator separator = new Separator("INLET-SEP", feed);
    Compressor compressor = new Compressor("EXPORT-COMP", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setPolytropicEfficiency(0.78);
    AdiabaticPipe exportLine = new AdiabaticPipe("EXPORT-LINE", compressor.getOutletStream());
    exportLine.setLength(1000.0);
    exportLine.setDiameter(0.2027);
    exportLine.setPipeWallRoughness(4.6e-5);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(exportLine);
    process.run();
    return process;
  }
}
