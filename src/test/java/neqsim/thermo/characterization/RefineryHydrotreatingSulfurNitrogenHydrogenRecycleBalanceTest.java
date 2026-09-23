package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests coupled sulfur/nitrogen hydrotreating hydrogen recycle and purge receipts. */
class RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalanceTest {
  private static final double NITROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  void publicBigHillScreenClosesFreshRecycleAndExportGas() {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = publicSupply(1000.0);

    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);

    assertSame(supply, recycle.getSupplyBalance());
    assertEquals(0.855, recycle.getEffectiveHydrogenRecycleFraction(), 0.0);
    assertEquals(0.095, recycle.getEffectiveHydrogenSulfideRecycleFraction(), 0.0);
    assertEquals(0.19, recycle.getEffectiveAmmoniaRecycleFraction(), 0.0);
    assertEquals(0.475, recycle.getEffectiveNonHydrogenRecycleFraction(), 0.0);
    assertEquals(241.05603253988616, recycle.getRecycleHydrogenMoles(), 1.0e-9);
    assertEquals(604.7546079509425, recycle.getFreshHydrogenMoles(), 1.0e-9);
    assertEquals(671.9495643899361, recycle.getFreshMakeupGasMoles(), 1.0e-9);
    assertEquals(3.101471911784249, recycle.getFreshMakeupGasMassKg(), 1.0e-12);
    assertEquals(140.31595765739368, recycle.getReactorOutletHydrogenSulfideMoles(), 1.0e-9);
    assertEquals(95.6487274590545, recycle.getReactorOutletAmmoniaMoles(), 1.0e-9);
    assertEquals(127.99039321713063, recycle.getReactorOutletNonHydrogenMoles(), 1.0e-9);
    assertEquals(333.35474351269596, recycle.getRecycleGasMoles(), 1.0e-9);
    assertEquals(312.5372149844924, recycle.getExportGasMoles(), 1.0e-9);
    assertEquals(7.612023933132949, recycle.getExportGasMassKg(), 1.0e-12);
    assertEquals(0.13080313531862703, recycle.getExportHydrogenMoleFraction(), 1.0e-15);
    assertEquals(0.4063066271523605, recycle.getExportHydrogenSulfideMoleFraction(), 1.0e-15);
    assertEquals(0.24789198062598197, recycle.getExportAmmoniaMoleFraction(), 1.0e-15);
    assertEquals(0.21499825690303057, recycle.getExportNonHydrogenMoleFraction(), 1.0e-15);
    assertEquals(0.285, recycle.getFreshHydrogenReductionFraction(), 1.0e-15);
    assertEquals(0.0, recycle.getHydrogenBalanceResidualMoles(), 1.0e-12);
    assertEquals(0.0, recycle.getHydrogenSulfideBalanceResidualMoles(), 1.0e-12);
    assertEquals(0.0, recycle.getAmmoniaBalanceResidualMoles(), 1.0e-12);
    assertEquals(0.0, recycle.getNonHydrogenBalanceResidualMoles(), 1.0e-12);
    assertEquals(0.0, recycle.getOverallMassBalanceResidualKg(), 1.0e-10);
  }

  @Test
  void fullPurgeReproducesOnceThroughReceipt() {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = publicSupply(1000.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 1.0, 1.0, 1.0, 1.0, 1.0);

    assertEquals(0.0, recycle.getRecycleGasMoles(), 0.0);
    assertEquals(supply.getMakeupGasMoles(), recycle.getFreshMakeupGasMoles(), 1.0e-12);
    assertEquals(supply.getMakeupGasMassKg(), recycle.getFreshMakeupGasMassKg(), 1.0e-12);
    assertEquals(supply.getOutletGasMoles(), recycle.getExportGasMoles(), 1.0e-12);
    assertEquals(supply.getOutletGasMassKg(), recycle.getExportGasMassKg(), 1.0e-12);
    assertEquals(0.0, recycle.getFreshHydrogenReductionFraction(), 0.0);
  }

  @Test
  void noRemovalProducesZeroGasReceipt() {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.004, 0.001, 0.004, 0.001, 1.0, 1.5);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);

    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 1.0, 1.0, 1.0, 1.0, 0.0);

    assertEquals(0.0, recycle.getFreshMakeupGasMoles(), 0.0);
    assertEquals(0.0, recycle.getReactorOutletGasMoles(), 0.0);
    assertEquals(0.0, recycle.getRecycleGasMoles(), 0.0);
    assertEquals(0.0, recycle.getExportGasMoles(), 0.0);
    assertEquals(0.0, recycle.getOverallMassBalanceResidualKg(), 0.0);
  }

  @Test
  void receiptsScaleWithFeedMass() {
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance one = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(publicSupply(1000.0), 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance two = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(publicSupply(2000.0), 0.90, 0.10, 0.20, 0.50, 0.05);

    assertEquals(2.0 * one.getFreshMakeupGasMassKg(), two.getFreshMakeupGasMassKg(), 1.0e-12);
    assertEquals(2.0 * one.getRecycleGasMassKg(), two.getRecycleGasMassKg(), 1.0e-12);
    assertEquals(2.0 * one.getExportGasMassKg(), two.getExportGasMassKg(), 1.0e-12);
    assertEquals(one.getExportAmmoniaMoleFraction(), two.getExportAmmoniaMoleFraction(), 0.0);
  }

  @Test
  void positiveSourceWithoutPurgeOrRejectionFailsClosed() {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = publicSupply(1000.0);

    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance.calculate(supply, 0.90, 1.0, 0.20, 0.50, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance.calculate(supply, 0.90, 0.10, 1.0, 0.50, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance.calculate(supply, 0.90, 0.10, 0.20, 1.0, 0.0));
  }

  @Test
  void invalidInputsFailClosed() {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = publicSupply(1000.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance.calculate(null, 0.90, 0.10, 0.20, 0.50, 0.05));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, -0.01, 0.10, 0.20, 0.50, 0.05));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 1.01, 0.20, 0.50, 0.05));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, Double.NaN, 0.50, 0.05));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 1.01));
  }

  private static RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance publicSupply(double feedMassKg) {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance
        .calculate(feedMassKg, 0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
    return RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance.calculate(material, 1.5, 0.90,
        NITROGEN_MOLAR_MASS_KG_PER_MOL);
  }
}
