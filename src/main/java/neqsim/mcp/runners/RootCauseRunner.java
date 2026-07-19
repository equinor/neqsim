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
import neqsim.process.diagnostics.AnomalyScanner;
import neqsim.process.diagnostics.CausalTopologyModel;
import neqsim.process.diagnostics.DiagnosisCase;
import neqsim.process.diagnostics.RelationshipGraph;
import neqsim.process.diagnostics.RootCauseAnalyzer;
import neqsim.process.diagnostics.RootCauseReport;
import neqsim.process.diagnostics.Symptom;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * MCP runner for root cause analysis of equipment operational anomalies.
 *
 * <p>
 * Integrates NeqSim process simulation, OREDA reliability data, plant historian time-series, and STID design conditions
 * to produce ranked failure hypotheses with Bayesian confidence scoring.
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
 * <li>{@code diagnosisCase} — optional reproducibility, provenance, and operating-context metadata</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RootCauseRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private RootCauseRunner() {
  }

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

      // Symptom is optional: when absent (or "AUTO"/"AUTONOMOUS"), the analyzer infers the symptom
      // from the historian data and discovers relationships on its own (autonomous investigation).
      boolean autonomous = true;
      Symptom symptom = null;
      if (input.has("symptom") && !input.get("symptom").isJsonNull()) {
        String symptomStr = input.get("symptom").getAsString().trim();
        if (!symptomStr.isEmpty() && !symptomStr.equalsIgnoreCase("AUTO")
            && !symptomStr.equalsIgnoreCase("AUTONOMOUS")) {
          symptom = parseSymptom(symptomStr);
          if (symptom == null) {
            return errorJson("Unknown symptom: " + symptomStr + ". Supported: TRIP, HIGH_VIBRATION, "
                + "SEAL_FAILURE, HIGH_TEMPERATURE, LOW_EFFICIENCY, PRESSURE_DEVIATION, FLOW_DEVIATION, "
                + "HIGH_POWER, SURGE_EVENT, FOULING, ABNORMAL_NOISE, LIQUID_CARRYOVER, "
                + "or omit / use AUTO for autonomous mode");
          }
          autonomous = false;
        }
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
      if (symptom != null) {
        rca.setSymptom(symptom);
      }

      // Optional reproducibility and provenance context. Missing fields are allowed for incremental adoption.
      if (input.has("diagnosisCase") && input.get("diagnosisCase").isJsonObject()) {
        JsonObject caseJson = input.getAsJsonObject("diagnosisCase");
        DiagnosisCase diagnosisCase = new DiagnosisCase(equipmentName);
        if (caseJson.has("caseId")) {
          diagnosisCase.setCaseId(caseJson.get("caseId").getAsString());
        }
        if (caseJson.has("windowStartEpochSeconds") && caseJson.has("windowEndEpochSeconds")) {
          diagnosisCase.setTimeWindow(caseJson.get("windowStartEpochSeconds").getAsDouble(),
              caseJson.get("windowEndEpochSeconds").getAsDouble());
        }
        String modelId = caseJson.has("processModelId") ? caseJson.get("processModelId").getAsString()
            : process.getClass().getName();
        String modelRevision = caseJson.has("processModelRevision")
            ? caseJson.get("processModelRevision").getAsString()
            : "runtime-instance";
        diagnosisCase.setProcessModel(modelId, modelRevision);
        addStringMap(caseJson, "dataSources", new StringMapConsumer() {
          @Override
          public void accept(String key, String value) {
            diagnosisCase.addDataSource(key, value);
          }
        });
        addStringMap(caseJson, "operatingContext", new StringMapConsumer() {
          @Override
          public void accept(String key, String value) {
            diagnosisCase.addOperatingContext(key, value);
          }
        });
        addStringMap(caseJson, "dataQuality", new StringMapConsumer() {
          @Override
          public void accept(String key, String value) {
            diagnosisCase.addDataQuality(key, value);
          }
        });
        rca.setDiagnosisCase(diagnosisCase);
      }

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

      // Optional: tag-to-equipment map (enables topology-classified causal edges in autonomous mode)
      Map<String, String> tagToEquipment = null;
      if (input.has("tagToEquipment") && input.get("tagToEquipment").isJsonObject()) {
        tagToEquipment = new HashMap<String, String>();
        JsonObject t2e = input.getAsJsonObject("tagToEquipment");
        for (Map.Entry<String, JsonElement> entry : t2e.entrySet()) {
          tagToEquipment.put(entry.getKey(), entry.getValue().getAsString());
        }
      }

      // Run analysis (autonomous when no explicit symptom was supplied)
      RootCauseReport report = autonomous ? rca.analyzeAutonomous(tagToEquipment) : rca.analyze();

      // Return JSON report, enriched with autonomous-investigation findings
      String reportJson = report.toJson();
      JsonObject result = JsonParser.parseString(reportJson).getAsJsonObject();
      result.addProperty("status", "success");
      result.addProperty("mode", autonomous ? "autonomous" : "symptom");
      result.addProperty("inferredSymptom", report.getSymptom() != null ? report.getSymptom().name() : "");
      result.add("anomalies", anomaliesToJson(rca));
      result.add("relationships", relationshipsToJson(rca));
      result.add("causalEdges", causalEdgesToJson(rca));
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

  /** Callback used while copying JSON string maps into a diagnosis case. */
  private interface StringMapConsumer {
    void accept(String key, String value);
  }

  /** Copies a JSON object's scalar members to a callback as strings. */
  private static void addStringMap(JsonObject parent, String field, StringMapConsumer consumer) {
    if (!parent.has(field) || !parent.get(field).isJsonObject()) {
      return;
    }
    for (Map.Entry<String, JsonElement> entry : parent.getAsJsonObject(field).entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue().getAsString());
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
   * Builds a JSON array of the anomalies detected by an autonomous analysis.
   *
   * @param rca the analyzer after an autonomous run
   * @return JSON array of anomalies (empty when none were detected)
   */
  private static JsonArray anomaliesToJson(RootCauseAnalyzer rca) {
    JsonArray arr = new JsonArray();
    for (AnomalyScanner.Anomaly a : rca.getLastAnomalies()) {
      JsonObject o = new JsonObject();
      o.addProperty("tag", a.getTag());
      o.addProperty("kind", a.getKind().name());
      o.addProperty("severity", a.getSeverity());
      o.addProperty("latestValue", a.getLatestValue());
      o.addProperty("description", a.getDescription());
      arr.add(o);
    }
    return arr;
  }

  /**
   * Builds a JSON array of the lead-lag relationships discovered by an autonomous analysis.
   *
   * @param rca the analyzer after an autonomous run
   * @return JSON array of relationships (empty when none were discovered)
   */
  private static JsonArray relationshipsToJson(RootCauseAnalyzer rca) {
    JsonArray arr = new JsonArray();
    for (RelationshipGraph.Relationship r : rca.getLastRelationships()) {
      JsonObject o = new JsonObject();
      o.addProperty("source", r.getSource());
      o.addProperty("target", r.getTarget());
      o.addProperty("direction", r.getDirection().name());
      o.addProperty("lagSamples", r.getLagSamples());
      o.addProperty("lagSeconds", r.getLagSeconds());
      o.addProperty("correlation", r.getCorrelation());
      arr.add(o);
    }
    return arr;
  }

  /**
   * Builds a JSON array of the topology-classified causal edges from an autonomous analysis.
   *
   * @param rca the analyzer after an autonomous run
   * @return JSON array of causal edges (empty when no tag-to-equipment map was supplied)
   */
  private static JsonArray causalEdgesToJson(RootCauseAnalyzer rca) {
    JsonArray arr = new JsonArray();
    for (CausalTopologyModel.CausalEdge e : rca.getLastCausalEdges()) {
      JsonObject o = new JsonObject();
      o.addProperty("source", e.getRelationship().getSource());
      o.addProperty("target", e.getRelationship().getTarget());
      o.addProperty("verdict", e.getVerdict().name());
      o.addProperty("leaderEquipment", e.getLeaderEquipment());
      o.addProperty("followerEquipment", e.getFollowerEquipment());
      o.addProperty("correlation", e.getRelationship().getCorrelation());
      o.addProperty("rationale", e.getRationale());
      arr.add(o);
    }
    return arr;
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
