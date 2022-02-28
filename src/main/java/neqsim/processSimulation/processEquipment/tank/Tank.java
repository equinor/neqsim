package neqsim.processSimulation.processEquipment.tank;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Tank class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Tank extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    Stream gasOutStream;
    Stream liquidOutStream;
    private int numberOfInputStreams = 0;
    Mixer inletStreamMixer = new Mixer("Separator Inlet Stream Mixer");
    private double efficiency = 1.0;
    private double liquidCarryoverFraction = 0.0;
    private double gasCarryunderFraction = 0.0;
    private double volume = 136000.0;
    double steelWallTemperature = 298.15, steelWallMass = 1840.0 * 1000.0, steelWallArea = 15613.0,
            heatTransferNumber = 5.0, steelCp = 450.0;
    double separatorLength = 40.0, separatorDiameter = 60.0;
    double liquidVolume = 235.0, gasVolume = 15.0;
    private double liquidLevel = liquidVolume / (liquidVolume + gasVolume);

    /**
     * <p>
     * Constructor for Tank.
     * </p>
     */
    @Deprecated
    public Tank() {
        super("Tank");
    }

    /**
     * <p>
     * Constructor for Tank.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    @Deprecated
    public Tank(Stream inletStream) {
        this("Tank", inletStream);
    }

    /**
     * Constructor for Tank.
     * 
     * @param name
     */
    public Tank(String name) {
        super(name);
    }

    /**
     * <p>
     * Constructor for Tank.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Tank(String name, Stream inletStream) {
        super(name);
        addStream(inletStream);
    }

    /**
     * <p>
     * setInletStream.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public void setInletStream(Stream inletStream) {
        inletStreamMixer.addStream(inletStream);
        thermoSystem = inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream("gasOutStream", gasSystem);

        thermoSystem = inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream("liquidOutStream", liquidSystem);
    }

    /**
     * <p>
     * addStream.
     * </p>
     *
     * @param newStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public void addStream(StreamInterface newStream) {
        if (numberOfInputStreams == 0) {
            setInletStream((Stream) newStream);
        } else {
            inletStreamMixer.addStream(newStream);
        }
        numberOfInputStreams++;
    }

    /**
     * <p>
     * Getter for the field <code>liquidOutStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getLiquidOutStream() {
        return liquidOutStream;
    }

    /**
     * <p>
     * Getter for the field <code>gasOutStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getGasOutStream() {
        return gasOutStream;
    }

    /**
     * <p>
     * getGas.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getGas() {
        return getGasOutStream();
    }

    /**
     * <p>
     * getLiquid.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getLiquid() {
        return getLiquidOutStream();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        inletStreamMixer.run();
        SystemInterface thermoSystem2 = inletStreamMixer.getOutStream().getThermoSystem().clone();
        ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem2);
        ops.VUflash(thermoSystem2.getVolume(), thermoSystem2.getInternalEnergy());
        System.out.println("Volume " + thermoSystem2.getVolume() + " internalEnergy "
                + thermoSystem2.getInternalEnergy());
        steelWallTemperature = thermoSystem2.getTemperature();
        if (thermoSystem2.hasPhaseType("gas")) {
            gasOutStream.setThermoSystemFromPhase(thermoSystem2, "gas");
        } else {
            gasOutStream.setThermoSystemFromPhase(thermoSystem2.getEmptySystemClone(), "gas");
        }
        if (thermoSystem2.hasPhaseType("oil")) {
            liquidOutStream.setThermoSystemFromPhase(thermoSystem2, "oil");
        } else {
            gasOutStream.setThermoSystemFromPhase(thermoSystem2.getEmptySystemClone(), "oil");
        }

        thermoSystem = thermoSystem2.clone();
        thermoSystem.setTotalNumberOfMoles(1.0e-10);
        thermoSystem.init(1);
        System.out.println("number of phases " + thermoSystem.getNumberOfPhases());
        for (int j = 0; j < thermoSystem.getNumberOfPhases(); j++) {
            double relFact = gasVolume / (thermoSystem.getPhase(j).getVolume() * 1.0e-5);
            if (j == 1) {
                relFact = liquidVolume / (thermoSystem.getPhase(j).getVolume() * 1.0e-5);
            }
            for (int i = 0; i < thermoSystem.getPhase(j).getNumberOfComponents(); i++) {
                thermoSystem.addComponent(
                        thermoSystem.getPhase(j).getComponent(i).getComponentName(),
                        relFact * thermoSystem.getPhase(j).getComponent(i)
                                .getNumberOfMolesInPhase(),
                        j);
            }
        }
        if (thermoSystem2.getNumberOfPhases() == 2) {
            thermoSystem.setBeta(gasVolume / thermoSystem2.getPhase(0).getMolarVolume()
                    / (gasVolume / thermoSystem2.getPhase(0).getMolarVolume()
                            + liquidVolume / thermoSystem2.getPhase(1).getMolarVolume()));
        } else {
            thermoSystem.setBeta(1.0 - 1e-10);
        }
        thermoSystem.init(3);
        System.out.println("moles in separator " + thermoSystem.getNumberOfMoles());
        double volume1 = thermoSystem.getVolume();
        System.out.println("volume1 bef " + volume1);
        System.out.println("beta " + thermoSystem.getBeta());

        if (thermoSystem2.getNumberOfPhases() == 2) {
            liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
        } else {
            liquidLevel = 1e-10;
        }
        liquidVolume = getLiquidLevel() * 3.14 / 4.0 * separatorDiameter * separatorDiameter
                * separatorLength;
        gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * separatorDiameter * separatorDiameter
                * separatorLength;
        System.out.println("moles out" + liquidOutStream.getThermoSystem().getTotalNumberOfMoles());
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        thermoSystem.display();
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt) {
        inletStreamMixer.run();

        System.out.println("moles out" + liquidOutStream.getThermoSystem().getTotalNumberOfMoles());
        // double inMoles =
        // inletStreamMixer.getOutStream().getThermoSystem().getTotalNumberOfMoles();
        // double gasoutMoles = gasOutStream.getThermoSystem().getNumberOfMoles();
        // double liqoutMoles = liquidOutStream.getThermoSystem().getNumberOfMoles();
        thermoSystem.init(3);
        gasOutStream.getThermoSystem().init(3);
        liquidOutStream.getThermoSystem().init(3);
        inletStreamMixer.getOutStream().getThermoSystem().init(3);
        double volume1 = thermoSystem.getVolume();
        System.out.println("volume1 " + volume1);
        double deltaEnergy = inletStreamMixer.getOutStream().getThermoSystem().getEnthalpy()
                - gasOutStream.getThermoSystem().getEnthalpy()
                - liquidOutStream.getThermoSystem().getEnthalpy();
        System.out.println("enthalph delta " + deltaEnergy);
        double wallHeatTransfer = heatTransferNumber * steelWallArea
                * (steelWallTemperature - thermoSystem.getTemperature()) * dt;
        System.out.println("delta temp " + (steelWallTemperature - thermoSystem.getTemperature()));
        steelWallTemperature -= wallHeatTransfer / (steelCp * steelWallMass);
        System.out.println("wall Temperature " + steelWallTemperature);

        double newEnergy = thermoSystem.getInternalEnergy() + dt * deltaEnergy + wallHeatTransfer;

        System.out.println("energy cooling " + dt * deltaEnergy);
        System.out.println("energy heating " + wallHeatTransfer / dt + " kW");

        for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
            double dn = 0.0;
            for (int k = 0; k < inletStreamMixer.getOutStream().getThermoSystem()
                    .getNumberOfPhases(); k++) {
                dn += inletStreamMixer.getOutStream().getThermoSystem().getPhase(k).getComponent(i)
                        .getNumberOfMolesInPhase();
            }
            dn = dn - gasOutStream.getThermoSystem().getPhase(0).getComponent(i)
                    .getNumberOfMolesInPhase()
                    - liquidOutStream.getThermoSystem().getPhase(0).getComponent(i)
                            .getNumberOfMolesInPhase();
            System.out.println("dn " + dn);
            thermoSystem.addComponent(inletStreamMixer.getOutStream().getThermoSystem().getPhase(0)
                    .getComponent(i).getComponentName(), dn * dt);
        }

        System.out.println("total moles " + thermoSystem.getTotalNumberOfMoles());
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.VUflash(volume1, newEnergy);

        setOutComposition(thermoSystem);
        setTempPres(thermoSystem.getTemperature(), thermoSystem.getPressure());

        if (thermoSystem.hasPhaseType("oil")) {
            liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
        } else {
            liquidLevel = 1e-10;
        }
        System.out.println("liquid level " + liquidLevel);
        liquidVolume = getLiquidLevel() * 3.14 / 4.0 * separatorDiameter * separatorDiameter
                * separatorLength;
        gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * separatorDiameter * separatorDiameter
                * separatorLength;
    }

    /**
     * <p>
     * setOutComposition.
     * </p>
     *
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public void setOutComposition(SystemInterface thermoSystem) {
        for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
            if (thermoSystem.hasPhaseType("gas")) {
                getGasOutStream().getThermoSystem().getPhase(0).getComponent(i)
                        .setx(thermoSystem.getPhase(thermoSystem.getPhaseNumberOfPhase("gas"))
                                .getComponent(i).getx());
            }
            if (thermoSystem.hasPhaseType("oil")) {
                getLiquidOutStream().getThermoSystem().getPhase(0).getComponent(i)
                        .setx(thermoSystem.getPhase(thermoSystem.getPhaseNumberOfPhase("oil"))
                                .getComponent(i).getx());
            }
        }
    }

    /**
     * <p>
     * setTempPres.
     * </p>
     *
     * @param temp a double
     * @param pres a double
     */
    public void setTempPres(double temp, double pres) {
        gasOutStream.getThermoSystem().setTemperature(temp);
        liquidOutStream.getThermoSystem().setTemperature(temp);

        inletStreamMixer.setPressure(pres);
        gasOutStream.getThermoSystem().setPressure(pres);
        liquidOutStream.getThermoSystem().setPressure(pres);

        inletStreamMixer.run();
        gasOutStream.run();
        liquidOutStream.run();
    }

    /**
     * <p>
     * Getter for the field <code>efficiency</code>.
     * </p>
     *
     * @return a double
     */
    public double getEfficiency() {
        return efficiency;
    }

    /**
     * <p>
     * Setter for the field <code>efficiency</code>.
     * </p>
     *
     * @param efficiency a double
     */
    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }

    /**
     * <p>
     * Getter for the field <code>liquidCarryoverFraction</code>.
     * </p>
     *
     * @return a double
     */
    public double getLiquidCarryoverFraction() {
        return liquidCarryoverFraction;
    }

    /**
     * <p>
     * Setter for the field <code>liquidCarryoverFraction</code>.
     * </p>
     *
     * @param liquidCarryoverFraction a double
     */
    public void setLiquidCarryoverFraction(double liquidCarryoverFraction) {
        this.liquidCarryoverFraction = liquidCarryoverFraction;
    }

    /**
     * <p>
     * Getter for the field <code>gasCarryunderFraction</code>.
     * </p>
     *
     * @return a double
     */
    public double getGasCarryunderFraction() {
        return gasCarryunderFraction;
    }

    /**
     * <p>
     * Setter for the field <code>gasCarryunderFraction</code>.
     * </p>
     *
     * @param gasCarryunderFraction a double
     */
    public void setGasCarryunderFraction(double gasCarryunderFraction) {
        this.gasCarryunderFraction = gasCarryunderFraction;
    }

    /**
     * <p>
     * Getter for the field <code>liquidLevel</code>.
     * </p>
     *
     * @return a double
     */
    public double getLiquidLevel() {
        return liquidLevel;
    }

    /**
     * <p>
     * Getter for the field <code>volume</code>.
     * </p>
     *
     * @return a double
     */
    public double getVolume() {
        return volume;
    }

    /**
     * <p>
     * Setter for the field <code>volume</code>.
     * </p>
     *
     * @param volume a double
     */
    public void setVolume(double volume) {
        this.volume = volume;
    }
}
