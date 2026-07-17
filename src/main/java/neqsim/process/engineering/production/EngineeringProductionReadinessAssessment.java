package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.design.EngineeringDesignIteration;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Aggregates technical and accountable evidence without ever authorizing final or construction design. */
public final class EngineeringProductionReadinessAssessment {
  private EngineeringProductionReadinessAssessment() {
  }

  public enum Level {
    NOT_READY, EXPERIMENTAL, VALIDATED_PRELIMINARY, QUALIFIED_FEED_SUPPORT
  }

  public static Result assess(EngineeringProject project, EngineeringProductionReadinessBasis basis) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    EngineeringProductionReadinessBasis evidence = basis == null ? new EngineeringProductionReadinessBasis() : basis;
    Map<String, Gate> gates = new LinkedHashMap<String, Gate>();
    boolean designLoop = project.getLatestEngineeringDesignLoopResult() != null
        && project.getLatestEngineeringDesignLoopResult().isConverged();
    gates.put("CLOSED_ENGINEERING_DESIGN_LOOP",
        gate(designLoop, "Run all cases to stable process values, physical variables and constraints"));

    EngineeringBenchmarkSuite.Report benchmark = evidence.getBenchmarkReport();
    Set<String> executedMethods = executedMethods(project);
    Set<String> unbenchmarkedMethods = new LinkedHashSet<String>(executedMethods);
    if (benchmark != null) {
      unbenchmarkedMethods.removeAll(benchmark.getQualifyingMethods());
    }
    boolean benchmarkPassed = benchmark != null && benchmark.isPassed() && !executedMethods.isEmpty()
        && unbenchmarkedMethods.isEmpty();
    gates.put("INDEPENDENT_VALIDATION_BENCHMARKS",
        gate(benchmarkPassed,
            benchmark == null ? "Attach a versioned benchmark report"
                : "Missing executed method evidence " + unbenchmarkedMethods + "; suite gaps "
                    + benchmark.getMissingQualifyingMethods()));

    Set<String> qualifiedMethods = new LinkedHashSet<String>();
    for (EngineeringMethodQualification qualification : evidence.getMethodQualifications()) {
      if (qualification.isProjectQualified()) {
        qualifiedMethods.add(qualification.getMethodKey());
      }
    }
    Set<String> missingQualifications = new LinkedHashSet<String>();
    missingQualifications.addAll(executedMethods);
    missingQualifications.removeAll(qualifiedMethods);
    boolean methods = !executedMethods.isEmpty() && missingQualifications.isEmpty();
    gates.put("PROJECT_QUALIFIED_METHODS", gate(methods,
        methods ? "All required method versions are project qualified" : missingQualifications.toString()));

    EngineeringAutoConfigurator.Result automation = evidence.getAutoConfigurationResult();
    gates.put("EXPLICIT_AUTOMATIC_CONFIGURATION", gate(automation != null && automation.isComplete(),
        "Attach a complete explicit auto-configuration result with no hidden defaults"));

    boolean dexpi = false;
    for (DexpiToolQualificationEvidence item : evidence.getDexpiEvidence()) {
      dexpi |= item.isQualified();
    }
    gates.put("NAMED_DEXPI_TOOL_ROUNDTRIP",
        gate(dexpi, "Record successful named-tool import/export and close every semantic difference"));

    EngineeringSafetyLifecycleAssessment.Result safety = EngineeringSafetyLifecycleAssessment.assess(project);
    gates.put("SAFETY_LIFECYCLE", gate(safety.isPassed(), "Close HAZOP/LOPA/SRS, SIF and shutdown findings"));

    Set<EngineeringPilotProjectEvidence.Scope> acceptedPilotScopes = EnumSet
        .noneOf(EngineeringPilotProjectEvidence.Scope.class);
    for (EngineeringPilotProjectEvidence pilot : evidence.getPilotEvidence()) {
      if (pilot.isAccepted()) {
        acceptedPilotScopes.add(pilot.getScope());
      }
    }
    boolean pilots = acceptedPilotScopes.containsAll(EnumSet.allOf(EngineeringPilotProjectEvidence.Scope.class));
    gates.put("THREE_INDEPENDENT_PILOTS",
        gate(pilots, "Accept separation/compression, pumping/heat-exchange and relief/blowdown/flare pilots"));

