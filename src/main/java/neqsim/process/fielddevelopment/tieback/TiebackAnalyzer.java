package neqsim.process.fielddevelopment.tieback;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.process.fielddevelopment.concept.WellsInput;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.NorwegianTaxModel;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator.DeclineType;
import neqsim.process.fielddevelopment.network.MultiphaseFlowIntegrator;
import neqsim.process.fielddevelopment.network.MultiphaseFlowIntegrator.PipelineResult;
import neqsim.process.fielddevelopment.screening.FlowAssuranceReport;
import neqsim.process.fielddevelopment.screening.FlowAssuranceResult;
import neqsim.process.fielddevelopment.screening.FlowAssuranceScreener;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Analyzes tie-back options for connecting satellite fields to host facilities.
 *
 * <p>
 * The TiebackAnalyzer is the main entry point for evaluating subsea tie-back development options.
 * For each potential host facility, it:
 * </p>
 * <ol>
 * <li>Calculates distance and checks routing feasibility</li>
 * <li>Evaluates host capacity constraints</li>
 * <li>Performs flow assurance screening</li>
 * <li>Estimates CAPEX breakdown</li>
 * <li>Calculates NPV and other economics</li>
 * <li>Ranks options by value</li>
 * </ol>
 *
 * <h2>Cost Estimation Basis</h2>
 * <p>
 * The analyzer uses parametric cost models calibrated to Norwegian Continental Shelf benchmarks:
 * </p>
 * <ul>
 * <li>Subsea tree: 25 MUSD per well</li>
 * <li>Manifold/template: 30-50 MUSD</li>
 * <li>Pipeline: 2-4 MUSD/km depending on diameter</li>
 * <li>Umbilical: 1 MUSD/km</li>
 * <li>Host modifications: 20-100 MUSD depending on scope</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Define discovery
 * FieldConcept discovery = FieldConcept.gasTieback("Marginal Gas", 25.0, 2, 1.5);
 *
 * // Define potential hosts
 * List<HostFacility> hosts = new ArrayList<>();
 * hosts.add(HostFacility.builder("Platform A").location(61.5, 2.3).waterDepth(110)
 *     .spareGasCapacity(3.0).minTieInPressure(80).build());
 * hosts.add(HostFacility.builder("FPSO B").location(61.8, 2.1).waterDepth(350)
 *     .spareGasCapacity(5.0).build());
 *
 * // Analyze
 * TiebackAnalyzer analyzer = new TiebackAnalyzer();
 * TiebackReport report = analyzer.analyze(discovery, hosts, 61.6, 2.5);
 *
 * // Review results
 * System.out.println(report.getSummary());
 * TiebackOption best = report.getBestOption();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see TiebackOption
 * @see TiebackReport
 * @see HostFacility
 */
public class TiebackAnalyzer implements Serializable {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // COST PARAMETERS (MUSD)
  // ============================================================================

  /** Cost per subsea tree in MUSD. */
  private double subseaTreeCostMusd = 25.0;

  /** Base cost for manifold/template in MUSD. */
  private double manifoldBaseCostMusd = 35.0;

  /** Pipeline cost per km in MUSD (base, adjusted for diameter). */
  private double pipelineCostPerKmMusd = 2.5;

  /** Umbilical cost per km in MUSD. */
  private double umbilicalCostPerKmMusd = 1.0;

  /** Base cost for host modifications in MUSD. */
  private double hostModificationBaseCostMusd = 30.0;

  /** Drilling cost per well in MUSD. */
  private double drillingCostPerWellMusd = 80.0;

  // ============================================================================
  // ANALYSIS PARAMETERS
  // ============================================================================

  /** Discount rate for NPV calculations. */
  private double discountRate = 0.08;

  /** Default gas price in USD/Sm3. */
  private double gasPriceUsdPerSm3 = 0.25;

  /** Default oil price in USD/bbl. */
  private double oilPriceUsdPerBbl = 75.0;

  /** OPEX as fraction of CAPEX per year. */
  private double opexFraction = 0.04;

  /** Maximum tieback distance in km. */
  private double maxTiebackDistanceKm = 150.0;

  /** Minimum hydrate margin for PASS in Celsius. */
  private double minHydrateMarginC = 5.0;

  /** Seabed temperature for flow assurance in Celsius. */
  private double seabedTemperatureC = 4.0;

  /** Flow assurance screener using NeqSim thermodynamics. */
  private final FlowAssuranceScreener flowAssuranceScreener = new FlowAssuranceScreener();

  // ============================================================================
  // TAX MODEL
  // ============================================================================

  private NorwegianTaxModel taxModel = new NorwegianTaxModel();

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new tieback analyzer with default parameters.
   */
  public TiebackAnalyzer() {
    // Default constructor
  }

  // ============================================================================
  // MAIN ANALYSIS METHODS
  // ============================================================================

  /**
   * Analyzes all tieback options for a discovery.
   *
   * <p>
   * For each host facility, evaluates the technical feasibility and economic attractiveness of a
   * tieback connection. Options are ranked by NPV, with infeasible options marked accordingly.
   * </p>
   *
   * @param discovery the satellite field concept
   * @param hosts list of potential host facilities
   * @param discoveryLatitude discovery latitude in degrees
   * @param discoveryLongitude discovery longitude in degrees
   * @return comprehensive tieback report with ranked options
   */
  public TiebackReport analyze(FieldConcept discovery, List<HostFacility> hosts,
      double discoveryLatitude, double discoveryLongitude) {
    List<TiebackOption> options = new ArrayList<TiebackOption>();

    for (HostFacility host : hosts) {
      TiebackOption option =
          evaluateSingleTieback(discovery, host, discoveryLatitude, discoveryLongitude);
      options.add(option);
    }

    // Sort by NPV (best first)
    Collections.sort(options);

    return new TiebackReport(discovery.getName(), options, discoveryLatitude, discoveryLongitude);
  }

