package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Screening-level advanced anti-surge control utilities for centrifugal compressor studies.
 *
 * <p>
 * This class collects the public, non-vendor-certified anti-surge methods that are commonly described in open
 * literature: reduced-coordinate surge comparison, transmitter validation and voting, recycle-valve/piping-volume
 * screening, dual hot/cold recycle valve command splitting, simple fault-tolerant override logic, heuristic fuzzy and
 * predictive/MPC-style valve demand, and a lumped educational surge-oscillation model.
 * </p>
 *
 * <p>
 * The calculations are intended for simulation, teaching, screening, and controller-logic regression tests. They are
 * not a replacement for compressor vendor anti-surge controllers, site safety instrumented functions, certified machine
 * protection systems, or project-specific sizing calculations.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AdvancedAntiSurgeControlSystem implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Small number used to avoid division by zero. */
  private static final double EPS = 1.0e-12;

  /** Normal gravity used for head conversion. */
  private static final double GRAVITY = 9.80665;

  /** Anti-surge controller algorithm selection. */
  public enum ControlAlgorithm {
    /** Classical proportional-integral recycle-valve control on margin error. */
    PI,
    /** PI control using the lower of measured and predicted margin. */
    PREDICTIVE_PI,
    /** Rule-based fuzzy-style recycle demand for low margin and fast approach. */
    FUZZY_SCREENING,
    /**
     * One-step screening MPC that chooses the smallest valve command that restores predicted margin.
     */
    MPC_SCREENING
  }

  /** Sensor fault types supported by the embedded instrumentation model. */
  public enum SensorFault {
    /** No sensor fault. */
    NONE,
    /** Sensor output is stuck at the configured fault parameter. */
    STUCK,
    /** Constant additive bias. */
    BIAS,
    /** Linear drift with rate equal to the configured fault parameter per second. */
    DRIFT,
    /** Force invalid/out-of-range status. */
    INVALID
  }

  /** Voting rule for redundant transmitter signals. */
  public enum VotingMode {
    /** Median of valid signals. */
    MEDIAN,
    /** Arithmetic average of valid signals. */
    AVERAGE,
    /** Lowest valid signal. */
    SELECT_LOW,
    /** Highest valid signal. */
    SELECT_HIGH
  }

  /** Certification status for the implemented public screening methods. */
  public enum CertificationStatus {
    /** Public engineering-screening implementation, not a certified vendor package. */
    ENGINEERING_SCREENING,
    /** Explicitly not vendor certified. */
    NOT_VENDOR_CERTIFIED
  }

  /** Algorithm used for valve split in the dual recycle architecture. */
  public enum RecycleValveRole {
    /** Fast hot-gas recycle path for emergency opening. */
    HOT_FAST,
    /** Normal cooled recycle path for sustained anti-surge operation. */
    COLD_NORMAL
  }

  /** Controller algorithm. */
  private ControlAlgorithm algorithm = ControlAlgorithm.PREDICTIVE_PI;

  /** Surge-control margin set point in dimensionless margin units. */
  private double marginSetPoint = 0.10;

  /** Proportional gain in valve percent per unit margin error. */
  private double proportionalGain = 350.0;

  /** Integral time in seconds; zero disables integral action. */
  private double integralTime = 20.0;

  /** Integral state in valve percent. */
  private double integralState = 0.0;

  /** Valve demand lower bound in percent. */
  private double minOpening = 0.0;

  /** Valve demand upper bound in percent. */
  private double maxOpening = 100.0;

  /** Prediction horizon for screening MPC/predictive control. */
  private double predictionHorizon = 2.0;

  /** Estimated margin improvement per one percent recycle valve opening. */
  private double marginGainPerValvePercent = 0.003;

  /** Margin below which the hot recycle valve starts to open. */
  private double hotValveMargin = 0.03;

  /** Approach rate threshold in margin units per second for fast hot-valve opening. */
  private double fastApproachRate = -0.03;

  /** Maximum disagreement among voted transmitters before degradation is reported. */
  private double votingDeviationLimit = 0.05;

  /** Default constructor. */
  public AdvancedAntiSurgeControlSystem() {
  }

  /**
   * Calculate reduced head and reduced-flow coordinates from process measurements.
   *
   * <p>
   * The reduced head follows the common anti-surge literature form {@code ((Pd / Ps)^sigma - 1) / sigma}. The reduced
   * flow follows the pressure-normalized flowmeter signal {@code dPflow / Ps}. These coordinates are useful for
   * composition-robust surge-line representation when the same units are used consistently for pressure and flowmeter
   * differential pressure.
   * </p>
   *
   * @param suctionPressure suction pressure in any absolute pressure unit, must be positive
   * @param dischargePressure discharge pressure in the same absolute pressure unit, must be positive
   * @param suctionTemperature suction temperature in Kelvin, must be positive
   * @param dischargeTemperature discharge temperature in Kelvin, must be positive
   * @param flowMeterDeltaPressure flowmeter differential pressure in the same pressure unit as suction pressure
   * @return reduced coordinate point
   * @throws IllegalArgumentException if an input is non-finite or physically invalid
   */
  public static ReducedCoordinatePoint calculateReducedCoordinates(double suctionPressure, double dischargePressure,
      double suctionTemperature, double dischargeTemperature, double flowMeterDeltaPressure) {
    validatePositive("suctionPressure", suctionPressure);
    validatePositive("dischargePressure", dischargePressure);
    validatePositive("suctionTemperature", suctionTemperature);
    validatePositive("dischargeTemperature", dischargeTemperature);
    validateFinite("flowMeterDeltaPressure", flowMeterDeltaPressure);
    if (flowMeterDeltaPressure < 0.0) {
      throw new IllegalArgumentException("flowMeterDeltaPressure must be non-negative");
    }

    double pressureRatio = dischargePressure / suctionPressure;
    double temperatureRatio = dischargeTemperature / suctionTemperature;
    double sigma = Math.log(Math.max(temperatureRatio, EPS)) / Math.log(Math.max(pressureRatio, 1.0 + EPS));
    if (!Double.isFinite(sigma) || Math.abs(sigma) < 1.0e-6) {
      sigma = 1.0;
    }
    double reducedHead = (Math.pow(pressureRatio, sigma) - 1.0) / sigma;
    double reducedFlow = flowMeterDeltaPressure / suctionPressure;
    return new ReducedCoordinatePoint(pressureRatio, sigma, reducedHead, reducedFlow);
  }

  /**
   * Convert compressor head and flow to fan-law invariant coordinates.
   *
   * @param flow actual inlet flow in m3/hr or any consistent volumetric unit
   * @param head compressor head in kJ/kg or any consistent head unit
   * @param speed compressor speed in rpm, must be positive
   * @return invariant map point where reduced flow is {@code flow / speed} and reduced head is {@code head / speed^2}
   * @throws IllegalArgumentException if speed is non-positive or any value is non-finite
   */
  public static InvariantMapPoint calculateInvariantMapPoint(double flow, double head, double speed) {
    validateFinite("flow", flow);
    validateFinite("head", head);
    validatePositive("speed", speed);
    return new InvariantMapPoint(flow / speed, head / (speed * speed));
  }

  /**
   * Calculate distance from a reduced-flow point to a reduced surge-control line.
   *
   * @param reducedFlow current reduced flow
   * @param surgeReducedFlow reduced flow at the physical surge line
   * @param controlMargin fractional control margin above the surge line, e.g. 0.10 for 10%
   * @return dimensionless distance to the surge-control line, positive on the safe side
   */
  public static double distanceToReducedControlLine(double reducedFlow, double surgeReducedFlow, double controlMargin) {
    validateFinite("reducedFlow", reducedFlow);
    validatePositive("surgeReducedFlow", surgeReducedFlow);
    validateFinite("controlMargin", controlMargin);
    double controlFlow = surgeReducedFlow * (1.0 + Math.max(controlMargin, 0.0));
    return reducedFlow / controlFlow - 1.0;
  }

  /**
   * Calculate a recycle valve command from measured margin and margin approach rate.
   *
   * @param measuredMargin measured distance to surge
   * @param marginRate rate of change of margin in margin units per second
   * @param dt timestep in seconds
   * @return valve command in percent opening
   */
  public double calculateValveCommand(double measuredMargin, double marginRate, double dt) {
    validateFinite("measuredMargin", measuredMargin);
    validateFinite("marginRate", marginRate);
    validatePositive("dt", dt);
    double predictedMargin = measuredMargin + marginRate * predictionHorizon;
    double controlMargin = algorithm == ControlAlgorithm.PREDICTIVE_PI ? Math.min(measuredMargin, predictedMargin)
        : measuredMargin;
    if (algorithm == ControlAlgorithm.FUZZY_SCREENING) {
      return fuzzyValveDemand(measuredMargin, marginRate);
    }
    if (algorithm == ControlAlgorithm.MPC_SCREENING) {
      return mpcValveDemand(measuredMargin, marginRate);
    }
    double error = marginSetPoint - controlMargin;
    if (integralTime > 0.0) {
      integralState += proportionalGain / integralTime * error * dt;
    }
    return clamp(proportionalGain * error + integralState, minOpening, maxOpening);
  }

  /**
   * Split total anti-surge demand between normal cold recycle and fast hot recycle valves.
   *
   * @param totalValveDemand total valve opening demand in percent
   * @param measuredMargin measured distance to surge
   * @param marginRate rate of change of margin in margin units per second
   * @return hot/cold valve command pair
   */
  public DualRecycleValveCommand splitDualRecycleCommand(double totalValveDemand, double measuredMargin,
      double marginRate) {
    validateFinite("totalValveDemand", totalValveDemand);
    validateFinite("measuredMargin", measuredMargin);
    validateFinite("marginRate", marginRate);
    double demand = clamp(totalValveDemand, minOpening, maxOpening);
    double hotOpening = 0.0;
    if (measuredMargin <= hotValveMargin || marginRate <= fastApproachRate) {
      double severity = Math.max((hotValveMargin - measuredMargin) / Math.max(hotValveMargin, EPS),
          (fastApproachRate - marginRate) / Math.max(Math.abs(fastApproachRate), EPS));
      hotOpening = clamp(30.0 + 70.0 * severity, 0.0, demand);
    }
    double coldOpening = clamp(demand - hotOpening, 0.0, maxOpening);
    return new DualRecycleValveCommand(hotOpening, coldOpening);
  }

  /**
   * Evaluate redundant anti-surge transmitter signals and calculate a fault-tolerant valve command.
   *
   * @param signals transmitter signals to validate and vote
   * @param mode voting mode
   * @param marginRate voted margin rate in margin units per second
   * @param dt timestep in seconds
   * @return fault-tolerant controller decision
   */
  public FaultTolerantDecision evaluateFaultTolerant(List<InstrumentSignal> signals, VotingMode mode, double marginRate,
      double dt) {
    VotingResult voted = vote(signals, mode, votingDeviationLimit);
    if (!voted.isValid()) {
      return new FaultTolerantDecision(false, true, maxOpening, Double.NaN, "No valid surge-margin transmitter vote");
    }
    double opening = calculateValveCommand(voted.getValue(), marginRate, dt);
    boolean fallback = voted.isDegraded();
    if (fallback) {
      opening = Math.max(opening, 50.0);
    }
    return new FaultTolerantDecision(true, fallback, opening, voted.getValue(), voted.getMessage());
  }

  /**
   * Vote redundant transmitter readings after validation.
   *
   * @param signals transmitter signals
   * @param mode voting mode
   * @param maxDeviation maximum accepted spread before degraded status is reported
   * @return voting result
   */
  public static VotingResult vote(List<InstrumentSignal> signals, VotingMode mode, double maxDeviation) {
    if (signals == null || signals.isEmpty()) {
      return new VotingResult(false, true, Double.NaN, 0, "No signals supplied");
    }
    List<Double> validValues = new ArrayList<Double>();
    for (InstrumentSignal signal : signals) {
      if (signal != null && signal.isValid()) {
        validValues.add(Double.valueOf(signal.getValue()));
      }
    }
    if (validValues.isEmpty()) {
      return new VotingResult(false, true, Double.NaN, 0, "No valid signals after validation");
    }
    Collections.sort(validValues, new Comparator<Double>() {
      @Override
      public int compare(Double left, Double right) {
        return left.compareTo(right);
      }
    });
    double value;
    VotingMode selectedMode = mode == null ? VotingMode.MEDIAN : mode;
    if (selectedMode == VotingMode.AVERAGE) {
      value = average(validValues);
    } else if (selectedMode == VotingMode.SELECT_LOW) {
      value = validValues.get(0).doubleValue();
    } else if (selectedMode == VotingMode.SELECT_HIGH) {
      value = validValues.get(validValues.size() - 1).doubleValue();
    } else {
      value = median(validValues);
    }
    double spread = validValues.get(validValues.size() - 1).doubleValue() - validValues.get(0).doubleValue();
    boolean degraded = validValues.size() < signals.size() || spread > maxDeviation;
    String message = degraded ? "Voted signal is degraded by invalid signals or transmitter disagreement"
        : "Voted signal is healthy";
    return new VotingResult(true, degraded, value, validValues.size(), message);
  }

  /**
   * Size recycle flow and screen valve/piping volume response.
   *
   * @param inletFlow current compressor inlet volumetric flow in m3/hr
   * @param surgeFlow compressor surge volumetric flow in m3/hr
   * @param controlMargin fractional control margin above surge flow
   * @param suctionDensity suction gas density in kg/m3
   * @param valvePressureDrop pressure drop available across recycle valve in bar
   * @param pipingVolume suction/recycle volume to be filled in m3
   * @param requiredResponseTime required response time in seconds
   * @return recycle sizing result
   */
  public static RecycleSizingResult sizeRecycleSystem(double inletFlow, double surgeFlow, double controlMargin,
      double suctionDensity, double valvePressureDrop, double pipingVolume, double requiredResponseTime) {
    validateFinite("inletFlow", inletFlow);
    validatePositive("surgeFlow", surgeFlow);
    validatePositive("suctionDensity", suctionDensity);
    validatePositive("valvePressureDrop", valvePressureDrop);
    validateFinite("pipingVolume", pipingVolume);
    validatePositive("requiredResponseTime", requiredResponseTime);
    double controlFlow = surgeFlow * (1.0 + Math.max(controlMargin, 0.0));
    double requiredRecycleFlow = Math.max(controlFlow - inletFlow, 0.0);
    double requiredRecycleMassFlow = requiredRecycleFlow * suctionDensity;
    double valveCvScreening = requiredRecycleFlow / Math.sqrt(Math.max(valvePressureDrop / suctionDensity, EPS));
    double volumeResponseTime = pipingVolume <= 0.0 || requiredRecycleFlow <= 0.0 ? 0.0
        : 3600.0 * pipingVolume / requiredRecycleFlow;
    boolean volumeOk = volumeResponseTime <= requiredResponseTime;
    return new RecycleSizingResult(requiredRecycleFlow, requiredRecycleMassFlow, valveCvScreening, volumeResponseTime,
        volumeOk);
  }

  /**
   * Step a lumped educational surge-oscillation model.
   *
   * <p>
   * This is a small Greitzer-inspired two-state oscillator for controller screening. It is intentionally not a rigorous
   * one-dimensional compressor/piping solver.
   * </p>
   *
   * @param state current model state
   * @param compressorPressureRise normalized compressor pressure-rise forcing
   * @param throttleDemand normalized downstream throttle demand
   * @param recycleFlow normalized recycle flow, increasing flow away from surge
   * @param dt timestep in seconds
   * @return next state
   */
  public static SurgeOscillationState stepSurgeOscillation(SurgeOscillationState state, double compressorPressureRise,
      double throttleDemand, double recycleFlow, double dt) {
    if (state == null) {
      throw new IllegalArgumentException("state can not be null");
    }
    validateFinite("compressorPressureRise", compressorPressureRise);
    validateFinite("throttleDemand", throttleDemand);
    validateFinite("recycleFlow", recycleFlow);
    validatePositive("dt", dt);
    double flowDerivative = compressorPressureRise - state.getPlenumPressure() - 0.25 * state.getFlow();
    double pressureDerivative = state.getFlow() + recycleFlow - throttleDemand;
    double nextFlow = state.getFlow() + dt * flowDerivative;
    double nextPressure = state.getPlenumPressure() + dt * pressureDerivative;
    boolean surgeCycle = state.getFlow() > 0.0 && nextFlow <= 0.0;
    int cycles = state.getSurgeCycleCount() + (surgeCycle ? 1 : 0);
    return new SurgeOscillationState(nextFlow, nextPressure, cycles);
  }

  /**
   * Get the certification status for these public screening methods.
   *
   * @return always {@link CertificationStatus#NOT_VENDOR_CERTIFIED}
   */
  public CertificationStatus getCertificationStatus() {
    return CertificationStatus.NOT_VENDOR_CERTIFIED;
  }

  /**
   * Set controller algorithm.
   *
   * @param algorithm controller algorithm; null selects predictive PI
   */
  public void setAlgorithm(ControlAlgorithm algorithm) {
    this.algorithm = algorithm == null ? ControlAlgorithm.PREDICTIVE_PI : algorithm;
  }

  /**
   * Get controller algorithm.
   *
   * @return selected algorithm
   */
  public ControlAlgorithm getAlgorithm() {
    return algorithm;
  }

  /**
   * Set margin set point.
   *
   * @param marginSetPoint dimensionless margin set point
   */
  public void setMarginSetPoint(double marginSetPoint) {
    validateFinite("marginSetPoint", marginSetPoint);
    this.marginSetPoint = marginSetPoint;
  }

  /**
   * Set PI tuning parameters.
   *
   * @param proportionalGain proportional gain in valve percent per unit margin error
   * @param integralTime integral time in seconds; zero disables integral action
   */
  public void setPiTuning(double proportionalGain, double integralTime) {
    validateFinite("proportionalGain", proportionalGain);
    validateFinite("integralTime", integralTime);
    this.proportionalGain = proportionalGain;
    this.integralTime = Math.max(integralTime, 0.0);
  }

  /**
   * Set prediction horizon.
   *
   * @param predictionHorizon prediction horizon in seconds
   */
  public void setPredictionHorizon(double predictionHorizon) {
    validateFinite("predictionHorizon", predictionHorizon);
    this.predictionHorizon = Math.max(predictionHorizon, 0.0);
  }

  /**
   * Set valve opening range.
   *
   * @param minOpening minimum opening in percent
   * @param maxOpening maximum opening in percent
   */
  public void setOpeningRange(double minOpening, double maxOpening) {
    validateFinite("minOpening", minOpening);
    validateFinite("maxOpening", maxOpening);
    this.minOpening = Math.min(minOpening, maxOpening);
    this.maxOpening = Math.max(minOpening, maxOpening);
  }

  /**
   * Set estimated margin gain per valve percent for screening MPC.
   *
   * @param marginGainPerValvePercent margin gain per percent opening
   */
  public void setMarginGainPerValvePercent(double marginGainPerValvePercent) {
    validatePositive("marginGainPerValvePercent", marginGainPerValvePercent);
    this.marginGainPerValvePercent = marginGainPerValvePercent;
  }

  /**
   * Set dual-valve activation limits.
   *
   * @param hotValveMargin margin below which hot recycle starts opening
   * @param fastApproachRate negative margin rate that triggers hot recycle opening
   */
  public void setDualValveActivation(double hotValveMargin, double fastApproachRate) {
    validateFinite("hotValveMargin", hotValveMargin);
    validateFinite("fastApproachRate", fastApproachRate);
    this.hotValveMargin = hotValveMargin;
    this.fastApproachRate = fastApproachRate;
  }

  /**
   * Set voting deviation limit.
   *
   * @param votingDeviationLimit maximum spread before degraded voting status
   */
  public void setVotingDeviationLimit(double votingDeviationLimit) {
    validateFinite("votingDeviationLimit", votingDeviationLimit);
    this.votingDeviationLimit = Math.max(votingDeviationLimit, 0.0);
  }

  /**
   * Reset integral state.
   */
  public void reset() {
    integralState = 0.0;
  }

  /**
   * Calculate rule-based fuzzy valve demand.
   *
   * @param measuredMargin measured margin
   * @param marginRate margin rate
   * @return valve demand in percent
   */
  private double fuzzyValveDemand(double measuredMargin, double marginRate) {
    double lowMargin = clamp((marginSetPoint - measuredMargin) / Math.max(marginSetPoint, EPS), 0.0, 1.0);
    double fastApproach = clamp(-marginRate / Math.max(Math.abs(fastApproachRate), EPS), 0.0, 1.0);
    double demand = 100.0 * Math.max(lowMargin, 0.7 * lowMargin + 0.3 * fastApproach);
    return clamp(demand, minOpening, maxOpening);
  }

  /**
   * Calculate one-step screening MPC valve demand.
   *
   * @param measuredMargin measured margin
   * @param marginRate margin rate
   * @return valve demand in percent
   */
  private double mpcValveDemand(double measuredMargin, double marginRate) {
    double predicted = measuredMargin + marginRate * predictionHorizon;
    double requiredMarginLift = Math.max(marginSetPoint - predicted, 0.0);
    double demand = requiredMarginLift / Math.max(marginGainPerValvePercent, EPS);
    return clamp(demand, minOpening, maxOpening);
  }

  /**
   * Validate finite input.
   *
   * @param name value name
   * @param value value
   * @throws IllegalArgumentException if value is not finite
   */
  private static void validateFinite(String name, double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /**
   * Validate positive finite input.
   *
   * @param name value name
   * @param value value
   * @throws IllegalArgumentException if value is not positive and finite
   */
  private static void validatePositive(String name, double value) {
    validateFinite(name, value);
    if (value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }

  /**
   * Clamp value to limits.
   *
   * @param value candidate value
   * @param low lower limit
   * @param high upper limit
   * @return clamped value
   */
  private static double clamp(double value, double low, double high) {
    return Math.max(low, Math.min(high, value));
  }

  /**
   * Calculate average.
   *
   * @param values values to average
   * @return average value
   */
  private static double average(List<Double> values) {
    double sum = 0.0;
    for (Double value : values) {
      sum += value.doubleValue();
    }
    return sum / values.size();
  }

  /**
   * Calculate median.
   *
   * @param values sorted values
   * @return median value
   */
  private static double median(List<Double> values) {
    int size = values.size();
    if (size % 2 == 1) {
      return values.get(size / 2).doubleValue();
    }
    return 0.5 * (values.get(size / 2 - 1).doubleValue() + values.get(size / 2).doubleValue());
  }

  /** Reduced anti-surge coordinate point. */
  public static class ReducedCoordinatePoint implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Pressure ratio. */
    private final double pressureRatio;
    /** Polytropic exponent estimate. */
    private final double sigma;
    /** Reduced head coordinate. */
    private final double reducedHead;
    /** Reduced flow coordinate. */
    private final double reducedFlow;

    /**
     * Constructor.
     *
     * @param pressureRatio pressure ratio
     * @param sigma polytropic exponent estimate
     * @param reducedHead reduced head
     * @param reducedFlow reduced flow
     */
    public ReducedCoordinatePoint(double pressureRatio, double sigma, double reducedHead, double reducedFlow) {
      this.pressureRatio = pressureRatio;
      this.sigma = sigma;
      this.reducedHead = reducedHead;
      this.reducedFlow = reducedFlow;
    }

    /**
     * Get pressure ratio.
     *
     * @return pressure ratio
     */
    public double getPressureRatio() {
      return pressureRatio;
    }

    /**
     * Get sigma.
     *
     * @return sigma
     */
    public double getSigma() {
      return sigma;
    }

    /**
     * Get reduced head.
     *
     * @return reduced head
     */
    public double getReducedHead() {
      return reducedHead;
    }

    /**
     * Get reduced flow.
     *
     * @return reduced flow
     */
    public double getReducedFlow() {
      return reducedFlow;
    }
  }

  /** Fan-law invariant map point. */
  public static class InvariantMapPoint implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Reduced flow. */
    private final double reducedFlow;
    /** Reduced head. */
    private final double reducedHead;

    /**
     * Constructor.
     *
     * @param reducedFlow reduced flow
     * @param reducedHead reduced head
     */
    public InvariantMapPoint(double reducedFlow, double reducedHead) {
      this.reducedFlow = reducedFlow;
      this.reducedHead = reducedHead;
    }

    /**
     * Get reduced flow.
     *
     * @return reduced flow
     */
    public double getReducedFlow() {
      return reducedFlow;
    }

    /**
     * Get reduced head.
     *
     * @return reduced head
     */
    public double getReducedHead() {
      return reducedHead;
    }
  }

  /** Validated transmitter signal. */
  public static class InstrumentSignal implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Signal name. */
    private final String name;
    /** Minimum valid range. */
    private double minimum;
    /** Maximum valid range. */
    private double maximum;
    /** First-order lag time constant. */
    private double lagTime;
    /** Current filtered value. */
    private double value;
    /** True when value has been initialized. */
    private boolean initialized = false;
    /** Fault type. */
    private SensorFault fault = SensorFault.NONE;
    /** Fault parameter. */
    private double faultParameter = 0.0;
    /** Drift accumulator. */
    private double drift = 0.0;
    /** Validity flag. */
    private boolean valid = false;

    /**
     * Constructor.
     *
     * @param name signal name
     * @param minimum minimum valid value
     * @param maximum maximum valid value
     * @param lagTime first-order lag time in seconds; zero disables lag
     */
    public InstrumentSignal(String name, double minimum, double maximum, double lagTime) {
      this.name = name;
      this.minimum = Math.min(minimum, maximum);
      this.maximum = Math.max(minimum, maximum);
      this.lagTime = Math.max(lagTime, 0.0);
    }

    /**
     * Update the signal from a raw process value.
     *
     * @param rawValue raw value
     * @param dt timestep in seconds
     * @return filtered and faulted value
     */
    public double update(double rawValue, double dt) {
      validateFinite("rawValue", rawValue);
      validatePositive("dt", dt);
      double faulted = applyFault(rawValue, dt);
      if (!initialized || lagTime <= 0.0) {
        value = faulted;
        initialized = true;
      } else {
        double alpha = dt / (lagTime + dt);
        value += alpha * (faulted - value);
      }
      valid = fault != SensorFault.INVALID && value >= minimum && value <= maximum && Double.isFinite(value);
      return value;
    }

    /**
     * Apply fault to raw value.
     *
     * @param rawValue raw value
     * @param dt timestep in seconds
     * @return faulted value
     */
    private double applyFault(double rawValue, double dt) {
      if (fault == SensorFault.STUCK) {
        return faultParameter;
      }
      if (fault == SensorFault.BIAS) {
        return rawValue + faultParameter;
      }
      if (fault == SensorFault.DRIFT) {
        drift += faultParameter * dt;
        return rawValue + drift;
      }
      if (fault == SensorFault.INVALID) {
        return rawValue;
      }
      return rawValue;
    }

    /**
     * Set a sensor fault.
     *
     * @param fault fault type
     * @param faultParameter fault parameter
     */
    public void setFault(SensorFault fault, double faultParameter) {
      this.fault = fault == null ? SensorFault.NONE : fault;
      this.faultParameter = faultParameter;
      this.drift = 0.0;
    }

    /** Clear fault. */
    public void clearFault() {
      setFault(SensorFault.NONE, 0.0);
    }

    /**
     * Get signal name.
     *
     * @return signal name
     */
    public String getName() {
      return name;
    }

    /**
     * Get signal value.
     *
     * @return signal value
     */
    public double getValue() {
      return value;
    }

    /**
     * Check whether signal is valid.
     *
     * @return true if valid
     */
    public boolean isValid() {
      return valid;
    }
  }

  /** Voting result for redundant instrumentation. */
  public static class VotingResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Valid vote flag. */
    private final boolean valid;
    /** Degraded vote flag. */
    private final boolean degraded;
    /** Voted value. */
    private final double value;
    /** Number of accepted signals. */
    private final int acceptedSignals;
    /** Message. */
    private final String message;

    /**
     * Constructor.
     *
     * @param valid valid flag
     * @param degraded degraded flag
     * @param value voted value
     * @param acceptedSignals number of accepted signals
     * @param message status message
     */
    public VotingResult(boolean valid, boolean degraded, double value, int acceptedSignals, String message) {
      this.valid = valid;
      this.degraded = degraded;
      this.value = value;
      this.acceptedSignals = acceptedSignals;
      this.message = message;
    }

    /**
     * Check if vote is valid.
     *
     * @return true if valid
     */
    public boolean isValid() {
      return valid;
    }

    /**
     * Check if vote is degraded.
     *
     * @return true if degraded
     */
    public boolean isDegraded() {
      return degraded;
    }

    /**
     * Get voted value.
     *
     * @return voted value
     */
    public double getValue() {
      return value;
    }

    /**
     * Get accepted signal count.
     *
     * @return accepted signal count
     */
    public int getAcceptedSignals() {
      return acceptedSignals;
    }

    /**
     * Get message.
     *
     * @return message
     */
    public String getMessage() {
      return message;
    }
  }

  /** Fault-tolerant anti-surge decision. */
  public static class FaultTolerantDecision implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Valid decision flag. */
    private final boolean valid;
    /** Fallback active flag. */
    private final boolean fallbackActive;
    /** Valve opening command in percent. */
    private final double valveOpening;
    /** Voted margin. */
    private final double votedMargin;
    /** Decision message. */
    private final String message;

    /**
     * Constructor.
     *
     * @param valid valid flag
     * @param fallbackActive fallback active flag
     * @param valveOpening valve opening in percent
     * @param votedMargin voted margin
     * @param message decision message
     */
    public FaultTolerantDecision(boolean valid, boolean fallbackActive, double valveOpening, double votedMargin,
        String message) {
      this.valid = valid;
      this.fallbackActive = fallbackActive;
      this.valveOpening = valveOpening;
      this.votedMargin = votedMargin;
      this.message = message;
    }

    /**
     * Check if decision is valid.
     *
     * @return true if valid
     */
    public boolean isValid() {
      return valid;
    }

    /**
     * Check if fallback is active.
     *
     * @return true if fallback is active
     */
    public boolean isFallbackActive() {
      return fallbackActive;
    }

    /**
     * Get valve opening.
     *
     * @return valve opening in percent
     */
    public double getValveOpening() {
      return valveOpening;
    }

    /**
     * Get voted margin.
     *
     * @return voted margin
     */
    public double getVotedMargin() {
      return votedMargin;
    }

    /**
     * Get message.
     *
     * @return message
     */
    public String getMessage() {
      return message;
    }
  }

  /** Dual recycle valve command. */
  public static class DualRecycleValveCommand implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Hot fast valve opening. */
    private final double hotValveOpening;
    /** Cold normal valve opening. */
    private final double coldValveOpening;

    /**
     * Constructor.
     *
     * @param hotValveOpening hot valve opening in percent
     * @param coldValveOpening cold valve opening in percent
     */
    public DualRecycleValveCommand(double hotValveOpening, double coldValveOpening) {
      this.hotValveOpening = hotValveOpening;
      this.coldValveOpening = coldValveOpening;
    }

    /**
     * Get valve opening by role.
     *
     * @param role valve role
     * @return opening in percent
     */
    public double getOpening(RecycleValveRole role) {
      return role == RecycleValveRole.HOT_FAST ? hotValveOpening : coldValveOpening;
    }

    /**
     * Get hot valve opening.
     *
     * @return hot valve opening in percent
     */
    public double getHotValveOpening() {
      return hotValveOpening;
    }

    /**
     * Get cold valve opening.
     *
     * @return cold valve opening in percent
     */
    public double getColdValveOpening() {
      return coldValveOpening;
    }
  }

  /** Recycle sizing result. */
  public static class RecycleSizingResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Required volumetric recycle flow. */
    private final double requiredRecycleFlow;
    /** Required mass recycle flow. */
    private final double requiredRecycleMassFlow;
    /** Screening valve coefficient. */
    private final double valveCvScreening;
    /** Volume response time. */
    private final double volumeResponseTime;
    /** True if volume response target is met. */
    private final boolean volumeResponseAcceptable;

    /**
     * Constructor.
     *
     * @param requiredRecycleFlow required volumetric recycle flow
     * @param requiredRecycleMassFlow required mass recycle flow
     * @param valveCvScreening screening valve coefficient
     * @param volumeResponseTime volume response time
     * @param volumeResponseAcceptable true if volume response target is met
     */
    public RecycleSizingResult(double requiredRecycleFlow, double requiredRecycleMassFlow, double valveCvScreening,
        double volumeResponseTime, boolean volumeResponseAcceptable) {
      this.requiredRecycleFlow = requiredRecycleFlow;
      this.requiredRecycleMassFlow = requiredRecycleMassFlow;
      this.valveCvScreening = valveCvScreening;
      this.volumeResponseTime = volumeResponseTime;
      this.volumeResponseAcceptable = volumeResponseAcceptable;
    }

    /**
     * Get required recycle flow.
     *
     * @return required recycle flow in m3/hr
     */
    public double getRequiredRecycleFlow() {
      return requiredRecycleFlow;
    }

    /**
     * Get required recycle mass flow.
     *
     * @return required recycle mass flow in kg/hr
     */
    public double getRequiredRecycleMassFlow() {
      return requiredRecycleMassFlow;
    }

    /**
     * Get screening valve coefficient.
     *
     * @return screening valve coefficient
     */
    public double getValveCvScreening() {
      return valveCvScreening;
    }

    /**
     * Get volume response time.
     *
     * @return volume response time in seconds
     */
    public double getVolumeResponseTime() {
      return volumeResponseTime;
    }

    /**
     * Check volume response acceptability.
     *
     * @return true if acceptable
     */
    public boolean isVolumeResponseAcceptable() {
      return volumeResponseAcceptable;
    }
  }

  /** Lumped educational surge-oscillation model state. */
  public static class SurgeOscillationState implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Normalized compressor flow. */
    private final double flow;
    /** Normalized plenum pressure. */
    private final double plenumPressure;
    /** Surge cycle count from flow reversals. */
    private final int surgeCycleCount;

    /**
     * Constructor.
     *
     * @param flow normalized flow
     * @param plenumPressure normalized plenum pressure
     * @param surgeCycleCount surge cycle count
     */
    public SurgeOscillationState(double flow, double plenumPressure, int surgeCycleCount) {
      this.flow = flow;
      this.plenumPressure = plenumPressure;
      this.surgeCycleCount = surgeCycleCount;
    }

    /**
     * Get flow.
     *
     * @return normalized flow
     */
    public double getFlow() {
      return flow;
    }

    /**
     * Get plenum pressure.
     *
     * @return normalized plenum pressure
     */
    public double getPlenumPressure() {
      return plenumPressure;
    }

    /**
     * Get surge cycle count.
     *
     * @return surge cycle count
     */
    public int getSurgeCycleCount() {
      return surgeCycleCount;
    }
  }
}
