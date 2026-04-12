package neqsim.mcp.runners;

/**
 * Plugin interface for extending the MCP server with custom runners.
 *
 * <p>
 * Third-party or domain-specific runners can implement this interface and register themselves via
 * {@link PluginRegistry}. Each plugin declares a unique name, a description for AI discovery, and a
 * schema string describing its input format.
 * </p>
 *
 * <p>
 * Example implementation:
 * </p>
 *
 * <pre>
 * public class MyCustomRunner implements McpRunnerPlugin {
 *   public String name() {
 *     return "my_custom_analysis";
 *   }
 * 
 *   public String description() {
 *     return "Runs custom corrosion analysis";
 *   }
 * 
 *   public String inputSchema() {
 *     return "{\"type\":\"object\",\"properties\":{...}}";
 *   }
 * 
 *   public String run(String json) {
 *     // Parse JSON, do calculation, return result JSON
 *   }
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public interface McpRunnerPlugin {

  /**
   * The unique name of this plugin (used as the tool identifier).
   *
   * @return the plugin name (alphanumeric + underscores, e.g. "corrosion_analysis")
   */
  String name();

  /**
   * A human-readable description of what this plugin does.
   *
   * @return the description (shown to the AI agent for tool selection)
   */
  String description();

  /**
   * A JSON Schema string describing the expected input format.
   *
   * @return the JSON Schema string, or empty string if unstructured
   */
  String inputSchema();

  /**
   * Executes the plugin with the given JSON input and returns JSON output.
   *
   * @param json the input JSON string
   * @return the output JSON string (should follow ApiEnvelope format)
   */
  String run(String json);
}
