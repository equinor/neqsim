package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Material selection helper per NORSOK M-001 "Materials selection".
 *
 * <p>
 * Provides material grade recommendations for pipelines and process piping based on the corrosion
 * environment (CO2 corrosion rate, H2S partial pressure, chloride content, temperature). Integrates
 * with {@link NorsokM506CorrosionRate} for CO2 corrosion rate input.
 * </p>
 *
 * <p>
 * NORSOK M-001 classifies service environments and maps them to suitable material grades:
 * </p>
 * <ul>
 * <li>Sweet service (CO2 only): carbon steel with corrosion allowance, or 13Cr/duplex for high
 * rates</li>
 * <li>Sour service (H2S present per NACE MR0175/ISO 15156): material restrictions based on
 * hardness and SSC resistance</li>
 * <li>Chloride-containing: pitting and SCC risk for austenitic stainless steels</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
 * selector.setCO2CorrosionRateMmyr(2.5);
 * selector.setH2SPartialPressureBar(0.05);
 * selector.setChlorideConcentrationMgL(50000);
 * selector.setDesignTemperatureC(80.0);
 * selector.setDesignLifeYears(25);
 * selector.evaluate();
 *
 * String material = selector.getRecommendedMaterial();
 * String category = selector.getServiceCategory();
 * double ca = selector.getRecommendedCorrosionAllowanceMm();
 * String json = selector.toJson();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see NorsokM506CorrosionRate
 */
