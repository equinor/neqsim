package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detailed emissions calculator for oil and gas facilities.
 *
 * <p>
 * This class provides comprehensive greenhouse gas emissions modeling including:
 * <ul>
 * <li><b>Scope 1:</b> Direct emissions (combustion, flaring, venting, fugitives)</li>
 * <li><b>Scope 2:</b> Indirect emissions from purchased energy</li>
 * <li><b>Scope 3:</b> Value chain emissions (optional)</li>
 * <li><b>Detailed source breakdown:</b> Equipment-level emissions</li>
 * <li><b>Emission factors:</b> Region and fuel-specific factors</li>
 * <li><b>Regulatory compliance:</b> EU ETS, EPA, IOGP methods</li>
 * </ul>
 *
 * <h2>Emission Categories</h2>
 * <table border="1">
 * <tr>
 * <th>Category</th>
 * <th>Sources</th>
 * <th>Typical Contribution</th>
 * </tr>
 * <tr>
 * <td>Combustion</td>
 * <td>Turbines, engines, heaters, boilers</td>
 * <td>60-80%</td>
 * </tr>
 * <tr>
 * <td>Flaring</td>
 * <td>Safety flare, pilot, process upsets</td>
 * <td>5-20%</td>
 * </tr>
 * <tr>
 * <td>Venting</td>
 * <td>Cold vents, tank breathing, process vents</td>
 * <td>1-10%</td>
 * </tr>
 * <tr>
 * <td>Fugitive</td>
 * <td>Leaks from valves, flanges, seals</td>
 * <td>0.5-3%</td>
 * </tr>
 * </table>
 *
 * <h2>Global Warming Potentials (GWP-100)</h2>
 * <ul>
 * <li>CO2: 1</li>
 * <li>CH4: 28 (AR5) / 25 (AR4)</li>
 * <li>N2O: 265 (AR5) / 298 (AR4)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * DetailedEmissionsCalculator calc = new DetailedEmissionsCalculator();
 * 
 * // Set production
 * calc.setOilProduction(10000, "bbl/day");
 * calc.setGasProduction(5.0, "MMSm3/day");
 * 
 * // Set combustion sources
 * calc.addGasTurbine("GT-1", 25.0, "MW"); // 25 MW gas turbine
 * calc.addGasTurbine("GT-2", 25.0, "MW");
 * calc.addHeater("Reboiler", 5.0, "MW");
 * 
 * // Set flaring
 * calc.setFlaringRate(0.02, "MMSm3/day");
 * calc.setFlareEfficiency(0.98);
 * 
 * // Set fugitives
 * calc.setFugitiveRate(0.01); // 0.01% of throughput
 * 
 * // Calculate
 * DetailedEmissionsReport report = calc.calculate();
 * 
 * System.out.println("Scope 1: " + report.getScope1Emissions() + " tCO2e/yr");
 * System.out.println("Scope 2: " + report.getScope2Emissions() + " tCO2e/yr");
 * System.out.println("Intensity: " + report.getIntensity() + " kg CO2e/boe");
 * System.out.println("\nBreakdown:");
 * for (Map.Entry<String, Double> entry : report.getEmissionsBySource().entrySet()) {
 *   System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " tCO2e/yr");
 * }
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see EmissionsTracker
 * @see EnergyEfficiencyCalculator
 */
public class DetailedEmissionsCalculator implements Serializable {
  private static final long serialVersionUID = 1000L;

  // Global Warming Potentials (IPCC AR5, 100-year)
  private static final double GWP_CO2 = 1.0;
  private static final double GWP_CH4 = 28.0; // AR5 value
  private static final double GWP_N2O = 265.0;

  // Emission factors for natural gas combustion
  private static final double NG_CO2_KG_PER_SM3 = 1.96; // kg CO2 per Sm³ gas burned
  private static final double NG_CH4_G_PER_SM3 = 0.05; // g CH4 per Sm³ (unburned)
  private static final double NG_N2O_G_PER_SM3 = 0.001; // g N2O per Sm³

  // Flare combustion
  private static final double FLARE_CO2_KG_PER_SM3 = 2.75; // Higher due to composition

