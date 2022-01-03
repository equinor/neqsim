/*
 * ProcessModuleBaseClass.java
 *
 * Created on 1. november 2006, 22:07
 */
package neqsim.processSimulation.processSystem;

import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>Abstract ProcessModuleBaseClass class.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public abstract class ProcessModuleBaseClass implements ModuleInterface {

    private static final long serialVersionUID = 1000;

    protected String preferedThermodynamicModel = "", moduleName = "";
    protected boolean isInitializedModule = false, isInitializedStreams = false;
    private boolean isCalcDesign = false;
    private neqsim.processSimulation.processSystem.ProcessSystem operations =
            new neqsim.processSimulation.processSystem.ProcessSystem();

    /**
     * Creates a new instance of ProcessModuleBaseClass
     */
    public ProcessModuleBaseClass() {}

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
    public void displayResult() {
        getOperations().displayResult();
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return moduleName;
    }

    /** {@inheritDoc} */
    @Override
    public void setName(String name) {
        moduleName = name;
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
     * <p>calcDesign.</p>
     */
    public abstract void calcDesign();

    /**
     * <p>setDesign.</p>
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
    public neqsim.processSimulation.processSystem.ProcessSystem getOperations() {
        return operations;
    }

    // this method needs to be updated...need to chec if all equipment are solved
    // correctly
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
    public SystemInterface getFluid() {
        return getThermoSystem();
    }

    /**
     * <p>setSpecification.</p>
     *
     * @param specificationName a {@link java.lang.String} object
     * @param value a double
     */
    public void setSpecification(String specificationName, double value) {

    }

    /** {@inheritDoc} */
    @Override
    public String getSpecification() {
        return null;
    }

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
     * <p>setProperty.</p>
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
    public void setPressure(double pressure) {

    }

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
    public double getExergyChange(String unit, double sourrondingTemperature) {
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {

    }

    /** {@inheritDoc} */
    @Override
    public String getConditionAnalysisMessage() {
        return null;
    }

    /**
     * <p>getResultTable.</p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[][] getResultTable() {
        return null;
    }
}