  /**
   * Analyzes tieback options using concept infrastructure input for location.
   *
   * @param discovery the satellite field concept
   * @param hosts list of potential host facilities
   * @return comprehensive tieback report
   */
  public TiebackReport analyze(FieldConcept discovery, List<HostFacility> hosts) {
    // Use infrastructure tieback length as a proxy for location if available
    InfrastructureInput infra = discovery.getInfrastructure();
    double estimatedDistance = infra != null ? infra.getTiebackLength() : 25.0;

    // Use first host location offset as discovery location
    if (hosts.isEmpty()) {
      return new TiebackReport(discovery.getName(), new ArrayList<TiebackOption>(), 0, 0);
    }

    HostFacility firstHost = hosts.get(0);
    // Estimate discovery location based on tieback distance (simplified)
    double discoveryLat = firstHost.getLatitude() + (estimatedDistance / 111.0); // ~111 km/degree
    double discoveryLon = firstHost.getLongitude();

    return analyze(discovery, hosts, discoveryLat, discoveryLon);
  }

  /**
   * Evaluates a single tieback option.
   *
   * @param discovery the satellite field concept
   * @param host the host facility
   * @param discoveryLatitude discovery latitude
   * @param discoveryLongitude discovery longitude
   * @return evaluated tieback option
   */
  public TiebackOption evaluateSingleTieback(FieldConcept discovery, HostFacility host,
      double discoveryLatitude, double discoveryLongitude) {
    TiebackOption option = new TiebackOption(discovery.getName(), host.getName());

    // Calculate routed distance, preferring explicit concept route length when available.
    double distance = resolveRouteLengthKm(discovery, host, discoveryLatitude, discoveryLongitude);
    option.setDistanceKm(distance);

    // Get discovery parameters
    ReservoirInput reservoir = discovery.getReservoir();
    WellsInput wells = discovery.getWells();

    int wellCount = wells != null ? wells.getProducerCount() : 2;
    double ratePerWell = wells != null ? wells.getRatePerWell() : 1.0e6; // Sm3/d
    String rateUnit = wells != null ? wells.getRateUnit() : "Sm3/d";
    double totalRate = wellCount * ratePerWell;

    option.setWellCount(wellCount);

    // Check distance feasibility
    if (distance > maxTiebackDistanceKm) {
      option.setFeasible(false);
      option.setInfeasibilityReason("Distance exceeds maximum (" + maxTiebackDistanceKm + " km)");
      return option;
    }

    // Check host capacity
    boolean isGasField =
        reservoir != null && (reservoir.getFluidType() == ReservoirInput.FluidType.LEAN_GAS
            || reservoir.getFluidType() == ReservoirInput.FluidType.RICH_GAS
            || reservoir.getFluidType() == ReservoirInput.FluidType.GAS_CONDENSATE);

    double requiredRateMSm3d = 0;
    double requiredRateBopd = 0;

    if (isGasField) {
      // Convert to MSm3/d
      if (rateUnit.toLowerCase().contains("sm3")) {
        requiredRateMSm3d = totalRate / 1.0e6;
      } else {
        requiredRateMSm3d = totalRate / 1.0e6; // Assume Sm3/d
      }

      double producedWaterRateM3d = estimateProducedWaterRate(reservoir, requiredRateBopd);
      HostFacility.HostCapacityReport capacityReport =
          host.assessCapacity(requiredRateMSm3d, requiredRateBopd, producedWaterRateM3d,
              estimateTotalLiquidRateM3d(requiredRateBopd, producedWaterRateM3d));
      option.setHostCapacitySummary(capacityReport.getSummary());

      if (!capacityReport.isCapacityAvailable()) {
        option.setFeasible(false);
        option.setInfeasibilityReason("Insufficient host capacity: " + capacityReport.getSummary());
        return option;
      }

      option.setMaxProductionRate(requiredRateMSm3d);
      option.setRateUnit("MSm3/d");
    } else {
      // Oil field
      if (rateUnit.toLowerCase().contains("bbl") || rateUnit.toLowerCase().contains("bopd")) {
        requiredRateBopd = totalRate;
      } else {
        requiredRateBopd = totalRate / 0.159; // Convert m3 to bbl
      }

      double producedWaterRateM3d = estimateProducedWaterRate(reservoir, requiredRateBopd);
      HostFacility.HostCapacityReport capacityReport =
          host.assessCapacity(requiredRateMSm3d, requiredRateBopd, producedWaterRateM3d,
              estimateTotalLiquidRateM3d(requiredRateBopd, producedWaterRateM3d));
      option.setHostCapacitySummary(capacityReport.getSummary());

      if (!capacityReport.isCapacityAvailable()) {
        option.setFeasible(false);
        option.setInfeasibilityReason("Insufficient host capacity: " + capacityReport.getSummary());
        return option;
      }

      option.setMaxProductionRate(requiredRateBopd);
      option.setRateUnit("bbl/d");
    }

    // Set water depth
    option.setMaxWaterDepthM(Math.max(resolveWaterDepthM(discovery, host), 100.0));

    // Pipeline diameter for both hydraulic screening and cost estimate.
    option.setPipelineDiameterInches(
        estimatePipelineDiameterInches(wellCount, totalRate, rateUnit, isGasField));

    // Flow assurance screening with NeqSim hydraulics and thermodynamics.
    screenFlowAssurance(option, discovery, host, reservoir, wells, isGasField, totalRate, rateUnit);

    // Estimate CAPEX
    estimateCapex(option, wellCount, distance, host);

    // Calculate economics
    calculateEconomics(option, isGasField, wellCount, requiredRateMSm3d, requiredRateBopd);

    return option;
  }

