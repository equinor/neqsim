package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.design.EngineeringDesignConstraint;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.design.EngineeringDesignUpdate;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.engineering.production.DexpiToolQualificationEvidence;
import neqsim.process.engineering.production.EngineeringAutoConfigurationPolicy;
import neqsim.process.engineering.production.EngineeringAutoConfigurator;
import neqsim.process.engineering.production.EngineeringBenchmarkSuite;
import neqsim.process.engineering.production.EngineeringMethodQualification;
import neqsim.process.engineering.production.EngineeringPilotProjectEvidence;
import neqsim.process.engineering.production.EngineeringProductionReadinessAssessment;
import neqsim.process.engineering.production.EngineeringProductionReadinessBasis;
import neqsim.process.engineering.production.EngineeringReleaseQualityEvidence;
import neqsim.process.engineering.production.EngineeringValidationBenchmark;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests evidence-based preliminary production readiness without construction authorization. */
class EngineeringProductionReadinessTest {

  @Test
  void regressionOnlyBenchmarkDoesNotQualifyMethod() {
    EngineeringBenchmarkSuite.Report report = new EngineeringBenchmarkSuite("suite", "1")
        .requireMethod("separator-method@1.0")
        .add(EngineeringValidationBenchmark.builder("case", "separator-method", "1.0")
            .source(EngineeringValidationBenchmark.SourceClass.REGRESSION_BASELINE, "internal", "1")
            .check("diameter", 1.0, 1.0, "m", 0.0, 0.0).build())
        .evaluate();

    assertFalse(report.isPassed());
    assertTrue(report.getMissingQualifyingMethods().contains("separator-method@1.0"));
  }

  @Test
  void evidenceForAnUnexecutedMethodCannotQualifyTheExecutedMethod() {
    EngineeringProject project = project();
    ProcessToEngineeringSimulator.run(project);
    EngineeringBenchmarkSuite.Report unrelated = new EngineeringBenchmarkSuite("unrelated", "A")
        .requireMethod("different-method@1.0")
        .add(EngineeringValidationBenchmark.builder("different-case", "different-method", "1.0")
            .source(EngineeringValidationBenchmark.SourceClass.INDEPENDENT_CALCULATION, "CALC-X", "A")
            .independentReview("Independent checker").check("value", 1.0, 1.0, "fraction", 0.0, 0.0).build())
        .evaluate();
    EngineeringProductionReadinessBasis basis = new EngineeringProductionReadinessBasis().benchmarkReport(unrelated)
        .autoConfigurationResult(
            EngineeringAutoConfigurator.configure(project, new EngineeringAutoConfigurationPolicy("policy", "A")))
        .addMethodQualification(new EngineeringMethodQualification("different-method", "1.0",
            EngineeringMethodQualification.Level.PROJECT_QUALIFIED).addStandardReference("DB-A")
            .addApplicabilityLimit("Different method only").addEvidenceReference("CALC-X").approve("TA-X"));

    EngineeringProductionReadinessAssessment.Result result = EngineeringProductionReadinessAssessment.assess(project,
        basis);

    assertTrue(result.getFailedGates().contains("INDEPENDENT_VALIDATION_BENCHMARKS"));
    assertTrue(result.getFailedGates().contains("PROJECT_QUALIFIED_METHODS"));
  }

