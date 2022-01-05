/*
 * ProcessEquipmentBaseClass.java
 *
 * Created on 6. juni 2006, 15:12
 */
package neqsim.processSimulation.processEquipment;

import java.util.HashMap;
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
public abstract class ProcessEquipmentBaseClass implements ProcessEquipmentInterface {
    private static final long serialVersionUID = 1000;

    private ControllerDeviceInterface controller = null;
    ControllerDeviceInterface flowValveController = null;
    public boolean hasController = false;
    public String name = new String();
    public MechanicalDesign mechanicalDesign = new MechanicalDesign(this);
    private String specification = "TP";
    public String[][] report = new String[0][0];
    public HashMap<String, String> properties = new HashMap<String, String>();
    public EnergyStream energyStream = new EnergyStream();
    private boolean isSetEnergyStream = false;

    public ProcessEquipmentBaseClass() {
        mechanicalDesign = new MechanicalDesign(this);
    }

    /**
     * <p>
     * Constructor for ProcessEquipmentBaseClass.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public ProcessEquipmentBaseClass(String name) {
        this();
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {}

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt) {
        run();
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

    ;

    /** {@inheritDoc} */
    @Override
    public void displayResult() {}

    ;

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override
    public void setName(String name) {
        this.name = name;
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
        hasController = true;
    }

    /**
     * <p>
     * Setter for the field <code>flowValveController</code>.
     * </p>
     *
     * @param controller a
     *        {@link neqsim.processSimulation.controllerDevice.ControllerDeviceInterface} object
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
        return mechanicalDesign;
    }

    /**
     * <p>
     * Setter for the field <code>mechanicalDesign</code>.
     * </p>
     *
     * @param mechanicalDesign the mechanicalDesign to set
     */
    public void setMechanicalDesign(MechanicalDesign mechanicalDesign) {
        this.mechanicalDesign = mechanicalDesign;
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
    public double getExergyChange(String unit, double sourrondingTemperature) {
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
}
