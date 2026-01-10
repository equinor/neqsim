package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.facility.BlockConfig;
import neqsim.process.fielddevelopment.facility.BlockType;
import neqsim.process.fielddevelopment.facility.FacilityConfig;

/**
 * Energy efficiency calculator for oil and gas facilities.
 *
 * <p>
 * This class provides comprehensive energy efficiency analysis including:
 * <ul>
 * <li><b>Specific energy consumption:</b> kWh per unit of production</li>
 * <li><b>Equipment efficiency:</b> Compressor, pump, and heating efficiency</li>
 * <li><b>Heat integration:</b> Opportunities for waste heat recovery</li>
 * <li><b>Energy benchmarking:</b> Comparison with industry standards</li>
 * <li><b>Improvement recommendations:</b> Potential savings and investments</li>
 * </ul>
 *
 * <h2>Energy Efficiency Metrics</h2>
 * <table border="1">
 * <tr>
 * <th>Metric</th>
 * <th>Definition</th>
 * <th>Unit</th>
 * </tr>
 * <tr>
 * <td>SEC (Specific Energy Consumption)</td>
 * <td>Total energy / Production</td>
 * <td>kWh/boe or MJ/Sm³</td>
 * </tr>
 * <tr>
 * <td>EEI (Energy Efficiency Index)</td>
 * <td>Actual SEC / Reference SEC</td>
 * <td>Dimensionless</td>
 * </tr>
 * <tr>
 * <td>Flare intensity</td>
 * <td>Flared gas / Production</td>
 * <td>Sm³/boe</td>
 * </tr>
 * <tr>
 * <td>Compression efficiency</td>
 * <td>Isentropic work / Actual work</td>
 * <td>%</td>
 * </tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * EnergyEfficiencyCalculator calc = new EnergyEfficiencyCalculator();
 * calc.setOilProduction(10000, "bbl/day");
 * calc.setGasProduction(2.0, "MMSm3/day");
 * calc.setCompressorPower(5000, "kW");
 * calc.setCompressorEfficiency(0.75);
 * calc.setPumpPower(1000, "kW");
 * calc.setHeatingDuty(8000, "kW");
 * calc.setFlaringRate(0.05, "MMSm3/day");
 * 
 * EnergyReport report = calc.calculate();
 * 
 * System.out.println("SEC: " + report.getSpecificEnergyConsumption() + " kWh/boe");
 * System.out.println("EEI: " + report.getEnergyEfficiencyIndex());
 * System.out.println("Potential savings: " + report.getPotentialSavings() + " MW");
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see EmissionsTracker
 */
public class EnergyEfficiencyCalculator implements Serializable {
  private static final long serialVersionUID = 1000L;

  // Reference values for benchmarking
  private static final double REFERENCE_SEC_PLATFORM = 50.0; // kWh/boe (typical North Sea)
  private static final double REFERENCE_SEC_FPSO = 60.0; // kWh/boe
  private static final double REFERENCE_SEC_SUBSEA = 40.0; // kWh/boe
  private static final double REFERENCE_COMPRESSOR_EFFICIENCY = 0.80;
  private static final double REFERENCE_PUMP_EFFICIENCY = 0.75;
  private static final double REFERENCE_HEATER_EFFICIENCY = 0.85;

  // Production rates
  private double oilProductionBblPerDay = 0.0;
  private double gasProductionMSm3PerDay = 0.0;
  private double waterProductionM3PerDay = 0.0;
  private double condensateProductionBblPerDay = 0.0;

  // Power consumers
  private double compressorPowerKW = 0.0;
  private double pumpPowerKW = 0.0;
  private double rotatingEquipmentPowerKW = 0.0; // Other rotating equipment
  private double electricalLoadKW = 0.0; // HVAC, lighting, etc.
  private double heatingDutyKW = 0.0;
  private double coolingDutyKW = 0.0;

