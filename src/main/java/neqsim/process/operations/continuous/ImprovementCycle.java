package neqsim.process.operations.continuous;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.automation.ProcessAutomation;

/**
 * One improvement cycle of a living task, executed on a NeqSim model through {@link ProcessAutomation}.
 *
 * <p>
 * Each call to {@link #runCycle(Map, String)} applies the measured inputs of the cycle as setpoints, runs the model to
 * convergence with {@link ProcessAutomation#evaluate}, reads the KPIs (each in its own unit), feeds them to a
 * {@link ModelDriftMonitor}, compares them with the promoted baseline through a {@link BaselineComparator}, and
 * attaches the capacity snapshot. The result is one schema-versioned JSON object and the method never throws, so a
 * scheduler or agent can run it unattended.
 * </p>
 *
 * <pre>
 * ImprovementCycle cycle = new ImprovementCycle(process.getAutomation());
 * cycle.addKpi("Compressor.power", "kW");
 * cycle.getComparator().addCriterion("Compressor.power", "&gt; 5000");
 * String json = cycle.runCycle(measurements, "bara");
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ImprovementCycle implements Serializable {
  private static final long serialVersionUID = 1L;

  private final transient ProcessAutomation automation;
  private final Map<String, String> kpis = new LinkedHashMap<String, String>();
  private ModelDriftMonitor driftMonitor = new ModelDriftMonitor();
  private BaselineComparator comparator = new BaselineComparator(null);
  private int maxIterations = 30;
  private double tolerance = 5.0e-3;
  private int cycleCount;

  /**
   * Creates a cycle runner on an automation facade.
   *
   * @param automation automation facade of a ProcessSystem or ProcessModel, not null
   * @throws IllegalArgumentException if {@code automation} is null
   */
  public ImprovementCycle(ProcessAutomation automation) {
    if (automation == null) {
      throw new IllegalArgumentException("automation must not be null");
    }
    this.automation = automation;
  }

  /**
   * Adds a KPI read after each run.
   *
   * @param address automation address, for example {@code "HP Sep.gasOutStream.flowRate"}
   * @param unit unit of the KPI, or null for the variable's default unit
   * @return this cycle
   */
  public ImprovementCycle addKpi(String address, String unit) {
    kpis.put(address, unit);
    return this;
  }

  /**
   * Replaces the drift monitor.
   *
   * @param monitor configured drift monitor
   * @return this cycle
   */
  public ImprovementCycle setDriftMonitor(ModelDriftMonitor monitor) {
    this.driftMonitor = monitor;
    return this;
  }

  /**
   * Replaces the baseline comparator (for example after a baseline promotion).
   *
   * @param baselineComparator comparator built on the promoted baseline
   * @return this cycle
   */
  public ImprovementCycle setComparator(BaselineComparator baselineComparator) {
    this.comparator = baselineComparator;
    return this;
  }

  /**
   * Returns the baseline comparator.
   *
   * @return the comparator used by each cycle
   */
  public BaselineComparator getComparator() {
    return comparator;
  }

  /**
   * Returns the drift monitor.
   *
   * @return the drift monitor used by each cycle
   */
  public ModelDriftMonitor getDriftMonitor() {
    return driftMonitor;
  }

  /**
   * Sets the convergence settings passed to {@link ProcessAutomation#evaluate}.
   *
   * @param iterations maximum outer iterations, at least 1
   * @param relativeTolerance relative tolerance, finite and positive
   * @return this cycle
   */
  public ImprovementCycle setConvergence(int iterations, double relativeTolerance) {
    this.maxIterations = iterations;
    this.tolerance = relativeTolerance;
    return this;
  }

  /**
   * Runs one cycle.
   *
   * @param measuredInputs measured model inputs of this cycle, address to value; may be null
   * @param inputUnit unit of all inputs, or null for each variable's default unit
   * @return schema-versioned JSON with {@code feasible}, {@code kpis}, {@code drift}, {@code comparison},
   * {@code triggers}, {@code bottleneck} and any {@code errors}
   */
  public String runCycle(Map<String, Double> measuredInputs, String inputUnit) {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "1.0");
    root.addProperty("cycle", ++cycleCount);
    JsonArray triggers = new JsonArray();
    JsonObject errors = new JsonObject();
    boolean feasible = false;
    try {
      JsonObject trial = JsonParser
          .parseString(automation.evaluate(measuredInputs, inputUnit, null, null, maxIterations, tolerance))
          .getAsJsonObject();
      feasible = trial.get("feasible").getAsBoolean();
      root.add("setpointsRejected", trial.get("setpointsRejected"));
    } catch (RuntimeException ex) {
      errors.addProperty("evaluate", String.valueOf(ex.getMessage()));
    }
    root.addProperty("feasible", feasible);

    Map<String, Double> values = new LinkedHashMap<String, Double>();
    JsonObject kpiJson = new JsonObject();
    JsonObject drift = new JsonObject();
    if (feasible) {
      for (Map.Entry<String, String> e : kpis.entrySet()) {
        try {
          double value = automation.getVariableValue(e.getKey(), e.getValue());
          values.put(e.getKey(), value);
          kpiJson.addProperty(e.getKey(), value);
          JsonObject status = driftMonitor.update(e.getKey(), value);
          drift.add(e.getKey(), status);
          if (status.get("newAlarm").getAsBoolean()) {
            triggers.add("drift:" + e.getKey());
          }
        } catch (RuntimeException ex) {
          errors.addProperty(e.getKey(), String.valueOf(ex.getMessage()));
        }
      }
    } else {
      triggers.add("model:infeasible");
    }
    root.add("kpis", kpiJson);
    root.add("drift", drift);
    JsonObject comparison = comparator.compare(values);
    for (JsonElement t : comparison.getAsJsonArray("triggers")) {
      triggers.add(t);
    }
    root.add("comparison", comparison.get("kpis"));
    try {
      JsonObject snapshot = JsonParser.parseString(automation.getUtilizationSnapshot()).getAsJsonObject();
      root.add("bottleneck", snapshot.get("bottleneck"));
      root.add("anyOverloaded", snapshot.get("anyOverloaded"));
    } catch (RuntimeException ex) {
      errors.addProperty("utilizationSnapshot", String.valueOf(ex.getMessage()));
    }
    root.add("triggers", triggers);
    root.add("errors", errors);
    return root.toString();
  }
}
