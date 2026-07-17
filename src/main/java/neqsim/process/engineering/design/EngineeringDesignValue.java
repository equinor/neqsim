package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** One traceable scalar in the evolving engineering design state. */
public final class EngineeringDesignValue implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String key;
  private final double value;
  private final String unit;
  private final String sourceModule;
  private final String governingCaseId;
  private final int iteration;

  public EngineeringDesignValue(String key, double value, String unit, String sourceModule, String governingCaseId,
      int iteration) {
    this.key = requireText(key, "key");
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("value must be finite");
    }
    this.value = value;
    this.unit = requireText(unit, "unit");
    this.sourceModule = requireText(sourceModule, "sourceModule");
    this.governingCaseId = governingCaseId == null ? "" : governingCaseId.trim();
    this.iteration = iteration;
  }

  public String getKey() {
    return key;
  }

  public double getValue() {
    return value;
  }

  public String getUnit() {
    return unit;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("key", key);
    result.put("value", Double.valueOf(value));
    result.put("unit", unit);
    result.put("sourceModule", sourceModule);
    result.put("governingCaseId", governingCaseId);
    result.put("iteration", Integer.valueOf(iteration));
    return result;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
