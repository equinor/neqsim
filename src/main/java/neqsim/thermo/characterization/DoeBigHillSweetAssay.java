package neqsim.thermo.characterization;

import java.util.Objects;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;

/**
 * Reproducible modeled assay slate for the DOE SPR Big Hill Sweet reference crude.
 *
 * <p>
 * The mass yields, liquid specific gravities, boiling boundaries, sulfur, nitrogen, and terminal-residue Watson factor
 * are frozen from the U.S. Department of Energy Strategic Petroleum Reserve Big Hill Sweet comprehensive assay reported
 * 24 September 2021. The C2-C4 composition and the C5-175 degF number-average molar mass use the companion DOE PIANO
 * workbook.
 * </p>
 *
 * <p>
 * DOE reports a 1.70 mass% gas cut but the PIANO debutanization table reports a C2-C4 subset rather than a complete gas
 * composition. This reference slate therefore normalizes the reported ethane, propane, i-butane, and n-butane weights
 * over that subset and allocates the complete gas-cut mass to those four components. That allocation and the zero
 * sulfur/nitrogen values used where DOE leaves a cut blank are explicit modeling assumptions, not additional
 * measurements.
 * </p>
 *
 * <p>
 * {@link #create(SystemInterface, double)} configures the assay attached to the supplied system but does not add
 * thermodynamic components. Call {@link OilAssayCharacterisation#apply()} explicitly after inspecting or modifying the
 * returned characterization.
 * </p>
 */
public final class DoeBigHillSweetAssay {
  /** Official DOE SPR comprehensive-assay workbook. */
  public static final String COMPREHENSIVE_ASSAY_URL = "https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx";

  /** Official DOE SPR PIANO workbook. */
  public static final String PIANO_ASSAY_URL = "https://www.spr.doe.gov/reports/Assays/2021/BigHillSwPIANO.xlsx";

  private static final double GAS_MASS_PERCENT = 1.70;
  private static final String[] GAS_COMPONENT_NAMES = { "ethane", "propane", "i-butane", "n-butane" };
  private static final double[] GAS_COMPONENT_WEIGHT_PERCENT = { 0.09, 10.38, 10.21, 45.95 };
  private static final double GAS_SUBSET_WEIGHT_PERCENT = 66.63;
  private static final double C5_175_MOLAR_MASS_KG_PER_MOL = 0.07915383665629189;
  private static final double[] VACUUM_SCREENING_SOURCE_WEIGHT_PERCENT = { 18.44, 12.84, 11.56 };
  private static final double VACUUM_SCREENING_WHOLE_CRUDE_MASS_PERCENT = 42.84;

  private DoeBigHillSweetAssay() {
  }

  /**
   * Configure a complete modeled Big Hill Sweet assay on a one-kilogram basis.
   *
   * @param system empty or caller-owned thermodynamic system
   * @return configured assay; no component has been added to {@code system}
   */
  public static OilAssayCharacterisation create(SystemInterface system) {
    return create(system, 1.0);
  }

  /**
   * Configure a complete modeled Big Hill Sweet assay.
   *
   * @param system empty or caller-owned thermodynamic system
   * @param totalAssayMassKg positive total assay mass in kg
   * @return configured assay; no component has been added to {@code system}
   */
  public static OilAssayCharacterisation create(SystemInterface system, double totalAssayMassKg) {
    Objects.requireNonNull(system, "system");
    if (!Double.isFinite(totalAssayMassKg) || !(totalAssayMassKg > 0.0)) {
      throw new IllegalArgumentException("Total assay mass must be finite and positive");
    }

    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.clearCuts();
    assay.setTotalAssayMass(totalAssayMassKg);

    addModeledGasCut(assay);
    assay.addCut(new AssayCut("DOE_BH_C5_175").withWeightPercent(5.22).withSpecificGravity(0.6731)
        .withMolarMassKgPerMol(C5_175_MOLAR_MASS_KG_PER_MOL).withUpperBoilingPointFahrenheit(175.0)
        .withSulfurMassPercent(0.0008).withNitrogenMassPercent(0.0));

    addBoundedCut(assay, "DOE_BH_175_250", 8.32, 0.7432, 175.0, 250.0, 0.0026, 0.0);
    addBoundedCut(assay, "DOE_BH_250_375", 12.55, 0.7817, 250.0, 375.0, 0.019, 0.0);
    addBoundedCut(assay, "DOE_BH_375_530", 16.19, 0.8297, 375.0, 530.0, 0.096, 0.0018);
    addBoundedCut(assay, "DOE_BH_530_650", 13.18, 0.8604, 530.0, 650.0, 0.313, 0.0186);
    addVacuumScreeningCuts(assay, 1.0);
    return assay;
  }

