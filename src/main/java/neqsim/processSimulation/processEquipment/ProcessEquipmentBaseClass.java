/*
 * ProcessEquipmentBaseClass.java
 *
 * Created on 6. juni 2006, 15:12
 */
package neqsim.processSimulation.processEquipment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import org.apache.commons.lang.SerializationUtils;
import neqsim.processSimulation.SimulationBaseClass;
import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.processEquipment.stream.EnergyStream;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract ProcessEquipmentBaseClass class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public abstract class ProcessEquipmentBaseClass extends SimulationBaseClass
    implements ProcessEquipmentInterface {
  private static final long serialVersionUID = 1000;

  private ControllerDeviceInterface controller = null;
  ControllerDeviceInterface flowValveController = null;
  public boolean hasController = false;
  private String specification = "TP";
  public String[][] report = new String[0][0];
  public HashMap<String, String> properties = new HashMap<String, String>();
  public EnergyStream energyStream = new EnergyStream();
  private boolean isSetEnergyStream = false;

  /**
   * <p>
   * Constructor for ProcessEquipmentBaseClass.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public ProcessEquipmentBaseClass(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {}

  /**
   * Create deep copy.
   * 
   * @return a deep copy of the unit operation/process equipment
   */
  public ProcessEquipmentInterface copy() {
    byte[] bytes = SerializationUtils.serialize(this);
    return (ProcessEquipmentInterface) SerializationUtils.deserialize(bytes);
  }

  /**
   * <p>
   * getProperty.
   * </p>
   *
   * @param propertyName a {@link java.lang.String} object
   * @return a {@link java.lang.Object} object
   */
  public Object getProperty(String propertyName) {
    // if(properties.containsKey(propertyName)) {
    // return properties.get(properties).getValue();
    // }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setRegulatorOutSignal(double signal) {}

  /** {@inheritDoc} */
  @Override
  public void setController(ControllerDeviceInterface controller) {
    this.controller = controller;
    hasController = controller != null;
  }

  /**
   * <p>
   * Setter for the field <code>flowValveController</code>.
   * </p>
   *
   * @param controller a {@link neqsim.processSimulation.controllerDevice.ControllerDeviceInterface}
   *        object
   */
  public void setFlowValveController(ControllerDeviceInterface controller) {
    this.flowValveController = controller;
  }

  /** {@inheritDoc} */
  @Override
  public ControllerDeviceInterface getController() {
    return controller;
  }

  /** {@inheritDoc} */
  @Override
  public MechanicalDesign getMechanicalDesign() {
    return new MechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public String getSpecification() {
    return specification;
  }

  /** {@inheritDoc} */
  @Override
  public void setSpecification(String specification) {
    this.specification = specification;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] reportResults() {
    return report;
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    return true;
  }

  /**
   * <p>
   * Getter for the field <code>energyStream</code>.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.processEquipment.stream.EnergyStream} object
   */
  public EnergyStream getEnergyStream() {
    return energyStream;
  }

  /**
   * <p>
   * Setter for the field <code>energyStream</code>.
   * </p>
   *
   * @param energyStream a {@link neqsim.processSimulation.processEquipment.stream.EnergyStream}
   *        object
   */
  public void setEnergyStream(EnergyStream energyStream) {
    setEnergyStream(true);
    this.energyStream = energyStream;
  }

  /**
   * <p>
   * isSetEnergyStream.
   * </p>
   *
   * @return a boolean
   */
  public boolean isSetEnergyStream() {
    return isSetEnergyStream;
  }

  /**
   * <p>
   * Setter for the field <code>energyStream</code>.
   * </p>
   *
   * @param isSetEnergyStream a boolean
   */
  public void setEnergyStream(boolean isSetEnergyStream) {
    this.isSetEnergyStream = isSetEnergyStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure() {
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pressure) {}

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {}

  public String conditionAnalysisMessage = "";

  /** {@inheritDoc} */
  @Override
  public String getConditionAnalysisMessage() {
    return conditionAnalysisMessage;
  }

  /**
   * <p>
   * getResultTable.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.deepHashCode(report);
    result = prime * result + Objects.hash(conditionAnalysisMessage, controller, energyStream,
        flowValveController, hasController, isSetEnergyStream, name, properties, specification);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ProcessEquipmentBaseClass other = (ProcessEquipmentBaseClass) obj;
    return Objects.equals(conditionAnalysisMessage, other.conditionAnalysisMessage)
        && Objects.equals(controller, other.controller)
        && Objects.equals(energyStream, other.energyStream)
        && Objects.equals(flowValveController, other.flowValveController)
        && hasController == other.hasController && isSetEnergyStream == other.isSetEnergyStream
        && Objects.equals(name, other.name) && Objects.equals(properties, other.properties)
        && Arrays.deepEquals(report, other.report)
        && Objects.equals(specification, other.specification);
  }
}
