package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * Corrosion Under Insulation (CUI) risk assessment per API 581 / NORSOK M-501.
 *
 * <p>
 * Provides temperature-based susceptibility screening, material and insulation type compatibility
 * checks, and inspection interval recommendations following industry best practice.
 * </p>
 *
 * <p>
 * CUI is one of the leading causes of unplanned shutdowns in process plants. The risk depends
 * primarily on operating temperature, insulation type, coating system, and environment.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>API 581: Risk-Based Inspection Methodology (CUI module)</li>
 * <li>API 583: Corrosion Under Insulation and Fireproofing</li>
 * <li>NORSOK M-501: Surface preparation and protective coating</li>
 * <li>EFC Publication 55: CUI Guidelines</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class CUIRiskAssessment implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** CUI risk level enumeration. */
  public enum CUIRisk {
    /** Low risk of CUI. */
    LOW,
    /** Medium risk of CUI, inspection recommended. */
    MEDIUM,
    /** High risk of CUI, priority inspection required. */
    HIGH,
    /** Very high risk of CUI, immediate action required. */
    VERY_HIGH
  }

  /** Insulation types and their CUI susceptibility. */
  public enum InsulationType {
    /** Mineral wool / rock wool - high moisture absorption. */
    MINERAL_WOOL(true, 1.3),
    /** Calcium silicate - moderate moisture absorption. */
    CALCIUM_SILICATE(true, 1.2),
    /** Cellular glass (Foamglas) - closed cell, low moisture. */
    CELLULAR_GLASS(false, 0.6),
    /** PIR foam - closed cell. */
    PIR_FOAM(false, 0.8),
    /** Expanded perlite - moderate. */
    PERLITE(true, 1.1),
    /** Aerogel blanket - hydrophobic. */
    AEROGEL(false, 0.5);

    private final boolean absorbsMoisture;
    private final double cuiMultiplier;

    InsulationType(boolean absorbsMoisture, double cuiMultiplier) {
      this.absorbsMoisture = absorbsMoisture;
      this.cuiMultiplier = cuiMultiplier;
    }

    /**
     * Returns whether this insulation type absorbs moisture.
     *
     * @return true if absorbs moisture
     */
    public boolean absorbsMoisture() {
      return absorbsMoisture;
    }

    /**
     * Returns a CUI risk multiplier relative to baseline.
     *
     * @return risk multiplier (greater than 1.0 is higher risk)
     */
    public double getCuiMultiplier() {
      return cuiMultiplier;
    }
  }

  /**
   * Assess CUI risk based on operating temperature per API 581 screening criteria.
   *
   * <p>
   * Carbon steel is most susceptible to CUI in the range -4 C to 175 C (25 F to 350 F). The highest
   * risk zone is 80-120 C where aqueous corrosion and wet-dry cycling are most aggressive.
   * </p>
   *
   * @param operatingTempC operating temperature in Celsius
   * @param isStainlessSteel true if material is austenitic stainless steel (300-series)
   * @param insulationType insulation type
   * @param coatingAge years since last coating application
   * @param isMarineEnvironment true if offshore or coastal
   * @return CUI risk level
   */
  public static CUIRisk assessRisk(double operatingTempC, boolean isStainlessSteel,
      InsulationType insulationType, double coatingAge, boolean isMarineEnvironment) {

    double baseScore = temperatureRiskScore(operatingTempC, isStainlessSteel);

    // Insulation type factor
    baseScore *= insulationType.getCuiMultiplier();

    // Coating degradation factor
    if (coatingAge > 15) {
      baseScore *= 1.5;
    } else if (coatingAge > 10) {
      baseScore *= 1.3;
    } else if (coatingAge > 5) {
      baseScore *= 1.1;
    }

    // Marine environment factor
    if (isMarineEnvironment) {
      baseScore *= 1.4;
    }

    if (baseScore >= 4.0) {
      return CUIRisk.VERY_HIGH;
    } else if (baseScore >= 2.5) {
      return CUIRisk.HIGH;
    } else if (baseScore >= 1.5) {
      return CUIRisk.MEDIUM;
    }
    return CUIRisk.LOW;
  }

  /**
   * Calculate temperature-based risk score per API 581 / API 583 guidance.
   *
   * @param operatingTempC operating temperature in Celsius
   * @param isStainlessSteel true if austenitic stainless steel
   * @return risk score (0 = negligible, 1 = low, 3 = high, 5 = very high)
   */
  public static double temperatureRiskScore(double operatingTempC, boolean isStainlessSteel) {
    if (isStainlessSteel) {
      // Stainless steel: susceptible to chloride SCC under insulation, 50-150 C
      if (operatingTempC >= 60 && operatingTempC <= 150) {
        return 4.0;
      } else if (operatingTempC >= 50 && operatingTempC <= 175) {
        return 2.5;
      } else if (operatingTempC >= -4 && operatingTempC <= 200) {
        return 1.0;
      }
      return 0.3;
    } else {
      // Carbon steel: aqueous corrosion under insulation
      if (operatingTempC >= 80 && operatingTempC <= 120) {
        return 4.0; // Peak CUI zone
      } else if (operatingTempC >= 50 && operatingTempC <= 150) {
        return 3.0;
      } else if (operatingTempC >= -4 && operatingTempC <= 175) {
        return 2.0;
      } else if (operatingTempC > 175 && operatingTempC <= 200) {
        return 1.0;
      }
      return 0.3; // Below -4 C or above 200 C: low CUI risk
    }
  }

  /**
   * Recommend inspection interval based on CUI risk level.
   *
   * @param risk CUI risk level
   * @return recommended inspection interval in years
   */
  public static int recommendedInspectionIntervalYears(CUIRisk risk) {
    switch (risk) {
      case VERY_HIGH:
        return 1;
      case HIGH:
        return 3;
      case MEDIUM:
        return 5;
      case LOW:
        return 10;
      default:
        return 5;
    }
  }

  /**
   * Return recommended inspection methods for CUI detection.
   *
   * @param risk CUI risk level
   * @return list of recommended inspection technique names
   */
  public static List<String> recommendedInspectionMethods(CUIRisk risk) {
    switch (risk) {
      case VERY_HIGH:
        return Arrays.asList("Strip insulation and visually inspect",
            "UT thickness measurement at CMLs", "Radiographic profile (RT)",
            "Pulsed eddy current (PEC)");
      case HIGH:
        return Arrays.asList("Pulsed eddy current (PEC) screening",
            "UT spot thickness measurements", "Infrared thermography for moisture detection",
            "Neutron backscatter for moisture mapping");
      case MEDIUM:
        return Arrays.asList("Infrared thermography survey",
            "Visual inspection of jacketing and sealants",
            "UT spot checks at known vulnerable locations");
      default:
        return Arrays.asList("Visual inspection of jacketing condition",
            "Infrared thermography (opportunistic)");
    }
  }

  /**
   * Check whether selected insulation type is appropriate for the operating temperature.
   *
   * @param insulationType insulation type
   * @param operatingTempC operating temperature in Celsius
   * @return true if insulation type is suitable for the temperature
   */
  public static boolean isInsulationSuitable(InsulationType insulationType, double operatingTempC) {
    switch (insulationType) {
      case PIR_FOAM:
        return operatingTempC <= 140; // PIR limited to ~140C
      case CELLULAR_GLASS:
        return operatingTempC >= -268 && operatingTempC <= 430;
      case MINERAL_WOOL:
        return operatingTempC >= -50 && operatingTempC <= 700;
      case CALCIUM_SILICATE:
        return operatingTempC >= 0 && operatingTempC <= 650;
      case PERLITE:
        return operatingTempC >= -268 && operatingTempC <= 650;
      case AEROGEL:
        return operatingTempC >= -200 && operatingTempC <= 650;
      default:
        return true;
    }
  }

  /**
   * Estimate remaining corrosion allowance based on CUI conditions.
   *
   * @param originalThicknessMm original wall thickness in mm
   * @param currentThicknessMm measured current wall thickness in mm
   * @param yearsInService years in service
   * @return estimated remaining life in years at current corrosion rate, or Double.MAX_VALUE if no
   *         measurable thinning
   */
  public static double estimateRemainingLife(double originalThicknessMm, double currentThicknessMm,
      double yearsInService) {
    double lostMm = originalThicknessMm - currentThicknessMm;
    if (lostMm <= 0 || yearsInService <= 0) {
      return Double.MAX_VALUE;
    }
    double ratePerYear = lostMm / yearsInService;
    double minimumThickness = originalThicknessMm * 0.5; // simplified retirement threshold
    double remainingAllowance = currentThicknessMm - minimumThickness;
    if (remainingAllowance <= 0) {
      return 0;
    }
    return remainingAllowance / ratePerYear;
  }
}