  @Test
  void reachesQualifiedFeedSupportOnlyWithEveryEvidenceGate() {
    EngineeringProject project = project();
    ProcessToEngineeringSimulator.run(project);
    EngineeringAutoConfigurator.Result automation = EngineeringAutoConfigurator.configure(project,
        new EngineeringAutoConfigurationPolicy("project-policy", "A"));
    EngineeringBenchmarkSuite.Report benchmark = new EngineeringBenchmarkSuite("independent-suite", "A")
        .requireMethod("test-pressure-design@1.0")
        .add(EngineeringValidationBenchmark.builder("pressure-case", "test-pressure-design", "1.0")
            .source(EngineeringValidationBenchmark.SourceClass.INDEPENDENT_CALCULATION, "CALC-001", "A")
            .independentReview("Checker / CALC-001-A").check("designPressure", 60.0, 60.0, "bara", 0.01, 1.0e-4)
            .build())
        .evaluate();
    EngineeringProductionReadinessBasis basis = new EngineeringProductionReadinessBasis().benchmarkReport(benchmark)
        .autoConfigurationResult(automation)
        .addMethodQualification(new EngineeringMethodQualification("test-pressure-design", "1.0",
            EngineeringMethodQualification.Level.PROJECT_QUALIFIED).addStandardReference("PROJECT-DESIGN-BASIS-A")
            .addApplicabilityLimit("Methane test stream at 300 K and 50 bara").addEvidenceReference("CALC-001-A")
            .approve("Technical authority / TA-001"))
        .addDexpiEvidence(new DexpiToolQualificationEvidence("Named CAE", "2026.1", "DEXPI 2.0", true, true, 0,
            "DEXPI-ROUNDTRIP-001", "CAE checker"))
        .addPilotEvidence(pilot("PILOT-SEP", EngineeringPilotProjectEvidence.Scope.SEPARATION_AND_COMPRESSION))
        .addPilotEvidence(pilot("PILOT-PUMP", EngineeringPilotProjectEvidence.Scope.PUMPING_AND_HEAT_EXCHANGE))
        .addPilotEvidence(pilot("PILOT-FLARE", EngineeringPilotProjectEvidence.Scope.RELIEF_BLOWDOWN_AND_FLARE))
        .releaseQualityEvidence(new EngineeringReleaseQualityEvidence("release-candidate-1").fullCiPassed(true)
            .supportedJavaMatrixPassed(true).deterministicConvergencePassed(true).performanceAcceptancePassed(true)
            .apiCompatibilityPassed(true).serializationMigrationPassed(true).securityReviewPassed(true)
            .evidenceReference("RELEASE-EVIDENCE-001").accountableReviewer("Release authority / RA-001"));
    project.addEvidenceRecord(new EngineeringEvidenceRecord("HAZOP-001", "HAZOP", "A").setTitle("Test hazard review")
        .setSourceOrganization("Independent engineering team").linkEquipment("FEED")
        .approve("Hazop chair / HAZOP-001-A"));

    EngineeringProductionReadinessAssessment.Result result = EngineeringProductionReadinessAssessment.assess(project,
        basis);

    assertEquals(EngineeringProductionReadinessAssessment.Level.QUALIFIED_FEED_SUPPORT, result.getLevel());
    assertTrue(result.isPreliminaryProductionReady());
    assertFalse(Boolean.TRUE.equals(result.toMap().get("fitnessForConstruction")));
  }

  private EngineeringPilotProjectEvidence pilot(String id, EngineeringPilotProjectEvidence.Scope scope) {
    return new EngineeringPilotProjectEvidence(id, scope, id + "-REFERENCE", 20, 0, "Independent checker",
        id + "-ACCEPTANCE");
  }

  private EngineeringProject project() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();
    EngineeringProject project = new EngineeringProject("production-readiness", "Production readiness", process,
        new EngineeringDesignBasis().setJurisdiction("Norway").setProjectPhase("FEED"));
    project.addDesignCase(new EngineeringDesignCase("normal", "Normal", EngineeringDesignCase.Type.NORMAL,
        new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem configured) {
            // The base process already represents the controlled normal case.
          }
        }));
    project.addEngineeringMetric(EngineeringMetric.equipmentPressure("FEED"));
    project.addEngineeringDesignModule(new TestPressureModule());
    return project;
  }

  private static final class TestPressureModule implements EngineeringDesignModule {
    private static final long serialVersionUID = 1000L;

    @Override
    public String getId() {
      return "test-pressure-design-FEED";
    }

    @Override
    public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
        EngineeringDesignState state, EngineeringCalculationContext context) {
      return EngineeringDesignModuleResult.builder(getId(), "test-pressure-design", "1.0")
          .addUpdate(EngineeringDesignUpdate.builder("FEED.designPressure", 60.0, "bara").build())
          .addConstraint(new EngineeringDesignConstraint("FEED.pressure-minimum", "Minimum design pressure",
              "FEED.designPressure", 60.0, "bara", EngineeringDesignConstraint.Comparison.MINIMUM))
          .build();
    }
  }
}
