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
package neqsim.standards.oilQuality;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
public class Standard_ASTM_D6377 extends neqsim.standards.Standard {

    private static final long serialVersionUID = 1000;
    String unit = "bara";
    double RVP = 1.0;

    public Standard_ASTM_D6377(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    public void calculate() {
        this.thermoSystem.setTemperature(273.15 + 30.7);
        this.thermoSystem.setPressure(1.01325);

        try {
            this.thermoOps.bubblePointPressureFlash();
        } catch (Exception e) {
            e.printStackTrace();
        }
        RVP = this.thermoSystem.getPressure();

    }

    public boolean isOnSpec() {
        return true;
    }

    public String getUnit(String returnParameter) {
        return unit;
    }

    public double getValue(String returnParameter, java.lang.String returnUnit) {
        return RVP;
    }

    public double getValue(String returnParameter) {
        return RVP;
    }
}
