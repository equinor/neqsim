package neqsim.processSimulation.processEquipment.separator;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TwoPhaseSeparator class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TwoPhaseSeparator extends Separator {
    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    StreamInterface inletStream;
    StreamInterface gasOutStream;
    StreamInterface liquidOutStream;
    String name = new String();

    /**
     * <p>
     * Constructor for TwoPhaseSeparator.
     * </p>
     */
    @Deprecated
    public TwoPhaseSeparator() {
        this("TwoPhaseSeparator");
    }

    /**
     * <p>
     * Constructor for TwoPhaseSeparator.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    @Deprecated
    public TwoPhaseSeparator(StreamInterface inletStream) {
        this("TwoPhaseSeparator", inletStream);
    }

    /**
     * Constructor for TwoPhaseSeparator.
     * 
     * @param name
     */
    public TwoPhaseSeparator(String name) {
        super(name);
    }

    /**
     * <p>
     * Constructor for TwoPhaseSeparator.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public TwoPhaseSeparator(String name, StreamInterface inletStream) {
        super(name, inletStream);
    }

    /** {@inheritDoc} */
    @Override
    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;

        thermoSystem = inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream("gasOutStream", gasSystem);

        thermoSystem = inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream("liquidOutStream", liquidSystem);
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getLiquidOutStream() {
        return liquidOutStream;
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getGasOutStream() {
        return gasOutStream;
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getGas() {
        return getGasOutStream();
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getLiquid() {
        return getLiquidOutStream();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        thermoSystem = inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasSystem.setNumberOfPhases(1);
        gasOutStream.setThermoSystem(gasSystem);

        thermoSystem = inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidSystem.setNumberOfPhases(1);
        liquidOutStream.setThermoSystem(liquidSystem);
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {}

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt) {
    }
}
