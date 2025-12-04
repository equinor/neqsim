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
import neqsim.process.equipment.stream.Stream;
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
    oldError = error;

    if (adjustedValueGetter != null) {
      inputValue = adjustedValueGetter.apply(adjustedEquipment);
    } else if (adjustedVariable.equals("mass flow")) {
      inputValue = ((Stream) adjustedEquipment).getThermoSystem().getFlowRate("kg/hr");
    } else if (adjustedVariable.equals("flow") && adjustedVariableUnit != null) {
      inputValue = ((Stream) adjustedEquipment).getThermoSystem().getFlowRate(adjustedVariableUnit);
    } else if (adjustedVariable.equals("pressure") && adjustedVariableUnit != null) {
      inputValue = ((Stream) adjustedEquipment).getPressure(adjustedVariableUnit);
    } else if (adjustedVariable.equals("temperature") && adjustedVariableUnit != null) {
    } else {
      inputValue = ((Stream) adjustedEquipment).getThermoSystem().getNumberOfMoles();
    }

    double targetValueCurrent = 0.0;
    if (targetValueCalculator != null) {
      targetValueCurrent = targetValueCalculator.apply(targetEquipment);
    } else if (targetVariable.equals("mass fraction") && !targetPhase.equals("")
        && !targetComponent.equals("")) {
      targetValueCurrent = ((Stream) targetEquipment).getThermoSystem().getPhase(targetPhase)
          .getWtFrac(targetComponent);
    } else if (targetVariable.equals("gasVolumeFlow")) {
    } else if (targetVariable.equals("gasVolumeFlow")) {
      targetValueCurrent = ((Stream) targetEquipment).getThermoSystem().getFlowRate(targetUnit);
    } else if (targetVariable.equals("pressure")) {
      targetValueCurrent = ((Stream) targetEquipment).getThermoSystem().getPressure(targetUnit);
    } else {
      targetValueCurrent = ((Stream) targetEquipment).getThermoSystem().getVolume(targetUnit);
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

    if (iterations < 2) {
      if (adjustedValueSetter != null) {
        // For custom setter, we use a 1% perturbation in the direction of deviation
        double perturbation = inputValue * 0.01 * Math.signum(deviation);
        if (Math.abs(perturbation) < 1e-6) {
          perturbation = 1e-3 * Math.signum(deviation);
        }
        adjustedValueSetter.accept(adjustedEquipment, inputValue + perturbation);
      } else if (adjustedVariable.equals("mass flow")) {
        ((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(inputValue + deviation,
            "kg/hr");
      } else if (adjustedVariable.equals("flow") && adjustedVariableUnit != null) {
        ((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(
            inputValue + Math.signum(deviation) * inputValue / 100.0, adjustedVariableUnit);
      } else if (adjustedVariable.equals("pressure") && adjustedVariableUnit != null) {
        ((Stream) adjustedEquipment).setPressure(inputValue + deviation / 10.0,
            adjustedVariableUnit);
      } else if (adjustedVariable.equals("temperature") && adjustedVariableUnit != null) {
        ((Stream) adjustedEquipment).setTemperature(inputValue + deviation / 10.0,
            adjustedVariableUnit);
      } else {
        ((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(inputValue + deviation,
            "mol/sec");
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
        ((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(inputValue - newVal,
            "kg/hr");
      } else if (adjustedVariable.equals("flow") && adjustedVariableUnit != null) {
        ((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(inputValue - newVal,
            adjustedVariableUnit);
      } else if (adjustedVariable.equals("pressure") && adjustedVariableUnit != null) {
        ((Stream) adjustedEquipment).setPressure(inputValue - newVal, adjustedVariableUnit);
      } else if (adjustedVariable.equals("temperature") && adjustedVariableUnit != null) {
        ((Stream) adjustedEquipment).setTemperature(inputValue - newVal, adjustedVariableUnit);
      } else {
        ((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(inputValue - newVal,
            "mol/sec");
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
}
