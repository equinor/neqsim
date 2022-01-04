/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.processSimulation.processEquipment.separator;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * ThreePhaseSeparator class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ThreePhaseSeparator extends Separator {
    private static final long serialVersionUID = 1000;

    StreamInterface waterOutStream = new Stream(waterSystem);

    String specifiedStream = "feed";
    double gasInAqueous = 0.00;
    String gasInAqueousSpec = "mole";

    double gasInOil = 0.00;
    String gasInOilSpec = "mole";

    double oilInGas = 0.00;
    String oilInGasSpec = "mole";

    double oilInAqueous = 0.00;
    String oilInAqueousSpec = "mole";

    double aqueousInGas = 0.00;
    String aqueousInGasSpec = "mole";

    double aqueousInOil = 0.00;
    String aqueousInOilSpec = "mole";

    /**
     * <p>
     * Constructor for ThreePhaseSeparator.
     * </p>
     */
    public ThreePhaseSeparator() {
        super();
    }

    /**
     * <p>
     * Constructor for ThreePhaseSeparator.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public ThreePhaseSeparator(StreamInterface inletStream) {
        this();
        addStream(inletStream);
    }

    /**
     * <p>
     * Constructor for ThreePhaseSeparator.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public ThreePhaseSeparator(String name, StreamInterface inletStream) {
        this();
        setName(name);
        addStream(inletStream);
    }

    /**
     * <p>
     * setEntrainment.
     * </p>
     *
     * @param val a double
     * @param specType a {@link java.lang.String} object
     * @param specifiedStream a {@link java.lang.String} object
     * @param phaseFrom a {@link java.lang.String} object
     * @param phaseTo a {@link java.lang.String} object
     */
    public void setEntrainment(double val, String specType, String specifiedStream,
            String phaseFrom, String phaseTo) {
        this.specifiedStream = specifiedStream;
        if (phaseFrom.equals("gas") && phaseTo.equals("aqueous")) {
            gasInAqueous = val;
            gasInAqueousSpec = specType;
        }
        if (phaseFrom.equals("gas") && phaseTo.equals("oil")) {
            gasInOil = val;
            gasInOilSpec = specType;
        }
        if (phaseFrom.equals("oil") && phaseTo.equals("aqueous")) {
            oilInAqueous = val;
            oilInAqueousSpec = specType;
        }
        if (phaseFrom.equals("oil") && phaseTo.equals("gas")) {
            oilInGas = val;
            oilInGasSpec = specType;
        }
        if (phaseFrom.equals("aqueous") && phaseTo.equals("gas")) {
            aqueousInGas = val;
            aqueousInGasSpec = specType;
        }
        if (phaseFrom.equals("aqueous") && phaseTo.equals("oil")) {
            aqueousInOil = val;
            aqueousInOilSpec = specType;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setInletStream(StreamInterface inletStream) {
        super.setInletStream(inletStream);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
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
        thermoSystem = (SystemInterface) inletStreamMixer.getOutStream().getThermoSystem().clone();

        thermoSystem.setMultiPhaseCheck(true);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.TPflash();
        // thermoSystem.display();
        thermoSystem.addPhaseFractionToPhase(gasInAqueous, gasInAqueousSpec, specifiedStream, "gas",
                "aqueous");
        thermoSystem.addPhaseFractionToPhase(gasInOil, gasInOilSpec, specifiedStream, "gas", "oil");
        thermoSystem.addPhaseFractionToPhase(oilInAqueous, oilInAqueousSpec, specifiedStream, "oil",
                "aqueous");
        thermoSystem.addPhaseFractionToPhase(oilInGas, oilInGasSpec, specifiedStream, "oil", "gas");
        thermoSystem.addPhaseFractionToPhase(aqueousInGas, aqueousInGasSpec, specifiedStream,
                "aqueous", "gas");
        thermoSystem.addPhaseFractionToPhase(aqueousInOil, aqueousInOilSpec, specifiedStream,
                "aqueous", "oil");
        // thermoSystem.init_x_y();
        // thermoSystem.display();
        // thermoSystem.init(3);
        // thermoSystem.setMultiPhaseCheck(false);

        // //gasSystem = (SystemInterface) thermoSystem.phaseToSystem(0);
        // //gasOutStream.setThermoSystem(gasSystem);
        if (thermoSystem.hasPhaseType("gas")) {
            gasOutStream.setThermoSystemFromPhase(thermoSystem, "gas");
        } else {
            gasOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
        }
        // //gasOutStream.run();

        //// liquidSystem = (SystemInterface) thermoSystem.phaseToSystem(1);
        //// liquidOutStream.setThermoSystem(liquidSystem);
        if (thermoSystem.hasPhaseType("oil")) {
            // thermoSystem.display();
            liquidOutStream.setThermoSystemFromPhase(thermoSystem, "oil");
            // thermoSystem.display();
        } else {
            liquidOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
        }
        // //liquidOutStream.run();

        //// waterSystem = (SystemInterface) thermoSystem.phaseToSystem(2);
        //// waterOutStream.setThermoSystem(waterSystem);
        if (thermoSystem.hasPhaseType("aqueous")) {
            waterOutStream.setThermoSystemFromPhase(thermoSystem, "aqueous");
        } else {
            waterOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
        }
        gasOutStream.run();
        liquidOutStream.run();
        waterOutStream.run();
        // //waterOutStream.run();
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        thermoSystem.display("from here " + getName());
        // gasOutStream.getThermoSystem().initPhysicalProperties();
        // waterOutStream.getThermoSystem().initPhysicalProperties();
        // try {
        // System.out.println("Gas Volume Flow Out " +
        // gasOutStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase()*gasOutStream.getThermoSystem().getPhase(0).getMolarMass()/gasOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity()*3600.0
        // + " m^3/h");
        // } finally {
        // }
        // try {
        // waterOutStream.getThermoSystem().display();
        // waterOutStream.run();
        // System.out.println("Water/MEG Volume Flow Out " +
        // waterOutStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase()*waterOutStream.getThermoSystem().getPhase(0).getMolarMass()/waterOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity()*3600.0
        // + " m^3/h");
        // System.out.println("Density MEG " +
        // waterOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity());
        // } finally {
        // }
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

    /** {@inheritDoc} */
    @Override
    public double getEntropyProduction(String unit) {
        double entrop = 0.0;
        for (int i = 0; i < numberOfInputStreams; i++) {
            inletStreamMixer.getStream(i).getFluid().init(3);
            entrop += inletStreamMixer.getStream(i).getFluid().getEntropy(unit);
        }
        getWaterOutStream().getThermoSystem().init(3);
        getOilOutStream().getThermoSystem().init(3);
        getGasOutStream().getThermoSystem().init(3);

        return getWaterOutStream().getThermoSystem().getEntropy(unit)
                + getOilOutStream().getThermoSystem().getEntropy(unit)
                + getGasOutStream().getThermoSystem().getEntropy(unit) - entrop;
    }

    /** {@inheritDoc} */
    @Override
    public double getExergyChange(String unit, double sourrondingTemperature) {
        double entrop = 0.0;
        for (int i = 0; i < numberOfInputStreams; i++) {
            inletStreamMixer.getStream(i).getFluid().init(3);
            entrop += inletStreamMixer.getStream(i).getFluid().getExergy(sourrondingTemperature,
                    unit);
        }
        getWaterOutStream().getThermoSystem().init(3);
        getOilOutStream().getThermoSystem().init(3);
        getGasOutStream().getThermoSystem().init(3);

        return getWaterOutStream().getThermoSystem().getExergy(sourrondingTemperature, unit)
                + getOilOutStream().getThermoSystem().getEntropy(unit)
                + getGasOutStream().getThermoSystem().getExergy(sourrondingTemperature, unit)
                - entrop;
    }
}
