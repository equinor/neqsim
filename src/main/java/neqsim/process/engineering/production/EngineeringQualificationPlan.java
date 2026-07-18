package neqsim.process.engineering.production;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Produces an executable evidence backlog for every production-qualification gate. */
public final class EngineeringQualificationPlan {
  private EngineeringQualificationPlan() {
  }

  public static Map<String, Object> build(EngineeringProject project, EngineeringProductionReadinessBasis basis) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    EngineeringProductionReadinessBasis evidence = basis == null ? new EngineeringProductionReadinessBasis() : basis;
    Set<String> executed = EngineeringProductionReadinessAssessment.executedMethods(project);
    executed.addAll(evidence.getTechnicalMethodKeys());
    Set<String> benchmarked = evidence.getBenchmarkReport() == null ? new LinkedHashSet<String>()
        : new LinkedHashSet<String>(evidence.getBenchmarkReport().getQualifyingMethods());
    Set<String> qualified = new LinkedHashSet<String>();
    for (EngineeringMethodQualification item : evidence.getMethodQualifications()) {
      if (item.isProjectQualified()) {
        qualified.add(item.getMethodKey());
      }
    }
    List<Map<String, Object>> methods = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> actions = new ArrayList<Map<String, Object>>();
    for (String method : executed) {
      boolean benchmark = benchmarked.contains(method);
      boolean qualification = qualified.contains(method);
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("methodKey", method);
      row.put("independentBenchmarkAvailable", Boolean.valueOf(benchmark));
      row.put("projectQualificationAvailable", Boolean.valueOf(qualification));
      row.put("ready", Boolean.valueOf(benchmark && qualification));
      methods.add(row);
      if (!benchmark) {
        actions.add(action("METHOD_BENCHMARK", method,
            "Run an independently reviewed non-regression benchmark for the exact method version"));
      }
      if (!qualification) {
        actions.add(action("METHOD_QUALIFICATION", method,
            "Approve standards, applicability limits and evidence for the project service"));
      }
    }
    if (evidence.getMethodQualificationRegistry() != null) {
      Set<String> serviceQualified = new LinkedHashSet<String>();
      for (EngineeringMethodQualificationRegistry.Result assessment : evidence.getMethodServiceAssessments()) {
        if (assessment.isQualifiedForService()) {
          serviceQualified.add(assessment.getMethodKey());
        }
      }
      for (String method : executed) {
        if (!serviceQualified.contains(method)) {
          actions.add(action("METHOD_SERVICE_APPLICABILITY", method,
              "Assess the exact method against a complete service context, intended use and qualified envelope"));
        }
      }
    }
    if (evidence.getAutoConfigurationResult() == null || !evidence.getAutoConfigurationResult().isComplete()) {
      actions.add(action("AUTOMATIC_CONFIGURATION", "project",
          "Complete explicit configuration coverage without hidden numerical defaults"));
    }
    boolean dexpi = false;
    for (DexpiToolQualificationEvidence item : evidence.getDexpiEvidence()) {
      dexpi |= item.isQualified();
    }
    if (!dexpi) {
      actions.add(action("DEXPI_TOOL_ROUNDTRIP", "project",
          "Execute import/export/reimport in a named tool and reconcile every semantic difference"));
    }
    Set<EngineeringPilotProjectEvidence.Scope> pilotScopes = EnumSet
        .noneOf(EngineeringPilotProjectEvidence.Scope.class);
    for (EngineeringPilotProjectEvidence item : evidence.getPilotEvidence()) {
      if (item.isAccepted()) {
        pilotScopes.add(item.getScope());
      }
    }
    for (EngineeringPilotProjectEvidence.Scope scope : EngineeringPilotProjectEvidence.Scope.values()) {
      if (!pilotScopes.contains(scope)) {
        actions.add(action("PILOT_PROJECT", scope.name(),
            "Complete independently reviewed scalar and semantic comparison with no open material discrepancy"));
      }
    }
    if (evidence.getReleaseQualityEvidence() == null || !evidence.getReleaseQualityEvidence().isPassed()) {
      actions.add(action("RELEASE_QUALITY", "release",
          "Close CI, Java matrix, determinism, performance, API, migration, security and review evidence"));
    }
    EngineeringExternalEvidenceAssessment.Result external = EngineeringExternalEvidenceAssessment
        .assess(evidence.getExternalEvidenceRegister());
    for (Map<String, Object> finding : external.getFindings()) {
      actions.add(action("EXTERNAL_EVIDENCE", String.valueOf(finding.get("subject")),
          String.valueOf(finding.get("requiredAction"))));
    }
    EngineeringSafetyLifecycleAssessment.Result safety = EngineeringSafetyLifecycleAssessment.assess(project,
        evidence.getExternalEvidenceRegister());
    if (!safety.isPassed()) {
      actions.add(action("SAFETY_LIFECYCLE", "project", "Close accountable HAZOP/LOPA/SRS, SIF and shutdown findings"));
    }
    EngineeringProductionReadinessAssessment.Result assessment = EngineeringProductionReadinessAssessment
        .assess(project, evidence);
    for (String failedGate : assessment.getFailedGates()) {
      if (isTechnicalCompletionGate(failedGate)) {
        actions.add(action("TECHNICAL_COMPLETION", failedGate,
            "Supply a calculated, constraint-satisfying and independently qualified technical result"));
      }
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", EngineeringSchemaCatalog.QUALIFICATION_PLAN);
    result.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.QUALIFICATION_PLAN));
    result.put("projectId", project.getProjectId());
    result.put("revision", project.getRevision());
    result.put("executedMethods", new ArrayList<String>(executed));
    result.put("methodQualificationMatrix", methods);
    result.put("externalEvidenceAssessment", external.toMap());
    result.put("actions", actions);
    result.put("openActionCount", Integer.valueOf(actions.size()));
    result.put("readinessLevel", assessment.getLevel().name());
    result.put("preliminaryProductionReady", Boolean.valueOf(assessment.isPreliminaryProductionReady()));
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("externalEvidenceMayNotBeGeneratedBySimulator", Boolean.TRUE);
    return result;
  }

  private static Map<String, Object> action(String type, String subject, String description) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("type", type);
    result.put("subject", subject);
    result.put("description", description);
    result.put("status", "OPEN");
    return result;
  }

  private static boolean isTechnicalCompletionGate(String gate) {
    return "DISTRIBUTED_TRANSIENT_PIPING".equals(gate) || "COMPRESSOR_PROTECTION_AND_MACHINERY".equals(gate)
        || "VALVE_AND_INSTRUMENT_QUALIFICATION".equals(gate) || "DETAILED_MECHANICAL_INTEGRITY".equals(gate)
        || "FLARE_RADIATION_DISPERSION_AND_NOISE".equals(gate);
  }
}
