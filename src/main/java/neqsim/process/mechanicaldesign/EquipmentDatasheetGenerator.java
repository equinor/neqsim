package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Generates structured equipment datasheets from mechanical design data.
 *
 * <p>
 * This class creates individual equipment datasheets in JSON format, suitable for import into
 * document management systems, PDM databases, or report generators. Each datasheet follows a
 * standard format per equipment type with sections for:
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
    int seq = 1;

    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      try {
        JsonObject ds = generateDatasheet(unit, seq);
        if (ds != null) {
          datasheets.add(ds);
          seq++;
        }
      } catch (Exception e) {
        // Skip equipment that fails
      }
    }

    JsonObject root = new JsonObject();
    root.addProperty("projectName", projectName);
    root.addProperty("revision", revision);
    root.addProperty("generatedAt", java.time.Instant.now().toString());
    root.addProperty("equipmentCount", datasheets.size());
    root.add("datasheets", datasheets);

    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(root);
  }

  /**
   * Generates a datasheet for a single equipment item.
   *
   * @param equipment the process equipment
   * @param sequenceNumber sequence number for document numbering
   * @return JSON object representing the datasheet, or null if not applicable
   */
  public JsonObject generateDatasheet(ProcessEquipmentInterface equipment, int sequenceNumber) {
    JsonObject ds = new JsonObject();

    // Header
    String docNumber = String.format("%s-%04d", documentPrefix, sequenceNumber);
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

    // Design Conditions from mechanical design
    JsonObject design = new JsonObject();
    try {
      MechanicalDesign mechDesign = equipment.getMechanicalDesign();
      if (mechDesign != null) {
        design.addProperty("designPressure_barg",
            roundTo(mechDesign.getMaxDesignPressure() - 1.01325, 1));
        design.addProperty("designTemperature_C",
            roundTo(mechDesign.getDesignMaxTemperatureLimit(), 0));
        design.addProperty("minDesignTemperature_C",
            roundTo(mechDesign.getDesignMinTemperatureLimit(), 0));
        design.addProperty("corrosionAllowance_mm",
            roundTo(mechDesign.getCorrosionAllowance() * 1000.0, 1));

        // Weight
        double shellWeight = mechDesign.getWeigthVesselShell();
        design.addProperty("dryWeight_kg", roundTo(shellWeight, 0));
        design.addProperty("wallThickness_mm", roundTo(mechDesign.getWallThickness() * 1000.0, 1));
      }
    } catch (Exception e) {
      // No mechanical design available
    }
    ds.add("designConditions", design);

    // Operating Conditions from streams
    JsonObject operating = buildOperatingConditions(equipment);
    ds.add("operatingConditions", operating);

    // Equipment-specific performance data
    JsonObject performance = buildPerformanceData(equipment);
    ds.add("performanceData", performance);

    // Applicable standards
    JsonArray standards = new JsonArray();
    standards.add("ASME Section VIII Div. 1 (Pressure Vessels)");
    standards.add("ASME B31.3 (Process Piping)");
    if (equipment instanceof Separator) {
      standards.add("NORSOK P-002 (Process System Design)");
      standards.add("API 12J (Specification for Oil and Gas Separators)");
    } else if (equipment instanceof Compressor) {
      standards.add("API 617 (Axial and Centrifugal Compressors)");
    } else if (equipment instanceof Pump) {
      standards.add("API 610 (Centrifugal Pumps)");
    }
    ds.add("applicableCodes", standards);

    return ds;
  }

  /**
   * Builds operating conditions section from equipment streams.
   *
   * @param equipment the equipment
   * @return JSON object with operating conditions
   */
  private JsonObject buildOperatingConditions(ProcessEquipmentInterface equipment) {
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
        ops.addProperty("inletTemperature_C",
            roundTo(comp.getInletStream().getTemperature() - 273.15, 1));
        ops.addProperty("power_kW", roundTo(comp.getPower() / 1000.0, 1));
        ops.addProperty("polytropicEfficiency", roundTo(comp.getPolytropicEfficiency(), 3));
      }

      if (equipment instanceof ThrottlingValve) {
        ThrottlingValve valve = (ThrottlingValve) equipment;
        ops.addProperty("inletPressure_bara", roundTo(valve.getInletStream().getPressure(), 1));
        ops.addProperty("outletPressure_bara", roundTo(valve.getOutletPressure(), 1));
      }
    } catch (Exception e) {
      // Cannot extract operating conditions
    }

    return ops;
  }

  /**
   * Builds equipment-specific performance data.
   *
   * @param equipment the equipment
   * @return JSON object with performance data
   */
  private JsonObject buildPerformanceData(ProcessEquipmentInterface equipment) {
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
        perf.addProperty("pressureRatio",
            roundTo(comp.getOutletPressure() / comp.getInletStream().getPressure(), 2));
      }

      if (equipment instanceof Heater) {
        Heater heater = (Heater) equipment;
        perf.addProperty("duty_kW", roundTo(heater.getDuty() / 1000.0, 1));
      }

      if (equipment instanceof Cooler) {
        Cooler cooler = (Cooler) equipment;
        perf.addProperty("duty_kW", roundTo(Math.abs(cooler.getDuty()) / 1000.0, 1));
      }
    } catch (Exception e) {
      // Cannot extract performance data
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
    String name = equipment.getName().toLowerCase();
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
  private double roundTo(double value, int decimals) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return value;
    }
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }
}
