/*
 * SampleCreator.java
 *
 * Created on 8. februar 2001, 09:14
 */

package neqsim.statistics.experimentalSampleCreation.sampleCreator;

import neqsim.statistics.experimentalEquipmentData.ExperimentalEquipmentData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author  even solbraa
 * @version
 */
public class SampleCreator {

    private static final long serialVersionUID = 1000;

    protected SystemInterface system;
    ThermodynamicOperations thermoOps;
    ExperimentalEquipmentData equipment;

    /** Creates new SampleCreator */
    public SampleCreator() {
    }

    public SampleCreator(SystemInterface system, ThermodynamicOperations thermoOps) {
        this.system = system;
        this.thermoOps = thermoOps;
    }

    public void setThermoSystem(SystemInterface system) {
        this.system = system;
    }

    public void setExperimentalEquipment(ExperimentalEquipmentData equipment) {
        this.equipment = equipment;
    }
}
