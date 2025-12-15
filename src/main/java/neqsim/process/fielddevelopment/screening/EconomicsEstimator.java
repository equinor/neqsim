package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.facility.BlockConfig;
import neqsim.process.fielddevelopment.facility.BlockType;
import neqsim.process.fielddevelopment.facility.FacilityConfig;

/**
 * Economics estimator for concept-level CAPEX/OPEX screening in field development.
 *
 * <p>
 * This class provides rapid, order-of-magnitude cost estimates for early-phase field development
 * concept screening. The estimates are based on parametric cost models calibrated to industry
 * benchmarks and are suitable for concept comparison and ranking, but NOT for project sanction or
 * detailed cost engineering.
 * </p>
 *
 * <h2>Estimation Methodology</h2>
 * <p>
 * The estimator uses a bottom-up approach considering four main cost categories:
 * </p>
 * <ul>
 * <li><b>Facility CAPEX</b>: Base facility cost adjusted for water depth and type (platform, FPSO,
 * subsea, onshore)</li>
 * <li><b>Equipment CAPEX</b>: Process equipment costs based on block types and sizing (compression,
 * dehydration, CO2 removal, etc.)</li>
 * <li><b>Well CAPEX</b>: Drilling and completion costs scaled by well count and type (subsea vs
 * platform wells)</li>
 * <li><b>Infrastructure CAPEX</b>: Pipeline and umbilical costs based on tieback distance</li>
 * </ul>
 *
 * <h2>Accuracy and Limitations</h2>
 * <p>
 * All estimates carry a ±40% accuracy range, typical for AACE Class 5 (concept screening)
 * estimates. Key limitations include:
 * </p>
 * <ul>
 * <li>No consideration of market conditions or regional cost factors</li>
 * <li>Simplified scaling relationships (no detailed equipment sizing)</li>
 * <li>No contingency or risk factors included</li>
 * <li>Fixed cost basis (no inflation adjustment)</li>
 * </ul>
 *
 * <h2>Cost Basis</h2>
 * <p>
 * Cost factors are based on Norwegian Continental Shelf benchmarks circa 2020-2023:
 * </p>
 * <ul>
 * <li>Platform base cost: 500 MUSD</li>
 * <li>FPSO base cost: 800 MUSD</li>
 * <li>Subsea well: 80 MUSD per well</li>
 * <li>Platform well: 40 MUSD per well</li>
 * <li>Pipeline: 2 MUSD/km</li>
 * <li>OPEX: 4% of CAPEX per year (typical for mature operations)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * // Create estimator
 * EconomicsEstimator estimator = new EconomicsEstimator();
 *
 * // Define a concept
 * FieldConcept concept = FieldConcept.gasTieback("MyField");
 *
 * // Get quick estimate (no facility config)
 * EconomicsReport quickReport = estimator.quickEstimate(concept);
 * System.out.println("Quick CAPEX: " + quickReport.getTotalCapexMUSD() + " MUSD");
 *
 * // Get detailed estimate with facility configuration
 * FacilityConfig facility = FacilityBuilder.autoGenerate(concept).build();
 * EconomicsReport detailedReport = estimator.estimate(concept, facility);
 *
 * // Access breakdown
 * System.out.println(detailedReport.getSummary());
 * System.out.println("CAPEX range: " + detailedReport.getCapexLowMUSD() + " - "
 *     + detailedReport.getCapexHighMUSD() + " MUSD");
 * }</pre>
 *
 * <h2>Integration with Concept Evaluation</h2>
 * <p>
 * This estimator is typically used as part of the
 * {@link neqsim.process.fielddevelopment.evaluation.ConceptEvaluator} workflow, which combines
 * economics with flow assurance, safety, and emissions screening to produce comprehensive concept
 * KPIs.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see neqsim.process.fielddevelopment.evaluation.ConceptEvaluator
 * @see neqsim.process.fielddevelopment.concept.FieldConcept
 * @see neqsim.process.fielddevelopment.facility.FacilityConfig
 */
public class EconomicsEstimator {

  // ============================================================================
  // COST CONSTANTS - BASE FACILITY CAPEX (MUSD)
  // ============================================================================

  /**
   * Base capital cost for a fixed platform facility in million USD. Assumes a typical North Sea
   * platform with 4-slot capacity. Adjusted for water depth using depth factor in calculations.
   */
  private static final double PLATFORM_BASE_MUSD = 500.0;

