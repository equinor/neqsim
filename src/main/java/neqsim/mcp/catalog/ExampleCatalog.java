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

  /**
   * Returns a water-hammer valve-closure example using route and tagreader-style fields.
   *
   * @return JSON string for WaterHammerRunner.run()
   */
  public static String waterHammerValveClosure() {
    return "{\n" + "  \"studyName\": \"Synthetic ESD valve closure screening\",\n"
        + "  \"model\": \"SRK\",\n" + "  \"temperature_C\": 20.0,\n"
        + "  \"pressure_bara\": 45.0,\n" + "  \"components\": {\"water\": 1.0},\n"
        + "  \"flowRate\": {\"value\": 120000.0, \"unit\": \"kg/hr\"},\n"
        + "  \"designPressure_bara\": 95.0,\n" + "  \"pipe\": {\n" + "    \"length_m\": 1200.0,\n"
        + "    \"diameter_m\": 0.2032,\n" + "    \"wallThickness_m\": 0.0127,\n"
        + "    \"roughness_m\": 4.6e-5,\n" + "    \"elevation_m\": 8.0,\n"
        + "    \"numberOfNodes\": 80\n" + "  },\n" + "  \"fieldData\": {\n"
        + "    \"inletPressure_bara\": 46.0,\n" + "    \"inletTemperature_C\": 19.0,\n"
        + "    \"flowRate_kg_hr\": 118000.0,\n" + "    \"valveOpening\": 1.0\n" + "  },\n"
        + "  \"eventSchedule\": [\n" + "    {\"type\": \"VALVE_CLOSURE\", \"startTime_s\": 0.10, "
        + "\"duration_s\": 0.15, \"startOpening\": 1.0, \"endOpening\": 0.0}\n" + "  ],\n"
        + "  \"simulationTime_s\": 4.0,\n"
        + "  \"sourceReferences\": [\"synthetic STID line-list row\", "
        + "\"synthetic tagreader event window\"]\n" + "}";
  }

  // ========== Root-Cause Analysis Examples ==========

  /**
   * Returns a compressor high-vibration root-cause analysis example.
   *
   * @return JSON string for RootCauseRunner.run()
   */
  public static String rootCauseCompressorHighVibration() {
    String processJson = processCompressionWithCooling().replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n");
    return "{\n" + "  \"equipmentName\": \"1st Stage\",\n" + "  \"symptom\": \"HIGH_VIBRATION\",\n"
        + "  \"processJson\": \"" + processJson + "\",\n" + "  \"simulationEnabled\": true,\n"
        + "  \"historianCsv\": \"time,vibration,bearingTemperature,lubeOilPressure,"
        + "scrubberLevel\\n0,2.1,68.0,4.6,42.0\\n10,3.0,71.0,4.2,55.0\\n"
        + "20,5.6,79.0,3.4,82.0\",\n" + "  \"designLimits\": {\n"
        + "    \"vibration\": [0.0, 4.5],\n" + "    \"bearingTemperature\": [0.0, 90.0],\n"
        + "    \"lubeOilPressure\": [3.5, 8.0],\n" + "    \"scrubberLevel\": [15.0, 75.0]\n"
        + "  },\n" + "  \"stidData\": {\n" + "    \"vibrationDesignLimit\": \"4.5\",\n"
        + "    \"normalBearingTemperature\": \"70.0\",\n"
        + "    \"sourceReference\": \"synthetic compressor datasheet and tagreader trend\"\n"
        + "  }\n" + "}";
  }

  /**
   * Returns a separator liquid-carryover root-cause analysis example with rich tagreader historian
   * data showing the complete integration between process simulation, STID design data, and
   * historian time-series from plant data systems (e.g. OSIsoft PI / Aspen IP.21).
   *
   * @return JSON string for RootCauseRunner.run()
   */
  public static String rootCauseSeparatorLiquidCarryover() {
    String processJson = processSimpleSeparation().replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n");
    return "{\n" + "  \"equipmentName\": \"HP Sep\",\n"
        + "  \"symptom\": \"LIQUID_CARRYOVER\",\n" + "  \"processJson\": \"" + processJson
        + "\",\n" + "  \"simulationEnabled\": true,\n"
        + "  \"historianCsv\": \"time,demisterDp,liquidLevel,gasOutFlow,"
        + "feedFlow,inletTemperature\\n"
        + "0,0.8,52.0,9200.0,10000.0,25.0\\n"
        + "3600,1.2,58.0,9100.0,10500.0,25.5\\n"
        + "7200,1.8,63.0,8800.0,11200.0,26.0\\n"
        + "10800,2.5,68.0,8500.0,12000.0,26.5\\n"
        + "14400,3.2,72.0,8200.0,12500.0,27.0\",\n"
        + "  \"designLimits\": {\n"
        + "    \"demisterDp\": [0.0, 2.5],\n"
        + "    \"liquidLevel\": [20.0, 70.0],\n"
        + "    \"gasOutFlow\": [0.0, 12000.0],\n"
        + "    \"feedFlow\": [0.0, 11000.0]\n" + "  },\n"
        + "  \"stidData\": {\n"
        + "    \"designFeedRate_kg_hr\": \"10000\",\n"
        + "    \"designLiquidLevel_pct\": \"50\",\n"
        + "    \"demisterType\": \"wire_mesh\",\n"
        + "    \"demisterMaxDp_mbar\": \"2.5\",\n"
        + "    \"lastInspectionDate\": \"2024-03-15\",\n"
        + "    \"tagreaderSource\": \"PI Web API tag mapping: "
        + "LT-2001.PV, PDT-2001.PV, FT-2002.PV, FT-2001.PV, TT-2001.PV\",\n"
        + "    \"sourceReference\": \"STID datasheet DS-V-2001 rev.C "
        + "and tagreader trend 01.06.2025-01.06.2025\"\n" + "  }\n" + "}";
  }

  /**
   * Returns a heat exchanger fouling root-cause analysis example integrating process simulation,
   * tagreader historian trend, design limits from STID datasheet, and equipment design data from
   * vendor technical documents.
   *
   * @return JSON string for RootCauseRunner.run()
   */
  public static String rootCauseHeatExchangerFouling() {
    return "{\n" + "  \"equipmentName\": \"Intercooler\",\n" + "  \"symptom\": \"FOULING\",\n"
        + "  \"processJson\": \""
        + processCompressionWithCooling().replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n")
        + "\",\n" + "  \"simulationEnabled\": true,\n"
        + "  \"historianCsv\": \"time,outletTemp,inletTemp,coolantFlow,"
        + "shellDp,tubeOutTemp\\n"
        + "0,35.0,120.0,80000.0,0.35,32.0\\n"
        + "86400,37.0,120.5,80000.0,0.40,33.0\\n"
        + "172800,39.5,121.0,79500.0,0.48,34.5\\n"
        + "259200,42.0,120.0,79000.0,0.55,36.0\\n"
        + "345600,45.0,120.5,78500.0,0.63,38.0\\n"
        + "432000,48.5,121.0,78000.0,0.72,40.0\",\n"
        + "  \"designLimits\": {\n"
        + "    \"outletTemp\": [0.0, 40.0],\n"
        + "    \"shellDp\": [0.0, 0.5],\n"
        + "    \"coolantFlow\": [70000.0, 90000.0]\n" + "  },\n"
        + "  \"stidData\": {\n"
        + "    \"designDuty_kW\": \"2500\",\n"
        + "    \"designUA_W_K\": \"45000\",\n"
        + "    \"designOutletTemp_C\": \"35\",\n"
        + "    \"hxType\": \"shell-and-tube\",\n"
        + "    \"temaClass\": \"R\",\n"
        + "    \"lastCleaningDate\": \"2024-01-20\",\n"
        + "    \"designFoulingResistance_m2K_W\": \"0.00035\",\n"
        + "    \"tagreaderSource\": \"Aspen IP.21 tag mapping: "
        + "TT-3501.PV, TT-3502.PV, FT-3501.PV, PDT-3501.PV, TT-3503.PV\",\n"
        + "    \"sourceReference\": \"STID datasheet DS-E-3501 rev.B "
        + "and vendor technical document VD-E-3501\"\n" + "  }\n" + "}";
  }

  // ========== Materials Review Examples ==========

  /**
   * Returns a materials review example based on normalized STID/material-register records.
   *
   * @return JSON string for MaterialsReviewRunner.run()
   */
  public static String materialsReviewStidRegister() {
    return "{\n" + "  \"projectName\": \"Synthetic materials review\",\n"
        + "  \"designLifeYears\": 25,\n" + "  \"materialsRegister\": [\n" + "    {\n"
        + "      \"tag\": \"DEMO-LINE-001\",\n" + "      \"equipmentType\": \"Pipeline\",\n"
        + "      \"existingMaterial\": \"Carbon Steel API 5L X65\",\n"
        + "      \"sourceReferences\": [\"synthetic STID line-list row 1\"],\n"
        + "      \"service\": {\n" + "        \"temperature_C\": 85.0,\n"
        + "        \"pressure_bara\": 95.0,\n" + "        \"co2_mole_fraction\": 0.04,\n"
        + "        \"h2s_mole_fraction\": 0.0008,\n" + "        \"free_water\": true,\n"
        + "        \"chloride_mg_per_l\": 55000.0,\n" + "        \"pH\": 5.2,\n"
        + "        \"flow_velocity_m_per_s\": 7.5,\n"
        + "        \"nominal_wall_thickness_mm\": 18.0,\n"
        + "        \"current_wall_thickness_mm\": 15.2,\n"
        + "        \"minimum_required_thickness_mm\": 11.0\n" + "      }\n" + "    },\n" + "    {\n"
        + "      \"tag\": \"DEMO-PIPING-002\",\n" + "      \"equipmentType\": \"Topside piping\",\n"
        + "      \"existingMaterial\": \"316L stainless steel\",\n"
        + "      \"sourceReferences\": [\"synthetic piping-class extract\"],\n"
        + "      \"service\": {\n" + "        \"temperature_C\": 95.0,\n"
        + "        \"pressure_bara\": 25.0,\n" + "        \"free_water\": true,\n"
        + "        \"chloride_mg_per_l\": 120000.0,\n" + "        \"dissolved_o2_ppb\": 80.0,\n"
        + "        \"insulated\": true,\n" + "        \"insulation_type\": \"mineral wool\",\n"
        + "        \"coating_age_years\": 14.0,\n" + "        \"marine_environment\": true\n"
        + "      }\n" + "    }\n" + "  ]\n" + "}";
  }

        // ========== Open Drain Review Examples ==========

        /**
         * Returns an open-drain review example based on normalized STID and tagreader evidence.
         *
         * @return JSON string for OpenDrainReviewRunner.run()
         */
        public static String openDrainReviewNorsokS001Stid() {
          return "{\n" + "  \"projectName\": \"Synthetic open drain review\",\n"
          + "  \"defaultLiquidLeakRateKgPerS\": 5.0,\n" + "  \"stidData\": {\n"
          + "    \"openDrainAreas\": [\n" + "      {\n"
          + "        \"areaId\": \"OD-PROCESS-001\",\n"
          + "        \"areaType\": \"process hazardous area\",\n"
          + "        \"drainSystemType\": \"hazardous open drain\",\n"
          + "        \"sourceReferences\": [\"synthetic STID OD-001 rev.A\"],\n"
          + "        \"standards\": [\"NORSOK S-001\", \"NORSOK P-002\", \"ISO 13702\"],\n"
          + "        \"sourceHasFlammableOrHazardousLiquid\": true,\n"
          + "        \"hasOpenDrainMeasures\": true,\n"
          + "        \"drainageCapacityKgPerS\": 14.0,\n"
          + "        \"fireWaterCapacityKgPerS\": 8.0,\n"
          + "        \"liquidLeakRateKgPerS\": 5.0,\n"
          + "        \"backflowPrevented\": true,\n"
          + "        \"closedOpenDrainInteractionPrevented\": true,\n"
          + "        \"hazardousNonHazardousPhysicallySeparated\": true,\n"
          + "        \"sealDesignedForMaxBackpressure\": true,\n"
          + "        \"ventTerminatedSafe\": true,\n"
          + "        \"openDrainDependsOnUtility\": false,\n"
          + "        \"tagreaderSource\": \"PI export: LS-OD-001.PV, PT-OD-001.PV\",\n"
          + "        \"sumpHighLevelEvents\": 0.0,\n"
          + "        \"observedBackflowEvents\": 0.0\n" + "      }\n" + "    ],\n"
          + "    \"helideckDrains\": [\n" + "      {\n"
          + "        \"areaId\": \"OD-HELIDECK\",\n"
          + "        \"areaType\": \"helideck\",\n"
          + "        \"dedicatedPipeDrainage\": true,\n"
          + "        \"sourceHasFlammableOrHazardousLiquid\": true,\n"
          + "        \"hasOpenDrainMeasures\": true,\n"
          + "        \"drainageCapacityKgPerS\": 6.0,\n"
          + "        \"fireWaterCapacityKgPerS\": 1.0,\n"
          + "        \"liquidLeakRateKgPerS\": 0.5,\n"
          + "        \"backflowPrevented\": true,\n"
          + "        \"closedOpenDrainInteractionPrevented\": true,\n"
          + "        \"hazardousNonHazardousPhysicallySeparated\": true,\n"
          + "        \"sealDesignedForMaxBackpressure\": true,\n"
          + "        \"ventTerminatedSafe\": true\n" + "      }\n" + "    ]\n" + "  }\n"
          + "}";
        }

  // ========== NORSOK S-001 Clause 10 Review Examples ==========

  /**
   * Returns a process safety system review example based on normalized technical documentation and
   * instrument evidence.
   *
   * @return JSON string for NorsokS001Clause10ReviewRunner.run()
   */
  public static String norsokS001Clause10ProcessSafetySystem() {
    return "{\n"
        + "  \"projectName\": \"Synthetic NORSOK S-001 Clause 10 review\",\n"
        + "  \"stidData\": {\n"
        + "    \"processSafetyFunctions\": [\n"
        + commonClause10Item("PSD-1001", "PSD", "V-100", true)
        + ",\n"
        + commonClause10Item("PSV-1001", "PSV", "V-100", false)
        + ",\n"
        + commonClause10Item("PAHH-1001", "ALARM", "V-100", false)
        + ",\n"
        + commonClause10Item("SIF-1001", "SECONDARY_PRESSURE_PROTECTION", "V-100", true)
        + "\n    ]\n"
        + "  }\n"
        + "}";
  }

  /**
   * Builds a common Clause 10 example item.
   *
   * @param functionId function identifier
   * @param functionType function type
   * @param equipmentTag protected equipment tag
   * @param logicEvidence true when logic solver evidence should be included
   * @return JSON object text
   */
  private static String commonClause10Item(String functionId, String functionType,
      String equipmentTag, boolean logicEvidence) {
    StringBuilder json = new StringBuilder();
    json.append("      {\n");
    json.append("        \"functionId\": \"").append(functionId).append("\",\n");
    json.append("        \"functionType\": \"").append(functionType).append("\",\n");
    json.append("        \"equipmentTag\": \"").append(equipmentTag).append("\",\n");
    json.append("        \"sourceReferences\": [\"synthetic C&E rev.A\", \"synthetic SRS rev.B\"],\n");
    json.append("        \"hazidHazopLopaCompleted\": true,\n");
    json.append("        \"srsDefinesRequiredFunctions\": true,\n");
    json.append("        \"sisEsdFgsDesignImplemented\": true,\n");
    json.append("        \"verificationTestingOperationConfirmed\": true,\n");
    json.append("        \"processSafetyRoleDefined\": true,\n");
    json.append("        \"interfacesDefined\": true,\n");
    json.append("        \"protectionLayersDocumented\": true,\n");
    json.append("        \"designBasisDocumented\": true,\n");
    json.append("        \"processSafetyPrinciplesDocumented\": true,\n");
    json.append("        \"bypassManagementDocumented\": true,\n");
    json.append("        \"requiredUtilitiesIdentified\": true,\n");
    json.append("        \"utilityDependent\": true,\n");
    json.append("        \"failSafeOnUtilityLoss\": true,\n");
    json.append("        \"survivabilityRequirementDocumented\": true,\n");
    json.append("        \"requiredSurvivabilityTimeMin\": 30.0,\n");
    json.append("        \"survivabilityTimeMin\": 60.0,\n");
    json.append("        \"tagreaderSource\": \"synthetic instrument status export\",\n");
    json.append("        \"bypassActive\": false,\n");
    json.append("        \"overrideActive\": false,\n");
    json.append("        \"proofTestOverdue\": false,\n");
    json.append("        \"tripDemandFailures\": 0.0");
    if ("PSD".equals(functionType)) {
      json.append(",\n        \"shutdownActionDefined\": true");
      json.append(",\n        \"psdValveFailsSafe\": true");
      json.append(",\n        \"psdValveIsolationAdequate\": true");
      json.append(",\n        \"requiredResponseTimeSeconds\": 30.0");
      json.append(",\n        \"actualResponseTimeSeconds\": 18.0");
      json.append(",\n        \"psdIndependentFromControl\": true");
      json.append(",\n        \"manualShutdownAvailable\": true");
    }
    if ("PSV".equals(functionType)) {
      json.append(",\n        \"psvSizingBasisDocumented\": true");
      json.append(",\n        \"reliefScenarioDocumented\": true");
      json.append(",\n        \"protectedEquipmentDocumented\": true");
      json.append(",\n        \"requiredReliefLoadKgPerS\": 10.0");
      json.append(",\n        \"psvCapacityKgPerS\": 14.0");
    }
    if ("ALARM".equals(functionType)) {
      json.append(",\n        \"alarmActionDefined\": true");
      json.append(",\n        \"alarmSetpointDocumented\": true");
      json.append(",\n        \"operatorResponseTimeSeconds\": 120.0");
      json.append(",\n        \"availableOperatorResponseTimeSeconds\": 300.0");
      json.append(",\n        \"requiredResponseTimeSeconds\": 300.0");
      json.append(",\n        \"actualResponseTimeSeconds\": 120.0");
    }
    if (logicEvidence) {
      json.append(",\n        \"logicSolverCertified\": true");
      json.append(",\n        \"logicSolverIndependent\": true");
      json.append(",\n        \"causeAndEffectTested\": true");
    }
    if ("SECONDARY_PRESSURE_PROTECTION".equals(functionType)) {
      json.append(",\n        \"maximumEventPressureBara\": 120.0");
      json.append(",\n        \"designPressureBara\": 100.0");
      json.append(",\n        \"testPressureBara\": 150.0");
      json.append(",\n        \"demandFrequencyPerYear\": 1.0e-4");
      json.append(",\n        \"reliefLeakageAssessed\": true");
      json.append(",\n        \"reliefLeakageToSafeLocation\": true");
      json.append(",\n        \"proofTestIntervalMonths\": 6.0");
      json.append(",\n        \"requiredResponseTimeSeconds\": 10.0");
      json.append(",\n        \"actualResponseTimeSeconds\": 5.0");
    }
    json.append("\n      }");
    return json.toString();
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
   * Returns a safety-system performance analysis example.
   *
   * @return JSON string for SafetySystemPerformanceRunner.run()
   */
  public static String safetySystemPerformance() {
    return "{\n" + "  \"register\": {\n" + "    \"registerId\": \"BR-SAFETY-SYSTEM-001\",\n"
        + "    \"name\": \"Safety critical systems performance review\",\n"
        + "    \"evidence\": [{\n" + "      \"evidenceId\": \"EV-CE-001\",\n"
        + "      \"documentId\": \"CE-001\",\n"
        + "      \"documentTitle\": \"Cause and effect chart\",\n" + "      \"revision\": \"C\",\n"
        + "      \"section\": \"F&G actions\",\n" + "      \"page\": 4,\n"
        + "      \"sourceReference\": \"FGS-101 voting group\",\n"
        + "      \"excerpt\": \"2oo3 gas detection activates deluge for V-101.\",\n"
        + "      \"confidence\": 0.94\n" + "    }],\n" + "    \"performanceStandards\": [{\n"
        + "      \"id\": \"PS-FGS-101\",\n"
        + "      \"title\": \"Fire and gas detection performance\",\n"
        + "      \"safetyFunction\": \"Detect gas release and activate deluge\",\n"
        + "      \"demandMode\": \"LOW_DEMAND\",\n" + "      \"targetPfd\": 0.001,\n"
        + "      \"requiredAvailability\": 0.99,\n" + "      \"responseTimeSeconds\": 20.0,\n"
        + "      \"acceptanceCriteria\": [\"2oo3 F&G voting shall activate deluge\"],\n"
        + "      \"evidenceRefs\": [\"EV-CE-001\"]\n" + "    }],\n" + "    \"barriers\": [{\n"
        + "      \"id\": \"B-FGS-101\",\n" + "      \"name\": \"F&G detection for V-101\",\n"
        + "      \"description\": \"Gas detectors activate deluge and ESD\",\n"
        + "      \"type\": \"MITIGATION\",\n" + "      \"status\": \"AVAILABLE\",\n"
        + "      \"pfd\": 0.0008,\n" + "      \"performanceStandardId\": \"PS-FGS-101\",\n"
        + "      \"equipmentTags\": [\"V-101\", \"GD-101\", \"GD-102\", \"GD-103\"],\n"
        + "      \"hazardIds\": [\"LOC-V-101\"],\n" + "      \"evidenceRefs\": [\"EV-CE-001\"]\n"
        + "    }]\n" + "  },\n" + "  \"demands\": [{\n" + "    \"demandId\": \"D-FGS-101\",\n"
        + "    \"barrierId\": \"B-FGS-101\",\n" + "    \"category\": \"FIRE_GAS_DETECTION\",\n"
        + "    \"requiredResponseTimeSeconds\": 20.0,\n"
        + "    \"actualResponseTimeSeconds\": 10.0,\n" + "    \"requiredAvailability\": 0.99,\n"
        + "    \"actualAvailability\": 0.996\n" + "  }],\n" + "  \"measurementDevices\": [\n"
        + "    {\"type\": \"gas\", \"name\": \"GD-101\", \"tag\": \"GD-101\", "
        + "\"location\": \"Module M01\", \"responseTimeSeconds\": 8.0}\n" + "  ],\n"
        + "  \"logicSifs\": [{\n" + "    \"name\": \"B-FGS-101 voting\",\n"
        + "    \"votingLogic\": \"2oo3\",\n" + "    \"detectors\": [\n"
        + "      {\"name\": \"GD-101\", \"type\": \"GAS\", "
        + "\"alarmLevel\": \"HIGH_HIGH\", \"setpoint\": 60.0, \"unit\": \"%LEL\"},\n"
        + "      {\"name\": \"GD-102\", \"type\": \"GAS\", "
        + "\"alarmLevel\": \"HIGH_HIGH\", \"setpoint\": 60.0, \"unit\": \"%LEL\"},\n"
        + "      {\"name\": \"GD-103\", \"type\": \"GAS\", "
        + "\"alarmLevel\": \"HIGH_HIGH\", \"setpoint\": 60.0, \"unit\": \"%LEL\"}\n" + "    ]\n"
        + "  }],\n" + "  \"quantitativeSifs\": [{\n" + "    \"id\": \"B-FGS-101\",\n"
        + "    \"name\": \"B-FGS-101 quantitative SIL\",\n" + "    \"claimedSIL\": 3,\n"
        + "    \"architecture\": \"2oo3\",\n" + "    \"pfdAvg\": 0.0008,\n"
        + "    \"proofTestInterval_hours\": 8760.0,\n"
        + "    \"protectedEquipment\": [\"V-101\"],\n" + "    \"category\": \"FIRE_GAS\"\n"
        + "  }]\n" + "}";
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

  // ========== Safety Examples ==========

  /**
   * Returns an evidence-linked barrier register example for HP separator overpressure.
   *
   * @return JSON string for BarrierRegisterRunner.run()
   */
  public static String safetyBarrierRegister() {
    return "{\n" + "  \"action\": \"audit\",\n" + "  \"register\": {\n"
        + "    \"registerId\": \"BR-HP-SEP-001\",\n"
        + "    \"name\": \"HP separator overpressure barrier register\",\n"
        + "    \"evidence\": [\n" + "      {\n" + "        \"evidenceId\": \"EV-PID-001\",\n"
        + "        \"documentId\": \"PID-001\",\n"
        + "        \"documentTitle\": \"HP separator P&ID\",\n" + "        \"revision\": \"B\",\n"
        + "        \"section\": \"ESD valves\",\n" + "        \"page\": 3,\n"
        + "        \"sourceReference\": \"V-101 / ESDV-101 inlet isolation\",\n"
        + "        \"excerpt\": \"HIPPS closes ESDV-101 on high-high pressure.\",\n"
        + "        \"confidence\": 0.92\n" + "      },\n" + "      {\n"
        + "        \"evidenceId\": \"EV-SRS-001\",\n" + "        \"documentId\": \"SRS-HP-101\",\n"
        + "        \"documentTitle\": \"HIPPS safety requirements specification\",\n"
        + "        \"revision\": \"1\",\n" + "        \"section\": \"Performance requirements\",\n"
        + "        \"page\": 12,\n" + "        \"sourceReference\": \"SIF-HIPPS-101 table\",\n"
        + "        \"excerpt\": \"SIF-HIPPS-101 shall achieve PFDavg <= 1E-3.\",\n"
        + "        \"confidence\": 0.95\n" + "      }\n" + "    ],\n"
        + "    \"performanceStandards\": [\n" + "      {\n" + "        \"id\": \"PS-HIPPS-101\",\n"
        + "        \"title\": \"HIPPS overpressure protection\",\n"
        + "        \"safetyFunction\": \"Prevent HP separator overpressure from blocked outlet\",\n"
        + "        \"demandMode\": \"LOW_DEMAND\",\n" + "        \"targetPfd\": 0.001,\n"
        + "        \"requiredAvailability\": 0.99,\n"
        + "        \"proofTestIntervalHours\": 8760,\n" + "        \"responseTimeSeconds\": 2.0,\n"
        + "        \"acceptanceCriteria\": [\n"
        + "          \"Close inlet ESD valve before separator MAWP is exceeded\",\n"
        + "          \"Proof-test interval not longer than 12 months\"\n" + "        ],\n"
        + "        \"evidenceRefs\": [\"EV-SRS-001\"]\n" + "      },\n" + "      {\n"
        + "        \"id\": \"PS-PSV-101\",\n" + "        \"title\": \"PSV relief to flare\",\n"
        + "        \"safetyFunction\": \"Relieve overpressure to flare header\",\n"
        + "        \"demandMode\": \"LOW_DEMAND\",\n" + "        \"targetPfd\": 0.01,\n"
        + "        \"requiredAvailability\": 0.98,\n" + "        \"acceptanceCriteria\": [\n"
        + "          \"PSV set pressure shall protect separator MAWP\",\n"
        + "          \"Relief path to flare shall be open during operation\"\n" + "        ],\n"
        + "        \"evidenceRefs\": [\"EV-PID-001\"]\n" + "      }\n" + "    ],\n"
        + "    \"barriers\": [\n" + "      {\n" + "        \"id\": \"B-HIPPS-101\",\n"
        + "        \"name\": \"HIPPS inlet shutdown\",\n"
        + "        \"description\": \"Independent high-pressure trip closes inlet ESD valve\",\n"
        + "        \"type\": \"PREVENTION\",\n" + "        \"status\": \"AVAILABLE\",\n"
        + "        \"safetyFunction\": \"Prevent LOC from separator overpressure\",\n"
        + "        \"owner\": \"Technical safety\",\n" + "        \"pfd\": 0.001,\n"
        + "        \"performanceStandardId\": \"PS-HIPPS-101\",\n"
        + "        \"equipmentTags\": [\"V-101\", \"ESDV-101\"],\n"
        + "        \"hazardIds\": [\"HAZ-OP-001\"],\n"
        + "        \"evidenceRefs\": [\"EV-PID-001\", \"EV-SRS-001\"]\n" + "      },\n"
        + "      {\n" + "        \"id\": \"B-PSV-101\",\n" + "        \"name\": \"PSV to flare\",\n"
        + "        \"description\": \"Relief valve protects separator against overpressure\",\n"
        + "        \"type\": \"MITIGATION\",\n" + "        \"status\": \"AVAILABLE\",\n"
        + "        \"owner\": \"Process\",\n" + "        \"pfd\": 0.01,\n"
        + "        \"performanceStandardId\": \"PS-PSV-101\",\n"
        + "        \"effectiveness\": 0.90,\n"
        + "        \"equipmentTags\": [\"V-101\", \"PSV-101\"],\n"
        + "        \"hazardIds\": [\"HAZ-OP-001\"],\n"
        + "        \"evidenceRefs\": [\"EV-PID-001\"]\n" + "      }\n" + "    ],\n"
        + "    \"safetyCriticalElements\": [\n" + "      {\n" + "        \"id\": \"SCE-V-101\",\n"
        + "        \"tag\": \"V-101\",\n"
        + "        \"name\": \"HP separator pressure protection\",\n"
        + "        \"type\": \"PROCESS_EQUIPMENT\",\n" + "        \"owner\": \"Operations\",\n"
        + "        \"equipmentTags\": [\"V-101\"],\n"
        + "        \"barrierRefs\": [\"B-HIPPS-101\", \"B-PSV-101\"],\n"
        + "        \"evidenceRefs\": [\"EV-PID-001\"]\n" + "      }\n" + "    ]\n" + "  }\n" + "}";
  }

  /**
   * Returns a simulation-backed HAZOP study example for a compression train.
   *
   * @return JSON string for HAZOPStudyRunner.run()
   */
  public static String safetyHazopStudy() {
    return "{\n" + "  \"studyId\": \"HAZOP-COMP-001\",\n" + "  \"runSimulations\": true,\n"
        + "  \"failureModes\": [\"COOLING_LOSS\", \"COMPRESSOR_TRIP\"],\n"
        + "  \"processDefinition\": {\n" + "    \"fluid\": {\n" + "      \"model\": \"SRK\",\n"
        + "      \"temperature\": 298.15,\n" + "      \"pressure\": 10.0,\n"
        + "      \"mixingRule\": \"classic\",\n" + "      \"components\": {\n"
        + "        \"methane\": 0.90,\n" + "        \"ethane\": 0.07,\n"
        + "        \"propane\": 0.03\n" + "      }\n" + "    },\n" + "    \"process\": [\n"
        + "      {\n" + "        \"type\": \"Stream\",\n" + "        \"name\": \"feed\",\n"
        + "        \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}\n" + "      },\n"
        + "      {\n" + "        \"type\": \"Compressor\",\n" + "        \"name\": \"1st Stage\",\n"
        + "        \"inlet\": \"feed\",\n"
        + "        \"properties\": {\"outletPressure\": [30.0, \"bara\"]}\n" + "      },\n"
        + "      {\n" + "        \"type\": \"Cooler\",\n" + "        \"name\": \"Intercooler\",\n"
        + "        \"inlet\": \"1st Stage\",\n"
        + "        \"properties\": {\"outTemperature\": [303.15, \"K\"]}\n" + "      },\n"
        + "      {\n" + "        \"type\": \"Compressor\",\n" + "        \"name\": \"2nd Stage\",\n"
        + "        \"inlet\": \"Intercooler\",\n"
        + "        \"properties\": {\"outletPressure\": [80.0, \"bara\"]}\n" + "      }\n"
        + "    ]\n" + "  },\n" + "  \"nodes\": [\n" + "    {\n"
        + "      \"nodeId\": \"Node-01 Intercooler\",\n"
        + "      \"designIntent\": \"Cool first-stage discharge before second-stage compression\",\n"
        + "      \"equipment\": [\"Intercooler\"],\n"
        + "      \"safeguards\": [\"High temperature alarm\", \"Compressor high-high temperature trip\"],\n"
        + "      \"evidenceRefs\": [\"EV-PID-101\"]\n" + "    },\n" + "    {\n"
        + "      \"nodeId\": \"Node-02 Second-stage compressor\",\n"
        + "      \"designIntent\": \"Compress cooled gas to export pressure\",\n"
        + "      \"equipment\": [\"2nd Stage\"],\n"
        + "      \"safeguards\": [\"Anti-surge control\", \"Discharge pressure trip\"],\n"
        + "      \"evidenceRefs\": [\"EV-COMP-201\"]\n" + "    }\n" + "  ],\n"
        + "  \"barrierRegister\": {\n" + "    \"registerId\": \"BR-COMP-001\",\n"
        + "    \"name\": \"Compression HAZOP safeguard register\",\n" + "    \"evidence\": [\n"
        + "      {\n" + "        \"evidenceId\": \"EV-PID-101\",\n"
        + "        \"documentId\": \"PID-101\",\n"
        + "        \"documentTitle\": \"Compression P&ID\",\n" + "        \"revision\": \"A\",\n"
        + "        \"section\": \"Temperature protection\",\n" + "        \"page\": 4,\n"
        + "        \"sourceReference\": \"TT-101 / TSHH-101\",\n"
        + "        \"excerpt\": \"TSHH-101 trips compressor train on high-high discharge temperature.\",\n"
        + "        \"confidence\": 0.90\n" + "      }\n" + "    ],\n" + "    \"barriers\": [\n"
        + "      {\n" + "        \"id\": \"B-TSHH-101\",\n"
        + "        \"name\": \"High-high temperature trip\",\n"
        + "        \"type\": \"PREVENTION\",\n" + "        \"status\": \"AVAILABLE\",\n"
        + "        \"pfd\": 0.01,\n"
        + "        \"equipmentTags\": [\"Intercooler\", \"2nd Stage\"],\n"
        + "        \"hazardIds\": [\"HAZOP-COMP-001\"],\n"
        + "        \"evidenceRefs\": [\"EV-PID-101\"]\n" + "      }\n" + "    ]\n" + "  }\n" + "}";
  }

  // ========== Catalog Metadata ==========

  /**
   * Returns a list of all available example categories.
   *
   * @return unmodifiable list of category names
   */
  public static List<String> getCategories() {
    return Collections.unmodifiableList(Arrays.asList("flash", "process", "validation", "batch",
        "property-table", "phase-envelope", "pvt", "flow-assurance", "standards", "pipeline",
        "water-hammer", "root-cause", "reservoir", "economics", "materials-review", "bioprocess",
        "open-drain-review", "process-safety-review", "session", "visualization",
        "equipment-sizing", "comparison", "safety"));
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
    } else if ("water-hammer".equals(category)) {
      return Arrays.asList("valve-closure");
    } else if ("root-cause".equals(category)) {
      return Arrays.asList("compressor-high-vibration", "separator-liquid-carryover",
          "hx-fouling");
    } else if ("materials-review".equals(category)) {
      return Arrays.asList("stid-register");
    } else if ("open-drain-review".equals(category)) {
      return Arrays.asList("norsok-s001-stid");
    } else if ("process-safety-review".equals(category)) {
      return Arrays.asList("norsok-s001-clause10");
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
    } else if ("safety".equals(category)) {
      return Arrays.asList("barrier-register", "safety-system-performance", "hazop-study");
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
    } else if ("water-hammer".equals(category)) {
      if ("valve-closure".equals(name)) {
        return waterHammerValveClosure();
      }
    } else if ("root-cause".equals(category)) {
      if ("compressor-high-vibration".equals(name)) {
        return rootCauseCompressorHighVibration();
      }
      if ("separator-liquid-carryover".equals(name)) {
        return rootCauseSeparatorLiquidCarryover();
      }
      if ("hx-fouling".equals(name)) {
        return rootCauseHeatExchangerFouling();
      }
    } else if ("materials-review".equals(category)) {
      if ("stid-register".equals(name)) {
        return materialsReviewStidRegister();
      }
    } else if ("open-drain-review".equals(category)) {
      if ("norsok-s001-stid".equals(name)) {
        return openDrainReviewNorsokS001Stid();
      }
    } else if ("process-safety-review".equals(category)) {
      if ("norsok-s001-clause10".equals(name)) {
        return norsokS001Clause10ProcessSafetySystem();
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
    } else if ("safety".equals(category)) {
      if ("barrier-register".equals(name)) {
        return safetyBarrierRegister();
      }
      if ("safety-system-performance".equals(name)) {
        return safetySystemPerformance();
      }
      if ("hazop-study".equals(name)) {
        return safetyHazopStudy();
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

    // Water-hammer examples
    Map<String, String> hammerExamples = new LinkedHashMap<String, String>();
    hammerExamples.put("valve-closure",
        "Water-hammer screening for fast ESD valve closure with STID/tagreader-style inputs");
    catalog.put("water-hammer", hammerExamples);

    // Root-cause analysis examples
    Map<String, String> rcaExamples = new LinkedHashMap<String, String>();
    rcaExamples.put("compressor-high-vibration",
        "Compressor RCA combining process JSON, historian trend, design limits, and STID data");
    rcaExamples.put("separator-liquid-carryover",
        "Separator RCA with tagreader historian (PI), STID datasheet, and demister dP trend");
    rcaExamples.put("hx-fouling",
        "Heat exchanger fouling RCA with IP.21 historian, vendor data, and UA degradation");
    catalog.put("root-cause", rcaExamples);

    // Materials review examples
    Map<String, String> materialsExamples = new LinkedHashMap<String, String>();
    materialsExamples.put("stid-register",
        "Materials selection, degradation, CUI, and remaining-life review from normalized STID records");
    catalog.put("materials-review", materialsExamples);

    // Open-drain review examples
    Map<String, String> openDrainExamples = new LinkedHashMap<String, String>();
    openDrainExamples.put("norsok-s001-stid",
      "NORSOK S-001 Clause 9 open-drain review from normalized STID and tagreader evidence");
    catalog.put("open-drain-review", openDrainExamples);

    // Process safety system review examples
    Map<String, String> processSafetyExamples = new LinkedHashMap<String, String>();
    processSafetyExamples.put("norsok-s001-clause10",
        "NORSOK S-001 Clause 10 process safety system review from normalized C&E, SRS, PSV, and instrument evidence");
    catalog.put("process-safety-review", processSafetyExamples);

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

    // Safety examples
    Map<String, String> safetyExamples = new LinkedHashMap<String, String>();
    safetyExamples.put("barrier-register",
        "Evidence-linked SCE/barrier register with LOPA, SIL, bow-tie, and QRA handoffs");
    safetyExamples.put("safety-system-performance",
        "Safety-system barrier performance analysis from STID and SIF data");
    safetyExamples.put("hazop-study",
        "Simulation-backed IEC 61882 HAZOP worksheet from process scenarios and STID evidence");
    catalog.put("safety", safetyExamples);

    return GSON.toJson(catalog);
  }
}
