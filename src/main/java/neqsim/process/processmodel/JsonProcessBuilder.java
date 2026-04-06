package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.EquipmentFactory;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.system.SystemUMRPRUMCEos;

/**
 * Builds a {@link ProcessSystem} from a JSON definition string.
 *
 * <p>
 * Supports declarative definition of fluids, streams, and equipment with automatic stream wiring by
 * name reference. Designed for web API integration where clients submit JSON process definitions
 * and receive simulation results.
 * </p>
 *
 * <h2>JSON Format:</h2>
 *
 * <pre>{@code { "fluid": { "model": "SRK", "temperature": 298.15, "pressure": 50.0, "mixingRule":
 * "classic", "components": { "methane": 0.85, "ethane": 0.10, "propane": 0.05 } }, "process": [ {
 * "type": "Stream", "name": "feed", "properties": { "flowRate": [50000.0, "kg/hr"] } }, { "type":
 * "Separator", "name": "HP Sep", "inlet": "feed" }, { "type": "Compressor", "name": "Comp",
 * "inlet": "HP Sep.gasOut", "properties": { "outletPressure": [80.0, "bara"] } } ] } }</pre>
 *
 * @author Even Solbraa @version 1.0
 */
public class JsonProcessBuilder {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(JsonProcessBuilder.class);

  /** Registry of named fluids created during build. */
  private final Map<String, SystemInterface> namedFluids = new LinkedHashMap<>();

  /** Registry of named equipment created during build. */
  private final Map<String, ProcessEquipmentInterface> namedEquipment = new LinkedHashMap<>();

  /** Errors accumulated during build. */
  private final List<SimulationResult.ErrorDetail> errors = new ArrayList<>();

  /** Warnings accumulated during build. */
  private final List<String> warnings = new ArrayList<>();

  /**
   * Constructs a new JsonProcessBuilder.
   */
  public JsonProcessBuilder() {}