  /**
   * Base capital cost for an FPSO (Floating Production Storage and Offloading) in million USD.
   * Higher than platform due to hull, mooring, and storage systems.
   */
  private static final double FPSO_BASE_MUSD = 800.0;

  /**
   * Capital cost per subsea template in million USD. Includes manifold, protection structure, and
   * connection systems.
   */
  private static final double SUBSEA_TEMPLATE_MUSD = 100.0;

  // ============================================================================
  // COST CONSTANTS - PROCESS EQUIPMENT CAPEX (MUSD)
  // ============================================================================

  /**
   * Capital cost per compression stage in million USD. Includes compressor, driver, cooler, and
   * associated piping. Typical for 10-20 MW gas compressor train.
   */
  private static final double COMPRESSION_PER_STAGE_MUSD = 50.0;

  /**
   * Capital cost for a TEG (triethylene glycol) dehydration unit in million USD. Sized for typical
   * offshore gas production rates (1-5 MSm3/d).
   */
  private static final double TEG_UNIT_MUSD = 30.0;

  /**
   * Capital cost for an amine-based CO2 removal unit in million USD. Higher cost due to
   * regeneration system and solvent handling. Suitable for CO2 content above 10%.
   */
  private static final double AMINE_UNIT_MUSD = 80.0;

  /**
   * Capital cost for a membrane CO2 removal unit in million USD. Lower cost than amine but limited
   * to moderate CO2 concentrations. Suitable for CO2 content 2-10%.
   */
  private static final double MEMBRANE_UNIT_MUSD = 40.0;

  /**
   * Capital cost for inlet separation train in million USD. Includes slug catcher, inlet separator,
   * and associated systems.
   */
  private static final double SEPARATION_TRAIN_MUSD = 40.0;

  // ============================================================================
  // COST CONSTANTS - WELL CAPEX (MUSD per well)
  // ============================================================================

  /**
   * Capital cost per subsea well in million USD. Higher than platform wells due to subsea tree,
   * controls, and intervention costs. Includes drilling, completion, and subsea equipment.
   */
  private static final double SUBSEA_WELL_MUSD = 80.0;

  /**
   * Capital cost per platform well in million USD. Lower than subsea due to simpler completion and
   * easier access. Includes drilling and completion only (platform already counted).
   */
  private static final double PLATFORM_WELL_MUSD = 40.0;

  // ============================================================================
  // COST CONSTANTS - INFRASTRUCTURE CAPEX
  // ============================================================================

  /**
   * Capital cost per kilometer of pipeline in million USD. Assumes typical 12-16" production
   * flowline with insulation. Actual cost varies significantly with diameter and water depth.
   */
  private static final double PIPELINE_MUSD_PER_KM = 2.0;

  /**
   * Capital cost per kilometer of umbilical in million USD. Includes hydraulic, electrical, and
   * fiber optic elements.
   */
  private static final double UMBILICAL_MUSD_PER_KM = 1.0;

  // ============================================================================
  // COST CONSTANTS - OPEX FACTORS
  // ============================================================================

  /**
   * Annual operating expenditure as percentage of total CAPEX. Industry typical range is 3-5% for
   * offshore oil and gas. Includes maintenance, insurance, and logistics.
   */
  private static final double OPEX_PERCENT_OF_CAPEX = 0.04;

  /**
   * Annual power cost per MW in million USD. Based on gas turbine fuel consumption and typical fuel
   * gas cost. For power from shore, this would be lower but offset by cable CAPEX.
   */
  private static final double POWER_COST_PER_MW_YEAR = 0.5;

  /**
   * Creates a new economics estimator instance.
   *
   * <p>
   * The estimator uses built-in cost factors calibrated to Norwegian Continental Shelf benchmarks.
   * No configuration is required.
   * </p>
   */
  public EconomicsEstimator() {
    // Default constructor - uses built-in cost factors
  }

  // ============================================================================
  // PUBLIC API - ESTIMATION METHODS
  // ============================================================================

