package neqsim.process.engineering;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.design.EngineeringDesignLoop;
import neqsim.process.engineering.design.EngineeringDesignLoopOptions;
import neqsim.process.engineering.design.EngineeringDesignLoopResult;
import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringCaseRunner;
import neqsim.process.engineering.designcase.EngineeringCaseSet;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareCalculation;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareInput;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareResult;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioRunner;
import neqsim.process.processmodel.ProcessSystem;

/** Runs all four engineering-simulator foundations configured on an {@link EngineeringProject}. */
public final class EngineeringSimulationRunner {
  private EngineeringSimulationRunner() {
  }

  public static EngineeringSimulationResult run(EngineeringProject project, EngineeringCaseRunOptions options) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    EngineeringDesignLoopResult designLoopResult = runDesignLoop(project, options);
    project.recordEngineeringDesignLoopResult(designLoopResult);
    EngineeringCaseRunReport caseReport = designLoopResult == null ? runCases(project, options)
        : designLoopResult.getIterations().get(designLoopResult.getIterations().size() - 1).getCaseReport();
    ProcessSystem designedProcess = designLoopResult == null ? project.getProcessSystem()
        : designLoopResult.getDesignedProcess();
    EngineeringCalculationContext context = context(project, caseReport);
    CoupledReliefBlowdownFlareCalculation coupledCalculation = new CoupledReliefBlowdownFlareCalculation();
    List<EngineeringCalculationResult<CoupledReliefBlowdownFlareResult>> coupledResults = new ArrayList<EngineeringCalculationResult<CoupledReliefBlowdownFlareResult>>();
    for (CoupledReliefBlowdownFlareInput input : project.getCoupledReliefBlowdownFlareStudies()) {
      coupledResults.add(coupledCalculation.calculate(input, context));
    }
    List<DynamicSafetyScenarioResult> dynamicResults = new ArrayList<DynamicSafetyScenarioResult>();
    for (DynamicSafetyScenario scenario : project.getDynamicSafetyScenarios()) {
      dynamicResults.add(DynamicSafetyScenarioRunner.run(designedProcess, scenario));
    }
    return new EngineeringSimulationResult(project.getProjectId(), project.getRevision(), caseReport, designLoopResult,
        coupledResults, dynamicResults);
  }

  private static EngineeringDesignLoopResult runDesignLoop(EngineeringProject project,
      EngineeringCaseRunOptions options) {
    if (project.getEngineeringDesignModules().isEmpty()) {
      return null;
    }
    EngineeringCaseSet set = buildCaseSet(project);
    int parallelism = options == null ? 1 : options.getParallelism();
    return EngineeringDesignLoop.run(project.getProcessSystem(), set, project.getEngineeringDesignModules(),
        EngineeringDesignLoopOptions.builder().caseParallelism(parallelism).build());
  }

  private static EngineeringCaseRunReport runCases(EngineeringProject project, EngineeringCaseRunOptions options) {
    if (project.getExecutableDesignCases().isEmpty() && project.getEngineeringMetrics().isEmpty()) {
      return null;
    }
    EngineeringCaseSet set = buildCaseSet(project);
    return EngineeringCaseRunner.run(project.getProcessSystem(), set,
        options == null ? EngineeringCaseRunOptions.sequential() : options);
  }

  /**
   * Build the deterministic case-set representation used by the detailed runner and design loop.
   *
   * @param project governed project
   * @return independent case-set container with the project's cases and metrics
   */
  public static EngineeringCaseSet buildCaseSet(EngineeringProject project) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    EngineeringCaseSet set = new EngineeringCaseSet(project.getProjectId() + "@" + project.getRevision());
    for (neqsim.process.engineering.designcase.EngineeringDesignCase designCase : project.getExecutableDesignCases()) {
      set.addCase(designCase);
    }
    for (neqsim.process.engineering.designcase.EngineeringMetric metric : project.getEngineeringMetrics()) {
      set.addMetric(metric);
    }
    return set;
  }

  private static EngineeringCalculationContext context(EngineeringProject project,
      EngineeringCaseRunReport caseReport) {
    EngineeringCalculationContext.Builder builder = EngineeringCalculationContext.builder()
        .designCaseId(caseReport == null ? "" : caseReport.getCaseSetId())
        .simulationFingerprint(caseReport == null ? "" : caseReport.getResultFingerprint())
        .attribute("projectId", project.getProjectId()).attribute("revision", project.getRevision());
    for (EngineeringStandard standard : project.getDesignBasis().getStandards()) {
      builder.addStandardReference(standard.getCode() + ":" + standard.getEdition());
    }
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      builder.addEvidenceReference(evidence.getDocumentId() + "@" + evidence.getRevision());
    }
    return builder.build();
  }
}
