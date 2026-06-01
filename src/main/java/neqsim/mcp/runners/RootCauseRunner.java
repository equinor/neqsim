package neqsim.mcp.runners;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.diagnostics.RootCauseAnalyzer;
import neqsim.process.diagnostics.RootCauseReport;
import neqsim.process.diagnostics.Symptom;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * MCP runner for root cause analysis of equipment operational anomalies.
 *
 * <p>
 * Integrates NeqSim process simulation, OREDA reliability data, plant historian time-series, and
 * STID design conditions to produce ranked failure hypotheses with Bayesian confidence scoring.
 * </p>
 *
 * <p>
 * Input JSON fields:
 * </p>
 * <ul>
 * <li>{@code processJson} — NeqSim process definition (same format as runProcess)</li>
 * <li>{@code equipmentName} — name of the equipment to diagnose</li>
 * <li>{@code symptom} — observed symptom (TRIP, HIGH_VIBRATION, etc.)</li>
 * <li>{@code historianCsv} — optional CSV with time-series data (timestamp,param1,param2,...)</li>
 * <li>{@code designLimits} — optional map of parameter to [min, max] design limits</li>
 * <li>{@code stidData} — optional map of STID design values</li>
 * <li>{@code simulationEnabled} — whether to run simulation verification (default true)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RootCauseRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private RootCauseRunner() {}

  /**
   * Runs root cause analysis from a JSON definition.
   *
   * @param json JSON with process, equipment, symptom, and optional evidence data
   * @return JSON string with ranked hypotheses and evidence
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();

      // Required fields
      String equipmentName = getRequiredString(input, "equipmentName");
      String symptomStr = getRequiredString(input, "symptom");

      Symptom symptom = parseSymptom(symptomStr);
      if (symptom == null) {
        return errorJson("Unknown symptom: " + symptomStr
            + ". Supported: TRIP, HIGH_VIBRATION, SEAL_FAILURE, HIGH_TEMPERATURE, "
            + "LOW_EFFICIENCY, PRESSURE_DEVIATION, FLOW_DEVIATION, HIGH_POWER, "
            + "SURGE_EVENT, FOULING, ABNORMAL_NOISE, LIQUID_CARRYOVER");
      }

      // Build process from JSON
      String processJsonStr = getRequiredString(input, "processJson");
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(processJsonStr);
      if (simResult.isError()) {
        return errorJson("Process simulation failed: " + simResult.getErrors().toString());
      }
      ProcessSystem process = simResult.getProcessSystem();

      // Create analyzer
      RootCauseAnalyzer rca = new RootCauseAnalyzer(process, equipmentName);
      rca.setSymptom(symptom);

      // Optional: simulation enabled
      if (input.has("simulationEnabled")) {
        rca.setSimulationEnabled(input.get("simulationEnabled").getAsBoolean());
      }

      // Optional: historian CSV data
      if (input.has("historianCsv")) {
        String csv = input.get("historianCsv").getAsString();
        parseAndSetHistorianData(rca, csv);
      }

      // Optional: design limits
      if (input.has("designLimits") && input.get("designLimits").isJsonObject()) {
        JsonObject limits = input.getAsJsonObject("designLimits");
        for (Map.Entry<String, JsonElement> entry : limits.entrySet()) {
          if (entry.getValue().isJsonArray()) {
            JsonArray arr = entry.getValue().getAsJsonArray();
            double min = arr.get(0).isJsonNull() ? Double.NaN : arr.get(0).getAsDouble();
            double max = arr.get(1).isJsonNull() ? Double.NaN : arr.get(1).getAsDouble();
            rca.setDesignLimit(entry.getKey(), min, max);
          }
        }
      }

      // Optional: STID data
      if (input.has("stidData") && input.get("stidData").isJsonObject()) {
        Map<String, String> stidMap = new HashMap<String, String>();
        JsonObject stid = input.getAsJsonObject("stidData");
        for (Map.Entry<String, JsonElement> entry : stid.entrySet()) {
          stidMap.put(entry.getKey(), entry.getValue().getAsString());
        }
        rca.setStidData(stidMap);
      }

      // Run analysis
      RootCauseReport report = rca.analyze();

      // Return JSON report
      String reportJson = report.toJson();
      JsonObject result = JsonParser.parseString(reportJson).getAsJsonObject();
      result.addProperty("status", "success");
      return GSON.toJson(result);

    } catch (IllegalArgumentException e) {
      return errorJson("Invalid input: " + e.getMessage());
    } catch (IllegalStateException e) {
      return errorJson("Analysis failed: " + e.getMessage());
    } catch (Exception e) {
      return errorJson("Root cause analysis failed: " + e.getMessage());
    }
  }

  /**
   * Parses CSV historian data and sets it on the analyzer.
   *
   * @param rca the root cause analyzer
   * @param csv CSV string with header row and data
   * @throws Exception if CSV parsing fails
   */
  private static void parseAndSetHistorianData(RootCauseAnalyzer rca, String csv) throws Exception {
    BufferedReader reader = new BufferedReader(new StringReader(csv));
    String headerLine = reader.readLine();
    if (headerLine == null || headerLine.trim().isEmpty()) {
      return;
    }

    String[] headers = headerLine.split(",");
    if (headers.length < 2) {
      return;
    }

    // Read all data rows
    List<String[]> rows = new ArrayList<String[]>();
    String line;
    while ((line = reader.readLine()) != null) {
      if (!line.trim().isEmpty()) {
        rows.add(line.split(","));
      }
    }

    if (rows.isEmpty()) {
      return;
    }

    // First column is timestamp
    double[] timestamps = new double[rows.size()];
    Map<String, double[]> data = new HashMap<String, double[]>();

    for (int col = 1; col < headers.length; col++) {
      data.put(headers[col].trim(), new double[rows.size()]);
    }

    for (int row = 0; row < rows.size(); row++) {
      String[] values = rows.get(row);
      timestamps[row] = Double.parseDouble(values[0].trim());
      for (int col = 1; col < headers.length && col < values.length; col++) {
        String paramName = headers[col].trim();
        double[] paramData = data.get(paramName);
        if (paramData != null) {
          paramData[row] = Double.parseDouble(values[col].trim());
        }
      }
    }

    rca.setHistorianData(data, timestamps);
  }

  /**
   * Parses a symptom string to the enum value.
   *
   * @param str the symptom string
   * @return the Symptom enum value, or null if not found
   */
  private static Symptom parseSymptom(String str) {
    try {
      return Symptom.valueOf(str.toUpperCase().trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Gets a required string field from the JSON object.
   *
   * @param input the JSON object
   * @param field the field name
   * @return the string value
   * @throws IllegalArgumentException if the field is missing or empty
   */
  private static String getRequiredString(JsonObject input, String field) {
    if (!input.has(field) || input.get(field).isJsonNull()) {
      throw new IllegalArgumentException("Missing required field: " + field);
    }
    String value = input.get(field).getAsString();
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException("Empty required field: " + field);
    }
    return value;
  }

  /**
   * Creates an error JSON response.
   *
   * @param message the error message
   * @return JSON error string
   */
  private static String errorJson(String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("message", message);
    return GSON.toJson(error);
  }
}
