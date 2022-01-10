package neqsim.processSimulation.processEquipment.stream;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * EquilibriumStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class EquilibriumStream extends Stream {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for EquilibriumStream.
     * </p>
     */
    public EquilibriumStream() {}

    /**
     * <p>
     * Constructor for EquilibriumStream.
     * </p>
     *
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public EquilibriumStream(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    /**
     * <p>
     * Constructor for EquilibriumStream.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public EquilibriumStream(StreamInterface stream) {
        super(stream.getThermoSystem());
    }

    /**
     * <p>
     * Constructor for EquilibriumStream.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public EquilibriumStream(String name, SystemInterface thermoSystem) {
        super(name, thermoSystem);
    }

    /** {@inheritDoc} */
    @Override
    public EquilibriumStream clone() {
        EquilibriumStream clonedStream = null;

        try {
            clonedStream = (EquilibriumStream) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        thermoSystem = thermoSystem.clone();
        return clonedStream;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        System.out.println("start flashing stream... " + streamNumber);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.TPflash();
        System.out.println("number of phases: " + thermoSystem.getNumberOfPhases());
        System.out.println("beta: " + thermoSystem.getBeta());
    }
}
