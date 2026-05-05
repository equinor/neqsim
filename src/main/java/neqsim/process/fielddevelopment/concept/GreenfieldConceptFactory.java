package neqsim.process.fielddevelopment.concept;

import java.util.Map;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator.DeclineType;
import neqsim.process.fielddevelopment.facility.FacilityBuilder;
import neqsim.process.fielddevelopment.facility.FacilityConfig;
import neqsim.process.fielddevelopment.screening.EconomicsEstimator;
import neqsim.process.fielddevelopment.screening.EconomicsEstimator.EconomicsReport;
import neqsim.process.fielddevelopment.screening.EmissionsTracker;
import neqsim.process.fielddevelopment.screening.EmissionsTracker.EmissionsReport;

/**
 * Factory for standardized field-development concept templates.
 *
 * <p>
 * The templates give comparable starting assumptions for subsea tiebacks, standalone greenfield
 * facilities, subsea-to-shore developments, onshore terminals, and phased brownfield expansion.
 * They are intended for screening and teaching workflows rather than project sanction estimates.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class GreenfieldConceptFactory {

  /** Default first production year for template economics. */
  private static final int DEFAULT_FIRST_PRODUCTION_YEAR = 2028;

  /** Default discount rate used for template economics. */
  private static final double DEFAULT_DISCOUNT_RATE = 0.08;

  private GreenfieldConceptFactory() {}

  /**
   * Creates a subsea tieback template to an existing host.
   *
   * @param name case name
   * @return standardized subsea tieback case
   */
  public static DevelopmentCaseTemplate subseaTieback(String name) {
    FieldConcept concept = FieldConcept.builder(name)
        .description("Subsea satellite tied back to an existing host facility")
        .reservoir(
            ReservoirInput.richGas().resourceEstimate(12.0, "GSm3").recoveryFactor(0.70).build())
        .wells(WellsInput.builder().producerCount(2).ratePerWell(1.2e6, "Sm3/d")
            .tubeheadPressure(120.0).build())
        .infrastructure(InfrastructureInput.subseaTieback().tiebackLength(30.0).waterDepth(350.0)
            .ambientTemperatures(6.0, 8.0).insulatedFlowline(true).build())
        .build();
    return buildTemplate(concept, "Subsea tieback", DEFAULT_FIRST_PRODUCTION_YEAR, 36);
  }

  /**
   * Creates a standalone FPSO template.
   *
   * @param name case name
   * @return standardized standalone FPSO case
   */
  public static DevelopmentCaseTemplate standaloneFpso(String name) {
    FieldConcept concept = FieldConcept.builder(name)
        .description("Standalone FPSO with subsea wells and stabilized oil export")
        .reservoir(ReservoirInput.blackOil().resourceEstimate(120.0, "MMbbl").recoveryFactor(0.45)
            .waterCut(0.20).build())
        .wells(WellsInput.builder().producerCount(6).ratePerWell(12000.0, "bbl/d")
            .tubeheadPressure(90.0).build())
        .infrastructure(InfrastructureInput.builder()
            .processingLocation(InfrastructureInput.ProcessingLocation.FPSO)
            .powerSupply(InfrastructureInput.PowerSupply.GAS_TURBINE)
            .exportType(InfrastructureInput.ExportType.STABILIZED_OIL).waterDepth(700.0)
            .tiebackLength(5.0).exportPipeline(5.0, 16.0).build())
        .build();
    return buildTemplate(concept, "Standalone FPSO", DEFAULT_FIRST_PRODUCTION_YEAR + 1, 48);
  }

  /**
   * Creates a fixed-platform greenfield template.
   *
   * @param name case name
   * @return standardized fixed-platform case
   */
  public static DevelopmentCaseTemplate fixedPlatform(String name) {
    FieldConcept concept = FieldConcept.builder(name)
        .description("Fixed platform with platform wells and gas export")
        .reservoir(ReservoirInput.blackOil().resourceEstimate(150.0, "MMbbl").recoveryFactor(0.50)
            .waterCut(0.15).build())
        .wells(
            WellsInput.builder().producerCount(8).completionType(WellsInput.CompletionType.PLATFORM)
                .ratePerWell(9000.0, "bbl/d").tubeheadPressure(100.0).build())
        .infrastructure(InfrastructureInput.builder()
            .processingLocation(InfrastructureInput.ProcessingLocation.PLATFORM)
            .powerSupply(InfrastructureInput.PowerSupply.GAS_TURBINE)
            .exportType(InfrastructureInput.ExportType.STABILIZED_OIL).waterDepth(120.0)
            .tiebackLength(0.0).exportPipeline(80.0, 20.0).build())
        .build();
    return buildTemplate(concept, "Fixed platform", DEFAULT_FIRST_PRODUCTION_YEAR + 2, 60);
  }

  /**
   * Creates a subsea-to-shore template.
   *
   * @param name case name
   * @return standardized subsea-to-shore case
   */
  public static DevelopmentCaseTemplate subseaToShore(String name) {
    FieldConcept concept = FieldConcept.builder(name)
        .description("Long subsea multiphase tieback to an onshore processing terminal")
        .reservoir(ReservoirInput.richGas().resourceEstimate(35.0, "GSm3").recoveryFactor(0.72)
            .co2Percent(2.0).build())
        .wells(WellsInput.builder().producerCount(4).ratePerWell(1.5e6, "Sm3/d")
            .tubeheadPressure(150.0).build())
        .infrastructure(InfrastructureInput.builder()
            .processingLocation(InfrastructureInput.ProcessingLocation.ONSHORE)
            .powerSupply(InfrastructureInput.PowerSupply.POWER_FROM_SHORE)
            .exportType(InfrastructureInput.ExportType.WET_GAS).waterDepth(300.0)
            .tiebackLength(120.0).exportPipeline(120.0, 18.0).insulatedFlowline(true)
            .electricHeating(true).build())
        .build();
    return buildTemplate(concept, "Subsea-to-shore", DEFAULT_FIRST_PRODUCTION_YEAR + 2, 54);
  }

  /**
   * Creates an onshore terminal development template.
   *
   * @param name case name
   * @return standardized onshore terminal case
   */
  public static DevelopmentCaseTemplate onshoreTerminal(String name) {
    FieldConcept concept = FieldConcept.builder(name)
        .description("Onshore gas processing terminal with dry gas export")
        .reservoir(ReservoirInput.leanGas().resourceEstimate(45.0, "GSm3").recoveryFactor(0.80)
            .co2Percent(0.8).build())
        .wells(
            WellsInput.builder().producerCount(5).completionType(WellsInput.CompletionType.ONSHORE)
                .ratePerWell(1.8e6, "Sm3/d").tubeheadPressure(110.0).build())
        .infrastructure(InfrastructureInput.builder()
            .processingLocation(InfrastructureInput.ProcessingLocation.ONSHORE)
            .powerSupply(InfrastructureInput.PowerSupply.POWER_FROM_SHORE)
            .exportType(InfrastructureInput.ExportType.DRY_GAS).waterDepth(0.0).tiebackLength(0.0)
            .exportPipeline(80.0, 24.0).build())
        .build();
    return buildTemplate(concept, "Onshore terminal", DEFAULT_FIRST_PRODUCTION_YEAR, 42);
  }

  /**
   * Creates a phased brownfield expansion template.
   *
   * @param name case name
   * @return standardized brownfield expansion case
   */
  public static DevelopmentCaseTemplate phasedBrownfieldExpansion(String name) {
    FieldConcept concept = FieldConcept.builder(name)
        .description("Phased satellite development using spare host capacity and later compression")
        .reservoir(ReservoirInput.gasCondensate().resourceEstimate(18.0, "GSm3")
            .recoveryFactor(0.68).co2Percent(1.5).build())
        .wells(WellsInput.builder().producerCount(2).ratePerWell(1.0e6, "Sm3/d")
            .tubeheadPressure(125.0).build())
        .infrastructure(InfrastructureInput.subseaTieback().tiebackLength(15.0).waterDepth(180.0)
            .powerSupply(InfrastructureInput.PowerSupply.POWER_FROM_HOST)
            .exportType(InfrastructureInput.ExportType.RICH_GAS_CONDENSATE).build())
        .build();
    return buildTemplate(concept, "Phased brownfield expansion", DEFAULT_FIRST_PRODUCTION_YEAR, 24);
  }

  /**
   * Builds the full comparable template object.
   *
   * @param concept field concept
   * @param caseType case type label
   * @param firstProductionYear first production year
   * @param developmentDurationMonths development duration in months
   * @return development case template
   */
  private static DevelopmentCaseTemplate buildTemplate(FieldConcept concept, String caseType,
      int firstProductionYear, int developmentDurationMonths) {
    FacilityBuilder facilityBuilder = FacilityBuilder.autoGenerate(concept)
        .includePowerGeneration(usesLocalPowerGeneration(concept));
    FacilityConfig facilityConfig = facilityBuilder.build();

    EconomicsEstimator economicsEstimator = new EconomicsEstimator();
    EconomicsReport economicsReport = economicsEstimator.estimate(concept, facilityConfig);
    EmissionsTracker emissionsTracker = new EmissionsTracker();
    EmissionsReport emissionsReport = emissionsTracker.estimate(concept, facilityConfig);

    boolean gasConcept = isGasConcept(concept);
    Map<Integer, Double> productionProfile =
        createProductionProfile(concept, gasConcept, firstProductionYear);
    CashFlowEngine engine = new CashFlowEngine("NO");
    engine.setCapex(economicsReport.getTotalCapexMUSD(), firstProductionYear - 1);
    engine.setFixedOpexPerYear(economicsReport.getAnnualOpexMUSD());
    engine.setOpexPercentOfCapex(0.0);
    if (gasConcept) {
      engine.setProductionProfile(null, productionProfile, null);
    } else {
      engine.setProductionProfile(productionProfile, null, null);
    }
    CashFlowEngine.CashFlowResult economics = engine.calculate(DEFAULT_DISCOUNT_RATE);

    String assumptions = String.format(
        "%s template, first production %d, development duration %d months, %d process blocks",
        caseType, firstProductionYear, developmentDurationMonths,
        facilityConfig.getBlocks().size());

    return new DevelopmentCaseTemplate(concept.getName(), caseType, concept, facilityConfig,
        economicsReport.getCapexBreakdown(), productionProfile, economicsReport.getAnnualOpexMUSD(),
        emissionsReport.getTotalPowerMW(), emissionsReport.getTotalEmissionsTonnesPerYear(),
        firstProductionYear, developmentDurationMonths, economics, assumptions);
  }

  /**
   * Checks whether local power generation should be included.
   *
   * @param concept field concept
   * @return true if the concept uses local power generation
   */
  private static boolean usesLocalPowerGeneration(FieldConcept concept) {
    InfrastructureInput.PowerSupply powerSupply = concept.getInfrastructure().getPowerSupply();
    return powerSupply == InfrastructureInput.PowerSupply.GAS_TURBINE
        || powerSupply == InfrastructureInput.PowerSupply.COMBINED_CYCLE
        || powerSupply == InfrastructureInput.PowerSupply.DIESEL;
  }

  /**
   * Checks whether the concept is gas-dominated.
   *
   * @param concept field concept
   * @return true for gas and gas-condensate concepts
   */
  private static boolean isGasConcept(FieldConcept concept) {
    ReservoirInput.FluidType fluidType = concept.getReservoir().getFluidType();
    return fluidType == ReservoirInput.FluidType.LEAN_GAS
        || fluidType == ReservoirInput.FluidType.RICH_GAS
        || fluidType == ReservoirInput.FluidType.GAS_CONDENSATE;
  }

  /**
   * Creates a production profile for template economics.
   *
   * @param concept field concept
   * @param gasConcept true for gas concepts
   * @param firstProductionYear first production year
   * @return annual production profile in Sm3/year for gas or bbl/year for oil
   */
  private static Map<Integer, Double> createProductionProfile(FieldConcept concept,
      boolean gasConcept, int firstProductionYear) {
    double peakRatePerDay =
        gasConcept ? concept.getWells().getRatePerWellSm3d() * concept.getWells().getProducerCount()
            : getOilRateBopd(concept);
    ProductionProfileGenerator generator = new ProductionProfileGenerator();
    return generator.generateFullProfile(peakRatePerDay, 2, 5, gasConcept ? 0.12 : 0.15, 0.5,
        gasConcept ? DeclineType.EXPONENTIAL : DeclineType.HYPERBOLIC, firstProductionYear, 30,
        peakRatePerDay * 0.05);
  }

  /**
   * Gets oil production rate in bbl/d.
   *
   * @param concept field concept
   * @return oil production rate in bbl/d
   */
  private static double getOilRateBopd(FieldConcept concept) {
    double totalRate = concept.getWells().getRatePerWell() * concept.getWells().getProducerCount();
    String unit = concept.getWells().getRateUnit();
    if (unit != null
        && (unit.toLowerCase().contains("bbl") || unit.toLowerCase().contains("bopd"))) {
      return totalRate;
    }
    return totalRate / 0.158987;
  }
}
