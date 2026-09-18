package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests hydrotreating makeup-gas and outlet-gas material-balance receipts. */
public class RefineryHydrotreatingHydrogenSupplyBalanceTest {
  private static final double NITROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  public void publicBigHillScreenClosesMakeupAndOutletGas() {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.0040867518, 15.0e-6, 2.0);

    RefineryHydrotreatingHydrogenSupplyBalance gas =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 1.5, 0.90,
            NITROGEN_MOLAR_MASS_KG_PER_MOL);

    assertSame(sulfur, gas.getSulfurBalance());
    assertEquals(253.97123337252972, gas.getHydrogenConsumedMoles(), 1.0e-11);
    assertEquals(380.9568500587946, gas.getHydrogenSuppliedMoles(), 1.0e-11);
    assertEquals(126.98561668626488, gas.getUnreactedHydrogenMoles(), 1.0e-11);
    assertEquals(42.328538895421616, gas.getNonHydrogenMoles(), 1.0e-11);
    assertEquals(423.2853889542162, gas.getMakeupGasMoles(), 1.0e-11);
    assertEquals(1.9537295863895268, gas.getMakeupGasMassKg(), 1.0e-12);
    assertEquals(126.98561668626486, gas.getHydrogenSulfideMoles(), 1.0e-11);
    assertEquals(296.2997722679514, gas.getOutletGasMoles(), 1.0e-10);
    assertEquals(5.769550858743105, gas.getOutletGasMassKg(), 1.0e-12);
    assertEquals(3.0 / 7.0, gas.getOutletHydrogenMoleFraction(), 1.0e-15);
    assertEquals(3.0 / 7.0, gas.getOutletHydrogenSulfideMoleFraction(), 1.0e-15);
    assertEquals(1.0 / 7.0, gas.getOutletNonHydrogenMoleFraction(), 1.0e-15);
    assertEquals(0.0, gas.getOverallMassBalanceResidualKg(), 1.0e-10);
  }

  @Test
  public void stoichiometricPureHydrogenLeavesOnlyHydrogenSulfideGas() {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.004, 15.0e-6, 1.0);
    RefineryHydrotreatingHydrogenSupplyBalance gas =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 1.0, 1.0,
            NITROGEN_MOLAR_MASS_KG_PER_MOL);

    assertEquals(0.0, gas.getUnreactedHydrogenMoles(), 0.0);
    assertEquals(0.0, gas.getNonHydrogenMoles(), 0.0);
    assertEquals(0.0, gas.getOutletHydrogenMoleFraction(), 0.0);
    assertEquals(1.0, gas.getOutletHydrogenSulfideMoleFraction(), 1.0e-15);
    assertEquals(0.0, gas.getOverallMassBalanceResidualKg(), 1.0e-10);
  }

  @Test
  public void zeroSulfurRemovalRequiresNoMakeupGas() {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.004, 0.004, 1.0);
    RefineryHydrotreatingHydrogenSupplyBalance gas =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 1.5, 0.90,
            NITROGEN_MOLAR_MASS_KG_PER_MOL);

    assertEquals(0.0, gas.getMakeupGasMoles(), 0.0);
    assertEquals(0.0, gas.getMakeupGasMassKg(), 0.0);
    assertEquals(0.0, gas.getOutletGasMoles(), 0.0);
    assertEquals(0.0, gas.getOutletGasMassKg(), 0.0);
    assertEquals(0.0, gas.getOutletHydrogenMoleFraction(), 0.0);
    assertEquals(0.0, gas.getOutletHydrogenSulfideMoleFraction(), 0.0);
    assertEquals(0.0, gas.getOutletNonHydrogenMoleFraction(), 0.0);
  }

  @Test
  public void gasReceiptsScaleWithFeedMass() {
    RefineryHydrotreatingSulfurBalance one =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.004, 15.0e-6, 2.0);
    RefineryHydrotreatingSulfurBalance two =
        RefineryHydrotreatingSulfurBalance.calculate(2000.0, 0.004, 15.0e-6, 2.0);
    RefineryHydrotreatingHydrogenSupplyBalance first =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(one, 1.5, 0.90,
            NITROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingHydrogenSupplyBalance second =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(two, 1.5, 0.90,
            NITROGEN_MOLAR_MASS_KG_PER_MOL);

    assertEquals(2.0 * first.getMakeupGasMassKg(), second.getMakeupGasMassKg(), 1.0e-12);
    assertEquals(2.0 * first.getOutletGasMassKg(), second.getOutletGasMassKg(), 1.0e-12);
    assertEquals(first.getOutletHydrogenMoleFraction(),
        second.getOutletHydrogenMoleFraction(), 0.0);
  }

  @Test
  public void invalidInputsFailClosed() {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.004, 15.0e-6, 2.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingHydrogenSupplyBalance.calculate(null, 1.5, 0.90,
            NITROGEN_MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 0.999, 0.90,
            NITROGEN_MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, Double.NaN,
            0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 1.5, 0.0,
            NITROGEN_MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 1.5, 1.01,
            NITROGEN_MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 1.5, 0.90,
            Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 1.5, 0.90,
            0.0));
  }
}
