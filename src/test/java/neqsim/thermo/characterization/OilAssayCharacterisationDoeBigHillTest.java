package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent public-data qualification for refinery assay bookkeeping.
 *
 * <p>
 * The source is the U.S. Department of Energy Strategic Petroleum Reserve Big Hill Sweet assay, sample MLI 009, dated
 * 1998-05-04, published as Exhibit D to 10 CFR Part 625 Appendix A. This test freezes the bounded 175-1050 degF
 * distillate slice because every included interval has a reported volume yield, weight yield, specific gravity, API
 * gravity, and explicit cut boundary. The light C5-/175 degF fraction and 1050 degF+ residuum are deliberately excluded
 * because they do not both have finite lower and upper boiling boundaries in the published table.
 * </p>
 *
 * <p>
 * Source: https://www.govinfo.gov/content/pkg/CFR-2004-title10-vol4/pdf/ CFR-2004-title10-vol4-chapII-subchapI.pdf (SPR
 * Crude Oil Comprehensive Analysis, Big Hill Sweet, MLI 009). DOE's 2024 Crude Oil Assay Manual documents D2892/D5236
 * fractionation and states that fractions are measured on a mass-percent basis while volume percentages are calculated
 * using fraction specific gravity.
 * </p>
 */
public class OilAssayCharacterisationDoeBigHillTest {
  private static final double[] LOWER_BOUNDARY_F = {175.0, 250.0, 375.0, 530.0, 650.0};
  private static final double[] UPPER_BOUNDARY_F = {250.0, 375.0, 530.0, 650.0, 1050.0};
  private static final double[] VOLUME_PERCENT = {9.8, 15.4, 15.5, 10.8, 27.8};
  private static final double[] WEIGHT_PERCENT = {8.6, 15.2, 15.2, 11.1, 30.3};
  private static final double[] SPECIFIC_GRAVITY = {0.7815, 0.8305, 0.8623, 0.9226, 0.9477};
  private static final double[] API_GRAVITY = {49.6, 38.9, 32.6, 21.9, 17.8};

  @Test
  public void doeSpecificGravityAndApiPairsAreConsistent() {
    for (int i = 0; i < SPECIFIC_GRAVITY.length; i++) {
      double calculatedApi = 141.5 / SPECIFIC_GRAVITY[i] - 131.5;
      assertEquals(API_GRAVITY[i], calculatedApi, 0.05,
          "Published specific-gravity/API pair should agree within reported rounding");
    }

    double wholeCrudeApi = 141.5 / 0.8451 - 131.5;
    assertEquals(35.9, wholeCrudeApi, 0.05,
        "Published whole-crude specific gravity and API gravity should be self-consistent");
  }

  @Test
  public void doeApiGravityInputMatchesSpecificGravityMassShapeWithinReportedRounding() {
    SystemInterface specificGravitySystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation specificGravityCharacterisation = specificGravitySystem.getOilAssayCharacterisation();
    configureVolumeBasisCuts(specificGravityCharacterisation, false);

    SystemInterface apiGravitySystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation apiGravityCharacterisation = apiGravitySystem.getOilAssayCharacterisation();
    configureVolumeBasisCuts(apiGravityCharacterisation, true);

    double[] specificGravityMassFractions = specificGravityCharacterisation.getResolvedMassFractions();
    double[] apiGravityMassFractions = apiGravityCharacterisation.getResolvedMassFractions();

    for (int i = 0; i < specificGravityMassFractions.length; i++) {
      assertEquals(specificGravityMassFractions[i], apiGravityMassFractions[i], 5.0e-5,
          "Published one-decimal API gravity should reproduce the mass shape from four-decimal specific gravity");
    }
  }

  @Test
  public void doeDistillateSliceQualifiesVolumeToMassConversionAndPseudoComponentClosure() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.setTotalAssayMass(1.0);
    configureVolumeBasisCuts(characterisation, false);

    double weightSum = sum(WEIGHT_PERCENT);
    double[] resolvedMassFractions = characterisation.getResolvedMassFractions();
    double maxAbsoluteMassFractionDeviation = 0.0;
    for (int i = 0; i < resolvedMassFractions.length; i++) {
      double publishedSliceMassFraction = WEIGHT_PERCENT[i] / weightSum;
      maxAbsoluteMassFractionDeviation = Math.max(maxAbsoluteMassFractionDeviation,
          Math.abs(resolvedMassFractions[i] - publishedSliceMassFraction));
    }

    assertTrue(maxAbsoluteMassFractionDeviation < 0.007,
        "Density-based conversion should reproduce the normalized DOE mass-yield shape within 0.7 percentage points");

    characterisation.apply();

    double reconstructedMass = 0.0;
    for (int i = 0; i < VOLUME_PERCENT.length; i++) {
      ComponentInterface component = system.getComponent("DOE_BH_" + (i + 2) + "_PC");
      assertNotNull(component);
      assertTrue(Double.isFinite(component.getMolarMass()));
      assertTrue(component.getMolarMass() > 0.0);
      assertTrue(Double.isFinite(component.getNumberOfmoles()));
      assertTrue(component.getNumberOfmoles() > 0.0);
      reconstructedMass += component.getNumberOfmoles() * component.getMolarMass();
    }

    assertEquals(1.0, reconstructedMass, 1.0e-10);
  }

  private static void configureVolumeBasisCuts(OilAssayCharacterisation characterisation, boolean useApiGravity) {
    characterisation.clearCuts();
    double volumeSum = sum(VOLUME_PERCENT);

    for (int i = 0; i < VOLUME_PERCENT.length; i++) {
      AssayCut cut = new AssayCut("DOE_BH_" + (i + 2)).withVolumeFraction(VOLUME_PERCENT[i] / volumeSum)
          .withBoilingRangeCelsius(fahrenheitToCelsius(LOWER_BOUNDARY_F[i]), fahrenheitToCelsius(UPPER_BOUNDARY_F[i]));
      if (useApiGravity) {
        cut.withApiGravity(API_GRAVITY[i]);
      } else {
        cut.withSpecificGravity(SPECIFIC_GRAVITY[i]);
      }
      characterisation.addCut(cut);
    }
  }

  private static double sum(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum;
  }

  private static double fahrenheitToCelsius(double fahrenheit) {
    return (fahrenheit - 32.0) * 5.0 / 9.0;
  }
}
