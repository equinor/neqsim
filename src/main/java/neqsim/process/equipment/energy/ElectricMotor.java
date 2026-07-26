package neqsim.process.equipment.energy;

import java.util.Objects;
import neqsim.process.equipment.compressor.driver.ElectricMotorDriver;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;
import neqsim.util.validation.ValidationResult;

/**
 * Electric motor converting electrical power to shaft work.
 *
 * <p>
 * The default model uses constant efficiency. An optional {@link ElectricMotorDriver} adds rated power, fixed-speed or
 * VFD capability, part-load efficiency, and ambient/altitude derating using the same driver model already used by
 * NeqSim compressor studies.
 * </p>
 *
 * @author NeqSim
 * @version 2.0
 */
public class ElectricMotor extends EnergyConverter {
  private static final long serialVersionUID = 1000L;

  private ElectricMotorDriver performanceModel;
  private double configuredOperatingSpeed = Double.NaN;

  /**
   * Creates an electric motor with 95 percent efficiency.
   *
   * @param name equipment name
   */
  public ElectricMotor(String name) {
    super(name, EnergyType.ELECTRICAL, EnergyType.SHAFT_WORK);
    setEfficiency(0.95);
  }

  /**
   * Creates an electric motor.
   *
   * @param name equipment name
   * @param efficiency electrical-to-shaft efficiency
   */
  public ElectricMotor(String name, double efficiency) {
    this(name);
    setEfficiency(efficiency);
  }

  /**
   * Attaches a detailed electric-motor performance model.
   *
   * @param performanceModel rated motor/VFD performance model
   */
  public void setPerformanceModel(ElectricMotorDriver performanceModel) {
    ElectricMotorDriver model = Objects.requireNonNull(performanceModel, "performanceModel cannot be null");
    if (!Double.isFinite(model.getRatedPower()) || model.getRatedPower() <= 0.0) {
      throw new IllegalArgumentException("Motor performance model must have positive finite rated power");
    }
    if (!Double.isFinite(model.getRatedSpeed()) || model.getRatedSpeed() <= 0.0) {
      throw new IllegalArgumentException("Motor performance model must have positive finite rated speed");
    }
    this.performanceModel = model;
  }

  /** Removes the detailed performance model and restores constant-efficiency behavior. */
  public void clearPerformanceModel() {
    performanceModel = null;
  }

  /**
   * Gets the attached performance model.
   *
   * @return performance model, or {@code null} when constant efficiency is active
   */
  public ElectricMotorDriver getPerformanceModel() {
    return performanceModel;
  }

  /**
   * Checks whether detailed motor performance is active.
   *
   * @return {@code true} when a driver model is attached
   */
  public boolean hasPerformanceModel() {
    return performanceModel != null;
  }

  /**
   * Sets operating speed used when no positive-speed {@link MechanicalShaft} is connected.
   *
   * @param speed operating speed in rpm
   */
  public void setOperatingSpeed(double speed) {
    if (!Double.isFinite(speed) || speed <= 0.0) {
      throw new IllegalArgumentException("Motor operating speed must be positive and finite");
    }
    configuredOperatingSpeed = speed;
  }

  /** Clears explicit speed so a connected shaft or the rated speed is used. */
  public void clearOperatingSpeed() {
    configuredOperatingSpeed = Double.NaN;
  }

  /**
   * Gets the resolved operating speed.
   *
   * @return connected shaft speed, configured speed, rated speed, or NaN without a detailed model
   */
  public double getOperatingSpeed() {
    EnergyPort outputPort = getEnergyPort(OUTPUT_PORT);
    if (outputPort.isConnected()) {
      EnergyStream outputStream = outputPort.getEnergyStream();
      if (outputStream instanceof MechanicalShaft && ((MechanicalShaft) outputStream).getSpeed() > 0.0) {
        return ((MechanicalShaft) outputStream).getSpeed();
      }
    }
    if (Double.isFinite(configuredOperatingSpeed)) {
      return configuredOperatingSpeed;
    }
    return performanceModel == null ? Double.NaN : performanceModel.getRatedSpeed();
  }

  /**
   * Gets currently available mechanical output capacity.
   *
   * @return available shaft power in W, or positive infinity for the constant-efficiency model
   */
  public double getAvailableShaftPower() {
    if (performanceModel == null) {
      return Double.POSITIVE_INFINITY;
    }
    return getAvailableShaftPower(getOperatingSpeed());
  }

