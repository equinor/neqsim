package neqsim.processSimulation.processEquipment;

import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ProcessEquipmentInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ProcessEquipmentInterface extends Runnable, java.io.Serializable {
    /** {@inheritDoc} 
     *     /
     * <p>
     * run
     * </p>
     * In this method all thermodynamic and unit the operation 
     * will be calculated in a steady state calculation.
     *
     * @return void
     */
    @Override
    public void run();

    /**
     * <p>
     * reportResults.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[][] reportResults();

    /** {@inheritDoc} 
     *     /
     * <p>
     * runTransient
     * </p>
     * In this method all thermodynamic and unit the operation 
     * will be calculated in a dynamic calculation.
     * dt is the delta time step (seconds)
     *
     * @return void
     */
    public void runTransient(double dt);

    /**
     * <p>
     * getMechanicalDesign.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
     */
    public MechanicalDesign getMechanicalDesign();

    /**
     * <p>
     * getSpecification.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getSpecification();

    /**
     * <p>
     * setSpecification.
     * </p>
     *
     * @param specification a {@link java.lang.String} object
     */
    public void setSpecification(String specification);

    /**
     * <p>
     * displayResult.
     * </p>
     */
    public void displayResult();

    /**
     * <p>
     * getName.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName();

    /**
     * <p>
     * setName.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setName(String name);

    /**
     * <p>
     * setRegulatorOutSignal.
     * </p>
     *
     * @param signal a double
     */
    public void setRegulatorOutSignal(double signal);

    /**
     * <p>
     * setController.
     * </p>
     *
     * @param controller a
     *        {@link neqsim.processSimulation.controllerDevice.ControllerDeviceInterface} object
     */
    public void setController(ControllerDeviceInterface controller);

    /**
     * <p>
     * getController.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.controllerDevice.ControllerDeviceInterface} object
     */
    public ControllerDeviceInterface getController();

    /**
     * <p>
     * solved.
     * </p>
     *
     * @return a boolean
     */
    public boolean solved();

    /**
     * <p>
     * getThermoSystem.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface getThermoSystem();

    /**
     * <p>
     * getMassBalance.
     * </p>
     *
     * @param unit a {@link java.lang.String} object
     * @return a double
     */
    public double getMassBalance(String unit);

    /**
     * <p>
     * getFluid.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface getFluid();

    /**
     * <p>
     * getPressure.
     * </p>
     *
     * @return a double
     */
    public double getPressure();

    /**
     * <p>
     * setPressure.
     * </p>
     *
     * @param pressure a double
     */
    public void setPressure(double pressure);

    /**
     * <p>
     * runConditionAnalysis.
     * </p>
     *
     * @param refExchanger a
     *        {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface} object
     */
    public void runConditionAnalysis(ProcessEquipmentInterface refExchanger);

    /**
     * <p>
     * getConditionAnalysisMessage.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
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
     * @param sourrondingTemperature a double
     */
    public double getExergyChange(String unit, double sourrondingTemperature);

    /**
     * <p>
     * getResultTable.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[][] getResultTable();
}
