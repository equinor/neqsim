package neqsim.process.research;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured metrics for a process synthesis candidate.
 *
 * <p>
 * The metrics object keeps process synthesis ranking transparent by separating product recovery,
 * purity, energy, heat integration, cost proxy, emissions, complexity, and robustness terms before
 * they are collapsed into a single score. Values are stored with unit-bearing keys such as
 * {@code totalPower_kW} and {@code hotUtility_kW} so downstream agents and notebooks can inspect
 * the basis of a ranking decision.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessResearchMetrics {
  private final Map<String, Double> values = new LinkedHashMap<String, Double>();

  /**
   * Creates an empty metrics container.
   */
  public ProcessResearchMetrics() {}

  /**
   * Sets a metric value.
   *
   * @param name metric name including units where relevant
   * @param value metric value
   * @return this metrics object
   */
  public ProcessResearchMetrics set(String name, double value) {
    values.put(name, value);
    return this;
  }

  /**
   * Adds to a metric value.
   *
   * @param name metric name including units where relevant
   * @param value value to add
   * @return this metrics object
   */
  public ProcessResearchMetrics add(String name, double value) {
    values.put(name, get(name, 0.0) + value);
    return this;
  }

  /**
   * Gets a metric value.
   *
   * @param name metric name
   * @param defaultValue value returned when the metric is absent
   * @return metric value or default value
   */
  public double get(String name, double defaultValue) {
    Double value = values.get(name);
    return value == null ? defaultValue : value.doubleValue();
  }

  /**
   * Gets all metric values.
   *
   * @return unmodifiable metric map
   */
  public Map<String, Double> asMap() {
    return Collections.unmodifiableMap(values);
  }
}
