package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Registry for MCP runner plugins.
 *
 * <p>
 * Provides a central place to register, discover, and invoke custom runner plugins. Plugins are
 * stored in a thread-safe map keyed by name. The registry can list all available plugins (with
 * descriptions and schemas) for AI agent discovery.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PluginRegistry {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Registered plugins keyed by name. */
  private static final ConcurrentHashMap<String, McpRunnerPlugin> PLUGINS =
      new ConcurrentHashMap<String, McpRunnerPlugin>();

  /**
   * Private constructor — all methods are static.
   */
  private PluginRegistry() {}

  /**
   * Registers a plugin. Overwrites any existing plugin with the same name.
   *
   * @param plugin the plugin to register
   * @throws IllegalArgumentException if plugin or its name is null
   */
  public static void register(McpRunnerPlugin plugin) {
    if (plugin == null || plugin.name() == null || plugin.name().trim().isEmpty()) {
      throw new IllegalArgumentException("Plugin and plugin name must not be null or empty");
    }
    PLUGINS.put(plugin.name(), plugin);
  }

  /**
   * Unregisters a plugin by name.
   *
   * @param name the plugin name
   * @return the removed plugin, or null if not found
   */
  public static McpRunnerPlugin unregister(String name) {
    return PLUGINS.remove(name);
  }

  /**
   * Returns a plugin by name.
   *
   * @param name the plugin name
   * @return the plugin, or null if not registered
   */
  public static McpRunnerPlugin get(String name) {
    return PLUGINS.get(name);
  }

  /**
   * Checks whether a plugin with the given name is registered.
   *
   * @param name the plugin name
   * @return true if registered
   */
  public static boolean has(String name) {
    return PLUGINS.containsKey(name);
  }

  /**
   * Returns the number of registered plugins.
   *
   * @return plugin count
   */
  public static int size() {
    return PLUGINS.size();
  }

  /**
   * Returns all registered plugin names.
   *
   * @return unmodifiable list of names
   */
  public static List<String> listNames() {
    return Collections.unmodifiableList(new ArrayList<String>(PLUGINS.keySet()));
  }

  /**
   * Runs a plugin by name with the given JSON input.
   *
   * @param name the plugin name
   * @param json the input JSON
   * @return the plugin output JSON, or an error JSON if not found
   */
  public static String runPlugin(String name, String json) {
    McpRunnerPlugin plugin = PLUGINS.get(name);
    if (plugin == null) {
      JsonObject error = new JsonObject();
      error.addProperty("status", "error");
      JsonArray errors = new JsonArray();
      JsonObject err = new JsonObject();
      err.addProperty("code", "PLUGIN_NOT_FOUND");
      err.addProperty("message", "No plugin registered with name: " + name);

      // Suggest similar names
      StringBuilder suggestion = new StringBuilder("Available plugins: ");
      List<String> names = listNames();
      if (names.isEmpty()) {
        suggestion.append("(none registered)");
      } else {
        for (int i = 0; i < names.size(); i++) {
          if (i > 0) {
            suggestion.append(", ");
          }
          suggestion.append(names.get(i));
        }
      }
      err.addProperty("remediation", suggestion.toString());
      errors.add(err);
      error.add("errors", errors);
      return GSON.toJson(error);
    }

    try {
      return plugin.run(json);
    } catch (Exception e) {
      JsonObject error = new JsonObject();
      error.addProperty("status", "error");
      JsonArray errors = new JsonArray();
      JsonObject err = new JsonObject();
      err.addProperty("code", "PLUGIN_ERROR");
      err.addProperty("message", "Plugin '" + name + "' failed: " + e.getMessage());
      err.addProperty("remediation", "Check plugin input format. Schema: " + plugin.inputSchema());
      errors.add(err);
      error.add("errors", errors);
      return GSON.toJson(error);
    }
  }

  /**
   * Lists all registered plugins with their metadata (for AI agent discovery).
   *
   * @return JSON string with plugin catalog
   */
  public static String listPlugins() {
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("count", PLUGINS.size());

    JsonArray pluginList = new JsonArray();
    for (Map.Entry<String, McpRunnerPlugin> entry : PLUGINS.entrySet()) {
      McpRunnerPlugin p = entry.getValue();
      JsonObject info = new JsonObject();
      info.addProperty("name", p.name());
      info.addProperty("description", p.description());
      info.addProperty("inputSchema", p.inputSchema());
      pluginList.add(info);
    }
    response.add("plugins", pluginList);

    return GSON.toJson(response);
  }

  /**
   * Clears all registered plugins. Primarily for testing.
   */
  public static void clear() {
    PLUGINS.clear();
  }
}
