package neqsim.process.equipment.energy;

import java.io.Serializable;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.MechanicalShaft;
import neqsim.util.validation.ValidationResult;

/**
 * Convenience coupling for an electrically driven compressor, pump, or other unit exposing a {@code shaftPower} port.
 *
 * <p>
 * The class connects an electrical bus to an {@link ElectricMotor}, the motor to a mechanical shaft, and that shaft to
 * the driven equipment. The motor and driven equipment remain ordinary process units and should both be added to the
 * process system.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class MotorDriveTrain implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ElectricMotor motor;
  private final ProcessEquipmentBaseClass drivenEquipment;
  private final EnergyBus electricalBus;
  private final MechanicalShaft shaft;

  /**
   * Creates and connects a motor drive train.
   *
   * @param motor electric motor process unit
   * @param drivenEquipment equipment exposing a shaftPower energy port
   * @param electricalBus electrical supply bus
   * @param shaft mechanical shaft bus
   */
  public MotorDriveTrain(ElectricMotor motor, ProcessEquipmentBaseClass drivenEquipment, EnergyBus electricalBus,
      MechanicalShaft shaft) {
    if (motor == null || drivenEquipment == null || electricalBus == null || shaft == null) {
      throw new IllegalArgumentException("Motor, driven equipment, electrical bus, and shaft are required");
    }
    if (drivenEquipment.getEnergyPort("shaftPower") == null) {
      throw new IllegalArgumentException("Driven equipment must expose a shaftPower energy port");
    }
    this.motor = motor;
    this.drivenEquipment = drivenEquipment;
    this.electricalBus = electricalBus;
    this.shaft = shaft;

    motor.connectEnergyStream(EnergyConverter.INPUT_PORT, electricalBus, EnergyPortMode.SPECIFICATION);
    motor.connectEnergyStream(EnergyConverter.OUTPUT_PORT, shaft, EnergyPortMode.CALCULATED);
    drivenEquipment.connectEnergyStream("shaftPower", shaft, EnergyPortMode.SPECIFICATION);
  }

  /**
   * Sets requested useful shaft power and calculates the motor electrical request.
   *
   * @param shaftPower requested shaft power in W
   */
  public void setRequestedShaftPower(double shaftPower) {
    if (!Double.isFinite(shaftPower) || shaftPower < 0.0) {
      throw new IllegalArgumentException("Requested shaft power must be non-negative and finite");
    }
    drivenEquipment.getEnergyPort("shaftPower").setRequestedPower(shaftPower);
    motor.setRequestedInputPower(motor.getRequiredInputPowerForOutput(shaftPower));
  }

  /**
   * Gets the motor.
   *
   * @return motor process unit
   */
  public ElectricMotor getMotor() {
    return motor;
  }

  /**
   * Gets driven equipment.
   *
   * @return driven equipment
   */
  public ProcessEquipmentBaseClass getDrivenEquipment() {
    return drivenEquipment;
  }

  /**
   * Gets electrical bus.
   *
   * @return electrical bus
   */
  public EnergyBus getElectricalBus() {
    return electricalBus;
  }

  /**
   * Gets mechanical shaft.
   *
   * @return shaft bus
   */
  public MechanicalShaft getShaft() {
    return shaft;
  }

  /**
   * Validates the connected drive train.
   *
   * @return validation result with actionable diagnostics
   */
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(drivenEquipment.getName() + " motor drive");
    if (motor.getEnergyPort(EnergyConverter.INPUT_PORT).getEnergyStream() != electricalBus) {
      result.addError("energy", "Motor is not connected to the configured electrical bus",
          "Reconnect motor energyInput to the electrical bus");
    }
    if (motor.getEnergyPort(EnergyConverter.OUTPUT_PORT).getEnergyStream() != shaft) {
      result.addError("energy", "Motor is not connected to the configured shaft",
          "Reconnect motor energyOutput to the mechanical shaft");
    }
    if (drivenEquipment.getEnergyPort("shaftPower").getEnergyStream() != shaft) {
      result.addError("energy", "Driven equipment is not connected to the configured shaft",
          "Reconnect shaftPower to the mechanical shaft");
    }
    return result;
  }
}