  /**
   * Estimates comprehensive economics for a field concept with detailed facility configuration.
   *
   * <p>
   * This method provides the most accurate screening-level estimate by considering:
   * </p>
   * <ul>
   * <li>Specific process blocks in the facility configuration</li>
   * <li>Block-level sizing parameters (e.g., compression stages)</li>
   * <li>Well counts and types from the concept</li>
   * <li>Infrastructure distances and requirements</li>
   * </ul>
   *
   * <p>
   * The returned report includes CAPEX and OPEX breakdowns, unit costs ($/boe), and accuracy ranges
   * for uncertainty assessment.
   * </p>
   *
   * @param concept the field concept containing reservoir, wells, and infrastructure data
   * @param facilityConfig the facility configuration with specific process blocks, or null for
   *        simplified estimation
   * @return an economics report with CAPEX, OPEX, and derived metrics
   * @throws NullPointerException if concept is null
   */
  public EconomicsReport estimate(FieldConcept concept, FacilityConfig facilityConfig) {
    EconomicsReport.Builder builder = EconomicsReport.builder();

    // Facility CAPEX
    double facilityCAPEX = estimateFacilityCAPEX(concept, facilityConfig);
    builder.facilityCapexMUSD(facilityCAPEX);
    builder.addCapexItem("facility", facilityCAPEX);

    // Process equipment CAPEX
    double equipmentCAPEX = estimateEquipmentCAPEX(concept, facilityConfig);
    builder.equipmentCapexMUSD(equipmentCAPEX);
    builder.addCapexItem("equipment", equipmentCAPEX);

    // Well CAPEX
    double wellCAPEX = estimateWellCAPEX(concept);
    builder.wellCapexMUSD(wellCAPEX);
    builder.addCapexItem("wells", wellCAPEX);

    // Infrastructure CAPEX
    double infraCAPEX = estimateInfrastructureCAPEX(concept);
    builder.infrastructureCapexMUSD(infraCAPEX);
    builder.addCapexItem("infrastructure", infraCAPEX);

    // Total CAPEX
    double totalCAPEX = facilityCAPEX + equipmentCAPEX + wellCAPEX + infraCAPEX;
    builder.totalCapexMUSD(totalCAPEX);

    // OPEX estimation
    double baseOPEX = totalCAPEX * OPEX_PERCENT_OF_CAPEX;
    double powerOPEX = estimatePowerOPEX(concept, facilityConfig);
    double totalOPEX = baseOPEX + powerOPEX;
    builder.annualOpexMUSD(totalOPEX);
    builder.addOpexItem("maintenance", baseOPEX);
    builder.addOpexItem("power", powerOPEX);

    // Calculate metrics
    double annualProductionMboe = getAnnualProductionMboe(concept);
    if (annualProductionMboe > 0) {
      double capexPerBoe = totalCAPEX * 1000.0 / (annualProductionMboe * 1000.0);
      double opexPerBoe = totalOPEX * 1000.0 / (annualProductionMboe * 1000.0);
      builder.capexPerBoeUSD(capexPerBoe);
      builder.opexPerBoeUSD(opexPerBoe);
    }

    // Accuracy range
    builder.accuracyRangePercent(40.0);

    return builder.build();
  }

  /**
   * Provides a quick economics estimate without detailed facility configuration.
   *
   * <p>
   * This convenience method estimates costs based solely on the concept definition, using
   * simplified assumptions about required process equipment. It's useful for:
   * </p>
   * <ul>
   * <li>Initial concept screening before facility design</li>
   * <li>Rapid comparison of many alternatives</li>
   * <li>Early-phase feasibility assessment</li>
   * </ul>
   *
   * <p>
   * For more accurate estimates, use {@link #estimate(FieldConcept, FacilityConfig)} with a proper
   * facility configuration.
   * </p>
   *
   * @param concept the field concept to estimate
   * @return an economics report with CAPEX, OPEX, and derived metrics
   * @see #estimate(FieldConcept, FacilityConfig)
   */
  public EconomicsReport quickEstimate(FieldConcept concept) {
    return estimate(concept, null);
  }

  // ============================================================================
  // PRIVATE METHODS - CAPEX ESTIMATION
  // ============================================================================

  /**
   * Estimates facility base CAPEX based on facility type and water depth.
   *
   * <p>
   * Applies depth factor to account for increased costs in deeper water: - Every 500m of water
   * depth adds ~50% to base cost - Subsea facilities scale with number of templates needed -
   * Onshore facilities are typically 60% of platform cost
   * </p>
   *
   * @param concept the field concept with infrastructure data
   * @param facilityConfig the facility configuration (currently unused)
   * @return estimated facility CAPEX in million USD
   */
  private double estimateFacilityCAPEX(FieldConcept concept, FacilityConfig facilityConfig) {
    InfrastructureInput infra = concept.getInfrastructure();
    if (infra == null) {
      return PLATFORM_BASE_MUSD;
    }

    double waterDepth = infra.getWaterDepthM();
    double depthFactor = 1.0 + (waterDepth / 500.0) * 0.5; // 50% increase per 500m

    switch (infra.getProcessingLocation()) {
      case PLATFORM:
        return PLATFORM_BASE_MUSD * depthFactor;
      case FPSO:
        return FPSO_BASE_MUSD * depthFactor;
      case SUBSEA:
        return SUBSEA_TEMPLATE_MUSD * Math.max(1,
            concept.getWells() != null ? concept.getWells().getProducerCount() / 2 : 2);
      case ONSHORE:
        return PLATFORM_BASE_MUSD * 0.6; // Onshore typically cheaper
      default:
        return PLATFORM_BASE_MUSD;
    }
  }

