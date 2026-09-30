package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests qualified D86 reference-point ingestion into refinery assay boundaries. */
class OilAssayCharacterisationD86ReferencePointTest {
  private static final double[] D86_C = {36.5, 54.1, 76.9, 101.5, 131.0, 171.0, 186.5};
  private static final double[] PUBLISHED_TBP_C = {14.1, 33.4, 68.9, 101.6, 135.1, 180.5, 194.1};
  private static final double[] SPECIFIC_GRAVITY = {0.70, 0.73, 0.76, 0.79, 0.82, 0.85, 0.88};
  private static final double[] VOLUME_FRACTION = {0.10, 0.20, 0.20, 0.20, 0.20, 0.05, 0.05};
  private static final double TERMINAL_TBP_C = 225.0;

  @Test
  void buildsQualifiedBoundariesAndAppliesSevenPseudoComponents() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.clearCuts();
    assay.setTotalAssayMass(1.0);

    assay.addD86ReferencePointCutBoundariesCelsius("D86Cut", D86_C, TERMINAL_TBP_C, SPECIFIC_GRAVITY);

    assertEquals(7, assay.getCuts().size());
    for (int i = 0; i < assay.getCuts().size(); i++) {
      AssayCut cut = assay.getCuts().get(i);
      double expectedUpperC = i + 1 < PUBLISHED_TBP_C.length ? PUBLISHED_TBP_C[i + 1] : TERMINAL_TBP_C;
      assertEquals(VOLUME_FRACTION[i], cut.getVolumeFraction(), 1.0e-12);
      assertEquals(PUBLISHED_TBP_C[i], cut.getLowerBoilingPointKelvin() - 273.15, 0.08,
          "published rounded lower TBP boundary");
      assertEquals(expectedUpperC, cut.getUpperBoilingPointKelvin() - 273.15, 0.08,
          "published rounded upper TBP boundary");
    }

    double resolvedMassFraction = 0.0;
    for (double fraction : assay.getResolvedMassFractions()) {
      resolvedMassFraction += fraction;
    }
    assertEquals(1.0, resolvedMassFraction, 1.0e-12);

    assay.apply();
    assertEquals(7, system.getNumberOfComponents());

    double reconstructedMass = 0.0;
    for (int i = 0; i < 7; i++) {
      ComponentInterface component = system.getComponent("D86Cut" + (i + 1) + "_PC");
      reconstructedMass += component.getNumberOfmoles() * component.getMolarMass();
    }
    assertEquals(1.0, reconstructedMass, 1.0e-8);
  }

  @Test
  void rejectsInvalidReferenceInputsWithoutMutatingAssay() {
    OilAssayCharacterisation assay = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    assay.clearCuts();

    assertThrows(IllegalArgumentException.class, () -> assay.addD86ReferencePointCutBoundariesCelsius("D86Cut",
        new double[] {36.5, 54.1}, TERMINAL_TBP_C, SPECIFIC_GRAVITY));
    assertTrue(assay.getCuts().isEmpty());

    double[] outOfDomainD86 = D86_C.clone();
    outOfDomainD86[1] = 34.9;
    assertThrows(IllegalArgumentException.class, () -> assay.addD86ReferencePointCutBoundariesCelsius("D86Cut",
        outOfDomainD86, TERMINAL_TBP_C, SPECIFIC_GRAVITY));
    assertTrue(assay.getCuts().isEmpty());

    assertThrows(IllegalArgumentException.class,
        () -> assay.addD86ReferencePointCutBoundariesCelsius("D86Cut", D86_C, Double.NaN, SPECIFIC_GRAVITY));
    assertTrue(assay.getCuts().isEmpty());

    assertThrows(IllegalArgumentException.class,
        () -> assay.addD86ReferencePointCutBoundariesCelsius("D86Cut", D86_C, PUBLISHED_TBP_C[6], SPECIFIC_GRAVITY));
    assertTrue(assay.getCuts().isEmpty());

    assertThrows(IllegalArgumentException.class,
        () -> assay.addD86ReferencePointCutBoundariesCelsius("D86Cut", D86_C, TERMINAL_TBP_C, new double[] {0.70}));
    assertTrue(assay.getCuts().isEmpty());
  }
}
