package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed result with calculation provenance, uncertainty, readiness, and review state. */
public final class EngineeringCalculationResult<T> implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Status {
    CALCULATED, CALCULATED_REVIEW_REQUIRED, BLOCKED, FAILED
  }

  /** Optional numeric uncertainty interval associated with the typed result. */
  public static final class Uncertainty implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double lower;
    private final double nominal;
    private final double upper;
    private final String unit;
    private final String basis;

    public Uncertainty(double lower, double nominal, double upper, String unit, String basis) {
      if (!Double.isFinite(lower) || !Double.isFinite(nominal) || !Double.isFinite(upper) || lower > nominal
          || nominal > upper) {
        throw new IllegalArgumentException("uncertainty bounds must be finite and ordered");
      }
      this.lower = lower;
      this.nominal = nominal;
      this.upper = upper;
      this.unit = requireText(unit, "unit");
      this.basis = requireText(basis, "basis");
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("lower", Double.valueOf(lower));
      result.put("nominal", Double.valueOf(nominal));
      result.put("upper", Double.valueOf(upper));
      result.put("unit", unit);
      result.put("basis", basis);
      return result;
    }
  }

  private final String calculationId;
  private final String method;
  private final String methodVersion;
  private final EngineeringCalculationContext context;
  private final CalculationReadiness readiness;
  private final Status status;
  private final T value;
  private final Map<String, Object> inputSnapshot;
  private final List<String> warnings;
  private final String message;
  private final Uncertainty uncertainty;

  private EngineeringCalculationResult(Builder<T> builder) {
    calculationId = requireText(builder.calculationId, "calculationId");
    method = requireText(builder.method, "method");
    methodVersion = requireText(builder.methodVersion, "methodVersion");
    context = builder.context == null ? EngineeringCalculationContext.builder().build() : builder.context;
    readiness = builder.readiness == null ? CalculationReadiness.ready() : builder.readiness;
    status = builder.status;
    value = builder.value;
    inputSnapshot = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(builder.inputSnapshot));
    warnings = Collections.unmodifiableList(new ArrayList<String>(builder.warnings));
    message = textOrEmpty(builder.message);
    uncertainty = builder.uncertainty;
  }

  public static <T> Builder<T> builder(String calculationId, String method, String methodVersion) {
    return new Builder<T>(calculationId, method, methodVersion);
  }

  public String getCalculationId() {
    return calculationId;
  }

  /**
   * Gets the stable calculation method identifier.
   *
   * @return method identifier
   */
  public String getMethod() {
    return method;
  }

  /**
   * Gets the calculation method version.
   *
   * @return method version
   */
  public String getMethodVersion() {
    return methodVersion;
  }

  /**
   * Gets the controlled execution context used by the calculation.
   *
   * @return calculation context containing case, standards, evidence, and method attributes
   */
  public EngineeringCalculationContext getContext() {
    return context;
  }

  public Status getStatus() {
    return status;
  }

  public T getValue() {
    return value;
  }

  public CalculationReadiness getReadiness() {
    return readiness;
  }

  /**
   * Gets the human-readable calculation outcome message.
   *
   * @return outcome message, or an empty string when none was supplied
   */
  public String getMessage() {
    return message;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("calculationId", calculationId);
    result.put("method", method);
    result.put("methodVersion", methodVersion);
    result.put("status", status.name());
    result.put("context", context.toMap());
    result.put("readiness", readiness.toMap());
    result.put("inputs", new LinkedHashMap<String, Object>(inputSnapshot));
    result.put("value", value);
    result.put("warnings", new ArrayList<String>(warnings));
    result.put("message", message);
    if (uncertainty != null) {
      result.put("uncertainty", uncertainty.toMap());
    }
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  /** Builder for a typed calculation result. */
  public static final class Builder<T> {
    private final String calculationId;
    private final String method;
    private final String methodVersion;
    private EngineeringCalculationContext context;
    private CalculationReadiness readiness;
    private Status status = Status.BLOCKED;
    private T value;
    private final Map<String, Object> inputSnapshot = new LinkedHashMap<String, Object>();
    private final List<String> warnings = new ArrayList<String>();
    private String message = "";
    private Uncertainty uncertainty;

    private Builder(String calculationId, String method, String methodVersion) {
      this.calculationId = calculationId;
      this.method = method;
      this.methodVersion = methodVersion;
    }

    public Builder<T> context(EngineeringCalculationContext value) {
      context = value;
      return this;
    }

    public Builder<T> readiness(CalculationReadiness value) {
      readiness = value;
      return this;
    }

    public Builder<T> status(Status value) {
      if (value == null) {
        throw new IllegalArgumentException("status must not be null");
      }
      status = value;
      return this;
    }

    public Builder<T> value(T resultValue) {
      value = resultValue;
      return this;
    }

    public Builder<T> input(String name, Object inputValue) {
      inputSnapshot.put(requireText(name, "input name"), inputValue);
      return this;
    }

    public Builder<T> warning(String value) {
      String normalized = textOrEmpty(value);
      if (!normalized.isEmpty()) {
        warnings.add(normalized);
      }
      return this;
    }

    public Builder<T> message(String value) {
      message = textOrEmpty(value);
      return this;
    }

    public Builder<T> uncertainty(Uncertainty value) {
      uncertainty = value;
      return this;
    }

    public EngineeringCalculationResult<T> build() {
      if (readiness != null && !readiness.isReady() && status != Status.BLOCKED && status != Status.FAILED) {
        throw new IllegalStateException("a calculation with readiness blockers cannot be calculated");
      }
      if ((status == Status.CALCULATED || status == Status.CALCULATED_REVIEW_REQUIRED) && value == null) {
        throw new IllegalStateException("a calculated result must contain a value");
      }
      return new EngineeringCalculationResult<T>(this);
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String textOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
