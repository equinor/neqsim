package neqsim.process.fielddevelopment.tieback;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.process.fielddevelopment.concept.WellsInput;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.NorwegianTaxModel;
import neqsim.process.fielddevelopment.screening.FlowAssuranceResult;

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

    // Calculate distance
    double distance = host.distanceToKm(discoveryLatitude, discoveryLongitude);
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

      if (!host.canAcceptGasRate(requiredRateMSm3d)) {
        option.setFeasible(false);
        option.setInfeasibilityReason("Insufficient host gas capacity (need " + requiredRateMSm3d
            + " MSm3/d, spare " + host.getSpareGasCapacity() + " MSm3/d)");
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

      if (!host.canAcceptOilRate(requiredRateBopd)) {
        option.setFeasible(false);
        option.setInfeasibilityReason("Insufficient host oil capacity");
        return option;
      }

      option.setMaxProductionRate(requiredRateBopd);
      option.setRateUnit("bbl/d");
    }

    // Set water depth
    option.setMaxWaterDepthM(Math.max(host.getWaterDepthM(), 100.0));

    // Flow assurance screening (simplified)
    screenFlowAssurance(option, reservoir, distance);

    // Estimate CAPEX
    estimateCapex(option, wellCount, distance, host);

    // Calculate economics
    calculateEconomics(option, isGasField, wellCount, requiredRateMSm3d, requiredRateBopd);

    return option;
  }

  // ============================================================================
  // PRIVATE SCREENING METHODS
  // ============================================================================

  private void screenFlowAssurance(TiebackOption option, ReservoirInput reservoir,
      double distanceKm) {
    // Simplified flow assurance screening
    // In a full implementation, this would use NeqSim thermodynamics

    double co2Percent = reservoir != null ? reservoir.getCo2Percent() : 2.0;
    double h2sPercent = reservoir != null ? reservoir.getH2sPercent() : 0.0;
    double waterCut = reservoir != null ? reservoir.getWaterCut() : 0.0;

    // Hydrate screening (simplified)
    // Longer distances = more cooling = higher hydrate risk
    double hydrateMargin = 10.0 - (distanceKm / 20.0); // Simplified
    option.setHydrateMarginC(hydrateMargin);

    if (hydrateMargin < 0) {
      option.setHydrateResult(FlowAssuranceResult.FAIL);
    } else if (hydrateMargin < minHydrateMarginC) {
      option.setHydrateResult(FlowAssuranceResult.MARGINAL);
    } else {
      option.setHydrateResult(FlowAssuranceResult.PASS);
    }

    // Wax screening (oil fields only, simplified)
    if (reservoir != null && (reservoir.getFluidType() == ReservoirInput.FluidType.BLACK_OIL
        || reservoir.getFluidType() == ReservoirInput.FluidType.HEAVY_OIL)) {
      double watMargin = 15.0 - (distanceKm / 15.0);
      option.setWatMarginC(watMargin);

      if (watMargin < 0) {
        option.setWaxResult(FlowAssuranceResult.FAIL);
      } else if (watMargin < 10.0) {
        option.setWaxResult(FlowAssuranceResult.MARGINAL);
      } else {
        option.setWaxResult(FlowAssuranceResult.PASS);
      }
    }

    // Corrosion screening
    if (h2sPercent > 0.01) { // > 100 ppm
      option.setCorrosionResult(FlowAssuranceResult.FAIL);
      option.setFlowAssuranceNotes("Sour service - full NACE MR0175 compliance required");
    } else if (co2Percent > 3.0) {
      option.setCorrosionResult(FlowAssuranceResult.MARGINAL);
      option.setFlowAssuranceNotes("High CO2 - CRA materials or continuous inhibition required");
    } else if (co2Percent > 1.0) {
      option.setCorrosionResult(FlowAssuranceResult.MARGINAL);
      option.setFlowAssuranceNotes("Moderate CO2 - corrosion inhibition recommended");
    }
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

    // Pipeline diameter (simplified sizing)
    double diameter = 8.0 + (wellCount * 2.0); // Inches
    option.setPipelineDiameterInches(Math.min(diameter, 24.0));
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

    // Estimate reserves and field life
    double fieldLife = 15.0; // Default 15 years
    option.setFieldLifeYears(fieldLife);

    // Production profile (plateau then decline)
    int plateauYears = 5;
    for (int year = 2026; year <= 2026 + (int) fieldLife; year++) {
      int productionYear = year - 2026;
      double declineFactor =
          productionYear < plateauYears ? 1.0 : Math.exp(-0.15 * (productionYear - plateauYears));

      if (isGasField) {
        double annualGasSm3 = gasRateMSm3d * 1.0e6 * 365.25 * declineFactor;
        engine.addAnnualProduction(year, 0, annualGasSm3, 0);
      } else {
        double annualOilBbl = oilRateBopd * 365.25 * declineFactor;
        engine.addAnnualProduction(year, annualOilBbl, 0, 0);
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

    // Estimate reserves
    double cumulativeProduction = 0;
    for (int year = 2026; year <= 2026 + (int) fieldLife; year++) {
      int productionYear = year - 2026;
      double declineFactor =
          productionYear < plateauYears ? 1.0 : Math.exp(-0.15 * (productionYear - plateauYears));

      if (isGasField) {
        cumulativeProduction += gasRateMSm3d * 1.0e6 * 365.25 * declineFactor;
      } else {
        cumulativeProduction += oilRateBopd * 365.25 * declineFactor;
      }
    }

    if (isGasField) {
      option.setRecoverableReserves(cumulativeProduction / 1.0e9); // GSm3
      option.setReservesUnit("GSm3");
    } else {
      option.setRecoverableReserves(cumulativeProduction / 1.0e6); // MMbbl
      option.setReservesUnit("MMbbl");
    }
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

    /** Get host name. */
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