  /**
   * Gets motor efficiency at a requested shaft output.
   *
   * @param outputPower requested shaft output in W
   * @return performance-model efficiency, or nominal constant efficiency
   */
  public double getEfficiencyAtOutputPower(double outputPower) {
    if (!Double.isFinite(outputPower) || outputPower < 0.0) {
      throw new IllegalArgumentException("Motor output power must be non-negative and finite");
    }
    if (performanceModel == null) {
      return getEfficiency();
    }
    if (outputPower == 0.0) {
      return 0.0;
    }
    double speed = getOperatingSpeed();
    double ratedAvailablePower = performanceModel.getAvailablePower(speed) * 1000.0;
    if (ratedAvailablePower <= 0.0) {
      return 0.0;
    }
    double loadFraction = Math.min(1.2, outputPower / ratedAvailablePower);
    return performanceModel.getEfficiency(speed, loadFraction);
  }

  /** {@inheritDoc} */
  @Override
  protected double calculateTargetOutput(double input) {
    if (performanceModel == null) {
      return super.calculateTargetOutput(input);
    }
    if (isTripped() || input <= getIdleLoss()) {
      return 0.0;
    }

    double availableOutput = getAvailableShaftPower();
    if (!Double.isFinite(availableOutput) || availableOutput <= 0.0) {
      return 0.0;
    }
    if (calculateRequiredInputForOutput(availableOutput) <= input) {
      return availableOutput;
    }

    double lowerOutput = 0.0;
    double upperOutput = availableOutput;
    for (int iteration = 0; iteration < 60; iteration++) {
      double trialOutput = 0.5 * (lowerOutput + upperOutput);
      if (calculateRequiredInputForOutput(trialOutput) <= input) {
        lowerOutput = trialOutput;
      } else {
        upperOutput = trialOutput;
      }
    }
    return lowerOutput;
  }

  /** {@inheritDoc} */
  @Override
  protected double calculateRequiredInputForOutput(double output) {
    if (performanceModel == null) {
      return super.calculateRequiredInputForOutput(output);
    }
    if (output <= 0.0) {
      return 0.0;
    }

    double speed = getOperatingSpeed();
    double availableOutput = getAvailableShaftPower(speed);
    if (output > availableOutput + Math.max(1.0e-6, availableOutput * 1.0e-10)) {
      throw new IllegalArgumentException("Requested shaft power exceeds motor capability at the operating speed");
    }
    double efficiency = getEfficiencyAtOutputPower(output);
    if (!Double.isFinite(efficiency) || efficiency <= 0.0 || efficiency > 1.0) {
      throw new IllegalStateException("Motor performance model returned invalid efficiency at the requested load");
    }
    return output / efficiency + getIdleLoss();
  }

  /** Gets derated shaft capability at one speed. */
  private double getAvailableShaftPower(double speed) {
    if (!Double.isFinite(speed) || speed <= 0.0) {
      return 0.0;
    }
    double availablePower = performanceModel.getAvailablePower(speed) * 1000.0;
    double derating = getCombinedEnvironmentalDeratingFactor();
    if (!Double.isFinite(availablePower) || !Double.isFinite(derating)) {
      throw new IllegalStateException("Motor performance model returned non-finite capability");
    }
    return Math.max(0.0, availablePower * derating);
  }

  /**
   * Gets combined ambient-temperature and altitude derating.
   *
   * <p>
   * The legacy driver returns early below 40 degrees Celsius, which omits altitude derating. The energy-network motor
   * preserves that driver behavior for temperature while applying the missing altitude factor whenever the early return
   * is active.
   * </p>
   */
  private double getCombinedEnvironmentalDeratingFactor() {
    double derating = performanceModel.getAmbientDeratingFactor();
    if (performanceModel.getAmbientTemperature() <= 40.0 && performanceModel.getAltitude() > 1000.0) {
      double altitudeDerating = 1.0 - 0.01 * (performanceModel.getAltitude() - 1000.0) / 100.0;
      derating *= altitudeDerating;
    }
    return Math.max(0.5, Math.min(1.0, derating));
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = super.validateSetup();
    if (performanceModel != null) {
      double speed = getOperatingSpeed();
      if (!Double.isFinite(speed) || speed <= 0.0) {
        result.addError("energy", "Motor operating speed is not available",
            "Set shaft speed, call setOperatingSpeed(rpm), or configure a rated motor speed");
      } else if (performanceModel.getAvailablePower(speed) <= 0.0) {
        result.addError("energy", "Motor cannot operate at the configured shaft speed",
            "Select a valid fixed-speed point or configure the VFD speed range");
      }
    }
    return result;
  }
}
