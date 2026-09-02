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
    addBoundedCut(assay, "DOE_BH_650_850", 18.44, 0.9039, 650.0, 850.0, 0.534, 0.102);
    addBoundedCut(assay, "DOE_BH_850_1050", 12.84, 0.9336, 850.0, 1050.0, 0.752, 0.234);

    assay.addCut(new AssayCut("DOE_BH_1050_PLUS").withWeightPercent(11.56).withSpecificGravity(1.0089)
        .withLowerBoilingPointFahrenheit(1050.0).withWatsonCharacterizationFactor(11.7).withSulfurMassPercent(1.334)
        .withNitrogenMassPercent(0.501));
    return assay;
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