  /**
   * Estimates process equipment CAPEX based on facility blocks or concept requirements.
   *
   * <p>
   * If a facility configuration is provided, costs are calculated for each specific block.
   * Otherwise, equipment is estimated from concept requirements (CO2 removal, dehydration, etc.).
   * </p>
   *
   * @param concept the field concept with processing requirements
   * @param facilityConfig optional facility configuration with specific blocks
   * @return estimated equipment CAPEX in million USD
   */
  private double estimateEquipmentCAPEX(FieldConcept concept, FacilityConfig facilityConfig) {
    double capex = 0.0;

    if (facilityConfig != null) {
      for (BlockConfig block : facilityConfig.getBlocks()) {
        capex += getBlockCAPEX(block);
      }
    } else {
      // Estimate from concept requirements
      capex += SEPARATION_TRAIN_MUSD; // Always have separation

      if (concept.needsCO2Removal()) {
        double co2 = concept.getReservoir() != null ? concept.getReservoir().getCo2Percent() : 5.0;
        if (co2 > 10) {
          capex += AMINE_UNIT_MUSD;
        } else {
          capex += MEMBRANE_UNIT_MUSD;
        }
      }

      if (concept.needsDehydration()) {
        capex += TEG_UNIT_MUSD;
      }

      // Assume 2 compression stages
      capex += 2 * COMPRESSION_PER_STAGE_MUSD;
    }

    return capex;
  }

  /**
   * Gets the CAPEX for a specific process block based on its type and parameters.
   *
   * <p>
   * Cost factors are based on typical offshore equipment costs and include:
   * </p>
   * <ul>
   * <li>Equipment procurement</li>
   * <li>Installation and hookup</li>
   * <li>Commissioning</li>
   * </ul>
   *
   * <p>
   * Costs do not include platform modifications or structural support.
   * </p>
   *
   * @param block the block configuration with type and parameters
   * @return estimated block CAPEX in million USD
   */
  private double getBlockCAPEX(BlockConfig block) {
    switch (block.getType()) {
      case COMPRESSION:
        int stages = block.getIntParameter("stages", 1);
        return stages * COMPRESSION_PER_STAGE_MUSD;
      case TEG_DEHYDRATION:
        return TEG_UNIT_MUSD;
      case CO2_REMOVAL_AMINE:
        return AMINE_UNIT_MUSD;
      case CO2_REMOVAL_MEMBRANE:
        return MEMBRANE_UNIT_MUSD;
      case H2S_REMOVAL:
        return 60.0;
      case NGL_RECOVERY:
        return 100.0;
      case INLET_SEPARATION:
      case TWO_PHASE_SEPARATOR:
      case THREE_PHASE_SEPARATOR:
        return 20.0;
      case OIL_STABILIZATION:
        return 30.0;
      case WATER_TREATMENT:
        return 25.0;
      case SUBSEA_BOOSTING:
        return 150.0;
      case FLARE_SYSTEM:
        return 20.0;
      case POWER_GENERATION:
        return 100.0;
      default:
        return 10.0;
    }
  }

  /**
   * Estimates well CAPEX based on well count and completion type.
   *
   * <p>
   * Subsea wells are approximately twice the cost of platform wells due to:
   * </p>
   * <ul>
   * <li>Subsea tree and controls</li>
   * <li>Intervention access requirements</li>
   * <li>Flowline connections</li>
   * </ul>
   *
   * @param concept the field concept with well configuration
   * @return estimated well CAPEX in million USD
   */
  private double estimateWellCAPEX(FieldConcept concept) {
    if (concept.getWells() == null) {
      return 200.0; // Default
    }

    int producerCount = concept.getWells().getProducerCount();
    int injectorCount = concept.getWells().getInjectorCount();
    int totalWells = producerCount + injectorCount;

    double costPerWell = concept.isSubseaTieback() ? SUBSEA_WELL_MUSD : PLATFORM_WELL_MUSD;

    return totalWells * costPerWell;
  }

