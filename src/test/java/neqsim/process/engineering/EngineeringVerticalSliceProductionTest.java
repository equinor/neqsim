package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import neqsim.process.engineering.deliverables.EngineeringDeliverableCompiler;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.production.EngineeringAutoConfigurationPolicy;
import neqsim.process.engineering.verticalslice.InletCompressionExportSlicePolicy;
import neqsim.process.engineering.verticalslice.ProductionVerticalSliceSimulator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.SafeSplineStoneWallCurve;
import neqsim.process.equipment.compressor.SafeSplineSurgeCurve;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression coverage for the production vertical-slice map envelope and fail-closed qualification. */
class EngineeringVerticalSliceProductionTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void calculatesCompressorMapEnvelopeAcrossDesignCases() {
    ProcessSystem process = chartedProcess();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Compressor envelope", process)
        .projectId("compressor-envelope").build();
    project.addDesignCase(flowCase("normal", EngineeringDesignCase.Type.NORMAL, 8000.0));
    project.addDesignCase(flowCase("maximum", EngineeringDesignCase.Type.MAXIMUM_PRODUCTION, 10000.0));
    ProcessToEngineeringDesignBuilder builder = ProcessToEngineeringDesignBuilder.on(project)
        .separatorBasis(800.0, 0.107, 120.0).exportLineLimits(25.0, 5.0)
        .compressorDrivers(0.10, 500.0, 1000.0, 2000.0, 5000.0, 10000.0);
    builder.addInletCompressionExportSlice("INLET-SEP", "EXPORT-COMP", "EXPORT-LINE", "", "PIT-100");
    builder.addCompressorOperatingEnvelopeDesign("EXPORT-COMP", 0.05, 0.05, 160.0, 0.10);

    EngineeringSimulationResult result = ProcessToEngineeringSimulator.run(project, 1);

    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("EXPORT-COMP.minimumSurgeMargin"));
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("EXPORT-COMP.minimumStonewallMargin"));
    assertTrue(
        result.getEngineeringDesignLoopResult().getState().contains("EXPORT-COMP.maximumRequiredRecycleFraction"));
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("EXPORT-COMP.maximumRecycleCoolerDuty"));
    assertTrue(result.toJson().contains("operatingPoints"));
  }

  @Test
  void failsClosedAndWritesQualificationArtifactWhenProductionTopologyIsIncomplete() throws Exception {
    ProcessSystem process = chartedProcess();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Incomplete production slice", process)
        .projectId("incomplete-slice").build();
    project.addDesignCase(flowCase("normal", EngineeringDesignCase.Type.NORMAL, 8000.0));
    project.addDesignCase(flowCase("maximum", EngineeringDesignCase.Type.MAXIMUM_PRODUCTION, 10000.0));
    EngineeringAutoConfigurationPolicy autoPolicy = new EngineeringAutoConfigurationPolicy("explicit", "A")
        .addInletCompressionExportSlice("INLET-SEP", "EXPORT-COMP", "EXPORT-LINE", "", "PIT-100", 800.0, 0.107,
            120.0, 25.0, 5.0, 0.10, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)
        .addCompressorOperatingEnvelope("EXPORT-COMP", 0.05, 0.05, 160.0, 0.10);
    InletCompressionExportSlicePolicy qualificationPolicy = InletCompressionExportSlicePolicy
        .builder("production-slice", "A").processTags("INLET-SEP", "EXPORT-COMP", "AFTERCOOLER", "EXPORT-LINE")
        .controlTags("ASV", "ASV-RECYCLE", "PCV", "LCV")
        .safetyTags("PSV", "BDV", "ESDV-SUCTION", "ESDV-DISCHARGE", "FLARE-CONNECTION")
        .addRequiredDynamicScenario("compressor-trip-esd").addEvidenceReference("HAZOP-REQUIRED").build();

    ProductionVerticalSliceSimulator.Result result = ProductionVerticalSliceSimulator.run(project, autoPolicy,
        qualificationPolicy, 1);

    assertFalse(result.getQualification().isQualifiedForControlledPilot());
    assertTrue(result.getQualification().getFailedGates().contains("COMPLETE_PROCESS_AND_SAFETY_TOPOLOGY"));
    assertTrue(result.getQualification().getFailedGates().contains("COMPLETE_CONVERGED_CASE_MATRIX"));
    assertTrue(result.getQualification().getFailedGates().contains("DYNAMIC_SAFE_STATE_VERIFICATION"));
    EngineeringDeliverableCompiler.compile(project, temporaryDirectory);
    Path artifact = temporaryDirectory.resolve("engineering-vertical-slice-qualification.json");
    assertTrue(Files.isRegularFile(artifact));
    String json = new String(Files.readAllBytes(artifact), StandardCharsets.UTF_8);
    assertTrue(json.contains("qualifiedForControlledPilot"));
    assertTrue(json.contains("fitnessForConstruction"));
  }

  private EngineeringDesignCase flowCase(String id, EngineeringDesignCase.Type type, final double flowKgHr) {
    return new EngineeringDesignCase(id, id, type, new EngineeringDesignCase.Configurator() {
      private static final long serialVersionUID = 1000L;

      @Override
      public void configure(ProcessSystem process) {
        ((Stream) process.getUnit("FEED")).setFlowRate(flowKgHr, "kg/hr");
      }
    }).addInput(new EngineeringDesignCase.Input("feedFlow", flowKgHr, "kg/hr", "DESIGN-BASIS-A"))
        .addEvidenceReference("DESIGN-BASIS-A");
  }

  private ProcessSystem chartedProcess() {
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
    compressor.setSpeed(10000.0);
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

    double[] conditions = new double[] { 19.0, 300.0, 50.0, 0.90 };
    double[] speeds = new double[] { 9000.0, 11000.0 };
    double[][] flows = new double[][] { { 50.0, 100.0, 150.0 }, { 70.0, 140.0, 210.0 } };
    double[][] heads = new double[][] { { 75.0, 65.0, 50.0 }, { 110.0, 95.0, 72.0 } };
    double[][] efficiencies = new double[][] { { 70.0, 79.0, 73.0 }, { 71.0, 80.0, 74.0 } };
    compressor.getCompressorChart().setCurves(conditions, speeds, flows, heads, efficiencies);
    compressor.getCompressorChart().setSurgeCurve(
        new SafeSplineSurgeCurve(new double[] { 42.0, 55.0, 70.0 }, new double[] { 110.0, 90.0, 65.0 }));
    compressor.getCompressorChart().setStoneWallCurve(
        new SafeSplineStoneWallCurve(new double[] { 170.0, 195.0, 220.0 }, new double[] { 110.0, 90.0, 65.0 }));
    compressor.getCompressorChart().setHeadUnit("kJ/kg");
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.getAntiSurge().setActive(true);
    return process;
  }
}
