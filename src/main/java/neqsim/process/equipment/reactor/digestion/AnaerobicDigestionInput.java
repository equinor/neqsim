package neqsim.process.equipment.reactor.digestion;

import java.io.Serializable;
import neqsim.thermo.characterization.BioFeedstock;

/**
 * Immutable calculation basis passed to an {@link AnaerobicDigestionModel}.
 *
 * @author NeqSim team
 * @version 1.0
 */
public class AnaerobicDigestionInput implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Feedstock characterization. */
  private final BioFeedstock feedstock;
  /** Wet feed rate in kg/hr. */
  private final double wetFeedRateKgPerHr;
  /** Hydraulic retention time in days. */
  private final double hydraulicRetentionTimeDays;
  /** Operating temperature in Kelvin. */
  private final double temperatureK;
  /** Optional VS-destruction override. */
  private final double vsDestructionOverride;
  /** Optional methane-yield override. */
  private final double methaneYieldOverride;
  /** Optional dry-gas methane-fraction override. */
  private final double methaneFractionOverride;
  /** Fraction of feed sulfur released to gas as H2S. */
  private final double sulfurToGasFraction;

  /**
   * Creates an input basis.
   *
   * @param feedstock feedstock characterization
   * @param wetFeedRateKgPerHr wet feed rate in kg/hr
   * @param hydraulicRetentionTimeDays hydraulic retention time in days
   * @param temperatureK operating temperature in Kelvin
   * @param vsDestructionOverride VS-destruction override or NaN
   * @param methaneYieldOverride methane-yield override or NaN
   * @param methaneFractionOverride methane-fraction override or NaN
   * @param sulfurToGasFraction fraction of feed sulfur released as H2S
   */
  public AnaerobicDigestionInput(BioFeedstock feedstock, double wetFeedRateKgPerHr, double hydraulicRetentionTimeDays,
      double temperatureK, double vsDestructionOverride, double methaneYieldOverride, double methaneFractionOverride,
      double sulfurToGasFraction) {
    if (feedstock == null) {
      throw new IllegalArgumentException("Feedstock must be provided");
    }
    if (wetFeedRateKgPerHr <= 0.0) {
      throw new IllegalArgumentException("Wet feed rate must be positive");
    }
    if (hydraulicRetentionTimeDays < 0.0) {
      throw new IllegalArgumentException("Hydraulic retention time cannot be negative");
    }
    if (sulfurToGasFraction < 0.0 || sulfurToGasFraction > 1.0) {
      throw new IllegalArgumentException("Sulfur-to-gas fraction must be between zero and one");
    }
    this.feedstock = feedstock;
    this.wetFeedRateKgPerHr = wetFeedRateKgPerHr;
    this.hydraulicRetentionTimeDays = hydraulicRetentionTimeDays;
    this.temperatureK = temperatureK;
    this.vsDestructionOverride = vsDestructionOverride;
    this.methaneYieldOverride = methaneYieldOverride;
    this.methaneFractionOverride = methaneFractionOverride;
    this.sulfurToGasFraction = sulfurToGasFraction;
  }

  /** @return feedstock characterization */
  public BioFeedstock getFeedstock() {
    return feedstock;
  }

  /** @return wet feed rate in kg/hr */
  public double getWetFeedRateKgPerHr() {
    return wetFeedRateKgPerHr;
  }

  /** @return hydraulic retention time in days */
  public double getHydraulicRetentionTimeDays() {
    return hydraulicRetentionTimeDays;
  }

  /** @return operating temperature in Kelvin */
  public double getTemperatureK() {
    return temperatureK;
  }

  /** @return VS-destruction override or NaN */
  public double getVsDestructionOverride() {
    return vsDestructionOverride;
  }

  /** @return methane-yield override or NaN */
  public double getMethaneYieldOverride() {
    return methaneYieldOverride;
  }

  /** @return dry-gas methane-fraction override or NaN */
  public double getMethaneFractionOverride() {
    return methaneFractionOverride;
  }

  /** @return fraction of feed sulfur released as H2S */
  public double getSulfurToGasFraction() {
    return sulfurToGasFraction;
  }
}
