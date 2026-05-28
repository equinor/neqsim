package neqsim.process.hydrogen;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.adsorber.PSACascade;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.reactor.ReformerFurnace;
import neqsim.process.equipment.reactor.WaterGasShiftReactor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.ComponentCaptureUnit;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Builder for a blue-hydrogen plant template based on SMR, shift conversion, PSA, and CO2 export.
 *
 * <p>
 * The template wires a fired SMR front end to high-temperature and low-temperature water-gas shift,
 * cooled condensate knock-out, selective CO2 capture, CO2 compression/export, PSA purification, H2
 * drying, H2 compression/export, and carbon-intensity reporting. The separation blocks are
 * screening-grade placeholders, but their stream interfaces allow users to replace them later with
 * detailed amine, membrane, molecular-sieve, or compression-train models without changing the route
 * boundary.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class BlueHydrogenPlantBuilder extends SMRHydrogenPlantBuilder {
  /** Molar mass of hydrogen in kg/mol. */
  private static final double H2_MOLAR_MASS_KG_PER_MOL = 0.00201588;

  /** Molar mass of carbon dioxide in kg/mol. */
  private static final double CO2_MOLAR_MASS_KG_PER_MOL = 0.0440095;

  /** Target fraction of process CO2 captured downstream of shift/PSA tail gas handling. */
  private double co2CaptureFraction = 0.90;

  /** High-temperature shift target temperature in Kelvin. */
  private double highTemperatureShiftTemperatureK = 623.15;

  /** Low-temperature shift target temperature in Kelvin. */
  private double lowTemperatureShiftTemperatureK = 473.15;

  /** Shift cooler outlet temperature in Kelvin before condensate knock-out. */
  private double shiftedGasCoolerOutletTemperatureK = 313.15;

  /** CO2 export compressor discharge pressure in bara. */
  private double co2ExportPressureBara = 110.0;

  /** H2 export compressor discharge pressure in bara. */
  private double h2ExportPressureBara = 100.0;

  /** Water removal fraction in the H2 dryer template. */
  private double h2DryerWaterRemovalFraction = 0.995;

  /** CO2 compressor isentropic efficiency. */
  private double co2CompressorEfficiency = 0.75;

  /** H2 compressor isentropic efficiency. */
  private double h2CompressorEfficiency = 0.75;

  /** Fired reformer furnace from the most recent build. */
  private ReformerFurnace reformerFurnace;

  /** High-temperature shift reactor from the most recent build. */
  private WaterGasShiftReactor highTemperatureShiftReactor;

  /** Low-temperature shift reactor from the most recent build. */
  private WaterGasShiftReactor lowTemperatureShiftReactor;

  /** Shifted gas cooler from the most recent build. */
  private Cooler shiftedGasCooler;

  /** Shifted condensate separator from the most recent build. */
  private Separator shiftedCondensateSeparator;

  /** CO2 capture placeholder from the most recent build. */
  private ComponentCaptureUnit co2CaptureUnit;

  /** CO2 export compressor from the most recent build. */
  private Compressor co2ExportCompressor;

  /** PSA cascade from the most recent build. */
  private PSACascade psaCascade;

  /** H2 dryer placeholder from the most recent build. */
  private ComponentCaptureUnit h2Dryer;

  /** H2 export compressor from the most recent build. */
  private Compressor h2ExportCompressor;

  /**
   * Creates a blue-hydrogen builder with blue-H2 default naming.
   */
  public BlueHydrogenPlantBuilder() {
    setName("Blue Hydrogen Plant");
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setName(String name) {
    super.setName(name);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setMethaneFeedMolePerSec(double methaneFeedMolePerSec) {
    super.setMethaneFeedMolePerSec(methaneFeedMolePerSec);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setSteamToCarbonRatio(double steamToCarbonRatio) {
    super.setSteamToCarbonRatio(steamToCarbonRatio);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setFeedTemperature(double feedTemperatureK) {
    super.setFeedTemperature(feedTemperatureK);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setPressure(double pressureBara) {
    super.setPressure(pressureBara);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setReformingTemperature(double reformingTemperatureK) {
    super.setReformingTemperature(reformingTemperatureK);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setFuelToFeedMethaneRatio(double fuelToFeedMethaneRatio) {
    super.setFuelToFeedMethaneRatio(fuelToFeedMethaneRatio);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setIncludePsa(boolean includePsa) {
    super.setIncludePsa(includePsa);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setPsaConfiguration(
      PSACascade.CascadeConfiguration configuration) {
    super.setPsaConfiguration(configuration);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BlueHydrogenPlantBuilder setPsaPerBedRecoveryTarget(double psaPerBedRecoveryTarget) {
    super.setPsaPerBedRecoveryTarget(psaPerBedRecoveryTarget);
    return this;
  }

  /**
   * Sets target CO2 capture fraction.
   *
   * @param fraction capture fraction between zero and one
   * @return this builder
   */
  public BlueHydrogenPlantBuilder setCo2CaptureFraction(double fraction) {
    if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("fraction must be finite and between zero and one");
    }
    this.co2CaptureFraction = fraction;
    return this;
  }

  /**
   * Sets high-temperature shift temperature.
   *
   * @param temperatureK shift temperature in Kelvin
   * @return this builder
   */
  public BlueHydrogenPlantBuilder setHighTemperatureShiftTemperature(double temperatureK) {
    validatePositive(temperatureK, "temperatureK");
    this.highTemperatureShiftTemperatureK = temperatureK;
    return this;
  }

  /**
   * Sets low-temperature shift temperature.
   *
   * @param temperatureK shift temperature in Kelvin
   * @return this builder
   */
  public BlueHydrogenPlantBuilder setLowTemperatureShiftTemperature(double temperatureK) {
    validatePositive(temperatureK, "temperatureK");
    this.lowTemperatureShiftTemperatureK = temperatureK;
    return this;
  }

  /**
   * Sets shifted gas cooler outlet temperature.
   *
   * @param temperatureK cooler outlet temperature in Kelvin
   * @return this builder
   */
  public BlueHydrogenPlantBuilder setShiftedGasCoolerOutletTemperature(double temperatureK) {
    validatePositive(temperatureK, "temperatureK");
    this.shiftedGasCoolerOutletTemperatureK = temperatureK;
    return this;
  }

  /**
   * Sets CO2 export pressure.
   *
   * @param pressureBara CO2 export pressure in bara
   * @return this builder
   */
  public BlueHydrogenPlantBuilder setCo2ExportPressure(double pressureBara) {
    validatePositive(pressureBara, "pressureBara");
    this.co2ExportPressureBara = pressureBara;
    return this;
  }

  /**
   * Sets H2 export pressure.
   *
   * @param pressureBara H2 export pressure in bara
   * @return this builder
   */
  public BlueHydrogenPlantBuilder setH2ExportPressure(double pressureBara) {
    validatePositive(pressureBara, "pressureBara");
    this.h2ExportPressureBara = pressureBara;
    return this;
  }

  /**
   * Sets the H2 dryer water removal fraction.
   *
   * @param fraction water removal fraction between zero and one
   * @return this builder
   */
  public BlueHydrogenPlantBuilder setH2DryerWaterRemovalFraction(double fraction) {
    validateFraction(fraction, "fraction");
    this.h2DryerWaterRemovalFraction = fraction;
    return this;
  }

  /**
   * Gets target CO2 capture fraction.
   *
   * @return capture fraction
   */
  public double getCo2CaptureFraction() {
    return co2CaptureFraction;
  }

  /**
   * Gets the fired reformer furnace from the latest build.
   *
   * @return fired reformer furnace, or null before build
   */
  public ReformerFurnace getReformerFurnace() {
    return reformerFurnace;
  }

  /**
   * Gets the high-temperature shift reactor from the latest build.
   *
   * @return high-temperature shift reactor, or null before build
   */
  public WaterGasShiftReactor getHighTemperatureShiftReactor() {
    return highTemperatureShiftReactor;
  }

  /**
   * Gets the low-temperature shift reactor from the latest build.
   *
   * @return low-temperature shift reactor, or null before build
   */
  public WaterGasShiftReactor getLowTemperatureShiftReactor() {
    return lowTemperatureShiftReactor;
  }

  /**
   * Gets the CO2 capture unit from the latest build.
   *
   * @return CO2 capture unit, or null before build
   */
  public ComponentCaptureUnit getCo2CaptureUnit() {
    return co2CaptureUnit;
  }

  /**
   * Gets the CO2 export compressor from the latest build.
   *
   * @return CO2 export compressor, or null before build
   */
  public Compressor getCo2ExportCompressor() {
    return co2ExportCompressor;
  }

  /**
   * Gets the PSA cascade from the latest build.
   *
   * @return PSA cascade, or null when PSA is disabled or before build
   */
  public PSACascade getPsaCascade() {
    return psaCascade;
  }

  /**
   * Gets the H2 dryer from the latest build.
   *
   * @return H2 dryer, or null before build
   */
  public ComponentCaptureUnit getH2Dryer() {
    return h2Dryer;
  }

  /**
   * Gets the H2 export compressor from the latest build.
   *
   * @return H2 export compressor, or null before build
   */
  public Compressor getH2ExportCompressor() {
    return h2ExportCompressor;
  }

  /**
   * Gets the final H2 product stream.
   *
   * @return H2 product stream after export compression, or null before build
   */
  public StreamInterface getH2ProductStream() {
    return h2ExportCompressor == null ? null : h2ExportCompressor.getOutletStream();
  }

  /**
   * Gets the compressed CO2 export stream.
   *
   * @return compressed CO2 export stream, or null before build
   */
  public StreamInterface getCo2ExportStream() {
    return co2ExportCompressor == null ? null : co2ExportCompressor.getOutletStream();
  }

  /**
   * Gets H2 product mass flow.
   *
   * @return H2 mass flow in kg/hr
   */
  public double getHydrogenProductMassFlowKgPerHour() {
    return getComponentMassFlowKgPerHour(getH2ProductStream(), "hydrogen",
        H2_MOLAR_MASS_KG_PER_MOL);
  }

  /**
   * Gets captured CO2 mass flow.
   *
   * @return captured CO2 mass flow in kg/hr
   */
  public double getCapturedCo2MassFlowKgPerHour() {
    return getComponentMassFlowKgPerHour(getCo2ExportStream(), "CO2", CO2_MOLAR_MASS_KG_PER_MOL);
  }

  /**
   * Gets residual direct CO2-equivalent emissions.
   *
   * @return residual carbon emitted as CO2 equivalent in kg/hr
   */
  public double getResidualCo2EquivalentKgPerHour() {
    double residual = getCarbonAsCo2EquivalentKgPerHour(
        reformerFurnace == null ? null : reformerFurnace.getFlueGasOutStream());
    if (psaCascade != null) {
      residual += getCarbonAsCo2EquivalentKgPerHour(psaCascade.getTailGasStream());
    }
    residual += getCarbonAsCo2EquivalentKgPerHour(getH2ProductStream());
    return residual;
  }

  /**
   * Gets gross carbon throughput that entered capture or emission paths.
   *
   * @return captured plus residual carbon as CO2 equivalent in kg/hr
   */
  public double getGrossCo2EquivalentKgPerHour() {
    return getCapturedCo2MassFlowKgPerHour() + getResidualCo2EquivalentKgPerHour();
  }

  /**
   * Gets residual carbon intensity after capture.
   *
   * @return kg CO2 equivalent per kg H2 product, or NaN if no H2 product exists
   */
  public double getCarbonIntensityKgCO2PerKgH2() {
    double hydrogen = getHydrogenProductMassFlowKgPerHour();
    return hydrogen > 0.0 ? getResidualCo2EquivalentKgPerHour() / hydrogen : Double.NaN;
  }

  /**
   * Gets gross carbon intensity before crediting capture.
   *
   * @return kg CO2 equivalent per kg H2 product, or NaN if no H2 product exists
   */
  public double getGrossCarbonIntensityKgCO2PerKgH2() {
    double hydrogen = getHydrogenProductMassFlowKgPerHour();
    return hydrogen > 0.0 ? getGrossCo2EquivalentKgPerHour() / hydrogen : Double.NaN;
  }

  /**
   * Gets a short capture-readiness description.
   *
   * @return capture-readiness description
   */
  public String getCaptureReadinessSummary() {
    return "SMR + HT/LT WGS + CO2 capture/compression + PSA + H2 drying/compression, "
        + "target CO2 capture fraction " + co2CaptureFraction;
  }

  /** {@inheritDoc} */
  @Override
  public ProcessSystem build() {
    ProcessSystem process = new ProcessSystem();
    String name = getPlantName();
    Stream feed = createMethaneSteamFeed(name + " feed", getMethaneFeedMolePerSec(),
        getSteamToCarbonRatio(), getFeedTemperatureK(), getPressureBara());
    Stream fuel = createMethaneFuel(name + " furnace fuel",
        getMethaneFeedMolePerSec() * getFuelToFeedMethaneRatio(), 298.15, getPressureBara());
    Stream air = createAir(name + " combustion air",
        2.0 * getMethaneFeedMolePerSec() * getFuelToFeedMethaneRatio(), 298.15, getPressureBara());

    reformerFurnace = new ReformerFurnace(name + " reformer furnace", feed);
    reformerFurnace.setFuelInletStream(fuel);
    reformerFurnace.setAirInletStream(air);
    reformerFurnace.setTargetReformingTemperature(getReformingTemperatureK());

    highTemperatureShiftReactor = new WaterGasShiftReactor(name + " high temperature shift",
        reformerFurnace.getSyngasOutStream());
    highTemperatureShiftReactor.setShiftTemperature(highTemperatureShiftTemperatureK);

    lowTemperatureShiftReactor = new WaterGasShiftReactor(name + " low temperature shift",
        highTemperatureShiftReactor.getOutletStream());
    lowTemperatureShiftReactor.setShiftTemperature(lowTemperatureShiftTemperatureK);

    shiftedGasCooler =
        new Cooler(name + " shifted gas cooler", lowTemperatureShiftReactor.getOutletStream());
    shiftedGasCooler.setOutletTemperature(shiftedGasCoolerOutletTemperatureK);

    shiftedCondensateSeparator =
        new Separator(name + " shifted condensate separator", shiftedGasCooler.getOutletStream());

    co2CaptureUnit = new ComponentCaptureUnit(name + " CO2 capture");
    co2CaptureUnit.setComponentName("CO2");
    co2CaptureUnit.setCaptureFraction(co2CaptureFraction);
    co2CaptureUnit.setInletStream(shiftedCondensateSeparator.getGasOutStream());

    co2ExportCompressor =
        new Compressor(name + " CO2 export compressor", co2CaptureUnit.getCapturedStream());
    co2ExportCompressor.setOutletPressure(co2ExportPressureBara);
    co2ExportCompressor.setIsentropicEfficiency(co2CompressorEfficiency);

    StreamInterface h2ConditioningFeed = co2CaptureUnit.getTreatedStream();
    psaCascade = null;
    if (isIncludePsa()) {
      psaCascade = new PSACascade(name + " PSA", co2CaptureUnit.getTreatedStream());
      psaCascade.setConfiguration(getPsaConfiguration());
      psaCascade.setPerBedRecoveryTarget(getPsaPerBedRecoveryTarget());
      h2ConditioningFeed = psaCascade.getOutletStream();
    }

    h2Dryer = new ComponentCaptureUnit(name + " H2 dryer");
    h2Dryer.setComponentName("water");
    h2Dryer.setCaptureFraction(h2DryerWaterRemovalFraction);
    h2Dryer.setInletStream(h2ConditioningFeed);

    h2ExportCompressor = new Compressor(name + " H2 export compressor", h2Dryer.getTreatedStream());
    h2ExportCompressor.setOutletPressure(h2ExportPressureBara);
    h2ExportCompressor.setIsentropicEfficiency(h2CompressorEfficiency);

    addUnits(process, feed, fuel, air);
    connectUnits(process, feed, fuel, air);
    return process;
  }

  /**
   * Gets a compact result map for reporting and notebooks.
   *
   * @return ordered result map with blue-H2 chain metrics
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("co2CaptureFractionTarget", co2CaptureFraction);
    results.put("highTemperatureShiftConversion", highTemperatureShiftReactor == null ? Double.NaN
        : highTemperatureShiftReactor.getCarbonMonoxideConversion());
    results.put("lowTemperatureShiftConversion", lowTemperatureShiftReactor == null ? Double.NaN
        : lowTemperatureShiftReactor.getCarbonMonoxideConversion());
    results.put("capturedCo2_kg_per_hr", getCapturedCo2MassFlowKgPerHour());
    results.put("residualCo2e_kg_per_hr", getResidualCo2EquivalentKgPerHour());
    results.put("grossCo2e_kg_per_hr", getGrossCo2EquivalentKgPerHour());
    results.put("h2Product_kg_per_hr", getHydrogenProductMassFlowKgPerHour());
    results.put("carbonIntensity_kgCO2_per_kgH2", getCarbonIntensityKgCO2PerKgH2());
    results.put("grossCarbonIntensity_kgCO2_per_kgH2", getGrossCarbonIntensityKgCO2PerKgH2());
    results.put("psaH2Purity", psaCascade == null ? Double.NaN : psaCascade.getH2Purity());
    results.put("psaH2Recovery", psaCascade == null ? Double.NaN : psaCascade.getH2Recovery());
    return results;
  }

  /**
   * Serializes current builder results.
   *
   * @return JSON representation of current blue-H2 metrics
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(getResults());
  }

  /**
   * Adds all blue-H2 process units to the process system.
   *
   * @param process process system to populate
   * @param feed process methane/steam feed
   * @param fuel furnace fuel stream
   * @param air combustion air stream
   */
  private void addUnits(ProcessSystem process, Stream feed, Stream fuel, Stream air) {
    process.add(feed);
    process.add(fuel);
    process.add(air);
    process.add(reformerFurnace);
    process.add(highTemperatureShiftReactor);
    process.add(lowTemperatureShiftReactor);
    process.add(shiftedGasCooler);
    process.add(shiftedCondensateSeparator);
    process.add(co2CaptureUnit);
    process.add(co2ExportCompressor);
    if (psaCascade != null) {
      process.add(psaCascade);
    }
    process.add(h2Dryer);
    process.add(h2ExportCompressor);
  }

  /**
   * Records explicit process connections for topology-aware tools.
   *
   * @param process process system to update
   * @param feed process methane/steam feed
   * @param fuel furnace fuel stream
   * @param air combustion air stream
   */
  private void connectUnits(ProcessSystem process, Stream feed, Stream fuel, Stream air) {
    process.connect(feed.getName(), reformerFurnace.getName());
    process.connect(fuel.getName(), reformerFurnace.getName());
    process.connect(air.getName(), reformerFurnace.getName());
    process.connect(reformerFurnace.getName(), highTemperatureShiftReactor.getName());
    process.connect(highTemperatureShiftReactor.getName(), lowTemperatureShiftReactor.getName());
    process.connect(lowTemperatureShiftReactor.getName(), shiftedGasCooler.getName());
    process.connect(shiftedGasCooler.getName(), shiftedCondensateSeparator.getName());
    process.connect(shiftedCondensateSeparator.getName(), co2CaptureUnit.getName());
    process.connect(co2CaptureUnit.getName(), co2ExportCompressor.getName());
    if (psaCascade != null) {
      process.connect(co2CaptureUnit.getName(), psaCascade.getName());
      process.connect(psaCascade.getName(), h2Dryer.getName());
    } else {
      process.connect(co2CaptureUnit.getName(), h2Dryer.getName());
    }
    process.connect(h2Dryer.getName(), h2ExportCompressor.getName());
  }

  /**
   * Gets selected component mass flow from a stream.
   *
   * @param stream stream to inspect
   * @param componentName component name
   * @param molarMassKgPerMol component molar mass in kg/mol
   * @return component mass flow in kg/hr
   */
  private double getComponentMassFlowKgPerHour(StreamInterface stream, String componentName,
      double molarMassKgPerMol) {
    double moles =
        getComponentMoles(stream == null ? null : stream.getThermoSystem(), componentName);
    return moles * molarMassKgPerMol * 3600.0;
  }

  /**
   * Gets carbon flow expressed as eventual CO2 emissions.
   *
   * @param stream stream containing carbon species
   * @return carbon mass flow as CO2 equivalent in kg/hr
   */
  private double getCarbonAsCo2EquivalentKgPerHour(StreamInterface stream) {
    if (stream == null || stream.getThermoSystem() == null) {
      return 0.0;
    }
    SystemInterface system = stream.getThermoSystem();
    double carbonMoles = getComponentMoles(system, "CO2") + getComponentMoles(system, "CO")
        + getComponentMoles(system, "methane");
    return carbonMoles * CO2_MOLAR_MASS_KG_PER_MOL * 3600.0;
  }

  /**
   * Gets component moles from a thermodynamic system.
   *
   * @param system thermodynamic system
   * @param componentName component name
   * @return component moles in the stream basis, or zero when absent
   */
  private double getComponentMoles(SystemInterface system, String componentName) {
    try {
      if (system != null && system.hasComponent(componentName)) {
        return Math.max(0.0, system.getComponent(componentName).getNumberOfmoles());
      }
    } catch (Exception ex) {
      return 0.0;
    }
    return 0.0;
  }

  /**
   * Validates positive finite values.
   *
   * @param value value to validate
   * @param parameterName parameter name used in exception messages
   */
  private void validatePositive(double value, String parameterName) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(parameterName + " must be finite and greater than zero");
    }
  }

  /**
   * Validates a closed unit interval fraction.
   *
   * @param value fraction value
   * @param parameterName parameter name used in exception messages
   */
  private void validateFraction(double value, String parameterName) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(
          parameterName + " must be finite and between zero and one");
    }
  }
}
