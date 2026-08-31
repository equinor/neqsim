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

/** Public DOE refinery-assay qualification of linear total-sulfur bookkeeping. */
public class OilAssayCharacterisationDoeBigHillSulfurTest {
  private static final double[] MASS_YIELD_PERCENT = { 1.70, 5.22, 8.32, 12.55, 16.19, 13.18, 18.44, 12.84, 11.56 };
  private static final double[] SULFUR_MASS_PERCENT = { 0.0, 0.0008, 0.0026, 0.019, 0.096, 0.313, 0.534, 0.752, 1.334 };
  private static final double DOE_WHOLE_CRUDE_SULFUR_MASS_PERCENT = 0.409;

  @Test
  public void doeNonOverlappingSlateReproducesWholeCrudeSulfur() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    addDoeCuts(assay, false);

    double resolvedMassClosure = 0.0;
    for (double massFraction : assay.getResolvedMassFractions()) {
      resolvedMassClosure += massFraction;
    }
    assertEquals(1.0, resolvedMassClosure, 1.0e-15);
    assertEquals(0.40867518, assay.getBulkSulfurMassPercent(), 1.0e-12);
    assertEquals(DOE_WHOLE_CRUDE_SULFUR_MASS_PERCENT, assay.getBulkSulfurMassPercent(), 0.001);
    assertEquals(0.00032482, Math.abs(assay.getBulkSulfurMassPercent() - DOE_WHOLE_CRUDE_SULFUR_MASS_PERCENT), 1.0e-12);
    assertEquals(0.0040867518, assay.getBulkSulfurMassFraction(), 1.0e-12);
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
          .withSulfurMassFraction(SULFUR_MASS_PERCENT[i] / 100.0));
    }

    assertEquals(forward.getBulkSulfurMassFraction(), reverse.getBulkSulfurMassFraction(), 1.0e-15);
    assertEquals(forward.getBulkSulfurMassFraction(), fractionView.getBulkSulfurMassFraction(), 1.0e-15);
  }

  @Test
  public void volumeBasisUsesResolvedMassFractions() {
    OilAssayCharacterisation assay = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    assay.addCut(new AssayCut("Light").withVolumeFraction(0.25).withSpecificGravity(0.80).withSulfurMassPercent(1.0));
    assay.addCut(new AssayCut("Heavy").withVolumeFraction(0.75).withSpecificGravity(0.90).withSulfurMassPercent(3.0));

    double expected = (0.25 * 0.80 * 0.01 + 0.75 * 0.90 * 0.03) / (0.25 * 0.80 + 0.75 * 0.90);
    assertEquals(expected, assay.getBulkSulfurMassFraction(), 1.0e-15);
  }

  @Test
  public void missingPositiveCutSulfurFailsClosed() {
    OilAssayCharacterisation incomplete = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    incomplete.addCut(new AssayCut("Known").withMassFraction(0.75).withSulfurMassPercent(0.2));
    incomplete.addCut(new AssayCut("Missing").withMassFraction(0.25));
    assertThrows(IllegalStateException.class, incomplete::getBulkSulfurMassFraction);

    OilAssayCharacterisation zeroYield = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    zeroYield.addCut(new AssayCut("Known").withMassFraction(1.0).withSulfurMassPercent(0.2));
    zeroYield.addCut(new AssayCut("ZeroYield").withMassFraction(0.0));
    assertEquals(0.002, zeroYield.getBulkSulfurMassFraction(), 1.0e-15);
  }

  @Test
  public void sulfurInputsRejectValuesOutsidePhysicalBounds() {
    assertThrows(IllegalArgumentException.class, () -> new AssayCut("Negative").withSulfurMassFraction(-1.0e-9));
    assertThrows(IllegalArgumentException.class, () -> new AssayCut("AboveFraction").withSulfurMassFraction(1.0000001));
    assertThrows(IllegalArgumentException.class, () -> new AssayCut("AbovePercent").withSulfurMassPercent(100.00001));
    assertFalse(new AssayCut("Unset").hasSulfurMassFraction());
  }

  @Test
  public void sulfurMetadataSurvivesSystemClone() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addCut(new AssayCut("SulfurCut").withMassFraction(1.0).withSulfurMassPercent(0.409));

    SystemInterface clonedSystem = system.clone();
    OilAssayCharacterisation clonedAssay = clonedSystem.getOilAssayCharacterisation();

    assertEquals(0.409, clonedAssay.getBulkSulfurMassPercent(), 1.0e-15);
    assertEquals(0.00409, clonedAssay.getCuts().get(0).getSulfurMassFraction(), 1.0e-15);
  }

  private static void addDoeCuts(OilAssayCharacterisation assay, boolean reverse) {
    assay.clearCuts();
    List<AssayCut> cuts = new ArrayList<AssayCut>();
    for (int i = 0; i < MASS_YIELD_PERCENT.length; i++) {
      cuts.add(new AssayCut("DOE_BH_2021_" + i).withWeightPercent(MASS_YIELD_PERCENT[i])
          .withSulfurMassPercent(SULFUR_MASS_PERCENT[i]));
    }
    if (reverse) {
      Collections.reverse(cuts);
    }
    assay.addCuts(cuts);
  }
}
