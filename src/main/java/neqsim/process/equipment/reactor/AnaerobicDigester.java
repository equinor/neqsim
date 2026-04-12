package neqsim.process.equipment.reactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Anaerobic digester for biogas production from organic substrates.
 *
 * <p>
 * Models single-stage continuous stirred-tank reactor (CSTR) anaerobic digestion using
 * stoichiometric conversion of a defined organic substrate to biogas (CH4 + CO2). The digester
 * extends {@link Fermenter} to reuse its reactor vessel, mixing, and stream management
 * infrastructure, adding biogas-specific calculations.
 * </p>
 *
 * <h2>Key Parameters</h2>
 * <ul>
 * <li><b>Organic loading rate (OLR)</b> — kg VS / (m3 * day)</li>
 * <li><b>Hydraulic retention time (HRT)</b> — days</li>
 * <li><b>Volatile solids (VS) destruction</b> — fraction of VS converted</li>
 * <li><b>Specific methane yield</b> — Nm3 CH4 / kg VS destroyed</li>
 * </ul>
 *
 * <h2>Substrate Types</h2>
 *
 * <table>
 * <caption>Typical substrate methane yields</caption>
 * <tr>
 * <th>Substrate</th>
 * <th>Specific CH4 Yield (Nm3/kg VS)</th>
 * <th>Default VS Destruction</th>
 * </tr>
 * <tr>
 * <td>SEWAGE_SLUDGE</td>
 * <td>0.30</td>
 * <td>55%</td>
 * </tr>
 * <tr>
 * <td>FOOD_WASTE</td>
 * <td>0.45</td>
 * <td>80%</td>
 * </tr>
 * <tr>
 * <td>MANURE</td>
 * <td>0.20</td>
 * <td>45%</td>
 * </tr>
 * <tr>
 * <td>CROP_RESIDUE</td>
 * <td>0.28</td>
 * <td>50%</td>
 * </tr>
 * <tr>
 * <td>ENERGY_CROP</td>
 * <td>0.35</td>
 * <td>65%</td>
 * </tr>
 * <tr>
 * <td>CUSTOM</td>
 * <td>user-defined</td>
 * <td>user-defined</td>
 * </tr>
 * </table>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * AnaerobicDigester digester = new AnaerobicDigester("AD-1");
 * digester.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
 * digester.setFeedRate(10000.0, 0.25); // 10 t/hr at 25% total solids
 * digester.setVesselVolume(5000.0);
 * digester.setDigesterTemperature(37.0, "C");
 * digester.run();
 *
 * double biogasFlow = digester.getBiogasFlowRateNm3PerDay();
 * double methaneContent = digester.getMethaneContentPercent();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AnaerobicDigester extends Fermenter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;
  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(AnaerobicDigester.class);

  /**
   * Substrate type enumeration.
   */
  public enum SubstrateType {
    /** Municipal sewage sludge. */
    SEWAGE_SLUDGE(0.30, 0.55, 0.70),
    /** Food waste / kitchen waste. */
    FOOD_WASTE(0.45, 0.80, 0.92),
    /** Animal manure (cattle/pig). */
    MANURE(0.20, 0.45, 0.80),
    /** Crop residues (straw, stalks, husks). */
    CROP_RESIDUE(0.28, 0.50, 0.85),
    /** Energy crops (maize silage, grass silage). */
    ENERGY_CROP(0.35, 0.65, 0.90),
    /** Custom substrate with user-defined parameters. */
    CUSTOM(0.30, 0.50, 0.80);

    /** Specific methane yield in Nm3 CH4 per kg VS destroyed. */
    private final double specificMethaneYield;
    /** Volatile solids destruction fraction (0-1). */
    private final double vsDestruction;
    /** VS/TS ratio (fraction of total solids that are volatile). */
    private final double vstsRatio;

    /**
     * Creates a substrate type enum constant.
     *
     * @param specificMethaneYield Nm3 CH4 / kg VS destroyed
     * @param vsDestruction VS destruction fraction
     * @param vstsRatio VS/TS ratio
     */
    SubstrateType(double specificMethaneYield, double vsDestruction, double vstsRatio) {
      this.specificMethaneYield = specificMethaneYield;
      this.vsDestruction = vsDestruction;
      this.vstsRatio = vstsRatio;
    }

    /**
     * Returns the specific methane yield.
     *
     * @return specific methane yield in Nm3 CH4 / kg VS destroyed
     */
    public double getSpecificMethaneYield() {
      return specificMethaneYield;
    }

    /**
     * Returns the VS destruction fraction.
     *
     * @return VS destruction fraction (0-1)
     */
    public double getVsDestruction() {
      return vsDestruction;
    }

    /**
     * Returns the VS/TS ratio.
     *
     * @return VS/TS ratio
     */
    public double getVstsRatio() {
      return vstsRatio;
    }
  }

  /**
   * Temperature regime enumeration.
   */
  public enum TemperatureRegime {
    /** Mesophilic digestion (30-40 C, typically 35-37 C). */
    MESOPHILIC,
    /** Thermophilic digestion (50-60 C, typically 52-55 C). */
    THERMOPHILIC
  }

  // ── Substrate configuration ──
  /** Substrate type. */
  private SubstrateType substrateType = SubstrateType.SEWAGE_SLUDGE;
  /** Feed rate in kg/hr (total wet mass). */
  private double feedRateKgPerHr = 0.0;
  /** Total solids fraction of the feed (0-1). */
  private double totalSolidsFraction = 0.05;
  /** VS/TS ratio (volatile solids as fraction of total solids). */
  private double vstsFraction = Double.NaN;
  /** Digester temperature in Kelvin. */
  private double digesterTemperature = 273.15 + 37.0;
  /** Temperature regime. */
  private TemperatureRegime temperatureRegime = TemperatureRegime.MESOPHILIC;

  // ── Override parameters ──
  /** Specific methane yield override (Nm3 CH4 / kg VS destroyed). */
  private double specificMethaneYield = Double.NaN;
  /** VS destruction fraction override. */
  private double vsDestruction = Double.NaN;

  // ── Biogas configuration ──
  /** Methane content in biogas (volume fraction, default 0.60). */
  private double methaneFraction = 0.60;

  // ── Output streams ──
  /** Biogas product stream. */
  private StreamInterface biogasOutStream;
  /** Digestate (effluent) outlet stream. */
  private StreamInterface digestateOutStream;

  // ── Results ──
  /** Biogas flow rate in Nm3/day. */
  private double biogasFlowRateNm3PerDay = 0.0;
  /** Methane production rate in Nm3/day. */
  private double methaneProductionNm3PerDay = 0.0;
  /** Organic loading rate in kg VS / (m3 * day). */
  private double organicLoadingRate = 0.0;
  /** Hydraulic retention time in days. */
  private double hydraulicRetentionTimeDays = 0.0;
  /** VS destruction achieved. */
  private double actualVsDestruction = 0.0;
  /** Actual specific methane yield used. */
  private double actualSpecificMethaneYield = 0.0;
  /** Whether the digester has been run. */
  private boolean digesterHasRun = false;

  /**
   * Creates an anaerobic digester with the given name.
   *
   * @param name equipment name
   */
  public AnaerobicDigester(String name) {
    super(name);
    setAerobic(false);
    setIsothermal(true);
  }

  /**
   * Creates an anaerobic digester with the given name and inlet stream.
   *
   * @param name equipment name
   * @param inletStream the sludge/substrate inlet stream
   */
  public AnaerobicDigester(String name, StreamInterface inletStream) {
    super(name, inletStream);
    setAerobic(false);
    setIsothermal(true);
  }

  /**
   * Sets the substrate type. This sets default values for specific methane yield, VS destruction,
   * and VS/TS ratio.
   *
   * @param type the substrate type
   */
  public void setSubstrateType(SubstrateType type) {
    this.substrateType = type;
  }

  /**
   * Gets the substrate type.
   *
   * @return substrate type
   */
  public SubstrateType getSubstrateType() {
    return substrateType;
  }

  /**
   * Sets the feed rate and total solids content.
   *
   * @param feedRateKgPerHr total wet feed rate in kg/hr
   * @param totalSolidsFrac total solids fraction (0-1)
   */
  public void setFeedRate(double feedRateKgPerHr, double totalSolidsFrac) {
    this.feedRateKgPerHr = feedRateKgPerHr;
    this.totalSolidsFraction = Math.max(0.001, Math.min(1.0, totalSolidsFrac));
  }

  /**
   * Gets the feed rate in kg/hr.
   *
   * @return feed rate in kg/hr
   */
  public double getFeedRateKgPerHr() {
    return feedRateKgPerHr;
  }

  /**
   * Gets the total solids fraction.
   *
   * @return total solids fraction (0-1)
   */
  public double getTotalSolidsFraction() {
    return totalSolidsFraction;
  }

  /**
   * Sets the VS/TS ratio (overrides the substrate type default).
   *
   * @param ratio VS/TS ratio (0-1)
   */
  public void setVSTSRatio(double ratio) {
    this.vstsFraction = Math.max(0.0, Math.min(1.0, ratio));
  }

  /**
   * Sets the digester temperature in Kelvin.
   *
   * @param temperatureK temperature in Kelvin
   */
  public void setDigesterTemperature(double temperatureK) {
    this.digesterTemperature = temperatureK;
    updateTemperatureRegime();
  }

  /**
   * Sets the digester temperature with unit specification.
   *
   * @param temperature temperature value
   * @param unit unit string ("K", "C", "F")
   */
  public void setDigesterTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.digesterTemperature = temperature + 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      this.digesterTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.digesterTemperature = temperature;
    }
    updateTemperatureRegime();
  }

  /**
   * Gets the digester temperature in Kelvin.
   *
   * @return digester temperature in K
   */
  public double getDigesterTemperature() {
    return digesterTemperature;
  }

  /**
   * Gets the temperature regime.
   *
   * @return temperature regime
   */
  public TemperatureRegime getTemperatureRegime() {
    return temperatureRegime;
  }

  /**
   * Sets the specific methane yield override.
   *
   * @param yieldNm3PerKgVS specific methane yield in Nm3 CH4 / kg VS destroyed
   */
  public void setSpecificMethaneYield(double yieldNm3PerKgVS) {
    this.specificMethaneYield = yieldNm3PerKgVS;
  }

  /**
   * Sets the VS destruction override.
   *
   * @param fraction VS destruction fraction (0-1)
   */
  public void setVSDestruction(double fraction) {
    this.vsDestruction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Sets the methane fraction in biogas.
   *
   * @param fraction methane volume fraction (0-1)
   */
  public void setMethaneFraction(double fraction) {
    this.methaneFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Returns the biogas outlet stream.
   *
   * @return biogas outlet stream, or null if not yet run
   */
  public StreamInterface getBiogasOutStream() {
    return biogasOutStream;
  }

  /**
   * Returns the digestate outlet stream.
   *
   * @return digestate outlet stream, or null if not yet run
   */
  public StreamInterface getDigestateOutStream() {
    return digestateOutStream;
  }

  /**
   * Returns the biogas flow rate in Nm3/day.
   *
   * @return biogas flow rate
   */
  public double getBiogasFlowRateNm3PerDay() {
    return biogasFlowRateNm3PerDay;
  }

  /**
   * Returns the methane production rate in Nm3/day.
   *
   * @return methane production rate
   */
  public double getMethaneProductionNm3PerDay() {
    return methaneProductionNm3PerDay;
  }

  /**
   * Returns the methane content as a volume percent.
   *
   * @return methane volume percent
   */
  public double getMethaneContentPercent() {
    return methaneFraction * 100.0;
  }

  /**
   * Returns the organic loading rate in kg VS / (m3 * day).
   *
   * @return organic loading rate
   */
  public double getOrganicLoadingRate() {
    return organicLoadingRate;
  }

  /**
   * Returns the hydraulic retention time in days.
   *
   * @return HRT in days
   */
  public double getHydraulicRetentionTimeDays() {
    return hydraulicRetentionTimeDays;
  }

  /**
   * Returns the actual VS destruction achieved.
   *
   * @return VS destruction (0-1)
   */
  public double getActualVsDestruction() {
    return actualVsDestruction;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<StreamInterface>();
    if (biogasOutStream != null) {
      outlets.add(biogasOutStream);
    }
    if (digestateOutStream != null) {
      outlets.add(digestateOutStream);
    }
    return Collections.unmodifiableList(outlets);
  }

  /**
   * Runs the anaerobic digester simulation.
   *
   * @param id UUID for this run
   */
  @Override
  public void run(UUID id) {
    if (feedRateKgPerHr <= 0.0) {
      throw new IllegalStateException("Feed rate must be positive");
    }

    // ── Step 1: Resolve parameters ──
    double effVstsRatio = Double.isNaN(vstsFraction) ? substrateType.getVstsRatio() : vstsFraction;
    actualVsDestruction =
        Double.isNaN(vsDestruction) ? substrateType.getVsDestruction() : vsDestruction;
    actualSpecificMethaneYield =
        Double.isNaN(specificMethaneYield) ? substrateType.getSpecificMethaneYield()
            : specificMethaneYield;

    // ── Step 2: Mass flow calculations ──
    double feedKgPerDay = feedRateKgPerHr * 24.0;
    double tsKgPerDay = feedKgPerDay * totalSolidsFraction;
    double vsKgPerDay = tsKgPerDay * effVstsRatio;
    double vsDestroyedKgPerDay = vsKgPerDay * actualVsDestruction;

    // ── Step 3: Biogas production ──
    methaneProductionNm3PerDay = vsDestroyedKgPerDay * actualSpecificMethaneYield;
    biogasFlowRateNm3PerDay =
        methaneFraction > 0.0 ? methaneProductionNm3PerDay / methaneFraction : 0.0;
    double co2ProductionNm3PerDay = biogasFlowRateNm3PerDay - methaneProductionNm3PerDay;

    // ── Step 4: Operational parameters ──
    double vesselVol = getVesselVolume();
    if (vesselVol > 0) {
      double feedM3PerDay = feedKgPerDay / 1000.0; // approximate water density
      hydraulicRetentionTimeDays = vesselVol / Math.max(1e-10, feedM3PerDay);
      organicLoadingRate = vsKgPerDay / vesselVol;
    }

    // ── Step 5: Create biogas stream ──
    double ch4MolPerHr = (methaneProductionNm3PerDay / 24.0) * 1000.0 / 22.414;
    double co2MolPerHr = (co2ProductionNm3PerDay / 24.0) * 1000.0 / 22.414;
    // Trace H2S in biogas (typically 100-10000 ppm)
    double h2sMolPerHr = ch4MolPerHr * 0.002; // ~0.2% H2S

    SystemInterface biogasFluid = new SystemSrkEos(digesterTemperature, 1.01325);
    biogasFluid.addComponent("methane", Math.max(1e-10, ch4MolPerHr), "mole/hr");
    biogasFluid.addComponent("CO2", Math.max(1e-10, co2MolPerHr), "mole/hr");
    biogasFluid.addComponent("H2S", Math.max(1e-10, h2sMolPerHr), "mole/hr");
    // Trace water vapour saturated at digester temperature
    biogasFluid.addComponent("water", Math.max(1e-10, ch4MolPerHr * 0.05), "mole/hr");
    biogasFluid.setMixingRule("classic");
    biogasFluid.init(0);
    biogasFluid.init(3);

    biogasOutStream = new Stream(getName() + " biogas", biogasFluid);
    biogasOutStream.run(id);

    // ── Step 6: Create digestate stream ──
    double digestateWaterKgPerHr = feedRateKgPerHr * (1.0 - totalSolidsFraction);
    double residualTsKgPerHr = feedRateKgPerHr * totalSolidsFraction - vsDestroyedKgPerDay / 24.0;
    residualTsKgPerHr = Math.max(0.0, residualTsKgPerHr);
    double digestateKgPerHr = digestateWaterKgPerHr + residualTsKgPerHr;

    SystemInterface digestateFluid = new SystemSrkEos(digesterTemperature, 1.01325);
    double waterMolPerHr = digestateWaterKgPerHr / 18.015;
    digestateFluid.addComponent("water", Math.max(1e-10, waterMolPerHr), "mole/hr");
    // Some dissolved CO2 in digestate
    digestateFluid.addComponent("CO2", Math.max(1e-10, co2MolPerHr * 0.05), "mole/hr");
    digestateFluid.setMixingRule("classic");
    digestateFluid.init(0);
    digestateFluid.init(3);

    digestateOutStream = new Stream(getName() + " digestate", digestateFluid);
    digestateOutStream.run(id);

    digesterHasRun = true;
  }

  /**
   * Updates the temperature regime classification based on digester temperature.
   */
  private void updateTemperatureRegime() {
    double tempC = digesterTemperature - 273.15;
    if (tempC >= 45.0) {
      temperatureRegime = TemperatureRegime.THERMOPHILIC;
    } else {
      temperatureRegime = TemperatureRegime.MESOPHILIC;
    }
  }

  /**
   * Returns a map of key results from the digester simulation.
   *
   * @return map of result name to value
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("substrateType", substrateType.name());
    results.put("temperatureRegime", temperatureRegime.name());
    results.put("digesterTemperature_C", digesterTemperature - 273.15);
    results.put("feedRate_kgPerHr", feedRateKgPerHr);
    results.put("totalSolidsFraction", totalSolidsFraction);
    results.put("vesselVolume_m3", getVesselVolume());
    results.put("biogasFlowRate_Nm3PerDay", biogasFlowRateNm3PerDay);
    results.put("methaneProduction_Nm3PerDay", methaneProductionNm3PerDay);
    results.put("methaneContent_percent", methaneFraction * 100.0);
    results.put("organicLoadingRate_kgVSperM3Day", organicLoadingRate);
    results.put("hydraulicRetentionTime_days", hydraulicRetentionTimeDays);
    results.put("vsDestruction", actualVsDestruction);
    results.put("specificMethaneYield_Nm3perKgVS", actualSpecificMethaneYield);
    return results;
  }

  /**
   * Returns a JSON string with the digester results.
   *
   * @return JSON results string
   */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!digesterHasRun) {
      return "AnaerobicDigester '" + getName() + "' (not yet run)";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("AnaerobicDigester '").append(getName()).append("'\n");
    sb.append(String.format("  Substrate: %s, Regime: %s%n", substrateType, temperatureRegime));
    sb.append(String.format("  T = %.1f C, V = %.0f m3%n", digesterTemperature - 273.15,
        getVesselVolume()));
    sb.append(String.format("  Biogas = %.0f Nm3/day (%.0f%% CH4)%n", biogasFlowRateNm3PerDay,
        methaneFraction * 100));
    sb.append(String.format("  CH4 = %.0f Nm3/day%n", methaneProductionNm3PerDay));
    sb.append(String.format("  OLR = %.2f kg VS/(m3*day), HRT = %.1f days%n", organicLoadingRate,
        hydraulicRetentionTimeDays));
    sb.append(String.format("  VS Destruction = %.0f%%%n", actualVsDestruction * 100));
    return sb.toString();
  }
}
