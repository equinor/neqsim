package neqsim.process.engineering.deliverables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.automation.EngineeringAutomationStudy;
import neqsim.process.engineering.automation.EngineeringAutomationStudy.Constraint;
import neqsim.process.engineering.automation.EngineeringAutomationStudy.Objective;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope.GoverningValue;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Compiles governed optimization metadata and baseline feasibility evidence without changing the source process. */
public final class EngineeringAutomationPlan {
  private EngineeringAutomationPlan() {
  }

  public static Map<String, Object> build(EngineeringProject project, EngineeringGraph graph,
      EngineeringDesignEnvelope envelope) {
    if (project == null || graph == null) {
      throw new IllegalArgumentException("project and graph are required");
    }
    List<Map<String, Object>> studies = new ArrayList<Map<String, Object>>();
    int variableCount = 0;
    int objectiveCount = 0;
    int constraintCount = 0;
    int readyCount = 0;
    int violatedCount = 0;
    for (EngineeringAutomationStudy study : project.getAutomationStudies()) {
      Map<String, Object> studyMap = new LinkedHashMap<String, Object>();
      studyMap.put("id", study.getId());
      studyMap.put("name", study.getName());
      List<Map<String, Object>> variables = new ArrayList<Map<String, Object>>();
      for (EngineeringAutomationStudy.DecisionVariable variable : study.getDecisionVariables()) {
        if (graph.getNode(variable.getGraphNodeId()) == null) {
          throw new IllegalArgumentException("Automation variable " + variable.getId()
              + " references unknown graph node " + variable.getGraphNodeId());
        }
        variables.add(variable.toMap());
      }
      List<Map<String, Object>> objectives = new ArrayList<Map<String, Object>>();
      for (Objective objective : study.getObjectives()) {
        Map<String, Object> value = objective.toMap();
        GoverningValue governing = governing(envelope, objective.getMetricKey());
        value.put("baselineValue", governing == null ? null : Double.valueOf(governing.getValue()));
        value.put("baselineDesignCaseId", governing == null ? "" : governing.getDesignCaseId());
        value.put("baselineStatus", governing == null ? "NOT_CALCULATED" : "CALCULATED");
        objectives.add(value);
      }
      List<Map<String, Object>> constraints = new ArrayList<Map<String, Object>>();
      int studyViolations = 0;
      for (Constraint constraint : study.getConstraints()) {
        Map<String, Object> value = constraint.toMap();
        GoverningValue governing = governing(envelope, constraint.getMetricKey());
        String status = assess(governing, constraint);
        value.put("baselineValue", governing == null ? null : Double.valueOf(governing.getValue()));
        value.put("baselineDesignCaseId", governing == null ? "" : governing.getDesignCaseId());
        value.put("baselineStatus", status);
        if ("BELOW_LOWER_BOUND".equals(status) || "ABOVE_UPPER_BOUND".equals(status)) {
          studyViolations++;
        }
        constraints.add(value);
      }
      boolean ready = !variables.isEmpty() && !objectives.isEmpty() && !constraints.isEmpty();
      studyMap.put("decisionVariables", variables);
      studyMap.put("objectives", objectives);
      studyMap.put("constraints", constraints);
      studyMap.put("executionMode", "ADVISORY_BOUNDED_SCREENING");
      studyMap.put("executionStatus", ready ? "READY_FOR_ISOLATED_SCREENING" : "CONFIGURATION_INCOMPLETE");
      studyMap.put("baselineFeasibility",
          studyViolations == 0 ? "NO_CONTROLLED_CONSTRAINT_VIOLATIONS" : "CONTROLLED_CONSTRAINTS_VIOLATED");
      studies.add(studyMap);
      variableCount += variables.size();
      objectiveCount += objectives.size();
      constraintCount += constraints.size();
      violatedCount += studyViolations;
      if (ready) {
        readyCount++;
      }
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", EngineeringSchemaCatalog.AUTOMATION_PLAN);
    result.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.AUTOMATION_PLAN));
    result.put("projectId", project.getProjectId());
    result.put("revision", project.getRevision());
    result.put("graphFingerprint", graph.toMap().get("fingerprint"));
    result.put("studies", studies);
    result.put("availableEngines", Arrays.asList("ProcessSimulationEvaluator", "ProcessModelSimulationEvaluator",
        "DesignOptimizer", "ProcessCandidateEvaluator", "ParetoFront", "SensitivityAnalysis", "MonteCarloSimulator"));
    Map<String, Object> boundary = new LinkedHashMap<String, Object>();
    boundary.put("candidateIsolation", "REQUIRED_PROCESS_SYSTEM_COPY");
    boundary.put("automaticPlantChange", Boolean.FALSE);
    boundary.put("resultGovernance", "REVIEW_REQUIRED_BEFORE_DESIGN_ADOPTION");
    boundary.put("standardsAndSafetyConstraints", "CONTROLLED_INPUTS_NOT_OPTIMIZER_ASSUMPTIONS");
    result.put("executionBoundary", boundary);
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("studyCount", Integer.valueOf(studies.size()));
    summary.put("readyStudyCount", Integer.valueOf(readyCount));
    summary.put("decisionVariableCount", Integer.valueOf(variableCount));
    summary.put("objectiveCount", Integer.valueOf(objectiveCount));
    summary.put("constraintCount", Integer.valueOf(constraintCount));
    summary.put("baselineConstraintViolationCount", Integer.valueOf(violatedCount));
    result.put("summary", summary);
    return result;
  }

  private static GoverningValue governing(EngineeringDesignEnvelope envelope, String metricKey) {
    return envelope == null ? null : envelope.getGoverningValues().get(metricKey);
  }

  private static String assess(GoverningValue governing, Constraint constraint) {
    if (governing == null) {
      return "NOT_CALCULATED";
    }
    double value = governing.getValue();
    if (constraint.getLowerBound() != null && value < constraint.getLowerBound().doubleValue()) {
      return "BELOW_LOWER_BOUND";
    }
    if (constraint.getUpperBound() != null && value > constraint.getUpperBound().doubleValue()) {
      return "ABOVE_UPPER_BOUND";
    }
    return "SATISFIED";
  }
}
