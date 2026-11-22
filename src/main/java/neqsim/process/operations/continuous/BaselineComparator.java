package neqsim.process.operations.continuous;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Compares current KPI values with a promoted baseline and raises step and criterion triggers.
 *
 * <p>
 * Step triggers fire when the absolute change from the baseline first exceeds its threshold; criterion triggers fire
 * when a value first satisfies a condition such as {@code "< 0.785"}. Both fire on crossing, so a persistent state
 * raises one trigger rather than one per cycle.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BaselineComparator implements Serializable {
  private static final long serialVersionUID = 1L;

  private final Map<String, Double> baseline = new LinkedHashMap<String, Double>();
  private final Map<String, Double> stepThresholds = new LinkedHashMap<String, Double>();
  private final Map<String, String> criteria = new LinkedHashMap<String, String>();
  private Map<String, Double> previous = new LinkedHashMap<String, Double>();

  /**
   * Creates a comparator for a promoted baseline.
   *
   * @param baselineKpis promoted KPI values, name to value
   */
  public BaselineComparator(Map<String, Double> baselineKpis) {
    if (baselineKpis != null) {
      baseline.putAll(baselineKpis);
    }
  }

  /**
   * Adds a step trigger.
   *
   * @param kpi KPI name
   * @param threshold absolute change from the baseline that raises the trigger, in the KPI unit
   * @return this comparator
   */
  public BaselineComparator addStepTrigger(String kpi, double threshold) {
    stepThresholds.put(kpi, threshold);
    return this;
  }

  /**
   * Adds a criterion trigger.
   *
   * @param kpi KPI name
   * @param condition condition of the form {@code "< 0.785"}, {@code ">= 3"}, operators {@code <, <=, >, >=}
   * @return this comparator
   * @throws IllegalArgumentException if the condition cannot be parsed
   */
  public BaselineComparator addCriterion(String kpi, String condition) {
    parse(condition);
    criteria.put(kpi, condition);
    return this;
  }

  /**
   * Compares one cycle of KPI values with the baseline.
   *
   * @param current KPI values of this cycle
   * @return JSON with a {@code kpis} table (value, baseline, delta) and a {@code triggers} array
   */
  public JsonObject compare(Map<String, Double> current) {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "1.0");
    JsonObject rows = new JsonObject();
    JsonArray triggers = new JsonArray();
    Map<String, Double> values = current == null ? new LinkedHashMap<String, Double>() : current;
    for (Map.Entry<String, Double> e : values.entrySet()) {
      JsonObject row = new JsonObject();
      row.addProperty("value", e.getValue());
      Double base = baseline.get(e.getKey());
      if (base != null && e.getValue() != null) {
        row.addProperty("baseline", base);
        row.addProperty("deltaVsBaseline", e.getValue() - base);
      }
      rows.add(e.getKey(), row);
    }
    for (Map.Entry<String, Double> e : stepThresholds.entrySet()) {
      Double base = baseline.get(e.getKey());
      Double now = values.get(e.getKey());
      Double before = previous.get(e.getKey());
      if (base == null || now == null) {
        continue;
      }
      boolean was = before != null && Math.abs(before - base) > e.getValue();
      if (Math.abs(now - base) > e.getValue() && !was) {
        triggers.add("kpi_step:" + e.getKey());
      }
    }
    for (Map.Entry<String, String> e : criteria.entrySet()) {
      if (hit(values.get(e.getKey()), e.getValue()) && !hit(previous.get(e.getKey()), e.getValue())) {
        triggers.add("criterion:" + e.getKey());
      }
    }
    previous = new LinkedHashMap<String, Double>(values);
    root.add("kpis", rows);
    root.add("triggers", triggers);
    return root;
  }

  /**
   * Evaluates a criterion.
   *
   * @param value value to test, may be null
   * @param condition condition such as {@code "< 0.785"}
   * @return true if the value is non-null and satisfies the condition
   */
  static boolean hit(Double value, String condition) {
    if (value == null || Double.isNaN(value)) {
      return false;
    }
    Object[] parsed = parse(condition);
    String op = (String) parsed[0];
    double limit = (Double) parsed[1];
    if ("<".equals(op)) {
      return value < limit;
    } else if ("<=".equals(op)) {
      return value <= limit;
    } else if (">".equals(op)) {
      return value > limit;
    }
    return value >= limit;
  }

  /**
   * Parses a condition into operator and limit.
   *
   * @param condition condition text
   * @return two-element array: operator string and limit as Double
   * @throws IllegalArgumentException if the text is not a supported condition
   */
  private static Object[] parse(String condition) {
    String text = condition == null ? "" : condition.trim();
    String[] ops = {"<=", ">=", "<", ">"};
    for (String op : ops) {
      if (text.startsWith(op)) {
        try {
          return new Object[] {op, Double.valueOf(text.substring(op.length()).trim())};
        } catch (NumberFormatException ex) {
          break;
        }
      }
    }
    throw new IllegalArgumentException(
        "Unsupported condition '" + condition + "'; use e.g. '< 0.785' with one of <, <=, >, >=");
  }
}
