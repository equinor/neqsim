package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable readiness assessment performed before an engineering calculation. */
public final class CalculationReadiness implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** One actionable readiness finding. */
  public static final class Finding implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String code;
    private final String message;
    private final String requiredAction;

    private Finding(String code, String message, String requiredAction) {
      this.code = requireText(code, "code");
      this.message = requireText(message, "message");
      this.requiredAction = textOrEmpty(requiredAction);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("code", code);
      result.put("message", message);
      result.put("requiredAction", requiredAction);
      return result;
    }
  }

  private final List<Finding> blockers;
  private final List<Finding> warnings;

  private CalculationReadiness(Builder builder) {
    blockers = Collections.unmodifiableList(new ArrayList<Finding>(builder.blockers));
    warnings = Collections.unmodifiableList(new ArrayList<Finding>(builder.warnings));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static CalculationReadiness ready() {
    return builder().build();
  }

  public boolean isReady() {
    return blockers.isEmpty();
  }

  public boolean requiresReview() {
    return !warnings.isEmpty();
  }

  public List<Finding> getBlockers() {
    return blockers;
  }

  public List<Finding> getWarnings() {
    return warnings;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("ready", Boolean.valueOf(isReady()));
    result.put("requiresReview", Boolean.valueOf(requiresReview()));
    result.put("blockers", maps(blockers));
    result.put("warnings", maps(warnings));
    return result;
  }

  private static List<Map<String, Object>> maps(List<Finding> findings) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Finding finding : findings) {
      result.add(finding.toMap());
    }
    return result;
  }

  /** Builder for a readiness result. */
  public static final class Builder {
    private final List<Finding> blockers = new ArrayList<Finding>();
    private final List<Finding> warnings = new ArrayList<Finding>();

    public Builder addBlocker(String code, String message, String requiredAction) {
      blockers.add(new Finding(code, message, requiredAction));
      return this;
    }

    public Builder addWarning(String code, String message, String requiredAction) {
      warnings.add(new Finding(code, message, requiredAction));
      return this;
    }

    public Builder merge(CalculationReadiness other) {
      if (other != null) {
        blockers.addAll(other.blockers);
        warnings.addAll(other.warnings);
      }
      return this;
    }

    public CalculationReadiness build() {
      return new CalculationReadiness(this);
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
