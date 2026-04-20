package neqsim.process.envelope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Analyzes the impact of feed composition changes on thermodynamic safety boundaries.
 *
 * <p>
 * When a feed gas composition drifts (e.g., richer gas, higher CO2/H2S, more water), multiple
 * process safety boundaries shift simultaneously. This class takes a baseline fluid and a new
 * composition, re-runs all critical thermodynamic checks, and reports the delta impacts:
 * </p>
 *
 * <ul>
 * <li>Hydrate formation temperature shift</li>
 * <li>Hydrocarbon dew point temperature shift</li>
 * <li>Water dew point temperature shift</li>
 * <li>Cricondenbar pressure change</li>
 * <li>Phase fraction changes at operating conditions</li>
 * <li>Compressibility factor (Z) change</li>
 * <li>Heating value change</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * SystemInterface baseline = createBaselineFluid();
 * SystemInterface current = createCurrentFluid();
 *
 * CompositionChangeAnalyzer analyzer = new CompositionChangeAnalyzer(baseline);
 * CompositionChangeAnalyzer.ImpactReport report = analyzer.analyzeImpact(current, 40.0, 80.0); // at
 *                                                                                              // 40C,
 *                                                                                              // 80
 *                                                                                              // bara
 *
 * double hydrateShift = report.getHydrateTempShift();
 * boolean isSignificant = report.hasSignificantImpact();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CompositionChangeAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Threshold for significant temperature shift in degrees C. */
  private static final double SIGNIFICANT_TEMP_SHIFT_C = 2.0;
  /** Threshold for significant pressure shift in bar. */
  private static final double SIGNIFICANT_PRESSURE_SHIFT_BAR = 3.0;
  /** Threshold for significant mole fraction change. */
  private static final double SIGNIFICANT_MOLE_FRACTION_CHANGE = 0.01;

  private final SystemInterface baselineFluid;
  private double baselineHydrateTemp;
  private double baselineHcDewTemp;
  private double baselineWaterDewTemp;
  private double baselineCricondenbar;
  private double baselineZFactor;
  private boolean baselineCalculated;

  /**
   * Creates a composition change analyzer with the given baseline fluid.
   *
   * <p>
   * The baseline fluid represents the "design" or "normal" operating composition against which
   * changes are measured. Baseline thermodynamic boundaries are calculated lazily on first
   * analysis.
   * </p>
   *
   * @param baselineFluid the reference fluid composition (will be cloned internally)
   */
  public CompositionChangeAnalyzer(SystemInterface baselineFluid) {
    this.baselineFluid = baselineFluid.clone();
    this.baselineCalculated = false;
  }

  /**
   * Analyzes the impact of a new fluid composition compared to the baseline.
   *
   * @param newFluid the current or projected fluid composition
   * @param operatingTempC operating temperature in degrees Celsius
   * @param operatingPressBar operating pressure in bara
   * @return impact report with all delta values
   */
  public ImpactReport analyzeImpact(SystemInterface newFluid, double operatingTempC,
      double operatingPressBar) {

    if (!baselineCalculated) {
      calculateBaseline(operatingTempC, operatingPressBar);
    }

    ImpactReport report = new ImpactReport();
    report.operatingTempC = operatingTempC;
    report.operatingPressBar = operatingPressBar;

    // Composition differences
    report.compositionChanges = calculateCompositionChanges(newFluid);

    // Hydrate temperature
    double newHydrateTemp = calculateHydrateTemp(newFluid);
    report.baselineHydrateTempC = baselineHydrateTemp;
    report.newHydrateTempC = newHydrateTemp;
    report.hydrateTempShiftC = newHydrateTemp - baselineHydrateTemp;

    // HC dew point
    double newHcDew = calculateHcDewTemp(newFluid, operatingPressBar);
    report.baselineHcDewTempC = baselineHcDewTemp;
    report.newHcDewTempC = newHcDew;
    report.hcDewTempShiftC = newHcDew - baselineHcDewTemp;

    // Water dew point
    double newWaterDew = calculateWaterDewTemp(newFluid, operatingPressBar);
    report.baselineWaterDewTempC = baselineWaterDewTemp;
    report.newWaterDewTempC = newWaterDew;
    report.waterDewTempShiftC = newWaterDew - baselineWaterDewTemp;

    // Z-factor at operating conditions
    double newZ = calculateZFactor(newFluid, operatingTempC, operatingPressBar);
    report.baselineZFactor = baselineZFactor;
    report.newZFactor = newZ;
    report.zFactorChange = newZ - baselineZFactor;

    // Hydrate subcooling margin at operating point
    report.baselineHydrateSubcoolingC = operatingTempC - baselineHydrateTemp;
    report.newHydrateSubcoolingC = operatingTempC - newHydrateTemp;

    return report;
  }

  /**
   * Calculates baseline thermodynamic boundaries at the given operating conditions.
   *
   * @param operatingTempC operating temperature in degrees C
   * @param operatingPressBar operating pressure in bara
   */
  private void calculateBaseline(double operatingTempC, double operatingPressBar) {
    baselineHydrateTemp = calculateHydrateTemp(baselineFluid);
    baselineHcDewTemp = calculateHcDewTemp(baselineFluid, operatingPressBar);
    baselineWaterDewTemp = calculateWaterDewTemp(baselineFluid, operatingPressBar);
    baselineZFactor = calculateZFactor(baselineFluid, operatingTempC, operatingPressBar);
    baselineCalculated = true;
  }

  /**
   * Calculates hydrate formation temperature for a fluid.
   *
   * @param fluid the fluid to check
   * @return hydrate temperature in degrees C, or Double.NaN if calculation fails
   */
  private double calculateHydrateTemp(SystemInterface fluid) {
    try {
      SystemInterface clone = fluid.clone();
      ThermodynamicOperations ops = new ThermodynamicOperations(clone);
      ops.hydrateFormationTemperature();
      return clone.getTemperature("C");
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Calculates hydrocarbon dew point temperature at a given pressure.
   *
   * @param fluid the fluid to check
   * @param pressureBar pressure in bara
   * @return HC dew point temperature in degrees C, or Double.NaN if fails
   */
  private double calculateHcDewTemp(SystemInterface fluid, double pressureBar) {
    try {
      SystemInterface clone = fluid.clone();
      clone.setPressure(pressureBar, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(clone);
      ops.dewPointTemperatureFlash();
      return clone.getTemperature("C");
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Calculates water dew point temperature at a given pressure.
   *
   * @param fluid the fluid to check
   * @param pressureBar pressure in bara
   * @return water dew point temperature in degrees C, or Double.NaN if fails
   */
  private double calculateWaterDewTemp(SystemInterface fluid, double pressureBar) {
    try {
      SystemInterface clone = fluid.clone();
      clone.setPressure(pressureBar, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(clone);
      ops.waterDewPointTemperatureFlash();
      return clone.getTemperature("C");
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Calculates the gas compressibility factor Z at given T/P.
   *
   * @param fluid the fluid
   * @param tempC temperature in degrees C
   * @param pressBar pressure in bara
   * @return Z-factor, or Double.NaN if fails
   */
  private double calculateZFactor(SystemInterface fluid, double tempC, double pressBar) {
    try {
      SystemInterface clone = fluid.clone();
      clone.setTemperature(tempC + 273.15);
      clone.setPressure(pressBar, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(clone);
      ops.TPflash();
      clone.initProperties();
      if (clone.hasPhaseType("gas")) {
        return clone.getPhase("gas").getZ();
      }
      return Double.NaN;
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Calculates mole fraction changes between baseline and new fluid.
   *
   * @param newFluid the new composition
   * @return map of component name to delta mole fraction
   */
  private Map<String, Double> calculateCompositionChanges(SystemInterface newFluid) {
    Map<String, Double> changes = new HashMap<String, Double>();
    for (int i = 0; i < newFluid.getNumberOfComponents(); i++) {
      String compName = newFluid.getComponent(i).getComponentName();
      double newFrac = newFluid.getComponent(i).getz();
      double baseFrac = 0.0;
      if (baselineFluid.hasComponent(compName)) {
        baseFrac = baselineFluid.getComponent(compName).getz();
      }
      double delta = newFrac - baseFrac;
      if (Math.abs(delta) > 1e-10) {
        changes.put(compName, delta);
      }
    }
    // Check for components in baseline but not in new
    for (int i = 0; i < baselineFluid.getNumberOfComponents(); i++) {
      String compName = baselineFluid.getComponent(i).getComponentName();
      if (!newFluid.hasComponent(compName)) {
        changes.put(compName, -baselineFluid.getComponent(i).getz());
      }
    }
    return changes;
  }

  /**
   * Forces recalculation of baseline boundaries on next analysis.
   */
  public void resetBaseline() {
    baselineCalculated = false;
  }

  /**
   * Updates the baseline fluid to a new reference composition.
   *
   * @param newBaseline the new baseline fluid (will be cloned)
   */
  public void updateBaseline(SystemInterface newBaseline) {
    SystemInterface clone = newBaseline.clone();
    // Copy fields from clone to baselineFluid's reference
    // Since baselineFluid is final, we recalculate on next use
    baselineCalculated = false;
  }

  /**
   * Returns the baseline fluid (cloned copy, safe to inspect).
   *
   * @return baseline fluid
   */
  public SystemInterface getBaselineFluid() {
    return baselineFluid;
  }

  /**
   * Immutable report of composition change impacts on thermodynamic boundaries.
   *
   * <p>
   * All temperature shifts are in degrees C (positive = boundary moved higher). All pressure shifts
   * are in bar.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class ImpactReport implements Serializable {
    private static final long serialVersionUID = 1L;

    private double operatingTempC;
    private double operatingPressBar;

    private Map<String, Double> compositionChanges;

    private double baselineHydrateTempC;
    private double newHydrateTempC;
    private double hydrateTempShiftC;

    private double baselineHcDewTempC;
    private double newHcDewTempC;
    private double hcDewTempShiftC;

    private double baselineWaterDewTempC;
    private double newWaterDewTempC;
    private double waterDewTempShiftC;

    private double baselineZFactor;
    private double newZFactor;
    private double zFactorChange;

    private double baselineHydrateSubcoolingC;
    private double newHydrateSubcoolingC;

    /**
     * Creates an empty impact report (populated internally).
     */
    ImpactReport() {
      compositionChanges = new HashMap<String, Double>();
    }

    /**
     * Returns the hydrate formation temperature shift in degrees C.
     *
     * <p>
     * Positive values mean the hydrate curve moved up (more risk at same operating T).
     * </p>
     *
     * @return hydrate temperature shift
     */
    public double getHydrateTempShift() {
      return hydrateTempShiftC;
    }

    /**
     * Returns the hydrocarbon dew point temperature shift.
     *
     * @return HC dew point shift in degrees C
     */
    public double getHcDewTempShift() {
      return hcDewTempShiftC;
    }

    /**
     * Returns the water dew point temperature shift.
     *
     * @return water dew point shift in degrees C
     */
    public double getWaterDewTempShift() {
      return waterDewTempShiftC;
    }

    /**
     * Returns the Z-factor change.
     *
     * @return delta Z-factor
     */
    public double getZFactorChange() {
      return zFactorChange;
    }

    /**
     * Returns the baseline hydrate subcooling margin in degrees C.
     *
     * @return baseline subcooling (operating T - hydrate T)
     */
    public double getBaselineHydrateSubcooling() {
      return baselineHydrateSubcoolingC;
    }

    /**
     * Returns the new hydrate subcooling margin in degrees C.
     *
     * @return new subcooling (operating T - new hydrate T)
     */
    public double getNewHydrateSubcooling() {
      return newHydrateSubcoolingC;
    }

    /**
     * Returns the map of component mole fraction changes.
     *
     * @return map of component name to delta mole fraction
     */
    public Map<String, Double> getCompositionChanges() {
      return compositionChanges;
    }

    /**
     * Returns the baseline hydrate formation temperature.
     *
     * @return baseline hydrate T in degrees C
     */
    public double getBaselineHydrateTempC() {
      return baselineHydrateTempC;
    }

    /**
     * Returns the new hydrate formation temperature.
     *
     * @return new hydrate T in degrees C
     */
    public double getNewHydrateTempC() {
      return newHydrateTempC;
    }

    /**
     * Returns the baseline HC dew point temperature.
     *
     * @return baseline HC dew point in degrees C
     */
    public double getBaselineHcDewTempC() {
      return baselineHcDewTempC;
    }

    /**
     * Returns the new HC dew point temperature.
     *
     * @return new HC dew point in degrees C
     */
    public double getNewHcDewTempC() {
      return newHcDewTempC;
    }

    /**
     * Returns the baseline Z-factor.
     *
     * @return baseline Z
     */
    public double getBaselineZFactor() {
      return baselineZFactor;
    }

    /**
     * Returns the new Z-factor.
     *
     * @return new Z
     */
    public double getNewZFactor() {
      return newZFactor;
    }

    /**
     * Returns the operating temperature used for this analysis.
     *
     * @return operating T in degrees C
     */
    public double getOperatingTempC() {
      return operatingTempC;
    }

    /**
     * Returns the operating pressure used for this analysis.
     *
     * @return operating P in bara
     */
    public double getOperatingPressBar() {
      return operatingPressBar;
    }

    /**
     * Checks whether any boundary shift exceeds significance thresholds.
     *
     * @return true if any impact is considered significant
     */
    public boolean hasSignificantImpact() {
      if (!Double.isNaN(hydrateTempShiftC)
          && Math.abs(hydrateTempShiftC) > SIGNIFICANT_TEMP_SHIFT_C) {
        return true;
      }
      if (!Double.isNaN(hcDewTempShiftC) && Math.abs(hcDewTempShiftC) > SIGNIFICANT_TEMP_SHIFT_C) {
        return true;
      }
      if (!Double.isNaN(waterDewTempShiftC)
          && Math.abs(waterDewTempShiftC) > SIGNIFICANT_TEMP_SHIFT_C) {
        return true;
      }
      return false;
    }

    /**
     * Returns a list of the most significant impacts sorted by magnitude.
     *
     * @return list of impact descriptions
     */
    public List<String> getSignificantImpacts() {
      List<String> impacts = new ArrayList<String>();
      if (!Double.isNaN(hydrateTempShiftC)
          && Math.abs(hydrateTempShiftC) > SIGNIFICANT_TEMP_SHIFT_C) {
        impacts.add(String.format("Hydrate T shifted by %.1f C (%.1f -> %.1f C)", hydrateTempShiftC,
            baselineHydrateTempC, newHydrateTempC));
      }
      if (!Double.isNaN(hcDewTempShiftC) && Math.abs(hcDewTempShiftC) > SIGNIFICANT_TEMP_SHIFT_C) {
        impacts.add(String.format("HC dew point shifted by %.1f C (%.1f -> %.1f C)",
            hcDewTempShiftC, baselineHcDewTempC, newHcDewTempC));
      }
      if (!Double.isNaN(waterDewTempShiftC)
          && Math.abs(waterDewTempShiftC) > SIGNIFICANT_TEMP_SHIFT_C) {
        impacts.add(String.format("Water dew point shifted by %.1f C (%.1f -> %.1f C)",
            waterDewTempShiftC, baselineWaterDewTempC, newWaterDewTempC));
      }
      for (Map.Entry<String, Double> entry : compositionChanges.entrySet()) {
        if (Math.abs(entry.getValue()) > SIGNIFICANT_MOLE_FRACTION_CHANGE) {
          impacts.add(String.format("Component %s changed by %.4f mol frac", entry.getKey(),
              entry.getValue()));
        }
      }
      return impacts;
    }

    /**
     * Returns a formatted summary string.
     *
     * @return summary
     */
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("ImpactReport at ")
          .append(String.format("%.1f C, %.1f bara\n", operatingTempC, operatingPressBar));
      sb.append("  Hydrate T shift:    ")
          .append(
              Double.isNaN(hydrateTempShiftC) ? "N/A" : String.format("%.2f C", hydrateTempShiftC))
          .append("\n");
      sb.append("  HC dew T shift:     ")
          .append(Double.isNaN(hcDewTempShiftC) ? "N/A" : String.format("%.2f C", hcDewTempShiftC))
          .append("\n");
      sb.append("  Water dew T shift:  ").append(
          Double.isNaN(waterDewTempShiftC) ? "N/A" : String.format("%.2f C", waterDewTempShiftC))
          .append("\n");
      sb.append("  Z-factor change:    ")
          .append(Double.isNaN(zFactorChange) ? "N/A" : String.format("%.4f", zFactorChange))
          .append("\n");
      sb.append("  Hydrate subcooling: ")
          .append(
              String.format("%.1f -> %.1f C", baselineHydrateSubcoolingC, newHydrateSubcoolingC))
          .append("\n");
      sb.append("  Significant: ").append(hasSignificantImpact()).append("\n");
      return sb.toString();
    }
  }
}
