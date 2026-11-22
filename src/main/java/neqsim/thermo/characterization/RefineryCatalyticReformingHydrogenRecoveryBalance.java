package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable hydrogen-recovery receipt for a catalytic-reforming feed balance.
 *
 * <p>
 * The calculation partitions the hydrogen from a {@link RefineryCatalyticReformingFeedBalance} into recovered and
 * unrecovered hydrogen using a caller-owned recovery fraction. It is an allocation balance, not a separator,
 * phase-equilibrium, purity, compression, or hydrogen-network model.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryCatalyticReformingHydrogenRecoveryBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final RefineryCatalyticReformingFeedBalance feedBalance;
  private final double hydrogenRecoveryFraction;
  private final double producedHydrogenFlowKmolPerHour;
  private final double recoveredHydrogenFlowKmolPerHour;
  private final double unrecoveredHydrogenFlowKmolPerHour;
  private final double producedHydrogenMassFlowKgPerHour;
  private final double recoveredHydrogenMassFlowKgPerHour;
  private final double unrecoveredHydrogenMassFlowKgPerHour;
  private final double recoveredHydrogenKgPerTonneFeed;
  private final double unrecoveredHydrogenKgPerTonneFeed;
  private final double hydrogenMolarBalanceResidualKmolPerHour;
  private final double hydrogenMassBalanceResidualKgPerHour;
  private final double overallMassBalanceResidualKgPerHour;

  private RefineryCatalyticReformingHydrogenRecoveryBalance(RefineryCatalyticReformingFeedBalance feedBalance,
      double hydrogenRecoveryFraction, double producedHydrogenFlowKmolPerHour, double recoveredHydrogenFlowKmolPerHour,
      double unrecoveredHydrogenFlowKmolPerHour, double producedHydrogenMassFlowKgPerHour,
      double recoveredHydrogenMassFlowKgPerHour, double unrecoveredHydrogenMassFlowKgPerHour,
      double recoveredHydrogenKgPerTonneFeed, double unrecoveredHydrogenKgPerTonneFeed,
      double hydrogenMolarBalanceResidualKmolPerHour, double hydrogenMassBalanceResidualKgPerHour,
      double overallMassBalanceResidualKgPerHour) {
    this.feedBalance = feedBalance;
    this.hydrogenRecoveryFraction = hydrogenRecoveryFraction;
    this.producedHydrogenFlowKmolPerHour = producedHydrogenFlowKmolPerHour;
    this.recoveredHydrogenFlowKmolPerHour = recoveredHydrogenFlowKmolPerHour;
    this.unrecoveredHydrogenFlowKmolPerHour = unrecoveredHydrogenFlowKmolPerHour;
    this.producedHydrogenMassFlowKgPerHour = producedHydrogenMassFlowKgPerHour;
    this.recoveredHydrogenMassFlowKgPerHour = recoveredHydrogenMassFlowKgPerHour;
    this.unrecoveredHydrogenMassFlowKgPerHour = unrecoveredHydrogenMassFlowKgPerHour;
    this.recoveredHydrogenKgPerTonneFeed = recoveredHydrogenKgPerTonneFeed;
    this.unrecoveredHydrogenKgPerTonneFeed = unrecoveredHydrogenKgPerTonneFeed;
    this.hydrogenMolarBalanceResidualKmolPerHour = hydrogenMolarBalanceResidualKmolPerHour;
    this.hydrogenMassBalanceResidualKgPerHour = hydrogenMassBalanceResidualKgPerHour;
    this.overallMassBalanceResidualKgPerHour = overallMassBalanceResidualKgPerHour;
  }

  /**
   * Calculate a hydrogen-recovery allocation over a qualified reforming-feed receipt.
   *
   * @param feedBalance qualified multi-cut catalytic-reforming receipt
   * @param hydrogenRecoveryFraction fraction of produced hydrogen recovered, in [0, 1]
   * @return immutable hydrogen-recovery receipt
   */
  public static RefineryCatalyticReformingHydrogenRecoveryBalance calculate(
      RefineryCatalyticReformingFeedBalance feedBalance, double hydrogenRecoveryFraction) {
    Objects.requireNonNull(feedBalance, "feedBalance");
    if (!Double.isFinite(hydrogenRecoveryFraction) || hydrogenRecoveryFraction < 0.0
        || hydrogenRecoveryFraction > 1.0) {
      throw new IllegalArgumentException("hydrogenRecoveryFraction must be finite and in [0, 1]");
    }

    double producedHydrogenFlowKmolPerHour = feedBalance.getHydrogenProductFlowKmolPerHour();
    double recoveredHydrogenFlowKmolPerHour = producedHydrogenFlowKmolPerHour * hydrogenRecoveryFraction;
    double unrecoveredHydrogenFlowKmolPerHour = producedHydrogenFlowKmolPerHour - recoveredHydrogenFlowKmolPerHour;
    double producedHydrogenMassFlowKgPerHour = feedBalance.getHydrogenProductMassFlowKgPerHour();
    double recoveredHydrogenMassFlowKgPerHour = producedHydrogenMassFlowKgPerHour * hydrogenRecoveryFraction;
    double unrecoveredHydrogenMassFlowKgPerHour = producedHydrogenMassFlowKgPerHour
        - recoveredHydrogenMassFlowKgPerHour;
    double tonnesFeedPerHour = feedBalance.getFeedMassFlowKgPerHour() / 1000.0;
    double recoveredHydrogenKgPerTonneFeed = recoveredHydrogenMassFlowKgPerHour / tonnesFeedPerHour;
    double unrecoveredHydrogenKgPerTonneFeed = unrecoveredHydrogenMassFlowKgPerHour / tonnesFeedPerHour;
    double hydrogenMolarBalanceResidualKmolPerHour = producedHydrogenFlowKmolPerHour - recoveredHydrogenFlowKmolPerHour
        - unrecoveredHydrogenFlowKmolPerHour;
    double hydrogenMassBalanceResidualKgPerHour = producedHydrogenMassFlowKgPerHour - recoveredHydrogenMassFlowKgPerHour
        - unrecoveredHydrogenMassFlowKgPerHour;
    double overallMassBalanceResidualKgPerHour = feedBalance.getFeedMassFlowKgPerHour()
        - feedBalance.getReformateMassFlowKgPerHour() - recoveredHydrogenMassFlowKgPerHour
        - unrecoveredHydrogenMassFlowKgPerHour;

    double molarTolerance = 1.0e-12 * Math.max(1.0, producedHydrogenFlowKmolPerHour);
    double massTolerance = 1.0e-12 * Math.max(1.0, feedBalance.getFeedMassFlowKgPerHour());
    if (!allFiniteNonNegative(producedHydrogenFlowKmolPerHour, recoveredHydrogenFlowKmolPerHour,
        unrecoveredHydrogenFlowKmolPerHour, producedHydrogenMassFlowKgPerHour, recoveredHydrogenMassFlowKgPerHour,
        unrecoveredHydrogenMassFlowKgPerHour, recoveredHydrogenKgPerTonneFeed, unrecoveredHydrogenKgPerTonneFeed)
        || Math.abs(hydrogenMolarBalanceResidualKmolPerHour) > molarTolerance
        || Math.abs(hydrogenMassBalanceResidualKgPerHour) > massTolerance
        || Math.abs(overallMassBalanceResidualKgPerHour) > massTolerance) {
      throw new IllegalArgumentException("inputs do not define a closed hydrogen-recovery balance");
    }

    return new RefineryCatalyticReformingHydrogenRecoveryBalance(feedBalance, hydrogenRecoveryFraction,
        producedHydrogenFlowKmolPerHour, recoveredHydrogenFlowKmolPerHour, unrecoveredHydrogenFlowKmolPerHour,
        producedHydrogenMassFlowKgPerHour, recoveredHydrogenMassFlowKgPerHour, unrecoveredHydrogenMassFlowKgPerHour,
        recoveredHydrogenKgPerTonneFeed, unrecoveredHydrogenKgPerTonneFeed, hydrogenMolarBalanceResidualKmolPerHour,
        hydrogenMassBalanceResidualKgPerHour, overallMassBalanceResidualKgPerHour);
  }

  /**
   * Check that calculated receipt values are finite and non-negative.
   *
   * @param values receipt values to check
   * @return true when every value is finite and non-negative
   */
  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream multi-cut catalytic-reforming receipt */
  public RefineryCatalyticReformingFeedBalance getFeedBalance() {
    return feedBalance;
  }

  /** @return caller-owned hydrogen recovery fraction */
  public double getHydrogenRecoveryFraction() {
    return hydrogenRecoveryFraction;
  }

  /** @return produced hydrogen flow in kmol/h */
  public double getProducedHydrogenFlowKmolPerHour() {
    return producedHydrogenFlowKmolPerHour;
  }

  /** @return recovered hydrogen flow in kmol/h */
  public double getRecoveredHydrogenFlowKmolPerHour() {
    return recoveredHydrogenFlowKmolPerHour;
  }

  /** @return unrecovered hydrogen flow in kmol/h */
  public double getUnrecoveredHydrogenFlowKmolPerHour() {
    return unrecoveredHydrogenFlowKmolPerHour;
  }

  /** @return produced hydrogen mass flow in kg/h */
  public double getProducedHydrogenMassFlowKgPerHour() {
    return producedHydrogenMassFlowKgPerHour;
  }

  /** @return recovered hydrogen mass flow in kg/h */
  public double getRecoveredHydrogenMassFlowKgPerHour() {
    return recoveredHydrogenMassFlowKgPerHour;
  }

  /** @return unrecovered hydrogen mass flow in kg/h */
  public double getUnrecoveredHydrogenMassFlowKgPerHour() {
    return unrecoveredHydrogenMassFlowKgPerHour;
  }

  /** @return recovered hydrogen in kg per tonne of feed */
  public double getRecoveredHydrogenKgPerTonneFeed() {
    return recoveredHydrogenKgPerTonneFeed;
  }

  /** @return unrecovered hydrogen in kg per tonne of feed */
  public double getUnrecoveredHydrogenKgPerTonneFeed() {
    return unrecoveredHydrogenKgPerTonneFeed;
  }

  /** @return produced-minus-allocated hydrogen residual in kmol/h */
  public double getHydrogenMolarBalanceResidualKmolPerHour() {
    return hydrogenMolarBalanceResidualKmolPerHour;
  }

  /** @return produced-minus-allocated hydrogen residual in kg/h */
  public double getHydrogenMassBalanceResidualKgPerHour() {
    return hydrogenMassBalanceResidualKgPerHour;
  }

  /** @return feed-minus-reformate-minus-allocated-hydrogen residual in kg/h */
  public double getOverallMassBalanceResidualKgPerHour() {
    return overallMassBalanceResidualKgPerHour;
  }
}
