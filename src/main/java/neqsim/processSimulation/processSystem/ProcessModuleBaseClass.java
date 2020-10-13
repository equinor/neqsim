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

   

    public String getPreferedThermodynamicModel() {
        return preferedThermodynamicModel;
    }

    public void setPreferedThermodynamicModel(String preferedThermodynamicModel) {
        this.preferedThermodynamicModel = preferedThermodynamicModel;
    }

    public void displayResult() {
        getOperations().displayResult();
    }

    public String getName() {
        return moduleName;
    }

    public void setName(String name) {
        moduleName = name;
    }

    public void setRegulatorOutSignal(double signal) {
    }

    public void setController(ControllerDeviceInterface controller) {
    }

    public ControllerDeviceInterface getController() {
        return null;
    }

    public MechanicalDesign getMechanicalDesign() {
        return null;
    }

    public abstract void calcDesign();

    public abstract void setDesign();

   public String[][] reportResults(){
        return null;
    }

    /**
     * @return the isCalcDesign
     */
    public boolean isCalcDesign() {
        return isCalcDesign;
    }

    /**
     * @param isCalcDesign the isCalcDesign to set
     */
    public void setIsCalcDesign(boolean isCalcDesign) {
        this.isCalcDesign = isCalcDesign;
    }

    /**
     * @return the operations
     */
    public neqsim.processSimulation.processSystem.ProcessSystem getOperations() {
        return operations;
    }
    
    //this method needs to be updated...need to chec if all equipment are solved correctly
    public boolean solved() {
    	return true;
    }
    

    public SystemInterface getThermoSystem() {
        return null;
    }
    
    public SystemInterface getFluid() {
        return getThermoSystem();
    }
    
    public void setSpecification(String specificationName, double value) {
    
    }
    
    public Object getUnit(String name) {
    	return operations.getUnit("name");
    }
    
    public void setProperty(String propertyName, double value) {
    	setSpecification(propertyName, value);
    }
    
    public void setProperty(String propertyName, double value, String unit) {
    }
    
    public double getPressure() {
		return 1.0;
	}

	public void setPressure(double pressure) {
		
	}
}