  // Fuel consumption rates (Sm³/MWh for gas turbines)
  private static final double GT_FUEL_SM3_PER_MWH_SIMPLE = 350.0; // Simple cycle ~28% eff
  private static final double GT_FUEL_SM3_PER_MWH_COMBINED = 220.0; // Combined cycle ~45% eff

  // Diesel emission factors
  private static final double DIESEL_CO2_KG_PER_L = 2.68;

  // Electricity grid factors (kg CO2e/kWh)
  private static final Map<String, Double> GRID_FACTORS = new LinkedHashMap<>();

  static {
    GRID_FACTORS.put("NORDIC", 0.02); // Hydro-dominated
    GRID_FACTORS.put("UK", 0.23);
    GRID_FACTORS.put("EU_AVERAGE", 0.30);
    GRID_FACTORS.put("US_AVERAGE", 0.42);
    GRID_FACTORS.put("GULF", 0.50); // Gas-dominated
    GRID_FACTORS.put("GLOBAL_AVERAGE", 0.45);
  }

  // Production data
  private double oilProductionBblPerDay = 0.0;
  private double gasProductionMSm3PerDay = 0.0;
  private double condensateProductionBblPerDay = 0.0;

  // Combustion sources
  private List<CombustionSource> combustionSources = new ArrayList<>();

  // Flaring data
  private double flaringRateMSm3PerDay = 0.0;
  private double flareEfficiency = 0.98; // 98% combustion efficiency
  private double flareCO2ContentPercent = 0.0; // CO2 in flare gas

  // Venting data
  private double coldVentingRateMSm3PerDay = 0.0;
  private double tankBreathingRateSm3PerDay = 0.0;
  private double processVentingRateSm3PerDay = 0.0;

  // Fugitive emissions
  private double fugitiveRatePercent = 0.01; // % of gas throughput
  private int flangeCount = 500;
  private int valveCount = 200;
  private int compressorSealCount = 4;
  private int pumpSealCount = 10;

  // Purchased electricity
  private double purchasedElectricityMWh = 0.0;
  private String gridRegion = "EU_AVERAGE";

  // Produced fluids CO2 content
  private double producedCO2ContentPercent = 0.0;
  private boolean ventingProducedCO2 = true; // vs. reinjection

  // Operating hours
  private double operatingHoursPerYear = 8400.0; // 96% availability

  /**
   * Creates a new detailed emissions calculator.
   */
  public DetailedEmissionsCalculator() {
    // Default constructor
  }

  /**
   * Calculates comprehensive emissions inventory.
   *
   * @return detailed emissions report
   */
  public DetailedEmissionsReport calculate() {
    DetailedEmissionsReport report = new DetailedEmissionsReport();

    // Calculate production metrics
    report.totalProductionBoePerYear = calculateAnnualProductionBoe();
    report.oilProductionBblPerYear = oilProductionBblPerDay * 365.0;
    report.gasProductionMSm3PerYear = gasProductionMSm3PerDay * 365.0;

    // Scope 1: Direct emissions
    calculateCombustionEmissions(report);
    calculateFlaringEmissions(report);
    calculateVentingEmissions(report);
    calculateFugitiveEmissions(report);

    // Scope 2: Indirect (purchased electricity)
    calculateScope2Emissions(report);

    // Sum totals
    report.scope1Total = 0;
    for (Double value : report.scope1Breakdown.values()) {
      report.scope1Total += value;
    }

    report.scope2Total = 0;
    for (Double value : report.scope2Breakdown.values()) {
      report.scope2Total += value;
    }

    report.totalEmissions = report.scope1Total + report.scope2Total;

    // Calculate intensity
    if (report.totalProductionBoePerYear > 0) {
      report.intensityKgCO2PerBoe =
          report.totalEmissions * 1000.0 / report.totalProductionBoePerYear;
    }

    // Assign rating
    report.rating = calculateEmissionsRating(report.intensityKgCO2PerBoe);

    return report;
  }

