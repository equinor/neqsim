/*
 * Stream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.processSimulation.processEquipment.stream;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * ScalePotentialCheckStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ScalePotentialCheckStream extends Stream {

    private static final long serialVersionUID = 1000;

    protected SystemInterface reactiveThermoSystem;

    /**
     * <p>Constructor for ScalePotentialCheckStream.</p>
     */
    public ScalePotentialCheckStream() {
        super();
    }

    /**
     * <p>
     * Constructor for ScalePotentialCheckStream.
     * </p>
     *
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public ScalePotentialCheckStream(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    /**
     * <p>
     * Constructor for ScalePotentialCheckStream.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public ScalePotentialCheckStream(StreamInterface stream) {
        super(stream);
    }

    /**
     * <p>
     * Constructor for ScalePotentialCheckStream.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public ScalePotentialCheckStream(String name, SystemInterface thermoSystem) {
        super(name, thermoSystem);
    }

    /** {@inheritDoc} */
    @Override
    public ScalePotentialCheckStream clone() {
        ScalePotentialCheckStream clonedSystem = null;
        try {
            clonedSystem = (ScalePotentialCheckStream) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedSystem;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        System.out.println("start flashing stream... " + streamNumber);
        if (stream != null) {
            thermoSystem = (SystemInterface) this.stream.getThermoSystem().clone();
        }
        if (stream != null) {
            reactiveThermoSystem =
                    this.stream.getThermoSystem().setModel("Electrolyte-CPA-EOS-statoil");
        }

        ThermodynamicOperations thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
        thermoOps.TPflash();
        reactiveThermoSystem.init(3);

        System.out.println("number of phases: " + reactiveThermoSystem.getNumberOfPhases());
        System.out.println("beta: " + reactiveThermoSystem.getBeta());
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        reactiveThermoSystem.display(name);
    }

}
