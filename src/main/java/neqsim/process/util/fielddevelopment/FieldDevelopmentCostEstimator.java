package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.costestimation.CostEstimationCalculator;
import neqsim.process.costestimation.ProcessCostEstimate;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Integrates process mechanical design and cost estimation into field development workflows.
 *
 * <p>
 * This class provides a bridge between process equipment design and field development economics,
 * enabling:
 * </p>
 * <ul>
 * <li>CAPEX estimation at different fidelity levels (screening, conceptual, FEED)</li>
 * <li>Equipment sizing linked to production capacity</li>
 * <li>Cost scaling with production rate</li>
 * <li>Mechanical design output (weights, dimensions, materials)</li>
 * <li>NPV-integrated cost estimation for concept comparison</li>
 * </ul>
 *
 * <h2>Fidelity Levels</h2>
 * 
 * <table border="1">
 * <tr>
 * <th>Level</th>
 * <th>Accuracy</th>
 * <th>Use Case</th>
 * <th>Cost Basis</th>
 * </tr>
 * <tr>
 * <td>SCREENING</td>
 * <td>±50%</td>
 * <td>Discovery, feasibility</td>
 * <td>Capacity correlations</td>
 * </tr>
 * <tr>
 * <td>CONCEPTUAL</td>
 * <td>±30%</td>
 * <td>Concept selection</td>
 * <td>Equipment-type correlations</td>
 * </tr>
 * <tr>
 * <td>PRE_FEED</td>
 * <td>±20%</td>
 * <td>Pre-FEED</td>
 * <td>Sized equipment costs</td>
 * </tr>
 * <tr>
 * <td>FEED</td>
 * <td>±10%</td>
 * <td>FEED, AFE</td>
 * <td>Detailed mechanical design</td>
 * </tr>
 * </table>
 *
 * <h2>Integration with FieldProductionScheduler</h2>
 * 
 * <pre>{@code
 * // Create production scheduler
 * FieldProductionScheduler scheduler = new FieldProductionScheduler("Offshore Field");
 * scheduler.addReservoir(reservoir);
 * scheduler.setFacility(facility);
 * scheduler.setPlateauRate(10.0, "MSm3/day");
 * 
 * // Create cost estimator linked to facility
 * FieldDevelopmentCostEstimator costEstimator = new FieldDevelopmentCostEstimator(facility);
 * costEstimator.setFidelityLevel(FidelityLevel.CONCEPTUAL);
 * costEstimator.setLocationFactor(1.3); // Norwegian Sea
 * 
 * // Run mechanical design and cost estimation
 * FieldDevelopmentCostReport report = costEstimator.estimateDevelopmentCosts();
 * 
 * System.out.println("Total CAPEX: $" + report.getTotalCapex() / 1e6 + " M");
 * System.out.println("Facilities weight: " + report.getTotalWeight() / 1000 + " tonnes");
 * System.out.println("Installation man-hours: " + report.getTotalManHours());
 * 
 * // Use for NPV calculation
 * scheduler.setCapex(report.getTotalCapex() / 1e6, 2025);
 * ProductionSchedule schedule = scheduler.generateSchedule(startDate, 20.0, 30.0);
 * System.out.println("NPV: $" + schedule.getNPV() / 1e6 + "M");
 * }</pre>
 *
 * <h2>Concept Comparison</h2>
 * 
 * <pre>{@code
 * // Compare multiple development concepts
 * List<ProcessSystem> concepts = Arrays.asList(conceptA, conceptB, conceptC);
 * List<FieldDevelopmentCostReport> reports = costEstimator.compareConceptCosts(concepts);
 * 
 * for (FieldDevelopmentCostReport report : reports) {
 *   System.out.printf("%s: CAPEX=$%.0fM, Weight=%.0ft, Area=%.0fm2%n", report.getConceptName(),
 *       report.getTotalCapex() / 1e6, report.getTotalWeight() / 1000, report.getFootprintArea());
 * }
 * }</pre>
 *
 * @author AGAS
 * @version 1.0
 * @see FieldProductionScheduler
 * @see ProcessCostEstimate
 * @see MechanicalDesign
 */
