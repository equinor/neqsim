package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public DOE refinery-assay qualification of linear total-nitrogen bookkeeping. */
public class OilAssayCharacterisationDoeBigHillNitrogenTest {
  private static final double[] MASS_YIELD_PERCENT = { 1.70, 5.22, 8.32, 12.55, 16.19, 13.18, 18.44, 12.84, 11.56 };
  private static final double[] NITROGEN_MASS_PERCENT = { 0.0, 0.0, 0.0, 0.0, 0.0018, 0.0186, 0.102, 0.234, 0.501 };
  private static final double DOE_WHOLE_CRUDE_NITROGEN_MASS_PERCENT = 0.11;

  @Test
  public void doeNonOverlappingSlateReproducesWholeCrudeNitrogen() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    addDoeCuts(assay, false);

    double resolvedMassClosure = 0.0;
    for (double massFraction : assay.getResolvedMassFractions()) {
      resolvedMassClosure += massFraction;
    }
    assertEquals(1.0, resolvedMassClosure, 1.0e-15);
    assertEquals(0.1095129, assay.getBulkNitrogenMassPercent(), 1.0e-12);
    assertEquals(DOE_WHOLE_CRUDE_NITROGEN_MASS_PERCENT, assay.getBulkNitrogenMassPercent(), 0.001);
    assertEquals(0.0004871, Math.abs(assay.getBulkNitrogenMassPercent() - DOE_WHOLE_CRUDE_NITROGEN_MASS_PERCENT),
        1.0e-12);
    assertEquals(0.001095129, assay.getBulkNitrogenMassFraction(), 1.0e-12);
    assertEquals(0, system.getNumberOfComponents(), "Property queries must not mutate the thermodynamic system");
  }

  @Test
  public void inputOrderAndFractionPercentViewsRemainEquivalent() {
    OilAssayCharacterisation forward = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    addDoeCuts(forward, false);

    OilAssayCharacterisation reverse = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    addDoeCuts(reverse, true);

    OilAssayCharacterisation fractionView = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    for (int i = 0; i < MASS_YIELD_PERCENT.length; i++) {
      fractionView.addCut(new AssayCut("Fraction" + i).withMassFraction(MASS_YIELD_PERCENT[i] / 100.0)
          .withNitrogenMassFraction(NITROGEN_MASS_PERCENT[i] / 100.0));
    }

    assertEquals(forward.getBulkNitrogenMassFraction(), reverse.getBulkNitrogenMassFraction(), 1.0e-15);
    assertEquals(forward.getBulkNitrogenMassFraction(), fractionView.getBulkNitrogenMassFraction(), 1.0e-15);
  }

  @Test
  public void volumeBasisUsesResolvedMassFractions() {
    OilAssayCharacterisation assay = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    assay.addCut(new AssayCut("Light").withVolumeFraction(0.25).withSpecificGravity(0.80).withNitrogenMassPercent(0.1));
    assay.addCut(new AssayCut("Heavy").withVolumeFraction(0.75).withSpecificGravity(0.90).withNitrogenMassPercent(0.5));

    double expected = (0.25 * 0.80 * 0.001 + 0.75 * 0.90 * 0.005) / (0.25 * 0.80 + 0.75 * 0.90);
    assertEquals(expected, assay.getBulkNitrogenMassFraction(), 1.0e-15);
  }

  @Test
  public void missingPositiveCutNitrogenFailsClosed() {
    OilAssayCharacterisation incomplete = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    incomplete.addCut(new AssayCut("Known").withMassFraction(0.75).withNitrogenMassPercent(0.2));
    incomplete.addCut(new AssayCut("Missing").withMassFraction(0.25));
    assertThrows(IllegalStateException.class, incomplete::getBulkNitrogenMassFraction);

    OilAssayCharacterisation zeroYield = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    zeroYield.addCut(new AssayCut("Known").withMassFraction(1.0).withNitrogenMassPercent(0.2));
    zeroYield.addCut(new AssayCut("ZeroYield").withMassFraction(0.0));
    assertEquals(0.002, zeroYield.getBulkNitrogenMassFraction(), 1.0e-15);
  }

  @Test
  public void nitrogenInputsRejectValuesOutsidePhysicalBounds() {
    assertThrows(IllegalArgumentException.class, () -> new AssayCut("Negative").withNitrogenMassFraction(-1.0e-9));
    assertThrows(IllegalArgumentException.class,
        () -> new AssayCut("AboveFraction").withNitrogenMassFraction(1.0000001));
    assertThrows(IllegalArgumentException.class, () -> new AssayCut("AbovePercent").withNitrogenMassPercent(100.00001));
    assertFalse(new AssayCut("Unset").hasNitrogenMassFraction());
  }

  @Test
  public void nitrogenMetadataSurvivesSystemClone() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addCut(new AssayCut("NitrogenCut").withMassFraction(1.0).withNitrogenMassPercent(0.11));

    SystemInterface clonedSystem = system.clone();
    OilAssayCharacterisation clonedAssay = clonedSystem.getOilAssayCharacterisation();

    assertEquals(0.11, clonedAssay.getBulkNitrogenMassPercent(), 1.0e-15);
    assertEquals(0.0011, clonedAssay.getCuts().get(0).getNitrogenMassFraction(), 1.0e-15);
  }

  private static void addDoeCuts(OilAssayCharacterisation assay, boolean reverse) {
    assay.clearCuts();
    List<AssayCut> cuts = new ArrayList<AssayCut>();
    for (int i = 0; i < MASS_YIELD_PERCENT.length; i++) {
      cuts.add(new AssayCut("DOE_BH_2021_" + i).withWeightPercent(MASS_YIELD_PERCENT[i])
          .withNitrogenMassPercent(NITROGEN_MASS_PERCENT[i]));
    }
    if (reverse) {
      Collections.reverse(cuts);
    }
    assay.addCuts(cuts);
  }
}
