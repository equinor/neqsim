package neqsim.process.mechanicaldesign.subsea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * SURF (Subsea, Umbilicals, Risers, Flowlines) CAPEX estimator for field development.
 *
 * <p>
 * Aggregates cost estimates for all major SURF equipment categories:
 * </p>
 * <ul>
 * <li><b>S</b> — Subsea infrastructure: Christmas trees, manifolds, PLETs, jumpers</li>
 * <li><b>U</b> — Umbilicals: control umbilicals from host platform to subsea field</li>
 * <li><b>R</b> — Risers: dynamic or rigid risers from seabed to host platform</li>
 * <li><b>F</b> — Flowlines: infield flowlines and export pipelines</li>
 * </ul>
 *
 * <p>
 * Cost estimates are based on industry benchmarks for the Norwegian Continental Shelf (NCS) and can
 * be adjusted for other regions using the {@link SubseaCostEstimator.Region} parameter. All costs
 * are in USD unless converted.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaCostEstimator
 */
public class SURFCostEstimator {

  // ============ Field Configuration ============
  /** Number of production wells. */
  private int numberOfWells = 4;

  /** Water depth in meters. */
  private double waterDepthM = 300.0;

  /** Region for cost adjustment. */
  private SubseaCostEstimator.Region region = SubseaCostEstimator.Region.NORWAY;

  // ============ Subsea Infrastructure ============
  /** Tree pressure rating in psi. */
  private double treePressureRatingPsi = 10000.0;

  /** Tree bore size in inches. */
  private double treeBoreSizeInches = 5.0;

  /** Whether trees are horizontal. */
  private boolean horizontalTrees = true;

  /** Whether trees are dual bore. */
  private boolean dualBoreTrees = false;

  /** Number of manifold slots. */
  private int manifoldSlots = 4;

  /** Manifold dry weight in tonnes. */
  private double manifoldWeightTonnes = 120.0;

  /** Whether manifold has test header. */
  private boolean manifoldHasTestHeader = true;

  /** Number of PLETs. */
  private int numberOfPLETs = 2;

  /** PLET dry weight in tonnes. */
  private double pletWeightTonnes = 25.0;

  /** PLET hub size in inches. */
  private double pletHubSizeInches = 16.0;

  /** Number of jumpers (well to manifold). */
  private int numberOfJumpers = 0;

  /** Jumper length in meters. */
  private double jumperLengthM = 30.0;

  /** Jumper diameter in inches. */
  private double jumperDiameterInches = 6.0;

  /** Whether jumpers are rigid. */
  private boolean rigidJumpers = true;

  // ============ Umbilical Configuration ============
  /** Umbilical length in km. */
  private double umbilicalLengthKm = 10.0;

  /** Number of hydraulic lines. */
  private int umbilicalHydraulicLines = 4;

  /** Number of chemical injection lines. */
  private int umbilicalChemicalLines = 2;

  /** Number of electrical cables. */
  private int umbilicalElectricalCables = 2;

  /** Whether umbilical has dynamic section. */
  private boolean umbilicalDynamic = true;

  // ============ Riser Configuration ============
  /** Whether risers are included. */
  private boolean includeRisers = true;

  /** Riser inner diameter in inches. */
  private double riserDiameterInches = 8.0;

  /** Number of production risers. */
  private int numberOfProductionRisers = 1;

  /** Riser length in meters. */
  private double riserLengthM = 0.0;

  /** Whether riser is flexible. */
  private boolean flexibleRiser = true;

  /** Whether dynamic riser has buoyancy modules. */
  private boolean riserHasBuoyancy = true;

  // ============ Flowline Configuration ============
  /** Infield flowline length in km. */
  private double infieldFlowlineLengthKm = 10.0;

  /** Infield flowline diameter in inches. */
  private double infieldFlowlineDiameterInches = 14.0;

  /** Whether infield flowline is flexible. */
  private boolean infieldFlowlineFlexible = false;

  /** Export pipeline length in km. */
  private double exportPipelineLengthKm = 80.0;

  /** Export pipeline diameter in inches. */
  private double exportPipelineDiameterInches = 24.0;

  /** Pipeline installation method. */
  private String pipelineInstallMethod = "S-lay";

