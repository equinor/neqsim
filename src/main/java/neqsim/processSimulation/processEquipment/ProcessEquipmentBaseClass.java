

/*
 * ProcessEquipmentBaseClass.java
 *
 * Created on 6. juni 2006, 15:12
 *
 * To change this template, choose Tools | Template Manager and open the template in the editor.
 */
package neqsim.processSimulation.processEquipment;

import java.util.HashMap;
import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.processEquipment.stream.EnergyStream;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
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

    /**
     * Creates a new instance of ProcessEquipmentBaseClass
     */
    public ProcessEquipmentBaseClass() {
        mechanicalDesign = new MechanicalDesign(this);
    }

    public ProcessEquipmentBaseClass(String name) {
        this();
        this.name = name;
    }

    @Override
    public void run() {}

    @Override
    public void runTransient(double dt) {
        run();
    }

    @Override
    public SystemInterface getThermoSystem() {
        return null;
    }

    @Override
    public SystemInterface getFluid() {
        return getThermoSystem();
    }

    ;

    @Override
    public void displayResult() {}

    ;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public Object getProperty(String propertyName) {
        // if(properties.containsKey(propertyName)) {
        // return properties.get(properties).getValue();
        // }
        return null;
    }

    @Override
    public void setRegulatorOutSignal(double signal) {}

    @Override
    public void setController(ControllerDeviceInterface controller) {
        this.controller = controller;
        hasController = true;
    }

    public void setFlowValveController(ControllerDeviceInterface controller) {
        this.flowValveController = controller;
    }

    @Override
    public ControllerDeviceInterface getController() {
        return controller;
    }

    /**
     * @return the mechanicalDesign
     */
    @Override
    public MechanicalDesign getMechanicalDesign() {
        return mechanicalDesign;
    }

    /**
     * @param mechanicalDesign the mechanicalDesign to set
     */
    public void setMechanicalDesign(MechanicalDesign mechanicalDesign) {
        this.mechanicalDesign = mechanicalDesign;
    }

    /**
     * @return the specification
     */
    @Override
    public String getSpecification() {
        return specification;
    }

    /**
     * @param specification the specification to set
     */
    @Override
    public void setSpecification(String specification) {
        this.specification = specification;
    }

    @Override
    public String[][] reportResults() {
        return report;
    }

    @Override
    public boolean solved() {
        return true;
    }

    public EnergyStream getEnergyStream() {
        return energyStream;
    }

    public void setEnergyStream(EnergyStream energyStream) {
        setEnergyStream(true);
        this.energyStream = energyStream;
    }

    public boolean isSetEnergyStream() {
        return isSetEnergyStream;
    }

    public void setEnergyStream(boolean isSetEnergyStream) {
        this.isSetEnergyStream = isSetEnergyStream;
    }

    @Override
    public double getPressure() {
        return 1.0;
    }

    @Override
    public void setPressure(double pressure) {}

    @Override
    public double getEntropyProduction(String unit) {
        return 0.0;
    }

    @Override
    public double getMassBalance(String unit) {
        return 0.0;
    }

    @Override
    public double getExergyChange(String unit, double sourrondingTemperature) {
        return 0.0;
    }

    @Override
    public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {}

    public String conditionAnalysisMessage = "";

    @Override
    public String getConditionAnalysisMessage() {
        return conditionAnalysisMessage;
    }

    public String[][] getResultTable() {
        return null;
    }
}
