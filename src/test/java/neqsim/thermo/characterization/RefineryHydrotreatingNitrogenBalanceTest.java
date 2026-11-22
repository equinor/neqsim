package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the fail-closed hydrotreating nitrogen, hydrogen, and ammonia receipt. */
public class RefineryHydrotreatingNitrogenBalanceTest {
  private static final double BIG_HILL_NITROGEN_MASS_FRACTION = 0.001095129;
  private static final double TARGET_NITROGEN_MASS_FRACTION = 10.0e-6;

  @Test
  public void publicDoeBigHillScreenClosesMassNitrogenAndTarget() {
    RefineryHydrotreatingNitrogenBalance receipt = RefineryHydrotreatingNitrogenBalance.calculate(1000.0,
        BIG_HILL_NITROGEN_MASS_FRACTION, TARGET_NITROGEN_MASS_FRACTION, 4.0);

    assertEquals(1.095129, receipt.getInitialNitrogenMassKg(), 1.0e-12);
    assertEquals(1.0851359469655435, receipt.getNitrogenRemovedMassKg(), 1.0e-12);
    assertEquals(0.009993053034456523, receipt.getRemainingNitrogenMassKg(), 1.0e-12);
    assertEquals(0.6247030282033714, receipt.getHydrogenConsumedMassKg(), 1.0e-12);
    assertEquals(1.3193995825418077, receipt.getAmmoniaProducedMassKg(), 1.0e-12);
    assertEquals(0.3904393926271071, receipt.getHydrogenRetainedInLiquidMassKg(), 1.0e-12);
    assertEquals(999.3053034456616, receipt.getProductMassKg(), 1.0e-10);
    assertEquals(TARGET_NITROGEN_MASS_FRACTION, receipt.getAchievedProductNitrogenMassFraction(), 1.0e-15);
    assertEquals(0.0, receipt.getTotalMassBalanceResidualKg(), 1.0e-10);
    assertEquals(0.0, receipt.getNitrogenBalanceResidualKg(), 1.0e-12);
  }

  @Test
  public void assayFactoryUsesBulkNitrogenWithoutMutation() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.clearCuts();
    assay.addCut(new AssayCut("DOE Big Hill reconstructed crude").withMassFraction(1.0)
        .withNitrogenMassFraction(BIG_HILL_NITROGEN_MASS_FRACTION));
    int componentCount = system.getNumberOfComponents();

    RefineryHydrotreatingNitrogenBalance receipt = RefineryHydrotreatingNitrogenBalance.calculateForAssay(1000.0, assay,
        TARGET_NITROGEN_MASS_FRACTION, 4.0);

    assertEquals(BIG_HILL_NITROGEN_MASS_FRACTION, receipt.getFeedNitrogenMassFraction(), 0.0);
    assertEquals(componentCount, system.getNumberOfComponents());
  }

  @Test
  public void scalingPreservesMassFractions() {
    RefineryHydrotreatingNitrogenBalance basis = RefineryHydrotreatingNitrogenBalance.calculate(1000.0,
        BIG_HILL_NITROGEN_MASS_FRACTION, TARGET_NITROGEN_MASS_FRACTION, 4.0);
    RefineryHydrotreatingNitrogenBalance doubled = RefineryHydrotreatingNitrogenBalance.calculate(2000.0,
        BIG_HILL_NITROGEN_MASS_FRACTION, TARGET_NITROGEN_MASS_FRACTION, 4.0);

    assertEquals(2.0 * basis.getNitrogenRemovedMassKg(), doubled.getNitrogenRemovedMassKg(), 1.0e-12);
    assertEquals(2.0 * basis.getHydrogenConsumedMassKg(), doubled.getHydrogenConsumedMassKg(), 1.0e-12);
    assertEquals(2.0 * basis.getAmmoniaProducedMassKg(), doubled.getAmmoniaProducedMassKg(), 1.0e-12);
    assertEquals(TARGET_NITROGEN_MASS_FRACTION, doubled.getAchievedProductNitrogenMassFraction(), 1.0e-15);
  }

  @Test
  public void targetEqualToFeedProducesZeroRemovalReceipt() {
    RefineryHydrotreatingNitrogenBalance receipt = RefineryHydrotreatingNitrogenBalance.calculate(1000.0, 0.001, 0.001,
        1.5);

    assertEquals(0.0, receipt.getNitrogenRemovedMassKg(), 0.0);
    assertEquals(0.0, receipt.getHydrogenConsumedMassKg(), 0.0);
    assertEquals(0.0, receipt.getAmmoniaProducedMassKg(), 0.0);
    assertEquals(1000.0, receipt.getProductMassKg(), 0.0);
    assertEquals(0.001, receipt.getAchievedProductNitrogenMassFraction(), 0.0);
  }

  @Test
  public void invalidInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingNitrogenBalance.calculate(0.0, 0.001, 10.0e-6, 4.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingNitrogenBalance.calculate(1000.0, Double.NaN, 10.0e-6, 4.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingNitrogenBalance.calculate(1000.0, 0.001, 0.002, 4.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingNitrogenBalance.calculate(1000.0, 0.001, 10.0e-6, 1.499));
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingNitrogenBalance.calculateForAssay(1000.0, null, 10.0e-6, 4.0));
  }
}
