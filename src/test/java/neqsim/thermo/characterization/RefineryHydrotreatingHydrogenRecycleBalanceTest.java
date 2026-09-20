package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests steady-state hydrotreating hydrogen recycle and purge receipts. */
public class RefineryHydrotreatingHydrogenRecycleBalanceTest {
  private static final double NITROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  public void publicBigHillScreenClosesFreshRecycleAndExportGas() {
    RefineryHydrotreatingHydrogenSupplyBalance supply = publicSupply(1000.0);

    RefineryHydrotreatingHydrogenRecycleBalance recycle =
        RefineryHydrotreatingHydrogenRecycleBalance.calculate(supply, 0.90, 0.10, 0.50, 0.05);

    assertSame(supply, recycle.getSupplyBalance());
    assertEquals(0.855, recycle.getEffectiveHydrogenRecycleFraction(), 0.0);
    assertEquals(0.095, recycle.getEffectiveHydrogenSulfideRecycleFraction(), 0.0);
    assertEquals(0.475, recycle.getEffectiveNonHydrogenRecycleFraction(), 0.0);
    assertEquals(108.57270226675647, recycle.getRecycleHydrogenMoles(), 1.0e-11);
    assertEquals(272.3841477920381, recycle.getFreshHydrogenMoles(), 1.0e-11);
    assertEquals(302.64905310226453, recycle.getFreshMakeupGasMoles(), 1.0e-11);
    assertEquals(1.3969166542685105, recycle.getFreshMakeupGasMassKg(), 1.0e-12);
    assertEquals(57.64743868614557, recycle.getReactorOutletNonHydrogenMoles(), 1.0e-11);
    assertEquals(140.31559854835896, recycle.getReactorOutletHydrogenSulfideMoles(), 1.0e-11);
    assertEquals(149.28521750476972, recycle.getRecycleGasMoles(), 1.0e-11);
    assertEquals(175.66343641599968, recycle.getExportGasMoles(), 1.0e-11);
    assertEquals(5.212737926622088, recycle.getExportGasMassKg(), 1.0e-12);
    assertEquals(0.10481927710843379, recycle.getExportHydrogenMoleFraction(), 1.0e-15);
    assertEquals(0.7228915662650605, recycle.getExportHydrogenSulfideMoleFraction(), 1.0e-15);
    assertEquals(0.17228915662650587, recycle.getExportNonHydrogenMoleFraction(), 1.0e-15);
    assertEquals(0.285, recycle.getFreshHydrogenReductionFraction(), 1.0e-15);
    assertEquals(0.0, recycle.getHydrogenBalanceResidualMoles(), 1.0e-12);
    assertEquals(0.0, recycle.getHydrogenSulfideBalanceResidualMoles(), 1.0e-12);
    assertEquals(0.0, recycle.getNonHydrogenBalanceResidualMoles(), 1.0e-12);
    assertEquals(0.0, recycle.getOverallMassBalanceResidualKg(), 1.0e-10);
  }

  @Test
  public void fullPurgeReproducesOnceThroughReceipt() {
    RefineryHydrotreatingHydrogenSupplyBalance supply = publicSupply(1000.0);
    RefineryHydrotreatingHydrogenRecycleBalance recycle =
        RefineryHydrotreatingHydrogenRecycleBalance.calculate(supply, 1.0, 1.0, 1.0, 1.0);

    assertEquals(0.0, recycle.getRecycleGasMoles(), 0.0);
    assertEquals(supply.getMakeupGasMoles(), recycle.getFreshMakeupGasMoles(), 1.0e-12);
    assertEquals(supply.getMakeupGasMassKg(), recycle.getFreshMakeupGasMassKg(), 1.0e-12);
    assertEquals(supply.getOutletGasMoles(), recycle.getExportGasMoles(), 1.0e-12);
    assertEquals(supply.getOutletGasMassKg(), recycle.getExportGasMassKg(), 1.0e-12);
    assertEquals(0.0, recycle.getFreshHydrogenReductionFraction(), 0.0);
  }

  @Test
  public void zeroSulfurRemovalProducesZeroGasReceipt() {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.004, 0.004, 1.0);
    RefineryHydrotreatingHydrogenSupplyBalance supply =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(
            sulfur, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);

    RefineryHydrotreatingHydrogenRecycleBalance recycle =
        RefineryHydrotreatingHydrogenRecycleBalance.calculate(supply, 1.0, 1.0, 1.0, 0.0);

    assertEquals(0.0, recycle.getFreshMakeupGasMoles(), 0.0);
    assertEquals(0.0, recycle.getReactorOutletGasMoles(), 0.0);
    assertEquals(0.0, recycle.getRecycleGasMoles(), 0.0);
    assertEquals(0.0, recycle.getExportGasMoles(), 0.0);
    assertEquals(0.0, recycle.getOverallMassBalanceResidualKg(), 0.0);
  }

  @Test
  public void recycleReceiptsScaleWithFeedMass() {
    RefineryHydrotreatingHydrogenRecycleBalance one =
        RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            publicSupply(1000.0), 0.90, 0.10, 0.50, 0.05);
    RefineryHydrotreatingHydrogenRecycleBalance two =
        RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            publicSupply(2000.0), 0.90, 0.10, 0.50, 0.05);

    assertEquals(2.0 * one.getFreshMakeupGasMassKg(), two.getFreshMakeupGasMassKg(), 1.0e-12);
    assertEquals(2.0 * one.getRecycleGasMassKg(), two.getRecycleGasMassKg(), 1.0e-12);
    assertEquals(2.0 * one.getExportGasMassKg(), two.getExportGasMassKg(), 1.0e-12);
    assertEquals(one.getExportHydrogenMoleFraction(), two.getExportHydrogenMoleFraction(), 0.0);
  }

  @Test
  public void positiveSourceWithoutPurgeOrRejectionFailsClosed() {
    RefineryHydrotreatingHydrogenSupplyBalance supply = publicSupply(1000.0);

    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, 0.90, 1.0, 0.50, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, 0.90, 0.10, 1.0, 0.0));
  }

  @Test
  public void invalidInputsFailClosed() {
    RefineryHydrotreatingHydrogenSupplyBalance supply = publicSupply(1000.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            null, 0.90, 0.10, 0.50, 0.05));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, -0.01, 0.10, 0.50, 0.05));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, 0.90, 1.01, 0.50, 0.05));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, 0.90, 0.10, Double.NaN, 0.05));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, 0.90, 0.10, 0.50, 1.01));
  }

  private static RefineryHydrotreatingHydrogenSupplyBalance publicSupply(double feedMassKg) {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(
            feedMassKg, 0.0040867518, 15.0e-6, 2.0);
    return RefineryHydrotreatingHydrogenSupplyBalance.calculate(
        sulfur, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);
  }
}
