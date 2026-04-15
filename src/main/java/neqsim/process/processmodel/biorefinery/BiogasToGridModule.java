package neqsim.process.processmodel.biorefinery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.reactor.AnaerobicDigester;
import neqsim.process.equipment.splitter.BiogasUpgrader;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Pre-built biorefinery module for biogas-to-grid injection.
 *
 * <p>
 * Composes an {@link AnaerobicDigester}, {@link BiogasUpgrader}, compressor, and cooler into a
 * complete biogas-to-grid process. The module takes a substrate feed stream and produces a
 * grid-quality biomethane stream at the specified injection pressure.
 * </p>
 *
 * <p>
 * The internal process is:
 * </p>
 * <ol>
 * <li>Anaerobic digestion of substrate feed to produce raw biogas</li>
 * <li>Biogas upgrading (PSA, membrane, water scrubbing, or amine) to biomethane</li>
 * <li>Compression to grid injection pressure</li>
 * <li>Cooling to grid injection temperature</li>
 * </ol>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BiogasToGridModule extends ProcessModule {
  private static final long serialVersionUID = 1004L;
  private static final Logger logger = LogManager.getLogger(BiogasToGridModule.class);

  // ── Configuration ──
  /** Target grid injection pressure in bara. */
  private double gridPressureBara = 40.0;
  /** Target grid injection temperature in Celsius. */
  private double gridTemperatureC = 25.0;
  /** Anaerobic digester temperature in Celsius. */
  private double digesterTemperatureC = 37.0;
  /** Hydraulic retention time in days. */
  private double hydraulicRetentionTimeDays = 25.0;
  /** Upgrading technology. */
  private BiogasUpgrader.UpgradingTechnology upgradingTechnology =
      BiogasUpgrader.UpgradingTechnology.MEMBRANE;
  /** Substrate type for digester. */
  private AnaerobicDigester.SubstrateType substrateType =
      AnaerobicDigester.SubstrateType.FOOD_WASTE;

  // ── Internal equipment ──
  private transient AnaerobicDigester digester;
  private transient BiogasUpgrader upgrader;
  private transient Compressor compressor;
  private transient Cooler cooler;
  private transient StreamInterface feedStream;
  private transient StreamInterface biomethaneOutStream;
  private transient StreamInterface offgasStream;
  private transient StreamInterface digestateStream;

  // ── Results ──
  private double biomethaneFlowNm3PerHour = 0.0;
  private double compressorPowerKW = 0.0;
  private double coolerDutyKW = 0.0;
  private boolean hasRun = false;

  /**
   * Creates a biogas-to-grid module with the given name.
   *
   * @param name module name
   */
  public BiogasToGridModule(String name) {
    super(name);
  }

  /**
   * Sets the grid injection pressure.
   *
   * @param pressureBara grid pressure in bara
   */
  public void setGridPressureBara(double pressureBara) {
    this.gridPressureBara = pressureBara;
  }

  /**
   * Sets the grid injection temperature.
   *
   * @param temperatureC grid temperature in Celsius
   */
  public void setGridTemperatureC(double temperatureC) {
    this.gridTemperatureC = temperatureC;
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
   * Sets the hydraulic retention time.
   *
   * @param days retention time in days
   */
  public void setHydraulicRetentionTimeDays(double days) {
    this.hydraulicRetentionTimeDays = days;
  }

  /**
   * Sets the upgrading technology.
   *
   * @param technology upgrading technology
   */
  public void setUpgradingTechnology(BiogasUpgrader.UpgradingTechnology technology) {
    this.upgradingTechnology = technology;
  }

  /**
   * Sets the substrate type for digestion.
   *
   * @param substrate substrate type
   */
  public void setSubstrateType(AnaerobicDigester.SubstrateType substrate) {
    this.substrateType = substrate;
  }

  /**
   * Sets the feed stream for the module.
   *
   * @param feed substrate feed stream
   */
  public void setFeedStream(StreamInterface feed) {
    this.feedStream = feed;
  }

  /**
   * Returns the biomethane output stream (grid-quality gas).
   *
   * @return biomethane output stream
   */
  public StreamInterface getBiomethaneOutStream() {
    return biomethaneOutStream;
  }

  /**
   * Returns the off-gas stream from upgrading.
   *
   * @return off-gas stream
   */
  public StreamInterface getOffgasStream() {
    return offgasStream;
  }

  /**
   * Returns the digestate output stream.
   *
   * @return digestate stream
   */
  public StreamInterface getDigestateStream() {
    return digestateStream;
  }

  /**
   * Returns the biomethane flow rate.
   *
   * @return biomethane flow in Nm3/hr
   */
  public double getBiomethaneFlowNm3PerHour() {
    return biomethaneFlowNm3PerHour;
  }

  /**
   * Returns the compressor power.
   *
   * @return compressor power in kW
   */
  public double getCompressorPowerKW() {
    return compressorPowerKW;
  }

  /**
   * Returns the cooler duty.
   *
   * @return cooler duty in kW
   */
  public double getCoolerDutyKW() {
    return coolerDutyKW;
  }

  /**
   * Builds and runs the complete biogas-to-grid process.
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    logger.info("Running BiogasToGridModule: " + getName());

    if (feedStream == null) {
      throw new RuntimeException(
          "BiogasToGridModule: feed stream not set. Call setFeedStream() first.");
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

    // ── Step 2: Biogas Upgrading ──
    upgrader = new BiogasUpgrader(getName() + "_upgrader", biogasStream);
    upgrader.setTechnology(upgradingTechnology);

    ProcessSystem upgradingSystem = new ProcessSystem();
    upgradingSystem.add(upgrader);
    upgradingSystem.run(id);

    StreamInterface upgradedBiomethane = upgrader.getBiomethaneOutStream();
    offgasStream = upgrader.getOffgasOutStream();

    // ── Step 3: Compression to grid pressure ──
    compressor = new Compressor(getName() + "_compressor", upgradedBiomethane);
    compressor.setOutletPressure(gridPressureBara);

    ProcessSystem compressionSystem = new ProcessSystem();
    compressionSystem.add(compressor);
    compressionSystem.run(id);

    StreamInterface compressedStream = compressor.getOutletStream();

    // ── Step 4: Cooling to grid temperature ──
    cooler = new Cooler(getName() + "_cooler", compressedStream);
    cooler.setOutTemperature(273.15 + gridTemperatureC);

    ProcessSystem coolingSystem = new ProcessSystem();
    coolingSystem.add(cooler);
    coolingSystem.run(id);

    biomethaneOutStream = cooler.getOutletStream();

    // ── Collect results ──
    try {
      biomethaneFlowNm3PerHour = biomethaneOutStream.getFlowRate("Nm3/hr");
    } catch (Exception e) {
      biomethaneFlowNm3PerHour = biomethaneOutStream.getFlowRate("kg/hr");
    }
    try {
      compressorPowerKW = compressor.getPower() / 1000.0;
    } catch (Exception e) {
      compressorPowerKW = 0.0;
    }
    try {
      coolerDutyKW = Math.abs(cooler.getDuty()) / 1000.0;
    } catch (Exception e) {
      coolerDutyKW = 0.0;
    }

    hasRun = true;
    setCalculationIdentifier(id);
    logger.info("BiogasToGridModule completed: " + getName());
  }

  /**
   * Returns a results map.
   *
   * @return map of result names to values
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("moduleName", getName());
    results.put("processType", "Biogas-to-Grid");
    results.put("gridPressure_bara", gridPressureBara);
    results.put("gridTemperature_C", gridTemperatureC);
    results.put("upgradingTechnology", upgradingTechnology.name());
    results.put("substrateType", substrateType.name());
    results.put("biomethaneFlow_Nm3_per_hr", biomethaneFlowNm3PerHour);
    results.put("compressorPower_kW", compressorPowerKW);
    results.put("coolerDuty_kW", coolerDutyKW);
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
