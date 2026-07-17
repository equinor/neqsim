package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** One selectable physical design candidate such as a pipe size, valve Cv, or driver rating. */
public final class DesignCandidate implements Serializable, Comparable<DesignCandidate> {
  private static final long serialVersionUID = 1000L;
  private final String id;
  private final double value;
  private final String unit;

  public DesignCandidate(String id, double value, String unit) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("value must be finite");
    }
    if (unit == null || unit.trim().isEmpty()) {
      throw new IllegalArgumentException("unit must not be blank");
    }
    this.id = id.trim();
    this.value = value;
    this.unit = unit.trim();
  }

  public String getId() {
    return id;
  }

  public double getValue() {
    return value;
  }

  public String getUnit() {
    return unit;
  }

  @Override
  public int compareTo(DesignCandidate other) {
    int valueComparison = Double.compare(value, other.value);
    return valueComparison == 0 ? id.compareTo(other.id) : valueComparison;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("value", Double.valueOf(value));
    result.put("unit", unit);
    return result;
  }
}
