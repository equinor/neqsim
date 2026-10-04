package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link GeneralCapabilityRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class GeneralCapabilityRunnerTest {

  /** Catalog pages must be bounded, stable and explicit about their evidence boundary. */
  @Test
  void testCoveragePaginationAndIsolation() {
    JsonObject first = JsonParser
        .parseString(GeneralCapabilityRunner.run("{\"action\":\"coverage\",\"view\":\"apis\",\"limit\":2}"))
        .getAsJsonObject();
    assertEquals("success", first.get("status").getAsString());
    assertEquals(2, first.getAsJsonArray("entries").size());
    assertTrue(first.get("total").getAsInt() > 100);
    assertEquals(2, first.get("nextOffset").getAsInt());
    assertEquals(false, first.get("complete").getAsBoolean());
    String digest = first.get("catalogDigest").getAsString();
    JsonObject next = JsonParser.parseString(GeneralCapabilityRunner.run(
        "{\"action\":\"coverage\",\"view\":\"apis\",\"offset\":2,\"limit\":2,\"catalogDigest\":\"" + digest + "\"}"))
        .getAsJsonObject();
    assertEquals(digest, next.get("catalogDigest").getAsString());
    assertTrue(!first.getAsJsonArray("entries").get(0).equals(next.getAsJsonArray("entries").get(0)));
    EngineeringCoverageCatalog.summary().addProperty("publicTypes", -1);
    assertTrue(EngineeringCoverageCatalog.summary().get("publicTypes").getAsInt() > 100);
  }

  /** Source test references must not be presented as executed tests or engineering qualification. */
  @Test
  void testCoverageDiscoveryLeadsToKnownCalculation() {
    JsonObject page = JsonParser
        .parseString(GeneralCapabilityRunner.run("{\"action\":\"coverage\",\"query\":\"sulfur-vapour-pressure\"}"))
        .getAsJsonObject();
    JsonObject row = page.getAsJsonArray("entries").get(0).getAsJsonObject();
    assertEquals("runCapability", row.get("tool").getAsString());
    assertEquals("not_recorded", row.getAsJsonObject("evidence").get("execution").getAsString());
    assertEquals("not_assessed", row.getAsJsonObject("evidence").get("engineeringQualification").getAsString());
    String className = row.getAsJsonArray("apis").get(0).getAsString();
    JsonObject value = JsonParser.parseString(GeneralCapabilityRunner.run("{\"action\":\"invoke\",\"className\":\""
        + className
        + "\",\"methodName\":\"calculateVapourPressureBar\",\"parameterTypes\":[\"double\"],\"arguments\":[717.76]}"))
        .getAsJsonObject();
    assertEquals(1.01325, value.get("result").getAsDouble(), 1.0e-10);
  }

  /** Invalid pages and stale catalog identities fail explicitly, rather than silently truncating numbers. */
  @Test
  void testCoverageRejectsInvalidRequests() {
    for (String fields : new String[] {"\"offset\":-1", "\"limit\":51", "\"limit\":0", "\"offset\":1.5",
        "\"offset\":2147483648", "\"view\":\"unknown\"", "\"catalogDigest\":\"stale\"", "\"query\":null"}) {
      JsonObject result = JsonParser
          .parseString(GeneralCapabilityRunner.run("{\"action\":\"coverage\"," + fields + "}")).getAsJsonObject();
      assertEquals("error", result.get("status").getAsString(), fields);
    }
    JsonObject empty = JsonParser
        .parseString(GeneralCapabilityRunner.run("{\"action\":\"coverage\",\"query\":\"no-such-capability\"}"))
        .getAsJsonObject();
    assertEquals(0, empty.get("total").getAsInt());
    assertEquals(false, empty.get("hasMore").getAsBoolean());
  }

  @Test
  void testSearchFindsStaticAndStatefulSulfurCapabilities() {
    JsonObject result = JsonParser.parseString(GeneralCapabilityRunner.search("sulfur vapour pressure", 25))
        .getAsJsonObject();

    assertEquals("success", result.get("status").getAsString());
    JsonArray matches = result.getAsJsonArray("matches");
    assertTrue(matches.toString().contains("SulfurThermodynamics"));
    assertTrue(matches.toString().contains("calculateVapourPressureBar"));
    assertTrue(matches.toString().contains("static-json"));

    JsonObject solubility = JsonParser.parseString(GeneralCapabilityRunner.search("sulfur solubility", 50))
        .getAsJsonObject();
    assertTrue(solubility.getAsJsonArray("matches").toString().contains("SulfurDepositionAnalyser"),
        solubility.toString());
    assertTrue(solubility.getAsJsonArray("matches").toString().contains("process-json"));
  }

  @Test
  void testInvokeRunsDiscoveredSulfurCalculation() {
    String request = "{\"action\":\"invoke\"," + "\"className\":\"neqsim.thermo.util.sulfur.SulfurThermodynamics\","
        + "\"methodName\":\"calculateVapourPressureBar\"," + "\"parameterTypes\":[\"double\"],\"arguments\":[717.76]}";

    JsonObject result = JsonParser.parseString(GeneralCapabilityRunner.run(request)).getAsJsonObject();

    assertEquals("success", result.get("status").getAsString());
    assertEquals("static-json", result.get("executionMode").getAsString());
    assertEquals(1.01325, result.get("result").getAsDouble(), 1.0e-10);
  }

  @Test
  void testInvokeRejectsInstanceMethodAndExternalClass() {
    String instanceRequest = "{\"action\":\"invoke\","
        + "\"className\":\"neqsim.process.equipment.reactor.SulfurDepositionAnalyser\","
        + "\"methodName\":\"getSulfurSolubilityMgSm3\",\"arguments\":[]}";
    JsonObject instanceResult = JsonParser.parseString(GeneralCapabilityRunner.run(instanceRequest)).getAsJsonObject();
    assertEquals("METHOD_NOT_EXECUTABLE", instanceResult.get("code").getAsString());
    assertTrue(instanceResult.get("remediation").getAsString().contains("runProcess"));

    String externalRequest = "{\"action\":\"invoke\",\"className\":\"java.lang.Runtime\","
        + "\"methodName\":\"getRuntime\",\"arguments\":[]}";
    JsonObject externalResult = JsonParser.parseString(GeneralCapabilityRunner.run(externalRequest)).getAsJsonObject();
    assertEquals("CLASS_NOT_ALLOWED", externalResult.get("code").getAsString());
  }

  @Test
  void testInvokeRejectsMcpRunnerAndRawGenericContainerSignatures() {
    String runnerRequest = "{\"action\":\"invoke\"," + "\"className\":\"neqsim.mcp.runners.ProcessRunner\","
        + "\"methodName\":\"validateAndRun\",\"arguments\":[\"{}\"]}";
    JsonObject runnerResult = JsonParser.parseString(GeneralCapabilityRunner.run(runnerRequest)).getAsJsonObject();
    assertEquals("METHOD_NOT_EXECUTABLE", runnerResult.get("code").getAsString());

    String genericRequest = "{\"action\":\"invoke\","
        + "\"className\":\"neqsim.process.fielddevelopment.economics.ProductionProfileGenerator\","
        + "\"methodName\":\"getProfileSummary\",\"arguments\":[{}]}";
    JsonObject genericResult = JsonParser.parseString(GeneralCapabilityRunner.run(genericRequest)).getAsJsonObject();
    assertEquals("METHOD_NOT_EXECUTABLE", genericResult.get("code").getAsString());
  }

  @Test
  @Tag("slow")
  void testSearchClampsLimitAndReturnsDeterministicRoutingMetadata() {
    JsonObject first = JsonParser.parseString(GeneralCapabilityRunner.search("sulfur", 1000)).getAsJsonObject();
    JsonObject second = JsonParser.parseString(GeneralCapabilityRunner.search("sulfur", 1000)).getAsJsonObject();

    assertTrue(first.get("returnedCount").getAsInt() <= 100);
    assertEquals(first.getAsJsonArray("matches"), second.getAsJsonArray("matches"));
    for (JsonElement match : first.getAsJsonArray("matches")) {
      JsonObject capability = match.getAsJsonObject();
      assertTrue(capability.has("executionMode"));
      assertTrue(capability.get("sourcePath").getAsString().startsWith("src/main/java/neqsim/"));
    }
  }

  @Test
  void testRunRejectsUnknownActionAndMalformedInput() {
    JsonObject unknown = JsonParser.parseString(GeneralCapabilityRunner.run("{\"action\":\"install\"}"))
        .getAsJsonObject();
    assertEquals("UNKNOWN_ACTION", unknown.get("code").getAsString());

    JsonObject malformed = JsonParser.parseString(GeneralCapabilityRunner.run("{")).getAsJsonObject();
    assertEquals("INPUT_ERROR", malformed.get("code").getAsString());
  }

  @Test
  void testRunRejectsOversizedRequestBeforeReflection() {
    StringBuilder query = new StringBuilder(70000);
    for (int i = 0; i < 70000; i++) {
      query.append('x');
    }
    String request = "{\"action\":\"search\",\"query\":\"" + query + "\"}";

    JsonObject result = JsonParser.parseString(GeneralCapabilityRunner.run(request)).getAsJsonObject();

    assertEquals("INPUT_TOO_LARGE", result.get("code").getAsString());
  }
}
