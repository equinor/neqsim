package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.costestimation.CostEstimateBasis;
import neqsim.process.costestimation.CostEstimateResult;
import neqsim.process.costestimation.CostEstimationCalculator;
import neqsim.process.costestimation.EstimateClass;
import neqsim.process.costestimation.ProcessCostEstimate;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.costestimation.topsides.TopsidesFacilityCostEstimator;
import neqsim.process.costestimation.topsides.TopsidesFacilityCostEstimator.FacilityType;
import neqsim.process.costestimation.topsides.TopsidesFacilityCostEstimator.ProjectContext;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.subsea.WellCostEstimator;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Integrates process mechanical design and cost estimation into field development workflows.
 *
 * <p>
 * This class provides a bridge between process equipment design and field development economics, enabling:
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
 * <caption>Cost Estimation Fidelity Levels</caption>
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
 * double totalCapexMUSD = report.getTotalCapex() / 1e6;
 * double facilitiesWeightTonnes = report.getTotalWeight() / 1000.0;
 * double installationManHours = report.getTotalManHours();
 *
 * // Use for NPV calculation
 * scheduler.setCapex(report.getTotalCapex() / 1e6, 2025);
 * ProductionSchedule schedule = scheduler.generateSchedule(startDate, 20.0, 30.0);
 * double npvMUSD = schedule.getNPV() / 1e6;
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

  /** Number of subsea wells for cost estimation. */
  private int numberOfWells = 0;

  /** Number of production wells. */
  private int numberOfProducers = 0;

  /** Number of injection wells. */
  private int numberOfInjectors = 0;

  /** Average well measured depth in meters. */
  private double averageWellDepth = 3500.0;

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
   * Set well parameters for subsea cost estimation.
   *
   * @param producers number of production wells
   * @param injectors number of injection wells
   * @param avgDepthM average measured depth in meters
   */
  public void setWellParameters(int producers, int injectors, double avgDepthM) {
    this.numberOfProducers = producers;
    this.numberOfInjectors = injectors;
    this.numberOfWells = producers + injectors;
    this.averageWellDepth = avgDepthM;
    this.includeSubseaCosts = true;
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

    CostEstimateResult topsidesDetailedEstimate = estimateTopsidesFacility(processCost);

    // Build cost report
    FieldDevelopmentCostReport report = new FieldDevelopmentCostReport();
    report.setConceptName(facility.getName());
    report.setFidelityLevel(fidelityLevel);
    report.setConceptType(conceptType);
    report.setTopsidesDetailedEstimateResult(topsidesDetailedEstimate);

    // Use detailed topsides result when available, with the old module-factor scalar as fallback.
    double legacyFacilityCostUSD = processCost.getTotalModuleCost() * conceptType.getCostFactor();
    double facilityCostUSD = resolveTopsidesCapex(topsidesDetailedEstimate, legacyFacilityCostUSD);
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

    // Add DRILEX and SURF costs if applicable. The legacy subsea bucket remains the sum.
    if (includeSubseaCosts) {
      double surfCost = estimateSurfCosts();
      double drilexCost = estimateDrilexCosts();
      report.setSurfCapex(surfCost);
      report.setDrilexCapex(drilexCost);
      report.setSubseaCapex(surfCost + drilexCost);
    }

    // Calculate totals
    report.calculateTotals();

    // Add accuracy range based on fidelity
    report.setAccuracyBand(fidelityLevel.getAccuracyBand());

    return report;
  }

  /**
   * Estimates detailed topsides facility cost from a process cost estimate.
   *
   * @param processCost process cost estimate backing the topsides scope
   * @return detailed topsides estimate result
   */
  private CostEstimateResult estimateTopsidesFacility(ProcessCostEstimate processCost) {
    TopsidesFacilityCostEstimator estimator = new TopsidesFacilityCostEstimator(processCost)
        .setFacilityType(mapConceptToFacilityType(conceptType))
        .setProjectContext(mapConceptToProjectContext(conceptType)).setEstimateBasis(createTopsidesEstimateBasis());
    return estimator.estimate();
  }

  /**
   * Creates the topsides estimate basis from field-development settings.
   *
   * @return topsides estimate basis
   */
  private CostEstimateBasis createTopsidesEstimateBasis() {
    return new CostEstimateBasis().setEstimateClass(mapFidelityToEstimateClass(fidelityLevel))
        .setCurrencyCode(currencyCode).setCostYear(referenceYear).setLocationFactor(locationFactor)
        .setLocationBasis("field-development-location").setDataSource("field-development-topsides-factored-mto")
        .setNotes(
            "Topsides CAPEX from NeqSim equipment costs with module, bulk, installation, hook-up, and project allowances.");
  }

  /**
   * Maps field-development fidelity to an AACE-style estimate class.
   *
   * @param level fidelity level
   * @return estimate class
   */
  private EstimateClass mapFidelityToEstimateClass(FidelityLevel level) {
    if (level == FidelityLevel.FEED) {
      return EstimateClass.CLASS_3;
    }
    if (level == FidelityLevel.PRE_FEED) {
      return EstimateClass.CLASS_4;
    }
    if (level == FidelityLevel.CONCEPTUAL) {
      return EstimateClass.CLASS_4;
    }
    return EstimateClass.CLASS_5;
  }

  /**
   * Maps development concept type to topsides facility type.
   *
   * @param type development concept type
   * @return topsides facility type
   */
  private FacilityType mapConceptToFacilityType(ConceptType type) {
    if (type == ConceptType.FPSO) {
      return FacilityType.FPSO;
    }
    if (type == ConceptType.SEMI_SUBMERSIBLE || type == ConceptType.TLP) {
      return FacilityType.SEMI_SUBMERSIBLE;
    }
    if (type == ConceptType.ONSHORE) {
      return FacilityType.ONSHORE;
    }
    if (type == ConceptType.SUBSEA_TIEBACK) {
      return FacilityType.BROWNFIELD_TIE_IN;
    }
    return FacilityType.FIXED_PLATFORM;
  }

  /**
   * Maps development concept type to topsides project context.
   *
   * @param type development concept type
   * @return topsides project context
   */
  private ProjectContext mapConceptToProjectContext(ConceptType type) {
    if (type == ConceptType.SUBSEA_TIEBACK) {
      return ProjectContext.HOST_TIE_IN;
    }
    return ProjectContext.GREENFIELD;
  }

  /**
   * Resolves facility CAPEX from the detailed topsides estimate.
   *
   * @param topsidesDetailedEstimate detailed topsides estimate
   * @param fallback fallback facility CAPEX in USD
   * @return resolved facility CAPEX in USD
   */
  private double resolveTopsidesCapex(CostEstimateResult topsidesDetailedEstimate, double fallback) {
    if (topsidesDetailedEstimate == null) {
      return fallback;
    }
    Double totalTopsidesCapex = topsidesDetailedEstimate.getProjectCostSummary().get("totalTopsidesCapex");
    if (totalTopsidesCapex != null && totalTopsidesCapex > 0.0) {
      return totalTopsidesCapex;
    }
    return fallback;
  }

  /**
   * Estimate subsea infrastructure costs.
   *
   * @return subsea CAPEX in USD
   */
  private double estimateSubseaCosts() {
    return estimateSurfCosts() + estimateDrilexCosts();
  }

  /**
   * Estimate SURF infrastructure costs.
   *
   * @return SURF CAPEX in USD
   */
  private double estimateSurfCosts() {
    double totalSurfCost = 0.0;

    // Flowline cost per km (increases with water depth)
    double depthFactor = 1.0 + (waterDepth - 100.0) / 500.0;
    double flowlineCostPerKm = 2.5e6 * depthFactor; // Base $2.5M/km
    totalSurfCost += subseaTiebackLength * flowlineCostPerKm;

    // Umbilical cost
    double umbilicalCostPerKm = 1.5e6 * depthFactor;
    totalSurfCost += subseaTiebackLength * umbilicalCostPerKm;

    // Subsea manifold (if tieback > 10km)
    if (subseaTiebackLength > 10.0) {
      totalSurfCost += 30.0e6 * depthFactor; // Manifold
    }

    // Riser system
    double riserCost = waterDepth * 50000.0; // ~$50k per meter depth
    totalSurfCost += riserCost;

    // Apply location factor
    totalSurfCost *= locationFactor;

    return totalSurfCost;
  }

  /**
   * Estimate drilling and completion expenditure (DRILEX) costs.
   *
   * @return DRILEX CAPEX in USD
   */
  private double estimateDrilexCosts() {
    if (numberOfWells > 0 || numberOfProducers > 0 || numberOfInjectors > 0) {
      return estimateWellCosts() * locationFactor;
    }
    return 0.0;
  }

  /**
   * Estimate drilling and completion costs for all wells.
   *
   * <p>
   * Uses {@link WellCostEstimator} for detailed well cost estimation with regional cost factors and well-type-specific
   * parameters.
   * </p>
   *
   * @return total well CAPEX in USD
   */
  private double estimateWellCosts() {
    double totalWellCosts = 0.0;

    int producers = numberOfProducers > 0 ? numberOfProducers : numberOfWells;
    int injectors = numberOfInjectors;

    // Estimate producer costs
    if (producers > 0) {
      WellCostEstimator prodEstimator = new WellCostEstimator();
      prodEstimator.calculateWellCost("OIL_PRODUCER", "SEMI_SUBMERSIBLE", "CASED_PERFORATED", averageWellDepth,
          waterDepth, 45.0, 25.0, 0.0, true, 4);
      totalWellCosts += prodEstimator.getTotalCost() * producers;
    }

    // Estimate injector costs
    if (injectors > 0) {
      WellCostEstimator injEstimator = new WellCostEstimator();
      injEstimator.calculateWellCost("WATER_INJECTOR", "SEMI_SUBMERSIBLE", "CASED_PERFORATED", averageWellDepth,
          waterDepth, 35.0, 15.0, 0.0, true, 4);
      totalWellCosts += injEstimator.getTotalCost() * injectors;
    }

    return totalWellCosts;
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
      estimator.currencyCode = this.currencyCode;
      estimator.referenceYear = this.referenceYear;

      if (this.includeSubseaCosts) {
        estimator.includeSubseaCosts = true;
        estimator.subseaTiebackLength = this.subseaTiebackLength;
        estimator.waterDepth = this.waterDepth;
        estimator.numberOfWells = this.numberOfWells;
        estimator.numberOfProducers = this.numberOfProducers;
        estimator.numberOfInjectors = this.numberOfInjectors;
        estimator.averageWellDepth = this.averageWellDepth;
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
    private double drilexCapex;
    private double surfCapex;
    private double subseaCapex;
    private double totalCapex;
    private double totalWeight;
    private double totalManHours;
    private double footprintArea;
    private double accuracyBand;
    private CostEstimateResult topsidesDetailedEstimateResult;
    private final List<EquipmentCostItem> equipmentItems = new ArrayList<EquipmentCostItem>();
    private final Map<String, Double> costByCategory = new LinkedHashMap<String, Double>();
    private final Map<String, Double> equipmentCostByCategory = new LinkedHashMap<String, Double>();

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
     * Set drilling and completion expenditure (DRILEX) CAPEX.
     *
     * @param capex DRILEX CAPEX in USD
     */
    public void setDrilexCapex(double capex) {
      this.drilexCapex = capex;
    }

    /**
     * Get drilling and completion expenditure (DRILEX) CAPEX.
     *
     * @return DRILEX CAPEX in USD
     */
    public double getDrilexCapex() {
      return drilexCapex;
    }

    /**
     * Set SURF CAPEX.
     *
     * @param capex SURF CAPEX in USD
     */
    public void setSurfCapex(double capex) {
      this.surfCapex = capex;
    }

    /**
     * Get SURF CAPEX.
     *
     * @return SURF CAPEX in USD
     */
    public double getSurfCapex() {
      return surfCapex;
    }

    /**
     * Set combined subsea CAPEX.
     *
     * @param capex combined SURF plus DRILEX CAPEX in USD
     */
    public void setSubseaCapex(double capex) {
      this.subseaCapex = capex;
    }

    /**
     * Get combined subsea CAPEX.
     *
     * @return combined SURF plus DRILEX CAPEX in USD
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
     * Sets the detailed topsides estimate result.
     *
     * @param topsidesDetailedEstimateResult detailed topsides estimate result
     */
    public void setTopsidesDetailedEstimateResult(CostEstimateResult topsidesDetailedEstimateResult) {
      this.topsidesDetailedEstimateResult = topsidesDetailedEstimateResult;
    }

    /**
     * Gets the detailed topsides estimate result.
     *
     * @return detailed topsides estimate result, or {@code null} when unavailable
     */
    public CostEstimateResult getTopsidesDetailedEstimateResult() {
      return topsidesDetailedEstimateResult;
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
      equipmentCostByCategory.clear();

      for (EquipmentCostItem item : equipmentItems) {
        totalWeight += item.getWeight();
        totalManHours += item.getManHours();

        String category = item.getType();
        equipmentCostByCategory.merge(category, item.getInstalledCost(), Double::sum);
      }

      if (!populateTopsidesCostByCategory()) {
        costByCategory.putAll(equipmentCostByCategory);
      }
      applyTopsidesPhysicalBasis();

      if (drilexCapex > 0.0) {
        costByCategory.put("DRILEX", drilexCapex);
      }
      if (surfCapex > 0.0) {
        costByCategory.put("SURF", surfCapex);
      }
      if (subseaCapex <= 0.0 && (drilexCapex > 0.0 || surfCapex > 0.0)) {
        subseaCapex = drilexCapex + surfCapex;
      }

      totalCapex = facilitiesCapex + subseaCapex;
    }

    /**
     * Populates the report category map from the detailed topsides CAPEX stack.
     *
     * @return true when detailed topsides categories were populated
     */
    private boolean populateTopsidesCostByCategory() {
      if (topsidesDetailedEstimateResult == null) {
        return false;
      }

      for (Map.Entry<String, Double> entry : topsidesDetailedEstimateResult.getCapitalCosts().entrySet()) {
        costByCategory.put("topsides." + entry.getKey(), entry.getValue());
      }
      for (Map.Entry<String, Double> entry : topsidesDetailedEstimateResult.getProjectCosts().entrySet()) {
        costByCategory.put("topsides." + entry.getKey(), entry.getValue());
      }
      return !costByCategory.isEmpty();
    }

    /**
     * Applies topsides dry-weight basis when a detailed topsides result is available.
     */
    private void applyTopsidesPhysicalBasis() {
      if (topsidesDetailedEstimateResult == null) {
        return;
      }
      Double topsidesWeight = topsidesDetailedEstimateResult.getWeightBasis().get("totalEstimatedDryWeight");
      if (topsidesWeight != null && !Double.isNaN(topsidesWeight.doubleValue())
          && !Double.isInfinite(topsidesWeight.doubleValue()) && topsidesWeight.doubleValue() > 0.0) {
        totalWeight = topsidesWeight.doubleValue();
      }
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
     * Get equipment-only installed cost breakdown by equipment category.
     *
     * @return map of equipment category to equipment-only installed cost
     */
    public Map<String, Double> getEquipmentCostByCategory() {
      return new LinkedHashMap<String, Double>(equipmentCostByCategory);
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
      capex.put("drilex_USD", drilexCapex);
      capex.put("surf_USD", surfCapex);
      capex.put("facilities_USD", facilitiesCapex);
      capex.put("subsea_USD", subseaCapex);
      capex.put("total_USD", totalCapex);
      capex.put("lowEstimate_USD", getLowEstimate());
      capex.put("highEstimate_USD", getHighEstimate());
      data.put("capex", capex);

      if (topsidesDetailedEstimateResult != null) {
        data.put("topsidesDetailedEstimateResult", topsidesDetailedEstimateResult.toMap());
      }

      Map<String, Object> physical = new LinkedHashMap<String, Object>();
      physical.put("totalWeight_kg", totalWeight);
      physical.put("totalWeight_tonnes", totalWeight / 1000.0);
      physical.put("installationManHours", totalManHours);
      data.put("physicalProperties", physical);

      data.put("costByCategory", costByCategory);
      data.put("equipmentCostByCategory", equipmentCostByCategory);

      List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
      for (EquipmentCostItem item : equipmentItems) {
        items.add(item.toMap());
      }
      data.put("equipmentBreakdown", items);

      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(data);
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
      sb.append("**Fidelity:** ").append(fidelityLevel != null ? fidelityLevel.getDisplayName() : "N/A");
      sb.append(" (±").append(String.format("%.0f", accuracyBand * 100)).append("%)\n");
      sb.append("**Concept Type:** ").append(conceptType != null ? conceptType.name() : "N/A");
      sb.append("\n\n");

      sb.append("## CAPEX Summary\n\n");
      sb.append("| Category | Cost (USD) | Cost (MUSD) |\n");
      sb.append("|----------|------------|-------------|\n");
      if (drilexCapex > 0) {
        sb.append(String.format("| DRILEX | $%,.0f | $%.1f M |\n", drilexCapex, drilexCapex / 1e6));
      }
      if (surfCapex > 0) {
        sb.append(String.format("| SURF | $%,.0f | $%.1f M |\n", surfCapex, surfCapex / 1e6));
      }
      sb.append(String.format("| Facilities | $%,.0f | $%.1f M |\n", facilitiesCapex, facilitiesCapex / 1e6));
      if (subseaCapex > 0 && drilexCapex <= 0.0 && surfCapex <= 0.0) {
        sb.append(String.format("| Subsea | $%,.0f | $%.1f M |\n", subseaCapex, subseaCapex / 1e6));
      }
      sb.append(String.format("| **Total** | **$%,.0f** | **$%.1f M** |\n", totalCapex, totalCapex / 1e6));
      sb.append(String.format("| Low Estimate | $%,.0f | $%.1f M |\n", getLowEstimate(), getLowEstimate() / 1e6));
      sb.append(String.format("| High Estimate | $%,.0f | $%.1f M |\n", getHighEstimate(), getHighEstimate() / 1e6));
      sb.append("\n");

      if (topsidesDetailedEstimateResult != null
          && topsidesDetailedEstimateResult.getProjectCostSummary().containsKey("totalTopsidesCapex")) {
        sb.append("## Topsides Detail\n\n");
        sb.append(String.format("- **Total topsides CAPEX:** $%,.0f\n",
            topsidesDetailedEstimateResult.getProjectCostSummary().get("totalTopsidesCapex")));
        sb.append(String.format("- **Direct field cost:** $%,.0f\n",
            topsidesDetailedEstimateResult.getCapitalCostSummary().get("directFieldCost")));
        sb.append("\n");
      }

      if (!costByCategory.isEmpty()) {
        sb.append("## Cost Breakdown\n\n");
        sb.append("| Category | Cost (USD) |\n");
        sb.append("|----------|------------|\n");
        for (Map.Entry<String, Double> entry : costByCategory.entrySet()) {
          sb.append(String.format("| %s | $%,.0f |\n", entry.getKey(), entry.getValue()));
        }
        sb.append("\n");
      }

      sb.append("## Physical Properties\n\n");
      sb.append(String.format("- **Total Weight:** %.1f tonnes\n", totalWeight / 1000.0));
      sb.append(String.format("- **Installation Man-Hours:** %.0f\n", totalManHours));
      sb.append("\n");

      if (!equipmentItems.isEmpty()) {
        sb.append("## Equipment-Only Breakdown\n\n");
        sb.append("| Equipment | Type | Weight (kg) | Cost (USD) |\n");
        sb.append("|-----------|------|-------------|------------|\n");
        for (EquipmentCostItem item : equipmentItems) {
          sb.append(String.format("| %s | %s | %.0f | $%,.0f |\n", item.getName(), item.getType(), item.getWeight(),
              item.getInstalledCost()));
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
