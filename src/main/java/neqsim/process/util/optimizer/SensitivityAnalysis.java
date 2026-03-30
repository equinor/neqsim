package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * One-dimensional parameter sensitivity analysis for process simulations.
 *
 * <p>
 * Varies a single process parameter across a range while tracking one or more output variables.
 * Each evaluation runs a fresh copy of the process system to avoid state contamination. This is the
 * workhorse utility for "what-if" engineering studies.
 * </p>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * ProcessSystem process = ...;
 * process.run(); // establish base case
 *
 * SensitivityAnalysis sa = new SensitivityAnalysis(process);
 * sa.setParameter("feed pressure", 30.0, 100.0, 8,
 *     p -&gt; p.getUnit("feed").getOutletStream().setPressure(sa.getCurrentValue(), "bara"));
 * sa.addOutput("compressor power (kW)",
 *     p -&gt; ((Compressor) p.getUnit("Compressor")).getPower("kW"));
 * sa.addOutput("outlet temperature (C)",
 *     p -&gt; p.getUnit("Compressor").getOutletStream().getTemperature("C"));
 *
 * SensitivityResult result = sa.run();
 * System.out.println(result.toJson());
 * String csv = result.toCSV();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see BatchStudy
 */
public class SensitivityAnalysis implements Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SensitivityAnalysis.class);

  /** Base process system to clone for each evaluation. */
  private final ProcessSystem baseProcess;

  /** Name of the varied parameter. */
  private String parameterName = "parameter";

  /** Minimum value of the parameter range. */
  private double minValue;

  /** Maximum value of the parameter range. */
  private double maxValue;

  /** Number of steps in the parameter sweep (inclusive of endpoints). */
  private int steps = 10;

  /** Action to apply the parameter value to a process copy. */
  private transient Consumer<ProcessSystem> parameterSetter;

  /** Current parameter value being set (for use by setter lambda). */
  private double currentValue;

  /** Named output extractors. */
  private final transient Map<String, Function<ProcessSystem, Double>> outputExtractors =
      new LinkedHashMap<>();

  /**
   * Creates a SensitivityAnalysis for the given base process.
   *
   * @param baseProcess the process system to use as a template (will be cloned for each run)
   */
  public SensitivityAnalysis(ProcessSystem baseProcess) {
    if (baseProcess == null) {
      throw new IllegalArgumentException("Base process cannot be null");
    }
    this.baseProcess = baseProcess;
  }

  /**
   * Defines the parameter to vary.
   *
   * @param name descriptive name of the parameter
   * @param minValue minimum value of the sweep range
   * @param maxValue maximum value of the sweep range
   * @param steps number of evaluation points (inclusive of endpoints)
   * @param setter action that applies the current parameter value to a process system copy; use
   *        {@link #getCurrentValue()} to get the value inside the setter
   * @return this for chaining
   */
  public SensitivityAnalysis setParameter(String name, double minValue, double maxValue, int steps,
      Consumer<ProcessSystem> setter) {
    this.parameterName = name;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.steps = Math.max(2, steps);
    this.parameterSetter = setter;
    return this;
  }

  /**
   * Defines the parameter to vary, with the value passed directly to the setter.
   *
   * <p>
   * This is a convenience overload that passes the current parameter value as the second argument
   * to the setter, avoiding the need to call {@link #getCurrentValue()}.
   * </p>
   *
   * @param name descriptive name of the parameter
   * @param minValue minimum value of the sweep range
   * @param maxValue maximum value of the sweep range
   * @param steps number of evaluation points (inclusive of endpoints)
   * @param setter action that receives the process copy and parameter value
   * @return this for chaining
   */
  public SensitivityAnalysis setParameter(String name, double minValue, double maxValue, int steps,
      BiConsumer<ProcessSystem, Double> setter) {
    this.parameterName = name;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.steps = Math.max(2, steps);
    this.parameterSetter = proc -> setter.accept(proc, currentValue);
    return this;
  }

  /**
   * Gets the current parameter value being evaluated.
   *
   * <p>
   * Use this inside the parameter setter lambda to get the value to apply.
   * </p>
   *
   * @return the current parameter value
   */
  public double getCurrentValue() {
    return currentValue;
  }

  /**
   * Adds an output variable to track during the sweep.
   *
   * @param name descriptive name of the output (e.g., "power (kW)")
   * @param extractor function that extracts the output value from a process system after it runs
   * @return this for chaining
   */
  public SensitivityAnalysis addOutput(String name, Function<ProcessSystem, Double> extractor) {
    outputExtractors.put(name, extractor);
    return this;
  }

  /**
   * Runs the sensitivity analysis.
   *
   * @return the results containing parameter values and all tracked outputs
   */
  public SensitivityResult run() {
    if (parameterSetter == null) {
      throw new IllegalStateException("No parameter setter defined. Call setParameter() first.");
    }
    if (outputExtractors.isEmpty()) {
      throw new IllegalStateException("No output extractors defined. Call addOutput() first.");
    }

    double[] parameterValues = new double[steps];
    double stepSize = (maxValue - minValue) / (steps - 1);
    for (int i = 0; i < steps; i++) {
      parameterValues[i] = minValue + i * stepSize;
    }

    Map<String, double[]> outputs = new LinkedHashMap<>();
    for (String name : outputExtractors.keySet()) {
      outputs.put(name, new double[steps]);
    }

    boolean[] succeeded = new boolean[steps];

    for (int i = 0; i < steps; i++) {
      currentValue = parameterValues[i];
      logger.debug("SensitivityAnalysis: {} = {} (step {}/{})", parameterName, currentValue, i + 1,
          steps);

      try {
        ProcessSystem copy = baseProcess.copy();
        parameterSetter.accept(copy);
        copy.run();

        for (Map.Entry<String, Function<ProcessSystem, Double>> entry : outputExtractors
            .entrySet()) {
          double value = entry.getValue().apply(copy);
          outputs.get(entry.getKey())[i] = value;
        }
        succeeded[i] = true;
      } catch (Exception e) {
        logger.warn("SensitivityAnalysis failed at {} = {}: {}", parameterName, currentValue,
            e.getMessage());
        for (String name : outputs.keySet()) {
          outputs.get(name)[i] = Double.NaN;
        }
        succeeded[i] = false;
      }
    }

    return new SensitivityResult(parameterName, parameterValues, outputs, succeeded);
  }

  /**
   * Results of a sensitivity analysis sweep.
   */
  public static class SensitivityResult implements Serializable {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** Name of the varied parameter. */
    private final String parameterName;

    /** Values of the varied parameter. */
    private final double[] parameterValues;

    /** Output values for each tracked variable. */
    private final Map<String, double[]> outputs;

    /** Whether each step succeeded. */
    private final boolean[] succeeded;

    /**
     * Creates a sensitivity result.
     *
     * @param parameterName name of varied parameter
     * @param parameterValues array of parameter values
     * @param outputs map of output name to value arrays
     * @param succeeded success flag per step
     */
    SensitivityResult(String parameterName, double[] parameterValues, Map<String, double[]> outputs,
        boolean[] succeeded) {
      this.parameterName = parameterName;
      this.parameterValues = parameterValues;
      this.outputs = outputs;
      this.succeeded = succeeded;
    }

    /**
     * Gets the parameter values used in the sweep.
     *
     * @return array of parameter values
     */
    public double[] getParameterValues() {
      return parameterValues;
    }

    /**
     * Gets the output values for a named output variable.
     *
     * @param outputName name of the output
     * @return array of output values, or null if not found
     */
    public double[] getOutput(String outputName) {
      return outputs.get(outputName);
    }

    /**
     * Gets all output names.
     *
     * @return list of output names
     */
    public List<String> getOutputNames() {
      return new ArrayList<>(outputs.keySet());
    }

    /**
     * Gets the number of evaluation points.
     *
     * @return number of steps
     */
    public int getSize() {
      return parameterValues.length;
    }

    /**
     * Gets the number of successful evaluations.
     *
     * @return count of succeeded evaluations
     */
    public int getSuccessCount() {
      int count = 0;
      for (boolean s : succeeded) {
        if (s) {
          count++;
        }
      }
      return count;
    }

    /**
     * Converts results to a JSON string.
     *
     * @return JSON representation
     */
    public String toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("parameterName", parameterName);
      json.addProperty("steps", parameterValues.length);
      json.addProperty("successCount", getSuccessCount());

      JsonArray valuesArray = new JsonArray();
      for (double v : parameterValues) {
        valuesArray.add(v);
      }
      json.add("parameterValues", valuesArray);

      JsonObject outputsJson = new JsonObject();
      for (Map.Entry<String, double[]> entry : outputs.entrySet()) {
        JsonArray arr = new JsonArray();
        for (double v : entry.getValue()) {
          arr.add(v);
        }
        outputsJson.add(entry.getKey(), arr);
      }
      json.add("outputs", outputsJson);

      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
          .toJson(json);
    }

    /**
     * Converts results to CSV format.
     *
     * @return CSV string with header row
     */
    public String toCSV() {
      StringBuilder sb = new StringBuilder();
      sb.append(parameterName);
      for (String name : outputs.keySet()) {
        sb.append(",").append(name);
      }
      sb.append(",succeeded\n");

      for (int i = 0; i < parameterValues.length; i++) {
        sb.append(parameterValues[i]);
        for (double[] values : outputs.values()) {
          sb.append(",").append(values[i]);
        }
        sb.append(",").append(succeeded[i]);
        sb.append("\n");
      }
      return sb.toString();
    }
  }
}
