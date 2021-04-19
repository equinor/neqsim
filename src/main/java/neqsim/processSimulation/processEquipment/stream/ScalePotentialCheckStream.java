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
public class ScalePotentialCheckStream extends Stream implements StreamInterface, Cloneable {

    private static final long serialVersionUID = 1000;

    protected SystemInterface reactiveThermoSystem;

    /** Creates new Stream */
    public ScalePotentialCheckStream() {
        super();
    }

    public ScalePotentialCheckStream(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    public ScalePotentialCheckStream(StreamInterface stream) {
        super(stream);
    }

    public ScalePotentialCheckStream(String name, SystemInterface thermoSystem) {
        super(name, thermoSystem);
    }

    @Override
	public Object clone() {
        ScalePotentialCheckStream clonedSystem = null;
        try {
            clonedSystem = (ScalePotentialCheckStream) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedSystem;
    }

    @Override
	public void run() {
        System.out.println("start flashing stream... " + streamNumber);
        if (stream != null) {
            thermoSystem = (SystemInterface) this.stream.getThermoSystem().clone();
        }
        if (stream != null) {
            reactiveThermoSystem = this.stream.getThermoSystem().setModel("Electrolyte-CPA-EOS-statoil");
        }

        ThermodynamicOperations thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
        thermoOps.TPflash();
        reactiveThermoSystem.init(3);

        System.out.println("number of phases: " + reactiveThermoSystem.getNumberOfPhases());
        System.out.println("beta: " + reactiveThermoSystem.getBeta());
    }

    @Override
	public void displayResult() {
        reactiveThermoSystem.display(name);
    }

}
