/*
 * GasScrubber.java
 *
 * Created on 12. mars 2001, 19:48
 */
package neqsim.processSimulation.processEquipment.separator;

import java.util.ArrayList;

import neqsim.processSimulation.mechanicalDesign.separator.GasScrubberMechanicalDesign;
import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * GasScrubber class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GasScrubber extends Separator {
    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    ArrayList<SeparatorSection> scrubberSection = null;
    StreamInterface inletStream;
    StreamInterface gasOutStream;
    StreamInterface liquidOutStream;
    String name = new String();

    /**
     * <p>
     * Constructor for GasScrubber.
     * </p>
     */
    @Deprecated
    public GasScrubber() {
        this("GasScrubber");
    }

    /**
     * <p>
     * Constructor for GasScrubber.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    @Deprecated
    public GasScrubber(StreamInterface inletStream) {
        this("GasScrubber", inletStream);
    }

    /**
     * Constructor for GasScrubber.
     * 
     * @param name
     */
    public GasScrubber(String name) {
        super(name);
        this.setOrientation("vertical");
    }

    /**
     * <p>
     * Constructor for GasScrubber.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public GasScrubber(String name, StreamInterface inletStream) {
        super(name, inletStream);
        this.setOrientation("vertical");
    }

    public GasScrubberMechanicalDesign getMechanicalDesign() {
        return new GasScrubberMechanicalDesign(this);
    }

    /**
     * <p>
     * Setter for the field <code>inletStream</code>.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;

        thermoSystem = inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream("gasOutStream", gasSystem);

        thermoSystem = inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream("liquidOutStream", liquidSystem);
    }

    /**
     * <p>
     * addScrubberSection.
     * </p>
     *
     * @param type a {@link java.lang.String} object
     */
    public void addScrubberSection(String type) {
        scrubberSection.add(new SeparatorSection("section" + scrubberSection.size() + 1, type, this));
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
    public void displayResult() {
    }
}