  // Equipment efficiencies
  private double compressorEfficiency = 0.75;
  private double pumpEfficiency = 0.70;
  private double heaterEfficiency = 0.85;

  // Flaring and losses
  private double flaringRateMSm3PerDay = 0.0;
  private double fuelGasRateMSm3PerDay = 0.0;
  private double ventingRateMSm3PerDay = 0.0;

  // Facility type
  private FacilityType facilityType = FacilityType.PLATFORM;

  // Heat integration parameters
  private boolean hasWasteHeatRecovery = false;
  private double wasteHeatRecoveryEfficiency = 0.0;
  private double availableWasteHeatKW = 0.0;

  // Driver type
  private DriverType primaryDriverType = DriverType.GAS_TURBINE;

  /**
   * Facility types for benchmarking.
   */
  public enum FacilityType {
    /** Fixed platform. */
    PLATFORM,
    /** Floating production. */
    FPSO,
    /** Subsea tieback. */
    SUBSEA,
    /** Onshore facility. */
    ONSHORE
  }

  /**
   * Driver types for power generation.
   */
  public enum DriverType {
    /** Gas turbine generator. */
    GAS_TURBINE,
    /** Combined cycle. */
    COMBINED_CYCLE,
    /** Power from shore. */
    POWER_FROM_SHORE,
    /** Diesel generator. */
    DIESEL
  }

  /**
   * Creates a new energy efficiency calculator.
   */
  public EnergyEfficiencyCalculator() {
    // Default constructor
  }

  /**
   * Calculates energy efficiency metrics.
   *
   * @return energy report with all metrics
   */
  public EnergyReport calculate() {
    EnergyReport report = new EnergyReport();

    // Total production in boe/day
    double totalProductionBoe = calculateTotalProductionBoe();
    report.totalProductionBoePerDay = totalProductionBoe;

    // Total power consumption
    double totalPowerKW =
        compressorPowerKW + pumpPowerKW + rotatingEquipmentPowerKW + electricalLoadKW;
    report.totalElectricPowerKW = totalPowerKW;

    // Total thermal load
    report.totalHeatingDutyKW = heatingDutyKW;
    report.totalCoolingDutyKW = coolingDutyKW;

    // Specific energy consumption (SEC)
    if (totalProductionBoe > 0) {
      report.specificEnergyConsumption = totalPowerKW * 24 / totalProductionBoe; // kWh/boe
    }

    // Energy Efficiency Index
    double referenceSEC = getReferenceSEC();
    report.referenceSEC = referenceSEC;
    if (referenceSEC > 0) {
      report.energyEfficiencyIndex = report.specificEnergyConsumption / referenceSEC;
    }

    // Power breakdown
    report.powerBreakdown.put("Compression", compressorPowerKW);
    report.powerBreakdown.put("Pumping", pumpPowerKW);
    report.powerBreakdown.put("Rotating Equipment", rotatingEquipmentPowerKW);
    report.powerBreakdown.put("Electrical Load", electricalLoadKW);

    // Fuel consumption (if gas turbine driven)
    if (primaryDriverType == DriverType.GAS_TURBINE
        || primaryDriverType == DriverType.COMBINED_CYCLE) {
      double turbineEfficiency = primaryDriverType == DriverType.COMBINED_CYCLE ? 0.50 : 0.35;
      double fuelEnergyKW = totalPowerKW / turbineEfficiency;
      // Natural gas: ~35 MJ/Sm3
      double fuelRateSm3PerHour = fuelEnergyKW * 3.6 / 35.0; // MJ/hr to Sm3/hr
      report.fuelGasConsumption = fuelRateSm3PerHour * 24 / 1000.0; // MSm3/day
    }

    // Flaring intensity
    if (totalProductionBoe > 0) {
      report.flaringIntensity = flaringRateMSm3PerDay * 1000.0 / totalProductionBoe; // Sm3/boe
    }

    // Energy losses analysis
    calculateEnergyLosses(report);

    // Heat integration opportunities
    calculateHeatIntegration(report);

    // Improvement recommendations
    generateRecommendations(report);

    return report;
  }

