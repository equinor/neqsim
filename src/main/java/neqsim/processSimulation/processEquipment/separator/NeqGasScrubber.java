package neqsim.processSimulation.processEquipment.separator;

import java.util.ArrayList;

import neqsim.processSimulation.mechanicalDesign.separator.GasScrubberMechanicalDesign;
import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * NeqGasScrubber class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NeqGasScrubber extends Separator {
    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    ArrayList<SeparatorSection> scrubberSection = null;
    Stream inletStream;
    Stream gasOutStream;
    Stream liquidOutStream;
    String name = new String();

    /**
     * <p>
     * Constructor for NeqGasScrubber.
     * </p>
     */
    public NeqGasScrubber() {
        super();
        this.setOrientation("vertical");
    }

    /**
     * <p>
     * Constructor for NeqGasScrubber.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public NeqGasScrubber(Stream inletStream) {
        this();
        this.setInletStream(inletStream);
    }

    /**
     * <p>
     * Constructor for NeqGasScrubber.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public NeqGasScrubber(String name, Stream inletStream) {
        this();
        this.name = name;
        this.setInletStream(inletStream);
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
    public void setInletStream(Stream inletStream) {
        this.inletStream = inletStream;

        thermoSystem = inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream(gasSystem);

        thermoSystem = inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream(liquidSystem);
    }

    /**
     * <p>
     * addScrubberSection.
     * </p>
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

    /**
     * <p>
     * runTransient.
     * </p>
     */
    public void runTransient() {}
}
