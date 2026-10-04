package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests coupled sulfur/nitrogen external product-distribution receipts. */
class RefineryHydrotreatingSulfurNitrogenProductDistributionReceiptTest {
  private static final double NITROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  void qualifiesPublicBigHillProductDistribution() {
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = publicThroughput(1000.0);
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt receipt = RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt
        .calculate(throughput);

    assertSame(throughput, receipt.getThroughputBalance());
    assertEquals(1003.1014719117842, receipt.getTotalExternalProductMassFlowKgPerHour(), 1.0e-9);
    assertEquals(0.9924115115506457, receipt.getLiquidProductMassFraction(), 1.0e-12);
    assertEquals(0.007588488449354379, receipt.getExportGasMassFraction(), 1.0e-12);
    assertEquals(0.0824108831077115, receipt.getExportHydrogenMassFlowKgPerHour(), 1.0e-12);
    assertEquals(4.327807878394079, receipt.getExportHydrogenSulfideMassFlowKgPerHour(), 1.0e-12);
    assertEquals(1.3194459789230566, receipt.getExportAmmoniaMassFlowKgPerHour(), 1.0e-12);
    assertEquals(1.882359192708103, receipt.getExportNonHydrogenMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getExternalProductClosureResidualKgPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getExportGasComponentClosureResidualKgPerHour(), 1.0e-12);
  }

  @Test
  void callerRateScalesMassRatesButPreservesFractionsAndNormalizedYields() {
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt base = RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt
        .calculate(publicThroughput(1000.0));
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt doubled = RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt
        .calculate(publicThroughput(2000.0));

    assertEquals(2.0 * base.getTotalExternalProductMassFlowKgPerHour(),
        doubled.getTotalExternalProductMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getExportHydrogenSulfideMassFlowKgPerHour(),
        doubled.getExportHydrogenSulfideMassFlowKgPerHour(), 1.0e-12);
    assertEquals(base.getLiquidProductMassFraction(), doubled.getLiquidProductMassFraction(), 1.0e-15);
    assertEquals(base.getExportHydrogenSulfideMassFraction(), doubled.getExportHydrogenSulfideMassFraction(), 1.0e-15);
    assertEquals(base.getLiquidProductKgPerTonneFeed(), doubled.getLiquidProductKgPerTonneFeed(), 1.0e-12);
    assertEquals(base.getExportGasKgPerTonneFeed(), doubled.getExportGasKgPerTonneFeed(), 1.0e-12);
  }

  @Test
  void reportsComponentAndFeedNormalizedClosures() {
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt receipt = RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt
        .calculate(publicThroughput(1000.0));

    assertEquals(1.0, receipt.getLiquidProductMassFraction() + receipt.getExportGasMassFraction(), 1.0e-15);
    assertEquals(1.0, receipt.getExportHydrogenMassFraction() + receipt.getExportHydrogenSulfideMassFraction()
        + receipt.getExportAmmoniaMassFraction() + receipt.getExportNonHydrogenMassFraction(), 1.0e-15);
    assertEquals(995.4894479786512, receipt.getLiquidProductKgPerTonneFeed(), 1.0e-9);
    assertEquals(7.61202393313295, receipt.getExportGasKgPerTonneFeed(), 1.0e-9);
    assertEquals(4.327807878394079, receipt.getExportHydrogenSulfideKgPerTonneFeed(), 1.0e-12);
    assertEquals(1.3194459789230566, receipt.getExportAmmoniaKgPerTonneFeed(), 1.0e-12);
  }

  @Test
  void nullInputFailsClosed() {
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt.calculate(null));
  }

  private static RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance publicThroughput(
      double feedMassFlowKgPerHour) {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    return RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance.calculate(recycle,
        feedMassFlowKgPerHour);
  }
}