  /** Pipeline steel grade. */
  private String pipelineMaterialGrade = "X65";

  /** Pipeline design pressure in bar. */
  private double pipelineDesignPressureBar = 165.0;

  /** Pipeline wall thickness in mm (0 = auto-calculate). */
  private double pipelineWallThicknessMm = 0.0;

  // ============ Cost Overrides ============
  /** Pipeline steel price per kg USD. */
  private double steelPricePerKg = 1.50;

  /** Pipeline coating price per m2 USD. */
  private double coatingPricePerM2 = 80.0;

  /** Contingency percentage (0-1). */
  private double contingencyPct = 0.15;

  // ============ Calculated Results ============
  /** Total subsea infrastructure cost (trees + manifold + PLETs + jumpers) in USD. */
  private double subseaCostUSD = 0.0;

  /** Total umbilical cost in USD. */
  private double umbilicalCostUSD = 0.0;

  /** Total riser cost in USD. */
  private double riserCostUSD = 0.0;

  /** Total flowline cost in USD. */
  private double flowlineCostUSD = 0.0;

  /** Grand total SURF CAPEX in USD. */
  private double totalSURFCostUSD = 0.0;

  /** Line-item cost breakdown. */
  private List<Map<String, Object>> lineItems = new ArrayList<Map<String, Object>>();

  /**
   * Default constructor.
   */
  public SURFCostEstimator() {}

  /**
   * Constructor with basic field parameters.
   *
   * @param numberOfWells number of production wells
   * @param waterDepthM water depth in meters
   * @param region cost estimation region
   */
  public SURFCostEstimator(int numberOfWells, double waterDepthM,
      SubseaCostEstimator.Region region) {
    this.numberOfWells = numberOfWells;
    this.waterDepthM = waterDepthM;
    this.region = region;
    this.numberOfJumpers = numberOfWells;
    this.manifoldSlots = numberOfWells;
  }

  /**
   * Calculate complete SURF CAPEX breakdown.
   *
   * <p>
   * Estimates costs for all SURF components using industry benchmark rates and scales for water
   * depth, number of wells, and region.
   * </p>
   *
   * @return total SURF cost in USD
   */
  public double calculate() {
    lineItems.clear();
    subseaCostUSD = 0.0;
    umbilicalCostUSD = 0.0;
    riserCostUSD = 0.0;
    flowlineCostUSD = 0.0;

    calculateSubseaInfrastructure();
    calculateUmbilicals();
    calculateRisers();
    calculateFlowlines();

    totalSURFCostUSD = subseaCostUSD + umbilicalCostUSD + riserCostUSD + flowlineCostUSD;

    return totalSURFCostUSD;
  }

  /**
   * Calculate Subsea infrastructure costs (trees, manifold, PLETs, jumpers).
   */
  private void calculateSubseaInfrastructure() {
    SubseaCostEstimator est = new SubseaCostEstimator(region);

    // Christmas trees
    est.calculateTreeCost(treePressureRatingPsi, treeBoreSizeInches, waterDepthM, horizontalTrees,
        dualBoreTrees);
    double treeCost = est.getTotalCost();
    double totalTreeCost = treeCost * numberOfWells;
    subseaCostUSD += totalTreeCost;
    addLineItem("S", "Christmas Trees", numberOfWells, "ea", treeCost, totalTreeCost,
        est.getVesselDays() * numberOfWells);

    // Manifold
    est = new SubseaCostEstimator(region);
    est.calculateManifoldCost(manifoldSlots, manifoldWeightTonnes, waterDepthM,
        manifoldHasTestHeader);
    double manifoldCost = est.getTotalCost();
    subseaCostUSD += manifoldCost;
    addLineItem("S", "Subsea Manifold (" + manifoldSlots + "-slot)", 1, "ea", manifoldCost,
        manifoldCost, est.getVesselDays());

    // PLETs
    if (numberOfPLETs > 0) {
      est = new SubseaCostEstimator(region);
      est.calculatePLETCost(pletWeightTonnes, pletHubSizeInches, waterDepthM, true, false);
      double pletCost = est.getTotalCost();
      double totalPletCost = pletCost * numberOfPLETs;
      subseaCostUSD += totalPletCost;
      addLineItem("S", "PLETs", numberOfPLETs, "ea", pletCost, totalPletCost,
          est.getVesselDays() * numberOfPLETs);
    }

    // Jumpers (well to manifold)
    if (numberOfJumpers > 0) {
      est = new SubseaCostEstimator(region);
      est.calculateJumperCost(jumperLengthM, jumperDiameterInches, rigidJumpers, waterDepthM);
      double jumperCost = est.getTotalCost();
      double totalJumperCost = jumperCost * numberOfJumpers;
      subseaCostUSD += totalJumperCost;
      addLineItem("S", "Jumpers (well-manifold)", numberOfJumpers, "ea", jumperCost,
          totalJumperCost, est.getVesselDays() * numberOfJumpers);
    }
  }