  /**
   * Return the source mass represented by the 650 degF+ vacuum-screening slice.
   *
   * @return source slice mass in percent of whole crude
   */
  public static double getVacuumScreeningWholeCrudeMassPercent() {
    return VACUUM_SCREENING_WHOLE_CRUDE_MASS_PERCENT;
  }

  /**
   * Return the three source mass percentages retained by the vacuum-screening slice.
   *
   * @return defensive copy ordered as 650-850 degF, 850-1050 degF, and 1050 degF+
   */
  public static double[] getVacuumScreeningSourceWeightPercent() {
    return VACUUM_SCREENING_SOURCE_WEIGHT_PERCENT.clone();
  }

  /**
   * Configure the normalized DOE Big Hill Sweet 650 degF+ vacuum-screening feed on a one-kilogram basis.
   *
   * <p>
   * The three retained source rows account for 42.84 mass% of whole crude. Their source mass percentages are normalized
   * to the returned feed basis. This is a reproducible heavy-assay slice, not a measured atmospheric-column bottoms
   * composition.
   * </p>
   *
   * @param system empty or caller-owned thermodynamic system
   * @return configured three-cut assay; no component has been added to {@code system}
   * @throws NullPointerException if {@code system} is {@code null}
   */
  public static OilAssayCharacterisation createVacuumScreeningFeed(SystemInterface system) {
    return createVacuumScreeningFeed(system, 1.0);
  }

  /**
   * Configure the normalized DOE Big Hill Sweet 650 degF+ vacuum-screening feed.
   *
   * @param system empty or caller-owned thermodynamic system
   * @param totalAssayMassKg positive screening-feed mass in kg
   * @return configured three-cut assay; no component has been added to {@code system}
   * @throws NullPointerException if {@code system} is {@code null}
   * @throws IllegalArgumentException if {@code totalAssayMassKg} is non-finite or not positive
   */
  public static OilAssayCharacterisation createVacuumScreeningFeed(SystemInterface system, double totalAssayMassKg) {
    Objects.requireNonNull(system, "system");
    if (!Double.isFinite(totalAssayMassKg) || !(totalAssayMassKg > 0.0)) {
      throw new IllegalArgumentException("Total assay mass must be finite and positive");
    }

    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.clearCuts();
    assay.setTotalAssayMass(totalAssayMassKg);
    addVacuumScreeningCuts(assay, 100.0 / VACUUM_SCREENING_WHOLE_CRUDE_MASS_PERCENT);
    return assay;
  }

  private static void addVacuumScreeningCuts(OilAssayCharacterisation assay, double weightScale) {
    addBoundedCut(assay, "DOE_BH_650_850", VACUUM_SCREENING_SOURCE_WEIGHT_PERCENT[0] * weightScale, 0.9039, 650.0,
        850.0, 0.534, 0.102);
    addBoundedCut(assay, "DOE_BH_850_1050", VACUUM_SCREENING_SOURCE_WEIGHT_PERCENT[1] * weightScale, 0.9336, 850.0,
        1050.0, 0.752, 0.234);
    assay.addCut(
        new AssayCut("DOE_BH_1050_PLUS").withWeightPercent(VACUUM_SCREENING_SOURCE_WEIGHT_PERCENT[2] * weightScale)
            .withSpecificGravity(1.0089).withLowerBoilingPointFahrenheit(1050.0).withWatsonCharacterizationFactor(11.7)
            .withSulfurMassPercent(1.334).withNitrogenMassPercent(0.501));
  }

  private static void addModeledGasCut(OilAssayCharacterisation assay) {
    for (int i = 0; i < GAS_COMPONENT_NAMES.length; i++) {
      double wholeCrudeWeightPercent = GAS_MASS_PERCENT * GAS_COMPONENT_WEIGHT_PERCENT[i] / GAS_SUBSET_WEIGHT_PERCENT;
      assay.addCut(new AssayCut("DOE_BH_GAS_" + (i + 1)).withWeightPercent(wholeCrudeWeightPercent)
          .withStandardComponent(GAS_COMPONENT_NAMES[i]).withSulfurMassPercent(0.0).withNitrogenMassPercent(0.0));
    }
  }

  private static void addBoundedCut(OilAssayCharacterisation assay, String name, double weightPercent,
      double specificGravity, double lowerFahrenheit, double upperFahrenheit, double sulfurMassPercent,
      double nitrogenMassPercent) {
    assay.addCut(new AssayCut(name).withWeightPercent(weightPercent).withSpecificGravity(specificGravity)
        .withBoilingRangeCelsius(fahrenheitToCelsius(lowerFahrenheit), fahrenheitToCelsius(upperFahrenheit))
        .withSulfurMassPercent(sulfurMassPercent).withNitrogenMassPercent(nitrogenMassPercent));
  }

  private static double fahrenheitToCelsius(double temperatureFahrenheit) {
    return (temperatureFahrenheit - 32.0) * 5.0 / 9.0;
  }
}
