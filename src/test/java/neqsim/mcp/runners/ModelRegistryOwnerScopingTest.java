package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests that registered models are reachable only by the principal that registered them.
 *
 * <p>
 * The registry used to scope entries by tenant alone. One directory tenant covers every user of an organisation, so
 * that left all of them in a single namespace: any caller could list every handle, read another engineer's flowsheet,
 * delete it, or revise it so its owner's next run silently computed on someone else's edits and still returned a
 * normal-looking result. These tests pin the ownership check on every access path.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ModelRegistryOwnerScopingTest {

  /** Tenant shared by every principal in these tests, as a single directory tenant is in production. */
  private static final String SHARED_TENANT = "equinor-tid";

  /** Name of the model owned by Alice; used to detect leakage of another owner's metadata. */
  private static final String OWNED_MODEL_NAME = "wisting-flowsheet";

  /**
   * Clears registry and identity state before each test.
   */
  @BeforeEach
  void reset() {
    ModelRegistry.resetForTests();
    McpRequestContext.clear();
  }

  /**
   * Clears registry and identity state after each test so bound principals do not leak.
   */
  @AfterEach
  void clearIdentity() {
    ModelRegistry.resetForTests();
    McpRequestContext.clear();
  }

  /**
   * Binds an authenticated principal in the shared tenant.
   *
   * @param subject the subject identifier
   */
  private void authenticateAs(String subject) {
    McpRequestContext.set(
        McpRequestContext.Principal.ofClaims(subject, SHARED_TENANT, Collections.<String>emptySet(), "test-issuer"));
  }

  /**
   * Builds a minimal valid single-area definition carrying a recognisable pressure.
   *
   * @param pressureBara the feed pressure used as a content marker
   * @return the process definition JSON
   */
  private static String processJson(double pressureBara) {
    return "{\"fluid\": {\"components\": {\"methane\": 0.9, \"ethane\": 0.1}, \"model\": \"SRK\","
        + " \"temperature_C\": 25.0, \"pressure_bara\": " + pressureBara + "}, \"process\": ["
        + "{\"type\": \"stream\", \"name\": \"feed\", \"flowRate\": {\"value\": 1000.0, \"unit\": \"kg/hr\"}},"
        + "{\"type\": \"separator\", \"name\": \"sep\", \"inlet\": \"feed\"}]}";
  }

  /**
   * Registers a definition for the currently bound principal.
   *
   * @param name the model name
   * @param definition the process definition JSON
   * @return the raw registration response
   */
  private static String registerResponse(String name, String definition) {
    return ModelRegistry.run("{\"action\": \"register\", \"name\": \"" + name + "\", \"processJson\": "
        + JsonParser.parseString(definition) + "}");
  }

  /**
   * Registers a definition for the currently bound principal and returns its handle.
   *
   * @param name the model name
   * @param definition the process definition JSON
   * @return the issued model id
   */
  private static String register(String name, String definition) {
    String response = registerResponse(name, definition);
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString(), "Registration must succeed: " + response);
    return root.get("modelId").getAsString();
  }

  /**
   * Runs a single-handle action for the currently bound principal.
   *
   * @param action the registry action
   * @param modelId the model handle
   * @return the raw response
   */
  private static String act(String action, String modelId) {
    return ModelRegistry.run("{\"action\": \"" + action + "\", \"modelId\": \"" + modelId + "\"}");
  }

  /**
   * Asserts that a response refused the request as an unknown handle without disclosing the real owner or content.
   *
   * @param action the attempted action, used in assertion messages
   * @param response the raw response
   */
  private static void assertRefusedAsUnknown(String action, String response) {
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("error", root.get("status").getAsString(), action + " must be refused: " + response);
    assertEquals("UNKNOWN_MODEL", root.get("code").getAsString(),
        action + " must look like an unknown handle, not a forbidden one: " + response);
    assertFalse(response.contains("alice"), action + " must not disclose the owner: " + response);
    assertFalse(response.contains(OWNED_MODEL_NAME), action + " must not disclose the model name: " + response);
    assertFalse(response.contains("separator"), action + " must not disclose the definition: " + response);
  }

  /**
   * Reading another principal's model must fail even though both callers share one directory tenant.
   */
  @Test
  @DisplayName("get is refused for a model owned by another principal in the same tenant")
  void testGetIsRefusedForAnotherOwner() {
    authenticateAs("alice");
    String modelId = register(OWNED_MODEL_NAME, processJson(50.0));

    authenticateAs("bob");
    assertRefusedAsUnknown("get", act("get", modelId));
  }

  /**
   * Structure disclosure is disclosure: inspect must be refused as well.
   */
  @Test
  @DisplayName("inspect is refused for a model owned by another principal")
  void testInspectIsRefusedForAnotherOwner() {
    authenticateAs("alice");
    String modelId = register(OWNED_MODEL_NAME, processJson(50.0));

    authenticateAs("bob");
    assertRefusedAsUnknown("inspect", act("inspect", modelId));
  }

  /**
   * The worst case: a foreign revision would make the owner's next run answer from someone else's edits, and the result
   * would look entirely normal. The stored definition must be untouched.
   */
  @Test
  @DisplayName("revise is refused for another principal and leaves the owner's definition intact")
  void testReviseIsRefusedForAnotherOwner() {
    authenticateAs("alice");
    String modelId = register(OWNED_MODEL_NAME, processJson(50.0));

    authenticateAs("bob");
    String tampered = ModelRegistry.run("{\"action\": \"revise\", \"modelId\": \"" + modelId + "\", \"processJson\": "
        + JsonParser.parseString(processJson(90.0)) + "}");
    assertRefusedAsUnknown("revise", tampered);

    authenticateAs("alice");
    String stored = ModelRegistry.resolve(modelId);
    assertTrue(stored.contains("50.0"), "The owner's definition must be unchanged: " + stored);
    assertFalse(stored.contains("90.0"), "Another principal's edit must not reach the owner's model: " + stored);
    assertEquals(1, JsonParser.parseString(act("get", modelId)).getAsJsonObject().get("revision").getAsInt(),
        "A refused revision must not increment the revision");
  }

  /**
   * Deletion by a non-owner must be refused and must not remove the model.
   */
  @Test
  @DisplayName("delete is refused for another principal and leaves the model registered")
  void testDeleteIsRefusedForAnotherOwner() {
    authenticateAs("alice");
    String modelId = register(OWNED_MODEL_NAME, processJson(50.0));

    authenticateAs("bob");
    assertRefusedAsUnknown("delete", act("delete", modelId));

    authenticateAs("alice");
    assertTrue(ModelRegistry.resolve(modelId).contains("50.0"), "The owner's model must survive a refused delete");
  }

  /**
   * Listing must not become an inventory of everyone's models.
   */
  @Test
  @DisplayName("list returns only the caller's own models")
  void testListIsScopedToTheOwner() {
    authenticateAs("alice");
    register(OWNED_MODEL_NAME, processJson(50.0));

    authenticateAs("bob");
    register("bob-flowsheet", processJson(70.0));
    String response = ModelRegistry.run("{\"action\": \"list\"}");
    JsonObject root = JsonParser.parseString(response).getAsJsonObject();
    JsonArray models = root.getAsJsonArray("models");

    assertEquals(1, root.get("count").getAsInt(), "Only the caller's own model must be listed: " + response);
    assertEquals(1, models.size(), response);
    assertEquals("bob", models.get(0).getAsJsonObject().get("owner").getAsString(), response);
    assertFalse(response.contains(OWNED_MODEL_NAME), "Listing must not expose another owner's model: " + response);

    authenticateAs("alice");
    JsonObject own = JsonParser.parseString(ModelRegistry.run("{\"action\": \"list\"}")).getAsJsonObject();
    assertEquals(1, own.get("count").getAsInt(), "The owner must still see her own model");
    assertEquals(OWNED_MODEL_NAME, own.getAsJsonArray("models").get(0).getAsJsonObject().get("name").getAsString());
  }

  /**
   * Resolution is the path every process-taking tool uses when a handle is passed instead of inline JSON, so it is the
   * path that must not hand another principal's flowsheet to runProcess and friends.
   */
  @Test
  @DisplayName("resolve refuses a handle owned by another principal")
  void testResolveIsRefusedForAnotherOwner() {
    authenticateAs("alice");
    final String modelId = register(OWNED_MODEL_NAME, processJson(50.0));

    authenticateAs("bob");
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        ModelRegistry.resolve(modelId);
      }
    }, "Another principal must not resolve the handle");
    assertTrue(error.getMessage().startsWith("Unknown model handle"),
        "Refusal must read as an unknown handle: " + error.getMessage());
    assertFalse(error.getMessage().contains("alice"), "Refusal must not disclose the owner: " + error.getMessage());
  }

  /**
   * The cached-solve path must be guarded too, otherwise the automation tools would read a solved flowsheet that the
   * caller was never allowed to see.
   */
  @Test
  @DisplayName("solvedProcess refuses a handle owned by another principal")
  void testSolvedProcessIsRefusedForAnotherOwner() {
    authenticateAs("alice");
    final String modelId = register(OWNED_MODEL_NAME, processJson(50.0));

    authenticateAs("bob");
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        ModelRegistry.solvedProcess(modelId);
      }
    }, "Another principal must not reach the solved flowsheet");
  }

  /**
   * An unresolved caller must fail closed rather than fall into a bucket shared with named principals. A token carrying
   * no tenant claim falls back to the same default scope as an unbound caller, so ownership is the only thing
   * separating them.
   */
  @Test
  @DisplayName("an anonymous caller cannot reach a named owner's model")
  void testAnonymousCannotReachANamedOwnersModel() {
    McpRequestContext
        .set(McpRequestContext.Principal.ofClaims("alice", null, Collections.<String>emptySet(), "test-issuer"));
    final String modelId = register(OWNED_MODEL_NAME, processJson(50.0));

    McpRequestContext.clear();
    assertRefusedAsUnknown("get", act("get", modelId));
    assertRefusedAsUnknown("delete", act("delete", modelId));
    assertEquals(0,
        JsonParser.parseString(ModelRegistry.run("{\"action\": \"list\"}")).getAsJsonObject().get("count").getAsInt(),
        "An anonymous caller must not see a named principal's models");
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        ModelRegistry.resolve(modelId);
      }
    });

    McpRequestContext.set(McpRequestContext.Principal.ofApiKey("service-key-1234"));
    assertRefusedAsUnknown("get", act("get", modelId));
  }

  /**
   * A refusal must be indistinguishable from a handle that was never registered, so the registry cannot be used to test
   * whether a given model exists.
   */
  @Test
  @DisplayName("a refused handle is indistinguishable from an unknown handle")
  void testRefusalIsNotAnEnumerationOracle() {
    authenticateAs("alice");
    String modelId = register(OWNED_MODEL_NAME, processJson(50.0));

    authenticateAs("bob");
    String unknownId = ModelRegistry.MODEL_ID_PREFIX + "0123456789abcdef";
    JsonObject real = JsonParser.parseString(act("get", modelId)).getAsJsonObject();
    JsonObject unknown = JsonParser.parseString(act("get", unknownId)).getAsJsonObject();

    assertEquals(unknown.get("code").getAsString(), real.get("code").getAsString(),
        "An existing handle must not be distinguishable by error code");
    assertEquals(unknown.get("message").getAsString().replace(unknownId, modelId), real.get("message").getAsString(),
        "An existing handle must not be distinguishable by message");
    assertEquals(unknown.get("remediation").getAsString(), real.get("remediation").getAsString(),
        "An existing handle must not be distinguishable by remediation");
  }

  /**
   * Ownership must restrict other principals only — the owner keeps the full lifecycle.
   */
  @Test
  @DisplayName("the owner retains get, inspect, revise, list and delete")
  void testOwnerRetainsFullAccess() {
    authenticateAs("alice");
    final String modelId = register(OWNED_MODEL_NAME, processJson(50.0));

    assertEquals("success", JsonParser.parseString(act("get", modelId)).getAsJsonObject().get("status").getAsString());
    assertEquals("success",
        JsonParser.parseString(act("inspect", modelId)).getAsJsonObject().get("status").getAsString());

    String revised = ModelRegistry.run("{\"action\": \"revise\", \"modelId\": \"" + modelId + "\", \"processJson\": "
        + JsonParser.parseString(processJson(70.0)) + "}");
    JsonObject revision = JsonParser.parseString(revised).getAsJsonObject();
    assertEquals("success", revision.get("status").getAsString(), revised);
    assertEquals(2, revision.get("revision").getAsInt(), revised);
    assertTrue(ModelRegistry.resolve(modelId).contains("70.0"), "The owner must resolve her own revision");

    assertEquals(1,
        JsonParser.parseString(ModelRegistry.run("{\"action\": \"list\"}")).getAsJsonObject().get("count").getAsInt());
    assertEquals("success",
        JsonParser.parseString(act("delete", modelId)).getAsJsonObject().get("status").getAsString());
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        ModelRegistry.resolve(modelId);
      }
    });
  }

  /**
   * Handles are content-derived, so two principals registering the same flowsheet must still get independent entries;
   * otherwise one caller's revision would silently change what the other resolves.
   */
  @Test
  @DisplayName("identical definitions registered by two principals stay independent")
  void testIdenticalDefinitionsStayIndependentPerOwner() {
    authenticateAs("alice");
    String aliceModel = register(OWNED_MODEL_NAME, processJson(50.0));

    authenticateAs("bob");
    String response = registerResponse("bob-copy", processJson(50.0));
    JsonObject registered = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("success", registered.get("status").getAsString(), response);
    assertEquals("bob", registered.get("owner").getAsString(),
        "Registering identical content must not hand back another principal's record: " + response);
    assertEquals("bob-copy", registered.get("name").getAsString(), response);
    String bobModel = registered.get("modelId").getAsString();

    ModelRegistry.run("{\"action\": \"revise\", \"modelId\": \"" + bobModel + "\", \"processJson\": "
        + JsonParser.parseString(processJson(90.0)) + "}");
    assertTrue(ModelRegistry.resolve(bobModel).contains("90.0"), "Bob must resolve his own revision");

    authenticateAs("alice");
    String stored = ModelRegistry.resolve(aliceModel);
    assertTrue(stored.contains("50.0"), "Alice must still resolve her own definition: " + stored);
    assertFalse(stored.contains("90.0"), "Another principal's revision must not reach Alice's model: " + stored);
  }

  /**
   * Single-user desktop use has no bound identity, and must keep working exactly as before.
   */
  @Test
  @DisplayName("unauthenticated desktop use keeps the full model lifecycle")
  void testAnonymousDesktopUseIsUnchanged() {
    McpRequestContext.clear();
    final String modelId = register("desktop-model", processJson(50.0));

    assertEquals("success", JsonParser.parseString(act("get", modelId)).getAsJsonObject().get("status").getAsString());
    assertEquals("success",
        JsonParser.parseString(act("inspect", modelId)).getAsJsonObject().get("status").getAsString());
    assertTrue(ModelRegistry.resolve(modelId).contains("50.0"), "A desktop caller must resolve its own handle");
    assertEquals(1,
        JsonParser.parseString(ModelRegistry.run("{\"action\": \"list\"}")).getAsJsonObject().get("count").getAsInt(),
        "A desktop caller must see the model it registered");
    assertEquals(modelId, register("desktop-model", processJson(50.0)),
        "Registration must stay idempotent for one caller");
    assertEquals("success",
        JsonParser.parseString(act("delete", modelId)).getAsJsonObject().get("status").getAsString());
  }
}
