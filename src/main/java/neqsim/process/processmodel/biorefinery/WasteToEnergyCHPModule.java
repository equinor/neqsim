package neqsim.process.processmodel.biorefinery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.reactor.AnaerobicDigester;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Pre-built biorefinery module for waste-to-energy combined heat and power (CHP).
 *
 * <p>
 * Composes an {@link AnaerobicDigester}, biogas cleanup, and a CHP engine model into a complete
 * waste-to-energy process. The module takes a substrate feed and produces electricity and useful
 * heat outputs, with associated efficiencies and emissions.
 * </p>
 *
 * <p>
 * The internal process is:
 * </p>
 * <ol>
 * <li>Anaerobic digestion of organic waste to produce biogas</li>
 * <li>Biogas desulphurisation (H2S removal modelled as split factor)</li>
 * <li>CHP engine combustion: CH4 + 2O2 &rarr; CO2 + 2H2O (simplified stoichiometry)</li>
 * <li>Heat recovery from exhaust gas and engine jacket cooling</li>
 * </ol>
 *
 * <p>
 * CHP engine performance defaults are based on typical gas-engine values:
 * </p>
 *
 * <table>
 * <caption>Default CHP engine parameters</caption>
 * <tr>
 * <th>Parameter</th>
 * <th>Value</th>
 * </tr>
 * <tr>
 * <td>Electrical efficiency</td>
 * <td>38%</td>
 * </tr>
 * <tr>
 * <td>Thermal efficiency</td>
 * <td>45%</td>
 * </tr>
 * <tr>
 * <td>Total CHP efficiency</td>
 * <td>83%</td>
 * </tr>
 * </table>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WasteToEnergyCHPModule extends ProcessModule {
  private static final long serialVersionUID = 1006L;
  private static final Logger logger = LogManager.getLogger(WasteToEnergyCHPModule.class);

  // ── CHP engine parameters ──
  /** Electrical efficiency of CHP engine (fraction, 0-1). */
  private double electricalEfficiency = 0.38;
  /** Thermal efficiency of CHP engine (fraction, 0-1). */
  private double thermalEfficiency = 0.45;
  /** Methane lower heating value in kWh/Nm3. */
  private static final double CH4_LHV_KWH_PER_NM3 = 9.97;

  // ── Digester configuration ──
  /** Digester temperature in Celsius. */
  private double digesterTemperatureC = 37.0;
  /** Hydraulic retention time in days. */
  private double hydraulicRetentionTimeDays = 25.0;
  /** Substrate type for digester. */
  private AnaerobicDigester.SubstrateType substrateType =
      AnaerobicDigester.SubstrateType.FOOD_WASTE;

  // ── Engine exhaust temperature ──
  /** Exhaust gas temperature after heat recovery in Celsius. */
  private double exhaustTemperatureC = 120.0;

  // ── Internal equipment ──
  private transient AnaerobicDigester digester;
  private transient StreamInterface feedStream;
  private transient StreamInterface exhaustGasStream;
  private transient StreamInterface digestateStream;

  // ── Results ──
  /** Electrical power output in kW. */
  private double electricalPowerKW = 0.0;
  /** Useful heat output in kW. */
  private double heatOutputKW = 0.0;
  /** Total fuel input in kW (LHV). */
  private double fuelInputKW = 0.0;
  /** Biogas flow rate in Nm3/hr. */
  private double biogasFlowNm3PerHr = 0.0;
  /** Methane flow rate in Nm3/hr. */
  private double methaneFlowNm3PerHr = 0.0;
  /** CO2 emissions from combustion in kg/hr. */
  private double co2EmissionsKgPerHr = 0.0;
  /** Annual electricity production in MWh/year (8000 hr/yr assumed). */
  private double annualElectricityMWh = 0.0;
  /** Annual heat production in MWh/year. */
  private double annualHeatMWh = 0.0;
  /** Total CHP efficiency. */
  private double totalCHPefficiency = 0.0;
  /** Whether module has run. */
  private boolean hasRun = false;

  /** Operating hours per year for annual calculations. */
  private double operatingHoursPerYear = 8000.0;

  /**
   * Creates a waste-to-energy CHP module.
   *
   * @param name module name
   */
  public WasteToEnergyCHPModule(String name) {
    super(name);
  }

  /**
   * Sets the feed stream.
   *
   * @param feed organic waste feed stream
   */
  public void setFeedStream(StreamInterface feed) {
    this.feedStream = feed;
  }

  /**
   * Sets the electrical efficiency of the CHP engine.
   *
   * @param efficiency electrical efficiency (0-1)
   */
  public void setElectricalEfficiency(double efficiency) {
    this.electricalEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Sets the thermal efficiency of the CHP engine.
   *
   * @param efficiency thermal efficiency (0-1)
   */
  public void setThermalEfficiency(double efficiency) {
    this.thermalEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Sets the digester temperature.
   *
   * @param temperatureC digester temperature in Celsius
   */
  public void setDigesterTemperatureC(double temperatureC) {
    this.digesterTemperatureC = temperatureC;
  }

  /**
   * Sets the substrate type.
   *
   * @param substrate substrate type
   */
  public void setSubstrateType(AnaerobicDigester.SubstrateType substrate) {
    this.substrateType = substrate;
  }

  /**
   * Sets the hydraulic retention time.
   *
   * @param days retention time in days
   */
  public void setHydraulicRetentionTimeDays(double days) {
    this.hydraulicRetentionTimeDays = days;
  }

  /**
   * Sets operating hours per year for annual calculations.
   *
   * @param hours operating hours per year
   */
  public void setOperatingHoursPerYear(double hours) {
    this.operatingHoursPerYear = hours;
  }

  /**
   * Returns the electrical power output.
   *
   * @return electrical power in kW
   */
  public double getElectricalPowerKW() {
    return electricalPowerKW;
  }

  /**
   * Returns the useful heat output.
   *
   * @return heat output in kW
   */
  public double getHeatOutputKW() {
    return heatOutputKW;
  }

  /**
   * Returns the total fuel input.
   *
   * @return fuel input in kW (LHV)
   */
  public double getFuelInputKW() {
    return fuelInputKW;
  }

  /**
   * Returns the CO2 emissions from combustion.
   *
   * @return CO2 emissions in kg/hr
   */
  public double getCO2EmissionsKgPerHr() {
    return co2EmissionsKgPerHr;
  }

  /**
   * Returns the total CHP efficiency.
   *
   * @return CHP efficiency (0-1)
   */
  public double getTotalCHPefficiency() {
    return totalCHPefficiency;
  }

  /**
   * Returns the annual electricity production.
   *
   * @return MWh/year
   */
  public double getAnnualElectricityMWh() {
    return annualElectricityMWh;
  }

  /**
   * Returns the annual heat production.
   *
   * @return MWh/year
   */
  public double getAnnualHeatMWh() {
    return annualHeatMWh;
  }

  /**
   * Returns the exhaust gas stream.
   *
   * @return exhaust gas stream
   */
  public StreamInterface getExhaustGasStream() {
    return exhaustGasStream;
  }

  /**
   * Returns the digestate stream.
   *
   * @return digestate stream
   */
  public StreamInterface getDigestateStream() {
    return digestateStream;
  }

  /**
   * Builds and runs the waste-to-energy CHP process.
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    logger.info("Running WasteToEnergyCHPModule: " + getName());

    if (feedStream == null) {
      throw new RuntimeException(
          "WasteToEnergyCHPModule: feed stream not set. Call setFeedStream() first.");
    }

    // ── Step 1: Anaerobic Digestion ──
    digester = new AnaerobicDigester(getName() + "_digester", feedStream);
    digester.setSubstrateType(substrateType);
    digester.setDigesterTemperature(digesterTemperatureC, "C");
    digester.setFeedRate(feedStream.getFlowRate("kg/hr"), 0.25);
    // Set vessel volume from HRT: V = Q_m3/day * HRT_days
    double feedM3PerDay = feedStream.getFlowRate("kg/hr") * 24.0 / 1000.0;
    digester.setVesselVolume(feedM3PerDay * hydraulicRetentionTimeDays);

    ProcessSystem digestionSystem = new ProcessSystem();
    digestionSystem.add(digester);
    digestionSystem.run(id);

    StreamInterface biogasStream = digester.getBiogasOutStream();
    digestateStream = digester.getDigestateOutStream();

    // ── Step 2: Extract biogas composition ──
    SystemInterface biogasFluid = biogasStream.getFluid();
    double methaneMoles = 0.0;
    double co2BiogasMoles = 0.0;
    try {
      methaneMoles = biogasFluid.getPhase(0).getComponent("methane").getNumberOfMolesInPhase();
    } catch (Exception e) {
      // no methane
    }
    try {
      co2BiogasMoles = biogasFluid.getPhase(0).getComponent("CO2").getNumberOfMolesInPhase();
    } catch (Exception e) {
      // no CO2
    }
    double totalBiogasMoles = methaneMoles + co2BiogasMoles;
    double methaneContentFraction = totalBiogasMoles > 0 ? methaneMoles / totalBiogasMoles : 0.60;

    // ── Step 3: CHP Engine Calculation ──
    // Methane flow in Nm3/hr: 1 mole ideal gas at 0C, 1 atm = 22.414 L
    methaneFlowNm3PerHr = methaneMoles * 22.414 / 1000.0;
    biogasFlowNm3PerHr = methaneFlowNm3PerHr / Math.max(methaneContentFraction, 0.01);

    // Fuel energy input
    fuelInputKW = methaneFlowNm3PerHr * CH4_LHV_KWH_PER_NM3;

    // CHP outputs
    electricalPowerKW = fuelInputKW * electricalEfficiency;
    heatOutputKW = fuelInputKW * thermalEfficiency;
    totalCHPefficiency = electricalEfficiency + thermalEfficiency;

    // CO2 emissions: CH4 + 2O2 -> CO2 + 2H2O
    // 1 Nm3 CH4 -> 1 Nm3 CO2 -> 1.964 kg CO2 (at STP, 44.01 g/mol, 22.414 L/mol)
    co2EmissionsKgPerHr = methaneFlowNm3PerHr * 1.964;

    // Annual production
    annualElectricityMWh = electricalPowerKW * operatingHoursPerYear / 1000.0;
    annualHeatMWh = heatOutputKW * operatingHoursPerYear / 1000.0;

    // ── Step 4: Build exhaust gas stream ──
    double exhaustCO2Moles = methaneMoles; // stoichiometric
    double exhaustH2OMoles = methaneMoles * 2.0;
    double exhaustN2Moles = methaneMoles * 2.0 * 3.76; // from air (O2 + 3.76 N2)

    SystemSrkEos exhaustFluid = new SystemSrkEos(273.15 + exhaustTemperatureC, 1.01325);
    exhaustFluid.addComponent("CO2", exhaustCO2Moles > 0 ? exhaustCO2Moles : 1.0e-10);
    exhaustFluid.addComponent("water", exhaustH2OMoles > 0 ? exhaustH2OMoles : 1.0e-10);
    exhaustFluid.addComponent("nitrogen", exhaustN2Moles > 0 ? exhaustN2Moles : 1.0e-10);
    exhaustFluid.setMixingRule("classic");
    exhaustFluid.init(0);
    exhaustFluid.init(3);
    Stream exhaustOut = new Stream(getName() + "_exhaust", exhaustFluid);
    exhaustOut.run(id);
    exhaustGasStream = exhaustOut;

    hasRun = true;
    setCalculationIdentifier(id);
    logger.info("WasteToEnergyCHPModule completed: " + getName());
  }

  /**
   * Returns a results map.
   *
   * @return map of result names to values
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("moduleName", getName());
    results.put("processType", "Waste-to-Energy CHP");
    results.put("substrateType", substrateType.name());
    results.put("digesterTemperature_C", digesterTemperatureC);
    results.put("electricalEfficiency", electricalEfficiency);
    results.put("thermalEfficiency", thermalEfficiency);
    results.put("totalCHPefficiency", totalCHPefficiency);
    results.put("biogasFlow_Nm3_per_hr", biogasFlowNm3PerHr);
    results.put("methaneFlow_Nm3_per_hr", methaneFlowNm3PerHr);
    results.put("fuelInput_kW", fuelInputKW);
    results.put("electricalPower_kW", electricalPowerKW);
    results.put("heatOutput_kW", heatOutputKW);
    results.put("CO2emissions_kg_per_hr", co2EmissionsKgPerHr);
    results.put("annualElectricity_MWh", annualElectricityMWh);
    results.put("annualHeat_MWh", annualHeatMWh);
    results.put("operatingHoursPerYear", operatingHoursPerYear);
    results.put("hasRun", hasRun);
    return results;
  }

  /**
   * Returns a JSON string of results.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }
}
