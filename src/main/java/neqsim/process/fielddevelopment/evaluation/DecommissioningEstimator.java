package neqsim.process.fielddevelopment.evaluation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Decommissioning cost estimation for offshore oil and gas facilities.
 *
 * <p>
 * Provides order-of-magnitude cost estimates for late-life planning and abandonment liability
 * calculations. Based on Norwegian Continental Shelf (NCS) experience and industry benchmarks.
 * </p>
 *
 * <h2>Cost Categories</h2>
 * <ul>
 * <li><b>Well P&amp;A</b> - Plug and abandon wells</li>
 * <li><b>Topside removal</b> - Platform topside structures</li>
 * <li><b>Substructure removal</b> - Jackets, GBS, floaters</li>
 * <li><b>Pipeline decommissioning</b> - Pipelines and risers</li>
 * <li><b>Site remediation</b> - Seabed clearing</li>
 * </ul>
 *
 * <h2>Facility Types</h2>
 * <ul>
 * <li><b>Fixed platform</b> - Steel jacket or GBS</li>
 * <li><b>Floating production</b> - FPSO, semi-sub, TLP, spar</li>
 * <li><b>Subsea tieback</b> - Subsea wells tied to host</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * DecommissioningEstimator estimator =
 *     new DecommissioningEstimator().setFacilityType(FacilityType.FIXED_JACKET).setWaterDepth(120)
 *         .setTopsideWeight(15000).setNumberOfWells(12).setPipelineLength(45);
 * 
 * double totalCost = estimator.getTotalCostMUSD();
 * System.out.println("Decom cost: $" + totalCost + "M");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class DecommissioningEstimator implements Serializable {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // FACILITY PARAMETERS
  // ============================================================================

  /** Facility type. */
  private FacilityType facilityType = FacilityType.FIXED_JACKET;

  /** Water depth in meters. */
  private double waterDepthM = 100.0;

  /** Topside weight in tonnes. */
  private double topsideWeightTonnes = 10000.0;

  /** Jacket/substructure weight in tonnes. */
  private double substructureWeightTonnes = 8000.0;

  /** Number of wells to P&amp;A. */
  private int numberOfWells = 6;

  /** Average well depth in meters. */
  private double averageWellDepthM = 3000.0;

  /** Pipeline length in km. */
  private double pipelineLengthKm = 20.0;

  /** Pipeline diameter in inches. */
  private double pipelineDiameterInch = 16.0;

  /** Number of risers. */
  private int numberOfRisers = 2;

  /** Subsea manifolds/templates. */
  private int numberOfSubseaStructures = 0;

  // ============================================================================
  // COST FACTORS (2024 USD MUSD)
  // ============================================================================

  /** Well P&amp;A cost per well (shallow, simple). */
  private static final double WELL_PA_COST_SHALLOW = 8.0;

  /** Well P&amp;A cost per well (medium depth). */
  private static final double WELL_PA_COST_MEDIUM = 12.0;

  /** Well P&amp;A cost per well (deep, complex). */
  private static final double WELL_PA_COST_DEEP = 18.0;

  /** Topside removal cost per tonne. */
  private static final double TOPSIDE_REMOVAL_PER_TONNE = 0.015;

  /** Jacket removal base cost (shallow). */
  private static final double JACKET_REMOVAL_SHALLOW = 30.0;

  /** Jacket removal base cost (deep). */
  private static final double JACKET_REMOVAL_DEEP = 80.0;

  /** Pipeline decom cost per km (leave in place). */
  private static final double PIPELINE_DECOM_LIP_PER_KM = 0.3;

  /** Pipeline decom cost per km (full removal). */
  private static final double PIPELINE_DECOM_REMOVE_PER_KM = 2.0;

  /** Riser removal cost per riser. */
  private static final double RISER_REMOVAL_COST = 5.0;

  /** Site remediation base cost. */
  private static final double SITE_REMEDIATION_BASE = 10.0;

  /** Subsea structure removal cost each. */
  private static final double SUBSEA_STRUCTURE_REMOVAL = 15.0;

  // ============================================================================
  // FACILITY TYPES
  // ============================================================================

  /**
   * Facility type enumeration.
   */
  public enum FacilityType {
    /** Steel jacket platform. */
    FIXED_JACKET("Fixed Jacket", 1.0),

    /** Gravity-based structure. */
    GRAVITY_BASED("GBS", 1.5),

    /** Floating Production Storage Offloading. */
    FPSO("FPSO", 0.6),

    /** Semi-submersible platform. */
    SEMI_SUBMERSIBLE("Semi-sub", 0.8),

    /** Tension Leg Platform. */
    TLP("TLP", 1.2),

    /** Spar platform. */
    SPAR("Spar", 0.9),

    /** Subsea tieback (no platform). */
    SUBSEA_TIEBACK("Subsea", 0.3),

    /** Unmanned wellhead platform. */
    WELLHEAD_PLATFORM("WHP", 0.5),

    /** Compliant tower. */
    COMPLIANT_TOWER("Compliant", 1.1);

    private final String displayName;
    private final double costFactor;

    FacilityType(String displayName, double costFactor) {
      this.displayName = displayName;
      this.costFactor = costFactor;
    }

    public String getDisplayName() {
      return displayName;
    }

    public double getCostFactor() {
      return costFactor;
    }
  }

  /**
   * Pipeline decommissioning strategy.
   */
  public enum PipelineStrategy {
    /** Leave in place with rock dumping. */
    LEAVE_IN_PLACE("Leave in Place", 0.3),

    /** Trench and bury. */
    TRENCH_BURY("Trench & Bury", 1.0),

    /** Full removal. */
    FULL_REMOVAL("Full Removal", 2.5);

    private final String displayName;
    private final double costFactor;

    PipelineStrategy(String displayName, double costFactor) {
      this.displayName = displayName;
      this.costFactor = costFactor;
    }

    public String getDisplayName() {
      return displayName;
    }

    public double getCostFactor() {
      return costFactor;
    }
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  /**
   * Sets facility type.
   *
   * @param type facility type
   * @return this for chaining
   */
  public DecommissioningEstimator setFacilityType(FacilityType type) {
    this.facilityType = type;
    return this;
  }

  /**
   * Sets water depth.
   *
   * @param depthM water depth in meters
   * @return this for chaining
   */
  public DecommissioningEstimator setWaterDepth(double depthM) {
    this.waterDepthM = depthM;
    return this;
  }

  /**
   * Sets topside weight.
   *
   * @param tonnes topside weight in tonnes
   * @return this for chaining
   */
  public DecommissioningEstimator setTopsideWeight(double tonnes) {
    this.topsideWeightTonnes = tonnes;
    return this;
  }

  /**
   * Sets substructure weight.
   *
   * @param tonnes jacket/substructure weight in tonnes
   * @return this for chaining
   */
  public DecommissioningEstimator setSubstructureWeight(double tonnes) {
    this.substructureWeightTonnes = tonnes;
    return this;
  }

  /**
   * Sets number of wells.
   *
   * @param wells number of wells
   * @return this for chaining
   */
  public DecommissioningEstimator setNumberOfWells(int wells) {
    this.numberOfWells = wells;
    return this;
  }

  /**
   * Sets average well depth.
   *
   * @param depthM well depth in meters
   * @return this for chaining
   */
  public DecommissioningEstimator setAverageWellDepth(double depthM) {
    this.averageWellDepthM = depthM;
    return this;
  }

  /**
   * Sets pipeline length.
   *
   * @param lengthKm pipeline length in km
   * @return this for chaining
   */
  public DecommissioningEstimator setPipelineLength(double lengthKm) {
    this.pipelineLengthKm = lengthKm;
    return this;
  }

  /**
   * Sets pipeline diameter.
   *
   * @param diameterInch pipeline diameter in inches
   * @return this for chaining
   */
  public DecommissioningEstimator setPipelineDiameter(double diameterInch) {
    this.pipelineDiameterInch = diameterInch;
    return this;
  }

  /**
   * Sets number of risers.
   *
   * @param risers number of risers
   * @return this for chaining
   */
  public DecommissioningEstimator setNumberOfRisers(int risers) {
    this.numberOfRisers = risers;
    return this;
  }

  /**
   * Sets number of subsea structures.
   *
   * @param structures number of manifolds/templates
   * @return this for chaining
   */
  public DecommissioningEstimator setNumberOfSubseaStructures(int structures) {
    this.numberOfSubseaStructures = structures;
    return this;
  }

  // ============================================================================
  // COST CALCULATIONS
  // ============================================================================

  /**
   * Calculates total well P&amp;A cost.
   *
   * @return cost in MUSD
   */
  public double getWellPACostMUSD() {
    double costPerWell;
    if (averageWellDepthM < 2000) {
      costPerWell = WELL_PA_COST_SHALLOW;
    } else if (averageWellDepthM < 4000) {
      costPerWell = WELL_PA_COST_MEDIUM;
    } else {
      costPerWell = WELL_PA_COST_DEEP;
    }

    // Water depth adjustment
    if (waterDepthM > 200) {
      costPerWell *= 1.3;
    } else if (waterDepthM > 100) {
      costPerWell *= 1.1;
    }

    return numberOfWells * costPerWell;
  }

  /**
   * Calculates topside removal cost.
   *
   * @return cost in MUSD
   */
  public double getTopsideRemovalCostMUSD() {
    double baseCost = topsideWeightTonnes * TOPSIDE_REMOVAL_PER_TONNE;

    // Minimum cost for mobilization
    baseCost = Math.max(baseCost, 20.0);

    // Facility type adjustment
    baseCost *= facilityType.getCostFactor();

    // Water depth adjustment
    if (waterDepthM > 150) {
      baseCost *= 1.2;
    }

    return baseCost;
  }

  /**
   * Calculates substructure removal cost.
   *
   * @return cost in MUSD
   */
  public double getSubstructureRemovalCostMUSD() {
    if (facilityType == FacilityType.SUBSEA_TIEBACK) {
      // No platform structure
      return numberOfSubseaStructures * SUBSEA_STRUCTURE_REMOVAL;
    }

    if (facilityType == FacilityType.FPSO) {
      // FPSO can sail away - minimal cost
      return 15.0;
    }

    double baseCost;
    if (waterDepthM < 75) {
      baseCost = JACKET_REMOVAL_SHALLOW;
    } else {
      baseCost = JACKET_REMOVAL_DEEP;
    }

    // Weight adjustment
    baseCost *= (substructureWeightTonnes / 10000.0);

    // Deep water adjustment
    if (waterDepthM > 200) {
      baseCost *= 1.5;
    }

    // GBS is more expensive
    if (facilityType == FacilityType.GRAVITY_BASED) {
      baseCost *= 2.0;
    }

    return baseCost;
  }

  /**
   * Calculates pipeline decommissioning cost.
   *
   * @param strategy decommissioning strategy
   * @return cost in MUSD
   */
  public double getPipelineDecomCostMUSD(PipelineStrategy strategy) {
    double baseCostPerKm = strategy.getCostFactor();

    // Diameter adjustment
    if (pipelineDiameterInch > 24) {
      baseCostPerKm *= 1.5;
    } else if (pipelineDiameterInch > 12) {
      baseCostPerKm *= 1.2;
    }

    // Water depth adjustment
    if (waterDepthM > 200) {
      baseCostPerKm *= 1.3;
    }

    double pipelineCost = pipelineLengthKm * baseCostPerKm;

    // Add riser removal
    pipelineCost += numberOfRisers * RISER_REMOVAL_COST;

    return pipelineCost;
  }

  /**
   * Calculates pipeline decommissioning cost with leave-in-place strategy.
   *
   * @return cost in MUSD
   */
  public double getPipelineDecomCostMUSD() {
    return getPipelineDecomCostMUSD(PipelineStrategy.LEAVE_IN_PLACE);
  }

  /**
   * Calculates site remediation cost.
   *
   * @return cost in MUSD
   */
  public double getSiteRemediationCostMUSD() {
    double baseCost = SITE_REMEDIATION_BASE;

    // Adjust for facility complexity
    baseCost *= facilityType.getCostFactor();

    // Adjust for number of wells (drill cuttings piles)
    baseCost += numberOfWells * 0.5;

    return baseCost;
  }

  /**
   * Gets total decommissioning cost.
   *
   * @return total cost in MUSD
   */
  public double getTotalCostMUSD() {
    return getWellPACostMUSD() + getTopsideRemovalCostMUSD() + getSubstructureRemovalCostMUSD()
        + getPipelineDecomCostMUSD() + getSiteRemediationCostMUSD();
  }

  /**
   * Gets total decommissioning cost with specified pipeline strategy.
   *
   * @param pipelineStrategy pipeline decommissioning approach
   * @return total cost in MUSD
   */
  public double getTotalCostMUSD(PipelineStrategy pipelineStrategy) {
    return getWellPACostMUSD() + getTopsideRemovalCostMUSD() + getSubstructureRemovalCostMUSD()
        + getPipelineDecomCostMUSD(pipelineStrategy) + getSiteRemediationCostMUSD();
  }

  // ============================================================================
  // COST BREAKDOWN
  // ============================================================================

  /**
   * Gets cost breakdown.
   *
   * @return list of cost items
   */
  public List<CostItem> getCostBreakdown() {
    List<CostItem> items = new ArrayList<>();

    items.add(new CostItem("Well P&A", getWellPACostMUSD(),
        String.format("%d wells @ avg %.0fm depth", numberOfWells, averageWellDepthM)));

    items.add(new CostItem("Topside Removal", getTopsideRemovalCostMUSD(),
        String.format("%.0f tonnes", topsideWeightTonnes)));

    items.add(new CostItem("Substructure Removal", getSubstructureRemovalCostMUSD(),
        String.format("%s in %.0fm water", facilityType.getDisplayName(), waterDepthM)));

    items.add(new CostItem("Pipeline Decom", getPipelineDecomCostMUSD(),
        String.format("%.0f km, %.0f\" dia", pipelineLengthKm, pipelineDiameterInch)));

    items.add(new CostItem("Site Remediation", getSiteRemediationCostMUSD(), "Seabed clearing"));

    return items;
  }

  // ============================================================================
  // SCHEDULE ESTIMATION
  // ============================================================================

  /**
   * Estimates decommissioning duration.
   *
   * @return estimated duration in months
   */
  public int getEstimatedDurationMonths() {
    int months = 0;

    // Well P&A: typically 1-2 months per well (can parallel)
    months += Math.max(12, numberOfWells * 2);

    // Topside removal: 3-12 months
    if (topsideWeightTonnes > 20000) {
      months += 12;
    } else if (topsideWeightTonnes > 10000) {
      months += 9;
    } else {
      months += 6;
    }

    // Substructure: 6-24 months
    if (facilityType == FacilityType.GRAVITY_BASED) {
      months += 24;
    } else if (facilityType == FacilityType.FIXED_JACKET && waterDepthM > 100) {
      months += 12;
    } else if (facilityType != FacilityType.SUBSEA_TIEBACK) {
      months += 6;
    }

    return months;
  }

  // ============================================================================
  // REPORT GENERATION
  // ============================================================================

  /**
   * Generates a decommissioning cost report.
   *
   * @return markdown formatted report
   */
  public String generateReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("# Decommissioning Cost Estimate\n\n");

    // Facility summary
    sb.append("## Facility Overview\n\n");
    sb.append(String.format("- **Type:** %s\n", facilityType.getDisplayName()));
    sb.append(String.format("- **Water Depth:** %.0f m\n", waterDepthM));
    sb.append(String.format("- **Topside Weight:** %.0f tonnes\n", topsideWeightTonnes));
    sb.append(String.format("- **Wells:** %d\n", numberOfWells));
    sb.append(String.format("- **Pipelines:** %.0f km\n\n", pipelineLengthKm));

    // Cost breakdown
    sb.append("## Cost Breakdown\n\n");
    sb.append("| Category | Cost (MUSD) | Notes |\n");
    sb.append("|----------|-------------|-------|\n");

    double total = 0;
    for (CostItem item : getCostBreakdown()) {
      sb.append(String.format("| %s | %.1f | %s |\n", item.category, item.costMUSD, item.notes));
      total += item.costMUSD;
    }
    sb.append(String.format("| **TOTAL** | **%.1f** | |\n\n", total));

    // Schedule
    sb.append("## Schedule\n\n");
    sb.append(String.format("**Estimated Duration:** %d months\n\n", getEstimatedDurationMonths()));

    // Uncertainty
    sb.append("## Uncertainty Range\n\n");
    sb.append(String.format("- **Low (-30%%):** $%.0f M\n", total * 0.7));
    sb.append(String.format("- **Base:** $%.0f M\n", total));
    sb.append(String.format("- **High (+50%%):** $%.0f M\n", total * 1.5));

    return sb.toString();
  }

  // ============================================================================
  // INNER CLASSES
  // ============================================================================

  /**
   * Cost breakdown item.
   */
  public static class CostItem implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String category;
    private final double costMUSD;
    private final String notes;

    /**
     * Creates a cost item.
     *
     * @param category cost category
     * @param costMUSD cost in MUSD
     * @param notes additional notes
     */
    public CostItem(String category, double costMUSD, String notes) {
      this.category = category;
      this.costMUSD = costMUSD;
      this.notes = notes;
    }

    public String getCategory() {
      return category;
    }

    public double getCostMUSD() {
      return costMUSD;
    }

    public String getNotes() {
      return notes;
    }
  }
}
