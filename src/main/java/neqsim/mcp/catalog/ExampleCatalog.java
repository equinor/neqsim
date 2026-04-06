package neqsim.mcp.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Catalog of example JSON inputs for each MCP runner.
 *
 * <p>
 * Provides ready-to-use example JSON strings for flash calculations, process simulations, component
 * queries, and validation. These examples serve a dual purpose:
 * </p>
 * <ul>
 * <li><strong>MCP Resources</strong> — served via {@code neqsim://example/{category}/{name}} URIs
 * so that language models can read them and learn the input format</li>
 * <li><strong>Test fixtures</strong> — used by unit tests to verify runners work correctly</li>
 * </ul>
 *
 * <p>
 * All methods are static and return JSON strings that can be passed directly to the corresponding
 * runner's {@code run(String)} method.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ExampleCatalog {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  /**
   * Private constructor — all methods are static.
   */
  private ExampleCatalog() {}

  // ========== Flash Examples ==========

  /**
   * Returns a simple TP flash of a natural gas.
   *
   * @return JSON string for FlashRunner.run()
   */
  public static String flashTPSimpleGas() {
    return "{\n" + "  \"model\": \"SRK\",\n"
        + "  \"temperature\": {\"value\": 25.0, \"unit\": \"C\"},\n"
        + "  \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"},\n" + "  \"flashType\": \"TP\",\n"
        + "  \"components\": {\n" + "    \"methane\": 0.85,\n" + "    \"ethane\": 0.10,\n"
        + "    \"propane\": 0.05\n" + "  },\n" + "  \"mixingRule\": \"classic\"\n" + "}";
  }

  /**
   * Returns a two-phase TP flash example.
   *
   * @return JSON string for FlashRunner.run()
   */
  public static String flashTPTwoPhase() {
    return "{\n" + "  \"model\": \"SRK\",\n"
        + "  \"temperature\": {\"value\": -20.0, \"unit\": \"C\"},\n"
        + "  \"pressure\": {\"value\": 10.0, \"unit\": \"bara\"},\n" + "  \"flashType\": \"TP\",\n"
        + "  \"components\": {\n" + "    \"methane\": 0.50,\n" + "    \"propane\": 0.50\n"
        + "  },\n" + "  \"mixingRule\": \"classic\"\n" + "}";
  }

  /**
   * Returns a dew point temperature flash example.
   *
   * @return JSON string for FlashRunner.run()
   */
  public static String flashDewPointT() {
    return "{\n" + "  \"model\": \"SRK\",\n"
        + "  \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"},\n"
        + "  \"flashType\": \"dewPointT\",\n" + "  \"components\": {\n" + "    \"methane\": 0.80,\n"
        + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05,\n" + "    \"n-butane\": 0.03,\n"
        + "    \"n-pentane\": 0.02\n" + "  }\n" + "}";
  }

  /**
   * Returns a bubble point pressure flash example.
   *
   * @return JSON string for FlashRunner.run()
   */
  public static String flashBubblePointP() {
    return "{\n" + "  \"model\": \"PR\",\n"
        + "  \"temperature\": {\"value\": 100.0, \"unit\": \"C\"},\n"
        + "  \"flashType\": \"bubblePointP\",\n" + "  \"components\": {\n"
        + "    \"methane\": 0.50,\n" + "    \"n-heptane\": 0.50\n" + "  }\n" + "}";
  }

  /**
   * Returns a CPA model flash with water for hydrate-forming systems.
   *
   * @return JSON string for FlashRunner.run()
   */
  public static String flashCPAWithWater() {
    return "{\n" + "  \"model\": \"CPA\",\n"
        + "  \"temperature\": {\"value\": 5.0, \"unit\": \"C\"},\n"
        + "  \"pressure\": {\"value\": 100.0, \"unit\": \"bara\"},\n" + "  \"flashType\": \"TP\",\n"
        + "  \"components\": {\n" + "    \"methane\": 0.80,\n" + "    \"ethane\": 0.05,\n"
        + "    \"water\": 0.15\n" + "  },\n" + "  \"mixingRule\": \"10\"\n" + "}";
  }

  // ========== Process Examples ==========

  /**
   * Returns a minimal stream + separator process.
   *
   * @return JSON string for ProcessRunner.run()
   */
  public static String processSimpleSeparation() {
    return "{\n" + "  \"fluid\": {\n" + "    \"model\": \"SRK\",\n"
        + "    \"temperature\": 298.15,\n" + "    \"pressure\": 50.0,\n"
        + "    \"mixingRule\": \"classic\",\n" + "    \"components\": {\n"
        + "      \"methane\": 0.85,\n" + "      \"ethane\": 0.10,\n" + "      \"propane\": 0.05\n"
        + "    }\n" + "  },\n" + "  \"process\": [\n" + "    {\n" + "      \"type\": \"Stream\",\n"
        + "      \"name\": \"feed\",\n"
        + "      \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}\n" + "    },\n" + "    {\n"
        + "      \"type\": \"Separator\",\n" + "      \"name\": \"HP Sep\",\n"
        + "      \"inlet\": \"feed\"\n" + "    }\n" + "  ]\n" + "}";
  }

  /**
   * Returns a compression process with cooling.
   *
   * @return JSON string for ProcessRunner.run()
   */
  public static String processCompressionWithCooling() {
    return "{\n" + "  \"fluid\": {\n" + "    \"model\": \"SRK\",\n"
        + "    \"temperature\": 298.15,\n" + "    \"pressure\": 10.0,\n"
        + "    \"mixingRule\": \"classic\",\n" + "    \"components\": {\n"
        + "      \"methane\": 0.90,\n" + "      \"ethane\": 0.07,\n" + "      \"propane\": 0.03\n"
        + "    }\n" + "  },\n" + "  \"process\": [\n" + "    {\n" + "      \"type\": \"Stream\",\n"
        + "      \"name\": \"feed\",\n"
        + "      \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}\n" + "    },\n" + "    {\n"
        + "      \"type\": \"Compressor\",\n" + "      \"name\": \"1st Stage\",\n"
        + "      \"inlet\": \"feed\",\n"
        + "      \"properties\": {\"outletPressure\": [30.0, \"bara\"]}\n" + "    },\n" + "    {\n"
        + "      \"type\": \"Cooler\",\n" + "      \"name\": \"Intercooler\",\n"
        + "      \"inlet\": \"1st Stage\",\n"
        + "      \"properties\": {\"outTemperature\": [303.15, \"K\"]}\n" + "    },\n" + "    {\n"
        + "      \"type\": \"Compressor\",\n" + "      \"name\": \"2nd Stage\",\n"
        + "      \"inlet\": \"Intercooler\",\n"
        + "      \"properties\": {\"outletPressure\": [80.0, \"bara\"]}\n" + "    }\n" + "  ]\n"
        + "}";
  }

  // ========== Batch Examples ==========

  /**
   * Returns a batch temperature sweep example with 3 cases.
   *
   * @return JSON string for BatchRunner.run()
   */
  public static String batchTemperatureSweep() {
    return "{\n" + "  \"model\": \"SRK\",\n" + "  \"components\": {\n" + "    \"methane\": 0.85,\n"
        + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05\n" + "  },\n"
        + "  \"mixingRule\": \"classic\",\n" + "  \"flashType\": \"TP\",\n" + "  \"cases\": [\n"
        + "    {\"temperature\": {\"value\": -20.0, \"unit\": \"C\"}, \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}},\n"
        + "    {\"temperature\": {\"value\": 25.0, \"unit\": \"C\"}, \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}},\n"
        + "    {\"temperature\": {\"value\": 80.0, \"unit\": \"C\"}, \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}}\n"
        + "  ]\n" + "}";
  }

  /**
   * Returns a batch pressure sweep example with 4 cases.
   *
   * @return JSON string for BatchRunner.run()
   */
  public static String batchPressureSweep() {
    return "{\n" + "  \"model\": \"PR\",\n" + "  \"components\": {\n" + "    \"methane\": 0.70,\n"
        + "    \"ethane\": 0.15,\n" + "    \"propane\": 0.10,\n" + "    \"n-butane\": 0.05\n"
        + "  },\n" + "  \"mixingRule\": \"classic\",\n" + "  \"flashType\": \"TP\",\n"
        + "  \"cases\": [\n"
        + "    {\"temperature\": {\"value\": 25.0, \"unit\": \"C\"}, \"pressure\": {\"value\": 10.0, \"unit\": \"bara\"}},\n"
        + "    {\"temperature\": {\"value\": 25.0, \"unit\": \"C\"}, \"pressure\": {\"value\": 30.0, \"unit\": \"bara\"}},\n"
        + "    {\"temperature\": {\"value\": 25.0, \"unit\": \"C\"}, \"pressure\": {\"value\": 60.0, \"unit\": \"bara\"}},\n"
        + "    {\"temperature\": {\"value\": 25.0, \"unit\": \"C\"}, \"pressure\": {\"value\": 100.0, \"unit\": \"bara\"}}\n"
        + "  ]\n" + "}";
  }

  // ========== Property Table Examples ==========

  /**
   * Returns a property table temperature sweep example.
   *
   * @return JSON string for PropertyTableRunner.run()
   */
  public static String propertyTableTemperatureSweep() {
    return "{\n" + "  \"model\": \"SRK\",\n" + "  \"components\": {\n" + "    \"methane\": 0.85,\n"
        + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05\n" + "  },\n"
        + "  \"mixingRule\": \"classic\",\n" + "  \"sweep\": \"temperature\",\n"
        + "  \"sweepFrom\": {\"value\": -40.0, \"unit\": \"C\"},\n"
        + "  \"sweepTo\": {\"value\": 80.0, \"unit\": \"C\"},\n" + "  \"points\": 13,\n"
        + "  \"fixedPressure\": {\"value\": 50.0, \"unit\": \"bara\"},\n"
        + "  \"properties\": [\"density\", \"viscosity\", \"Cp\", \"Z\", \"enthalpy\"]\n" + "}";
  }

  /**
   * Returns a property table pressure sweep example.
   *
   * @return JSON string for PropertyTableRunner.run()
   */
  public static String propertyTablePressureSweep() {
    return "{\n" + "  \"model\": \"SRK\",\n" + "  \"components\": {\n" + "    \"methane\": 0.90,\n"
        + "    \"ethane\": 0.07,\n" + "    \"propane\": 0.03\n" + "  },\n"
        + "  \"mixingRule\": \"classic\",\n" + "  \"sweep\": \"pressure\",\n"
        + "  \"sweepFrom\": {\"value\": 10.0, \"unit\": \"bara\"},\n"
        + "  \"sweepTo\": {\"value\": 150.0, \"unit\": \"bara\"},\n" + "  \"points\": 15,\n"
        + "  \"fixedTemperature\": {\"value\": 25.0, \"unit\": \"C\"},\n"
        + "  \"properties\": [\"density\", \"Z\", \"molarMass\", \"soundSpeed\"]\n" + "}";
  }

  // ========== Phase Envelope Examples ==========

  /**
   * Returns a phase envelope example for a natural gas.
   *
   * @return JSON string for PhaseEnvelopeRunner.run()
   */
  public static String phaseEnvelopeNaturalGas() {
    return "{\n" + "  \"model\": \"SRK\",\n" + "  \"components\": {\n" + "    \"methane\": 0.80,\n"
        + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05,\n" + "    \"n-butane\": 0.03,\n"
        + "    \"n-pentane\": 0.02\n" + "  },\n" + "  \"mixingRule\": \"classic\"\n" + "}";
  }

  // ========== Validation Examples ==========

  /**
   * Returns a flash input with known validation errors (unknown component, invalid model).
   *
   * @return JSON string for Validator.validate()
   */
  public static String validationErrorFlash() {
    return "{\n" + "  \"model\": \"UNKNOWN_EOS\",\n"
        + "  \"temperature\": {\"value\": 25.0, \"unit\": \"C\"},\n"
        + "  \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"},\n" + "  \"components\": {\n"
        + "    \"methan\": 0.90,\n" + "    \"ethane\": 0.10\n" + "  }\n" + "}";
  }

  // ========== Catalog Metadata ==========

  /**
   * Returns a list of all available example categories.
   *
   * @return unmodifiable list of category names
   */
  public static List<String> getCategories() {
    return Collections.unmodifiableList(Arrays.asList("flash", "process", "validation", "batch",
        "property-table", "phase-envelope"));
  }

  /**
   * Returns the example names within a category.
   *
   * @param category the category name
   * @return list of example names in that category
   */
  public static List<String> getExampleNames(String category) {
    if ("flash".equals(category)) {
      return Arrays.asList("tp-simple-gas", "tp-two-phase", "dew-point-t", "bubble-point-p",
          "cpa-with-water");
    } else if ("process".equals(category)) {
      return Arrays.asList("simple-separation", "compression-with-cooling");
    } else if ("validation".equals(category)) {
      return Arrays.asList("error-flash");
    } else if ("batch".equals(category)) {
      return Arrays.asList("temperature-sweep", "pressure-sweep");
    } else if ("property-table".equals(category)) {
      return Arrays.asList("temperature-sweep", "pressure-sweep");
    } else if ("phase-envelope".equals(category)) {
      return Arrays.asList("natural-gas");
    }
    return Collections.emptyList();
  }

  /**
   * Returns an example JSON by category and name.
   *
   * @param category the category (flash, process, validation)
   * @param name the example name within that category
   * @return the example JSON string, or null if not found
   */
  public static String getExample(String category, String name) {
    if ("flash".equals(category)) {
      if ("tp-simple-gas".equals(name)) {
        return flashTPSimpleGas();
      }
      if ("tp-two-phase".equals(name)) {
        return flashTPTwoPhase();
      }
      if ("dew-point-t".equals(name)) {
        return flashDewPointT();
      }
      if ("bubble-point-p".equals(name)) {
        return flashBubblePointP();
      }
      if ("cpa-with-water".equals(name)) {
        return flashCPAWithWater();
      }
    } else if ("process".equals(category)) {
      if ("simple-separation".equals(name)) {
        return processSimpleSeparation();
      }
      if ("compression-with-cooling".equals(name)) {
        return processCompressionWithCooling();
      }
    } else if ("validation".equals(category)) {
      if ("error-flash".equals(name)) {
        return validationErrorFlash();
      }
    } else if ("batch".equals(category)) {
      if ("temperature-sweep".equals(name)) {
        return batchTemperatureSweep();
      }
      if ("pressure-sweep".equals(name)) {
        return batchPressureSweep();
      }
    } else if ("property-table".equals(category)) {
      if ("temperature-sweep".equals(name)) {
        return propertyTableTemperatureSweep();
      }
      if ("pressure-sweep".equals(name)) {
        return propertyTablePressureSweep();
      }
    } else if ("phase-envelope".equals(category)) {
      if ("natural-gas".equals(name)) {
        return phaseEnvelopeNaturalGas();
      }
    }
    return null;
  }

  /**
   * Returns a full catalog listing with all categories, names, and descriptions as JSON.
   *
   * @return JSON string listing all available examples
   */
  public static String getCatalogJson() {
    Map<String, Object> catalog = new LinkedHashMap<String, Object>();

    // Flash examples
    Map<String, String> flashExamples = new LinkedHashMap<String, String>();
    flashExamples.put("tp-simple-gas", "TP flash of a simple 3-component natural gas");
    flashExamples.put("tp-two-phase", "TP flash at conditions producing two phases (CH4/C3)");
    flashExamples.put("dew-point-t", "Dew point temperature calculation for a 5-component gas");
    flashExamples.put("bubble-point-p", "Bubble point pressure for a CH4/n-C7 mixture using PR");
    flashExamples.put("cpa-with-water", "CPA model flash with water for hydrate-forming systems");
    catalog.put("flash", flashExamples);

    // Process examples
    Map<String, String> processExamples = new LinkedHashMap<String, String>();
    processExamples.put("simple-separation", "Minimal feed stream + HP separator process");
    processExamples.put("compression-with-cooling", "Two-stage compression with intercooling");
    catalog.put("process", processExamples);

    // Validation examples
    Map<String, String> validationExamples = new LinkedHashMap<String, String>();
    validationExamples.put("error-flash",
        "Flash input with known errors (unknown model, misspelled component)");
    catalog.put("validation", validationExamples);

    // Batch examples
    Map<String, String> batchExamples = new LinkedHashMap<String, String>();
    batchExamples.put("temperature-sweep",
        "Batch of 3 TP flashes at different temperatures (SRK, 3-component gas)");
    batchExamples.put("pressure-sweep",
        "Batch of 4 TP flashes at different pressures (PR, 4-component gas)");
    catalog.put("batch", batchExamples);

    // Property table examples
    Map<String, String> propTableExamples = new LinkedHashMap<String, String>();
    propTableExamples.put("temperature-sweep",
        "Temperature sweep from -40 to 80 C at 50 bara (density, viscosity, Cp, Z, enthalpy)");
    propTableExamples.put("pressure-sweep",
        "Pressure sweep from 10 to 150 bara at 25 C (density, Z, molarMass, soundSpeed)");
    catalog.put("property-table", propTableExamples);

    // Phase envelope examples
    Map<String, String> envelopeExamples = new LinkedHashMap<String, String>();
    envelopeExamples.put("natural-gas", "Phase envelope for a 5-component natural gas (SRK)");
    catalog.put("phase-envelope", envelopeExamples);

    return GSON.toJson(catalog);
  }
}
