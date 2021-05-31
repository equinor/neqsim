/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * ProcessModuleBaseClass.java
 *
 * Created on 1. november 2006, 22:07
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package neqsim.processSimulation.processSystem;

import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
public abstract class ProcessModuleBaseClass implements ModuleInterface {

    private static final long serialVersionUID = 1000;

    protected String preferedThermodynamicModel = "", moduleName = "";
    protected boolean isInitializedModule = false, isInitializedStreams = false;
    private boolean isCalcDesign = false;
    private neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();

    /**
     * Creates a new instance of ProcessModuleBaseClass
     */
    public ProcessModuleBaseClass() {
    }

    @Override
	public String getPreferedThermodynamicModel() {
        return preferedThermodynamicModel;
    }

    @Override
	public void setPreferedThermodynamicModel(String preferedThermodynamicModel) {
        this.preferedThermodynamicModel = preferedThermodynamicModel;
    }

    @Override
	public void displayResult() {
        getOperations().displayResult();
    }

    @Override
	public String getName() {
        return moduleName;
    }

    @Override
	public void setName(String name) {
        moduleName = name;
    }

    @Override
	public void setRegulatorOutSignal(double signal) {
    }

    @Override
	public void setController(ControllerDeviceInterface controller) {
    }

    @Override
	public ControllerDeviceInterface getController() {
        return null;
    }

    @Override
	public MechanicalDesign getMechanicalDesign() {
        return null;
    }

    public abstract void calcDesign();

    public abstract void setDesign();

    @Override
	public String[][] reportResults() {
        return null;
    }

    /**
     * @return the isCalcDesign
     */
    @Override
	public boolean isCalcDesign() {
        return isCalcDesign;
    }

    /**
     * @param isCalcDesign the isCalcDesign to set
     */
    @Override
	public void setIsCalcDesign(boolean isCalcDesign) {
        this.isCalcDesign = isCalcDesign;
    }

    /**
     * @return the operations
     */
    @Override
	public neqsim.processSimulation.processSystem.ProcessSystem getOperations() {
        return operations;
    }

    // this method needs to be updated...need to chec if all equipment are solved
    // correctly
    @Override
	public boolean solved() {
        return true;
    }

    @Override
	public SystemInterface getThermoSystem() {
        return null;
    }

    @Override
	public SystemInterface getFluid() {
        return getThermoSystem();
    }

    public void setSpecification(String specificationName, double value) {

    }

    /**
     * @return the specification
     */
    @Override
	public String getSpecification() {
        return null;
    }

    /**
     * @param specification the specification to set
     */
    @Override
	public void setSpecification(String specification) {
    }

    @Override
	public Object getUnit(String name) {
        return operations.getUnit("name");
    }

    @Override
	public void setProperty(String propertyName, double value) {
        setSpecification(propertyName, value);
    }

    public void setProperty(String propertyName, double value, String unit) {
    }

    @Override
	public double getPressure() {
        return 1.0;
    }

    @Override
	public void setPressure(double pressure) {

    }

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
	public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {

    }

    @Override
	public String getConditionAnalysisMessage() {
        return null;
    }
}
