package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Generates inline visual content (SVG charts, Mermaid diagrams, HTML tables) that can be rendered
 * directly in chat interfaces supporting rich content.
 *
 * <p>
 * Output types:
 * <ul>
 * <li>Phase envelope SVG — PT diagram with phase boundaries</li>
 * <li>Process flowsheet — Mermaid diagram of equipment connections</li>
 * <li>Compressor map SVG — performance curves with operating point</li>
 * <li>Property comparison — styled HTML table comparing multi-case results</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class VisualizationRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private VisualizationRunner() {}

  /**
   * Main entry point for visualization generation.
   *
   * @param json JSON with visualization type and data
   * @return JSON with rendered content (SVG, Mermaid, HTML)
   */
  public static String run(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String type = input.has("type") ? input.get("type").getAsString() : "";

      switch (type) {
        case "phaseEnvelope":
          return generatePhaseEnvelopeSVG(input);
        case "flowsheet":
          return generateFlowsheetDiagram(input);
        case "compressorMap":
          return generateCompressorMapSVG(input);
        case "propertyTable":
          return generateStyledTable(input);
        case "barChart":
          return generateBarChartSVG(input);
        case "pieChart":
          return generatePieChartSVG(input);
        case "lineChart":
          return generateLineChartSVG(input);
        default:
          return errorJson("Unknown visualization type: " + type
              + ". Use: phaseEnvelope, flowsheet, compressorMap, propertyTable, barChart, "
              + "pieChart, lineChart");
      }
    } catch (Exception e) {
      return errorJson("Visualization failed: " + e.getMessage());
    }
  }

  /**
   * Generates an SVG phase envelope (PT diagram) for a fluid composition.
   *
   * @param input JSON with components and model
   * @return JSON with SVG string and data points
   */
  private static String generatePhaseEnvelopeSVG(JsonObject input) {
    JsonObject components =
        input.has("components") ? input.getAsJsonObject("components") : new JsonObject();
    String model = input.has("model") ? input.get("model").getAsString() : "SRK";

    try {
      // Calculate phase envelope points
      double refTemp = 273.15 + 25.0;
      double refPres = 50.0;
      SystemInterface fluid = FlashRunner.createFluid(model, refTemp, refPres);
      for (Map.Entry<String, com.google.gson.JsonElement> entry : components.entrySet()) {
        fluid.addComponent(entry.getKey(), entry.getValue().getAsDouble());
      }
      fluid.setMixingRule("classic");

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.calcPTphaseEnvelope(true, 1.0);

      double[][] envData = ops.getData();
      // envData[0] = pressure, envData[1] = temperature

      if (envData == null || envData.length < 2 || envData[0].length < 3) {
        return errorJson("Phase envelope calculation returned insufficient data points");
      }

      // Find bounds
      double minT = Double.MAX_VALUE;
      double maxT = -Double.MAX_VALUE;
      double minP = Double.MAX_VALUE;
      double maxP = -Double.MAX_VALUE;

      List<double[]> validPoints = new ArrayList<double[]>();
      for (int i = 0; i < envData[0].length; i++) {
        double p = envData[0][i];
        double t = envData[1][i] - 273.15; // Convert to Celsius
        if (!Double.isNaN(p) && !Double.isNaN(t) && !Double.isInfinite(p) && !Double.isInfinite(t)
            && p > 0 && t > -274) {
          validPoints.add(new double[] {t, p});
          minT = Math.min(minT, t);
          maxT = Math.max(maxT, t);
          minP = Math.min(minP, p);
          maxP = Math.max(maxP, p);
        }
      }

      if (validPoints.size() < 3) {
        return errorJson("Not enough valid phase envelope points");
      }

      // Generate SVG
      int width = 600;
      int height = 400;
      int margin = 60;
      int plotW = width - 2 * margin;
      int plotH = height - 2 * margin;

      StringBuilder svg = new StringBuilder();
      svg.append("<svg xmlns='http://www.w3.org/2000/svg' ");
      svg.append("width='").append(width).append("' height='").append(height).append("'>\n");
      svg.append("<rect width='100%' height='100%' fill='white'/>\n");

      // Title
      svg.append("<text x='").append(width / 2).append("' y='20' ");
      svg.append("text-anchor='middle' font-size='14' font-weight='bold'>");
      svg.append("Phase Envelope</text>\n");

      // Axes
      svg.append("<line x1='").append(margin).append("' y1='").append(margin);
      svg.append("' x2='").append(margin).append("' y2='").append(height - margin);
      svg.append("' stroke='black'/>\n");
      svg.append("<line x1='").append(margin).append("' y1='").append(height - margin);
      svg.append("' x2='").append(width - margin).append("' y2='").append(height - margin);
      svg.append("' stroke='black'/>\n");

      // Axis labels
      svg.append("<text x='").append(width / 2).append("' y='").append(height - 10);
      svg.append("' text-anchor='middle' font-size='12'>Temperature (°C)</text>\n");
      svg.append("<text x='15' y='").append(height / 2);
      svg.append("' text-anchor='middle' font-size='12' ");
      svg.append("transform='rotate(-90,15,").append(height / 2).append(")'>");
      svg.append("Pressure (bar)</text>\n");

      // Plot phase envelope curve
      svg.append("<polyline points='");
      for (double[] pt : validPoints) {
        double x = margin + (pt[0] - minT) / (maxT - minT) * plotW;
        double y = (height - margin) - (pt[1] - minP) / (maxP - minP) * plotH;
        svg.append(String.format("%.1f,%.1f ", x, y));
      }
      svg.append("' fill='none' stroke='#2196F3' stroke-width='2'/>\n");

      // Axis tick labels (5 ticks each)
      for (int i = 0; i <= 4; i++) {
        double tVal = minT + (maxT - minT) * i / 4.0;
        double x = margin + plotW * i / 4.0;
        svg.append("<text x='").append(String.format("%.0f", x));
        svg.append("' y='").append(height - margin + 15);
        svg.append("' text-anchor='middle' font-size='10'>");
        svg.append(String.format("%.0f", tVal)).append("</text>\n");

        double pVal = minP + (maxP - minP) * i / 4.0;
        double y = (height - margin) - plotH * i / 4.0;
        svg.append("<text x='").append(margin - 5);
        svg.append("' y='").append(String.format("%.0f", y + 4));
        svg.append("' text-anchor='end' font-size='10'>");
        svg.append(String.format("%.1f", pVal)).append("</text>\n");
      }

      svg.append("</svg>");

      // Build response with both SVG and data
      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.addProperty("visualizationType", "phaseEnvelope");
      response.addProperty("svg", svg.toString());
      response.addProperty("mimeType", "image/svg+xml");

      JsonArray dataPoints = new JsonArray();
      for (double[] pt : validPoints) {
        JsonObject dp = new JsonObject();
        dp.addProperty("temperature_C", pt[0]);
        dp.addProperty("pressure_bar", pt[1]);
        dataPoints.add(dp);
      }
      response.add("dataPoints", dataPoints);
      response.addProperty("pointCount", validPoints.size());

      return GSON.toJson(response);

    } catch (Exception e) {
      return errorJson("Phase envelope SVG generation failed: " + e.getMessage());
    }
  }

  /**
   * Generates a Mermaid flowsheet diagram from process simulation results.
   *
   * @param input JSON with equipment list and connections
   * @return JSON with Mermaid diagram string
   */
  private static String generateFlowsheetDiagram(JsonObject input) {
    StringBuilder mermaid = new StringBuilder();
    mermaid.append("graph LR\n");

    String title = input.has("title") ? input.get("title").getAsString() : "Process Flowsheet";

    if (input.has("equipment") && input.get("equipment").isJsonArray()) {
      JsonArray equipment = input.getAsJsonArray("equipment");

      for (int i = 0; i < equipment.size(); i++) {
        JsonObject eq = equipment.get(i).getAsJsonObject();
        String name = eq.has("name") ? eq.get("name").getAsString() : "Unit" + i;
        String type = eq.has("type") ? eq.get("type").getAsString() : "Equipment";
        String safeName = name.replaceAll("[^a-zA-Z0-9]", "_");

        // Use different shapes for different equipment types
        String shape = getEquipmentShape(type);
        mermaid.append("    ").append(safeName).append(shape.replace("NAME", name));
        mermaid.append("\n");
      }

      // Add connections if provided
      if (input.has("connections") && input.get("connections").isJsonArray()) {
        JsonArray connections = input.getAsJsonArray("connections");
        for (int i = 0; i < connections.size(); i++) {
          JsonObject conn = connections.get(i).getAsJsonObject();
          String from = conn.has("from") ? conn.get("from").getAsString() : "";
          String to = conn.has("to") ? conn.get("to").getAsString() : "";
          String label = conn.has("label") ? conn.get("label").getAsString() : "";
          String safeFrom = from.replaceAll("[^a-zA-Z0-9]", "_");
          String safeTo = to.replaceAll("[^a-zA-Z0-9]", "_");

          if (label.isEmpty()) {
            mermaid.append("    ").append(safeFrom).append(" --> ").append(safeTo).append("\n");
          } else {
            mermaid.append("    ").append(safeFrom).append(" -->|").append(label).append("| ")
                .append(safeTo).append("\n");
          }
        }
      } else {
        // Auto-connect sequentially
        for (int i = 0; i < equipment.size() - 1; i++) {
          String name1 = equipment.get(i).getAsJsonObject().has("name")
              ? equipment.get(i).getAsJsonObject().get("name").getAsString()
              : "Unit" + i;
          String name2 = equipment.get(i + 1).getAsJsonObject().has("name")
              ? equipment.get(i + 1).getAsJsonObject().get("name").getAsString()
              : "Unit" + (i + 1);
          mermaid.append("    ").append(name1.replaceAll("[^a-zA-Z0-9]", "_"));
          mermaid.append(" --> ");
          mermaid.append(name2.replaceAll("[^a-zA-Z0-9]", "_")).append("\n");
        }
      }
    }

    // Style nodes by type
    mermaid.append("    style Feed fill:#e3f2fd,stroke:#1976D2\n");

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("visualizationType", "flowsheet");
    response.addProperty("title", title);
    response.addProperty("mermaid", mermaid.toString());
    response.addProperty("mimeType", "text/x-mermaid");
    response.addProperty("renderHint", "Render this Mermaid diagram in a Mermaid-capable viewer");
    return GSON.toJson(response);
  }

  /**
   * Generates an SVG bar chart for comparing values.
   *
   * @param input JSON with labels and values
   * @return JSON with SVG string
   */
  private static String generateBarChartSVG(JsonObject input) {
    String title = input.has("title") ? input.get("title").getAsString() : "Comparison Chart";
    String yLabel = input.has("yLabel") ? input.get("yLabel").getAsString() : "Value";

    JsonArray labels = input.has("labels") ? input.getAsJsonArray("labels") : new JsonArray();
    JsonArray values = input.has("values") ? input.getAsJsonArray("values") : new JsonArray();

    if (labels.size() == 0 || values.size() == 0) {
      return errorJson("Provide 'labels' and 'values' arrays for bar chart");
    }

    int n = Math.min(labels.size(), values.size());
    double maxVal = 0;
    List<Double> vals = new ArrayList<Double>();
    for (int i = 0; i < n; i++) {
      double v = values.get(i).getAsDouble();
      vals.add(v);
      maxVal = Math.max(maxVal, Math.abs(v));
    }
    if (maxVal == 0) {
      maxVal = 1;
    }

    int width = 100 + n * 80;
    int height = 350;
    int margin = 60;
    int plotH = height - 2 * margin;
    int barWidth = Math.min(50, (width - 2 * margin) / n - 10);

    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns='http://www.w3.org/2000/svg' ");
    svg.append("width='").append(width).append("' height='").append(height).append("'>\n");
    svg.append("<rect width='100%' height='100%' fill='white'/>\n");

    // Title
    svg.append("<text x='").append(width / 2).append("' y='20' ");
    svg.append("text-anchor='middle' font-size='14' font-weight='bold'>");
    svg.append(escapeXml(title)).append("</text>\n");

    // Y axis
    svg.append("<line x1='").append(margin).append("' y1='").append(margin);
    svg.append("' x2='").append(margin).append("' y2='").append(height - margin);
    svg.append("' stroke='black'/>\n");

    // Y axis label
    svg.append("<text x='15' y='").append(height / 2);
    svg.append("' text-anchor='middle' font-size='11' ");
    svg.append("transform='rotate(-90,15,").append(height / 2).append(")'>");
    svg.append(escapeXml(yLabel)).append("</text>\n");

    // Bars
    String[] colors =
        {"#2196F3", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0", "#00BCD4", "#795548", "#607D8B"};

    for (int i = 0; i < n; i++) {
      double v = vals.get(i);
      int barH = (int) (Math.abs(v) / maxVal * plotH);
      int x = margin + 15 + i * (barWidth + 10);
      int y = (height - margin) - barH;

      svg.append("<rect x='").append(x).append("' y='").append(y);
      svg.append("' width='").append(barWidth).append("' height='").append(barH);
      svg.append("' fill='").append(colors[i % colors.length]).append("'/>\n");

      // Value label on bar
      svg.append("<text x='").append(x + barWidth / 2).append("' y='").append(y - 5);
      svg.append("' text-anchor='middle' font-size='10'>");
      svg.append(String.format("%.1f", v)).append("</text>\n");

      // X axis label
      String label = labels.get(i).getAsString();
      svg.append("<text x='").append(x + barWidth / 2);
      svg.append("' y='").append(height - margin + 15);
      svg.append("' text-anchor='middle' font-size='9'>");
      svg.append(escapeXml(label.length() > 12 ? label.substring(0, 12) : label));
      svg.append("</text>\n");
    }

    svg.append("</svg>");

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("visualizationType", "barChart");
    response.addProperty("svg", svg.toString());
    response.addProperty("mimeType", "image/svg+xml");
    return GSON.toJson(response);
  }

  /**
   * Generates a compressor performance map SVG.
   *
   * @param input JSON with compressor data and operating point
   * @return JSON with SVG and data
   */
  private static String generateCompressorMapSVG(JsonObject input) {
    double inletFlow = input.has("inletFlow") ? input.get("inletFlow").getAsDouble() : 5000;
    double outletPressure =
        input.has("outletPressure") ? input.get("outletPressure").getAsDouble() : 100;
    double inletPressure = input.has("inletPressure") ? input.get("inletPressure").getAsDouble()
        : outletPressure / 3.0;
    double efficiency = input.has("efficiency") ? input.get("efficiency").getAsDouble() : 0.80;
    double power = input.has("power_kW") ? input.get("power_kW").getAsDouble() : 2500;
    double surgeFlow =
        input.has("surgeFlow") ? input.get("surgeFlow").getAsDouble() : inletFlow * 0.6;
    double stonewall =
        input.has("stonewallFlow") ? input.get("stonewallFlow").getAsDouble() : inletFlow * 1.4;
    double pressureRatio;
    if (input.has("pressureRatio")) {
      pressureRatio = input.get("pressureRatio").getAsDouble();
    } else {
      pressureRatio = outletPressure / Math.max(inletPressure, 1.0e-6);
    }

    int width = 600;
    int height = 400;
    int margin = 60;

    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns='http://www.w3.org/2000/svg' ");
    svg.append("width='").append(width).append("' height='").append(height).append("'>\n");
    svg.append("<rect width='100%' height='100%' fill='white'/>\n");

    svg.append("<text x='").append(width / 2).append("' y='20' ");
    svg.append("text-anchor='middle' font-size='14' font-weight='bold'>");
    svg.append("Compressor Performance Map</text>\n");

    // Axes
    svg.append("<line x1='").append(margin).append("' y1='").append(margin);
    svg.append("' x2='").append(margin).append("' y2='").append(height - margin);
    svg.append("' stroke='black'/>\n");
    svg.append("<line x1='").append(margin).append("' y1='").append(height - margin);
    svg.append("' x2='").append(width - margin).append("' y2='").append(height - margin);
    svg.append("' stroke='black'/>\n");

    // Axis labels
    svg.append("<text x='").append(width / 2).append("' y='").append(height - 10);
    svg.append("' text-anchor='middle' font-size='12'>Flow (Am3/hr)</text>\n");
    svg.append("<text x='12' y='").append(height / 2);
    svg.append("' text-anchor='middle' font-size='12' ");
    svg.append("transform='rotate(-90,12,").append(height / 2).append(")'>");
    svg.append("Pressure Ratio</text>\n");

    int plotW = width - 2 * margin;
    int plotH = height - 2 * margin;
    double flowMin = surgeFlow * 0.7;
    double flowMax = stonewall * 1.1;
    double prMin = pressureRatio * 0.5;
    double prMax = pressureRatio * 1.3;

    // Surge line (vertical dashed)
    double surgeX = margin + (surgeFlow - flowMin) / (flowMax - flowMin) * plotW;
    svg.append("<line x1='").append(String.format("%.0f", surgeX));
    svg.append("' y1='").append(margin).append("' x2='");
    svg.append(String.format("%.0f", surgeX)).append("' y2='").append(height - margin);
    svg.append("' stroke='red' stroke-dasharray='5,5'/>\n");
    svg.append("<text x='").append(String.format("%.0f", surgeX));
    svg.append("' y='").append(margin - 5);
    svg.append("' text-anchor='middle' font-size='10' fill='red'>Surge</text>\n");

    // Stonewall line
    double swX = margin + (stonewall - flowMin) / (flowMax - flowMin) * plotW;
    svg.append("<line x1='").append(String.format("%.0f", swX));
    svg.append("' y1='").append(margin).append("' x2='");
    svg.append(String.format("%.0f", swX)).append("' y2='").append(height - margin);
    svg.append("' stroke='orange' stroke-dasharray='5,5'/>\n");
    svg.append("<text x='").append(String.format("%.0f", swX));
    svg.append("' y='").append(margin - 5);
    svg.append("' text-anchor='middle' font-size='10' fill='orange'>Stonewall</text>\n");

    // Operating point
    double opX = margin + (inletFlow - flowMin) / (flowMax - flowMin) * plotW;
    double opY = (height - margin) - (pressureRatio - prMin) / (prMax - prMin) * plotH;
    svg.append("<circle cx='").append(String.format("%.0f", opX));
    svg.append("' cy='").append(String.format("%.0f", opY));
    svg.append("' r='8' fill='#4CAF50' stroke='#2E7D32' stroke-width='2'/>\n");
    svg.append("<text x='").append(String.format("%.0f", opX + 12));
    svg.append("' y='").append(String.format("%.0f", opY - 5));
    svg.append("' font-size='11' font-weight='bold'>Operating Point</text>\n");

    // Legend box
    svg.append("<rect x='").append(width - margin - 140).append("' y='").append(margin + 5);
    svg.append("' width='135' height='70' fill='#f5f5f5' stroke='#ccc'/>\n");
    svg.append("<text x='").append(width - margin - 130).append("' y='").append(margin + 22);
    svg.append("' font-size='10'>Flow: ").append(String.format("%.0f", inletFlow));
    svg.append(" Am3/hr</text>\n");
    svg.append("<text x='").append(width - margin - 130).append("' y='").append(margin + 36);
    svg.append("' font-size='10'>PR: ").append(String.format("%.2f", pressureRatio));
    svg.append("</text>\n");
    svg.append("<text x='").append(width - margin - 130).append("' y='").append(margin + 50);
    svg.append("' font-size='10'>Eff: ").append(String.format("%.1f%%", efficiency * 100));
    svg.append("</text>\n");
    svg.append("<text x='").append(width - margin - 130).append("' y='").append(margin + 64);
    svg.append("' font-size='10'>Power: ").append(String.format("%.0f", power));
    svg.append(" kW</text>\n");

    svg.append("</svg>");

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("visualizationType", "compressorMap");
    response.addProperty("svg", svg.toString());
    response.addProperty("mimeType", "image/svg+xml");
    return GSON.toJson(response);
  }

  /**
   * Generates a styled HTML table for property comparisons.
   *
   * @param input JSON with headers and rows
   * @return JSON with HTML table string
   */
  private static String generateStyledTable(JsonObject input) {
    String title = input.has("title") ? input.get("title").getAsString() : "Results Table";
    JsonArray headers = input.has("headers") ? input.getAsJsonArray("headers") : new JsonArray();
    JsonArray rows = input.has("rows") ? input.getAsJsonArray("rows") : new JsonArray();

    StringBuilder html = new StringBuilder();
    html.append("<table style='border-collapse:collapse;width:100%;font-family:sans-serif;");
    html.append("font-size:13px;'>\n");
    html.append("<caption style='font-weight:bold;font-size:14px;padding:8px;'>");
    html.append(escapeHtml(title)).append("</caption>\n");

    // Header row
    html.append("<tr style='background:#1976D2;color:white;'>\n");
    for (int i = 0; i < headers.size(); i++) {
      html.append("  <th style='padding:8px 12px;text-align:left;border:1px solid #ddd;'>");
      html.append(escapeHtml(headers.get(i).getAsString()));
      html.append("</th>\n");
    }
    html.append("</tr>\n");

    // Data rows
    for (int r = 0; r < rows.size(); r++) {
      JsonArray row = rows.get(r).getAsJsonArray();
      String bg = r % 2 == 0 ? "#f5f5f5" : "white";
      html.append("<tr style='background:").append(bg).append(";'>\n");
      for (int c = 0; c < row.size(); c++) {
        html.append("  <td style='padding:6px 12px;border:1px solid #ddd;'>");
        html.append(escapeHtml(row.get(c).getAsString()));
        html.append("</td>\n");
      }
      html.append("</tr>\n");
    }
    html.append("</table>");

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("visualizationType", "propertyTable");
    response.addProperty("html", html.toString());
    response.addProperty("mimeType", "text/html");
    return GSON.toJson(response);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Maps equipment type to Mermaid shape syntax.
   *
   * @param type the equipment type
   * @return the Mermaid shape string with NAME placeholder
   */
  private static String getEquipmentShape(String type) {
    String lower = type != null ? type.toLowerCase() : "";
    if (lower.contains("stream") || lower.contains("feed")) {
      return "([NAME])";
    }
    if (lower.contains("separator") || lower.contains("vessel")) {
      return "{{NAME}}";
    }
    if (lower.contains("compressor")) {
      return ">NAME]";
    }
    if (lower.contains("valve") || lower.contains("throttl")) {
      return "{NAME}";
    }
    if (lower.contains("heat") || lower.contains("cooler") || lower.contains("heater")) {
      return "[[NAME]]";
    }
    if (lower.contains("mixer")) {
      return "((NAME))";
    }
    if (lower.contains("splitter")) {
      return "((NAME))";
    }
    if (lower.contains("column") || lower.contains("distill")) {
      return "[/NAME\\]";
    }
    return "[NAME]";
  }

  /**
   * Escapes XML special characters.
   *
   * @param text the text to escape
   * @return escaped text
   */
  private static String escapeXml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("'", "&apos;").replace("\"", "&quot;");
  }

  /**
   * Escapes HTML special characters.
   *
   * @param text the text to escape
   * @return escaped text
   */
  private static String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"",
        "&quot;");
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param message the error message
   * @return the JSON string
   */
  private static String errorJson(String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("message", message);
    return GSON.toJson(error);
  }

  /**
   * Generates an SVG pie chart showing proportions.
   *
   * @param input JSON with categories and values arrays
   * @return JSON with SVG string
   */
  private static String generatePieChartSVG(JsonObject input) {
    String title = input.has("title") ? input.get("title").getAsString() : "Pie Chart";
    JsonArray categories =
        input.has("categories") ? input.getAsJsonArray("categories") : new JsonArray();
    JsonArray values = input.has("values") ? input.getAsJsonArray("values") : new JsonArray();

    if (categories.size() == 0 || values.size() == 0) {
      return errorJson("Provide 'categories' and 'values' arrays for pie chart");
    }

    int n = Math.min(categories.size(), values.size());
    double total = 0;
    List<Double> vals = new ArrayList<Double>();
    for (int i = 0; i < n; i++) {
      double v = Math.abs(values.get(i).getAsDouble());
      vals.add(v);
      total += v;
    }
    if (total == 0) {
      total = 1;
    }

    int size = 400;
    int cx = 160;
    int cy = 180;
    int r = 120;

    String[] colors =
        {"#2196F3", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0", "#00BCD4", "#795548", "#607D8B"};

    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns='http://www.w3.org/2000/svg' ");
    svg.append("width='").append(size).append("' height='").append(size).append("'>\n");
    svg.append("<rect width='100%' height='100%' fill='white'/>\n");

    // Title
    svg.append("<text x='").append(size / 2).append("' y='25' ");
    svg.append("text-anchor='middle' font-size='14' font-weight='bold'>");
    svg.append(escapeXml(title)).append("</text>\n");

    double startAngle = 0;
    for (int i = 0; i < n; i++) {
      double fraction = vals.get(i) / total;
      double angle = fraction * 2 * Math.PI;
      double endAngle = startAngle + angle;

      double x1 = cx + r * Math.cos(startAngle);
      double y1 = cy + r * Math.sin(startAngle);
      double x2 = cx + r * Math.cos(endAngle);
      double y2 = cy + r * Math.sin(endAngle);
      int largeArc = angle > Math.PI ? 1 : 0;

      svg.append("<path d='M ").append(cx).append(",").append(cy);
      svg.append(" L ").append(String.format("%.1f", x1)).append(",");
      svg.append(String.format("%.1f", y1));
      svg.append(" A ").append(r).append(",").append(r).append(" 0 ");
      svg.append(largeArc).append(",1 ");
      svg.append(String.format("%.1f", x2)).append(",");
      svg.append(String.format("%.1f", y2)).append(" Z'");
      svg.append(" fill='").append(colors[i % colors.length]).append("'/>\n");

      startAngle = endAngle;
    }

    // Legend
    int legendX = 310;
    int legendY = 60;
    for (int i = 0; i < n; i++) {
      svg.append("<rect x='").append(legendX).append("' y='").append(legendY + i * 22);
      svg.append("' width='12' height='12' fill='").append(colors[i % colors.length]);
      svg.append("'/>\n");
      svg.append("<text x='").append(legendX + 18).append("' y='").append(legendY + i * 22 + 11);
      svg.append("' font-size='10'>");
      svg.append(escapeXml(categories.get(i).getAsString()));
      svg.append(" (").append(String.format("%.1f%%", vals.get(i) / total * 100)).append(")");
      svg.append("</text>\n");
    }

    svg.append("</svg>");

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("visualizationType", "pieChart");
    response.addProperty("title", title);
    response.addProperty("svg", svg.toString());
    response.addProperty("mimeType", "image/svg+xml");
    return GSON.toJson(response);
  }

  /**
   * Generates an SVG line chart for trend/time-series data.
   *
   * @param input JSON with xValues, yValues arrays, labels, and optional series
   * @return JSON with SVG string
   */
  private static String generateLineChartSVG(JsonObject input) {
    String title = input.has("title") ? input.get("title").getAsString() : "Line Chart";
    String xLabel = input.has("xLabel") ? input.get("xLabel").getAsString() : "X";
    String yLabel = input.has("yLabel") ? input.get("yLabel").getAsString() : "Y";

    JsonArray xValues = input.has("xValues") ? input.getAsJsonArray("xValues") : new JsonArray();
    JsonArray yValues = input.has("yValues") ? input.getAsJsonArray("yValues") : new JsonArray();

    if (xValues.size() == 0 || yValues.size() == 0) {
      return errorJson("Provide 'xValues' and 'yValues' arrays for line chart");
    }

    int n = Math.min(xValues.size(), yValues.size());
    List<Double> xVals = new ArrayList<Double>();
    List<Double> yVals = new ArrayList<Double>();
    double xMin = Double.MAX_VALUE;
    double xMax = -Double.MAX_VALUE;
    double yMin = Double.MAX_VALUE;
    double yMax = -Double.MAX_VALUE;

    for (int i = 0; i < n; i++) {
      double x = xValues.get(i).getAsDouble();
      double y = yValues.get(i).getAsDouble();
      xVals.add(x);
      yVals.add(y);
      xMin = Math.min(xMin, x);
      xMax = Math.max(xMax, x);
      yMin = Math.min(yMin, y);
      yMax = Math.max(yMax, y);
    }

    if (xMax == xMin) {
      xMax = xMin + 1;
    }
    if (yMax == yMin) {
      yMax = yMin + 1;
    }

    int width = 600;
    int height = 400;
    int margin = 65;
    int plotW = width - 2 * margin;
    int plotH = height - 2 * margin;

    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns='http://www.w3.org/2000/svg' ");
    svg.append("width='").append(width).append("' height='").append(height).append("'>\n");
    svg.append("<rect width='100%' height='100%' fill='white'/>\n");

    // Title
    svg.append("<text x='").append(width / 2).append("' y='20' ");
    svg.append("text-anchor='middle' font-size='14' font-weight='bold'>");
    svg.append(escapeXml(title)).append("</text>\n");

    // Axes
    svg.append("<line x1='").append(margin).append("' y1='").append(margin);
    svg.append("' x2='").append(margin).append("' y2='").append(height - margin);
    svg.append("' stroke='black'/>\n");
    svg.append("<line x1='").append(margin).append("' y1='").append(height - margin);
    svg.append("' x2='").append(width - margin).append("' y2='").append(height - margin);
    svg.append("' stroke='black'/>\n");

    // Axis labels
    svg.append("<text x='").append(width / 2).append("' y='").append(height - 10);
    svg.append("' text-anchor='middle' font-size='11'>").append(escapeXml(xLabel));
    svg.append("</text>\n");
    svg.append("<text x='12' y='").append(height / 2);
    svg.append("' text-anchor='middle' font-size='11' ");
    svg.append("transform='rotate(-90,12,").append(height / 2).append(")'>");
    svg.append(escapeXml(yLabel)).append("</text>\n");

    // Grid lines (5 horizontal)
    for (int i = 0; i <= 4; i++) {
      double yVal = yMin + (yMax - yMin) * i / 4.0;
      int py = (height - margin) - (int) ((yVal - yMin) / (yMax - yMin) * plotH);
      svg.append("<line x1='").append(margin).append("' y1='").append(py);
      svg.append("' x2='").append(width - margin).append("' y2='").append(py);
      svg.append("' stroke='#e0e0e0'/>\n");
      svg.append("<text x='").append(margin - 5).append("' y='").append(py + 4);
      svg.append("' text-anchor='end' font-size='9'>");
      svg.append(String.format("%.1f", yVal)).append("</text>\n");
    }

    // X-axis tick labels
    for (int i = 0; i <= 4; i++) {
      double xVal = xMin + (xMax - xMin) * i / 4.0;
      int px = margin + (int) ((xVal - xMin) / (xMax - xMin) * plotW);
      svg.append("<text x='").append(px).append("' y='").append(height - margin + 15);
      svg.append("' text-anchor='middle' font-size='9'>");
      svg.append(String.format("%.1f", xVal)).append("</text>\n");
    }

    // Line
    StringBuilder path = new StringBuilder();
    for (int i = 0; i < n; i++) {
      int px = margin + (int) ((xVals.get(i) - xMin) / (xMax - xMin) * plotW);
      int py = (height - margin) - (int) ((yVals.get(i) - yMin) / (yMax - yMin) * plotH);
      if (i == 0) {
        path.append("M ").append(px).append(",").append(py);
      } else {
        path.append(" L ").append(px).append(",").append(py);
      }
    }
    svg.append("<path d='").append(path.toString());
    svg.append("' fill='none' stroke='#2196F3' stroke-width='2'/>\n");

    // Data point dots
    for (int i = 0; i < n; i++) {
      int px = margin + (int) ((xVals.get(i) - xMin) / (xMax - xMin) * plotW);
      int py = (height - margin) - (int) ((yVals.get(i) - yMin) / (yMax - yMin) * plotH);
      svg.append("<circle cx='").append(px).append("' cy='").append(py);
      svg.append("' r='3' fill='#2196F3'/>\n");
    }

    svg.append("</svg>");

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("visualizationType", "lineChart");
    response.addProperty("title", title);
    response.addProperty("svg", svg.toString());
    response.addProperty("mimeType", "image/svg+xml");
    response.addProperty("dataPointCount", n);
    return GSON.toJson(response);
  }
}
