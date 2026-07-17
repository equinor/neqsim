package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import neqsim.process.engineering.production.EngineeringAutoConfigurator;
import neqsim.process.engineering.verticalslice.InletCompressionExportReferenceFacility;
import neqsim.process.engineering.verticalslice.InletCompressionExportReferenceFacility.Definition;
import neqsim.process.engineering.verticalslice.ProductionVerticalSlicePreflight;
import neqsim.process.engineering.verticalslice.ProductionVerticalSliceSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end acceptance tests for the executable process-to-engineering reference facility. */
class QualifiedEngineeringReferenceFacilityTest {
  @TempDir
  Path outputDirectory;

  @Test
  void buildsConnectedExecutionReadyReferenceFacility() {
    Definition definition = InletCompressionExportReferenceFacility.build();

    ProductionVerticalSlicePreflight.Result preflight = ProductionVerticalSlicePreflight
        .assess(definition.getProject(), definition.getQualificationPolicy());
    EngineeringAutoConfigurator.Result configuration = EngineeringAutoConfigurator.configure(definition.getProject(),
        definition.getAutoConfigurationPolicy());

    assertTrue(preflight.isReadyForSimulation(), preflight.getBlockers().toString());
    assertTrue(configuration.isExecutionReady(), configuration.getExecutionBlockers().toString());
    assertFalse(configuration.isComplete() && configuration.getConfiguredTags().isEmpty());
  }

  @Test
  void runsStrictClosedLoopSafetyQualificationAndPackageCompilation() throws Exception {
    Definition definition = InletCompressionExportReferenceFacility.build();

    ProductionVerticalSliceSimulator.Result result = ProductionVerticalSliceSimulator.runStrictAndCompile(
        definition.getProject(), definition.getAutoConfigurationPolicy(), definition.getQualificationPolicy(), 1,
        outputDirectory, null);

    assertTrue(result.getPreflight().isReadyForSimulation());
    assertTrue(result.getSimulation().getEngineeringDesignLoopResult().isConverged(),
        result.getSimulation().getEngineeringDesignLoopResult().getTerminationReason());
    assertFalse(result.getSimulation().getCaseRunReport().getEnvelope().hasCaseFailures());
    assertTrue(result.getQualification().isQualifiedForControlledPilot(),
        result.getQualification().getFailedGates().toString());
    assertTrue(result.getSimulation().getDynamicScenarioResults().get(0).isPassed());
    assertTrue(result.getSimulation().getCoupledSafetyResults().get(0).getValue().isCapacityAcceptable());
    assertTrue(Files.isRegularFile(result.getCompilation().getDexpiResult().getDexpi20File()));
    assertTrue(Files.isRegularFile(result.getCompilation().getCompilerManifestFile()));
    assertTrue(Files.isRegularFile(result.getCompilation().getVerticalSliceExecutionManifestFile()));
    assertTrue(result.toMap().containsKey("fitnessForConstruction"));
  }
}