  // ============================================================================
  // PRIVATE SCREENING METHODS
  // ============================================================================

  /**
   * Screens the tieback route using pipeline hydraulics and thermodynamic flow-assurance checks.
   *
   * @param option tieback option to update
   * @param discovery discovery concept
   * @param host receiving host facility
   * @param reservoir reservoir input, or null for defaults
   * @param wells well input, or null for defaults
   * @param isGasField true when gas-rate economics and capacity should be used
   * @param totalRate total wellhead rate in the supplied rate unit
   * @param rateUnit total rate unit
   */
  private void screenFlowAssurance(TiebackOption option, FieldConcept discovery, HostFacility host,
      ReservoirInput reservoir, WellsInput wells, boolean isGasField, double totalRate,
      String rateUnit) {
    InfrastructureInput infrastructure = discovery.getInfrastructure();
    double routeSeabedTemperatureC =
        infrastructure != null ? infrastructure.getEstimatedSeabedTemperature()
            : seabedTemperatureC;
    double heatTransferCoefficient = estimateFlowlineHeatTransferCoefficient(infrastructure);
    option.setPipelineHeatTransferCoefficientWm2K(heatTransferCoefficient);

    double inletPressureBara = wells != null ? wells.getTubeheadPressure() : 100.0;
    double inletTemperatureC = reservoir != null ? reservoir.getReservoirTemperature() : 70.0;
    Stream stream = createRepresentativeWellheadStream(reservoir, inletPressureBara,
        inletTemperatureC, isGasField, totalRate, rateUnit);

    PipelineResult hydraulicResult = null;
    try {
      MultiphaseFlowIntegrator integrator = new MultiphaseFlowIntegrator();
      integrator.setPipelineLength(option.getDistanceKm());
      integrator.setPipelineDiameter(option.getPipelineDiameterInches() * 0.0254);
      integrator.setSeabedTemperature(routeSeabedTemperatureC);
      integrator.setOverallHeatTransferCoeff(heatTransferCoefficient);
      integrator.setMinArrivalPressure(host.getMinTieInPressureBara());
      hydraulicResult = integrator.calculateHydraulics(stream, host.getMinTieInPressureBara());
      applyHydraulicResult(option, hydraulicResult, host);
    } catch (Exception e) {
      option.setHydraulicFeasible(false);
      option.setHydraulicInfeasibilityReason("Hydraulic screening failed: " + e.getMessage());
      option.setArrivalPressureBara(
          Math.max(host.getMinTieInPressureBara(), inletPressureBara - 10.0));
      option.setArrivalTemperatureC(estimateArrivalTemperatureC(inletTemperatureC,
          routeSeabedTemperatureC, option.getDistanceKm(), infrastructure));
    }

    double flowAssuranceTemperatureC =
        option.getArrivalTemperatureC() != 0.0 ? option.getArrivalTemperatureC()
            : routeSeabedTemperatureC;
    double flowAssurancePressureBara =
        option.getArrivalPressureBara() > 0.0 ? option.getArrivalPressureBara()
            : Math.max(host.getMinTieInPressureBara(), 30.0);
    FlowAssuranceReport report = flowAssuranceScreener.screen(discovery, flowAssuranceTemperatureC,
        flowAssurancePressureBara);
    applyFlowAssuranceReport(option, report);

    option.setShutdownCooldownTimeToHydrateHours(estimateShutdownCooldownHours(option,
        inletTemperatureC, routeSeabedTemperatureC, infrastructure));
    option.setShutdownCooldownRiskScore(estimateShutdownCooldownRisk(option, infrastructure));
    option.setFlowAssuranceNotes(
        buildFlowAssuranceNotes(option, report, hydraulicResult, routeSeabedTemperatureC));
  }

  /**
   * Applies hydraulic result values to the tieback option.
   *
   * @param option tieback option to update
   * @param result pipeline hydraulic result
   * @param host receiving host facility
   */
  private void applyHydraulicResult(TiebackOption option, PipelineResult result,
      HostFacility host) {
    option.setArrivalPressureBara(result.getArrivalPressureBar());
    option.setArrivalTemperatureC(result.getArrivalTemperatureC());
    option.setHydraulicFeasible(result.isFeasible());
    option.setHydraulicInfeasibilityReason(result.getInfeasibilityReason());
    option.setErosionalVelocityRatio(result.getErosionalVelocityRatio());
    option
        .setFlowRegime(result.getFlowRegime() != null ? result.getFlowRegime().name() : "UNKNOWN");
    if (!result.isFeasible() && result.getArrivalPressureBar() < host.getMinTieInPressureBara()) {
      option.setFeasible(false);
      option.setInfeasibilityReason(
          "Hydraulic tie-in pressure below host minimum: " + result.getInfeasibilityReason());
    }
  }

