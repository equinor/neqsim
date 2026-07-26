package neqsim.process.equipment.energy;

import java.io.Serializable;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.MechanicalShaft;
import neqsim.util.validation.ValidationResult;

/**
 * Expander-driven compressor train with supplemental electric-motor power on a common shaft.
 *
 * <p>
 * The expander and motor publish calculated shaft power while the compressor receives a deterministic shaft-power
 * allocation. When recovered expander power is insufficient, the motor contribution supplies the configured assist.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class MotorAssistedDriveTrain implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Expander expander;
  private final Compressor compressor;
  private final ElectricMotor assistMotor;
  private final EnergyBus electricalBus;
  private final MechanicalShaft shaft;

  /**
   * Creates and connects a motor-assisted expander/compressor train.
   *
   * @param expander shaft-power producer
   * @param compressor shaft-power consumer
   * @param assistMotor supplemental electric motor
   * @param electricalBus motor supply bus
   * @param shaft common shaft bus
   */
  public MotorAssistedDriveTrain(Expander expander, Compressor compressor, ElectricMotor assistMotor,
      EnergyBus electricalBus, MechanicalShaft shaft) {
    if (expander == null || compressor == null || assistMotor == null || electricalBus == null || shaft == null) {
      throw new IllegalArgumentException("Expander, compressor, assist motor, electrical bus, and shaft are required");
    }
    this.expander = expander;
    this.compressor = compressor;
    this.assistMotor = assistMotor;
    this.electricalBus = electricalBus;
    this.shaft = shaft;

    expander.connectEnergyStream("shaftPower", shaft, EnergyPortMode.CALCULATED);
    assistMotor.connectEnergyStream(EnergyConverter.INPUT_PORT, electricalBus, EnergyPortMode.SPECIFICATION);
    assistMotor.connectEnergyStream(EnergyConverter.OUTPUT_PORT, shaft, EnergyPortMode.CALCULATED);
    compressor.connectEnergyStream("shaftPower", shaft, EnergyPortMode.SPECIFICATION);
  }

  /**
   * Sets compressor demand and maximum planned motor assist.
   *
   * @param compressorPower requested compressor shaft power in W
   * @param motorAssistPower planned supplemental shaft power in W
   */
  public void setPowerTargets(double compressorPower, double motorAssistPower) {
    if (!Double.isFinite(compressorPower) || compressorPower < 0.0 || !Double.isFinite(motorAssistPower)
        || motorAssistPower < 0.0) {
      throw new IllegalArgumentException("Drive-train power targets must be non-negative and finite");
    }
    compressor.getEnergyPort("shaftPower").setRequestedPower(compressorPower);
    assistMotor.setRequestedInputPower(assistMotor.getRequiredInputPowerForOutput(motorAssistPower));
  }

  /**
   * Gets common shaft.
   *
   * @return shaft bus
   */
  public MechanicalShaft getShaft() {
    return shaft;
  }

  /**
   * Gets assist motor.
   *
   * @return assist motor
   */
  public ElectricMotor getAssistMotor() {
    return assistMotor;
  }

  /**
   * Validates the connected train.
   *
   * @return validation result
   */
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(compressor.getName() + " assisted drive");
    if (expander.getEnergyPort("shaftPower").getEnergyStream() != shaft) {
      result.addError("energy", "Expander is not connected to the common shaft",
          "Reconnect expander shaftPower to the common shaft");
    }
    if (compressor.getEnergyPort("shaftPower").getEnergyStream() != shaft) {
      result.addError("energy", "Compressor is not connected to the common shaft",
          "Reconnect compressor shaftPower to the common shaft");
    }
    if (assistMotor.getEnergyPort(EnergyConverter.INPUT_PORT).getEnergyStream() != electricalBus) {
      result.addError("energy", "Assist motor is not connected to the electrical bus",
          "Reconnect motor energyInput to the electrical bus");
    }
    if (assistMotor.getEnergyPort(EnergyConverter.OUTPUT_PORT).getEnergyStream() != shaft) {
      result.addError("energy", "Assist motor is not connected to the common shaft",
          "Reconnect motor energyOutput to the common shaft");
    }
    return result;
  }
}