  /**
   * Calculates combustion emissions from all sources.
   */
  private void calculateCombustionEmissions(DetailedEmissionsReport report) {
    double totalCombustionCO2e = 0.0;

    for (CombustionSource source : combustionSources) {
      double annualFuelSm3 = source.calculateAnnualFuelConsumption(operatingHoursPerYear);

      // CO2 from fuel combustion
      double co2Tonnes = annualFuelSm3 * NG_CO2_KG_PER_SM3 / 1000.0;

      // CH4 from incomplete combustion
      double ch4Tonnes = annualFuelSm3 * NG_CH4_G_PER_SM3 / 1e6;
      double ch4CO2e = ch4Tonnes * GWP_CH4;

      // N2O
      double n2oTonnes = annualFuelSm3 * NG_N2O_G_PER_SM3 / 1e6;
      double n2oCO2e = n2oTonnes * GWP_N2O;

      double totalCO2e = co2Tonnes + ch4CO2e + n2oCO2e;

      report.emissionsBySource.put(source.name + " (combustion)", totalCO2e);
      totalCombustionCO2e += totalCO2e;
    }

    report.scope1Breakdown.put("Combustion", totalCombustionCO2e);
  }

  /**
   * Calculates flaring emissions.
   */
  private void calculateFlaringEmissions(DetailedEmissionsReport report) {
    if (flaringRateMSm3PerDay <= 0) {
      return;
    }

    double annualFlaredSm3 = flaringRateMSm3PerDay * 1e6 * 365.0;

    // Combusted fraction
    double combustedSm3 = annualFlaredSm3 * flareEfficiency;
    double co2Tonnes = combustedSm3 * FLARE_CO2_KG_PER_SM3 / 1000.0;

    // Uncombusted methane (slip)
    double unburntSm3 = annualFlaredSm3 * (1.0 - flareEfficiency);
    double ch4Tonnes = unburntSm3 * 0.7 / 1000.0; // 0.7 kg/Sm³ methane density
    double ch4CO2e = ch4Tonnes * GWP_CH4;

    // Any CO2 in the flare gas passes through
    double passThruCO2 = annualFlaredSm3 * flareCO2ContentPercent / 100.0 * 1.98 / 1000.0;

    double totalFlaring = co2Tonnes + ch4CO2e + passThruCO2;

    report.scope1Breakdown.put("Flaring", totalFlaring);
    report.emissionsBySource.put("Flare (combustion)", co2Tonnes);
    report.emissionsBySource.put("Flare (methane slip)", ch4CO2e);
  }

  /**
   * Calculates venting emissions.
   */
  private void calculateVentingEmissions(DetailedEmissionsReport report) {
    double totalVenting = 0.0;

    // Cold venting (direct methane release)
    if (coldVentingRateMSm3PerDay > 0) {
      double annualColdVentSm3 = coldVentingRateMSm3PerDay * 1e6 * 365.0;
      double ch4Tonnes = annualColdVentSm3 * 0.7 / 1000.0;
      double ch4CO2e = ch4Tonnes * GWP_CH4;
      report.emissionsBySource.put("Cold venting", ch4CO2e);
      totalVenting += ch4CO2e;
    }

    // Tank breathing
    if (tankBreathingRateSm3PerDay > 0) {
      double annualTankSm3 = tankBreathingRateSm3PerDay * 365.0;
      // Assume 80% methane equivalent
      double ch4Tonnes = annualTankSm3 * 0.8 * 0.7 / 1000.0;
      double ch4CO2e = ch4Tonnes * GWP_CH4;
      report.emissionsBySource.put("Tank breathing", ch4CO2e);
      totalVenting += ch4CO2e;
    }

    // Process venting
    if (processVentingRateSm3PerDay > 0) {
      double annualProcessSm3 = processVentingRateSm3PerDay * 365.0;
      double ch4Tonnes = annualProcessSm3 * 0.7 / 1000.0;
      double ch4CO2e = ch4Tonnes * GWP_CH4;
      report.emissionsBySource.put("Process venting", ch4CO2e);
      totalVenting += ch4CO2e;
    }

    // Produced CO2 venting
    if (producedCO2ContentPercent > 0 && ventingProducedCO2) {
      double gasProductionSm3Year = gasProductionMSm3PerDay * 1e6 * 365.0;
      double co2Sm3 = gasProductionSm3Year * producedCO2ContentPercent / 100.0;
      double co2Tonnes = co2Sm3 * 1.98 / 1000.0;
      report.emissionsBySource.put("Produced CO2 venting", co2Tonnes);
      totalVenting += co2Tonnes;
    }

    report.scope1Breakdown.put("Venting", totalVenting);
  }

