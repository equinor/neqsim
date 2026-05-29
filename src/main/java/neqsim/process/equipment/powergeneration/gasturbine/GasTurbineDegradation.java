package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.Serializable;

/**
 * Simple two-component degradation model for an industrial gas turbine.
 *
 * <p>
 * <b>Recoverable losses</b> (compressor fouling) accumulate at roughly 0.04 % heat-rate penalty per
 * 100 fired hours and are reset by an offline water wash. They are capped at a configurable maximum
 * (default 4 %) which normally triggers an unscheduled wash.
 * </p>
 *
 * <p>
 * <b>Non-recoverable losses</b> (blade erosion, tip-clearance opening, coating wear) accumulate at
 * roughly 1 % per major overhaul interval (~25 000 fired hours for an aero-derivative) and are only
 * reset by a hot section / major overhaul.
 * </p>
 *
 * <p>
 * The model is deliberately simple — sufficient for screening NPV studies comparing fleet
 * replacement options. For detailed performance contracts use the OEM degradation model instead.
 * </p>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public class GasTurbineDegradation implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  private double hoursSinceWash = 0.0;
  private double hoursSinceOverhaul = 0.0;
  private double recoverableRatePerHour = 0.04 / 100.0 / 100.0;
  private double recoverableCapFraction = 0.04;
  private double nonRecoverableRatePerHour = 0.01 / 25000.0;
  private double nonRecoverableCapFraction = 0.05;

  /** Create a degradation model with default coefficients (aero-derivative). */
  public GasTurbineDegradation() {}

  /**
   * Get the current recoverable heat-rate penalty.
   *
   * @return fractional heat-rate increase (e.g. 0.02 = +2 %)
   */
  public double getRecoverablePenalty() {
    return Math.min(recoverableCapFraction, recoverableRatePerHour * hoursSinceWash);
  }

  /**
   * Get the current non-recoverable heat-rate penalty.
   *
   * @return fractional heat-rate increase
   */
  public double getNonRecoverablePenalty() {
    return Math.min(nonRecoverableCapFraction, nonRecoverableRatePerHour * hoursSinceOverhaul);
  }

  /**
   * Get the combined heat-rate penalty.
   *
   * @return fractional heat-rate increase
   */
  public double getTotalHeatRatePenalty() {
    return getRecoverablePenalty() + getNonRecoverablePenalty();
  }

  /**
   * Available-power derate factor (combined fouling and wear effect on compressor mass flow and
   * turbine inlet temperature margin).
   *
   * @return multiplier on rated power (≤ 1)
   */
  public double getPowerDerateFactor() {
    // Each 1 % heat-rate penalty costs about 0.5 % power capacity
    double total = getTotalHeatRatePenalty();
    double factor = 1.0 - 0.5 * total;
    if (factor < 0.8) {
      factor = 0.8;
    }
    return factor;
  }

  /**
   * Advance the simulated operating hours.
   *
   * @param hours fired hours to add
   */
  public void addFiredHours(double hours) {
    if (hours <= 0.0) {
      return;
    }
    this.hoursSinceWash += hours;
    this.hoursSinceOverhaul += hours;
  }

  /** Perform an offline water wash — resets recoverable losses to zero. */
  public void offlineWash() {
    this.hoursSinceWash = 0.0;
  }

  /** Perform a major / hot-section overhaul — resets both loss counters. */
  public void majorOverhaul() {
    this.hoursSinceWash = 0.0;
    this.hoursSinceOverhaul = 0.0;
  }

  /**
   * Get hours since the last offline wash.
   *
   * @return hours since last wash
   */
  public double getHoursSinceWash() {
    return hoursSinceWash;
  }

  /**
   * Set hours since the last offline wash.
   *
   * @param hoursSinceWash hours since last wash
   */
  public void setHoursSinceWash(double hoursSinceWash) {
    this.hoursSinceWash = hoursSinceWash;
  }

  /**
   * Get hours since the last major overhaul.
   *
   * @return hours since last overhaul
   */
  public double getHoursSinceOverhaul() {
    return hoursSinceOverhaul;
  }

  /**
   * Set hours since the last major overhaul.
   *
   * @param hoursSinceOverhaul hours since last overhaul
   */
  public void setHoursSinceOverhaul(double hoursSinceOverhaul) {
    this.hoursSinceOverhaul = hoursSinceOverhaul;
  }

  /**
   * Override recoverable degradation rate.
   *
   * @param ratePerHour fractional heat-rate penalty per fired hour
   * @param capFraction maximum total recoverable penalty
   */
  public void setRecoverableRates(double ratePerHour, double capFraction) {
    this.recoverableRatePerHour = ratePerHour;
    this.recoverableCapFraction = capFraction;
  }

  /**
   * Override non-recoverable degradation rate.
   *
   * @param ratePerHour fractional heat-rate penalty per fired hour
   * @param capFraction maximum total non-recoverable penalty
   */
  public void setNonRecoverableRates(double ratePerHour, double capFraction) {
    this.nonRecoverableRatePerHour = ratePerHour;
    this.nonRecoverableCapFraction = capFraction;
  }
}
