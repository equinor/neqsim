package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * pHProbe class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class pHProbe extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    /** Constant <code>numberOfStreams=0</code> */
    protected static int numberOfStreams = 0;
    protected String name = new String();
    protected StreamInterface stream = null;
    protected SystemInterface reactiveThermoSystem;
    protected ThermodynamicOperations thermoOps;

    /**
     * <p>
     * Constructor for pHProbe.
     * </p>
     */
    public pHProbe() {}

    /**
     * <p>
     * Constructor for pHProbe.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public pHProbe(StreamInterface stream) {
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
    }

    /**
     * <p>
     * run.
     * </p>
     */
    public void run() {
        if (stream != null) {
            reactiveThermoSystem =
                    this.stream.getThermoSystem().setModel("Electrolyte-CPA-EOS-statoil");
        }
        thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
        thermoOps.TPflash();
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        System.out.println("measured temperature " + stream.getTemperature());
    }

    /** {@inheritDoc} */
    @Override
    public double getMeasuredValue() {
        return reactiveThermoSystem.getPhase(reactiveThermoSystem.getPhaseNumberOfPhase("aqueous"))
                .getpH();
    }
}