public class NorsokM001MaterialSelection implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  // --- Input parameters ---

  /** CO2 corrosion rate in mm/yr (from NORSOK M-506 or equivalent model). */
  private double co2CorrosionRateMmyr = 0.0;

  /** H2S partial pressure in bar. */
  private double h2sPartialPressureBar = 0.0;

  /** Design temperature in degrees Celsius. */
  private double designTemperatureC = 60.0;

  /** Maximum design temperature in degrees Celsius. */
  private double maxDesignTemperatureC = 100.0;

  /** Chloride concentration in mg/L (ppm). */
  private double chlorideConcentrationMgL = 0.0;

  /** Design life in years. */
  private double designLifeYears = 25.0;

  /** pH of aqueous phase. */
  private double aqueousPH = 4.5;

  /** CO2 partial pressure in bar (for CRA selection criteria). */
  private double co2PartialPressureBar = 0.0;

  /** Whether the system has free water present. */
  private boolean freeWaterPresent = true;

  // --- Evaluated results ---

  /** Service category string. */
  private String serviceCategory = "";

  /** Recommended material grade. */
  private String recommendedMaterial = "";

  /** Alternative material options. */
  private List<String> alternativeMaterials = new ArrayList<String>();

  /** Recommended corrosion allowance in mm. */
  private double recommendedCorrosionAllowanceMm = 0.0;

  /** Maximum temperature for recommended material in degrees C. */
  private double materialMaxTemperatureC = 0.0;

  /** Sour service classification per NACE MR0175. */
  private String sourClassification = "";

  /** Chloride SCC risk level. */
  private String chlorideSCCRisk = "";

  /** Key notes and warnings. */
  private List<String> notes = new ArrayList<String>();

  /** Whether evaluation has been performed. */
  private boolean hasBeenEvaluated = false;

  /**
   * Creates a new NorsokM001MaterialSelection with default parameters.
   */
  public NorsokM001MaterialSelection() {}

  // --- Setters ---

  /**
   * Sets the CO2 corrosion rate (from NORSOK M-506 model or measured).
   *
   * @param rateMmyr CO2 corrosion rate in mm/yr
   */
  public void setCO2CorrosionRateMmyr(double rateMmyr) {
    this.co2CorrosionRateMmyr = Math.max(0.0, rateMmyr);
    this.hasBeenEvaluated = false;
  }

  /**
   * Sets the H2S partial pressure.
   *
   * @param pressureBar H2S partial pressure in bar
   */
  public void setH2SPartialPressureBar(double pressureBar) {
    this.h2sPartialPressureBar = Math.max(0.0, pressureBar);
    this.hasBeenEvaluated = false;
  }

  /**
   * Sets the design temperature.
   *
   * @param temperatureC design temperature in Celsius
   */
  public void setDesignTemperatureC(double temperatureC) {
    this.designTemperatureC = temperatureC;
    this.hasBeenEvaluated = false;
  }

  /**
   * Sets the maximum design temperature.
   *
   * @param temperatureC maximum design temperature in Celsius
   */
  public void setMaxDesignTemperatureC(double temperatureC) {
    this.maxDesignTemperatureC = temperatureC;
    this.hasBeenEvaluated = false;
  }

  /**
   * Sets the chloride concentration in formation/produced water.
   *
   * @param concentrationMgL chloride concentration in mg/L (ppm)
   */
  public void setChlorideConcentrationMgL(double concentrationMgL) {
    this.chlorideConcentrationMgL = Math.max(0.0, concentrationMgL);
    this.hasBeenEvaluated = false;
  }

  /**
   * Sets the design life for corrosion allowance calculation.
   *
   * @param years design life in years (typically 20-30)
   */
  public void setDesignLifeYears(double years) {
    this.designLifeYears = Math.max(1.0, years);
    this.hasBeenEvaluated = false;
  }

  /**
   * Sets the aqueous phase pH.
   *
   * @param pH aqueous phase pH (3.0 to 7.0)
   */
  public void setAqueousPH(double pH) {
    this.aqueousPH = pH;
    this.hasBeenEvaluated = false;
  }

  /**
   * Sets the CO2 partial pressure (for CRA selection criteria).
   *
   * @param pressureBar CO2 partial pressure in bar
   */
  public void setCO2PartialPressureBar(double pressureBar) {
    this.co2PartialPressureBar = Math.max(0.0, pressureBar);
    this.hasBeenEvaluated = false;
  }

  /**
   * Sets whether free water is present in the system.
   *
   * @param present true if free water is present
   */
  public void setFreeWaterPresent(boolean present) {
    this.freeWaterPresent = present;
    this.hasBeenEvaluated = false;
  }

  // --- Core evaluation ---

  /**
   * Evaluates the service environment and recommends material grade and corrosion allowance.
   *
   * <p>
   * This method classifies the service conditions (sweet/sour, chloride risk), evaluates the
   * corrosion severity, and selects the appropriate material grade per NORSOK M-001 guidelines.
   * </p>
   */
  public void evaluate() {
    notes = new ArrayList<String>();
    alternativeMaterials = new ArrayList<String>();

    // Step 1: Classify service
    sourClassification = classifySourService();
    serviceCategory = determineServiceCategory();
    chlorideSCCRisk = assessChlorideSCCRisk();

    // Step 2: Select material
    selectMaterial();

    // Step 3: Calculate corrosion allowance
    calculateCorrosionAllowance();

    hasBeenEvaluated = true;
  }

  /**
   * Classifies sour service severity per NACE MR0175/ISO 15156.
   *
   * <p>
   * Classification based on H2S partial pressure:
   * </p>
   * <ul>
   * <li>Non-sour: H2S pp less than 0.3 kPa (0.003 bar)</li>
   * <li>Mild sour (SSC Region 0): 0.003 to 0.01 bar</li>
   * <li>Moderate sour (SSC Region 1): 0.01 to 0.1 bar</li>
   * <li>Severe sour (SSC Region 2/3): greater than 0.1 bar</li>
   * </ul>
   *
   * @return sour service classification string
   */
  private String classifySourService() {
    if (h2sPartialPressureBar < 0.003) {
      return "Non-sour";
    } else if (h2sPartialPressureBar < 0.01) {
      return "Mild sour (SSC Region 0)";
    } else if (h2sPartialPressureBar < 0.1) {
      return "Moderate sour (SSC Region 1)";
    } else {
      return "Severe sour (SSC Region 2/3)";
    }
  }

  /**
   * Determines the overall service category.
   *
   * @return service category string
   */
  private String determineServiceCategory() {
    boolean isSour = h2sPartialPressureBar >= 0.003;
    boolean hasChlorides = chlorideConcentrationMgL > 50;
    boolean hasCO2 = co2PartialPressureBar > 0 || co2CorrosionRateMmyr > 0;

    if (!freeWaterPresent) {
      notes.add("No free water: corrosion risk is minimal regardless of gas composition");
      return "Dry service (no free water)";
    }

    if (isSour && hasChlorides) {
      return "Sour + chloride service";
    } else if (isSour) {
      return "Sour service";
    } else if (hasChlorides && hasCO2) {
      return "Sweet + chloride service";
    } else if (hasCO2) {
      return "Sweet service (CO2 only)";
    } else {
      return "Non-corrosive service";
    }
  }

  /**
   * Assesses chloride stress corrosion cracking (SCC) risk.
   *
   * <p>
   * Per NORSOK M-001, SCC risk for austenitic and duplex stainless steels depends on chloride
   * concentration and temperature:
   * </p>
   * <ul>
   * <li>Low risk: Cl less than 50 mg/L or temperature below 60 degrees C</li>
   * <li>Medium risk: 50-1000 mg/L Cl at 60-100 degrees C</li>
   * <li>High risk: greater than 1000 mg/L Cl at temperatures above 80 degrees C</li>
   * <li>Very high: greater than 50000 mg/L (saturated brine) at elevated temperature</li>
   * </ul>
   *
   * @return SCC risk classification
   */
  private String assessChlorideSCCRisk() {
    if (chlorideConcentrationMgL < 50 || maxDesignTemperatureC < 60) {
      return "Low";
    } else if (chlorideConcentrationMgL < 1000) {
      if (maxDesignTemperatureC < 100) {
        return "Medium";
      } else {
        return "High";
      }
    } else if (chlorideConcentrationMgL < 50000) {
      if (maxDesignTemperatureC < 80) {
        return "Medium";
      } else {
        return "High";
      }
    } else {
      return "Very High";
    }
  }

  /**
   * Selects the recommended material and alternatives based on service conditions.
   */
  private void selectMaterial() {
    boolean isSour = h2sPartialPressureBar >= 0.003;
    boolean isSevereSour = h2sPartialPressureBar >= 0.1;
    boolean highChloride = chlorideConcentrationMgL > 1000;
    boolean veryHighChloride = chlorideConcentrationMgL > 50000;

    if (!freeWaterPresent) {
      // No free water: carbon steel acceptable
      recommendedMaterial = "Carbon steel (CS)";
      materialMaxTemperatureC = 450.0;
      alternativeMaterials = Arrays.asList("Carbon steel (CS)");
      return;
    }

    // Sweet service (no H2S)
    if (!isSour) {
      selectSweetServiceMaterial(highChloride, veryHighChloride);
      return;
    }

    // Sour service
    selectSourServiceMaterial(isSevereSour, highChloride, veryHighChloride);
  }

  /**
   * Selects material for sweet (CO2 only) service per NORSOK M-001.
   *
   * @param highChloride true if Cl greater than 1000 mg/L
   * @param veryHighChloride true if Cl greater than 50000 mg/L
   */
  private void selectSweetServiceMaterial(boolean highChloride, boolean veryHighChloride) {
    if (co2CorrosionRateMmyr < 0.1) {
      // Low corrosion rate: carbon steel with minimal CA
      recommendedMaterial = "Carbon steel (CS)";
      materialMaxTemperatureC = 450.0;
      alternativeMaterials = Arrays.asList("Carbon steel (CS)");
      notes.add("Low CO2 corrosion rate (<0.1 mm/yr): CS acceptable with standard CA");

    } else if (co2CorrosionRateMmyr < 0.3) {
      // Medium corrosion: CS with increased CA or consider CRA
      recommendedMaterial = "Carbon steel with increased corrosion allowance";
      materialMaxTemperatureC = 450.0;
      alternativeMaterials = Arrays.asList(
          "Carbon steel (CS) with 3-6 mm CA", "13Cr martensitic SS (UNS S41000)");
      notes.add("Medium CO2 corrosion (0.1-0.3 mm/yr): evaluate CS with CA vs. 13Cr lifecycle");

    } else if (co2CorrosionRateMmyr < 1.0) {
      // High corrosion: 13Cr or carbon steel with heavy CA + inhibitor
      if (highChloride) {
        recommendedMaterial = "22Cr Duplex SS (UNS S31803)";
        materialMaxTemperatureC = 200.0;
        alternativeMaterials = Arrays.asList(
            "22Cr Duplex SS (UNS S31803)",
            "25Cr Super Duplex SS (UNS S32750)");
        notes.add("High corrosion + chlorides: duplex SS recommended for combined resistance");
      } else {
        recommendedMaterial = "13Cr martensitic SS (UNS S41000)";
        materialMaxTemperatureC = 150.0;
        alternativeMaterials = Arrays.asList(
            "13Cr martensitic SS (UNS S41000)",
            "Super 13Cr (UNS S41426)",
            "Carbon steel with CI and heavy CA (>6 mm)");
        notes.add("High CO2 corrosion (0.3-1.0 mm/yr): CRA recommended over CS with heavy CA");
      }

    } else {
      // Very high corrosion: CRA mandatory
      if (veryHighChloride) {
        recommendedMaterial = "25Cr Super Duplex SS (UNS S32750)";
        materialMaxTemperatureC = 200.0;
        alternativeMaterials = Arrays.asList(
            "25Cr Super Duplex SS (UNS S32750)",
            "Alloy 625 (UNS N06625)");
      } else if (highChloride) {
        recommendedMaterial = "22Cr Duplex SS (UNS S31803)";
        materialMaxTemperatureC = 200.0;
        alternativeMaterials = Arrays.asList(
            "22Cr Duplex SS (UNS S31803)",
            "25Cr Super Duplex SS (UNS S32750)");
      } else {
        recommendedMaterial = "22Cr Duplex SS (UNS S31803)";
        materialMaxTemperatureC = 200.0;
        alternativeMaterials = Arrays.asList(
            "Super 13Cr (UNS S41426)",
            "22Cr Duplex SS (UNS S31803)");
      }
      notes.add("Very high CO2 corrosion (>1.0 mm/yr): CRA mandatory, CS not practical");
    }

    // Temperature check
    if (maxDesignTemperatureC > materialMaxTemperatureC) {
      notes.add("WARNING: Design temperature (" + maxDesignTemperatureC
          + " C) exceeds material limit (" + materialMaxTemperatureC + " C)");
    }
  }

  /**
   * Selects material for sour service per NORSOK M-001 and NACE MR0175/ISO 15156.
   *
   * @param isSevereSour true if H2S pp greater than 0.1 bar
   * @param highChloride true if Cl greater than 1000 mg/L
   * @param veryHighChloride true if Cl greater than 50000 mg/L
   */
  private void selectSourServiceMaterial(boolean isSevereSour, boolean highChloride,
      boolean veryHighChloride) {
    notes.add("Sour service: materials must comply with NACE MR0175/ISO 15156");

    if (isSevereSour) {
      // Severe sour: very restricted material options
      if (veryHighChloride) {
        recommendedMaterial = "Alloy C-276 (UNS N10276)";
        materialMaxTemperatureC = 300.0;
        alternativeMaterials = Arrays.asList(
            "Alloy C-276 (UNS N10276)",
            "Alloy 625 (UNS N06625)");
        notes.add("Severe sour + high chloride: nickel alloy required");
      } else if (highChloride) {
        recommendedMaterial = "25Cr Super Duplex SS (UNS S32750)";
        materialMaxTemperatureC = 200.0;
        alternativeMaterials = Arrays.asList(
            "25Cr Super Duplex SS (UNS S32750)",
            "Alloy 625 (UNS N06625)");
      } else {
        recommendedMaterial = "22Cr Duplex SS (UNS S31803)";
        materialMaxTemperatureC = 200.0;
        alternativeMaterials = Arrays.asList(
            "22Cr Duplex SS (UNS S31803)",
            "25Cr Super Duplex SS (UNS S32750)");
      }
      notes.add("Severe sour (H2S pp > 0.1 bar): CS HIC/SSC risk, CRA required");
      notes.add("All materials must be qualified per ISO 15156-3 for sour service");

    } else {
      // Mild/moderate sour
      if (co2CorrosionRateMmyr < 0.3 && !highChloride) {
        // Low CO2 rate: sour-rated CS may be acceptable
        recommendedMaterial = "Carbon steel (sour service grade, HIC/SSC tested)";
        materialMaxTemperatureC = 450.0;
        alternativeMaterials = Arrays.asList(
            "Carbon steel (HIC-tested per ISO 15156-2)",
            "13Cr (sour service qualified, max H2S per ISO 15156-3)");
        notes.add("Mild sour: CS acceptable if HIC/SSC tested per ISO 15156-2");
        notes.add("CS hardness must not exceed 22 HRC (248 HB) per NACE MR0175");
      } else {
        // Moderate sour + high rate
        if (highChloride) {
          recommendedMaterial = "22Cr Duplex SS (UNS S31803)";
          materialMaxTemperatureC = 200.0;
          alternativeMaterials = Arrays.asList(
              "22Cr Duplex SS (UNS S31803)",
              "25Cr Super Duplex SS (UNS S32750)");
        } else {
          recommendedMaterial = "Super 13Cr (UNS S41426, sour service qualified)";
          materialMaxTemperatureC = 150.0;
          alternativeMaterials = Arrays.asList(
              "Super 13Cr (UNS S41426)",
              "22Cr Duplex SS (UNS S31803)");
        }
        notes.add("Moderate sour + elevated CO2 corrosion: CRA recommended");
      }
    }

    // Temperature limit check
    if (maxDesignTemperatureC > materialMaxTemperatureC) {
      notes.add("WARNING: Design temperature (" + maxDesignTemperatureC
          + " C) exceeds material limit (" + materialMaxTemperatureC + " C)");
    }
  }

  /**
   * Calculates the recommended corrosion allowance per NORSOK M-001.
   *
   * <p>
   * Guidelines per NORSOK M-001:
   * </p>
   * <ul>
   * <li>Minimum CA for carbon steel: 1.0 mm</li>
   * <li>Standard CA: corrosion rate * design life</li>
   * <li>Maximum practical CA: 6.0 mm (above this, CRA is usually more economic)</li>
   * <li>CRA materials: 0 mm CA (inherently resistant)</li>
   * <li>Sour service CS: additional 1.0 mm for pitting/localized attack</li>
   * </ul>
   */
  private void calculateCorrosionAllowance() {
    if (recommendedMaterial.contains("Carbon steel")) {
      double baseCa = co2CorrosionRateMmyr * designLifeYears;

      // Minimum per NORSOK M-001
      baseCa = Math.max(1.0, baseCa);

      // Additional allowance for sour service (pitting factor)
      boolean isSour = h2sPartialPressureBar >= 0.003;
      if (isSour) {
        baseCa += 1.0;
        notes.add("Added 1.0 mm CA for sour service localized corrosion risk");
      }

      if (baseCa > 6.0) {
        notes.add("CA exceeds 6.0 mm: recommend re-evaluating with CRA material for lifecycle "
            + "cost benefit. Calculated CA = " + String.format("%.1f", baseCa) + " mm");
        recommendedCorrosionAllowanceMm = 6.0;
      } else {
        recommendedCorrosionAllowanceMm = baseCa;
      }
    } else {
      // CRA materials: no corrosion allowance needed
      recommendedCorrosionAllowanceMm = 0.0;
      notes.add("CRA material: no corrosion allowance required");
    }
  }

  // --- Getters ---

  /**
   * Returns the service category classification.
   *
   * @return service category string
   */
  public String getServiceCategory() {
    ensureEvaluated();
    return serviceCategory;
  }

  /**
   * Returns the recommended material grade.
   *
   * @return recommended material string
   */
  public String getRecommendedMaterial() {
    ensureEvaluated();
    return recommendedMaterial;
  }

  /**
   * Returns the list of alternative material options.
   *
   * @return list of alternative material strings
   */
  public List<String> getAlternativeMaterials() {
    ensureEvaluated();
    return alternativeMaterials;
  }

  /**
   * Returns the recommended corrosion allowance.
   *
   * @return corrosion allowance in mm
   */
  public double getRecommendedCorrosionAllowanceMm() {
    ensureEvaluated();
    return recommendedCorrosionAllowanceMm;
  }

  /**
   * Returns the maximum temperature limit for the recommended material.
   *
   * @return maximum temperature in degrees Celsius
   */
  public double getMaterialMaxTemperatureC() {
    ensureEvaluated();
    return materialMaxTemperatureC;
  }

  /**
   * Returns the sour service classification.
   *
   * @return sour classification string per NACE MR0175
   */
  public String getSourClassification() {
    ensureEvaluated();
    return sourClassification;
  }

  /**
   * Returns the chloride SCC risk level.
   *
   * @return SCC risk classification string
   */
  public String getChlorideSCCRisk() {
    ensureEvaluated();
    return chlorideSCCRisk;
  }

  /**
   * Returns the notes and warnings from the evaluation.
   *
   * @return list of notes and warning strings
   */
  public List<String> getNotes() {
    ensureEvaluated();
    return notes;
  }

  // --- JSON output ---

  /**
   * Returns all evaluation results as a map.
   *
   * @return map of all parameters and recommendations
   */
  public Map<String, Object> toMap() {
    ensureEvaluated();

    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Input conditions
    Map<String, Object> inputs = new LinkedHashMap<String, Object>();
    inputs.put("co2CorrosionRate_mmyr", co2CorrosionRateMmyr);
    inputs.put("h2sPartialPressure_bar", h2sPartialPressureBar);
    inputs.put("co2PartialPressure_bar", co2PartialPressureBar);
    inputs.put("designTemperature_C", designTemperatureC);
    inputs.put("maxDesignTemperature_C", maxDesignTemperatureC);
    inputs.put("chlorideConcentration_mgL", chlorideConcentrationMgL);
    inputs.put("designLife_years", designLifeYears);
    inputs.put("aqueousPH", aqueousPH);
    inputs.put("freeWaterPresent", freeWaterPresent);
    result.put("inputConditions", inputs);

    // Classification
    Map<String, Object> classification = new LinkedHashMap<String, Object>();
    classification.put("serviceCategory", serviceCategory);
    classification.put("sourClassification", sourClassification);
    classification.put("chlorideSCCRisk", chlorideSCCRisk);
    result.put("serviceClassification", classification);

    // Recommendation
    Map<String, Object> recommendation = new LinkedHashMap<String, Object>();
    recommendation.put("recommendedMaterial", recommendedMaterial);
    recommendation.put("alternativeMaterials", alternativeMaterials);
    recommendation.put("corrosionAllowance_mm", recommendedCorrosionAllowanceMm);
    recommendation.put("materialMaxTemperature_C", materialMaxTemperatureC);
    result.put("materialRecommendation", recommendation);

    // Notes
    result.put("notes", notes);

    // Standards referenced
    Map<String, Object> standards = new LinkedHashMap<String, Object>();
    standards.put("primary", "NORSOK M-001 (Materials selection)");
    standards.put("corrosionModel", "NORSOK M-506 (CO2 corrosion rate)");
    standards.put("sourService", "NACE MR0175 / ISO 15156 (Sour service)");
    standards.put("pitting", "ASTM G48 (Pitting and crevice corrosion testing)");
    result.put("applicableStandards", standards);

    return result;
  }

  /**
   * Returns a comprehensive JSON report of all recommendations and classifications.
   *
   * @return JSON string with all material selection results
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(toMap());
  }

  /**
   * Ensures evaluation has been performed.
   */
  private void ensureEvaluated() {
    if (!hasBeenEvaluated) {
      evaluate();
    }
  }
}
