package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Current scalar state of an iterative engineering design. */
public final class EngineeringDesignState implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final Map<String, EngineeringDesignValue> values = new LinkedHashMap<String, EngineeringDesignValue>();

  public EngineeringDesignValue get(String key) {
    return values.get(key);
  }

  public double requireValue(String key) {
    EngineeringDesignValue value = values.get(key);
    if (value == null) {
      throw new IllegalArgumentException("Unknown engineering design value " + key);
    }
    return value.getValue();
  }

  public boolean contains(String key) {
    return values.containsKey(key);
  }

  void put(EngineeringDesignValue value) {
    values.put(value.getKey(), value);
  }

  public Map<String, EngineeringDesignValue> getValues() {
    return Collections.unmodifiableMap(values);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, EngineeringDesignValue> entry : values.entrySet()) {
      result.put(entry.getKey(), entry.getValue().toMap());
    }
    return result;
  }
}
