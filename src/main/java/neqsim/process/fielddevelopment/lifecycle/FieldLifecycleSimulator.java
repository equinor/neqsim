package neqsim.process.fielddevelopment.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.CashFlowResult;
import neqsim.process.fielddevelopment.lifecycle.FacilityCapacityAllocator.AllocationResult;
import neqsim.process.fielddevelopment.tieback.capacity.CapacityAllocationPolicy;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Time-marching reservoir-to-market simulator for field-development evaluation.
 *
 * <p>
 * Each time step solves the live NeqSim process at the constrained well rate, allocates recovered gas between export
 * and injection, updates reservoir material balance through {@code SimpleReservoir.runTransient}, and records annual
 * production, power and emissions. The resulting profiles are passed directly to the Norwegian-capable
 * {@link CashFlowEngine} for NPV, IRR, payback and break-even calculations.
 * </p>
 *
 * <p>
 * This is a tank/material-balance concept model. It is suitable for screening and concept selection; full-field grid
 * effects such as sweep, breakthrough and well interference should be supplied by an external reservoir simulator at
 * higher fidelity.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class FieldLifecycleSimulator {
  private static final Logger logger = LogManager.getLogger(FieldLifecycleSimulator.class);
  private static final double DAYS_PER_YEAR = 365.25;
  private static final double SECONDS_PER_DAY = 86400.0;
  private static final double BBL_PER_SM3 = 6.28981077;

  /** Runs an executable field concept over its configured lifetime. */
  public FieldLifecycleResult run(FieldLifecycleConcept concept) {
    return run(concept.getModel(), concept.getConfiguration());
  }

  /**
   * Runs an assembled field model over its configured lifetime.
   *
   * @param model assembled NeqSim model
   * @param config lifecycle assumptions
   * @return integrated lifecycle result
   */
  public FieldLifecycleResult run(FieldLifecycleModel model, FieldLifecycleConfiguration config) {
    if (config.getFacilityLifecycleStrategy() != null) {
      return runFacilityLifecycle(model, config, config.getFacilityLifecycleStrategy());
    }
    double initialPressure = model.getReservoir().getReservoirFluid().getPressure("bara");
    Map<Integer, AnnualAccumulator> annual = new LinkedHashMap<Integer, AnnualAccumulator>();
    double elapsedDays = 0.0;
    double horizonDays = config.getProjectYears() * DAYS_PER_YEAR;
    String stopReason = "project horizon reached";

    while (elapsedDays < horizonDays - 1.0e-9) {
      double dtDays = Math.min(config.getTimeStepDays(), horizonDays - elapsedDays);
      double fieldAgeYears = elapsedDays / DAYS_PER_YEAR;
      int calendarYear = config.getStartYear() + (int) Math.floor(fieldAgeYears + 1.0e-9);
      double reservoirPressure = model.getReservoir().getReservoirFluid().getPressure("bara");
      double onlineOilRate = calculateConstrainedOilRate(config, fieldAgeYears, reservoirPressure);
      if (onlineOilRate * config.getAvailability() < config.getEconomicLimitOilRateSm3PerDay()) {
        stopReason = reservoirPressure <= config.getMinimumBottomHolePressureBara()
            ? "minimum bottom-hole pressure reached"
            : "economic oil-rate limit reached";
        break;
      }

      double waterCut = config.getWaterCut(fieldAgeYears);
      double onlineWaterRate = onlineOilRate * waterCut / Math.max(1.0e-9, 1.0 - waterCut);
      setReservoirProductionRates(model, config, onlineOilRate, onlineWaterRate);

      ProcessRates rates = solveProcessAndAllocateGas(model, config, fieldAgeYears, onlineOilRate, onlineWaterRate);
      ProductSpecificationResult productQuality = evaluateProductQuality(model, config);
      double operatingDays = dtDays * config.getAvailability();
      double oilVolume = rates.oilRateSm3PerDay * operatingDays;
      double gasExportVolume = rates.gasExportRateSm3PerDay * operatingDays;
      double gasInjectionVolume = rates.gasInjectionRateSm3PerDay * operatingDays;
      double waterVolume = rates.waterRateSm3PerDay * operatingDays;
      double energyMWh = rates.powerKw * 24.0 * operatingDays / 1000.0;
      double emissionsTonnes = rates.powerKw * 24.0 * operatingDays * config.getGridEmissionFactorKgPerKWh() / 1000.0;

      AnnualAccumulator accumulator = annual.get(calendarYear);
      if (accumulator == null) {
        accumulator = new AnnualAccumulator(calendarYear);
        annual.put(calendarYear, accumulator);
      }
      accumulator.add(dtDays, oilVolume, gasExportVolume, gasInjectionVolume, waterVolume, waterCut, reservoirPressure,
          energyMWh, emissionsTonnes, onlineOilRate, onlineOilRate, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
          "legacy facility limits", 0.0, "legacy facility limits", productQuality);

      model.getReservoir().runTransient(operatingDays * SECONDS_PER_DAY);
      elapsedDays += dtDays;
    }

    List<FieldLifecycleResult.AnnualResult> annualResults = toAnnualResults(annual);
    CashFlowEngine economics = createEconomics(config, annualResults);
    CashFlowResult cashFlow = economics.calculate(config.getDiscountRate());
    double breakevenOil = economics.calculateBreakevenOilPrice(config.getDiscountRate());
    double breakevenGas = economics.calculateBreakevenGasPrice(config.getDiscountRate());

    double cumulativeOil = 0.0;
    double cumulativeGasExport = 0.0;
    double cumulativeGasInjected = 0.0;
    double cumulativeWater = 0.0;
    double lifecycleEnergy = 0.0;
    double lifecycleCo2 = 0.0;
    for (FieldLifecycleResult.AnnualResult result : annualResults) {
      cumulativeOil += result.getOilSm3();
      cumulativeGasExport += result.getGasExportSm3();
      cumulativeGasInjected += result.getGasInjectedSm3();
      cumulativeWater += result.getWaterProducedSm3();
      lifecycleEnergy += result.getEnergyMWh();
      lifecycleCo2 += result.getCo2EmissionsTonnes();
    }

    double finalPressure = model.getReservoir().getReservoirFluid().getPressure("bara");
    return new FieldLifecycleResult(model.getName(), annualResults, cashFlow, breakevenOil, breakevenGas,
        initialPressure, finalPressure, cumulativeOil, cumulativeGasExport, cumulativeGasInjected, cumulativeWater,
        lifecycleEnergy, lifecycleCo2, stopReason, null, 0.0, 0.0);
  }

  private FieldLifecycleResult runFacilityLifecycle(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      FacilityLifecycleStrategy strategy) {
    if (strategy.getDevelopmentMode() == FacilityLifecycleStrategy.DevelopmentMode.BROWNFIELD_TIEBACK
        && !model.hasHostProductionFeeds()) {
      throw new IllegalArgumentException("brownfield tieback model must expose host oil, gas and water feed streams");
    }

    double initialPressure = model.getReservoir().getReservoirFluid().getPressure("bara");
    FacilityDesignResult facilityDesign = prepareFacilityDesign(model, config, strategy);
    FacilityCapacityAllocator allocator = new FacilityCapacityAllocator();
    Map<Integer, AnnualAccumulator> annual = new LinkedHashMap<Integer, AnnualAccumulator>();
    double elapsedDays = 0.0;
    double horizonDays = config.getProjectYears() * DAYS_PER_YEAR;
    String stopReason = "project horizon reached";

    while (elapsedDays < horizonDays - 1.0e-9) {
      double dtDays = Math.min(config.getTimeStepDays(), horizonDays - elapsedDays);
      double fieldAgeYears = elapsedDays / DAYS_PER_YEAR;
      int calendarYear = config.getStartYear() + (int) Math.floor(fieldAgeYears + 1.0e-9);
      double reservoirPressure = model.getReservoir().getReservoirFluid().getPressure("bara");
      FacilityProductionRate suppliedPotential = getSuppliedPotential(model, config, fieldAgeYears, reservoirPressure);
      double potentialOilRate = suppliedPotential.getOilSm3PerDay();
      if (potentialOilRate * config.getAvailability() < config.getEconomicLimitOilRateSm3PerDay()) {
        stopReason = reservoirPressure <= config.getMinimumBottomHolePressureBara()
            ? "minimum bottom-hole pressure reached"
            : "economic oil-rate limit reached";
        break;
      }

      double potentialWaterRate = suppliedPotential.getWaterSm3PerDay();
      double waterCut = potentialWaterRate / Math.max(1.0e-9, potentialOilRate + potentialWaterRate);
      PotentialRates potential = evaluateSatellitePotential(model, config, potentialOilRate, potentialWaterRate);
      if (potential.oilRateSm3PerDay * config.getAvailability() < config.getEconomicLimitOilRateSm3PerDay()) {
        stopReason = "wells/SURF hydraulic pressure limit reached";
        break;
      }
      FacilityProductionRate satellitePotential = new FacilityProductionRate(potential.oilRateSm3PerDay,
          suppliedPotential.getGasSm3PerDay() > 0.0 ? suppliedPotential.getGasSm3PerDay() : potential.gasRateSm3PerDay,
          potential.waterRateSm3PerDay);
      AllocationResult allocation = allocator.allocate(strategy, calendarYear, satellitePotential);
      FacilityOperatingResult operation = operateSharedFacility(model, config, strategy, fieldAgeYears, allocation,
          facilityDesign, potential.oilRecoveryFactor);
      ProductSpecificationResult productQuality = evaluateProductQuality(model, config);

      double operatingDays = dtDays * config.getAvailability();
      double oilVolume = operation.satelliteOilExportRateSm3PerDay * operatingDays;
      double gasExportVolume = operation.satelliteGasExportRateSm3PerDay * operatingDays;
      double gasInjectionVolume = operation.gasInjectionRateSm3PerDay * operatingDays;
      double waterVolume = operation.satelliteAllocated.getWaterSm3PerDay() * operatingDays;
      double energyMWh = operation.powerKw * 24.0 * operatingDays / 1000.0;
      double emissionsTonnes = operation.powerKw * 24.0 * operatingDays * config.getGridEmissionFactorKgPerKWh()
          / 1000.0;
      double holdbackOil = Math.max(0.0,
          allocation.getSatellitePotential().getOilSm3PerDay() - allocation.getSatelliteRequested().getOilSm3PerDay())
          * operatingDays;
      double capacityDeferredOil = Math.max(0.0,
          allocation.getSatelliteRequested().getOilSm3PerDay() - operation.satelliteAllocated.getOilSm3PerDay())
          * operatingDays;

      AnnualAccumulator accumulator = annual.get(calendarYear);
      if (accumulator == null) {
        accumulator = new AnnualAccumulator(calendarYear);
        annual.put(calendarYear, accumulator);
      }
      accumulator.add(dtDays, oilVolume, gasExportVolume, gasInjectionVolume, waterVolume, waterCut, reservoirPressure,
          energyMWh, emissionsTonnes, allocation.getSatellitePotential().getOilSm3PerDay(),
          allocation.getSatelliteRequested().getOilSm3PerDay(), operation.hostAllocated.getOilSm3PerDay(),
          operation.hostAllocated.getGasSm3PerDay(), operation.hostAllocated.getWaterSm3PerDay(), holdbackOil,
          capacityDeferredOil, operation.maximumUtilization, operation.primaryBottleneck,
          operation.unconstrainedUtilization, operation.unconstrainedBottleneck, productQuality);

      setReservoirProductionRates(model, config, operation.satelliteAllocated.getOilSm3PerDay(),
          operation.satelliteAllocated.getWaterSm3PerDay());
      synchronizeInjectionStream(model, operation.gasInjectionRateSm3PerDay);
      model.getReservoir().runTransient(operatingDays * SECONDS_PER_DAY);
      elapsedDays += dtDays;
    }

    List<FieldLifecycleResult.AnnualResult> annualResults = toAnnualResults(annual);
    CashFlowEngine economics = createEconomics(config, annualResults);
    CashFlowResult cashFlow = economics.calculate(config.getDiscountRate());
    double cumulativeOil = 0.0;
    double cumulativeGasExport = 0.0;
    double cumulativeGasInjected = 0.0;
    double cumulativeWater = 0.0;
    double lifecycleEnergy = 0.0;
    double lifecycleCo2 = 0.0;
    double cumulativeDeferredOil = 0.0;
    double peakFacilityUtilization = 0.0;
    for (FieldLifecycleResult.AnnualResult result : annualResults) {
      cumulativeOil += result.getOilSm3();
      cumulativeGasExport += result.getGasExportSm3();
      cumulativeGasInjected += result.getGasInjectedSm3();
      cumulativeWater += result.getWaterProducedSm3();
      lifecycleEnergy += result.getEnergyMWh();
      lifecycleCo2 += result.getCo2EmissionsTonnes();
      cumulativeDeferredOil += result.getHoldbackOilSm3() + result.getCapacityDeferredOilSm3();
      peakFacilityUtilization = Math.max(peakFacilityUtilization, result.getMaximumFacilityUtilization());
    }

    double breakevenOil = economics.calculateBreakevenOilPrice(config.getDiscountRate());
    double breakevenGas = economics.calculateBreakevenGasPrice(config.getDiscountRate());
    double finalPressure = model.getReservoir().getReservoirFluid().getPressure("bara");
    return new FieldLifecycleResult(model.getName(), annualResults, cashFlow, breakevenOil, breakevenGas,
        initialPressure, finalPressure, cumulativeOil, cumulativeGasExport, cumulativeGasInjected, cumulativeWater,
        lifecycleEnergy, lifecycleCo2, stopReason, facilityDesign, cumulativeDeferredOil, peakFacilityUtilization);
  }

  private double calculateConstrainedOilRate(FieldLifecycleConfiguration config, double fieldAgeYears,
      double reservoirPressureBara) {
    double drawdown = Math.max(0.0, reservoirPressureBara - config.getMinimumBottomHolePressureBara());
    double wellDeliverability = config.getProducerCount() * config.getProductivityIndexSm3PerDayBarPerWell() * drawdown;
    double waterCut = config.getWaterCut(fieldAgeYears);
    double rate = Math.min(config.getPlateauOilRateSm3PerDay(), wellDeliverability);
    rate = Math.min(rate, config.getMaximumLiquidRateSm3PerDay() * (1.0 - waterCut));
    if (waterCut > 1.0e-9) {
      rate = Math.min(rate, config.getMaximumWaterRateSm3PerDay() * (1.0 - waterCut) / waterCut);
    }
    return Math.max(0.0, rate);
  }

  private double calculateWellPotentialOilRate(FieldLifecycleConfiguration config, double reservoirPressureBara) {
    double drawdown = Math.max(0.0, reservoirPressureBara - config.getMinimumBottomHolePressureBara());
    double wellDeliverability = config.getProducerCount() * config.getProductivityIndexSm3PerDayBarPerWell() * drawdown;
    return Math.max(0.0, Math.min(config.getPlateauOilRateSm3PerDay(), wellDeliverability));
  }

  private FacilityProductionRate getSuppliedPotential(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      double fieldAgeYears, double reservoirPressureBara) {
    if (model.getProductionPotentialProvider() != null) {
      FacilityProductionRate potential = model.getProductionPotentialProvider().getPotential(model, config,
          fieldAgeYears, reservoirPressureBara);
      if (potential == null) {
        throw new IllegalStateException("production potential provider returned null");
      }
      return potential;
    }
    double oilRate = calculateWellPotentialOilRate(config, reservoirPressureBara);
    double waterCut = config.getWaterCut(fieldAgeYears);
    double waterRate = oilRate * waterCut / Math.max(1.0e-9, 1.0 - waterCut);
    return new FacilityProductionRate(oilRate, 0.0, waterRate);
  }

  private FacilityDesignResult prepareFacilityDesign(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      FacilityLifecycleStrategy strategy) {
    FacilityProductionRate designRates;
    if (strategy.getDevelopmentMode() == FacilityLifecycleStrategy.DevelopmentMode.GREENFIELD) {
      designRates = strategy.getDesignRates();
      setHostProductionRates(model, FacilityProductionRate.zero());
      setReservoirProductionRates(model, config, designRates.getOilSm3PerDay(), designRates.getWaterSm3PerDay());
    } else {
      designRates = finiteRates(strategy.getBaseCapacity());
      FacilityProductionRate hostDesignRates = strategy.getHostProduction(config.getStartYear());
      double satelliteOilDesignRate = Math.max(5000.0,
          finiteCapacity(strategy.getBaseCapacity().getOilSm3PerDay()) - hostDesignRates.getOilSm3PerDay());
      double satelliteWaterDesignRate = satelliteOilDesignRate * config.getInitialWaterCut()
          / Math.max(1.0e-9, 1.0 - config.getInitialWaterCut());
      setReservoirProductionRates(model, config, satelliteOilDesignRate, satelliteWaterDesignRate);
      setHostProductionRates(model, hostDesignRates);
    }

    configureGasAllocation(model, config, 0.0);
    runProcess(model);
    double designPowerKw = Math.max(0.0, getProcessPower(model, "kW"));
    int autoSizedCount = 0;
    int constraintCount = 0;
    if (strategy.isAutoSizeDetailedProcess()) {
      List<CompressorOperatingMode> compressorOperatingModes = captureCompressorOperatingModes(model);
      try {
        autoSizedCount = autoSizeProcess(model, strategy.getDesignMargin());
      } finally {
        restoreCompressorOperatingModes(compressorOperatingModes);
      }
      if (strategy.isUseDetailedProcessConstraints()) {
        constraintCount = applyMechanicalDesignCapacityConstraints(model);
        runProcess(model);
      }
    } else if (strategy.isUseDetailedProcessConstraints()) {
      constraintCount = applyMechanicalDesignCapacityConstraints(model);
      runProcess(model);
    }

    FacilityCapacity configured = strategy.getBaseCapacity();
    double powerCapacity = configured.getPowerKw();
    if (Double.isInfinite(powerCapacity) && designPowerKw > 0.0) {
      powerCapacity = designPowerKw * strategy.getDesignMargin();
    }
    FacilityCapacity effectiveCapacity = new FacilityCapacity(configured.getOilSm3PerDay(),
        configured.getGasSm3PerDay(), configured.getWaterSm3PerDay(), configured.getLiquidSm3PerDay(), powerCapacity);
    ProcessEquipmentInterface bottleneck = strategy.isUseDetailedProcessConstraints() ? getProcessBottleneck(model)
        : null;
    double designBottleneckUtilization = strategy.isUseDetailedProcessConstraints()
        ? getProcessBottleneckUtilization(model)
        : 0.0;
    double detailedCapacityMultiplier = strategy.isAutoSizeDetailedProcess()
        ? Math.max(1.0, designBottleneckUtilization / strategy.getMaximumDetailedProcessUtilization())
        : 1.0;
    int requiredParallelTrainCount = (int) Math.ceil(detailedCapacityMultiplier - 1.0e-12);
    return new FacilityDesignResult(strategy.getName(), strategy.getDevelopmentMode(), designRates, effectiveCapacity,
        strategy.getDesignMargin(), designPowerKw, autoSizedCount, constraintCount,
        getProcessBottleneckName(model, bottleneck), designBottleneckUtilization, detailedCapacityMultiplier,
        requiredParallelTrainCount);
  }

  private FacilityProductionRate finiteRates(FacilityCapacity capacity) {
    return new FacilityProductionRate(finiteCapacity(capacity.getOilSm3PerDay()),
        finiteCapacity(capacity.getGasSm3PerDay()), finiteCapacity(capacity.getWaterSm3PerDay()));
  }

  private double finiteCapacity(double capacity) {
    return Double.isFinite(capacity) ? capacity : 0.0;
  }

  private PotentialRates evaluateSatellitePotential(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      double requestedOilRate, double requestedWaterRate) {
    setHostProductionRates(model, FacilityProductionRate.zero());
    model.getGasAllocationSplitter().setSplitFactors(new double[] { 1.0, 0.0 });
    double scale = 1.0;
    RuntimeException lastFailure = null;
    for (int attempt = 0; attempt < 7; attempt++) {
      try {
        setReservoirProductionRates(model, config, requestedOilRate * scale, requestedWaterRate * scale);
        runProcess(model);
        double oilFeedRate = requestedOilRate * scale;
        double oilRecoveryFactor = oilFeedRate <= 1.0e-12 ? 0.0
            : stockTankOilRate(model.getStabilizedOilExport()) / oilFeedRate;
        return new PotentialRates(requestedOilRate * scale, nonNegativeFlow(model.getRecoveredGas(), "Sm3/day"),
            requestedWaterRate * scale, oilRecoveryFactor);
      } catch (RuntimeException ex) {
        lastFailure = ex;
        scale *= 0.70;
        logger.warn("Wells/SURF solve failed for {} at {}% potential; retrying", model.getName(),
            Math.round(scale * 100.0));
      }
    }
    logger.warn("Wells/SURF model for {} reached its hydraulic pressure limit: {}", model.getName(),
        lastFailure == null ? "unknown solve failure" : lastFailure.getMessage());
    return new PotentialRates(0.0, 0.0, 0.0, 0.0);
  }

  private FacilityOperatingResult operateSharedFacility(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      FacilityLifecycleStrategy strategy, double fieldAgeYears, AllocationResult allocation,
      FacilityDesignResult facilityDesign, double satelliteOilRecoveryFactor) {
    FacilityCapacity designCapacity = facilityDesign.getNameplateCapacity();
    FacilityProductionRate satellite = allocation.getSatelliteAllocated();
    FacilityProductionRate host = allocation.getHostAllocated();
    FacilityOperatingResult lastResult = null;
    RuntimeException lastFailure = null;
    double unconstrainedUtilization = allocation.getRequestedMaximumUtilization();
    String unconstrainedBottleneck = allocation.getRequestedPrimaryBottleneck();
    boolean detailedRequestedStateCaptured = false;

    for (int attempt = 0; attempt < 10; attempt++) {
      try {
        setReservoirProductionRates(model, config, satellite.getOilSm3PerDay(), satellite.getWaterSm3PerDay());
        setHostProductionRates(model, host);
        configureGasAllocation(model, config, fieldAgeYears);
        runProcess(model);

        double recoveredGasRate = nonNegativeFlow(model.getRecoveredGas(), "Sm3/day");
        double injectionFraction = fieldAgeYears >= config.getGasInjectionStartYear()
            ? config.getProducedGasRecycleFraction()
            : 0.0;
        double injectionRate = Math.min(recoveredGasRate * injectionFraction,
            config.getMaximumGasInjectionRateSm3PerDay());
        double actualInjectionFraction = recoveredGasRate > 0.0 ? injectionRate / recoveredGasRate : 0.0;
        if (Math.abs(actualInjectionFraction - injectionFraction) > 1.0e-9) {
          model.getGasAllocationSplitter()
              .setSplitFactors(new double[] { 1.0 - actualInjectionFraction, actualInjectionFraction });
          runProcess(model);
        }

        double totalOilExport = stockTankOilRate(model.getStabilizedOilExport());
        double totalGasExport = nonNegativeFlow(model.getGasExport(), "Sm3/day");
        double satelliteGasShare = share(satellite.getGasSm3PerDay(), host.getGasSm3PerDay());
        double powerKw = Math.max(0.0, getProcessPower(model, "kW"));

        FacilityProductionRate totalAllocated = satellite.plus(host);
        FacilityCapacity annualCapacity = allocation.getCapacity();
        double maximumUtilization = annualCapacity.getMaximumUtilization(totalAllocated);
        String primaryBottleneck = annualCapacity.getPrimaryConstraint(totalAllocated);
        double powerUtilization = Double.isInfinite(designCapacity.getPowerKw()) ? 0.0
            : powerKw / designCapacity.getPowerKw();
        if (powerUtilization > maximumUtilization) {
          maximumUtilization = powerUtilization;
          primaryBottleneck = "facility power capacity";
        }
        if (strategy.isUseDetailedProcessConstraints()) {
          double detailedUtilization = getProcessBottleneckUtilization(model)
              / facilityDesign.getDetailedCapacityMultiplier();
          ProcessEquipmentInterface bottleneck = getProcessBottleneck(model);
          if (detailedUtilization > maximumUtilization) {
            maximumUtilization = detailedUtilization;
            primaryBottleneck = bottleneck == null ? "detailed process equipment"
                : getProcessBottleneckName(model, bottleneck);
          }
        }

        if (!detailedRequestedStateCaptured) {
          if (maximumUtilization > unconstrainedUtilization) {
            unconstrainedUtilization = maximumUtilization;
            unconstrainedBottleneck = primaryBottleneck;
          }
          detailedRequestedStateCaptured = true;
        }

        double attributedSatelliteOil = Math.min(totalOilExport,
            satellite.getOilSm3PerDay() * satelliteOilRecoveryFactor);
        lastResult = new FacilityOperatingResult(satellite, host, attributedSatelliteOil,
            totalGasExport * satelliteGasShare, injectionRate, powerKw, maximumUtilization, primaryBottleneck,
            unconstrainedUtilization, unconstrainedBottleneck);
        if (maximumUtilization <= strategy.getMaximumDetailedProcessUtilization() + 1.0e-6) {
          return lastResult;
        }
        double reduction = Math.max(0.20,
            Math.min(0.98, strategy.getMaximumDetailedProcessUtilization() / maximumUtilization * 0.98));
        FacilityProductionRate[] reduced = reduceForPolicy(strategy.getAllocationPolicy(), satellite, host, reduction);
        satellite = reduced[0];
        host = reduced[1];
      } catch (RuntimeException ex) {
        lastFailure = ex;
        FacilityProductionRate[] reduced = reduceForPolicy(strategy.getAllocationPolicy(), satellite, host, 0.70);
        satellite = reduced[0];
        host = reduced[1];
      }
    }
    if (lastResult != null) {
      logger.warn("Facility {} remains above target utilization after rate allocation", strategy.getName());
      return lastResult;
    }
    throw new IllegalStateException("Could not solve shared facility after rate reduction", lastFailure);
  }

  private FacilityProductionRate[] reduceForPolicy(CapacityAllocationPolicy policy, FacilityProductionRate satellite,
      FacilityProductionRate host, double factor) {
    if (policy == CapacityAllocationPolicy.PRO_RATA) {
      return new FacilityProductionRate[] { satellite.scale(factor), host.scale(factor) };
    }
    if (policy == CapacityAllocationPolicy.SATELLITE_FIRST) {
      return throughput(host) > 1.0e-9 ? new FacilityProductionRate[] { satellite, host.scale(factor) }
          : new FacilityProductionRate[] { satellite.scale(factor), host };
    }
    return throughput(satellite) > 1.0e-9 ? new FacilityProductionRate[] { satellite.scale(factor), host }
        : new FacilityProductionRate[] { satellite, host.scale(factor) };
  }

  private double throughput(FacilityProductionRate rates) {
    return rates.getLiquidSm3PerDay() + rates.getGasSm3PerDay();
  }

  private double share(double satellite, double host) {
    double total = satellite + host;
    return total <= 1.0e-12 ? 0.0 : satellite / total;
  }

  private void configureGasAllocation(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      double fieldAgeYears) {
    double injectionFraction = fieldAgeYears >= config.getGasInjectionStartYear()
        ? config.getProducedGasRecycleFraction()
        : 0.0;
    model.getGasAllocationSplitter().setSplitFactors(new double[] { 1.0 - injectionFraction, injectionFraction });
  }

  private void setHostProductionRates(FieldLifecycleModel model, FacilityProductionRate rates) {
    if (!model.hasHostProductionFeeds()) {
      return;
    }
    setStandardRate(model.getHostOilFeed(), rates.getOilSm3PerDay());
    setStandardRate(model.getHostGasFeed(), rates.getGasSm3PerDay());
    setStandardRate(model.getHostWaterFeed(), rates.getWaterSm3PerDay());
  }

  private void setStandardRate(StreamInterface stream, double rateSm3PerDay) {
    if (rateSm3PerDay <= 0.0) {
      stream.setFlowRate(1.0e-12, "kg/sec");
    } else {
      stream.setFlowRate(rateSm3PerDay, "Sm3/day");
    }
  }

  private void setReservoirProductionRates(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      double oilRateSm3PerDay, double waterRateSm3PerDay) {
    double oilKgPerSecond = oilRateSm3PerDay * config.getOilDensityKgPerSm3() / SECONDS_PER_DAY;
    double waterKgPerSecond = waterRateSm3PerDay * config.getWaterDensityKgPerSm3() / SECONDS_PER_DAY;
    model.getReservoirOilProducer().setFlowRate(oilKgPerSecond, "kg/sec");
    model.getReservoirWaterProducer().setFlowRate(waterKgPerSecond, "kg/sec");
  }

  private ProcessRates solveProcessAndAllocateGas(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      double fieldAgeYears, double oilRateSm3PerDay, double waterRateSm3PerDay) {
    double injectionFraction = fieldAgeYears >= config.getGasInjectionStartYear()
        ? config.getProducedGasRecycleFraction()
        : 0.0;
    model.getGasAllocationSplitter().setSplitFactors(new double[] { 1.0 - injectionFraction, injectionFraction });
    double rateScale = runProcessWithRateFallback(model, config, oilRateSm3PerDay, waterRateSm3PerDay);

    double recoveredGasRate = nonNegativeFlow(model.getRecoveredGas(), "Sm3/day");
    double injectionRate = Math.min(recoveredGasRate * injectionFraction, config.getMaximumGasInjectionRateSm3PerDay());
    double actualInjectionFraction = recoveredGasRate > 0.0 ? injectionRate / recoveredGasRate : 0.0;
    model.getGasAllocationSplitter()
        .setSplitFactors(new double[] { 1.0 - actualInjectionFraction, actualInjectionFraction });
    runProcess(model);

    synchronizeInjectionStream(model, injectionRate);
    double stabilizedOilRate = stockTankOilRate(model.getStabilizedOilExport());
    double gasExportRate = nonNegativeFlow(model.getGasExport(), "Sm3/day");
    double powerKw = Math.max(0.0, getProcessPower(model, "kW"));
    return new ProcessRates(stabilizedOilRate, gasExportRate, injectionRate, waterRateSm3PerDay * rateScale, powerKw);
  }

  private double runProcessWithRateFallback(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      double requestedOilRate, double requestedWaterRate) {
    double scale = 1.0;
    RuntimeException lastFailure = null;
    for (int attempt = 0; attempt < 6; attempt++) {
      try {
        setReservoirProductionRates(model, config, requestedOilRate * scale, requestedWaterRate * scale);
        runProcess(model);
        double gasRate = nonNegativeFlow(model.getRecoveredGas(), "Sm3/day");
        if (gasRate > config.getMaximumGasRateSm3PerDay() && gasRate > 0.0) {
          scale *= config.getMaximumGasRateSm3PerDay() / gasRate;
          continue;
        }
        return scale;
      } catch (RuntimeException ex) {
        lastFailure = ex;
        scale *= 0.70;
        logger.warn("Process solve failed for {} at {}% rate; retrying at lower rate", model.getName(),
            Math.round(scale * 100.0));
      }
    }
    throw new IllegalStateException("Could not solve lifecycle process after rate reduction", lastFailure);
  }

  private void runProcess(FieldLifecycleModel model) {
    if (model.hasProcessModel()) {
      boolean optimizedExecution = model.getProcessModel().isUseOptimizedExecution();
      model.getProcessModel().setUseOptimizedExecution(false);
      try {
        model.getProcessModel().run();
      } finally {
        model.getProcessModel().setUseOptimizedExecution(optimizedExecution);
      }
    } else {
      boolean optimizedExecution = model.getProcessSystem().isUseOptimizedExecution();
      model.getProcessSystem().setUseOptimizedExecution(false);
      try {
        model.getProcessSystem().run();
      } finally {
        model.getProcessSystem().setUseOptimizedExecution(optimizedExecution);
      }
    }
    if (hasInvalidCompressionPower(model)) {
      throw new IllegalStateException("Process solve for " + model.getName() + " produced invalid compressor work");
    }
  }

  private boolean hasInvalidCompressionPower(FieldLifecycleModel model) {
    if (model.hasProcessModel()) {
      for (String areaName : model.getProcessModel().getProcessSystemNames()) {
        if (hasInvalidCompressionPower(model.getProcessModel().get(areaName))) {
          return true;
        }
      }
      return false;
    }
    return hasInvalidCompressionPower(model.getProcessSystem());
  }

  private boolean hasInvalidCompressionPower(ProcessSystem processSystem) {
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (!(equipment instanceof Compressor)) {
        continue;
      }
      Compressor compressor = (Compressor) equipment;
      StreamInterface inlet = compressor.getInletStream();
      if (inlet == null || compressor.getOutletStream() == null || inlet.getFlowRate("kg/sec") <= 1.0e-9
          || compressor.getOutletStream().getPressure("bara") <= inlet.getPressure("bara") + 1.0e-9) {
        continue;
      }
      try {
        double powerKw = compressor.getPower("kW");
        if (!Double.isFinite(powerKw) || powerKw <= 0.0) {
          return true;
        }
      } catch (RuntimeException ex) {
        return true;
      }
    }
    return false;
  }

  private double getProcessPower(FieldLifecycleModel model, String unit) {
    return model.hasProcessModel() ? model.getProcessModel().getPower(unit) : model.getProcessSystem().getPower(unit);
  }

  private int autoSizeProcess(FieldLifecycleModel model, double designMargin) {
    return model.hasProcessModel() ? model.getProcessModel().autoSizeEquipment(designMargin)
        : model.getProcessSystem().autoSizeEquipment(designMargin);
  }

  private List<CompressorOperatingMode> captureCompressorOperatingModes(FieldLifecycleModel model) {
    List<CompressorOperatingMode> operatingModes = new ArrayList<CompressorOperatingMode>();
    if (model.hasProcessModel()) {
      for (String areaName : model.getProcessModel().getProcessSystemNames()) {
        captureCompressorOperatingModes(model.getProcessModel().get(areaName), operatingModes);
      }
    } else {
      captureCompressorOperatingModes(model.getProcessSystem(), operatingModes);
    }
    return operatingModes;
  }

  private void captureCompressorOperatingModes(ProcessSystem processSystem,
      List<CompressorOperatingMode> operatingModes) {
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment instanceof Compressor) {
        operatingModes.add(new CompressorOperatingMode((Compressor) equipment));
      }
    }
  }

  private void restoreCompressorOperatingModes(List<CompressorOperatingMode> operatingModes) {
    for (CompressorOperatingMode operatingMode : operatingModes) {
      operatingMode.restore();
    }
  }

  private int applyMechanicalDesignCapacityConstraints(FieldLifecycleModel model) {
    return model.hasProcessModel() ? model.getProcessModel().applyMechanicalDesignCapacityConstraints()
        : model.getProcessSystem().applyMechanicalDesignCapacityConstraints();
  }

  private ProcessEquipmentInterface getProcessBottleneck(FieldLifecycleModel model) {
    return model.hasProcessModel() ? model.getProcessModel().getBottleneck() : model.getProcessSystem().getBottleneck();
  }

  private double getProcessBottleneckUtilization(FieldLifecycleModel model) {
    return model.hasProcessModel() ? model.getProcessModel().getBottleneckUtilization()
        : model.getProcessSystem().getBottleneckUtilization();
  }

  private String getProcessBottleneckName(FieldLifecycleModel model, ProcessEquipmentInterface bottleneck) {
    if (bottleneck == null) {
      return null;
    }
    if (model.hasProcessModel()) {
      for (String areaName : model.getProcessModel().getProcessSystemNames()) {
        ProcessSystem area = model.getProcessModel().get(areaName);
        for (ProcessEquipmentInterface equipment : area.getUnitOperations()) {
          if (equipment == bottleneck) {
            return areaName + "::" + bottleneck.getName();
          }
        }
      }
    }
    return bottleneck.getName();
  }

  private void synchronizeInjectionStream(FieldLifecycleModel model, double injectionRateSm3PerDay) {
    if (injectionRateSm3PerDay <= 0.0) {
      model.getReservoirGasInjector().setFlowRate(1.0e-12, "kg/sec");
      return;
    }
    SystemInterface injectionFluid = model.getCompressedInjectionGas().getFluid().clone();
    model.getReservoirGasInjector().setFluid(injectionFluid);
    model.getReservoirGasInjector().setFlowRate(injectionRateSm3PerDay, "Sm3/day");
  }

  private double nonNegativeFlow(StreamInterface stream, String unit) {
    double value = stream.getFlowRate(unit);
    return Double.isNaN(value) ? 0.0 : Math.max(0.0, value);
  }

  private ProductSpecificationResult evaluateProductQuality(FieldLifecycleModel model,
      FieldLifecycleConfiguration config) {
    FieldProductSpecifications specifications = config.getProductSpecifications();
    if (specifications == null || !specifications.hasActiveLimits()) {
      return ProductSpecificationResult.notEvaluated();
    }
    if (model.getProductQualityProvider() != null) {
      ProductSpecificationResult result = model.getProductQualityProvider().evaluate(model, specifications);
      if (result == null) {
        throw new IllegalStateException("product quality provider returned null");
      }
      return result;
    }
    return new ProductSpecificationEvaluator().evaluate(model, specifications);
  }

  private double stockTankOilRate(StreamInterface stream) {
    SystemInterface stockTankFluid = stream.getFluid().clone();
    stockTankFluid.setTemperature(15.0, "C");
    stockTankFluid.setPressure(1.01325, "bara");
    new ThermodynamicOperations(stockTankFluid).TPflash();
    stockTankFluid.initPhysicalProperties();
    if (!stockTankFluid.hasPhaseType("oil")) {
      return 0.0;
    }
    return Math.max(0.0, stockTankFluid.getPhase("oil").getFlowRate("m3/sec") * SECONDS_PER_DAY);
  }

  private List<FieldLifecycleResult.AnnualResult> toAnnualResults(Map<Integer, AnnualAccumulator> annual) {
    List<FieldLifecycleResult.AnnualResult> results = new ArrayList<FieldLifecycleResult.AnnualResult>();
    for (AnnualAccumulator accumulator : annual.values()) {
      results.add(accumulator.toResult());
    }
    return results;
  }

  private CashFlowEngine createEconomics(FieldLifecycleConfiguration config,
      List<FieldLifecycleResult.AnnualResult> annualResults) {
    CashFlowEngine economics = new CashFlowEngine("NO");
    economics.setOilPrice(config.getOilPriceUsdPerBbl());
    economics.setGasPrice(config.getGasPriceUsdPerSm3());
    economics.setOilTariff(config.getOilTariffUsdPerBbl());
    economics.setGasTariff(config.getGasTariffUsdPerSm3());
    economics.setFixedOpexPerYear(config.getFixedOpexMusdPerYear());
    economics.setFixedOpexStartYear(config.getStartYear());
    economics.setVariableOpexPerBoe(config.getVariableOpexUsdPerBoe());
    economics.setOpexPercentOfCapex(0.0);
    for (Map.Entry<Integer, Double> capex : config.getCapexScheduleMusd().entrySet()) {
      economics.addCapex(capex.getValue(), capex.getKey());
    }
    for (FieldLifecycleResult.AnnualResult result : annualResults) {
      economics.addAnnualProduction(result.getYear(), result.getOilSm3() * BBL_PER_SM3, result.getGasExportSm3(), 0.0);
    }
    return economics;
  }

  private static final class ProcessRates {
    private final double oilRateSm3PerDay;
    private final double gasExportRateSm3PerDay;
    private final double gasInjectionRateSm3PerDay;
    private final double waterRateSm3PerDay;
    private final double powerKw;

    private ProcessRates(double oilRateSm3PerDay, double gasExportRateSm3PerDay, double gasInjectionRateSm3PerDay,
        double waterRateSm3PerDay, double powerKw) {
      this.oilRateSm3PerDay = oilRateSm3PerDay;
      this.gasExportRateSm3PerDay = gasExportRateSm3PerDay;
      this.gasInjectionRateSm3PerDay = gasInjectionRateSm3PerDay;
      this.waterRateSm3PerDay = waterRateSm3PerDay;
      this.powerKw = powerKw;
    }
  }

  /**
   * Compressor settings that control the thermodynamic operating solve rather than the retained mechanical design.
   */
  private static final class CompressorOperatingMode {
    private final Compressor compressor;
    private final CompressorChartInterface compressorChart;
    private final boolean useCompressorChart;
    private final boolean solveSpeed;
    private final boolean limitSpeed;

    private CompressorOperatingMode(Compressor compressor) {
      this.compressor = compressor;
      this.compressorChart = compressor.getCompressorChart();
      this.useCompressorChart = compressorChart != null && compressorChart.isUseCompressorChart();
      this.solveSpeed = compressor.isSolveSpeed();
      this.limitSpeed = compressor.isLimitSpeed();
    }

    private void restore() {
      if (compressorChart != null) {
        compressor.setCompressorChart(compressorChart);
        compressorChart.setUseCompressorChart(useCompressorChart);
      }
      compressor.setSolveSpeed(solveSpeed);
      compressor.setLimitSpeed(limitSpeed);
    }
  }

  private static final class PotentialRates {
    private final double oilRateSm3PerDay;
    private final double gasRateSm3PerDay;
    private final double waterRateSm3PerDay;
    private final double oilRecoveryFactor;

    private PotentialRates(double oilRateSm3PerDay, double gasRateSm3PerDay, double waterRateSm3PerDay,
        double oilRecoveryFactor) {
      this.oilRateSm3PerDay = oilRateSm3PerDay;
      this.gasRateSm3PerDay = gasRateSm3PerDay;
      this.waterRateSm3PerDay = waterRateSm3PerDay;
      this.oilRecoveryFactor = oilRecoveryFactor;
    }
  }

  private static final class FacilityOperatingResult {
    private final FacilityProductionRate satelliteAllocated;
    private final FacilityProductionRate hostAllocated;
    private final double satelliteOilExportRateSm3PerDay;
    private final double satelliteGasExportRateSm3PerDay;
    private final double gasInjectionRateSm3PerDay;
    private final double powerKw;
    private final double maximumUtilization;
    private final String primaryBottleneck;
    private final double unconstrainedUtilization;
    private final String unconstrainedBottleneck;

    private FacilityOperatingResult(FacilityProductionRate satelliteAllocated, FacilityProductionRate hostAllocated,
        double satelliteOilExportRateSm3PerDay, double satelliteGasExportRateSm3PerDay,
        double gasInjectionRateSm3PerDay, double powerKw, double maximumUtilization, String primaryBottleneck,
        double unconstrainedUtilization, String unconstrainedBottleneck) {
      this.satelliteAllocated = satelliteAllocated;
      this.hostAllocated = hostAllocated;
      this.satelliteOilExportRateSm3PerDay = satelliteOilExportRateSm3PerDay;
      this.satelliteGasExportRateSm3PerDay = satelliteGasExportRateSm3PerDay;
      this.gasInjectionRateSm3PerDay = gasInjectionRateSm3PerDay;
      this.powerKw = powerKw;
      this.maximumUtilization = maximumUtilization;
      this.primaryBottleneck = primaryBottleneck;
      this.unconstrainedUtilization = unconstrainedUtilization;
      this.unconstrainedBottleneck = unconstrainedBottleneck;
    }
  }

  private static final class AnnualAccumulator {
    private final int year;
    private double calendarDays;
    private double oilSm3;
    private double gasExportSm3;
    private double gasInjectedSm3;
    private double waterSm3;
    private double waterCutDays;
    private double pressureDays;
    private double energyMWh;
    private double emissionsTonnes;
    private double potentialOilRateDays;
    private double requestedOilRateDays;
    private double hostOilRateDays;
    private double hostGasRateDays;
    private double hostWaterRateDays;
    private double holdbackOilSm3;
    private double capacityDeferredOilSm3;
    private double maximumFacilityUtilization;
    private String primaryBottleneck;
    private double unconstrainedFacilityUtilization;
    private String unconstrainedBottleneck;
    private ProductSpecificationResult productSpecificationResult = ProductSpecificationResult.notEvaluated();

    private AnnualAccumulator(int year) {
      this.year = year;
    }

    private void add(double days, double oil, double gasExport, double gasInjected, double water, double waterCut,
        double pressure, double energy, double emissions, double potentialOilRate, double requestedOilRate,
        double hostOilRate, double hostGasRate, double hostWaterRate, double holdbackOil, double capacityDeferredOil,
        double facilityUtilization, String bottleneck, double requestedFacilityUtilization, String requestedBottleneck,
        ProductSpecificationResult productQuality) {
      calendarDays += days;
      oilSm3 += oil;
      gasExportSm3 += gasExport;
      gasInjectedSm3 += gasInjected;
      waterSm3 += water;
      waterCutDays += waterCut * days;
      pressureDays += pressure * days;
      energyMWh += energy;
      emissionsTonnes += emissions;
      potentialOilRateDays += potentialOilRate * days;
      requestedOilRateDays += requestedOilRate * days;
      hostOilRateDays += hostOilRate * days;
      hostGasRateDays += hostGasRate * days;
      hostWaterRateDays += hostWaterRate * days;
      holdbackOilSm3 += holdbackOil;
      capacityDeferredOilSm3 += capacityDeferredOil;
      if (facilityUtilization >= maximumFacilityUtilization) {
        maximumFacilityUtilization = facilityUtilization;
        primaryBottleneck = bottleneck;
      }
      if (requestedFacilityUtilization >= unconstrainedFacilityUtilization) {
        unconstrainedFacilityUtilization = requestedFacilityUtilization;
        unconstrainedBottleneck = requestedBottleneck;
      }
      productSpecificationResult = productSpecificationResult.combine(productQuality);
    }

    private FieldLifecycleResult.AnnualResult toResult() {
      double days = Math.max(1.0e-12, calendarDays);
      return new FieldLifecycleResult.AnnualResult(year, oilSm3, gasExportSm3, gasInjectedSm3, waterSm3, oilSm3 / days,
          waterCutDays / days, pressureDays / days, energyMWh, emissionsTonnes, potentialOilRateDays / days,
          requestedOilRateDays / days, hostOilRateDays / days, hostGasRateDays / days, hostWaterRateDays / days,
          holdbackOilSm3, capacityDeferredOilSm3, maximumFacilityUtilization, primaryBottleneck,
          unconstrainedFacilityUtilization, unconstrainedBottleneck, productSpecificationResult);
    }
  }
}
