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

package neqsim.thermo;

import java.beans.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
/**
 *
 * @author  Even Solbraa
 * @version
 */
// testing..
//testing again...

public class thermoBean extends Object implements java.io.Serializable {
      private static final long serialVersionUID = 1000;
    private static final String PROP_SAMPLE_PROPERTY = "SampleProperty";
    
    private String sampleProperty;
    private PropertyChangeSupport propertySupport;
    private SystemInterface thermoSystem = new SystemSrkEos(298,20);
    private ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    
    /** Creates new thermoBean */
    public thermoBean() {
        propertySupport = new PropertyChangeSupport ( this );
    }
    
    public synchronized String getSampleProperty () {
        return sampleProperty;
    }
    
    public synchronized void setTemperature(double value) {
        thermoSystem.setTemperature(value);
    }
    
    public synchronized void setPressure(double value) {
        thermoSystem.setPressure(value);
    }
        
    public synchronized void addComponent(String component, double moles) {
        thermoSystem.addComponent(component, moles);
    }
      
    public synchronized void TPflash(){
        testOps.TPflash();
        testOps.displayResult();
    }
    
    public synchronized double getTemperature() {
        return thermoSystem.getTemperature();
    }
    
    public synchronized double getPressure() {
        return thermoSystem.getPressure();
    }
    
    public synchronized void setSampleProperty (String value) {
        String oldValue = sampleProperty;
        sampleProperty = value;
        propertySupport.firePropertyChange (PROP_SAMPLE_PROPERTY, oldValue, sampleProperty);
    }
    
    
    public synchronized void addPropertyChangeListener (PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener (listener);
    }
    
    public synchronized void removePropertyChangeListener (PropertyChangeListener listener) {
        propertySupport.removePropertyChangeListener (listener);
    }
    
    /** Getter for property name.
     * @return Value of property name.
     *
     */
    public String getName() {
        return thermoSystem.getFluidName();
    }
    
    /** Setter for property name.
     * @param name New value of property name.
     *
     */
    public void setName(String name) {
        thermoSystem.setFluidName(name);
    }
    
}
