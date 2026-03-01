package neqsim.process.mechanicaldesign.subsea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.subsea.SubseaWell;

/**
 * Cost estimation calculator for subsea wells.
 *
 * <p>
 * Provides cost estimation for drilling and completing subsea wells including:
 * </p>
 * <ul>
 * <li>Drilling costs (rig, casing, cement, mud, bits)</li>
 * <li>Completion costs (tubing, packers, screens, safety valves)</li>
 * <li>Wellhead and Xmas tree procurement</li>
 * <li>Logging and evaluation</li>
 * <li>Contingency and risk allowances</li>
 * </ul>
 *
 * <p>
 * Cost data is based on industry benchmarks and regional factors. Baseline costs are representative
 * of Norwegian Continental Shelf (NCS) development wells in the 2020-2024 timeframe, aligned with
 * data from actual field development plans (e.g., Ultima Thule concept study estimates).
 * </p>
 *
 * <h2>Cost Breakdown Structure</h2>
 *
 * <table>
 * <caption>Typical well cost categories as percentage of total</caption>
 * <tr>
 * <th>Category</th>
 * <th>Oil Producer</th>
 * <th>Water Injector</th>
 * </tr>
 * <tr>
 * <td>Drilling</td>
 * <td>55-60%</td>
 * <td>55-60%</td>
 * </tr>
 * <tr>
 * <td>Completion</td>
 * <td>20-25%</td>
 * <td>15-20%</td>
 * </tr>
 * <tr>
 * <td>Wellhead/Tree</td>
 * <td>10-12%</td>
 * <td>12-15%</td>
 * </tr>
 * <tr>
 * <td>Logging</td>
 * <td>5-8%</td>
 * <td>4-6%</td>
 * </tr>
 * <tr>
 * <td>Contingency</td>
 * <td>15%</td>
 * <td>15%</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 * @see WellMechanicalDesign
 * @see SubseaCostEstimator
 */
public class WellCostEstimator {

  /** Region for cost adjustment — reuses SubseaCostEstimator.Region. */
  private SubseaCostEstimator.Region region = SubseaCostEstimator.Region.NORWAY;

  // ============ Cost Components (USD) ============
  /** Drilling rig and services cost. */
  private double drillingCost = 0.0;

  /** Casing material cost. */
  private double casingMaterialCost = 0.0;

  /** Cement and cementing services cost. */
  private double cementCost = 0.0;

  /** Drilling mud / fluids cost. */
  private double mudCost = 0.0;

  /** Drill bits and downhole tools cost. */
  private double bitsCost = 0.0;

  /** Completion equipment cost (tubing, packers, screens). */
  private double completionCost = 0.0;

  /** Wellhead and Xmas tree cost. */
  private double wellheadCost = 0.0;

  /** Safety valves (DHSV, SCSSV) cost. */
  private double safetyValveCost = 0.0;

  /** Logging, surveys and evaluation cost. */
  private double loggingCost = 0.0;

  /** Well testing cost. */
  private double wellTestCost = 0.0;

  /** Contingency allowance. */
  private double contingencyCost = 0.0;

  /** Total well cost. */
  private double totalCost = 0.0;

  // ============ Settings ============
  /** Contingency percentage (default 15%). */
  private double contingencyPct = 0.15;

  /** Regional cost factor. */
  private double regionFactor = 1.0;

  // ============ Rate Assumptions ============
  /** Rig day rate in USD. */
  private double rigDayRate = 400000.0;

  /** Casing cost per tonne USD. */
  private double casingCostPerTonne = 3000.0;

  /** Cement cost per m3 USD. */
  private double cementCostPerM3 = 2000.0;

  /** Mud cost per m3 USD. */
  private double mudCostPerM3 = 1500.0;

  /** Completion equipment base cost USD (producer). */
  private double completionBaseCostProducer = 5500000.0;