  /**
   * Calculates energy efficiency from a FieldConcept.
   *
   * @param concept field concept
   * @param facilityConfig facility configuration
   * @return energy report
   */
  public EnergyReport calculateFromConcept(FieldConcept concept, FacilityConfig facilityConfig) {
    // Extract production from concept
    if (concept.getWells() != null) {
      double ratePerWell = concept.getWells().getRatePerWellSm3d();
      int producers = concept.getWells().getProducerCount();
      double totalLiquid = ratePerWell * producers;

      // Assume some GOR
      double gor = 200.0; // Sm3/Sm3 default
      if (concept.getReservoir() != null && concept.getReservoir().getGor() > 0) {
        gor = concept.getReservoir().getGor();
      }

      // Convert to units
      this.oilProductionBblPerDay = totalLiquid * 6.29; // Sm3 to bbl
      this.gasProductionMSm3PerDay = totalLiquid * gor / 1e6;
    }

    // Estimate power from facility config
    if (facilityConfig != null) {
      for (BlockConfig block : facilityConfig.getBlocksOfType(BlockType.COMPRESSION)) {
        int stages = block.getIntParameter("stages", 1);
        this.compressorPowerKW += stages * 5000; // 5 MW per stage typical
      }

      if (facilityConfig.hasBlock(BlockType.TEG_DEHYDRATION)) {
        this.heatingDutyKW += 2000; // TEG reboiler
      }

      if (facilityConfig.hasBlock(BlockType.MEG_REGENERATION)) {
        this.heatingDutyKW += 5000; // MEG regeneration
      }
    }

    // Set facility type from processing location
    if (concept.getInfrastructure() != null
        && concept.getInfrastructure().getProcessingLocation() != null) {
      InfrastructureInput.ProcessingLocation location =
          concept.getInfrastructure().getProcessingLocation();
      switch (location) {
        case FPSO:
          this.facilityType = FacilityType.FPSO;
          break;
        case SUBSEA:
          this.facilityType = FacilityType.SUBSEA;
          break;
        case ONSHORE:
          this.facilityType = FacilityType.ONSHORE;
          break;
        default:
          this.facilityType = FacilityType.PLATFORM;
          break;
      }
    }

    return calculate();
  }

  /**
   * Calculates total production in boe/day.
   *
   * @return total production (boe/day)
   */
  private double calculateTotalProductionBoe() {
    // Conversions:
    // 1 Sm3 oil = 6.29 bbl
    // 1 MSm3 gas = 1000 * 0.18 = 180 boe (varies with composition)
    // 1 bbl condensate = 1 boe

    double oilBoe = oilProductionBblPerDay;
    double gasBoe = gasProductionMSm3PerDay * 180.0; // MSm3 to boe
    double condensateBoe = condensateProductionBblPerDay;

    return oilBoe + gasBoe + condensateBoe;
  }

  /**
   * Gets reference SEC for benchmarking.
   *
   * @return reference SEC (kWh/boe)
   */
  private double getReferenceSEC() {
    switch (facilityType) {
      case FPSO:
        return REFERENCE_SEC_FPSO;
      case SUBSEA:
        return REFERENCE_SEC_SUBSEA;
      case ONSHORE:
        return REFERENCE_SEC_PLATFORM * 0.8; // Onshore typically more efficient
      case PLATFORM:
      default:
        return REFERENCE_SEC_PLATFORM;
    }
  }

