package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable liquid-product-basis intensity receipt for a qualified net hydrotreating operating receipt.
 *
 * <p>
 * This class converts existing feed-basis energy, emissions, and operating-cost results to the qualified external
 * liquid-product basis. It does not allocate burdens to export gas and adds no process model, product value, price,
 * emissions factor, lifecycle boundary, or quality claim.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt netOperatingReceipt;
  private final RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt productDistributionReceipt;
  private final double feedMassFlowKgPerHour;
  private final double liquidProductMassFlowKgPerHour;
  private final double liquidProductKgPerTonneFeed;
  private final double totalExternalEnergyMWhPerTonneFeed;
  private final double totalExternalEnergyMWhPerTonneLiquidProduct;
  private final double totalEmissionsKgCo2EquivalentPerTonneFeed;
  private final double totalEmissionsKgCo2EquivalentPerTonneLiquidProduct;
  private final double totalOperatingCostPerTonneFeed;
  private final double totalOperatingCostPerTonneLiquidProduct;
  private final double energyBasisClosureResidualMWhPerHour;
  private final double emissionsBasisClosureResidualKgCo2EquivalentPerHour;
  private final double costBasisClosureResidualPerHour;

  private RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt(
      RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt netOperatingReceipt,
      RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt productDistributionReceipt,
      double feedMassFlowKgPerHour, double liquidProductMassFlowKgPerHour, double liquidProductKgPerTonneFeed,
      double totalExternalEnergyMWhPerTonneFeed, double totalExternalEnergyMWhPerTonneLiquidProduct,
      double totalEmissionsKgCo2EquivalentPerTonneFeed, double totalEmissionsKgCo2EquivalentPerTonneLiquidProduct,
      double totalOperatingCostPerTonneFeed, double totalOperatingCostPerTonneLiquidProduct,
      double energyBasisClosureResidualMWhPerHour, double emissionsBasisClosureResidualKgCo2EquivalentPerHour,
      double costBasisClosureResidualPerHour) {
    this.netOperatingReceipt = netOperatingReceipt;
    this.productDistributionReceipt = productDistributionReceipt;
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.liquidProductMassFlowKgPerHour = liquidProductMassFlowKgPerHour;
    this.liquidProductKgPerTonneFeed = liquidProductKgPerTonneFeed;
    this.totalExternalEnergyMWhPerTonneFeed = totalExternalEnergyMWhPerTonneFeed;
    this.totalExternalEnergyMWhPerTonneLiquidProduct = totalExternalEnergyMWhPerTonneLiquidProduct;
    this.totalEmissionsKgCo2EquivalentPerTonneFeed = totalEmissionsKgCo2EquivalentPerTonneFeed;
    this.totalEmissionsKgCo2EquivalentPerTonneLiquidProduct = totalEmissionsKgCo2EquivalentPerTonneLiquidProduct;
    this.totalOperatingCostPerTonneFeed = totalOperatingCostPerTonneFeed;
    this.totalOperatingCostPerTonneLiquidProduct = totalOperatingCostPerTonneLiquidProduct;
    this.energyBasisClosureResidualMWhPerHour = energyBasisClosureResidualMWhPerHour;
    this.emissionsBasisClosureResidualKgCo2EquivalentPerHour = emissionsBasisClosureResidualKgCo2EquivalentPerHour;
    this.costBasisClosureResidualPerHour = costBasisClosureResidualPerHour;
  }

  /**
   * Convert a qualified net operating receipt to a liquid-product reporting basis.
   *
   * @param netOperatingReceipt qualified net operating receipt
   * @return immutable feed- and liquid-product-basis intensity receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt calculate(
      RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt netOperatingReceipt) {
    Objects.requireNonNull(netOperatingReceipt, "netOperatingReceipt");

    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution = netOperatingReceipt
        .getHeatRecoveryCredit().getHeatRecoveryBalance().getStackLossBalance().getCombustionBalance()
        .getUtilityBalance().getThermalDutyBalance().getProductDistributionReceipt();
    double feedMassFlow = netOperatingReceipt.getHydrogenOperatingReceipt().getFeedMassFlowKgPerHour();
    double distributionFeedMassFlow = distribution.getThroughputBalance().getFeedMassFlowKgPerHour();
    double liquidProductMassFlow = distribution.getThroughputBalance().getProductMassFlowKgPerHour();
    double feedTolerance = 1.0e-12 * Math.max(1.0, Math.max(feedMassFlow, distributionFeedMassFlow));
    if (Math.abs(feedMassFlow - distributionFeedMassFlow) > feedTolerance || !Double.isFinite(liquidProductMassFlow)
        || liquidProductMassFlow <= 0.0) {
      throw new IllegalArgumentException(
          "upstream receipts must define one common basis and positive liquid product flow");
    }

    double feedTonnesPerHour = feedMassFlow / KILOGRAMS_PER_TONNE;
    double liquidProductTonnesPerHour = liquidProductMassFlow / KILOGRAMS_PER_TONNE;
    double energyPerTonneFeed = netOperatingReceipt.getTotalExternalEnergyMWhPerTonneFeed();
    double emissionsPerTonneFeed = netOperatingReceipt.getTotalEmissionsKgCo2EquivalentPerTonneFeed();
    double costPerTonneFeed = netOperatingReceipt.getTotalOperatingCostPerTonneFeed();
    double energyRate = energyPerTonneFeed * feedTonnesPerHour;
    double emissionsRate = netOperatingReceipt.getTotalEmissionsKgCo2EquivalentPerHour();
    double costRate = netOperatingReceipt.getTotalOperatingCostPerHour();
    double energyPerTonneProduct = energyRate / liquidProductTonnesPerHour;
    double emissionsPerTonneProduct = emissionsRate / liquidProductTonnesPerHour;
    double costPerTonneProduct = costRate / liquidProductTonnesPerHour;
    double energyResidual = energyPerTonneProduct * liquidProductTonnesPerHour - energyRate;
    double emissionsResidual = emissionsPerTonneProduct * liquidProductTonnesPerHour - emissionsRate;
    double costResidual = costPerTonneProduct * liquidProductTonnesPerHour - costRate;

    if (!allFiniteNonNegative(feedMassFlow, liquidProductMassFlow, energyPerTonneFeed, emissionsPerTonneFeed,
        costPerTonneFeed, energyPerTonneProduct, emissionsPerTonneProduct, costPerTonneProduct)
        || Math.abs(energyResidual) > 1.0e-12 || Math.abs(emissionsResidual) > 1.0e-9
        || Math.abs(costResidual) > 1.0e-9) {
      throw new IllegalArgumentException("upstream receipts do not define closed liquid-product-basis intensities");
    }

    return new RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt(netOperatingReceipt, distribution,
        feedMassFlow, liquidProductMassFlow, distribution.getLiquidProductKgPerTonneFeed(), energyPerTonneFeed,
        energyPerTonneProduct, emissionsPerTonneFeed, emissionsPerTonneProduct, costPerTonneFeed, costPerTonneProduct,
        energyResidual, emissionsResidual, costResidual);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return qualified upstream net operating receipt */
  public RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt getNetOperatingReceipt() {
    return netOperatingReceipt;
  }

  /** @return qualified upstream product-distribution receipt */
  public RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt getProductDistributionReceipt() {
    return productDistributionReceipt;
  }

  /** @return fresh liquid feed rate in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return external liquid-product rate in kg/h */
  public double getLiquidProductMassFlowKgPerHour() {
    return liquidProductMassFlowKgPerHour;
  }

  /** @return external liquid product in kg per tonne liquid feed */
  public double getLiquidProductKgPerTonneFeed() {
    return liquidProductKgPerTonneFeed;
  }

  /** @return total external energy in MWh per tonne liquid feed */
  public double getTotalExternalEnergyMWhPerTonneFeed() {
    return totalExternalEnergyMWhPerTonneFeed;
  }

  /** @return total external energy in MWh per tonne external liquid product */
  public double getTotalExternalEnergyMWhPerTonneLiquidProduct() {
    return totalExternalEnergyMWhPerTonneLiquidProduct;
  }

  /** @return total emissions in kg CO2e per tonne liquid feed */
  public double getTotalEmissionsKgCo2EquivalentPerTonneFeed() {
    return totalEmissionsKgCo2EquivalentPerTonneFeed;
  }

  /** @return total emissions in kg CO2e per tonne external liquid product */
  public double getTotalEmissionsKgCo2EquivalentPerTonneLiquidProduct() {
    return totalEmissionsKgCo2EquivalentPerTonneLiquidProduct;
  }

  /** @return total operating cost in currency units per tonne liquid feed */
  public double getTotalOperatingCostPerTonneFeed() {
    return totalOperatingCostPerTonneFeed;
  }

  /** @return total operating cost in currency units per tonne external liquid product */
  public double getTotalOperatingCostPerTonneLiquidProduct() {
    return totalOperatingCostPerTonneLiquidProduct;
  }

  /** @return product-basis energy minus feed-basis energy rate in MWh/h */
  public double getEnergyBasisClosureResidualMWhPerHour() {
    return energyBasisClosureResidualMWhPerHour;
  }

  /** @return product-basis emissions minus total emissions rate in kg CO2e/h */
  public double getEmissionsBasisClosureResidualKgCo2EquivalentPerHour() {
    return emissionsBasisClosureResidualKgCo2EquivalentPerHour;
  }

  /** @return product-basis cost minus total operating cost rate in currency units/h */
  public double getCostBasisClosureResidualPerHour() {
    return costBasisClosureResidualPerHour;
  }
}