  /**
   * Applies thermodynamic flow-assurance results to the tieback option.
   *
   * @param option tieback option to update
   * @param report flow assurance report
   */
  private void applyFlowAssuranceReport(TiebackOption option, FlowAssuranceReport report) {
    option.setHydrateResult(report.getHydrateResult());
    option.setWaxResult(report.getWaxResult());
    option.setCorrosionResult(report.getCorrosionResult());
    option.setHydrateMarginC(report.getHydrateMarginC());
    option.setWatMarginC(report.getWaxMarginC());
    option.setHydrateFormationTemperatureC(report.getHydrateFormationTempC());
  }

  private void estimateCapex(TiebackOption option, int wellCount, double distanceKm,
      HostFacility host) {
    // Subsea equipment
    double subseaCost = wellCount * subseaTreeCostMusd + manifoldBaseCostMusd;
    option.setSubseaCapexMusd(subseaCost);

    // Pipeline (adjust cost for water depth)
    double depthFactor = 1.0 + (host.getWaterDepthM() / 1000.0);
    double pipelineCost = distanceKm * pipelineCostPerKmMusd * depthFactor;
    option.setPipelineCapexMusd(pipelineCost);

    // Umbilical
    double umbilicalCost = distanceKm * umbilicalCostPerKmMusd;
    option.setUmbilicalCapexMusd(umbilicalCost);

    // Drilling
    double drillingCost = wellCount * drillingCostPerWellMusd;
    option.setDrillingCapexMusd(drillingCost);

    // Host modifications
    double hostModCost = hostModificationBaseCostMusd;
    option.setHostModificationCapexMusd(hostModCost);

    // Total
    option.calculateTotalCapex();

    if (option.getPipelineDiameterInches() <= 0.0) {
      option.setPipelineDiameterInches(Math.min(8.0 + (wellCount * 2.0), 24.0));
    }
  }

  private void calculateEconomics(TiebackOption option, boolean isGasField, int wellCount,
      double gasRateMSm3d, double oilRateBopd) {
    CashFlowEngine engine = new CashFlowEngine(taxModel);

    // Set prices
    engine.setGasPrice(gasPriceUsdPerSm3);
    engine.setOilPrice(oilPriceUsdPerBbl);
    engine.setOpexPercentOfCapex(opexFraction);

    // Set CAPEX (year before first production)
    engine.setCapex(option.getTotalCapexMusd(), 2025);

    ProductionProfileGenerator profileGenerator = new ProductionProfileGenerator();
    double peakRatePerDay = isGasField ? gasRateMSm3d * 1.0e6 : oilRateBopd;
    Map<Integer, Double> profile =
        profileGenerator.generateFullProfile(peakRatePerDay, 2, 5, isGasField ? 0.12 : 0.15, 0.5,
            isGasField ? DeclineType.EXPONENTIAL : DeclineType.HYPERBOLIC, 2026, 25,
            peakRatePerDay * 0.05);
    option.setFieldLifeYears(profile.size());

    for (Map.Entry<Integer, Double> entry : profile.entrySet()) {
      if (isGasField) {
        engine.addAnnualProduction(entry.getKey(), 0, entry.getValue(), 0);
      } else {
        engine.addAnnualProduction(entry.getKey(), entry.getValue(), 0, 0);
      }
    }

    // Calculate cash flow
    CashFlowEngine.CashFlowResult result = engine.calculate(discountRate);

    option.setNpvMusd(result.getNpv());
    option.setIrr(result.getIrr());
    option.setPaybackYears(result.getPaybackYears());

    // Breakeven price
    if (isGasField) {
      option.setBreakevenPrice(engine.calculateBreakevenGasPrice(discountRate));
    } else {
      option.setBreakevenPrice(engine.calculateBreakevenOilPrice(discountRate));
    }

    double cumulativeProduction = ProductionProfileGenerator.calculateCumulativeProduction(profile);

    if (isGasField) {
      option.setRecoverableReserves(cumulativeProduction / 1.0e9); // GSm3
      option.setReservesUnit("GSm3");
    } else {
      option.setRecoverableReserves(cumulativeProduction / 1.0e6); // MMbbl
      option.setReservesUnit("MMbbl");
    }
  }

  /**
   * Resolves the route length used for screening.
   *
   * @param discovery discovery concept
   * @param host host facility
   * @param discoveryLatitude discovery latitude in degrees
   * @param discoveryLongitude discovery longitude in degrees
   * @return route length in km
   */
  private double resolveRouteLengthKm(FieldConcept discovery, HostFacility host,
      double discoveryLatitude, double discoveryLongitude) {
    InfrastructureInput infrastructure = discovery.getInfrastructure();
    if (infrastructure != null && infrastructure.getTiebackLength() > 0.0) {
      return infrastructure.getTiebackLength();
    }
    return host.distanceToKm(discoveryLatitude, discoveryLongitude);
  }

  /**
   * Resolves water depth from concept infrastructure and host data.
   *
   * @param discovery discovery concept
   * @param host host facility
   * @return maximum route water depth in meters
   */
  private double resolveWaterDepthM(FieldConcept discovery, HostFacility host) {
    InfrastructureInput infrastructure = discovery.getInfrastructure();
    double conceptDepth = infrastructure != null ? infrastructure.getWaterDepth() : 0.0;
    return Math.max(host.getWaterDepthM(), conceptDepth);
  }

  /**
   * Estimates produced-water rate from water cut and oil rate.
   *
   * @param reservoir reservoir input, or null
   * @param oilRateBopd oil rate in bbl/d
   * @return produced-water rate in m3/d
   */
  private double estimateProducedWaterRate(ReservoirInput reservoir, double oilRateBopd) {
    if (reservoir == null || oilRateBopd <= 0.0) {
      return 0.0;
    }
    double waterCut = reservoir.getWaterCut();
    if (waterCut <= 0.0 || waterCut >= 0.99) {
      return 0.0;
    }
    double oilRateM3d = oilRateBopd * 0.158987;
    return oilRateM3d * waterCut / (1.0 - waterCut);
  }