  /**
   * Calculates energy losses from inefficiencies.
   *
   * @param report energy report
   */
  private void calculateEnergyLosses(EnergyReport report) {
    // Compression losses
    double compressorLoss = compressorPowerKW * (1.0 - compressorEfficiency);
    report.energyLosses.put("Compressor inefficiency", compressorLoss);

    // Pump losses
    double pumpLoss = pumpPowerKW * (1.0 - pumpEfficiency);
    report.energyLosses.put("Pump inefficiency", pumpLoss);

    // Heater losses
    double heaterLoss = heatingDutyKW * (1.0 - heaterEfficiency);
    report.energyLosses.put("Heater losses", heaterLoss);

    // Flaring losses (energy content of flared gas)
    // Natural gas: ~35 MJ/Sm3 = 9.72 kWh/Sm3
    double flaringLoss = flaringRateMSm3PerDay * 1000.0 * 9.72 / 24.0; // kW
    report.energyLosses.put("Flaring", flaringLoss);

    // Total losses
    report.totalEnergyLossKW = compressorLoss + pumpLoss + heaterLoss + flaringLoss;
  }

  /**
   * Analyzes heat integration opportunities.
   *
   * @param report energy report
   */
  private void calculateHeatIntegration(EnergyReport report) {
    // Waste heat from compression (compressor outlet)
    // Typically 30-50% of compressor power as heat
    double compressionHeat = compressorPowerKW * 0.4;
    report.wasteHeatSources.put("Compressor discharge", compressionHeat);

    // Waste heat from gas turbine exhaust
    if (primaryDriverType == DriverType.GAS_TURBINE) {
      double turbineExhaust = compressorPowerKW + pumpPowerKW; // Approx equal to shaft power
      report.wasteHeatSources.put("Gas turbine exhaust", turbineExhaust);
    }

    // Cooling water return (if coolers used)
    if (coolingDutyKW > 0) {
      double coolingWasteHeat = coolingDutyKW * 0.8; // Recoverable portion
      report.wasteHeatSources.put("Cooling water return", coolingWasteHeat);
    }

    // Total available waste heat
    double totalWasteHeat = 0;
    for (Double heat : report.wasteHeatSources.values()) {
      totalWasteHeat += heat;
    }
    report.totalAvailableWasteHeatKW = totalWasteHeat;

    // Heat sinks (demand)
    report.heatSinks.put("Process heating", heatingDutyKW);
    report.heatSinks.put("Reboilers", heatingDutyKW * 0.3); // Approx
    report.heatSinks.put("Hot water", heatingDutyKW * 0.1);

    double totalHeatDemand = 0;
    for (Double heat : report.heatSinks.values()) {
      totalHeatDemand += heat;
    }
    report.totalHeatDemandKW = totalHeatDemand;

    // Potential heat recovery
    double recoverableFraction = hasWasteHeatRecovery ? wasteHeatRecoveryEfficiency : 0.5;
    report.potentialHeatRecoveryKW =
        Math.min(totalWasteHeat * recoverableFraction, totalHeatDemand);
  }

