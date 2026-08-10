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
 * transformers. Subclasses can override the protected conversion methods to provide load-dependent performance while
 * retaining common network, ramp, trip, and conservation behavior.
 * </p>
 *
 * @author NeqSim
 * @version 1.1
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
  /** Output power at the start of the current physical transient step. */
  private double transientStepStartOutputPower = 0.0;
  /** Physical transient-step identifier associated with {@link #transientStepStartOutputPower}. */
  private UUID transientStepIdentifier = null;

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
   * Gets nominal conversion efficiency.
   *
   * <p>
   * Subclasses with performance maps may use a different operating efficiency at the current load. Use
   * {@link #getOperatingEfficiency()} for the efficiency realized by the latest calculation.
   * </p>
   *
   * @return nominal efficiency from zero to one
   */
  public double getEfficiency() {
    return efficiency;
  }

  /**
   * Sets nominal conversion efficiency.
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
   * Gets the current realized input power.
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
   * Gets realized total conversion efficiency from the latest calculation.
   *
   * @return output divided by realized input, or zero when no input is consumed
   */
  public double getOperatingEfficiency() {
    return currentInputPower > 0.0 ? currentOutputPower / currentInputPower : 0.0;
  }

  /**
   * Calculates input power required for a requested useful output.
   *
   * <p>
   * The default implementation uses nominal efficiency and idle loss. Subclasses may override the protected inverse
   * conversion method to apply load-, speed-, or ambient-dependent performance maps.
   * </p>
   *
   * @param outputPower requested useful output in W
   * @return required input in W
   * @throws IllegalArgumentException when the output is invalid or exceeds configured capability
   */
  public double getRequiredInputPowerForOutput(double outputPower) {
    if (!Double.isFinite(outputPower) || outputPower < 0.0) {
      throw new IllegalArgumentException("Requested output power must be non-negative and finite");
    }
    double requiredInput = calculateRequiredInputForOutput(outputPower);
    if (!Double.isFinite(requiredInput) || requiredInput < 0.0) {
      throw new IllegalStateException("Converter performance model returned invalid required input power");
    }
    if (Double.isFinite(maximumInputPower)
        && requiredInput > maximumInputPower + Math.max(1.0e-9, maximumInputPower * 1.0e-12)) {
      throw new IllegalArgumentException("Requested output exceeds the converter input-power capability");
    }
    return requiredInput;
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
    double availableInput = readAvailableInput();
    double output = calculateTargetOutput(availableInput);
    publish(calculateRealizedInput(availableInput, output), output);
    transientStepIdentifier = null;
    setCalculationIdentifier(id);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * A non-null calculation identifier represents one physical transient step. Repeated nonlinear/refinement evaluations
   * with the same identifier recompute the ramped output from the output power that existed at the start of that
   * physical step; they do not integrate the ramp or advance the converter clock a second time. A different identifier
   * starts the next physical step. Null identifiers preserve the legacy behavior where every direct call is treated as
   * a new physical step.
   * </p>
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!Double.isFinite(dt) || dt < 0.0) {
      throw new IllegalArgumentException("Converter timestep must be non-negative and finite");
    }

    boolean repeatedEvaluation = id != null && id.equals(transientStepIdentifier);
    if (!repeatedEvaluation) {
      transientStepStartOutputPower = currentOutputPower;
      transientStepIdentifier = id;
    }

    double availableInput = readAvailableInput();
    double targetOutput = calculateTargetOutput(availableInput);
    double maximumChange = rampRate * dt;
    double rampedOutput = targetOutput;
    if (Double.isFinite(maximumChange) && Math.abs(targetOutput - transientStepStartOutputPower) > maximumChange) {
      rampedOutput = transientStepStartOutputPower
          + Math.copySign(maximumChange, targetOutput - transientStepStartOutputPower);
    }

    // A memoryless converter cannot sustain a ramp-limited output using energy that is no longer available.
    // Ramp limits therefore constrain increases, while a loss of input immediately caps output at the
    // thermodynamically available target.
    double output = Math.min(targetOutput, Math.max(0.0, rampedOutput));
    publish(calculateRealizedInput(availableInput, output), output);
    if (!repeatedEvaluation) {
      increaseTime(dt);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Calculates useful steady-state output from available input.
   *
   * <p>
   * Subclasses can override this method to apply performance maps. Implementations must return a finite non-negative
   * output that can be supported by the supplied input.
   * </p>
   *
   * @param input available input in W
   * @return useful output in W
   */
  protected double calculateTargetOutput(double input) {
    if (tripped || input <= idleLoss) {
      return 0.0;
    }
    return (input - idleLoss) * efficiency;
  }

  /**
   * Calculates required input for useful output.
   *
   * @param output useful output in W
   * @return required input in W
   */
  protected double calculateRequiredInputForOutput(double output) {
    if (output <= 0.0) {
      return 0.0;
    }
    return output / efficiency + idleLoss;
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

  /** Calculates input actually consumed by the realized output. */
  private double calculateRealizedInput(double availableInput, double output) {
    if (output > 0.0) {
      return Math.min(availableInput, getRequiredInputPowerForOutput(output));
    }
    if (availableInput > 0.0 && availableInput <= idleLoss) {
      return availableInput;
    }
    return 0.0;
  }

  /**
   * Publishes converter results to connected output and loss ports.
   *
   * @param input realized input power in W
   * @param output useful output in W
   */
  private void publish(double input, double output) {
    if (!Double.isFinite(input) || input < 0.0 || !Double.isFinite(output) || output < 0.0 || output > input) {
      throw new IllegalStateException("Energy converter result violates finite non-negative energy conservation");
    }
    currentInputPower = input;
    currentOutputPower = output;
    heatLoss = currentInputPower - currentOutputPower;

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
    result.put("nominalEfficiency", efficiency);
    result.put("operatingEfficiency", getOperatingEfficiency());
    result.put("tripped", tripped);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(result);
  }
}
