package neqsim.process.mechanicaldesign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Provides whole-process mechanical design aggregation and reporting.
 *
 * <p>
 * This class aggregates mechanical design data from all equipment in a {@link ProcessSystem},
 * providing:
 * </p>
 * <ul>
 * <li>Total weight, volume, and plot space for the entire process</li>
 * <li>Weight breakdown by equipment type (separators, compressors, heat exchangers, etc.)</li>
 * <li>Weight breakdown by discipline (mechanical, piping, E&amp;I, structural)</li>
 * <li>Utility requirements summary (power, heating duty, cooling duty)</li>
 * <li>Equipment list with key design parameters</li>
 * <li>Summary reports in various formats</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>
 * {@code
 * ProcessSystem process = new ProcessSystem();
 * // ... add equipment to process ...
 * process.run();
 *
 * SystemMechanicalDesign mecDesign = new SystemMechanicalDesign(process);
 * mecDesign.runDesignCalculation();
 *
 * // Get summaries
 * System.out.println("Total weight: " + mecDesign.getTotalWeight() + " kg");
 * System.out.println(
 *     "Compressor weight: " + mecDesign.getWeightByEquipmentType().get("Compressor") + " kg");
 * System.out.println("Total power: " + mecDesign.getTotalPowerRequired() + " kW");
 *
 * // Print full report
 * System.out.println(mecDesign.generateSummaryReport());
 * }
 * </pre>
 *
 * @author esol
 * @version 2.0
 */
