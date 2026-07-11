package neqsim.process.mechanicaldesign.separator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.mechanicaldesign.separator.internals.InternalOperatingWindow;

/**
 * Aggregated separation-efficiency report for a complete separator or gas scrubber.
 *
 * <p>
 * Combines the physics-based entrainment / carry-under fractions computed by the {@code SeparatorPerformanceCalculator}
 * with a per-internal K-factor operating-window assessment (from {@link InternalOperatingWindow}). The report answers
 * two questions:
 * </p>
 *
 * <ol>
 * <li>What is the separation efficiency of the vessel (gas-liquid and, for three-phase, liquid-liquid) given how the
 * internals are configured?</li>
 * <li>Is each internal (mist mat, vane pack, cyclone deck) operating inside its recommended K-factor window, or is it
 * below turndown or into flooding?</li>
 * </ol>
 *
 * <p>
 * The report is produced on demand by {@code SeparatorMechanicalDesign.calculateSeparationEfficiency()} and does not
 * alter the separator run-time behaviour. Whether the physics model is applied during {@code run()} is controlled
 * separately by the detailed-entrainment toggle.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SeparatorEfficiencyReport implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Overall verdict values. */
  public static final String VERDICT_GOOD = "GOOD_PERFORMANCE";
  /** Verdict: operating below the recommended K-factor window (turndown limited). */
  public static final String VERDICT_BELOW_TURNDOWN = "BELOW_TURNDOWN";
  /** Verdict: at least one internal is above its maximum K-factor (flooding risk). */
  public static final String VERDICT_FLOODING_RISK = "FLOODING_RISK";
  /** Verdict: internals in range but overall efficiency below the good threshold. */
  public static final String VERDICT_MARGINAL = "MARGINAL_EFFICIENCY";
  /** Verdict: not enough information to assess. */
  public static final String VERDICT_UNKNOWN = "UNKNOWN";

  /** Efficiency threshold above which gas-liquid performance is considered good. */
  private double goodEfficiencyThreshold = 0.99;

  /** Separator name. */
  private String separatorName = "";

  /** Vessel orientation ("horizontal" or "vertical"). */
  private String orientation = "";

  /** Whether the assessment is for a three-phase (gas-oil-water) system. */
  private boolean threePhase = false;

  /** Whether the physics efficiency model was enabled/available at report time. */
  private boolean efficiencyModelEnabled = false;

  /** Operating Souders-Brown K-factor [m/s]. */
  private double operatingKFactor = 0.0;

  /** Design (target) Souders-Brown K-factor [m/s]. */
  private double designKFactor = 0.0;

  /** Per-internal K-factor operating windows. */
  private List<InternalOperatingWindow> windows = new ArrayList<InternalOperatingWindow>();

  /** Overall gas-liquid separation efficiency [0-1]. */
  private double overallGasLiquidEfficiency = Double.NaN;

  /** Oil-in-gas carry-over fraction [0-1]. */
  private double oilInGasFraction = 0.0;

  /** Water-in-gas carry-over fraction [0-1]. */
  private double waterInGasFraction = 0.0;

  /** Gas-in-oil carry-under fraction [0-1]. */
  private double gasInOilFraction = 0.0;

  /** Gas-in-water carry-under fraction [0-1]. */
  private double gasInWaterFraction = 0.0;

  /** Oil-in-water carry-over fraction [0-1] (three-phase). */
  private double oilInWaterFraction = 0.0;

  /** Water-in-oil carry-over fraction [0-1] (three-phase). */
  private double waterInOilFraction = 0.0;

  /** Whether the mist eliminator is flooded at the operating point. */
  private boolean mistEliminatorFlooded = false;

  /** Overall verdict. */
  private String verdict = VERDICT_UNKNOWN;

  /** Informational notes (e.g. missing data). */
  private List<String> notes = new ArrayList<String>();

  /**
   * Constructs an empty efficiency report.
   */
  public SeparatorEfficiencyReport() {
  }

  /**
   * Adds a per-internal operating window to the report.
   *
   * @param window the operating window to add (ignored if null)
   */
  public void addWindow(InternalOperatingWindow window) {
    if (window != null) {
      windows.add(window);
    }
  }

  /**
   * Adds an informational note to the report.
   *
   * @param note the note text
   */
  public void addNote(String note) {
    if (note != null && !note.trim().isEmpty()) {
      notes.add(note);
    }
  }

  /**
   * Computes the overall verdict from the internal operating windows and the overall gas-liquid efficiency.
   *
   * <p>
   * Flooding of any internal dominates, followed by below-turndown operation; otherwise the verdict is driven by the
   * overall gas-liquid efficiency against {@link #goodEfficiencyThreshold}.
   * </p>
   */
  public void computeVerdict() {
    boolean anyFlooding = mistEliminatorFlooded;
    boolean anyBelowTurndown = false;
    boolean anyKnown = false;
    for (InternalOperatingWindow w : windows) {
      switch (w.getStatus()) {
      case ABOVE_MAX_FLOODING:
        anyFlooding = true;
        anyKnown = true;
        break;
      case BELOW_MIN_TURNDOWN:
        anyBelowTurndown = true;
        anyKnown = true;
        break;
      case IN_RANGE:
        anyKnown = true;
        break;
      default:
        break;
      }
    }
    if (anyFlooding) {
      verdict = VERDICT_FLOODING_RISK;
    } else if (anyBelowTurndown) {
      verdict = VERDICT_BELOW_TURNDOWN;
    } else if (!anyKnown && Double.isNaN(overallGasLiquidEfficiency)) {
      verdict = VERDICT_UNKNOWN;
    } else if (!Double.isNaN(overallGasLiquidEfficiency) && overallGasLiquidEfficiency < goodEfficiencyThreshold) {
      verdict = VERDICT_MARGINAL;
    } else {
      verdict = VERDICT_GOOD;
    }
  }

  /**
   * Builds a map representation of this report for serialization or reporting.
   *
   * @return an ordered map of report properties
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("separatorName", separatorName);
    map.put("orientation", orientation);
    map.put("threePhase", threePhase);
    map.put("efficiencyModelEnabled", efficiencyModelEnabled);
    map.put("operatingKFactor_m_s", operatingKFactor);
    map.put("designKFactor_m_s", designKFactor);
    map.put("overallGasLiquidEfficiency", overallGasLiquidEfficiency);
    map.put("mistEliminatorFlooded", mistEliminatorFlooded);

    Map<String, Object> entrainment = new LinkedHashMap<String, Object>();
    entrainment.put("oilInGasFraction", oilInGasFraction);
    entrainment.put("waterInGasFraction", waterInGasFraction);
    entrainment.put("gasInOilFraction", gasInOilFraction);
    entrainment.put("gasInWaterFraction", gasInWaterFraction);
    if (threePhase) {
      entrainment.put("oilInWaterFraction", oilInWaterFraction);
      entrainment.put("waterInOilFraction", waterInOilFraction);
    }
    map.put("entrainment", entrainment);

    List<Map<String, Object>> windowList = new ArrayList<Map<String, Object>>();
    for (InternalOperatingWindow w : windows) {
      windowList.add(w.toMap());
    }
    map.put("internals", windowList);

    map.put("verdict", verdict);
    map.put("notes", notes);
    return map;
  }

  /**
   * Serializes this report to a pretty-printed JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }

  /**
   * Gets the separator name.
   *
   * @return the separator name
   */
  public String getSeparatorName() {
    return separatorName;
  }

  /**
   * Sets the separator name.
   *
   * @param separatorName the separator name
   */
  public void setSeparatorName(String separatorName) {
    this.separatorName = separatorName;
  }

  /**
   * Gets the vessel orientation.
   *
   * @return the orientation
   */
  public String getOrientation() {
    return orientation;
  }

  /**
   * Sets the vessel orientation.
   *
   * @param orientation the orientation
   */
  public void setOrientation(String orientation) {
    this.orientation = orientation;
  }

  /**
   * Returns whether the assessment is for a three-phase system.
   *
   * @return true if three-phase
   */
  public boolean isThreePhase() {
    return threePhase;
  }

  /**
   * Sets whether the assessment is for a three-phase system.
   *
   * @param threePhase true if three-phase
   */
  public void setThreePhase(boolean threePhase) {
    this.threePhase = threePhase;
  }

  /**
   * Returns whether the physics efficiency model was enabled/available at report time.
   *
   * @return true if enabled
   */
  public boolean isEfficiencyModelEnabled() {
    return efficiencyModelEnabled;
  }

  /**
   * Sets whether the physics efficiency model was enabled/available at report time.
   *
   * @param efficiencyModelEnabled true if enabled
   */
  public void setEfficiencyModelEnabled(boolean efficiencyModelEnabled) {
    this.efficiencyModelEnabled = efficiencyModelEnabled;
  }

  /**
   * Gets the operating Souders-Brown K-factor.
   *
   * @return operating K-factor [m/s]
   */
  public double getOperatingKFactor() {
    return operatingKFactor;
  }

  /**
   * Sets the operating Souders-Brown K-factor.
   *
   * @param operatingKFactor operating K-factor [m/s]
   */
  public void setOperatingKFactor(double operatingKFactor) {
    this.operatingKFactor = operatingKFactor;
  }

  /**
   * Gets the design (target) Souders-Brown K-factor.
   *
   * @return design K-factor [m/s]
   */
  public double getDesignKFactor() {
    return designKFactor;
  }

  /**
   * Sets the design (target) Souders-Brown K-factor.
   *
   * @param designKFactor design K-factor [m/s]
   */
  public void setDesignKFactor(double designKFactor) {
    this.designKFactor = designKFactor;
  }

  /**
   * Gets the per-internal operating windows.
   *
   * @return list of operating windows
   */
  public List<InternalOperatingWindow> getWindows() {
    return windows;
  }

  /**
   * Gets the overall gas-liquid separation efficiency.
   *
   * @return overall gas-liquid efficiency [0-1]
   */
  public double getOverallGasLiquidEfficiency() {
    return overallGasLiquidEfficiency;
  }

  /**
   * Sets the overall gas-liquid separation efficiency.
   *
   * @param overallGasLiquidEfficiency overall gas-liquid efficiency [0-1]
   */
  public void setOverallGasLiquidEfficiency(double overallGasLiquidEfficiency) {
    this.overallGasLiquidEfficiency = overallGasLiquidEfficiency;
  }

  /**
   * Gets the oil-in-gas carry-over fraction.
   *
   * @return oil-in-gas fraction [0-1]
   */
  public double getOilInGasFraction() {
    return oilInGasFraction;
  }

  /**
   * Sets the oil-in-gas carry-over fraction.
   *
   * @param oilInGasFraction oil-in-gas fraction [0-1]
   */
  public void setOilInGasFraction(double oilInGasFraction) {
    this.oilInGasFraction = oilInGasFraction;
  }

  /**
   * Gets the water-in-gas carry-over fraction.
   *
   * @return water-in-gas fraction [0-1]
   */
  public double getWaterInGasFraction() {
    return waterInGasFraction;
  }

  /**
   * Sets the water-in-gas carry-over fraction.
   *
   * @param waterInGasFraction water-in-gas fraction [0-1]
   */
  public void setWaterInGasFraction(double waterInGasFraction) {
    this.waterInGasFraction = waterInGasFraction;
  }

  /**
   * Gets the gas-in-oil carry-under fraction.
   *
   * @return gas-in-oil fraction [0-1]
   */
  public double getGasInOilFraction() {
    return gasInOilFraction;
  }

  /**
   * Sets the gas-in-oil carry-under fraction.
   *
   * @param gasInOilFraction gas-in-oil fraction [0-1]
   */
  public void setGasInOilFraction(double gasInOilFraction) {
    this.gasInOilFraction = gasInOilFraction;
  }

  /**
   * Gets the gas-in-water carry-under fraction.
   *
   * @return gas-in-water fraction [0-1]
   */
  public double getGasInWaterFraction() {
    return gasInWaterFraction;
  }

  /**
   * Sets the gas-in-water carry-under fraction.
   *
   * @param gasInWaterFraction gas-in-water fraction [0-1]
   */
  public void setGasInWaterFraction(double gasInWaterFraction) {
    this.gasInWaterFraction = gasInWaterFraction;
  }

  /**
   * Gets the oil-in-water carry-over fraction.
   *
   * @return oil-in-water fraction [0-1]
   */
  public double getOilInWaterFraction() {
    return oilInWaterFraction;
  }

  /**
   * Sets the oil-in-water carry-over fraction.
   *
   * @param oilInWaterFraction oil-in-water fraction [0-1]
   */
  public void setOilInWaterFraction(double oilInWaterFraction) {
    this.oilInWaterFraction = oilInWaterFraction;
  }

  /**
   * Gets the water-in-oil carry-over fraction.
   *
   * @return water-in-oil fraction [0-1]
   */
  public double getWaterInOilFraction() {
    return waterInOilFraction;
  }

  /**
   * Sets the water-in-oil carry-over fraction.
   *
   * @param waterInOilFraction water-in-oil fraction [0-1]
   */
  public void setWaterInOilFraction(double waterInOilFraction) {
    this.waterInOilFraction = waterInOilFraction;
  }

  /**
   * Returns whether the mist eliminator is flooded at the operating point.
   *
   * @return true if flooded
   */
  public boolean isMistEliminatorFlooded() {
    return mistEliminatorFlooded;
  }

  /**
   * Sets whether the mist eliminator is flooded at the operating point.
   *
   * @param mistEliminatorFlooded true if flooded
   */
  public void setMistEliminatorFlooded(boolean mistEliminatorFlooded) {
    this.mistEliminatorFlooded = mistEliminatorFlooded;
  }

  /**
   * Gets the overall verdict.
   *
   * @return the verdict string
   */
  public String getVerdict() {
    return verdict;
  }

  /**
   * Gets the informational notes.
   *
   * @return list of notes
   */
  public List<String> getNotes() {
    return notes;
  }

  /**
   * Gets the efficiency threshold above which gas-liquid performance is considered good.
   *
   * @return the good-efficiency threshold [0-1]
   */
  public double getGoodEfficiencyThreshold() {
    return goodEfficiencyThreshold;
  }

  /**
   * Sets the efficiency threshold above which gas-liquid performance is considered good.
   *
   * @param goodEfficiencyThreshold the good-efficiency threshold [0-1]
   */
  public void setGoodEfficiencyThreshold(double goodEfficiencyThreshold) {
    this.goodEfficiencyThreshold = goodEfficiencyThreshold;
  }
}
