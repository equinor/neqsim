package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests the unit-explicit hydrotreating throughput receipt. */
public class RefineryHydrotreatingThroughputBalanceTest {
  private static final double NITROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  public void publicBigHillBasisClosesRateReceipt() {
    RefineryHydrotreatingHydrogenRecycleBalance recycle = publicRecycle(1000.0);
    RefineryHydrotreatingThroughputBalance rate = RefineryHydrotreatingThroughputBalance.calculate(recycle, 1000.0);

    assertSame(recycle, rate.getRecycleBalance());
    assertEquals(1.0, rate.getBasisScalePerHour(), 0.0);
    assertEquals(4.071809037319086, rate.getSulfurRemovedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.5119755299310151, rate.getHydrogenConsumedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(996.1841787276463, rate.getLiquidProductMassFlowKgPerHour(), 1.0e-10);
    assertEquals(1.3969166542685105, rate.getFreshMakeupGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(1.4402465113605114, rate.getRecycleGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(5.212737926622088, rate.getExportGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.2723841477920381, rate.getFreshHydrogenMolarFlowKmolPerHour(), 1.0e-13);
    assertEquals(0.10857270226675647, rate.getRecycleHydrogenMolarFlowKmolPerHour(), 1.0e-13);
    assertEquals(0.0, rate.getOverallMassBalanceResidualKgPerHour(), 1.0e-10);
  }

  @Test
  public void ratesScaleWithoutMutatingBasis() {
    RefineryHydrotreatingHydrogenRecycleBalance recycle = publicRecycle(1000.0);
    RefineryHydrotreatingThroughputBalance one = RefineryHydrotreatingThroughputBalance.calculate(recycle, 1000.0);
    RefineryHydrotreatingThroughputBalance two = RefineryHydrotreatingThroughputBalance.calculate(recycle, 2000.0);

    assertSame(recycle, one.getRecycleBalance());
    assertSame(recycle, two.getRecycleBalance());
    assertEquals(2.0 * one.getFreshMakeupGasMassFlowKgPerHour(), two.getFreshMakeupGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * one.getRecycleGasMassFlowKgPerHour(), two.getRecycleGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * one.getExportHydrogenSulfideMolarFlowKmolPerHour(),
        two.getExportHydrogenSulfideMolarFlowKmolPerHour(), 1.0e-13);
  }

  @Test
  public void zeroRemovalHasOnlyLiquidThroughput() {
    RefineryHydrotreatingSulfurBalance sulfur = RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.004, 0.004, 1.0);
    RefineryHydrotreatingHydrogenSupplyBalance supply = RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur,
        1.0, 1.0, NITROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingHydrogenRecycleBalance recycle = RefineryHydrotreatingHydrogenRecycleBalance.calculate(supply,
        1.0, 1.0, 1.0, 0.0);
    RefineryHydrotreatingThroughputBalance rate = RefineryHydrotreatingThroughputBalance.calculate(recycle, 750.0);

    assertEquals(750.0, rate.getLiquidProductMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, rate.getSulfurRemovedMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, rate.getFreshMakeupGasMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, rate.getRecycleGasMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, rate.getExportGasMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, rate.getOverallMassBalanceResidualKgPerHour(), 0.0);
  }

  @Test
  public void invalidInputsFailClosed() {
    RefineryHydrotreatingHydrogenRecycleBalance recycle = publicRecycle(1000.0);
    assertThrows(NullPointerException.class, () -> RefineryHydrotreatingThroughputBalance.calculate(null, 1000.0));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingThroughputBalance.calculate(recycle, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingThroughputBalance.calculate(recycle, Double.NaN));
  }

  private static RefineryHydrotreatingHydrogenRecycleBalance publicRecycle(double feedMassKg) {
    RefineryHydrotreatingSulfurBalance sulfur = RefineryHydrotreatingSulfurBalance.calculate(feedMassKg, 0.0040867518,
        15.0e-6, 2.0);
    RefineryHydrotreatingHydrogenSupplyBalance supply = RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur,
        1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);
    return RefineryHydrotreatingHydrogenRecycleBalance.calculate(supply, 0.90, 0.10, 0.50, 0.05);
  }
}