  /**
   * Generates improvement recommendations.
   *
   * @param report energy report
   */
  private void generateRecommendations(EnergyReport report) {
    // Compressor upgrade potential
    if (compressorEfficiency < REFERENCE_COMPRESSOR_EFFICIENCY) {
      double potentialSavings = compressorPowerKW
          * (REFERENCE_COMPRESSOR_EFFICIENCY - compressorEfficiency) / compressorEfficiency;
      Recommendation rec = new Recommendation();
      rec.category = "Compression";
      rec.description = "Upgrade compressors to higher efficiency";
      rec.potentialSavingsKW = potentialSavings;
      rec.estimatedCapex = potentialSavings * 1000; // $1000/kW typical
      rec.paybackYears = calculatePayback(potentialSavings, rec.estimatedCapex);
      report.recommendations.add(rec);
    }

    // Pump upgrade potential
    if (pumpEfficiency < REFERENCE_PUMP_EFFICIENCY) {
      double potentialSavings =
          pumpPowerKW * (REFERENCE_PUMP_EFFICIENCY - pumpEfficiency) / pumpEfficiency;
      Recommendation rec = new Recommendation();
      rec.category = "Pumping";
      rec.description = "Upgrade pumps to higher efficiency";
      rec.potentialSavingsKW = potentialSavings;
      rec.estimatedCapex = potentialSavings * 500;
      rec.paybackYears = calculatePayback(potentialSavings, rec.estimatedCapex);
      report.recommendations.add(rec);
    }

    // Waste heat recovery
    if (!hasWasteHeatRecovery && report.totalAvailableWasteHeatKW > 1000) {
      double potentialRecovery = report.totalAvailableWasteHeatKW * 0.4;
      Recommendation rec = new Recommendation();
      rec.category = "Heat Integration";
      rec.description = "Install waste heat recovery system";
      rec.potentialSavingsKW = potentialRecovery;
      rec.estimatedCapex = potentialRecovery * 800;
      rec.paybackYears = calculatePayback(potentialRecovery, rec.estimatedCapex);
      report.recommendations.add(rec);
    }

    // Flare reduction
    if (flaringRateMSm3PerDay > 0.01) {
      double flareEnergy = flaringRateMSm3PerDay * 1000 * 9.72 / 24; // kW
      Recommendation rec = new Recommendation();
      rec.category = "Flare Reduction";
      rec.description = "Install flare gas recovery or mini-LNG";
      rec.potentialSavingsKW = flareEnergy * 0.8; // 80% recoverable
      rec.estimatedCapex = 5e6; // $5M typical for FGR
      rec.paybackYears = calculatePayback(rec.potentialSavingsKW, rec.estimatedCapex);
      report.recommendations.add(rec);
    }

    // Combined cycle conversion
    if (primaryDriverType == DriverType.GAS_TURBINE && compressorPowerKW > 10000) {
      double efficiencyGain = (0.50 - 0.35) / 0.35; // ~43% fuel savings
      double fuelSavings = (compressorPowerKW + pumpPowerKW) * efficiencyGain * 0.35 / 0.50;
      Recommendation rec = new Recommendation();
      rec.category = "Power Generation";
      rec.description = "Convert to combined cycle power generation";
      rec.potentialSavingsKW = fuelSavings;
      rec.estimatedCapex = compressorPowerKW * 400; // $400/kW for CCGT
      rec.paybackYears = calculatePayback(fuelSavings, rec.estimatedCapex);
      report.recommendations.add(rec);
    }

    // Calculate total savings potential
    double totalSavings = 0;
    for (Recommendation rec : report.recommendations) {
      totalSavings += rec.potentialSavingsKW;
    }
    report.totalPotentialSavingsKW = totalSavings;
  }

  /**
   * Calculates simple payback period.
   *
   * @param savingsKW annual energy savings (kW)
   * @param capex capital expenditure (USD)
   * @return payback years
   */
  private double calculatePayback(double savingsKW, double capex) {
    // Assume $0.10/kWh equivalent value
    double annualSavings = savingsKW * 8760 * 0.10;
    return annualSavings > 0 ? capex / annualSavings : Double.MAX_VALUE;
  }

  // ===================== Setters =====================

