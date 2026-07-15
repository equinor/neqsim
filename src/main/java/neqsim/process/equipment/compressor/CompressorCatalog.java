package neqsim.process.equipment.compressor;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.compressor.CompressorThermalLink.Mechanism;
import neqsim.process.equipment.compressor.CompressorThermalNode.NodeType;

/**
 * JSON-backed catalog of selectable compressor definitions.
 *
 * <p>
 * Built-in entries are generic screening templates, not vendor performance guarantees. Users can
 * add private OEM definitions, serialize the catalog and select an entry by stable identifier.
 * </p>
 */
public class CompressorCatalog implements Serializable {
  private static final long serialVersionUID = 1000L;

  private String name = "compressor catalog";
  private final Map<String, CompressorCatalogEntry> entries = new LinkedHashMap<String, CompressorCatalogEntry>();

  /** Create an empty catalog. */
  public CompressorCatalog() {
  }

  /** @param name catalog name */
  public CompressorCatalog(String name) {
    setName(name);
  }

  /** @return catalog name */
  public String getName() {
    return name;
  }

  /** @param name non-empty catalog name */
  public void setName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("catalog name must not be empty");
    }
    this.name = name;
  }

  /**
   * Add or replace a catalog entry.
   *
   * @param entry entry to add or replace
   * @return this catalog
   */
  public CompressorCatalog add(CompressorCatalogEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("catalog entry must not be null");
    }
    entries.put(entry.getId(), entry);
    return this;
  }

  /**
   * Get a catalog entry.
   *
   * @param id entry id
   * @return matching entry or null
   */
  public CompressorCatalogEntry get(String id) {
    return entries.get(id);
  }

  /** @return entry ids in insertion order */
  public List<String> getIds() {
    return new ArrayList<String>(entries.keySet());
  }

  /** @return immutable view of entries */
  public Map<String, CompressorCatalogEntry> getEntries() {
    return Collections.unmodifiableMap(entries);
  }

  /**
   * Apply a catalog entry to a compressor.
   *
   * @param id catalog entry id
   * @param compressor target compressor
   */
  public void apply(String id, Compressor compressor) {
    CompressorCatalogEntry entry = entries.get(id);
    if (entry == null) {
      throw new IllegalArgumentException("no compressor catalog entry named '" + id + "'; available: " + getIds());
    }
    entry.applyTo(compressor);
  }

  /** @return round-trippable pretty-printed JSON */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(this);
  }

  /**
   * Deserialize a compressor catalog.
   *
   * @param json serialized catalog
   * @return deserialized catalog
   */
  public static CompressorCatalog fromJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      throw new IllegalArgumentException("compressor catalog JSON must not be empty");
    }
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().fromJson(json, CompressorCatalog.class);
  }

  /**
   * Save this catalog as JSON.
   *
   * @param path output file path
   * @throws IOException if writing fails
   */
  public void save(String path) throws IOException {
    Files.write(Paths.get(path), toJson().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Load a catalog from JSON.
   *
   * @param path catalog file path
   * @return loaded catalog
   * @throws IOException if reading fails
   */
  public static CompressorCatalog load(String path) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    return fromJson(new String(bytes, StandardCharsets.UTF_8));
  }

  /**
   * Create generic centrifugal-compressor templates with explicitly documented calibration inputs.
   *
   * @return catalog containing single-stage and multistage dry-gas-seal templates
   */
  public static CompressorCatalog createDefaultCatalog() {
    CompressorCatalog catalog = new CompressorCatalog("NeqSim generic compressor templates");
    catalog.add(createGenericEntry("generic-centrifugal-single-stage", 1, 3.0e5, 120.0));
    catalog.add(createGenericEntry("generic-centrifugal-multistage", 5, 8.0e5, 220.0));
    return catalog;
  }

  private static CompressorCatalogEntry createGenericEntry(String id, int stages, double rotorHeatCapacity,
      double rotorGasConductance) {
    CompressorThermalModel model = new CompressorThermalModel(id + " thermal network");
    model.addNode(boundary(CompressorThermalModel.SUCTION_GAS, 293.15));
    model.addNode(boundary(CompressorThermalModel.DISCHARGE_GAS, 373.15));
    model.addNode(boundary(CompressorThermalModel.SEAL_GAS, 313.15));
    model.addNode(boundary(CompressorThermalModel.LUBE_OIL, 320.65));
    model.addNode(boundary(CompressorThermalModel.AMBIENT, 298.15));
    model.addNode(node(CompressorThermalModel.INLET_SHAFT, NodeType.SHAFT, 313.15, 0.35 * rotorHeatCapacity));
    model.addNode(node(CompressorThermalModel.IMPELLER, NodeType.IMPELLER, 333.15, 0.65 * rotorHeatCapacity));
    model.addNode(node(CompressorThermalModel.CASING, NodeType.CASING, 323.15, 1.2 * rotorHeatCapacity));
    model.addNode(node(CompressorThermalModel.DRY_GAS_SEAL, NodeType.SEAL, 313.15, 5.0e4));
    model.addNode(node(CompressorThermalModel.RADIAL_BEARING, NodeType.RADIAL_BEARING, 323.15, 8.0e4));
    model.addNode(node(CompressorThermalModel.THRUST_BEARING, NodeType.THRUST_BEARING, 323.15, 8.0e4));

    link(model, CompressorThermalModel.SUCTION_GAS, CompressorThermalModel.INLET_SHAFT, 80.0, Mechanism.CONVECTION);
    link(model, CompressorThermalModel.DISCHARGE_GAS, CompressorThermalModel.IMPELLER, rotorGasConductance,
        Mechanism.CONVECTION);
    link(model, CompressorThermalModel.INLET_SHAFT, CompressorThermalModel.IMPELLER, 350.0, Mechanism.CONDUCTION);
    link(model, CompressorThermalModel.IMPELLER, CompressorThermalModel.CASING, 90.0, Mechanism.EFFECTIVE);
    link(model, CompressorThermalModel.CASING, CompressorThermalModel.AMBIENT, 45.0, Mechanism.CONVECTION);
    link(model, CompressorThermalModel.INLET_SHAFT, CompressorThermalModel.DRY_GAS_SEAL, 140.0, Mechanism.CONDUCTION);
    link(model, CompressorThermalModel.DRY_GAS_SEAL, CompressorThermalModel.SEAL_GAS, 70.0, Mechanism.CONVECTION);
    link(model, CompressorThermalModel.INLET_SHAFT, CompressorThermalModel.RADIAL_BEARING, 120.0, Mechanism.CONDUCTION);
    link(model, CompressorThermalModel.INLET_SHAFT, CompressorThermalModel.THRUST_BEARING, 100.0, Mechanism.CONDUCTION);
    link(model, CompressorThermalModel.RADIAL_BEARING, CompressorThermalModel.LUBE_OIL, 350.0, Mechanism.CONVECTION);
    link(model, CompressorThermalModel.THRUST_BEARING, CompressorThermalModel.LUBE_OIL, 300.0, Mechanism.CONVECTION);
    model.setCompressionHeatFractionToImpeller(0.002);

    CompressorCatalogEntry entry = new CompressorCatalogEntry(id, "NeqSim", "generic template");
    entry.setNumberOfStages(stages);
    entry.setThermalModel(model);
    entry
        .requireParameter("thermal conductances W/K",
            "Fit each link from OEM geometry, heat-transfer analysis or measured temperatures")
        .requireParameter("node heat capacities J/K",
            "Calculate from participating metal mass and temperature-dependent heat capacity")
        .requireParameter("bearing losses kW", "Use vendor loss curves, oil heat balance or a validated bearing model")
        .requireParameter("seal and lube-oil temperatures C",
            "Use measured supply and return temperatures at the operating condition")
        .requireParameter("compression heat fraction",
            "Fit the rotor heat pickup; the generic value is only a screening assumption")
        .addReference("API 617", "Axial and Centrifugal Compressors and Expander-compressors")
        .addReference("API 692", "Dry Gas Sealing Systems for Compressors and Expanders")
        .addReference("model equation", "Lumped thermal capacitance energy balance solved as a resistance network");
    return entry;
  }

  private static CompressorThermalNode boundary(String id, double temperatureK) {
    return new CompressorThermalNode(id, NodeType.FLUID_BOUNDARY, temperatureK, 0.0, true);
  }

  private static CompressorThermalNode node(String id, NodeType type, double temperatureK, double heatCapacityJPerK) {
    return new CompressorThermalNode(id, type, temperatureK, heatCapacityJPerK, false);
  }

  private static void link(CompressorThermalModel model, String from, String to, double conductance,
      Mechanism mechanism) {
    model.addLink(new CompressorThermalLink(from, to, conductance, mechanism));
  }
}
