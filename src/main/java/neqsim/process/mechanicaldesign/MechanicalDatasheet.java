package neqsim.process.mechanicaldesign;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * Preliminary mechanical equipment datasheet from a process and mechanical design snapshot.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class MechanicalDatasheet {
  private final JsonObject equipment;
  private final JsonObject sections = new JsonObject();
  private final JsonArray nozzles = new JsonArray();
  private final Map<String, JsonObject> requirements = new LinkedHashMap<String, JsonObject>();

  /**
   * Builds a mechanical view of a process equipment sheet.
   *
   * @param equipment source equipment snapshot
   */
  MechanicalDatasheet(JsonObject equipment) {
    this.equipment = equipment;
    JsonObject design = object(equipment, "mechanicalDesign");
    JsonObject model = object(design, "designData");
    addQuantities("Design basis", object(model, "designBasis"));
    addQuantities("Dimensions", object(model, "geometry"));
    addQuantities("Mechanical operating data", object(model, "operatingConditions"));
    JsonObject weights = object(equipment, "weights");
    for (String key : new String[] {"vesselShellWeight", "dryWeight", "operatingWeight"}) {
      addRow("Weights", field(key, weights.get(key + "_kg"), "kg", "NeqSim equipment mechanical design", "ESTIMATE"));
    }
    require("Weights", "hydrotestWeight", "Hydrotest assembly weight", "kg");
    require("Weights", "shipping_and_lifting", "Shipping and lifting weights, centre of gravity and boundaries", "");
    require("Project basis", "drawing", "Governing P&ID or equipment drawing", "");
    require("Project basis", "cases", "Governing design cases, including upset and external pressure", "");
    require("Project basis", "code", "Governing mechanical code, edition and jurisdiction", "");
    require("Materials and fabrication", "materials", "Material specification and service compatibility", "");
    require("Loads and supports", "loads", "Site, transport, environmental and equipment loads", "");
    require("Loads and supports", "supports", "Support arrangement, reactions and foundation interfaces", "");
    require("Inspection and testing", "inspection", "Inspection, examination and acceptance basis", "");
    require("Inspection and testing", "test", "Test method, pressure, temperature and test boundary", "");
    require("Inspection and testing", "documentation", "Vendor documents, certification and release requirements", "");
    if (design.has("separatorType")) {
      addSeparator(design);
    } else {
      addFamilyRequirements(cell(model.get("geometryKind")));
    }
  }

  /**
   * Stores a controlled project or vendor input against a listed requirement. Model estimates remain intact.
   *
   * @param key requirement identifier listed in the sheet
   * @param value controlled value or document reference
   * @param source identifiable source and revision
   * @return this sheet
   */
  public MechanicalDatasheet setRequirement(String key, String value, String source) {
    JsonObject item = requirements.get(key);
    if (item == null || "hydrotestWeight".equals(key)) {
      throw new IllegalArgumentException("Requirement does not accept text input: " + key);
    }
    String controlledValue = requiredText(value);
    item.addProperty("source", requiredText(source));
    item.addProperty("value", controlledValue);
    item.addProperty("status", "SUPPLIED");
    return this;
  }

  /**
   * Adds a hydrotest mass estimate for a defined assembly and filled volume.
   *
   * @param assemblyDryMassKg dry mass of the test assembly in kg
   * @param filledVolumeM3 volume included in the test fill in cubic metres
   * @param densityKgM3 test fluid density in kg/m3 at test conditions
   * @param source source defining the test assembly, volume and fluid basis
   * @return this sheet
   */
  public MechanicalDatasheet setHydrotestWeightBasis(double assemblyDryMassKg, double filledVolumeM3,
      double densityKgM3, String source) {
    double mass = assemblyDryMassKg + filledVolumeM3 * densityKgM3;
    if (!(assemblyDryMassKg > 0.0) || !(filledVolumeM3 > 0.0) || !(densityKgM3 > 0.0) || !Double.isFinite(mass)) {
      throw new IllegalArgumentException("Supply finite positive test assembly mass, filled volume and fluid density");
    }
    JsonObject item = requirements.get("hydrotestWeight");
    item.addProperty("source", requiredText(source));
    item.addProperty("value", mass);
    item.addProperty("status", "ESTIMATE");
    JsonObject basis = new JsonObject();
    basis.addProperty("assemblyDryMass_kg", assemblyDryMassKg);
    basis.addProperty("filledVolume_m3", filledVolumeM3);
    basis.addProperty("testFluidDensity_kg_m3", densityKgM3);
    item.add("calculationBasis", basis);
    return this;
  }

  /**
   * Exports the mechanical datasheet, including the original process ports and equipment-specific model results.
   *
   * @return independent sheet object
   */
  public JsonObject toJsonObject() {
    JsonObject document = new JsonObject();
    document.addProperty("schemaVersion", "1.0");
    document.addProperty("issueStatus", "PRELIMINARY");
    for (String key : new String[] {"equipmentTag", "equipmentType", "projectName", "documentNumber", "revision",
        "inletStreams", "outletStreams", "performanceData", "mechanicalDesign"}) {
      if (equipment.has(key)) {
        document.add(key, equipment.get(key).deepCopy());
      }
    }
    JsonArray holds = new JsonArray();
    if (equipment.has("holds")) {
      for (JsonElement entry : equipment.getAsJsonArray("holds")) {
        if (!"Confirm hydrotest weight".equals(entry.getAsString())) {
          holds.add(entry);
        }
      }
    }
    document.add("processHolds", holds);
    document.add("sections", sections.deepCopy());
    document.add("separatorNozzles", nozzles.deepCopy());
    return document;
  }

  /**
   * Renders the sheet as strict, human-readable JSON.
   *
   * @return serialized mechanical datasheet
   */
  public String toJson() {
    return new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(toJsonObject());
  }

  /**
   * Renders the controlled sections and the separator nozzle schedule as Markdown.
   *
   * @return readable mechanical datasheet
   */
  public String toMarkdown() {
    StringBuilder text = new StringBuilder("# ").append(cell(equipment.get("equipmentTag")))
        .append(" mechanical datasheet\n\n");
    text.append("**PRELIMINARY** / ").append(cell(equipment.get("documentNumber"))).append(" / Rev ")
        .append(cell(equipment.get("revision"))).append(" / ").append(cell(equipment.get("projectName"))).append('\n');
    for (Map.Entry<String, JsonElement> section : sections.entrySet()) {
      text.append("\n## ").append(section.getKey()).append("\n\n")
          .append("| Field | Value | Unit | Status | Basis |\n|---|---|---|---|---|\n");
      for (JsonElement element : section.getValue().getAsJsonArray()) {
        JsonObject row = element.getAsJsonObject();
        text.append("| ").append(cell(row.get("label"))).append(" | ").append(cell(row.get("value"))).append(" | ")
            .append(cell(row.get("unit"))).append(" | ").append(cell(row.get("status"))).append(" | ")
            .append(cell(row.get("source"))).append(" |\n");
      }
    }
    if (nozzles.size() > 0) {
      text.append("\n## Separator nozzle schedule\n\n")
          .append("| Service | Stream | Bore (mm) | Status |\n|---|---|---|---|\n");
      for (JsonElement element : nozzles) {
        JsonObject row = element.getAsJsonObject();
        text.append("| ").append(cell(row.get("service"))).append(" | ").append(cell(row.get("streamTag")))
            .append(" | ").append(cell(row.get("screeningBore_mm"))).append(" | ").append(cell(row.get("status")))
            .append(" |\n");
      }
    }
    return text.toString();
  }

  /**
   * Adds design and verification requirements for the equipment family supplied by the native snapshot.
   *
   * @param kind mechanical envelope kind
   */
  private void addFamilyRequirements(String kind) {
    switch (kind) {
    case "compressor_envelope":
    case "pump_envelope":
      require("Rotating equipment", "rotor", "Rated performance envelope, driver and shaft train", "");
      require("Rotating equipment", "seals", "Seals, bearings, auxiliaries and mechanical limits", "");
      require("Rotating equipment", "vibration", "Vibration, shop test and acceptance criteria", "");
      break;
    case "heat_exchanger_envelope":
      require("Heat exchanger", "sides", "Design and test conditions by side and differential cases", "");
      require("Heat exchanger", "construction", "Exchanger type, bundle or core, joints and materials by side", "");
      break;
    case "valve_envelope":
      require("Valve", "valve_trim", "Body, ends, trim, shutoff class and actuator basis", "");
      break;
    case "column_envelope":
      require("Column", "column_internals", "Tray or packing arrangements, internals and support loads", "");
      break;
    case "straight_pipe_section":
      require("Pipeline", "line_design", "Line list, corrosion basis, route and governing mechanical load cases", "");
      break;
    default:
      require("Equipment-specific basis", "vendor_scope", "Equipment construction and vendor technical scope", "");
      break;
    }
  }

  /**
   * Adds separator-specific geometry, internals, levels and nozzle screening data.
   *
   * @param design separator mechanical response
   */
  private void addSeparator(JsonObject design) {
    for (String level : new String[] {"llll", "lll", "nll", "hll", "hhll", "lil", "nil", "hil", "weirHeight"}) {
      addRow("Separator internals and levels",
          field(level, design.get(level), "m", "Separator mechanical design", "ESTIMATE"));
    }
    addRow("Separator internals and levels",
        field("demister", design.get("demisterType"), "", "Separator mechanical design", "MODEL"));
    addRow("Dimensions", field("orientation", design.get("orientation"), "", "Separator mechanical design", "MODEL"));
    require("Separator pressure boundary", "shell_plate", "Selected nominal shell plate and tolerances", "");
    require("Separator pressure boundary", "heads", "Head profile, material and required and nominal thickness", "");
    require("Separator pressure boundary", "mawp_mdmt", "MAWP, external pressure rating and MDMT", "");
    require("Separator pressure boundary", "welds", "Joint details, examination, joint efficiency and heat treatment",
        "");
    require("Separator pressure boundary", "calculation", "Pressure, fatigue and local stress calculations", "");
    require("Separator internals and levels", "internals", "Inlet device, weir and demister construction and loads",
        "");
    require("Nozzle details", "additional_nozzles", "Relief, drains, vents, manways and instrument connections", "");
    boolean threePhase = "three-phase".equals(cell(design.get("separatorType")));
    addNozzle("inlet", "Multiphase inlet", equipment.getAsJsonArray("inletStreams"), 0,
        design.get("inletNozzleDiameter"));
    JsonArray outlet = equipment.getAsJsonArray("outletStreams");
    addNozzle("gas", "Gas outlet", outlet, 0, design.get("gasOutletNozzleDiameter"));
    addNozzle("liquid", threePhase ? "Oil outlet" : "Liquid outlet", outlet, 1,
        design.get("liquidOutletNozzleDiameter"));
    if (threePhase) {
      addNozzle("water", "Water outlet", outlet, 2, design.get("waterOutletNozzleDiameter"));
    }
  }

  /**
   * Creates a preliminary nozzle row and the associated controlled connection requirement.
   *
   * @param key role identifier
   * @param service nozzle service
   * @param ports process port list
   * @param index expected process port position
   * @param diameter native diameter in mm
   */
  private void addNozzle(String key, String service, JsonArray ports, int index, JsonElement diameter) {
    JsonObject nozzle = new JsonObject();
    nozzle.addProperty("role", key);
    nozzle.addProperty("service", service);
    nozzle.add("streamTag",
        ports != null && ports.size() > index && (index > 0 || ports.size() == 1 || !"inlet".equals(key))
            ? ports.get(index).getAsJsonObject().get("streamTag")
            : JsonNull.INSTANCE);
    boolean screened = diameter != null && !diameter.isJsonNull() && diameter.getAsDouble() > 0.0;
    nozzle.add("screeningBore_mm", screened ? diameter : JsonNull.INSTANCE);
    nozzle.addProperty("status", screened ? "ESTIMATE" : "HOLD");
    nozzles.add(nozzle);
    require("Nozzle details", "nozzle." + key,
        service + " tag, size, rating, facing, orientation, projection, reinforcement and allowable loads", "");
  }

  /**
   * Copies unit-bearing normalized design snapshot quantities into a section.
   *
   * @param name section title
   * @param quantities source quantities
   */
  private void addQuantities(String name, JsonObject quantities) {
    for (Map.Entry<String, JsonElement> entry : quantities.entrySet()) {
      JsonObject quantity = entry.getValue().getAsJsonObject();
      String modelStatus = cell(quantity.get("status"));
      String status = "MODEL";
      if ("configured_or_default".equals(modelStatus)) {
        status = "CONFIGURED OR DEFAULT";
      } else if ("equipment_input".equals(modelStatus)) {
        status = "SUPPLIED";
      }
      addRow(name, field(entry.getKey(), quantity.get("value"), cell(quantity.get("unit")),
          cell(quantity.get("source")), status));
    }
  }

  /**
   * Defines a controlled requirement for project or vendor input.
   *
   * @param section section title
   * @param key unique requirement identifier
   * @param label displayed label
   * @param unit expected unit
   */
  private void require(String section, String key, String label, String unit) {
    JsonObject row = field(label, JsonNull.INSTANCE, unit, "Project or vendor input required", "HOLD");
    row.addProperty("key", key);
    requirements.put(key, row);
    addRow(section, row);
  }

  /**
   * Appends a field to its section.
   *
   * @param section section title
   * @param row field object
   */
  private void addRow(String section, JsonObject row) {
    if (!sections.has(section)) {
      sections.add(section, new JsonArray());
    }
    sections.getAsJsonArray(section).add(row);
  }

  /**
   * Builds a field with status and provenance.
   *
   * @param label displayed attribute
   * @param value field value
   * @param unit field unit
   * @param source value provenance
   * @param status status for the available value
   * @return labeled field
   */
  private static JsonObject field(String label, JsonElement value, String unit, String source, String status) {
    JsonObject row = new JsonObject();
    row.addProperty("label", label);
    boolean present = value != null && !value.isJsonNull();
    row.add("value", present ? value : JsonNull.INSTANCE);
    row.addProperty("unit", unit);
    row.addProperty("status", present ? status : "HOLD");
    row.addProperty("source", source);
    return row;
  }

  /**
   * Reads an optional nested object.
   *
   * @param parent source object
   * @param key property name
   * @return captured object or empty object
   */
  private static JsonObject object(JsonObject parent, String key) {
    return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
  }

  /**
   * Gets a nonblank controlled input.
   *
   * @param text input text
   * @return trimmed input
   */
  private static String requiredText(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("Provide a nonblank controlled value and source");
    }
    return text.trim();
  }

  /**
   * Formats a scalar for a Markdown cell.
   *
   * @param value field value
   * @return escaped display text
   */
  private static String cell(JsonElement value) {
    return value == null || value.isJsonNull() ? "—"
        : value.getAsString().replace("|", "\\|").replaceAll("[\\r\\n]+", " ");
  }
}
