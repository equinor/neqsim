package neqsim.process.fielddevelopment.tieback.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Time-series host tie-in planner for capacity allocation, satellite holdback, process-model
 * checks, and debottleneck recommendations.
 *
 * <p>
 * The planner has three layers:
 * </p>
 * <ol>
 * <li>Nameplate ullage: base-host and satellite rates are checked against gas, oil, water, and
 * liquid capacities in {@link HostFacility}.</li>
 * <li>Process capacity: when a process model and {@link HostTieInPoint} are configured, the
 * accepted satellite load is injected into a host stream and equipment capacity constraints are
 * checked.</li>
 * <li>Decision layer: deferred or curtailed production value is summarized and converted into a
 * simple debottleneck recommendation.</li>
 * </ol>
 *
 * @author ESOL
 * @version 1.0
 */
public class TieInCapacityPlanner implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Host facility receiving the satellite production. */
  private final HostFacility hostFacility;

  /** Base host production profile. */
  private ProductionProfileSeries hostProductionProfile;

  /** Satellite production profile. */
  private ProductionProfileSeries satelliteProductionProfile;

  /** Allocation policy for constrained periods. */
  private CapacityAllocationPolicy allocationPolicy = CapacityAllocationPolicy.BASE_FIRST;

  /** Holdback policy for unaccepted satellite production. */
  private HoldbackPolicy holdbackPolicy = HoldbackPolicy.CURTAIL;

  /** Optional process-model tie-in point. */
  private HostTieInPoint tieInPoint;

  /** Maximum accepted process utilization as fraction. */
  private double processUtilizationLimit = 1.0;

  /** Number of binary-search iterations for process holdback. */
  private int processSearchIterations = 24;

  /** Gas value in USD per MSm3 used when loads do not carry values. */
  private double defaultGasValueUsdPerMSm3 = 120000.0;

  /** Oil value in USD per bbl used when loads do not carry values. */
  private double defaultOilValueUsdPerBbl = 70.0;

  /** Water value in USD per m3 used when loads do not carry values. */
  private double defaultWaterValueUsdPerM3 = 0.0;

  /** Liquid value in USD per m3 used when loads do not carry values. */
  private double defaultLiquidValueUsdPerM3 = 0.0;

  /** Discount rate used for deferred value. */
  private double discountRate = 0.08;

  /** Default debottleneck investment estimate. */
  private double defaultDebottleneckCapexMusd = 50.0;

  /**
   * Creates a tie-in capacity planner.
   *
   * @param hostFacility host facility to evaluate
   */
  public TieInCapacityPlanner(HostFacility hostFacility) {
    if (hostFacility == null) {
      throw new IllegalArgumentException("Host facility cannot be null");
    }
    this.hostFacility = hostFacility;
  }

  /**
   * Sets the base host production profile.
   *
   * @param hostProductionProfile host production profile
   * @return this planner for method chaining
   */
  public TieInCapacityPlanner setHostProductionProfile(
      ProductionProfileSeries hostProductionProfile) {
    this.hostProductionProfile = hostProductionProfile;
    return this;
  }

  /**
   * Sets the satellite production profile.
   *
   * @param satelliteProductionProfile satellite production profile
   * @return this planner for method chaining
   */
  public TieInCapacityPlanner setSatelliteProductionProfile(
      ProductionProfileSeries satelliteProductionProfile) {
    this.satelliteProductionProfile = satelliteProductionProfile;
    return this;
  }

  /**
   * Sets the allocation policy.
   *
   * @param allocationPolicy allocation policy
   * @return this planner for method chaining
   */
  public TieInCapacityPlanner setAllocationPolicy(CapacityAllocationPolicy allocationPolicy) {
    this.allocationPolicy =
        allocationPolicy == null ? CapacityAllocationPolicy.BASE_FIRST : allocationPolicy;
    return this;
  }

  /**
   * Sets the holdback policy.
   *
   * @param holdbackPolicy holdback policy
   * @return this planner for method chaining
   */
  public TieInCapacityPlanner setHoldbackPolicy(HoldbackPolicy holdbackPolicy) {
    this.holdbackPolicy = holdbackPolicy == null ? HoldbackPolicy.CURTAIL : holdbackPolicy;
    return this;
  }

  /**
   * Sets the process-model tie-in point.
   *
   * @param tieInPoint process-model tie-in point
   * @return this planner for method chaining
   */
  public TieInCapacityPlanner setTieInPoint(HostTieInPoint tieInPoint) {
    this.tieInPoint = tieInPoint;
    return this;
  }

  /**
   * Sets the process utilization limit.
   *
   * @param processUtilizationLimit maximum utilization fraction allowed in the process model
   * @return this planner for method chaining
   */
  public TieInCapacityPlanner setProcessUtilizationLimit(double processUtilizationLimit) {
    this.processUtilizationLimit = Math.max(0.01, processUtilizationLimit);
    return this;
  }

  /**
   * Sets default commodity values.
   *
   * @param gasUsdPerMSm3 gas value in USD/MSm3
   * @param oilUsdPerBbl oil value in USD/bbl
   * @param waterUsdPerM3 water value in USD/m3
   * @param liquidUsdPerM3 liquid value in USD/m3
   * @return this planner for method chaining
   */
  public TieInCapacityPlanner setDefaultCommodityValues(double gasUsdPerMSm3, double oilUsdPerBbl,
      double waterUsdPerM3, double liquidUsdPerM3) {
    this.defaultGasValueUsdPerMSm3 = gasUsdPerMSm3;
    this.defaultOilValueUsdPerBbl = oilUsdPerBbl;
    this.defaultWaterValueUsdPerM3 = waterUsdPerM3;
    this.defaultLiquidValueUsdPerM3 = liquidUsdPerM3;
    return this;
  }

  /**
   * Sets the discount rate.
   *
   * @param discountRate annual discount rate as a fraction
   * @return this planner for method chaining
   */
  public TieInCapacityPlanner setDiscountRate(double discountRate) {
    this.discountRate = Math.max(0.0, discountRate);
    return this;
  }

  /**
   * Sets default debottleneck CAPEX.
   *
   * @param defaultDebottleneckCapexMusd default debottleneck CAPEX in MUSD
   * @return this planner for method chaining
   */
  public TieInCapacityPlanner setDefaultDebottleneckCapexMusd(double defaultDebottleneckCapexMusd) {
    this.defaultDebottleneckCapexMusd = Math.max(0.0, defaultDebottleneckCapexMusd);
    return this;
  }

  /**
   * Runs the capacity and holdback study.
   *
   * @return aggregated tie-in capacity result
   */
  public TieInCapacityResult run() {
    validateProfiles();
    List<TieInPeriodResult> periods = new ArrayList<TieInPeriodResult>();
    ProductionLoad deferredBacklog = ProductionLoad.zero(getFirstYear(), "initial backlog");

    for (int index = 0; index < satelliteProductionProfile.size(); index++) {
      ProductionLoad scheduledSatellite = satelliteProductionProfile.getLoad(index);
      ProductionLoad baseRequest =
          getBaseLoad(scheduledSatellite.getYear(), index, scheduledSatellite.getPeriodName());
      ProductionLoad deferredIntoPeriod = deferredBacklog
          .withPeriod(scheduledSatellite.getPeriodName(), scheduledSatellite.getYear());
      ProductionLoad satelliteRequest = scheduledSatellite.plus(deferredIntoPeriod);

      AllocationResult allocation = allocateNameplate(baseRequest, satelliteRequest);
      ProcessAdjustedAllocation adjusted = applyProcessCapacity(baseRequest, allocation);

      ProductionLoad heldBack = satelliteRequest.subtractNonNegative(adjusted.acceptedSatellite);
      ProductionLoad deferredToNext = holdbackPolicy == HoldbackPolicy.DEFER_TO_LATER_YEARS
          ? heldBack
          : ProductionLoad.zero(scheduledSatellite.getYear(), scheduledSatellite.getPeriodName());
      deferredBacklog = deferredToNext;

      double deferredValueMusd = calculatePeriodValueMusd(heldBack);
      double deferredValueNpvMusd = discount(deferredValueMusd, index);
      String summary = buildPeriodSummary(scheduledSatellite, adjusted, heldBack);

      periods.add(new TieInPeriodResult(scheduledSatellite.getPeriodName(),
          scheduledSatellite.getYear(), baseRequest, adjusted.acceptedBase, scheduledSatellite,
          deferredIntoPeriod, satelliteRequest, adjusted.acceptedSatellite, heldBack,
          deferredToNext, adjusted.satelliteAllocationScale, allocation.bottleneckName,
          adjusted.processOutcome.processModelUsed, adjusted.processOutcome.capacityAvailable,
          adjusted.processOutcome.bottleneckName, adjusted.processOutcome.bottleneckUtilization,
          adjusted.processOutcome.utilizationSummary, deferredValueMusd, deferredValueNpvMusd,
          summary));
    }

    List<DebottleneckDecision> decisions = buildDebottleneckDecisions(periods);
    return new TieInCapacityResult(hostFacility.getName(), allocationPolicy, holdbackPolicy,
        periods, decisions, buildResultSummary(periods, decisions));
  }

  /**
   * Validates required profiles before running.
   */
  private void validateProfiles() {
    if (satelliteProductionProfile == null || satelliteProductionProfile.isEmpty()) {
      throw new IllegalStateException(
          "Satellite production profile must contain at least one period");
    }
  }

  /**
   * Gets the first year in the satellite profile.
   *
   * @return first satellite profile year
   */
  private int getFirstYear() {
    if (satelliteProductionProfile == null || satelliteProductionProfile.isEmpty()) {
      return 0;
    }
    return satelliteProductionProfile.getLoad(0).getYear();
  }

  /**
   * Gets base host production for a period.
   *
   * @param year calendar year
   * @param index zero-based profile index
   * @param periodName period name
   * @return base host production load
   */
  private ProductionLoad getBaseLoad(int year, int index, String periodName) {
    if (hostProductionProfile != null && !hostProductionProfile.isEmpty()) {
      return hostProductionProfile.getLoadByYearOrIndex(year, index, periodName);
    }
    return new ProductionLoad(periodName, year,
        hostFacility.getGasCapacityMSm3d() * hostFacility.getGasUtilization(),
        hostFacility.getOilCapacityBopd() * hostFacility.getOilUtilization(),
        hostFacility.getWaterCapacityM3d() * hostFacility.getWaterUtilization(),
        hostFacility.getLiquidCapacityM3d() * hostFacility.getLiquidUtilization());
  }

  /**
   * Allocates nameplate capacity between base and satellite production.
   *
   * @param baseRequest base host request
   * @param satelliteRequest satellite request
   * @return allocation result before process-model holdback
   */
  private AllocationResult allocateNameplate(ProductionLoad baseRequest,
      ProductionLoad satelliteRequest) {
    ProductionLoad capacity =
        getHostCapacityLoad(baseRequest.getYear(), baseRequest.getPeriodName());
    if (allocationPolicy == CapacityAllocationPolicy.PRO_RATA) {
      double scale = scaleAgainstCapacity(baseRequest.plus(satelliteRequest), capacity);
      ProductionLoad acceptedBase = baseRequest.scale(scale);
      ProductionLoad acceptedSatellite = satelliteRequest.scale(scale);
      return new AllocationResult(acceptedBase, acceptedSatellite, scale,
          identifyNameplateBottleneck(baseRequest.plus(satelliteRequest), capacity));
    }

    if (allocationPolicy == CapacityAllocationPolicy.SATELLITE_FIRST) {
      double satelliteScale = scaleAgainstCapacity(satelliteRequest, capacity);
      ProductionLoad acceptedSatellite = satelliteRequest.scale(satelliteScale);
      ProductionLoad remainingCapacity = capacity.subtractNonNegative(acceptedSatellite);
      double baseScale = scaleAgainstCapacity(baseRequest, remainingCapacity);
      return new AllocationResult(baseRequest.scale(baseScale), acceptedSatellite, satelliteScale,
          identifyNameplateBottleneck(baseRequest.plus(satelliteRequest), capacity));
    }

    if (allocationPolicy == CapacityAllocationPolicy.VALUE_WEIGHTED
        && calculateDailyValueUsd(satelliteRequest) > calculateDailyValueUsd(baseRequest)) {
      double satelliteScale = scaleAgainstCapacity(satelliteRequest, capacity);
      ProductionLoad acceptedSatellite = satelliteRequest.scale(satelliteScale);
      ProductionLoad remainingCapacity = capacity.subtractNonNegative(acceptedSatellite);
      double baseScale = scaleAgainstCapacity(baseRequest, remainingCapacity);
      return new AllocationResult(baseRequest.scale(baseScale), acceptedSatellite, satelliteScale,
          identifyNameplateBottleneck(baseRequest.plus(satelliteRequest), capacity));
    }

    double baseScale = scaleAgainstCapacity(baseRequest, capacity);
    ProductionLoad acceptedBase = baseRequest.scale(baseScale);
    ProductionLoad remainingCapacity = capacity.subtractNonNegative(acceptedBase);
    double satelliteScale = scaleAgainstCapacity(satelliteRequest, remainingCapacity);
    return new AllocationResult(acceptedBase, satelliteRequest.scale(satelliteScale),
        satelliteScale, identifyNameplateBottleneck(baseRequest.plus(satelliteRequest), capacity));
  }

  /**
   * Builds a production-load object representing host nameplate capacity.
   *
   * @param year calendar year
   * @param periodName period name
   * @return nameplate capacity load
   */
  private ProductionLoad getHostCapacityLoad(int year, String periodName) {
    return new ProductionLoad(periodName, year, hostFacility.getGasCapacityMSm3d(),
        hostFacility.getOilCapacityBopd(), hostFacility.getWaterCapacityM3d(),
        hostFacility.getLiquidCapacityM3d());
  }

  /**
   * Calculates the feasible scale against host capacity.
   *
   * @param request requested load
   * @param capacity capacity load
   * @return feasible scale between zero and one
   */
  private double scaleAgainstCapacity(ProductionLoad request, ProductionLoad capacity) {
    double scale = 1.0;
    scale = Math.min(scale,
        requiredDimensionScale(request.getGasRateMSm3d(), capacity.getGasRateMSm3d()));
    scale = Math.min(scale,
        requiredDimensionScale(request.getOilRateBopd(), capacity.getOilRateBopd()));
    if (hostFacility.getWaterCapacityM3d() > 0.0) {
      scale = Math.min(scale,
          requiredDimensionScale(request.getWaterRateM3d(), capacity.getWaterRateM3d()));
    }
    if (hostFacility.getLiquidCapacityM3d() > 0.0) {
      scale = Math.min(scale, requiredDimensionScale(request.getTotalLiquidRateM3d(),
          capacity.getTotalLiquidRateM3d()));
    }
    return clampScale(scale);
  }

  /**
   * Calculates scale for one required capacity dimension.
   *
   * @param requested requested rate
   * @param capacity available capacity rate
   * @return scale for the dimension
   */
  private double requiredDimensionScale(double requested, double capacity) {
    if (requested <= 0.0) {
      return 1.0;
    }
    if (capacity <= 0.0) {
      return 0.0;
    }
    return capacity / requested;
  }

  /**
   * Clamps a scale factor to the valid allocation range.
   *
   * @param scale raw scale
   * @return scale between zero and one
   */
  private double clampScale(double scale) {
    if (Double.isNaN(scale) || scale < 0.0) {
      return 0.0;
    }
    if (scale > 1.0) {
      return 1.0;
    }
    return scale;
  }

  /**
   * Identifies the most constrained nameplate capacity category.
   *
   * @param request combined production request
   * @param capacity host capacity
   * @return bottleneck category name
   */
  private String identifyNameplateBottleneck(ProductionLoad request, ProductionLoad capacity) {
    Map<String, Double> utilizations = new LinkedHashMap<String, Double>();
    addUtilization(utilizations, "gas capacity", request.getGasRateMSm3d(),
        capacity.getGasRateMSm3d(), true);
    addUtilization(utilizations, "oil capacity", request.getOilRateBopd(),
        capacity.getOilRateBopd(), true);
    addUtilization(utilizations, "water capacity", request.getWaterRateM3d(),
        capacity.getWaterRateM3d(), hostFacility.getWaterCapacityM3d() > 0.0);
    addUtilization(utilizations, "liquid capacity", request.getTotalLiquidRateM3d(),
        capacity.getTotalLiquidRateM3d(), hostFacility.getLiquidCapacityM3d() > 0.0);

    String bottleneck = "None";
    double highest = 0.0;
    for (Map.Entry<String, Double> entry : utilizations.entrySet()) {
      if (entry.getValue() > highest) {
        highest = entry.getValue();
        bottleneck = entry.getKey();
      }
    }
    return bottleneck;
  }

  /**
   * Adds one utilization entry when the capacity dimension is active.
   *
   * @param utilizations utilization map
   * @param name capacity category name
   * @param requested requested rate
   * @param capacity capacity rate
   * @param active true if the dimension should constrain allocation
   */
  private void addUtilization(Map<String, Double> utilizations, String name, double requested,
      double capacity, boolean active) {
    if (!active || requested <= 0.0) {
      return;
    }
    if (capacity <= 0.0) {
      utilizations.put(name, Double.POSITIVE_INFINITY);
    } else {
      utilizations.put(name, requested / capacity);
    }
  }

  /**
   * Applies process-model capacity checks and additional holdback if required.
   *
   * @param baseRequest base production request
   * @param allocation nameplate allocation result
   * @return process-adjusted allocation
   */
  private ProcessAdjustedAllocation applyProcessCapacity(ProductionLoad baseRequest,
      AllocationResult allocation) {
    ProcessOutcome initialOutcome =
        evaluateProcessCapacity(allocation.acceptedBase, allocation.acceptedSatellite);
    if (!initialOutcome.processModelUsed || initialOutcome.capacityAvailable
        || allocation.acceptedSatellite.isZero()) {
      return new ProcessAdjustedAllocation(allocation.acceptedBase, allocation.acceptedSatellite,
          allocation.satelliteScale, initialOutcome);
    }

    double low = 0.0;
    double high = 1.0;
    ProcessOutcome bestOutcome =
        evaluateProcessCapacity(allocation.acceptedBase, allocation.acceptedSatellite.scale(0.0));
    for (int iteration = 0; iteration < processSearchIterations; iteration++) {
      double mid = 0.5 * (low + high);
      ProductionLoad trialSatellite = allocation.acceptedSatellite.scale(mid);
      ProcessOutcome trialOutcome =
          evaluateProcessCapacity(allocation.acceptedBase, trialSatellite);
      if (trialOutcome.capacityAvailable) {
        low = mid;
        bestOutcome = trialOutcome;
      } else {
        high = mid;
      }
    }

    ProductionLoad processAcceptedSatellite = allocation.acceptedSatellite.scale(low);
    ProcessOutcome finalOutcome =
        low > 0.0 ? evaluateProcessCapacity(allocation.acceptedBase, processAcceptedSatellite)
            : bestOutcome;
    return new ProcessAdjustedAllocation(allocation.acceptedBase, processAcceptedSatellite,
        allocation.satelliteScale * low, finalOutcome);
  }

  /**
   * Evaluates the process model for a base-plus-satellite operating point.
   *
   * @param acceptedBase accepted base production
   * @param acceptedSatellite accepted satellite production
   * @return process outcome
   */
  private ProcessOutcome evaluateProcessCapacity(ProductionLoad acceptedBase,
      ProductionLoad acceptedSatellite) {
    ProcessSystem processSystem = hostFacility.getProcessSystem();
    if (processSystem == null || tieInPoint == null) {
      return ProcessOutcome.notUsed();
    }
    StreamInterface stream =
        processSystem.resolveStreamReference(tieInPoint.getProcessStreamReference());
    if (stream == null) {
      return ProcessOutcome.failed("Missing stream: " + tieInPoint.getProcessStreamReference());
    }

    double originalFlow = Double.NaN;
    try {
      originalFlow = stream.getFlowRate(tieInPoint.getProcessRateUnit());
      double targetFlow = calculateTargetProcessRate(originalFlow, acceptedBase, acceptedSatellite);
      stream.setFlowRate(targetFlow, tieInPoint.getProcessRateUnit());
      processSystem.run();
      BottleneckResult bottleneck = processSystem.findBottleneck();
      Map<String, Double> utilizationSummary = processSystem.getCapacityUtilizationSummary();
      double utilization = bottleneck.hasBottleneck() ? bottleneck.getUtilization() : 0.0;
      boolean capacityAvailable =
          utilization <= processUtilizationLimit && !processSystem.isAnyHardLimitExceeded();
      return new ProcessOutcome(true, capacityAvailable, bottleneck.getEquipmentName(), utilization,
          utilizationSummary);
    } catch (Exception exception) {
      Map<String, Double> utilizationSummary = new LinkedHashMap<String, Double>();
      return new ProcessOutcome(true, false, "process model error: " + exception.getMessage(),
          Double.POSITIVE_INFINITY, utilizationSummary);
    } finally {
      restoreProcessStream(processSystem, stream, originalFlow);
    }
  }

  /**
   * Calculates the target process-stream rate for a trial operating point.
   *
   * @param originalFlow original process-stream rate
   * @param acceptedBase accepted base production
   * @param acceptedSatellite accepted satellite production
   * @return target process-stream flow rate
   */
  private double calculateTargetProcessRate(double originalFlow, ProductionLoad acceptedBase,
      ProductionLoad acceptedSatellite) {
    double profileRate = tieInPoint.toProcessRate(acceptedBase.plus(acceptedSatellite));
    if (profileRate > 0.0) {
      return profileRate;
    }
    double baseRate = Double.isNaN(tieInPoint.getBaseProcessRate()) ? originalFlow
        : tieInPoint.getBaseProcessRate();
    return Math.max(0.0, baseRate + tieInPoint.toProcessRate(acceptedSatellite));
  }

  /**
   * Restores the process stream after a trial process-model run.
   *
   * @param processSystem process system containing the stream
   * @param stream stream to restore
   * @param originalFlow original flow rate in the tie-in point rate unit
   */
  private void restoreProcessStream(ProcessSystem processSystem, StreamInterface stream,
      double originalFlow) {
    if (Double.isNaN(originalFlow)) {
      return;
    }
    try {
      stream.setFlowRate(originalFlow, tieInPoint.getProcessRateUnit());
      processSystem.run();
    } catch (Exception exception) {
      // The planner result already records process-model errors from the trial run.
    }
  }

  /**
   * Calculates daily value using load-specific values or planner defaults.
   *
   * @param load production load
   * @return daily value in USD/d
   */
  private double calculateDailyValueUsd(ProductionLoad load) {
    double gasValue = load.getGasValueUsdPerMSm3() == 0.0 ? defaultGasValueUsdPerMSm3
        : load.getGasValueUsdPerMSm3();
    double oilValue =
        load.getOilValueUsdPerBbl() == 0.0 ? defaultOilValueUsdPerBbl : load.getOilValueUsdPerBbl();
    double waterValue = load.getWaterValueUsdPerM3() == 0.0 ? defaultWaterValueUsdPerM3
        : load.getWaterValueUsdPerM3();
    double liquidValue = load.getLiquidValueUsdPerM3() == 0.0 ? defaultLiquidValueUsdPerM3
        : load.getLiquidValueUsdPerM3();
    return load.getGasRateMSm3d() * gasValue + load.getOilRateBopd() * oilValue
        + load.getWaterRateM3d() * waterValue + load.getTotalLiquidRateM3d() * liquidValue;
  }

  /**
   * Calculates total period value.
   *
   * @param load production load
   * @return period value in MUSD
   */
  private double calculatePeriodValueMusd(ProductionLoad load) {
    return calculateDailyValueUsd(load) * load.getPeriodLengthDays() / 1.0e6;
  }

  /**
   * Discounts a value by period index.
   *
   * @param valueMusd value in MUSD before discounting
   * @param periodIndex zero-based period index
   * @return discounted value in MUSD
   */
  private double discount(double valueMusd, int periodIndex) {
    return valueMusd / Math.pow(1.0 + discountRate, Math.max(0, periodIndex));
  }

  /**
   * Builds debottleneck decisions from period results.
   *
   * @param periods period results
   * @return debottleneck decisions
   */
  private List<DebottleneckDecision> buildDebottleneckDecisions(List<TieInPeriodResult> periods) {
    if (periods.isEmpty()) {
      return Collections.emptyList();
    }
    double totalDeferredNpv = 0.0;
    Map<String, Integer> bottleneckCounts = new HashMap<String, Integer>();
    for (TieInPeriodResult period : periods) {
      totalDeferredNpv += period.getDeferredValueNpvMusd();
      String bottleneck = period.getPrimaryBottleneck();
      if (period.getHeldBackSatellite().isZero() || bottleneck == null) {
        continue;
      }
      Integer count = bottleneckCounts.get(bottleneck);
      bottleneckCounts.put(bottleneck, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }
    if (totalDeferredNpv <= 0.0) {
      return Collections.emptyList();
    }

    String bottleneck = chooseMostFrequentBottleneck(bottleneckCounts);
    double npv = totalDeferredNpv - defaultDebottleneckCapexMusd;
    double paybackYears = totalDeferredNpv > 0.0
        ? defaultDebottleneckCapexMusd
            / Math.max(1.0e-12, totalDeferredNpv / Math.max(1, periods.size()))
        : Double.POSITIVE_INFINITY;
    List<DebottleneckDecision> decisions = new ArrayList<DebottleneckDecision>();
    decisions.add(new DebottleneckDecision(bottleneck,
        "Increase host capacity or debottleneck " + bottleneck
            + " to recover constrained satellite production.",
        defaultDebottleneckCapexMusd, totalDeferredNpv, npv, paybackYears, npv > 0.0));
    return decisions;
  }

  /**
   * Chooses the most frequent bottleneck from constrained periods.
   *
   * @param bottleneckCounts bottleneck occurrence counts
   * @return most frequent bottleneck name
   */
  private String chooseMostFrequentBottleneck(Map<String, Integer> bottleneckCounts) {
    String selected = "host capacity";
    int selectedCount = 0;
    for (Map.Entry<String, Integer> entry : bottleneckCounts.entrySet()) {
      if (entry.getValue().intValue() > selectedCount) {
        selected = entry.getKey();
        selectedCount = entry.getValue().intValue();
      }
    }
    return selected;
  }

  /**
   * Builds a period summary string.
   *
   * @param scheduledSatellite scheduled satellite load
   * @param adjusted process-adjusted allocation
   * @param heldBack held-back satellite load
   * @return summary string
   */
  private String buildPeriodSummary(ProductionLoad scheduledSatellite,
      ProcessAdjustedAllocation adjusted, ProductionLoad heldBack) {
    return String.format(
        "%s: accepted %.2f of %.2f MSm3/d gas, held back %.2f MSm3/d, bottleneck %s",
        scheduledSatellite.getPeriodName(), adjusted.acceptedSatellite.getGasRateMSm3d(),
        scheduledSatellite.getGasRateMSm3d(), heldBack.getGasRateMSm3d(),
        adjusted.processOutcome.bottleneckName == null ? "nameplate"
            : adjusted.processOutcome.bottleneckName);
  }

  /**
   * Builds an aggregate result summary.
   *
   * @param periods period results
   * @param decisions debottleneck decisions
   * @return summary string
   */
  private String buildResultSummary(List<TieInPeriodResult> periods,
      List<DebottleneckDecision> decisions) {
    double acceptedGas = 0.0;
    double heldGas = 0.0;
    double deferredNpv = 0.0;
    for (TieInPeriodResult period : periods) {
      acceptedGas += period.getAcceptedSatellite().getGasVolumeMSm3();
      heldGas += period.getHeldBackSatellite().getGasVolumeMSm3();
      deferredNpv += period.getDeferredValueNpvMusd();
    }
    String decisionText = decisions.isEmpty() ? "no debottleneck case generated"
        : "best debottleneck NPV " + String.format("%.1f MUSD", decisions.get(0).getNpvMusd());
    return String.format(
        "Host %s accepted %.1f MSm3 gas and held back %.1f MSm3 gas; deferred value %.1f MUSD NPV; %s.",
        hostFacility.getName(), acceptedGas, heldGas, deferredNpv, decisionText);
  }

  /**
   * Nameplate allocation result.
   */
  private static final class AllocationResult {
    /** Accepted base production. */
    private final ProductionLoad acceptedBase;

    /** Accepted satellite production. */
    private final ProductionLoad acceptedSatellite;

    /** Satellite allocation scale. */
    private final double satelliteScale;

    /** Nameplate bottleneck name. */
    private final String bottleneckName;

    /**
     * Creates a nameplate allocation result.
     *
     * @param acceptedBase accepted base production
     * @param acceptedSatellite accepted satellite production
     * @param satelliteScale satellite allocation scale
     * @param bottleneckName nameplate bottleneck name
     */
    private AllocationResult(ProductionLoad acceptedBase, ProductionLoad acceptedSatellite,
        double satelliteScale, String bottleneckName) {
      this.acceptedBase = acceptedBase;
      this.acceptedSatellite = acceptedSatellite;
      this.satelliteScale = satelliteScale;
      this.bottleneckName = bottleneckName;
    }
  }

  /**
   * Process-adjusted allocation result.
   */
  private static final class ProcessAdjustedAllocation {
    /** Accepted base production after process checks. */
    private final ProductionLoad acceptedBase;

    /** Accepted satellite production after process checks. */
    private final ProductionLoad acceptedSatellite;

    /** Satellite allocation scale after process checks. */
    private final double satelliteAllocationScale;

    /** Process outcome. */
    private final ProcessOutcome processOutcome;

    /**
     * Creates a process-adjusted allocation.
     *
     * @param acceptedBase accepted base production
     * @param acceptedSatellite accepted satellite production
     * @param satelliteAllocationScale satellite allocation scale
     * @param processOutcome process-model outcome
     */
    private ProcessAdjustedAllocation(ProductionLoad acceptedBase, ProductionLoad acceptedSatellite,
        double satelliteAllocationScale, ProcessOutcome processOutcome) {
      this.acceptedBase = acceptedBase;
      this.acceptedSatellite = acceptedSatellite;
      this.satelliteAllocationScale = satelliteAllocationScale;
      this.processOutcome = processOutcome;
    }
  }

  /**
   * Process-model capacity outcome.
   */
  private static final class ProcessOutcome {
    /** True if a process model was used. */
    private final boolean processModelUsed;

    /** True if process capacity is available. */
    private final boolean capacityAvailable;

    /** Bottleneck equipment or diagnostic name. */
    private final String bottleneckName;

    /** Bottleneck utilization as a fraction. */
    private final double bottleneckUtilization;

    /** Equipment utilization summary in percent. */
    private final Map<String, Double> utilizationSummary;

    /**
     * Creates a process-model outcome.
     *
     * @param processModelUsed true if a process model was used
     * @param capacityAvailable true if process capacity is available
     * @param bottleneckName bottleneck name
     * @param bottleneckUtilization bottleneck utilization fraction
     * @param utilizationSummary utilization summary in percent
     */
    private ProcessOutcome(boolean processModelUsed, boolean capacityAvailable,
        String bottleneckName, double bottleneckUtilization,
        Map<String, Double> utilizationSummary) {
      this.processModelUsed = processModelUsed;
      this.capacityAvailable = capacityAvailable;
      this.bottleneckName = bottleneckName;
      this.bottleneckUtilization = bottleneckUtilization;
      this.utilizationSummary = new LinkedHashMap<String, Double>(utilizationSummary);
    }

    /**
     * Creates an outcome for cases where no process model is configured.
     *
     * @return process outcome marked as not used
     */
    private static ProcessOutcome notUsed() {
      return new ProcessOutcome(false, true, null, 0.0, new LinkedHashMap<String, Double>());
    }

    /**
     * Creates a failed process outcome.
     *
     * @param message diagnostic message
     * @return failed process outcome
     */
    private static ProcessOutcome failed(String message) {
      return new ProcessOutcome(true, false, message, Double.POSITIVE_INFINITY,
          new LinkedHashMap<String, Double>());
    }
  }
}
