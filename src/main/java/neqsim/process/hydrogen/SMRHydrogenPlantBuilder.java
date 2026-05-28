package neqsim.process.hydrogen;

import neqsim.process.equipment.adsorber.PSACascade;
import neqsim.process.equipment.reactor.ReformerFurnace;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Builder for a steam-methane-reforming hydrogen plant template.
 *
 * <p>
 * The template wires a methane/steam feed, fired reformer furnace, and optional PSA purification
 * cascade. It is intended for repeatable screening studies where users need a complete baseline SMR
 * route before adding water-gas shift, heat recovery, or CO2 capture details.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class SMRHydrogenPlantBuilder extends HydrogenPlantBuilderBase {
  /** Plant name used in unit names. */
  private String name = "SMR Hydrogen Plant";

  /** Methane feed rate in mole/sec. */
  private double methaneFeedMolePerSec = 100.0;

  /** Steam-to-carbon molar ratio. */
  private double steamToCarbonRatio = 3.0;

  /** Tube feed temperature in Kelvin. */
  private double feedTemperatureK = 773.15;

  /** Reformer pressure in bara. */
  private double pressureBara = 25.0;

  /** Target reformer outlet temperature in Kelvin. */
  private double reformingTemperatureK = 1123.15;

  /** Fuel methane fraction relative to process methane feed. */
  private double fuelToFeedMethaneRatio = 0.25;

  /** Whether to include PSA purification. */
  private boolean includePsa = true;

  /** PSA cascade configuration. */
  private PSACascade.CascadeConfiguration psaConfiguration = PSACascade.CascadeConfiguration.BEDS_6;

  /** PSA per-bed recovery target. */
  private double psaPerBedRecoveryTarget = 0.84;

  /**
   * Sets plant name.
   *
   * @param name plant name
   * @return this builder
   */
  public SMRHydrogenPlantBuilder setName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name cannot be null or empty");
    }
    this.name = name;
    return this;
  }

  /**
   * Sets methane feed rate.
   *
   * @param methaneFeedMolePerSec methane feed in mole/sec
   * @return this builder
   */
  public SMRHydrogenPlantBuilder setMethaneFeedMolePerSec(double methaneFeedMolePerSec) {
    validatePositive(methaneFeedMolePerSec, "methaneFeedMolePerSec");
    this.methaneFeedMolePerSec = methaneFeedMolePerSec;
    return this;
  }

  /**
   * Sets steam-to-carbon ratio.
   *
   * @param steamToCarbonRatio steam-to-carbon ratio
   * @return this builder
   */
  public SMRHydrogenPlantBuilder setSteamToCarbonRatio(double steamToCarbonRatio) {
    validatePositive(steamToCarbonRatio, "steamToCarbonRatio");
    this.steamToCarbonRatio = steamToCarbonRatio;
    return this;
  }

  /**
   * Sets feed temperature.
   *
   * @param feedTemperatureK feed temperature in Kelvin
   * @return this builder
   */
  public SMRHydrogenPlantBuilder setFeedTemperature(double feedTemperatureK) {
    validatePositive(feedTemperatureK, "feedTemperatureK");
    this.feedTemperatureK = feedTemperatureK;
    return this;
  }

  /**
   * Sets plant pressure.
   *
   * @param pressureBara pressure in bara
   * @return this builder
   */
  public SMRHydrogenPlantBuilder setPressure(double pressureBara) {
    validatePositive(pressureBara, "pressureBara");
    this.pressureBara = pressureBara;
    return this;
  }

  /**
   * Sets reformer outlet temperature.
   *
   * @param reformingTemperatureK reforming temperature in Kelvin
   * @return this builder
   */
  public SMRHydrogenPlantBuilder setReformingTemperature(double reformingTemperatureK) {
    validatePositive(reformingTemperatureK, "reformingTemperatureK");
    this.reformingTemperatureK = reformingTemperatureK;
    return this;
  }

  /**
   * Sets fuel methane fraction relative to process methane feed.
   *
   * @param fuelToFeedMethaneRatio fuel ratio
   * @return this builder
   */
  public SMRHydrogenPlantBuilder setFuelToFeedMethaneRatio(double fuelToFeedMethaneRatio) {
    validatePositive(fuelToFeedMethaneRatio, "fuelToFeedMethaneRatio");
    this.fuelToFeedMethaneRatio = fuelToFeedMethaneRatio;
    return this;
  }

  /**
   * Sets whether PSA purification is included.
   *
   * @param includePsa true to include PSA
   * @return this builder
   */
  public SMRHydrogenPlantBuilder setIncludePsa(boolean includePsa) {
    this.includePsa = includePsa;
    return this;
  }

  /**
   * Sets PSA cascade configuration.
   *
   * @param configuration cascade configuration
   * @return this builder
   */
  public SMRHydrogenPlantBuilder setPsaConfiguration(
      PSACascade.CascadeConfiguration configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration cannot be null");
    }
    this.psaConfiguration = configuration;
    return this;
  }

  /**
   * Builds the process system.
   *
   * @return configured process system
   */
  public ProcessSystem build() {
    ProcessSystem process = new ProcessSystem();
    Stream feed = createMethaneSteamFeed(name + " feed", methaneFeedMolePerSec, steamToCarbonRatio,
        feedTemperatureK, pressureBara);
    Stream fuel = createMethaneFuel(name + " furnace fuel",
        methaneFeedMolePerSec * fuelToFeedMethaneRatio, 298.15, pressureBara);
    Stream air = createAir(name + " combustion air",
        2.0 * methaneFeedMolePerSec * fuelToFeedMethaneRatio, 298.15, pressureBara);

    ReformerFurnace furnace = new ReformerFurnace(name + " reformer furnace", feed);
    furnace.setFuelInletStream(fuel);
    furnace.setAirInletStream(air);
    furnace.setTargetReformingTemperature(reformingTemperatureK);

    process.add(feed);
    process.add(fuel);
    process.add(air);
    process.add(furnace);
    process.connect(feed.getName(), furnace.getName());
    process.connect(fuel.getName(), furnace.getName());
    process.connect(air.getName(), furnace.getName());

    if (includePsa) {
      PSACascade psa = new PSACascade(name + " PSA", furnace.getSyngasOutStream());
      psa.setConfiguration(psaConfiguration);
      psa.setPerBedRecoveryTarget(psaPerBedRecoveryTarget);
      process.add(psa);
      process.connect(furnace.getName(), psa.getName());
    }
    return process;
  }

  /**
   * Validates positive finite values.
   *
   * @param value value to validate
   * @param parameterName parameter name for exception text
   */
  private void validatePositive(double value, String parameterName) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(parameterName + " must be finite and greater than zero");
    }
  }
}