  /**
   * Estimates infrastructure CAPEX based on tieback distance.
   *
   * <p>
   * Includes costs for:
   * </p>
   * <ul>
   * <li>Production flowlines/pipelines</li>
   * <li>Umbilicals (for subsea tiebacks only)</li>
   * </ul>
   *
   * <p>
   * Costs are simplified linear estimates; actual costs vary significantly with:
   * </p>
   * <ul>
   * <li>Pipeline diameter and wall thickness</li>
   * <li>Water depth (installation method)</li>
   * <li>Seabed conditions</li>
   * <li>Shore approach requirements</li>
   * </ul>
   *
   * @param concept the field concept with infrastructure configuration
   * @return estimated infrastructure CAPEX in million USD
   */
  private double estimateInfrastructureCAPEX(FieldConcept concept) {
    if (concept.getInfrastructure() == null) {
      return 50.0; // Default
    }

    double tiebackLength = concept.getInfrastructure().getTiebackLengthKm();

    double pipelineCost = tiebackLength * PIPELINE_MUSD_PER_KM;
    double umbilicalCost = concept.isSubseaTieback() ? tiebackLength * UMBILICAL_MUSD_PER_KM : 0;

    return pipelineCost + umbilicalCost;
  }

  // ============================================================================
  // PRIVATE METHODS - OPEX AND PRODUCTION ESTIMATION
  // ============================================================================

  /**
   * Estimates annual power-related operating costs.
   *
   * <p>
   * Power consumption is estimated based on process equipment, primarily compression. Base facility
   * load is assumed at 10 MW, with additional power per compression stage.
   * </p>
   *
   * @param concept the field concept
   * @param facilityConfig optional facility configuration for detailed power estimation
   * @return estimated annual power OPEX in million USD
   */
  private double estimatePowerOPEX(FieldConcept concept, FacilityConfig facilityConfig) {
    // Rough power estimate
    double powerMW = 10.0; // Base

    if (facilityConfig != null) {
      for (BlockConfig block : facilityConfig.getBlocksOfType(BlockType.COMPRESSION)) {
        int stages = block.getIntParameter("stages", 1);
        powerMW += stages * 5.0;
      }
    }

    return powerMW * POWER_COST_PER_MW_YEAR;
  }

  /**
   * Calculates annual production in million barrels of oil equivalent (Mboe).
   *
   * <p>
   * Conversion assumes 6000 Sm3 of gas per barrel of oil equivalent, which is a standard industry
   * conversion factor.
   * </p>
   *
   * @param concept the field concept with production rates
   * @return annual production in Mboe
   */
  private double getAnnualProductionMboe(FieldConcept concept) {
    if (concept.getWells() == null) {
      return 10.0; // Default 10 Mboe/year
    }

    double sm3d = concept.getWells().getRatePerWellSm3d() * concept.getWells().getProducerCount();
    double boepd = sm3d / 6000.0;
    return boepd * 365.0 / 1000.0; // Mboe
  }

  // ============================================================================
  // INNER CLASS - ECONOMICS REPORT
  // ============================================================================

  /**
   * Immutable report containing economics screening results.
   *
   * <p>
   * This class encapsulates all economic metrics from a screening estimate, including:
   * </p>
   * <ul>
   * <li><b>CAPEX breakdown</b>: Facility, equipment, wells, and infrastructure costs</li>
   * <li><b>OPEX estimate</b>: Annual operating expenditure</li>
   * <li><b>Unit costs</b>: CAPEX and OPEX per barrel of oil equivalent</li>
   * <li><b>Accuracy range</b>: Uncertainty bounds for the estimate</li>
   * </ul>
   *
   * <p>
   * <b>Accuracy Considerations</b>
   * </p>
   * <p>
   * All estimates carry a ±40% accuracy range (AACE Class 5). The actual costs can vary
   * significantly based on:
   * </p>
   * <ul>
   * <li>Market conditions and contractor availability</li>
   * <li>Detailed engineering and specifications</li>
   * <li>Regional cost factors</li>
   * <li>Project schedule and execution strategy</li>
   * </ul>
   *
   * <p>
   * <b>Thread Safety</b>
   * </p>
   * <p>
   * This class is immutable and thread-safe. All collections returned by getter methods are
   * defensive copies.
   * </p>
   *
   * <p>
   * <b>Serialization</b>
   * </p>
   * <p>
   * Implements {@link Serializable} for persistence and network transfer.
   * </p>
   *
   * @see EconomicsEstimator#estimate(FieldConcept, FacilityConfig)
   */
  public static final class EconomicsReport implements Serializable {
    /**
     * Serial version UID for serialization compatibility.
     */
    private static final long serialVersionUID = 1000L;