  /**
   * Estimates total liquid rate.
   *
   * @param oilRateBopd oil rate in bbl/d
   * @param waterRateM3d water rate in m3/d
   * @return total liquid rate in m3/d
   */
  private double estimateTotalLiquidRateM3d(double oilRateBopd, double waterRateM3d) {
    return oilRateBopd * 0.158987 + waterRateM3d;
  }

  /**
   * Estimates a screening pipeline diameter.
   *
   * @param wellCount number of producing wells
   * @param totalRate total production rate
   * @param rateUnit total production rate unit
   * @param isGasField true for gas field concepts
   * @return pipeline diameter in inches
   */
  private double estimatePipelineDiameterInches(int wellCount, double totalRate, String rateUnit,
      boolean isGasField) {
    if (isGasField) {
      double rateMSm3d = totalRate / 1.0e6;
      if (rateUnit != null && rateUnit.toLowerCase().contains("msm3")) {
        rateMSm3d = totalRate;
      }
      if (rateMSm3d <= 1.0) {
        return 8.0;
      } else if (rateMSm3d <= 3.0) {
        return 10.0;
      } else if (rateMSm3d <= 6.0) {
        return 12.0;
      }
      return 16.0;
    }
    double oilRateBopd = totalRate;
    if (rateUnit != null
        && !(rateUnit.toLowerCase().contains("bbl") || rateUnit.toLowerCase().contains("bopd"))) {
      oilRateBopd = totalRate / 0.158987;
    }
    if (oilRateBopd <= 10000.0) {
      return Math.max(8.0, 6.0 + wellCount);
    } else if (oilRateBopd <= 30000.0) {
      return 12.0;
    }
    return 16.0;
  }

  /**
   * Estimates a representative flowline heat transfer coefficient.
   *
   * @param infrastructure infrastructure input, or null
   * @return overall heat transfer coefficient in W/m2K
   */
  private double estimateFlowlineHeatTransferCoefficient(InfrastructureInput infrastructure) {
    if (infrastructure == null) {
      return 6.0;
    }
    if (infrastructure.hasElectricHeating()) {
      return 1.5;
    }
    return infrastructure.isInsulatedFlowline() ? 2.0 : 8.0;
  }

  /**
   * Creates a representative wellhead stream for hydraulic screening.
   *
   * @param reservoir reservoir input, or null for defaults
   * @param pressureBara wellhead pressure in bara
   * @param temperatureC wellhead temperature in Celsius
   * @param isGasField true for gas field concepts
   * @param totalRate total production rate
   * @param rateUnit total production rate unit
   * @return representative stream with total mass flow set
   */
  private Stream createRepresentativeWellheadStream(ReservoirInput reservoir, double pressureBara,
      double temperatureC, boolean isGasField, double totalRate, String rateUnit) {
    SystemInterface fluid = createRepresentativeFluid(reservoir, temperatureC, pressureBara);
    Stream stream = new Stream("Tieback wellhead", fluid);
    stream.setFlowRate(estimateMassFlowKgHr(isGasField, totalRate, rateUnit), "kg/hr");
    stream.run();
    return stream;
  }