public class FieldDevelopmentCostEstimator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Fidelity level for cost estimation.
   */
  public enum FidelityLevel {
    /** ±50% accuracy - capacity correlations only. */
    SCREENING(0.5, "Screening"),
    /** ±30% accuracy - equipment-type correlations. */
    CONCEPTUAL(0.3, "Conceptual"),
    /** ±20% accuracy - sized equipment. */
    PRE_FEED(0.2, "Pre-FEED"),
    /** ±10% accuracy - detailed mechanical design. */
    FEED(0.1, "FEED");

    private final double accuracyBand;
    private final String displayName;

    FidelityLevel(double accuracyBand, String displayName) {
      this.accuracyBand = accuracyBand;
      this.displayName = displayName;
    }

    /**
     * Gets the accuracy band (e.g., 0.3 = ±30%).
     *
     * @return accuracy band
     */
    public double getAccuracyBand() {
      return accuracyBand;
    }

    /**
     * Gets the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Development concept type affecting cost factors.
   */
  public enum ConceptType {
    /** Fixed platform. */
    FIXED_PLATFORM(1.0),
    /** Floating production (FPSO). */
    FPSO(1.15),
    /** Semi-submersible. */
    SEMI_SUBMERSIBLE(1.25),
    /** Tension leg platform. */
    TLP(1.3),
    /** Subsea tieback. */
    SUBSEA_TIEBACK(0.85),
    /** Onshore processing. */
    ONSHORE(0.7);

    private final double costFactor;

    ConceptType(double costFactor) {
      this.costFactor = costFactor;
    }

    /**
     * Gets the cost factor relative to fixed platform.
     *
     * @return cost factor
     */
    public double getCostFactor() {
      return costFactor;
    }
  }

  /** Process system for cost estimation. */
  private final ProcessSystem facility;

  /** Fidelity level. */
  private FidelityLevel fidelityLevel = FidelityLevel.CONCEPTUAL;

  /** Development concept type. */
  private ConceptType conceptType = ConceptType.FIXED_PLATFORM;

  /** Cost calculator. */
  private transient CostEstimationCalculator costCalculator;

  /** Location factor (1.0 = US Gulf Coast). */
  private double locationFactor = 1.0;

  /** Complexity factor (1.0 = standard). */
  private double complexityFactor = 1.0;

  /** Currency code for reporting. */
  private String currencyCode = "USD";

  /** Reference year for costs. */
  private int referenceYear = 2024;

  /** Include subsea costs. */
  private boolean includeSubseaCosts = false;

  /** Subsea tieback length in km. */
  private double subseaTiebackLength = 0.0;

  /** Water depth in meters. */
  private double waterDepth = 100.0;

  /**
   * Constructor with process system.
   *
   * @param facility the process system to estimate costs for
   */
  public FieldDevelopmentCostEstimator(ProcessSystem facility) {
    this.facility = facility;
    this.costCalculator = new CostEstimationCalculator();
  }

  /**
   * Set the fidelity level for cost estimation.
   *
   * @param level fidelity level
   */
  public void setFidelityLevel(FidelityLevel level) {
    this.fidelityLevel = level;
  }

  /**
   * Get the fidelity level.
   *
   * @return fidelity level
   */
  public FidelityLevel getFidelityLevel() {
    return fidelityLevel;
  }

  /**
   * Set the development concept type.
   *
   * @param type concept type
   */
  public void setConceptType(ConceptType type) {
    this.conceptType = type;
  }

  /**
   * Set location factor for regional cost adjustment.
   *
   * @param factor location factor (1.0 = US Gulf Coast, 1.3 = North Sea, etc.)
   */
  public void setLocationFactor(double factor) {
    this.locationFactor = factor;
    if (costCalculator != null) {
      costCalculator.setLocationFactor(factor);
    }
  }

  /**
   * Set complexity factor.
   *
   * @param factor complexity factor (1.0 = standard, &gt;1.0 = complex)
   */
  public void setComplexityFactor(double factor) {
    this.complexityFactor = factor;
  }

  /**
   * Set subsea parameters.
   *
   * @param tiebackLength tieback length in km
   * @param waterDepthM water depth in meters
   */
  public void setSubseaParameters(double tiebackLength, double waterDepthM) {
    this.includeSubseaCosts = true;
    this.subseaTiebackLength = tiebackLength;
    this.waterDepth = waterDepthM;
  }

  /**
   * Estimate development costs for the facility.
   *
   * <p>
   * This method:
   * </p>
   * <ol>
   * <li>Runs mechanical design for all equipment</li>
   * <li>Calculates cost estimates based on fidelity level</li>
   * <li>Applies location and concept factors</li>
   * <li>Generates comprehensive cost report</li>
   * </ol>
   *
   * @return cost report with CAPEX, weights, and man-hours
   */
  public FieldDevelopmentCostReport estimateDevelopmentCosts() {
    // Initialize mechanical design for all equipment
    if (fidelityLevel == FidelityLevel.FEED || fidelityLevel == FidelityLevel.PRE_FEED) {
      facility.runAllMechanicalDesigns();
    }

    // Create process cost estimate
    ProcessCostEstimate processCost = new ProcessCostEstimate(facility);
    processCost.setLocationFactor(locationFactor);
    processCost.setComplexityFactor(complexityFactor);
    processCost.calculateAllCosts();

    // Build cost report
    FieldDevelopmentCostReport report = new FieldDevelopmentCostReport();
    report.setConceptName(facility.getName());
    report.setFidelityLevel(fidelityLevel);
    report.setConceptType(conceptType);

    // Apply concept type factor
    double facilityCostUSD = processCost.getTotalModuleCost() * conceptType.getCostFactor();
    report.setFacilitiesCapex(facilityCostUSD);

    // Equipment breakdown
    for (ProcessEquipmentInterface equipment : facility.getUnitOperations()) {
      if (equipment.getMechanicalDesign() != null) {
        MechanicalDesign mecDesign = equipment.getMechanicalDesign();
        UnitCostEstimateBaseClass costEst = mecDesign.getCostEstimate();

        EquipmentCostItem item = new EquipmentCostItem();
        item.setName(equipment.getName());
        item.setType(equipment.getClass().getSimpleName());
        item.setWeight(mecDesign.getWeightTotal());
        item.setPurchasedCost(costEst.getPurchasedEquipmentCost());
        item.setInstalledCost(costEst.getTotalModuleCost() * conceptType.getCostFactor());
        item.setManHours(costEst.getInstallationManHours());

        report.addEquipmentItem(item);
      }
    }

    // Add subsea costs if applicable
    if (includeSubseaCosts) {
      double subseaCost = estimateSubseaCosts();
      report.setSubseaCapex(subseaCost);
    }

    // Calculate totals
    report.calculateTotals();

    // Add accuracy range based on fidelity
    report.setAccuracyBand(fidelityLevel.getAccuracyBand());

    return report;
  }

  /**
   * Estimate subsea infrastructure costs.
   *
   * @return subsea CAPEX in USD
   */
  private double estimateSubseaCosts() {
    double totalSubseaCost = 0.0;

    // Flowline cost per km (increases with water depth)
    double depthFactor = 1.0 + (waterDepth - 100.0) / 500.0;
    double flowlineCostPerKm = 2.5e6 * depthFactor; // Base $2.5M/km
    totalSubseaCost += subseaTiebackLength * flowlineCostPerKm;

    // Umbilical cost
    double umbilicalCostPerKm = 1.5e6 * depthFactor;
    totalSubseaCost += subseaTiebackLength * umbilicalCostPerKm;

    // Subsea manifold (if tieback > 10km)
    if (subseaTiebackLength > 10.0) {
      totalSubseaCost += 30.0e6 * depthFactor; // Manifold
    }

    // Riser system
    double riserCost = waterDepth * 50000.0; // ~$50k per meter depth
    totalSubseaCost += riserCost;

    // Apply location factor
    totalSubseaCost *= locationFactor;

    return totalSubseaCost;
  }

  /**
   * Compare costs for multiple development concepts.
   *
   * @param concepts list of process systems representing different concepts
   * @return list of cost reports for comparison
   */
  public List<FieldDevelopmentCostReport> compareConceptCosts(List<ProcessSystem> concepts) {
    List<FieldDevelopmentCostReport> reports = new ArrayList<FieldDevelopmentCostReport>();

    for (ProcessSystem concept : concepts) {
      FieldDevelopmentCostEstimator estimator = new FieldDevelopmentCostEstimator(concept);
      estimator.setFidelityLevel(this.fidelityLevel);
      estimator.setConceptType(this.conceptType);
      estimator.setLocationFactor(this.locationFactor);
      estimator.setComplexityFactor(this.complexityFactor);

      if (this.includeSubseaCosts) {
        estimator.setSubseaParameters(this.subseaTiebackLength, this.waterDepth);
      }

      reports.add(estimator.estimateDevelopmentCosts());
    }

    return reports;
  }

  /**
   * Estimate CAPEX for a given production capacity using scaling factors.
   *
   * <p>
   * Uses the six-tenths rule: Cost2/Cost1 = (Capacity2/Capacity1)^0.6
   * </p>
   *
   * @param baseCapex base CAPEX in USD
   * @param baseCapacity base capacity
   * @param targetCapacity target capacity
   * @param capacityUnit capacity unit
   * @return scaled CAPEX in USD
   */
  public double scaleCapexByCapacity(double baseCapex, double baseCapacity, double targetCapacity,
      String capacityUnit) {
    if (baseCapacity <= 0 || targetCapacity <= 0) {
      return baseCapex;
    }

    // Six-tenths rule for process equipment
    double scalingExponent = 0.6;
    double scaleFactor = Math.pow(targetCapacity / baseCapacity, scalingExponent);

    return baseCapex * scaleFactor * locationFactor * complexityFactor;
  }

  /**
   * Get cost calculator.
   *
   * @return cost calculator instance
   */
  public CostEstimationCalculator getCostCalculator() {
    if (costCalculator == null) {
      costCalculator = new CostEstimationCalculator();
      costCalculator.setLocationFactor(locationFactor);
    }
    return costCalculator;
  }

  // ============================================================================
  // Inner Classes
  // ============================================================================

  /**
   * Cost report for field development.
   */
  public static class FieldDevelopmentCostReport implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String conceptName;
    private FidelityLevel fidelityLevel;
    private ConceptType conceptType;
    private double facilitiesCapex;
    private double subseaCapex;
    private double totalCapex;
    private double totalWeight;
    private double totalManHours;
    private double footprintArea;
    private double accuracyBand;
    private final List<EquipmentCostItem> equipmentItems = new ArrayList<EquipmentCostItem>();
    private final Map<String, Double> costByCategory = new LinkedHashMap<String, Double>();

    /**
     * Set concept name.
     *
     * @param name concept name
     */
    public void setConceptName(String name) {
      this.conceptName = name;
    }

    /**
     * Get concept name.
     *
     * @return concept name
     */
    public String getConceptName() {
      return conceptName;
    }

    /**
     * Set fidelity level.
     *
     * @param level fidelity level
     */
    public void setFidelityLevel(FidelityLevel level) {
      this.fidelityLevel = level;
    }

    /**
     * Set concept type.
     *
     * @param type concept type
     */
    public void setConceptType(ConceptType type) {
      this.conceptType = type;
    }

    /**
     * Set facilities CAPEX.
     *
     * @param capex facilities CAPEX in USD
     */
    public void setFacilitiesCapex(double capex) {
      this.facilitiesCapex = capex;
    }

    /**
     * Get facilities CAPEX.
     *
     * @return facilities CAPEX in USD
     */
    public double getFacilitiesCapex() {
      return facilitiesCapex;
    }

    /**
     * Set subsea CAPEX.
     *
     * @param capex subsea CAPEX in USD
     */
    public void setSubseaCapex(double capex) {
      this.subseaCapex = capex;
    }

    /**
     * Get subsea CAPEX.
     *
     * @return subsea CAPEX in USD
     */
    public double getSubseaCapex() {
      return subseaCapex;
    }

    /**
     * Get total CAPEX.
     *
     * @return total CAPEX in USD
     */
    public double getTotalCapex() {
      return totalCapex;
    }

    /**
     * Get total weight in kg.
     *
     * @return total weight in kg
     */
    public double getTotalWeight() {
      return totalWeight;
    }

    /**
     * Get total installation man-hours.
     *
     * @return total man-hours
     */
    public double getTotalManHours() {
      return totalManHours;
    }

    /**
     * Get footprint area in m2.
     *
     * @return footprint area
     */
    public double getFootprintArea() {
      return footprintArea;
    }

    /**
     * Set accuracy band.
     *
     * @param band accuracy band
     */
    public void setAccuracyBand(double band) {
      this.accuracyBand = band;
    }

    /**
     * Get accuracy band.
     *
     * @return accuracy band
     */
    public double getAccuracyBand() {
      return accuracyBand;
    }

    /**
     * Get low estimate (CAPEX - accuracy band).
     *
     * @return low estimate in USD
     */
    public double getLowEstimate() {
      return totalCapex * (1.0 - accuracyBand);
    }

    /**
     * Get high estimate (CAPEX + accuracy band).
     *
     * @return high estimate in USD
     */
    public double getHighEstimate() {
      return totalCapex * (1.0 + accuracyBand);
    }

    /**
     * Add equipment cost item.
     *
     * @param item equipment cost item
     */
    public void addEquipmentItem(EquipmentCostItem item) {
      equipmentItems.add(item);
    }

    /**
     * Get equipment items.
     *
     * @return list of equipment cost items
     */
    public List<EquipmentCostItem> getEquipmentItems() {
      return new ArrayList<EquipmentCostItem>(equipmentItems);
    }

    /**
     * Calculate totals from equipment items.
     */
    public void calculateTotals() {
      totalWeight = 0.0;
      totalManHours = 0.0;
      costByCategory.clear();

      for (EquipmentCostItem item : equipmentItems) {
        totalWeight += item.getWeight();
        totalManHours += item.getManHours();

        String category = item.getType();
        costByCategory.merge(category, item.getInstalledCost(), Double::sum);
      }

      totalCapex = facilitiesCapex + subseaCapex;
    }

    /**
     * Get cost breakdown by equipment category.
     *
     * @return map of category to cost
     */
    public Map<String, Double> getCostByCategory() {
      return new LinkedHashMap<String, Double>(costByCategory);
    }

    /**
     * Convert to JSON string.
     *
     * @return JSON representation
     */
    public String toJson() {
      Map<String, Object> data = new LinkedHashMap<String, Object>();
      data.put("conceptName", conceptName);
      data.put("fidelityLevel", fidelityLevel != null ? fidelityLevel.getDisplayName() : null);
      data.put("conceptType", conceptType != null ? conceptType.name() : null);
      data.put("accuracyBand", String.format("±%.0f%%", accuracyBand * 100));

      Map<String, Object> capex = new LinkedHashMap<String, Object>();
      capex.put("facilities_USD", facilitiesCapex);
      capex.put("subsea_USD", subseaCapex);
      capex.put("total_USD", totalCapex);
      capex.put("lowEstimate_USD", getLowEstimate());
      capex.put("highEstimate_USD", getHighEstimate());
      data.put("capex", capex);

      Map<String, Object> physical = new LinkedHashMap<String, Object>();
      physical.put("totalWeight_kg", totalWeight);
      physical.put("totalWeight_tonnes", totalWeight / 1000.0);
      physical.put("installationManHours", totalManHours);
      data.put("physicalProperties", physical);

      data.put("costByCategory", costByCategory);

      List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
      for (EquipmentCostItem item : equipmentItems) {
        items.add(item.toMap());
      }
      data.put("equipmentBreakdown", items);

      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
          .toJson(data);
    }

    /**
     * Convert to markdown table.
     *
     * @return markdown table string
     */
    public String toMarkdownTable() {
      StringBuilder sb = new StringBuilder();

      sb.append("# Field Development Cost Report\n\n");
      sb.append("**Concept:** ").append(conceptName).append("\n");
      sb.append("**Fidelity:** ")
          .append(fidelityLevel != null ? fidelityLevel.getDisplayName() : "N/A");
      sb.append(" (±").append(String.format("%.0f", accuracyBand * 100)).append("%)\n");
      sb.append("**Concept Type:** ").append(conceptType != null ? conceptType.name() : "N/A");
      sb.append("\n\n");

      sb.append("## CAPEX Summary\n\n");
      sb.append("| Category | Cost (USD) | Cost (MUSD) |\n");
      sb.append("|----------|------------|-------------|\n");
      sb.append(String.format("| Facilities | $%,.0f | $%.1f M |\n", facilitiesCapex,
          facilitiesCapex / 1e6));
      if (subseaCapex > 0) {
        sb.append(String.format("| Subsea | $%,.0f | $%.1f M |\n", subseaCapex, subseaCapex / 1e6));
      }
      sb.append(String.format("| **Total** | **$%,.0f** | **$%.1f M** |\n", totalCapex,
          totalCapex / 1e6));
      sb.append(String.format("| Low Estimate | $%,.0f | $%.1f M |\n", getLowEstimate(),
          getLowEstimate() / 1e6));
      sb.append(String.format("| High Estimate | $%,.0f | $%.1f M |\n", getHighEstimate(),
          getHighEstimate() / 1e6));
      sb.append("\n");

      sb.append("## Physical Properties\n\n");
      sb.append(String.format("- **Total Weight:** %.1f tonnes\n", totalWeight / 1000.0));
      sb.append(String.format("- **Installation Man-Hours:** %.0f\n", totalManHours));
      sb.append("\n");

      if (!equipmentItems.isEmpty()) {
        sb.append("## Equipment Breakdown\n\n");
        sb.append("| Equipment | Type | Weight (kg) | Cost (USD) |\n");
        sb.append("|-----------|------|-------------|------------|\n");
        for (EquipmentCostItem item : equipmentItems) {
          sb.append(String.format("| %s | %s | %.0f | $%,.0f |\n", item.getName(), item.getType(),
              item.getWeight(), item.getInstalledCost()));
        }
      }

      return sb.toString();
    }
  }

  /**
   * Equipment cost item for detailed breakdown.
   */
  public static class EquipmentCostItem implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String name;
    private String type;
    private double weight;
    private double purchasedCost;
    private double installedCost;
    private double manHours;

    /**
     * Set equipment name.
     *
     * @param name equipment name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Get equipment name.
     *
     * @return equipment name
     */
    public String getName() {
      return name;
    }

    /**
     * Set equipment type.
     *
     * @param type equipment type
     */
    public void setType(String type) {
      this.type = type;
    }

    /**
     * Get equipment type.
     *
     * @return equipment type
     */
    public String getType() {
      return type;
    }

    /**
     * Set weight in kg.
     *
     * @param weight weight in kg
     */
    public void setWeight(double weight) {
      this.weight = weight;
    }

    /**
     * Get weight in kg.
     *
     * @return weight in kg
     */
    public double getWeight() {
      return weight;
    }

    /**
     * Set purchased equipment cost.
     *
     * @param cost cost in USD
     */
    public void setPurchasedCost(double cost) {
      this.purchasedCost = cost;
    }

    /**
     * Get purchased equipment cost.
     *
     * @return cost in USD
     */
    public double getPurchasedCost() {
      return purchasedCost;
    }

    /**
     * Set installed cost.
     *
     * @param cost cost in USD
     */
    public void setInstalledCost(double cost) {
      this.installedCost = cost;
    }

    /**
     * Get installed cost.
     *
     * @return cost in USD
     */
    public double getInstalledCost() {
      return installedCost;
    }

    /**
     * Set installation man-hours.
     *
     * @param hours man-hours
     */
    public void setManHours(double hours) {
      this.manHours = hours;
    }

    /**
     * Get installation man-hours.
     *
     * @return man-hours
     */
    public double getManHours() {
      return manHours;
    }

    /**
     * Convert to map.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> data = new LinkedHashMap<String, Object>();
      data.put("name", name);
      data.put("type", type);
      data.put("weight_kg", weight);
      data.put("purchasedCost_USD", purchasedCost);
      data.put("installedCost_USD", installedCost);
      data.put("manHours", manHours);
      return data;
    }
  }
}
