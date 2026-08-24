package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests canonical process balance evidence in MCP process responses. */
class ProcessRunnerBalanceEvidenceTests {

  @Test
  void testSingleAreaRunExposesCanonicalUnitMassBalanceEvidence() {
    JsonObject response = JsonParser.parseString(ProcessRunner.run(McpAcceptanceFixtureCatalog.smallTrainInput()))
        .getAsJsonObject();

    assertEquals("success", response.get("status").getAsString(), response.toString());
    JsonObject report = response.getAsJsonObject("report");
    assertTrue(report.has("massBalanceEvidence"), report.toString());

    JsonObject evidence = report.getAsJsonObject("massBalanceEvidence");
    assertEquals("PER_UNIT_PROCESS_SYSTEM_MASS_BALANCE", evidence.get("scope").getAsString());
    assertEquals("kg/sec", evidence.get("flowUnit").getAsString());
    assertTrue(evidence.get("configuredPercentErrorThreshold").getAsDouble() > 0.0, evidence.toString());
    assertTrue(evidence.get("minimumFlowForErrorCheckKgPerSec").getAsDouble() >= 0.0, evidence.toString());
    assertTrue(evidence.get("checkedUnitCount").getAsInt() > 0, evidence.toString());
    assertTrue(evidence.get("evaluatedUnitCount").getAsInt() > 0, evidence.toString());
    assertEquals("CANONICAL_PROCESS_SYSTEM_UNIT_EVIDENCE_NOT_FACILITY_CLOSURE",
        evidence.get("qualification").getAsString());
    assertTrue(evidence.get("boundary").getAsString().contains("not establish complete facility"), evidence.toString());

    boolean finiteEvaluatedUnitFound = false;
    for (Map.Entry<String, JsonElement> entry : evidence.getAsJsonObject("units").entrySet()) {
      JsonObject unit = entry.getValue().getAsJsonObject();
      if (unit.get("evaluated").getAsBoolean()) {
        assertTrue(Double.isFinite(unit.get("absoluteError").getAsDouble()), unit.toString());
        assertTrue(Double.isFinite(unit.get("percentError").getAsDouble()), unit.toString());
        assertTrue(unit.get("percentError").getAsDouble() >= 0.0, unit.toString());
        finiteEvaluatedUnitFound = true;
      }
    }
    assertTrue(finiteEvaluatedUnitFound, evidence.toString());

    JsonObject data = response.getAsJsonObject("data");
    assertTrue(data.getAsJsonObject("report").has("massBalanceEvidence"), data.toString());
    assertEquals(evidence, data.getAsJsonObject("report").getAsJsonObject("massBalanceEvidence"));
  }

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