  /** Completion equipment base cost USD (injector). */
  private double completionBaseCostInjector = 4000000.0;

  /** Wellhead and tree base cost USD. */
  private double wellheadBaseCost = 2500000.0;

  /** Logging base cost per day USD. */
  private double loggingDayRate = 150000.0;

  /** DHSV cost USD. */
  private double dhsvCost = 350000.0;

  /** Well test cost per day USD. */
  private double wellTestDayRate = 250000.0;

  /**
   * Default constructor.
   */
  public WellCostEstimator() {
    applyRegionFactor();
  }

  /**
   * Constructor with region.
   *
   * @param region cost estimation region
   */
  public WellCostEstimator(SubseaCostEstimator.Region region) {
    this.region = region;
    applyRegionFactor();
  }

  /**
   * Apply regional cost adjustment factor.
   *
   * <p>
   * Regional factors reflect differences in rig markets, labor costs, logistics, and regulatory
   * requirements.
   * </p>
   */
  private void applyRegionFactor() {
    switch (region) {
      case NORWAY:
        regionFactor = 1.35;
        break;
      case UK:
        regionFactor = 1.20;
        break;
      case GOM:
        regionFactor = 1.0;
        break;
      case BRAZIL:
        regionFactor = 0.90;
        break;
      case WEST_AFRICA:
        regionFactor = 1.10;
        break;
      default:
        regionFactor = 1.0;
        break;
    }

    // Adjust base rates by region
    rigDayRate *= regionFactor;
    casingCostPerTonne *= regionFactor;
    cementCostPerM3 *= regionFactor;
    mudCostPerM3 *= regionFactor;
    completionBaseCostProducer *= regionFactor;
    completionBaseCostInjector *= regionFactor;
    wellheadBaseCost *= regionFactor;
    loggingDayRate *= regionFactor;
    dhsvCost *= regionFactor;
    wellTestDayRate *= regionFactor;
  }

  /**
   * Calculate total well cost.
   *
   * @param wellType well type string (OIL_PRODUCER, GAS_PRODUCER, WATER_INJECTOR, etc.)
   * @param rigType rig type string (SEMI_SUBMERSIBLE, DRILLSHIP, etc.)
   * @param completionType completion type string
   * @param measuredDepth measured depth in meters
   * @param waterDepth water depth in meters
   * @param drillingDays planned drilling days
   * @param completionDays planned completion days
   * @param rigDayRateOverride rig day rate override (0 to use default)
   * @param hasDHSV whether well has downhole safety valve
   * @param numberOfCasingStrings number of casing strings
   */
  public void calculateWellCost(String wellType, String rigType, String completionType,
      double measuredDepth, double waterDepth, double drillingDays, double completionDays,
      double rigDayRateOverride, boolean hasDHSV, int numberOfCasingStrings) {

    double effectiveRigRate = rigDayRateOverride > 0 ? rigDayRateOverride : rigDayRate;

    // Adjust rig rate by type
    effectiveRigRate *= getRigTypeFactor(rigType);

    // ---- Drilling Cost ----
    // Rig time + spread cost during drilling
    drillingCost = effectiveRigRate * drillingDays;

    // ---- Casing Material ----
    // Estimate casing weight from depth and number of strings
    double estimatedCasingTonnes = estimateCasingWeight(measuredDepth, numberOfCasingStrings);
    casingMaterialCost = estimatedCasingTonnes * casingCostPerTonne;

    // ---- Cement ----
    double estimatedCementVolume = estimateCementVolume(measuredDepth, numberOfCasingStrings);
    cementCost = estimatedCementVolume * cementCostPerM3;

    // ---- Mud / Drilling Fluids ----
    double estimatedMudVolume = estimateMudVolume(measuredDepth);
    mudCost = estimatedMudVolume * mudCostPerM3;

    // ---- Bits and Tools ----
    // Typically 3-5% of drilling cost
    bitsCost = drillingCost * 0.04;

    // ---- Completion Cost ----
    boolean isProducer = "OIL_PRODUCER".equals(wellType) || "GAS_PRODUCER".equals(wellType);
    double completionBase = isProducer ? completionBaseCostProducer : completionBaseCostInjector;

    // Adjust for completion complexity
    completionBase *= getCompletionTypeFactor(completionType);

    // Rig time during completion
    completionCost = completionBase + effectiveRigRate * completionDays;

    // ---- Wellhead and Tree ----
    wellheadCost = wellheadBaseCost;
    // Deep water premium
    if (waterDepth > 500) {
      wellheadCost *= 1.15;
    }
    if (waterDepth > 1500) {
      wellheadCost *= 1.20;
    }

    // ---- Safety Valves ----
    safetyValveCost = hasDHSV ? dhsvCost : 0.0;

    // ---- Logging and Testing ----
    double loggingDays = isProducer ? 5.0 : 3.0;
    loggingCost = loggingDayRate * loggingDays;

    double wellTestDays = isProducer ? 3.0 : 1.0;
    wellTestCost = wellTestDayRate * wellTestDays;

    // ---- Contingency ----
    double subtotal = drillingCost + casingMaterialCost + cementCost + mudCost + bitsCost
        + completionCost + wellheadCost + safetyValveCost + loggingCost + wellTestCost;
    contingencyCost = subtotal * contingencyPct;

    // ---- Total ----
    totalCost = subtotal + contingencyCost;
  }

