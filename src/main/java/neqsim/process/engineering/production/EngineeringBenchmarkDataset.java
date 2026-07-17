package neqsim.process.engineering.production;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned, serializable independent-reference dataset and executable benchmark runner. */
public final class EngineeringBenchmarkDataset implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** One expected scalar with explicit unit and acceptance tolerance. */
  public static final class ExpectedValue implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double expected;
    private final String unit;
    private final double absoluteTolerance;
    private final double relativeTolerance;

    public ExpectedValue(String name, double expected, String unit, double absoluteTolerance,
        double relativeTolerance) {
      this.name = text(name, "name");
      this.unit = text(unit, "unit");
      if (!Double.isFinite(expected) || !Double.isFinite(absoluteTolerance) || !Double.isFinite(relativeTolerance)
          || absoluteTolerance < 0.0 || relativeTolerance < 0.0) {
        throw new IllegalArgumentException("Expected value and tolerances must be finite and non-negative");
      }
      this.expected = expected;
      this.absoluteTolerance = absoluteTolerance;
      this.relativeTolerance = relativeTolerance;
    }
  }

  /** One controlled reference case for an exact calculation method version. */
  public static final class Case implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String methodId;
    private final String methodVersion;
    private final EngineeringValidationBenchmark.SourceClass sourceClass;
    private final String sourceReference;
    private final String sourceRevision;
    private final String independentReviewRecord;
    private final List<ExpectedValue> expectedValues = new ArrayList<ExpectedValue>();

    public Case(String id, String methodId, String methodVersion,
        EngineeringValidationBenchmark.SourceClass sourceClass, String sourceReference, String sourceRevision,
        String independentReviewRecord) {
      this.id = text(id, "id");
      this.methodId = text(methodId, "methodId");
      this.methodVersion = text(methodVersion, "methodVersion");
      if (sourceClass == null) {
        throw new IllegalArgumentException("sourceClass must not be null");
      }
      this.sourceClass = sourceClass;
      this.sourceReference = text(sourceReference, "sourceReference");
      this.sourceRevision = text(sourceRevision, "sourceRevision");
      this.independentReviewRecord = optional(independentReviewRecord);
    }

    public Case expect(String name, double expected, String unit, double absoluteTolerance, double relativeTolerance) {
      expectedValues.add(new ExpectedValue(name, expected, unit, absoluteTolerance, relativeTolerance));
      return this;
    }

    public String getId() {
      return id;
    }

    public String getMethodKey() {
      return methodId + "@" + methodVersion;
    }

    EngineeringValidationBenchmark evaluate(Map<String, Double> actualValues) {
      if (expectedValues.isEmpty()) {
        throw new IllegalStateException("Benchmark case " + id + " has no expected values");
      }
      Map<String, Double> actual = actualValues == null ? Collections.<String, Double>emptyMap() : actualValues;
      EngineeringValidationBenchmark.Builder result = EngineeringValidationBenchmark
          .builder(id, methodId, methodVersion).source(sourceClass, sourceReference, sourceRevision);
      if (!independentReviewRecord.isEmpty()) {
        result.independentReview(independentReviewRecord);
      }
      for (ExpectedValue expectedValue : expectedValues) {
        Double value = actual.get(expectedValue.name);
        if (value == null || !Double.isFinite(value.doubleValue())) {
          throw new IllegalArgumentException(
              "Missing finite actual output " + expectedValue.name + " for benchmark case " + id);
        }
        result.check(expectedValue.name, expectedValue.expected, value.doubleValue(), expectedValue.unit,
            expectedValue.absoluteTolerance, expectedValue.relativeTolerance);
      }
      return result.build();
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("methodId", methodId);
      result.put("methodVersion", methodVersion);
      result.put("methodKey", getMethodKey());
      result.put("sourceClass", sourceClass.name());
      result.put("sourceReference", sourceReference);
      result.put("sourceRevision", sourceRevision);
      result.put("independentReviewRecord", independentReviewRecord);
      List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
      for (ExpectedValue value : expectedValues) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", value.name);
        row.put("expected", Double.valueOf(value.expected));
        row.put("unit", value.unit);
        row.put("absoluteTolerance", Double.valueOf(value.absoluteTolerance));
        row.put("relativeTolerance", Double.valueOf(value.relativeTolerance));
        values.add(row);
      }
      result.put("expectedValues", values);
      return result;
    }
  }

  /** Dataset execution result retaining missing case/output findings. */
  public static final class RunResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final EngineeringBenchmarkSuite.Report report;
    private final List<String> findings;

    RunResult(EngineeringBenchmarkSuite.Report report, List<String> findings) {
      this.report = report;
      this.findings = Collections.unmodifiableList(new ArrayList<String>(findings));
    }

    public EngineeringBenchmarkSuite.Report getReport() {
      return report;
    }

    public boolean isComplete() {
      return findings.isEmpty();
    }

    public List<String> getFindings() {
      return findings;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("report", report.toMap());
      result.put("findings", new ArrayList<String>(findings));
      result.put("complete", Boolean.valueOf(isComplete()));
      return result;
    }
  }

  private final String id;
  private final String revision;
  private final List<Case> cases = new ArrayList<Case>();

  public EngineeringBenchmarkDataset(String id, String revision) {
    this.id = text(id, "id");
    this.revision = text(revision, "revision");
  }

  public EngineeringBenchmarkDataset add(Case value) {
    if (value == null) {
      throw new IllegalArgumentException("benchmark case must not be null");
    }
    for (Case existing : cases) {
      if (existing.id.equals(value.id)) {
        throw new IllegalArgumentException("Duplicate benchmark case " + value.id);
      }
    }
    cases.add(value);
    return this;
  }

  /** Executes every case against actual outputs keyed first by case id and then output name. */
  public RunResult run(Map<String, Map<String, Double>> actualOutputs) {
    Map<String, Map<String, Double>> actual = actualOutputs == null
        ? Collections.<String, Map<String, Double>>emptyMap()
        : actualOutputs;
    EngineeringBenchmarkSuite suite = new EngineeringBenchmarkSuite(id, revision);
    Set<String> methods = new LinkedHashSet<String>();
    for (Case item : cases) {
      methods.add(item.getMethodKey());
    }
    for (String method : methods) {
      suite.requireMethod(method);
    }
    List<String> findings = new ArrayList<String>();
    for (Case item : cases) {
      try {
        suite.add(item.evaluate(actual.get(item.id)));
      } catch (IllegalArgumentException | IllegalStateException ex) {
        findings.add(ex.getMessage());
      }
    }
    return new RunResult(suite.evaluate(), findings);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("datasetId", id);
    result.put("revision", revision);
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (Case item : cases) {
      rows.add(item.toMap());
    }
    result.put("cases", rows);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static String optional(String value) {
    return value == null ? "" : value.trim();
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
