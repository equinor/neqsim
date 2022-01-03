/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.processSimulation.processEquipment.separator;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>TwoPhaseSeparator class.</p>
 *
 * @author  Even Solbraa
 */
public class TwoPhaseSeparator extends Separator {

    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    StreamInterface inletStream;
    StreamInterface gasOutStream;
    StreamInterface liquidOutStream;
    String name = new String();

    /**
     * Creates new Separator
     */
    public TwoPhaseSeparator() {
    }

    /**
     * <p>Constructor for TwoPhaseSeparator.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public TwoPhaseSeparator(StreamInterface inletStream) {
        this.setInletStream(inletStream);
    }

    /**
     * <p>Constructor for TwoPhaseSeparator.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public TwoPhaseSeparator(String name, StreamInterface inletStream) {
        this.name = name;
        this.setInletStream(inletStream);
    }

    /** {@inheritDoc} */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream(gasSystem);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream(liquidSystem);
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
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasSystem.setNumberOfPhases(1);
        gasOutStream.setThermoSystem(gasSystem);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidSystem.setNumberOfPhases(1);
        liquidOutStream.setThermoSystem(liquidSystem);
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /**
     * <p>runTransient.</p>
     */
    public void runTransient() {
    }

}
