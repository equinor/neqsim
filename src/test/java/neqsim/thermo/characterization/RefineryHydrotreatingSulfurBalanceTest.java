package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the fail-closed hydrotreating sulfur and hydrogen accounting receipt. */
public class RefineryHydrotreatingSulfurBalanceTest {
  private static final double BIG_HILL_SULFUR_MASS_FRACTION = 0.0040867518;
  private static final double TARGET_SULFUR_MASS_FRACTION = 15.0e-6;

  @Test
  public void publicDoeBigHillScreenClosesMassSulfurAndTarget() {
    RefineryHydrotreatingSulfurBalance receipt =
        RefineryHydrotreatingSulfurBalance.calculate(
            1000.0, BIG_HILL_SULFUR_MASS_FRACTION, TARGET_SULFUR_MASS_FRACTION, 2.0);

    assertEquals(4.0867518, receipt.getInitialSulfurMassKg(), 1.0e-12);
    assertEquals(4.071809037319086, receipt.getSulfurRemovedMassKg(), 1.0e-12);
    assertEquals(0.014942762680914434, receipt.getRemainingSulfurMassKg(), 1.0e-12);
    assertEquals(0.5119755299310151, receipt.getHydrogenConsumedMassKg(), 1.0e-12);
    assertEquals(4.327796802284593, receipt.getHydrogenSulfideProducedMassKg(), 1.0e-12);
    assertEquals(0.25598776496550757, receipt.getHydrogenRetainedInLiquidMassKg(), 1.0e-12);
    assertEquals(996.1841787276463, receipt.getProductMassKg(), 1.0e-10);
    assertEquals(
        TARGET_SULFUR_MASS_FRACTION,
        receipt.getAchievedProductSulfurMassFraction(),
        1.0e-15);
    assertEquals(0.0, receipt.getTotalMassBalanceResidualKg(), 1.0e-10);
    assertEquals(0.0, receipt.getSulfurBalanceResidualKg(), 1.0e-12);
  }

  @Test
  public void assayFactoryUsesBulkSulfurWithoutMutation() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.clearCuts();
    assay.addCut(
        new AssayCut("DOE Big Hill reconstructed crude")
            .withMassFraction(1.0)
            .withSulfurMassFraction(BIG_HILL_SULFUR_MASS_FRACTION));
    int componentCount = system.getNumberOfComponents();

    RefineryHydrotreatingSulfurBalance receipt =
        RefineryHydrotreatingSulfurBalance.calculateForAssay(
            1000.0, assay, TARGET_SULFUR_MASS_FRACTION, 2.0);

    assertEquals(BIG_HILL_SULFUR_MASS_FRACTION, receipt.getFeedSulfurMassFraction(), 0.0);
    assertEquals(componentCount, system.getNumberOfComponents());
  }

  @Test
  public void targetEqualToFeedProducesZeroRemovalReceipt() {
    RefineryHydrotreatingSulfurBalance receipt =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.004, 0.004, 1.0);

    assertEquals(0.0, receipt.getSulfurRemovedMassKg(), 0.0);
    assertEquals(0.0, receipt.getHydrogenConsumedMassKg(), 0.0);
    assertEquals(0.0, receipt.getHydrogenSulfideProducedMassKg(), 0.0);
    assertEquals(1000.0, receipt.getProductMassKg(), 0.0);
    assertEquals(0.004, receipt.getAchievedProductSulfurMassFraction(), 0.0);
  }

  @Test
  public void invalidInputsFailClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurBalance.calculate(0.0, 0.004, 15.0e-6, 2.0));
    assertThrows(
        IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurBalance.calculate(1000.0, Double.NaN, 15.0e-6, 2.0));
    assertThrows(
        IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.004, 0.005, 2.0));
    assertThrows(
        IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.004, 15.0e-6, 0.999));
    assertThrows(
        NullPointerException.class,
        () ->
            RefineryHydrotreatingSulfurBalance.calculateForAssay(
                1000.0, null, 15.0e-6, 2.0));
  }
}
