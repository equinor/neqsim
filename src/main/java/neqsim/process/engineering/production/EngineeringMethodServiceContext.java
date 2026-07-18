package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Controlled project-service values used to evaluate a method applicability envelope. */
public final class EngineeringMethodServiceContext implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** One scalar service value with an explicit engineering unit. */
  public static final class Quantity implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double value;
    private final String unit;

    Quantity(double value, String unit) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("service value must be finite");
      }
      this.value = value;
      this.unit = text(unit, "unit");
    }

    public double getValue() {
      return value;
    }

    public String getUnit() {
      return unit;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("value", Double.valueOf(value));
      result.put("unit", unit);
      return result;
    }
  }

  private final EngineeringMethodQualification.IntendedUse intendedUse;
  private final Set<String> suppliedInputs = new LinkedHashSet<String>();
  private final Map<String, Quantity> numericValues = new LinkedHashMap<String, Quantity>();
  private final Map<String, String> categoricalValues = new LinkedHashMap<String, String>();

  public EngineeringMethodServiceContext(EngineeringMethodQualification.IntendedUse intendedUse) {
    if (intendedUse == null) {
      throw new IllegalArgumentException("intendedUse must not be null");
    }
    this.intendedUse = intendedUse;
  }

  /** Records that a required input is present without exposing its controlled value. */
  public EngineeringMethodServiceContext suppliedInput(String name) {
    suppliedInputs.add(text(name, "input name"));
    return this;
  }

  /** Records one numerical service condition. */
  public EngineeringMethodServiceContext numericValue(String name, double value, String unit) {
    String key = text(name, "numeric value name");
    numericValues.put(key, new Quantity(value, unit));
    suppliedInputs.add(key);
    return this;
  }

  /** Records one categorical service condition. */
  public EngineeringMethodServiceContext categoricalValue(String name, String value) {
    String key = text(name, "categorical value name");
    categoricalValues.put(key, text(value, "categorical value"));
    suppliedInputs.add(key);
    return this;
  }

  public EngineeringMethodQualification.IntendedUse getIntendedUse() {
    return intendedUse;
  }

  public Set<String> getSuppliedInputs() {
    return Collections.unmodifiableSet(suppliedInputs);
  }

  public Map<String, Quantity> getNumericValues() {
    return Collections.unmodifiableMap(numericValues);
  }

  public Map<String, String> getCategoricalValues() {
    return Collections.unmodifiableMap(categoricalValues);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("intendedUse", intendedUse.name());
    result.put("suppliedInputs", new LinkedHashSet<String>(suppliedInputs));
    Map<String, Object> numeric = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Quantity> value : numericValues.entrySet()) {
      numeric.put(value.getKey(), value.getValue().toMap());
    }
    result.put("numericValues", numeric);
    result.put("categoricalValues", new LinkedHashMap<String, String>(categoricalValues));
    return result;
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
