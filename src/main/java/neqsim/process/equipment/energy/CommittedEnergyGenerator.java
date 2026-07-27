package neqsim.process.equipment.energy;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.util.validation.ValidationResult;

/**
 * Dispatchable generation unit with startup, minimum-load, ramp, and minimum up/down constraints.
 *
 * <p>
 * The unit publishes calculated generation through {@value #OUTPUT_PORT}. A positive requested power is an on-command;
 * zero is an off-command. Transient execution applies chronological commitment constraints and records cumulative
 * startup cost and startup emissions.
 * </p>
 */
public class CommittedEnergyGenerator extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;
  public static final String OUTPUT_PORT = "energyOutput";

  /** Immutable diagnostic for the latest commitment step. */
  public static final class StepResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final boolean committed;
    private final boolean started;
    private final boolean stopped;
    private final boolean startBlocked;
    private final boolean stopBlocked;
    private final double requestedPower;
    private final double generatedPower;

    private StepResult(boolean committed, boolean started, boolean stopped, boolean startBlocked, boolean stopBlocked,
        double requestedPower, double generatedPower) {
      this.committed = committed;
      this.started = started;
      this.stopped = stopped;
      this.startBlocked = startBlocked;
      this.stopBlocked = stopBlocked;
      this.requestedPower = requestedPower;
      this.generatedPower = generatedPower;
    }

    public boolean isCommitted() {
      return committed;
    }

    public boolean isStarted() {
      return started;
    }

    public boolean isStopped() {
      return stopped;
    }

    public boolean isStartBlocked() {
      return startBlocked;
    }

    public boolean isStopBlocked() {
      return stopBlocked;
    }

    public double getRequestedPower() {
      return requestedPower;
    }

    public double getGeneratedPower() {
      return generatedPower;
    }
  }

  private final EnergyType outputType;
  private double requestedPower = 0.0;
  private double currentPower = 0.0;
  private double minimumStablePower = 0.0;
  private double maximumPower = Double.POSITIVE_INFINITY;
  private double rampUpRate = Double.POSITIVE_INFINITY;
  private double rampDownRate = Double.POSITIVE_INFINITY;
  private double minimumUpTime = 0.0;
  private double minimumDownTime = 0.0;
  private double timeInState = 0.0;
  private boolean committed = false;
  private double startupCost = 0.0;
  private double startupEmissionsKg = 0.0;
  private double cumulativeStartupCost = 0.0;
  private double cumulativeStartupEmissionsKg = 0.0;
  private int startupCount = 0;
  private StepResult lastStepResult = new StepResult(false, false, false, false, false, 0.0, 0.0);

  public CommittedEnergyGenerator(String name, EnergyType outputType) {
    super(name);
    if (outputType == null || outputType == EnergyType.UNSPECIFIED) {
      throw new IllegalArgumentException("A specified output energy type is required");
    }
    this.outputType = outputType;
    registerEnergyPort(OUTPUT_PORT, outputType, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
  }

  public void setPowerLimits(double minimumStablePower, double maximumPower) {
    if (!Double.isFinite(minimumStablePower) || minimumStablePower < 0.0 || !Double.isFinite(maximumPower)
        || maximumPower <= 0.0 || minimumStablePower > maximumPower) {
      throw new IllegalArgumentException("Power limits must be finite and satisfy 0 <= minimum <= maximum");
    }
    this.minimumStablePower = minimumStablePower;
    this.maximumPower = maximumPower;
  }

  public void setRampRates(double rampUpRate, double rampDownRate) {
    requirePositiveOrInfinity(rampUpRate, "Ramp-up rate");
    requirePositiveOrInfinity(rampDownRate, "Ramp-down rate");
    this.rampUpRate = rampUpRate;
    this.rampDownRate = rampDownRate;
  }

  public void setMinimumUpDownTimes(double minimumUpTime, double minimumDownTime) {
    requireNonNegative(minimumUpTime, "Minimum up time");
    requireNonNegative(minimumDownTime, "Minimum down time");
    this.minimumUpTime = minimumUpTime;
    this.minimumDownTime = minimumDownTime;
  }

  public void setStartupPenalty(double startupCost, double startupEmissionsKg) {
    requireNonNegative(startupCost, "Startup cost");
    requireNonNegative(startupEmissionsKg, "Startup emissions");
    this.startupCost = startupCost;
    this.startupEmissionsKg = startupEmissionsKg;
  }

  public void setRequestedPower(double requestedPower) {
    if (!Double.isFinite(requestedPower) || requestedPower < 0.0) {
      throw new IllegalArgumentException("Requested power must be non-negative and finite");
    }
    this.requestedPower = requestedPower;
  }

  public double getRequestedPower() {
    return requestedPower;
  }

  public double getCurrentPower() {
    return currentPower;
  }

  public boolean isCommitted() {
    return committed;
  }

  public double getTimeInState() {
    return timeInState;
  }

  public int getStartupCount() {
    return startupCount;
  }

  public double getCumulativeStartupCost() {
    return cumulativeStartupCost;
  }

  public double getCumulativeStartupEmissionsKg() {
    return cumulativeStartupEmissionsKg;
  }

  public StepResult getLastStepResult() {
    return lastStepResult;
  }

  /** Initializes chronological state before a study. */
  public void initializeCommitment(boolean committed, double timeInState, double initialPower) {
    requireNonNegative(timeInState, "Time in state");
    requireNonNegative(initialPower, "Initial power");
    if (!committed && initialPower > 0.0) {
      throw new IllegalArgumentException("An offline unit cannot have positive initial power");
    }
    if (committed && (initialPower < minimumStablePower || initialPower > maximumPower)) {
      throw new IllegalArgumentException("Initial online power must be within configured power limits");
    }
    this.committed = committed;
    this.timeInState = timeInState;
    this.currentPower = initialPower;
    publish();
  }

  @Override
  public void run(UUID id) {
    runTransient(0.0, id);
  }

  @Override
  public void runTransient(double dt, UUID id) {
    requireNonNegative(dt, "Commitment timestep");
    boolean wantsOn = requestedPower > 0.0;
    boolean started = false;
    boolean stopped = false;
    boolean startBlocked = false;
    boolean stopBlocked = false;

    if (wantsOn && !committed) {
      if (timeInState >= minimumDownTime) {
        committed = true;
        timeInState = 0.0;
        started = true;
        startupCount++;
        cumulativeStartupCost += startupCost;
        cumulativeStartupEmissionsKg += startupEmissionsKg;
      } else {
        startBlocked = true;
      }
    } else if (!wantsOn && committed) {
      if (timeInState >= minimumUpTime) {
        committed = false;
        timeInState = 0.0;
        stopped = true;
      } else {
        stopBlocked = true;
      }
    }

    double target = 0.0;
    if (committed) {
      target = wantsOn ? Math.max(minimumStablePower, Math.min(maximumPower, requestedPower)) : minimumStablePower;
    }
    double maximumIncrease = rampUpRate * dt;
    double maximumDecrease = rampDownRate * dt;
    if (target > currentPower && Double.isFinite(maximumIncrease)) {
      currentPower = Math.min(target, currentPower + maximumIncrease);
    } else if (target < currentPower && Double.isFinite(maximumDecrease)) {
      currentPower = Math.max(target, currentPower - maximumDecrease);
    } else {
      currentPower = target;
    }
    if (!committed && currentPower <= Math.max(1.0e-9, maximumPower * 1.0e-12)) {
      currentPower = 0.0;
    }

    timeInState += dt;
    publish();
    lastStepResult = new StepResult(committed, started, stopped, startBlocked, stopBlocked, requestedPower,
        currentPower);
    increaseTime(dt);
    setCalculationIdentifier(id);
  }

  private void publish() {
    if (getEnergyPort(OUTPUT_PORT).isConnected()) {
      getEnergyPort(OUTPUT_PORT).setDuty(currentPower);
    }
  }

  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (!Double.isFinite(maximumPower)) {
      result.addError("energy", "Maximum committed-unit power is not configured",
          "Call setPowerLimits(minimumStablePower, maximumPower)");
    }
    if (!getEnergyPort(OUTPUT_PORT).isConnected()) {
      result.addWarning("energy", "Committed generation output is not connected",
          "Connect energyOutput to an EnergyBus");
    }
    return result;
  }

  public EnergyType getOutputType() {
    return outputType;
  }

  private static void requireNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be non-negative and finite");
    }
  }

  private static void requirePositiveOrInfinity(double value, String name) {
    if (Double.isNaN(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
