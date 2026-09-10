package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Software-contract tests for bounded metadata-only {@link CompositionRunner} behavior.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CompositionRunnerTest {
  private static final String CUSTOM_SERVER = "phase0-composition-test";

  @AfterEach
  void removeCustomMetadata() {
    CompositionRunner.run("{\"action\":\"removeServer\",\"name\":\"" + CUSTOM_SERVER + "\"}");
    for (int index = 0; index < 33; index++) {
      CompositionRunner.run("{\"action\":\"removeServer\",\"name\":\"phase0-capacity-" + index + "\"}");
    }
  }

  @Test
  void defaultServerDiscoveryIsDeterministicAndMetadataOnly() {
    JsonObject first = run("{\"action\":\"listServers\"}");
    JsonObject second = run("{\"action\":\"listServers\"}");

    assertEquals("success", first.get("status").getAsString());
    assertEquals(5, first.get("count").getAsInt());
    assertTrue(first.get("metadataOnly").getAsBoolean());
    assertFalse(first.get("executionPerformed").getAsBoolean());
    assertEquals(first.getAsJsonArray("servers"), second.getAsJsonArray("servers"));

    List<String> names = names(first.getAsJsonArray("servers"));
    List<String> sortedNames = new ArrayList<String>(names);
    Collections.sort(sortedNames);
    assertEquals(sortedNames, names);
    assertTrue(names.contains("plant-historian"));
    assertFalse(first.toString().contains("endpoint"));
    assertFalse(first.toString().contains("credentials"));
  }

  @Test
  void fixedWorkflowCatalogAndDetailRemainOrdered() {
    JsonObject catalog = run("{\"action\":\"listWorkflows\"}");
    assertEquals("success", catalog.get("status").getAsString());
    assertEquals(4, catalog.get("count").getAsInt());
    assertEquals("digital-twin",
        catalog.getAsJsonArray("workflows").get(0).getAsJsonObject().get("id").getAsString());

    JsonObject workflow = run("{\"action\":\"getWorkflow\",\"workflowId\":\"safety-study\"}");
    assertEquals("success", workflow.get("status").getAsString());
    assertEquals("safety-study", workflow.get("id").getAsString());
    assertEquals(4, workflow.getAsJsonArray("steps").size());
    assertEquals("neqsim",
        workflow.getAsJsonArray("steps").get(0).getAsJsonObject().get("server").getAsString());
  }

  @Test
  void compositionPlanIsSequentialMetadataAndPerformsNoExecution() {
    JsonObject result =
        run("{\"action\":\"planComposition\",\"task\":\"Compare plant measurements and project cost\"}");

    assertEquals("success", result.get("status").getAsString());
    assertTrue(result.get("metadataOnly").getAsBoolean());
    assertFalse(result.get("executionPerformed").getAsBoolean());
    assertTrue(result.get("hostExecutionRequired").getAsBoolean());
    JsonArray steps = result.getAsJsonArray("suggestedSteps");
    assertEquals(5, steps.size());
    for (int index = 0; index < steps.size(); index++) {
      assertEquals(index + 1, steps.get(index).getAsJsonObject().get("order").getAsInt());
    }
  }

  @Test
  void customMetadataLifecycleDeduplicatesBoundedCollections() {
    JsonObject registered = run("{\"action\":\"registerServer\",\"name\":\"" + CUSTOM_SERVER
        + "\",\"description\":\"Synthetic test metadata\",\"domain\":\"test\","
        + "\"tools\":[\"inspect\",\"inspect\"],\"dataFormats\":[\"JSON\",\"JSON\"]}");
    assertEquals("success", registered.get("status").getAsString());
    assertTrue(registered.get("metadataOnly").getAsBoolean());
    assertFalse(registered.get("executionPerformed").getAsBoolean());

    JsonObject catalog = run("{\"action\":\"listServers\"}");
    JsonObject custom = server(catalog.getAsJsonArray("servers"), CUSTOM_SERVER);
    assertEquals(1, custom.getAsJsonArray("tools").size());
    assertEquals(1, custom.getAsJsonArray("dataFormats").size());

    JsonObject removed =
        run("{\"action\":\"removeServer\",\"name\":\"" + CUSTOM_SERVER + "\"}");
    assertEquals("success", removed.get("status").getAsString());
    assertTrue(removed.get("removed").getAsBoolean());
  }

  @Test
  void connectionAndCredentialMaterialFailsClosed() {
    JsonObject endpoint = run("{\"action\":\"registerServer\",\"name\":\"" + CUSTOM_SERVER
        + "\",\"endpoint\":\"https://example.invalid/mcp\"}");
    assertEquals("UNSUPPORTED_CONNECTION_DATA", errorCode(endpoint));

    JsonObject token = run("{\"action\":\"registerServer\",\"name\":\"" + CUSTOM_SERVER
        + "\",\"token\":\"do-not-store\"}");
    assertEquals("UNSUPPORTED_CONNECTION_DATA", errorCode(token));

    JsonObject catalog = run("{\"action\":\"listServers\"}");
    assertFalse(names(catalog.getAsJsonArray("servers")).contains(CUSTOM_SERVER));
  }

  @Test
  void builtInMetadataCannotBeReplacedOrRemoved() {
    JsonObject replace =
        run("{\"action\":\"registerServer\",\"name\":\"plant-historian\"}");
    assertEquals("PROTECTED_SERVER", errorCode(replace));

    JsonObject remove =
        run("{\"action\":\"removeServer\",\"name\":\"plant-historian\"}");
    assertEquals("PROTECTED_SERVER", errorCode(remove));

    JsonObject catalog = run("{\"action\":\"listServers\"}");
    assertTrue(names(catalog.getAsJsonArray("servers")).contains("plant-historian"));
  }

  @Test
  void malformedBlankAndOversizedRequestsFailClosed() {
    assertEquals("INVALID_INPUT", errorCode(run("{")));
    assertEquals("INVALID_INPUT", errorCode(run("{}")));
    assertEquals("INVALID_INPUT",
        errorCode(run("{\"action\":\"planComposition\",\"task\":\"   \"}")));
    assertEquals("INVALID_INPUT",
        errorCode(run("{\"action\":\"planComposition\",\"task\":\"" + repeat("x", 4097) + "\"}")));
    assertEquals("INVALID_INPUT", errorCode(run(repeat("x", 16385))));
    assertEquals("UNKNOWN_ACTION", errorCode(run("{\"action\":\"execute\"}")));
  }

  @Test
  void customRegistryHasAFixedCapacity() {
    for (int index = 0; index < 32; index++) {
      JsonObject response = run("{\"action\":\"registerServer\",\"name\":\"phase0-capacity-"
          + index + "\"}");
      assertEquals("success", response.get("status").getAsString());
    }
    JsonObject overflow =
        run("{\"action\":\"registerServer\",\"name\":\"phase0-capacity-32\"}");
    assertEquals("REGISTRY_LIMIT", errorCode(overflow));
  }

  private static JsonObject run(String json) {
    return JsonParser.parseString(CompositionRunner.run(json)).getAsJsonObject();
  }

  private static String errorCode(JsonObject response) {
    assertEquals("error", response.get("status").getAsString());
    return response.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString();
  }

  private static List<String> names(JsonArray servers) {
    List<String> names = new ArrayList<String>();
    for (int index = 0; index < servers.size(); index++) {
      names.add(servers.get(index).getAsJsonObject().get("name").getAsString());
    }
    return names;
  }

  private static JsonObject server(JsonArray servers, String name) {
    for (int index = 0; index < servers.size(); index++) {
      JsonObject server = servers.get(index).getAsJsonObject();
      if (name.equals(server.get("name").getAsString())) {
        return server;
      }
    }
    throw new AssertionError("Missing server metadata: " + name);
  }

  private static String repeat(String value, int count) {
    StringBuilder builder = new StringBuilder(count);
    for (int index = 0; index < count; index++) {
      builder.append(value);
    }
    return builder.toString();
  }
}
