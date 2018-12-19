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
package neqsim.physicalProperties;

import neqsim.thermo.phase.PhaseInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author ESOL
 */
public class PhysicalPropertiyHandler implements Cloneable, java.io.Serializable {

    private neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhysicalProperties = null;
    private neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface oilPhysicalProperties = null;
    private neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface aqueousPhysicalProperties = null;
    static Logger logger = Logger.getLogger(PhysicalPropertiyHandler.class);

    public PhysicalPropertiyHandler() {
       
    }

    public void setPhysicalProperties(PhaseInterface phase, int type) {
        if (type == 0) {
            gasPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(phase, 0, 0);
            oilPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(phase, 0, 0);
            aqueousPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.WaterPhysicalProperties(phase, 0, 0);
        } else if (type == 1) {
            gasPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(phase, 0, 0);
            oilPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(phase, 0, 0);
            aqueousPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.WaterPhysicalProperties(phase, 0, 0);

        } else if (type == 2) {
            gasPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(phase, 0, 0);
            oilPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(phase, 0, 0);
            aqueousPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.GlycolPhysicalProperties(phase, 0, 0);

        } else if (type == 3) {
            gasPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(phase, 0, 0);
            oilPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(phase, 0, 0);
            aqueousPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.AminePhysicalProperties(phase, 0, 0);

        } else if (type == 4) {
            gasPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties.GasPhysicalProperties(phase, 0, 0);
            oilPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.LiquidPhysicalProperties(phase, 0, 0);
            aqueousPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties.CO2waterPhysicalProperties(phase, 0, 0);

        } else if (type == 6) {
            gasPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(phase, 0, 0);
            oilPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(phase, 0, 0);
            aqueousPhysicalProperties = new neqsim.physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties.DefaultPhysicalProperties(phase, 0, 0);
        } else {
            logger.error("error selecting physical properties model.\n Continue using default model...");
            setPhysicalProperties(phase, 0);
        }

    }

    public neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface getPhysicalProperty(PhaseInterface phase) {
        if (phase.getPhaseTypeName().equals("gas")) {
            return gasPhysicalProperties;
        } else if (phase.getPhaseTypeName().equals("oil")) {
            return oilPhysicalProperties;
        } else if (phase.getPhaseTypeName().equals("aqueous")) {
            return aqueousPhysicalProperties;
        } else {
            return gasPhysicalProperties;
        }
    }

    @Override
    public Object clone() {
        PhysicalPropertiyHandler clonedHandler = null;

        try {
            clonedHandler = (PhysicalPropertiyHandler) super.clone();
        } catch (Exception e) {
            // e.printStackTrace(System.err);
        }
        try {
            if (gasPhysicalProperties != null) {
                clonedHandler.gasPhysicalProperties = (neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface) gasPhysicalProperties.clone();
            }
            if (oilPhysicalProperties != null) {
                clonedHandler.oilPhysicalProperties = (neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface) oilPhysicalProperties.clone();
            }
            if (aqueousPhysicalProperties != null) {
                clonedHandler.aqueousPhysicalProperties = (neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface) aqueousPhysicalProperties.clone();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedHandler;
    }
}
