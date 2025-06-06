/*
 * ProcessModuleBaseClass.java
 *
 * Created on 1. november 2006, 22:07
 */

package neqsim.process.processmodel;

import java.util.UUID;
import neqsim.process.SimulationBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.util.report.Report;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Abstract ProcessModuleBaseClass class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public abstract class ProcessModuleBaseClass extends SimulationBaseClass
    implements ModuleInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected String preferedThermodynamicModel = "";
  protected boolean isInitializedModule = false;
  protected boolean isInitializedStreams = false;

  private boolean isCalcDesign = false;
  private neqsim.process.processmodel.ProcessSystem operations =
      new neqsim.process.processmodel.ProcessSystem();

  /**
   * <p>
   * Constructor for ProcessModuleBaseClass.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public ProcessModuleBaseClass(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public String getPreferedThermodynamicModel() {
    return preferedThermodynamicModel;
  }

  /** {@inheritDoc} */
  @Override
  public void setPreferedThermodynamicModel(String preferedThermodynamicModel) {
    this.preferedThermodynamicModel = preferedThermodynamicModel;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    getOperations().displayResult();
  }

  /** {@inheritDoc} */
  @Override
  public void setRegulatorOutSignal(double signal) {}

  /** {@inheritDoc} */
  @Override
  public void setController(ControllerDeviceInterface controller) {}

  /** {@inheritDoc} */
  @Override
  public ControllerDeviceInterface getController() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public MechanicalDesign getMechanicalDesign() {
    return null;
  }

  /**
   * <p>
   * calcDesign.
   * </p>
   */
  public abstract void calcDesign();

  /**
   * <p>
   * setDesign.
   * </p>
   */
  public abstract void setDesign();

  /** {@inheritDoc} */
  @Override
  public String[][] reportResults() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCalcDesign() {
    return isCalcDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void setIsCalcDesign(boolean isCalcDesign) {
    this.isCalcDesign = isCalcDesign;
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.processmodel.ProcessSystem getOperations() {
    return operations;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    getOperations().runTransient(dt, id);
  }

  // TODO: Check if all the equipment is solved correctly
  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String getSpecification() {
    return null;
  }

  /**
   * <p>
   * setSpecification.
   * </p>
   *
   * @param specificationName a {@link java.lang.String} object
   * @param value a double
   */
  public void setSpecification(String specificationName, double value) {}

  /** {@inheritDoc} */
  @Override
  public void setSpecification(String specification) {}

  /** {@inheritDoc} */
  @Override
  public Object getUnit(String name) {
    return operations.getUnit("name");
  }

  /** {@inheritDoc} */
  @Override
  public void setProperty(String propertyName, double value) {
    setSpecification(propertyName, value);
  }

  /**
   * <p>
   * setProperty.
   * </p>
   *
   * @param propertyName a {@link java.lang.String} object
   * @param value a double
   * @param unit a {@link java.lang.String} object
   */
  public void setProperty(String propertyName, double value, String unit) {}

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

  /** {@inheritDoc} */
  @Override
  public String getConditionAnalysisMessage() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure(String unit) {
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String getReport_json() {
    return new Report(this).generateJsonReport();
  }

  /** {@inheritDoc} */
  @Override
  public void run_step(UUID id) {}

  /** {@inheritDoc} */
  @Override
  public double getTemperature() {
    return getFluid().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature(String unit) {
    return getFluid().getTemperature(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temperature) {
    getFluid().setTemperature(temperature);
  }
}
