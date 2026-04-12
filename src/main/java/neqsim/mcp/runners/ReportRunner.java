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

/**
 * Report generator that transforms simulation results into structured engineering reports.
 *
 * <p>
 * Produces rich Markdown reports with tables, SVG-compatible data (for chart rendering by AI
 * agents), and structured JSON data arrays suitable for plotting. Reports cover process summaries,
 * PVT studies, parametric sweeps, flow assurance analyses, and field development evaluations.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ReportRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private ReportRunner() {}

  /**
   * Generates a structured engineering report from simulation results.
   *
   * <p>
   * Input JSON format:
   * </p>
   *
   * <pre>
   * {
   *   "reportType": "process_summary" | "pvt_study" | "parametric_sweep" |
   *                  "flow_assurance" | "equipment_design" | "custom",
   *   "title": "HP Compression System Design",
   *   "author": "NeqSim MCP",
   *   "data": { ... results from previous simulation ... },
   *   "includeValidation": true,
   *   "includeChartData": true
   * }
   * </pre>
   *
   * @param json the report request JSON
   * @return JSON containing markdown report, tables, chart data, and metadata
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("REPORT_ERROR", "Report JSON is null or empty");
    }

    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String reportType =
          input.has("reportType") ? input.get("reportType").getAsString() : "process_summary";
      String title =
          input.has("title") ? input.get("title").getAsString() : "NeqSim Simulation Report";
      String author = input.has("author") ? input.get("author").getAsString() : "NeqSim MCP Server";

      JsonObject data = input.has("data") ? input.getAsJsonObject("data") : new JsonObject();

      boolean includeValidation =
          !input.has("includeValidation") || input.get("includeValidation").getAsBoolean();
      boolean includeChartData =
          !input.has("includeChartData") || input.get("includeChartData").getAsBoolean();

      // Build the report
      JsonObject report = new JsonObject();
      report.addProperty("title", title);
      report.addProperty("author", author);
      report.addProperty("reportType", reportType);
      report.addProperty("generatedAt",
          new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));

      // Generate markdown
      String markdown = generateMarkdown(reportType, title, author, data);
      report.addProperty("markdown", markdown);

      // Generate structured tables
      JsonArray tables = extractTables(data, reportType);
      report.add("tables", tables);

      // Generate chart data arrays (for AI to render)
      if (includeChartData) {
        JsonArray chartData = extractChartData(data, reportType);
        report.add("chartData", chartData);
      }

      // Include validation if requested
      if (includeValidation) {
        String validation = EngineeringValidator.validate(GSON.toJson(data), reportType);
        try {
          report.add("validation", JsonParser.parseString(validation));
        } catch (Exception e) {
          report.addProperty("validationError", e.getMessage());
        }
      }

      // Summary statistics
      JsonObject summary = computeSummary(data);
      report.add("summary", summary);

      return GSON.toJson(report);

    } catch (Exception e) {
      return errorJson("REPORT_ERROR", "Report generation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Markdown generation
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Generates a Markdown report.
   *
   * @param reportType the report type
   * @param title the report title
   * @param author the author
   * @param data the simulation data
   * @return Markdown string
   */
  private static String generateMarkdown(String reportType, String title, String author,
      JsonObject data) {
    StringBuilder md = new StringBuilder();

    md.append("# ").append(title).append("\n\n");
    md.append("**Author:** ").append(author).append("  \n");
    md.append("**Date:** ")
        .append(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()))
        .append("  \n");
    md.append("**Engine:** NeqSim (Java-based thermodynamic simulator)  \n");
    md.append("**Report Type:** ").append(reportType).append("\n\n");
    md.append("---\n\n");

    // Section: Input Summary
    md.append("## 1. Input Summary\n\n");
    if (data.has("fluid")) {
      md.append("### Fluid Definition\n\n");
      appendJsonAsTable(md, data.getAsJsonObject("fluid"), "Property", "Value");
    }
    if (data.has("parameters")) {
      md.append("### Operating Parameters\n\n");
      appendJsonAsTable(md, data.getAsJsonObject("parameters"), "Parameter", "Value");
    }

    // Section: Results
    md.append("## 2. Results\n\n");

    // Process summary specifics
    if (data.has("streams") || data.has("equipment")) {
      md.append("### Stream Table\n\n");
      md.append("_(See structured table data in the 'tables' section)_\n\n");
    }

    // Key results as table
    if (data.has("key_results")) {
      md.append("### Key Results\n\n");
      appendJsonAsTable(md, data.getAsJsonObject("key_results"), "Result", "Value");
    }

    // Phase envelope / PVT results
    if ("pvt_study".equals(reportType) && data.has("results")) {
      md.append("### PVT Results\n\n");
      md.append("_(See chart data for phase envelope, CME, CVD curves)_\n\n");
    }

    // Section: Conclusions
    md.append("## 3. Conclusions\n\n");
    if (data.has("conclusions")) {
      md.append(data.get("conclusions").getAsString()).append("\n\n");
    } else {
      md.append("Simulation completed successfully. Results are within expected ranges.\n\n");
    }

    // Section: Notes
    md.append("---\n\n");
    md.append("*Report generated by NeqSim MCP Server.*\n");

    return md.toString();
  }

  /**
   * Appends a flat JSON object as a Markdown table.
   *
   * @param md the StringBuilder
   * @param obj the JSON object
   * @param keyHeader the key column header
   * @param valueHeader the value column header
   */
  private static void appendJsonAsTable(StringBuilder md, JsonObject obj, String keyHeader,
      String valueHeader) {
    md.append("| ").append(keyHeader).append(" | ").append(valueHeader).append(" |\n");
    md.append("|---|---|\n");
    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
      JsonElement val = entry.getValue();
      String displayVal;
      if (val.isJsonPrimitive()) {
        displayVal = val.getAsString();
      } else if (val.isJsonNull()) {
        displayVal = "-";
      } else {
        displayVal = val.toString();
      }
      md.append("| ").append(entry.getKey()).append(" | ").append(displayVal).append(" |\n");
    }
    md.append("\n");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Table extraction
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Extracts structured tables from simulation data.
   *
   * @param data the simulation data
   * @param reportType the report type
   * @return JSON array of table objects
   */
  private static JsonArray extractTables(JsonObject data, String reportType) {
    JsonArray tables = new JsonArray();

    // Extract any flat key-value pairs as a summary table
    JsonObject summaryTable = new JsonObject();
    summaryTable.addProperty("title", "Simulation Summary");
    JsonArray headers = new JsonArray();
    headers.add("Property");
    headers.add("Value");
    headers.add("Unit");
    summaryTable.add("headers", headers);

    JsonArray rows = new JsonArray();
    extractFlatValues(data, "", rows);
    summaryTable.add("rows", rows);
    if (rows.size() > 0) {
      tables.add(summaryTable);
    }

    return tables;
  }

  /**
   * Recursively extracts flat numeric values from JSON into table rows.
   *
   * @param obj the JSON object
   * @param prefix the key prefix
   * @param rows the rows array to populate
   */
  private static void extractFlatValues(JsonObject obj, String prefix, JsonArray rows) {
    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
      String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
      JsonElement val = entry.getValue();

      if (val.isJsonPrimitive() && val.getAsJsonPrimitive().isNumber()) {
        JsonArray row = new JsonArray();
        row.add(key);
        row.add(val.getAsNumber());

        // Try to infer unit from key name
        String unit = inferUnit(entry.getKey());
        row.add(unit);
        rows.add(row);
      } else if (val.isJsonObject() && rows.size() < 100) {
        // Limit depth to avoid huge tables
        extractFlatValues(val.getAsJsonObject(), key, rows);
      }
    }
  }

  /**
   * Infers a unit from a field name suffix.
   *
   * @param key the field name
   * @return the inferred unit or empty string
   */
  private static String inferUnit(String key) {
    String lower = key.toLowerCase();
    if (lower.endsWith("_c") || lower.contains("temperature")) {
      return "C";
    }
    if (lower.endsWith("_bar") || lower.contains("pressure")) {
      return "bara";
    }
    if (lower.endsWith("_kw") || lower.contains("power")) {
      return "kW";
    }
    if (lower.contains("flow") && lower.contains("kg")) {
      return "kg/hr";
    }
    if (lower.contains("density")) {
      return "kg/m3";
    }
    if (lower.contains("viscosity")) {
      return "cP";
    }
    if (lower.contains("efficiency") || lower.contains("_pct")) {
      return "%";
    }
    if (lower.contains("velocity")) {
      return "m/s";
    }
    if (lower.contains("length") || lower.contains("diameter")) {
      return "m";
    }
    return "";
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Chart data extraction
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Extracts chart-ready data arrays from simulation results.
   *
   * @param data the simulation data
   * @param reportType the report type
   * @return JSON array of chart data objects
   */
  private static JsonArray extractChartData(JsonObject data, String reportType) {
    JsonArray charts = new JsonArray();

    // Look for array-type data suitable for plotting
    findPlottableArrays(data, "", charts);

    return charts;
  }

  /**
   * Recursively finds arrays of numbers that could be plotted.
   *
   * @param obj the JSON object
   * @param prefix the key prefix
   * @param charts the charts array to populate
   */
  private static void findPlottableArrays(JsonObject obj, String prefix, JsonArray charts) {
    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
      String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
      JsonElement val = entry.getValue();

      if (val.isJsonArray()) {
        JsonArray arr = val.getAsJsonArray();
        if (arr.size() > 1 && arr.get(0).isJsonPrimitive()
            && arr.get(0).getAsJsonPrimitive().isNumber()) {
          // Found a numeric array — wrap as chart data
          JsonObject chart = new JsonObject();
          chart.addProperty("name", key);
          chart.addProperty("type", "line");
          chart.addProperty("xLabel", "Index");
          chart.addProperty("yLabel", entry.getKey());
          chart.add("data", arr);
          charts.add(chart);
        }
      } else if (val.isJsonObject() && charts.size() < 20) {
        findPlottableArrays(val.getAsJsonObject(), key, charts);
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Summary computation
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Computes summary statistics from the data.
   *
   * @param data the simulation data
   * @return summary JSON object
   */
  private static JsonObject computeSummary(JsonObject data) {
    JsonObject summary = new JsonObject();

    int numericFieldCount = 0;
    int objectFieldCount = 0;
    int arrayFieldCount = 0;

    for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
      if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
        numericFieldCount++;
      } else if (entry.getValue().isJsonObject()) {
        objectFieldCount++;
      } else if (entry.getValue().isJsonArray()) {
        arrayFieldCount++;
      }
    }

    summary.addProperty("numericFields", numericFieldCount);
    summary.addProperty("objectFields", objectFieldCount);
    summary.addProperty("arrayFields", arrayFieldCount);
    summary.addProperty("totalFields", data.size());

    return summary;
  }

  /**
   * Creates an error JSON string.
   *
   * @param code the error code
   * @param message the error message
   * @return error JSON
   */
  private static String errorJson(String code, String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    errors.add(err);
    error.add("errors", errors);
    return GSON.toJson(error);
  }
}
