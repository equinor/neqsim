package neqsim.mcp.server;

import io.quarkiverse.mcp.server.TextResourceContents;
import jakarta.enterprise.context.ApplicationScoped;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;
import neqsim.mcp.runners.DataCatalogRunner;

/**
 * MCP resources exposing NeqSim example and schema catalogs, component data, design standards,
 * equation of state models, and material properties.
 *
 * <p>
 * Static resources provide full catalog listings. Resource templates allow clients to fetch
 * individual items by URI pattern. These are read-only data endpoints that agents can browse to
 * discover available data before invoking tools.
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

  // ═══════════════════════════════════════════════════════════════════════════
  // Component database resources
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Browse component families available in the NeqSim database.
   *
   * @return JSON listing component categories and representative components
   */
  @io.quarkiverse.mcp.server.Resource(uri = "neqsim://components", name = "Component Families",
      description = "Browse available component families: hydrocarbons, acid gases, "
          + "inerts, glycols, olefins, hydrogen/syngas, refrigerants, mercaptans")
  public String componentFamilies() {
    return DataCatalogRunner.listComponentFamilies();
  }

  /**
   * Get full thermodynamic properties for a specific component.
   *
   * @param name the component name
   * @return resource contents with all properties (Tc, Pc, omega, MW, etc.)
   */
  @io.quarkiverse.mcp.server.ResourceTemplate(uriTemplate = "neqsim://components/{name}",
      name = "Component Properties",
      description = "Get full thermodynamic properties for a component "
          + "(Tc, Pc, acentric factor, MW, boiling point, CPA/SAFT params, etc.)")
  public TextResourceContents componentProperties(String name) {
    String content = DataCatalogRunner.getComponentProperties(name);
    return TextResourceContents.create("neqsim://components/" + name, content);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Design standards resources
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Browse all available design standards (ASME, API, DNV, ISO, NORSOK).
   *
   * @return JSON listing all standards with scope and applicability
   */
  @io.quarkiverse.mcp.server.Resource(uri = "neqsim://standards", name = "Design Standards Catalog",
      description = "Browse available design standards: ASME, API, DNV, ISO, NORSOK, "
          + "ASTM, EN, TEMA — with equipment type applicability")
  public String designStandards() {
    return DataCatalogRunner.listDesignStandards();
  }

  /**
   * Query a specific design standard's parameters.
   *
   * @param code the standard code (e.g., API-617, DNV-ST-F101)
   * @return resource contents with standard parameters
   */
  @io.quarkiverse.mcp.server.ResourceTemplate(uriTemplate = "neqsim://standards/{code}",
      name = "Standard Detail",
      description = "Get detailed parameters for a specific design standard "
          + "(design factors, limits, material requirements)")
  public TextResourceContents standardDetail(String code) {
    String content = DataCatalogRunner.queryStandard(code, null);
    return TextResourceContents.create("neqsim://standards/" + code, content);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Equation of state resources
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Browse available equation of state models.
   *
   * @return JSON listing all EOS models with descriptions and recommendations
   */
  @io.quarkiverse.mcp.server.Resource(uri = "neqsim://models", name = "Equation of State Models",
      description = "Browse available thermodynamic models: SRK, PR, CPA, GERG-2008, "
          + "PC-SAFT, UMR-PRU, Electrolyte-CPA — with usage recommendations")
  public String eosModels() {
    return DataCatalogRunner.listEOSModels();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Material properties resources
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Browse material properties for a given category.
   *
   * @param type the material category: pipe, plate, casing, compressor, heatExchanger
   * @return resource contents with material grades and properties
   */
  @io.quarkiverse.mcp.server.ResourceTemplate(uriTemplate = "neqsim://materials/{type}",
      name = "Material Properties",
      description = "Browse material grades and properties by type: pipe, plate, "
          + "casing, compressor, heatExchanger — includes SMYS, SMTS, density")
  public TextResourceContents materialProperties(String type) {
    String content = DataCatalogRunner.listMaterials(type);
    return TextResourceContents.create("neqsim://materials/" + type, content);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Database catalog resource
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Browse all available data tables in thermodynamic and design databases.
   *
   * @return JSON listing all queryable tables with descriptions
   */
  @io.quarkiverse.mcp.server.Resource(uri = "neqsim://data-tables", name = "Data Tables Catalog",
      description = "Browse all available data tables: thermodynamic properties, "
          + "binary interaction parameters, reaction data, design standards, "
          + "material properties, cost estimation data")
  public String dataTables() {
    return DataCatalogRunner.listDataTables();
  }
}
