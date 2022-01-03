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
 * <p>
 * SampleCreator class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class SampleCreator {
    private static final long serialVersionUID = 1000;

    protected SystemInterface system;
    ThermodynamicOperations thermoOps;
    ExperimentalEquipmentData equipment;

    /**
     * <p>
     * Constructor for SampleCreator.
     * </p>
     */
    public SampleCreator() {}

    /**
     * <p>
     * Constructor for SampleCreator.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param thermoOps a {@link neqsim.thermodynamicOperations.ThermodynamicOperations} object
     */
    public SampleCreator(SystemInterface system, ThermodynamicOperations thermoOps) {
        this.system = system;
        this.thermoOps = thermoOps;
    }

    /**
     * <p>
     * setThermoSystem.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public void setThermoSystem(SystemInterface system) {
        this.system = system;
    }

    /**
     * <p>
     * setExperimentalEquipment.
     * </p>
     *
     * @param equipment a
     *        {@link neqsim.statistics.experimentalEquipmentData.ExperimentalEquipmentData} object
     */
    public void setExperimentalEquipment(ExperimentalEquipmentData equipment) {
        this.equipment = equipment;
    }
}
