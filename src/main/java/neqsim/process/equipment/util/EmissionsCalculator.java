package neqsim.process.equipment.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Utility class for calculating greenhouse gas emissions from process streams.
 *
 * <p>
 * Calculates CO2, methane, and non-methane VOC (nmVOC) emissions from gas streams, typically from
 * produced water degassing, cold flares, or other venting points.
 * </p>
 *
 * <p>
 * Based on methodology from "Virtual Measurement of Emissions from Produced Water Using an Online
 * Process Simulator" (GFMW 2023).
 * </p>
 *
 * <h2>Norwegian Regulatory Framework</h2>
 * <p>
 * Complies with Norwegian offshore emission quantification requirements:
 * </p>
 * <ul>
 * <li>Aktivitetsforskriften (Activity Regulations) Section 70: Measurement and calculation</li>
 * <li>Norsk olje og gass: "Handbook for quantification of direct emissions"</li>
 * <li>Norwegian Environment Agency reporting requirements</li>
 * </ul>
 *
 * <h2>Calculation Methods</h2>
 * <p>
 * This class supports two calculation approaches:
 * </p>
 * <table>
 * <caption>Emission calculation methods comparison</caption>
 * <tr>
 * <th>Method</th>
 * <th>Accuracy</th>
 * <th>Use Case</th>
 * </tr>
 * <tr>
 * <td>Thermodynamic (CPA-EoS)</td>
 * <td>+/-3.6%</td>
 * <td>Virtual measurement (recommended)</td>
 * </tr>
 * <tr>
 * <td>Conventional handbook</td>
 * <td>+/-50%</td>
 * <td>Screening/fallback only</td>
 * </tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * {@code
 * // Calculate emissions from a separator gas outlet
 * EmissionsCalculator calc = new EmissionsCalculator(separator.getGasOutStream());
 * calc.calculate();
 * 
 * System.out.println("CO2: " + calc.getCO2EmissionRate("kg/hr") + " kg/hr");
 * System.out.println("Methane: " + calc.getMethaneEmissionRate("kg/hr") + " kg/hr");
 * System.out.println("CO2 equivalents: " + calc.getCO2Equivalents("tonnes/year") + " t/yr");
 * 
 * // Compare with conventional method
 * double conventionalCH4 = EmissionsCalculator.calculateConventionalCH4(waterVol, dP);
 * System.out.println("Conventional method gives: " + conventionalCH4 + " kg");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.1
 * @see <a href="https://lovdata.no/dokument/SF/forskrift/2010-04-29-613">Aktivitetsforskriften</a>
 */
public class EmissionsCalculator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Global Warming Potential for methane (IPCC AR5 100-year). */
  public static final double GWP_METHANE = 28.0;

  /** Global Warming Potential for nmVOC (approximate average). */
  public static final double GWP_NMVOC = 2.2;

  /** Global Warming Potential for CO2. */
  public static final double GWP_CO2 = 1.0;

  /** Hours per year for annual calculations. */
  private static final double HOURS_PER_YEAR = 8760.0;

  /**
   * Default methane solubility factor per Norwegian offshore emission handbook. Unit: g/(m³ water ·
   * bar). This is used in the conventional calculation method.
   */
  public static final double HANDBOOK_F_CH4 = 14.0;

  /**
   * Default nmVOC solubility factor per Norwegian offshore emission handbook. Unit: g/(m³ water ·
   * bar). This is used in the conventional calculation method.
   */
  public static final double HANDBOOK_F_NMVOC = 3.5;

  // Input stream
  private StreamInterface gasStream;

  // Calculated emissions (kg/s)
  private double co2EmissionRate = 0.0;
  private double methaneEmissionRate = 0.0;
  private double ethaneEmissionRate = 0.0;
  private double propaneEmissionRate = 0.0;
  private double butanesEmissionRate = 0.0;
  private double pentanesEmissionRate = 0.0;
  private double heavierEmissionRate = 0.0;
  private double nitrogenEmissionRate = 0.0;
  private double waterVaporRate = 0.0;
  private double totalGasRate = 0.0;

  // Cumulative tracking
  private double cumulativeCO2_kg = 0.0;
  private double cumulativeMethane_kg = 0.0;
  private double cumulativeNMVOC_kg = 0.0;
  private double cumulativeTotalGas_kg = 0.0;
  private double totalRunTime_hours = 0.0;

  /**
   * Creates an emissions calculator for a gas stream.
   *
   * @param gasStream the gas stream to calculate emissions from
   */
  public EmissionsCalculator(StreamInterface gasStream) {
    this.gasStream = gasStream;
  }

  /**
   * Creates an emissions calculator from a separator's gas outlet.
   *
   * @param separator the separator equipment
   */
  public EmissionsCalculator(Separator separator) {
    this.gasStream = separator.getGasOutStream();
  }

  /**
   * Calculate emissions from the current stream conditions.
   */
  public void calculate() {
    if (gasStream == null || gasStream.getFluid() == null) {
      return;
    }

    SystemInterface fluid = gasStream.getFluid();

    // Find gas phase
    PhaseInterface gasPhase = null;
    if (fluid.hasPhaseType("gas")) {
      gasPhase = fluid.getPhase("gas");
    } else {
      // No gas phase - all rates are zero
      resetRates();
      return;
    }

    totalGasRate = gasPhase.getFlowRate("kg/sec");

    // Calculate individual component rates
    co2EmissionRate = getComponentRate(gasPhase, "CO2");
    methaneEmissionRate = getComponentRate(gasPhase, "methane");
    ethaneEmissionRate = getComponentRate(gasPhase, "ethane");
    propaneEmissionRate = getComponentRate(gasPhase, "propane");
    butanesEmissionRate =
        getComponentRate(gasPhase, "i-butane") + getComponentRate(gasPhase, "n-butane");
    pentanesEmissionRate =
        getComponentRate(gasPhase, "i-pentane") + getComponentRate(gasPhase, "n-pentane");
    nitrogenEmissionRate = getComponentRate(gasPhase, "nitrogen");
    waterVaporRate = getComponentRate(gasPhase, "water");

    // Calculate heavier components (C6+)
    heavierEmissionRate = 0.0;
    for (int i = 0; i < gasPhase.getNumberOfComponents(); i++) {
      String name = gasPhase.getComponent(i).getComponentName().toLowerCase();
      if (isHeavierHydrocarbon(name)) {
        heavierEmissionRate += gasPhase.getComponent(i).getFlowRate("kg/sec");
      }
    }
  }

  /**
   * Update cumulative emissions tracking.
   *
   * @param timeStep_hours time step in hours
   */
  public void updateCumulative(double timeStep_hours) {
    if (timeStep_hours <= 0) {
      return;
    }

    cumulativeCO2_kg += co2EmissionRate * timeStep_hours * 3600.0;
    cumulativeMethane_kg += methaneEmissionRate * timeStep_hours * 3600.0;
    cumulativeNMVOC_kg += getNMVOCEmissionRate("kg/sec") * timeStep_hours * 3600.0;
    cumulativeTotalGas_kg += totalGasRate * timeStep_hours * 3600.0;
    totalRunTime_hours += timeStep_hours;
  }

  /**
   * Reset cumulative tracking.
   */
  public void resetCumulative() {
    cumulativeCO2_kg = 0.0;
    cumulativeMethane_kg = 0.0;
    cumulativeNMVOC_kg = 0.0;
    cumulativeTotalGas_kg = 0.0;
    totalRunTime_hours = 0.0;
  }

  // ============================================================================
  // EMISSION RATE GETTERS
  // ============================================================================

  /**
   * Get CO2 emission rate.
   *
   * @param unit "kg/sec", "kg/hr", "tonnes/day", "tonnes/year"
   * @return emission rate in specified unit
   */
  public double getCO2EmissionRate(String unit) {
    return convertRate(co2EmissionRate, unit);
  }

  /**
   * Get methane emission rate.
   *
   * @param unit "kg/sec", "kg/hr", "tonnes/day", "tonnes/year"
   * @return emission rate in specified unit
   */
  public double getMethaneEmissionRate(String unit) {
    return convertRate(methaneEmissionRate, unit);
  }

  /**
   * Get non-methane VOC (nmVOC) emission rate. Includes C2+ hydrocarbons.
   *
   * @param unit "kg/sec", "kg/hr", "tonnes/day", "tonnes/year"
   * @return emission rate in specified unit
   */
  public double getNMVOCEmissionRate(String unit) {
    double nmvoc = ethaneEmissionRate + propaneEmissionRate + butanesEmissionRate
        + pentanesEmissionRate + heavierEmissionRate;
    return convertRate(nmvoc, unit);
  }

  /**
   * Get total gas emission rate.
   *
   * @param unit "kg/sec", "kg/hr", "tonnes/day", "tonnes/year"
   * @return emission rate in specified unit
   */
  public double getTotalGasRate(String unit) {
    return convertRate(totalGasRate, unit);
  }

  /**
   * Get nitrogen emission rate.
   *
   * @param unit "kg/sec", "kg/hr", "tonnes/day", "tonnes/year"
   * @return emission rate in specified unit
   */
  public double getNitrogenEmissionRate(String unit) {
    return convertRate(nitrogenEmissionRate, unit);
  }

  // ============================================================================
  // CO2 EQUIVALENTS
  // ============================================================================

  /**
   * Calculate CO2 equivalent emissions using GWP-100 factors.
   *
   * @param unit "kg/sec", "kg/hr", "tonnes/day", "tonnes/year"
   * @return CO2 equivalent emission rate
   */
  public double getCO2Equivalents(String unit) {
    double co2eq = co2EmissionRate * GWP_CO2 + methaneEmissionRate * GWP_METHANE
        + getNMVOCEmissionRate("kg/sec") * GWP_NMVOC;
    return convertRate(co2eq, unit);
  }

  /**
   * Get cumulative CO2 equivalent emissions.
   *
   * @param unit "kg", "tonnes"
   * @return cumulative CO2 equivalents
   */
  public double getCumulativeCO2Equivalents(String unit) {
    double co2eq = cumulativeCO2_kg * GWP_CO2 + cumulativeMethane_kg * GWP_METHANE
        + cumulativeNMVOC_kg * GWP_NMVOC;

    if (unit.equalsIgnoreCase("tonnes")) {
      return co2eq / 1000.0;
    }
    return co2eq;
  }

  // ============================================================================
  // CUMULATIVE GETTERS
  // ============================================================================

  /**
   * Get cumulative CO2 emissions.
   *
   * @param unit "kg" or "tonnes"
   * @return cumulative emissions
   */
  public double getCumulativeCO2(String unit) {
    return unit.equalsIgnoreCase("tonnes") ? cumulativeCO2_kg / 1000.0 : cumulativeCO2_kg;
  }

  /**
   * Get cumulative methane emissions.
   *
   * @param unit "kg" or "tonnes"
   * @return cumulative emissions
   */
  public double getCumulativeMethane(String unit) {
    return unit.equalsIgnoreCase("tonnes") ? cumulativeMethane_kg / 1000.0 : cumulativeMethane_kg;
  }

  /**
   * Get cumulative nmVOC emissions.
   *
   * @param unit "kg" or "tonnes"
   * @return cumulative emissions
   */
  public double getCumulativeNMVOC(String unit) {
    return unit.equalsIgnoreCase("tonnes") ? cumulativeNMVOC_kg / 1000.0 : cumulativeNMVOC_kg;
  }

  /**
   * Get total run time for cumulative tracking.
   *
   * @return run time in hours
   */
  public double getTotalRunTime() {
    return totalRunTime_hours;
  }

  // ============================================================================
  // GAS COMPOSITION
  // ============================================================================

  /**
   * Get gas composition as mass fractions.
   *
   * @return map of component name to mass fraction
   */
  public Map<String, Double> getGasCompositionMass() {
    Map<String, Double> composition = new HashMap<>();
    if (totalGasRate > 0) {
      composition.put("CO2", co2EmissionRate / totalGasRate);
      composition.put("methane", methaneEmissionRate / totalGasRate);
      composition.put("ethane", ethaneEmissionRate / totalGasRate);
      composition.put("propane", propaneEmissionRate / totalGasRate);
      composition.put("butanes", butanesEmissionRate / totalGasRate);
      composition.put("pentanes", pentanesEmissionRate / totalGasRate);
      composition.put("C6+", heavierEmissionRate / totalGasRate);
      composition.put("nitrogen", nitrogenEmissionRate / totalGasRate);
      composition.put("water", waterVaporRate / totalGasRate);
    }
    return composition;
  }

  /**
   * Get gas composition as mole fractions from the gas phase.
   *
   * @return map of component name to mole fraction
   */
  public Map<String, Double> getGasCompositionMole() {
    Map<String, Double> composition = new HashMap<>();

    if (gasStream == null || gasStream.getFluid() == null) {
      return composition;
    }

    SystemInterface fluid = gasStream.getFluid();
    if (!fluid.hasPhaseType("gas")) {
      return composition;
    }

    PhaseInterface gasPhase = fluid.getPhase("gas");
    for (int i = 0; i < gasPhase.getNumberOfComponents(); i++) {
      composition.put(gasPhase.getComponent(i).getComponentName(), gasPhase.getComponent(i).getx());
    }

    return composition;
  }

  // ============================================================================
  // EMISSION FACTORS (for comparison with conventional method)
  // ============================================================================

  /**
   * Calculate gas-to-water mass factor (GWMF).
   *
   * @param waterFlowRate_m3hr water flow rate in m³/hr
   * @param pressureDrop_bar pressure drop in bar
   * @return GWMF in g/m³/bar
   */
  public double calculateGWMF(double waterFlowRate_m3hr, double pressureDrop_bar) {
    if (waterFlowRate_m3hr <= 0 || pressureDrop_bar <= 0) {
      return 0.0;
    }
    double gasFlow_ghr = totalGasRate * 3600.0 * 1000.0; // kg/s -> g/hr
    return gasFlow_ghr / waterFlowRate_m3hr / pressureDrop_bar;
  }

  /**
   * Calculate methane solubility factor.
   *
   * @param waterFlowRate_m3hr water flow rate in m³/hr
   * @param pressureDrop_bar pressure drop in bar
   * @return methane factor in g/m³/bar
   */
  public double calculateMethaneFactor(double waterFlowRate_m3hr, double pressureDrop_bar) {
    if (waterFlowRate_m3hr <= 0 || pressureDrop_bar <= 0) {
      return 0.0;
    }
    double ch4Flow_ghr = methaneEmissionRate * 3600.0 * 1000.0;
    return ch4Flow_ghr / waterFlowRate_m3hr / pressureDrop_bar;
  }

  /**
   * Calculate nmVOC solubility factor.
   *
   * @param waterFlowRate_m3hr water flow rate in m³/hr
   * @param pressureDrop_bar pressure drop in bar
   * @return nmVOC factor in g/m³/bar
   */
  public double calculateNMVOCFactor(double waterFlowRate_m3hr, double pressureDrop_bar) {
    if (waterFlowRate_m3hr <= 0 || pressureDrop_bar <= 0) {
      return 0.0;
    }
    double nmvocFlow_ghr = getNMVOCEmissionRate("kg/sec") * 3600.0 * 1000.0;
    return nmvocFlow_ghr / waterFlowRate_m3hr / pressureDrop_bar;
  }

  // ============================================================================
  // NORWEGIAN HANDBOOK CONVENTIONAL METHOD
  // ============================================================================
  // Per "Handbook for quantification of direct emissions from Norwegian petroleum
  // industry" (Norsk olje og gass) and Aktivitetsforskriften §70

  /**
   * Calculate methane emissions using conventional Norwegian handbook method.
   *
   * <p>
   * Formula: U_CH4 = f_CH4 × V_pw × ΔP × 10⁻⁶
   * </p>
   * <p>
   * Where:
   * </p>
   * <ul>
   * <li>f_CH4 = 14 g/(m³·bar) - standard solubility factor</li>
   * <li>V_pw = produced water volume (m³)</li>
   * <li>ΔP = pressure drop (bar)</li>
   * </ul>
   *
   * <p>
   * <b>Note:</b> This conventional method typically overestimates CH4 by ~60% and misses CO2
   * entirely. Use thermodynamic method (CPA-EoS) for accurate reporting.
   * </p>
   *
   * @param producedWaterVolume_m3 produced water volume in m³
   * @param pressureDrop_bar pressure drop in bar
   * @return methane emission in tonnes
   */
  public static double calculateConventionalCH4(double producedWaterVolume_m3,
      double pressureDrop_bar) {
    return HANDBOOK_F_CH4 * producedWaterVolume_m3 * pressureDrop_bar * 1e-6;
  }

  /**
   * Calculate nmVOC emissions using conventional Norwegian handbook method.
   *
   * <p>
   * Formula: U_nmVOC = f_nmVOC × V_pw × ΔP × 10⁻⁶
   * </p>
   *
   * <p>
   * <b>Note:</b> This conventional method does not distinguish individual VOC components. Use
   * thermodynamic method for detailed component breakdown.
   * </p>
   *
   * @param producedWaterVolume_m3 produced water volume in m³
   * @param pressureDrop_bar pressure drop in bar
   * @return nmVOC emission in tonnes
   */
  public static double calculateConventionalNMVOC(double producedWaterVolume_m3,
      double pressureDrop_bar) {
    return HANDBOOK_F_NMVOC * producedWaterVolume_m3 * pressureDrop_bar * 1e-6;
  }

  /**
   * Calculate total emissions using conventional Norwegian handbook method.
   *
   * <p>
   * This is the traditional method used before thermodynamic modeling. It assumes all dissolved gas
   * is hydrocarbon (methane + nmVOC) and ignores CO2 completely.
   * </p>
   *
   * @param producedWaterVolume_m3 produced water volume in m³
   * @param pressureDrop_bar pressure drop in bar
   * @return map with CH4, nmVOC, and CO2eq values in tonnes
   */
  public static java.util.Map<String, Double> calculateConventionalEmissions(
      double producedWaterVolume_m3, double pressureDrop_bar) {
    java.util.Map<String, Double> results = new HashMap<>();

    double ch4 = calculateConventionalCH4(producedWaterVolume_m3, pressureDrop_bar);
    double nmvoc = calculateConventionalNMVOC(producedWaterVolume_m3, pressureDrop_bar);
    double co2eq = ch4 * GWP_METHANE + nmvoc * GWP_NMVOC;

    results.put("CH4_tonnes", ch4);
    results.put("nmVOC_tonnes", nmvoc);
    results.put("CO2_tonnes", 0.0); // Conventional method ignores CO2!
    results.put("CO2eq_tonnes", co2eq);

    return results;
  }

  /**
   * Compare thermodynamic vs conventional method results.
   *
   * <p>
   * Generates a comparison showing how much the conventional method differs from the thermodynamic
   * calculation. Useful for demonstrating the improvement from virtual measurement.
   * </p>
   *
   * @param producedWaterVolume_m3 produced water volume in m³
   * @param pressureDrop_bar pressure drop in bar
   * @return map with comparison metrics
   */
  public java.util.Map<String, Object> compareWithConventionalMethod(double producedWaterVolume_m3,
      double pressureDrop_bar) {
    java.util.Map<String, Object> comparison = new HashMap<>();

    // Thermodynamic results (current calculation, converted to tonnes)
    double thermoHours = producedWaterVolume_m3 / 100.0; // Assume 100 m³/hr basis
    double thermoCH4 = getMethaneEmissionRate("tonnes/year") * thermoHours / HOURS_PER_YEAR;
    double thermoNMVOC = getNMVOCEmissionRate("tonnes/year") * thermoHours / HOURS_PER_YEAR;
    double thermoCO2 = getCO2EmissionRate("tonnes/year") * thermoHours / HOURS_PER_YEAR;
    double thermoCO2eq = thermoCO2 + thermoCH4 * GWP_METHANE + thermoNMVOC * GWP_NMVOC;

    // Conventional results
    double convCH4 = calculateConventionalCH4(producedWaterVolume_m3, pressureDrop_bar);
    double convNMVOC = calculateConventionalNMVOC(producedWaterVolume_m3, pressureDrop_bar);
    double convCO2eq = convCH4 * GWP_METHANE + convNMVOC * GWP_NMVOC;

    // Thermodynamic results
    java.util.Map<String, Double> thermoResults = new HashMap<>();
    thermoResults.put("CH4_tonnes", thermoCH4);
    thermoResults.put("nmVOC_tonnes", thermoNMVOC);
    thermoResults.put("CO2_tonnes", thermoCO2);
    thermoResults.put("CO2eq_tonnes", thermoCO2eq);
    comparison.put("thermodynamicMethod", thermoResults);

    // Conventional results
    java.util.Map<String, Double> convResults = new HashMap<>();
    convResults.put("CH4_tonnes", convCH4);
    convResults.put("nmVOC_tonnes", convNMVOC);
    convResults.put("CO2_tonnes", 0.0);
    convResults.put("CO2eq_tonnes", convCO2eq);
    comparison.put("conventionalMethod", convResults);

    // Differences
    java.util.Map<String, Double> diff = new HashMap<>();
    diff.put("CH4_difference_percent", convCH4 > 0 ? (convCH4 - thermoCH4) / convCH4 * 100 : 0);
    diff.put("CO2eq_difference_percent",
        convCO2eq > 0 ? (convCO2eq - thermoCO2eq) / convCO2eq * 100 : 0);
    diff.put("CO2_missed_tonnes", thermoCO2); // CO2 completely missed by conventional
    comparison.put("differences", diff);

    return comparison;
  }

  // ============================================================================
  // GAS-WATER RATIO (GWR) CALCULATIONS
  // ============================================================================

  /**
   * Calculate Gas-Water Ratio (GWR) at standard conditions.
   *
   * <p>
   * GWR is defined as Sm³ gas evolved per Sm³ water at standard conditions. This is a key metric
   * for validating the thermodynamic model against lab analysis.
   * </p>
   *
   * @param waterFlowRate_m3hr water flow rate in m³/hr at process conditions
   * @return GWR in Sm³ gas / Sm³ water
   */
  public double calculateGWR(double waterFlowRate_m3hr) {
    if (waterFlowRate_m3hr <= 0 || gasStream == null) {
      return 0.0;
    }

    // Get gas standard volume flow
    SystemInterface fluid = gasStream.getFluid();
    if (fluid == null || !fluid.hasPhaseType("gas")) {
      return 0.0;
    }

    // Gas flow at standard conditions (Sm³/hr)
    // Using ideal gas approximation: n*R*T_std/P_std
    double totalGasMoles = fluid.getPhase("gas").getNumberOfMolesInPhase()
        * fluid.getPhase("gas").getFlowRate("mole/sec") * 3600.0;
    double gasStdVolume = totalGasMoles * 22.414 / 1000.0; // Sm³/hr at 0°C, 1 atm

    return gasStdVolume / waterFlowRate_m3hr;
  }

  /**
   * Calculate Gas-Water Ratio from known gas composition.
   *
   * <p>
   * Alternative GWR calculation when you know the total gas moles released.
   * </p>
   *
   * @param gasMoles_kmol total gas moles released (kmol)
   * @param waterVolume_m3 water volume at standard conditions (m³)
   * @return GWR in Sm³ gas / Sm³ water
   */
  public static double calculateGWR(double gasMoles_kmol, double waterVolume_m3) {
    if (waterVolume_m3 <= 0) {
      return 0.0;
    }
    // 1 kmol = 22.414 Sm³ at standard conditions
    return gasMoles_kmol * 22.414 / waterVolume_m3;
  }

  // ============================================================================
  // REPORTING
  // ============================================================================

  /**
   * Generate emissions report as formatted string.
   *
   * @return formatted emissions report
   */
  public String generateReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== EMISSIONS REPORT ===\n\n");

    sb.append("Instantaneous Rates:\n");
    sb.append(String.format("  Total Gas:  %.4f kg/hr%n", getTotalGasRate("kg/hr")));
    sb.append(String.format("  CO2:        %.4f kg/hr%n", getCO2EmissionRate("kg/hr")));
    sb.append(String.format("  Methane:    %.4f kg/hr%n", getMethaneEmissionRate("kg/hr")));
    sb.append(String.format("  nmVOC:      %.4f kg/hr%n", getNMVOCEmissionRate("kg/hr")));
    sb.append(String.format("  Nitrogen:   %.4f kg/hr%n", getNitrogenEmissionRate("kg/hr")));
    sb.append("\n");

    sb.append("Annual Equivalent (8760 hrs):\n");
    sb.append(String.format("  Total Gas:  %.2f tonnes/year%n", getTotalGasRate("tonnes/year")));
    sb.append(String.format("  CO2:        %.2f tonnes/year%n", getCO2EmissionRate("tonnes/year")));
    sb.append(
        String.format("  Methane:    %.2f tonnes/year%n", getMethaneEmissionRate("tonnes/year")));
    sb.append(
        String.format("  nmVOC:      %.2f tonnes/year%n", getNMVOCEmissionRate("tonnes/year")));
    sb.append("\n");

    sb.append("CO2 Equivalents (GWP-100):\n");
    sb.append(String.format("  Instantaneous: %.2f kg CO2eq/hr%n", getCO2Equivalents("kg/hr")));
    sb.append(String.format("  Annual:        %.2f tonnes CO2eq/year%n",
        getCO2Equivalents("tonnes/year")));

    if (totalRunTime_hours > 0) {
      sb.append("\nCumulative (").append(String.format("%.1f", totalRunTime_hours))
          .append(" hours):\n");
      sb.append(String.format("  CO2:        %.2f tonnes%n", getCumulativeCO2("tonnes")));
      sb.append(String.format("  Methane:    %.2f tonnes%n", getCumulativeMethane("tonnes")));
      sb.append(String.format("  nmVOC:      %.2f tonnes%n", getCumulativeNMVOC("tonnes")));
      sb.append(
          String.format("  CO2eq:      %.2f tonnes%n", getCumulativeCO2Equivalents("tonnes")));
    }

    return sb.toString();
  }

  /**
   * Get emissions data as a map for JSON export or database storage.
   *
   * @return map of emission data
   */
  public Map<String, Object> toMap() {
    Map<String, Object> data = new HashMap<>();

    // Instantaneous rates
    Map<String, Double> rates = new HashMap<>();
    rates.put("totalGas_kghr", getTotalGasRate("kg/hr"));
    rates.put("CO2_kghr", getCO2EmissionRate("kg/hr"));
    rates.put("methane_kghr", getMethaneEmissionRate("kg/hr"));
    rates.put("nmVOC_kghr", getNMVOCEmissionRate("kg/hr"));
    rates.put("nitrogen_kghr", getNitrogenEmissionRate("kg/hr"));
    rates.put("CO2eq_kghr", getCO2Equivalents("kg/hr"));
    data.put("instantaneousRates", rates);

    // Annual equivalent
    Map<String, Double> annual = new HashMap<>();
    annual.put("totalGas_tonnesperyear", getTotalGasRate("tonnes/year"));
    annual.put("CO2_tonnesperyear", getCO2EmissionRate("tonnes/year"));
    annual.put("methane_tonnesperyear", getMethaneEmissionRate("tonnes/year"));
    annual.put("nmVOC_tonnesperyear", getNMVOCEmissionRate("tonnes/year"));
    annual.put("CO2eq_tonnesperyear", getCO2Equivalents("tonnes/year"));
    data.put("annualEquivalent", annual);

    // Cumulative
    if (totalRunTime_hours > 0) {
      Map<String, Double> cumulative = new HashMap<>();
      cumulative.put("runTime_hours", totalRunTime_hours);
      cumulative.put("CO2_tonnes", getCumulativeCO2("tonnes"));
      cumulative.put("methane_tonnes", getCumulativeMethane("tonnes"));
      cumulative.put("nmVOC_tonnes", getCumulativeNMVOC("tonnes"));
      cumulative.put("CO2eq_tonnes", getCumulativeCO2Equivalents("tonnes"));
      data.put("cumulative", cumulative);
    }

    // Gas composition
    data.put("gasComposition_mass", getGasCompositionMass());
    data.put("gasComposition_mole", getGasCompositionMole());

    return data;
  }

  // ============================================================================
  // PRIVATE HELPERS
  // ============================================================================

  private void resetRates() {
    co2EmissionRate = 0.0;
    methaneEmissionRate = 0.0;
    ethaneEmissionRate = 0.0;
    propaneEmissionRate = 0.0;
    butanesEmissionRate = 0.0;
    pentanesEmissionRate = 0.0;
    heavierEmissionRate = 0.0;
    nitrogenEmissionRate = 0.0;
    waterVaporRate = 0.0;
    totalGasRate = 0.0;
  }

  private double getComponentRate(PhaseInterface phase, String componentName) {
    if (phase.hasComponent(componentName)) {
      return phase.getComponent(componentName).getFlowRate("kg/sec");
    }
    return 0.0;
  }

  private boolean isHeavierHydrocarbon(String name) {
    // Check for C6+ components (heavier hydrocarbons in nmVOC)
    if (name.startsWith("c6") || name.startsWith("nc6") || name.startsWith("n-hexane")
        || name.startsWith("hexane")) {
      return true;
    }
    if (name.startsWith("c7") || name.startsWith("c8") || name.startsWith("c9")
        || name.startsWith("c10")) {
      return true;
    }
    // TBP fractions
    if (name.matches("c\\d+.*") && !name.equals("co2")) {
      try {
        int carbonNum = Integer.parseInt(name.replaceAll("[^0-9]", "").substring(0, 1));
        return carbonNum >= 6;
      } catch (Exception e) {
        return false;
      }
    }
    return false;
  }

  private double convertRate(double rateKgSec, String unit) {
    if (unit == null) {
      return rateKgSec;
    }

    switch (unit.toLowerCase()) {
      case "kg/sec":
      case "kg/s":
        return rateKgSec;
      case "kg/hr":
      case "kg/h":
        return rateKgSec * 3600.0;
      case "kg/day":
      case "kg/d":
        return rateKgSec * 3600.0 * 24.0;
      case "tonnes/hr":
      case "t/hr":
        return rateKgSec * 3600.0 / 1000.0;
      case "tonnes/day":
      case "t/day":
        return rateKgSec * 3600.0 * 24.0 / 1000.0;
      case "tonnes/year":
      case "t/year":
        return rateKgSec * 3600.0 * HOURS_PER_YEAR / 1000.0;
      default:
        return rateKgSec;
    }
  }
}