  /**
   * Sets oil production rate.
   *
   * @param rate rate value
   * @param unit unit ("bbl/day", "Sm3/day")
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setOilProduction(double rate, String unit) {
    if ("Sm3/day".equalsIgnoreCase(unit)) {
      this.oilProductionBblPerDay = rate * 6.29;
    } else {
      this.oilProductionBblPerDay = rate;
    }
    return this;
  }

  /**
   * Sets gas production rate.
   *
   * @param rate rate value
   * @param unit unit ("MMSm3/day", "MSm3/day")
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setGasProduction(double rate, String unit) {
    if ("MMSm3/day".equalsIgnoreCase(unit)) {
      this.gasProductionMSm3PerDay = rate * 1000.0;
    } else {
      this.gasProductionMSm3PerDay = rate;
    }
    return this;
  }

  /**
   * Sets compressor power.
   *
   * @param power power value
   * @param unit unit ("kW", "MW")
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setCompressorPower(double power, String unit) {
    if ("MW".equalsIgnoreCase(unit)) {
      this.compressorPowerKW = power * 1000.0;
    } else {
      this.compressorPowerKW = power;
    }
    return this;
  }

  /**
   * Sets compressor efficiency.
   *
   * @param efficiency efficiency (0-1)
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setCompressorEfficiency(double efficiency) {
    this.compressorEfficiency = Math.max(0.1, Math.min(efficiency, 1.0));
    return this;
  }

  /**
   * Sets pump power.
   *
   * @param power power value
   * @param unit unit ("kW", "MW")
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setPumpPower(double power, String unit) {
    if ("MW".equalsIgnoreCase(unit)) {
      this.pumpPowerKW = power * 1000.0;
    } else {
      this.pumpPowerKW = power;
    }
    return this;
  }

  /**
   * Sets pump efficiency.
   *
   * @param efficiency efficiency (0-1)
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setPumpEfficiency(double efficiency) {
    this.pumpEfficiency = Math.max(0.1, Math.min(efficiency, 1.0));
    return this;
  }

  /**
   * Sets heating duty.
   *
   * @param duty duty value
   * @param unit unit ("kW", "MW")
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setHeatingDuty(double duty, String unit) {
    if ("MW".equalsIgnoreCase(unit)) {
      this.heatingDutyKW = duty * 1000.0;
    } else {
      this.heatingDutyKW = duty;
    }
    return this;
  }

  /**
   * Sets flaring rate.
   *
   * @param rate rate value
   * @param unit unit ("MMSm3/day", "MSm3/day")
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setFlaringRate(double rate, String unit) {
    if ("MMSm3/day".equalsIgnoreCase(unit)) {
      this.flaringRateMSm3PerDay = rate * 1000.0;
    } else {
      this.flaringRateMSm3PerDay = rate;
    }
    return this;
  }

  /**
   * Sets facility type.
   *
   * @param facilityType facility type
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setFacilityType(FacilityType facilityType) {
    this.facilityType = facilityType;
    return this;
  }

  /**
   * Sets primary driver type.
   *
   * @param driverType driver type
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setDriverType(DriverType driverType) {
    this.primaryDriverType = driverType;
    return this;
  }

  /**
   * Sets whether waste heat recovery is installed.
   *
   * @param hasRecovery true if WHR installed
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setHasWasteHeatRecovery(boolean hasRecovery) {
    this.hasWasteHeatRecovery = hasRecovery;
    return this;
  }

  /**
   * Sets cooling duty.
   *
   * @param duty duty value (kW)
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setCoolingDuty(double duty) {
    this.coolingDutyKW = duty;
    return this;
  }

  /**
   * Sets electrical load (auxiliary).
   *
   * @param load load value (kW)
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setElectricalLoad(double load) {
    this.electricalLoadKW = load;
    return this;
  }

  /**
   * Sets heater efficiency.
   *
   * @param efficiency efficiency (0-1)
   * @return this for chaining
   */
  public EnergyEfficiencyCalculator setHeaterEfficiency(double efficiency) {
    this.heaterEfficiency = Math.max(0.1, Math.min(efficiency, 1.0));
    return this;
  }

  // ===================== Result Classes =====================

  /**
   * Energy efficiency report.
   */
  public static class EnergyReport implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Total production (boe/day). */
    public double totalProductionBoePerDay;

    /** Total electric power (kW). */
    public double totalElectricPowerKW;

    /** Total heating duty (kW). */
    public double totalHeatingDutyKW;

    /** Total cooling duty (kW). */
    public double totalCoolingDutyKW;

    /** Specific energy consumption (kWh/boe). */
    public double specificEnergyConsumption;

    /** Reference SEC for benchmarking (kWh/boe). */
    public double referenceSEC;

