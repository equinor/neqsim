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

  // ========== PVT Examples ==========

  /**
   * Returns a CME experiment example.
   *
   * @return JSON string for PVTRunner.run()
   */
  public static String pvtCME() {
    return "{\n" + "  \"model\": \"SRK\",\n" + "  \"temperature_C\": 100.0,\n"
        + "  \"pressure_bara\": 300.0,\n" + "  \"components\": {\n" + "    \"methane\": 0.70,\n"
        + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05,\n" + "    \"n-heptane\": 0.15\n"
        + "  },\n" + "  \"experiment\": \"CME\",\n" + "  \"experimentConfig\": {\n"
        + "    \"pressures_bara\": [300, 250, 200, 150, 100, 50]\n" + "  }\n" + "}";
  }

  /**
   * Returns a saturation pressure experiment example.
   *
   * @return JSON string for PVTRunner.run()
   */
  public static String pvtSaturationPressure() {
    return "{\n" + "  \"model\": \"PR\",\n" + "  \"temperature_C\": 100.0,\n"
        + "  \"pressure_bara\": 200.0,\n" + "  \"components\": {\n" + "    \"methane\": 0.70,\n"
        + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05,\n" + "    \"n-heptane\": 0.15\n"
        + "  },\n" + "  \"experiment\": \"saturationPressure\"\n" + "}";
  }

  // ========== Flow Assurance Examples ==========

  /**
   * Returns a hydrate risk map analysis example.
   *
   * @return JSON string for FlowAssuranceRunner.run()
   */
  public static String flowAssuranceHydrate() {
    return "{\n" + "  \"model\": \"CPA\",\n" + "  \"temperature_C\": 20.0,\n"
        + "  \"pressure_bara\": 100.0,\n" + "  \"components\": {\n" + "    \"methane\": 0.80,\n"
        + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05,\n" + "    \"water\": 0.05\n"
        + "  },\n" + "  \"analysis\": \"hydrateRiskMap\"\n" + "}";
  }

  // ========== Standards Examples ==========

  /**
   * Returns an ISO 6976 standard calculation example.
   *
   * @return JSON string for StandardsRunner.run()
   */
  public static String standardISO6976() {
    return "{\n" + "  \"model\": \"SRK\",\n" + "  \"temperature_C\": 15.0,\n"
        + "  \"pressure_bara\": 1.01325,\n" + "  \"components\": {\n" + "    \"methane\": 0.90,\n"
        + "    \"ethane\": 0.05,\n" + "    \"propane\": 0.03,\n" + "    \"nitrogen\": 0.01,\n"
        + "    \"CO2\": 0.01\n" + "  },\n" + "  \"standard\": \"ISO6976\"\n" + "}";
  }

  // ========== Pipeline Examples ==========

  /**
   * Returns a pipeline simulation example.
   *
   * @return JSON string for PipelineRunner.run()
   */
  public static String pipelineMultiphase() {
    return "{\n" + "  \"model\": \"SRK\",\n" + "  \"temperature_C\": 40.0,\n"
        + "  \"pressure_bara\": 80.0,\n" + "  \"components\": {\n" + "    \"methane\": 0.85,\n"
        + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05\n" + "  },\n"
        + "  \"flowRate\": {\"value\": 50000.0, \"unit\": \"kg/hr\"},\n" + "  \"pipe\": {\n"
        + "    \"diameter_m\": 0.254,\n" + "    \"length_m\": 50000.0,\n"
        + "    \"elevation_m\": 0.0,\n" + "    \"roughness_m\": 0.00005,\n"
        + "    \"numberOfIncrements\": 20\n" + "  }\n" + "}";
  }

  // ========== Reservoir Examples ==========

  /**
   * Returns a simple reservoir depletion example.
   *
   * @return JSON string for ReservoirRunner.run()
   */
  public static String reservoirDepletion() {
    return "{\n" + "  \"model\": \"SRK\",\n" + "  \"reservoirTemperature_C\": 100.0,\n"
        + "  \"reservoirPressure_bara\": 200.0,\n" + "  \"components\": {\n"
        + "    \"methane\": 0.85,\n" + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05\n"
        + "  },\n" + "  \"gasVolume_Sm3\": 1.0e9,\n" + "  \"oilVolume_Sm3\": 0.0,\n"
        + "  \"waterVolume_Sm3\": 0.0,\n" + "  \"producers\": [\n"
        + "    {\"name\": \"Well-1\", \"flowRate\": {\"value\": 1.0, \"unit\": \"MSm3/day\"}}\n"
        + "  ],\n" + "  \"simulationYears\": 20,\n" + "  \"timeStepDays\": 30\n" + "}";
  }

  // ========== Field Economics Examples ==========

  /**
   * Returns a Norwegian NCS cash flow example.
   *
   * @return JSON string for FieldDevelopmentRunner.run()
   */
  public static String economicsNorwegianNCS() {
    return "{\n" + "  \"mode\": \"cashflow\",\n" + "  \"country\": \"NO\",\n"
        + "  \"capex\": {\"totalMusd\": 2000.0, \"year\": 2025},\n"
        + "  \"opex\": {\"percentOfCapex\": 0.04},\n" + "  \"oilPrice_usdPerBbl\": 75.0,\n"
        + "  \"gasPrice_usdPerSm3\": 0.25,\n" + "  \"production\": {\n"
        + "    \"oil\": {\"2027\": 15000000, \"2028\": 13500000, "
        + "\"2029\": 12150000, \"2030\": 10935000},\n"
        + "    \"gas\": {\"2027\": 5.0e8, \"2028\": 4.5e8, \"2029\": 4.0e8, \"2030\": 3.6e8}\n"
        + "  },\n" + "  \"discountRate\": 0.08,\n" + "  \"calculateBreakeven\": true\n" + "}";
  }

  /**
   * Returns a production profile generation example.
   *
   * @return JSON string for FieldDevelopmentRunner.run()
   */
  public static String economicsDeclineCurve() {
    return "{\n" + "  \"mode\": \"productionProfile\",\n" + "  \"declineType\": \"EXPONENTIAL\",\n"
        + "  \"initialRate_bblPerDay\": 15000.0,\n" + "  \"annualDeclineRate\": 0.10,\n"
        + "  \"startYear\": 2027,\n" + "  \"totalYears\": 20,\n" + "  \"plateauYears\": 3,\n"
        + "  \"economicLimit_bblPerDay\": 500.0\n" + "}";
  }

  // ========== Bioprocess Examples ==========

  /**
   * Returns an anaerobic digestion example.
   *
   * @return JSON string for BioprocessRunner.run()
   */
  public static String bioprocessAnaerobicDigestion() {
    return "{\n" + "  \"reactorType\": \"anaerobicDigester\",\n"
        + "  \"substrateType\": \"FOOD_WASTE\",\n" + "  \"feedRate_kgPerHr\": 2000.0,\n"
        + "  \"totalSolidsFraction\": 0.25,\n" + "  \"temperature_C\": 37.0,\n"
        + "  \"specificMethaneYield_Nm3PerKgVS\": 0.40,\n" + "  \"volume_m3\": 500.0\n" + "}";
  }

  /**
   * Returns a biomass gasification example.
   *
   * @return JSON string for BioprocessRunner.run()
   */
  public static String bioprocessGasification() {
    return "{\n" + "  \"reactorType\": \"gasifier\",\n" + "  \"gasifierType\": \"DOWNDRAFT\",\n"
        + "  \"agentType\": \"AIR\",\n" + "  \"feedRate_kgPerHr\": 1000.0,\n"
        + "  \"temperature_C\": 850.0,\n" + "  \"equivalenceRatio\": 0.3,\n" + "  \"biomass\": {\n"
        + "    \"carbon\": 50.0,\n" + "    \"hydrogen\": 6.0,\n" + "    \"oxygen\": 42.0,\n"
        + "    \"nitrogen\": 0.5,\n" + "    \"sulfur\": 0.1,\n" + "    \"ash\": 1.4,\n"
        + "    \"moisture\": 0.15\n" + "  }\n" + "}";
  }

  // ========== Session Examples ==========

  /**
   * Returns a session creation example.
   *
   * @return JSON string for SessionRunner.run()
   */
  public static String sessionCreate() {
    return "{\n" + "  \"action\": \"create\",\n" + "  \"fluid\": {\n" + "    \"model\": \"SRK\",\n"
        + "    \"temperature\": 298.15,\n" + "    \"pressure\": 50.0,\n"
        + "    \"mixingRule\": \"classic\",\n" + "    \"components\": {\n"
        + "      \"methane\": 0.85,\n" + "      \"ethane\": 0.10,\n" + "      \"propane\": 0.05\n"
        + "    }\n" + "  }\n" + "}";
  }

  /**
   * Returns a session add-equipment example (requires a valid sessionId).
   *
   * @return JSON string for SessionRunner.run()
   */
  public static String sessionAddEquipment() {
    return "{\n" + "  \"action\": \"addEquipment\",\n" + "  \"sessionId\": \"<SESSION_ID>\",\n"
        + "  \"equipment\": {\n" + "    \"type\": \"Separator\",\n" + "    \"name\": \"HP Sep\",\n"
        + "    \"inlet\": \"feed\"\n" + "  }\n" + "}";
  }

  // ========== Visualization Examples ==========

  /**
   * Returns a phase envelope visualization example.
   *
   * @return JSON string for VisualizationRunner.run()
   */
  public static String visualizationPhaseEnvelope() {
    return "{\n" + "  \"type\": \"phaseEnvelope\",\n" + "  \"model\": \"SRK\",\n"
        + "  \"components\": {\n" + "    \"methane\": 0.80,\n" + "    \"ethane\": 0.10,\n"
        + "    \"propane\": 0.05,\n" + "    \"n-butane\": 0.03,\n" + "    \"n-pentane\": 0.02\n"
        + "  }\n" + "}";
  }

  /**
   * Returns a bar chart visualization example.
   *
   * @return JSON string for VisualizationRunner.run()
   */
  public static String visualizationBarChart() {
    return "{\n" + "  \"type\": \"barChart\",\n" + "  \"title\": \"Phase Fractions\",\n"
        + "  \"xLabel\": \"Phase\",\n" + "  \"yLabel\": \"Mole Fraction\",\n"
        + "  \"categories\": [\"Gas\", \"Liquid\"],\n" + "  \"values\": [0.72, 0.28]\n" + "}";
  }

  /**
   * Returns a flowsheet diagram visualization example.
   *
   * @return JSON string for VisualizationRunner.run()
   */
  public static String visualizationFlowsheet() {
    return "{\n" + "  \"type\": \"flowsheet\",\n" + "  \"equipment\": [\n" + "    {\n"
        + "      \"name\": \"Feed\",\n" + "      \"type\": \"Stream\"\n" + "    },\n" + "    {\n"
        + "      \"name\": \"HP Sep\",\n" + "      \"type\": \"Separator\",\n"
        + "      \"inlet\": \"Feed\"\n" + "    },\n" + "    {\n"
        + "      \"name\": \"Compressor\",\n" + "      \"type\": \"Compressor\",\n"
        + "      \"inlet\": \"HP Sep\"\n" + "    }\n" + "  ]\n" + "}";
  }

  // ========== Equipment Sizing Examples ==========

  /**
   * Returns a separator sizing example.
   *
   * @return JSON string for EquipmentSizingRunner.run()
   */
  public static String sizingSeparator() {
    return "{\n" + "  \"equipmentType\": \"separator\",\n" + "  \"model\": \"SRK\",\n"
        + "  \"temperature_C\": 40.0,\n" + "  \"pressure_bara\": 50.0,\n" + "  \"components\": {\n"
        + "    \"methane\": 0.80,\n" + "    \"ethane\": 0.10,\n" + "    \"propane\": 0.05,\n"
        + "    \"n-heptane\": 0.05\n" + "  },\n"
        + "  \"flowRate\": {\"value\": 50000.0, \"unit\": \"kg/hr\"},\n"
        + "  \"orientation\": \"horizontal\",\n" + "  \"liquidRetentionTime_min\": 5.0\n" + "}";
  }

  /**
   * Returns a compressor sizing example.
   *
   * @return JSON string for EquipmentSizingRunner.run()
   */
  public static String sizingCompressor() {
    return "{\n" + "  \"equipmentType\": \"compressor\",\n" + "  \"model\": \"SRK\",\n"
        + "  \"temperature_C\": 30.0,\n" + "  \"pressure_bara\": 10.0,\n" + "  \"components\": {\n"
        + "    \"methane\": 0.90,\n" + "    \"ethane\": 0.07,\n" + "    \"propane\": 0.03\n"
        + "  },\n" + "  \"flowRate\": {\"value\": 20000.0, \"unit\": \"kg/hr\"},\n"
        + "  \"outletPressure_bara\": 80.0,\n" + "  \"polytropicEfficiency\": 0.75\n" + "}";
  }

  // ========== Process Comparison Examples ==========

  /**
   * Returns a process comparison example with two cases.
   *
   * @return JSON string for ProcessComparisonRunner.run()
   */
  public static String comparisonTwoCases() {
    return "{\n" + "  \"cases\": [\n" + "    {\n" + "      \"name\": \"Low Pressure\",\n"
        + "      \"fluid\": {\n" + "        \"model\": \"SRK\",\n"
        + "        \"temperature\": 298.15,\n" + "        \"pressure\": 30.0,\n"
        + "        \"mixingRule\": \"classic\",\n" + "        \"components\": {\n"
        + "          \"methane\": 0.85,\n" + "          \"ethane\": 0.10,\n"
        + "          \"propane\": 0.05\n" + "        }\n" + "      },\n" + "      \"process\": [\n"
        + "        {\"type\": \"Stream\", \"name\": \"feed\", "
        + "\"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},\n"
        + "        {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}\n"
        + "      ]\n" + "    },\n" + "    {\n" + "      \"name\": \"High Pressure\",\n"
        + "      \"fluid\": {\n" + "        \"model\": \"SRK\",\n"
        + "        \"temperature\": 298.15,\n" + "        \"pressure\": 80.0,\n"
        + "        \"mixingRule\": \"classic\",\n" + "        \"components\": {\n"
        + "          \"methane\": 0.85,\n" + "          \"ethane\": 0.10,\n"
        + "          \"propane\": 0.05\n" + "        }\n" + "      },\n" + "      \"process\": [\n"
        + "        {\"type\": \"Stream\", \"name\": \"feed\", "
        + "\"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},\n"
        + "        {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}\n"
        + "      ]\n" + "    }\n" + "  ]\n" + "}";
  }

  // ========== Catalog Metadata ==========

  /**
   * Returns a list of all available example categories.
   *
   * @return unmodifiable list of category names
   */
  public static List<String> getCategories() {
    return Collections.unmodifiableList(
        Arrays.asList("flash", "process", "validation", "batch", "property-table", "phase-envelope",
            "pvt", "flow-assurance", "standards", "pipeline", "reservoir", "economics",
            "bioprocess", "session", "visualization", "equipment-sizing", "comparison"));
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
    } else if ("pvt".equals(category)) {
      return Arrays.asList("cme-oil", "saturation-pressure");
    } else if ("flow-assurance".equals(category)) {
      return Arrays.asList("hydrate-risk");
    } else if ("standards".equals(category)) {
      return Arrays.asList("iso6976-gas");
    } else if ("pipeline".equals(category)) {
      return Arrays.asList("multiphase-flow");
    } else if ("reservoir".equals(category)) {
      return Arrays.asList("gas-depletion");
    } else if ("economics".equals(category)) {
      return Arrays.asList("norwegian-ncs", "decline-curve");
    } else if ("bioprocess".equals(category)) {
      return Arrays.asList("anaerobic-digestion", "gasification");
    } else if ("session".equals(category)) {
      return Arrays.asList("create", "add-equipment");
    } else if ("visualization".equals(category)) {
      return Arrays.asList("phase-envelope", "bar-chart", "flowsheet");
    } else if ("equipment-sizing".equals(category)) {
      return Arrays.asList("separator", "compressor");
    } else if ("comparison".equals(category)) {
      return Arrays.asList("two-cases");
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
    } else if ("pvt".equals(category)) {
      if ("cme-oil".equals(name)) {
        return pvtCME();
      }
      if ("saturation-pressure".equals(name)) {
        return pvtSaturationPressure();
      }
    } else if ("flow-assurance".equals(category)) {
      if ("hydrate-risk".equals(name)) {
        return flowAssuranceHydrate();
      }
    } else if ("standards".equals(category)) {
      if ("iso6976-gas".equals(name)) {
        return standardISO6976();
      }
    } else if ("pipeline".equals(category)) {
      if ("multiphase-flow".equals(name)) {
        return pipelineMultiphase();
      }
    } else if ("reservoir".equals(category)) {
      if ("gas-depletion".equals(name)) {
        return reservoirDepletion();
      }
    } else if ("economics".equals(category)) {
      if ("norwegian-ncs".equals(name)) {
        return economicsNorwegianNCS();
      }
      if ("decline-curve".equals(name)) {
        return economicsDeclineCurve();
      }
    } else if ("bioprocess".equals(category)) {
      if ("anaerobic-digestion".equals(name)) {
        return bioprocessAnaerobicDigestion();
      }
      if ("gasification".equals(name)) {
        return bioprocessGasification();
      }
    } else if ("session".equals(category)) {
      if ("create".equals(name)) {
        return sessionCreate();
      }
      if ("add-equipment".equals(name)) {
        return sessionAddEquipment();
      }
    } else if ("visualization".equals(category)) {
      if ("phase-envelope".equals(name)) {
        return visualizationPhaseEnvelope();
      }
      if ("bar-chart".equals(name)) {
        return visualizationBarChart();
      }
      if ("flowsheet".equals(name)) {
        return visualizationFlowsheet();
      }
    } else if ("equipment-sizing".equals(category)) {
      if ("separator".equals(name)) {
        return sizingSeparator();
      }
      if ("compressor".equals(name)) {
        return sizingCompressor();
      }
    } else if ("comparison".equals(category)) {
      if ("two-cases".equals(name)) {
        return comparisonTwoCases();
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

    // PVT examples
    Map<String, String> pvtExamples = new LinkedHashMap<String, String>();
    pvtExamples.put("cme-oil", "Constant Mass Expansion on a 4-component oil at 100C");
    pvtExamples.put("saturation-pressure", "Saturation pressure calculation using PR EOS");
    catalog.put("pvt", pvtExamples);

    // Flow assurance examples
    Map<String, String> faExamples = new LinkedHashMap<String, String>();
    faExamples.put("hydrate-risk", "Hydrate risk map for wet gas at 100 bara using CPA");
    catalog.put("flow-assurance", faExamples);

    // Standards examples
    Map<String, String> stdExamples = new LinkedHashMap<String, String>();
    stdExamples.put("iso6976-gas", "ISO 6976 heating value, Wobbe index, density for natural gas");
    catalog.put("standards", stdExamples);

    // Pipeline examples
    Map<String, String> pipeExamples = new LinkedHashMap<String, String>();
    pipeExamples.put("multiphase-flow",
        "Beggs & Brill multiphase pipeline flow for 50 km gas line");
    catalog.put("pipeline", pipeExamples);

    // Reservoir examples
    Map<String, String> resExamples = new LinkedHashMap<String, String>();
    resExamples.put("gas-depletion",
        "Simple gas reservoir depletion (1 BCM) with a single producer over 20 years");
    catalog.put("reservoir", resExamples);

    // Economics examples
    Map<String, String> econExamples = new LinkedHashMap<String, String>();
    econExamples.put("norwegian-ncs", "Norwegian NCS cash flow with petroleum tax and breakeven");
    econExamples.put("decline-curve", "Exponential decline production profile with 3-year plateau");
    catalog.put("economics", econExamples);

    // Bioprocess examples
    Map<String, String> bioExamples = new LinkedHashMap<String, String>();
    bioExamples.put("anaerobic-digestion", "Anaerobic digestion of food waste at 37C mesophilic");
    bioExamples.put("gasification", "Downdraft biomass gasification with air agent at 850C");
    catalog.put("bioprocess", bioExamples);

    // Session examples
    Map<String, String> sessionExamples = new LinkedHashMap<String, String>();
    sessionExamples.put("create", "Create a new simulation session with a 3-component gas fluid");
    sessionExamples.put("add-equipment",
        "Add a separator to an existing session (requires sessionId)");
    catalog.put("session", sessionExamples);

    // Visualization examples
    Map<String, String> vizExamples = new LinkedHashMap<String, String>();
    vizExamples.put("phase-envelope", "Phase envelope SVG for a 5-component natural gas");
    vizExamples.put("bar-chart", "Bar chart SVG comparing phase fractions");
    vizExamples.put("flowsheet", "Mermaid flowsheet diagram for a simple process");
    catalog.put("visualization", vizExamples);

    // Equipment sizing examples
    Map<String, String> sizingExamples = new LinkedHashMap<String, String>();
    sizingExamples.put("separator", "Horizontal separator sizing for a 4-component gas/liquid");
    sizingExamples.put("compressor", "Centrifugal compressor sizing from 10 to 80 bara");
    catalog.put("equipment-sizing", sizingExamples);

    // Process comparison examples
    Map<String, String> compExamples = new LinkedHashMap<String, String>();
    compExamples.put("two-cases", "Compare separation at 30 bara vs 80 bara side by side");
    catalog.put("comparison", compExamples);

    return GSON.toJson(catalog);
  }
}
