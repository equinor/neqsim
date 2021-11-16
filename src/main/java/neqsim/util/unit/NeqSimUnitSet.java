/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package neqsim.util.unit;

/**
 *
 * @author ESOL
 */
public class NeqSimUnitSet {
    /**
     * @return the componentConcentrationUnit
     */
    public String getComponentConcentrationUnit() {
        return componentConcentrationUnit;
    }

    /**
     * @param componentConcentrationUnit the componentConcentrationUnit to set
     */
    public void setComponentConcentrationUnit(String componentConcentrationUnit) {
        this.componentConcentrationUnit = componentConcentrationUnit;
    }

    private static final long serialVersionUID = 1000;

    /**
     * @return the flowRateUnit
     */
    public String getFlowRateUnit() {
        return flowRateUnit;
    }

    /**
     * @param flowRateUnit the flowRateUnit to set
     */
    public void setFlowRateUnit(String flowRateUnit) {
        this.flowRateUnit = flowRateUnit;
    }

    /**
     * @return the pressureUnit
     */
    public String getPressureUnit() {
        return pressureUnit;
    }

    /**
     * @param pressureUnit the pressureUnit to set
     */
    public void setPressureUnit(String pressureUnit) {
        this.pressureUnit = pressureUnit;
    }

    /**
     * @return the temperatureUnit
     */
    public String getTemperatureUnit() {
        return temperatureUnit;
    }

    /**
     * @param temperatureUnit the temperatureUnit to set
     */
    public void setTemperatureUnit(String temperatureUnit) {
        this.temperatureUnit = temperatureUnit;
    }

    private String temperatureUnit = "K";
    private String pressureUnit = "bara";
    private String flowRateUnit = "mol/sec";
    private String componentConcentrationUnit = "molefraction";
}
