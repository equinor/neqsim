package neqsim.mcp.runners;

import java.util.Arrays;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Builds the public synthetic acceptance-fixture catalog for MCP campaign Phase 0.
 *
 * <p>
 * The fixtures deliberately reuse the normal MCP runner inputs and canonical NeqSim process JSON. They are not a second
 * simulator or an optimization benchmark. The catalog spans a single calculation, a small recycle train, a multi-area
 * {@code ProcessModel}, and a generated 150+ unit recycle facility so later Phase 0 measurements can use stable,
 * public, reproducible inputs.
 * </p>
 */
public final class McpAcceptanceFixtureCatalog {

  /** Private constructor for utility class. */
  private McpAcceptanceFixtureCatalog() {
  }

  /**
   * Builds the four-scale acceptance catalog.
   *
   * @return machine-readable fixture catalog
   */
  public static JsonObject build() {
    JsonObject catalog = new JsonObject();
    catalog.addProperty("catalogVersion", "1.0");
    catalog.addProperty("fixtureCount", 4);
    catalog.addProperty("complete", true);
    catalog.addProperty("executionEvidenceStatus", "BASELINE_HARNESS_AVAILABLE_RESULTS_RUN_SPECIFIC");
    catalog.addProperty("evidenceDocument", "neqsim-mcp-server/docs/ACCEPTANCE_FIXTURES.md");

    JsonArray fixtures = new JsonArray();
    fixtures.add(fixture("single-calculation", "SINGLE_CALCULATION", "runFlash", "thermodynamic-flash",
        singleCalculationInput(), 0, 0, 0,
        new String[] { "successful standard MCP envelope", "finite phase/property result",
            "provenance and validation" },
        "Thermodynamic accuracy remains governed by the selected model and BenchmarkTrust evidence."));
    fixtures.add(fixture("small-recycle-train", "SMALL_TRAIN", "runProcess", "ProcessSystem", smallTrainInput(), 1, 10,
        1,
        new String[] { "successful process solve", "canonical processDefinition replay",
            "mass/energy validation evidence", "deterministic repeated execution" },
        "This fixture exercises MCP process composition and recycle delivery, not optimization or dynamic qualification."));
    fixtures.add(fixture("multi-area-facility", "MULTI_AREA", "runProcess", "ProcessModel", multiAreaInput(), 3, 8, 0,
        new String[] { "three named areas", "successful ProcessModel solve", "area ordering and canonical replay",
            "convergence summary and validation evidence" },
        "The Phase 0 fixture qualifies multi-area MCP construction/delivery; plant-wide optimization remains #3154-owned."));
    fixtures.add(fixture("large-recycle-facility", "LARGE_FACILITY", "runProcess", "ProcessSystem",
        largeFacilityInput(), 1, 154, 1,
        new String[] { "150+ unit executable definition", "successful bounded solve", "recycle convergence",
            "canonical replay", "response-size and selective-retrieval baseline" },
        "This fixture freezes an MCP transport/execution scale. Generic process performance remains #2939-owned and optimization fidelity remains #3154-owned."));
    catalog.add("fixtures", fixtures);

    catalog.addProperty("remainingPhase0Boundary",
        "Execute McpAcceptanceBaselineRunner on each exact head, close explicit numerical balance/report gaps where justified, then complete the campaign traceability and discipline-maturity matrices");
    return catalog;
  }

  /** Returns the single-calculation flash input. */
  public static String singleCalculationInput() {
    return ExampleCatalog.flashTPSimpleGas();
  }

  /** Returns the small recycle-train process input. */
  public static String smallTrainInput() {
    return ExampleCatalog.processMixerSplitterRecycle();
  }

  /**
   * Returns a deterministic three-area ProcessModel input using existing supported process examples.
   *
   * @return multi-area builder JSON
   */
  public static String multiAreaInput() {
    JsonObject root = new JsonObject();
    JsonObject areas = new JsonObject();
    areas.add("inlet-separation", JsonParser.parseString(ExampleCatalog.processSimpleSeparation()).getAsJsonObject());
    areas.add("gas-compression",
        JsonParser.parseString(ExampleCatalog.processCompressionWithCooling()).getAsJsonObject());
    areas.add("export-conditioning",
        JsonParser.parseString(ExampleCatalog.processSimpleSeparation()).getAsJsonObject());
    root.add("areas", areas);
    root.addProperty("maxIterations", 50);
    root.addProperty("flowTolerance", 1.0e-6);
    return root.toString();
  }

