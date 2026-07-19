package neqsim.process.fielddevelopment.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.CashFlowResult;
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
          energyMWh, emissionsTonnes);

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
        lifecycleEnergy, lifecycleCo2, stopReason);
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
    model.getProcessSystem().run();

    synchronizeInjectionStream(model, injectionRate);
    double stabilizedOilRate = stockTankOilRate(model.getStabilizedOilExport());
    double gasExportRate = nonNegativeFlow(model.getGasExport(), "Sm3/day");
    double powerKw = Math.max(0.0, model.getProcessSystem().getPower("kW"));
    return new ProcessRates(stabilizedOilRate, gasExportRate, injectionRate, waterRateSm3PerDay * rateScale, powerKw);
  }

  private double runProcessWithRateFallback(FieldLifecycleModel model, FieldLifecycleConfiguration config,
      double requestedOilRate, double requestedWaterRate) {
    double scale = 1.0;
    RuntimeException lastFailure = null;
    for (int attempt = 0; attempt < 6; attempt++) {
      try {
        setReservoirProductionRates(model, config, requestedOilRate * scale, requestedWaterRate * scale);
        model.getProcessSystem().run();
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

    private AnnualAccumulator(int year) {
      this.year = year;
    }

    private void add(double days, double oil, double gasExport, double gasInjected, double water, double waterCut,
        double pressure, double energy, double emissions) {
      calendarDays += days;
      oilSm3 += oil;
      gasExportSm3 += gasExport;
      gasInjectedSm3 += gasInjected;
      waterSm3 += water;
      waterCutDays += waterCut * days;
      pressureDays += pressure * days;
      energyMWh += energy;
      emissionsTonnes += emissions;
    }

    private FieldLifecycleResult.AnnualResult toResult() {
      double days = Math.max(1.0e-12, calendarDays);
      return new FieldLifecycleResult.AnnualResult(year, oilSm3, gasExportSm3, gasInjectedSm3, waterSm3, oilSm3 / days,
          waterCutDays / days, pressureDays / days, energyMWh, emissionsTonnes);
    }
  }
}
