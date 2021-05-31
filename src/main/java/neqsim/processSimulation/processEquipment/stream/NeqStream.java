/*
 * Stream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.processSimulation.processEquipment.stream;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class NeqStream extends Stream {

    private static final long serialVersionUID = 1000;

    public NeqStream() {
    }

    public NeqStream(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    public NeqStream(String name, SystemInterface thermoSystem) {
        super(name, thermoSystem);
    }

    public NeqStream(StreamInterface stream) {
        super(stream);
    }

    @Override
	public Object clone() {

        NeqStream clonedStream = null;

        try {
            clonedStream = (NeqStream) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        thermoSystem = (SystemInterface) thermoSystem.clone();

        return clonedStream;
    }

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
