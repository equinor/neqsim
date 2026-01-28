package neqsim.process.costestimation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.SystemMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;

/**
 * System-level cost estimation for an entire process.
 *
 * <p>
 * This class aggregates cost estimates from all equipment in a {@link ProcessSystem}, providing:
 * </p>
 * <ul>
 * <li>Total purchased equipment cost (PEC)</li>
 * <li>Total bare module cost (BMC)</li>
 * <li>Total module cost (TMC)</li>
 * <li>Total grass roots cost (including infrastructure)</li>
 * <li>Cost breakdown by equipment type</li>
 * <li>Cost breakdown by discipline</li>
 * <li>Installation man-hours estimation</li>
 * <li>Consolidated bill of materials</li>
 * </ul>
 *
 * <p>
 * The workflow for process cost estimation:
 * </p>
 * <ol>
 * <li>ProcessSystem.run() - run the process simulation</li>
 * <li>ProcessSystem.initAllMechanicalDesigns() - initialize mechanical designs</li>
 * <li>ProcessSystem.runAllMechanicalDesigns() - calculate mechanical designs</li>
 * <li>ProcessSystem.getCostEstimate() - get this ProcessCostEstimate</li>
 * <li>ProcessCostEstimate.calculateAllCosts() - aggregate all cost estimates</li>
 * <li>ProcessCostEstimate.toJson() - export comprehensive cost report</li>
 * </ol>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * ProcessSystem process = new ProcessSystem();
 * // ... add equipment ...
 * process.run();
 *
 * // Get cost estimate through ProcessSystem
 * ProcessCostEstimate costEstimate = process.getCostEstimate();
 * costEstimate.calculateAllCosts();
 *
 * // Get totals
 * System.out.println("Total PEC: $" + costEstimate.getTotalPurchasedEquipmentCost());
 * System.out.println("Total TMC: $" + costEstimate.getTotalModuleCost());
 * System.out.println("Grass Roots: $" + costEstimate.getTotalGrassRootsCost());
 *
 * // Export to JSON
 * String json = costEstimate.toJson();
 * }
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class ProcessCostEstimate implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // References
  // ============================================================================

  /** The process system being estimated. */
  private ProcessSystem processSystem;

  /** The system mechanical design (optional, for integrated calculations). */
  private SystemMechanicalDesign systemMechanicalDesign;

  /** Central cost calculator for process-level factors. */
  private transient CostEstimationCalculator costCalculator;

  // ============================================================================
  // Cost Totals
  // ============================================================================

  /** Total purchased equipment cost (PEC) in USD. */
  private double totalPurchasedEquipmentCost = 0.0;

  /** Total bare module cost (BMC) in USD. */
  private double totalBareModuleCost = 0.0;

  /** Total module cost (TMC) in USD. */
  private double totalModuleCost = 0.0;

  /** Total grass roots cost in USD. */
  private double totalGrassRootsCost = 0.0;

  /** Total installation man-hours. */
  private double totalInstallationManHours = 0.0;

  /** Total annual operating cost in USD. */
  private double totalAnnualOperatingCost = 0.0;

  // ============================================================================
  // Cost Breakdowns
  // ============================================================================

  /** Cost breakdown by equipment type. */
  private Map<String, Double> costByEquipmentType = new LinkedHashMap<String, Double>();

  /** Cost breakdown by discipline. */
  private Map<String, Double> costByDiscipline = new LinkedHashMap<String, Double>();

  /** Individual equipment costs. */
  private List<EquipmentCostSummary> equipmentCosts = new ArrayList<EquipmentCostSummary>();

  /** Operating cost breakdown by category. */
  private Map<String, Double> operatingCostBreakdown = new LinkedHashMap<String, Double>();

  // ============================================================================
  // Process-Level Factors
  // ============================================================================

  /** Process contingency factor (fraction). */
  private double processContingencyFactor = 0.15;

  /** Project contingency factor (fraction). */
  private double projectContingencyFactor = 0.10;

  /** Working capital factor (fraction of fixed capital). */
  private double workingCapitalFactor = 0.15;

  /** Location factor (1.0 = US Gulf Coast). */
  private double locationFactor = 1.0;

  /** Process complexity factor (1.0 = standard). */
  private double complexityFactor = 1.0;

  // ============================================================================
  // Cost Summary Inner Class
  // ============================================================================

  /**
   * Summary of cost data for a single equipment item.
   */
  public static class EquipmentCostSummary implements java.io.Serializable {
    private static final long serialVersionUID = 1000L;

    private String name;
    private String type;
    private double purchasedEquipmentCost;
    private double bareModuleCost;
    private double totalModuleCost;
    private double installationManHours;
    private double weight;

    /**
     * Constructor.
     *
     * @param name equipment name
     * @param type equipment type
     */
    public EquipmentCostSummary(String name, String type) {
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

    /**
     * Get PEC.
     *
     * @return PEC in USD
     */
    public double getPurchasedEquipmentCost() {
      return purchasedEquipmentCost;
    }

    /**
     * Set PEC.
     *
     * @param cost PEC in USD
     */
    public void setPurchasedEquipmentCost(double cost) {
      this.purchasedEquipmentCost = cost;
    }

    /**
     * Get BMC.
     *
     * @return BMC in USD
     */
    public double getBareModuleCost() {
      return bareModuleCost;
    }

    /**
     * Set BMC.
     *
     * @param cost BMC in USD
     */
    public void setBareModuleCost(double cost) {
      this.bareModuleCost = cost;
    }

    /**
     * Get TMC.
     *
     * @return TMC in USD
     */
    public double getTotalModuleCost() {
      return totalModuleCost;
    }

    /**
     * Set TMC.
     *
     * @param cost TMC in USD
     */
    public void setTotalModuleCost(double cost) {
      this.totalModuleCost = cost;
    }

    /**
     * Get installation hours.
     *
     * @return man-hours
     */
    public double getInstallationManHours() {
      return installationManHours;
    }

    /**
     * Set installation hours.
     *
     * @param hours man-hours
     */
    public void setInstallationManHours(double hours) {
      this.installationManHours = hours;
    }

    /**
     * Get weight.
     *
     * @return weight in kg
     */
    public double getWeight() {
      return weight;
    }

    /**
     * Set weight.
     *
     * @param weight weight in kg
     */
    public void setWeight(double weight) {
      this.weight = weight;
    }
  }

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public ProcessCostEstimate() {
    this.costCalculator = new CostEstimationCalculator();
  }

  /**
   * Constructor with process system.
   *
   * @param processSystem the process system to estimate costs for
   */
  public ProcessCostEstimate(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.costCalculator = new CostEstimationCalculator();
  }

  /**
   * Constructor with process system and mechanical design.
   *
   * @param processSystem the process system
   * @param systemMechanicalDesign the system mechanical design
   */
  public ProcessCostEstimate(ProcessSystem processSystem,
      SystemMechanicalDesign systemMechanicalDesign) {
    this.processSystem = processSystem;
    this.systemMechanicalDesign = systemMechanicalDesign;
    this.costCalculator = new CostEstimationCalculator();
  }

  // ============================================================================
  // Main Calculation Methods
  // ============================================================================

  /**
   * Calculate cost estimates for all equipment in the process.
   *
   * <p>
   * This method iterates through all equipment, initializes mechanical designs if needed,
   * calculates cost estimates, and aggregates the totals.
   * </p>
   */
  public void calculateAllCosts() {
    if (processSystem == null) {
      return;
    }

    // Reset totals
    resetTotals();

    // Initialize cost breakdowns
    costByEquipmentType.clear();
    costByDiscipline.clear();
    equipmentCosts.clear();

    // Process each equipment
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment == null) {
        continue;
      }

      // Initialize mechanical design if needed
      equipment.initMechanicalDesign();
      MechanicalDesign mecDesign = equipment.getMechanicalDesign();

      if (mecDesign == null) {
        continue;
      }

      // Run mechanical design calculation
      mecDesign.calcDesign();

      // Calculate cost estimate
      mecDesign.calculateCostEstimate();
      UnitCostEstimateBaseClass costEst = mecDesign.getCostEstimate();

      if (costEst == null) {
        continue;
      }

      // Create equipment summary
      String equipType = classifyEquipment(equipment);
      EquipmentCostSummary summary = new EquipmentCostSummary(equipment.getName(), equipType);
      summary.setPurchasedEquipmentCost(costEst.getPurchasedEquipmentCost());
      summary.setBareModuleCost(costEst.getBareModuleCost());
      summary.setTotalModuleCost(costEst.getTotalModuleCost());
      summary.setInstallationManHours(costEst.getInstallationManHours());
      summary.setWeight(mecDesign.getWeightTotal());
      equipmentCosts.add(summary);

      // Accumulate totals
      totalPurchasedEquipmentCost += costEst.getPurchasedEquipmentCost();
      totalBareModuleCost += costEst.getBareModuleCost();
      totalModuleCost += costEst.getTotalModuleCost();
      totalInstallationManHours += costEst.getInstallationManHours();

      // Accumulate by type
      accumulateCostByType(equipType, costEst.getPurchasedEquipmentCost());
    }

    // Apply location factor
    totalPurchasedEquipmentCost *= locationFactor;
    totalBareModuleCost *= locationFactor;
    totalModuleCost *= locationFactor;

    // Calculate grass roots cost (TMC + site development + auxiliary buildings)
    double siteDevelopment = totalModuleCost * 0.10;
    double auxiliaryBuildings = totalModuleCost * 0.05;
    totalGrassRootsCost = totalModuleCost + siteDevelopment + auxiliaryBuildings;

    // Apply complexity factor
    totalGrassRootsCost *= complexityFactor;

    // Calculate discipline costs (rough estimates)
    calculateDisciplineCosts();
  }

  /**
   * Reset all cost totals to zero.
   */
  private void resetTotals() {
    totalPurchasedEquipmentCost = 0.0;
    totalBareModuleCost = 0.0;
    totalModuleCost = 0.0;
    totalGrassRootsCost = 0.0;
    totalInstallationManHours = 0.0;
  }

  /**
   * Classify equipment into a type category.
   *
   * @param equipment the equipment to classify
   * @return the equipment type name
   */
  private String classifyEquipment(ProcessEquipmentInterface equipment) {
    String className = equipment.getClass().getSimpleName();

    if (className.contains("Separator") || className.contains("Scrubber")) {
      return "Vessels";
    } else if (className.contains("Compressor")) {
      return "Compressors";
    } else if (className.contains("Pump")) {
      return "Pumps";
    } else if (className.contains("Expander")) {
      return "Expanders";
    } else if (className.contains("HeatExchanger") || className.contains("Cooler")
        || className.contains("Heater")) {
      return "Heat Exchangers";
    } else if (className.contains("Valve")) {
      return "Valves";
    } else if (className.contains("Tank")) {
      return "Tanks";
    } else if (className.contains("Column") || className.contains("Distillation")
        || className.contains("Absorber")) {
      return "Columns";
    } else if (className.contains("Mixer") || className.contains("Splitter")) {
      return "Piping";
    } else if (className.contains("Pipeline") || className.contains("Pipe")) {
      return "Pipelines";
    } else if (className.contains("Stream")) {
      return "Streams";
    } else {
      return "Other";
    }
  }

  /**
   * Accumulate cost by equipment type.
   *
   * @param type equipment type
   * @param cost cost to add
   */
  private void accumulateCostByType(String type, double cost) {
    Double current = costByEquipmentType.get(type);
    if (current == null) {
      current = 0.0;
    }
    costByEquipmentType.put(type, current + cost);
  }

  /**
   * Calculate discipline cost breakdown (rough estimates based on typical percentages).
   */
  private void calculateDisciplineCosts() {
    // Typical cost distribution for process plants:
    // Equipment: ~30%, Piping: ~25%, E&I: ~15%, Civil/Structural: ~10%, Other: ~20%
    double equipmentFactor = 0.30;
    double pipingFactor = 0.25;
    double eiAndFactor = 0.15;
    double civilStructuralFactor = 0.10;
    double otherFactor = 0.20;

    costByDiscipline.put("Process Equipment", totalPurchasedEquipmentCost);
    costByDiscipline.put("Piping & Valves", totalBareModuleCost * pipingFactor / equipmentFactor);
    costByDiscipline.put("Electrical & Instrumentation",
        totalBareModuleCost * eiAndFactor / equipmentFactor);
    costByDiscipline.put("Civil & Structural",
        totalBareModuleCost * civilStructuralFactor / equipmentFactor);
    costByDiscipline.put("Other (HVAC, Paint, Insulation)",
        totalBareModuleCost * otherFactor / equipmentFactor);
  }

  // ============================================================================
  // Getters for Cost Totals
  // ============================================================================

  /**
   * Get total purchased equipment cost.
   *
   * @return total PEC in USD
   */
  public double getTotalPurchasedEquipmentCost() {
    return totalPurchasedEquipmentCost;
  }

  /**
   * Get total bare module cost.
   *
   * @return total BMC in USD
   */
  public double getTotalBareModuleCost() {
    return totalBareModuleCost;
  }

  /**
   * Get total module cost.
   *
   * @return total TMC in USD
   */
  public double getTotalModuleCost() {
    return totalModuleCost;
  }

  /**
   * Get total grass roots cost.
   *
   * @return total grass roots cost in USD
   */
  public double getTotalGrassRootsCost() {
    return totalGrassRootsCost;
  }

  /**
   * Get total installation man-hours.
   *
   * @return total man-hours
   */
  public double getTotalInstallationManHours() {
    return totalInstallationManHours;
  }

  /**
   * Get cost breakdown by equipment type.
   *
   * @return map of equipment type to cost
   */
  public Map<String, Double> getCostByEquipmentType() {
    return new LinkedHashMap<String, Double>(costByEquipmentType);
  }

  /**
   * Get cost breakdown by discipline.
   *
   * @return map of discipline to cost
   */
  public Map<String, Double> getCostByDiscipline() {
    return new LinkedHashMap<String, Double>(costByDiscipline);
  }

  /**
   * Get list of individual equipment costs.
   *
   * @return list of equipment cost summaries
   */
  public List<EquipmentCostSummary> getEquipmentCosts() {
    return new ArrayList<EquipmentCostSummary>(equipmentCosts);
  }

  // ============================================================================
  // Configuration Setters
  // ============================================================================

  /**
   * Set the process system.
   *
   * @param processSystem the process system
   */
  public void setProcessSystem(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Set the location factor.
   *
   * @param locationFactor location factor (1.0 = US Gulf Coast)
   */
  public void setLocationFactor(double locationFactor) {
    this.locationFactor = locationFactor;
  }

  /**
   * Get the location factor.
   *
   * @return location factor
   */
  public double getLocationFactor() {
    return locationFactor;
  }

  /**
   * Set the complexity factor.
   *
   * @param complexityFactor complexity factor (1.0 = standard)
   */
  public void setComplexityFactor(double complexityFactor) {
    this.complexityFactor = complexityFactor;
  }

  /**
   * Get the complexity factor.
   *
   * @return complexity factor
   */
  public double getComplexityFactor() {
    return complexityFactor;
  }

  /**
   * Set the CEPCI for cost escalation.
   *
   * @param cepci the Chemical Engineering Plant Cost Index
   */
  public void setCepci(double cepci) {
    if (costCalculator == null) {
      costCalculator = new CostEstimationCalculator();
    }
    costCalculator.setCurrentCepci(cepci);
  }

  /**
   * Set material of construction for all equipment.
   *
   * @param material material name (e.g., "Carbon Steel", "SS316")
   */
  public void setMaterial(String material) {
    if (costCalculator == null) {
      costCalculator = new CostEstimationCalculator();
    }
    costCalculator.setMaterialOfConstruction(material);
  }

  // ============================================================================
  // Report Generation
  // ============================================================================

  /**
   * Generate a summary report as text.
   *
   * @return formatted summary report
   */
  public String generateSummaryReport() {
    StringBuilder sb = new StringBuilder();
    String processName = (processSystem != null) ? processSystem.getName() : "Unknown Process";

    sb.append("======================================================================\n");
    sb.append("PROCESS COST ESTIMATE SUMMARY\n");
    sb.append("Process: ").append(processName).append("\n");
    sb.append("======================================================================\n\n");

    sb.append("CAPITAL COST SUMMARY\n");
    sb.append("----------------------------------------------------------------------\n");
    sb.append(
        String.format("Purchased Equipment Cost (PEC):    $%,.0f%n", totalPurchasedEquipmentCost));
    sb.append(String.format("Bare Module Cost (BMC):            $%,.0f%n", totalBareModuleCost));
    sb.append(String.format("Total Module Cost (TMC):           $%,.0f%n", totalModuleCost));
    sb.append(String.format("Grass Roots Cost:                  $%,.0f%n", totalGrassRootsCost));
    sb.append("\n");

    sb.append("INSTALLATION\n");
    sb.append("----------------------------------------------------------------------\n");
    sb.append(
        String.format("Total Installation Man-Hours:      %,.0f%n", totalInstallationManHours));
    sb.append("\n");

    sb.append("COST BY EQUIPMENT TYPE\n");
    sb.append("----------------------------------------------------------------------\n");
    sb.append(String.format("%-25s %15s %10s%n", "Type", "Cost (USD)", "% of PEC"));
    for (Map.Entry<String, Double> entry : costByEquipmentType.entrySet()) {
      double pct =
          (totalPurchasedEquipmentCost > 0) ? (entry.getValue() / totalPurchasedEquipmentCost * 100)
              : 0;
      sb.append(String.format("%-25s $%,14.0f %9.1f%%%n", entry.getKey(), entry.getValue(), pct));
    }
    sb.append("\n");

    sb.append("COST BY DISCIPLINE (ESTIMATED)\n");
    sb.append("----------------------------------------------------------------------\n");
    double totalDiscipline = 0;
    for (Double cost : costByDiscipline.values()) {
      totalDiscipline += cost;
    }
    for (Map.Entry<String, Double> entry : costByDiscipline.entrySet()) {
      double pct = (totalDiscipline > 0) ? (entry.getValue() / totalDiscipline * 100) : 0;
      sb.append(String.format("%-35s $%,14.0f %9.1f%%%n", entry.getKey(), entry.getValue(), pct));
    }
    sb.append("\n");

    sb.append("FACTORS APPLIED\n");
    sb.append("----------------------------------------------------------------------\n");
    sb.append(String.format("Location Factor:                   %.2f%n", locationFactor));
    sb.append(String.format("Complexity Factor:                 %.2f%n", complexityFactor));
    sb.append("\n");

    sb.append("======================================================================\n");
    sb.append("END OF COST ESTIMATE\n");

    return sb.toString();
  }

  /**
   * Generate detailed equipment list report.
   *
   * @return formatted equipment list
   */
  public String generateEquipmentListReport() {
    StringBuilder sb = new StringBuilder();

    sb.append("======================================================================\n");
    sb.append("EQUIPMENT COST LIST\n");
    sb.append("======================================================================\n\n");
    sb.append(String.format("%-20s %-15s %15s %15s %12s%n", "Name", "Type", "PEC (USD)",
        "TMC (USD)", "Man-Hours"));
    sb.append(String.format("%-20s %-15s %15s %15s %12s%n", "--------------------",
        "---------------", "---------------", "---------------", "------------"));

    for (EquipmentCostSummary eq : equipmentCosts) {
      sb.append(String.format("%-20s %-15s $%,14.0f $%,14.0f %,11.0f%n", truncate(eq.getName(), 20),
          truncate(eq.getType(), 15), eq.getPurchasedEquipmentCost(), eq.getTotalModuleCost(),
          eq.getInstallationManHours()));
    }

    sb.append("\n======================================================================\n");
    return sb.toString();
  }

  /**
   * Truncate string to max length.
   *
   * @param str the string to truncate
   * @param maxLen maximum length
   * @return truncated string
   */
  private String truncate(String str, int maxLen) {
    if (str == null) {
      return "";
    }
    return (str.length() > maxLen) ? str.substring(0, maxLen - 2) + ".." : str;
  }

  // ============================================================================
  // JSON Export
  // ============================================================================

  /**
   * Export cost estimate to JSON format.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Process identification
    result.put("processName", (processSystem != null) ? processSystem.getName() : "Unknown");
    result.put("reportType", "ProcessCostEstimate");
    result.put("generatedAt", java.time.Instant.now().toString());
    result.put("cepciYear", costCalculator != null ? costCalculator.getCurrentCepci() : 0);

    // Cost summary
    Map<String, Object> costSummary = new LinkedHashMap<String, Object>();
    costSummary.put("purchasedEquipmentCost_USD", totalPurchasedEquipmentCost);
    costSummary.put("bareModuleCost_USD", totalBareModuleCost);
    costSummary.put("totalModuleCost_USD", totalModuleCost);
    costSummary.put("grassRootsCost_USD", totalGrassRootsCost);
    costSummary.put("totalInstallationManHours", totalInstallationManHours);
    result.put("costSummary", costSummary);

    // Factors
    Map<String, Object> factors = new LinkedHashMap<String, Object>();
    factors.put("locationFactor", locationFactor);
    factors.put("complexityFactor", complexityFactor);
    factors.put("processContingencyFactor", processContingencyFactor);
    factors.put("projectContingencyFactor", projectContingencyFactor);
    result.put("factors", factors);

    // Cost by equipment type
    result.put("costByEquipmentType_USD", costByEquipmentType);

    // Cost by discipline
    result.put("costByDiscipline_USD", costByDiscipline);

    // Equipment list
    List<Map<String, Object>> equipList = new ArrayList<Map<String, Object>>();
    for (EquipmentCostSummary eq : equipmentCosts) {
      Map<String, Object> equipMap = new LinkedHashMap<String, Object>();
      equipMap.put("name", eq.getName());
      equipMap.put("type", eq.getType());
      equipMap.put("purchasedEquipmentCost_USD", eq.getPurchasedEquipmentCost());
      equipMap.put("bareModuleCost_USD", eq.getBareModuleCost());
      equipMap.put("totalModuleCost_USD", eq.getTotalModuleCost());
      equipMap.put("installationManHours", eq.getInstallationManHours());
      equipMap.put("weight_kg", eq.getWeight());
      equipList.add(equipMap);
    }
    result.put("equipment", equipList);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }

  /**
   * Export cost estimate to compact JSON format.
   *
   * @return compact JSON string
   */
  public String toCompactJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("processName", (processSystem != null) ? processSystem.getName() : "Unknown");
    result.put("PEC_USD", totalPurchasedEquipmentCost);
    result.put("BMC_USD", totalBareModuleCost);
    result.put("TMC_USD", totalModuleCost);
    result.put("grassRoots_USD", totalGrassRootsCost);
    result.put("manHours", totalInstallationManHours);
    result.put("equipmentCount", equipmentCosts.size());
    return new GsonBuilder().create().toJson(result);
  }

  // ============================================================================
  // Operating Cost (OPEX) Calculation
  // ============================================================================

  /**
   * Calculate annual operating cost for the process.
   *
   * <p>
   * Operating costs include:
   * </p>
   * <ul>
   * <li>Utilities (electricity, steam, cooling water, fuel)</li>
   * <li>Maintenance (typically 3-5% of fixed capital)</li>
   * <li>Operating labor</li>
   * <li>Supervision and overhead</li>
   * <li>Supplies and materials</li>
   * </ul>
   *
   * @param operatingHoursPerYear annual operating hours (typically 8000-8760)
   * @param electricityCostPerKWh electricity cost in $/kWh
   * @param steamCostPerTonne steam cost in $/tonne
   * @param coolingWaterCostPerM3 cooling water cost in $/m3
   * @param laborCostPerHour labor cost in $/hr
   * @return total annual operating cost in USD
   */
  public double calculateOperatingCost(int operatingHoursPerYear, double electricityCostPerKWh,
      double steamCostPerTonne, double coolingWaterCostPerM3, double laborCostPerHour) {
    operatingCostBreakdown.clear();

    // Calculate utility costs based on equipment
    double electricityCost = 0.0;
    double steamCost = 0.0;
    double coolingWaterCost = 0.0;

    if (processSystem != null) {
      for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
        if (equipment == null) {
          continue;
        }

        String className = equipment.getClass().getSimpleName();

        // Estimate power consumption for rotating equipment
        if (className.contains("Compressor") || className.contains("Pump")) {
          double power = 0.0;
          try {
            java.lang.reflect.Method getPower =
                equipment.getClass().getMethod("getPower", String.class);
            Object result = getPower.invoke(equipment, "kW");
            if (result instanceof Number) {
              power = ((Number) result).doubleValue();
            }
          } catch (Exception e) {
            // Ignore if method doesn't exist
          }
          electricityCost += power * operatingHoursPerYear * electricityCostPerKWh;
        }

        // Estimate steam for reboilers and heaters
        if (className.contains("Heater") || className.contains("Reboiler")) {
          double duty = 0.0;
          try {
            java.lang.reflect.Method getDuty = equipment.getClass().getMethod("getDuty");
            Object result = getDuty.invoke(equipment);
            if (result instanceof Number) {
              duty = Math.abs(((Number) result).doubleValue()); // kW
            }
          } catch (Exception e) {
            // Ignore if method doesn't exist
          }
          // Steam at 2200 kJ/kg latent heat
          double steamFlow = duty * 3.6 / 2200.0; // kg/hr
          steamCost += steamFlow * operatingHoursPerYear * steamCostPerTonne / 1000.0;
        }

        // Estimate cooling water for coolers and condensers
        if (className.contains("Cooler") || className.contains("Condenser")) {
          double duty = 0.0;
          try {
            java.lang.reflect.Method getDuty = equipment.getClass().getMethod("getDuty");
            Object result = getDuty.invoke(equipment);
            if (result instanceof Number) {
              duty = Math.abs(((Number) result).doubleValue()); // kW
            }
          } catch (Exception e) {
            // Ignore if method doesn't exist
          }
          // Cooling water at 10°C rise, 4.18 kJ/kg·K
          double cwFlow = duty * 3.6 / (4.18 * 10) / 1000.0; // m3/hr
          coolingWaterCost += cwFlow * operatingHoursPerYear * coolingWaterCostPerM3;
        }
      }
    }

    // Maintenance cost (3-5% of fixed capital investment)
    double maintenanceCost = totalModuleCost * 0.04;

    // Operating labor cost
    // Rule of thumb: 1 operator per 5 major equipment items per shift
    int majorEquipmentCount = Math.max(1, equipmentCosts.size());
    int operatorsPerShift = Math.max(2, majorEquipmentCount / 5);
    int shiftsPerDay = 3;
    double laborCost =
        operatorsPerShift * shiftsPerDay * operatingHoursPerYear / 3 * laborCostPerHour;

    // Supervision (15-20% of labor)
    double supervisionCost = laborCost * 0.18;

    // Operating supplies (10-15% of maintenance)
    double suppliesCost = maintenanceCost * 0.12;

    // Laboratory charges (10-20% of labor)
    double laboratoryCost = laborCost * 0.15;

    // Overhead (50-60% of labor + supervision + maintenance)
    double overheadCost = (laborCost + supervisionCost + maintenanceCost) * 0.55;

    // Build breakdown
    operatingCostBreakdown.put("Electricity", electricityCost);
    operatingCostBreakdown.put("Steam", steamCost);
    operatingCostBreakdown.put("Cooling Water", coolingWaterCost);
    operatingCostBreakdown.put("Maintenance", maintenanceCost);
    operatingCostBreakdown.put("Operating Labor", laborCost);
    operatingCostBreakdown.put("Supervision", supervisionCost);
    operatingCostBreakdown.put("Operating Supplies", suppliesCost);
    operatingCostBreakdown.put("Laboratory", laboratoryCost);
    operatingCostBreakdown.put("Overhead", overheadCost);

    // Total
    totalAnnualOperatingCost = electricityCost + steamCost + coolingWaterCost + maintenanceCost
        + laborCost + supervisionCost + suppliesCost + laboratoryCost + overheadCost;

    return totalAnnualOperatingCost;
  }

  /**
   * Calculate operating cost with default utility prices.
   *
   * <p>
   * Default prices (US Gulf Coast 2025):
   * </p>
   * <ul>
   * <li>Electricity: $0.08/kWh</li>
   * <li>Steam (150 psig): $15/tonne</li>
   * <li>Cooling water: $0.05/m3</li>
   * <li>Labor: $50/hr</li>
   * </ul>
   *
   * @param operatingHoursPerYear annual operating hours
   * @return total annual operating cost in USD
   */
  public double calculateOperatingCost(int operatingHoursPerYear) {
    return calculateOperatingCost(operatingHoursPerYear, 0.08, 15.0, 0.05, 50.0);
  }

  /**
   * Get annual operating cost.
   *
   * @return annual operating cost in USD
   */
  public double getTotalAnnualOperatingCost() {
    return totalAnnualOperatingCost;
  }

  /**
   * Get operating cost breakdown by category.
   *
   * @return map of category to annual cost
   */
  public Map<String, Double> getOperatingCostBreakdown() {
    return new LinkedHashMap<String, Double>(operatingCostBreakdown);
  }

  /**
   * Calculate simple payback period.
   *
   * @param annualRevenue annual revenue in USD
   * @return payback period in years
   */
  public double calculatePaybackPeriod(double annualRevenue) {
    double annualProfit = annualRevenue - totalAnnualOperatingCost;
    if (annualProfit <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    return totalGrassRootsCost / annualProfit;
  }

  /**
   * Calculate return on investment (ROI).
   *
   * @param annualRevenue annual revenue in USD
   * @return ROI as percentage
   */
  public double calculateROI(double annualRevenue) {
    double annualProfit = annualRevenue - totalAnnualOperatingCost;
    if (totalGrassRootsCost <= 0) {
      return 0.0;
    }
    return (annualProfit / totalGrassRootsCost) * 100.0;
  }

  /**
   * Calculate net present value (NPV).
   *
   * @param annualRevenue annual revenue in USD
   * @param discountRate annual discount rate (e.g., 0.10 for 10%)
   * @param projectLifeYears project life in years
   * @return NPV in USD
   */
  public double calculateNPV(double annualRevenue, double discountRate, int projectLifeYears) {
    double annualCashFlow = annualRevenue - totalAnnualOperatingCost;
    double npv = -totalGrassRootsCost;

    for (int year = 1; year <= projectLifeYears; year++) {
      npv += annualCashFlow / Math.pow(1 + discountRate, year);
    }

    return npv;
  }

  /**
   * Set location by region name (convenience method).
   *
   * @param region region name
   */
  public void setLocationByRegion(String region) {
    if (costCalculator == null) {
      costCalculator = new CostEstimationCalculator();
    }
    costCalculator.setLocationByRegion(region);
    this.locationFactor = costCalculator.getLocationFactor();
  }

  /**
   * Set currency for cost reporting.
   *
   * @param currencyCode currency code (USD, EUR, NOK, GBP, etc.)
   */
  public void setCurrency(String currencyCode) {
    if (costCalculator == null) {
      costCalculator = new CostEstimationCalculator();
    }
    costCalculator.setCurrencyCode(currencyCode);
  }

  /**
   * Get costs in current currency.
   *
   * @return map of cost items in current currency
   */
  public Map<String, Double> getCostsInCurrency() {
    Map<String, Double> costs = new LinkedHashMap<String, Double>();
    double rate = (costCalculator != null) ? costCalculator.getExchangeRate() : 1.0;

    costs.put("purchasedEquipmentCost", totalPurchasedEquipmentCost * rate);
    costs.put("bareModuleCost", totalBareModuleCost * rate);
    costs.put("totalModuleCost", totalModuleCost * rate);
    costs.put("grassRootsCost", totalGrassRootsCost * rate);
    costs.put("annualOperatingCost", totalAnnualOperatingCost * rate);

    return costs;
  }

  /**
   * Get currency code.
   *
   * @return currency code
   */
  public String getCurrencyCode() {
    return (costCalculator != null) ? costCalculator.getCurrencyCode() : "USD";
  }
}