  /**
   * Calculates fugitive emissions using EPA method.
   */
  private void calculateFugitiveEmissions(DetailedEmissionsReport report) {
    double totalFugitive = 0.0;

    // Method 1: Percentage of throughput
    double gasThroughputSm3Year = gasProductionMSm3PerDay * 1e6 * 365.0;
    double fugitiveSm3 = gasThroughputSm3Year * fugitiveRatePercent / 100.0;
    double ch4Tonnes = fugitiveSm3 * 0.7 / 1000.0;
    double ch4CO2e = ch4Tonnes * GWP_CH4;
    totalFugitive += ch4CO2e;

    // Method 2: Component count (EPA factors)
    // Simplified component emission factors (kg CH4/component/year)
    double flangeLeakRate = 0.1; // kg/yr per flange
    double valveLeakRate = 0.5; // kg/yr per valve
    double compressorSealRate = 100.0; // kg/yr per compressor
    double pumpSealRate = 5.0; // kg/yr per pump

    double componentEmissions = (flangeCount * flangeLeakRate + valveCount * valveLeakRate
        + compressorSealCount * compressorSealRate + pumpSealCount * pumpSealRate) / 1000.0
        * GWP_CH4;

    // Use higher of the two methods
    totalFugitive = Math.max(ch4CO2e, componentEmissions);

    report.scope1Breakdown.put("Fugitive", totalFugitive);
    report.emissionsBySource.put("Fugitive emissions", totalFugitive);
  }

  /**
   * Calculates Scope 2 emissions from purchased electricity.
   */
  private void calculateScope2Emissions(DetailedEmissionsReport report) {
    if (purchasedElectricityMWh <= 0) {
      return;
    }

    double gridFactor = GRID_FACTORS.getOrDefault(gridRegion.toUpperCase(), 0.30);
    double co2e = purchasedElectricityMWh * gridFactor; // tonnes

    report.scope2Breakdown.put("Purchased electricity", co2e);
    report.emissionsBySource.put("Grid electricity", co2e);
  }

  /**
   * Calculates annual production in boe.
   */
  private double calculateAnnualProductionBoe() {
    // Oil: 1 bbl = 1 boe
    double oilBoe = oilProductionBblPerDay * 365.0;

    // Gas: ~6000 Sm³ per boe (varies with composition)
    double gasBoe = gasProductionMSm3PerDay * 1e6 / 6000.0 * 365.0;

    // Condensate: 1 bbl = 1 boe
    double condensateBoe = condensateProductionBblPerDay * 365.0;

    return oilBoe + gasBoe + condensateBoe;
  }

  /**
   * Calculates emissions rating based on intensity.
   */
  private String calculateEmissionsRating(double intensity) {
    if (intensity < 5) {
      return "A - Very Low";
    } else if (intensity < 10) {
      return "B - Low";
    } else if (intensity < 15) {
      return "C - Average";
    } else if (intensity < 25) {
      return "D - High";
    } else if (intensity < 40) {
      return "E - Very High";
    } else {
      return "F - Extremely High";
    }
  }

  // ===================== Configuration Methods =====================

  /**
   * Sets oil production rate.
   *
   * @param rate rate value
   * @param unit unit ("bbl/day", "Sm3/day")
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setOilProduction(double rate, String unit) {
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
  public DetailedEmissionsCalculator setGasProduction(double rate, String unit) {
    if ("MMSm3/day".equalsIgnoreCase(unit)) {
      this.gasProductionMSm3PerDay = rate * 1000.0;
    } else {
      this.gasProductionMSm3PerDay = rate;
    }
    return this;
  }

  /**
   * Adds a gas turbine combustion source.
   *
   * @param name turbine name
   * @param power power output
   * @param unit power unit ("MW", "kW")
   * @return this for chaining
   */
  public DetailedEmissionsCalculator addGasTurbine(String name, double power, String unit) {
    CombustionSource source = new CombustionSource();
    source.name = name;
    source.type = CombustionType.GAS_TURBINE_SIMPLE;
    source.powerMW = "kW".equalsIgnoreCase(unit) ? power / 1000.0 : power;
    combustionSources.add(source);
    return this;
  }

