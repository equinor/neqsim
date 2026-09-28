package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests liquid-product-basis intensities derived from qualified net operating receipts. */
class RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceiptTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void qualifiesPublicBigHillLiquidProductIntensities() {
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt net = netReceipt(1000.0, 1.0, 100.0);
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt receipt = RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt
        .calculate(net);

    assertSame(net, receipt.getNetOperatingReceipt());
    assertSame(productDistribution(net), receipt.getProductDistributionReceipt());
    assertEquals(1000.0, receipt.getFeedMassFlowKgPerHour(), 0.0);
    assertEquals(995.4894479786512, receipt.getLiquidProductMassFlowKgPerHour(), 1.0e-9);
    assertEquals(995.4894479786512, receipt.getLiquidProductKgPerTonneFeed(), 1.0e-9);
    assertEquals(1.0180634422423367, receipt.getTotalExternalEnergyMWhPerTonneFeed(), 1.0e-12);
    assertEquals(1.0226762767897963, receipt.getTotalExternalEnergyMWhPerTonneLiquidProduct(), 1.0e-12);
    assertEquals(223.31521913699645, receipt.getTotalEmissionsKgCo2EquivalentPerTonneFeed(), 1.0e-9);
    assertEquals(224.3270580019102, receipt.getTotalEmissionsKgCo2EquivalentPerTonneLiquidProduct(), 1.0e-9);
    assertEquals(54.13873899696584, receipt.getTotalOperatingCostPerTonneFeed(), 1.0e-9);
    assertEquals(54.38404104322246, receipt.getTotalOperatingCostPerTonneLiquidProduct(), 1.0e-9);
    assertEquals(0.0, receipt.getEnergyBasisClosureResidualMWhPerHour(), 1.0e-15);
    assertEquals(0.0, receipt.getEmissionsBasisClosureResidualKgCo2EquivalentPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getCostBasisClosureResidualPerHour(), 1.0e-12);
  }

  @Test
  void scalesRatesAndPreservesFeedAndProductIntensities() {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt base = RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt
        .calculate(netReceipt(1000.0, 1.0, 100.0));
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt doubled = RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt
        .calculate(netReceipt(2000.0, 2.0, 100.0));

    assertEquals(2.0 * base.getLiquidProductMassFlowKgPerHour(), doubled.getLiquidProductMassFlowKgPerHour(), 1.0e-9);
    assertEquals(base.getLiquidProductKgPerTonneFeed(), doubled.getLiquidProductKgPerTonneFeed(), 1.0e-12);
    assertEquals(base.getTotalExternalEnergyMWhPerTonneFeed(), doubled.getTotalExternalEnergyMWhPerTonneFeed(),
        1.0e-12);
    assertEquals(base.getTotalExternalEnergyMWhPerTonneLiquidProduct(),
        doubled.getTotalExternalEnergyMWhPerTonneLiquidProduct(), 1.0e-12);
    assertEquals(base.getTotalEmissionsKgCo2EquivalentPerTonneLiquidProduct(),
        doubled.getTotalEmissionsKgCo2EquivalentPerTonneLiquidProduct(), 1.0e-9);
    assertEquals(base.getTotalOperatingCostPerTonneLiquidProduct(),
        doubled.getTotalOperatingCostPerTonneLiquidProduct(), 1.0e-9);
  }

  @Test
  void zeroCarbonPriceRetainsEnergyAndEmissionsButReducesProductBasisCost() {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt priced = RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt
        .calculate(netReceipt(1000.0, 1.0, 100.0));
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt zeroCarbon = RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt
        .calculate(netReceipt(1000.0, 1.0, 0.0));

    assertEquals(priced.getTotalExternalEnergyMWhPerTonneLiquidProduct(),
        zeroCarbon.getTotalExternalEnergyMWhPerTonneLiquidProduct(), 1.0e-12);
    assertEquals(priced.getTotalEmissionsKgCo2EquivalentPerTonneLiquidProduct(),
        zeroCarbon.getTotalEmissionsKgCo2EquivalentPerTonneLiquidProduct(), 1.0e-9);
    assertEquals(31.95133524303144, zeroCarbon.getTotalOperatingCostPerTonneLiquidProduct(), 1.0e-9);
  }

  @Test
  void rejectsNullInput() {
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt.calculate(null));
  }

  private static RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt productDistribution(
      RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt net) {
    return net.getHeatRecoveryCredit().getHeatRecoveryBalance().getStackLossBalance().getCombustionBalance()
        .getUtilityBalance().getThermalDutyBalance().getProductDistributionReceipt();
  }

  private static RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt netReceipt(double feedMassFlowKgPerHour,
      double sensibleHeatingDutyMegaWatt, double carbonPricePerTonneCo2e) {
    return RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt.calculate(hydrogenReceipt(feedMassFlowKgPerHour),
        heatCredit(feedMassFlowKgPerHour, sensibleHeatingDutyMegaWatt), carbonPricePerTonneCo2e);
  }

  private static RefineryHydrotreatingSulfurNitrogenOperatingReceipt hydrogenReceipt(double feedMassFlowKgPerHour) {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material(), 1.5, 0.90, NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, feedMassFlowKgPerHour);
    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utility = RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance
        .calculate(throughput, 120.0, 3.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance emissions = RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance
        .calculate(utility, 10.0, 100.0);
    return RefineryHydrotreatingSulfurNitrogenOperatingReceipt.calculate(emissions);
  }

  private static RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance heatCredit(
      double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt) {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material(), 1.5, 0.90, NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, feedMassFlowKgPerHour);
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution = RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt
        .calculate(throughput);
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance thermal = RefineryHydrotreatingSulfurNitrogenThermalDutyBalance
        .calculate(distribution, 100.0, 50.0, sensibleHeatingDutyMegaWatt);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utility = RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance
        .calculate(thermal, 0.85, 50.0, 0.40, 3.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion = RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(utility, METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0, 0.0, 0.2095, 0.15);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack = RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(combustion, 473.15, 298.15, 34.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery = RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance
        .calculate(stack, 0.60);
    return RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance.calculate(recovery, 0.75);
  }

  private static RefineryHydrotreatingSulfurNitrogenBalance material() {
    return RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.0040867518, 0.001095129, 15.0e-6, 10.0e-6,
        2.0, 4.0);
  }
}
