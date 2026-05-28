package neqsim.process.hydrogen;

import neqsim.process.equipment.adsorber.PSACascade;
import neqsim.process.equipment.reactor.PartialOxidationReactor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Builder for a partial-oxidation hydrogen and syngas plant template.
 *
 * <p>
 * The template creates a methane, oxygen, and optional steam feed, routes it through a
 * {@link PartialOxidationReactor}, and can attach a PSA cascade for hydrogen recovery studies.
 * Water-gas shift is intentionally left to downstream user configuration so the same template can
 * support both hydrogen and CO-rich syngas studies.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class POXHydrogenPlantBuilder extends HydrogenPlantBuilderBase {
  /** Plant name used in unit names. */
  private String name = "POX Hydrogen Plant";

  /** Methane feed rate in mole/sec. */
  private double methaneFeedMolePerSec = 100.0;

  /** Oxygen-to-carbon ratio. */
  private double oxygenToCarbonRatio = 0.55;

  /** Steam-to-carbon ratio for soot suppression. */
  private double steamToCarbonRatio = 0.20;

  /** Feed temperature in Kelvin. */
  private double feedTemperatureK = 573.15;

  /** Pressure in bara. */
  private double pressureBara = 30.0;

  /** Whether to include PSA purification. */
  private boolean includePsa = false;

  /**
   * Sets plant name.
   *
   * @param name plant name
   * @return this builder
   */
  public POXHydrogenPlantBuilder setName(String name) {
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
  public POXHydrogenPlantBuilder setMethaneFeedMolePerSec(double methaneFeedMolePerSec) {
    validatePositive(methaneFeedMolePerSec, "methaneFeedMolePerSec");
    this.methaneFeedMolePerSec = methaneFeedMolePerSec;
    return this;
  }

  /**
   * Sets oxygen-to-carbon ratio.
   *
   * @param oxygenToCarbonRatio oxygen-to-carbon ratio
   * @return this builder
   */
  public POXHydrogenPlantBuilder setOxygenToCarbonRatio(double oxygenToCarbonRatio) {
    validatePositive(oxygenToCarbonRatio, "oxygenToCarbonRatio");
    this.oxygenToCarbonRatio = oxygenToCarbonRatio;
    return this;
  }

  /**
   * Sets steam-to-carbon ratio.
   *
   * @param steamToCarbonRatio steam-to-carbon ratio
   * @return this builder
   */
  public POXHydrogenPlantBuilder setSteamToCarbonRatio(double steamToCarbonRatio) {
    if (!Double.isFinite(steamToCarbonRatio) || steamToCarbonRatio < 0.0) {
      throw new IllegalArgumentException("steamToCarbonRatio must be finite and non-negative");
    }
    this.steamToCarbonRatio = steamToCarbonRatio;
    return this;
  }

  /**
   * Sets whether PSA purification is included.
   *
   * @param includePsa true to include PSA
   * @return this builder
   */
  public POXHydrogenPlantBuilder setIncludePsa(boolean includePsa) {
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

    PartialOxidationReactor pox = new PartialOxidationReactor(name + " partial oxidation", feed);
    pox.setOxygenToCarbonTarget(oxygenToCarbonRatio);
    pox.setSteamToCarbonTarget(steamToCarbonRatio);

    process.add(feed);
    process.add(pox);
    process.connect(feed.getName(), pox.getName());

    if (includePsa) {
      PSACascade psa = new PSACascade(name + " PSA", pox.getOutletStream());
      psa.setConfiguration(PSACascade.CascadeConfiguration.BEDS_6);
      psa.setPerBedRecoveryTarget(0.82);
      process.add(psa);
      process.connect(pox.getName(), psa.getName());
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