  /**
   * Builds a ProcessSystem from a JSON string.
   *
   * @param json the JSON process definition
   * @return a SimulationResult containing the built ProcessSystem or errors
   */
  public SimulationResult build(String json) {
    if (json == null || json.trim().isEmpty()) {
      return SimulationResult.error("JSON_PARSE_ERROR", "JSON input is null or empty",
          "Provide a valid JSON process definition");
    }
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      return buildFromJsonObject(root);
    } catch (Exception e) {
      return SimulationResult.error("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }
  }

  /**
   * Builds a ProcessSystem from a parsed JsonObject.
   *
   * @param root the root JSON object
   * @return a SimulationResult containing the built ProcessSystem or errors
   */
  public SimulationResult buildFromJsonObject(JsonObject root) {
    errors.clear();
    warnings.clear();
    namedFluids.clear();
    namedEquipment.clear();

    ProcessSystem process = new ProcessSystem("json-process");

    // Step 1: Build fluid(s)
    SystemInterface defaultFluid = null;
    if (root.has("fluid")) {
      defaultFluid = buildFluid(root.getAsJsonObject("fluid"));
      if (defaultFluid != null) {
        namedFluids.put("default", defaultFluid);
      }
    }

    // Step 1b: Named fluids map
    if (root.has("fluids")) {
      JsonObject fluidsObj = root.getAsJsonObject("fluids");
      for (Map.Entry<String, JsonElement> entry : fluidsObj.entrySet()) {
        SystemInterface fluid = buildFluid(entry.getValue().getAsJsonObject());
        if (fluid != null) {
          namedFluids.put(entry.getKey(), fluid);
        }
      }
    }

    // Step 2: Build process units (two-pass for recycle loops)
    if (!root.has("process")) {
      errors.add(
          new SimulationResult.ErrorDetail("MISSING_PROCESS", "JSON must contain a 'process' array",
              null, "Add a 'process' array with unit definitions"));
      return SimulationResult.failure(errors, warnings);
    }

    JsonArray processArray = root.getAsJsonArray("process");

    // Pass 1: Create all equipment objects (no wiring yet)
    List<JsonObject> unitDefs = new ArrayList<>();
    for (int i = 0; i < processArray.size(); i++) {
      JsonObject unitDef = processArray.get(i).getAsJsonObject();
      unitDefs.add(unitDef);
      createUnit(process, unitDef, defaultFluid, i);
    }

    // Pass 2: Wire inlet connections iteratively (handles forward references).
    // Some references resolve only after upstream units are wired, so iterate
    // until all are resolved or no progress is made.
    java.util.Set<String> unwired = new java.util.LinkedHashSet<>();
    Map<String, JsonObject> unitDefMap = new LinkedHashMap<>();
    for (int i = 0; i < unitDefs.size(); i++) {
      JsonObject unitDef = unitDefs.get(i);
      String type = unitDef.has("type") ? unitDef.get("type").getAsString() : "";
      String name = unitDef.has("name") ? unitDef.get("name").getAsString() : type + "_" + (i + 1);
      unitDefMap.put(name, unitDef);
      if (needsWiring(unitDef)) {
        unwired.add(name);
      }
    }

    int maxIterations = unwired.size() + 1;
    for (int iter = 0; iter < maxIterations && !unwired.isEmpty(); iter++) {
      java.util.Set<String> wiredThisRound = new java.util.LinkedHashSet<>();
      for (String name : unwired) {
        JsonObject unitDef = unitDefMap.get(name);
        if (unitDef != null && tryWireUnit(name, unitDef)) {
          wiredThisRound.add(name);
        }
      }
      unwired.removeAll(wiredThisRound);
      if (wiredThisRound.isEmpty()) {
        break; // No progress — remaining refs cannot be resolved
      }
    }

    // Report any remaining unwired units as warnings (not hard errors).
    // Missing references are common in complex models with sub-flowsheets
    // or skipped operation types — allow partial success.
    // Also remove completely unwired non-Stream units from the process
    // to prevent NPEs during simulation (e.g. Mixer with zero inlets).
    for (String name : unwired) {
      JsonObject unitDef = unitDefMap.get(name);
      if (unitDef != null) {
        reportUnwiredUnit(name, unitDef);
        // Remove the unit from the process so it doesn't crash during run()
        process.removeUnit(name);
        namedEquipment.remove(name);
      }
    }

    // Only fail on critical errors (MISSING_PROCESS, MISSING_TYPE, JSON_PARSE_ERROR).
    // Wiring and unit-creation issues are downgraded to warnings for partial success.
    List<SimulationResult.ErrorDetail> criticalErrors = new ArrayList<>();
    for (SimulationResult.ErrorDetail err : errors) {
      String code = err.getCode();
      if ("STREAM_NOT_FOUND".equals(code) || "UNIT_ERROR".equals(code)) {
        warnings.add("[" + code + "] " + err.getMessage());
      } else {
        criticalErrors.add(err);
      }
    }
    errors.clear();
    errors.addAll(criticalErrors);

    if (!errors.isEmpty()) {
      return SimulationResult.failure(process, errors, warnings);
    }

    // Step 3: Pre-run safety — remove equipment that would NPE during simulation.
    // Mixers whose mixedStream was not initialised (e.g. because the first inlet
    // stream had no ThermoSystem at wiring time) will crash in mixStream().
    List<String> toRemove = new ArrayList<>();
    for (ProcessEquipmentInterface eq : process.getUnitOperations()) {
      if (eq instanceof Mixer && ((Mixer) eq).getOutletStream() == null) {
        toRemove.add(eq.getName());
      }
    }
    for (String removeName : toRemove) {
      warnings.add("Removed Mixer '" + removeName
          + "' — outlet stream was null (inlet stream not initialised)");
      process.removeUnit(removeName);
      namedEquipment.remove(removeName);
    }

    // Step 4: Optionally run
    boolean autoRun = root.has("autoRun") && root.get("autoRun").getAsBoolean();
    if (autoRun) {
      try {
        process.run();
      } catch (Exception e) {
        warnings.add(
            "process.run() threw: " + e.getMessage() + " — partial results may still be available");
      }
      // Report generation can also fail if some units have corrupted state
      String report = null;
      try {
        report = process.getReport_json();
      } catch (Exception e) {
        warnings.add("Report generation failed: " + e.getMessage());
      }
      return SimulationResult.success(process, report, warnings);
    }

    return SimulationResult.success(process, null, warnings);
  }

  /**
   * Builds a fluid (SystemInterface) from a JSON definition.
   *
   * @param fluidDef the JSON object defining the fluid
   * @return the created fluid, or null if creation failed
   */
  private SystemInterface buildFluid(JsonObject fluidDef) {
    try {
      double temperature =
          fluidDef.has("temperature") ? fluidDef.get("temperature").getAsDouble() : 288.15;
      double pressure = fluidDef.has("pressure") ? fluidDef.get("pressure").getAsDouble() : 1.01325;

      String model =
          fluidDef.has("model") ? fluidDef.get("model").getAsString().toUpperCase() : "SRK";

      SystemInterface fluid = createFluidByModel(model, temperature, pressure);
      if (fluid == null) {
        errors.add(new SimulationResult.ErrorDetail("UNKNOWN_MODEL",
            "Unknown thermodynamic model: " + model, null,
            "Use one of: SRK, PR, CPA, GERG2008, PCSAFT, UMRPRU"));
        return null;
      }

      // Add components
      if (fluidDef.has("components")) {
        JsonObject components = fluidDef.getAsJsonObject("components");
        for (Map.Entry<String, JsonElement> comp : components.entrySet()) {
          fluid.addComponent(comp.getKey(), comp.getValue().getAsDouble());
        }
      }

      // Set mixing rule
      String mixingRule =
          fluidDef.has("mixingRule") ? fluidDef.get("mixingRule").getAsString() : "classic";
      fluid.setMixingRule(mixingRule);

      // Multi-phase check
      if (fluidDef.has("multiPhaseCheck")) {
        fluid.setMultiPhaseCheck(fluidDef.get("multiPhaseCheck").getAsBoolean());
      }

      fluid.init(0);
      return fluid;
    } catch (Exception e) {
      errors.add(new SimulationResult.ErrorDetail("FLUID_ERROR",
          "Failed to create fluid: " + e.getMessage(), null,
          "Check component names and fluid parameters"));
      return null;
    }
  }

  /**
   * Creates a SystemInterface based on the model type string.
   *
   * @param model the model name (e.g., "SRK", "PR", "CPA")
   * @param temperature temperature in Kelvin
   * @param pressure pressure in bara
   * @return the created fluid system, or null if unknown model
   */
  private SystemInterface createFluidByModel(String model, double temperature, double pressure) {
    switch (model) {
      case "SRK":
        return new SystemSrkEos(temperature, pressure);
      case "PR":
        return new SystemPrEos(temperature, pressure);
      case "CPA":
        return new SystemSrkCPAstatoil(temperature, pressure);
      case "GERG2008":
        return new SystemGERG2008Eos(temperature, pressure);
      case "PCSAFT":
        return new SystemPCSAFT(temperature, pressure);
      case "UMRPRU":
        return new SystemUMRPRUMCEos(temperature, pressure);
      default:
        return null;
    }
  }

  /**
   * Pass 1: Creates a unit (no wiring) and registers it.
   *
   * @param process the process system
   * @param unitDef the JSON definition
   * @param defaultFluid the default fluid
   * @param index the unit index
   */
  private void createUnit(ProcessSystem process, JsonObject unitDef, SystemInterface defaultFluid,
      int index) {
    if (!unitDef.has("type")) {
      errors.add(new SimulationResult.ErrorDetail("MISSING_TYPE",
          "Unit at index " + index + " has no 'type' field", null,
          "Add a 'type' field (e.g., 'Stream', 'Separator', 'Compressor')"));
      return;
    }

    String type = unitDef.get("type").getAsString();
    String name =
        unitDef.has("name") ? unitDef.get("name").getAsString() : type + "_" + (index + 1);

    try {
      ProcessEquipmentInterface equipment;

      if ("Stream".equalsIgnoreCase(type)) {
        equipment = createStream(name, unitDef, defaultFluid);
      } else if ("DistillationColumn".equalsIgnoreCase(type) || "Column".equalsIgnoreCase(type)) {
        equipment = createDistillationColumn(name, unitDef);
      } else {
        equipment = EquipmentFactory.createEquipment(name, type);
      }

      if (equipment != null) {
        // Special handling for Splitter: set split number before wiring
        // so that setInletStream creates the right number of split streams
        if (equipment instanceof Splitter && unitDef.has("properties")) {
          JsonObject props = unitDef.getAsJsonObject("properties");
          if (props.has("splitFactors")) {
            int nSplits = props.getAsJsonArray("splitFactors").size();
            ((Splitter) equipment).setSplitNumber(nSplits);
          }
        }

        // Special handling for Recycle: create a guess stream so downstream
        // units can reference RCY.outlet before the Recycle is wired
        if (equipment instanceof Recycle && defaultFluid != null) {
          StreamInterface guessStream = new Stream(name + "_guess", defaultFluid.clone());
          ((Recycle) equipment).setOutletStream(guessStream);
        }

        // Apply properties only for Streams (they don't need wiring).
        // Non-stream properties (e.g. outletPressure) are applied after wiring
        // because they may reference the outlet stream which needs an inlet first.
        if ("Stream".equalsIgnoreCase(type) && unitDef.has("properties")) {
          applyProperties(equipment, unitDef.getAsJsonObject("properties"));
        }

        process.add(equipment);
        namedEquipment.put(name, equipment);
      }
    } catch (Exception e) {
      errors.add(new SimulationResult.ErrorDetail("UNIT_ERROR",
          "Failed to create unit '" + name + "' (type: " + type + "): " + e.getMessage(), name,
          "Check the unit definition and ensure all required fields are present"));
    }
  }

  /**
   * Creates a DistillationColumn from its JSON definition, extracting tray count and
   * condenser/reboiler flags from properties.
   *
   * @param name the column name
   * @param unitDef the JSON definition
   * @return the created DistillationColumn
   */
  private ProcessEquipmentInterface createDistillationColumn(String name, JsonObject unitDef) {
    int numberOfTrays = 5;
    boolean hasReboiler = true;
    boolean hasCondenser = true;

    if (unitDef.has("properties")) {
      JsonObject props = unitDef.getAsJsonObject("properties");
      if (props.has("numberOfTrays")) {
        numberOfTrays = props.get("numberOfTrays").getAsInt();
      }
      if (props.has("hasReboiler")) {
        hasReboiler = props.get("hasReboiler").getAsBoolean();
      }
      if (props.has("hasCondenser")) {
        hasCondenser = props.get("hasCondenser").getAsBoolean();
      }
    }

    return new DistillationColumn(name, numberOfTrays, hasReboiler, hasCondenser);
  }

  /**
   * Reports an unwired unit as a warning (not a hard error). Extracts reference details so the user
   * knows which connections could not be resolved.
   *
   * @param name the unit name
   * @param unitDef the JSON definition
   */
  private void reportUnwiredUnit(String name, JsonObject unitDef) {
    if (unitDef.has("inlets")) {
      JsonArray inletsArr = unitDef.getAsJsonArray("inlets");
      for (JsonElement inletElem : inletsArr) {
        String inletRef = inletElem.getAsString();
        StreamInterface stream = resolveStreamReference(inletRef);
        if (stream == null) {
          warnings.add("Unresolved inlet '" + inletRef + "' for unit '" + name
              + "' (source may be in sub-flowsheet or skipped operation)");
        }
      }
    } else if (unitDef.has("inlet")) {
      String inletRef = unitDef.get("inlet").getAsString();
      StreamInterface stream = resolveStreamReference(inletRef);
      if (stream == null) {
        warnings.add("Unresolved inlet '" + inletRef + "' for unit '" + name
            + "' (source may be in sub-flowsheet or skipped operation)");
      }
    }
  }

  /**
   * Pass 2: Wires inlet connections for a unit (all units now exist).
   *
   * @param name the unit name
   * @param unitDef the JSON definition
   */
  private void wireUnit(String name, JsonObject unitDef) {
    ProcessEquipmentInterface equipment = namedEquipment.get(name);
    if (equipment == null || equipment instanceof StreamInterface) {
      return; // Streams don't need wiring
    }

    // Handle multiple inlets (e.g., Mixer)
    if (unitDef.has("inlets")) {
      JsonArray inletsArr = unitDef.getAsJsonArray("inlets");
      for (JsonElement inletElem : inletsArr) {
        String inletRef = inletElem.getAsString();
        StreamInterface stream = resolveStreamReference(inletRef);
        if (stream == null) {
          errors.add(new SimulationResult.ErrorDetail("STREAM_NOT_FOUND",
              "Inlet reference '" + inletRef + "' not found for unit '" + name + "'", name,
              "Ensure the referenced unit exists and was defined before this unit"));
        } else {
          wireInletStream(equipment, stream);
        }
      }
    } else if (unitDef.has("inlet")) {
      // Single inlet
      String inletRef = unitDef.get("inlet").getAsString();
      StreamInterface inletStream = resolveStreamReference(inletRef);
      if (inletStream == null) {
        errors.add(new SimulationResult.ErrorDetail("STREAM_NOT_FOUND",
            "Inlet reference '" + inletRef + "' not found for unit '" + name + "'", name,
            "Ensure the referenced unit exists"));
      } else {
        wireInletStream(equipment, inletStream);
      }
    }
  }

  /**
   * Checks whether a unit definition needs inlet wiring.
   *
   * @param unitDef the JSON definition
   * @return true if the unit has inlet references that need to be resolved
   */
  private boolean needsWiring(JsonObject unitDef) {
    String type = unitDef.has("type") ? unitDef.get("type").getAsString() : "";
    if ("Stream".equalsIgnoreCase(type)) {
      return false;
    }
    return unitDef.has("inlet") || unitDef.has("inlets");
  }

  /**
   * Attempts to wire all inlet references for a unit. Returns true only if ALL references resolve
   * successfully, allowing downstream units to use this unit's outlets. Does not add error details
   * on failure (those are added by the fallback wireUnit call).
   *
   * @param name the unit name
   * @param unitDef the JSON definition
   * @return true if all inlet references resolved and were wired
   */
  private boolean tryWireUnit(String name, JsonObject unitDef) {
    ProcessEquipmentInterface equipment = namedEquipment.get(name);
    if (equipment == null) {
      return false;
    }

    if (unitDef.has("inlets")) {
      JsonArray inletsArr = unitDef.getAsJsonArray("inlets");
      List<StreamInterface> resolved = new ArrayList<>();
      boolean allResolved = true;
      for (JsonElement inletElem : inletsArr) {
        StreamInterface stream = resolveStreamReference(inletElem.getAsString());
        if (stream == null) {
          allResolved = false;
        } else {
          resolved.add(stream);
        }
      }
      // For Mixer: wire whatever inlets we have (partial is OK)
      // For HeatExchanger: need both sides
      if (equipment instanceof Mixer && !resolved.isEmpty()) {
        for (StreamInterface stream : resolved) {
          wireInletStream(equipment, stream);
        }
        if (!allResolved) {
          warnings.add("Mixer '" + name + "' wired with " + resolved.size() + " of "
              + inletsArr.size() + " inlets (some not found)");
        }
      } else if (equipment instanceof HeatExchanger && resolved.size() == 2) {
        ((HeatExchanger) equipment).setFeedStream(0, resolved.get(0));
        ((HeatExchanger) equipment).setFeedStream(1, resolved.get(1));
      } else if (allResolved) {
        for (StreamInterface stream : resolved) {
          wireInletStream(equipment, stream);
        }
      } else {
        return false; // Not all resolved yet
      }
    } else if (unitDef.has("inlet")) {
      StreamInterface stream = resolveStreamReference(unitDef.get("inlet").getAsString());
      if (stream == null) {
        return false;
      }
      wireInletStream(equipment, stream);
    }

    // Apply all properties AFTER wiring (outlet stream now exists)
    if (unitDef.has("properties")) {
      JsonObject props = unitDef.getAsJsonObject("properties");
      // Handle split factors specially (Splitter needs inlet stream first)
      if (equipment instanceof Splitter && props.has("splitFactors")) {
        JsonArray factors = props.getAsJsonArray("splitFactors");
        double[] splitFact = new double[factors.size()];
        for (int i = 0; i < factors.size(); i++) {
          splitFact[i] = factors.get(i).getAsDouble();
        }
        ((Splitter) equipment).setSplitFactors(splitFact);
      }
      applyProperties(equipment, props);
    }

    return true;
  }

  /**
   * Builds a single process unit from its JSON definition and adds it to the process.
   *
   * @param process the process system to add the unit to
   * @param unitDef the JSON object defining the unit
   * @param defaultFluid the default fluid to use for streams
   * @param index the unit index (for error reporting)
   * @deprecated Use createUnit + wireUnit two-pass approach instead
   */
  @Deprecated
  private void buildUnit(ProcessSystem process, JsonObject unitDef, SystemInterface defaultFluid,
      int index) {
    createUnit(process, unitDef, defaultFluid, index);
    String type = unitDef.has("type") ? unitDef.get("type").getAsString() : "";
    String name =
        unitDef.has("name") ? unitDef.get("name").getAsString() : type + "_" + (index + 1);
    wireUnit(name, unitDef);
  }

  /**
   * Creates a unit and wires its inlet stream based on the JSON definition.
   *
   * @param process the process system
   * @param type the equipment type
   * @param name the equipment name
   * @param unitDef the JSON definition
   * @param defaultFluid the default fluid
   * @return the created equipment, or null if creation failed
   */
  private ProcessEquipmentInterface createAndWireUnit(ProcessSystem process, String type,
      String name, JsonObject unitDef, SystemInterface defaultFluid) {

    // Handle Stream type specially - it needs a fluid
    if ("Stream".equalsIgnoreCase(type)) {
      return createStream(name, unitDef, defaultFluid);
    }

    // Create equipment via factory
    ProcessEquipmentInterface equipment = EquipmentFactory.createEquipment(name, type);

    // Handle multiple inlets (e.g., Mixer with "inlets" array)
    if (unitDef.has("inlets")) {
      JsonArray inletsArr = unitDef.getAsJsonArray("inlets");
      for (JsonElement inletElem : inletsArr) {
        String inletRef = inletElem.getAsString();
        StreamInterface stream = resolveStreamReference(inletRef);
        if (stream == null) {
          errors.add(new SimulationResult.ErrorDetail("STREAM_NOT_FOUND",
              "Inlet reference '" + inletRef + "' not found for unit '" + name + "'", name,
              "Ensure the referenced unit exists and was defined before this unit"));
        } else {
          wireInletStream(equipment, stream);
        }
      }
    } else if (unitDef.has("inlet")) {
      // Single inlet
      String inletRef = unitDef.get("inlet").getAsString();
      StreamInterface inletStream = resolveStreamReference(inletRef);
      if (inletStream == null) {
        errors.add(new SimulationResult.ErrorDetail("STREAM_NOT_FOUND",
            "Inlet reference '" + inletRef + "' not found for unit '" + name + "'", name,
            "Ensure the referenced unit exists and was defined before this unit"));
        return null;
      }
      wireInletStream(equipment, inletStream);
    }

    return equipment;
  }

  /**
   * Creates a Stream from its JSON definition.
   *
   * @param name the stream name
   * @param unitDef the JSON definition
   * @param defaultFluid the default fluid
   * @return the created stream
   */
  private ProcessEquipmentInterface createStream(String name, JsonObject unitDef,
      SystemInterface defaultFluid) {
    // Determine which fluid to use
    SystemInterface fluid = defaultFluid;
    if (unitDef.has("fluidRef")) {
      String fluidRef = unitDef.get("fluidRef").getAsString();
      fluid = namedFluids.get(fluidRef);
      if (fluid == null) {
        errors.add(new SimulationResult.ErrorDetail("FLUID_NOT_FOUND",
            "Fluid reference '" + fluidRef + "' not found for stream '" + name + "'", name,
            "Define the fluid in the 'fluids' section first"));
        return null;
      }
    }

    if (fluid == null) {
      errors.add(
          new SimulationResult.ErrorDetail("NO_FLUID", "No fluid defined for stream '" + name + "'",
              name, "Define a 'fluid' section or set 'fluidRef' on the stream"));
      return null;
    }

    // Clone fluid so each stream gets its own instance
    SystemInterface streamFluid = fluid.clone();
    Stream stream = new Stream(name, streamFluid);

    return stream;
  }

  /**
   * Resolves a stream reference string to an actual StreamInterface.
   *
   * <p>
   * Supports the following reference formats:
   * <ul>
   * <li>"unitName" — resolves to the default outlet stream</li>
   * <li>"unitName.gasOut" — resolves to gas outlet of a separator</li>
   * <li>"unitName.liquidOut" — resolves to liquid outlet of a separator</li>
   * <li>"unitName.outlet" — resolves to the outlet stream</li>
   * </ul>
   *
   * @param ref the stream reference string
   * @return the resolved stream, or null if not found
   */
  private StreamInterface resolveStreamReference(String ref) {
    String unitName;
    String port = "outlet"; // default

    if (ref.contains(".")) {
      String[] parts = ref.split("\\.", 2);
      unitName = parts[0];
      port = parts[1].toLowerCase();
    } else {
      unitName = ref;
    }

    ProcessEquipmentInterface unit = namedEquipment.get(unitName);
    if (unit == null) {
      return null;
    }

    // If the unit is a Stream, return it directly
    if (unit instanceof StreamInterface) {
      return (StreamInterface) unit;
    }

    try {
      switch (port) {
        case "gasout":
        case "gas":
          return (StreamInterface) unit.getClass().getMethod("getGasOutStream").invoke(unit);
        case "liquidout":
        case "liquid":
          return (StreamInterface) unit.getClass().getMethod("getLiquidOutStream").invoke(unit);
        case "oilout":
        case "oil":
          return (StreamInterface) unit.getClass().getMethod("getOilOutStream").invoke(unit);
        case "waterout":
        case "water":
          return (StreamInterface) unit.getClass().getMethod("getWaterOutStream").invoke(unit);
        case "outlet":
        default:
          // Handle indexed split streams: "split0", "split1", etc.
          if (port.startsWith("split") && port.length() > 5) {
            try {
              int idx = Integer.parseInt(port.substring(5));
              return (StreamInterface) unit.getClass().getMethod("getSplitStream", int.class)
                  .invoke(unit, idx);
            } catch (NumberFormatException nfe) {
              // fall through to default outlet
            }
          }
          // Handle HeatExchanger which uses getOutStream(int) instead of getOutletStream()
          if (unit instanceof HeatExchanger) {
            return ((HeatExchanger) unit).getOutStream(0);
          }
          return (StreamInterface) unit.getClass().getMethod("getOutletStream").invoke(unit);
      }
    } catch (NoSuchMethodException e) {
      // Fallback chain: getOutStream(int) -> getOutletStreams().get(0) -> getOutStream()
      try {
        return (StreamInterface) unit.getClass().getMethod("getOutStream", int.class).invoke(unit,
            0);
      } catch (Exception ex2) {
        try {
          List<StreamInterface> outlets = unit.getOutletStreams();
          if (outlets != null && !outlets.isEmpty()) {
            return outlets.get(0);
          }
        } catch (Exception ex3) {
          // ignore
        }
        try {
          return (StreamInterface) unit.getClass().getMethod("getOutStream").invoke(unit);
        } catch (Exception ex4) {
          return null;
        }
      }
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Wires an inlet stream to an equipment unit via reflection.
   *
   * @param equipment the equipment to wire
   * @param stream the inlet stream
   */
  private void wireInletStream(ProcessEquipmentInterface equipment, StreamInterface stream) {
    // Special handling for DistillationColumn — uses addFeedStream, not setInletStream
    if (equipment instanceof DistillationColumn) {
      DistillationColumn column = (DistillationColumn) equipment;
      column.addFeedStream(stream);
      return;
    }

    try {
      java.lang.reflect.Method setInlet =
          equipment.getClass().getMethod("setInletStream", StreamInterface.class);
      setInlet.invoke(equipment, stream);
    } catch (NoSuchMethodException e) {
      // Try addStream for Mixer
      try {
        java.lang.reflect.Method addStream =
            equipment.getClass().getMethod("addStream", StreamInterface.class);
        addStream.invoke(equipment, stream);
      } catch (Exception ex) {
        // Try addFeedStream for column-like equipment
        try {
          java.lang.reflect.Method addFeed =
              equipment.getClass().getMethod("addFeedStream", StreamInterface.class);
          addFeed.invoke(equipment, stream);
        } catch (Exception ex2) {
          warnings.add("Cannot set inlet stream on " + equipment.getName()
              + ": no setInletStream, addStream, or addFeedStream method");
        }
      }
    } catch (Exception e) {
      warnings.add("Error wiring inlet to " + equipment.getName() + ": " + e.getMessage());
    }
  }

  /**
   * Applies property settings from JSON to an equipment unit via reflection.
   *
   * @param equipment the equipment to configure
   * @param properties the properties JSON object
   */
  private void applyProperties(ProcessEquipmentInterface equipment, JsonObject properties) {
    for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
      String propName = entry.getKey();
      JsonElement value = entry.getValue();
      applyProperty(equipment, propName, value);
    }
  }

  /**
   * Applies a single property to an equipment unit.
   *
   * @param equipment the equipment
   * @param propName the property name
   * @param value the property value
   */
  private void applyProperty(ProcessEquipmentInterface equipment, String propName,
      JsonElement value) {
    String setterName = "set" + Character.toUpperCase(propName.charAt(0)) + propName.substring(1);

    try {
      if (value.isJsonArray()) {
        // Array format: [value, "unit"] — e.g., [50000, "kg/hr"]
        JsonArray arr = value.getAsJsonArray();
        if (arr.size() >= 2) {
          double numValue = arr.get(0).getAsDouble();
          String unit = arr.get(1).getAsString();
          java.lang.reflect.Method method =
              equipment.getClass().getMethod(setterName, double.class, String.class);
          method.invoke(equipment, numValue, unit);
        }
      } else if (value.isJsonPrimitive()) {
        if (value.getAsJsonPrimitive().isNumber()) {
          // Try double setter first
          try {
            java.lang.reflect.Method method =
                equipment.getClass().getMethod(setterName, double.class);
            method.invoke(equipment, value.getAsDouble());
          } catch (NoSuchMethodException e) {
            // Try int setter
            java.lang.reflect.Method method = equipment.getClass().getMethod(setterName, int.class);
            method.invoke(equipment, value.getAsInt());
          }
        } else if (value.getAsJsonPrimitive().isBoolean()) {
          java.lang.reflect.Method method =
              equipment.getClass().getMethod(setterName, boolean.class);
          method.invoke(equipment, value.getAsBoolean());
        } else if (value.getAsJsonPrimitive().isString()) {
          java.lang.reflect.Method method =
              equipment.getClass().getMethod(setterName, String.class);
          method.invoke(equipment, value.getAsString());
        }
      }
    } catch (NoSuchMethodException e) {
      warnings.add("Property '" + propName + "' not found on " + equipment.getName() + " (tried "
          + setterName + ")");
    } catch (Exception e) {
      warnings.add(
          "Error setting '" + propName + "' on " + equipment.getName() + ": " + e.getMessage());
    }
  }

  /**
   * Convenience method to build and run a process from JSON.
   *
   * @param json the JSON process definition
   * @return the simulation result with report
   */
  public static SimulationResult buildAndRun(String json) {
    JsonProcessBuilder builder = new JsonProcessBuilder();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    if (!root.has("autoRun")) {
      root.addProperty("autoRun", true);
    }
    return builder.buildFromJsonObject(root);
  }

  /**
   * Convenience method to build (without running) a process from JSON.
   *
   * @param json the JSON process definition
   * @return the simulation result with the ProcessSystem
   */
  public static SimulationResult buildOnly(String json) {
    JsonProcessBuilder builder = new JsonProcessBuilder();
    return builder.build(json);
  }
}