    /** Facility base CAPEX in million USD. */
    private final double facilityCapexMUSD;
    /** Process equipment CAPEX in million USD. */
    private final double equipmentCapexMUSD;
    /** Well drilling and completion CAPEX in million USD. */
    private final double wellCapexMUSD;
    /** Infrastructure (pipelines, umbilicals) CAPEX in million USD. */
    private final double infrastructureCapexMUSD;
    /** Total CAPEX in million USD. */
    private final double totalCapexMUSD;
    /** Annual OPEX in million USD per year. */
    private final double annualOpexMUSD;
    /** CAPEX per barrel of oil equivalent in USD. */
    private final double capexPerBoeUSD;
    /** OPEX per barrel of oil equivalent in USD. */
    private final double opexPerBoeUSD;
    /** Accuracy range as percentage (e.g., 40 means ±40%). */
    private final double accuracyRangePercent;
    /** Detailed CAPEX breakdown by category. */
    private final Map<String, Double> capexBreakdown;
    /** Detailed OPEX breakdown by category. */
    private final Map<String, Double> opexBreakdown;

    private EconomicsReport(Builder builder) {
      this.facilityCapexMUSD = builder.facilityCapexMUSD;
      this.equipmentCapexMUSD = builder.equipmentCapexMUSD;
      this.wellCapexMUSD = builder.wellCapexMUSD;
      this.infrastructureCapexMUSD = builder.infrastructureCapexMUSD;
      this.totalCapexMUSD = builder.totalCapexMUSD;
      this.annualOpexMUSD = builder.annualOpexMUSD;
      this.capexPerBoeUSD = builder.capexPerBoeUSD;
      this.opexPerBoeUSD = builder.opexPerBoeUSD;
      this.accuracyRangePercent = builder.accuracyRangePercent;
      this.capexBreakdown = new LinkedHashMap<>(builder.capexBreakdown);
      this.opexBreakdown = new LinkedHashMap<>(builder.opexBreakdown);
    }

    /**
     * Creates a new builder for constructing an EconomicsReport.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
      return new Builder();
    }

    // ========================================================================
    // GETTERS - CAPEX COMPONENTS
    // ========================================================================

    /**
     * Gets the facility base CAPEX in million USD.
     *
     * <p>
     * Includes platform, FPSO, or subsea template costs adjusted for water depth.
     * </p>
     *
     * @return facility CAPEX in MUSD
     */
    public double getFacilityCapexMUSD() {
      return facilityCapexMUSD;
    }

    /**
     * Gets the process equipment CAPEX in million USD.
     *
     * <p>
     * Includes all topside or subsea process equipment: separation, compression, dehydration, gas
     * treatment, etc.
     * </p>
     *
     * @return equipment CAPEX in MUSD
     */
    public double getEquipmentCapexMUSD() {
      return equipmentCapexMUSD;
    }

    /**
     * Gets the well CAPEX in million USD.
     *
     * <p>
     * Includes drilling and completion costs for all producer and injector wells.
     * </p>
     *
     * @return well CAPEX in MUSD
     */
    public double getWellCapexMUSD() {
      return wellCapexMUSD;
    }

    /**
     * Gets the infrastructure CAPEX in million USD.
     *
     * <p>
     * Includes pipelines, flowlines, umbilicals, and other tie-in infrastructure.
     * </p>
     *
     * @return infrastructure CAPEX in MUSD
     */
    public double getInfrastructureCapexMUSD() {
      return infrastructureCapexMUSD;
    }

    /**
     * Gets the total CAPEX in million USD.
     *
     * <p>
     * Sum of facility, equipment, wells, and infrastructure CAPEX. This is the headline number for
     * concept comparison.
     * </p>
     *
     * @return total CAPEX in MUSD
     */
    public double getTotalCapexMUSD() {
      return totalCapexMUSD;
    }

    // ========================================================================
    // GETTERS - OPEX AND UNIT COSTS
    // ========================================================================

    /**
     * Gets the annual OPEX in million USD per year.
     *
     * <p>
     * Includes maintenance, power, logistics, and other recurring costs. Based on percentage of
     * CAPEX plus power-specific costs.
     * </p>
     *
     * @return annual OPEX in MUSD/year
     */
    public double getAnnualOpexMUSD() {
      return annualOpexMUSD;
    }

