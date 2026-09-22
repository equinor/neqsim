package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable unit-explicit throughput receipt for a coupled sulfur/nitrogen hydrotreating basis.
 *
 * <p>
 * This class scales an already qualified material-balance receipt from its finite mass basis to hourly rates. It does
 * not change the upstream chemistry or predict reactor, separation, recycle, utility, or product-yield behavior.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenThroughputBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final RefineryHydrotreatingSulfurNitrogenBalance materialBalance;
  private final double feedMassFlowKgPerHour;
  private final double basisScalePerHour;
  private final double productMassFlowKgPerHour;
  private final double initialSulfurMassFlowKgPerHour;
  private final double sulfurRemovedMassFlowKgPerHour;
  private final double remainingSulfurMassFlowKgPerHour;
  private final double initialNitrogenMassFlowKgPerHour;
  private final double nitrogenRemovedMassFlowKgPerHour;
  private final double remainingNitrogenMassFlowKgPerHour;
  private final double sulfurHydrogenConsumedMassFlowKgPerHour;
  private final double nitrogenHydrogenConsumedMassFlowKgPerHour;
  private final double totalHydrogenConsumedMassFlowKgPerHour;
  private final double hydrogenSulfideProducedMassFlowKgPerHour;
  private final double ammoniaProducedMassFlowKgPerHour;
  private final double hydrogenRetainedInLiquidMassFlowKgPerHour;
  private final double totalMassBalanceResidualKgPerHour;
  private final double sulfurBalanceResidualKgPerHour;
  private final double nitrogenBalanceResidualKgPerHour;

  private RefineryHydrotreatingSulfurNitrogenThroughputBalance(
      RefineryHydrotreatingSulfurNitrogenBalance materialBalance, double feedMassFlowKgPerHour,
      double basisScalePerHour, double productMassFlowKgPerHour, double initialSulfurMassFlowKgPerHour,
      double sulfurRemovedMassFlowKgPerHour, double remainingSulfurMassFlowKgPerHour,
      double initialNitrogenMassFlowKgPerHour, double nitrogenRemovedMassFlowKgPerHour,
      double remainingNitrogenMassFlowKgPerHour, double sulfurHydrogenConsumedMassFlowKgPerHour,
      double nitrogenHydrogenConsumedMassFlowKgPerHour, double totalHydrogenConsumedMassFlowKgPerHour,
      double hydrogenSulfideProducedMassFlowKgPerHour, double ammoniaProducedMassFlowKgPerHour,
      double hydrogenRetainedInLiquidMassFlowKgPerHour, double totalMassBalanceResidualKgPerHour,
      double sulfurBalanceResidualKgPerHour, double nitrogenBalanceResidualKgPerHour) {
    this.materialBalance = materialBalance;
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.basisScalePerHour = basisScalePerHour;
    this.productMassFlowKgPerHour = productMassFlowKgPerHour;
    this.initialSulfurMassFlowKgPerHour = initialSulfurMassFlowKgPerHour;
    this.sulfurRemovedMassFlowKgPerHour = sulfurRemovedMassFlowKgPerHour;
    this.remainingSulfurMassFlowKgPerHour = remainingSulfurMassFlowKgPerHour;
    this.initialNitrogenMassFlowKgPerHour = initialNitrogenMassFlowKgPerHour;
    this.nitrogenRemovedMassFlowKgPerHour = nitrogenRemovedMassFlowKgPerHour;
    this.remainingNitrogenMassFlowKgPerHour = remainingNitrogenMassFlowKgPerHour;
    this.sulfurHydrogenConsumedMassFlowKgPerHour = sulfurHydrogenConsumedMassFlowKgPerHour;
    this.nitrogenHydrogenConsumedMassFlowKgPerHour = nitrogenHydrogenConsumedMassFlowKgPerHour;
    this.totalHydrogenConsumedMassFlowKgPerHour = totalHydrogenConsumedMassFlowKgPerHour;
    this.hydrogenSulfideProducedMassFlowKgPerHour = hydrogenSulfideProducedMassFlowKgPerHour;
    this.ammoniaProducedMassFlowKgPerHour = ammoniaProducedMassFlowKgPerHour;
    this.hydrogenRetainedInLiquidMassFlowKgPerHour = hydrogenRetainedInLiquidMassFlowKgPerHour;
    this.totalMassBalanceResidualKgPerHour = totalMassBalanceResidualKgPerHour;
    this.sulfurBalanceResidualKgPerHour = sulfurBalanceResidualKgPerHour;
    this.nitrogenBalanceResidualKgPerHour = nitrogenBalanceResidualKgPerHour;
  }

  /**
   * Scale a qualified coupled material balance to hourly rates.
   *
   * @param materialBalance qualified coupled sulfur/nitrogen receipt
   * @param feedMassFlowKgPerHour fresh liquid feed rate in kg/h
   * @return immutable unit-explicit throughput receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenThroughputBalance calculate(
      RefineryHydrotreatingSulfurNitrogenBalance materialBalance, double feedMassFlowKgPerHour) {
    Objects.requireNonNull(materialBalance, "materialBalance");
    if (!Double.isFinite(feedMassFlowKgPerHour) || feedMassFlowKgPerHour <= 0.0) {
      throw new IllegalArgumentException("feedMassFlowKgPerHour must be finite and positive");
    }

    double scale = feedMassFlowKgPerHour / materialBalance.getFeedMassKg();
    double productRate = materialBalance.getProductMassKg() * scale;
    double initialSulfurRate = materialBalance.getInitialSulfurMassKg() * scale;
    double sulfurRemovedRate = materialBalance.getSulfurRemovedMassKg() * scale;
    double remainingSulfurRate = materialBalance.getRemainingSulfurMassKg() * scale;
    double initialNitrogenRate = materialBalance.getInitialNitrogenMassKg() * scale;
    double nitrogenRemovedRate = materialBalance.getNitrogenRemovedMassKg() * scale;
    double remainingNitrogenRate = materialBalance.getRemainingNitrogenMassKg() * scale;
    double sulfurHydrogenRate = materialBalance.getSulfurHydrogenConsumedMassKg() * scale;
    double nitrogenHydrogenRate = materialBalance.getNitrogenHydrogenConsumedMassKg() * scale;
    double totalHydrogenRate = materialBalance.getTotalHydrogenConsumedMassKg() * scale;
    double hydrogenSulfideRate = materialBalance.getHydrogenSulfideProducedMassKg() * scale;
    double ammoniaRate = materialBalance.getAmmoniaProducedMassKg() * scale;
    double retainedHydrogenRate = materialBalance.getHydrogenRetainedInLiquidMassKg() * scale;

    double totalResidual = feedMassFlowKgPerHour + totalHydrogenRate - productRate - hydrogenSulfideRate - ammoniaRate;
    double sulfurResidual = initialSulfurRate - sulfurRemovedRate - remainingSulfurRate;
    double nitrogenResidual = initialNitrogenRate - nitrogenRemovedRate - remainingNitrogenRate;
    double tolerance = 1.0e-12 * Math.max(1.0, feedMassFlowKgPerHour + totalHydrogenRate);

    if (!allFiniteNonNegative(productRate, initialSulfurRate, sulfurRemovedRate, remainingSulfurRate,
        initialNitrogenRate, nitrogenRemovedRate, remainingNitrogenRate, sulfurHydrogenRate, nitrogenHydrogenRate,
        totalHydrogenRate, hydrogenSulfideRate, ammoniaRate, retainedHydrogenRate)
        || Math.abs(totalResidual) > tolerance || Math.abs(sulfurResidual) > tolerance
        || Math.abs(nitrogenResidual) > tolerance) {
      throw new IllegalArgumentException("material balance does not define finite closed throughput rates");
    }

    return new RefineryHydrotreatingSulfurNitrogenThroughputBalance(materialBalance, feedMassFlowKgPerHour, scale,
        productRate, initialSulfurRate, sulfurRemovedRate, remainingSulfurRate, initialNitrogenRate,
        nitrogenRemovedRate, remainingNitrogenRate, sulfurHydrogenRate, nitrogenHydrogenRate, totalHydrogenRate,
        hydrogenSulfideRate, ammoniaRate, retainedHydrogenRate, totalResidual, sulfurResidual, nitrogenResidual);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream immutable coupled material-balance receipt */
  public RefineryHydrotreatingSulfurNitrogenBalance getMaterialBalance() {
    return materialBalance;
  }

  /** @return fresh liquid feed rate in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return upstream-basis scale in 1/h */
  public double getBasisScalePerHour() {
    return basisScalePerHour;
  }

  /** @return liquid product rate in kg/h */
  public double getProductMassFlowKgPerHour() {
    return productMassFlowKgPerHour;
  }

  /** @return feed sulfur rate in kg/h */
  public double getInitialSulfurMassFlowKgPerHour() {
    return initialSulfurMassFlowKgPerHour;
  }

  /** @return removed sulfur rate in kg/h */
  public double getSulfurRemovedMassFlowKgPerHour() {
    return sulfurRemovedMassFlowKgPerHour;
  }

  /** @return product sulfur rate in kg/h */
  public double getRemainingSulfurMassFlowKgPerHour() {
    return remainingSulfurMassFlowKgPerHour;
  }

  /** @return feed nitrogen rate in kg/h */
  public double getInitialNitrogenMassFlowKgPerHour() {
    return initialNitrogenMassFlowKgPerHour;
  }

  /** @return removed nitrogen rate in kg/h */
  public double getNitrogenRemovedMassFlowKgPerHour() {
    return nitrogenRemovedMassFlowKgPerHour;
  }

  /** @return product nitrogen rate in kg/h */
  public double getRemainingNitrogenMassFlowKgPerHour() {
    return remainingNitrogenMassFlowKgPerHour;
  }

  /** @return sulfur-route H2 consumption in kg/h */
  public double getSulfurHydrogenConsumedMassFlowKgPerHour() {
    return sulfurHydrogenConsumedMassFlowKgPerHour;
  }

  /** @return nitrogen-route H2 consumption in kg/h */
  public double getNitrogenHydrogenConsumedMassFlowKgPerHour() {
    return nitrogenHydrogenConsumedMassFlowKgPerHour;
  }

  /** @return total H2 consumption in kg/h */
  public double getTotalHydrogenConsumedMassFlowKgPerHour() {
    return totalHydrogenConsumedMassFlowKgPerHour;
  }

  /** @return H2S production rate in kg/h */
  public double getHydrogenSulfideProducedMassFlowKgPerHour() {
    return hydrogenSulfideProducedMassFlowKgPerHour;
  }

  /** @return NH3 production rate in kg/h */
  public double getAmmoniaProducedMassFlowKgPerHour() {
    return ammoniaProducedMassFlowKgPerHour;
  }

  /** @return hydrogen retained in the liquid bookkeeping balance in kg/h */
  public double getHydrogenRetainedInLiquidMassFlowKgPerHour() {
    return hydrogenRetainedInLiquidMassFlowKgPerHour;
  }

  /** @return total external mass-balance residual in kg/h */
  public double getTotalMassBalanceResidualKgPerHour() {
    return totalMassBalanceResidualKgPerHour;
  }

  /** @return sulfur-balance residual in kg/h */
  public double getSulfurBalanceResidualKgPerHour() {
    return sulfurBalanceResidualKgPerHour;
  }

  /** @return nitrogen-balance residual in kg/h */
  public double getNitrogenBalanceResidualKgPerHour() {
    return nitrogenBalanceResidualKgPerHour;
  }
}
