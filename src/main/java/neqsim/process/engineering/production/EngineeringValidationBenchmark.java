package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One versioned comparison against a regression, published, independent, vendor, or CAE reference. */
public final class EngineeringValidationBenchmark implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Provenance class for the expected values. */
  public enum SourceClass {
    REGRESSION_BASELINE, PUBLISHED_WORKED_EXAMPLE, INDEPENDENT_CALCULATION, VENDOR_OR_CAE_REFERENCE
  }

  /** One scalar acceptance check. */
  public static final class Check implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double expected;
    private final double actual;
    private final String unit;
    private final double absoluteTolerance;
    private final double relativeTolerance;

    public Check(String name, double expected, double actual, String unit, double absoluteTolerance,
        double relativeTolerance) {
      this.name = text(name, "name");
      this.unit = text(unit, "unit");
      if (!Double.isFinite(expected) || !Double.isFinite(actual) || !Double.isFinite(absoluteTolerance)
          || !Double.isFinite(relativeTolerance) || absoluteTolerance < 0.0 || relativeTolerance < 0.0) {
        throw new IllegalArgumentException(
            "Benchmark values and tolerances must be finite and tolerances non-negative");
      }
      this.expected = expected;
      this.actual = actual;
      this.absoluteTolerance = absoluteTolerance;
      this.relativeTolerance = relativeTolerance;
    }

    public double getAbsoluteError() {
      return Math.abs(actual - expected);
    }

    public double getRelativeError() {
      return getAbsoluteError() / Math.max(Math.abs(expected), 1.0e-12);
    }

    public boolean isPassed() {
      return getAbsoluteError() <= absoluteTolerance || getRelativeError() <= relativeTolerance;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("name", name);
      result.put("expected", Double.valueOf(expected));
      result.put("actual", Double.valueOf(actual));
      result.put("unit", unit);
      result.put("absoluteTolerance", Double.valueOf(absoluteTolerance));
      result.put("relativeTolerance", Double.valueOf(relativeTolerance));
      result.put("absoluteError", Double.valueOf(getAbsoluteError()));
      result.put("relativeError", Double.valueOf(getRelativeError()));
      result.put("passed", Boolean.valueOf(isPassed()));
      return result;
    }
  }

  private final String id;
  private final String methodId;
  private final String methodVersion;
  private final SourceClass sourceClass;
  private final String sourceReference;
  private final String datasetRevision;
  private final String independentReviewRecord;
  private final List<Check> checks;

  private EngineeringValidationBenchmark(Builder builder) {
    id = text(builder.id, "id");
    methodId = text(builder.methodId, "methodId");
    methodVersion = text(builder.methodVersion, "methodVersion");
    if (builder.sourceClass == null) {
      throw new IllegalArgumentException("sourceClass must not be null");
    }
    sourceClass = builder.sourceClass;
    sourceReference = text(builder.sourceReference, "sourceReference");
    datasetRevision = text(builder.datasetRevision, "datasetRevision");
    independentReviewRecord = builder.independentReviewRecord == null ? "" : builder.independentReviewRecord.trim();
    if (builder.checks.isEmpty()) {
      throw new IllegalArgumentException("At least one benchmark check is required");
    }
    checks = Collections.unmodifiableList(new ArrayList<Check>(builder.checks));
  }

  public static Builder builder(String id, String methodId, String methodVersion) {
    return new Builder(id, methodId, methodVersion);
  }

  /** @return stable benchmark case identifier */
  public String getId() {
    return id;
  }

  public String getMethodId() {
    return methodId;
  }

  public String getMethodVersion() {
    return methodVersion;
  }

  public String getMethodKey() {
    return methodId + "@" + methodVersion;
  }

  public SourceClass getSourceClass() {
    return sourceClass;
  }

  public boolean isIndependentSource() {
    return sourceClass != SourceClass.REGRESSION_BASELINE;
  }

  public boolean isIndependentlyReviewed() {
    return !independentReviewRecord.isEmpty();
  }

  public boolean isPassed() {
    for (Check check : checks) {
      if (!check.isPassed()) {
        return false;
      }
    }
    return true;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("methodId", methodId);
    result.put("methodVersion", methodVersion);
    result.put("sourceClass", sourceClass.name());
    result.put("sourceReference", sourceReference);
    result.put("datasetRevision", datasetRevision);
    result.put("independentReviewRecord", independentReviewRecord);
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (Check check : checks) {
      rows.add(check.toMap());
    }
    result.put("checks", rows);
    result.put("passed", Boolean.valueOf(isPassed()));
    result.put("qualifyingEvidence", Boolean.valueOf(isPassed() && isIndependentSource() && isIndependentlyReviewed()));
    return result;
  }

  /** Builder for a controlled benchmark record. */
  public static final class Builder {
    private final String id;
    private final String methodId;
    private final String methodVersion;
    private SourceClass sourceClass = SourceClass.REGRESSION_BASELINE;
    private String sourceReference = "UNASSIGNED";
    private String datasetRevision = "working";
    private String independentReviewRecord = "";
    private final List<Check> checks = new ArrayList<Check>();

    private Builder(String id, String methodId, String methodVersion) {
      this.id = id;
      this.methodId = methodId;
      this.methodVersion = methodVersion;
    }

    public Builder source(SourceClass value, String reference, String revision) {
      sourceClass = value;
      sourceReference = reference;
      datasetRevision = revision;
      return this;
    }

    public Builder independentReview(String value) {
      independentReviewRecord = value;
      return this;
    }

    public Builder check(String name, double expected, double actual, String unit, double absoluteTolerance,
        double relativeTolerance) {
      checks.add(new Check(name, expected, actual, unit, absoluteTolerance, relativeTolerance));
      return this;
    }

    public EngineeringValidationBenchmark build() {
      return new EngineeringValidationBenchmark(this);
    }
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
