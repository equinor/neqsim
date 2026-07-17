package neqsim.process.engineering;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
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

/** Runs all four engineering-simulator foundations configured on an {@link EngineeringProject}. */
public final class EngineeringSimulationRunner {
  private EngineeringSimulationRunner() {
  }

  public static EngineeringSimulationResult run(EngineeringProject project, EngineeringCaseRunOptions options) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    EngineeringCaseRunReport caseReport = runCases(project, options);
    EngineeringCalculationContext context = context(project, caseReport);
    CoupledReliefBlowdownFlareCalculation coupledCalculation = new CoupledReliefBlowdownFlareCalculation();
    List<EngineeringCalculationResult<CoupledReliefBlowdownFlareResult>> coupledResults = new ArrayList<EngineeringCalculationResult<CoupledReliefBlowdownFlareResult>>();
    for (CoupledReliefBlowdownFlareInput input : project.getCoupledReliefBlowdownFlareStudies()) {
      coupledResults.add(coupledCalculation.calculate(input, context));
    }
    List<DynamicSafetyScenarioResult> dynamicResults = new ArrayList<DynamicSafetyScenarioResult>();
    for (DynamicSafetyScenario scenario : project.getDynamicSafetyScenarios()) {
      dynamicResults.add(DynamicSafetyScenarioRunner.run(project.getProcessSystem(), scenario));
    }
    return new EngineeringSimulationResult(project.getProjectId(), project.getRevision(), caseReport, coupledResults,
        dynamicResults);
  }

  private static EngineeringCaseRunReport runCases(EngineeringProject project, EngineeringCaseRunOptions options) {
    if (project.getExecutableDesignCases().isEmpty() && project.getEngineeringMetrics().isEmpty()) {
      return null;
    }
    EngineeringCaseSet set = new EngineeringCaseSet(project.getProjectId() + "@" + project.getRevision());
    for (neqsim.process.engineering.designcase.EngineeringDesignCase designCase : project.getExecutableDesignCases()) {
      set.addCase(designCase);
    }
    for (neqsim.process.engineering.designcase.EngineeringMetric metric : project.getEngineeringMetrics()) {
      set.addMetric(metric);
    }
    return EngineeringCaseRunner.run(project.getProcessSystem(), set,
        options == null ? EngineeringCaseRunOptions.sequential() : options);
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
