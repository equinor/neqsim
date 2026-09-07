package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Software-contract tests for {@link VisualizationRunner}.
 *
 * @author Even Solbraa
 * @version 1.1
 */
class VisualizationRunnerTest {

  @Test
  void documentedFlowsheetAliasReturnsCanonicalMermaidContract() {
    JsonObject result = run("{\"type\":\"flowsheetDiagram\",\"title\":\"Separation\","
        + "\"equipment\":[{\"name\":\"Feed\",\"type\":\"Stream\"}," + "{\"name\":\"HP Sep\",\"type\":\"Separator\"}]}");

    assertSuccess(result, "flowsheet", "text/x-mermaid", "mermaid");
    assertTrue(result.get("mermaid").getAsString().contains("Feed --> HP_Sep"));
  }

  @Test
  void tableAliasesReturnCanonicalHtmlContractAndHonorCaption() {
    for (String type : new String[] { "propertyTable", "styledTable", "table" }) {
      JsonObject result = run("{\"type\":\"" + type + "\",\"caption\":\"Stream Summary\","
          + "\"headers\":[\"Property\",\"Value\"],\"rows\":[[\"Pressure\",\"50 bara\"]]}");

      assertSuccess(result, "propertyTable", "text/html", "html");
      assertTrue(result.get("html").getAsString().contains("Stream Summary"));
    }
  }

  @Test
  void nonNumericalChartTypesReturnStableSvgContracts() {
    JsonObject bar = run("{\"type\":\"barChart\",\"labels\":[\"A\",\"B\"],\"values\":[1,2]}");
    JsonObject pie = run("{\"type\":\"pieChart\",\"categories\":[\"A\",\"B\"],\"values\":[1,2]}");
    JsonObject line = run("{\"type\":\"lineChart\",\"xValues\":[0,1],\"yValues\":[1,2]}");

    assertSuccess(bar, "barChart", "image/svg+xml", "svg");
    assertSuccess(pie, "pieChart", "image/svg+xml", "svg");
    assertSuccess(line, "lineChart", "image/svg+xml", "svg");
  }

  @Test
  void chartAndTableTextIsEscaped() {
    JsonObject chart = run(
        "{\"type\":\"barChart\",\"title\":\"<unsafe>&\"," + "\"labels\":[\"<label>\"],\"values\":[1]}");
    JsonObject table = run(
        "{\"type\":\"propertyTable\",\"title\":\"<unsafe>&\"," + "\"headers\":[\"<header>\"],\"rows\":[[\"<cell>\"]]}");

    String svg = chart.get("svg").getAsString();
    String html = table.get("html").getAsString();
    assertTrue(svg.contains("&lt;unsafe&gt;&amp;"));
    assertTrue(svg.contains("&lt;label&gt;"));
    assertFalse(svg.contains("<unsafe>"));
    assertTrue(html.contains("&lt;unsafe&gt;&amp;"));
    assertTrue(html.contains("&lt;header&gt;"));
    assertTrue(html.contains("&lt;cell&gt;"));
    assertFalse(html.contains("<unsafe>"));
  }

  @Test
  void nullMalformedMissingAndUnknownTypesFailClosed() {
    assertError(VisualizationRunner.run(null));
    assertError(VisualizationRunner.run("{"));
    assertError(VisualizationRunner.run("{}"));
    assertError(VisualizationRunner.run("{\"type\":\"unknown\"}"));
  }

  @Test
  void emptyChartArraysFailClosed() {
    assertError(VisualizationRunner.run("{\"type\":\"barChart\",\"labels\":[],\"values\":[]}"));
    assertError(VisualizationRunner.run("{\"type\":\"pieChart\",\"categories\":[],\"values\":[]}"));
    assertError(VisualizationRunner.run("{\"type\":\"lineChart\",\"xValues\":[],\"yValues\":[]}"));
  }

  @Test
  void mismatchedChartArraysFailClosedInsteadOfTruncating() {
    assertError(VisualizationRunner.run("{\"type\":\"barChart\",\"labels\":[\"A\",\"B\"],\"values\":[1]}"));
    assertError(VisualizationRunner.run("{\"type\":\"pieChart\",\"categories\":[\"A\"],\"values\":[1,2]}"));
    assertError(VisualizationRunner.run("{\"type\":\"lineChart\",\"xValues\":[0,1],\"yValues\":[1]}"));
  }

  private static JsonObject run(String json) {
    return JsonParser.parseString(VisualizationRunner.run(json)).getAsJsonObject();
  }

  private static void assertSuccess(JsonObject result, String type, String mimeType, String contentField) {
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertEquals(type, result.get("visualizationType").getAsString());
    assertEquals(mimeType, result.get("mimeType").getAsString());
    assertTrue(result.has(contentField));
  }

  private static void assertError(String result) {
    JsonObject error = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", error.get("status").getAsString(), result);
    assertTrue(error.has("message"));
  }
}
