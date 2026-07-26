package neqsim.process.equipment.energy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.util.validation.ValidationResult;

/**
 * Generic two-domain energy conversion equipment with efficiency, capacity, ramp, loss, and trip behavior.
 *
 * <p>
 * The input is a specification port, the useful output is a calculated port, and conversion losses are available as a
 * calculated heat port. This common implementation is used by motors, generators, gearboxes, inverters, and
 * transformers.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class EnergyConverter extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;

  /** Input port name. */
  public static final String INPUT_PORT = "energyInput";
  /** Useful output port name. */
  public static final String OUTPUT_PORT = "energyOutput";
  /** Loss heat port name. */
  public static final String LOSS_PORT = "heatLoss";

  private final EnergyType inputType;
  private final EnergyType outputType;
  private double efficiency = 1.0;
  private double maximumInputPower = Double.POSITIVE_INFINITY;
  private double idleLoss = 0.0;
  private double rampRate = Double.POSITIVE_INFINITY;
  private double currentInputPower = 0.0;
  private double currentOutputPower = 0.0;
  private double heatLoss = 0.0;
  private boolean tripped = false;

  /**
   * Creates an energy converter.
   *
   * @param name equipment name
   * @param inputType input energy domain
   * @param outputType output energy domain
   */
  public EnergyConverter(String name, EnergyType inputType, EnergyType outputType) {
    super(name);
    if (inputType == null || outputType == null) {
      throw new IllegalArgumentException("Input and output energy types are required");
    }
    this.inputType = inputType;
    this.outputType = outputType;
    registerEnergyPort(INPUT_PORT, inputType, EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION);
    registerEnergyPort(OUTPUT_PORT, outputType, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
    registerEnergyPort(LOSS_PORT, EnergyType.HEAT, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
  }

  /**
   * Gets the input energy domain.
   *
   * @return input energy type
   */
  public EnergyType getInputType() {
    return inputType;
  }

  /**
   * Gets the output energy domain.
   *
   * @return output energy type
   */
  public EnergyType getOutputType() {
    return outputType;
  }

  /**
   * Gets conversion efficiency.
   *
   * @return efficiency from zero to one
   */
  public double getEfficiency() {
    return efficiency;
  }

  /**
   * Sets conversion efficiency.
   *
   * @param efficiency efficiency greater than zero and at most one
   */
  public void setEfficiency(double efficiency) {
    if (!Double.isFinite(efficiency) || efficiency <= 0.0 || efficiency > 1.0) {
      throw new IllegalArgumentException("Efficiency must be greater than zero and at most one");
    }
    this.efficiency = efficiency;
  }

  /**
   * Gets maximum input power.
   *
   * @return maximum input in W
   */
  public double getMaximumInputPower() {
    return maximumInputPower;
  }

  /**
   * Sets maximum input power.
   *
   * @param maximumInputPower maximum input in W
   */
  public void setMaximumInputPower(double maximumInputPower) {
    if (Double.isNaN(maximumInputPower) || maximumInputPower <= 0.0) {
      throw new IllegalArgumentException("Maximum input power must be positive");
    }
    this.maximumInputPower = maximumInputPower;
    getEnergyPort(INPUT_PORT).setPowerLimits(0.0, maximumInputPower);
  }

  /**
   * Gets fixed idle loss.
   *
   * @return idle loss in W
   */
  public double getIdleLoss() {
    return idleLoss;
  }

  /**
   * Sets fixed idle loss.
   *
   * @param idleLoss idle loss in W
   */
  public void setIdleLoss(double idleLoss) {
    if (!Double.isFinite(idleLoss) || idleLoss < 0.0) {
      throw new IllegalArgumentException("Idle loss must be non-negative and finite");
    }
    this.idleLoss = idleLoss;
  }

  /**
   * Gets output ramp rate.
   *
   * @return ramp rate in W/s
   */
  public double getRampRate() {
    return rampRate;
  }

  /**
   * Sets output ramp rate.
   *
   * @param rampRate ramp rate in W/s; positive infinity disables the limit
   */
  public void setRampRate(double rampRate) {
    if (Double.isNaN(rampRate) || rampRate <= 0.0) {
      throw new IllegalArgumentException("Ramp rate must be positive");
    }
    this.rampRate = rampRate;
  }

  /**
   * Sets requested input power for the upstream network solver.
   *
   * @param power requested input in W
   */
  public void setRequestedInputPower(double power) {
    getEnergyPort(INPUT_PORT).setRequestedPower(power);
  }

  /**
   * Gets the current allocated input power.
   *
   * @return input power in W
   */
  public double getInputPower() {
    return currentInputPower;
  }

  /**
   * Gets useful output power.
   *
   * @return output power in W
   */
  public double getOutputPower() {
    return currentOutputPower;
  }

  /**
   * Gets conversion heat loss.
   *
   * @return loss in W
   */
  public double getHeatLoss() {
    return heatLoss;
  }

  /**
   * Checks whether the converter is tripped.
   *
   * @return {@code true} when tripped
   */
  public boolean isTripped() {
    return tripped;
  }

  /**
   * Trips or resets the converter.
   *
   * @param tripped trip state
   */
  public void setTripped(boolean tripped) {
    this.tripped = tripped;
    if (tripped) {
      getEnergyPort(INPUT_PORT).setRequestedPower(0.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double input = readAvailableInput();
    publish(input, calculateTargetOutput(input));
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    double input = readAvailableInput();
    double targetOutput = calculateTargetOutput(input);
    double maximumChange = rampRate * Math.max(0.0, dt);
    double output = targetOutput;
    if (Double.isFinite(maximumChange) && Math.abs(targetOutput - currentOutputPower) > maximumChange) {
      output = currentOutputPower + Math.copySign(maximumChange, targetOutput - currentOutputPower);
    }
    double effectiveInput = output > 0.0 ? Math.min(input, output / efficiency + idleLoss) : 0.0;
    publish(effectiveInput, output);
    increaseTime(dt);
    setCalculationIdentifier(id);
  }

  /**
   * Reads bounded allocated input power.
   *
   * @return available input in W
   */
  private double readAvailableInput() {
    if (tripped || !getEnergyPort(INPUT_PORT).isConnected()) {
      return 0.0;
    }
    return Math.min(maximumInputPower, getEnergyPort(INPUT_PORT).getPowerMagnitude());
  }

  /**
   * Calculates useful steady-state output.
   *
   * @param input input power in W
   * @return useful output in W
   */
  private double calculateTargetOutput(double input) {
    if (tripped || input <= idleLoss) {
      return 0.0;
    }
    return (input - idleLoss) * efficiency;
  }

  /**
   * Publishes converter results to connected output and loss ports.
   *
   * @param input input power in W
   * @param output useful output power in W
   */
  private void publish(double input, double output) {
    currentInputPower = Math.max(0.0, input);
    currentOutputPower = Math.max(0.0, output);
    heatLoss = Math.max(0.0, currentInputPower - currentOutputPower);

    EnergyPort outputPort = getEnergyPort(OUTPUT_PORT);
    outputPort.setConversionLoss(heatLoss);
    if (outputPort.isConnected()) {
      outputPort.setDuty(currentOutputPower);
    }
    EnergyPort lossPort = getEnergyPort(LOSS_PORT);
    if (lossPort.isConnected()) {
      lossPort.setDuty(heatLoss);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Energy converter has no name", "Set a name in the constructor");
    }
    if (!getEnergyPort(INPUT_PORT).isConnected()) {
      result.addError("energy", "Input energy port is not connected",
          "Connect an " + inputType + " stream to " + INPUT_PORT);
    }
    if (!getEnergyPort(OUTPUT_PORT).isConnected()) {
      result.addWarning("energy", "Output energy port is not connected",
          "Connect an " + outputType + " stream to " + OUTPUT_PORT);
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("name", getName());
    result.put("inputType", inputType);
    result.put("outputType", outputType);
    result.put("inputPowerW", currentInputPower);
    result.put("outputPowerW", currentOutputPower);
    result.put("heatLossW", heatLoss);
    result.put("efficiency", efficiency);
    result.put("tripped", tripped);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(result);
  }
}
