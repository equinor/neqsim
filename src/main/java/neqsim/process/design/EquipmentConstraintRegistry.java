package neqsim.process.design;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * Registry for default equipment capacity constraints.
 *
 * <p>
 * This class manages default constraint configurations for different equipment types and provides
 * factory methods for creating constraints. It enables consistent constraint handling across the
 * optimization framework.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * EquipmentConstraintRegistry registry = EquipmentConstraintRegistry.getInstance();
 * 
 * // Get default constraints for a separator
 * List&lt;CapacityConstraint&gt; constraints = registry.getDefaultConstraints(separator);
 * 
 * // Register custom constraint
 * registry.registerCustomConstraint("Separator", new CapacityConstraint(...));
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EquipmentConstraintRegistry {

  private static EquipmentConstraintRegistry instance;

  private Map<String, List<ConstraintTemplate>> defaultConstraints = new HashMap<>();
  private Map<String, List<CapacityConstraint>> customConstraints = new HashMap<>();

  /**
   * Private constructor for singleton pattern.
   */
  private EquipmentConstraintRegistry() {
    initializeDefaults();
  }

  /**
   * Get the singleton instance of the registry.
   *
   * @return the registry instance
   */
  public static synchronized EquipmentConstraintRegistry getInstance() {
    if (instance == null) {
      instance = new EquipmentConstraintRegistry();
    }
    return instance;
  }

  /**
   * Initialize default constraint templates for each equipment type.
   */
  private void initializeDefaults() {
    // Separator defaults
    List<ConstraintTemplate> separatorConstraints = new ArrayList<>();
    separatorConstraints.add(new ConstraintTemplate("gasLoadFactor", "Gas Load Factor (K)", "m/s",
        "Current gas load factor must be below design K-factor"));
    separatorConstraints.add(new ConstraintTemplate("liquidResidenceTime", "Liquid Residence Time",
        "min", "Liquid residence time must meet minimum requirement"));
    defaultConstraints.put("Separator", separatorConstraints);

    // Compressor defaults
    List<ConstraintTemplate> compressorConstraints = new ArrayList<>();
    compressorConstraints.add(new ConstraintTemplate("surgeLine", "Surge Margin", "%",
        "Operating point must be above surge line"));
    compressorConstraints.add(new ConstraintTemplate("stonewallLine", "Stonewall Margin", "%",
        "Operating point must be below stonewall line"));
    compressorConstraints.add(new ConstraintTemplate("maxSpeed", "Maximum Speed", "rpm",
        "Speed must be below maximum design speed"));
    compressorConstraints.add(new ConstraintTemplate("maxPower", "Maximum Power", "kW",
        "Power must be below driver capacity"));
    defaultConstraints.put("Compressor", compressorConstraints);

    // Valve defaults
    List<ConstraintTemplate> valveConstraints = new ArrayList<>();
    valveConstraints.add(new ConstraintTemplate("maxOpening", "Max Valve Opening", "%",
        "Valve opening must be below maximum design opening"));
    valveConstraints.add(new ConstraintTemplate("maxCv", "Max Cv Capacity", "",
        "Required Cv must be below valve rated Cv"));
    defaultConstraints.put("Valve", valveConstraints);

    // Pipeline defaults
    List<ConstraintTemplate> pipelineConstraints = new ArrayList<>();
    pipelineConstraints.add(new ConstraintTemplate("maxVelocity", "Max Velocity", "m/s",
        "Flow velocity must be below erosional/design velocity limit"));
    pipelineConstraints.add(new ConstraintTemplate("maxPressureDrop", "Max Pressure Drop", "bar",
        "Pressure drop must be within acceptable limits"));
    pipelineConstraints.add(new ConstraintTemplate("fivLOF", "FIV Likelihood of Failure", "",
        "Flow-induced vibration risk must be acceptable"));
    defaultConstraints.put("Pipeline", pipelineConstraints);

    // Heater defaults
    List<ConstraintTemplate> heaterConstraints = new ArrayList<>();
    heaterConstraints.add(new ConstraintTemplate("maxDuty", "Max Duty", "MW",
        "Heat duty must be below design capacity"));
    heaterConstraints.add(new ConstraintTemplate("maxOutletTemperature", "Max Outlet Temperature",
        "°C", "Outlet temperature must be below metallurgy limit"));
    defaultConstraints.put("Heater", heaterConstraints);
  }

  /**
   * Get the equipment type string for a given equipment instance.
   *
   * @param equipment the equipment to classify
   * @return equipment type string
   */
  public String getEquipmentType(ProcessEquipmentInterface equipment) {
    if (equipment instanceof Compressor) {
      return "Compressor";
    } else if (equipment instanceof Separator) {
      return "Separator";
    } else if (equipment instanceof ThrottlingValve) {
      return "Valve";
    } else if (equipment instanceof PipeBeggsAndBrills) {
      return "Pipeline";
    } else if (equipment instanceof Heater) {
      return "Heater";
    }
    return "Unknown";
  }

  /**
   * Get the available constraint types for an equipment type.
   *
   * @param equipmentType equipment type (e.g., "Separator", "Compressor")
   * @return list of constraint templates
   */
  public List<ConstraintTemplate> getConstraintTemplates(String equipmentType) {
    return defaultConstraints.getOrDefault(equipmentType, new ArrayList<>());
  }

  /**
   * Get the available constraint types for an equipment instance.
   *
   * @param equipment the equipment
   * @return list of constraint templates
   */
  public List<ConstraintTemplate> getConstraintTemplates(ProcessEquipmentInterface equipment) {
    return getConstraintTemplates(getEquipmentType(equipment));
  }

  /**
   * Create a capacity constraint for a specific equipment and constraint type.
   *
   * @param equipment the equipment
   * @param constraintType the constraint type (e.g., "gasLoadFactor", "maxVelocity")
   * @param maxValue the maximum allowed value
   * @return the capacity constraint
   */
  public CapacityConstraint createConstraint(ProcessEquipmentInterface equipment,
      String constraintType, double maxValue) {
    // Find the template
    String equipmentType = getEquipmentType(equipment);
    ConstraintTemplate template = findTemplate(equipmentType, constraintType);

    String unit = template != null ? template.getUnit() : "";
    String description = template != null ? template.getDescription() : "";

    // Use builder pattern
    return new CapacityConstraint(constraintType, unit, CapacityConstraint.ConstraintType.SOFT)
        .setMaxValue(maxValue).setDescription(description);
  }

  /**
   * Register a custom constraint for an equipment type.
   *
   * @param equipmentType the equipment type
   * @param constraint the custom constraint
   */
  public void registerCustomConstraint(String equipmentType, CapacityConstraint constraint) {
    customConstraints.computeIfAbsent(equipmentType, k -> new ArrayList<>()).add(constraint);
  }

  /**
   * Get custom constraints registered for an equipment type.
   *
   * @param equipmentType the equipment type
   * @return list of custom constraints
   */
  public List<CapacityConstraint> getCustomConstraints(String equipmentType) {
    return customConstraints.getOrDefault(equipmentType, new ArrayList<>());
  }

  /**
   * Check if a constraint type is supported for an equipment type.
   *
   * @param equipmentType the equipment type
   * @param constraintType the constraint type
   * @return true if supported
   */
  public boolean isConstraintSupported(String equipmentType, String constraintType) {
    return findTemplate(equipmentType, constraintType) != null;
  }

  private ConstraintTemplate findTemplate(String equipmentType, String constraintType) {
    List<ConstraintTemplate> templates = defaultConstraints.get(equipmentType);
    if (templates != null) {
      for (ConstraintTemplate t : templates) {
        if (t.getType().equals(constraintType)) {
          return t;
        }
      }
    }
    return null;
  }

  /**
   * Template class for constraint metadata.
   */
  public static class ConstraintTemplate {
    private String type;
    private String displayName;
    private String unit;
    private String description;

    /**
     * Create a constraint template.
     *
     * @param type constraint type identifier
     * @param displayName human-readable name
     * @param unit unit of measurement
     * @param description description of the constraint
     */
    public ConstraintTemplate(String type, String displayName, String unit, String description) {
      this.type = type;
      this.displayName = displayName;
      this.unit = unit;
      this.description = description;
    }

    /**
     * Get the constraint type identifier.
     *
     * @return type identifier
     */
    public String getType() {
      return type;
    }

    /**
     * Get the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Get the unit of measurement.
     *
     * @return unit
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Get the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }
  }
}
