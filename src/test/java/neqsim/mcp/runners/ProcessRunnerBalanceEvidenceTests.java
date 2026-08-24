package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests solver-native ProcessModel convergence and mass-closure evidence in MCP process responses. */
class ProcessRunnerBalanceEvidenceTests {

  @Test
  void testMultiAreaRunExposesSolverNativeMassClosureEvidence() {
    JsonObject response = JsonParser.parseString(ProcessRunner.run(McpAcceptanceFixtureCatalog.multiAreaInput()))
        .getAsJsonObject();

    assertEquals("success", response.get("status").getAsString(), response.toString());
    assertTrue(response.has("convergenceReport"), response.toString());

    JsonObject report = response.getAsJsonObject("convergenceReport");
    assertEquals("1.0", report.get("schemaVersion").getAsString());
    assertTrue(report.get("converged").getAsBoolean(), report.toString());

    JsonObject massClosure = report.getAsJsonObject("massClosure");
    assertTrue(massClosure.get("tolerance").getAsDouble() > 0.0, massClosure.toString());
    assertFalse(massClosure.get("relativeError").isJsonNull(), massClosure.toString());
    double relativeError = massClosure.get("relativeError").getAsDouble();
    assertTrue(Double.isFinite(relativeError), massClosure.toString());
    assertTrue(relativeError >= 0.0, massClosure.toString());
    assertTrue(relativeError <= massClosure.get("tolerance").getAsDouble(), massClosure.toString());
    assertTrue(massClosure.has("summary"));
    assertTrue(massClosure.has("worstUnits"));

    JsonObject data = response.getAsJsonObject("data");
    assertTrue(data.has("convergenceReport"), data.toString());
    assertEquals(report, data.getAsJsonObject("convergenceReport"));
  }
}
