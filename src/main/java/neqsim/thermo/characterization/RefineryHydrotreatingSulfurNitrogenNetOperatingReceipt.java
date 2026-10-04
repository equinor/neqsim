package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable net energy, emissions, and caller-priced operating receipt after heat recovery.
 *
 * <p>
 * The receipt composes qualified hydrogen and fired-heater heat-recovery receipts. It introduces no process
 * correlation, equipment assumption, fuel price, emissions factor, or carbon price.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingSulfurNitrogenOperatingReceipt hydrogenOperatingReceipt;
  private final RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance heatRecoveryCredit;
  private final double carbonPricePerTonneCo2Equivalent;
  private final double netFuelEnergyMWhPerTonneFeed;
  private final double totalExternalEnergyMWhPerTonneFeed;
  private final double totalEmissionsKgCo2EquivalentPerHour;
  private final double totalEmissionsKgCo2EquivalentPerTonneFeed;
  private final double netFuelCostPerHour;
  private final double combinedCarbonCostPerHour;
  private final double totalOperatingCostPerHour;
  private final double totalOperatingCostPerTonneFeed;
  private final double energyClosureResidualMWhPerTonneFeed;
  private final double emissionsClosureResidualKgCo2EquivalentPerHour;
  private final double costClosureResidualPerHour;

  private RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt(
      RefineryHydrotreatingSulfurNitrogenOperatingReceipt hydrogenOperatingReceipt,
      RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance heatRecoveryCredit,
      double carbonPricePerTonneCo2Equivalent, double netFuelEnergyMWhPerTonneFeed,
      double totalExternalEnergyMWhPerTonneFeed, double totalEmissionsKgCo2EquivalentPerHour,
      double totalEmissionsKgCo2EquivalentPerTonneFeed, double netFuelCostPerHour, double combinedCarbonCostPerHour,
      double totalOperatingCostPerHour, double totalOperatingCostPerTonneFeed,
      double energyClosureResidualMWhPerTonneFeed, double emissionsClosureResidualKgCo2EquivalentPerHour,
      double costClosureResidualPerHour) {
    this.hydrogenOperatingReceipt = hydrogenOperatingReceipt;
    this.heatRecoveryCredit = heatRecoveryCredit;
    this.carbonPricePerTonneCo2Equivalent = carbonPricePerTonneCo2Equivalent;
    this.netFuelEnergyMWhPerTonneFeed = netFuelEnergyMWhPerTonneFeed;
    this.totalExternalEnergyMWhPerTonneFeed = totalExternalEnergyMWhPerTonneFeed;
    this.totalEmissionsKgCo2EquivalentPerHour = totalEmissionsKgCo2EquivalentPerHour;
    this.totalEmissionsKgCo2EquivalentPerTonneFeed = totalEmissionsKgCo2EquivalentPerTonneFeed;
    this.netFuelCostPerHour = netFuelCostPerHour;
    this.combinedCarbonCostPerHour = combinedCarbonCostPerHour;
    this.totalOperatingCostPerHour = totalOperatingCostPerHour;
    this.totalOperatingCostPerTonneFeed = totalOperatingCostPerTonneFeed;
    this.energyClosureResidualMWhPerTonneFeed = energyClosureResidualMWhPerTonneFeed;
    this.emissionsClosureResidualKgCo2EquivalentPerHour = emissionsClosureResidualKgCo2EquivalentPerHour;
    this.costClosureResidualPerHour = costClosureResidualPerHour;
  }

  /**
   * Compose a net operating receipt on a common liquid-feed basis.
   *
   * @param hydrogenOperatingReceipt qualified hydrogen operating receipt
   * @param heatRecoveryCredit qualified fired-heater heat-recovery credit receipt
   * @param carbonPricePerTonneCo2Equivalent caller-owned carbon price in currency units per tonne CO2e
   * @return immutable net operating receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt calculate(
      RefineryHydrotreatingSulfurNitrogenOperatingReceipt hydrogenOperatingReceipt,
      RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance heatRecoveryCredit,
      double carbonPricePerTonneCo2Equivalent) {
    Objects.requireNonNull(hydrogenOperatingReceipt, "hydrogenOperatingReceipt");
    Objects.requireNonNull(heatRecoveryCredit, "heatRecoveryCredit");
    if (!Double.isFinite(carbonPricePerTonneCo2Equivalent) || carbonPricePerTonneCo2Equivalent < 0.0) {
      throw new IllegalArgumentException("carbonPricePerTonneCo2Equivalent must be finite and non-negative");
    }

    double feedMassFlow = hydrogenOperatingReceipt.getFeedMassFlowKgPerHour();
    double heaterFeedMassFlow = heatRecoveryCredit.getHeatRecoveryBalance().getStackLossBalance().getCombustionBalance()
        .getUtilityBalance().getThermalDutyBalance().getProductDistributionReceipt().getThroughputBalance()
        .getFeedMassFlowKgPerHour();
    double feedTolerance = 1.0e-12 * Math.max(1.0, Math.max(feedMassFlow, heaterFeedMassFlow));
    if (Math.abs(feedMassFlow - heaterFeedMassFlow) > feedTolerance) {
      throw new IllegalArgumentException("upstream receipts must use the same liquid-feed basis");
    }

    double hydrogenEnergy = hydrogenOperatingReceipt.getFreshHydrogenEnergyMWhPerTonneFeed();
    double netFuelEnergy = heatRecoveryCredit.getNetFuelChemicalPowerMegaWatt() * KILOGRAMS_PER_TONNE / feedMassFlow;
    double totalEnergy = hydrogenEnergy + netFuelEnergy;
    double hydrogenEmissions = hydrogenOperatingReceipt.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour();
    double fuelEmissions = heatRecoveryCredit.getNetFuelEmissionsKgCo2EquivalentPerHour();
    double totalEmissions = hydrogenEmissions + fuelEmissions;
    double totalEmissionsPerTonne = totalEmissions * KILOGRAMS_PER_TONNE / feedMassFlow;
    double hydrogenPurchaseCost = hydrogenOperatingReceipt.getHydrogenPurchaseCostPerHour();
    double netFuelCost = heatRecoveryCredit.getNetFuelCostPerHour();
    double carbonCost = totalEmissions * carbonPricePerTonneCo2Equivalent / KILOGRAMS_PER_TONNE;
    double totalCost = hydrogenPurchaseCost + netFuelCost + carbonCost;
    double totalCostPerTonne = totalCost * KILOGRAMS_PER_TONNE / feedMassFlow;
    double energyResidual = totalEnergy - hydrogenEnergy - netFuelEnergy;
    double emissionsResidual = totalEmissions - hydrogenEmissions - fuelEmissions;
    double costResidual = totalCost - hydrogenPurchaseCost - netFuelCost - carbonCost;

    if (!allFiniteNonNegative(netFuelEnergy, totalEnergy, totalEmissions, totalEmissionsPerTonne, netFuelCost,
        carbonCost, totalCost, totalCostPerTonne) || Math.abs(energyResidual) > 1.0e-12
        || Math.abs(emissionsResidual) > 1.0e-9 || Math.abs(costResidual) > 1.0e-9) {
      throw new IllegalArgumentException("upstream receipts do not define a closed net operating receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt(hydrogenOperatingReceipt, heatRecoveryCredit,
        carbonPricePerTonneCo2Equivalent, netFuelEnergy, totalEnergy, totalEmissions, totalEmissionsPerTonne,
        netFuelCost, carbonCost, totalCost, totalCostPerTonne, energyResidual, emissionsResidual, costResidual);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return qualified upstream hydrogen operating receipt */
  public RefineryHydrotreatingSulfurNitrogenOperatingReceipt getHydrogenOperatingReceipt() {
    return hydrogenOperatingReceipt;
  }

  /** @return qualified upstream fired-heater heat-recovery credit receipt */
  public RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance getHeatRecoveryCredit() {
    return heatRecoveryCredit;
  }

  /** @return caller-owned carbon price in currency units per tonne CO2e */
  public double getCarbonPricePerTonneCo2Equivalent() {
    return carbonPricePerTonneCo2Equivalent;
  }

  /** @return net fired-heater fuel energy in MWh per tonne liquid feed */
  public double getNetFuelEnergyMWhPerTonneFeed() {
    return netFuelEnergyMWhPerTonneFeed;
  }

  /** @return fresh-H2 plus net fired-heater fuel energy in MWh per tonne liquid feed */
  public double getTotalExternalEnergyMWhPerTonneFeed() {
    return totalExternalEnergyMWhPerTonneFeed;
  }

  /** @return hydrogen-supply plus net fuel emissions in kg CO2e/h */
  public double getTotalEmissionsKgCo2EquivalentPerHour() {
    return totalEmissionsKgCo2EquivalentPerHour;
  }

  /** @return hydrogen-supply plus net fuel emissions in kg CO2e per tonne liquid feed */
  public double getTotalEmissionsKgCo2EquivalentPerTonneFeed() {
    return totalEmissionsKgCo2EquivalentPerTonneFeed;
  }

  /** @return net caller-priced fired-heater fuel cost in currency units/h */
  public double getNetFuelCostPerHour() {
    return netFuelCostPerHour;
  }

  /** @return carbon cost for combined hydrogen-supply and net fuel emissions in currency units/h */
  public double getCombinedCarbonCostPerHour() {
    return combinedCarbonCostPerHour;
  }

  /** @return H2 purchase, net fuel, and combined carbon cost in currency units/h */
  public double getTotalOperatingCostPerHour() {
    return totalOperatingCostPerHour;
  }

  /** @return total caller-scenario operating cost in currency units per tonne liquid feed */
  public double getTotalOperatingCostPerTonneFeed() {
    return totalOperatingCostPerTonneFeed;
  }

  /** @return total external energy minus its hydrogen and net-fuel terms in MWh/t feed */
  public double getEnergyClosureResidualMWhPerTonneFeed() {
    return energyClosureResidualMWhPerTonneFeed;
  }

  /** @return total emissions minus hydrogen-supply and net-fuel emissions in kg CO2e/h */
  public double getEmissionsClosureResidualKgCo2EquivalentPerHour() {
    return emissionsClosureResidualKgCo2EquivalentPerHour;
  }

  /** @return total cost minus hydrogen, net-fuel, and carbon terms in currency units/h */
  public double getCostClosureResidualPerHour() {
    return costClosureResidualPerHour;
  }
}