public class SystemMechanicalDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SystemMechanicalDesign.class);

  ProcessSystem processSystem = null;
  double totalPlotSpace = 0.0;
  double totalVolume = 0.0;
  double totalWeight = 0.0;
  int numberOfModules = 0;

  // ============================================================================
  // Enhanced Breakdown Data
  // ============================================================================

  /** Weight breakdown by equipment type (e.g., "Separator", "Compressor", "Valve"). */
  private Map<String, Double> weightByEquipmentType = new LinkedHashMap<String, Double>();

  /** Weight breakdown by discipline (Mechanical, Piping, E and I, Structural). */
  private Map<String, Double> weightByDiscipline = new LinkedHashMap<String, Double>();

  /** Equipment count by type. */
  private Map<String, Integer> equipmentCountByType = new LinkedHashMap<String, Integer>();

  /** Equipment list with design data. */
  private List<EquipmentDesignSummary> equipmentList = new ArrayList<EquipmentDesignSummary>();

  // ============================================================================
  // Utility Requirements
  // ============================================================================

  /** Total power required (compressors, pumps) [kW]. */
  private double totalPowerRequired = 0.0;

  /** Total power recovered (expanders) [kW]. */
  private double totalPowerRecovered = 0.0;

  /** Total heating duty (heaters) [kW]. */
  private double totalHeatingDuty = 0.0;

  /** Total cooling duty (coolers) [kW]. */
  private double totalCoolingDuty = 0.0;

  // ============================================================================
  // Plot/Module Dimensions
  // ============================================================================

  /** Total footprint length [m]. */
  private double totalFootprintLength = 0.0;

  /** Total footprint width [m]. */
  private double totalFootprintWidth = 0.0;

  /** Maximum equipment height [m]. */
  private double maxEquipmentHeight = 0.0;

  // ============================================================================
  // Equipment Design Summary Inner Class
  // ============================================================================

  /**
   * Summary of key design data for a single equipment item.
   */
  public static class EquipmentDesignSummary implements java.io.Serializable {
    private static final long serialVersionUID = 1000L;

    private String name;
    private String type;
    private double weight;
    private double designPressure;
    private double designTemperature;
    private double power;
    private double duty;
    private String dimensions;
    private double length;
    private double width;
    private double height;

    /**
     * Constructor for EquipmentDesignSummary.
     *
     * @param name equipment name
     * @param type equipment type
     */
    public EquipmentDesignSummary(String name, String type) {
      this.name = name;
      this.type = type;
    }

    /**
     * Get equipment name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Get equipment type.
     *
     * @return type
     */
    public String getType() {
      return type;
    }

    /** Get equipment weight in kg. @return weight */
    public double getWeight() {
      return weight;
    }

    /** Set equipment weight. @param weight weight in kg */
    public void setWeight(double weight) {
      this.weight = weight;
    }

    /** Get design pressure in bara. @return design pressure */
    public double getDesignPressure() {
      return designPressure;
    }

    /** Set design pressure. @param designPressure pressure in bara */
    public void setDesignPressure(double designPressure) {
      this.designPressure = designPressure;
    }

    /** Get design temperature in °C. @return design temperature */
    public double getDesignTemperature() {
      return designTemperature;
    }

    /** Set design temperature. @param designTemperature temperature in °C */
    public void setDesignTemperature(double designTemperature) {
      this.designTemperature = designTemperature;
    }

    /** Get power in kW. @return power */
    public double getPower() {
      return power;
    }

    /** Set power. @param power power in kW */
    public void setPower(double power) {
      this.power = power;
    }

    /** Get duty in kW. @return duty */
    public double getDuty() {
      return duty;
    }

    /** Set duty. @param duty duty in kW */
    public void setDuty(double duty) {
      this.duty = duty;
    }

    /** Get dimensions string. @return dimensions */
    public String getDimensions() {
      return dimensions;
    }

    /** Set dimensions string. @param dimensions dimensions description */
    public void setDimensions(String dimensions) {
      this.dimensions = dimensions;
    }

    /** Get length in meters. @return length */
    public double getLength() {
      return length;
    }

    /** Set length. @param length length in meters */
    public void setLength(double length) {
      this.length = length;
    }

    /** Get width in meters. @return width */
    public double getWidth() {
      return width;
    }

    /** Set width. @param width width in meters */
    public void setWidth(double width) {
      this.width = width;
    }

    /** Get height in meters. @return height */
    public double getHeight() {
      return height;
    }

    /** Set height. @param height height in meters */
    public void setHeight(double height) {
      this.height = height;
    }

    @Override
    public String toString() {
      return String.format("%-20s %-15s %10.0f kg", name, type, weight);
    }
  }

  /**
   * <p>
   * Constructor for SystemMechanicalDesign.
   * </p>
   *
   * @param processSystem a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public SystemMechanicalDesign(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * <p>
   * getProcess.
   * </p>
   *
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public ProcessSystem getProcess() {
    return processSystem;
  }

  /**
   * <p>
   * setCompanySpecificDesignStandards.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setCompanySpecificDesignStandards(String name) {
    for (int i = 0; i < this.processSystem.getUnitOperations().size(); i++) {
      this.getProcess().getUnitOperations().get(i).getMechanicalDesign()
          .setCompanySpecificDesignStandards(name);
    }
  }

  /**
   * Run design calculations for all equipment in the process system.
   *
   * <p>
   * This method iterates through all unit operations, initializes their mechanical design, runs
   * calculations, and aggregates the results. After calling this method, all getters will return
   * updated values.
   * </p>
   */
  public void runDesignCalculation() {
    // Reset all accumulators
    resetAccumulators();

    ArrayList<String> names = this.processSystem.getAllUnitNames();
    for (int i = 0; i < names.size(); i++) {
      try {
        ProcessEquipmentInterface equipment = this.processSystem.getUnit(names.get(i));
        if (equipment == null) {
          continue;
        }

        equipment.initMechanicalDesign();
        MechanicalDesign mecDesign = equipment.getMechanicalDesign();
        mecDesign.calcDesign();

        // Accumulate totals
        double plotSpace = mecDesign.getModuleHeight() * mecDesign.getModuleLength();
        totalPlotSpace += plotSpace;
        totalVolume += mecDesign.getVolumeTotal();
        totalWeight += mecDesign.getWeightTotal();
        numberOfModules++;

        // Track dimensions
        totalFootprintLength += mecDesign.getModuleLength();
        totalFootprintWidth = Math.max(totalFootprintWidth, mecDesign.getModuleWidth());
        maxEquipmentHeight = Math.max(maxEquipmentHeight, mecDesign.getModuleHeight());

        // Classify equipment and accumulate by type
        String equipmentType = classifyEquipment(equipment);
        accumulateByType(equipmentType, mecDesign.getWeightTotal());

        // Accumulate by discipline
        accumulateByDiscipline(mecDesign);

        // Accumulate utility requirements
        accumulateUtilities(equipment);

        // Create equipment summary
        EquipmentDesignSummary summary = createEquipmentSummary(equipment, mecDesign);
        equipmentList.add(summary);

      } catch (Exception ex) {
        logger.error("Error processing equipment " + names.get(i) + ": " + ex.getMessage(), ex);
      }
    }
  }

  /**
   * Reset all accumulators before running calculations.
   */
  private void resetAccumulators() {
    totalPlotSpace = 0.0;
    totalVolume = 0.0;
    totalWeight = 0.0;
    numberOfModules = 0;
    totalFootprintLength = 0.0;
    totalFootprintWidth = 0.0;
    maxEquipmentHeight = 0.0;
    totalPowerRequired = 0.0;
    totalPowerRecovered = 0.0;
    totalHeatingDuty = 0.0;
    totalCoolingDuty = 0.0;
    weightByEquipmentType.clear();
    weightByDiscipline.clear();
    equipmentCountByType.clear();
    equipmentList.clear();

    // Initialize discipline categories
    weightByDiscipline.put("Mechanical Equipment", 0.0);
    weightByDiscipline.put("Piping", 0.0);
    weightByDiscipline.put("Electrical & Instrumentation", 0.0);
    weightByDiscipline.put("Structural Steel", 0.0);
  }

  /**
   * Classify equipment into a type category.
   *
   * @param equipment the equipment to classify
   * @return the equipment type name
   */
  private String classifyEquipment(ProcessEquipmentInterface equipment) {
    String className = equipment.getClass().getSimpleName();

    // Map specific classes to broader categories
    if (className.contains("Separator") || className.contains("Scrubber")) {
      return "Separator";
    } else if (className.contains("Compressor")) {
      return "Compressor";
    } else if (className.contains("Pump")) {
      return "Pump";
    } else if (className.contains("Expander")) {
      return "Expander";
    } else if (className.contains("HeatExchanger") || className.contains("Cooler")
        || className.contains("Heater")) {
      return "Heat Exchanger";
    } else if (className.contains("Valve")) {
      return "Valve";
    } else if (className.contains("Tank")) {
      return "Tank";
    } else if (className.contains("Column") || className.contains("Distillation")
        || className.contains("Absorber")) {
      return "Column";
    } else if (className.contains("Mixer") || className.contains("Splitter")) {
      return "Mixer/Splitter";
    } else if (className.contains("Stream")) {
      return "Stream";
    } else if (className.contains("Pipeline") || className.contains("Pipe")) {
      return "Pipeline";
    } else {
      return "Other";
    }
  }

  /**
   * Accumulate weight by equipment type.
   *
   * @param type equipment type
   * @param weight weight to add
   */
  private void accumulateByType(String type, double weight) {
    Double current = weightByEquipmentType.get(type);
    if (current == null) {
      current = 0.0;
    }
    weightByEquipmentType.put(type, current + weight);

    Integer count = equipmentCountByType.get(type);
    if (count == null) {
      count = 0;
    }
    equipmentCountByType.put(type, count + 1);
  }

  /**
   * Accumulate weight by discipline.
   *
   * @param mecDesign mechanical design object
   */
  private void accumulateByDiscipline(MechanicalDesign mecDesign) {
    // Mechanical equipment (vessels, rotating equipment)
    double mechWeight = mecDesign.getWeightTotal() - mecDesign.getWeightPiping()
        - mecDesign.getWeightElectroInstrument() - mecDesign.getWeightStructualSteel();
    weightByDiscipline.put("Mechanical Equipment",
        weightByDiscipline.get("Mechanical Equipment") + Math.max(0, mechWeight));

    // Piping
    weightByDiscipline.put("Piping",
        weightByDiscipline.get("Piping") + mecDesign.getWeightPiping());

    // E&I
    weightByDiscipline.put("Electrical & Instrumentation",
        weightByDiscipline.get("Electrical & Instrumentation")
            + mecDesign.getWeightElectroInstrument());

    // Structural
    weightByDiscipline.put("Structural Steel",
        weightByDiscipline.get("Structural Steel") + mecDesign.getWeightStructualSteel());
  }

  /**
   * Accumulate utility requirements from equipment.
   *
   * @param equipment the equipment to check
   */
  private void accumulateUtilities(ProcessEquipmentInterface equipment) {
    try {
      if (equipment instanceof Compressor) {
        Compressor comp = (Compressor) equipment;
        totalPowerRequired += comp.getPower("kW");
      } else if (equipment instanceof Pump) {
        Pump pump = (Pump) equipment;
        totalPowerRequired += pump.getPower("kW");
      } else if (equipment instanceof Expander) {
        Expander exp = (Expander) equipment;
        totalPowerRecovered += Math.abs(exp.getPower("kW"));
      } else if (equipment instanceof Heater) {
        Heater heater = (Heater) equipment;
        double duty = heater.getDuty();
        if (duty > 0) {
          totalHeatingDuty += duty / 1000.0; // Convert to kW
        } else {
          totalCoolingDuty += Math.abs(duty) / 1000.0;
        }
      }
    } catch (Exception e) {
      // Ignore if equipment doesn't have power/duty
    }
  }

  /**
   * Create an equipment design summary.
   *
   * @param equipment the equipment
   * @param mecDesign the mechanical design
   * @return equipment design summary
   */
  private EquipmentDesignSummary createEquipmentSummary(ProcessEquipmentInterface equipment,
      MechanicalDesign mecDesign) {
    String type = classifyEquipment(equipment);
    EquipmentDesignSummary summary = new EquipmentDesignSummary(equipment.getName(), type);
    summary.setWeight(mecDesign.getWeightTotal());
    summary.setDesignPressure(mecDesign.getMaxOperationPressure() * 1.1);
    summary.setDesignTemperature(mecDesign.getMaxOperationTemperature() + 30);

    // Set power/duty based on equipment type
    try {
      if (equipment instanceof Compressor) {
        summary.setPower(((Compressor) equipment).getPower("kW"));
      } else if (equipment instanceof Pump) {
        summary.setPower(((Pump) equipment).getPower("kW"));
      } else if (equipment instanceof Expander) {
        summary.setPower(-Math.abs(((Expander) equipment).getPower("kW")));
      } else if (equipment instanceof Heater) {
        summary.setDuty(((Heater) equipment).getDuty() / 1000.0);
      }
    } catch (Exception e) {
      // Ignore
    }

    // Set dimensions
    String dims = String.format("%.1fm x %.1fm x %.1fm", mecDesign.getModuleLength(),
        mecDesign.getModuleWidth(), mecDesign.getModuleHeight());
    summary.setDimensions(dims);

    return summary;
  }

  /**
   * <p>
   * setDesign.
   * </p>
   */
  public void setDesign() {
    for (int i = 0; i < this.processSystem.getUnitOperations().size(); i++) {
      this.processSystem.getUnitOperations().get(i).getMechanicalDesign().setDesign();
    }
  }

  /**
   * <p>
   * Getter for the field <code>totalPlotSpace</code>.
   * </p>
   *
   * @return a double
   */
  public double getTotalPlotSpace() {
    return totalPlotSpace;
  }

  /**
   * <p>
   * Getter for the field <code>totalVolume</code>.
   * </p>
   *
   * @return a double
   */
  public double getTotalVolume() {
    return totalVolume;
  }

  /**
   * <p>
   * Getter for the field <code>totalWeight</code>.
   * </p>
   *
   * @return a double
   */
  public double getTotalWeight() {
    return totalWeight;
  }

  /**
   * Get the total number of modules.
   *
   * @return number of modules
   */
  public int getTotalNumberOfModules() {
    return numberOfModules;
  }

  // ============================================================================
  // New Getters for Enhanced Breakdowns
  // ============================================================================

  /**
   * Get weight breakdown by equipment type.
   *
   * @return map of equipment type to total weight in kg
   */
  public Map<String, Double> getWeightByEquipmentType() {
    return new LinkedHashMap<String, Double>(weightByEquipmentType);
  }

  /**
   * Get weight breakdown by discipline.
   *
   * @return map of discipline to total weight in kg
   */
  public Map<String, Double> getWeightByDiscipline() {
    return new LinkedHashMap<String, Double>(weightByDiscipline);
  }

  /**
   * Get equipment count by type.
   *
   * @return map of equipment type to count
   */
  public Map<String, Integer> getEquipmentCountByType() {
    return new LinkedHashMap<String, Integer>(equipmentCountByType);
  }

  /**
   * Get list of all equipment with design summaries.
   *
   * @return list of equipment design summaries
   */
  public List<EquipmentDesignSummary> getEquipmentList() {
    return new ArrayList<EquipmentDesignSummary>(equipmentList);
  }

  /**
   * Get total power required (compressors + pumps).
   *
   * @return total power in kW
   */
  public double getTotalPowerRequired() {
    return totalPowerRequired;
  }

  /**
   * Get total power recovered (expanders).
   *
   * @return total recovered power in kW
   */
  public double getTotalPowerRecovered() {
    return totalPowerRecovered;
  }

  /**
   * Get net power requirement.
   *
   * @return net power (required - recovered) in kW
   */
  public double getNetPowerRequirement() {
    return totalPowerRequired - totalPowerRecovered;
  }

  /**
   * Get total heating duty.
   *
   * @return total heating duty in kW
   */
  public double getTotalHeatingDuty() {
    return totalHeatingDuty;
  }

  /**
   * Get total cooling duty.
   *
   * @return total cooling duty in kW
   */
  public double getTotalCoolingDuty() {
    return totalCoolingDuty;
  }

  /**
   * Get total footprint length.
   *
   * @return total length in meters
   */
  public double getTotalFootprintLength() {
    return totalFootprintLength;
  }

  /**
   * Get total footprint width.
   *
   * @return maximum width in meters
   */
  public double getTotalFootprintWidth() {
    return totalFootprintWidth;
  }

  /**
   * Get maximum equipment height.
   *
   * @return maximum height in meters
   */
  public double getMaxEquipmentHeight() {
    return maxEquipmentHeight;
  }

  // ============================================================================
  // Summary Report Generation
  // ============================================================================

  /**
   * Generate a comprehensive summary report.
   *
   * @return formatted summary report string
   */
  public String generateSummaryReport() {
    StringBuilder sb = new StringBuilder();
    String separator = repeat("=", 70);
    String subSeparator = repeat("-", 70);

    sb.append(separator).append("\n");
    sb.append("PROCESS MECHANICAL DESIGN SUMMARY\n");
    sb.append("Process: ").append(processSystem.getName()).append("\n");
    sb.append(separator).append("\n\n");

    // Overall Summary
    sb.append("OVERALL SUMMARY\n");
    sb.append(subSeparator).append("\n");
    sb.append(String.format("Total Equipment Count:     %d\n", numberOfModules));
    sb.append(String.format("Total Weight:              %.0f kg (%.1f tonnes)\n", totalWeight,
        totalWeight / 1000.0));
    sb.append(String.format("Total Volume:              %.1f m³\n", totalVolume));
    sb.append(String.format("Total Plot Space:          %.1f m²\n", totalPlotSpace));
    sb.append(String.format("Footprint (L x W):         %.1f x %.1f m\n", totalFootprintLength,
        totalFootprintWidth));
    sb.append(String.format("Max Equipment Height:      %.1f m\n", maxEquipmentHeight));
    sb.append("\n");

    // Utility Requirements
    sb.append("UTILITY REQUIREMENTS\n");
    sb.append(subSeparator).append("\n");
    sb.append(String.format("Power Required:            %.0f kW\n", totalPowerRequired));
    sb.append(String.format("Power Recovered:           %.0f kW\n", totalPowerRecovered));
    sb.append(String.format("Net Power:                 %.0f kW\n", getNetPowerRequirement()));
    sb.append(String.format("Heating Duty:              %.0f kW\n", totalHeatingDuty));
    sb.append(String.format("Cooling Duty:              %.0f kW\n", totalCoolingDuty));
    sb.append("\n");

    // Weight by Equipment Type
    sb.append("WEIGHT BY EQUIPMENT TYPE\n");
    sb.append(subSeparator).append("\n");
    sb.append(String.format("%-25s %10s %10s %10s\n", "Equipment Type", "Count", "Weight (kg)",
        "% of Total"));
    for (Map.Entry<String, Double> entry : weightByEquipmentType.entrySet()) {
      int count = equipmentCountByType.getOrDefault(entry.getKey(), 0);
      double percentage = (totalWeight > 0) ? (entry.getValue() / totalWeight * 100.0) : 0.0;
      sb.append(String.format("%-25s %10d %10.0f %10.1f%%\n", entry.getKey(), count,
          entry.getValue(), percentage));
    }
    sb.append("\n");

    // Weight by Discipline
    sb.append("WEIGHT BY DISCIPLINE\n");
    sb.append(subSeparator).append("\n");
    sb.append(String.format("%-30s %15s %10s\n", "Discipline", "Weight (kg)", "% of Total"));
    for (Map.Entry<String, Double> entry : weightByDiscipline.entrySet()) {
      double percentage = (totalWeight > 0) ? (entry.getValue() / totalWeight * 100.0) : 0.0;
      sb.append(
          String.format("%-30s %15.0f %10.1f%%\n", entry.getKey(), entry.getValue(), percentage));
    }
    sb.append("\n");

    // Equipment List
    sb.append("EQUIPMENT LIST\n");
    sb.append(subSeparator).append("\n");
    sb.append(String.format("%-20s %-15s %12s %12s %12s\n", "Name", "Type", "Weight (kg)",
        "Power (kW)", "Duty (kW)"));
    for (EquipmentDesignSummary item : equipmentList) {
      String powerStr = (item.getPower() != 0) ? String.format("%.0f", item.getPower()) : "-";
      String dutyStr = (item.getDuty() != 0) ? String.format("%.0f", item.getDuty()) : "-";
      sb.append(String.format("%-20s %-15s %12.0f %12s %12s\n", truncate(item.getName(), 20),
          truncate(item.getType(), 15), item.getWeight(), powerStr, dutyStr));
    }
    sb.append("\n");

    sb.append(separator).append("\n");
    sb.append("END OF REPORT\n");

    return sb.toString();
  }

  /**
   * Generate a JSON-format summary (legacy method).
   *
   * @return JSON string
   * @deprecated Use {@link #toJson()} instead for proper Gson serialization
   */
  @Deprecated
  public String generateJsonSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append(String.format("  \"processName\": \"%s\",\n", processSystem.getName()));
    sb.append(String.format("  \"totalWeight\": %.1f,\n", totalWeight));
    sb.append(String.format("  \"totalVolume\": %.1f,\n", totalVolume));
    sb.append(String.format("  \"totalPlotSpace\": %.1f,\n", totalPlotSpace));
    sb.append(String.format("  \"equipmentCount\": %d,\n", numberOfModules));
    sb.append(String.format("  \"powerRequired\": %.1f,\n", totalPowerRequired));
    sb.append(String.format("  \"powerRecovered\": %.1f,\n", totalPowerRecovered));
    sb.append(String.format("  \"heatingDuty\": %.1f,\n", totalHeatingDuty));
    sb.append(String.format("  \"coolingDuty\": %.1f,\n", totalCoolingDuty));

    sb.append("  \"weightByType\": {\n");
    int i = 0;
    for (Map.Entry<String, Double> entry : weightByEquipmentType.entrySet()) {
      sb.append(String.format("    \"%s\": %.1f", entry.getKey(), entry.getValue()));
      sb.append((++i < weightByEquipmentType.size()) ? ",\n" : "\n");
    }
    sb.append("  },\n");

    sb.append("  \"weightByDiscipline\": {\n");
    i = 0;
    for (Map.Entry<String, Double> entry : weightByDiscipline.entrySet()) {
      sb.append(String.format("    \"%s\": %.1f", entry.getKey(), entry.getValue()));
      sb.append((++i < weightByDiscipline.size()) ? ",\n" : "\n");
    }
    sb.append("  }\n");

    sb.append("}\n");
    return sb.toString();
  }

  /**
   * Export mechanical design data to JSON format.
   *
   * <p>
   * This method creates a {@link MechanicalDesignResponse} object and serializes it to JSON using
   * Gson. The JSON includes system-level totals, weight breakdowns by type and discipline, utility
   * requirements, and an equipment list with key design parameters.
   * </p>
   *
   * <p>
   * Usage example:
   * </p>
   * 
   * <pre>
   * {@code
   * SystemMechanicalDesign mecDesign = new SystemMechanicalDesign(process);
   * mecDesign.runDesignCalculation();
   * String json = mecDesign.toJson();
   * // json contains properly formatted JSON with all design data
   * }
   * </pre>
   *
   * @return JSON string representation of the mechanical design
   */
  public String toJson() {
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);
    return response.toJson();
  }

  /**
   * Export mechanical design data to compact JSON format (no pretty printing).
   *
   * @return compact JSON string
   */
  public String toCompactJson() {
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);
    return response.toCompactJson();
  }

  /**
   * Get the mechanical design response object.
   *
   * <p>
   * This method returns a {@link MechanicalDesignResponse} object that can be further customized or
   * combined with other data before serialization.
   * </p>
   *
   * @return MechanicalDesignResponse object
   */
  public MechanicalDesignResponse getResponse() {
    return new MechanicalDesignResponse(this);
  }

  /**
   * Truncate a string to a maximum length.
   *
   * @param str the string
   * @param maxLen maximum length
   * @return truncated string
   */
  private String truncate(String str, int maxLen) {
    if (str == null) {
      return "";
    }
    return (str.length() <= maxLen) ? str : str.substring(0, maxLen - 2) + "..";
  }

  /**
   * Repeat a string n times (Java 8 compatible).
   *
   * @param str string to repeat
   * @param count number of times
   * @return repeated string
   */
  private static String repeat(String str, int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append(str);
    }
    return sb.toString();
  }

  /**
   * <p>
   * getMechanicalWeight.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getMechanicalWeight(String unit) {
    double weight = 0.0;
    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      processSystem.getUnitOperations().get(i).getMechanicalDesign().calcDesign();
      System.out.println("Name " + processSystem.getUnitOperations().get(i).getName() + "  weight "
          + processSystem.getUnitOperations().get(i).getMechanicalDesign().getWeightTotal());
      weight += processSystem.getUnitOperations().get(i).getMechanicalDesign().getWeightTotal();
    }
    return weight;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(numberOfModules, processSystem, totalPlotSpace, totalVolume, totalWeight,
        totalPowerRequired, totalPowerRecovered, totalHeatingDuty, totalCoolingDuty);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    SystemMechanicalDesign other = (SystemMechanicalDesign) obj;
    return numberOfModules == other.numberOfModules
        && Objects.equals(processSystem, other.processSystem)
        && Double.doubleToLongBits(totalPlotSpace) == Double.doubleToLongBits(other.totalPlotSpace)
        && Double.doubleToLongBits(totalVolume) == Double.doubleToLongBits(other.totalVolume)
        && Double.doubleToLongBits(totalWeight) == Double.doubleToLongBits(other.totalWeight)
        && Double.doubleToLongBits(totalPowerRequired) == Double
            .doubleToLongBits(other.totalPowerRequired)
        && Double.doubleToLongBits(totalPowerRecovered) == Double
            .doubleToLongBits(other.totalPowerRecovered);
  }
}
