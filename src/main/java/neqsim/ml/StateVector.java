package neqsim.ml;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standardized state vector for physics-grounded world models and RL integration.
 *
 * <p>
 * Provides a flat, normalized representation of process equipment state suitable for:
 * <ul>
 * <li>Reinforcement Learning observations</li>
 * <li>Neural network surrogate models</li>
 * <li>Multi-agent coordination</li>
 * <li>Real-time control systems</li>
 * </ul>
 *
 * <p>
 * Design principles:
 * <ul>
 * <li>All values normalized to [0, 1] or [-1, 1] for ML compatibility</li>
 * <li>Physical bounds preserved for constraint checking</li>
 * <li>Named features for explainability</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class StateVector implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Map<String, Double> values;
  private final Map<String, Double> lowerBounds;
  private final Map<String, Double> upperBounds;
  private final Map<String, String> units;
  private final long timestampMs;

  /**
   * Create an empty state vector.
   */
  public StateVector() {
    this.values = new LinkedHashMap<>();
    this.lowerBounds = new LinkedHashMap<>();
    this.upperBounds = new LinkedHashMap<>();
    this.units = new LinkedHashMap<>();
    this.timestampMs = System.currentTimeMillis();
  }

  /**
   * Add a state variable with bounds.
   *
   * @param name variable name (e.g., "temperature", "pressure")
   * @param value current value in physical units
   * @param lowerBound physical lower bound
   * @param upperBound physical upper bound
   * @param unit unit string (e.g., "K", "bar", "kg/s")
   * @return this StateVector for chaining
   */
  public StateVector add(String name, double value, double lowerBound, double upperBound,
      String unit) {
    values.put(name, value);
    lowerBounds.put(name, lowerBound);
    upperBounds.put(name, upperBound);
    units.put(name, unit);
    return this;
  }

  /**
   * Add a state variable without explicit bounds (uses value as reference).
   *
   * @param name variable name
   * @param value current value
   * @param unit unit string
   * @return this StateVector for chaining
   */
  public StateVector add(String name, double value, String unit) {
    return add(name, value, 0.0, value * 2.0 + 1.0, unit);
  }

  /**
   * Get raw value in physical units.
   *
   * @param name variable name
   * @return value in physical units
   */
  public double getValue(String name) {
    return values.getOrDefault(name, Double.NaN);
  }

  /**
   * Get normalized value in [0, 1] range.
   *
   * @param name variable name
   * @return normalized value
   */
  public double getNormalized(String name) {
    double val = values.getOrDefault(name, Double.NaN);
    double lb = lowerBounds.getOrDefault(name, 0.0);
    double ub = upperBounds.getOrDefault(name, 1.0);
    if (ub <= lb) {
      return 0.5;
    }
    return Math.max(0.0, Math.min(1.0, (val - lb) / (ub - lb)));
  }

  /**
   * Get all values as a flat array (for neural networks).
   *
   * @return array of raw values in insertion order
   */
  public double[] toArray() {
    return values.values().stream().mapToDouble(Double::doubleValue).toArray();
  }

  /**
   * Get all normalized values as a flat array.
   *
   * @return array of normalized values in [0, 1]
   */
  public double[] toNormalizedArray() {
    String[] names = values.keySet().toArray(new String[0]);
    double[] result = new double[names.length];
    for (int i = 0; i < names.length; i++) {
      result[i] = getNormalized(names[i]);
    }
    return result;
  }

  /**
   * Get feature names in order.
   *
   * @return array of feature names
   */
  public String[] getFeatureNames() {
    return values.keySet().toArray(new String[0]);
  }

  /**
   * Get number of features.
   *
   * @return feature count
   */
  public int size() {
    return values.size();
  }

  /**
   * Get lower bounds array.
   *
   * @return array of lower bounds
   */
  public double[] getLowerBounds() {
    return lowerBounds.values().stream().mapToDouble(Double::doubleValue).toArray();
  }

  /**
   * Get upper bounds array.
   *
   * @return array of upper bounds
   */
  public double[] getUpperBounds() {
    return upperBounds.values().stream().mapToDouble(Double::doubleValue).toArray();
  }

  /**
   * Get timestamp when this state was captured.
   *
   * @return timestamp in milliseconds
   */
  public long getTimestampMs() {
    return timestampMs;
  }

  /**
   * Convert to JSON-like map for serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("timestamp_ms", timestampMs);

    Map<String, Object> features = new LinkedHashMap<>();
    for (String name : values.keySet()) {
      Map<String, Object> feature = new LinkedHashMap<>();
      feature.put("value", values.get(name));
      feature.put("normalized", getNormalized(name));
      feature.put("lower_bound", lowerBounds.get(name));
      feature.put("upper_bound", upperBounds.get(name));
      feature.put("unit", units.get(name));
      features.put(name, feature);
    }
    result.put("features", features);

    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("StateVector[");
    boolean first = true;
    for (String name : values.keySet()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(name).append("=").append(String.format("%.4f", values.get(name)));
      sb.append(" ").append(units.get(name));
      first = false;
    }
    sb.append("]");
    return sb.toString();
  }
}
