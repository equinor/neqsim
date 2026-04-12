package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Parametric study runner for sensitivity analysis and optimization sweeps.
 *
 * <p>
 * Enables license-free batch exploration of a process design space. A typical cooperative
 * UniSim–NeqSim workflow:
 * </p>
 * <ol>
 * <li>Engineer builds the base case in UniSim (industry-accepted model)</li>
 * <li>Convert to NeqSim JSON via {@code unisim_reader.py}</li>
 * <li>Run a parametric study with this runner (hundreds of cases, no license)</li>
 * <li>Identify optimal operating point</li>
 * <li>Write the optimal case back to UniSim via {@code unisim_writer.py}</li>
 * </ol>
 *
 * <h2>Input JSON Format:</h2>
 *
 * <pre>{@code { "baseProcess": { ... standard process JSON ... }, "sweeps": [ { "address":
 * "Feed.temperature", "unit": "C", "values": [10.0, 20.0, 30.0, 40.0, 50.0] }, { "address":
 * "Feed.pressure", "unit": "bara", "from": 30.0, "to": 80.0, "steps": 6 } ], "outputs": [
 * {"address": "HP Sep.gasOutStream.flowRate", "unit": "MSm3/day"}, {"address": "Compressor.power",
 * "unit": "kW"} ], "mode": "full_factorial" } }</pre>
 *
 * @author Even Solbraa @version 1.0
 */
