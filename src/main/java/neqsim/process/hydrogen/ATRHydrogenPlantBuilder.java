package neqsim.process.hydrogen;

import neqsim.process.equipment.adsorber.PSACascade;
import neqsim.process.equipment.reactor.AutothermalReformer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Builder for an autothermal-reforming hydrogen plant template.
 *
 * <p>
 * The template wires a methane, steam, and oxygen feed into a dedicated
 * {@link AutothermalReformer}, followed by optional PSA purification. It is a practical starting
 * point for ATR versus SMR route comparisons and blue-hydrogen concept screening.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ATRHydrogenPlantBuilder extends HydrogenPlantBuilderBase {
  /** Plant name used in unit names. */
  private String name = "ATR Hydrogen Plant";

  /** Methane feed rate in mole/sec. */
  private double methaneFeedMolePerSec = 100.0;

  /** Steam-to-carbon ratio. */
  private double steamToCarbonRatio = 1.5;

  /** Oxygen-to-carbon ratio. */
  private double oxygenToCarbonRatio = 0.60;

  /** Feed temperature in Kelvin. */
  private double feedTemperatureK = 823.15;

  /** Pressure in bara. */
  private double pressureBara = 30.0;

  /** Whether to include PSA purification. */
  private boolean includePsa = true;

  /** PSA configuration. */
  private PSACascade.CascadeConfiguration psaConfiguration = PSACascade.CascadeConfiguration.BEDS_8;

  /**
   * Sets plant name.
   *
   * @param name plant name
   * @return this builder
   */
  public ATRHydrogenPlantBuilder setName(String name) {
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
  public ATRHydrogenPlantBuilder setMethaneFeedMolePerSec(double methaneFeedMolePerSec) {
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
  public ATRHydrogenPlantBuilder setSteamToCarbonRatio(double steamToCarbonRatio) {
    validatePositive(steamToCarbonRatio, "steamToCarbonRatio");
    this.steamToCarbonRatio = steamToCarbonRatio;
    return this;
  }

  /**
   * Sets oxygen-to-carbon ratio.
   *
   * @param oxygenToCarbonRatio oxygen-to-carbon ratio
   * @return this builder
   */
  public ATRHydrogenPlantBuilder setOxygenToCarbonRatio(double oxygenToCarbonRatio) {
    validatePositive(oxygenToCarbonRatio, "oxygenToCarbonRatio");
    this.oxygenToCarbonRatio = oxygenToCarbonRatio;
    return this;
  }

  /**
   * Sets feed temperature.
   *
   * @param feedTemperatureK feed temperature in Kelvin
   * @return this builder
   */
  public ATRHydrogenPlantBuilder setFeedTemperature(double feedTemperatureK) {
    validatePositive(feedTemperatureK, "feedTemperatureK");
    this.feedTemperatureK = feedTemperatureK;
    return this;
  }

  /**
   * Sets pressure.
   *
   * @param pressureBara pressure in bara
   * @return this builder
   */
  public ATRHydrogenPlantBuilder setPressure(double pressureBara) {
    validatePositive(pressureBara, "pressureBara");
    this.pressureBara = pressureBara;
    return this;
  }

  /**
   * Sets whether PSA purification is included.
   *
   * @param includePsa true to include PSA
   * @return this builder
   */
  public ATRHydrogenPlantBuilder setIncludePsa(boolean includePsa) {
    this.includePsa = includePsa;
    return this;
  }

  /**
   * Builds the process system.
   *
   * @return configured process system
   */
  public ProcessSystem build() {
    ProcessSystem process = new ProcessSystem();
    Stream feed = createMethaneSteamOxygenFeed(name + " feed", methaneFeedMolePerSec,
        steamToCarbonRatio, oxygenToCarbonRatio, feedTemperatureK, pressureBara);

    AutothermalReformer reformer = new AutothermalReformer(name + " autothermal reformer", feed);
    reformer.setOxygenToCarbonTarget(oxygenToCarbonRatio);
    reformer.setSteamToCarbonTarget(steamToCarbonRatio);

    process.add(feed);
    process.add(reformer);
    process.connect(feed.getName(), reformer.getName());

    if (includePsa) {
      PSACascade psa = new PSACascade(name + " PSA", reformer.getOutletStream());
      psa.setConfiguration(psaConfiguration);
      psa.setPerBedRecoveryTarget(0.84);
      process.add(psa);
      process.connect(reformer.getName(), psa.getName());
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
