package neqsim.process.equipment.compressor;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Screening-level reciprocating compressor performance calculator following API 618 conventions.
 *
 * <p>
 * Computes single-stage volumetric efficiency, actual inlet capacity, discharge temperature and (optionally) the
 * rod-load utilisation for a double-acting reciprocating compressor cylinder. These are preliminary-design screening
 * calculations; detailed performance and pulsation studies (API 618 Design Approach 2/3) require vendor cylinder data
 * and acoustic simulation.
 * </p>
 *
 * <p>
 * <b>Volumetric efficiency</b> (API 618 form):
 * </p>
 *
 * <pre>
 * VE = 0.97 - c * (r ^ (1 / n) - 1) - L
 * </pre>
 *
 * <p>
 * where {@code c} is the cylinder clearance fraction, {@code r} the pressure ratio, {@code n} the polytropic exponent
 * and {@code L} a slip/loss allowance.
 * </p>
 *
 * <p>
 * <b>Discharge temperature:</b> {@code T2 = T1 * r^((n-1)/n)}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ReciprocatingCompressorPerformance implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Reference volumetric-efficiency intercept per API 618 screening form. */
  private static final double VE_INTERCEPT = 0.97;
  /** Default slip/loss allowance for the volumetric-efficiency screening form. */
  private static final double DEFAULT_SLIP_LOSS = 0.03;
  /** Default rod-load alarm threshold (fraction of allowable). */
  private static final double DEFAULT_ROD_LOAD_LIMIT = 1.0;
  /** Typical discharge-temperature screening alarm, K (135 degC). */
  private static final double DISCHARGE_TEMPERATURE_ALARM_K = 408.15;
  /** Volumetric-efficiency screening alarm (fraction). */
  private static final double LOW_VE_ALARM = 0.70;

  // Inputs
  private String name = "reciprocating compressor";
  private double suctionPressure = Double.NaN;
  private double dischargePressure = Double.NaN;
  private double suctionTemperature = Double.NaN;
  private double clearanceFraction = 0.12;
  private double polytropicExponent = 1.3;
  private double slipLossFraction = DEFAULT_SLIP_LOSS;
  private double displacementRate = Double.NaN;

  // Optional rod-load inputs
  private boolean rodLoadEnabled = false;
  private double pistonArea = Double.NaN;
  private double rodArea = Double.NaN;
  private double allowableRodLoad = Double.NaN;

  // Outputs
  private double pressureRatio = Double.NaN;
  private double volumetricEfficiency = Double.NaN;
  private double actualInletCapacity = Double.NaN;
  private double dischargeTemperature = Double.NaN;
  private double compressionRodLoad = Double.NaN;
  private double tensionRodLoad = Double.NaN;
  private double rodLoadUtilization = Double.NaN;
  private boolean calculated = false;
  private final List<String> warnings = new ArrayList<String>();

  /** Creates a reciprocating compressor performance calculator with default settings. */
  public ReciprocatingCompressorPerformance() {
  }

  /**
   * Creates a reciprocating compressor performance calculator with a name.
   *
   * @param name calculator/cylinder identifier
   */
  public ReciprocatingCompressorPerformance(String name) {
    this.name = name;
  }

  /**
   * Sets the suction and discharge pressures.
   *
   * @param suctionPressure suction pressure, bara (must be &gt; 0)
   * @param dischargePressure discharge pressure, bara (must be &gt; suctionPressure)
   * @return this calculator
   */
  public ReciprocatingCompressorPerformance setPressures(double suctionPressure, double dischargePressure) {
    this.suctionPressure = suctionPressure;
    this.dischargePressure = dischargePressure;
    this.calculated = false;
    return this;
  }

  /**
   * Sets the suction temperature.
   *
   * @param suctionTemperature suction temperature, K (must be &gt; 0)
   * @return this calculator
   */
  public ReciprocatingCompressorPerformance setSuctionTemperature(double suctionTemperature) {
    this.suctionTemperature = suctionTemperature;
    this.calculated = false;
    return this;
  }

  /**
   * Sets the cylinder clearance fraction.
   *
   * @param clearanceFraction clearance volume fraction (typically 0.08 - 0.25)
   * @return this calculator
   */
  public ReciprocatingCompressorPerformance setClearanceFraction(double clearanceFraction) {
    this.clearanceFraction = clearanceFraction;
    this.calculated = false;
    return this;
  }

  /**
   * Sets the polytropic (compression) exponent.
   *
   * @param polytropicExponent polytropic exponent n (must be &gt; 1)
   * @return this calculator
   */
  public ReciprocatingCompressorPerformance setPolytropicExponent(double polytropicExponent) {
    this.polytropicExponent = polytropicExponent;
    this.calculated = false;
    return this;
  }

  /**
   * Sets the slip/loss allowance for the volumetric-efficiency screening form.
   *
   * @param slipLossFraction slip/loss fraction (typically 0.02 - 0.05)
   * @return this calculator
   */
  public ReciprocatingCompressorPerformance setSlipLossFraction(double slipLossFraction) {
    this.slipLossFraction = slipLossFraction;
    this.calculated = false;
    return this;
  }

  /**
   * Sets the cylinder swept-volume displacement rate.
   *
   * @param displacementRate swept-volume displacement rate, m3/s (must be &gt; 0)
   * @return this calculator
   */
  public ReciprocatingCompressorPerformance setDisplacementRate(double displacementRate) {
    this.displacementRate = displacementRate;
    this.calculated = false;
    return this;
  }

  /**
   * Enables and configures the gas rod-load screening for a double-acting cylinder.
   *
   * @param pistonArea piston area, m2 (must be &gt; 0)
   * @param rodArea piston-rod cross-sectional area, m2 (must be &ge; 0 and &lt; pistonArea)
   * @param allowableRodLoad continuous allowable rod load, N (must be &gt; 0)
   * @return this calculator
   */
  public ReciprocatingCompressorPerformance setRodLoad(double pistonArea, double rodArea, double allowableRodLoad) {
    this.rodLoadEnabled = true;
    this.pistonArea = pistonArea;
    this.rodArea = rodArea;
    this.allowableRodLoad = allowableRodLoad;
    this.calculated = false;
    return this;
  }

  /**
   * Validates the inputs and computes the screening outputs.
   *
   * @return this calculator
   */
  public ReciprocatingCompressorPerformance calculate() {
    validateSetup();
    warnings.clear();

    pressureRatio = dischargePressure / suctionPressure;
    volumetricEfficiency = VE_INTERCEPT - clearanceFraction * (Math.pow(pressureRatio, 1.0 / polytropicExponent) - 1.0)
	- slipLossFraction;
    if (volumetricEfficiency < 0.0) {
      volumetricEfficiency = 0.0;
    }
    actualInletCapacity = displacementRate * volumetricEfficiency;
    dischargeTemperature = suctionTemperature
	* Math.pow(pressureRatio, (polytropicExponent - 1.0) / polytropicExponent);

    if (rodLoadEnabled) {
      double suctionPa = suctionPressure * 1.0e5;
      double dischargePa = dischargePressure * 1.0e5;
      // Compression (head-end loaded): discharge acts on full piston, suction on crank-end annulus.
      compressionRodLoad = dischargePa * pistonArea - suctionPa * (pistonArea - rodArea);
      // Tension (crank-end loaded): discharge on annulus, suction on full piston.
      tensionRodLoad = dischargePa * (pistonArea - rodArea) - suctionPa * pistonArea;
      double peakRodLoad = Math.max(Math.abs(compressionRodLoad), Math.abs(tensionRodLoad));
      rodLoadUtilization = peakRodLoad / allowableRodLoad;
      if (rodLoadUtilization > DEFAULT_ROD_LOAD_LIMIT) {
	warnings.add("Peak gas rod load exceeds the allowable rod load (utilisation "
	    + String.format("%.2f", rodLoadUtilization) + ").");
      }
    }

    if (dischargeTemperature > DISCHARGE_TEMPERATURE_ALARM_K) {
      warnings.add("Discharge temperature " + String.format("%.1f", dischargeTemperature - 273.15)
	  + " degC exceeds the screening alarm (135 degC); consider staging.");
    }
    if (volumetricEfficiency < LOW_VE_ALARM) {
      warnings.add("Volumetric efficiency " + String.format("%.2f", volumetricEfficiency)
	  + " is low; review clearance and pressure ratio.");
    }

    calculated = true;
    return this;
  }

  /**
   * Validates the configured inputs prior to calculation.
   *
   * @throws IllegalStateException if any required input is missing or out of range
   */
  public void validateSetup() {
    if (!(suctionPressure > 0.0)) {
      throw new IllegalStateException("suctionPressure must be set and positive");
    }
    if (!(dischargePressure > suctionPressure)) {
      throw new IllegalStateException("dischargePressure must be greater than suctionPressure");
    }
    if (!(suctionTemperature > 0.0)) {
      throw new IllegalStateException("suctionTemperature must be set and positive");
    }
    if (!(polytropicExponent > 1.0)) {
      throw new IllegalStateException("polytropicExponent must be greater than 1");
    }
    if (clearanceFraction < 0.0) {
      throw new IllegalStateException("clearanceFraction must be non-negative");
    }
    if (!(displacementRate > 0.0)) {
      throw new IllegalStateException("displacementRate must be set and positive");
    }
    if (rodLoadEnabled) {
      if (!(pistonArea > 0.0)) {
	throw new IllegalStateException("pistonArea must be positive");
      }
      if (rodArea < 0.0 || rodArea >= pistonArea) {
	throw new IllegalStateException("rodArea must be in [0, pistonArea)");
      }
      if (!(allowableRodLoad > 0.0)) {
	throw new IllegalStateException("allowableRodLoad must be positive");
      }
    }
  }

  private void requireCalculated() {
    if (!calculated) {
      throw new IllegalStateException("calculate() must be called before reading results");
    }
  }

  /**
   * Gets the pressure ratio.
   *
   * @return pressure ratio (discharge/suction)
   */
  public double getPressureRatio() {
    requireCalculated();
    return pressureRatio;
  }

  /**
   * Gets the volumetric efficiency.
   *
   * @return volumetric efficiency (fraction)
   */
  public double getVolumetricEfficiency() {
    requireCalculated();
    return volumetricEfficiency;
  }

  /**
   * Gets the actual inlet capacity.
   *
   * @return actual inlet capacity, m3/s
   */
  public double getActualInletCapacity() {
    requireCalculated();
    return actualInletCapacity;
  }

  /**
   * Gets the discharge temperature.
   *
   * @return discharge temperature, K
   */
  public double getDischargeTemperature() {
    requireCalculated();
    return dischargeTemperature;
  }

  /**
   * Gets the peak compression rod load.
   *
   * @return compression rod load, N (NaN if rod-load screening is disabled)
   */
  public double getCompressionRodLoad() {
    requireCalculated();
    return compressionRodLoad;
  }

  /**
   * Gets the peak tension rod load.
   *
   * @return tension rod load, N (NaN if rod-load screening is disabled)
   */
  public double getTensionRodLoad() {
    requireCalculated();
    return tensionRodLoad;
  }

  /**
   * Gets the rod-load utilisation (peak gas rod load divided by the allowable rod load).
   *
   * @return rod-load utilisation (fraction, NaN if rod-load screening is disabled)
   */
  public double getRodLoadUtilization() {
    requireCalculated();
    return rodLoadUtilization;
  }

  /**
   * Gets the screening warnings raised during the last calculation.
   *
   * @return warnings (defensive copy)
   */
  public List<String> getWarnings() {
    return new ArrayList<String>(warnings);
  }

  /**
   * Gets the calculator name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Serializes the screening result to pretty-printed JSON.
   *
   * @return result as pretty JSON
   */
  public String toJson() {
    requireCalculated();
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