public class ParametricStudyRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Maximum total cases to prevent resource exhaustion. */
  private static final int MAX_TOTAL_CASES = 5000;

  private ParametricStudyRunner() {}

  /**
   * Runs a parametric study by sweeping input variables and recording outputs.
   *
   * <p>
   * Supports two modes:
   * <ul>
   * <li>{@code full_factorial} — all combinations of sweep values (N1 × N2 × ...)</li>
   * <li>{@code one_at_a_time} — vary one parameter while keeping others at base (default)</li>
   * </ul>
   *
   * @param json the parametric study specification
   * @return JSON with all case results and summary statistics
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide JSON with 'baseProcess', 'sweeps', and 'outputs'");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse: " + e.getMessage(),
          "Ensure well-formed JSON");
    }

    if (!input.has("baseProcess")) {
      return errorJson("MISSING_FIELD", "'baseProcess' is required", "Include full process JSON");
    }
    if (!input.has("sweeps") || !input.get("sweeps").isJsonArray()) {
      return errorJson("MISSING_FIELD", "'sweeps' array is required",
          "Define at least one sweep variable");
    }

    JsonObject baseProcess = input.getAsJsonObject("baseProcess");
    String mode = input.has("mode") ? input.get("mode").getAsString() : "one_at_a_time";

    // Parse sweep definitions
    List<SweepDef> sweeps = new ArrayList<>();
    for (JsonElement elem : input.getAsJsonArray("sweeps")) {
      JsonObject sw = elem.getAsJsonObject();
      SweepDef def = new SweepDef();
      def.address = sw.get("address").getAsString();
      def.unit = sw.has("unit") ? sw.get("unit").getAsString() : "";

      if (sw.has("values") && sw.get("values").isJsonArray()) {
        for (JsonElement v : sw.getAsJsonArray("values")) {
          def.values.add(v.getAsDouble());
        }
      } else if (sw.has("from") && sw.has("to") && sw.has("steps")) {
        double from = sw.get("from").getAsDouble();
        double to = sw.get("to").getAsDouble();
        int steps = sw.get("steps").getAsInt();
        if (steps < 2) {
          steps = 2;
        }
        for (int i = 0; i < steps; i++) {
          def.values.add(from + (to - from) * i / (steps - 1));
        }
      } else {
        return errorJson("INVALID_SWEEP",
            "Sweep for '" + def.address + "' needs 'values' array or 'from'/'to'/'steps'",
            "Provide either explicit values or a range specification");
      }
      sweeps.add(def);
    }

    // Parse output definitions
    List<OutputDef> outputs = new ArrayList<>();
    if (input.has("outputs") && input.get("outputs").isJsonArray()) {
      for (JsonElement elem : input.getAsJsonArray("outputs")) {
        JsonObject out = elem.getAsJsonObject();
        outputs.add(new OutputDef(out.get("address").getAsString(),
            out.has("unit") ? out.get("unit").getAsString() : ""));
      }
    }

    // Generate case list
    List<Map<String, Double>> cases;
    if ("full_factorial".equalsIgnoreCase(mode)) {
      cases = generateFullFactorialCases(sweeps);
    } else {
      cases = generateOneAtATimeCases(sweeps);
    }

    if (cases.size() > MAX_TOTAL_CASES) {
      return errorJson("TOO_MANY_CASES",
          "Parametric study generates " + cases.size() + " cases (max " + MAX_TOTAL_CASES + ")",
          "Reduce sweep resolution or use one_at_a_time mode");
    }

    // Run all cases
    long totalStart = System.currentTimeMillis();
    JsonArray caseResults = new JsonArray();
    int convergedCount = 0;

    for (int i = 0; i < cases.size(); i++) {
      Map<String, Double> inputValues = cases.get(i);
      JsonObject caseResult = runCase(baseProcess, sweeps, inputValues, outputs, i);
      caseResults.add(caseResult);
      if (caseResult.has("converged") && caseResult.get("converged").getAsBoolean()) {
        convergedCount++;
      }
    }

    // Build response
    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.addProperty("mode", mode);
    result.addProperty("totalCases", cases.size());
    result.addProperty("convergedCases", convergedCount);
    result.addProperty("failedCases", cases.size() - convergedCount);
    result.addProperty("computationTimeMs", System.currentTimeMillis() - totalStart);
    result.add("cases", caseResults);

    // Summary statistics for each output variable
    if (!outputs.isEmpty()) {
      result.add("outputSummary", buildOutputSummary(outputs, caseResults));
    }

    return GSON.toJson(result);
  }

  /**
   * Runs a single parametric case.
   *
   * @param baseProcess the base process JSON definition
   * @param sweeps the sweep parameter definitions
   * @param inputValues the input values for this case
   * @param outputs the output definitions to collect
   * @param caseIndex the index of this case
   * @return a JsonObject containing the case results
   */
  private static JsonObject runCase(JsonObject baseProcess, List<SweepDef> sweeps,
      Map<String, Double> inputValues, List<OutputDef> outputs, int caseIndex) {
    JsonObject caseResult = new JsonObject();
    caseResult.addProperty("caseIndex", caseIndex);

    // Record input values
    JsonObject inputs = new JsonObject();
    for (SweepDef sweep : sweeps) {
      inputs.addProperty(sweep.address, inputValues.get(sweep.address));
    }
    caseResult.add("inputs", inputs);

    try {
      // Build and run base process
      String jsonStr = GSON.toJson(baseProcess);
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(jsonStr);

      if (simResult.isError()) {
        caseResult.addProperty("converged", false);
        caseResult.addProperty("error", simResult.getErrors().toString());
        return caseResult;
      }

      ProcessSystem process = simResult.getProcessSystem();
      ProcessAutomation auto = process.getAutomation();

      // Set sweep variables
      for (SweepDef sweep : sweeps) {
        double value = inputValues.get(sweep.address);
        auto.setVariableValue(sweep.address, value, sweep.unit);
      }

      // Re-run the process
      process.run();

      caseResult.addProperty("converged", true);

      // Extract outputs
      if (!outputs.isEmpty()) {
        JsonObject outputVals = new JsonObject();
        for (OutputDef out : outputs) {
          try {
            double val = auto.getVariableValue(out.address, out.unit);
            outputVals.addProperty(out.address, val);
          } catch (Exception e) {
            outputVals.addProperty(out.address + "_error", e.getMessage());
          }
        }
        caseResult.add("outputs", outputVals);
      }
    } catch (Exception e) {
      caseResult.addProperty("converged", false);
      caseResult.addProperty("error", e.getMessage());
    }

    return caseResult;
  }

  /**
   * Generates all combinations for full-factorial design.
   *
   * @param sweeps the sweep parameter definitions
   * @return a list of maps, each representing one factorial case
   */
  private static List<Map<String, Double>> generateFullFactorialCases(List<SweepDef> sweeps) {
    List<Map<String, Double>> cases = new ArrayList<>();
    generateFactorialRecursive(sweeps, 0, new LinkedHashMap<>(), cases);
    return cases;
  }

  private static void generateFactorialRecursive(List<SweepDef> sweeps, int depth,
      Map<String, Double> current, List<Map<String, Double>> cases) {
    if (depth == sweeps.size()) {
      cases.add(new LinkedHashMap<>(current));
      return;
    }
    SweepDef sweep = sweeps.get(depth);
    for (double val : sweep.values) {
      current.put(sweep.address, val);
      generateFactorialRecursive(sweeps, depth + 1, current, cases);
    }
  }

  /**
   * Generates one-at-a-time cases: vary one parameter while keeping others at midpoint.
   */
  private static List<Map<String, Double>> generateOneAtATimeCases(List<SweepDef> sweeps) {
    List<Map<String, Double>> cases = new ArrayList<>();

    // Compute midpoint for each sweep
    Map<String, Double> midpoints = new LinkedHashMap<>();
    for (SweepDef sweep : sweeps) {
      int midIdx = sweep.values.size() / 2;
      midpoints.put(sweep.address, sweep.values.get(midIdx));
    }

    // For each sweep, vary it while keeping all others at midpoint
    for (SweepDef sweep : sweeps) {
      for (double val : sweep.values) {
        Map<String, Double> caseMap = new LinkedHashMap<>(midpoints);
        caseMap.put(sweep.address, val);
        cases.add(caseMap);
      }
    }

    return cases;
  }

  /**
   * Builds summary statistics for each output variable across all converged cases.
   */
  private static JsonObject buildOutputSummary(List<OutputDef> outputs, JsonArray caseResults) {
    JsonObject summary = new JsonObject();

    for (OutputDef out : outputs) {
      List<Double> values = new ArrayList<>();
      for (JsonElement elem : caseResults) {
        JsonObject c = elem.getAsJsonObject();
        if (c.has("converged") && c.get("converged").getAsBoolean() && c.has("outputs")
            && c.getAsJsonObject("outputs").has(out.address)) {
          values.add(c.getAsJsonObject("outputs").get(out.address).getAsDouble());
        }
      }

      if (!values.isEmpty()) {
        JsonObject stats = new JsonObject();
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        stats.addProperty("min", min);
        stats.addProperty("max", max);
        stats.addProperty("mean", mean);
        stats.addProperty("range", max - min);
        stats.addProperty("rangePct",
            mean != 0 ? Math.round(((max - min) / Math.abs(mean)) * 10000.0) / 100.0 : 0);
        stats.addProperty("sampleCount", values.size());
        stats.addProperty("unit", out.unit);
        summary.add(out.address, stats);
      }
    }

    return summary;
  }

  // --- Helper classes ---

  private static class SweepDef {
    String address;
    String unit;
    List<Double> values = new ArrayList<>();
  }

  private static class OutputDef {
    final String address;
    final String unit;

    OutputDef(String address, String unit) {
      this.address = address;
      this.unit = unit;
    }
  }

  private static String errorJson(String code, String message, String remediation) {
    JsonObject obj = new JsonObject();
    obj.addProperty("status", "error");
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    obj.add("error", err);
    return GSON.toJson(obj);
  }
}
