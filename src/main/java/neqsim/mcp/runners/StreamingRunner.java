package neqsim.mcp.runners;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Streaming simulation runner that executes long-running operations asynchronously and provides incremental result
 * polling.
 *
 * <p>
 * Supports: convergence monitoring during flash sweeps, parametric studies with per-case results, dynamic simulation
 * time-step streaming, and Monte Carlo progress. Agents poll for intermediate results while the computation proceeds in
 * background.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class StreamingRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Background thread pool for async simulations. */
  private static final ExecutorService EXECUTOR = McpExecutionPolicy.workerPool();

  /** Active streaming operations. */
  private static final ConcurrentHashMap<String, StreamingOperation> OPERATIONS = new ConcurrentHashMap<String, StreamingOperation>();

  /** Max concurrent streaming operations. */
  private static final int MAX_OPERATIONS = 20;

  /** Maximum points accepted for a parametric sweep. */
  static final int MAX_SWEEP_POINTS = 1000;

  /** Maximum time steps accepted for one dynamic operation. */
  static final int MAX_DYNAMIC_STEPS = 10000;

  /** Maximum iterations accepted for one Monte Carlo operation. */
  static final int MAX_MONTE_CARLO_ITERATIONS = 1000;

  /** Maximum result records returned by one poll. */
  static final int MAX_RESULTS_PER_POLL = 100;

  /** Maximum accepted serialized process definition size. */
  private static final int MAX_PROCESS_JSON_BYTES = 256 * 1024;

  /** Retention time for completed streaming operations. */
  private static final long OPERATION_RETENTION_MS = 30L * 60L * 1000L;

  /** Cryptographically strong random source for operation identifiers. */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /**
   * Private constructor — all methods are static.
   */
  private StreamingRunner() {
  }

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
      case "startParametricSweep":
        return startParametricSweep(input);
      case "startDynamic":
      case "startDynamicStreaming":
        return startDynamicStreaming(input);
      case "startMonteCarlo":
        return startMonteCarlo(input);
      case "poll":
      case "pollResults":
        return pollResults(input);
      case "cancel":
      case "cancelOperation":
        return cancelOperation(input);
      case "list":
      case "listOperations":
        return listOperations();
      default:
        return errorJson("UNKNOWN_ACTION", "Unknown streaming action: " + action,
            "Use: startSweep/startParametricSweep, startDynamic/startDynamicStreaming, "
                + "startMonteCarlo, poll/pollResults, cancel/cancelOperation, " + "list/listOperations");
      }
    } catch (Exception e) {
      return errorJson("STREAMING_ERROR", "Streaming operation failed: " + e.getMessage(), "Check JSON format");
    }
  }

  /**
   * Starts a parametric sweep that reports results incrementally.
   *
   * @param input the sweep configuration
   * @return JSON with operation ID
   */
  private static String startParametricSweep(JsonObject input) {
    cleanupOperations();
    String opId = newOperationId("sweep");
    StreamingOperation op = new StreamingOperation(opId, "parametric_sweep");

    // Parse sweep parameters
    JsonObject components = input.has("components") && input.get("components").isJsonObject()
        ? input.getAsJsonObject("components")
        : null;
    String model = input.has("model") ? input.get("model").getAsString() : "SRK";
    String sweepVar = input.has("sweepVariable") ? input.get("sweepVariable").getAsString() : "temperature";
    double from = input.has("from") ? input.get("from").getAsDouble() : 0;
    double to = input.has("to") ? input.get("to").getAsDouble() : 100;
    int points = input.has("points") ? input.get("points").getAsInt() : 20;
    String unit = input.has("unit") ? input.get("unit").getAsString() : "C";

    // Fixed conditions
    double fixedTemp = input.has("fixedTemperature") ? input.get("fixedTemperature").getAsDouble() : 25.0;
    String fixedTempUnit = input.has("fixedTemperatureUnit") ? input.get("fixedTemperatureUnit").getAsString() : "C";
    double fixedPressure = input.has("fixedPressure") ? input.get("fixedPressure").getAsDouble() : 1.0;
    String fixedPressureUnit = input.has("fixedPressureUnit") ? input.get("fixedPressureUnit").getAsString() : "bara";

    String componentError = validateComponents(components);
    if (componentError != null) {
      return componentError;
    }
    if (points < 1 || points > MAX_SWEEP_POINTS) {
      return errorJson("INVALID_POINTS", "points must be between 1 and " + MAX_SWEEP_POINTS,
          "Choose a bounded positive sweep size");
    }
    if (!"temperature".equalsIgnoreCase(sweepVar) && !"pressure".equalsIgnoreCase(sweepVar)) {
      return errorJson("INVALID_SWEEP_VARIABLE", "sweepVariable must be temperature or pressure",
          "Use one of the documented sweep variables");
    }
    if (!isTemperatureUnit(fixedTempUnit) || !isPressureUnit(fixedPressureUnit)
        || ("temperature".equalsIgnoreCase(sweepVar) && !isTemperatureUnit(unit))
        || ("pressure".equalsIgnoreCase(sweepVar) && !isPressureUnit(unit))) {
      return errorJson("INVALID_UNIT", "Unsupported sweep or fixed-condition unit",
          "Use C, K or F for temperature and bara, bar, psi, kPa, MPa or atm for pressure");
    }
    if (!isFinite(from) || !isFinite(to) || !isFinite(fixedTemp) || !isFinite(fixedPressure)
        || convertToKelvin(fixedTemp, fixedTempUnit) <= 0.0 || convertToBara(fixedPressure, fixedPressureUnit) <= 0.0
        || ("temperature".equalsIgnoreCase(sweepVar)
            && (convertToKelvin(from, unit) <= 0.0 || convertToKelvin(to, unit) <= 0.0))
        || ("pressure".equalsIgnoreCase(sweepVar)
            && (convertToBara(from, unit) <= 0.0 || convertToBara(to, unit) <= 0.0))) {
      return errorJson("INVALID_RANGE", "Sweep and fixed conditions must be finite and physically positive",
          "Use temperatures above absolute zero and positive absolute pressures");
    }

    op.totalSteps = points;
    String limited = registerOperation(op);
    if (limited != null) {
      return limited;
    }

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

        op.markFinished(op.cancelled ? "cancelled" : "completed");
      } catch (Exception e) {
        op.markFinished("failed");
        op.errorMessage = e.getMessage();
      }
    });

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("operationStatus", "started");
    response.addProperty("operationId", opId);
    response.addProperty("type", "parametric_sweep");
    response.addProperty("totalPoints", points);
    response.addProperty("message", "Use action 'poll' with this operationId to get incremental results");
    return GSON.toJson(response);
  }

  /**
   * Starts a dynamic simulation that streams time-step results.
   *
   * @param input the dynamic simulation configuration
   * @return JSON with operation ID
   */
  private static String startDynamicStreaming(JsonObject input) {
    cleanupOperations();
    String opId = newOperationId("dynamic");
    StreamingOperation op = new StreamingOperation(opId, "dynamic_simulation");

    if (!input.has("processJson") || input.get("processJson").isJsonNull()) {
      return errorJson("MISSING_PROCESS", "processJson is required for dynamic streaming",
          "Provide a nested process definition or a JSON string");
    }
    JsonElement processElement = input.get("processJson");
    if (!processElement.isJsonObject()
        && (!processElement.isJsonPrimitive() || !processElement.getAsJsonPrimitive().isString())) {
      return errorJson("INVALID_PROCESS", "processJson must be an object or JSON string",
          "Provide a ProcessSystem JSON definition");
    }
    String processJson = processElement.isJsonObject() ? GSON.toJson(processElement) : processElement.getAsString();
    if (processJson.trim().isEmpty()) {
      return errorJson("INVALID_PROCESS", "processJson must not be blank", "Provide a ProcessSystem JSON definition");
    }
    if (processJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PROCESS_JSON_BYTES) {
      return errorJson("REQUEST_TOO_LARGE", "processJson exceeds " + MAX_PROCESS_JSON_BYTES + " bytes",
          "Reduce the process definition before starting the operation");
    }
    double totalTime = input.has("totalTime") ? input.get("totalTime").getAsDouble() : 3600.0;
    double timeStep = input.has("timeStep") ? input.get("timeStep").getAsDouble() : 1.0;
    if (!isFinite(totalTime) || !isFinite(timeStep) || totalTime <= 0.0 || timeStep <= 0.0) {
      return errorJson("INVALID_TIME_RANGE", "totalTime and timeStep must be finite and positive",
          "Provide positive durations in seconds");
    }

    double stepCount = Math.floor(totalTime / timeStep);
    if (stepCount < 1.0 || stepCount > MAX_DYNAMIC_STEPS) {
      return errorJson("INVALID_STEP_COUNT",
          "Dynamic operation must contain between 1 and " + MAX_DYNAMIC_STEPS + " time steps",
          "Increase timeStep or reduce totalTime");
    }
    int steps = (int) stepCount;
    op.totalSteps = steps;
    String limited = registerOperation(op);
    if (limited != null) {
      return limited;
    }

    EXECUTOR.submit(() -> {
      try {
        SimulationResult buildResult = ProcessSystem.fromJsonAndRun(processJson);
        if (buildResult.isError() || buildResult.getProcessSystem() == null) {
          op.errorMessage = "Failed to build process";
          op.markFinished("failed");
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

        op.markFinished(op.cancelled ? "cancelled" : "completed");
      } catch (Exception e) {
        op.markFinished("failed");
        op.errorMessage = e.getMessage();
      }
    });

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("operationStatus", "started");
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
    cleanupOperations();
    String opId = newOperationId("mc");
    StreamingOperation op = new StreamingOperation(opId, "monte_carlo");

    JsonObject baseComponents = input.has("components") && input.get("components").isJsonObject()
        ? input.getAsJsonObject("components")
        : null;
    String model = input.has("model") ? input.get("model").getAsString() : "SRK";
    int iterations = input.has("iterations") ? input.get("iterations").getAsInt() : 100;

    // Parameter variations
    double tempMean = input.has("temperatureMean") ? input.get("temperatureMean").getAsDouble() : 25.0;
    double tempStd = input.has("temperatureStd") ? input.get("temperatureStd").getAsDouble() : 5.0;
    double presMean = input.has("pressureMean") ? input.get("pressureMean").getAsDouble() : 50.0;
    double presStd = input.has("pressureStd") ? input.get("pressureStd").getAsDouble() : 10.0;

    String componentError = validateComponents(baseComponents);
    if (componentError != null) {
      return componentError;
    }
    if (iterations < 1 || iterations > MAX_MONTE_CARLO_ITERATIONS) {
      return errorJson("INVALID_ITERATIONS", "iterations must be between 1 and " + MAX_MONTE_CARLO_ITERATIONS,
          "Choose a bounded positive Monte Carlo size");
    }
    if (!isFinite(tempMean) || !isFinite(tempStd) || !isFinite(presMean) || !isFinite(presStd) || tempStd < 0.0
        || presStd < 0.0 || convertToKelvin(tempMean, "C") <= 0.0 || presMean <= 0.0) {
      return errorJson("INVALID_DISTRIBUTION",
          "Monte Carlo parameters must be finite, with non-negative "
              + "standard deviations, temperature above absolute zero, and positive mean pressure",
          "Correct the requested distribution");
    }

    op.totalSteps = iterations;
    String limited = registerOperation(op);
    if (limited != null) {
      return limited;
    }

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

        op.markFinished(op.cancelled ? "cancelled" : "completed");
      } catch (Exception e) {
        op.markFinished("failed");
        op.errorMessage = e.getMessage();
      }
    });

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("operationStatus", "started");
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
    StreamingOperation op = ownedOperation(opId);

    if (op == null) {
      return errorJson("NOT_FOUND", "Operation not found: " + opId, "Use action 'list' to see active operations");
    }
    op.touch();

    int lastIndex = input.has("lastIndex") ? input.get("lastIndex").getAsInt() : 0;
    if (lastIndex < 0) {
      return errorJson("INVALID_CURSOR", "lastIndex must be zero or greater",
          "Use nextPollIndex from the previous response");
    }

    JsonObject response = new JsonObject();
    response.addProperty("operationId", opId);
    response.addProperty("type", op.type);
    response.addProperty("status", "success");
    response.addProperty("operationStatus", op.status);
    response.addProperty("completedSteps", op.completedSteps);
    response.addProperty("totalSteps", op.totalSteps);
    response.addProperty("progressPercent", op.totalSteps > 0 ? (100.0 * op.completedSteps / op.totalSteps) : 0);

    if (op.errorMessage != null) {
      response.addProperty("error", op.errorMessage);
    }

    // Get new results since lastIndex
    List<JsonObject> newResults = op.getResultsSince(lastIndex, MAX_RESULTS_PER_POLL);
    JsonArray results = new JsonArray();
    for (JsonObject r : newResults) {
      results.add(r);
    }
    response.add("newResults", results);
    response.addProperty("newResultCount", newResults.size());
    response.addProperty("totalResultCount", op.getResultCount());
    response.addProperty("nextPollIndex", lastIndex + newResults.size());
    response.addProperty("maxResultsPerPoll", MAX_RESULTS_PER_POLL);
    response.addProperty("hasMoreResults", lastIndex + newResults.size() < op.getResultCount());

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
    StreamingOperation op = ownedOperation(opId);

    if (op == null) {
      String notFound = errorJson("NOT_FOUND", "Operation not found: " + opId,
          "Use action 'list' to see active operations");
      JsonObject response = JsonParser.parseString(notFound).getAsJsonObject();
      response.addProperty("operationStatus", "not_found");
      return GSON.toJson(response);
    }
    String operationStatus = op.requestCancellation();
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("operationStatus", operationStatus);
    response.addProperty("operationId", opId);
    return GSON.toJson(response);
  }

  /**
   * Lists all active and recent streaming operations.
   *
   * @return JSON with operation summaries
   */
  private static String listOperations() {
    cleanupOperations();
    String owner = McpRequestContext.currentSubject();
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");

    JsonArray ops = new JsonArray();
    for (Map.Entry<String, StreamingOperation> entry : OPERATIONS.entrySet()) {
      StreamingOperation op = entry.getValue();
      if (!owner.equals(op.owner)) {
        continue;
      }
      JsonObject info = new JsonObject();
      info.addProperty("operationId", op.operationId);
      info.addProperty("type", op.type);
      info.addProperty("status", op.status);
      info.addProperty("progress", op.totalSteps > 0 ? (100.0 * op.completedSteps / op.totalSteps) : 0);
      info.addProperty("completedSteps", op.completedSteps);
      info.addProperty("totalSteps", op.totalSteps);
      info.addProperty("ageSeconds", (System.currentTimeMillis() - op.createdAt) / 1000);
      info.addProperty("lastUpdatedSecondsAgo", (System.currentTimeMillis() - op.lastUpdatedAt) / 1000);
      ops.add(info);
    }
    response.addProperty("count", ops.size());
    response.add("operations", ops);
    response.add("executionPolicy", McpExecutionPolicy.describe());
    response.add("requestLimits", describeRequestLimits());
    response.addProperty("activeForCaller", McpExecutionPolicy.activeOperations(owner));
    return GSON.toJson(response);
  }

  /**
   * Resolves an operation only when it belongs to the current caller.
   *
   * @param operationId the operation identifier
   * @return the operation, or null when unknown or owned by another principal
   */
  private static StreamingOperation ownedOperation(String operationId) {
    StreamingOperation op = OPERATIONS.get(operationId);
    if (op == null || !McpRequestContext.currentSubject().equals(op.owner)) {
      return null;
    }
    return op;
  }

  /**
   * Registers a new operation, enforcing the per-principal concurrency limit and arming its timeout.
   *
   * @param op the operation to register
   * @return null when registration succeeded, otherwise an error response
   */
  private static synchronized String registerOperation(StreamingOperation op) {
    if (activeOperationCount() >= MAX_OPERATIONS) {
      return errorJson("LIMIT_REACHED", "Max streaming operations reached", "Wait for active work to finish");
    }
    if (!McpExecutionPolicy.tryAcquireSlot()) {
      return errorJson("CONCURRENCY_LIMIT",
          "This caller already has " + McpExecutionPolicy.getMaxOperationsPerPrincipal() + " operations running",
          "Wait for one to finish, or cancel it with action 'cancel'");
    }
    OPERATIONS.put(op.operationId, op);
    op.armTimeout();
    return null;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Counts active operations without treating retained terminal results as active work.
   *
   * @return number of non-terminal operations
   */
  private static int activeOperationCount() {
    int active = 0;
    for (StreamingOperation op : OPERATIONS.values()) {
      if (!op.isTerminal()) {
        active++;
      }
    }
    return active;
  }

  /**
   * Validates a non-empty, finite and non-negative composition with positive total amount.
   *
   * @param components component amount map
   * @return null when valid, otherwise a structured error response
   */
  private static String validateComponents(JsonObject components) {
    if (components == null || components.size() == 0) {
      return errorJson("MISSING_COMPONENTS", "components must contain at least one component",
          "Provide a component-to-amount map");
    }
    double total = 0.0;
    try {
      for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
        JsonElement element = entry.getValue();
        if (entry.getKey().trim().isEmpty() || element == null || !element.isJsonPrimitive()
            || !element.getAsJsonPrimitive().isNumber()) {
          return errorJson("INVALID_COMPONENTS", "Component names and amounts must be numeric",
              "Provide non-blank names and finite non-negative amounts");
        }
        double amount = element.getAsDouble();
        if (!isFinite(amount) || amount < 0.0) {
          return errorJson("INVALID_COMPONENTS", "Component amounts must be finite and non-negative",
              "Correct the component map");
        }
        total += amount;
      }
    } catch (RuntimeException e) {
      return errorJson("INVALID_COMPONENTS", "Component amounts must be numeric",
          "Provide finite non-negative amounts");
    }
    if (!isFinite(total) || total <= 0.0) {
      return errorJson("INVALID_COMPONENTS", "At least one component amount must be positive",
          "Correct the component map");
    }
    return null;
  }

  /**
   * Reports fixed streaming request bounds.
   *
   * @return request-limit object
   */
  private static JsonObject describeRequestLimits() {
    JsonObject limits = new JsonObject();
    limits.addProperty("maxSweepPoints", MAX_SWEEP_POINTS);
    limits.addProperty("maxDynamicSteps", MAX_DYNAMIC_STEPS);
    limits.addProperty("maxMonteCarloIterations", MAX_MONTE_CARLO_ITERATIONS);
    limits.addProperty("maxResultsPerPoll", MAX_RESULTS_PER_POLL);
    limits.addProperty("maxProcessJsonBytes", MAX_PROCESS_JSON_BYTES);
    return limits;
  }

  /**
   * Checks whether a value is finite.
   *
   * @param value value to check
   * @return true only for finite values
   */
  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  /**
   * Checks a documented temperature unit.
   *
   * @param unit unit token
   * @return true for C, K or F
   */
  private static boolean isTemperatureUnit(String unit) {
    return "C".equalsIgnoreCase(unit) || "K".equalsIgnoreCase(unit) || "F".equalsIgnoreCase(unit);
  }

  /**
   * Checks a documented pressure unit.
   *
   * @param unit unit token
   * @return true for bara, bar, psi, kPa, MPa or atm
   */
  private static boolean isPressureUnit(String unit) {
    return "bara".equalsIgnoreCase(unit) || "bar".equalsIgnoreCase(unit) || "psi".equalsIgnoreCase(unit)
        || "kPa".equalsIgnoreCase(unit) || "MPa".equalsIgnoreCase(unit) || "atm".equalsIgnoreCase(unit);
  }

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
   * Generates a globally unique, URL-safe operation identifier.
   *
   * @param prefix operation type prefix
   * @return operation identifier
   */
  private static String newOperationId(String prefix) {
    byte[] bytes = new byte[18];
    SECURE_RANDOM.nextBytes(bytes);
    return prefix + "-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Removes completed operations after the retention period.
   */
  private static void cleanupOperations() {
    long now = System.currentTimeMillis();
    List<String> toRemove = new ArrayList<String>();
    for (Map.Entry<String, StreamingOperation> entry : OPERATIONS.entrySet()) {
      StreamingOperation op = entry.getValue();
      if (op.isTerminal() && now - op.lastUpdatedAt > OPERATION_RETENTION_MS) {
        toRemove.add(entry.getKey());
      }
    }
    for (String operationId : toRemove) {
      OPERATIONS.remove(operationId);
    }
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

    /** Principal subject that started the operation. */
    final String owner;

    /** Creation timestamp. */
    final long createdAt = System.currentTimeMillis();

    /** Last update or poll timestamp. */
    volatile long lastUpdatedAt = System.currentTimeMillis();

    /** Current status: pending, running, completed, failed, cancelled, timed_out. */
    volatile String status = "pending";

    /** Number of completed steps. */
    volatile int completedSteps = 0;

    /** Total expected steps. */
    volatile int totalSteps = 0;

    /** Whether cancellation was requested. */
    volatile boolean cancelled = false;

    /** Error message if failed. */
    volatile String errorMessage;

    /** Pending timeout watchdog, cancelled when the operation finishes normally. */
    private volatile java.util.concurrent.ScheduledFuture<?> timeoutHandle;

    /** Guards against releasing the concurrency slot more than once. */
    private final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(
        false);

    /** Incremental results. */
    private final List<JsonObject> results = java.util.Collections.synchronizedList(new ArrayList<JsonObject>());

    /**
     * Creates a new streaming operation owned by the current principal.
     *
     * @param operationId the operation ID
     * @param type the operation type
     */
    StreamingOperation(String operationId, String type) {
      this.operationId = operationId;
      this.type = type;
      this.owner = McpRequestContext.currentSubject();
    }

    /**
     * Arms the wall-clock watchdog that cancels a run-away operation.
     */
    void armTimeout() {
      timeoutHandle = McpExecutionPolicy.scheduleTimeout(new Runnable() {
        @Override
        public void run() {
          if (!isTerminal()) {
            cancelled = true;
            errorMessage = "Operation exceeded the " + McpExecutionPolicy.getOperationTimeoutSeconds()
                + " s execution timeout and was cancelled";
            markFinished("timed_out");
          }
        }
      });
    }

    /**
     * Adds a result point.
     *
     * @param result the result
     */
    void addResult(JsonObject result) {
      results.add(result);
      touch();
    }

    /**
     * Records access or progress on the operation.
     */
    void touch() {
      lastUpdatedAt = System.currentTimeMillis();
    }

    /**
     * Marks the operation finished with a terminal status.
     *
     * @param finalStatus final status value
     */
    synchronized void markFinished(String finalStatus) {
      if (!finished.compareAndSet(false, true)) {
        return;
      }
      status = finalStatus;
      touch();
      java.util.concurrent.ScheduledFuture<?> handle = timeoutHandle;
      if (handle != null) {
        handle.cancel(false);
      }
      McpExecutionPolicy.releaseSlot(owner);
    }

    /**
     * Requests cooperative cancellation without overwriting a terminal outcome.
     *
     * @return the resulting operation status
     */
    synchronized String requestCancellation() {
      if (isTerminal()) {
        return status;
      }
      cancelled = true;
      status = "cancelling";
      touch();
      return status;
    }

    /**
     * Checks whether this operation has reached a terminal state.
     *
     * @return true for completed, failed, or cancelled operations
     */
    boolean isTerminal() {
      return "completed".equals(status) || "failed".equals(status) || "cancelled".equals(status)
          || "timed_out".equals(status);
    }

    /**
     * Gets results added since the given index.
     *
     * @param fromIndex the start index
     * @return new results
     */
    List<JsonObject> getResultsSince(int fromIndex, int limit) {
      synchronized (results) {
        if (fromIndex >= results.size()) {
          return new ArrayList<JsonObject>();
        }
        int toIndex = Math.min(results.size(), fromIndex + limit);
        return new ArrayList<JsonObject>(results.subList(fromIndex, toIndex));
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
