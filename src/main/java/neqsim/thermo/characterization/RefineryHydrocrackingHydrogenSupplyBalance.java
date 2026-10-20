package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable hydrogen-supply receipt coupling recovered reformer hydrogen to hydrocracking demand.
 *
 * <p>
 * Recovered catalytic-reformer hydrogen is allocated against a qualified hydrocracking demand. Any deficit is reported
 * as imported hydrogen and any unused recovered hydrogen as surplus. The calculation is a mass allocation, not a
 * hydrogen-network, purity, compression, or pressure model.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrocrackingHydrogenSupplyBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final RefineryHydrocrackingYieldBalance hydrocrackingBalance;
  private final RefineryCatalyticReformingHydrogenRecoveryBalance reformingRecoveryBalance;
  private final double hydrocrackingHydrogenDemandKgPerHour;
  private final double recoveredReformerHydrogenAvailableKgPerHour;
  private final double recoveredReformerHydrogenAllocatedKgPerHour;
  private final double importedHydrogenRequiredKgPerHour;
  private final double recoveredReformerHydrogenSurplusKgPerHour;
  private final double hydrogenDemandCoverageFraction;
  private final double importedHydrogenRequiredKgPerTonneHydrocrackerFeed;
  private final double recoveredReformerHydrogenSurplusKgPerTonneHydrocrackerFeed;
  private final double hydrogenDemandBalanceResidualKgPerHour;
  private final double recoveredHydrogenSupplyBalanceResidualKgPerHour;

  private RefineryHydrocrackingHydrogenSupplyBalance(RefineryHydrocrackingYieldBalance hydrocrackingBalance,
      RefineryCatalyticReformingHydrogenRecoveryBalance reformingRecoveryBalance,
      double hydrocrackingHydrogenDemandKgPerHour, double recoveredReformerHydrogenAvailableKgPerHour,
      double recoveredReformerHydrogenAllocatedKgPerHour, double importedHydrogenRequiredKgPerHour,
      double recoveredReformerHydrogenSurplusKgPerHour, double hydrogenDemandCoverageFraction,
      double importedHydrogenRequiredKgPerTonneHydrocrackerFeed,
      double recoveredReformerHydrogenSurplusKgPerTonneHydrocrackerFeed, double hydrogenDemandBalanceResidualKgPerHour,
      double recoveredHydrogenSupplyBalanceResidualKgPerHour) {
    this.hydrocrackingBalance = hydrocrackingBalance;
    this.reformingRecoveryBalance = reformingRecoveryBalance;
    this.hydrocrackingHydrogenDemandKgPerHour = hydrocrackingHydrogenDemandKgPerHour;
    this.recoveredReformerHydrogenAvailableKgPerHour = recoveredReformerHydrogenAvailableKgPerHour;
    this.recoveredReformerHydrogenAllocatedKgPerHour = recoveredReformerHydrogenAllocatedKgPerHour;
    this.importedHydrogenRequiredKgPerHour = importedHydrogenRequiredKgPerHour;
    this.recoveredReformerHydrogenSurplusKgPerHour = recoveredReformerHydrogenSurplusKgPerHour;
    this.hydrogenDemandCoverageFraction = hydrogenDemandCoverageFraction;
    this.importedHydrogenRequiredKgPerTonneHydrocrackerFeed = importedHydrogenRequiredKgPerTonneHydrocrackerFeed;
    this.recoveredReformerHydrogenSurplusKgPerTonneHydrocrackerFeed = recoveredReformerHydrogenSurplusKgPerTonneHydrocrackerFeed;
    this.hydrogenDemandBalanceResidualKgPerHour = hydrogenDemandBalanceResidualKgPerHour;
    this.recoveredHydrogenSupplyBalanceResidualKgPerHour = recoveredHydrogenSupplyBalanceResidualKgPerHour;
  }

  /**
   * Allocate recovered catalytic-reformer hydrogen to hydrocracking hydrogen demand.
   *
   * @param hydrocrackingBalance qualified hydrocracking yield and hydrogen-demand receipt
   * @param reformingRecoveryBalance qualified catalytic-reforming hydrogen-recovery receipt
   * @return immutable cross-unit hydrogen-supply receipt
   */
  public static RefineryHydrocrackingHydrogenSupplyBalance calculate(
      RefineryHydrocrackingYieldBalance hydrocrackingBalance,
      RefineryCatalyticReformingHydrogenRecoveryBalance reformingRecoveryBalance) {
    Objects.requireNonNull(hydrocrackingBalance, "hydrocrackingBalance");
    Objects.requireNonNull(reformingRecoveryBalance, "reformingRecoveryBalance");

    double demand = hydrocrackingBalance.getHydrogenConsumptionMassFlowKgPerHour();
    double available = reformingRecoveryBalance.getRecoveredHydrogenMassFlowKgPerHour();
    double allocated = Math.min(demand, available);
    double imported = Math.max(0.0, demand - available);
    double surplus = Math.max(0.0, available - demand);
    double coverage = demand > 0.0 ? allocated / demand : 1.0;
    double tonnesFeedPerHour = hydrocrackingBalance.getFeedMassFlowKgPerHour() / 1000.0;
    double importedPerTonne = imported / tonnesFeedPerHour;
    double surplusPerTonne = surplus / tonnesFeedPerHour;
    double demandResidual = allocated + imported - demand;
    double supplyResidual = available - allocated - surplus;

    double tolerance = 1.0e-12 * Math.max(1.0, Math.max(demand, available));
    if (!allFiniteNonNegative(demand, available, allocated, imported, surplus, coverage, importedPerTonne,
        surplusPerTonne) || coverage > 1.0 || Math.abs(demandResidual) > tolerance
        || Math.abs(supplyResidual) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed hydrocracking hydrogen-supply balance");
    }

    return new RefineryHydrocrackingHydrogenSupplyBalance(hydrocrackingBalance, reformingRecoveryBalance, demand,
        available, allocated, imported, surplus, coverage, importedPerTonne, surplusPerTonne, demandResidual,
        supplyResidual);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return qualified hydrocracking yield and demand receipt */
  public RefineryHydrocrackingYieldBalance getHydrocrackingBalance() {
    return hydrocrackingBalance;
  }

  /** @return qualified catalytic-reforming hydrogen-recovery receipt */
  public RefineryCatalyticReformingHydrogenRecoveryBalance getReformingRecoveryBalance() {
    return reformingRecoveryBalance;
  }

  /** @return hydrocracking hydrogen demand in kg/h */
  public double getHydrocrackingHydrogenDemandKgPerHour() {
    return hydrocrackingHydrogenDemandKgPerHour;
  }

  /** @return recovered reformer hydrogen available in kg/h */
  public double getRecoveredReformerHydrogenAvailableKgPerHour() {
    return recoveredReformerHydrogenAvailableKgPerHour;
  }

  /** @return recovered reformer hydrogen allocated to hydrocracking in kg/h */
  public double getRecoveredReformerHydrogenAllocatedKgPerHour() {
    return recoveredReformerHydrogenAllocatedKgPerHour;
  }

  /** @return imported hydrogen required to close hydrocracking demand in kg/h */
  public double getImportedHydrogenRequiredKgPerHour() {
    return importedHydrogenRequiredKgPerHour;
  }

  /** @return recovered reformer hydrogen left after allocation in kg/h */
  public double getRecoveredReformerHydrogenSurplusKgPerHour() {
    return recoveredReformerHydrogenSurplusKgPerHour;
  }

  /** @return fraction of hydrocracking demand covered by recovered reformer hydrogen */
  public double getHydrogenDemandCoverageFraction() {
    return hydrogenDemandCoverageFraction;
  }

  /** @return imported hydrogen in kg/t hydrocracker feed */
  public double getImportedHydrogenRequiredKgPerTonneHydrocrackerFeed() {
    return importedHydrogenRequiredKgPerTonneHydrocrackerFeed;
  }

  /** @return surplus recovered reformer hydrogen in kg/t hydrocracker feed */
  public double getRecoveredReformerHydrogenSurplusKgPerTonneHydrocrackerFeed() {
    return recoveredReformerHydrogenSurplusKgPerTonneHydrocrackerFeed;
  }

  /** @return allocated-plus-imported-minus-demand residual in kg/h */
  public double getHydrogenDemandBalanceResidualKgPerHour() {
    return hydrogenDemandBalanceResidualKgPerHour;
  }

  /** @return available-minus-allocated-minus-surplus residual in kg/h */
  public double getRecoveredHydrogenSupplyBalanceResidualKgPerHour() {
    return recoveredHydrogenSupplyBalanceResidualKgPerHour;
  }
}
