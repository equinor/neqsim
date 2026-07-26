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
 * Thermal utility consumer with a requested duty, utility-grade requirement, and optional temperature feasibility rule.
 *
 * @author NeqSim
 * @version 2.0
 */
public class ThermalUtilityConsumer extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;

  /** Thermal input port name. */
  public static final String INPUT_PORT = "utilityInput";

  private final UtilityLevel utilityLevel;
  private double allocatedPower = 0.0;
  private double requiredProcessTemperature = Double.NaN;
  private double minimumApproachTemperature = 0.0;

  /**
   * Creates a thermal utility consumer.
   *
   * @param name equipment name
   * @param utilityLevel required utility grade
   */
  public ThermalUtilityConsumer(String name, UtilityLevel utilityLevel) {
    super(name);
    if (utilityLevel == null || utilityLevel == UtilityLevel.UNSPECIFIED) {
      throw new IllegalArgumentException("A thermal utility level is required");
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

  /**
   * Sets the process temperature and minimum utility approach that must be feasible.
   *
   * <p>
   * A heating utility must have supply temperature at least {@code processTemperature + minimumApproachTemperature}. A
   * cooling utility must have supply temperature at most {@code processTemperature - minimumApproachTemperature}.
   * </p>
   *
   * @param processTemperature process-side target temperature in K
   * @param minimumApproachTemperature minimum approach temperature in K
   */
  public void setProcessTemperatureRequirement(double processTemperature, double minimumApproachTemperature) {
    if (!Double.isFinite(processTemperature) || processTemperature <= 0.0) {
      throw new IllegalArgumentException("Process temperature must be positive and finite");
    }
    if (!Double.isFinite(minimumApproachTemperature) || minimumApproachTemperature < 0.0) {
      throw new IllegalArgumentException("Minimum approach temperature must be non-negative and finite");
    }
    requiredProcessTemperature = processTemperature;
    this.minimumApproachTemperature = minimumApproachTemperature;
  }

  /** Clears the process-temperature feasibility requirement. */
  public void clearProcessTemperatureRequirement() {
    requiredProcessTemperature = Double.NaN;
    minimumApproachTemperature = 0.0;
  }

  /**
   * Checks whether a process-temperature requirement is configured.
   *
   * @return {@code true} when configured
   */
  public boolean hasProcessTemperatureRequirement() {
    return Double.isFinite(requiredProcessTemperature);
  }

  /**
   * Gets required process temperature.
   *
   * @return temperature in K, or NaN when unspecified
   */
  public double getRequiredProcessTemperature() {
    return requiredProcessTemperature;
  }

  /**
   * Gets minimum utility approach temperature.
   *
   * @return approach in K
   */
  public double getMinimumApproachTemperature() {
    return minimumApproachTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (hasProcessTemperatureRequirement() && getEnergyPort(INPUT_PORT).isConnected()) {
      if (!(getEnergyPort(INPUT_PORT).getEnergyStream() instanceof UtilityEnergyBus)) {
        throw new IllegalStateException("Temperature-constrained utility consumers require a UtilityEnergyBus");
      }
      ThermalUtilityQualityAnalysis.requireFeasibleProcessTemperature(
          (UtilityEnergyBus) getEnergyPort(INPUT_PORT).getEnergyStream(), requiredProcessTemperature,
          minimumApproachTemperature);
    }
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
    } else if (!(getEnergyPort(INPUT_PORT).getEnergyStream() instanceof UtilityEnergyBus)) {
      result.addError("energy", "Thermal utility input is not connected to a typed UtilityEnergyBus",
          "Connect " + INPUT_PORT + " to a " + utilityLevel + " UtilityEnergyBus");
    } else {
      UtilityEnergyBus utilityBus = (UtilityEnergyBus) getEnergyPort(INPUT_PORT).getEnergyStream();
      if (utilityBus.getUtilityLevel() != utilityLevel) {
        result.addError("energy", "Connected utility level does not satisfy the consumer requirement",
            "Connect " + INPUT_PORT + " to a " + utilityLevel + " UtilityEnergyBus");
      } else if (hasProcessTemperatureRequirement() && !ThermalUtilityQualityAnalysis
          .canServeProcessTemperature(utilityBus, requiredProcessTemperature, minimumApproachTemperature)) {
        result.addError("energy", "Connected utility temperature grade cannot satisfy the process requirement",
            "Select a hotter heating utility, a colder cooling utility, or reduce the minimum approach temperature");
      }
    }
    return result;
  }
}
