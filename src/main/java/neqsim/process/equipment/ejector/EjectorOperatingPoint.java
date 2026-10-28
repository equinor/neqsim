package neqsim.process.equipment.ejector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operating point of a fixed-geometry ejector as calculated by {@link EjectorFixedGeometrySolver}.
 *
 * <p>
 * All flows are mass flows in kg/s and all pressures are in bara. The suction flow is a result of the calculation (the
 * entrainment capacity of the fixed geometry at the given conditions), not an input.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class EjectorOperatingPoint implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Flow regime of a fixed-geometry ejector.
   *
   * @author esol
   * @version 1.0
   */
  public enum Regime {
    /** Back pressure at or below the critical back pressure: double-choked, maximum entrainment. */
    CRITICAL,
    /** Back pressure above the critical value: entrainment is reduced. */
    SUBCRITICAL,
    /** Back pressure above what the motive can recover at any entrainment: entrainment has collapsed. */
    BREAKDOWN
  }

  private Regime regime = Regime.CRITICAL;
  private double motiveFlow;
  private double suctionFlow;
  private double motivePressure;
  private double suctionPressure;
  private double dischargePressure;
  private double mixingPressure;
  private double throatPressure;
  private double entrainmentRatio;
  private double compressionRatio;
  private double expansionRatio;
  private double criticalBackPressure;
  private double breakdownMargin;
  private double areaRatio;
  private double throatArea;
  private double mixingArea;
  private boolean motiveChoked;
  private double dischargeTemperatureC = Double.NaN;
  private double mixedMolarMass = Double.NaN;
  private double nozzleExitTemperatureC = Double.NaN;
  private double mixingTemperatureC = Double.NaN;
  private double nozzleVaporFraction = Double.NaN;
  private double mixingMach = Double.NaN;
  private double nozzleExitVelocity = Double.NaN;
  private double suctionVelocity = Double.NaN;
  private double mixingVelocity = Double.NaN;
  private final List<String> warnings = new ArrayList<String>();

  /**
   * Gets the flow regime.
   *
   * @return the regime
   */
  public Regime getRegime() {
    return regime;
  }

  /**
   * Sets the flow regime.
   *
   * @param regime the regime
   */
  void setRegime(Regime regime) {
    this.regime = regime;
  }

  /**
   * Returns true when entrainment has collapsed because the back pressure exceeds what the motive flow can recover.
   *
   * @return true if the ejector is in breakdown
   */
  public boolean isInBreakdown() {
    return regime == Regime.BREAKDOWN;
  }

  /**
   * Returns true when the back pressure is above the critical back pressure of the fixed geometry (sub-critical or
   * breakdown), i.e. the entrainment is lower than the critical capacity.
   *
   * @return true if the back pressure is above the critical value
   */
  public boolean isBackPressureAboveCritical() {
    return regime != Regime.CRITICAL;
  }

  /**
   * Gets the motive mass flow demanded by the nozzle (choked flow of the throat).
   *
   * @return motive flow in kg/s
   */
  public double getMotiveFlow() {
    return motiveFlow;
  }

  /**
   * Sets the motive flow.
   *
   * @param value motive flow in kg/s
   */
  void setMotiveFlow(double value) {
    this.motiveFlow = value;
  }

  /**
   * Gets the entrained suction mass flow (entrainment capacity at the given conditions).
   *
   * @return suction flow in kg/s
   */
  public double getSuctionFlow() {
    return suctionFlow;
  }

  /**
   * Sets the suction flow.
   *
   * @param value suction flow in kg/s
   */
  void setSuctionFlow(double value) {
    this.suctionFlow = value;
  }

  /**
   * Gets the motive pressure.
   *
   * @return pressure in bara
   */
  public double getMotivePressure() {
    return motivePressure;
  }

  /**
   * Sets the motive pressure.
   *
   * @param value pressure in bara
   */
  void setMotivePressure(double value) {
    this.motivePressure = value;
  }

  /**
   * Gets the suction pressure.
   *
   * @return pressure in bara
   */
  public double getSuctionPressure() {
    return suctionPressure;
  }

  /**
   * Sets the suction pressure.
   *
   * @param value pressure in bara
   */
  void setSuctionPressure(double value) {
    this.suctionPressure = value;
  }

  /**
   * Gets the discharge (back) pressure.
   *
   * @return pressure in bara
   */
  public double getDischargePressure() {
    return dischargePressure;
  }

  /**
   * Sets the discharge pressure.
   *
   * @param value pressure in bara
   */
  void setDischargePressure(double value) {
    this.dischargePressure = value;
  }

  /**
   * Gets the static pressure in the mixing section.
   *
   * @return pressure in bara
   */
  public double getMixingPressure() {
    return mixingPressure;
  }

  /**
   * Sets the mixing pressure.
   *
   * @param value pressure in bara
   */
  void setMixingPressure(double value) {
    this.mixingPressure = value;
  }

  /**
   * Gets the static pressure at the nozzle throat (choked).
   *
   * @return pressure in bara
   */
  public double getThroatPressure() {
    return throatPressure;
  }

  /**
   * Sets the throat pressure.
   *
   * @param value pressure in bara
   */
  void setThroatPressure(double value) {
    this.throatPressure = value;
  }

  /**
   * Gets the entrainment ratio (suction flow / motive flow).
   *
   * @return entrainment ratio
   */
  public double getEntrainmentRatio() {
    return entrainmentRatio;
  }

  /**
   * Sets the entrainment ratio.
   *
   * @param value entrainment ratio
   */
  void setEntrainmentRatio(double value) {
    this.entrainmentRatio = value;
  }

  /**
   * Gets the compression ratio (discharge / suction pressure).
   *
   * @return compression ratio
   */
  public double getCompressionRatio() {
    return compressionRatio;
  }

  /**
   * Sets the compression ratio.
   *
   * @param value compression ratio
   */
  void setCompressionRatio(double value) {
    this.compressionRatio = value;
  }

  /**
   * Gets the expansion ratio (motive / discharge pressure).
   *
   * @return expansion ratio
   */
  public double getExpansionRatio() {
    return expansionRatio;
  }

  /**
   * Sets the expansion ratio.
   *
   * @param value expansion ratio
   */
  void setExpansionRatio(double value) {
    this.expansionRatio = value;
  }

  /**
   * Gets the critical back pressure of the fixed geometry at the full motive pressure.
   *
   * @return critical back pressure in bara
   */
  public double getCriticalBackPressure() {
    return criticalBackPressure;
  }

  /**
   * Sets the critical back pressure.
   *
   * @param value pressure in bara
   */
  void setCriticalBackPressure(double value) {
    this.criticalBackPressure = value;
  }

  /**
   * Gets the margin to breakdown, critical back pressure / discharge pressure minus one. Negative when the back
   * pressure is above the critical value.
   *
   * @return relative margin (dimensionless)
   */
  public double getBreakdownMargin() {
    return breakdownMargin;
  }

  /**
   * Sets the breakdown margin.
   *
   * @param value relative margin
   */
  void setBreakdownMargin(double value) {
    this.breakdownMargin = value;
  }

  /**
   * Gets the area ratio of the geometry (mixing section / nozzle throat).
   *
   * @return area ratio
   */
  public double getAreaRatio() {
    return areaRatio;
  }

  /**
   * Gets the nozzle throat area.
   *
   * @return area in m2
   */
  public double getThroatArea() {
    return throatArea;
  }

  /**
   * Gets the mixing section area.
   *
   * @return area in m2
   */
  public double getMixingArea() {
    return mixingArea;
  }

  /**
   * Sets the geometry areas.
   *
   * @param throat nozzle throat area in m2
   * @param mixing mixing section area in m2
   */
  void setAreas(double throat, double mixing) {
    this.throatArea = throat;
    this.mixingArea = mixing;
    this.areaRatio = throat > 0.0 ? mixing / throat : 0.0;
  }

  /**
   * Returns whether the motive nozzle is choked.
   *
   * @return true if choked
   */
  public boolean isMotiveChoked() {
    return motiveChoked;
  }

  /**
   * Sets the motive choked flag.
   *
   * @param value choked flag
   */
  void setMotiveChoked(boolean value) {
    this.motiveChoked = value;
  }

  /**
   * Gets the discharge temperature of the mixed stream.
   *
   * @return temperature in degC
   */
  public double getDischargeTemperatureC() {
    return dischargeTemperatureC;
  }

  /**
   * Sets the discharge temperature.
   *
   * @param value temperature in degC
   */
  void setDischargeTemperatureC(double value) {
    this.dischargeTemperatureC = value;
  }

  /**
   * Gets the molar mass of the mixed stream.
   *
   * @return molar mass in g/mol
   */
  public double getMixedMolarMass() {
    return mixedMolarMass;
  }

  /**
   * Sets the mixed molar mass.
   *
   * @param value molar mass in g/mol
   */
  void setMixedMolarMass(double value) {
    this.mixedMolarMass = value;
  }

  /**
   * Gets the temperature at the nozzle exit.
   *
   * @return temperature in degC
   */
  public double getNozzleExitTemperatureC() {
    return nozzleExitTemperatureC;
  }

  /**
   * Sets the nozzle exit temperature.
   *
   * @param value temperature in degC
   */
  void setNozzleExitTemperatureC(double value) {
    this.nozzleExitTemperatureC = value;
  }

  /**
   * Gets the static temperature in the mixing section.
   *
   * @return temperature in degC
   */
  public double getMixingTemperatureC() {
    return mixingTemperatureC;
  }

  /**
   * Sets the mixing section temperature.
   *
   * @param value temperature in degC
   */
  void setMixingTemperatureC(double value) {
    this.mixingTemperatureC = value;
  }

  /**
   * Gets the vapour fraction at the nozzle exit (below one means condensation in the nozzle).
   *
   * @return vapour (mole) fraction
   */
  public double getNozzleVaporFraction() {
    return nozzleVaporFraction;
  }

  /**
   * Sets the nozzle exit vapour fraction.
   *
   * @param value vapour fraction
   */
  void setNozzleVaporFraction(double value) {
    this.nozzleVaporFraction = value;
  }

  /**
   * Gets the Mach number of the mixed stream in the mixing section.
   *
   * @return Mach number or NaN
   */
  public double getMixingMach() {
    return mixingMach;
  }

  /**
   * Sets the mixing Mach number.
   *
   * @param value Mach number
   */
  void setMixingMach(double value) {
    this.mixingMach = value;
  }

  /**
   * Gets the velocity of the primary jet at the mixing pressure.
   *
   * @return velocity in m/s
   */
  public double getNozzleExitVelocity() {
    return nozzleExitVelocity;
  }

  /**
   * Gets the velocity of the entrained stream at the mixing pressure.
   *
   * @return velocity in m/s
   */
  public double getSuctionVelocity() {
    return suctionVelocity;
  }

  /**
   * Gets the velocity of the mixed stream after momentum mixing.
   *
   * @return velocity in m/s
   */
  public double getMixingVelocity() {
    return mixingVelocity;
  }

  /**
   * Sets the velocities of the mixing section.
   *
   * @param nozzle primary jet velocity in m/s
   * @param suction entrained stream velocity in m/s
   * @param mixed mixed stream velocity in m/s
   */
  void setVelocities(double nozzle, double suction, double mixed) {
    this.nozzleExitVelocity = nozzle;
    this.suctionVelocity = suction;
    this.mixingVelocity = mixed;
  }

  /**
   * Gets the warnings collected during the calculation.
   *
   * @return list of warnings
   */
  public List<String> getWarnings() {
    return warnings;
  }

  /**
   * Returns the operating point as an ordered map for reports and JSON output.
   *
   * @return map of result names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<String, Object>();
    m.put("regime", regime.name());
    m.put("isInBreakdown", isInBreakdown());
    m.put("backPressureAboveCritical", isBackPressureAboveCritical());
    m.put("motiveFlow_kg_s", motiveFlow);
    m.put("suctionFlow_kg_s", suctionFlow);
    m.put("entrainmentRatio", entrainmentRatio);
    m.put("motivePressure_bara", motivePressure);
    m.put("suctionPressure_bara", suctionPressure);
    m.put("dischargePressure_bara", dischargePressure);
    m.put("mixingPressure_bara", mixingPressure);
    m.put("compressionRatio", compressionRatio);
    m.put("expansionRatio", expansionRatio);
    m.put("criticalBackPressure_bara", criticalBackPressure);
    m.put("breakdownMargin", breakdownMargin);
    m.put("areaRatio", areaRatio);
    m.put("throatArea_m2", throatArea);
    m.put("mixingArea_m2", mixingArea);
    m.put("motiveChoked", motiveChoked);
    m.put("dischargeTemperature_C", dischargeTemperatureC);
    m.put("mixedMolarMass_g_mol", mixedMolarMass);
    m.put("nozzleExitTemperature_C", nozzleExitTemperatureC);
    m.put("nozzleVaporFraction", nozzleVaporFraction);
    m.put("warnings", new ArrayList<String>(warnings));
    return m;
  }
}