    EngineeringReleaseQualityEvidence release = evidence.getReleaseQualityEvidence();
    gates.put("RELEASE_QUALITY", gate(release != null && release.isPassed(),
        release == null ? "Attach release quality evidence" : release.getMissingGates().toString()));

    boolean all = true;
    for (Gate value : gates.values()) {
      all &= value.passed;
    }
    boolean validated = designLoop && benchmarkPassed && methods && automation != null && automation.isComplete()
        && safety.isPassed();
    Level level = all ? Level.QUALIFIED_FEED_SUPPORT
        : validated ? Level.VALIDATED_PRELIMINARY : designLoop ? Level.EXPERIMENTAL : Level.NOT_READY;
    return new Result(project.getProjectId(), project.getRevision(), level, gates, safety, evidence, all);
  }

  private static Set<String> executedMethods(EngineeringProject project) {
    Set<String> result = new LinkedHashSet<String>();
    if (project.getLatestEngineeringDesignLoopResult() == null
        || project.getLatestEngineeringDesignLoopResult().getIterations().isEmpty()) {
      return result;
    }
    EngineeringDesignIteration iteration = project.getLatestEngineeringDesignLoopResult().getIterations()
        .get(project.getLatestEngineeringDesignLoopResult().getIterations().size() - 1);
    for (EngineeringDesignModuleResult module : iteration.getModuleResults()) {
      result.add(module.getMethod() + "@" + module.getMethodVersion());
    }
    return result;
  }

  private static Gate gate(boolean passed, String action) {
    return new Gate(passed, action);
  }

  private static final class Gate implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final boolean passed;
    private final String action;

    Gate(boolean passed, String action) {
      this.passed = passed;
      this.action = action;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("passed", Boolean.valueOf(passed));
      result.put("requiredAction", passed ? "NONE" : action);
      return result;
    }
  }

  /** Immutable readiness decision and evidence snapshot. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String projectId;
    private final String revision;
    private final Level level;
    private final Map<String, Gate> gates;
    private final EngineeringSafetyLifecycleAssessment.Result safety;
    private final EngineeringProductionReadinessBasis basis;
    private final boolean preliminaryProductionReady;

    Result(String projectId, String revision, Level level, Map<String, Gate> gates,
        EngineeringSafetyLifecycleAssessment.Result safety, EngineeringProductionReadinessBasis basis,
        boolean preliminaryProductionReady) {
      this.projectId = projectId;
      this.revision = revision;
      this.level = level;
      this.gates = new LinkedHashMap<String, Gate>(gates);
      this.safety = safety;
      this.basis = basis;
      this.preliminaryProductionReady = preliminaryProductionReady;
    }

    public Level getLevel() {
      return level;
    }

    public boolean isPreliminaryProductionReady() {
      return preliminaryProductionReady;
    }

    public List<String> getFailedGates() {
      List<String> result = new ArrayList<String>();
      for (Map.Entry<String, Gate> item : gates.entrySet()) {
        if (!item.getValue().passed) {
          result.add(item.getKey());
        }
      }
      return result;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", EngineeringSchemaCatalog.PRODUCTION_READINESS);
      result.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.PRODUCTION_READINESS));
      result.put("projectId", projectId);
      result.put("revision", revision);
      result.put("maturityLevel", level.name());
      Map<String, Object> gateMaps = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, Gate> gate : gates.entrySet()) {
        gateMaps.put(gate.getKey(), gate.getValue().toMap());
      }
      result.put("gates", gateMaps);
      result.put("failedGates", getFailedGates());
      result.put("safetyLifecycle", safety.toMap());
      result.put("evidenceBasis", basis.toMap());
      result.put("preliminaryProductionReady", Boolean.valueOf(preliminaryProductionReady));
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("finalEngineeringApprovalGranted", Boolean.FALSE);
      result.put("governance", "Qualified FEED support still requires accountable discipline and project approval");
      return result;
    }
  }
}
