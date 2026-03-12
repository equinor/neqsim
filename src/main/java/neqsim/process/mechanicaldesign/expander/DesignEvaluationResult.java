package neqsim.process.mechanicaldesign.expander;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the result of evaluating a fixed turbo-expander-compressor mechanical design against a set
 * of operating conditions (pressures, temperatures, flows, compositions).
 *
 * <p>
 * Each margin is expressed as a fraction (positive = within limit, negative = exceeded). Warnings
 * are raised when margin &lt; 15 %, failures when margin &lt; 0 %.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DesignEvaluationResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Warning threshold: margin below this fraction triggers a warning. */
  private static final double WARNING_THRESHOLD = 0.15;

  // ============================================================================
  // Margins (positive = safe, negative = violated)
  // ============================================================================

  /** Expander tip-speed margin relative to material limit. */
  private double expanderTipSpeedMargin = 1.0;

  /** Compressor tip-speed margin relative to material limit. */
  private double compressorTipSpeedMargin = 1.0;

  /** Expander casing pressure margin relative to design pressure. */
  private double expanderCasingPressureMargin = 1.0;

  /** Compressor casing pressure margin relative to design pressure. */
  private double compressorCasingPressureMargin = 1.0;

  /** Shaft torsional-stress margin relative to allowable stress. */
  private double shaftStressMargin = 1.0;

  /** Critical-speed separation margin per API 617. */
  private double criticalSpeedSeparationMargin = 1.0;

  /** Compressor surge margin (flow above surge line). */
  private double compressorSurgeMargin = 1.0;

  /** Expander choke margin (flow below choke). */
  private double expanderChokeMargin = 1.0;

  /** Compressor discharge temperature margin to material limit. */
  private double dischargeTemperatureMargin = 1.0;

  /** Thrust bearing load margin. */
  private double thrustBearingLoadMargin = 1.0;

  /** Shear pin torque margin (operating torque vs breaking torque). */
  private double shearPinTorqueMargin = 1.0;

  /** Seal gas differential pressure margin. */
  private double sealGasDpMargin = 1.0;

  /** Anti-surge margin (operating flow vs surge control line). */
  private double antiSurgeMargin = 1.0;

  /** Whether thrust direction has reversed compared to design. */
  private boolean thrustReversalDetected = false;

  /** Net axial thrust [N] — positive = towards compressor, negative = towards expander. */
  private double netAxialThrustN = 0.0;

  /** Expander-side axial thrust [N]. */
  private double expanderAxialThrustN = 0.0;

  /** Compressor-side axial thrust [N]. */
  private double compressorAxialThrustN = 0.0;

  /** Overall result: true if every margin is &ge; 0. */
  private boolean acceptable = true;

  /** Scenario name for multi-scenario evaluation. */
  private String scenarioName = "";

  /** Warnings (margin &lt; 15 % but &ge; 0 %). */
  private final List<String> warnings = new ArrayList<String>();

  /** Failures (margin &lt; 0 %). */
  private final List<String> failures = new ArrayList<String>();

  /**
   * Construct an empty evaluation result.
   */
  public DesignEvaluationResult() {}

  // ============================================================================
  // Margin setters with automatic warning / failure detection
  // ============================================================================

  /**
   * Set the expander tip-speed margin and check limits.
   *
   * @param margin fractional margin (1.0 = 100 % headroom)
   */
  public void setExpanderTipSpeedMargin(double margin) {
    this.expanderTipSpeedMargin = margin;
    checkMargin("Expander tip speed", margin);
  }

  /**
   * Set the compressor tip-speed margin and check limits.
   *
   * @param margin fractional margin
   */
  public void setCompressorTipSpeedMargin(double margin) {
    this.compressorTipSpeedMargin = margin;
    checkMargin("Compressor tip speed", margin);
  }

  /**
   * Set the expander casing pressure margin and check limits.
   *
   * @param margin fractional margin
   */
  public void setExpanderCasingPressureMargin(double margin) {
    this.expanderCasingPressureMargin = margin;
    checkMargin("Expander casing pressure", margin);
  }

  /**
   * Set the compressor casing pressure margin and check limits.
   *
   * @param margin fractional margin
   */
  public void setCompressorCasingPressureMargin(double margin) {
    this.compressorCasingPressureMargin = margin;
    checkMargin("Compressor casing pressure", margin);
  }

  /**
   * Set the shaft torsional-stress margin and check limits.
   *
   * @param margin fractional margin
   */
  public void setShaftStressMargin(double margin) {
    this.shaftStressMargin = margin;
    checkMargin("Shaft torsional stress", margin);
  }

  /**
   * Set the critical-speed separation margin and check limits.
   *
   * @param margin fractional margin
   */
  public void setCriticalSpeedSeparationMargin(double margin) {
    this.criticalSpeedSeparationMargin = margin;
    checkMargin("Critical speed separation", margin);
  }

  /**
   * Set the compressor surge margin and check limits.
   *
   * @param margin fractional margin
   */
  public void setCompressorSurgeMargin(double margin) {
    this.compressorSurgeMargin = margin;
    checkMargin("Compressor surge", margin);
  }

  /**
   * Set the expander choke margin and check limits.
   *
   * @param margin fractional margin
   */
  public void setExpanderChokeMargin(double margin) {
    this.expanderChokeMargin = margin;
    checkMargin("Expander choke", margin);
  }

  /**
   * Set the discharge temperature margin and check limits.
   *
   * @param margin fractional margin
   */
  public void setDischargeTemperatureMargin(double margin) {
    this.dischargeTemperatureMargin = margin;
    checkMargin("Discharge temperature", margin);
  }

  /**
   * Set the thrust bearing load margin and check limits.
   *
   * @param margin fractional margin
   */
  public void setThrustBearingLoadMargin(double margin) {
    this.thrustBearingLoadMargin = margin;
    checkMargin("Thrust bearing load", margin);
  }

  /**
   * Set the shear pin torque margin and check limits.
   *
   * <p>
   * Margin = (breakingTorque - operatingTorque) / breakingTorque. A negative margin means the pins
   * would shear during normal operation.
   * </p>
   *
   * @param margin fractional margin
   */
  public void setShearPinTorqueMargin(double margin) {
    this.shearPinTorqueMargin = margin;
    checkMargin("Shear pin torque", margin);
  }

  /**
   * Set the seal gas differential pressure margin and check limits.
   *
   * <p>
   * Margin = (sealGasSupplyP - processP - minDp) / minDp. A negative margin means seal gas supply
   * pressure is insufficient to maintain the buffer.
   * </p>
   *
   * @param margin fractional margin
   */
  public void setSealGasDpMargin(double margin) {
    this.sealGasDpMargin = margin;
    checkMargin("Seal gas differential pressure", margin);
  }

  /**
   * Set the anti-surge margin and check limits.
   *
   * <p>
   * Margin = (operatingFlow - surgeControlLineFlow) / surgeControlLineFlow. A negative margin means
   * the compressor is inside the surge control line.
   * </p>
   *
   * @param margin fractional margin
   */
  public void setAntiSurgeMargin(double margin) {
    this.antiSurgeMargin = margin;
    checkMargin("Anti-surge", margin);
  }

  /**
   * Set detailed thrust balance values.
   *
   * @param expanderThrustN expander axial thrust [N]
   * @param compressorThrustN compressor axial thrust [N]
   * @param netThrustN net axial thrust [N] (positive = towards compressor end)
   * @param reversalDetected true if thrust direction reversed vs design
   */
  public void setThrustBalanceDetails(double expanderThrustN, double compressorThrustN,
      double netThrustN, boolean reversalDetected) {
    this.expanderAxialThrustN = expanderThrustN;
    this.compressorAxialThrustN = compressorThrustN;
    this.netAxialThrustN = netThrustN;
    this.thrustReversalDetected = reversalDetected;
    if (reversalDetected) {
      warnings.add("Thrust reversal detected — bearing life may be reduced");
    }
  }

  // ============================================================================
  // Getters
  // ============================================================================

  /**
   * Whether all margins are non-negative.
   *
   * @return true if the design is acceptable at these conditions
   */
  public boolean isAcceptable() {
    return acceptable;
  }

  /**
   * Get the scenario name.
   *
   * @return scenario name
   */
  public String getScenarioName() {
    return scenarioName;
  }

  /**
   * Set the scenario name.
   *
   * @param name scenario name
   */
  public void setScenarioName(String name) {
    this.scenarioName = name;
  }

  /**
   * Get the expander tip-speed margin.
   *
   * @return fractional margin
   */
  public double getExpanderTipSpeedMargin() {
    return expanderTipSpeedMargin;
  }

  /**
   * Get the compressor tip-speed margin.
   *
   * @return fractional margin
   */
  public double getCompressorTipSpeedMargin() {
    return compressorTipSpeedMargin;
  }

  /**
   * Get the expander casing pressure margin.
   *
   * @return fractional margin
   */
  public double getExpanderCasingPressureMargin() {
    return expanderCasingPressureMargin;
  }

  /**
   * Get the compressor casing pressure margin.
   *
   * @return fractional margin
   */
  public double getCompressorCasingPressureMargin() {
    return compressorCasingPressureMargin;
  }

  /**
   * Get the shaft torsional-stress margin.
   *
   * @return fractional margin
   */
  public double getShaftStressMargin() {
    return shaftStressMargin;
  }

  /**
   * Get the critical-speed separation margin.
   *
   * @return fractional margin
   */
  public double getCriticalSpeedSeparationMargin() {
    return criticalSpeedSeparationMargin;
  }

  /**
   * Get the compressor surge margin.
   *
   * @return fractional margin
   */
  public double getCompressorSurgeMargin() {
    return compressorSurgeMargin;
  }

  /**
   * Get the expander choke margin.
   *
   * @return fractional margin
   */
  public double getExpanderChokeMargin() {
    return expanderChokeMargin;
  }

  /**
   * Get the discharge temperature margin.
   *
   * @return fractional margin
   */
  public double getDischargeTemperatureMargin() {
    return dischargeTemperatureMargin;
  }

  /**
   * Get the thrust bearing load margin.
   *
   * @return fractional margin
   */
  public double getThrustBearingLoadMargin() {
    return thrustBearingLoadMargin;
  }

  /**
   * Get the shear pin torque margin.
   *
   * @return fractional margin
   */
  public double getShearPinTorqueMargin() {
    return shearPinTorqueMargin;
  }

  /**
   * Get the seal gas differential pressure margin.
   *
   * @return fractional margin
   */
  public double getSealGasDpMargin() {
    return sealGasDpMargin;
  }

  /**
   * Get the anti-surge margin.
   *
   * @return fractional margin
   */
  public double getAntiSurgeMargin() {
    return antiSurgeMargin;
  }

  /**
   * Whether thrust direction has reversed compared to design.
   *
   * @return true if thrust reversal detected
   */
  public boolean isThrustReversalDetected() {
    return thrustReversalDetected;
  }

  /**
   * Get net axial thrust (positive = towards compressor end).
   *
   * @return net thrust in N
   */
  public double getNetAxialThrustN() {
    return netAxialThrustN;
  }

  /**
   * Get expander-side axial thrust.
   *
   * @return thrust in N
   */
  public double getExpanderAxialThrustN() {
    return expanderAxialThrustN;
  }

  /**
   * Get compressor-side axial thrust.
   *
   * @return thrust in N
   */
  public double getCompressorAxialThrustN() {
    return compressorAxialThrustN;
  }

  /**
   * Get all warnings (near-limit conditions).
   *
   * @return unmodifiable list of warning strings
   */
  public List<String> getWarnings() {
    return new ArrayList<String>(warnings);
  }

  /**
   * Get all failures (violated limits).
   *
   * @return unmodifiable list of failure strings
   */
  public List<String> getFailures() {
    return new ArrayList<String>(failures);
  }

  /**
   * Return all margins as a map.
   *
   * @return map of margin name to fractional value
   */
  public Map<String, Double> getMargins() {
    Map<String, Double> m = new LinkedHashMap<String, Double>();
    m.put("expanderTipSpeed", expanderTipSpeedMargin);
    m.put("compressorTipSpeed", compressorTipSpeedMargin);
    m.put("expanderCasingPressure", expanderCasingPressureMargin);
    m.put("compressorCasingPressure", compressorCasingPressureMargin);
    m.put("shaftStress", shaftStressMargin);
    m.put("criticalSpeedSeparation", criticalSpeedSeparationMargin);
    m.put("compressorSurge", compressorSurgeMargin);
    m.put("expanderChoke", expanderChokeMargin);
    m.put("dischargeTemperature", dischargeTemperatureMargin);
    m.put("thrustBearingLoad", thrustBearingLoadMargin);
    m.put("shearPinTorque", shearPinTorqueMargin);
    m.put("sealGasDp", sealGasDpMargin);
    m.put("antiSurge", antiSurgeMargin);
    return m;
  }

  // ============================================================================
  // Internal helpers
  // ============================================================================

  /**
   * Check a margin against thresholds and record warnings / failures.
   *
   * @param name human-readable name of the parameter
   * @param margin fractional margin
   */
  private void checkMargin(String name, double margin) {
    if (margin < 0.0) {
      acceptable = false;
      failures.add(name + " exceeded (margin = " + String.format("%.1f", margin * 100) + " %)");
    } else if (margin < WARNING_THRESHOLD) {
      warnings.add(name + " near limit (margin = " + String.format("%.1f", margin * 100) + " %)");
    }
  }
}
