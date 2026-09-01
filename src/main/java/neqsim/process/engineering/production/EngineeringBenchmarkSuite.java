package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executes acceptance logic over versioned engineering benchmark records; every case for a method must qualify. */
public final class EngineeringBenchmarkSuite implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String id;
  private final String revision;
  private final Set<String> requiredMethods = new LinkedHashSet<String>();
  private final List<EngineeringValidationBenchmark> benchmarks = new ArrayList<EngineeringValidationBenchmark>();

  public EngineeringBenchmarkSuite(String id, String revision) {
    this.id = text(id, "id");
    this.revision = text(revision, "revision");
  }

  public EngineeringBenchmarkSuite requireMethod(String methodId) {
    requiredMethods.add(text(methodId, "methodId"));
    return this;
  }

  public EngineeringBenchmarkSuite add(EngineeringValidationBenchmark benchmark) {
    if (benchmark == null) {
      throw new IllegalArgumentException("benchmark must not be null");
    }
    benchmarks.add(benchmark);
    return this;
  }

  public Report evaluate() {
    return new Report(id, revision, requiredMethods, benchmarks);
  }

  /** Immutable suite result used by the production-readiness gate. */
  public static final class Report implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String revision;
    private final Set<String> requiredMethods;
    private final List<EngineeringValidationBenchmark> benchmarks;
    private final Set<String> qualifyingMethods = new LinkedHashSet<String>();

    Report(String id, String revision, Set<String> requiredMethods, List<EngineeringValidationBenchmark> benchmarks) {
      this.id = id;
      this.revision = revision;
      this.requiredMethods = Collections.unmodifiableSet(new LinkedHashSet<String>(requiredMethods));
      this.benchmarks = Collections.unmodifiableList(new ArrayList<EngineeringValidationBenchmark>(benchmarks));
      Map<String, Boolean> methodStatus = new LinkedHashMap<String, Boolean>();
      for (EngineeringValidationBenchmark benchmark : benchmarks) {
        String method = benchmark.getMethodKey();
        boolean caseQualifies = benchmark.isPassed() && benchmark.isIndependentSource()
            && benchmark.isIndependentlyReviewed();
        Boolean previous = methodStatus.get(method);
        methodStatus.put(method, Boolean.valueOf((previous == null || previous.booleanValue()) && caseQualifies));
      }
      for (Map.Entry<String, Boolean> method : methodStatus.entrySet()) {
        if (method.getValue().booleanValue()) {
          qualifyingMethods.add(method.getKey());
        }
      }
    }

    public boolean isPassed() {
      return !requiredMethods.isEmpty() && qualifyingMethods.containsAll(requiredMethods);
    }

    /**
     * Check numeric regression tolerances independently of evidence qualification.
     *
     * <p>
     * A passing regression baseline protects implementation behavior but does not qualify a method. Use
     * {@link #isPassed()} for the independently reviewed production-readiness gate.
     * </p>
     *
     * @return true when the report contains at least one benchmark and every numeric check passes
     */
    public boolean areAllBenchmarksPassed() {
      if (benchmarks.isEmpty()) {
        return false;
      }
      for (EngineeringValidationBenchmark benchmark : benchmarks) {
        if (!benchmark.isPassed()) {
          return false;
        }
      }
      return true;
    }

    /** @return immutable IDs of benchmark cases outside their numeric tolerances */
    public List<String> getFailedBenchmarkIds() {
      List<String> failed = new ArrayList<String>();
      for (EngineeringValidationBenchmark benchmark : benchmarks) {
        if (!benchmark.isPassed()) {
          failed.add(benchmark.getId());
        }
      }
      return Collections.unmodifiableList(failed);
    }

    /** @return immutable benchmark records evaluated by this report */
    public List<EngineeringValidationBenchmark> getBenchmarks() {
      return benchmarks;
    }

    public Set<String> getMissingQualifyingMethods() {
      Set<String> missing = new LinkedHashSet<String>(requiredMethods);
      missing.removeAll(qualifyingMethods);
      return Collections.unmodifiableSet(missing);
    }

    public Set<String> getRequiredMethods() {
      return requiredMethods;
    }

    public Set<String> getQualifyingMethods() {
      return Collections.unmodifiableSet(qualifyingMethods);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "engineering_benchmark_suite.v1");
      result.put("suiteId", id);
      result.put("revision", revision);
      result.put("requiredMethods", new ArrayList<String>(requiredMethods));
      result.put("qualifyingMethods", new ArrayList<String>(qualifyingMethods));
      result.put("missingQualifyingMethods", new ArrayList<String>(getMissingQualifyingMethods()));
      List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
      for (EngineeringValidationBenchmark benchmark : benchmarks) {
        rows.add(benchmark.toMap());
      }
      result.put("benchmarks", rows);
      result.put("allBenchmarksPassed", Boolean.valueOf(areAllBenchmarksPassed()));
      result.put("failedBenchmarkIds", new ArrayList<String>(getFailedBenchmarkIds()));
      result.put("passed", Boolean.valueOf(isPassed()));
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      return result;
    }
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
