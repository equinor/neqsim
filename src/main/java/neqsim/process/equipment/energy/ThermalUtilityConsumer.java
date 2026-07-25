package neqsim.process.equipment.energy;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyQuality;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.UtilityLevel;
import neqsim.util.validation.ValidationResult;

/**
 * Thermal utility consumer with a requested duty and explicit utility-grade requirement.
 *
 * @author NeqSim
 * @version 1.0
 */
public class ThermalUtilityConsumer extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;

  /** Thermal input port name. */
  public static final String INPUT_PORT = "utilityInput";

  private final UtilityLevel utilityLevel;
  private double allocatedPower = 0.0;

  /**
   * Creates a thermal utility consumer.
   *
   * @param name equipment name
   * @param utilityLevel required utility grade
   */
  public ThermalUtilityConsumer(String name, UtilityLevel utilityLevel) {
    super(name);
    if (utilityLevel == null || utilityLevel == UtilityLevel.UNSPECIFIED) {
      throw new IllegalArgumentException("A required thermal utility level is required");
    }
    this.utilityLevel = utilityLevel;
    registerEnergyPort(INPUT_PORT, EnergyType.HEAT, EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION);
    getEnergyPort(INPUT_PORT).setRequiredQuality(new EnergyQuality(utilityLevel));
  }

  /**
   * Sets requested thermal duty.
   *
   * @param requestedPower requested duty in W
   */
  public void setRequestedPower(double requestedPower) {
    getEnergyPort(INPUT_PORT).setRequestedPower(requestedPower);
  }

  /**
   * Gets allocated thermal duty.
   *
   * @return allocated duty in W
   */
  public double getAllocatedPower() {
    return allocatedPower;
  }

  /**
   * Gets required utility grade.
   *
   * @return utility grade
   */
  public UtilityLevel getUtilityLevel() {
    return utilityLevel;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    allocatedPower = getEnergyPort(INPUT_PORT).isConnected() ? getEnergyPort(INPUT_PORT).getPowerMagnitude() : 0.0;
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (!getEnergyPort(INPUT_PORT).isConnected()) {
      result.addError("energy", "Thermal utility input is not connected",
          "Connect " + INPUT_PORT + " to a " + utilityLevel + " UtilityEnergyBus");
    }
    return result;
  }
}