  /**
   * Get rig type cost factor.
   *
   * @param rigType rig type string
   * @return cost factor multiplier
   */
  private double getRigTypeFactor(String rigType) {
    if ("DRILLSHIP".equals(rigType)) {
      return 1.20;
    } else if ("SEMI_SUBMERSIBLE".equals(rigType)) {
      return 1.0;
    } else if ("JACK_UP".equals(rigType)) {
      return 0.70;
    } else if ("PLATFORM_RIG".equals(rigType)) {
      return 0.60;
    }
    return 1.0;
  }

  /**
   * Get completion type cost factor.
   *
   * @param completionType completion type string
   * @return cost factor multiplier
   */
  private double getCompletionTypeFactor(String completionType) {
    if ("OPEN_HOLE".equals(completionType)) {
      return 0.70;
    } else if ("CASED_PERFORATED".equals(completionType)) {
      return 1.0;
    } else if ("GRAVEL_PACK".equals(completionType)) {
      return 1.30;
    } else if ("ICD".equals(completionType)) {
      return 1.25;
    } else if ("AICD".equals(completionType)) {
      return 1.45;
    } else if ("MULTI_ZONE".equals(completionType)) {
      return 1.80;
    }
    return 1.0;
  }

  /**
   * Estimate casing weight from well depth and string count.
   *
   * @param measuredDepth measured depth in meters
   * @param numberOfStrings number of casing strings
   * @return estimated weight in tonnes
   */
  private double estimateCasingWeight(double measuredDepth, int numberOfStrings) {
    // Average 100-150 kg/m for a typical multi-string casing program
    double averageLinearWeight = 80.0 + 20.0 * numberOfStrings; // kg/m
    return averageLinearWeight * measuredDepth / 1000.0;
  }

  /**
   * Estimate cement volume from depth and string count.
   *
   * @param measuredDepth measured depth in meters
   * @param numberOfStrings number of casing strings
   * @return cement volume in m3
   */
  private double estimateCementVolume(double measuredDepth, int numberOfStrings) {
    // Roughly 0.05 m3 per meter per string (annular volume)
    return 0.05 * measuredDepth * numberOfStrings / 2.0;
  }

  /**
   * Estimate mud volume from depth.
   *
   * @param measuredDepth measured depth in meters
   * @return mud volume in m3
   */
  private double estimateMudVolume(double measuredDepth) {
    // Active volume + losses, typically 100-300 m3
    return 100.0 + 0.05 * measuredDepth;
  }

