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
 * CRA (Corrosion Resistant Alloy) material selection for CO2-containing service.
 *
 * <p>
 * Determines when carbon steel with inhibition or corrosion allowance is insufficient, and
 * recommends the appropriate CRA grade. Uses empirical temperature and chloride limits from NORSOK
 * M-001, EFC 17, and vendor qualification data.
 * </p>
 *
 * <h2>Material Hierarchy</h2>
 * <ul>
 * <li>Carbon steel + corrosion allowance + inhibition (cheapest, most common)</li>
 * <li>13Cr martensitic (low CO2, low Cl⁻, no H2S)</li>
 * <li>22Cr duplex (moderate CO2, moderate Cl⁻, mild sour)</li>
 * <li>25Cr super duplex (high CO2, high Cl⁻, moderate sour)</li>
 * <li>Nickel alloy (severe sour + high Cl⁻ + high T)</li>
 * </ul>
 *
 * <h2>Standards</h2>
 *
 * <table>
 * <caption>Standards used for CRA material selection</caption>
 * <tr>
 * <th>Standard</th>
 * <th>Scope</th>
 * </tr>
 * <tr>
 * <td>NORSOK M-001</td>
 * <td>Material selection framework</td>
 * </tr>
 * <tr>
 * <td>EFC 17</td>
 * <td>CRA guidelines for CO2/H2S service</td>
 * </tr>
 * <tr>
 * <td>ISO 15156-3</td>
 * <td>CRA limits in sour service</td>
 * </tr>
 * <tr>
 * <td>DNV-RP-F112</td>
 * <td>Duplex SS in subsea production systems</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 * @see NorsokM001MaterialSelection
 * @see NorsokM506CorrosionRate
 */