  /**
   * Calculate umbilical costs.
   */
  private void calculateUmbilicals() {
    SubseaCostEstimator est = new SubseaCostEstimator(region);
    est.calculateUmbilicalCost(umbilicalLengthKm, umbilicalHydraulicLines, umbilicalChemicalLines,
        umbilicalElectricalCables, waterDepthM, umbilicalDynamic);
    umbilicalCostUSD = est.getTotalCost();
    addLineItem("U", "Control Umbilical (" + String.format("%.0f", umbilicalLengthKm) + " km)", 1,
        "ea", umbilicalCostUSD, umbilicalCostUSD, est.getVesselDays());
  }

  /**
   * Calculate riser costs.
   */
  private void calculateRisers() {
    if (!includeRisers) {
      return;
    }

    double effectiveRiserLength = riserLengthM > 0 ? riserLengthM : waterDepthM * 1.5;

    for (int i = 0; i < numberOfProductionRisers; i++) {
      SubseaCostEstimator est = new SubseaCostEstimator(region);
      if (flexibleRiser) {
        est.calculateFlexiblePipeCost(effectiveRiserLength, riserDiameterInches, waterDepthM, true,
            riserHasBuoyancy);
      } else {
        // Rigid riser treated as rigid jumper with vertical orientation
        est.calculateJumperCost(effectiveRiserLength, riserDiameterInches, true, waterDepthM);
      }
      double riserCost = est.getTotalCost();
      riserCostUSD += riserCost;
      addLineItem("R",
          (flexibleRiser ? "Flexible" : "Rigid") + " Production Riser "
              + String.format("%.0f", riserDiameterInches) + "\"",
          1, "ea", riserCost, riserCost, est.getVesselDays());
    }
  }

  /**
   * Calculate flowline and pipeline costs.
   */
  private void calculateFlowlines() {
    // Infield flowline
    if (infieldFlowlineLengthKm > 0) {
      double infieldCost;
      if (infieldFlowlineFlexible) {
        SubseaCostEstimator est = new SubseaCostEstimator(region);
        est.calculateFlexiblePipeCost(infieldFlowlineLengthKm * 1000, infieldFlowlineDiameterInches,
            waterDepthM, false, false);
        infieldCost = est.getTotalCost();
        flowlineCostUSD += infieldCost;
        addLineItem("F",
            "Infield Flowline " + String.format("%.0f", infieldFlowlineDiameterInches) + "\" ("
                + String.format("%.0f", infieldFlowlineLengthKm) + " km, flexible)",
            1, "ea", infieldCost, infieldCost, est.getVesselDays());
      } else {
        infieldCost = calculateRigidPipelineCost(infieldFlowlineLengthKm,
            infieldFlowlineDiameterInches, "Infield Flowline");
      }
    }

    // Export pipeline
    if (exportPipelineLengthKm > 0) {
      double exportCost = calculateRigidPipelineCost(exportPipelineLengthKm,
          exportPipelineDiameterInches, "Export Pipeline");
    }
  }