  /**
   * Get cost breakdown as a nested Map for reporting.
   *
   * @return map of cost categories and values
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    Map<String, Object> drilling = new LinkedHashMap<String, Object>();
    drilling.put("rigCost", drillingCost);
    drilling.put("casingMaterial", casingMaterialCost);
    drilling.put("cement", cementCost);
    drilling.put("mud", mudCost);
    drilling.put("bitsAndTools", bitsCost);
    drilling.put("subtotal", drillingCost + casingMaterialCost + cementCost + mudCost + bitsCost);
    breakdown.put("drilling", drilling);

    Map<String, Object> completion = new LinkedHashMap<String, Object>();
    completion.put("completionEquipment", completionCost);
    completion.put("wellhead", wellheadCost);
    completion.put("safetyValves", safetyValveCost);
    completion.put("subtotal", completionCost + wellheadCost + safetyValveCost);
    breakdown.put("completion", completion);

    Map<String, Object> evaluation = new LinkedHashMap<String, Object>();
    evaluation.put("logging", loggingCost);
    evaluation.put("wellTest", wellTestCost);
    evaluation.put("subtotal", loggingCost + wellTestCost);
    breakdown.put("evaluation", evaluation);

    breakdown.put("contingencyPct", contingencyPct * 100.0);
    breakdown.put("contingency", contingencyCost);
    breakdown.put("totalCost", totalCost);
    breakdown.put("region", region.name());
    breakdown.put("regionFactor", regionFactor);

    return breakdown;
  }

  /**
   * Generate bill of materials for a well.
   *
   * @param well the SubseaWell to generate BOM for
   * @return list of BOM items as maps
   */
  public List<Map<String, Object>> generateBillOfMaterials(SubseaWell well) {
    List<Map<String, Object>> bom = new ArrayList<Map<String, Object>>();

    // Conductor casing
    if (well.getConductorDepth() > 0) {
      bom.add(createBOMItem("Conductor Casing",
          String.format("%.0f\" conductor", well.getConductorOD()), "Casing - Conductor",
          well.getConductorDepth(), "m"));
    }

    // Surface casing
    if (well.getSurfaceCasingDepth() > 0) {
      bom.add(createBOMItem("Surface Casing",
          String.format("%.3f\" surface casing", well.getSurfaceCasingOD()), "Casing - Surface",
          well.getSurfaceCasingDepth(), "m"));
    }

    // Intermediate casing
    if (well.getIntermediateCasingDepth() > 0) {
      bom.add(createBOMItem("Intermediate Casing",
          String.format("%.3f\" intermediate casing", well.getIntermediateCasingOD()),
          "Casing - Intermediate", well.getIntermediateCasingDepth(), "m"));
    }

    // Production casing
    if (well.getProductionCasingDepth() > 0) {
      bom.add(createBOMItem("Production Casing",
          String.format("%.3f\" production casing", well.getProductionCasingOD()),
          "Casing - Production", well.getProductionCasingDepth(), "m"));
    }

    // Tubing
    bom.add(createBOMItem("Production Tubing",
        String.format("%.1f\" %s tubing", well.getTubingOD(), well.getTubingGrade()), "Tubing",
        well.getProductionCasingDepth(), "m"));

    // Wellhead
    bom.add(createBOMItem("Subsea Wellhead", "18-3/4\" wellhead assembly", "Wellhead", 1, "ea"));

    // DHSV
    if (well.hasDHSV()) {
      bom.add(createBOMItem("DHSV", "Downhole Safety Valve", "Safety Equipment", 1, "ea"));
    }

    // Cement
    bom.add(createBOMItem("Cement", "Class G cement + additives", "Consumables",
        totalCementVolumeFrom(well), "m3"));

    return bom;
  }

