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
import neqsim.process.equipment.reactor.digestion.AnaerobicDigestionInput;
import neqsim.process.equipment.reactor.digestion.AnaerobicDigestionModel;
import neqsim.process.equipment.reactor.digestion.AnaerobicDigestionResult;
import neqsim.process.equipment.reactor.digestion.EmpiricalYieldDigestionModel;
import neqsim.process.equipment.reactor.digestion.FirstOrderHydrolysisDigestionModel;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.BioFeedstock;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Anaerobic digester for biogas production from organic substrates.
 *
 * <p>
 * Models single-stage continuous stirred-tank reactor (CSTR) anaerobic digestion using stoichiometric conversion of a
 * defined organic substrate to biogas (CH4 + CO2). The digester extends {@link Fermenter} to reuse its reactor vessel,
 * mixing, and stream management infrastructure, adding biogas-specific calculations.
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
  /** Optional evidence-bearing feedstock characterization. */
  private BioFeedstock feedstock;
  /** Pluggable biochemical conversion model. */
  private AnaerobicDigestionModel digestionModel = new EmpiricalYieldDigestionModel();
  /** Fraction of feed sulfur released to the dry gas as hydrogen sulfide. */
  private double sulfurToGasFraction = 0.10;
  /** Whether the product gas is saturated with water at reactor conditions. */
  private boolean saturateBiogasWithWater = true;

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
  /** Detailed biochemical calculation result. */
  private AnaerobicDigestionResult digestionResult;
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
   * Sets the substrate type. This sets default values for specific methane yield, VS destruction, and VS/TS ratio.
   *
   * @param type the substrate type
   */
  public void setSubstrateType(SubstrateType type) {
    if (type == null) {
      throw new IllegalArgumentException("Substrate type must be provided");
    }
    this.substrateType = type;
    this.feedstock = null;
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
   * Sets an evidence-bearing feedstock characterization.
   *
   * <p>
   * The feedstock supplies solids, elemental, kinetic, gas-composition, and yield properties. A later call to
   * {@link #setFeedRate(double, double)} may still override the as-received total-solids fraction for a scenario.
   * </p>
   *
   * @param characterizedFeedstock feedstock characterization
   */
  public void setFeedstock(BioFeedstock characterizedFeedstock) {
    if (characterizedFeedstock == null) {
      throw new IllegalArgumentException("Feedstock must be provided");
    }
    characterizedFeedstock.validate();
    feedstock = characterizedFeedstock;
    totalSolidsFraction = characterizedFeedstock.getTotalSolidsFraction();
    methaneFraction = characterizedFeedstock.getMethaneFraction();
  }

  /**
   * Returns the explicitly configured feedstock.
   *
   * @return configured feedstock, or null when legacy substrate defaults are active
   */
  public BioFeedstock getFeedstock() {
    return feedstock;
  }

  /**
   * Sets the biochemical conversion model.
   *
   * @param model digestion model
   */
  public void setDigestionModel(AnaerobicDigestionModel model) {
    if (model == null) {
      throw new IllegalArgumentException("Digestion model must be provided");
    }
    digestionModel = model;
  }

  /**
   * Selects the residence-time and temperature-sensitive first-order hydrolysis model.
   */
  public void useFirstOrderHydrolysisModel() {
    digestionModel = new FirstOrderHydrolysisDigestionModel();
  }

  /**
   * Returns the active biochemical conversion model.
   *
   * @return digestion model
   */
  public AnaerobicDigestionModel getDigestionModel() {
    return digestionModel;
  }

  /**
   * Returns the last biochemical calculation result.
   *
   * @return digestion result, or null before the first run
   */
  public AnaerobicDigestionResult getDigestionResult() {
    return digestionResult;
  }

  /**
   * Sets the fraction of feed sulfur released as hydrogen sulfide.
   *
   * @param fraction sulfur release fraction between zero and one
   */
  public void setSulfurToGasFraction(double fraction) {
    if (fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("Sulfur-to-gas fraction must be between zero and one");
    }
    sulfurToGasFraction = fraction;
  }

  /**
   * Enables or disables thermodynamic water saturation of the product gas.
   *
   * @param enabled true to saturate at digester conditions
   */
  public void setSaturateBiogasWithWater(boolean enabled) {
    saturateBiogasWithWater = enabled;
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

    // ── Step 1: Resolve parameters and operating conditions ──
    double effVstsRatio = Double.isNaN(vstsFraction)
        ? (feedstock == null ? substrateType.getVstsRatio() : feedstock.getVolatileSolidsFraction())
        : vstsFraction;
    actualSpecificMethaneYield = Double.isNaN(specificMethaneYield) ? substrateType.getSpecificMethaneYield()
        : specificMethaneYield;
    double feedKgPerDay = feedRateKgPerHr * 24.0;
    double vesselVol = getVesselVolume();
    if (vesselVol > 0) {
      double feedM3PerDay = feedKgPerDay / 1000.0; // approximate water density
      hydraulicRetentionTimeDays = vesselVol / Math.max(1e-10, feedM3PerDay);
    } else {
      hydraulicRetentionTimeDays = 0.0;
    }

    BioFeedstock effectiveFeedstock = resolveFeedstock(effVstsRatio);
    double methaneFractionOverride = feedstock == null ? methaneFraction : Double.NaN;
    AnaerobicDigestionInput input = new AnaerobicDigestionInput(effectiveFeedstock, feedRateKgPerHr,
        hydraulicRetentionTimeDays, digesterTemperature, vsDestruction, specificMethaneYield, methaneFractionOverride,
        sulfurToGasFraction);
    digestionResult = digestionModel.calculate(input);

    actualVsDestruction = digestionResult.getVsDestruction();
    actualSpecificMethaneYield = digestionResult.getDestroyedVsKgPerDay() > 0.0
        ? digestionResult.getMethaneNm3PerDay() / digestionResult.getDestroyedVsKgPerDay()
        : 0.0;
    methaneFraction = digestionResult.getMethaneFraction();
    methaneProductionNm3PerDay = digestionResult.getMethaneNm3PerDay();
    biogasFlowRateNm3PerDay = digestionResult.getDryBiogasNm3PerDay();
    organicLoadingRate = vesselVol > 0.0 ? digestionResult.getVolatileSolidsKgPerDay() / vesselVol : 0.0;

    // ── Step 2: Create a dry gas stream and optionally saturate it with water ──
    double ch4MolPerHr = (methaneProductionNm3PerDay / 24.0) * 1000.0 / 22.414;
    double co2MolPerHr = (digestionResult.getCarbonDioxideNm3PerDay() / 24.0) * 1000.0 / 22.414;
    double h2sMolPerHr = (digestionResult.getHydrogenSulfideNm3PerDay() / 24.0) * 1000.0 / 22.414;

    SystemInterface biogasFluid = new SystemSrkEos(digesterTemperature, 1.01325);
    biogasFluid.addComponent("methane", Math.max(1e-10, ch4MolPerHr), "mole/hr");
    biogasFluid.addComponent("CO2", Math.max(1e-10, co2MolPerHr), "mole/hr");
    biogasFluid.addComponent("H2S", Math.max(1e-10, h2sMolPerHr), "mole/hr");
    biogasFluid.setMixingRule("classic");
    if (saturateBiogasWithWater) {
      try {
        new ThermodynamicOperations(biogasFluid).saturateWithWater();
      } catch (Exception e) {
        logger.warn("Could not saturate digester product gas with water; returning dry gas", e);
      }
    }
    biogasFluid.init(3);

    biogasOutStream = new Stream(getName() + " biogas", biogasFluid);
    biogasOutStream.run(id);

    // ── Step 3: Create the aqueous digestate carrier; residual solids remain in the result ledger ──
    double dryGasMassKgPerHr = ch4MolPerHr * 16.043 / 1000.0 + co2MolPerHr * 44.010 / 1000.0
        + h2sMolPerHr * 34.081 / 1000.0;
    double waterVaporKgPerHr = Math.max(0.0, biogasOutStream.getFlowRate("kg/hr") - dryGasMassKgPerHr);
    double digestateWaterKgPerHr = Math.max(1.0e-10,
        (digestionResult.getDigestateKgPerDay() - digestionResult.getDigestateSolidsKgPerDay()) / 24.0
            - waterVaporKgPerHr);
    SystemInterface digestateFluid = new SystemSrkEos(digesterTemperature, 1.01325);
    double waterMolPerHr = digestateWaterKgPerHr / 18.015;
    digestateFluid.addComponent("water", Math.max(1e-10, waterMolPerHr), "mole/hr");
    digestateFluid.setMixingRule("classic");
    digestateFluid.init(3);

    digestateOutStream = new Stream(getName() + " digestate", digestateFluid);
    digestateOutStream.run(id);

    digesterHasRun = true;
    setCalculationIdentifier(id);
  }

  /**
   * Resolves legacy substrate defaults into the same feedstock contract used by pluggable models.
   *
   * @param effectiveVstsRatio volatile-solids fraction selected for the run
   * @return run-specific feedstock characterization
   */
  private BioFeedstock resolveFeedstock(double effectiveVstsRatio) {
    BioFeedstock resolved = feedstock == null ? createLegacyFeedstock() : feedstock.copy();
    double maximumDestruction = Double.isNaN(vsDestruction)
        ? (feedstock == null ? substrateType.getVsDestruction() : resolved.getMaximumVsDestruction())
        : vsDestruction;
    double methaneYield = Double.isNaN(specificMethaneYield)
        ? (feedstock == null ? substrateType.getSpecificMethaneYield() : resolved.getMethaneYieldNm3PerKgDestroyedVs())
        : specificMethaneYield;
    resolved.setSolidsAnalysis(totalSolidsFraction, effectiveVstsRatio, maximumDestruction, resolved.getAshFraction());
    resolved.setDigestionProperties(methaneYield, resolved.getHydrolysisRatePerDay(), methaneFraction);
    resolved.validate();
    return resolved;
  }

  /**
   * Creates an elemental and handling basis for the legacy substrate enumeration.
   *
   * @return legacy-compatible characterization
   */
  private BioFeedstock createLegacyFeedstock() {
    BioFeedstock resolved;
    switch (substrateType) {
    case SEWAGE_SLUDGE:
      resolved = BioFeedstock.library("sewage_sludge");
      break;
    case MANURE:
      resolved = BioFeedstock.library("manure");
      break;
    case CROP_RESIDUE:
    case ENERGY_CROP:
      resolved = BioFeedstock.library("crop_residue");
      break;
    case FOOD_WASTE:
    case CUSTOM:
    default:
      resolved = BioFeedstock.library("food_residue");
      break;
    }
    resolved.setEvidenceReference("legacy substrate-family screening basis");
    return resolved;
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
    if (digestionResult != null) {
      results.put("digestionModel", digestionResult.getModelIdentifier());
      results.put("modelFidelity", digestionResult.getFidelity().name());
      results.put("dryMassClosureFraction", digestionResult.getMassClosureFraction());
      results.put("carbonClosureFraction", digestionResult.getCarbonClosureFraction());
      results.put("digestateSolids_kgPerDay", digestionResult.getDigestateSolidsKgPerDay());
      results.put("calculationWarnings", digestionResult.getWarnings());
    }
    return results;
  }

  /**
   * Returns a JSON string with the digester results.
   *
   * @return JSON results string
   */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(getResults());
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
    sb.append(String.format("  T = %.1f C, V = %.0f m3%n", digesterTemperature - 273.15, getVesselVolume()));
    sb.append(String.format("  Biogas = %.0f Nm3/day (%.0f%% CH4)%n", biogasFlowRateNm3PerDay, methaneFraction * 100));
    sb.append(String.format("  CH4 = %.0f Nm3/day%n", methaneProductionNm3PerDay));
    sb.append(String.format("  OLR = %.2f kg VS/(m3*day), HRT = %.1f days%n", organicLoadingRate,
        hydraulicRetentionTimeDays));
    sb.append(String.format("  VS Destruction = %.0f%%%n", actualVsDestruction * 100));
    return sb.toString();
  }
}
