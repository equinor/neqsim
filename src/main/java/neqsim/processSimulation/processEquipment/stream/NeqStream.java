/*
 * Stream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.processSimulation.processEquipment.stream;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * NeqStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NeqStream extends Stream {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for NeqStream.
     * </p>
     */
    public NeqStream() {}

    /**
     * <p>
     * Constructor for NeqStream.
     * </p>
     *
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public NeqStream(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    /**
     * <p>
     * Constructor for NeqStream.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public NeqStream(String name, SystemInterface thermoSystem) {
        super(name, thermoSystem);
    }

    /**
     * <p>
     * Constructor for NeqStream.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public NeqStream(StreamInterface stream) {
        super(stream);
    }

    /** {@inheritDoc} */
    @Override
    public NeqStream clone() {
        NeqStream clonedStream = null;

        try {
            clonedStream = (NeqStream) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        thermoSystem = (SystemInterface) thermoSystem.clone();

        return clonedStream;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        System.out.println("start flashing stream... " + streamNumber);
        if (stream != null) {
            thermoSystem = (SystemInterface) this.stream.getThermoSystem().clone();
        }
        this.thermoSystem.init_x_y();
        this.thermoSystem.initBeta();
        this.thermoSystem.init(3);
        // thermoOps = new ThermodynamicOperations(thermoSystem);
        // thermoOps.TPflash();
        System.out.println("number of phases: " + thermoSystem.getNumberOfPhases());
        System.out.println("beta: " + thermoSystem.getBeta());
    }
}
