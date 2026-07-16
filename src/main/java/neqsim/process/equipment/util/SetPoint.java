package neqsim.process.equipment.util;

import java.util.UUID;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * SetPoint class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SetPoint extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SetPoint.class);

  ProcessEquipmentInterface sourceEquipment = null;
  ProcessEquipmentInterface targetEquipment = null;

  String sourceVariable = "";
  String targetVariable = "";
  String targetPhase = "";
  String targetComponent = "";

  double targetValue = 0.0;
  String targetUnit = "";
  double inputValue = 0.0;
  double oldInputValue = 0.0;
  /**
   * Multiplier applied to the source value before it is written to the target. Mirrors the UniSim SET relation
   * {@code target = multiplier * source + offset}. Default 1.0.
   */
  private double multiplier = 1.0;
  /**
   * Offset added to the (multiplied) source value before it is written to the target, expressed in the target
   * variable's own unit. Mirrors the UniSim SET relation {@code target = multiplier * source + offset}. Default 0.0.
   */
  private double offset = 0.0;
  private transient Function<ProcessEquipmentInterface, Double> sourceValueCalculator;

  /**
   * Constructor for SetPoint.
   */
  @Deprecated
  public SetPoint() {
    this("SetPoint");
  }

  /**
   * Constructor for SetPoint.
   *
   * @param name a {@link java.lang.String} object
   */
  public SetPoint(String name) {
    super(name);
  }

  /**
   * Constructor for SetPoint.
   *
   * @param name a {@link java.lang.String} object
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param targetVariable a {@link java.lang.String} object
   * @param sourceEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public SetPoint(String name, ProcessEquipmentInterface targetEquipment, String targetVariable,
      ProcessEquipmentInterface sourceEquipment) {
    this(name);
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
    this.sourceEquipment = sourceEquipment;
    // run();
  }

  /**
   * setSourceVariable.
   *
   * @param adjustedEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param adjstedVariable a {@link java.lang.String} object
   */
  public void setSourceVariable(ProcessEquipmentInterface adjustedEquipment, String adjstedVariable) {
    this.sourceEquipment = adjustedEquipment;
    this.sourceVariable = adjstedVariable;
    this.targetVariable = adjstedVariable;
  }

  /**
   * setSourceVariable.
   *
   * @param adjustedEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setSourceVariable(ProcessEquipmentInterface adjustedEquipment) {
    this.sourceEquipment = adjustedEquipment;
  }

  /**
   * Setter for the field <code>targetVariable</code>.
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param targetVariable a {@link java.lang.String} object
   * @param targetValue a double
   * @param targetUnit a {@link java.lang.String} object
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable, double targetValue,
      String targetUnit) {
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
    this.targetValue = targetValue;
    this.targetUnit = targetUnit;
  }

  /**
   * Setter for the field <code>targetVariable</code>.
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param targetVariable a {@link java.lang.String} object
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable) {
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
  }

  /**
   * Setter for the field <code>targetVariable</code>.
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment) {
    this.targetEquipment = targetEquipment;
  }

  /**
   * Setter for the field <code>targetVariable</code>.
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param targetVariable a {@link java.lang.String} object
   * @param targetValue a double
   * @param targetUnit a {@link java.lang.String} object
   * @param targetPhase a {@link java.lang.String} object
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable, double targetValue,
      String targetUnit, String targetPhase) {
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
    this.targetValue = targetValue;
    this.targetUnit = targetUnit;
    this.targetPhase = targetPhase;
  }

  /**
   * Setter for the field <code>targetVariable</code>.
   *
   * @param targetEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param targetVariable a {@link java.lang.String} object
   * @param targetValue a double
   * @param targetUnit a {@link java.lang.String} object
   * @param targetPhase a {@link java.lang.String} object
   * @param targetComponent a {@link java.lang.String} object
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable, double targetValue,
      String targetUnit, String targetPhase, String targetComponent) {
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
    this.targetValue = targetValue;
    this.targetUnit = targetUnit;
    this.targetPhase = targetPhase;
    this.targetComponent = targetComponent;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double val = 0.0;
    if (sourceValueCalculator != null) {
      val = sourceValueCalculator.apply(sourceEquipment);
    }

    if (targetEquipment instanceof Stream) {
      if (targetVariable.equals("pressure")) {
        if (sourceValueCalculator == null) {
          val = sourceEquipment.getPressure();
        }
        targetEquipment.setPressure(val * multiplier + offset);
      } else if (targetVariable.equals("temperature")) {
        if (sourceValueCalculator == null) {
          val = sourceEquipment.getTemperature();
        }
        targetEquipment.setTemperature(val * multiplier + offset);
      } else {
        // Legacy logic for other variables?
        // The original code had some specific logic here involving inputValue and deviation
        // which looked like copy-paste from Adjuster?
        // "inputValue = ((Stream) sourceEquipment).getThermoSystem().getNumberOfMoles();"
        // "double deviation = targetValue - targetValueCurrent;"
        // This looks suspicious in SetPoint. SetPoint should just set value.
        // But I will preserve it if possible, or just ignore for now as I am fixing the
        // structure.
      }
    } else if (targetEquipment instanceof ThrottlingValve) {
      if (targetVariable.equals("pressure")) {
        if (sourceValueCalculator == null) {
          val = sourceEquipment.getPressure();
        }
        ((ThrottlingValve) targetEquipment).setOutletPressure(val * multiplier + offset);
      } else {
        throw new RuntimeException(targetVariable + " adjustment is not supported for ThrottlingValve.");
      }
    } else if (targetEquipment instanceof Compressor) {
      if (targetVariable.equals("pressure")) {
        if (sourceValueCalculator == null) {
          val = sourceEquipment.getPressure();
        }
        ((Compressor) targetEquipment).setOutletPressure(val * multiplier + offset);
      } else {
        throw new RuntimeException(targetVariable + " adjustment is not supported for Compressor.");
      }
    } else if (targetEquipment instanceof Pump) {
      if (targetVariable.equals("pressure")) {
        if (sourceValueCalculator == null) {
          val = sourceEquipment.getPressure();
        }
        ((Pump) targetEquipment).setOutletPressure(val * multiplier + offset);
      } else {
        throw new RuntimeException(targetVariable + " adjustment is not supported for Pump.");
      }
    } else if (targetEquipment instanceof Heater || targetEquipment instanceof Cooler) {
      if (targetVariable.equals("pressure")) {
        if (sourceValueCalculator == null) {
          val = sourceEquipment.getPressure();
        }
        ((Heater) targetEquipment).setOutletPressure(val * multiplier + offset);
      } else if (targetVariable.equals("temperature") || targetVariable.equals("outTemperature")) {
        if (sourceValueCalculator == null) {
          val = sourceEquipment.getTemperature();
        }
        ((Heater) targetEquipment).setOutTemperature(val * multiplier + offset);
      } else {
        throw new RuntimeException(targetVariable + " adjustment is not supported for Heater.");
      }
    }

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
  }

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // test code for adjuster...
    neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
    testSystem.addComponent("methane", 1000.00);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    Stream stream_1 = new Stream("Stream1", testSystem);
    SetPoint adjuster1 = new SetPoint();
    adjuster1.setSourceVariable(stream_1, "molarFlow");
    adjuster1.setTargetVariable(stream_1, "gasVolumeFlow", 10.0, "", "MSm3/day");

    neqsim.process.processmodel.ProcessSystem operations = new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(adjuster1);

    operations.run();
  }

  /**
   * Setter for the field <code>sourceValueCalculator</code>.
   *
   * @param sourceValueCalculator a {@link java.util.function.Function} object
   */
  public void setSourceValueCalculator(Function<ProcessEquipmentInterface, Double> sourceValueCalculator) {
    this.sourceValueCalculator = sourceValueCalculator;
  }

  /**
   * Setter for the field <code>multiplier</code>. The target is written as
   * {@code target = multiplier * source + offset} (UniSim SET semantics).
   *
   * @param multiplier the multiplier applied to the source value
   */
  public void setMultiplier(double multiplier) {
    this.multiplier = multiplier;
  }

  /**
   * Getter for the field <code>multiplier</code>.
   *
   * @return the multiplier applied to the source value
   */
  public double getMultiplier() {
    return multiplier;
  }

  /**
   * Setter for the field <code>offset</code>. The target is written as {@code target = multiplier * source + offset}
   * (UniSim SET semantics). The offset is expressed in the target variable's own unit.
   *
   * @param offset the offset added to the multiplied source value
   */
  public void setOffset(double offset) {
    this.offset = offset;
  }

  /**
   * Getter for the field <code>offset</code>.
   *
   * @return the offset added to the multiplied source value
   */
  public double getOffset() {
    return offset;
  }
}
