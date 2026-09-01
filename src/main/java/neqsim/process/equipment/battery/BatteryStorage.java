package neqsim.process.equipment.battery;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.util.validation.ValidationResult;

/**
 * Battery storage with state of charge, efficiencies, power limits, ramp response, trip behavior, and bus balancing.
 *
 * <p>
 * Stored capacity and state of charge use Wh for compatibility with the original charge/discharge API. On an
 * {@link EnergyBus}, positive battery power is discharge to the bus and negative power is charging. Legacy
 * point-to-point streams retain their historical opposite sign convention.
 * </p>
 *
 * @author esol
 * @version 2.0
 */
public class BatteryStorage extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Electrical bidirectional port name. */
  public static final String ELECTRICAL_PORT = "electricalPower";

  private double capacity;
  private double stateOfCharge;
  private double chargeEfficiency = 0.95;
  private double dischargeEfficiency = 0.95;
  private double currentPower = 0.0;
  private double targetPower = 0.0;
  private double maximumChargePower = Double.POSITIVE_INFINITY;
  private double maximumDischargePower = Double.POSITIVE_INFINITY;
  private double powerRampRate = Double.POSITIVE_INFINITY;
  private boolean tripped = false;
  /** Power at the start of the current physical transient step. */
  private double transientStepStartPower = 0.0;
  /** Stored energy at the start of the current physical transient step, in Wh. */
  private double transientStepStartStateOfCharge = 0.0;
  /** Physical transient-step identifier associated with the saved step-start state. */
  private UUID transientStepIdentifier = null;

  /**
   * Constructs a battery.
   *
   * @param name unit name
   * @param capacity maximum stored energy in Wh
   */
  public BatteryStorage(String name, double capacity) {
    super(name);
    registerEnergyPort(ELECTRICAL_PORT, EnergyType.ELECTRICAL, EnergyPortDirection.BIDIRECTIONAL,
        EnergyPortMode.CALCULATED);
    setCapacity(capacity);
    stateOfCharge = 0.0;
  }

  /** Default constructor with zero capacity. */
  public BatteryStorage() {
    this("BatteryStorage", 0.0);
  }

  /**
   * Constructs a battery with zero capacity.
   *
   * @param name unit name
   */
  public BatteryStorage(String name) {
    this(name, 0.0);
  }

  /**
   * Charges the battery immediately over a specified duration.
   *
   * @param power charging power in W
   * @param hours charging duration in h
   */
  public void charge(double power, double hours) {
    validatePowerAndDuration(power, hours);
    double actualPower = Math.min(power, maximumChargePower);
    double energyIn = actualPower * hours * chargeEfficiency;
    stateOfCharge = Math.min(capacity, stateOfCharge + energyIn);
    currentPower = -actualPower;
    targetPower = currentPower;
    transientStepIdentifier = null;
  }

  /**
   * Discharges the battery immediately over a specified duration.
   *
   * @param power requested discharge power in W
   * @param hours discharge duration in h
   * @return actual delivered power in W
   */
  public double discharge(double power, double hours) {
    validatePowerAndDuration(power, hours);
    double actualPower = Math.min(power, maximumDischargePower);
    double energyNeeded = actualPower * hours / dischargeEfficiency;
    if (energyNeeded > stateOfCharge && hours > 0.0) {
      actualPower = stateOfCharge * dischargeEfficiency / hours;
      energyNeeded = stateOfCharge;
    }
    stateOfCharge = Math.max(0.0, stateOfCharge - energyNeeded);
    currentPower = actualPower;
    targetPower = currentPower;
    transientStepIdentifier = null;
    return actualPower;
  }

  /**
   * Gets state of charge.
   *
   * @return stored energy in Wh
   */
  public double getStateOfCharge() {
    return stateOfCharge;
  }

  /**
   * Sets state of charge.
   *
   * @param stateOfCharge stored energy in Wh
   */
  public void setStateOfCharge(double stateOfCharge) {
    if (!Double.isFinite(stateOfCharge)) {
      throw new IllegalArgumentException("State of charge must be finite");
    }
    this.stateOfCharge = Math.max(0.0, Math.min(capacity, stateOfCharge));
    transientStepIdentifier = null;
  }

  /**
   * Gets capacity.
   *
   * @return maximum stored energy in Wh
   */
  public double getCapacity() {
    return capacity;
  }

  /**
   * Sets capacity.
   *
   * @param capacity maximum stored energy in Wh
   */
  public void setCapacity(double capacity) {
    if (!Double.isFinite(capacity) || capacity < 0.0) {
      throw new IllegalArgumentException("Battery capacity must be non-negative and finite");
    }
    this.capacity = capacity;
    stateOfCharge = Math.min(stateOfCharge, capacity);
    transientStepIdentifier = null;
  }

  /**
   * Gets state of charge as a fraction of capacity.
   *
   * @return state-of-charge fraction from zero to one
   */
  public double getStateOfChargeFraction() {
    return capacity > 0.0 ? stateOfCharge / capacity : 0.0;
  }

  /**
   * Sets charge and discharge efficiencies.
   *
   * @param chargeEfficiency charging efficiency in (0, 1]
   * @param dischargeEfficiency discharging efficiency in (0, 1]
   */
  public void setEfficiencies(double chargeEfficiency, double dischargeEfficiency) {
    validateEfficiency(chargeEfficiency, "Charge efficiency");
    validateEfficiency(dischargeEfficiency, "Discharge efficiency");
    this.chargeEfficiency = chargeEfficiency;
    this.dischargeEfficiency = dischargeEfficiency;
  }

  /**
   * Sets charge and discharge power limits.
   *
   * @param maximumChargePower maximum charging power in W
   * @param maximumDischargePower maximum discharging power in W
   */
  public void setPowerLimits(double maximumChargePower, double maximumDischargePower) {
    if (!Double.isFinite(maximumChargePower) || maximumChargePower < 0.0 || !Double.isFinite(maximumDischargePower)
        || maximumDischargePower < 0.0) {
      throw new IllegalArgumentException("Battery power limits must be non-negative and finite");
    }
    this.maximumChargePower = maximumChargePower;
    this.maximumDischargePower = maximumDischargePower;
    getEnergyPort(ELECTRICAL_PORT).setBalanceLimits(tripped ? 0.0 : maximumDischargePower,
        tripped ? 0.0 : maximumChargePower);
  }

  /**
   * Sets battery power ramp rate.
   *
   * @param powerRampRate maximum power change in W/s
   */
  public void setPowerRampRate(double powerRampRate) {
    if (Double.isNaN(powerRampRate) || powerRampRate <= 0.0) {
      throw new IllegalArgumentException("Battery ramp rate must be positive");
    }
    this.powerRampRate = powerRampRate;
  }

  /**
   * Sets target power for transient operation.
   *
   * @param targetPower positive discharge or negative charge power in W
   */
  public void setTargetPower(double targetPower) {
    if (!Double.isFinite(targetPower)) {
      throw new IllegalArgumentException("Battery target power must be finite");
    }
    this.targetPower = Math.max(-maximumChargePower, Math.min(maximumDischargePower, targetPower));
  }

  /**
   * Gets current battery power.
   *
   * @return positive discharge or negative charge power in W
   */
  public double getCurrentPower() {
    return currentPower;
  }

  /**
   * Enables deterministic automatic bus balancing.
   *
   * @param maximumChargePower maximum surplus absorption in W
   * @param maximumDischargePower maximum shortage generation in W
   * @param priority balancing priority
   */
  public void enableAutomaticBalancing(double maximumChargePower, double maximumDischargePower, int priority) {
    setPowerLimits(maximumChargePower, maximumDischargePower);
    getEnergyPort(ELECTRICAL_PORT).setPriority(priority);
    getEnergyPort(ELECTRICAL_PORT).setMode(EnergyPortMode.BALANCE);
  }

  /**
   * Trips or resets the battery converter.
   *
   * @param tripped trip state
   */
  public void setTripped(boolean tripped) {
    this.tripped = tripped;
    if (tripped) {
      targetPower = 0.0;
    }
    if (getEnergyPort(ELECTRICAL_PORT).getMode() == EnergyPortMode.BALANCE && Double.isFinite(maximumChargePower)
        && Double.isFinite(maximumDischargePower)) {
      getEnergyPort(ELECTRICAL_PORT).setBalanceLimits(tripped ? 0.0 : maximumDischargePower,
          tripped ? 0.0 : maximumChargePower);
    }
  }

  /**
   * Checks trip state.
   *
   * @return {@code true} when tripped
   */
  public boolean isTripped() {
    return tripped;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    publishPower(false);
    transientStepIdentifier = null;
    setCalculationIdentifier(id);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * A non-null calculation identifier represents one physical transient step. Repeated refinement evaluations with the
   * same identifier recompute the power ramp and state-of-charge update from the state that existed at the start of the
   * physical step. They therefore do not consume stored energy or advance the battery clock a second time. A different
   * identifier starts the next physical step from the previously accepted battery state. Null identifiers preserve the
   * legacy one-call-per-step behavior.
   * </p>
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!Double.isFinite(dt) || dt < 0.0) {
      throw new IllegalArgumentException("Battery timestep must be non-negative and finite");
    }

    boolean repeatedEvaluation = id != null && id.equals(transientStepIdentifier);
    if (!repeatedEvaluation) {
      transientStepStartPower = currentPower;
      transientStepStartStateOfCharge = stateOfCharge;
      transientStepIdentifier = id;
    } else {
      currentPower = transientStepStartPower;
      stateOfCharge = transientStepStartStateOfCharge;
    }

    double requestedPower = tripped ? 0.0 : targetPower;
    if (getEnergyPort(ELECTRICAL_PORT).isConnected()
        && getEnergyPort(ELECTRICAL_PORT).getMode() == EnergyPortMode.BALANCE
        && getEnergyPort(ELECTRICAL_PORT).getEnergyStream() instanceof EnergyBus) {
      EnergyBus energyBus = (EnergyBus) getEnergyPort(ELECTRICAL_PORT).getEnergyStream();
      getEnergyPort(ELECTRICAL_PORT).clearRealizedBalancePower();
      if (!energyBus.hasSolution()) {
        energyBus.solveBalance();
      }
      if (!tripped) {
        requestedPower = getEnergyPort(ELECTRICAL_PORT).getDuty();
      }
    }
    requestedPower = Math.max(-maximumChargePower, Math.min(maximumDischargePower, requestedPower));

    double maximumChange = powerRampRate * dt;
    if (Double.isFinite(maximumChange) && Math.abs(requestedPower - currentPower) > maximumChange) {
      currentPower += Math.copySign(maximumChange, requestedPower - currentPower);
    } else {
      currentPower = requestedPower;
    }

    double hours = dt / 3600.0;
    if (currentPower > 0.0 && hours > 0.0) {
      double requiredEnergy = currentPower * hours / dischargeEfficiency;
      if (requiredEnergy > stateOfCharge) {
        currentPower = stateOfCharge * dischargeEfficiency / hours;
        requiredEnergy = stateOfCharge;
      }
      stateOfCharge -= requiredEnergy;
    } else if (currentPower < 0.0 && hours > 0.0) {
      double availableCapacity = capacity - stateOfCharge;
      double storedEnergy = -currentPower * hours * chargeEfficiency;
      if (storedEnergy > availableCapacity) {
        currentPower = -availableCapacity / (hours * chargeEfficiency);
        storedEnergy = availableCapacity;
      }
      stateOfCharge += storedEnergy;
    }

    publishPower(true);
    if (!repeatedEvaluation) {
      increaseTime(dt);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Publishes current battery power.
   *
   * @param reconcileBalance whether transient balance power should be reconciled with the bus allocation
   */
  private void publishPower(boolean reconcileBalance) {
    if (!getEnergyPort(ELECTRICAL_PORT).isConnected()) {
      return;
    }
    if (getEnergyPort(ELECTRICAL_PORT).getMode() == EnergyPortMode.BALANCE
        && getEnergyPort(ELECTRICAL_PORT).getEnergyStream() instanceof EnergyBus) {
      if (reconcileBalance) {
        getEnergyPort(ELECTRICAL_PORT).reportRealizedBalancePower(currentPower);
      }
      return;
    }
    if (getEnergyPort(ELECTRICAL_PORT).getEnergyStream() instanceof EnergyBus) {
      getEnergyPort(ELECTRICAL_PORT).setDuty(currentPower);
    } else {
      getEnergyPort(ELECTRICAL_PORT).setDuty(-currentPower);
    }
  }

  /**
   * Validates power and duration arguments.
   *
   * @param power power in W
   * @param hours duration in h
   */
  private static void validatePowerAndDuration(double power, double hours) {
    if (!Double.isFinite(power) || power < 0.0 || !Double.isFinite(hours) || hours < 0.0) {
      throw new IllegalArgumentException("Power and duration must be non-negative and finite");
    }
  }

  /**
   * Validates an efficiency.
   *
   * @param efficiency efficiency value
   * @param name property name
   */
  private static void validateEfficiency(double efficiency, String name) {
    if (!Double.isFinite(efficiency) || efficiency <= 0.0 || efficiency > 1.0) {
      throw new IllegalArgumentException(name + " must be in (0, 1]");
    }
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (capacity <= 0.0) {
      result.addError("energy", "Battery capacity is not positive", "Call setCapacity(capacityWh)");
    }
    if (!getEnergyPort(ELECTRICAL_PORT).isConnected()) {
      result.addWarning("energy", "Battery electrical port is not connected",
          "Connect electricalPower to an electrical EnergyBus");
    }
    return result;
  }
}