  /**
   * Calculate cost for a rigid steel pipeline.
   *
   * @param lengthKm pipeline length in km
   * @param diameterInches outer diameter in inches
   * @param label description label
   * @return total cost in USD
   */
  private double calculateRigidPipelineCost(double lengthKm, double diameterInches, String label) {
    double lengthM = lengthKm * 1000.0;
    double outerDiameterM = diameterInches * 0.0254;

    // Wall thickness calculation (simplified Barlow formula with design factor)
    double wallThicknessM;
    if (pipelineWallThicknessMm > 0) {
      wallThicknessM = pipelineWallThicknessMm / 1000.0;
    } else {
      // Simplified: t = P * D / (2 * SMYS * F * E)
      double smys = getSMYSForGrade(pipelineMaterialGrade);
      double designFactor = 0.72; // ASME B31.8 Location Class 1
      double jointFactor = 1.0;
      double designPressureMPa = pipelineDesignPressureBar / 10.0;
      wallThicknessM =
          designPressureMPa * outerDiameterM / (2.0 * smys * designFactor * jointFactor);
      // Add corrosion allowance (3mm) and fabrication tolerance (12.5%)
      wallThicknessM = (wallThicknessM + 0.003) / (1.0 - 0.125);
      // Round up to nearest 0.5mm
      wallThicknessM = Math.ceil(wallThicknessM * 2000.0) / 2000.0;
    }

    // Steel weight per meter
    double innerDiameterM = outerDiameterM - 2.0 * wallThicknessM;
    double steelAreaM2 =
        Math.PI * (outerDiameterM * outerDiameterM - innerDiameterM * innerDiameterM) / 4.0;
    double steelWeightPerM = steelAreaM2 * 7850.0; // kg/m
    double totalSteelWeight = steelWeightPerM * lengthM;

    // Material cost
    double steelCostUSD = totalSteelWeight * steelPricePerKg;

    // Coating cost (external surface)
    double externalSurfaceM2 = Math.PI * outerDiameterM * lengthM;
    double coatingCostUSD = externalSurfaceM2 * coatingPricePerM2;

    // Installation cost per meter (varies by method and diameter)
    double installCostPerM =
        getInstallationCostPerMeter(pipelineInstallMethod, outerDiameterM, waterDepthM);
    double installCostUSD = installCostPerM * lengthM;

    // Welding cost
    double jointLength = 12.2; // standard pipe joint length in meters
    int numberOfJoints = (int) Math.ceil(lengthM / jointLength);
    double weldCostPerJoint = 3000.0; // USD per field weld
    double weldingCostUSD = numberOfJoints * weldCostPerJoint;

    // Direct cost subtotal
    double directCost = steelCostUSD + coatingCostUSD + installCostUSD + weldingCostUSD;

    // Indirect costs
    double engineeringUSD = directCost * 0.08;
    double testingUSD = directCost * 0.03;
    double contingencyUSD = directCost * contingencyPct;

    double totalCost = directCost + engineeringUSD + testingUSD + contingencyUSD;
    flowlineCostUSD += totalCost;

    // Estimate vessel days
    double layRateKmPerDay;
    if ("S-lay".equals(pipelineInstallMethod)) {
      layRateKmPerDay = 2.0;
    } else if ("J-lay".equals(pipelineInstallMethod)) {
      layRateKmPerDay = 1.5;
    } else if ("Reel-lay".equals(pipelineInstallMethod)) {
      layRateKmPerDay = 3.0;
    } else {
      layRateKmPerDay = 2.0;
    }
    double vesselDays = lengthKm / layRateKmPerDay + 5; // Plus mob/demob

    addLineItem("F",
        label + " " + String.format("%.0f", diameterInches) + "\" x "
            + String.format("%.1f", wallThicknessM * 1000) + "mm WT ("
            + String.format("%.0f", lengthKm) + " km, " + pipelineMaterialGrade + ")",
        1, "ea", totalCost, totalCost, vesselDays);

    return totalCost;
  }

  /**
   * Get SMYS for common pipe steel grades.
   *
   * @param grade material grade (e.g. "X52", "X65", "X70")
   * @return SMYS in MPa
   */
  private double getSMYSForGrade(String grade) {
    if ("X42".equals(grade)) {
      return 290.0;
    } else if ("X52".equals(grade)) {
      return 359.0;
    } else if ("X60".equals(grade)) {
      return 414.0;
    } else if ("X65".equals(grade)) {
      return 448.0;
    } else if ("X70".equals(grade)) {
      return 483.0;
    } else if ("X80".equals(grade)) {
      return 551.0;
    }
    return 448.0; // Default to X65
  }

