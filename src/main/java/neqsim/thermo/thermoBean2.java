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
import neqsim.thermo.system.SystemElectrolyteCPA;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemNRTL;
import neqsim.thermo.system.SystemPrCPA;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemPsrkEos;
import neqsim.thermo.system.SystemRKEos;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
/**
 *
 * @author  Even Solbraa
 * @version
 */
public class thermoBean2 extends Object implements java.io.Serializable {
      private static final long serialVersionUID = 1000;
    private static final String PROP_SAMPLE_PROPERTY = "SampleProperty";
    
    private String sampleProperty;
    private PropertyChangeSupport propertySupport;
    private SystemInterface thermoSystem = new SystemSrkEos(298,20);
    private ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    
    /** Creates new thermoBean */
    public thermoBean2() {
        propertySupport = new PropertyChangeSupport( this );
        System.setProperty("NeqSim.home", "c:/java/neqsim/");
    }
    
    public synchronized void setModel(String name){
        double temperature=298.0, pressure=10.0;
        if (name.equals("srk")) {
            thermoSystem = new SystemSrkEos(temperature, pressure);
        } else if(name.equals("SRK-EoS")|| name.equals("SRK")) {
            thermoSystem = new SystemSrkEos(temperature, pressure);
        } else if(name.equals("Psrk-EoS")) {
            thermoSystem = new SystemPsrkEos(temperature, pressure);
        } else if(name.equals("PSRK-EoS")) {
            thermoSystem = new SystemPsrkEos(temperature, pressure);
        } else if(name.equals("RK-EoS")) {
            thermoSystem = new SystemRKEos(temperature, pressure);
        } else if(name.equals("pr")) {
            thermoSystem = new SystemPrEos(temperature, pressure);
        } else if(name.equals("srk-s") || name.equals("scrk")) {
            thermoSystem = new SystemSrkSchwartzentruberEos(temperature, pressure);
        } else if(name.equals("nrtl")) {
            thermoSystem = new SystemNRTL(temperature, pressure);
        } else if(name.equals("unifac")) {
            thermoSystem = new SystemGEWilson(temperature, pressure);
        } else if(name.equals("electrolyte")) {
            thermoSystem = new SystemFurstElectrolyteEos(temperature, pressure);
        } else if(name.equals("electrolyte") || name.equals("Electrolyte-ScRK-EoS")) {
            thermoSystem = new SystemFurstElectrolyteEos(temperature, pressure);
        } else if(name.equals("Electrolyte-CPA-EoS")) {
            thermoSystem = new SystemElectrolyteCPA(temperature, pressure);
        } else if(name.equals("Electrolyte-CPA-EoS") || name.equals("cpa-el")) {
            thermoSystem = new SystemElectrolyteCPA(temperature, pressure);
        } else if(name.equals("CPA-SRK-EoS") || name.equals("cpa-srk")) {
            thermoSystem = new SystemSrkCPA(temperature, pressure);
        } else if(name.equals("cpa-pr")) {
            thermoSystem = new SystemPrCPA(temperature, pressure);
        } else {
            thermoSystem = new SystemSrkEos(298,20);
        }
    }
    
    public synchronized void createDatabase(boolean test){
        thermoSystem.createDatabase(test);
    }
    
    public synchronized double getProperty(String prop, String compName, int phase){
        return    thermoSystem.getProperty(prop,compName,phase);
    }
    
    public synchronized double getProperty(String prop, int phase){
        return    thermoSystem.getProperty(prop,phase);
    }
    
    public synchronized double getProperty(String prop){
        return    thermoSystem.getProperty(prop);
    }
    
    public synchronized void removeMoles(){
        thermoSystem.removeMoles();
    }
    
    public synchronized String getSampleProperty() {
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
        testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
    }
    
    public synchronized void bubp(){
        testOps = new ThermodynamicOperations(thermoSystem);
        try {
            testOps.bubblePointPressureFlash(false);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public synchronized void dewp(){
        testOps = new ThermodynamicOperations(thermoSystem);
        try {
            testOps.dewPointPressureFlash();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public synchronized void bubt(){
        testOps = new ThermodynamicOperations(thermoSystem);
        try {
            testOps.bubblePointTemperatureFlash();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public synchronized void dewt(){
        testOps = new ThermodynamicOperations(thermoSystem);
        try {
            testOps.dewPointTemperatureFlash();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
    public synchronized SystemInterface getSystem(){
        return    thermoSystem;
    }
    
    public synchronized void display(){
        thermoSystem.display();
    }
    
    
    public synchronized double getTemperature() {
        return thermoSystem.getTemperature();
    }
    
    public synchronized double getPressure() {
        return thermoSystem.getPressure();
    }
    
    public synchronized void setMixingRule(int a){
        thermoSystem.setMixingRule(a);
    }
    
    public synchronized void setMixingRule(String a){
        thermoSystem.setMixingRule(a);
    }
    
    public synchronized void setSampleProperty(String value) {
        String oldValue = sampleProperty;
        sampleProperty = value;
        propertySupport.firePropertyChange(PROP_SAMPLE_PROPERTY, oldValue, sampleProperty);
    }
    
    
    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener(listener);
    }
    
    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.removePropertyChangeListener(listener);
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
