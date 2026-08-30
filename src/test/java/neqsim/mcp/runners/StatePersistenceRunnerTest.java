package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link StatePersistenceRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class StatePersistenceRunnerTest {

  @Test
  void testGetInfo() {
    String json = "{\"action\": \"getInfo\"}";
    String result = StatePersistenceRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Get info failed: " + result);
  }

  @Test
  void testListStates(@TempDir Path temporaryDirectory) {
    String allowExternalProperty = "neqsim.mcp.allowExternalStateDir";
    String previousAllowExternal = System.getProperty(allowExternalProperty);
    System.setProperty(allowExternalProperty, "true");
    try {
      JsonObject configureInput = new JsonObject();
      configureInput.addProperty("action", "setStorageDir");
      configureInput.addProperty("directory", temporaryDirectory.resolve("saved_simulations").toString());
      String configureResult = StatePersistenceRunner.run(configureInput.toString());
      JsonObject configureObject = JsonParser.parseString(configureResult).getAsJsonObject();
      assertEquals("success", configureObject.get("status").getAsString(),
          "Configure temporary storage failed: " + configureResult);

      String result = StatePersistenceRunner.run("{\"action\": \"list\"}");
      assertNotNull(result);
      JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
      assertEquals("success", obj.get("status").getAsString(), "List states failed: " + result);
      assertEquals(0, obj.get("count").getAsInt());
    } finally {
      if (previousAllowExternal == null) {
        System.clearProperty(allowExternalProperty);
      } else {
        System.setProperty(allowExternalProperty, previousAllowExternal);
      }
    }
  }

  @Test
  void testNullInput() {
    String result = StatePersistenceRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void loadStateRejectsFilePathOutsideStorageDirectory() {
    String outside = Paths.get(System.getProperty("java.io.tmpdir"), "outside-state.json").toString().replace("\\",
        "\\\\");
    String result = StatePersistenceRunner.run("{\"action\": \"load\", \"filePath\": \"" + outside + "\"}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
    assertEquals("INVALID_PATH", obj.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void deleteStateRejectsTraversalFilename() {
    String result = StatePersistenceRunner.run("{\"action\": \"delete\", \"filename\": \"../outside.json\"}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
    assertEquals("INVALID_FILENAME", obj.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void setStorageDirectoryRejectsExternalDirectoryByDefault() {
    String outside = Paths.get(System.getProperty("java.io.tmpdir"), "neqsim-external-state").toString().replace("\\",
        "\\\\");
    String result = StatePersistenceRunner.run("{\"action\": \"setStorageDir\", \"directory\": \"" + outside + "\"}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
    assertEquals("DIR_OUTSIDE_SANDBOX",
        obj.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void getNeqSimVersionUsesSystemPropertyOverride() {
    String previousVersion = System.getProperty("neqsim.version");
    System.setProperty("neqsim.version", "9.9.9-test");
    try {
      assertEquals("9.9.9-test", StatePersistenceRunner.getNeqSimVersion());
    } finally {
      if (previousVersion == null) {
        System.clearProperty("neqsim.version");
      } else {
        System.setProperty("neqsim.version", previousVersion);
      }
    }
  }

  @Test
  void qualifiesCanonicalPersistedStateLifecycle(@TempDir Path temporaryDirectory) throws Exception {
    String allowExternalProperty = "neqsim.mcp.allowExternalStateDir";
    String previousAllowExternal = System.getProperty(allowExternalProperty);
    System.setProperty(allowExternalProperty, "true");
    String originalSessionId = null;
    String restoredSessionId = null;
    try {
      Path storageDirectory = temporaryDirectory.resolve("saved_simulations");
      JsonObject configureInput = new JsonObject();
      configureInput.addProperty("action", "setStorageDir");
      configureInput.addProperty("directory", storageDirectory.toString());
      assertSuccess(StatePersistenceRunner.run(configureInput.toString()));

      JsonObject processDefinition = JsonParser.parseString(ExampleCatalog.getExample("process", "simple-separation"))
          .getAsJsonObject();
      JsonObject createInput = new JsonObject();
      createInput.addProperty("action", "create");
      createInput.addProperty("name", "phase0-persisted-state");
      createInput.add("processJson", processDefinition);
      JsonObject created = assertSuccess(SessionRunner.run(createInput.toString()));
      originalSessionId = created.get("sessionId").getAsString();
      assertTrue(created.get("equipmentCount").getAsInt() > 0);

      JsonObject saveInput = new JsonObject();
      saveInput.addProperty("action", "save");
      saveInput.addProperty("sessionId", originalSessionId);
      saveInput.addProperty("name", "phase0-state");
      saveInput.addProperty("version", "1.0.0");
      saveInput.addProperty("description", "Canonical simple-separation lifecycle evidence");
      saveInput.add("processDefinition", processDefinition);
      JsonObject firstSave = assertSuccess(StatePersistenceRunner.run(saveInput.toString()));
      JsonObject secondSave = assertSuccess(StatePersistenceRunner.run(saveInput.toString()));
      String firstFilename = firstSave.get("filename").getAsString();
      String secondFilename = secondSave.get("filename").getAsString();
      assertFalse(firstFilename.equals(secondFilename), "A repeated save must not overwrite the first state");

      Path firstPath = storageDirectory.resolve(firstFilename);
      JsonObject savedEnvelope = JsonParser
          .parseString(new String(Files.readAllBytes(firstPath), StandardCharsets.UTF_8)).getAsJsonObject();
      assertEquals("neqsim-saved-state", savedEnvelope.get("format").getAsString());
      assertEquals("1.0.0", savedEnvelope.get("formatVersion").getAsString());
      assertEquals(processDefinition, savedEnvelope.getAsJsonObject("processDefinition"));
      assertTrue(savedEnvelope.has("neqsimVersion"));

      JsonObject listed = assertSuccess(StatePersistenceRunner.run("{\"action\":\"list\"}"));
      assertEquals(2, listed.get("count").getAsInt());

      JsonObject compareInput = new JsonObject();
      compareInput.addProperty("action", "compare");
      compareInput.addProperty("file1", firstFilename);
      compareInput.addProperty("file2", secondFilename);
      JsonObject compared = assertSuccess(StatePersistenceRunner.run(compareInput.toString()));
      assertTrue(compared.get("processDefinitionsEqual").getAsBoolean());
      assertTrue(compared.has("statesEqual"));

      JsonObject loadInput = new JsonObject();
      loadInput.addProperty("action", "load");
      loadInput.addProperty("filename", firstFilename);
      JsonObject loaded = assertSuccess(StatePersistenceRunner.run(loadInput.toString()));
      restoredSessionId = loaded.get("sessionId").getAsString();
      assertFalse(originalSessionId.equals(restoredSessionId));
      JsonObject restoredState = assertSuccess(
          SessionRunner.run("{\"action\":\"getState\",\"sessionId\":\"" + restoredSessionId + "\"}"));
      assertTrue(restoredState.get("equipmentCount").getAsInt() > 0);

      JsonObject exportInput = new JsonObject();
      exportInput.addProperty("action", "export");
      exportInput.addProperty("sessionId", originalSessionId);
      JsonObject exported = assertSuccess(StatePersistenceRunner.run(exportInput.toString()));
      assertEquals("neqsim-exported-session", exported.getAsJsonObject("exportedSession").get("format").getAsString());

      deleteSavedState(firstFilename);
      deleteSavedState(secondFilename);
      JsonObject emptyList = assertSuccess(StatePersistenceRunner.run("{\"action\":\"list\"}"));
      assertEquals(0, emptyList.get("count").getAsInt());

      JsonObject unknown = JsonParser.parseString(StatePersistenceRunner.run("{\"action\":\"not-supported\"}"))
          .getAsJsonObject();
      assertEquals("error", unknown.get("status").getAsString());
      assertEquals("UNKNOWN_ACTION", errorCode(unknown));
    } finally {
      closeSession(restoredSessionId);
      closeSession(originalSessionId);
      if (previousAllowExternal == null) {
        System.clearProperty(allowExternalProperty);
      } else {
        System.setProperty(allowExternalProperty, previousAllowExternal);
      }
    }
  }

  private static void deleteSavedState(String filename) {
    JsonObject deleteInput = new JsonObject();
    deleteInput.addProperty("action", "delete");
    deleteInput.addProperty("filename", filename);
    assertSuccess(StatePersistenceRunner.run(deleteInput.toString()));
  }

  private static void closeSession(String sessionId) {
    if (sessionId != null) {
      SessionRunner.run("{\"action\":\"close\",\"sessionId\":\"" + sessionId + "\"}");
    }
  }

  private static JsonObject assertSuccess(String result) {
    JsonObject object = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", object.get("status").getAsString(), result);
    return object;
  }

  private static String errorCode(JsonObject response) {
    return response.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString();
  }
}
