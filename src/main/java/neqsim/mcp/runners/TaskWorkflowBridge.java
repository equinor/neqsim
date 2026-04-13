package neqsim.mcp.runners;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Bridges MCP tool outputs to the task_solve results.json format used by the NeqSim engineering
 * task-solving workflow.
 *
 * <p>
 * The task_solve workflow (docs/development/TASK_SOLVING_GUIDE.md) expects a results.json file with
 * specific keys: key_results, validation, approach, conclusions, figure_captions,
 * figure_discussion, equations, tables, references, uncertainty, risk_evaluation.
 * </p>
 *
 * <p>
 * This class takes raw MCP tool outputs (flash results, process results, PVT data, etc.) and
 * transforms them into the task_solve results.json schema so that the report generator
 * (step3_report/generate_report.py) can produce professional engineering reports.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TaskWorkflowBridge {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — utility class.
   */
  private TaskWorkflowBridge() {}

  /**
   * Converts MCP tool output into task_solve results.json format.
   *
   * <p>
   * Input JSON format:
   * </p>
   *
   * <pre>
   * {
   *   "action": "toResultsJson",
   *   "toolOutput": { ... },        // Raw output from any MCP tool
   *   "sourceRunner": "runFlash",    // Which tool produced this
   *   "taskTitle": "Methane density analysis",
   *   "approach": "Used SRK EOS with classic mixing rule",
   *   "conclusions": "Density matches NIST within 2%"
   * }
   * </pre>
   *
   * @param json the bridge request JSON
   * @return JSON in task_solve results.json format
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("Bridge input JSON is null or empty");
    }

    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "toResultsJson";

      switch (action) {
        case "toResultsJson":
          return convertToResultsJson(input);
        case "getSchema":
          return getResultsJsonSchema();
        default:
          return errorJson("Unknown action: " + action + ". Use toResultsJson or getSchema.");
      }
    } catch (Exception e) {
      return errorJson("Bridge operation failed: " + e.getMessage());
    }
  }

  /**
   * Converts tool output to the task_solve results.json format.
   *
   * @param input the bridge request with toolOutput, sourceRunner, etc.
   * @return complete results.json content
   */
  private static String convertToResultsJson(JsonObject input) {
    JsonObject results = new JsonObject();

    String sourceRunner =
        input.has("sourceRunner") ? input.get("sourceRunner").getAsString() : "unknown";
    JsonObject toolOutput = input.has("toolOutput") ? input.getAsJsonObject("toolOutput") : null;

    if (toolOutput == null) {
      return errorJson("No toolOutput provided. Pass the raw output from an MCP tool.");
    }

    // key_results — extract the most important numerical values
    JsonObject keyResults = extractKeyResults(toolOutput, sourceRunner);
    results.add("key_results", keyResults);

    // validation — extract validation status
    JsonObject validation = extractValidation(toolOutput);
    results.add("validation", validation);

    // approach
    String approach = input.has("approach") ? input.get("approach").getAsString()
        : "Calculated using NeqSim MCP server (" + sourceRunner + ")";
    results.addProperty("approach", approach);

    // conclusions
    String conclusions = input.has("conclusions") ? input.get("conclusions").getAsString()
        : "Results generated via " + sourceRunner + " tool.";
    results.addProperty("conclusions", conclusions);

    // Empty placeholders for optional fields
    results.add("figure_captions", new JsonObject());
    results.add("figure_discussion", new JsonArray());
    results.add("equations", new JsonArray());
    results.add("tables", new JsonArray());
    results.add("references", new JsonArray());

    // Add source metadata
    JsonObject meta = new JsonObject();
    meta.addProperty("source", "neqsim-mcp-server");
    meta.addProperty("tool", sourceRunner);
    meta.addProperty("generated_by", "TaskWorkflowBridge");
    results.add("_meta", meta);

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.add("resultsJson", results);

    return GSON.toJson(response);
  }

  /**
   * Extracts key results from various tool outputs.
   *
   * @param output raw tool output
   * @param runner the source runner name
   * @return key_results object with labeled values
   */
  private static JsonObject extractKeyResults(JsonObject output, String runner) {
    JsonObject kr = new JsonObject();

    switch (runner) {
      case "runFlash":
        extractFlashResults(output, kr);
        break;
      case "runProcess":
        extractProcessResults(output, kr);
        break;
      case "runPVT":
        extractPVTResults(output, kr);
        break;
      case "runPipeline":
        extractPipelineResults(output, kr);
        break;
      case "calculateStandard":
        extractStandardsResults(output, kr);
        break;
      case "runFieldEconomics":
        extractEconomicsResults(output, kr);
        break;
      default:
        // Generic: just flag the status
        kr.addProperty("status", output.has("status") ? output.get("status").getAsString() : "n/a");
        break;
    }

    return kr;
  }

  /**
   * Extracts key flash results.
   *
   * @param output flash tool output
   * @param kr key_results to populate
   */
  private static void extractFlashResults(JsonObject output, JsonObject kr) {
    if (output.has("fluid")) {
      JsonObject fluid = output.getAsJsonObject("fluid");
      if (fluid.has("conditions")) {
        JsonObject cond = fluid.getAsJsonObject("conditions");
        if (cond.has("temperature_K")) {
          double tK = cond.get("temperature_K").getAsDouble();
          kr.addProperty("temperature_C", tK - 273.15);
        }
        if (cond.has("pressure_bara")) {
          kr.add("pressure_bar", cond.get("pressure_bara"));
        }
      }
      if (fluid.has("properties")) {
        JsonObject props = fluid.getAsJsonObject("properties");
        if (props.has("density_kgm3")) {
          kr.add("density_kgm3", props.get("density_kgm3"));
        }
        if (props.has("molarMass_kgmol")) {
          kr.add("molar_mass_kgmol", props.get("molarMass_kgmol"));
        }
      }
    }
    if (output.has("flash")) {
      JsonObject flash = output.getAsJsonObject("flash");
      if (flash.has("numberOfPhases")) {
        kr.add("number_of_phases", flash.get("numberOfPhases"));
      }
    }
  }

  /**
   * Extracts key process results.
   *
   * @param output process tool output
   * @param kr key_results to populate
   */
  private static void extractProcessResults(JsonObject output, JsonObject kr) {
    if (output.has("equipment")) {
      kr.addProperty("equipment_count", output.getAsJsonArray("equipment").size());
    }
    if (output.has("streams")) {
      kr.addProperty("stream_count", output.getAsJsonArray("streams").size());
    }
  }

  /**
   * Extracts key PVT results.
   *
   * @param output PVT tool output
   * @param kr key_results to populate
   */
  private static void extractPVTResults(JsonObject output, JsonObject kr) {
    if (output.has("results")) {
      JsonObject pvtResults = output.getAsJsonObject("results");
      if (pvtResults.has("saturationPressure_bara")) {
        kr.add("saturation_pressure_bar", pvtResults.get("saturationPressure_bara"));
      }
    }
    if (output.has("experiment")) {
      kr.add("experiment_type", output.get("experiment"));
    }
  }

  /**
   * Extracts key pipeline results.
   *
   * @param output pipeline tool output
   * @param kr key_results to populate
   */
  private static void extractPipelineResults(JsonObject output, JsonObject kr) {
    if (output.has("results")) {
      JsonObject pipeResults = output.getAsJsonObject("results");
      if (pipeResults.has("pressureDrop_bar")) {
        kr.add("pressure_drop_bar", pipeResults.get("pressureDrop_bar"));
      }
      if (pipeResults.has("outletTemperature_C")) {
        kr.add("outlet_temperature_C", pipeResults.get("outletTemperature_C"));
      }
    }
  }

  /**
   * Extracts key standards results.
   *
   * @param output standards tool output
   * @param kr key_results to populate
   */
  private static void extractStandardsResults(JsonObject output, JsonObject kr) {
    if (output.has("results") || output.has("properties")) {
      JsonObject results = output.has("results") ? output.getAsJsonObject("results")
          : output.getAsJsonObject("properties");
      // Copy all standard result values
      for (Map.Entry<String, JsonElement> entry : results.entrySet()) {
        kr.add(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Extracts key economics results.
   *
   * @param output economics tool output
   * @param kr key_results to populate
   */
  private static void extractEconomicsResults(JsonObject output, JsonObject kr) {
    if (output.has("results")) {
      JsonObject econResults = output.getAsJsonObject("results");
      for (String key : new String[] {"npv_musd", "irr_pct", "payback_years"}) {
        if (econResults.has(key)) {
          kr.add(key, econResults.get(key));
        }
      }
    }
  }

  /**
   * Extracts validation information from tool output.
   *
   * @param output raw tool output
   * @return validation object
   */
  private static JsonObject extractValidation(JsonObject output) {
    JsonObject val = new JsonObject();

    if (output.has("validation")) {
      val = output.getAsJsonObject("validation").deepCopy();
    } else if (output.has("status")) {
      val.addProperty("status", output.get("status").getAsString());
      val.addProperty("acceptance_criteria_met",
          "success".equals(output.get("status").getAsString()));
    }

    return val;
  }

  /**
   * Returns the results.json schema documentation.
   *
   * @return JSON describing the task_solve results.json schema
   */
  private static String getResultsJsonSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("status", "success");
    schema.addProperty("description",
        "Schema for task_solve results.json — the standard output format "
            + "for engineering task deliverables. Feed this to generate_report.py.");

    JsonObject fields = new JsonObject();
    fields.addProperty("key_results",
        "Object with labeled numerical results. Use suffixes like _C, _bar, _kg for auto-unit detection.");
    fields.addProperty("validation",
        "Object with pass/fail checks. Include 'acceptance_criteria_met': true/false.");
    fields.addProperty("approach", "String describing the methodology.");
    fields.addProperty("conclusions", "String with key conclusions.");
    fields.addProperty("figure_captions", "Object mapping filename.png to caption string.");
    fields.addProperty("figure_discussion",
        "Array of discussion objects with observation, mechanism, implication, recommendation.");
    fields.addProperty("equations", "Array of {label, latex} objects.");
    fields.addProperty("tables", "Array of {title, headers, rows} objects.");
    fields.addProperty("references", "Array of {id, text} objects.");
    fields.addProperty("uncertainty",
        "Object with method, n_simulations, P10/P50/P90, tornado data.");
    fields.addProperty("risk_evaluation",
        "Object with risks array (id, description, category, likelihood, consequence, "
            + "risk_level, mitigation), overall_risk_level, and risk_matrix_used.");
    fields.addProperty("benchmark_validation",
        "Array of {test, expected, actual, tolerance, pass} objects.");

    schema.add("fields", fields);
    return GSON.toJson(schema);
  }

  /**
   * Formats an error response.
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
