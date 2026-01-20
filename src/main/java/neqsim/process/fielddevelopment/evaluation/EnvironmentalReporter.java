package neqsim.process.fielddevelopment.evaluation;

import java.io.Serializable;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Environmental KPI reporter for field development.
 *
 * <p>
 * Calculates and reports environmental metrics aligned with Norwegian Continental Shelf (NCS)
 * regulatory requirements and industry best practices.
 * </p>
 *
 * <h2>Key Metrics</h2>
 * <ul>
 * <li><b>CO2 emissions</b> - Direct and indirect emissions in tonnes/year</li>
 * <li><b>CO2 intensity</b> - kg CO2 per barrel oil equivalent (boe)</li>
 * <li><b>Oil-in-water discharge</b> - Annual oil discharged in tonnes</li>
 * <li><b>Flaring</b> - Gas flared in MSm³/year</li>
 * <li><b>Energy efficiency</b> - Power consumption metrics</li>
 * </ul>
 *
 * <h2>NCS Regulatory Framework</h2>
 * <ul>
 * <li>CO2 tax: ~1000 NOK/tonne (2024)</li>
 * <li>OIW limit: 30 mg/L monthly average</li>
 * <li>Zero routine flaring target</li>
 * <li>Electrification incentives</li>
 * </ul>
 *
 * <h2>Industry Benchmarks</h2>
 * <ul>
 * <li>World average CO2 intensity: ~17 kg CO2/boe</li>
 * <li>NCS average: ~8 kg CO2/boe</li>
 * <li>Best performers: &lt;5 kg CO2/boe</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class EnvironmentalReporter implements Serializable {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // CONSTANTS - EMISSION FACTORS
  // ============================================================================

  /** CO2 emission factor for gas turbine (kg CO2 / kWh). */
  public static final double CO2_GAS_TURBINE = 0.55;

  /** CO2 emission factor for diesel generator (kg CO2 / kWh). */
  public static final double CO2_DIESEL = 0.70;

  /** CO2 emission factor for combined cycle (kg CO2 / kWh). */
  public static final double CO2_COMBINED_CYCLE = 0.40;

  /** CO2 emission factor for power from shore (Norway hydro - kg CO2 / kWh). */
  public static final double CO2_POWER_FROM_SHORE = 0.02;

  /** CO2 emission from flaring (kg CO2 / Sm³ gas). */
  public static final double CO2_FLARE_PER_SM3 = 2.5;

  /** NCS OIW discharge limit (mg/L). */
  public static final double NCS_OIW_LIMIT = 30.0;

  /** Conversion: barrel to Sm³. */
  public static final double BBL_TO_SM3 = 0.159;

  // ============================================================================
  // INSTANCE VARIABLES
  // ============================================================================

  /** Power supply type. */
  private PowerSupplyType powerSupplyType = PowerSupplyType.GAS_TURBINE;

  /** Annual operating hours. */
  private double operatingHoursPerYear = 8000.0;

  /** Annual oil production (Sm³/year). */
  private double annualOilProductionSm3 = 0.0;

  /** Annual gas production (Sm³/year). */
  private double annualGasProductionSm3 = 0.0;

  /** Annual water production (m³/year). */
  private double annualWaterProductionM3 = 0.0;

  // ============================================================================
  // ENUMS
  // ============================================================================

  /**
   * Power supply type for emission calculations.
   */
  public enum PowerSupplyType {
    /** Gas turbine (offshore standard). */
    GAS_TURBINE(CO2_GAS_TURBINE),
    /** Diesel generator (backup/remote). */
    DIESEL(CO2_DIESEL),
    /** Combined cycle gas turbine. */
    COMBINED_CYCLE(CO2_COMBINED_CYCLE),
    /** Power from shore (electrification). */
    POWER_FROM_SHORE(CO2_POWER_FROM_SHORE);

    private final double emissionFactor;

    PowerSupplyType(double factor) {
      this.emissionFactor = factor;
    }

    /**
     * Gets emission factor in kg CO2/kWh.
     *
     * @return emission factor
     */
    public double getEmissionFactor() {
      return emissionFactor;
    }
  }

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates an environmental reporter with default settings.
   */
  public EnvironmentalReporter() {}

  /**
   * Creates an environmental reporter for a power supply type.
   *
   * @param powerType power supply type
   */
  public EnvironmentalReporter(PowerSupplyType powerType) {
    this.powerSupplyType = powerType;
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  /**
   * Sets the power supply type.
   *
   * @param type power supply type
   * @return this for chaining
   */
  public EnvironmentalReporter setPowerSupplyType(PowerSupplyType type) {
    this.powerSupplyType = type;
    return this;
  }

  /**
   * Sets annual operating hours.
   *
   * @param hours operating hours per year
   * @return this for chaining
   */
  public EnvironmentalReporter setOperatingHours(double hours) {
    this.operatingHoursPerYear = hours;
    return this;
  }

  /**
   * Sets annual production volumes.
   *
   * @param oilSm3 oil production (Sm³/year)
   * @param gasSm3 gas production (Sm³/year)
   * @param waterM3 water production (m³/year)
   * @return this for chaining
   */
  public EnvironmentalReporter setProduction(double oilSm3, double gasSm3, double waterM3) {
    this.annualOilProductionSm3 = oilSm3;
    this.annualGasProductionSm3 = gasSm3;
    this.annualWaterProductionM3 = waterM3;
    return this;
  }

  // ============================================================================
  // FACILITY-LEVEL CALCULATIONS
  // ============================================================================

  /**
   * Calculates total power consumption from a process system.
   *
   * <p>
   * Scans the process for power-consuming equipment (compressors, pumps, heaters).
   * </p>
   *
   * @param facility process system
   * @return total power consumption in kW
   */
  public double getTotalPowerConsumption(ProcessSystem facility) {
    double totalPower = 0.0;

    for (ProcessEquipmentInterface equip : facility.getUnitOperations()) {
      if (equip instanceof Compressor) {
        Compressor comp = (Compressor) equip;
        totalPower += Math.abs(comp.getPower("kW"));
      } else if (equip instanceof Pump) {
        Pump pump = (Pump) equip;
        totalPower += Math.abs(pump.getPower("kW"));
      } else if (equip instanceof Heater) {
        Heater heater = (Heater) equip;
        // Only count fired heaters, not exchangers
        double duty = heater.getDuty();
        if (duty > 0) {
          totalPower += duty / 1000.0; // Convert W to kW
        }
      }
    }

    return totalPower;
  }

  /**
   * Calculates CO2 emissions from power consumption.
   *
   * @param powerKW power consumption in kW
   * @return CO2 emissions in tonnes/year
   */
  public double getCO2FromPower(double powerKW) {
    double kgCO2PerHour = powerKW * powerSupplyType.getEmissionFactor();
    return kgCO2PerHour * operatingHoursPerYear / 1000.0; // tonnes/year
  }

  /**
   * Calculates CO2 emissions from flaring.
   *
   * @param facility process system containing flare(s)
   * @return CO2 from flaring in tonnes/year
   */
  public double getCO2FromFlaring(ProcessSystem facility) {
    double totalCO2 = 0.0;

    for (ProcessEquipmentInterface equip : facility.getUnitOperations()) {
      if (equip instanceof Flare) {
        Flare flare = (Flare) equip;
        // Use NeqSim's built-in CO2 calculation
        double co2KgH = flare.getCO2Emission("kg/hr");
        totalCO2 += co2KgH * operatingHoursPerYear / 1000.0; // tonnes/year
      }
    }

    return totalCO2;
  }

  /**
   * Calculates CO2 intensity in kg CO2/boe.
   *
   * @param annualCO2Tonnes total annual CO2 emissions (tonnes)
   * @return CO2 intensity (kg CO2/boe)
   */
  public double getCO2Intensity(double annualCO2Tonnes) {
    // Convert production to boe
    double oilBoe = annualOilProductionSm3 / BBL_TO_SM3;
    double gasBoe = annualGasProductionSm3 / 1000.0 / BBL_TO_SM3; // Assume 1000 Sm³ gas = 1 Sm³ oe
    double totalBoe = oilBoe + gasBoe;

    if (totalBoe <= 0) {
      return 0.0;
    }

    return annualCO2Tonnes * 1000.0 / totalBoe; // kg CO2/boe
  }

  /**
   * Calculates oil-in-water discharge from treatment train.
   *
   * @param treatment water treatment train
   * @return annual oil discharge in tonnes
   */
  public double getOilDischarge(ProducedWaterTreatmentTrain treatment) {
    return treatment.getAnnualOilDischargeTonnes(operatingHoursPerYear);
  }

  // ============================================================================
  // COMPREHENSIVE REPORT
  // ============================================================================

  /**
   * Generates a comprehensive environmental report.
   *
   * @param facility process system to analyze
   * @return environmental report
   */
  public EnvironmentalReport generateReport(ProcessSystem facility) {
    EnvironmentalReport report = new EnvironmentalReport();

    // Power analysis
    report.totalPowerKW = getTotalPowerConsumption(facility);
    report.powerSupplyType = powerSupplyType;

    // CO2 emissions
    report.co2FromPowerTonnesYear = getCO2FromPower(report.totalPowerKW);
    report.co2FromFlaringTonnesYear = getCO2FromFlaring(facility);
    report.totalCO2TonnesYear = report.co2FromPowerTonnesYear + report.co2FromFlaringTonnesYear;

    // Intensity
    report.co2IntensityKgBoe = getCO2Intensity(report.totalCO2TonnesYear);

    // Production
    report.oilProductionSm3Year = annualOilProductionSm3;
    report.gasProductionSm3Year = annualGasProductionSm3;
    report.waterProductionM3Year = annualWaterProductionM3;

    // Compliance
    report.co2TaxNOK = report.totalCO2TonnesYear * 1000.0; // ~1000 NOK/tonne
    report.isLowEmitter = report.co2IntensityKgBoe < 10.0;

    return report;
  }

  /**
   * Generates a comprehensive environmental report including water treatment.
   *
   * @param facility process system
   * @param treatment water treatment train
   * @return environmental report
   */
  public EnvironmentalReport generateReport(ProcessSystem facility,
      ProducedWaterTreatmentTrain treatment) {
    EnvironmentalReport report = generateReport(facility);

    // Add water treatment metrics
    if (treatment != null) {
      report.oiwMgL = treatment.getOilInWaterMgL();
      report.oilDischargeTonnesYear = getOilDischarge(treatment);
      report.isOIWCompliant = treatment.isDischargeCompliant();
    }

    return report;
  }

  // ============================================================================
  // RESULT CLASS
  // ============================================================================

  /**
   * Environmental report result container.
   */
  public static class EnvironmentalReport implements Serializable {
    private static final long serialVersionUID = 1000L;

    // Power
    /** Total facility power consumption (kW). */
    public double totalPowerKW;

    /** Power supply type. */
    public PowerSupplyType powerSupplyType;

    // Emissions
    /** CO2 from power generation (tonnes/year). */
    public double co2FromPowerTonnesYear;

    /** CO2 from flaring (tonnes/year). */
    public double co2FromFlaringTonnesYear;

    /** Total CO2 emissions (tonnes/year). */
    public double totalCO2TonnesYear;

    /** CO2 intensity (kg CO2/boe). */
    public double co2IntensityKgBoe;

    // Production
    /** Annual oil production (Sm³/year). */
    public double oilProductionSm3Year;

    /** Annual gas production (Sm³/year). */
    public double gasProductionSm3Year;

    /** Annual water production (m³/year). */
    public double waterProductionM3Year;

    // Water discharge
    /** Oil-in-water concentration (mg/L). */
    public double oiwMgL;

    /** Annual oil discharge (tonnes/year). */
    public double oilDischargeTonnesYear;

    /** OIW NCS compliance status. */
    public boolean isOIWCompliant;

    // Economics
    /** Annual CO2 tax (NOK). */
    public double co2TaxNOK;

    /** Low emitter status (&lt;10 kg CO2/boe). */
    public boolean isLowEmitter;

    /**
     * Returns report as markdown table.
     *
     * @return markdown formatted report
     */
    public String toMarkdown() {
      StringBuilder sb = new StringBuilder();
      sb.append("# Environmental Report\n\n");

      sb.append("## Emissions\n\n");
      sb.append("| Metric | Value | Unit |\n");
      sb.append("|--------|-------|------|\n");
      sb.append(String.format("| Total CO2 | %.0f | tonnes/year |\n", totalCO2TonnesYear));
      sb.append(String.format("| CO2 from Power | %.0f | tonnes/year |\n", co2FromPowerTonnesYear));
      sb.append(
          String.format("| CO2 from Flaring | %.0f | tonnes/year |\n", co2FromFlaringTonnesYear));
      sb.append(String.format("| CO2 Intensity | %.1f | kg CO2/boe |\n", co2IntensityKgBoe));
      sb.append(String.format("| Power Consumption | %.0f | kW |\n", totalPowerKW));
      sb.append(String.format("| Power Supply | %s | - |\n", powerSupplyType));

      sb.append("\n## Water Discharge\n\n");
      sb.append("| Metric | Value | Unit | Compliant |\n");
      sb.append("|--------|-------|------|----------|\n");
      sb.append(String.format("| OIW Concentration | %.1f | mg/L | %s |\n", oiwMgL,
          isOIWCompliant ? "✓" : "✗"));
      sb.append(String.format("| Annual Oil Discharge | %.1f | tonnes/year | - |\n",
          oilDischargeTonnesYear));

      sb.append("\n## Economics\n\n");
      sb.append(String.format("| CO2 Tax (NCS) | %.0f | kNOK/year |\n", co2TaxNOK / 1000.0));

      sb.append("\n## Status\n\n");
      sb.append(
          String.format("- **Low Emitter (<10 kg/boe):** %s\n", isLowEmitter ? "✓ Yes" : "✗ No"));
      sb.append(
          String.format("- **OIW Compliant (<30 mg/L):** %s\n", isOIWCompliant ? "✓ Yes" : "✗ No"));

      return sb.toString();
    }

    @Override
    public String toString() {
      return String.format(
          "EnvironmentalReport[CO2=%.0f t/yr, intensity=%.1f kg/boe, OIW=%.1f mg/L]",
          totalCO2TonnesYear, co2IntensityKgBoe, oiwMgL);
    }
  }
}
