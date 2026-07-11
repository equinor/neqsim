package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;

/**
 * A gas-lifted producing well with a controllable choke and operational locks (NIP-2).
 *
 * <p>
 * This descriptor couples a {@link GasLiftPerformanceCurve} (oil response to lift gas) with the operational degrees of
 * freedom and constraints an operator actually works with when building a "strupe/&oslash;ke" (choke-back / open-up)
 * list:
 * </p>
 * <ul>
 * <li><b>Choke bound and setting</b> - a maximum allowed choke opening fraction (e.g. {@code 0.60} for a "0-60%" well)
 * and the current opening.</li>
 * <li><b>Operational lock</b> - a hard "keep shut" flag with a reason (sand production, lost communication, life
 * extension, etc.).</li>
 * <li><b>Per-well gas-handling ceiling</b> - a maximum produced-gas rate for the well (a "mye gass" limit).</li>
 * <li><b>Fluid ratios</b> - water cut and gas/oil ratio, used to roll produced water and produced gas up to shared
 * facility constraints.</li>
 * </ul>
 *
 * <p>
 * The choke is modelled at screening level as a linear scale on deliverability: at the maximum allowed opening the well
 * delivers the full {@link GasLiftPerformanceCurve} oil rate, and the rate scales linearly to zero as the opening
 * closes. This keeps the "open the choke until a facility ceiling binds, then choke back the least efficient wells"
 * logic of a strupe/&oslash;ke list transparent. It is not a rigorous choke-pressure model.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see GasLiftPerformanceCurve
 * @see ChokeAndGasLiftAllocationOptimizer
 */