    /**
     * Gets the CAPEX per barrel of oil equivalent in USD.
     *
     * <p>
     * Useful for comparing capital intensity across different concept sizes. Calculated as: Total
     * CAPEX / Annual Production (first year).
     * </p>
     *
     * @return CAPEX per boe in USD
     */
    public double getCapexPerBoeUSD() {
      return capexPerBoeUSD;
    }

    /**
     * Gets the OPEX per barrel of oil equivalent in USD.
     *
     * <p>
     * Useful for comparing operating efficiency across concepts. Calculated as: Annual OPEX /
     * Annual Production.
     * </p>
     *
     * @return OPEX per boe in USD
     */
    public double getOpexPerBoeUSD() {
      return opexPerBoeUSD;
    }

    /**
     * Gets the accuracy range as a percentage.
     *
     * <p>
     * For screening estimates, this is typically ±40% (AACE Class 5). Use
     * {@link #getCapexLowMUSD()} and {@link #getCapexHighMUSD()} for range bounds.
     * </p>
     *
     * @return accuracy range in percent (e.g., 40 means ±40%)
     */
    public double getAccuracyRangePercent() {
      return accuracyRangePercent;
    }

    // ========================================================================
    // GETTERS - BREAKDOWNS
    // ========================================================================

    /**
     * Gets the CAPEX breakdown by category.
     *
     * <p>
     * Returns a defensive copy of the internal map. Keys include: "facility", "equipment", "wells",
     * "infrastructure".
     * </p>
     *
     * @return map of category name to cost in MUSD
     */
    public Map<String, Double> getCapexBreakdown() {
      return new LinkedHashMap<>(capexBreakdown);
    }

    /**
     * Gets the OPEX breakdown by category.
     *
     * <p>
     * Returns a defensive copy of the internal map. Keys include: "maintenance", "power".
     * </p>
     *
     * @return map of category name to annual cost in MUSD
     */
    public Map<String, Double> getOpexBreakdown() {
      return new LinkedHashMap<>(opexBreakdown);
    }

    // ========================================================================
    // DERIVED METRICS
    // ========================================================================

    /**
     * Gets CAPEX range (low estimate).
     *
     * @return low CAPEX estimate
     */
    public double getCapexLowMUSD() {
      return totalCapexMUSD * (1.0 - accuracyRangePercent / 100.0);
    }

    /**
     * Gets CAPEX range (high estimate).
     *
     * @return high CAPEX estimate
     */
    public double getCapexHighMUSD() {
      return totalCapexMUSD * (1.0 + accuracyRangePercent / 100.0);
    }

    /**
     * Gets summary suitable for reporting.
     *
     * @return summary string
     */
    public String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("Economics Assessment (±").append(String.format("%.0f", accuracyRangePercent))
          .append("%):\n");
      sb.append("  CAPEX: ").append(String.format("%.0f", totalCapexMUSD)).append(" MUSD");
      sb.append(" (range: ").append(String.format("%.0f", getCapexLowMUSD()));
      sb.append(" - ").append(String.format("%.0f", getCapexHighMUSD())).append(")\n");
      sb.append("    - Facility: ").append(String.format("%.0f", facilityCapexMUSD))
          .append(" MUSD\n");
      sb.append("    - Equipment: ").append(String.format("%.0f", equipmentCapexMUSD))
          .append(" MUSD\n");
      sb.append("    - Wells: ").append(String.format("%.0f", wellCapexMUSD)).append(" MUSD\n");
      sb.append("    - Infrastructure: ").append(String.format("%.0f", infrastructureCapexMUSD))
          .append(" MUSD\n");
      sb.append("  OPEX: ").append(String.format("%.1f", annualOpexMUSD)).append(" MUSD/year\n");
      sb.append("  Unit costs: ").append(String.format("%.1f", capexPerBoeUSD)).append(" $/boe");
      sb.append(" (CAPEX), ").append(String.format("%.1f", opexPerBoeUSD))
          .append(" $/boe (OPEX)\n");
      return sb.toString();
    }

    @Override
    public String toString() {
      return String.format("EconomicsReport[CAPEX=%.0f MUSD, OPEX=%.1f MUSD/yr]", totalCapexMUSD,
          annualOpexMUSD);
    }