  /**
   * Adds a combined cycle gas turbine.
   *
   * @param name unit name
   * @param power power output (MW)
   * @return this for chaining
   */
  public DetailedEmissionsCalculator addCombinedCycleTurbine(String name, double power) {
    CombustionSource source = new CombustionSource();
    source.name = name;
    source.type = CombustionType.GAS_TURBINE_COMBINED;
    source.powerMW = power;
    combustionSources.add(source);
    return this;
  }

  /**
   * Adds a heater/boiler.
   *
   * @param name heater name
   * @param duty heat duty
   * @param unit unit ("MW", "kW")
   * @return this for chaining
   */
  public DetailedEmissionsCalculator addHeater(String name, double duty, String unit) {
    CombustionSource source = new CombustionSource();
    source.name = name;
    source.type = CombustionType.FIRED_HEATER;
    source.powerMW = "kW".equalsIgnoreCase(unit) ? duty / 1000.0 : duty;
    combustionSources.add(source);
    return this;
  }

  /**
   * Adds a diesel engine.
   *
   * @param name engine name
   * @param fuelRateLitersPerHour fuel consumption rate
   * @return this for chaining
   */
  public DetailedEmissionsCalculator addDieselEngine(String name, double fuelRateLitersPerHour) {
    CombustionSource source = new CombustionSource();
    source.name = name;
    source.type = CombustionType.DIESEL_ENGINE;
    source.fuelRateLPerHour = fuelRateLitersPerHour;
    combustionSources.add(source);
    return this;
  }

  /**
   * Sets flaring rate.
   *
   * @param rate rate value
   * @param unit unit ("MMSm3/day", "MSm3/day")
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setFlaringRate(double rate, String unit) {
    if ("MMSm3/day".equalsIgnoreCase(unit)) {
      this.flaringRateMSm3PerDay = rate * 1000.0;
    } else {
      this.flaringRateMSm3PerDay = rate;
    }
    return this;
  }

  /**
   * Sets flare combustion efficiency.
   *
   * @param efficiency efficiency (0-1)
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setFlareEfficiency(double efficiency) {
    this.flareEfficiency = Math.max(0.90, Math.min(efficiency, 1.0));
    return this;
  }

  /**
   * Sets fugitive emission rate as percentage of throughput.
   *
   * @param percent fugitive rate (%)
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setFugitiveRate(double percent) {
    this.fugitiveRatePercent = percent;
    return this;
  }

  /**
   * Sets component counts for fugitive emissions calculation.
   *
   * @param flanges number of flanges
   * @param valves number of valves
   * @param compressorSeals number of compressor seals
   * @param pumpSeals number of pump seals
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setComponentCounts(int flanges, int valves,
      int compressorSeals, int pumpSeals) {
    this.flangeCount = flanges;
    this.valveCount = valves;
    this.compressorSealCount = compressorSeals;
    this.pumpSealCount = pumpSeals;
    return this;
  }

  /**
   * Sets purchased electricity.
   *
   * @param mwhPerYear annual electricity consumption (MWh)
   * @param gridRegion grid region for emission factor
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setPurchasedElectricity(double mwhPerYear, String gridRegion) {
    this.purchasedElectricityMWh = mwhPerYear;
    this.gridRegion = gridRegion;
    return this;
  }

  /**
   * Sets produced CO2 content.
   *
   * @param percent CO2 content in produced gas (%)
   * @param venting true if vented, false if reinjected
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setProducedCO2(double percent, boolean venting) {
    this.producedCO2ContentPercent = percent;
    this.ventingProducedCO2 = venting;
    return this;
  }

  /**
   * Sets cold venting rate.
   *
   * @param rate rate (MSm3/day)
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setColdVentingRate(double rate) {
    this.coldVentingRateMSm3PerDay = rate;
    return this;
  }

  /**
   * Sets tank breathing emissions.
   *
   * @param rate rate (Sm3/day)
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setTankBreathingRate(double rate) {
    this.tankBreathingRateSm3PerDay = rate;
    return this;
  }

  /**
   * Sets operating hours per year.
   *
   * @param hours operating hours
   * @return this for chaining
   */
  public DetailedEmissionsCalculator setOperatingHours(double hours) {
    this.operatingHoursPerYear = hours;
    return this;
  }

  // ===================== Inner Classes =====================