  /**
   * Create a BOM item.
   *
   * @param name item name
   * @param description item description
   * @param category item category
   * @param quantity quantity
   * @param unit unit of measure
   * @return map representing the BOM item
   */
  private Map<String, Object> createBOMItem(String name, String description, String category,
      double quantity, String unit) {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("name", name);
    item.put("description", description);
    item.put("category", category);
    item.put("quantity", quantity);
    item.put("unit", unit);
    return item;
  }

  /**
   * Estimate total cement volume from well properties.
   *
   * @param well the subsea well
   * @return estimated cement volume in m3
   */
  private double totalCementVolumeFrom(SubseaWell well) {
    double depth = well.getProductionCasingDepth();
    if (depth <= 0) {
      depth = 3500.0;
    }
    int strings = well.getNumberOfCasingStrings();
    return estimateCementVolume(depth, strings);
  }

  /**
   * Convert results to JSON string.
   *
   * @return JSON representation of cost estimate
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getCostBreakdown());
  }

  // ============ Getters ============

  /**
   * Get total cost.
   *
   * @return total well cost in USD
   */
  public double getTotalCost() {
    return totalCost;
  }

  /**
   * Get drilling cost.
   *
   * @return drilling cost in USD
   */
  public double getDrillingCost() {
    return drillingCost;
  }

  /**
   * Get completion cost.
   *
   * @return completion cost in USD
   */
  public double getCompletionCost() {
    return completionCost;
  }

  /**
   * Get wellhead cost.
   *
   * @return wellhead and tree cost in USD
   */
  public double getWellheadCost() {
    return wellheadCost;
  }

  /**
   * Get logging cost.
   *
   * @return logging and evaluation cost in USD
   */
  public double getLoggingCost() {
    return loggingCost;
  }

  /**
   * Get casing material cost.
   *
   * @return casing material cost in USD
   */
  public double getCasingMaterialCost() {
    return casingMaterialCost;
  }

  /**
   * Get contingency cost.
   *
   * @return contingency allowance in USD
   */
  public double getContingencyCost() {
    return contingencyCost;
  }

  /**
   * Get contingency percentage.
   *
   * @return contingency as a fraction (e.g., 0.15 for 15%)
   */
  public double getContingencyPct() {
    return contingencyPct;
  }

  /**
   * Get region.
   *
   * @return the cost region
   */
  public SubseaCostEstimator.Region getRegion() {
    return region;
  }

  /**
   * Set region.
   *
   * @param region the cost region
   */
  public void setRegion(SubseaCostEstimator.Region region) {
    this.region = region;
    applyRegionFactor();
  }

  /**
   * Set contingency percentage.
   *
   * @param contingencyPct contingency as a fraction (e.g., 0.15 for 15%)
   */
  public void setContingencyPct(double contingencyPct) {
    this.contingencyPct = contingencyPct;
  }

  /**
   * Set rig day rate.
   *
   * @param rigDayRate rig day rate in USD
   */
  public void setRigDayRate(double rigDayRate) {
    this.rigDayRate = rigDayRate;
  }

  /**
   * Get cement cost.
   *
   * @return cement cost in USD
   */
  public double getCementCost() {
    return cementCost;
  }

  /**
   * Get mud cost.
   *
   * @return drilling mud cost in USD
   */
  public double getMudCost() {
    return mudCost;
  }

  /**
   * Get bits and tools cost.
   *
   * @return bits cost in USD
   */
  public double getBitsCost() {
    return bitsCost;
  }

  /**
   * Get safety valve cost.
   *
   * @return safety valve cost in USD
   */
  public double getSafetyValveCost() {
    return safetyValveCost;
  }

  /**
   * Get well test cost.
   *
   * @return well test cost in USD
   */
  public double getWellTestCost() {
    return wellTestCost;
  }

  /**
   * Get regional cost factor.
   *
   * @return regional factor multiplier
   */
  public double getRegionFactor() {
    return regionFactor;
  }
}
