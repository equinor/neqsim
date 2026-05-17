package neqsim.mcp.runners;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.util.database.NeqSimDataBase;
import neqsim.util.database.NeqSimProcessDesignDataBase;

/**
 * Read-only data catalog runner that exposes NeqSim databases as browsable resources.
 *
 * <p>
 * Provides structured access to the component database, design standards tables, thermodynamic
 * property data, and material properties without running simulations. Agents can discover available
 * data before deciding what tools to invoke.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DataCatalogRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private DataCatalogRunner() {}

  /**
   * Main entry point for data catalog operations.
   *
   * @param json JSON with action and parameters
   * @return JSON with results
   */
  public static String run(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "";

      switch (action) {
        case "listComponentFamilies":
          return listComponentFamilies();
        case "getComponentProperties":
          return getComponentProperties(
              input.has("componentName") ? input.get("componentName").getAsString() : "");
        case "listEOSModels":
          return listEOSModels();
        case "listMaterials":
          return listMaterials(
              input.has("materialType") ? input.get("materialType").getAsString() : "pipe");
        case "listDesignStandards":
          return listDesignStandards();
        case "queryStandard":
          return queryStandard(input.has("code") ? input.get("code").getAsString() : "",
              input.has("equipmentType") ? input.get("equipmentType").getAsString() : "");
        case "listDataTables":
          return listDataTables();
        default:
          JsonObject error = new JsonObject();
          error.addProperty("status", "error");
          error.addProperty("message", "Unknown data catalog action: " + action);
          error.addProperty("remediation",
              "Use: listComponentFamilies, getComponentProperties, listEOSModels, "
                  + "listMaterials, listDesignStandards, queryStandard, listDataTables");
          return GSON.toJson(error);
      }
    } catch (Exception e) {
      JsonObject error = new JsonObject();
      error.addProperty("status", "error");
      error.addProperty("message", e.getMessage());
      return GSON.toJson(error);
    }
  }

  /**
   * Lists all available component families (categories) in the component database.
   *
   * @return JSON with component families and count
   */
  public static String listComponentFamilies() {
    JsonObject result = new JsonObject();
    result.addProperty("status", "success");

    Map<String, List<String>> families = new LinkedHashMap<String, List<String>>();
    families.put("hydrocarbons",
        Arrays.asList("methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
            "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "n-decane", "benzene",
            "toluene", "cyclohexane"));
    families.put("acid_gases", Arrays.asList("CO2", "H2S", "COS", "SO2"));
    families.put("inerts", Arrays.asList("nitrogen", "helium", "argon", "oxygen"));
    families.put("water_and_hydrates", Arrays.asList("water", "MEG", "DEG", "TEG", "methanol"));
    families.put("olefins", Arrays.asList("ethylene", "propylene", "1-butene"));
    families.put("hydrogen_and_syngas", Arrays.asList("hydrogen", "CO", "ammonia"));
    families.put("refrigerants", Arrays.asList("R-22", "R-134a", "R-32"));
    families.put("mercaptans", Arrays.asList("methyl-mercaptan", "ethyl-mercaptan"));

    JsonObject fam = new JsonObject();
    for (Map.Entry<String, List<String>> entry : families.entrySet()) {
      JsonArray arr = new JsonArray();
      for (String comp : entry.getValue()) {
        arr.add(comp);
      }
      fam.add(entry.getKey(), arr);
    }
    result.add("families", fam);
    result.addProperty("note", "Use searchComponents tool for full database search. "
        + "Use getComponentProperties for detailed thermodynamic data.");
    return GSON.toJson(result);
  }

  /**
   * Gets full thermodynamic properties for a component from the database.
   *
   * @param componentName the component name
   * @return JSON with all available properties
   */
  public static String getComponentProperties(String componentName) {
    JsonObject result = new JsonObject();
    try {
      try (NeqSimDataBase database = new NeqSimDataBase()) {
        ResultSet rs = database
            .getResultSet("SELECT * FROM comp WHERE NAME='" + sanitize(componentName) + "'");
        if (rs.next()) {
          result.addProperty("status", "success");
          result.addProperty("component", componentName);
          ResultSetMetaData meta = rs.getMetaData();
          JsonObject props = new JsonObject();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            String col = meta.getColumnName(i);
            String val = rs.getString(i);
            if (val != null && !val.isEmpty()) {
              try {
                double numVal = Double.parseDouble(val);
                props.addProperty(col, numVal);
              } catch (NumberFormatException e) {
                props.addProperty(col, val);
              }
            }
          }
          result.add("properties", props);
        } else {
          result.addProperty("status", "error");
          result.addProperty("message", "Component '" + componentName + "' not found");
          // Try fuzzy match
          String closest = ComponentQuery.closestMatch(componentName);
          if (closest != null) {
            result.addProperty("suggestion", closest);
          }
        }
      }
    } catch (Exception e) {
      result.addProperty("status", "error");
      result.addProperty("message", e.getMessage());
    }
    return GSON.toJson(result);
  }

  /**
   * Lists all available design standards in the standards index.
   *
   * @return JSON with all standards, their scope, and data file references
   */
  public static String listDesignStandards() {
    JsonObject result = new JsonObject();
    try {
      try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
        ResultSet rs = database.getResultSet("SELECT * FROM standards_index WHERE ACTIVE='true'");
        JsonArray standards = new JsonArray();
        while (rs.next()) {
          JsonObject std = new JsonObject();
          std.addProperty("code", rs.getString("STANDARD_CODE"));
          std.addProperty("version", rs.getString("VERSION"));
          std.addProperty("name", rs.getString("NAME"));
          std.addProperty("equipmentTypes", rs.getString("EQUIPMENT_TYPES"));
          std.addProperty("dataFile", rs.getString("DATA_FILE"));
          standards.add(std);
        }
        result.addProperty("status", "success");
        result.addProperty("count", standards.size());
        result.add("standards", standards);
      }
    } catch (Exception e) {
      result.addProperty("status", "success");
      // Provide static fallback if DB query fails
      JsonArray standards = new JsonArray();
      String[][] stdData = {{"ASME-VIII-Div1", "Pressure Vessels", "Separator,Vessel,Column"},
          {"ASME-B31.3", "Process Piping", "Pipeline,Pipe"},
          {"API-617", "Axial and Centrifugal Compressors", "Compressor"},
          {"API-650", "Welded Tanks for Oil Storage", "Tank,StorageTank"},
          {"DNV-ST-F101", "Submarine Pipeline Systems", "Pipeline,SubseaPipeline"},
          {"DNV-OS-F101", "Submarine Pipeline Systems (legacy)", "Pipeline"},
          {"NORSOK-L-001", "Piping and Valves", "Pipeline,Valve"},
          {"NORSOK-P-001", "Process Design", "All"},
          {"ISO-6976", "Natural Gas - Calorific Value", "Gas"},
          {"TEMA", "Heat Exchangers", "HeatExchanger,Cooler,Heater"},
          {"API-RP-14E", "Pipeline Velocity Limits", "Pipeline"},
          {"API-5CT", "Casing and Tubing", "Well,SubseaWell"},
          {"NORSOK-D-010", "Well Integrity", "Well,SubseaWell"}};
      for (String[] s : stdData) {
        JsonObject std = new JsonObject();
        std.addProperty("code", s[0]);
        std.addProperty("name", s[1]);
        std.addProperty("equipmentTypes", s[2]);
        standards.add(std);
      }
      result.addProperty("count", standards.size());
      result.add("standards", standards);
    }
    return GSON.toJson(result);
  }

  /**
   * Queries a specific standards table for parameters applicable to an equipment type.
   *
   * @param standardCode the standard code (e.g., "ASME-VIII-Div1")
   * @param equipmentType the equipment type to filter by (optional)
   * @return JSON with standard parameters
   */
  public static String queryStandard(String standardCode, String equipmentType) {
    JsonObject result = new JsonObject();
    try {
      // Map standard code to table
      String table = mapStandardToTable(standardCode);
      if (table == null) {
        result.addProperty("status", "error");
        result.addProperty("message", "Unknown standard code: " + standardCode
            + ". Use listDesignStandards for available codes.");
        return GSON.toJson(result);
      }

      try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
        String sql = "SELECT * FROM " + table;
        if (equipmentType != null && !equipmentType.isEmpty()) {
          sql += " WHERE EQUIPMENT_TYPE LIKE '%" + sanitize(equipmentType) + "%'";
        }
        sql += " FETCH FIRST 100 ROWS ONLY";

        ResultSet rs = database.getResultSet(sql);
        ResultSetMetaData meta = rs.getMetaData();
        JsonArray rows = new JsonArray();
        JsonArray columns = new JsonArray();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
          columns.add(meta.getColumnName(i));
        }
        while (rs.next()) {
          JsonObject row = new JsonObject();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            String val = rs.getString(i);
            if (val != null) {
              row.addProperty(meta.getColumnName(i), val);
            }
          }
          rows.add(row);
        }
        result.addProperty("status", "success");
        result.addProperty("standard", standardCode);
        result.addProperty("table", table);
        result.add("columns", columns);
        result.addProperty("rowCount", rows.size());
        result.add("data", rows);
      }
    } catch (Exception e) {
      result.addProperty("status", "error");
      result.addProperty("message", e.getMessage());
    }
    return GSON.toJson(result);
  }

  /**
   * Lists available material properties (pipe materials, plate materials, casing, etc.).
   *
   * @param materialType the material category: pipe, plate, casing, compressor, heatExchanger
   * @return JSON with material grades and their properties
   */
  public static String listMaterials(String materialType) {
    JsonObject result = new JsonObject();
    try {
      String table;
      switch (materialType != null ? materialType.toLowerCase() : "pipe") {
        case "plate":
          table = "MaterialPlateProperties";
          break;
        case "casing":
          table = "CasingProperties";
          break;
        case "compressor":
          table = "CompressorCasingMaterials";
          break;
        case "heatexchanger":
        case "heat_exchanger":
          table = "HeatExchangerTubeMaterials";
          break;
        default:
          table = "MaterialPipeProperties";
          break;
      }

      try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
        ResultSet rs = database.getResultSet("SELECT * FROM " + table);
        ResultSetMetaData meta = rs.getMetaData();
        JsonArray materials = new JsonArray();
        while (rs.next()) {
          JsonObject mat = new JsonObject();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            String val = rs.getString(i);
            if (val != null && !val.isEmpty()) {
              mat.addProperty(meta.getColumnName(i), val);
            }
          }
          materials.add(mat);
        }
        result.addProperty("status", "success");
        result.addProperty("materialType", materialType);
        result.addProperty("table", table);
        result.addProperty("count", materials.size());
        result.add("materials", materials);
      }
    } catch (Exception e) {
      result.addProperty("status", "error");
      result.addProperty("message", e.getMessage());
    }
    return GSON.toJson(result);
  }

  /**
   * Lists all available equation of state models with descriptions and applicability.
   *
   * @return JSON with EOS models catalog
   */
  public static String listEOSModels() {
    JsonObject result = new JsonObject();
    result.addProperty("status", "success");

    JsonArray models = new JsonArray();

    addEOS(models, "SRK", "Soave-Redlich-Kwong",
        "General-purpose cubic EOS. Good for gas-phase and vapor-liquid equilibria.",
        "Natural gas, hydrocarbons, light gases", "1972");
    addEOS(models, "PR", "Peng-Robinson",
        "Cubic EOS widely used in oil and gas. Better liquid density than SRK.",
        "Oil systems, heavy hydrocarbons, gas processing", "1976");
    addEOS(models, "CPA", "CPA-SRK (Cubic Plus Association)",
        "Handles associating fluids (water, alcohols, glycols, acids).",
        "Water+hydrocarbon, MEG/DEG/TEG, methanol injection, CO2+water", "2006");
    addEOS(models, "GERG2008", "GERG-2008",
        "High-accuracy multi-parameter EOS for natural gas custody transfer.",
        "Sales gas, pipeline gas, custody metering (ISO 20765)", "2008");
    addEOS(models, "PCSAFT", "PC-SAFT", "Statistical mechanics-based EOS for complex fluids.",
        "Polymers, deep eutectic solvents, complex mixtures", "2001");
    addEOS(models, "UMRPRU", "UMR-PRU",
        "PR with UNIFAC mixing rules and Mathias-Copeman alpha function.",
        "Polar mixtures, asymmetric systems", "2004");
    addEOS(models, "Electrolyte-CPA", "Electrolyte CPA",
        "CPA with electrolyte term for salt-containing systems.",
        "Produced water, brines, scale prediction, desalination", "2010");

    result.add("models", models);
    result.addProperty("recommendation",
        "SRK for general gas work, PR for oil, CPA for water/glycol systems, "
            + "GERG2008 for custody transfer metering.");
    return GSON.toJson(result);
  }

  /**
   * Lists available data tables in both thermodynamic and design databases.
   *
   * @return JSON with table names and descriptions
   */
  public static String listDataTables() {
    JsonObject result = new JsonObject();
    result.addProperty("status", "success");

    JsonObject thermoDb = new JsonObject();
    thermoDb.addProperty("name", "Thermodynamic Database");
    JsonArray thermoTables = new JsonArray();
    addTable(thermoTables, "comp", "Component properties (Tc, Pc, omega, MW, etc.)");
    addTable(thermoTables, "inter", "Binary interaction parameters");
    addTable(thermoTables, "element", "Elemental composition");
    addTable(thermoTables, "ISO6976constants", "ISO 6976 gas property constants");
    addTable(thermoTables, "ISO6976constants2016", "ISO 6976:2016 updated constants");
    addTable(thermoTables, "STOCCOEFDATA", "Stoichiometric coefficient data");
    addTable(thermoTables, "REACTIONDATA", "Chemical reaction data");
    addTable(thermoTables, "ReactionKSPdata", "Equilibrium constant data");
    addTable(thermoTables, "UNIFACcomp", "UNIFAC component parameters");
    addTable(thermoTables, "UNIFACGroupParam", "UNIFAC group parameters");
    addTable(thermoTables, "MBWR32param", "MBWR 32-parameter EOS data");
    addTable(thermoTables, "PitzerParameters", "Pitzer electrolyte parameters");
    addTable(thermoTables, "PIPEDATA", "Pipe dimension data");
    thermoDb.add("tables", thermoTables);

    JsonObject designDb = new JsonObject();
    designDb.addProperty("name", "Process Design Database");
    JsonArray designTables = new JsonArray();
    addTable(designTables, "TechnicalRequirements_Process",
        "Company-specific process design requirements");
    addTable(designTables, "TechnicalRequirements_Piping", "Piping design requirements");
    addTable(designTables, "TechnicalRequirements_Material", "Material requirements");
    addTable(designTables, "MaterialPipeProperties", "Pipe material grades and SMYS/SMTS");
    addTable(designTables, "MaterialPlateProperties", "Plate material grades");
    addTable(designTables, "CasingProperties", "Well casing material properties");
    addTable(designTables, "standards_index", "Standards applicability index");
    addTable(designTables, "Packing", "Column packing data");
    addTable(designTables, "CompressorSuppliers", "Compressor OEM database");
    addTable(designTables, "HeatExchangerSuppliers", "Heat exchanger supplier database");
    addTable(designTables, "SubseaCostEstimation", "Subsea equipment cost data");
    designDb.add("tables", designTables);

    result.add("thermodynamicDatabase", thermoDb);
    result.add("designDatabase", designDb);

    return GSON.toJson(result);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Maps a standard code prefix to a database table name.
   *
   * @param standardCode the standard code
   * @return the table name, or null if unknown
   */
  private static String mapStandardToTable(String standardCode) {
    if (standardCode == null) {
      return null;
    }
    String upper = standardCode.toUpperCase();
    if (upper.startsWith("API")) {
      return "api_standards";
    }
    if (upper.startsWith("ASME")) {
      return "asme_standards";
    }
    if (upper.startsWith("ASTM")) {
      return "astm_standards";
    }
    if (upper.startsWith("DNV") || upper.startsWith("ISO") || upper.startsWith("EN")) {
      return "dnv_iso_en_standards";
    }
    if (upper.startsWith("NORSOK")) {
      return "norsok_standards";
    }
    if (upper.contains("SUBSEA")) {
      return "subsea_standards";
    }
    return null;
  }

  /**
   * Sanitizes a string for safe SQL use (basic defense against injection).
   *
   * @param input the input string
   * @return sanitized string
   */
  private static String sanitize(String input) {
    if (input == null) {
      return "";
    }
    return input.replaceAll("[';\"\\\\]", "");
  }

  /**
   * Adds an EOS model entry to the models array.
   *
   * @param models the array to add to
   * @param code the short code
   * @param fullName the full name
   * @param description the description
   * @param applicability when to use this model
   * @param year the year introduced
   */
  private static void addEOS(JsonArray models, String code, String fullName, String description,
      String applicability, String year) {
    JsonObject m = new JsonObject();
    m.addProperty("code", code);
    m.addProperty("name", fullName);
    m.addProperty("description", description);
    m.addProperty("applicability", applicability);
    m.addProperty("year", year);
    models.add(m);
  }

  /**
   * Adds a table description entry.
   *
   * @param tables the array to add to
   * @param name the table name
   * @param description the description
   */
  private static void addTable(JsonArray tables, String name, String description) {
    JsonObject t = new JsonObject();
    t.addProperty("name", name);
    t.addProperty("description", description);
    tables.add(t);
  }
}