  /**
   * Returns a deterministic 154-unit recycle process for MCP scale and payload baselining.
   *
   * <p>
   * The process uses only standard stream, mixer, heater, cooler, splitter, and recycle equipment. Seventy-five
   * heater/cooler pairs make the fixture large without introducing owner-roadmap-specific optimization constraints or
   * specialist physics. The final two-percent recycle closes through the first mixer.
   * </p>
   *
   * @return large canonical process JSON
   */
  public static String largeFacilityInput() {
    JsonObject root = new JsonObject();
    root.add("fluid", baseFluid());
    root.addProperty("autoRun", true);

    JsonArray process = new JsonArray();
    process.add(unit("Stream", "feed", null, flowProperties(250000.0)));

    JsonObject mixer = unit("Mixer", "Feed + Recycle", null, null);
    JsonArray mixerInlets = new JsonArray();
    mixerInlets.add("feed");
    mixerInlets.add("Large Recycle.out");
    mixer.add("inlets", mixerInlets);
    process.add(mixer);

    String inlet = "Feed + Recycle.out";
    for (int index = 1; index <= 75; index++) {
      String heaterName = String.format("Conditioning Heater %02d", Integer.valueOf(index));
      JsonObject heaterProperties = new JsonObject();
      heaterProperties.add("outTemperature", valueWithUnit(303.15, "K"));
      process.add(unit("Heater", heaterName, inlet, heaterProperties));

      String coolerName = String.format("Conditioning Cooler %02d", Integer.valueOf(index));
      JsonObject coolerProperties = new JsonObject();
      coolerProperties.add("outTemperature", valueWithUnit(298.15, "K"));
      process.add(unit("Cooler", coolerName, heaterName + ".out", coolerProperties));
      inlet = coolerName + ".out";
    }

    JsonObject splitterProperties = new JsonObject();
    JsonArray splitFactors = new JsonArray();
    splitFactors.add(0.98);
    splitFactors.add(0.02);
    splitterProperties.add("splitFactors", splitFactors);
    process.add(unit("Splitter", "Export Splitter", inlet, splitterProperties));
    process.add(unit("Recycle", "Large Recycle", "Export Splitter.splitStream_1", null));

    root.add("process", process);
    return root.toString();
  }

  /** Builds one fixture descriptor. */
  private static JsonObject fixture(String id, String scale, String tool, String modelKind, String input, int areaCount,
      int unitCount, int recycleCount, String[] checks, String boundary) {
    JsonObject fixture = new JsonObject();
    fixture.addProperty("id", id);
    fixture.addProperty("scale", scale);
    fixture.addProperty("executionTool", tool);
    fixture.addProperty("modelKind", modelKind);
    fixture.addProperty("areaCount", areaCount);
    fixture.addProperty("unitCount", unitCount);
    fixture.addProperty("recycleCount", recycleCount);
    fixture.add("input", JsonParser.parseString(input));
    fixture.add("acceptanceChecks", toJsonArray(checks));
    fixture.addProperty("boundary", boundary);
    fixture.addProperty("publicSynthetic", true);
    return fixture;
  }

  /** Builds the stable natural-gas fluid used by the generated large fixture. */
  private static JsonObject baseFluid() {
    JsonObject fluid = new JsonObject();
    fluid.addProperty("model", "SRK");
    fluid.addProperty("temperature", 298.15);
    fluid.addProperty("pressure", 70.0);
    fluid.addProperty("mixingRule", "classic");
    JsonObject components = new JsonObject();
    components.addProperty("methane", 0.88);
    components.addProperty("ethane", 0.08);
    components.addProperty("propane", 0.04);
    fluid.add("components", components);
    return fluid;
  }

  /** Builds one process unit. */
  private static JsonObject unit(String type, String name, String inlet, JsonObject properties) {
    JsonObject unit = new JsonObject();
    unit.addProperty("type", type);
    unit.addProperty("name", name);
    if (inlet != null) {
      unit.addProperty("inlet", inlet);
    }
    if (properties != null) {
      unit.add("properties", properties);
    }
    return unit;
  }

  /** Builds stream flow properties. */
  private static JsonObject flowProperties(double flowRateKgHr) {
    JsonObject properties = new JsonObject();
    properties.add("flowRate", valueWithUnit(flowRateKgHr, "kg/hr"));
    return properties;
  }

  /** Builds the builder's two-element value/unit array. */
  private static JsonArray valueWithUnit(double value, String unit) {
    JsonArray result = new JsonArray();
    result.add(value);
    result.add(unit);
    return result;
  }

  /** Converts strings to an ordered JSON array. */
  private static JsonArray toJsonArray(String[] values) {
    JsonArray result = new JsonArray();
    for (String value : Arrays.asList(values)) {
      result.add(value);
    }
    return result;
  }
}
