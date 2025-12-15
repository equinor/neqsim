package neqsim.ml;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standardized action vector for RL control integration.
 *
 * <p>
 * Represents control actions that can be applied to process equipment. Actions are defined with
 * physical bounds to ensure safe operation.
 *
 * @author ESOL
 * @version 1.0
 */
public class ActionVector implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Map<String, Double> values;
  private final Map<String, Double> lowerBounds;
  private final Map<String, Double> upperBounds;
  private final Map<String, String> units;

  /**
   * Create an empty action vector.
   */
  public ActionVector() {
    this.values = new LinkedHashMap<>();
    this.lowerBounds = new LinkedHashMap<>();
    this.upperBounds = new LinkedHashMap<>();
    this.units = new LinkedHashMap<>();
  }

  /**
   * Define an action dimension with bounds.
   *
   * @param name action name (e.g., "valve_opening", "setpoint")
   * @param lowerBound minimum allowed value
   * @param upperBound maximum allowed value
   * @param unit unit string
   * @return this ActionVector for chaining
   */
  public ActionVector define(String name, double lowerBound, double upperBound, String unit) {
    values.put(name, (lowerBound + upperBound) / 2.0); // Default to midpoint
    lowerBounds.put(name, lowerBound);
    upperBounds.put(name, upperBound);
    units.put(name, unit);
    return this;
  }

  /**
   * Set an action value, clamping to bounds.
   *
   * @param name action name
   * @param value desired value (will be clamped to bounds)
   * @return this ActionVector for chaining
   */
  public ActionVector set(String name, double value) {
    if (!values.containsKey(name)) {
      throw new IllegalArgumentException("Unknown action: " + name);
    }
    double lb = lowerBounds.get(name);
    double ub = upperBounds.get(name);
    values.put(name, Math.max(lb, Math.min(ub, value)));
    return this;
  }

  /**
   * Set action from normalized value [0, 1].
   *
   * @param name action name
   * @param normalizedValue value in [0, 1] range
   * @return this ActionVector for chaining
   */
  public ActionVector setNormalized(String name, double normalizedValue) {
    if (!values.containsKey(name)) {
      throw new IllegalArgumentException("Unknown action: " + name);
    }
    double lb = lowerBounds.get(name);
    double ub = upperBounds.get(name);
    double value = lb + normalizedValue * (ub - lb);
    values.put(name, value);
    return this;
  }

  /**
   * Set all actions from normalized array.
   *
   * @param normalizedValues array of values in [0, 1] in definition order
   * @return this ActionVector for chaining
   */
  public ActionVector setFromNormalizedArray(double[] normalizedValues) {
    String[] names = values.keySet().toArray(new String[0]);
    if (normalizedValues.length != names.length) {
      throw new IllegalArgumentException(
          "Array length " + normalizedValues.length + " != action dimension " + names.length);
    }
    for (int i = 0; i < names.length; i++) {
      setNormalized(names[i], normalizedValues[i]);
    }
    return this;
  }

  /**
   * Get action value in physical units.
   *
   * @param name action name
   * @return value in physical units
   */
  public double get(String name) {
    return values.getOrDefault(name, Double.NaN);
  }

  /**
   * Get all action names.
   *
   * @return array of action names
   */
  public String[] getActionNames() {
    return values.keySet().toArray(new String[0]);
  }

  /**
   * Get number of action dimensions.
   *
   * @return action count
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
   * Get all values as array.
   *
   * @return array of action values
   */
  public double[] toArray() {
    return values.values().stream().mapToDouble(Double::doubleValue).toArray();
  }

  /**
   * Check if an action value is at its bounds.
   *
   * @param name action name
   * @return true if at lower or upper bound
   */
  public boolean isAtBound(String name) {
    double val = values.getOrDefault(name, Double.NaN);
    double lb = lowerBounds.getOrDefault(name, Double.NaN);
    double ub = upperBounds.getOrDefault(name, Double.NaN);
    double tol = (ub - lb) * 1e-6;
    return Math.abs(val - lb) < tol || Math.abs(val - ub) < tol;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ActionVector[");
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
