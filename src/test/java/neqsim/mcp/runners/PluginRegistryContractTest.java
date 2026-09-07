package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Software-contract tests for {@link PluginRegistry}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PluginRegistryContractTest {

  @AfterEach
  void clearRegistry() {
    PluginRegistry.clear();
  }

  @Test
  void registrationPublishesMetadataWithoutDependingOnCatalogOrder() {
    PluginRegistry.register(new StubPlugin("alpha", "First plugin", false, "alpha"));

    JsonObject catalog = parse(PluginRegistry.listPlugins());
    assertEquals("success", catalog.get("status").getAsString());
    assertEquals(1, catalog.get("count").getAsInt());
    JsonArray plugins = catalog.getAsJsonArray("plugins");
    assertEquals(1, plugins.size());
    JsonObject plugin = plugins.get(0).getAsJsonObject();
    assertEquals("alpha", plugin.get("name").getAsString());
    assertEquals("First plugin", plugin.get("description").getAsString());
    assertEquals(StubPlugin.SCHEMA, plugin.get("inputSchema").getAsString());
  }

  @Test
  void invocationForwardsExactInputAndReturnsPluginOutput() {
    StubPlugin plugin = new StubPlugin("echo", "Echo plugin", false, "first");
    PluginRegistry.register(plugin);
    String input = "{\"value\":42}";

    JsonObject result = parse(PluginRegistry.runPlugin("echo", input));

    assertEquals("success", result.get("status").getAsString());
    assertEquals("first", result.get("version").getAsString());
    assertEquals(input, plugin.lastInput);
  }

  @Test
  void sameNameRegistrationReplacesOnlyThatProcessLocalEntry() {
    StubPlugin first = new StubPlugin("replaceable", "First", false, "one");
    StubPlugin second = new StubPlugin("replaceable", "Second", false, "two");
    PluginRegistry.register(first);
    PluginRegistry.register(second);

    assertEquals(1, PluginRegistry.size());
    assertSame(second, PluginRegistry.get("replaceable"));
    assertEquals("two",
        parse(PluginRegistry.runPlugin("replaceable", "{}")).get("version").getAsString());
  }

  @Test
  void nullAndBlankPluginNamesFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> PluginRegistry.register(null));
    assertThrows(IllegalArgumentException.class,
        () -> PluginRegistry.register(new StubPlugin(" ", "Blank", false, "blank")));
    assertEquals(0, PluginRegistry.size());
  }

  @Test
  void absentPluginReturnsStructuredDiagnosticAndAvailableNames() {
    PluginRegistry.register(new StubPlugin("available", "Available", false, "ok"));

    JsonObject result = parse(PluginRegistry.runPlugin("missing", "{}"));
    JsonObject error = result.getAsJsonArray("errors").get(0).getAsJsonObject();

    assertEquals("error", result.get("status").getAsString());
    assertEquals("PLUGIN_NOT_FOUND", error.get("code").getAsString());
    assertTrue(error.get("message").getAsString().contains("missing"));
    assertTrue(error.get("remediation").getAsString().contains("available"));
  }

  @Test
  void pluginExceptionIsNormalizedWithSchemaRemediation() {
    PluginRegistry.register(new StubPlugin("failing", "Fails", true, "unused"));

    JsonObject result = parse(PluginRegistry.runPlugin("failing", "{\"bad\":true}"));
    JsonObject error = result.getAsJsonArray("errors").get(0).getAsJsonObject();

    assertEquals("error", result.get("status").getAsString());
    assertEquals("PLUGIN_ERROR", error.get("code").getAsString());
    assertTrue(error.get("message").getAsString().contains("synthetic failure"));
    assertTrue(error.get("remediation").getAsString().contains(StubPlugin.SCHEMA));
  }

  @Test
  void unregisterAndClearRemoveOnlyProcessLocalRegistryState() {
    StubPlugin alpha = new StubPlugin("alpha", "Alpha", false, "a");
    StubPlugin beta = new StubPlugin("beta", "Beta", false, "b");
    PluginRegistry.register(alpha);
    PluginRegistry.register(beta);

    assertSame(alpha, PluginRegistry.unregister("alpha"));
    assertFalse(PluginRegistry.has("alpha"));
    assertTrue(PluginRegistry.has("beta"));
    assertNull(PluginRegistry.unregister("missing"));

    PluginRegistry.clear();
    assertEquals(0, PluginRegistry.size());
    assertTrue(PluginRegistry.listNames().isEmpty());
  }

  private static JsonObject parse(String json) {
    return JsonParser.parseString(json).getAsJsonObject();
  }

  private static final class StubPlugin implements McpRunnerPlugin {
    private static final String SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"number\"}}}";

    private final String name;
    private final String description;
    private final boolean fail;
    private final String version;
    private String lastInput;

    private StubPlugin(String name, String description, boolean fail, String version) {
      this.name = name;
      this.description = description;
      this.fail = fail;
      this.version = version;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String description() {
      return description;
    }

    @Override
    public String inputSchema() {
      return SCHEMA;
    }

    @Override
    public String run(String json) {
      lastInput = json;
      if (fail) {
        throw new IllegalStateException("synthetic failure");
      }
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("version", version);
      return result.toString();
    }
  }
}
