package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable operating receipt composed from qualified coupled sulfur/nitrogen coupled sulfur/nitrogen hydrotreating utility and emissions balances.
 *
 * <p>
 * This class aggregates existing material, energy, emissions, and caller-priced scenario receipts. It introduces no
 * process model, physical correlation, price, or emissions factor.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenOperatingReceipt implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance emissionsBalance;
  private final double feedMassFlowKgPerHour;
  private final double freshHydrogenMassFlowKgPerHour;
  private final double freshHydrogenEnergyMWhPerTonneFeed;
  private final double hydrogenSupplyEmissionsKgCo2EquivalentPerHour;
  private final double hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed;
  private final double hydrogenPurchaseCostPerHour;
  private final double carbonCostPerHour;
  private final double totalScenarioCostPerHour;
  private final double hydrogenPurchaseCostPerTonneFeed;
  private final double carbonCostPerTonneFeed;
  private final double totalScenarioCostPerTonneFeed;
  private final double totalScenarioCostClosureResidualPerHour;

  private RefineryHydrotreatingSulfurNitrogenOperatingReceipt(RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance emissionsBalance,
      double feedMassFlowKgPerHour, double freshHydrogenMassFlowKgPerHour, double freshHydrogenEnergyMWhPerTonneFeed,
      double hydrogenSupplyEmissionsKgCo2EquivalentPerHour, double hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed,
      double hydrogenPurchaseCostPerHour, double carbonCostPerHour, double totalScenarioCostPerHour,
      double hydrogenPurchaseCostPerTonneFeed, double carbonCostPerTonneFeed, double totalScenarioCostPerTonneFeed,
      double totalScenarioCostClosureResidualPerHour) {
    this.emissionsBalance = emissionsBalance;
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.freshHydrogenMassFlowKgPerHour = freshHydrogenMassFlowKgPerHour;
    this.freshHydrogenEnergyMWhPerTonneFeed = freshHydrogenEnergyMWhPerTonneFeed;
    this.hydrogenSupplyEmissionsKgCo2EquivalentPerHour = hydrogenSupplyEmissionsKgCo2EquivalentPerHour;
    this.hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed = hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed;
    this.hydrogenPurchaseCostPerHour = hydrogenPurchaseCostPerHour;
    this.carbonCostPerHour = carbonCostPerHour;
    this.totalScenarioCostPerHour = totalScenarioCostPerHour;
    this.hydrogenPurchaseCostPerTonneFeed = hydrogenPurchaseCostPerTonneFeed;
    this.carbonCostPerTonneFeed = carbonCostPerTonneFeed;
    this.totalScenarioCostPerTonneFeed = totalScenarioCostPerTonneFeed;
    this.totalScenarioCostClosureResidualPerHour = totalScenarioCostClosureResidualPerHour;
  }

  /**
   * Compose a process-integration and scenario-economics receipt.
   *
   * @param emissionsBalance qualified coupled sulfur/nitrogen coupled sulfur/nitrogen hydrotreating hydrogen-supply emissions receipt
   * @return immutable integrated operating receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenOperatingReceipt calculate(
      RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance emissionsBalance) {
    Objects.requireNonNull(emissionsBalance, "emissionsBalance");

    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utility = emissionsBalance.getUtilityBalance();
    double feedMassFlow = utility.getThroughputBalance().getFeedMassFlowKgPerHour();
    double freshHydrogenMassFlow = utility.getFreshHydrogenMassFlowKgPerHour();
    double energyIntensity = utility.getFreshHydrogenChemicalPowerMegaWatt() * KILOGRAMS_PER_TONNE / feedMassFlow;
    double emissionsPerHour = emissionsBalance.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour();
    double emissionsPerTonneFeed = emissionsBalance.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed();
    double hydrogenCostPerHour = utility.getFreshHydrogenCostPerHour();
    double carbonCostPerHour = emissionsBalance.getCarbonCostPerHour();
    double totalCostPerHour = hydrogenCostPerHour + carbonCostPerHour;
    double hydrogenCostPerTonneFeed = utility.getFreshHydrogenCostPerTonneFeed();
    double carbonCostPerTonneFeed = emissionsBalance.getCarbonCostPerTonneFeed();
    double totalCostPerTonneFeed = hydrogenCostPerTonneFeed + carbonCostPerTonneFeed;
    double costResidual = totalCostPerHour - hydrogenCostPerHour - carbonCostPerHour;

    if (!allFiniteNonNegative(feedMassFlow, freshHydrogenMassFlow, energyIntensity, emissionsPerHour,
        emissionsPerTonneFeed, hydrogenCostPerHour, carbonCostPerHour, totalCostPerHour, hydrogenCostPerTonneFeed,
        carbonCostPerTonneFeed, totalCostPerTonneFeed)) {
      throw new IllegalArgumentException("upstream receipts do not define finite non-negative operating results");
    }

    return new RefineryHydrotreatingSulfurNitrogenOperatingReceipt(emissionsBalance, feedMassFlow, freshHydrogenMassFlow,
        energyIntensity, emissionsPerHour, emissionsPerTonneFeed, hydrogenCostPerHour, carbonCostPerHour,
        totalCostPerHour, hydrogenCostPerTonneFeed, carbonCostPerTonneFeed, totalCostPerTonneFeed, costResidual);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream immutable emissions receipt */
  public RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance getEmissionsBalance() {
    return emissionsBalance;
  }

  /** @return fresh liquid feed rate in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return external fresh H2 rate in kg/h */
  public double getFreshHydrogenMassFlowKgPerHour() {
    return freshHydrogenMassFlowKgPerHour;
  }

  /** @return external fresh-H2 LHV energy intensity in MWh per tonne liquid feed */
  public double getFreshHydrogenEnergyMWhPerTonneFeed() {
    return freshHydrogenEnergyMWhPerTonneFeed;
  }

  /** @return indirect hydrogen-supply emissions in kg CO2e/h */
  public double getHydrogenSupplyEmissionsKgCo2EquivalentPerHour() {
    return hydrogenSupplyEmissionsKgCo2EquivalentPerHour;
  }

  /** @return indirect hydrogen-supply emissions in kg CO2e per tonne liquid feed */
  public double getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed() {
    return hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed;
  }

  /** @return caller-priced fresh-H2 purchase cost in currency units/h */
  public double getHydrogenPurchaseCostPerHour() {
    return hydrogenPurchaseCostPerHour;
  }

  /** @return caller-priced carbon cost in currency units/h */
  public double getCarbonCostPerHour() {
    return carbonCostPerHour;
  }

  /** @return combined caller-priced scenario cost in currency units/h */
  public double getTotalScenarioCostPerHour() {
    return totalScenarioCostPerHour;
  }

  /** @return caller-priced fresh-H2 purchase cost in currency units per tonne liquid feed */
  public double getHydrogenPurchaseCostPerTonneFeed() {
    return hydrogenPurchaseCostPerTonneFeed;
  }

  /** @return caller-priced carbon cost in currency units per tonne liquid feed */
  public double getCarbonCostPerTonneFeed() {
    return carbonCostPerTonneFeed;
  }

  /** @return combined caller-priced scenario cost in currency units per tonne liquid feed */
  public double getTotalScenarioCostPerTonneFeed() {
    return totalScenarioCostPerTonneFeed;
  }

  /** @return total-cost arithmetic closure residual in currency units/h */
  public double getTotalScenarioCostClosureResidualPerHour() {
    return totalScenarioCostClosureResidualPerHour;
  }
}
