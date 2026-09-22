package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalanceTest {
  private static final double NITROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  void qualifiesPublicBigHillGasSupplyReceipt() {
    RefineryHydrotreatingSulfurNitrogenBalance material = publicBigHillBalance();

    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance gas = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);

    assertSame(material, gas.getMaterialBalance());
    assertEquals(563.8737603272191, gas.getHydrogenConsumedMoles(), 1.0e-9);
    assertEquals(845.8106404908286, gas.getHydrogenSuppliedMoles(), 1.0e-9);
    assertEquals(939.7896005453651, gas.getMakeupGasMoles(), 1.0e-9);
    assertEquals(4.337722953544405, gas.getMakeupGasMassKg(), 1.0e-12);
    assertEquals(580.3772511399216, gas.getOutletGasMoles(), 1.0e-9);
    assertEquals(8.848274974893107, gas.getOutletGasMassKg(), 1.0e-12);
    assertEquals(0.4857821005386687, gas.getOutletHydrogenMoleFraction(), 1.0e-12);
    assertEquals(0.21879896469154783, gas.getOutletHydrogenSulfideMoleFraction(), 1.0e-12);
    assertEquals(0.13349156792356046, gas.getOutletAmmoniaMoleFraction(), 1.0e-12);
    assertEquals(0.1619273668462229, gas.getOutletNonHydrogenMoleFraction(), 1.0e-12);
    assertEquals(1.0, gas.getOutletHydrogenMoleFraction() + gas.getOutletHydrogenSulfideMoleFraction()
        + gas.getOutletAmmoniaMoleFraction() + gas.getOutletNonHydrogenMoleFraction(), 1.0e-12);
    assertEquals(0.0, gas.getOverallMassBalanceResidualKg(), 1.0e-12);
  }

  @Test
  void scalesLinearlyAndPreservesComposition() {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance one = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(publicBigHillBalance(), 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenBalance doubledMaterial = RefineryHydrotreatingSulfurNitrogenBalance
        .calculate(2000.0, 0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance two = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(doubledMaterial, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);

    assertEquals(2.0 * one.getMakeupGasMassKg(), two.getMakeupGasMassKg(), 1.0e-12);
    assertEquals(2.0 * one.getOutletGasMassKg(), two.getOutletGasMassKg(), 1.0e-12);
    assertEquals(one.getOutletHydrogenMoleFraction(), two.getOutletHydrogenMoleFraction(), 0.0);
    assertEquals(one.getOutletAmmoniaMoleFraction(), two.getOutletAmmoniaMoleFraction(), 0.0);
  }

  @Test
  void preservesNoRemovalCase() {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.004, 0.001, 0.004, 0.001, 1.0, 1.5);

    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance gas = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);

    assertEquals(0.0, gas.getHydrogenConsumedMoles(), 0.0);
    assertEquals(0.0, gas.getMakeupGasMassKg(), 0.0);
    assertEquals(0.0, gas.getOutletGasMassKg(), 0.0);
    assertEquals(0.0, gas.getOverallMassBalanceResidualKg(), 0.0);
  }

  @Test
  void invalidInputsFailClosed() {
    RefineryHydrotreatingSulfurNitrogenBalance material = publicBigHillBalance();

    assertThrows(NullPointerException.class, () -> RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(null, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 0.999, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.0, NITROGEN_MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 1.01, NITROGEN_MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance.calculate(material, 1.5, 0.90, Double.NaN));
  }

  private static RefineryHydrotreatingSulfurNitrogenBalance publicBigHillBalance() {
    return RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.0040867518, 0.001095129, 15.0e-6, 10.0e-6,
        2.0, 4.0);
  }
}