public class ChokeableGasLiftWell implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final GasLiftPerformanceCurve curve;
  private double maxChokeFraction = 1.0;
  private double currentChokeFraction = 0.0;
  private double currentLiftRateSm3PerDay = 0.0;
  private boolean forcedShut = false;
  private String shutReason = "";
  private double gasHandlingLimitSm3PerDay = Double.NaN;
  private double gorSm3PerSm3 = 0.0;
  private double waterCutFraction = 0.0;

  /**
   * Creates a chokeable gas-lifted well.
   *
   * @param name unique well name
   * @param curve gas-lift performance curve giving oil rate versus lift-gas rate
   */
  public ChokeableGasLiftWell(String name, GasLiftPerformanceCurve curve) {
    if (name == null || curve == null) {
      throw new IllegalArgumentException("name and curve must be non-null");
    }
    this.name = name;
    this.curve = curve;
  }

  /**
   * Sets the maximum allowed choke opening fraction.
   *
   * @param maxChokeFraction maximum opening in [0, 1] (e.g. 0.60 for a "0-60%" well)
   * @return this well for chaining
   */
  public ChokeableGasLiftWell setMaxChokeFraction(double maxChokeFraction) {
    this.maxChokeFraction = clamp01(maxChokeFraction);
    return this;
  }

  /**
   * Sets the current choke opening fraction (used as the baseline for the strupe/&oslash;ke recommendation).
   *
   * @param currentChokeFraction current opening in [0, 1]
   * @return this well for chaining
   */
  public ChokeableGasLiftWell setCurrentChokeFraction(double currentChokeFraction) {
    this.currentChokeFraction = clamp01(currentChokeFraction);
    return this;
  }

  /**
   * Sets the current lift-gas injection rate.
   *
   * @param liftRateSm3PerDay current lift-gas rate in Sm3/day
   * @return this well for chaining
   */
  public ChokeableGasLiftWell setCurrentLiftRate(double liftRateSm3PerDay) {
    this.currentLiftRateSm3PerDay = Math.max(0.0, liftRateSm3PerDay);
    return this;
  }

  /**
   * Marks the well as force-shut (a hard operational lock) with a reason.
   *
   * @param forcedShut true to keep the well shut regardless of economics
   * @param reason human-readable lock reason (e.g. "sand production")
   * @return this well for chaining
   */
  public ChokeableGasLiftWell setForcedShut(boolean forcedShut, String reason) {
    this.forcedShut = forcedShut;
    this.shutReason = reason == null ? "" : reason;
    return this;
  }

  /**
   * Sets the per-well produced-gas ceiling (gas-handling limit).
   *
   * @param gasHandlingLimitSm3PerDay maximum produced-gas rate in Sm3/day; use {@code Double.NaN} for no limit
   * @return this well for chaining
   */
  public ChokeableGasLiftWell setGasHandlingLimit(double gasHandlingLimitSm3PerDay) {
    this.gasHandlingLimitSm3PerDay = gasHandlingLimitSm3PerDay;
    return this;
  }

  /**
   * Sets the produced gas/oil ratio.
   *
   * @param gorSm3PerSm3 gas/oil ratio in Sm3 gas per Sm3 oil; must be &ge; 0
   * @return this well for chaining
   */
  public ChokeableGasLiftWell setGor(double gorSm3PerSm3) {
    this.gorSm3PerSm3 = Math.max(0.0, gorSm3PerSm3);
    return this;
  }

  /**
   * Sets the water cut.
   *
   * @param waterCutFraction water cut (water / total liquid) in [0, 1)
   * @return this well for chaining
   */
  public ChokeableGasLiftWell setWaterCut(double waterCutFraction) {
    this.waterCutFraction = Math.max(0.0, Math.min(0.999, waterCutFraction));
    return this;
  }

  /**
   * Returns the oil rate at a given choke opening and lift-gas rate.
   *
   * @param chokeFraction choke opening in [0, maxChoke]
   * @param liftRateSm3PerDay lift-gas rate in Sm3/day
   * @return oil rate in Sm3/day (zero when the well is force-shut)
   */
  public double oilRate(double chokeFraction, double liftRateSm3PerDay) {
    if (forcedShut) {
      return 0.0;
    }
    double frac = chokeScale(chokeFraction);
    return frac * curve.oilRateAt(liftRateSm3PerDay);
  }

  /**
   * Returns the produced-gas rate (formation gas plus injected lift gas) for a given oil rate and lift rate.
   *
   * @param oilRateSm3PerDay oil rate in Sm3/day
   * @param liftRateSm3PerDay injected lift-gas rate in Sm3/day
   * @return produced-gas rate in Sm3/day
   */
  public double producedGasRate(double oilRateSm3PerDay, double liftRateSm3PerDay) {
    return oilRateSm3PerDay * gorSm3PerSm3 + Math.max(0.0, liftRateSm3PerDay);
  }

  /**
   * Returns the produced-water rate for a given oil rate.
   *
   * @param oilRateSm3PerDay oil rate in Sm3/day
   * @return produced-water rate in Sm3/day
   */
  public double producedWaterRate(double oilRateSm3PerDay) {
    if (waterCutFraction <= 0.0) {
      return 0.0;
    }
    return oilRateSm3PerDay * waterCutFraction / (1.0 - waterCutFraction);
  }

  /**
   * Maps a requested choke opening to a deliverability scale in [0, 1] (1 at the maximum allowed opening).
   *
   * @param chokeFraction requested opening
   * @return deliverability scale in [0, 1]
   */
  double chokeScale(double chokeFraction) {
    if (maxChokeFraction <= 0.0) {
      return 0.0;
    }
    double frac = clamp01(chokeFraction) / maxChokeFraction;
    return Math.max(0.0, Math.min(1.0, frac));
  }

  /**
   * Clamps a value to the interval [0, 1].
   *
   * @param value input value
   * @return clamped value
   */
  private static double clamp01(double value) {
    if (Double.isNaN(value)) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, value));
  }

  /**
   * Returns the well name.
   *
   * @return unique well name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the gas-lift performance curve.
   *
   * @return the well's gas-lift performance curve
   */
  public GasLiftPerformanceCurve getCurve() {
    return curve;
  }

  /**
   * Returns the maximum allowed choke opening fraction.
   *
   * @return maximum opening in [0, 1]
   */
  public double getMaxChokeFraction() {
    return maxChokeFraction;
  }

  /**
   * Returns the current choke opening fraction.
   *
   * @return current opening in [0, 1]
   */
  public double getCurrentChokeFraction() {
    return currentChokeFraction;
  }

  /**
   * Returns the current lift-gas rate.
   *
   * @return current lift-gas rate in Sm3/day
   */
  public double getCurrentLiftRate() {
    return currentLiftRateSm3PerDay;
  }

  /**
   * Returns whether the well is force-shut.
   *
   * @return true when the well is locked shut
   */
  public boolean isForcedShut() {
    return forcedShut;
  }

  /**
   * Returns the operational lock reason.
   *
   * @return lock reason, or an empty string when not locked
   */
  public String getShutReason() {
    return shutReason;
  }

  /**
   * Returns the per-well produced-gas ceiling.
   *
   * @return gas-handling limit in Sm3/day, or {@code Double.NaN} when unlimited
   */
  public double getGasHandlingLimit() {
    return gasHandlingLimitSm3PerDay;
  }

  /**
   * Returns the produced gas/oil ratio.
   *
   * @return gas/oil ratio in Sm3/Sm3
   */
  public double getGor() {
    return gorSm3PerSm3;
  }

  /**
   * Returns the water cut.
   *
   * @return water cut in [0, 1)
   */
  public double getWaterCut() {
    return waterCutFraction;
  }
}
