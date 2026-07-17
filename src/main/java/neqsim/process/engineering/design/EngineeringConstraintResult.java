package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Result of checking one declared engineering constraint. */
public final class EngineeringConstraintResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String id;
  private final String description;
  private final boolean satisfied;
  private final double actual;
  private final double limit;
  private final String unit;
  private final String comparison;

  public EngineeringConstraintResult(String id, String description, boolean satisfied, double actual, double limit,
      String unit, String comparison) {
    this.id = id;
    this.description = description;
    this.satisfied = satisfied;
    this.actual = actual;
    this.limit = limit;
    this.unit = unit;
    this.comparison = comparison;
  }

  public boolean isSatisfied() {
    return satisfied;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("description", description);
    result.put("satisfied", Boolean.valueOf(satisfied));
    result.put("actual", Double.valueOf(actual));
    result.put("limit", Double.valueOf(limit));
    result.put("unit", unit);
    result.put("comparison", comparison);
    result.put("margin", Double.valueOf("MAXIMUM".equals(comparison) ? limit - actual : actual - limit));
    return result;
  }
}
