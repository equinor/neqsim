package neqsim.process.engineering.safety;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Measured process response checked against a controlled safety-study limit. */
public final class ProcessSafetyConstraint implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Direction in which the measured value must remain acceptable. */
  public enum LimitType {
    /** Measured value must not exceed the limit. */
    MAXIMUM,
    /** Measured value must not fall below the limit. */
    MINIMUM
  }

  private final String id;
  private final String name;
  private final String unit;
  private final LimitType limitType;
  private final double measuredValue;
  private final double limitValue;
  private final boolean required;
  private final String evidenceReference;

  private ProcessSafetyConstraint(String id, String name, String unit, LimitType limitType, double measuredValue,
      double limitValue, boolean required, String evidenceReference) {
    this.id = requireText(id, "id");
    this.name = requireText(name, "name");
    this.unit = requireText(unit, "unit");
    if (limitType == null) {
      throw new IllegalArgumentException("limitType is required");
    }
    this.limitType = limitType;
    this.measuredValue = finite(measuredValue, "measuredValue");
    this.limitValue = finite(limitValue, "limitValue");
    this.required = required;
    this.evidenceReference = normalize(evidenceReference);
  }

  /**
   * Creates a required maximum-value constraint.
   *
   * @param id controlled constraint identifier
   * @param name descriptive constraint name
   * @param unit engineering unit
   * @param measuredValue calculated or measured value
   * @param maximumValue largest acceptable value
   * @param evidenceReference controlled source for the value and limit
   * @return immutable constraint
   */
  public static ProcessSafetyConstraint maximum(String id, String name, String unit, double measuredValue,
      double maximumValue, String evidenceReference) {
    return new ProcessSafetyConstraint(id, name, unit, LimitType.MAXIMUM, measuredValue, maximumValue, true,
        evidenceReference);
  }

  /**
   * Creates a required minimum-value constraint.
   *
   * @param id controlled constraint identifier
   * @param name descriptive constraint name
   * @param unit engineering unit
   * @param measuredValue calculated or measured value
   * @param minimumValue smallest acceptable value
   * @param evidenceReference controlled source for the value and limit
   * @return immutable constraint
   */
  public static ProcessSafetyConstraint minimum(String id, String name, String unit, double measuredValue,
      double minimumValue, String evidenceReference) {
    return new ProcessSafetyConstraint(id, name, unit, LimitType.MINIMUM, measuredValue, minimumValue, true,
        evidenceReference);
  }

  /** @return whether the measured value is on the acceptable side of the limit */
  public boolean isMet() {
    return limitType == LimitType.MAXIMUM ? measuredValue <= limitValue : measuredValue >= limitValue;
  }

  /** @return whether the constraint has a controlled evidence reference */
  public boolean isEvidenceComplete() {
    return !evidenceReference.isEmpty();
  }

  /** @return whether this constraint is mandatory for the technical verdict */
  public boolean isRequired() {
    return required;
  }

  /** @return stable constraint identifier */
  public String getId() {
    return id;
  }

  /** @return structured constraint evidence */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("name", name);
    result.put("unit", unit);
    result.put("limitType", limitType.name());
    result.put("measuredValue", Double.valueOf(measuredValue));
    result.put("limitValue", Double.valueOf(limitValue));
    result.put("required", Boolean.valueOf(required));
    result.put("met", Boolean.valueOf(isMet()));
    result.put("evidenceReference", evidenceReference);
    return result;
  }

  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value;
  }

  private static String requireText(String value, String field) {
    String normalized = normalize(value);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return normalized;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