  /**
   * Creates a representative fluid for hydraulic screening.
   *
   * @param reservoir reservoir input, or null for defaults
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @return initialized thermodynamic system
   */
  private SystemInterface createRepresentativeFluid(ReservoirInput reservoir, double temperatureC,
      double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(temperatureC + 273.15, pressureBara);
    ReservoirInput.FluidType fluidType =
        reservoir != null ? reservoir.getFluidType() : ReservoirInput.FluidType.RICH_GAS;
    switch (fluidType) {
      case LEAN_GAS:
        fluid.addComponent("methane", 0.90);
        fluid.addComponent("ethane", 0.06);
        fluid.addComponent("propane", 0.02);
        break;
      case BLACK_OIL:
      case HEAVY_OIL:
      case VOLATILE_OIL:
        fluid.addComponent("methane", 0.25);
        fluid.addComponent("ethane", 0.05);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-butane", 0.05);
        fluid.addComponent("n-hexane", 0.10);
        fluid.addComponent("n-heptane", 0.20);
        fluid.addComponent("nC10", 0.25);
        break;
      case GAS_CONDENSATE:
        fluid.addComponent("methane", 0.72);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.06);
        fluid.addComponent("n-butane", 0.04);
        fluid.addComponent("n-hexane", 0.04);
        fluid.addComponent("n-heptane", 0.03);
        break;
      default:
        fluid.addComponent("methane", 0.82);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-butane", 0.02);
        break;
    }
    if (reservoir != null && reservoir.getCo2Percent() > 0.1) {
      fluid.addComponent("CO2", reservoir.getCo2Percent() / 100.0);
    }
    if (reservoir != null && reservoir.getH2SPercent() > 0.001) {
      fluid.addComponent("H2S", reservoir.getH2SPercent() / 100.0);
    }
    fluid.setMixingRule("classic");
    fluid.createDatabase(true);
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.TPflash();
      fluid.initProperties();
    } catch (Exception e) {
      fluid.init(0);
    }
    return fluid;
  }

  /**
   * Estimates total mass flow for hydraulic screening.
   *
   * @param isGasField true for gas field concepts
   * @param totalRate total rate
   * @param rateUnit rate unit
   * @return mass flow in kg/hr
   */
  private double estimateMassFlowKgHr(boolean isGasField, double totalRate, String rateUnit) {
    if (isGasField) {
      double gasRateSm3d = totalRate;
      if (rateUnit != null && rateUnit.toLowerCase().contains("msm3")) {
        gasRateSm3d = totalRate * 1.0e6;
      }
      return gasRateSm3d * 0.85 / 24.0;
    }
    if (rateUnit != null
        && (rateUnit.toLowerCase().contains("bbl") || rateUnit.toLowerCase().contains("bopd"))) {
      return totalRate * 0.158987 * 850.0 / 24.0;
    }
    return totalRate * 850.0 / 24.0;
  }

  /**
   * Estimates arrival temperature if the hydraulic model cannot run.
   *
   * @param inletTemperatureC inlet temperature in Celsius
   * @param seabedTemperatureC seabed temperature in Celsius
   * @param distanceKm route length in km
   * @param infrastructure infrastructure input, or null
   * @return estimated arrival temperature in Celsius
   */
  private double estimateArrivalTemperatureC(double inletTemperatureC, double seabedTemperatureC,
      double distanceKm, InfrastructureInput infrastructure) {
    double coolingFactor =
        infrastructure != null && infrastructure.isInsulatedFlowline() ? 0.010 : 0.035;
    if (infrastructure != null && infrastructure.hasElectricHeating()) {
      coolingFactor = 0.005;
    }
    double approach = Math.exp(-coolingFactor * distanceKm);
    return seabedTemperatureC + (inletTemperatureC - seabedTemperatureC) * approach;
  }

  /**
   * Estimates shutdown cooldown time to hydrate risk.
   *
   * @param option tieback option with hydrate results
   * @param inletTemperatureC inlet temperature in Celsius
   * @param seabedTemperatureC seabed temperature in Celsius
   * @param infrastructure infrastructure input, or null
   * @return cooldown time in hours
   */
  private double estimateShutdownCooldownHours(TiebackOption option, double inletTemperatureC,
      double seabedTemperatureC, InfrastructureInput infrastructure) {
    double hydrateTemperatureC = option.getHydrateFormationTemperatureC();
    if (Double.isNaN(hydrateTemperatureC) || hydrateTemperatureC <= seabedTemperatureC) {
      return Double.POSITIVE_INFINITY;
    }
    double initialTemperatureC = Math.max(option.getArrivalTemperatureC(), inletTemperatureC);
    if (initialTemperatureC <= hydrateTemperatureC) {
      return 0.0;
    }
    double thermalTimeConstantHours =
        infrastructure != null && infrastructure.isInsulatedFlowline() ? 24.0 : 8.0;
    if (infrastructure != null && infrastructure.hasElectricHeating()) {
      thermalTimeConstantHours = 48.0;
    }
    double numerator = initialTemperatureC - seabedTemperatureC;
    double denominator = hydrateTemperatureC - seabedTemperatureC;
    return thermalTimeConstantHours * Math.log(numerator / denominator);
  }

  /**
   * Estimates shutdown cooldown risk score.
   *
   * @param option tieback option with cooldown results
   * @param infrastructure infrastructure input, or null
   * @return risk score from 0 to 1
   */
  private double estimateShutdownCooldownRisk(TiebackOption option,
      InfrastructureInput infrastructure) {
    if (infrastructure != null && infrastructure.hasElectricHeating()) {
      return 0.10;
    }
    double hours = option.getShutdownCooldownTimeToHydrateHours();
    if (Double.isInfinite(hours)) {
      return 0.0;
    }
    if (hours <= 0.0) {
      return 1.0;
    }
    if (hours < 6.0) {
      return 0.85;
    }
    if (hours < 24.0) {
      return 0.55;
    }
    return 0.25;
  }

  /**
   * Builds human-readable flow-assurance notes.
   *
   * @param option tieback option
   * @param report flow assurance report
   * @param hydraulicResult hydraulic result, or null if hydraulic screening failed
   * @param seabedTemperatureC seabed temperature in Celsius
   * @return notes string
   */
  private String buildFlowAssuranceNotes(TiebackOption option, FlowAssuranceReport report,
      PipelineResult hydraulicResult, double seabedTemperatureC) {
    StringBuilder notes = new StringBuilder();
    notes.append(
      String.format("Hydraulics: route %.1f km, seabed %.1f C, U %.1f W/m2K. ", option.getDistanceKm(),
            seabedTemperatureC, option.getPipelineHeatTransferCoefficientWm2K()));
    if (hydraulicResult != null) {
      notes.append(String.format("Arrival %.1f bara / %.1f C, regime %s, erosional ratio %.2f. ",
          option.getArrivalPressureBara(), option.getArrivalTemperatureC(), option.getFlowRegime(),
          option.getErosionalVelocityRatio()));
    }
    notes.append(String.format("Hydrate formation %.1f C, margin %.1f C. ",
        option.getHydrateFormationTemperatureC(), option.getHydrateMarginC()));
    notes.append(String.format("Shutdown cooldown risk %.0f%%, time to hydrate %.1f h. ",
        option.getShutdownCooldownRiskScore() * 100.0,
        option.getShutdownCooldownTimeToHydrateHours()));
    for (String recommendation : report.getRecommendations().values()) {
      notes.append(recommendation).append(" ");
    }
    if (!option.isHydraulicFeasible() && option.getHydraulicInfeasibilityReason() != null) {
      notes.append(option.getHydraulicInfeasibilityReason());
    }
    return notes.toString().trim();
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Gets the discount rate.
   *
   * @return discount rate (0-1)
   */
  public double getDiscountRate() {
    return discountRate;
  }

  /**
   * Sets the discount rate.
   *
   * @param discountRate discount rate (0-1)
   */
  public void setDiscountRate(double discountRate) {
    this.discountRate = discountRate;
  }

  /**
   * Gets the gas price.
   *
   * @return gas price in USD/Sm3
   */
  public double getGasPriceUsdPerSm3() {
    return gasPriceUsdPerSm3;
  }

  /**
   * Sets the gas price.
   *
   * @param gasPriceUsdPerSm3 gas price in USD/Sm3
   */
  public void setGasPriceUsdPerSm3(double gasPriceUsdPerSm3) {
    this.gasPriceUsdPerSm3 = gasPriceUsdPerSm3;
  }

  /**
   * Gets the oil price.
   *
   * @return oil price in USD/bbl
   */
  public double getOilPriceUsdPerBbl() {
    return oilPriceUsdPerBbl;
  }

  /**
   * Sets the oil price.
   *
   * @param oilPriceUsdPerBbl oil price in USD/bbl
   */
  public void setOilPriceUsdPerBbl(double oilPriceUsdPerBbl) {
    this.oilPriceUsdPerBbl = oilPriceUsdPerBbl;
  }

  /**
   * Gets the subsea tree cost.
   *
   * @return cost in MUSD per tree
   */
  public double getSubseaTreeCostMusd() {
    return subseaTreeCostMusd;
  }

  /**
   * Sets the subsea tree cost.
   *
   * @param subseaTreeCostMusd cost in MUSD per tree
   */
  public void setSubseaTreeCostMusd(double subseaTreeCostMusd) {
    this.subseaTreeCostMusd = subseaTreeCostMusd;
  }

  /**
   * Gets the pipeline cost per km.
   *
   * @return cost in MUSD/km
   */
  public double getPipelineCostPerKmMusd() {
    return pipelineCostPerKmMusd;
  }

  /**
   * Sets the pipeline cost per km.
   *
   * @param pipelineCostPerKmMusd cost in MUSD/km
   */
  public void setPipelineCostPerKmMusd(double pipelineCostPerKmMusd) {
    this.pipelineCostPerKmMusd = pipelineCostPerKmMusd;
  }

  /**
   * Gets the maximum tieback distance.
   *
   * @return maximum distance in km
   */
  public double getMaxTiebackDistanceKm() {
    return maxTiebackDistanceKm;
  }

  /**
   * Sets the maximum tieback distance.
   *
   * @param maxTiebackDistanceKm maximum distance in km
   */
  public void setMaxTiebackDistanceKm(double maxTiebackDistanceKm) {
    this.maxTiebackDistanceKm = maxTiebackDistanceKm;
  }

  /**
   * Gets the tax model.
   *
   * @return tax model
   */
  public NorwegianTaxModel getTaxModel() {
    return taxModel;
  }

  /**
   * Sets the tax model.
   *
   * @param taxModel tax model
   */
  public void setTaxModel(NorwegianTaxModel taxModel) {
    this.taxModel = taxModel;
  }

  // ============================================================================
  // TIEBACK SCREENING METHODS
  // ============================================================================

  /**
   * Quick screening for tieback feasibility without full analysis.
   *
   * <p>
   * Performs rapid go/no-go screening based on key constraints:
   * </p>
   * <ul>
   * <li>Distance to host</li>
   * <li>Water depth compatibility</li>
   * <li>Host capacity</li>
   * <li>Pressure compatibility</li>
   * </ul>
   *
   * @param discoveryLat discovery latitude
   * @param discoveryLon discovery longitude
   * @param reservesMMboe reserves in MMboe
   * @param waterDepthM water depth in meters
   * @param host potential host facility
   * @return screening result with pass/fail and reason
   */
  public TiebackScreeningResult quickScreen(double discoveryLat, double discoveryLon,
      double reservesMMboe, double waterDepthM, HostFacility host) {
    TiebackScreeningResult result = new TiebackScreeningResult();
    result.setHostName(host.getName());

    // Distance check
    double distance = host.distanceToKm(discoveryLat, discoveryLon);
    result.setDistanceKm(distance);

    if (distance > maxTiebackDistanceKm) {
      result.setPassed(false);
      result.setFailureReason("Distance " + String.format("%.1f", distance) + " km exceeds maximum "
          + maxTiebackDistanceKm + " km");
      return result;
    }

    // Water depth check
    if (waterDepthM > host.getWaterDepthM() * 1.2) {
      result.setPassed(false);
      result.setFailureReason("Water depth " + waterDepthM + " m exceeds host capability");
      return result;
    }

    // Reserves check (minimum economic size)
    double minReserves = distance * 0.5; // Simple rule: 0.5 MMboe per km
    if (reservesMMboe < minReserves) {
      result.setPassed(false);
      result.setFailureReason("Reserves " + reservesMMboe + " MMboe below minimum "
          + String.format("%.1f", minReserves) + " MMboe for distance");
      return result;
    }

    result.setPassed(true);
    result.setEstimatedCapexMusd(estimateQuickCapex(distance, waterDepthM));
    result.setEstimatedNpvMusd(estimateQuickNpv(reservesMMboe, result.getEstimatedCapexMusd()));

    return result;
  }

  /**
   * Screen multiple hosts quickly and return ranked results.
   *
   * @param discoveryLat discovery latitude
   * @param discoveryLon discovery longitude
   * @param reservesMMboe reserves in MMboe
   * @param waterDepthM water depth in meters
   * @param hosts list of potential hosts
   * @return list of screening results, ranked by estimated NPV
   */
  public List<TiebackScreeningResult> screenAllHosts(double discoveryLat, double discoveryLon,
      double reservesMMboe, double waterDepthM, List<HostFacility> hosts) {
    List<TiebackScreeningResult> results = new ArrayList<TiebackScreeningResult>();

    for (HostFacility host : hosts) {
      results.add(quickScreen(discoveryLat, discoveryLon, reservesMMboe, waterDepthM, host));
    }

    // Sort by estimated NPV (best first)
    Collections.sort(results, new java.util.Comparator<TiebackScreeningResult>() {
      @Override
      public int compare(TiebackScreeningResult a, TiebackScreeningResult b) {
        if (a.isPassed() && !b.isPassed()) {
          return -1;
        }
        if (!a.isPassed() && b.isPassed()) {
          return 1;
        }
        return Double.compare(b.getEstimatedNpvMusd(), a.getEstimatedNpvMusd());
      }
    });

    return results;
  }

  /**
   * Quick CAPEX estimate for screening.
   *
   * @param distanceKm the tieback distance in kilometers
   * @param waterDepthM the water depth in meters
   * @return the estimated CAPEX in million USD
   */
  private double estimateQuickCapex(double distanceKm, double waterDepthM) {
    double pipeline = distanceKm * pipelineCostPerKmMusd;
    double umbilical = distanceKm * umbilicalCostPerKmMusd;
    double subsea = 2 * subseaTreeCostMusd; // Assume 2 wells
    double manifold = manifoldBaseCostMusd;
    double hostMod = hostModificationBaseCostMusd;

    // Deep water multiplier
    double depthMultiplier = 1.0 + Math.max(0, (waterDepthM - 200) / 500);

    return (pipeline + umbilical + subsea + manifold + hostMod) * depthMultiplier;
  }

  /**
   * Quick NPV estimate for screening.
   *
   * @param reservesMMboe recoverable reserves in million barrels of oil equivalent
   * @param capexMusd capital expenditure in million USD
   * @return estimated net present value in million USD
   */
  private double estimateQuickNpv(double reservesMMboe, double capexMusd) {
    // Simplified NPV: revenue - capex - opex
    double revenuePerBoe = oilPriceUsdPerBbl * 0.8; // 80% netback
    double totalRevenue = reservesMMboe * 1e6 * revenuePerBoe / 1e6; // MUSD
    double opex = capexMusd * 0.04 * 15; // 4% of CAPEX for 15 years
    double discountFactor = 0.6; // Rough discount for timing

    return (totalRevenue - opex) * discountFactor - capexMusd;
  }

  /**
   * Screening result for quick tieback evaluation.
   */
  public static class TiebackScreeningResult implements Serializable {
    private static final long serialVersionUID = 1100L;

    private String hostName;
    private boolean passed;
    private String failureReason;
    private double distanceKm;
    private double estimatedCapexMusd;
    private double estimatedNpvMusd;

    /**
     * Get host name.
     *
     * @return the host facility name
     */
    public String getHostName() {
      return hostName;
    }

    /**
     * Set host name.
     *
     * @param name the host facility name
     */
    public void setHostName(String name) {
      this.hostName = name;
    }

    /**
     * Check if passed.
     *
     * @return true if feasibility check passed
     */
    public boolean isPassed() {
      return passed;
    }

    /**
     * Set passed status.
     *
     * @param passed true if feasibility check passed
     */
    public void setPassed(boolean passed) {
      this.passed = passed;
    }

    /**
     * Get failure reason.
     *
     * @return the reason for failure, or null if passed
     */
    public String getFailureReason() {
      return failureReason;
    }

    /**
     * Set failure reason.
     *
     * @param reason the reason for failure
     */
    public void setFailureReason(String reason) {
      this.failureReason = reason;
    }

    /**
     * Get distance.
     *
     * @return the tieback distance in kilometers
     */
    public double getDistanceKm() {
      return distanceKm;
    }

    /**
     * Set distance.
     *
     * @param km the tieback distance in kilometers
     */
    public void setDistanceKm(double km) {
      this.distanceKm = km;
    }

    /**
     * Get estimated CAPEX.
     *
     * @return the estimated capital expenditure in million USD
     */
    public double getEstimatedCapexMusd() {
      return estimatedCapexMusd;
    }

    /**
     * Set estimated CAPEX.
     *
     * @param capex the estimated capital expenditure in million USD
     */
    public void setEstimatedCapexMusd(double capex) {
      this.estimatedCapexMusd = capex;
    }

    /**
     * Get estimated NPV.
     *
     * @return the estimated net present value in million USD
     */
    public double getEstimatedNpvMusd() {
      return estimatedNpvMusd;
    }

    /**
     * Set estimated NPV.
     *
     * @param npv the estimated net present value in million USD
     */
    public void setEstimatedNpvMusd(double npv) {
      this.estimatedNpvMusd = npv;
    }

    @Override
    public String toString() {
      if (passed) {
        return String.format("%s: PASS (%.0f km, CAPEX=%.0f, NPV=%.0f MUSD)", hostName, distanceKm,
            estimatedCapexMusd, estimatedNpvMusd);
      } else {
        return String.format("%s: FAIL - %s", hostName, failureReason);
      }
    }
  }
}
