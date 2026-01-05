package neqsim.process.equipment.compressor;

import java.util.Objects;

/**
 * AntiSurge class for compressor surge protection in dynamic simulations.
 *
 * <p>
 * This class models anti-surge control systems including recycle valve control, surge detection,
 * and various control strategies used to protect centrifugal compressors from surge.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class AntiSurge implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;

  /**
   * Enum for anti-surge control strategies.
   */
  public enum ControlStrategy {
    /** Simple on/off control based on surge line proximity. */
    ON_OFF,
    /** Proportional control based on distance to surge. */
    PROPORTIONAL,
    /** PID control for smooth valve positioning. */
    PID,
    /** Predictive control using rate of change. */
    PREDICTIVE,
    /** Dual-loop control with separate surge and capacity controllers. */
    DUAL_LOOP
  }

  private boolean isActive = false;
  private boolean isSurge = false;
  private double surgeControlFactor = 1.05;
  private double currentSurgeFraction = 0.0;

  // Enhanced dynamic simulation fields
  private ControlStrategy controlStrategy = ControlStrategy.PROPORTIONAL;
  private double minimumRecycleFlow = 0.0; // Minimum recycle flow in m³/hr
  private double maximumRecycleFlow = Double.MAX_VALUE; // Maximum recycle flow
  private double valvePosition = 0.0; // Current valve position (0-1)
  private double valveResponseTime = 2.0; // Valve response time in seconds
  private double valveRateLimit = 0.5; // Maximum valve movement per second (0-1 scale)
  private double targetValvePosition = 0.0; // Target valve position

  // PID controller parameters
  private double pidKp = 2.0; // Proportional gain
  private double pidKi = 0.5; // Integral gain
  private double pidKd = 0.1; // Derivative gain
  private double pidIntegral = 0.0; // Integral accumulator
  private double pidLastError = 0.0; // Last error for derivative
  private double pidSetpoint = 0.10; // Setpoint surge margin

  // Predictive control parameters
  private double surgeApproachRate = 0.0; // Rate of change of surge margin
  private double lastSurgeFraction = 0.0; // Previous surge fraction for rate calculation
  private double predictiveHorizon = 5.0; // Prediction horizon in seconds
  private double predictionGain = 1.5; // Gain for predictive action

  // Control line parameters
  private double surgeControlLineOffset = 0.10; // Offset from surge line (10%)
  private double surgeControlLineSlope = 1.0; // Slope factor for control line

  // Alarm and trip settings
  private double surgeWarningMargin = 0.08; // Warning at 8% margin
  private double surgeTripMargin = 0.0; // Trip at surge line
  private int surgeCycleCount = 0; // Number of surge cycles detected
  private int maxSurgeCyclesBeforeTrip = 3; // Trip after this many surge cycles
  private double surgeCycleResetTime = 60.0; // Reset cycle count after this time
  private double timeSinceLastSurge = 0.0;

  // Hot gas bypass option
  private boolean useHotGasBypass = false;
  private double hotGasBypassFlow = 0.0;

  /**
   * Default constructor.
   */
  public AntiSurge() {}

  /**
   * Constructor with control strategy.
   *
   * @param strategy the control strategy to use
   */
  public AntiSurge(ControlStrategy strategy) {
    this.controlStrategy = strategy;
  }

  /**
   * Check if anti-surge is active.
   *
   * @return true if active
   */
  public boolean isActive() {
    return isActive;
  }

  /**
   * Set anti-surge active state.
   *
   * @param isActive true to activate
   */
  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  /**
   * Check if compressor is in surge.
   *
   * @return true if in surge
   */
  public boolean isSurge() {
    return isSurge;
  }

  /**
   * Set surge state.
   *
   * @param isSurge true if in surge
   */
  public void setSurge(boolean isSurge) {
    if (isSurge && !this.isSurge) {
      // Entering surge
      surgeCycleCount++;
      timeSinceLastSurge = 0.0;
    }
    this.isSurge = isSurge;
  }

  /**
   * Get the surge control factor.
   *
   * @return the surge control factor
   */
  public double getSurgeControlFactor() {
    return surgeControlFactor;
  }

  /**
   * Set the surge control factor.
   *
   * @param antiSurgeSafetyFactor the safety factor
   */
  public void setSurgeControlFactor(double antiSurgeSafetyFactor) {
    this.surgeControlFactor = antiSurgeSafetyFactor;
  }

  /**
   * Get the current surge fraction.
   *
   * @return the surge fraction
   */
  public double getCurrentSurgeFraction() {
    return currentSurgeFraction;
  }

  /**
   * Set the current surge fraction.
   *
   * @param currentSurgeFraction the surge fraction
   */
  public void setCurrentSurgeFraction(double currentSurgeFraction) {
    this.lastSurgeFraction = this.currentSurgeFraction;
    this.currentSurgeFraction = currentSurgeFraction;
  }

  /**
   * Get the control strategy.
   *
   * @return the control strategy
   */
  public ControlStrategy getControlStrategy() {
    return controlStrategy;
  }

  /**
   * Set the control strategy.
   *
   * @param strategy the control strategy
   */
  public void setControlStrategy(ControlStrategy strategy) {
    this.controlStrategy = strategy;
  }

  /**
   * Get the minimum recycle flow.
   *
   * @return minimum flow in m³/hr
   */
  public double getMinimumRecycleFlow() {
    return minimumRecycleFlow;
  }

  /**
   * Set the minimum recycle flow.
   *
   * @param flow minimum flow in m³/hr
   */
  public void setMinimumRecycleFlow(double flow) {
    this.minimumRecycleFlow = flow;
  }

  /**
   * Get the maximum recycle flow.
   *
   * @return maximum flow in m³/hr
   */
  public double getMaximumRecycleFlow() {
    return maximumRecycleFlow;
  }

  /**
   * Set the maximum recycle flow.
   *
   * @param flow maximum flow in m³/hr
   */
  public void setMaximumRecycleFlow(double flow) {
    this.maximumRecycleFlow = flow;
  }

  /**
   * Get the current valve position.
   *
   * @return valve position (0-1)
   */
  public double getValvePosition() {
    return valvePosition;
  }

  /**
   * Set the valve position directly.
   *
   * @param position valve position (0-1)
   */
  public void setValvePosition(double position) {
    this.valvePosition = Math.max(0.0, Math.min(1.0, position));
  }

  /**
   * Get the valve response time.
   *
   * @return response time in seconds
   */
  public double getValveResponseTime() {
    return valveResponseTime;
  }

  /**
   * Set the valve response time.
   *
   * @param time response time in seconds
   */
  public void setValveResponseTime(double time) {
    this.valveResponseTime = time;
  }

  /**
   * Get the valve rate limit.
   *
   * @return rate limit per second
   */
  public double getValveRateLimit() {
    return valveRateLimit;
  }

  /**
   * Set the valve rate limit.
   *
   * @param rateLimit rate limit per second
   */
  public void setValveRateLimit(double rateLimit) {
    this.valveRateLimit = rateLimit;
  }

  /**
   * Get the target valve position.
   *
   * @return target position (0-1)
   */
  public double getTargetValvePosition() {
    return targetValvePosition;
  }

  /**
   * Set the target valve position.
   *
   * @param position target position (0-1)
   */
  public void setTargetValvePosition(double position) {
    this.targetValvePosition = Math.max(0.0, Math.min(1.0, position));
  }

  /**
   * Update the anti-surge controller.
   *
   * @param surgeMargin current distance to surge
   * @param timeStep time step in seconds
   * @return the calculated valve position
   */
  public double updateController(double surgeMargin, double timeStep) {
    // Update surge cycle tracking
    timeSinceLastSurge += timeStep;
    if (timeSinceLastSurge > surgeCycleResetTime) {
      surgeCycleCount = 0;
    }

    // Calculate surge approach rate
    surgeApproachRate = (currentSurgeFraction - lastSurgeFraction) / timeStep;

    // Calculate target valve position based on control strategy
    switch (controlStrategy) {
      case ON_OFF:
        targetValvePosition = calculateOnOffControl(surgeMargin);
        break;
      case PROPORTIONAL:
        targetValvePosition = calculateProportionalControl(surgeMargin);
        break;
      case PID:
        targetValvePosition = calculatePIDControl(surgeMargin, timeStep);
        break;
      case PREDICTIVE:
        targetValvePosition = calculatePredictiveControl(surgeMargin, timeStep);
        break;
      case DUAL_LOOP:
        targetValvePosition = calculateDualLoopControl(surgeMargin, timeStep);
        break;
      default:
        targetValvePosition = calculateProportionalControl(surgeMargin);
        break;
    }

    // Apply valve rate limiting
    updateValvePosition(timeStep);

    return valvePosition;
  }

  /**
   * Calculate on/off control output.
   *
   * @param surgeMargin current surge margin
   * @return valve position (0 or 1)
   */
  private double calculateOnOffControl(double surgeMargin) {
    if (surgeMargin <= surgeControlLineOffset) {
      return 1.0; // Full open
    }
    return 0.0; // Closed
  }

  /**
   * Calculate proportional control output.
   *
   * @param surgeMargin current surge margin
   * @return valve position (0-1)
   */
  private double calculateProportionalControl(double surgeMargin) {
    double controlLineMargin = surgeControlLineOffset * surgeControlFactor;

    if (surgeMargin >= controlLineMargin * 2) {
      return 0.0; // Far from surge, close valve
    } else if (surgeMargin <= 0) {
      return 1.0; // In surge, full open
    } else {
      // Linear interpolation
      return 1.0 - (surgeMargin / (controlLineMargin * 2));
    }
  }

  /**
   * Calculate PID control output.
   *
   * @param surgeMargin current surge margin
   * @param timeStep time step in seconds
   * @return valve position (0-1)
   */
  private double calculatePIDControl(double surgeMargin, double timeStep) {
    double error = pidSetpoint - surgeMargin;

    // Proportional term
    double pTerm = pidKp * error;

    // Integral term with anti-windup
    if (valvePosition > 0.01 && valvePosition < 0.99) {
      pidIntegral += error * timeStep;
    }
    double iTerm = pidKi * pidIntegral;

    // Derivative term
    double dTerm = pidKd * (error - pidLastError) / timeStep;
    pidLastError = error;

    // Combine and clamp
    double output = pTerm + iTerm + dTerm;
    return Math.max(0.0, Math.min(1.0, output));
  }

  /**
   * Calculate predictive control output.
   *
   * @param surgeMargin current surge margin
   * @param timeStep time step in seconds
   * @return valve position (0-1)
   */
  private double calculatePredictiveControl(double surgeMargin, double timeStep) {
    // Predict future surge margin
    double predictedMargin = surgeMargin + surgeApproachRate * predictiveHorizon;

    // Use proportional control on predicted margin
    double predictiveAction = calculateProportionalControl(predictedMargin);

    // Blend with current margin control
    double currentAction = calculateProportionalControl(surgeMargin);

    return currentAction * (1.0 - predictionGain * 0.5) + predictiveAction * predictionGain * 0.5;
  }

  /**
   * Calculate dual-loop control output.
   *
   * @param surgeMargin current surge margin
   * @param timeStep time step in seconds
   * @return valve position (0-1)
   */
  private double calculateDualLoopControl(double surgeMargin, double timeStep) {
    // Primary loop: surge protection (fast)
    double surgeControl = calculateProportionalControl(surgeMargin);

    // Secondary loop: capacity control (slower)
    // For now, just use the primary loop
    // In a full implementation, this would coordinate with process capacity needs

    return surgeControl;
  }

  /**
   * Update valve position with rate limiting and response time.
   *
   * @param timeStep time step in seconds
   */
  private void updateValvePosition(double timeStep) {
    double positionError = targetValvePosition - valvePosition;

    // Apply rate limiting
    double maxChange = valveRateLimit * timeStep;
    double actualChange;

    if (Math.abs(positionError) <= maxChange) {
      actualChange = positionError;
    } else {
      actualChange = Math.signum(positionError) * maxChange;
    }

    // Apply first-order response
    double tau = valveResponseTime;
    if (tau > 0) {
      double alpha = timeStep / (tau + timeStep);
      actualChange = alpha * positionError;
    }

    valvePosition = Math.max(0.0, Math.min(1.0, valvePosition + actualChange));
  }

  /**
   * Get the recycle flow rate based on valve position.
   *
   * @param maxFlow maximum possible recycle flow
   * @return calculated recycle flow
   */
  public double getRecycleFlow(double maxFlow) {
    double flow = valvePosition * maxFlow;
    flow = Math.max(minimumRecycleFlow, flow);
    flow = Math.min(maximumRecycleFlow, flow);
    return flow;
  }

  /**
   * Check if the compressor should trip due to repeated surge cycles.
   *
   * @return true if trip is required
   */
  public boolean shouldTrip() {
    return surgeCycleCount >= maxSurgeCyclesBeforeTrip;
  }

  /**
   * Get the number of surge cycles.
   *
   * @return surge cycle count
   */
  public int getSurgeCycleCount() {
    return surgeCycleCount;
  }

  /**
   * Reset the surge cycle counter.
   */
  public void resetSurgeCycleCount() {
    surgeCycleCount = 0;
  }

  /**
   * Get the surge approach rate.
   *
   * @return rate of change of surge margin per second
   */
  public double getSurgeApproachRate() {
    return surgeApproachRate;
  }

  /**
   * Set PID controller parameters.
   *
   * @param kp proportional gain
   * @param ki integral gain
   * @param kd derivative gain
   */
  public void setPIDParameters(double kp, double ki, double kd) {
    this.pidKp = kp;
    this.pidKi = ki;
    this.pidKd = kd;
  }

  /**
   * Set PID setpoint.
   *
   * @param setpoint target surge margin
   */
  public void setPIDSetpoint(double setpoint) {
    this.pidSetpoint = setpoint;
  }

  /**
   * Reset the PID controller state.
   */
  public void resetPID() {
    pidIntegral = 0.0;
    pidLastError = 0.0;
  }

  /**
   * Set the surge control line offset.
   *
   * @param offset offset from surge line (e.g., 0.10 for 10%)
   */
  public void setSurgeControlLineOffset(double offset) {
    this.surgeControlLineOffset = offset;
  }

  /**
   * Get the surge control line offset.
   *
   * @return offset from surge line
   */
  public double getSurgeControlLineOffset() {
    return surgeControlLineOffset;
  }

  /**
   * Set the maximum surge cycles before trip.
   *
   * @param maxCycles maximum cycles
   */
  public void setMaxSurgeCyclesBeforeTrip(int maxCycles) {
    this.maxSurgeCyclesBeforeTrip = maxCycles;
  }

  /**
   * Check if hot gas bypass is enabled.
   *
   * @return true if enabled
   */
  public boolean isUseHotGasBypass() {
    return useHotGasBypass;
  }

  /**
   * Enable or disable hot gas bypass.
   *
   * @param use true to enable
   */
  public void setUseHotGasBypass(boolean use) {
    this.useHotGasBypass = use;
  }

  /**
   * Get hot gas bypass flow.
   *
   * @return bypass flow in m³/hr
   */
  public double getHotGasBypassFlow() {
    return hotGasBypassFlow;
  }

  /**
   * Set hot gas bypass flow.
   *
   * @param flow bypass flow in m³/hr
   */
  public void setHotGasBypassFlow(double flow) {
    this.hotGasBypassFlow = flow;
  }

  /**
   * Get the surge warning margin.
   *
   * @return warning margin
   */
  public double getSurgeWarningMargin() {
    return surgeWarningMargin;
  }

  /**
   * Set the surge warning margin.
   *
   * @param margin warning margin
   */
  public void setSurgeWarningMargin(double margin) {
    this.surgeWarningMargin = margin;
  }

  /**
   * Get the predictive horizon.
   *
   * @return prediction horizon in seconds
   */
  public double getPredictiveHorizon() {
    return predictiveHorizon;
  }

  /**
   * Set the predictive horizon.
   *
   * @param horizon prediction horizon in seconds
   */
  public void setPredictiveHorizon(double horizon) {
    this.predictiveHorizon = horizon;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(currentSurgeFraction, isActive, isSurge, surgeControlFactor,
        controlStrategy, valvePosition);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    AntiSurge other = (AntiSurge) obj;
    return Double.doubleToLongBits(currentSurgeFraction) == Double
        .doubleToLongBits(other.currentSurgeFraction) && isActive == other.isActive
        && isSurge == other.isSurge
        && Double.doubleToLongBits(surgeControlFactor) == Double
            .doubleToLongBits(other.surgeControlFactor)
        && controlStrategy == other.controlStrategy
        && Double.doubleToLongBits(valvePosition) == Double.doubleToLongBits(other.valvePosition);
  }

  /**
   * Generate a summary of the anti-surge controller state.
   *
   * @return multi-line summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Anti-Surge Controller Summary ===\n");
    sb.append(String.format("Active: %s\n", isActive));
    sb.append(String.format("In Surge: %s\n", isSurge));
    sb.append(String.format("Control Strategy: %s\n", controlStrategy));
    sb.append(String.format("Surge Fraction: %.2f%%\n", currentSurgeFraction * 100));
    sb.append(String.format("Valve Position: %.1f%%\n", valvePosition * 100));
    sb.append(String.format("Target Position: %.1f%%\n", targetValvePosition * 100));
    sb.append(String.format("Surge Cycles: %d\n", surgeCycleCount));
    sb.append(String.format("Approach Rate: %.4f/s\n", surgeApproachRate));
    return sb.toString();
  }
}
