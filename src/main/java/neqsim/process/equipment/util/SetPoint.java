package neqsim.process.equipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SetPoint class.
 * </p>
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
   * <p>
   * Constructor for SetPoint.
   * </p>
   */
  @Deprecated
  public SetPoint() {
    this("SetPoint");
  }

  /**
   * <p>
   * Constructor for SetPoint.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public SetPoint(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for SetPoint.
   * </p>
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
   * <p>
   * setSourceVariable.
   * </p>
   *
   * @param adjustedEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @param adjstedVariable a {@link java.lang.String} object
   */
  public void setSourceVariable(ProcessEquipmentInterface adjustedEquipment,
      String adjstedVariable) {
    this.sourceEquipment = adjustedEquipment;
    this.sourceVariable = adjstedVariable;
  }

  /**
   * <p>
   * setSourceVariable.
   * </p>
   *
   * @param adjustedEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setSourceVariable(ProcessEquipmentInterface adjustedEquipment) {
    this.sourceEquipment = adjustedEquipment;
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
   */
  public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable) {
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
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

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (targetVariable.equals("pressure")) {
      targetEquipment.setPressure(sourceEquipment.getPressure());
    } else {
      inputValue = ((Stream) sourceEquipment).getThermoSystem().getNumberOfMoles();

      double targetValueCurrent =
          ((Stream) targetEquipment).getThermoSystem().getVolume(targetUnit);

      double deviation = targetValue - targetValueCurrent;

      logger.info("adjuster deviation " + deviation + " inputValue " + inputValue);

      oldInputValue = inputValue;
    }
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}

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
    SetPoint adjuster1 = new SetPoint();
    adjuster1.setSourceVariable(stream_1, "molarFlow");
    adjuster1.setTargetVariable(stream_1, "gasVolumeFlow", 10.0, "", "MSm3/day");

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(adjuster1);

    operations.run();
  }
}
