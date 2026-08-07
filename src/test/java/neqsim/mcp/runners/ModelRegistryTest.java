package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Tests for {@link ModelRegistry}.
 *
 * <p>
 * The registry exists so a chat session can anchor on one flowsheet instead of resending it on every call, so these
 * tests cover handle issue and reuse, revisioning, tenant isolation, and the resolution path that lets any
 * process-taking tool accept a handle.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ModelRegistryTest {

  /** A minimal valid single-area process definition. */
  private static final String PROCESS_JSON = "{\"fluid\": {\"components\": {\"methane\": 0.9, \"ethane\": 0.1},"
      + " \"model\": \"SRK\", \"temperature_C\": 25.0, \"pressure_bara\": 50.0}," + " \"process\": ["
      + "{\"type\": \"stream\", \"name\": \"feed\", \"flowRate\": {\"value\": 1000.0, \"unit\": \"kg/hr\"}},"
      + "{\"type\": \"separator\", \"name\": \"sep\", \"inlet\": \"feed\"}]}";

  /**
   * Clears registry and identity state before each test.
   */
  @BeforeEach
  void reset() {
    ModelRegistry.resetForTests();
    McpRequestContext.clear();
  }

  /**
   * Clears identity state after each test so bound principals do not leak.
   */
  @AfterEach
  void clearIdentity() {
    ModelRegistry.resetForTests();
    McpRequestContext.clear();
  }

  /**
   * Registers the standard definition and returns the issued handle.
   *
   * @return the model id
   */
  private String registerStandardModel() {
    String response = ModelRegistry.run("{\"action\": \"register\", \"name\": \"test-sep\", \"processJson\": "
        + com.google.gson.JsonParser.parseString(PROCESS_JSON) + "}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString(), "Registration must succeed: " + response);
    return root.get("modelId").getAsString();
  }

  /**
   * A registered model returns a handle that resolves back to the stored definition.
   */
  @Test
  @DisplayName("Register issues a handle that resolves to the stored definition")
  void testRegisterAndResolve() {
    String modelId = registerStandardModel();
    assertTrue(modelId.startsWith(ModelRegistry.MODEL_ID_PREFIX), "Handle must be recognisable: " + modelId);
    assertTrue(ModelRegistry.isModelHandle(modelId));

    String resolved = ModelRegistry.resolve(modelId);
    JsonObject definition = JsonParser.parseString(resolved).getAsJsonObject();
    assertTrue(definition.has("process"), "Resolved definition must contain the process array");
  }

  /**
   * Inline JSON must pass through untouched so existing callers keep working.
   */
  @Test
  @DisplayName("Inline process JSON passes through resolution unchanged")
  void testInlineJsonPassesThrough() {
    assertEquals(PROCESS_JSON, ModelRegistry.resolve(PROCESS_JSON));
    assertTrue(!ModelRegistry.isModelHandle(PROCESS_JSON));
  }

  /**
   * Identical content must map to the same handle so repeated registration is idempotent.
   */
  @Test
  @DisplayName("Identical definitions map to the same handle")
  void testRegistrationIsContentAddressed() {
    String first = registerStandardModel();
    String second = registerStandardModel();
    assertEquals(first, second, "Same content must yield the same handle");
  }

  /**
   * Revising a model keeps the handle stable and increments the revision, so results can cite one.
   */
  @Test
  @DisplayName("Revise increments the revision under the same handle")
  void testReviseIncrementsRevision() {
    String modelId = registerStandardModel();
    String revised = PROCESS_JSON.replace("\"pressure_bara\": 50.0", "\"pressure_bara\": 70.0");

    String response = ModelRegistry.run("{\"action\": \"revise\", \"modelId\": \"" + modelId + "\", \"processJson\": "
        + JsonParser.parseString(revised) + "}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString(), response);
    assertEquals(2, root.get("revision").getAsInt(), "Revision must increment");
    assertEquals(modelId, root.get("modelId").getAsString(), "Handle must stay stable across revisions");

    assertTrue(ModelRegistry.resolve(modelId).contains("70.0"), "Resolution must return the revised definition");
  }

  /**
   * Inspect must describe structure without running the model.
   */
  @Test
  @DisplayName("Inspect reports equipment without running the model")
  void testInspectReportsStructure() {
    String modelId = registerStandardModel();
    String response = ModelRegistry.run("{\"action\": \"inspect\", \"modelId\": \"" + modelId + "\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString(), response);
    assertEquals(2, root.get("equipmentCount").getAsInt(), "Both units must be reported");
    assertTrue(response.contains("separator"), "Equipment types must be listed");
  }

  /**
   * Listing must only show models belonging to the caller's tenant.
   */
  @Test
  @DisplayName("Models are isolated by tenant")
  void testTenantIsolation() {
    McpRequestContext
        .set(McpRequestContext.Principal.ofClaims("alice", "tenant-a", Collections.<String>emptySet(), null));
    String modelId = registerStandardModel();

    McpRequestContext
        .set(McpRequestContext.Principal.ofClaims("bob", "tenant-b", Collections.<String>emptySet(), null));
    String listResponse = ModelRegistry.run("{\"action\": \"list\"}");
    JsonObject list = JsonParser.parseString(listResponse).getAsJsonObject();
    assertEquals(0, list.get("count").getAsInt(), "A different tenant must not see the model");

    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        ModelRegistry.resolve(modelId);
      }
    }, "Resolving another tenant's handle must fail");
  }

  /**
   * An unknown handle must produce an actionable error rather than a silent pass-through.
   */
  @Test
  @DisplayName("Unknown handles fail with a remediation hint")
  void testUnknownHandleFails() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        new org.junit.jupiter.api.function.Executable() {
          @Override
          public void execute() {
            ModelRegistry.resolve("model_deadbeefdeadbeef");
          }
        });
    assertTrue(error.getMessage().contains("manageModel"), "Error must point at the registration tool");
  }

  /**
   * A definition without a process or areas block must be rejected at registration, not at run time.
   */
  @Test
  @DisplayName("Invalid definitions are rejected at registration")
  void testInvalidDefinitionRejected() {
    String response = ModelRegistry.run("{\"action\": \"register\", \"processJson\": {\"fluid\": {}}}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("error", root.get("status").getAsString(), response);
    assertEquals("INVALID_DEFINITION", root.get("code").getAsString());
  }

  /**
   * Deleting a model makes its handle unresolvable.
   */
  @Test
  @DisplayName("Delete removes the handle")
  void testDelete() {
    final String modelId = registerStandardModel();
    String response = ModelRegistry.run("{\"action\": \"delete\", \"modelId\": \"" + modelId + "\"}");
    assertEquals("success", JsonParser.parseString(response).getAsJsonObject().get("status").getAsString(), response);

    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        ModelRegistry.resolve(modelId);
      }
    });
  }

  /**
   * The registry must accept a process definition sent as a nested JSON object, which is the form an LLM produces more
   * reliably than a JSON string escaped inside a string.
   */
  @Test
  @DisplayName("Register accepts a nested JSON object as well as a JSON string")
  void testAcceptsStructuredDefinition() {
    String asString = ModelRegistry
        .run("{\"action\": \"register\", \"processJson\": " + JsonParser.parseString(PROCESS_JSON) + "}");
    String asEscapedString = ModelRegistry.run("{\"action\": \"register\", \"processJson\": "
        + com.google.gson.JsonParser.parseString("\"" + PROCESS_JSON.replace("\"", "\\\"") + "\"") + "}");

    JsonObject structured = JsonParser.parseString(asString).getAsJsonObject();
    JsonObject escaped = JsonParser.parseString(asEscapedString).getAsJsonObject();
    assertEquals("success", structured.get("status").getAsString(), asString);
    assertEquals("success", escaped.get("status").getAsString(), asEscapedString);
    assertNotNull(structured.get("modelId"));
    assertNotNull(escaped.get("modelId"));
  }

  /**
   * Different definitions must not collide on one handle.
   */
  @Test
  @DisplayName("Different definitions get different handles")
  void testDistinctDefinitionsGetDistinctHandles() {
    String first = registerStandardModel();
    String other = PROCESS_JSON.replace("\"pressure_bara\": 50.0", "\"pressure_bara\": 90.0");
    String response = ModelRegistry
        .run("{\"action\": \"register\", \"processJson\": " + JsonParser.parseString(other) + "}");
    String second = JsonParser.parseString(response).getAsJsonObject().get("modelId").getAsString();
    assertNotEquals(first, second);
  }

  /**
   * Reads through a handle must reuse a single solve. Rebuilding and re-solving on every read costs seconds to minutes
   * on a plant-sized model, which is what makes an interactive session unusable.
   */
  @Test
  @DisplayName("Reads through a handle reuse one solve")
  void testReadsReuseOneSolve() {
    String modelId = registerStandardModel();

    ProcessSystem first = ModelRegistry.solvedProcess(modelId);
    ProcessSystem second = ModelRegistry.solvedProcess(modelId);
    assertSame(first, second, "The same solved flowsheet must be reused");

    AutomationRunner.listUnits(modelId);
    AutomationRunner.getAdjustableParameters(modelId);
    assertTrue(ModelRegistry.solvedHits(modelId) >= 3,
        "Automation reads must be served from the cached solve, hits=" + ModelRegistry.solvedHits(modelId));
  }

  /**
   * A revision must invalidate the cached solve, otherwise reads would answer from a stale flowsheet.
   */
  @Test
  @DisplayName("Revising a model invalidates the cached solve")
  void testReviseInvalidatesSolve() {
    String modelId = registerStandardModel();
    ProcessSystem before = ModelRegistry.solvedProcess(modelId);

    String revised = PROCESS_JSON.replace("\"pressure_bara\": 50.0", "\"pressure_bara\": 70.0");
    ModelRegistry.run("{\"action\": \"revise\", \"modelId\": \"" + modelId + "\", \"processJson\": "
        + JsonParser.parseString(revised) + "}");

    assertNotSame(before, ModelRegistry.solvedProcess(modelId), "A revised model must be solved again");
  }

  /**
   * Deleting a model must release its cached solve.
   */
  @Test
  @DisplayName("Deleting a model drops the cached solve")
  void testDeleteDropsSolve() {
    String modelId = registerStandardModel();
    ModelRegistry.solvedProcess(modelId);
    ModelRegistry.run("{\"action\": \"delete\", \"modelId\": \"" + modelId + "\"}");
    assertEquals(0, ModelRegistry.solvedHits(modelId), "Cached solve must be released on delete");
  }

  /**
   * A handle must drive the same simulation and automation results as inline JSON, which is the whole point of the
   * indirection.
   */
  @Test
  @DisplayName("A handle produces the same results as inline JSON through the runner path")
  void testHandleDrivesRunnersLikeInlineJson() {
    String modelId = registerStandardModel();

    String fromHandle = ProcessRunner.validateAndRun(ModelRegistry.resolve(modelId));
    String fromInline = ProcessRunner.validateAndRun(PROCESS_JSON);
    assertEquals("success", JsonParser.parseString(fromHandle).getAsJsonObject().get("status").getAsString(),
        fromHandle);
    assertEquals("success", JsonParser.parseString(fromInline).getAsJsonObject().get("status").getAsString(),
        fromInline);

    String units = AutomationRunner.listUnits(ModelRegistry.resolve(modelId));
    assertTrue(units.contains("sep"), "Automation must see the units of the registered model: " + units);
  }

  /**
   * Resolving a handle must record usage so a server can report which models are active.
   */
  @Test
  @DisplayName("Resolution increments the use count")
  void testResolutionTracksUsage() {
    String modelId = registerStandardModel();
    ModelRegistry.resolve(modelId);
    ModelRegistry.resolve(modelId);

    String response = ModelRegistry.run("{\"action\": \"list\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    JsonObject model = root.getAsJsonArray("models").get(0).getAsJsonObject();
    assertEquals(2, model.get("useCount").getAsInt());
  }

  /**
   * Pins the response fields documented in the MCP API reference and README, so the published examples cannot drift
   * from the actual payload.
   */
  @Test
  @DisplayName("Register response contains every documented field")
  void testDocumentedResponseFieldsExist() {
    String response = ModelRegistry.run("{\"action\": \"register\", \"name\": \"HP separation train\","
        + " \"version\": \"1.0.0\", \"processJson\": " + JsonParser.parseString(PROCESS_JSON) + "}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();

    String[] documented = { "status", "modelId", "name", "version", "revision", "tenant", "useCount", "usage" };
    for (String field : documented) {
      assertTrue(root.has(field), "Documented field '" + field + "' missing from register response: " + response);
    }
    assertEquals("HP separation train", root.get("name").getAsString());
    assertEquals("1.0.0", root.get("version").getAsString());
    assertEquals(1, root.get("revision").getAsInt());

    JsonObject inspected = JsonParser
        .parseString(
            ModelRegistry.run("{\"action\": \"inspect\", \"modelId\": \"" + root.get("modelId").getAsString() + "\"}"))
        .getAsJsonObject();
    for (String field : new String[] { "equipment", "areas", "equipmentCount" }) {
      assertTrue(inspected.has(field), "Documented inspect field '" + field + "' missing: " + inspected);
    }
  }
}
