package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ModuleInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Generates structured equipment datasheets from mechanical design data.
 *
 * <p>
 * This class creates individual equipment datasheets in JSON format, suitable for import into document management
 * systems, PDM databases, or report generators. Each datasheet follows a standard format per equipment type with
 * sections for:
 * </p>
 * <ul>
 * <li>General information (tag, description, service)</li>
 * <li>Design conditions (pressure, temperature, materials)</li>
 * <li>Operating conditions (normal, minimum, maximum)</li>
 * <li>Mechanical design (dimensions, weight, wall thickness)</li>
 * <li>Nozzle/connection schedule</li>
 * <li>Performance data (equipment-specific)</li>
 * <li>Applicable codes and standards</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class EquipmentDatasheetGenerator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The process system. */
  private ProcessSystem processSystem;

  /** Project name. */
  private String projectName = "Not specified";

  /** Document number prefix. */
  private String documentPrefix = "DS";

  /** Revision. */
  private String revision = "A";

  /**
   * Creates a new EquipmentDatasheetGenerator for a process system.
   *
   * @param processSystem the process system to generate datasheets for
   */
  public EquipmentDatasheetGenerator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Sets the project name for datasheet headers.
   *
   * @param projectName the project name
   */
  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  /**
   * Sets the document number prefix.
   *
   * @param prefix document prefix (e.g., "DS", "EDS")
   */
  public void setDocumentPrefix(String prefix) {
    this.documentPrefix = prefix;
  }

  /**
   * Sets the revision level.
   *
   * @param revision revision string (e.g., "A", "B", "0")
   */
  public void setRevision(String revision) {
    this.revision = revision;
  }

  /**
   * Generates datasheets for all equipment in the process system.
   *
   * @return JSON string containing an array of equipment datasheets
   */
  public String generateAllDatasheets() {
    JsonArray datasheets = new JsonArray();
    JsonArray excluded = new JsonArray();
    boolean solvedCase = hasSolvedCase();
    collectDatasheets(processSystem, datasheets, excluded, solvedCase);

    JsonObject root = new JsonObject();
    root.addProperty("projectName", projectName);
    root.addProperty("revision", revision);
    root.addProperty("generatedAt", java.time.Instant.now().toString());
    root.addProperty("equipmentCount", datasheets.size());
    root.add("datasheets", datasheets);
    root.add("excludedSimulationObjects", excluded);
    root.addProperty("operatingCaseStatus", solvedCase ? "solved" : "unsolved");
    root.add("weightSummary", summarizeWeights(datasheets));

    Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    return gson.toJson(root);
  }

  /**
   * Traverses process modules and reserves one numbered sheet for each physical item.
   *
   * @param process flowsheet to visit
   * @param datasheets document register
   * @param excluded streams and solver objects excluded from the equipment register
   * @param solvedCase whether the simulation has a converged operating case
   */
  private void collectDatasheets(ProcessSystem process, JsonArray datasheets, JsonArray excluded, boolean solvedCase) {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof ModuleInterface) {
        collectDatasheets(((ModuleInterface) unit).getOperations(), datasheets, excluded, solvedCase);
      } else if (unit instanceof StreamInterface || unit instanceof Recycle || unit instanceof Adjuster
          || unit instanceof Calculator || unit instanceof SetPoint) {
        excluded.add(unit.getName());
      } else {
        datasheets.add(generateDatasheet(unit, datasheets.size() + 1, solvedCase));
      }
    }
  }

  /**
   * Reports known mass subtotals and reports full totals only when every item has a value.
   *
   * @param datasheets equipment document register
   * @return weight coverage and totals by weight condition
   */
  private static JsonObject summarizeWeights(JsonArray datasheets) {
    JsonObject summary = new JsonObject();
    for (String condition : new String[] {"dry", "operating", "hydrotest"}) {
      String key = condition + "Weight_kg";
      double subtotal = 0.0;
      int known = 0;
      for (int index = 0; index < datasheets.size(); index++) {
        JsonObject weight = datasheets.get(index).getAsJsonObject().getAsJsonObject("weights");
        if (!weight.get(key).isJsonNull()) {
          subtotal += weight.get(key).getAsDouble();
          known++;
        }
      }
      JsonObject total = new JsonObject();
      total.addProperty("knownCount", known);
      total.addProperty("missingCount", datasheets.size() - known);
      total.addProperty("knownSubtotal_kg", known > 0 ? subtotal : null);
      total.addProperty("total_kg", known > 0 && known == datasheets.size() ? subtotal : null);
      summary.add(condition, total);
    }
    return summary;
  }

  /**
   * Generates a datasheet for a single equipment item.
   *
   * @param equipment the process equipment
   * @param sequenceNumber sequence number for document numbering
   * @return JSON object representing the datasheet
   */
  public JsonObject generateDatasheet(ProcessEquipmentInterface equipment, int sequenceNumber) {
    return generateDatasheet(equipment, sequenceNumber, hasSolvedCase());
  }

  /**
   * Builds a controlled mechanical sheet from the same equipment and case snapshot as the process datasheet.
   *
   * @param equipment process equipment
   * @param sequenceNumber sheet number in the document register
   * @return preliminary mechanical datasheet
   */
  public MechanicalDatasheet generateMechanicalDatasheet(ProcessEquipmentInterface equipment, int sequenceNumber) {
    return new MechanicalDatasheet(generateDatasheet(equipment, sequenceNumber));
  }

  /**
   * Builds mechanical datasheets for each physical equipment entry in the process register.
   *
   * @return sheets in process register order
   */
  public List<MechanicalDatasheet> generateAllMechanicalDatasheets() {
    JsonArray sheets = JsonParser.parseString(generateAllDatasheets()).getAsJsonObject().getAsJsonArray("datasheets");
    List<MechanicalDatasheet> result = new java.util.ArrayList<MechanicalDatasheet>();
    for (com.google.gson.JsonElement sheet : sheets) {
      result.add(new MechanicalDatasheet(sheet.getAsJsonObject()));
    }
    return result;
  }

  /**
   * Checks for a completed, converged run before reporting operating values.
   *
   * @return true when the process has a run identity and is solved
   */
  private boolean hasSolvedCase() {
    return processSystem.getCalculationIdentifier() != null && processSystem.solved();
  }

  /**
   * Creates a document using the supplied reporting basis for operating data.
   *
   * @param equipment process equipment
   * @param sequenceNumber sheet sequence
   * @param solvedCase whether the operating values are supported by a solved case
   * @return equipment datasheet
   */
  private JsonObject generateDatasheet(ProcessEquipmentInterface equipment, int sequenceNumber, boolean solvedCase) {
    JsonObject ds = new JsonObject();

    // Header
    String docNumber = String.format(Locale.ROOT, "%s-%04d", documentPrefix, sequenceNumber);
    ds.addProperty("documentNumber", docNumber);
    ds.addProperty("projectName", projectName);
    ds.addProperty("revision", revision);
    ds.addProperty("equipmentTag", equipment.getName());
    ds.addProperty("equipmentType", equipment.getClass().getSimpleName());

    // General Information
    JsonObject general = new JsonObject();
    general.addProperty("tag", equipment.getName());
    general.addProperty("type", getEquipmentTypeDescription(equipment));
    general.addProperty("service", inferService(equipment));
    ds.add("generalInformation", general);

    JsonArray holds = new JsonArray();
    JsonObject weights = new JsonObject();
    weights.add("dryWeight_kg", JsonNull.INSTANCE);
    weights.add("operatingWeight_kg", JsonNull.INSTANCE);
    weights.add("hydrotestWeight_kg", JsonNull.INSTANCE);
    JsonObject design = new JsonObject();
    JsonArray standards = new JsonArray();
    try {
      MechanicalDesign mechDesign = equipment.getMechanicalDesign();
      if (mechDesign != null) {
        design.addProperty("designPressure_barg", roundTo(mechDesign.getMaxDesignPressure("barg"), 1));
        design.addProperty("designTemperature_C", roundTo(mechDesign.getDesignMaxTemperatureLimit("C"), 1));
        design.addProperty("minDesignTemperature_C", roundTo(mechDesign.getDesignMinTemperatureLimit("C"), 1));
        design.addProperty("corrosionAllowance_mm", roundTo(mechDesign.getCorrosionAllowance(), 1));
        MechanicalDesignResponse response = mechDesign.getResponse();
        if (response.getDesignStandard() != null) {
          standards.add(response.getDesignStandard());
        }
        design.addProperty("dryWeight_kg", positiveOrNull(mechDesign.getWeightTotal()));
        weights.addProperty("dryWeight_kg", positiveOrNull(mechDesign.getWeightTotal()));
        weights.addProperty("vesselShellWeight_kg", positiveOrNull(mechDesign.getWeigthVesselShell()));
        weights.addProperty("operatingWeight_kg", positiveOrNull(response.getOperatingWeight()));
        weights.addProperty("basis", "NeqSim mechanical design estimate");
        String wallUnit = MechanicalDesignData.wallThicknessUnit(mechDesign);
        if (wallUnit != null) {
          design.addProperty("wallThickness_mm",
              roundTo(mechDesign.getWallThickness() * ("m".equals(wallUnit) ? 1000.0 : 1.0), 1));
        }
        ds.add("mechanicalDesign", JsonParser.parseString(response.toJson()));
      }
    } catch (RuntimeException ex) {
      holds.add("Mechanical design unavailable (" + ex.getClass().getSimpleName() + ")");
    }
    for (String condition : new String[] {"dry", "operating", "hydrotest"}) {
      if (weights.get(condition + "Weight_kg").isJsonNull()) {
        holds.add("Confirm " + condition + " weight");
      }
    }
    ds.add("designConditions", design);
    ds.add("applicableCodes", standards);
    ds.add("weights", weights);

    // Operating Conditions from streams
    JsonObject operating = solvedCase ? buildOperatingConditions(equipment, holds) : new JsonObject();
    ds.add("operatingConditions", operating);

    // Equipment-specific performance data
    JsonObject performance = solvedCase ? buildPerformanceData(equipment, holds) : new JsonObject();
    ds.add("performanceData", performance);

    ds.add("inletStreams", describePorts(equipment.getInletStreams(), holds, solvedCase));
    ds.add("outletStreams", describePorts(equipment.getOutletStreams(), holds, solvedCase));
    if (!solvedCase) {
      holds.add("Solve the operating case before issuing process data");
    }
    ds.add("holds", holds);
    ds.addProperty("issueStatus", "PRELIMINARY");

    return ds;
  }

  /**
   * Captures availability and operating properties for every exposed material connection.
   *
   * @param streams equipment port streams
   * @param holds sheet-level missing data notes
   * @param solvedCase whether the operating values are supported by a solved case
   * @return connected streams in equipment port order
   */
  private JsonArray describePorts(List<StreamInterface> streams, JsonArray holds, boolean solvedCase) {
    JsonArray ports = new JsonArray();
    for (StreamInterface stream : streams) {
      if (stream == null) {
        continue;
      }
      JsonObject port = new JsonObject();
      port.addProperty("streamTag", stream.getName());
      if (solvedCase) {
        try {
          port.addProperty("pressure_bara", roundTo(stream.getPressure(), 4));
          port.addProperty("temperature_C", roundTo(stream.getTemperature("C"), 3));
          port.addProperty("massFlow_kgPerHr", roundTo(stream.getFlowRate("kg/hr"), 3));
        } catch (RuntimeException ex) {
          holds.add("Confirm operating data at stream " + stream.getName());
        }
      }
      ports.add(port);
    }
    return ports;
  }

  /**
   * Converts an unavailable or nonphysical equipment weight to a JSON null.
   *
   * @param weight weight in kg
   * @return positive finite weight or null
   */
  private static Double positiveOrNull(double weight) {
    return Double.isFinite(weight) && weight > 0.0 ? weight : null;
  }

  /**
   * Builds operating conditions section from equipment streams.
   *
   * @param equipment the equipment
   * @param holds sheet-level missing data notes
   * @return JSON object with operating conditions
   */
  private JsonObject buildOperatingConditions(ProcessEquipmentInterface equipment, JsonArray holds) {
    JsonObject ops = new JsonObject();

    try {
      // Try to get inlet conditions
      if (equipment instanceof Separator) {
        Separator sep = (Separator) equipment;
        StreamInterface feed = sep.getFeedStream();
        if (feed != null && feed.getFluid() != null) {
          ops.addProperty("inletPressure_bara", roundTo(feed.getPressure(), 1));
          ops.addProperty("inletTemperature_C", roundTo(feed.getTemperature() - 273.15, 1));
          ops.addProperty("inletFlowRate_kgPerHr", roundTo(feed.getFlowRate("kg/hr"), 0));
        }
      }

      if (equipment instanceof Compressor) {
        Compressor comp = (Compressor) equipment;
        ops.addProperty("inletPressure_bara", roundTo(comp.getInletStream().getPressure(), 1));
        ops.addProperty("outletPressure_bara", roundTo(comp.getOutletPressure(), 1));
        ops.addProperty("inletTemperature_C", roundTo(comp.getInletStream().getTemperature() - 273.15, 1));
        ops.addProperty("power_kW", roundTo(comp.getPower() / 1000.0, 1));
        ops.addProperty("polytropicEfficiency", roundTo(comp.getPolytropicEfficiency(), 3));
      }

      if (equipment instanceof ThrottlingValve) {
        ThrottlingValve valve = (ThrottlingValve) equipment;
        ops.addProperty("inletPressure_bara", roundTo(valve.getInletStream().getPressure(), 1));
        ops.addProperty("outletPressure_bara", roundTo(valve.getOutletPressure(), 1));
      }
    } catch (RuntimeException ex) {
      holds.add("Confirm equipment operating conditions");
    }

    return ops;
  }

  /**
   * Builds equipment-specific performance data.
   *
   * @param equipment the equipment
   * @param holds sheet-level missing data notes
   * @return JSON object with performance data
   */
  private JsonObject buildPerformanceData(ProcessEquipmentInterface equipment, JsonArray holds) {
    JsonObject perf = new JsonObject();

    try {
      if (equipment instanceof Separator) {
        Separator sep = (Separator) equipment;
        perf.addProperty("internalDiameter_m", roundTo(sep.getInternalDiameter(), 3));
        perf.addProperty("separatorLength_m", roundTo(sep.getSeparatorLength(), 2));
      }

      if (equipment instanceof Compressor) {
        Compressor comp = (Compressor) equipment;
        perf.addProperty("power_kW", roundTo(comp.getPower() / 1000.0, 1));
        perf.addProperty("polytropicHead_kJPerKg", roundTo(comp.getPolytropicHead() / 1000.0, 1));
        perf.addProperty("polytropicEfficiency", roundTo(comp.getPolytropicEfficiency(), 3));
        perf.addProperty("pressureRatio", roundTo(comp.getOutletPressure() / comp.getInletStream().getPressure(), 2));
      }

      if (equipment instanceof Heater) {
        Heater heater = (Heater) equipment;
        perf.addProperty("duty_kW", roundTo(heater.getDuty() / 1000.0, 1));
      }

      if (equipment instanceof Cooler) {
        Cooler cooler = (Cooler) equipment;
        perf.addProperty("duty_kW", roundTo(Math.abs(cooler.getDuty()) / 1000.0, 1));
      }

      if (equipment instanceof HeatExchanger) {
        perf.addProperty("duty_kW", roundTo(((HeatExchanger) equipment).getDuty("kW"), 1));
      }

      if (equipment instanceof Pump) {
        perf.addProperty("power_kW", roundTo(((Pump) equipment).getPower("kW"), 1));
      }

      if (equipment instanceof ThrottlingValve) {
        ThrottlingValve valve = (ThrottlingValve) equipment;
        perf.addProperty("pressureDrop_bar",
            roundTo(valve.getInletStream().getPressure() - valve.getOutletStream().getPressure(), 3));
        perf.addProperty("Cv_US", positiveOrNull(valve.getCv()));
      }
    } catch (RuntimeException ex) {
      holds.add("Confirm equipment performance data");
    }

    return perf;
  }

  /**
   * Gets a human-readable equipment type description.
   *
   * @param equipment the equipment
   * @return type description
   */
  private String getEquipmentTypeDescription(ProcessEquipmentInterface equipment) {
    String className = equipment.getClass().getSimpleName();
    if (className.contains("ThreePhaseSeparator")) {
      return "Three-Phase Separator";
    } else if (className.contains("Separator")) {
      return "Two-Phase Separator";
    } else if (className.contains("GasScrubber")) {
      return "Gas Scrubber";
    } else if (className.contains("Compressor")) {
      return "Centrifugal Compressor";
    } else if (className.contains("Pump")) {
      return "Centrifugal Pump";
    } else if (className.contains("Heater")) {
      return "Heater";
    } else if (className.contains("Cooler")) {
      return "Cooler";
    } else if (className.contains("HeatExchanger")) {
      return "Shell and Tube Heat Exchanger";
    } else if (className.contains("Valve")) {
      return "Control Valve";
    } else if (className.contains("Mixer")) {
      return "Mixing Tee";
    } else if (className.contains("Splitter")) {
      return "Flow Splitter";
    } else if (className.contains("Stream")) {
      return "Process Stream";
    } else {
      return className;
    }
  }

  /**
   * Infers the service description from equipment context.
   *
   * @param equipment the equipment
   * @return service description
   */
  private String inferService(ProcessEquipmentInterface equipment) {
    String name = equipment.getName().toLowerCase(Locale.ROOT);
    if (name.contains("hp") || name.contains("high pressure")) {
      return "High Pressure Service";
    } else if (name.contains("mp") || name.contains("medium pressure")) {
      return "Medium Pressure Service";
    } else if (name.contains("lp") || name.contains("low pressure")) {
      return "Low Pressure Service";
    } else if (name.contains("export")) {
      return "Export Service";
    } else if (name.contains("fuel")) {
      return "Fuel Gas Service";
    } else if (name.contains("water")) {
      return "Produced Water Service";
    } else {
      return "Process Service";
    }
  }

  /**
   * Rounds a double to specified decimal places.
   *
   * @param value the value
   * @param decimals number of decimal places
   * @return rounded value
   */
  private Double roundTo(double value, int decimals) {
    if (!Double.isFinite(value)) {
      return null;
    }
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }
}