  /**
   * Get installation cost per meter for pipeline.
   *
   * @param method installation method (S-lay, J-lay, Reel-lay)
   * @param outerDiameterM outer diameter in meters
   * @param waterDepth water depth in meters
   * @return cost per meter in USD
   */
  private double getInstallationCostPerMeter(String method, double outerDiameterM,
      double waterDepth) {
    double baseCost;
    double depthFactor;

    if ("S-lay".equals(method)) {
      baseCost = 800.0;
      depthFactor = 2.0;
    } else if ("J-lay".equals(method)) {
      baseCost = 1200.0;
      depthFactor = 3.0;
    } else if ("Reel-lay".equals(method)) {
      baseCost = 600.0;
      depthFactor = 1.5;
    } else {
      baseCost = 300.0;
      depthFactor = 0.0;
    }

    // Scale by diameter (normalized to 20" = 0.508m)
    double diameterFactor = outerDiameterM / 0.508;

    // Region factor
    double regionFactor = getRegionFactor();

    return (baseCost + waterDepth * depthFactor) * diameterFactor * regionFactor;
  }

  /**
   * Get cost adjustment factor for region.
   *
   * @return region factor
   */
  private double getRegionFactor() {
    switch (region) {
      case NORWAY:
        return 1.35;
      case UK:
        return 1.25;
      case GOM:
        return 1.0;
      case BRAZIL:
        return 0.85;
      case WEST_AFRICA:
        return 1.1;
      default:
        return 1.0;
    }
  }

  /**
   * Add a line item to the cost breakdown.
   *
   * @param category SURF category (S, U, R, F)
   * @param description item description
   * @param quantity quantity
   * @param unit unit of measure
   * @param unitCostUSD unit cost in USD
   * @param totalCostUSD total cost in USD
   * @param vesselDays vessel days required
   */
  private void addLineItem(String category, String description, int quantity, String unit,
      double unitCostUSD, double totalCostUSD, double vesselDays) {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("category", category);
    item.put("description", description);
    item.put("quantity", quantity);
    item.put("unit", unit);
    item.put("unitCostUSD", unitCostUSD);
    item.put("totalCostUSD", totalCostUSD);
    item.put("vesselDays", vesselDays);
    lineItems.add(item);
  }