  /**
   * Combustion source types.
   */
  public enum CombustionType {
    /** Simple cycle gas turbine. */
    GAS_TURBINE_SIMPLE,
    /** Combined cycle gas turbine. */
    GAS_TURBINE_COMBINED,
    /** Fired heater or boiler. */
    FIRED_HEATER,
    /** Diesel engine. */
    DIESEL_ENGINE
  }

  /**
   * Combustion source configuration.
   */
  private static class CombustionSource implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    CombustionType type;
    double powerMW; // For turbines and heaters
    double fuelRateLPerHour; // For diesel

    double calculateAnnualFuelConsumption(double operatingHours) {
      switch (type) {
        case GAS_TURBINE_SIMPLE:
          return powerMW * operatingHours * GT_FUEL_SM3_PER_MWH_SIMPLE;
        case GAS_TURBINE_COMBINED:
          return powerMW * operatingHours * GT_FUEL_SM3_PER_MWH_COMBINED;
        case FIRED_HEATER:
          // Assume 85% efficiency, gas: 35 MJ/Sm³
          double heatMJ = powerMW * operatingHours * 3600.0; // MWh to MJ
          return heatMJ / 35.0 / 0.85;
        case DIESEL_ENGINE:
          // Convert liters to Sm³ equivalent
          return fuelRateLPerHour * operatingHours * DIESEL_CO2_KG_PER_L / NG_CO2_KG_PER_SM3;
        default:
          return 0;
      }
    }
  }

  /**
   * Detailed emissions report.
   */
  public static class DetailedEmissionsReport implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Total production (boe/year). */
    public double totalProductionBoePerYear;

    /** Oil production (bbl/year). */
    public double oilProductionBblPerYear;

    /** Gas production (MSm3/year). */
    public double gasProductionMSm3PerYear;

    /** Scope 1 total emissions (tCO2e/year). */
    public double scope1Total;

    /** Scope 2 total emissions (tCO2e/year). */
    public double scope2Total;

    /** Total emissions (tCO2e/year). */
    public double totalEmissions;

    /** Intensity (kg CO2e/boe). */
    public double intensityKgCO2PerBoe;

    /** Emissions rating. */
    public String rating;

    /** Scope 1 breakdown by category. */
    public Map<String, Double> scope1Breakdown = new LinkedHashMap<>();

    /** Scope 2 breakdown by category. */
    public Map<String, Double> scope2Breakdown = new LinkedHashMap<>();

    /** Emissions by individual source. */
    public Map<String, Double> emissionsBySource = new LinkedHashMap<>();

    /**
     * Gets Scope 1 emissions.
     *
     * @return scope 1 emissions (tCO2e/year)
     */
    public double getScope1Emissions() {
      return scope1Total;
    }

    /**
     * Gets Scope 2 emissions.
     *
     * @return scope 2 emissions (tCO2e/year)
     */
    public double getScope2Emissions() {
      return scope2Total;
    }

    /**
     * Gets emissions intensity.
     *
     * @return intensity (kg CO2e/boe)
     */
    public double getIntensity() {
      return intensityKgCO2PerBoe;
    }

    /**
     * Gets emissions by source.
     *
     * @return map of source to emissions
     */
    public Map<String, Double> getEmissionsBySource() {
      return new LinkedHashMap<>(emissionsBySource);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Detailed Emissions Report\n");
      sb.append("=========================\n");
      sb.append(String.format("Production: %.0f boe/year%n", totalProductionBoePerYear));
      sb.append(String.format("%nScope 1 (Direct): %.0f tCO2e/year%n", scope1Total));
      for (Map.Entry<String, Double> entry : scope1Breakdown.entrySet()) {
        sb.append(String.format("  %s: %.0f tCO2e%n", entry.getKey(), entry.getValue()));
      }
      sb.append(String.format("%nScope 2 (Indirect): %.0f tCO2e/year%n", scope2Total));
      for (Map.Entry<String, Double> entry : scope2Breakdown.entrySet()) {
        sb.append(String.format("  %s: %.0f tCO2e%n", entry.getKey(), entry.getValue()));
      }
      sb.append(String.format("%nTotal: %.0f tCO2e/year%n", totalEmissions));
      sb.append(String.format("Intensity: %.1f kg CO2e/boe%n", intensityKgCO2PerBoe));
      sb.append(String.format("Rating: %s%n", rating));
      return sb.toString();
    }
  }
}
