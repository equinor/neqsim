

/*
 * OperationInterafce.java
 *
 * Created on 2. oktober 2000, 22:14
 */
package neqsim.processSimulation.processEquipment;

import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface ProcessEquipmentInterface extends Runnable, java.io.Serializable {
    @Override
    public void run();

    public String[][] reportResults();

    public void runTransient(double dt);

    public MechanicalDesign getMechanicalDesign();

    public String getSpecification();

    public void setSpecification(String specification);

    public void displayResult();

    public String getName();

    public void setName(String name);

    public void setRegulatorOutSignal(double signal);

    public void setController(ControllerDeviceInterface controller);

    public ControllerDeviceInterface getController();

    public boolean solved();

    public SystemInterface getThermoSystem();

    public double getMassBalance(String unit);

    public SystemInterface getFluid();

    public double getPressure();

    public void setPressure(double pressure);

    public void runConditionAnalysis(ProcessEquipmentInterface refExchanger);

    public String getConditionAnalysisMessage();

    /**
     * method to return entropy production of the unit operation
     * 
     * @param unit The unit as a string. Supported units are J/K and kJ/K
     * @return entropy in specified unit
     */
    public double getEntropyProduction(String unit);

    /**
     * method to return exergy change production of the unit operation * @param
     * sourrondingTemperature The surrounding temperature in Kelvin
     * 
     * @param unit The unit as a string. Supported units are J and kJ
     * @return change in exergy in specified unit
     */
    public double getExergyChange(String unit, double sourrondingTemperature);

    public String[][] getResultTable();
}