  /**
   * Get complete cost breakdown as a map.
   *
   * @return map with SURF cost breakdown
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Summary by SURF category
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("subsea_S_USD", subseaCostUSD);
    summary.put("umbilical_U_USD", umbilicalCostUSD);
    summary.put("riser_R_USD", riserCostUSD);
    summary.put("flowline_F_USD", flowlineCostUSD);
    summary.put("totalSURF_USD", totalSURFCostUSD);
    result.put("categorySummary", summary);

    // Percentage breakdown
    if (totalSURFCostUSD > 0) {
      Map<String, Object> pct = new LinkedHashMap<String, Object>();
      pct.put("subsea_S_pct", subseaCostUSD / totalSURFCostUSD * 100);
      pct.put("umbilical_U_pct", umbilicalCostUSD / totalSURFCostUSD * 100);
      pct.put("riser_R_pct", riserCostUSD / totalSURFCostUSD * 100);
      pct.put("flowline_F_pct", flowlineCostUSD / totalSURFCostUSD * 100);
      result.put("categoryPercentages", pct);
    }

    // Line items
    result.put("lineItems", lineItems);

    // Field configuration
    Map<String, Object> config = new LinkedHashMap<String, Object>();
    config.put("numberOfWells", numberOfWells);
    config.put("waterDepthM", waterDepthM);
    config.put("region", region.name());
    config.put("contingencyPct", contingencyPct * 100);
    result.put("fieldConfiguration", config);

    // Total vessel days
    double totalVesselDays = 0;
    for (Map<String, Object> item : lineItems) {
      Object vd = item.get("vesselDays");
      if (vd instanceof Number) {
        totalVesselDays += ((Number) vd).doubleValue();
      }
    }
    result.put("totalVesselDays", totalVesselDays);

    return result;
  }

  /**
   * Get cost breakdown for a specific SURF category.
   *
   * @param category category code: "S", "U", "R", or "F"
   * @return list of line items for that category
   */
  public List<Map<String, Object>> getCategoryLineItems(String category) {
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> item : lineItems) {
      if (category.equals(item.get("category"))) {
        items.add(item);
      }
    }
    return items;
  }

  /**
   * Convert total SURF cost from USD to another currency.
   *
   * @param exchangeRate exchange rate (units of target currency per 1 USD)
   * @return total cost in target currency
   */
  public double getTotalCostInCurrency(double exchangeRate) {
    return totalSURFCostUSD * exchangeRate;
  }

  /**
   * Get cost as JSON string.
   *
   * @return JSON string with complete SURF cost breakdown
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getCostBreakdown());
  }

  // ============ Getters for cost results ============

  /**
   * Get total SURF cost in USD.
   *
   * @return total SURF CAPEX in USD
   */
  public double getTotalSURFCostUSD() {
    return totalSURFCostUSD;
  }

  /**
   * Get subsea infrastructure cost in USD.
   *
   * @return subsea cost (trees + manifold + PLETs + jumpers) in USD
   */
  public double getSubseaCostUSD() {
    return subseaCostUSD;
  }

  /**
   * Get umbilical cost in USD.
   *
   * @return umbilical cost in USD
   */
  public double getUmbilicalCostUSD() {
    return umbilicalCostUSD;
  }

  /**
   * Get riser cost in USD.
   *
   * @return riser cost in USD
   */
  public double getRiserCostUSD() {
    return riserCostUSD;
  }

  /**
   * Get flowline cost in USD.
   *
   * @return flowline + pipeline cost in USD
   */
  public double getFlowlineCostUSD() {
    return flowlineCostUSD;
  }

  /**
   * Get all line items.
   *
   * @return list of cost line items
   */
  public List<Map<String, Object>> getLineItems() {
    return new ArrayList<Map<String, Object>>(lineItems);
  }

  // ============ Setters for configuration ============

  /**
   * Set number of production wells.
   *
   * @param numberOfWells number of wells
   */
  public void setNumberOfWells(int numberOfWells) {
    this.numberOfWells = numberOfWells;
  }

  /**
   * Set water depth.
   *
   * @param waterDepthM water depth in meters
   */
  public void setWaterDepthM(double waterDepthM) {
    this.waterDepthM = waterDepthM;
  }

  /**
   * Set region for cost estimation.
   *
   * @param region cost region
   */
  public void setRegion(SubseaCostEstimator.Region region) {
    this.region = region;
  }

  /**
   * Set Christmas tree pressure rating.
   *
   * @param treePressureRatingPsi pressure rating in psi
   */
  public void setTreePressureRatingPsi(double treePressureRatingPsi) {
    this.treePressureRatingPsi = treePressureRatingPsi;
  }

  /**
   * Set tree bore size.
   *
   * @param treeBoreSizeInches bore size in inches
   */
  public void setTreeBoreSizeInches(double treeBoreSizeInches) {
    this.treeBoreSizeInches = treeBoreSizeInches;
  }

  /**
   * Set whether trees are horizontal.
   *
   * @param horizontalTrees true for horizontal trees
   */
  public void setHorizontalTrees(boolean horizontalTrees) {
    this.horizontalTrees = horizontalTrees;
  }

  /**
   * Set whether trees are dual bore.
   *
   * @param dualBoreTrees true for dual bore trees
   */
  public void setDualBoreTrees(boolean dualBoreTrees) {
    this.dualBoreTrees = dualBoreTrees;
  }

  /**
   * Set number of manifold slots.
   *
   * @param manifoldSlots number of slots
   */
  public void setManifoldSlots(int manifoldSlots) {
    this.manifoldSlots = manifoldSlots;
  }

  /**
   * Set manifold weight.
   *
   * @param manifoldWeightTonnes dry weight in tonnes
   */
  public void setManifoldWeightTonnes(double manifoldWeightTonnes) {
    this.manifoldWeightTonnes = manifoldWeightTonnes;
  }

  /**
   * Set whether manifold has test header.
   *
   * @param manifoldHasTestHeader true if has test header
   */
  public void setManifoldHasTestHeader(boolean manifoldHasTestHeader) {
    this.manifoldHasTestHeader = manifoldHasTestHeader;
  }

  /**
   * Set number of PLETs.
   *
   * @param numberOfPLETs number of PLETs
   */
  public void setNumberOfPLETs(int numberOfPLETs) {
    this.numberOfPLETs = numberOfPLETs;
  }

  /**
   * Set PLET weight.
   *
   * @param pletWeightTonnes dry weight in tonnes
   */
  public void setPletWeightTonnes(double pletWeightTonnes) {
    this.pletWeightTonnes = pletWeightTonnes;
  }

  /**
   * Set PLET hub size.
   *
   * @param pletHubSizeInches hub size in inches
   */
  public void setPletHubSizeInches(double pletHubSizeInches) {
    this.pletHubSizeInches = pletHubSizeInches;
  }

  /**
   * Set number of jumpers.
   *
   * @param numberOfJumpers number of jumpers
   */
  public void setNumberOfJumpers(int numberOfJumpers) {
    this.numberOfJumpers = numberOfJumpers;
  }

  /**
   * Set jumper length.
   *
   * @param jumperLengthM jumper length in meters
   */
  public void setJumperLengthM(double jumperLengthM) {
    this.jumperLengthM = jumperLengthM;
  }

  /**
   * Set jumper diameter.
   *
   * @param jumperDiameterInches jumper diameter in inches
   */
  public void setJumperDiameterInches(double jumperDiameterInches) {
    this.jumperDiameterInches = jumperDiameterInches;
  }

  /**
   * Set whether jumpers are rigid.
   *
   * @param rigidJumpers true for rigid jumpers
   */
  public void setRigidJumpers(boolean rigidJumpers) {
    this.rigidJumpers = rigidJumpers;
  }

  /**
   * Set umbilical length.
   *
   * @param umbilicalLengthKm length in km
   */
  public void setUmbilicalLengthKm(double umbilicalLengthKm) {
    this.umbilicalLengthKm = umbilicalLengthKm;
  }

  /**
   * Set umbilical hydraulic lines.
   *
   * @param umbilicalHydraulicLines number of hydraulic lines
   */
  public void setUmbilicalHydraulicLines(int umbilicalHydraulicLines) {
    this.umbilicalHydraulicLines = umbilicalHydraulicLines;
  }

  /**
   * Set umbilical chemical lines.
   *
   * @param umbilicalChemicalLines number of chemical injection lines
   */
  public void setUmbilicalChemicalLines(int umbilicalChemicalLines) {
    this.umbilicalChemicalLines = umbilicalChemicalLines;
  }

  /**
   * Set umbilical electrical cables.
   *
   * @param umbilicalElectricalCables number of electrical cables
   */
  public void setUmbilicalElectricalCables(int umbilicalElectricalCables) {
    this.umbilicalElectricalCables = umbilicalElectricalCables;
  }

  /**
   * Set whether umbilical has dynamic section.
   *
   * @param umbilicalDynamic true for dynamic umbilical
   */
  public void setUmbilicalDynamic(boolean umbilicalDynamic) {
    this.umbilicalDynamic = umbilicalDynamic;
  }

  /**
   * Set whether risers are included in the estimate.
   *
   * @param includeRisers true to include risers
   */
  public void setIncludeRisers(boolean includeRisers) {
    this.includeRisers = includeRisers;
  }

  /**
   * Set riser inner diameter.
   *
   * @param riserDiameterInches riser inner diameter in inches
   */
  public void setRiserDiameterInches(double riserDiameterInches) {
    this.riserDiameterInches = riserDiameterInches;
  }

  /**
   * Set number of production risers.
   *
   * @param numberOfProductionRisers number of production risers
   */
  public void setNumberOfProductionRisers(int numberOfProductionRisers) {
    this.numberOfProductionRisers = numberOfProductionRisers;
  }

  /**
   * Set riser length.
   *
   * @param riserLengthM riser length in meters (0 = auto from water depth)
   */
  public void setRiserLengthM(double riserLengthM) {
    this.riserLengthM = riserLengthM;
  }

  /**
   * Set whether riser is flexible.
   *
   * @param flexibleRiser true for flexible riser
   */
  public void setFlexibleRiser(boolean flexibleRiser) {
    this.flexibleRiser = flexibleRiser;
  }

  /**
   * Set whether riser has buoyancy modules.
   *
   * @param riserHasBuoyancy true for buoyancy modules
   */
  public void setRiserHasBuoyancy(boolean riserHasBuoyancy) {
    this.riserHasBuoyancy = riserHasBuoyancy;
  }

  /**
   * Set infield flowline length.
   *
   * @param infieldFlowlineLengthKm length in km
   */
  public void setInfieldFlowlineLengthKm(double infieldFlowlineLengthKm) {
    this.infieldFlowlineLengthKm = infieldFlowlineLengthKm;
  }

  /**
   * Set infield flowline diameter.
   *
   * @param infieldFlowlineDiameterInches OD in inches
   */
  public void setInfieldFlowlineDiameterInches(double infieldFlowlineDiameterInches) {
    this.infieldFlowlineDiameterInches = infieldFlowlineDiameterInches;
  }

  /**
   * Set whether infield flowline is flexible.
   *
   * @param infieldFlowlineFlexible true for flexible flowline
   */
  public void setInfieldFlowlineFlexible(boolean infieldFlowlineFlexible) {
    this.infieldFlowlineFlexible = infieldFlowlineFlexible;
  }

  /**
   * Set export pipeline length.
   *
   * @param exportPipelineLengthKm length in km
   */
  public void setExportPipelineLengthKm(double exportPipelineLengthKm) {
    this.exportPipelineLengthKm = exportPipelineLengthKm;
  }

  /**
   * Set export pipeline diameter.
   *
   * @param exportPipelineDiameterInches OD in inches
   */
  public void setExportPipelineDiameterInches(double exportPipelineDiameterInches) {
    this.exportPipelineDiameterInches = exportPipelineDiameterInches;
  }

  /**
   * Set pipeline installation method.
   *
   * @param pipelineInstallMethod installation method: "S-lay", "J-lay", "Reel-lay"
   */
  public void setPipelineInstallMethod(String pipelineInstallMethod) {
    this.pipelineInstallMethod = pipelineInstallMethod;
  }

  /**
   * Set pipeline material grade.
   *
   * @param pipelineMaterialGrade grade (e.g. "X65")
   */
  public void setPipelineMaterialGrade(String pipelineMaterialGrade) {
    this.pipelineMaterialGrade = pipelineMaterialGrade;
  }

  /**
   * Set pipeline design pressure.
   *
   * @param pipelineDesignPressureBar design pressure in bar
   */
  public void setPipelineDesignPressureBar(double pipelineDesignPressureBar) {
    this.pipelineDesignPressureBar = pipelineDesignPressureBar;
  }

  /**
   * Set pipeline wall thickness.
   *
   * @param pipelineWallThicknessMm wall thickness in mm (0 = auto-calculate)
   */
  public void setPipelineWallThicknessMm(double pipelineWallThicknessMm) {
    this.pipelineWallThicknessMm = pipelineWallThicknessMm;
  }

  /**
   * Set pipeline steel price.
   *
   * @param steelPricePerKg price per kg in USD
   */
  public void setSteelPricePerKg(double steelPricePerKg) {
    this.steelPricePerKg = steelPricePerKg;
  }

  /**
   * Set pipeline coating price.
   *
   * @param coatingPricePerM2 price per m2 in USD
   */
  public void setCoatingPricePerM2(double coatingPricePerM2) {
    this.coatingPricePerM2 = coatingPricePerM2;
  }

  /**
   * Set contingency percentage.
   *
   * @param contingencyPct contingency percentage (0.0 to 1.0)
   */
  public void setContingencyPct(double contingencyPct) {
    this.contingencyPct = contingencyPct;
  }

  /**
   * Set whether to exclude the export pipeline from the estimate.
   *
   * @param lengthKm set to 0.0 to exclude export pipeline
   */
  public void excludeExportPipeline(double lengthKm) {
    this.exportPipelineLengthKm = lengthKm;
  }
}
