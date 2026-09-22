package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the coupled sulfur, nitrogen, hydrogen, H2S, and NH3 material-balance receipt. */
public class RefineryHydrotreatingSulfurNitrogenBalanceTest {
  private static final double BIG_HILL_SULFUR_MASS_FRACTION = 0.0040867518;
  private static final double BIG_HILL_NITROGEN_MASS_FRACTION = 0.001095129;
  private static final double TARGET_SULFUR_MASS_FRACTION = 15.0e-6;
  private static final double TARGET_NITROGEN_MASS_FRACTION = 10.0e-6;

  @Test
  public void publicBigHillScreenClosesCoupledTargetsAndMass() {
    RefineryHydrotreatingSulfurNitrogenBalance receipt =
        RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
            BIG_HILL_SULFUR_MASS_FRACTION, BIG_HILL_NITROGEN_MASS_FRACTION,
            TARGET_SULFUR_MASS_FRACTION, TARGET_NITROGEN_MASS_FRACTION, 2.0, 4.0);

    assertEquals(995.4894479786512, receipt.getProductMassKg(), 1.0e-10);
    assertEquals(4.07181945828032, receipt.getSulfurRemovedMassKg(), 1.0e-12);
    assertEquals(1.0851741055202135, receipt.getNitrogenRemovedMassKg(), 1.0e-12);
    assertEquals(0.5119768402275201, receipt.getSulfurHydrogenConsumedMassKg(), 1.0e-12);
    assertEquals(0.6247249957409144, receipt.getNitrogenHydrogenConsumedMassKg(), 1.0e-12);
    assertEquals(1.1367018359684344, receipt.getTotalHydrogenConsumedMassKg(), 1.0e-12);
    assertEquals(4.327807878394079, receipt.getHydrogenSulfideProducedMassKg(), 1.0e-12);
    assertEquals(1.3194459789230566, receipt.getAmmoniaProducedMassKg(), 1.0e-12);
    assertEquals(0.6464415424518316, receipt.getHydrogenRetainedInLiquidMassKg(), 1.0e-12);
    assertEquals(TARGET_SULFUR_MASS_FRACTION,
        receipt.getAchievedProductSulfurMassFraction(), 1.0e-15);
    assertEquals(TARGET_NITROGEN_MASS_FRACTION,
        receipt.getAchievedProductNitrogenMassFraction(), 1.0e-15);
    assertEquals(0.0, receipt.getTotalMassBalanceResidualKg(), 1.0e-10);
    assertEquals(0.0, receipt.getSulfurBalanceResidualKg(), 1.0e-12);
    assertEquals(0.0, receipt.getNitrogenBalanceResidualKg(), 1.0e-12);
  }

  @Test
  public void assayFactoryReadsBothAttributesWithoutMutation() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.clearCuts();
    assay.addCut(new AssayCut("DOE Big Hill reconstructed crude").withMassFraction(1.0)
        .withSulfurMassFraction(BIG_HILL_SULFUR_MASS_FRACTION)
        .withNitrogenMassFraction(BIG_HILL_NITROGEN_MASS_FRACTION));
    int componentCount = system.getNumberOfComponents();

    RefineryHydrotreatingSulfurNitrogenBalance receipt =
        RefineryHydrotreatingSulfurNitrogenBalance.calculateForAssay(1000.0, assay,
            TARGET_SULFUR_MASS_FRACTION, TARGET_NITROGEN_MASS_FRACTION, 2.0, 4.0);

    assertEquals(BIG_HILL_SULFUR_MASS_FRACTION, receipt.getFeedSulfurMassFraction(), 0.0);
    assertEquals(BIG_HILL_NITROGEN_MASS_FRACTION,
        receipt.getFeedNitrogenMassFraction(), 0.0);
    assertEquals(componentCount, system.getNumberOfComponents());
  }

  @Test
  public void scalingPreservesCoupledMassFractions() {
    RefineryHydrotreatingSulfurNitrogenBalance basis =
        RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.004, 0.001,
            TARGET_SULFUR_MASS_FRACTION, TARGET_NITROGEN_MASS_FRACTION, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenBalance doubled =
        RefineryHydrotreatingSulfurNitrogenBalance.calculate(2000.0, 0.004, 0.001,
            TARGET_SULFUR_MASS_FRACTION, TARGET_NITROGEN_MASS_FRACTION, 2.0, 4.0);

    assertEquals(2.0 * basis.getProductMassKg(), doubled.getProductMassKg(), 1.0e-10);
    assertEquals(2.0 * basis.getTotalHydrogenConsumedMassKg(),
        doubled.getTotalHydrogenConsumedMassKg(), 1.0e-12);
    assertEquals(basis.getAchievedProductSulfurMassFraction(),
        doubled.getAchievedProductSulfurMassFraction(), 1.0e-15);
    assertEquals(basis.getAchievedProductNitrogenMassFraction(),
        doubled.getAchievedProductNitrogenMassFraction(), 1.0e-15);
  }

  @Test
  public void equalTargetsProduceZeroRemoval() {
    RefineryHydrotreatingSulfurNitrogenBalance receipt =
        RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.004, 0.001,
            0.004, 0.001, 1.0, 1.5);

    assertEquals(0.0, receipt.getSulfurRemovedMassKg(), 1.0e-15);
    assertEquals(0.0, receipt.getNitrogenRemovedMassKg(), 1.0e-15);
    assertEquals(0.0, receipt.getTotalHydrogenConsumedMassKg(), 1.0e-15);
    assertEquals(1000.0, receipt.getProductMassKg(), 1.0e-12);
  }

  @Test
  public void invalidInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenBalance.calculate(0.0, 0.004, 0.001,
            15.0e-6, 10.0e-6, 2.0, 4.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.004, 0.001,
            0.005, 10.0e-6, 2.0, 4.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.004, 0.001,
            15.0e-6, 0.002, 2.0, 4.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.004, 0.001,
            15.0e-6, 10.0e-6, 0.999, 4.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.004, 0.001,
            15.0e-6, 10.0e-6, 2.0, 1.499));
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenBalance.calculateForAssay(1000.0, null,
            15.0e-6, 10.0e-6, 2.0, 4.0));
  }
}
