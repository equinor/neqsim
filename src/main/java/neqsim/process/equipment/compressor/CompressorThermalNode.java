package neqsim.process.equipment.compressor;

import java.io.Serializable;

/** A lumped temperature node in a {@link CompressorThermalModel}. */
public class CompressorThermalNode implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Physical role of a thermal node. */
  public enum NodeType {
    /** Process or utility fluid whose temperature is imposed by an external calculation. */
    FLUID_BOUNDARY,
    /** Compressor shaft or rotor metal. */
    SHAFT,
    /** Compressor impeller metal. */
    IMPELLER,
    /** Compressor casing metal. */
    CASING,
    /** Radial bearing or bearing housing. */
    RADIAL_BEARING,
    /** Thrust bearing or bearing housing. */
    THRUST_BEARING,
    /** Dry-gas or oil seal hardware. */
    SEAL,
    /** User-defined compressor location. */
    OTHER
  }

  private String id;
  private String description;
  private NodeType type;
  private double temperatureK;
  private double heatCapacityJPerK;
  private double heatGenerationW;
  private boolean fixedTemperature;

  /** No-argument constructor for JSON deserialization. */
  public CompressorThermalNode() {
  }

  /**
   * Create a thermal node.
   *
   * @param id unique node identifier
   * @param type physical node type
   * @param temperatureK initial temperature in K
   * @param heatCapacityJPerK lumped heat capacity in J/K
   * @param fixedTemperature whether the node is an imposed boundary temperature
   */
  public CompressorThermalNode(String id, NodeType type, double temperatureK, double heatCapacityJPerK,
      boolean fixedTemperature) {
    setId(id);
    setType(type);
    setTemperatureK(temperatureK);
    setHeatCapacityJPerK(heatCapacityJPerK);
    this.fixedTemperature = fixedTemperature;
  }

  /** @return node identifier */
  public String getId() {
    return id;
  }

  /** @param id unique non-empty node identifier */
  public void setId(String id) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("thermal node id must not be empty");
    }
    this.id = id;
  }

  /** @return node description */
  public String getDescription() {
    return description;
  }

  /** @param description node description */
  public void setDescription(String description) {
    this.description = description;
  }

  /** @return physical node type */
  public NodeType getType() {
    return type;
  }

  /** @param type physical node type */
  public void setType(NodeType type) {
    if (type == null) {
      throw new IllegalArgumentException("thermal node type must not be null");
    }
    this.type = type;
  }

  /** @return temperature in K */
  public double getTemperatureK() {
    return temperatureK;
  }

  /** @param temperatureK temperature in K */
  public void setTemperatureK(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperature must be finite and above 0 K");
    }
    this.temperatureK = temperatureK;
  }

  /** @return lumped heat capacity in J/K */
  public double getHeatCapacityJPerK() {
    return heatCapacityJPerK;
  }

  /** @param heatCapacityJPerK lumped heat capacity in J/K, zero only for fixed nodes */
  public void setHeatCapacityJPerK(double heatCapacityJPerK) {
    if (!Double.isFinite(heatCapacityJPerK) || heatCapacityJPerK < 0.0) {
      throw new IllegalArgumentException("heat capacity must be finite and non-negative");
    }
    this.heatCapacityJPerK = heatCapacityJPerK;
  }

  /** @return user-specified heat generation in W */
  public double getHeatGenerationW() {
    return heatGenerationW;
  }

  /** @param heatGenerationW user-specified heat generation in W */
  public void setHeatGenerationW(double heatGenerationW) {
    if (!Double.isFinite(heatGenerationW)) {
      throw new IllegalArgumentException("heat generation must be finite");
    }
    this.heatGenerationW = heatGenerationW;
  }

  /** @return true if the temperature is externally imposed */
  public boolean isFixedTemperature() {
    return fixedTemperature;
  }

  /** @param fixedTemperature whether the temperature is externally imposed */
  public void setFixedTemperature(boolean fixedTemperature) {
    this.fixedTemperature = fixedTemperature;
  }
}