    /**
     * Builder for constructing {@link EconomicsReport} instances.
     *
     * <p>
     * Uses the builder pattern for flexible and readable construction of economics reports. All
     * monetary values are in million USD unless otherwise specified.
     * </p>
     *
     * <p>
     * <b>Usage Example</b>
     * </p>
     * 
     * <pre>
     * EconomicsReport report = EconomicsReport.builder().facilityCapexMUSD(400).equipmentCapexMUSD(150)
     *     .wellCapexMUSD(200).infrastructureCapexMUSD(50).totalCapexMUSD(800).annualOpexMUSD(80)
     *     .capexPerBoeUSD(53.0).opexPerBoeUSD(5.3).accuracyRangePercent(40.0)
     *     .addCapexItem("facility", 400.0).addCapexItem("equipment", 150.0)
     *     .addOpexItem("maintenance", 60.0).addOpexItem("power", 20.0).build();
     * </pre>
     */
    public static final class Builder {
      private double facilityCapexMUSD;
      private double equipmentCapexMUSD;
      private double wellCapexMUSD;
      private double infrastructureCapexMUSD;
      private double totalCapexMUSD;
      private double annualOpexMUSD;
      private double capexPerBoeUSD;
      private double opexPerBoeUSD;
      private double accuracyRangePercent = 40.0;
      private final Map<String, Double> capexBreakdown = new LinkedHashMap<>();
      private final Map<String, Double> opexBreakdown = new LinkedHashMap<>();

      /**
       * Sets the facility base CAPEX.
       *
       * @param value facility CAPEX in million USD
       * @return this builder for chaining
       */
      public Builder facilityCapexMUSD(double value) {
        this.facilityCapexMUSD = value;
        return this;
      }

      /**
       * Sets the process equipment CAPEX.
       *
       * @param value equipment CAPEX in million USD
       * @return this builder for chaining
       */
      public Builder equipmentCapexMUSD(double value) {
        this.equipmentCapexMUSD = value;
        return this;
      }

      /**
       * Sets the well drilling and completion CAPEX.
       *
       * @param value well CAPEX in million USD
       * @return this builder for chaining
       */
      public Builder wellCapexMUSD(double value) {
        this.wellCapexMUSD = value;
        return this;
      }

      /**
       * Sets the infrastructure CAPEX (pipelines, umbilicals).
       *
       * @param value infrastructure CAPEX in million USD
       * @return this builder for chaining
       */
      public Builder infrastructureCapexMUSD(double value) {
        this.infrastructureCapexMUSD = value;
        return this;
      }

      /**
       * Sets the total CAPEX.
       *
       * @param value total CAPEX in million USD
       * @return this builder for chaining
       */
      public Builder totalCapexMUSD(double value) {
        this.totalCapexMUSD = value;
        return this;
      }

      /**
       * Sets the annual OPEX.
       *
       * @param value annual OPEX in million USD per year
       * @return this builder for chaining
       */
      public Builder annualOpexMUSD(double value) {
        this.annualOpexMUSD = value;
        return this;
      }

      /**
       * Sets the CAPEX per barrel of oil equivalent.
       *
       * @param value CAPEX per boe in USD
       * @return this builder for chaining
       */
      public Builder capexPerBoeUSD(double value) {
        this.capexPerBoeUSD = value;
        return this;
      }

      /**
       * Sets the OPEX per barrel of oil equivalent.
       *
       * @param value OPEX per boe in USD
       * @return this builder for chaining
       */
      public Builder opexPerBoeUSD(double value) {
        this.opexPerBoeUSD = value;
        return this;
      }

      /**
       * Sets the accuracy range percentage.
       *
       * @param value accuracy range (e.g., 40 for ±40%)
       * @return this builder for chaining
       */
      public Builder accuracyRangePercent(double value) {
        this.accuracyRangePercent = value;
        return this;
      }

      /**
       * Adds a CAPEX breakdown item.
       *
       * @param item the cost category name (e.g., "facility", "equipment")
       * @param value the cost in million USD
       * @return this builder for chaining
       */
      public Builder addCapexItem(String item, double value) {
        this.capexBreakdown.put(item, value);
        return this;
      }

      /**
       * Adds an OPEX breakdown item.
       *
       * @param item the cost category name (e.g., "maintenance", "power")
       * @param value the annual cost in million USD
       * @return this builder for chaining
       */
      public Builder addOpexItem(String item, double value) {
        this.opexBreakdown.put(item, value);
        return this;
      }

      /**
       * Builds the EconomicsReport with the configured values.
       *
       * @return a new immutable EconomicsReport instance
       */
      public EconomicsReport build() {
        return new EconomicsReport(this);
      }
    }
  }
}
