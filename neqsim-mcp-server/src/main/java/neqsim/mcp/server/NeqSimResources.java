package neqsim.mcp.server;

import io.quarkiverse.mcp.server.TextResourceContents;
import jakarta.enterprise.context.ApplicationScoped;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;

/**
 * MCP resources exposing NeqSim example and schema catalogs.
 *
 * <p>
 * Static resources provide the full catalog listings. Resource templates allow clients to fetch
 * individual examples or schemas by URI pattern.
 * </p>
 */
@ApplicationScoped
public class NeqSimResources {

  /**
   * Full catalog of all available NeqSim examples.
   *
   * @return JSON listing all example categories, names, and descriptions
   */
  @io.quarkiverse.mcp.server.Resource(uri = "neqsim://example-catalog", name = "Example Catalog",
      description = "Full catalog of NeqSim examples for flash calculations and process simulations")
  public String exampleCatalog() {
    return ExampleCatalog.getCatalogJson();
  }

  /**
   * Full catalog of all available JSON schemas.
   *
   * @return JSON listing schemas for all tool inputs and outputs
   */
  @io.quarkiverse.mcp.server.Resource(uri = "neqsim://schema-catalog", name = "Schema Catalog",
      description = "JSON schemas for all NeqSim MCP tool inputs and outputs")
  public String schemaCatalog() {
    return SchemaCatalog.getCatalogJson();
  }

  /**
   * Get a specific example by category and name.
   *
   * @param category the example category (flash, process, validation)
   * @param name the example name
   * @return resource contents with the example JSON
   */
  @io.quarkiverse.mcp.server.ResourceTemplate(uriTemplate = "neqsim://examples/{category}/{name}",
      name = "NeqSim Example", description = "Get a specific NeqSim example by category and name")
  public TextResourceContents example(String category, String name) {
    String example = ExampleCatalog.getExample(category, name);
    String content = example != null ? example : "{\"error\": \"Example not found\"}";
    return TextResourceContents.create("neqsim://examples/" + category + "/" + name, content);
  }

  /**
   * Get a specific JSON schema by tool name and type.
   *
   * @param tool the tool name
   * @param type input or output
   * @return resource contents with the JSON schema
   */
  @io.quarkiverse.mcp.server.ResourceTemplate(uriTemplate = "neqsim://schemas/{tool}/{type}",
      name = "NeqSim Schema",
      description = "Get the JSON schema for a specific tool input or output")
  public TextResourceContents schema(String tool, String type) {
    String schema = SchemaCatalog.getSchema(tool, type);
    String content = schema != null ? schema : "{\"error\": \"Schema not found\"}";
    return TextResourceContents.create("neqsim://schemas/" + tool + "/" + type, content);
  }
}
