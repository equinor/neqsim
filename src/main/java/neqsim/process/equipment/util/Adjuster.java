package neqsim.process.equipment.util;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Adjuster class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Adjuster extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Adjuster.class);

  ProcessEquipmentInterface adjustedEquipment = null;
  ProcessEquipmentInterface targetEquipment = null;

  String adjustedVariable = "";
  String adjustedVariableUnit = "";
  double maxAdjustedValue = 1e10;
  double minAdjustedValue = -1e10;
  String targetVariable = "";
  String targetPhase = "";
  String targetComponent = "";

  double targetValue = 0.0;
  String targetUnit = "";
  private double tolerance = 1e-6;
  double inputValue = 0.0;
  double oldInputValue = 0.0;
  private double error = 1e6;
  private double oldError = 1.0e6;
  int iterations = 0;
  private boolean activateWhenLess = false;
  private boolean active = true;
  private Function<ProcessEquipmentInterface, Double> targetValueCalculator;
  private Function<ProcessEquipmentInterface, Double> adjustedValueGetter;
  private BiConsumer<ProcessEquipmentInterface, Double> adjustedValueSetter;

  /**
   * <p>
   * Constructor for Adjuster.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Adjuster(String name) {
    super(name);
  }

  /**
   * Helper method to get a StreamInterface from equipment. If the equipment is a StreamInterface,
   * returns it directly. Otherwise, tries to get the outlet stream from TwoPortInterface or
   * MixerInterface.
   *
   * @param equipment the equipment to get stream from
   * @return StreamInterface or null if not available
   */
  private StreamInterface getStreamFromEquipment(ProcessEquipmentInterface equipment) {
    if (equipment instanceof StreamInterface) {
      return (StreamInterface) equipment;
    } else if (equipment instanceof TwoPortInterface) {
      return ((TwoPortInterface) equipment).getOutletStream();
    } else if (equipment instanceof MixerInterface) {
      return ((MixerInterface) equipment).getOutletStream();
    }
    return null;
  }

  /**
   * <p>
   * setAdjustedVariable.
   * </p>
   *
   * @param adjustedEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param adjstedVariable a {@link java.lang.String} object
   * @param unit a string
   */
  public void setAdjustedVariable(ProcessEquipmentInterface adjustedEquipment,
      String adjstedVariable, String unit) {
    this.adjustedEquipment = adjustedEquipment;
    this.adjustedVariable = adjstedVariable;
    this.adjustedVariableUnit = unit;
  }

  /**
   * <p>
   * setAdjustedVariable.
   * </p>
   *
   * @param adjustedEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param adjstedVariable a {@link java.lang.String} object
   */
  public void setAdjustedVariable(ProcessEquipmentInterface adjustedEquipment,
      String adjstedVariable) {
    this.adjustedEquipment = adjustedEquipment;
    this.adjustedVariable = adjstedVariable;
  }

  /**
   * <p>
   * setAdjustedVariable.
   * </p>
   *
   * @param adjustedEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setAdjustedVariable(ProcessEquipmentInterface adjustedEquipment) {
    this.adjustedEquipment = adjustedEquipment;
  }

  /**
   * <p>
   * Setter for the field <code>targetVariable</code>.
   * </p>
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment) {
    this.targetEquipment = targetEquipment;
  }

  /**
   * <p>
   * Setter for the field <code>targetVariable</code>.
   * </p>
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param targetVariable a {@link java.lang.String} object
   * @param targetValue a double
   * @param targetUnit a {@link java.lang.String} object
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable,
      double targetValue, String targetUnit) {
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
    this.targetValue = targetValue;
    this.targetUnit = targetUnit;
  }

  /**
   * <p>
   * Setter for the field <code>targetVariable</code>.
   * </p>
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param targetVariable a {@link java.lang.String} object
   * @param targetValue a double
   * @param targetUnit a {@link java.lang.String} object
   * @param targetPhase a {@link java.lang.String} object
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable,
      double targetValue, String targetUnit, String targetPhase) {
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
    this.targetValue = targetValue;
    this.targetUnit = targetUnit;
    this.targetPhase = targetPhase;
  }

  /**
   * <p>
   * Setter for the field <code>targetVariable</code>.
   * </p>
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param targetVariable a {@link java.lang.String} object
   * @param targetValue a double
   * @param targetUnit a {@link java.lang.String} object
   * @param targetPhase a {@link java.lang.String} object
   * @param targetComponent a {@link java.lang.String} object
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable,
      double targetValue, String targetUnit, String targetPhase, String targetComponent) {
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
    this.targetValue = targetValue;
    this.targetUnit = targetUnit;
    this.targetPhase = targetPhase;
    this.targetComponent = targetComponent;
  }

  /**
   * <p>
   * Setter for the field <code>targetValue</code>.
   * </p>
   *
   * @param targetValue a double
   */
  public void setTargetValue(double targetValue) {
    this.targetValue = targetValue;
  }

  /**
   * <p>
   * Setter for the field <code>adjustedEquipment</code>.
   * </p>
   *
   * @param adjustedEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setAdjustedEquipment(ProcessEquipmentInterface adjustedEquipment) {
    this.adjustedEquipment = adjustedEquipment;
  }

  /**
   * <p>
   * Setter for the field <code>targetEquipment</code>.
   * </p>
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setTargetEquipment(ProcessEquipmentInterface targetEquipment) {
    this.targetEquipment = targetEquipment;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Skip adjustment if not active
    if (!active) {
      setCalculationIdentifier(id);
      return;
    }

    oldError = error;

    // Get streams from equipment (handles both Stream and other equipment types)
    StreamInterface adjustedStream = getStreamFromEquipment(adjustedEquipment);
    StreamInterface targetStream = getStreamFromEquipment(targetEquipment);

    // Only require adjustedStream when NOT using custom functional interfaces
    if (adjustedStream == null && adjustedValueGetter == null) {
      logger.error("Adjuster: Cannot get stream from adjusted equipment: "
          + (adjustedEquipment != null ? adjustedEquipment.getName() : "null"));
      setCalculationIdentifier(id);
      return;
    }

    if (adjustedValueGetter != null) {
      inputValue = adjustedValueGetter.apply(adjustedEquipment);
    } else if (adjustedVariable.equals("mass flow")) {
      inputValue = adjustedStream.getThermoSystem().getFlowRate("kg/hr");
    } else if (adjustedVariable.equals("flow") && adjustedVariableUnit != null
        && !adjustedVariableUnit.isEmpty()) {
      inputValue = adjustedStream.getThermoSystem().getFlowRate(adjustedVariableUnit);
    } else if (adjustedVariable.equals("pressure") && adjustedVariableUnit != null
        && !adjustedVariableUnit.isEmpty()) {
      inputValue = adjustedStream.getPressure(adjustedVariableUnit);
    } else if (adjustedVariable.equals("temperature") && adjustedVariableUnit != null
        && !adjustedVariableUnit.isEmpty()) {
      inputValue = adjustedStream.getTemperature(adjustedVariableUnit);
    } else {
      inputValue = adjustedStream.getThermoSystem().getNumberOfMoles();
    }

    double targetValueCurrent = 0.0;
    if (targetValueCalculator != null) {
      targetValueCurrent = targetValueCalculator.apply(targetEquipment);
    } else if (targetStream == null) {
      logger.error("Adjuster: Cannot get stream from target equipment: "
          + (targetEquipment != null ? targetEquipment.getName() : "null"));
      setCalculationIdentifier(id);
      return;
    } else if (targetVariable.equals("mass fraction") && !targetPhase.equals("")
        && !targetComponent.equals("")) {
      targetValueCurrent =
          targetStream.getThermoSystem().getPhase(targetPhase).getWtFrac(targetComponent);
    } else if (targetVariable.equals("gasVolumeFlow")) {
      targetValueCurrent = targetStream.getThermoSystem().getFlowRate(targetUnit);
    } else if (targetVariable.equals("pressure")) {
      targetValueCurrent = targetStream.getThermoSystem().getPressure(targetUnit);
    } else {
      targetValueCurrent = targetStream.getThermoSystem().getVolume(targetUnit);
    }

    if (activateWhenLess && targetValueCurrent > targetValue) {
      error = 0.0;
      activateWhenLess = true;
      setCalculationIdentifier(id);
      return;
    }

    iterations++;
    double deviation = targetValue - targetValueCurrent;

    error = deviation;

    if (iterations >= 2 && Math.abs(inputValue - oldInputValue) < 1e-12) {
      if (Math.abs(error) < tolerance) {
        return;
      }
      iterations = 1;
    }

    if (iterations < 2) {
      if (adjustedValueSetter != null) {
        // For custom setter, we use a 1% perturbation in the direction of deviation
        double perturbation = inputValue * 0.01 * Math.signum(deviation);
        if (Math.abs(perturbation) < 1e-6) {
          perturbation = 1e-3 * Math.signum(deviation);
        }
        adjustedValueSetter.accept(adjustedEquipment, inputValue + perturbation);
      } else if (adjustedVariable.equals("mass flow")) {
        adjustedStream.getThermoSystem().setTotalFlowRate(inputValue + deviation, "kg/hr");
      } else if (adjustedVariable.equals("flow") && adjustedVariableUnit != null
          && !adjustedVariableUnit.isEmpty()) {
        adjustedStream.getThermoSystem().setTotalFlowRate(
            inputValue + Math.signum(deviation) * inputValue / 100.0, adjustedVariableUnit);
      } else if (adjustedVariable.equals("pressure") && adjustedVariableUnit != null
          && !adjustedVariableUnit.isEmpty()) {
        adjustedStream.setPressure(inputValue + deviation / 10.0, adjustedVariableUnit);
      } else if (adjustedVariable.equals("temperature") && adjustedVariableUnit != null
          && !adjustedVariableUnit.isEmpty()) {
        adjustedStream.setTemperature(inputValue + deviation / 10.0, adjustedVariableUnit);
      } else {
        adjustedStream.getThermoSystem().setTotalFlowRate(inputValue + deviation, "mol/sec");
      }
    } else {
      double derivate = (error - oldError) / (inputValue - oldInputValue);
      if (Math.abs(derivate) < 1e-12) {
        derivate = 1e-12;
      }
      double newVal = error / derivate;
      if (inputValue - newVal > maxAdjustedValue) {
        newVal = inputValue - maxAdjustedValue;
        if (Math.abs(oldInputValue - inputValue) < 1e-10) {
          error = tolerance * 0.9;
        }
      }
      if (inputValue - newVal < minAdjustedValue) {
        newVal = inputValue - minAdjustedValue;
        if (Math.abs(oldInputValue - inputValue) < 1e-10) {
          error = tolerance * 0.9;
        }
      }

      if (adjustedValueSetter != null) {
        adjustedValueSetter.accept(adjustedEquipment, inputValue - newVal);
      } else if (adjustedVariable.equals("mass flow")) {
        adjustedStream.getThermoSystem().setTotalFlowRate(inputValue - newVal, "kg/hr");
      } else if (adjustedVariable.equals("flow") && adjustedVariableUnit != null
          && !adjustedVariableUnit.isEmpty()) {
        adjustedStream.getThermoSystem().setTotalFlowRate(inputValue - newVal,
            adjustedVariableUnit);
      } else if (adjustedVariable.equals("pressure") && adjustedVariableUnit != null
          && !adjustedVariableUnit.isEmpty()) {
        adjustedStream.setPressure(inputValue - newVal, adjustedVariableUnit);
      } else if (adjustedVariable.equals("temperature") && adjustedVariableUnit != null
          && !adjustedVariableUnit.isEmpty()) {
        adjustedStream.setTemperature(inputValue - newVal, adjustedVariableUnit);
      } else {
        adjustedStream.getThermoSystem().setTotalFlowRate(inputValue - newVal, "mol/sec");
      }
    }

    oldInputValue = inputValue;

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    if (Math.abs(error) < tolerance) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Checks if this Adjuster is active.
   *
   * <p>
   * When inactive, the Adjuster will not perform any adjustments during process runs.
   * </p>
   *
   * @return true if the Adjuster is active, false otherwise
   */
  public boolean isActive() {
    return active;
  }

  /**
   * Sets the active state of this Adjuster.
   *
   * <p>
   * This can be used to temporarily disable an Adjuster during optimization to prevent it from
   * interfering with the optimization search.
   * </p>
   *
   * @param active true to enable the Adjuster, false to disable
   */
  public void setActive(boolean active) {
    this.active = active;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}

  /**
   * <p>
   * Getter for the field <code>tolerance</code>.
   * </p>
   *
   * @return a double
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * <p>
   * Setter for the field <code>tolerance</code>.
   * </p>
   *
   * @param tolerance the tolerance to set
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * <p>
   * Getter for the field <code>error</code>.
   * </p>
   *
   * @return the error
   */
  public double getError() {
    return error;
  }

  /**
   * <p>
   * Setter for the field <code>error</code>.
   * </p>
   *
   * @param error the error to set
   */
  public void setError(double error) {
    this.error = error;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // test code for adjuster...
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
    testSystem.addComponent("methane", 1000.00);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    Stream stream_1 = new Stream("Stream1", testSystem);
    Adjuster adjuster1 = new Adjuster("adjuster");
    adjuster1.setAdjustedVariable(stream_1, "molarFlow");
    adjuster1.setTargetVariable(stream_1, "gasVolumeFlow", 10.0, "MSm3/day", "");

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(adjuster1);

    operations.run();
  }

  /**
   * <p>
   * isActivateWhenLess.
   * </p>
   *
   * @return a boolean
   */
  public boolean isActivateWhenLess() {
    return activateWhenLess;
  }

  /**
   * <p>
   * Setter for the field <code>activateWhenLess</code>.
   * </p>
   *
   * @param activateWhenLess a boolean
   */
  public void setActivateWhenLess(boolean activateWhenLess) {
    this.activateWhenLess = activateWhenLess;
  }

  /**
   * <p>
   * Setter for the field <code>maxAdjustedValue</code>.
   * </p>
   *
   * @param maxVal a double
   */
  public void setMaxAdjustedValue(double maxVal) {
    maxAdjustedValue = maxVal;
    if (maxAdjustedValue < minAdjustedValue) {
      minAdjustedValue = maxAdjustedValue;
    }
  }

  /**
   * <p>
   * Setter for the field <code>minAdjustedValue</code>.
   * </p>
   *
   * @param minVal a double
   */
  public void setMinAdjustedValue(double minVal) {
    minAdjustedValue = minVal;
    if (minAdjustedValue > maxAdjustedValue) {
      maxAdjustedValue = minAdjustedValue;
    }
  }

  /**
   * <p>
   * Getter for the field <code>maxAdjustedValue</code>.
   * </p>
   *
   * @return a double
   */
  public double getMaxAdjustedValue() {
    return maxAdjustedValue;
  }

  /**
   * <p>
   * Getter for the field <code>minAdjustedValue</code>.
   * </p>
   *
   * @return a double
   */
  public double getMinAdjustedValue() {
    return minAdjustedValue;
  }

  /**
   * <p>
   * Setter for the field <code>targetValueCalculator</code>.
   * </p>
   *
   * @param targetValueCalculator a {@link java.util.function.Function} object
   */
  public void setTargetValueCalculator(
      Function<ProcessEquipmentInterface, Double> targetValueCalculator) {
    this.targetValueCalculator = targetValueCalculator;
  }

  /**
   * <p>
   * Setter for the field <code>targetValueCalculator</code>.
   * </p>
   *
   * @param targetValueCalculator a {@link java.util.function.Supplier} object
   */
  public void setTargetValueCalculator(Supplier<Double> targetValueCalculator) {
    this.targetValueCalculator = (eq) -> targetValueCalculator.get();
  }

  /**
   * <p>
   * Setter for the field <code>adjustedValueGetter</code>.
   * </p>
   *
   * @param adjustedValueGetter a {@link java.util.function.Function} object
   */
  public void setAdjustedValueGetter(
      Function<ProcessEquipmentInterface, Double> adjustedValueGetter) {
    this.adjustedValueGetter = adjustedValueGetter;
  }

  /**
   * <p>
   * Setter for the field <code>adjustedValueGetter</code>.
   * </p>
   *
   * @param adjustedValueGetter a {@link java.util.function.Supplier} object
   */
  public void setAdjustedValueGetter(Supplier<Double> adjustedValueGetter) {
    this.adjustedValueGetter = (eq) -> adjustedValueGetter.get();
  }

  /**
   * <p>
   * Setter for the field <code>adjustedValueSetter</code>.
   * </p>
   *
   * @param adjustedValueSetter a {@link java.util.function.BiConsumer} object
   */
  public void setAdjustedValueSetter(
      BiConsumer<ProcessEquipmentInterface, Double> adjustedValueSetter) {
    this.adjustedValueSetter = adjustedValueSetter;
  }

  /**
   * <p>
   * Setter for the field <code>adjustedValueSetter</code>.
   * </p>
   *
   * @param adjustedValueSetter a {@link java.util.function.Consumer} object
   */
  public void setAdjustedValueSetter(Consumer<Double> adjustedValueSetter) {
    this.adjustedValueSetter = (eq, val) -> adjustedValueSetter.accept(val);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates the adjuster setup before execution. Checks that:
   * <ul>
   * <li>Equipment has a valid name</li>
   * <li>Adjusted equipment is set</li>
   * <li>Target equipment or value is set</li>
   * <li>Tolerance is positive</li>
   * </ul>
   *
   * @return validation result with errors and warnings
   */
  @Override
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result =
        new neqsim.util.validation.ValidationResult(getName());

    // Check: Equipment has a valid name
    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Adjuster has no name",
          "Set adjuster name in constructor: new Adjuster(\"MyAdjuster\")");
    }

    // Check: Adjusted equipment is set
    if (adjustedEquipment == null) {
      result.addError("adjusted", "No adjusted equipment set",
          "Set adjusted equipment: adjuster.setAdjustedVariable(equipment, \"variableName\")");
    }

    // Check: Target equipment or calculator is set
    if (targetEquipment == null && targetValueCalculator == null) {
      result.addWarning("target", "No target equipment or value calculator set",
          "Set target: adjuster.setTargetVariable(equipment, \"variableName\", targetValue)");
    }

    // Check: Adjusted variable is specified (if not using custom getter/setter)
    if (adjustedEquipment != null && adjustedValueGetter == null
        && (adjustedVariable == null || adjustedVariable.trim().isEmpty())) {
      result.addWarning("adjusted", "Adjusted variable name not specified",
          "Specify variable: adjuster.setAdjustedVariable(equipment, \"variableName\")");
    }

    // Check: Target variable is specified (if not using custom calculator)
    if (targetEquipment != null && targetValueCalculator == null
        && (targetVariable == null || targetVariable.trim().isEmpty())) {
      result.addWarning("target", "Target variable name not specified",
          "Specify variable: adjuster.setTargetVariable(equipment, \"variableName\", value)");
    }

    // Check: Tolerance is positive
    if (tolerance <= 0) {
      result.addError("tolerance", "Tolerance must be positive: " + tolerance,
          "Set positive tolerance: adjuster.setTolerance(1e-6)");
    }

    // Check: Min/max adjusted value bounds are consistent
    if (maxAdjustedValue <= minAdjustedValue) {
      result.addWarning("bounds",
          "Max adjusted value (" + maxAdjustedValue + ") <= min adjusted value (" + minAdjustedValue
              + ")",
          "Set consistent bounds: adjuster.setMaxAdjustedValue(max); adjuster.setMinAdjustedValue(min)");
    }

    return result;
  }
}
