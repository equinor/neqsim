package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests coupled sulfur/nitrogen recycle-throughput receipts. */
class RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalanceTest {
  private static final double NITROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  void publicBigHillReceiptScalesEveryExternalAndRecycleStream() {
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = publicRecycle();
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance rate = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, 1000.0);

    assertSame(recycle, rate.getRecycleBalance());
    assertEquals(1.0, rate.getBasisScalePerHour(), 0.0);
    assertEquals(recycle.getSupplyBalance().getMaterialBalance().getProductMassKg(), rate.getProductMassFlowKgPerHour(),
        1.0e-12);
    assertEquals(3.101471911784249, rate.getFreshMakeupGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(recycle.getRecycleGasMassKg(), rate.getRecycleGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(7.612023933132949, rate.getExportGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.6047546079509425, rate.getFreshHydrogenMolarFlowKmolPerHour(), 1.0e-12);
    assertEquals(0.24105603253988616, rate.getRecycleHydrogenMolarFlowKmolPerHour(), 1.0e-12);
    assertEquals(0.0, rate.getExternalMassBalanceResidualKgPerHour(), 1.0e-10);
  }

  @Test
  void callerThroughputScalesQualifiedBasisWithoutChangingComposition() {
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = publicRecycle();
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance one = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, 1000.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance two = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, 2000.0);

    assertEquals(2.0 * one.getProductMassFlowKgPerHour(), two.getProductMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * one.getFreshMakeupGasMassFlowKgPerHour(), two.getFreshMakeupGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * one.getRecycleAmmoniaMolarFlowKmolPerHour(), two.getRecycleAmmoniaMolarFlowKmolPerHour(),
        1.0e-12);
    assertEquals(2.0 * one.getExportHydrogenSulfideMolarFlowKmolPerHour(),
        two.getExportHydrogenSulfideMolarFlowKmolPerHour(), 1.0e-12);
  }

  @Test
  void invalidInputsFailClosed() {
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = publicRecycle();

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance.calculate(null, 1000.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance.calculate(recycle, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance.calculate(recycle, Double.NaN));
  }

  private static RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance publicRecycle() {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);
    return RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance.calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
  }
}