public class CO2CorrosionMaterialSelection implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1005L;

  // ─── Input Parameters ───────────────────────────────────

  /** CO2 partial pressure in bar. */
  private double co2PartialPressureBar = 0.0;

  /** H2S partial pressure in bar. */
  private double h2sPartialPressureBar = 0.0;

  /** Temperature in degrees Celsius. */
  private double temperatureC = 60.0;

  /** Chloride concentration in mg/L. */
  private double chlorideConcentrationMgL = 0.0;

  /** CO2 corrosion rate (uninhibited) in mm/yr. */
  private double co2CorrosionRateMmyr = 0.0;

  /** Inhibitor availability factor (0-1). */
  private double inhibitorAvailability = 0.0;

  /** Design life in years. */
  private double designLifeYears = 25.0;

  /** Maximum allowable corrosion allowance in mm. */
  private double maxCorrosionAllowanceMm = 6.0;

  /** Whether continuous inhibition is feasible. */
  private boolean inhibitionFeasible = false;

  /** In-situ pH. */
  private double inSituPH = 4.5;

  // ─── Results ────────────────────────────────────────────

  /** Selected material grade. */
  private String selectedMaterial = "";

  /** Material alternatives (ranked by cost). */
  private List<String> alternatives = new ArrayList<String>();

  /** Selection rationale. */
  private String selectionRationale = "";

  /** Required corrosion allowance for CS option (mm). */
  private double csCorrosionAllowanceMm = 0.0;

  /** Whether carbon steel with inhibition is viable. */
  private boolean carbonSteelViable = false;

  /** Approximate relative cost factor (CS=1.0). */
  private double relativeCostFactor = 1.0;

  /** Notes. */
  private List<String> notes = new ArrayList<String>();

  /** Evaluated flag. */
  private boolean evaluated = false;

  /**
   * Default constructor.
   */
  public CO2CorrosionMaterialSelection() {}

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets CO2 partial pressure.
   *
   * @param bar CO2 partial pressure in bar
   */
  public void setCO2PartialPressureBar(double bar) {
    this.co2PartialPressureBar = bar;
  }

  /**
   * Sets H2S partial pressure.
   *
   * @param bar H2S partial pressure in bar
   */
  public void setH2SPartialPressureBar(double bar) {
    this.h2sPartialPressureBar = bar;
  }

  /**
   * Sets temperature.
   *
   * @param tempC temperature in degrees Celsius
   */
  public void setTemperatureC(double tempC) {
    this.temperatureC = tempC;
  }

  /**
   * Sets chloride concentration.
   *
   * @param mgL chloride in mg/L
   */
  public void setChlorideConcentrationMgL(double mgL) {
    this.chlorideConcentrationMgL = mgL;
  }

  /**
   * Sets CO2 corrosion rate (uninhibited).
   *
   * @param mmyr corrosion rate in mm/yr
   */
  public void setCO2CorrosionRateMmyr(double mmyr) {
    this.co2CorrosionRateMmyr = mmyr;
  }

  /**
   * Sets inhibitor availability factor.
   *
   * @param factor availability factor 0-1 (0.95 = 95%)
   */
  public void setInhibitorAvailability(double factor) {
    this.inhibitorAvailability = factor;
  }

  /**
   * Sets design life.
   *
   * @param years design life in years
   */
  public void setDesignLifeYears(double years) {
    this.designLifeYears = years;
  }

  /**
   * Sets whether continuous inhibition is feasible.
   *
   * @param feasible true if inhibition is feasible
   */
  public void setInhibitionFeasible(boolean feasible) {
    this.inhibitionFeasible = feasible;
  }

  /**
   * Sets in-situ pH.
   *
   * @param pH in-situ pH value
   */
  public void setInSituPH(double pH) {
    this.inSituPH = pH;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Performs material selection.
   */
  public void evaluate() {
    notes.clear();
    alternatives.clear();

    evaluateCarbonSteelViability();
    selectCRAGrade();

    evaluated = true;
  }

  /**
   * Checks if carbon steel with inhibition is viable.
   */
  private void evaluateCarbonSteelViability() {
    double inhibitedRate = co2CorrosionRateMmyr;
    if (inhibitionFeasible && inhibitorAvailability > 0) {
      inhibitedRate = co2CorrosionRateMmyr * (1.0 - inhibitorAvailability * 0.85);
    }

    csCorrosionAllowanceMm = inhibitedRate * designLifeYears;

    carbonSteelViable = (csCorrosionAllowanceMm <= maxCorrosionAllowanceMm)
        && (h2sPartialPressureBar < 0.003) && (co2CorrosionRateMmyr < 10.0);

    if (carbonSteelViable) {
      notes.add("Carbon steel viable with " + String.format("%.1f", csCorrosionAllowanceMm)
          + " mm corrosion allowance (" + designLifeYears + " yr).");
    }
  }

  /**
   * Selects the appropriate CRA grade.
   */
  private void selectCRAGrade() {
    boolean isSour = h2sPartialPressureBar >= 0.003;
    boolean highCl = chlorideConcentrationMgL > 50000;
    boolean veryHighCl = chlorideConcentrationMgL > 120000;

    // 13Cr limits: T < 150°C, pH2S < 0.01 bar, Cl⁻ < 80000 mg/L
    boolean suit13Cr = temperatureC <= 150 && h2sPartialPressureBar <= 0.01
        && chlorideConcentrationMgL <= 80000 && inSituPH >= 3.5;

    // 22Cr duplex limits: T < 232°C, pH2S < 1.0 bar (EFC 17)
    boolean suit22Cr = temperatureC <= 232 && h2sPartialPressureBar <= 1.0 && !veryHighCl;

    // 25Cr super duplex: T < 232°C, pH2S < 3.0 bar
    boolean suit25Cr = temperatureC <= 232 && h2sPartialPressureBar <= 3.0;

    if (carbonSteelViable && !isSour) {
      selectedMaterial = "Carbon steel + corrosion inhibition";
      relativeCostFactor = 1.0;
      selectionRationale = "CO2 corrosion rate manageable with inhibition and "
          + String.format("%.1f", csCorrosionAllowanceMm) + " mm CA.";
      alternatives.add("13Cr (if inhibition reliability is a concern)");
    } else if (suit13Cr && !isSour) {
      selectedMaterial = "13Cr martensitic stainless steel";
      relativeCostFactor = 2.0;
      selectionRationale = "Moderate CO2 environment. T <= 150°C, no H2S.";
      alternatives.add("22Cr duplex (higher margin)");
    } else if (suit22Cr) {
      selectedMaterial = "22Cr duplex stainless steel";
      relativeCostFactor = 3.5;
      selectionRationale = "CO2 + mild sour service. T <= 232°C, pH2S <= 1 bar.";
      alternatives.add("25Cr super duplex (higher Cl⁻/H2S tolerance)");
    } else if (suit25Cr) {
      selectedMaterial = "25Cr super duplex stainless steel";
      relativeCostFactor = 4.5;
      selectionRationale = "Moderate sour service with high chlorides.";
      alternatives.add("Nickel alloy 625 (for extreme conditions)");
    } else {
      selectedMaterial = "Nickel alloy (Alloy 625 or C-276)";
      relativeCostFactor = 8.0;
      selectionRationale = "Severe sour + high temperature + high chlorides.";
      alternatives.add("Alloy C-276 (highest corrosion resistance)");
    }
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Gets selected material.
   *
   * @return selected material grade
   */
  public String getSelectedMaterial() {
    return selectedMaterial;
  }

  /**
   * Gets alternatives.
   *
   * @return list of alternative materials
   */
  public List<String> getAlternatives() {
    return alternatives;
  }

  /**
   * Gets selection rationale.
   *
   * @return rationale text
   */
  public String getSelectionRationale() {
    return selectionRationale;
  }

  /**
   * Gets carbon steel corrosion allowance.
   *
   * @return corrosion allowance in mm
   */
  public double getCsCorrosionAllowanceMm() {
    return csCorrosionAllowanceMm;
  }

  /**
   * Checks if carbon steel is viable.
   *
   * @return true if CS is viable
   */
  public boolean isCarbonSteelViable() {
    return carbonSteelViable;
  }

  /**
   * Gets relative cost factor (CS = 1.0).
   *
   * @return cost multiplier
   */
  public double getRelativeCostFactor() {
    return relativeCostFactor;
  }

  /**
   * Gets notes.
   *
   * @return list of notes
   */
  public List<String> getNotes() {
    return notes;
  }

  /**
   * Converts results to a map.
   *
   * @return ordered map of results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("standard", "NORSOK M-001 / EFC 17 / ISO 15156-3");
    map.put("co2PartialPressure_bar", co2PartialPressureBar);
    map.put("h2sPartialPressure_bar", h2sPartialPressureBar);
    map.put("temperature_C", temperatureC);
    map.put("chlorideConcentration_mgL", chlorideConcentrationMgL);
    map.put("co2CorrosionRate_mmyr", co2CorrosionRateMmyr);
    map.put("selectedMaterial", selectedMaterial);
    map.put("selectionRationale", selectionRationale);
    map.put("alternatives", alternatives);
    map.put("carbonSteelViable", carbonSteelViable);
    map.put("csCorrosionAllowance_mm", csCorrosionAllowanceMm);
    map.put("relativeCostFactor", relativeCostFactor);
    map.put("notes", notes);
    return map;
  }

  /**
   * Converts results to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
