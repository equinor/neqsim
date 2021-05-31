/*
 * Stream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.processSimulation.processEquipment.stream;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class EquilibriumStream extends Stream {

    private static final long serialVersionUID = 1000;

    /** Creates new Stream */
    public EquilibriumStream() {
    }

    public EquilibriumStream(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    public EquilibriumStream(StreamInterface stream) {
        super(stream.getThermoSystem());
    }

    public EquilibriumStream(String name, SystemInterface thermoSystem) {
        super(name, thermoSystem);
    }

    @Override
	public Object clone() {
        EquilibriumStream clonedStream = null;

        try {
            clonedStream = (EquilibriumStream) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        thermoSystem = (SystemInterface) thermoSystem.clone();
        return clonedStream;
    }

    @Override
	public void run() {
        System.out.println("start flashing stream... " + streamNumber);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.TPflash();
        System.out.println("number of phases: " + thermoSystem.getNumberOfPhases());
        System.out.println("beta: " + thermoSystem.getBeta());
    }

}
