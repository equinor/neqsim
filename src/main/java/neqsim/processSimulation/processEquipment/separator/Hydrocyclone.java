package neqsim.processSimulation.processEquipment.separator;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Hydrocyclone class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Hydrocyclone extends Separator {
    private static final long serialVersionUID = 1000;

    double PDR = 1.4;
    double rejectRatio = 0.9;
    double underflowPressure = 1.0;
    double overflowPressure = 1.0;
    double separationEfficiency = 0.9;
    double oilInAqueous = 100e-6;
    StreamInterface waterOutStream = new Stream(waterSystem);

    /**
     * <p>
     * Constructor for Hydrocyclone.
     * </p>
     */
    public Hydrocyclone() {
        super();
    }

    /**
     * <p>
     * Constructor for Hydrocyclone.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public Hydrocyclone(StreamInterface inletStream) {
        this();
        addStream(inletStream);
    }

    /**
     * <p>
     * Constructor for Hydrocyclone.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public Hydrocyclone(String name, StreamInterface inletStream) {
        this();
        setName(name);
        addStream(inletStream);
    }

    /** {@inheritDoc} */
    @Override
    public void setInletStream(StreamInterface inletStream) {
        super.setInletStream(inletStream);

        thermoSystem = inletStream.getThermoSystem().clone();
        waterSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        waterOutStream = new Stream(waterSystem);
    }

    /**
     * <p>
     * Getter for the field <code>waterOutStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getWaterOutStream() {
        return waterOutStream;
    }

    /**
     * <p>
     * getOilOutStream.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOilOutStream() {
        return liquidOutStream;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        inletStreamMixer.run();
        thermoSystem = inletStreamMixer.getOutStream().getThermoSystem().clone();

        // double oilInWaterIn = 0.0001; //
        // thermoSystem.getPhase("aqueous").getOilMolarConcentration();
        // double oilInWaterOut = 0.0;
        double inPressure = thermoSystem.getPressure("bara");
        underflowPressure = inPressure / 2.0;
        overflowPressure = inPressure + (inPressure - underflowPressure) / 1.0 / PDR;
        separationEfficiency = 0.9;
        // oilInWaterOut = oilInWaterIn * separationEfficiency;

        thermoSystem.setMultiPhaseCheck(true);
        thermoSystem.setPressure(underflowPressure);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.TPflash();

        thermoSystem.addPhaseFractionToPhase(oilInAqueous, "mole", "oil", "aqueous");
        // thermoOps.TPflash();
        thermoSystem.addPhaseFractionToPhase(1.0, "mole", "oil", "gas");
        thermoSystem.addPhaseFractionToPhase(0.02, "mole", "aqueous", "gas");
        // thermoOps.TPflash();

        if (thermoSystem.hasPhaseType("gas")) {
            gasOutStream.setThermoSystemFromPhase(thermoSystem, "gas");
        } else {
            gasOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
        }
        // //gasOutStream.run();

        //// liquidSystem = thermoSystem.phaseToSystem(1);
        //// liquidOutStream.setThermoSystem(liquidSystem);
        if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
            liquidOutStream.setThermoSystemFromPhase(thermoSystem, "liquid");
            liquidOutStream.getFluid().init(2);
        } else {
            liquidOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
        }
        // sOutStream.setPressure(overflowPressure, "bara");
        gasOutStream.run();
        liquidOutStream.run();
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        thermoSystem.display("from here " + getName());
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /**
     * <p>
     * runTransient.
     * </p>
     */
    public void runTransient() {}
}
