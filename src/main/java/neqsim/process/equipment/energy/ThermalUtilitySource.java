package neqsim.process.equipment.energy;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.UtilityLevel;
import neqsim.util.validation.ValidationResult;

/**
 * Dispatchable source of a standard thermal utility such as steam, hot oil, cooling water, or refrigeration.
 *
 * @author NeqSim
 * @version 1.0
 */
public class ThermalUtilitySource extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;

  /** Thermal output port name. */
  public static final String OUTPUT_PORT = "utilityOutput";

  private final UtilityLevel utilityLevel;
  private double availablePower = 0.0;

  /**
   * Creates a thermal utility source.
   *
   * @param name equipment name
   * @param utilityLevel utility grade
   */
  public ThermalUtilitySource(String name, UtilityLevel utilityLevel) {
    super(name);
    if (utilityLevel == null || utilityLevel == UtilityLevel.UNSPECIFIED) {
      throw new IllegalArgumentException("A thermal utility level is required");
    }
    this.utilityLevel = utilityLevel;
    registerEnergyPort(OUTPUT_PORT, EnergyType.HEAT, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
  }

  /**
   * Gets utility grade.
   *
   * @return utility grade
   */
  public UtilityLevel getUtilityLevel() {
    return utilityLevel;
  }

  /**
   * Gets available utility power.
   *
   * @return available power in W
   */
  public double getAvailablePower() {
    return availablePower;
  }

  /**
   * Sets available utility power.
   *
   * @param availablePower available power in W
   */
  public void setAvailablePower(double availablePower) {
    if (!Double.isFinite(availablePower) || availablePower < 0.0) {
      throw new IllegalArgumentException("Available power must be non-negative and finite");
    }
    this.availablePower = availablePower;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (getEnergyPort(OUTPUT_PORT).isConnected()) {
      getEnergyPort(OUTPUT_PORT).setDuty(availablePower);
    }
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (!getEnergyPort(OUTPUT_PORT).isConnected()) {
      result.addError("energy", "Thermal utility output is not connected",
          "Connect " + OUTPUT_PORT + " to a UtilityEnergyBus");
    } else if (!(getEnergyPort(OUTPUT_PORT).getEnergyStream() instanceof UtilityEnergyBus)) {
      result.addError("energy", "Thermal utility output is not connected to a typed UtilityEnergyBus",
          "Connect " + OUTPUT_PORT + " to a " + utilityLevel + " UtilityEnergyBus");
    } else if (getEnergyPort(OUTPUT_PORT).getEnergyStream() instanceof UtilityEnergyBus) {
      UtilityEnergyBus bus = (UtilityEnergyBus) getEnergyPort(OUTPUT_PORT).getEnergyStream();
      if (bus.getUtilityLevel() != utilityLevel) {
        result.addError("energy", "Utility level does not match connected bus",
            "Connect the source to a " + utilityLevel + " bus");
      }
    }
    return result;
  }
}
