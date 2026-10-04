package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable caller-scenario thermal-duty receipt for a coupled sulfur/nitrogen hydrotreating screen.
 *
 * <p>
 * The receipt converts caller-supplied sulfur- and nitrogen-removal heat-release factors to process rates and offsets
 * them against a caller-supplied sensible-heating duty. It does not supply reaction enthalpies or calculate process
 * enthalpy.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenThermalDutyBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double SECONDS_PER_HOUR = 3600.0;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt productDistributionReceipt;
  private final double sulfurReactionHeatReleaseMegaJoulePerKgRemoved;
  private final double nitrogenReactionHeatReleaseMegaJoulePerKgRemoved;
  private final double sensibleHeatingDutyMegaWatt;
  private final double sulfurReactionHeatReleaseMegaWatt;
  private final double nitrogenReactionHeatReleaseMegaWatt;
  private final double totalReactionHeatReleaseMegaWatt;
  private final double reactionHeatReleaseMegaWattHourPerTonneFeed;
  private final double sensibleHeatingMegaWattHourPerTonneFeed;
  private final double netExternalDutyMegaWatt;
  private final double externalHeatingDutyMegaWatt;
  private final double externalCoolingDutyMegaWatt;
  private final double dutyClosureResidualMegaWatt;

  private RefineryHydrotreatingSulfurNitrogenThermalDutyBalance(
      RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt productDistributionReceipt,
      double sulfurReactionHeatReleaseMegaJoulePerKgRemoved, double nitrogenReactionHeatReleaseMegaJoulePerKgRemoved,
      double sensibleHeatingDutyMegaWatt, double sulfurReactionHeatReleaseMegaWatt,
      double nitrogenReactionHeatReleaseMegaWatt, double totalReactionHeatReleaseMegaWatt,
      double reactionHeatReleaseMegaWattHourPerTonneFeed, double sensibleHeatingMegaWattHourPerTonneFeed,
      double netExternalDutyMegaWatt, double externalHeatingDutyMegaWatt, double externalCoolingDutyMegaWatt,
      double dutyClosureResidualMegaWatt) {
    this.productDistributionReceipt = productDistributionReceipt;
    this.sulfurReactionHeatReleaseMegaJoulePerKgRemoved = sulfurReactionHeatReleaseMegaJoulePerKgRemoved;
    this.nitrogenReactionHeatReleaseMegaJoulePerKgRemoved = nitrogenReactionHeatReleaseMegaJoulePerKgRemoved;
    this.sensibleHeatingDutyMegaWatt = sensibleHeatingDutyMegaWatt;
    this.sulfurReactionHeatReleaseMegaWatt = sulfurReactionHeatReleaseMegaWatt;
    this.nitrogenReactionHeatReleaseMegaWatt = nitrogenReactionHeatReleaseMegaWatt;
    this.totalReactionHeatReleaseMegaWatt = totalReactionHeatReleaseMegaWatt;
    this.reactionHeatReleaseMegaWattHourPerTonneFeed = reactionHeatReleaseMegaWattHourPerTonneFeed;
    this.sensibleHeatingMegaWattHourPerTonneFeed = sensibleHeatingMegaWattHourPerTonneFeed;
    this.netExternalDutyMegaWatt = netExternalDutyMegaWatt;
    this.externalHeatingDutyMegaWatt = externalHeatingDutyMegaWatt;
    this.externalCoolingDutyMegaWatt = externalCoolingDutyMegaWatt;
    this.dutyClosureResidualMegaWatt = dutyClosureResidualMegaWatt;
  }

  /**
   * Calculate caller-owned reaction-heat and net external-duty bookkeeping.
   *
   * @param productDistributionReceipt qualified coupled product-distribution receipt
   * @param sulfurReactionHeatReleaseMegaJoulePerKgRemoved caller-supplied heat release in MJ per kg sulfur removed
   * @param nitrogenReactionHeatReleaseMegaJoulePerKgRemoved caller-supplied heat release in MJ per kg nitrogen removed
   * @param sensibleHeatingDutyMegaWatt caller-supplied non-reaction heating duty in MW
   * @return immutable thermal-duty receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenThermalDutyBalance calculate(
      RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt productDistributionReceipt,
      double sulfurReactionHeatReleaseMegaJoulePerKgRemoved, double nitrogenReactionHeatReleaseMegaJoulePerKgRemoved,
      double sensibleHeatingDutyMegaWatt) {
    Objects.requireNonNull(productDistributionReceipt, "productDistributionReceipt");
    requireFiniteNonNegative("sulfurReactionHeatReleaseMegaJoulePerKgRemoved",
        sulfurReactionHeatReleaseMegaJoulePerKgRemoved);
    requireFiniteNonNegative("nitrogenReactionHeatReleaseMegaJoulePerKgRemoved",
        nitrogenReactionHeatReleaseMegaJoulePerKgRemoved);
    requireFiniteNonNegative("sensibleHeatingDutyMegaWatt", sensibleHeatingDutyMegaWatt);

    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = productDistributionReceipt
        .getThroughputBalance();
    RefineryHydrotreatingSulfurNitrogenBalance material = throughput.getRecycleBalance().getSupplyBalance()
        .getMaterialBalance();
    double scale = throughput.getBasisScalePerHour();
    double sulfurRemovedKgPerHour = material.getSulfurRemovedMassKg() * scale;
    double nitrogenRemovedKgPerHour = material.getNitrogenRemovedMassKg() * scale;
    double sulfurReactionHeat = sulfurRemovedKgPerHour * sulfurReactionHeatReleaseMegaJoulePerKgRemoved
        / SECONDS_PER_HOUR;
    double nitrogenReactionHeat = nitrogenRemovedKgPerHour * nitrogenReactionHeatReleaseMegaJoulePerKgRemoved
        / SECONDS_PER_HOUR;
    double totalReactionHeat = sulfurReactionHeat + nitrogenReactionHeat;
    double tonnesFeedPerHour = throughput.getFeedMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double reactionHeatPerTonneFeed = totalReactionHeat / tonnesFeedPerHour;
    double sensibleHeatingPerTonneFeed = sensibleHeatingDutyMegaWatt / tonnesFeedPerHour;
    double netExternalDuty = sensibleHeatingDutyMegaWatt - totalReactionHeat;
    double externalHeatingDuty = Math.max(0.0, netExternalDuty);
    double externalCoolingDuty = Math.max(0.0, -netExternalDuty);
    double dutyClosure = externalHeatingDuty - externalCoolingDuty + totalReactionHeat - sensibleHeatingDutyMegaWatt;

    double tolerance = 1.0e-12 * Math.max(1.0, sensibleHeatingDutyMegaWatt + totalReactionHeat);
    if (!allFinite(sulfurReactionHeat, nitrogenReactionHeat, totalReactionHeat, reactionHeatPerTonneFeed,
        sensibleHeatingPerTonneFeed, netExternalDuty, externalHeatingDuty, externalCoolingDuty, dutyClosure)
        || Math.abs(dutyClosure) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed thermal-duty receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenThermalDutyBalance(productDistributionReceipt,
        sulfurReactionHeatReleaseMegaJoulePerKgRemoved, nitrogenReactionHeatReleaseMegaJoulePerKgRemoved,
        sensibleHeatingDutyMegaWatt, sulfurReactionHeat, nitrogenReactionHeat, totalReactionHeat,
        reactionHeatPerTonneFeed, sensibleHeatingPerTonneFeed, netExternalDuty, externalHeatingDuty,
        externalCoolingDuty, dutyClosure);
  }

  private static void requireFiniteNonNegative(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  private static boolean allFinite(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value)) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream qualified coupled product-distribution receipt */
  public RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt getProductDistributionReceipt() {
    return productDistributionReceipt;
  }

  /** @return caller-supplied sulfur reaction-heat release in MJ/kg sulfur removed */
  public double getSulfurReactionHeatReleaseMegaJoulePerKgRemoved() {
    return sulfurReactionHeatReleaseMegaJoulePerKgRemoved;
  }

  /** @return caller-supplied nitrogen reaction-heat release in MJ/kg nitrogen removed */
  public double getNitrogenReactionHeatReleaseMegaJoulePerKgRemoved() {
    return nitrogenReactionHeatReleaseMegaJoulePerKgRemoved;
  }

  /** @return caller-supplied non-reaction sensible-heating duty in MW */
  public double getSensibleHeatingDutyMegaWatt() {
    return sensibleHeatingDutyMegaWatt;
  }

  /** @return sulfur-removal reaction-heat release in MW */
  public double getSulfurReactionHeatReleaseMegaWatt() {
    return sulfurReactionHeatReleaseMegaWatt;
  }

  /** @return nitrogen-removal reaction-heat release in MW */
  public double getNitrogenReactionHeatReleaseMegaWatt() {
    return nitrogenReactionHeatReleaseMegaWatt;
  }

  /** @return total reaction-heat release in MW */
  public double getTotalReactionHeatReleaseMegaWatt() {
    return totalReactionHeatReleaseMegaWatt;
  }

  /** @return reaction-heat release in MWh per tonne liquid feed */
  public double getReactionHeatReleaseMegaWattHourPerTonneFeed() {
    return reactionHeatReleaseMegaWattHourPerTonneFeed;
  }

  /** @return sensible-heating requirement in MWh per tonne liquid feed */
  public double getSensibleHeatingMegaWattHourPerTonneFeed() {
    return sensibleHeatingMegaWattHourPerTonneFeed;
  }

  /** @return signed net external duty in MW; positive is heating and negative is cooling */
  public double getNetExternalDutyMegaWatt() {
    return netExternalDutyMegaWatt;
  }

  /** @return required external heating duty in MW */
  public double getExternalHeatingDutyMegaWatt() {
    return externalHeatingDutyMegaWatt;
  }

  /** @return required external cooling duty in MW */
  public double getExternalCoolingDutyMegaWatt() {
    return externalCoolingDutyMegaWatt;
  }

  /** @return thermal-duty closure residual in MW */
  public double getDutyClosureResidualMegaWatt() {
    return dutyClosureResidualMegaWatt;
  }
}