    /** Energy efficiency index (actual/reference). */
    public double energyEfficiencyIndex;

    /** Flaring intensity (Sm3/boe). */
    public double flaringIntensity;

    /** Fuel gas consumption (MSm3/day). */
    public double fuelGasConsumption;

    /** Total energy loss (kW). */
    public double totalEnergyLossKW;

    /** Total available waste heat (kW). */
    public double totalAvailableWasteHeatKW;

    /** Total heat demand (kW). */
    public double totalHeatDemandKW;

    /** Potential heat recovery (kW). */
    public double potentialHeatRecoveryKW;

    /** Total potential savings (kW). */
    public double totalPotentialSavingsKW;

    /** Power breakdown by category. */
    public Map<String, Double> powerBreakdown = new LinkedHashMap<>();

    /** Energy losses by source. */
    public Map<String, Double> energyLosses = new LinkedHashMap<>();

    /** Waste heat sources. */
    public Map<String, Double> wasteHeatSources = new LinkedHashMap<>();

    /** Heat sinks. */
    public Map<String, Double> heatSinks = new LinkedHashMap<>();

    /** Improvement recommendations. */
    public java.util.List<Recommendation> recommendations = new java.util.ArrayList<>();

    /**
     * Gets specific energy consumption.
     *
     * @return SEC (kWh/boe)
     */
    public double getSpecificEnergyConsumption() {
      return specificEnergyConsumption;
    }

    /**
     * Gets energy efficiency index.
     *
     * @return EEI
     */
    public double getEnergyEfficiencyIndex() {
      return energyEfficiencyIndex;
    }

    /**
     * Gets potential savings.
     *
     * @return savings (kW)
     */
    public double getPotentialSavings() {
      return totalPotentialSavingsKW;
    }

    /**
     * Gets efficiency rating.
     *
     * @return rating (A, B, C, D, E, F)
     */
    public String getEfficiencyRating() {
      if (energyEfficiencyIndex < 0.7) {
        return "A";
      } else if (energyEfficiencyIndex < 0.85) {
        return "B";
      } else if (energyEfficiencyIndex < 1.0) {
        return "C";
      } else if (energyEfficiencyIndex < 1.15) {
        return "D";
      } else if (energyEfficiencyIndex < 1.3) {
        return "E";
      } else {
        return "F";
      }
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Energy Efficiency Report\n");
      sb.append("========================\n");
      sb.append(String.format("Production: %.0f boe/day%n", totalProductionBoePerDay));
      sb.append(String.format("Total Power: %.0f kW (%.1f MW)%n", totalElectricPowerKW,
          totalElectricPowerKW / 1000));
      sb.append(String.format("SEC: %.1f kWh/boe (Reference: %.1f)%n", specificEnergyConsumption,
          referenceSEC));
      sb.append(
          String.format("EEI: %.2f (Rating: %s)%n", energyEfficiencyIndex, getEfficiencyRating()));
      sb.append(String.format("Flaring Intensity: %.2f Sm3/boe%n", flaringIntensity));
      sb.append(String.format("Energy Losses: %.0f kW%n", totalEnergyLossKW));
      sb.append(String.format("Potential Savings: %.0f kW%n", totalPotentialSavingsKW));

      if (!recommendations.isEmpty()) {
        sb.append("\nRecommendations:\n");
        for (Recommendation rec : recommendations) {
          sb.append(String.format("  - %s: %.0f kW savings, %.1f year payback%n", rec.description,
              rec.potentialSavingsKW, rec.paybackYears));
        }
      }

      return sb.toString();
    }
  }

  /**
   * Energy improvement recommendation.
   */
  public static class Recommendation implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Category. */
    public String category;

    /** Description. */
    public String description;

    /** Potential savings (kW). */
    public double potentialSavingsKW;

    /** Estimated CAPEX (USD). */
    public double estimatedCapex;

    /** Simple payback (years). */
    public double paybackYears;
  }
}
