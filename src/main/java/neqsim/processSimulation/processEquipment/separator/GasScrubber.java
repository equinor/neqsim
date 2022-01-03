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
import neqsim.thermo.system.SystemInterface;

/**
 * <p>GasScrubber class.</p>
 *
 * @author  Even Solbraa
 * @version $Id: $Id
 */
public class GasScrubber extends Separator {

    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    ArrayList<SeparatorSection> scrubberSection = null;
    Stream inletStream;
    Stream gasOutStream;
    Stream liquidOutStream;
    String name = new String();

    /**
     * Creates new GasScrubber
     */
    public GasScrubber() {
        super();
        mechanicalDesign = new GasScrubberMechanicalDesign(this);
        this.setOrientation("vertical");
    }

    /**
     * <p>Constructor for GasScrubber.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public GasScrubber(Stream inletStream) {
        this();
        this.setInletStream(inletStream);
    }

    /**
     * <p>Constructor for GasScrubber.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public GasScrubber(String name, Stream inletStream) {
        this();
        this.name = name;
        this.setInletStream(inletStream);
    }

    /** {@inheritDoc} */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Setter for the field <code>inletStream</code>.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public void setInletStream(Stream inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream(gasSystem);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream(liquidSystem);
    }

    /**
     * <p>addScrubberSection.</p>
     *
     * @param type a {@link java.lang.String} object
     */
    public void addScrubberSection(String type) {
        scrubberSection.add(new SeparatorSection(type, this));
    }

    /** {@inheritDoc} */
    @Override
    public Stream getLiquidOutStream() {
        return liquidOutStream;
    }

    /** {@inheritDoc} */
    @Override
    public Stream getGasOutStream() {
        return gasOutStream;
    }

    /** {@inheritDoc} */
    @Override
    public Stream getGas() {
        return getGasOutStream();
    }

    /** {@inheritDoc} */
    @Override
    public Stream getLiquid() {
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
