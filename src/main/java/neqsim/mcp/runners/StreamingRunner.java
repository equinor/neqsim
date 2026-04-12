package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Streaming simulation runner that executes long-running operations asynchronously and provides
 * incremental result polling.
 *
 * <p>
 * Supports: convergence monitoring during flash sweeps, parametric studies with per-case results,
 * dynamic simulation time-step streaming, and Monte Carlo progress. Agents poll for intermediate
 * results while the computation proceeds in background.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class StreamingRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Background thread pool for async simulations. */
  private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

  /** Active streaming operations. */
  private static final ConcurrentHashMap<String, StreamingOperation> OPERATIONS =
      new ConcurrentHashMap<String, StreamingOperation>();

  /** Max concurrent streaming operations. */
  private static final int MAX_OPERATIONS = 20;

  /**
   * Private constructor — all methods are static.
   */
  private StreamingRunner() {}

  /**
   * Main entry point for streaming operations.
   *
   * @param json JSON with action (start, poll, cancel, list) and details
   * @return JSON with operation status and results
   */
  public static String run(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "";

      switch (action) {
        case "startSweep":
          return startParametricSweep(input);
        case "startDynamic":
          return startDynamicStreaming(input);
        case "startMonteCarlo":
          return startMonteCarlo(input);
        case "poll":
          return pollResults(input);
        case "cancel":
          return cancelOperation(input);
        case "list":
          return listOperations();
        default:
          return errorJson("UNKNOWN_ACTION", "Unknown streaming action: " + action,
              "Use: startSweep, startDynamic, startMonteCarlo, poll, cancel, list");
      }
    } catch (Exception e) {
      return errorJson("STREAMING_ERROR", "Streaming operation failed: " + e.getMessage(),
          "Check JSON format");
    }
  }

  /**
   * Starts a parametric sweep that reports results incrementally.
   *
   * @param input the sweep configuration
   * @return JSON with operation ID
   */
  private static String startParametricSweep(JsonObject input) {
    if (OPERATIONS.size() >= MAX_OPERATIONS) {
      return errorJson("LIMIT_REACHED", "Max streaming operations reached", "Cancel some first");
    }

    String opId = "sweep-" + UUID.randomUUID().toString().substring(0, 8);
    StreamingOperation op = new StreamingOperation(opId, "parametric_sweep");

    // Parse sweep parameters
    JsonObject components =
        input.has("components") ? input.getAsJsonObject("components") : new JsonObject();
    String model = input.has("model") ? input.get("model").getAsString() : "SRK";
    String sweepVar =
        input.has("sweepVariable") ? input.get("sweepVariable").getAsString() : "temperature";
    double from = input.has("from") ? input.get("from").getAsDouble() : 0;
    double to = input.has("to") ? input.get("to").getAsDouble() : 100;
    int points = input.has("points") ? input.get("points").getAsInt() : 20;
    String unit = input.has("unit") ? input.get("unit").getAsString() : "C";

    // Fixed conditions
    double fixedTemp =
        input.has("fixedTemperature") ? input.get("fixedTemperature").getAsDouble() : 25.0;
    String fixedTempUnit =
        input.has("fixedTemperatureUnit") ? input.get("fixedTemperatureUnit").getAsString() : "C";
    double fixedPressure =
        input.has("fixedPressure") ? input.get("fixedPressure").getAsDouble() : 1.0;
    String fixedPressureUnit =
        input.has("fixedPressureUnit") ? input.get("fixedPressureUnit").getAsString() : "bara";

    op.totalSteps = points;
    OPERATIONS.put(opId, op);

    // Run in background
    EXECUTOR.submit(() -> {
      try {
        for (int i = 0; i < points && !op.cancelled; i++) {
          double val = from + (to - from) * i / (Math.max(points - 1, 1));

          double tempK;
          double pressBar;

          if ("temperature".equalsIgnoreCase(sweepVar)) {
            tempK = convertToKelvin(val, unit);
            pressBar = convertToBara(fixedPressure, fixedPressureUnit);
          } else {
            tempK = convertToKelvin(fixedTemp, fixedTempUnit);
            pressBar = convertToBara(val, unit);
          }

          SystemInterface fluid = FlashRunner.createFluid(model, tempK, pressBar);
          for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
            fluid.addComponent(entry.getKey(), entry.getValue().getAsDouble());
          }
          fluid.setMixingRule("classic");
          ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
          ops.TPflash();
          fluid.initProperties();

          // Build result for this point
          JsonObject point = new JsonObject();
          point.addProperty("index", i);
          point.addProperty("sweepValue", val);
          point.addProperty("sweepUnit", unit);
          point.addProperty("temperature_K", fluid.getTemperature());
          point.addProperty("pressure_bara", fluid.getPressure());
          point.addProperty("density_kg_m3", fluid.getDensity("kg/m3"));
          point.addProperty("numberOfPhases", fluid.getNumberOfPhases());
          point.addProperty("compressibilityZ", fluid.getZ());
          point.addProperty("enthalpy_J_mol", fluid.getEnthalpy());
          point.addProperty("entropy_J_molK", fluid.getEntropy());

          if (fluid.getNumberOfPhases() > 0) {
            point.addProperty("phase0_type", fluid.getPhase(0).getPhaseTypeName());
            point.addProperty("phase0_fraction", fluid.getPhase(0).getBeta());
          }

          op.addResult(point);
          op.completedSteps = i + 1;
          op.status = "running";
        }

        op.status = op.cancelled ? "cancelled" : "completed";
      } catch (Exception e) {
        op.status = "failed";
        op.errorMessage = e.getMessage();
      }
    });

    JsonObject response = new JsonObject();
    response.addProperty("status", "started");
    response.addProperty("operationId", opId);
    response.addProperty("type", "parametric_sweep");
    response.addProperty("totalPoints", points);
    response.addProperty("message",
        "Use action 'poll' with this operationId to get incremental results");
    return GSON.toJson(response);
  }

  /**
   * Starts a dynamic simulation that streams time-step results.
   *
   * @param input the dynamic simulation configuration
   * @return JSON with operation ID
   */
  private static String startDynamicStreaming(JsonObject input) {
    if (OPERATIONS.size() >= MAX_OPERATIONS) {
      return errorJson("LIMIT_REACHED", "Max streaming operations reached", "Cancel some first");
    }

    String opId = "dynamic-" + UUID.randomUUID().toString().substring(0, 8);
    StreamingOperation op = new StreamingOperation(opId, "dynamic_simulation");

    String processJson = input.has("processJson") ? GSON.toJson(input.get("processJson")) : "{}";
    double totalTime = input.has("totalTime") ? input.get("totalTime").getAsDouble() : 3600.0;
    double timeStep = input.has("timeStep") ? input.get("timeStep").getAsDouble() : 1.0;

    int steps = (int) (totalTime / timeStep);
    op.totalSteps = steps;
    OPERATIONS.put(opId, op);

    EXECUTOR.submit(() -> {
      try {
        SimulationResult buildResult = ProcessSystem.fromJsonAndRun(processJson);
        if (buildResult.isError()) {
          op.status = "failed";
          op.errorMessage = "Failed to build process";
          return;
        }

        ProcessSystem process = buildResult.getProcessSystem();
        op.status = "running";

        for (int i = 0; i < steps && !op.cancelled; i++) {
          process.runTransient(timeStep);

          JsonObject point = new JsonObject();
          point.addProperty("timeStep", i);
          point.addProperty("time_s", (i + 1) * timeStep);

          // Report from process
          String report = process.getReport_json();
          if (report != null && !report.isEmpty()) {
            point.add("state", JsonParser.parseString(report));
          }

          op.addResult(point);
          op.completedSteps = i + 1;
        }

        op.status = op.cancelled ? "cancelled" : "completed";
      } catch (Exception e) {
        op.status = "failed";
        op.errorMessage = e.getMessage();
      }
    });

    JsonObject response = new JsonObject();
    response.addProperty("status", "started");
    response.addProperty("operationId", opId);
    response.addProperty("type", "dynamic_simulation");
    response.addProperty("totalSteps", steps);
    response.addProperty("message", "Poll for time-step results with action 'poll'");
    return GSON.toJson(response);
  }

  /**
   * Starts a Monte Carlo sweep that tracks convergence statistics.
   *
   * @param input the Monte Carlo configuration
   * @return JSON with operation ID
   */
  private static String startMonteCarlo(JsonObject input) {
    if (OPERATIONS.size() >= MAX_OPERATIONS) {
      return errorJson("LIMIT_REACHED", "Max streaming operations reached", "Cancel some first");
    }

    String opId = "mc-" + UUID.randomUUID().toString().substring(0, 8);
    StreamingOperation op = new StreamingOperation(opId, "monte_carlo");

    JsonObject baseComponents =
        input.has("components") ? input.getAsJsonObject("components") : new JsonObject();
    String model = input.has("model") ? input.get("model").getAsString() : "SRK";
    int iterations = input.has("iterations") ? input.get("iterations").getAsInt() : 100;

    // Parameter variations
    double tempMean =
        input.has("temperatureMean") ? input.get("temperatureMean").getAsDouble() : 25.0;
    double tempStd = input.has("temperatureStd") ? input.get("temperatureStd").getAsDouble() : 5.0;
    double presMean = input.has("pressureMean") ? input.get("pressureMean").getAsDouble() : 50.0;
    double presStd = input.has("pressureStd") ? input.get("pressureStd").getAsDouble() : 10.0;

    op.totalSteps = iterations;
    OPERATIONS.put(opId, op);

    EXECUTOR.submit(() -> {
      try {
        java.util.Random rng = new java.util.Random(42);
        List<Double> densities = new ArrayList<Double>();
        List<Double> zFactors = new ArrayList<Double>();

        for (int i = 0; i < iterations && !op.cancelled; i++) {
          double temp = tempMean + tempStd * rng.nextGaussian();
          double pres = Math.max(1.0, presMean + presStd * rng.nextGaussian());
          double tempK = convertToKelvin(temp, "C");

          SystemInterface fluid = FlashRunner.createFluid(model, tempK, pres);
          for (Map.Entry<String, JsonElement> entry : baseComponents.entrySet()) {
            fluid.addComponent(entry.getKey(), entry.getValue().getAsDouble());
          }
          fluid.setMixingRule("classic");
          ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
          ops.TPflash();
          fluid.initProperties();

          double density = fluid.getDensity("kg/m3");
          double z = fluid.getZ();
          densities.add(density);
          zFactors.add(z);

          // Report every 10th iteration or last
          if (i % 10 == 0 || i == iterations - 1) {
            JsonObject point = new JsonObject();
            point.addProperty("iteration", i + 1);
            point.addProperty("temperature_C", temp);
            point.addProperty("pressure_bara", pres);
            point.addProperty("density_kg_m3", density);
            point.addProperty("Z", z);

            // Running statistics
            point.addProperty("mean_density", mean(densities));
            point.addProperty("std_density", stddev(densities));
            point.addProperty("mean_Z", mean(zFactors));
            point.addProperty("std_Z", stddev(zFactors));
            point.addProperty("samples", densities.size());

            op.addResult(point);
          }

          op.completedSteps = i + 1;
          op.status = "running";
        }

        // Final statistics
        JsonObject summary = new JsonObject();
        summary.addProperty("type", "summary");
        summary.addProperty("totalIterations", densities.size());
        summary.addProperty("density_mean", mean(densities));
        summary.addProperty("density_std", stddev(densities));
        summary.addProperty("density_p10", percentile(densities, 10));
        summary.addProperty("density_p50", percentile(densities, 50));
        summary.addProperty("density_p90", percentile(densities, 90));
        summary.addProperty("Z_mean", mean(zFactors));
        summary.addProperty("Z_std", stddev(zFactors));
        op.addResult(summary);

        op.status = op.cancelled ? "cancelled" : "completed";
      } catch (Exception e) {
        op.status = "failed";
        op.errorMessage = e.getMessage();
      }
    });

    JsonObject response = new JsonObject();
    response.addProperty("status", "started");
    response.addProperty("operationId", opId);
    response.addProperty("type", "monte_carlo");
    response.addProperty("iterations", iterations);
    response.addProperty("message", "Poll for running statistics with action 'poll'");
    return GSON.toJson(response);
  }

  /**
   * Polls for incremental results from a streaming operation.
   *
   * @param input JSON with operationId and optional lastIndex
   * @return JSON with new results since lastIndex
   */
  private static String pollResults(JsonObject input) {
    String opId = input.has("operationId") ? input.get("operationId").getAsString() : "";
    StreamingOperation op = OPERATIONS.get(opId);

    if (op == null) {
      return errorJson("NOT_FOUND", "Operation not found: " + opId,
          "Use action 'list' to see active operations");
    }

    int lastIndex = input.has("lastIndex") ? input.get("lastIndex").getAsInt() : 0;

    JsonObject response = new JsonObject();
    response.addProperty("operationId", opId);
    response.addProperty("type", op.type);
    response.addProperty("status", op.status);
    response.addProperty("completedSteps", op.completedSteps);
    response.addProperty("totalSteps", op.totalSteps);
    response.addProperty("progressPercent",
        op.totalSteps > 0 ? (100.0 * op.completedSteps / op.totalSteps) : 0);

    if (op.errorMessage != null) {
      response.addProperty("error", op.errorMessage);
    }

    // Get new results since lastIndex
    List<JsonObject> newResults = op.getResultsSince(lastIndex);
    JsonArray results = new JsonArray();
    for (JsonObject r : newResults) {
      results.add(r);
    }
    response.add("newResults", results);
    response.addProperty("newResultCount", newResults.size());
    response.addProperty("totalResultCount", op.getResultCount());
    response.addProperty("nextPollIndex", lastIndex + newResults.size());

    return GSON.toJson(response);
  }

  /**
   * Cancels a streaming operation.
   *
   * @param input JSON with operationId
   * @return JSON confirmation
   */
  private static String cancelOperation(JsonObject input) {
    String opId = input.has("operationId") ? input.get("operationId").getAsString() : "";
    StreamingOperation op = OPERATIONS.get(opId);

    JsonObject response = new JsonObject();
    if (op != null) {
      op.cancelled = true;
      response.addProperty("status", "cancelling");
      response.addProperty("operationId", opId);
    } else {
      response.addProperty("status", "not_found");
      response.addProperty("operationId", opId);
    }
    return GSON.toJson(response);
  }

  /**
   * Lists all active and recent streaming operations.
   *
   * @return JSON with operation summaries
   */
  private static String listOperations() {
    JsonObject response = new JsonObject();
    response.addProperty("count", OPERATIONS.size());

    JsonArray ops = new JsonArray();
    for (Map.Entry<String, StreamingOperation> entry : OPERATIONS.entrySet()) {
      StreamingOperation op = entry.getValue();
      JsonObject info = new JsonObject();
      info.addProperty("operationId", op.operationId);
      info.addProperty("type", op.type);
      info.addProperty("status", op.status);
      info.addProperty("progress",
          op.totalSteps > 0 ? (100.0 * op.completedSteps / op.totalSteps) : 0);
      info.addProperty("completedSteps", op.completedSteps);
      info.addProperty("totalSteps", op.totalSteps);
      ops.add(info);
    }
    response.add("operations", ops);
    return GSON.toJson(response);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Converts temperature to Kelvin.
   *
   * @param value the temperature value
   * @param unit the temperature unit
   * @return temperature in Kelvin
   */
  private static double convertToKelvin(double value, String unit) {
    if ("K".equalsIgnoreCase(unit)) {
      return value;
    }
    if ("F".equalsIgnoreCase(unit)) {
      return (value - 32.0) * 5.0 / 9.0 + 273.15;
    }
    return value + 273.15; // Default: Celsius
  }

  /**
   * Converts pressure to bara.
   *
   * @param value the pressure value
   * @param unit the pressure unit
   * @return pressure in bara
   */
  private static double convertToBara(double value, String unit) {
    if ("bara".equalsIgnoreCase(unit) || "bar".equalsIgnoreCase(unit)) {
      return value;
    }
    if ("psi".equalsIgnoreCase(unit)) {
      return value * 0.0689476;
    }
    if ("kPa".equalsIgnoreCase(unit)) {
      return value / 100.0;
    }
    if ("MPa".equalsIgnoreCase(unit)) {
      return value * 10.0;
    }
    if ("atm".equalsIgnoreCase(unit)) {
      return value * 1.01325;
    }
    return value;
  }

  /**
   * Calculates the mean of a list of doubles.
   *
   * @param values the values
   * @return the mean
   */
  private static double mean(List<Double> values) {
    if (values.isEmpty()) {
      return 0;
    }
    double sum = 0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.size();
  }

  /**
   * Calculates the standard deviation of a list of doubles.
   *
   * @param values the values
   * @return the standard deviation
   */
  private static double stddev(List<Double> values) {
    if (values.size() < 2) {
      return 0;
    }
    double avg = mean(values);
    double sumSq = 0;
    for (double v : values) {
      sumSq += (v - avg) * (v - avg);
    }
    return Math.sqrt(sumSq / (values.size() - 1));
  }

  /**
   * Calculates a percentile from a list of doubles.
   *
   * @param values the values
   * @param pct the percentile (0-100)
   * @return the percentile value
   */
  private static double percentile(List<Double> values, int pct) {
    if (values.isEmpty()) {
      return 0;
    }
    List<Double> sorted = new ArrayList<Double>(values);
    java.util.Collections.sort(sorted);
    int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
    idx = Math.max(0, Math.min(idx, sorted.size() - 1));
    return sorted.get(idx);
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return the JSON string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    errors.add(err);
    error.add("errors", errors);
    return GSON.toJson(error);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Streaming operation state
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Internal state for a streaming operation.
   */
  static class StreamingOperation {
    /** Operation ID. */
    final String operationId;

    /** Operation type. */
    final String type;

    /** Current status: pending, running, completed, failed, cancelled. */
    volatile String status = "pending";

    /** Number of completed steps. */
    volatile int completedSteps = 0;

    /** Total expected steps. */
    volatile int totalSteps = 0;

    /** Whether cancellation was requested. */
    volatile boolean cancelled = false;

    /** Error message if failed. */
    volatile String errorMessage;

    /** Incremental results. */
    private final List<JsonObject> results =
        java.util.Collections.synchronizedList(new ArrayList<JsonObject>());

    /**
     * Creates a new streaming operation.
     *
     * @param operationId the operation ID
     * @param type the operation type
     */
    StreamingOperation(String operationId, String type) {
      this.operationId = operationId;
      this.type = type;
    }

    /**
     * Adds a result point.
     *
     * @param result the result
     */
    void addResult(JsonObject result) {
      results.add(result);
    }

    /**
     * Gets results added since the given index.
     *
     * @param fromIndex the start index
     * @return new results
     */
    List<JsonObject> getResultsSince(int fromIndex) {
      synchronized (results) {
        if (fromIndex >= results.size()) {
          return new ArrayList<JsonObject>();
        }
        return new ArrayList<JsonObject>(results.subList(fromIndex, results.size()));
      }
    }

    /**
     * Gets the total result count.
     *
     * @return the count
     */
    int getResultCount() {
      return results.size();
    }
  }
}
