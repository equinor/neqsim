package neqsim.processSimulation.processEquipment.distillation;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * SimpleTray class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SimpleTray extends neqsim.processSimulation.processEquipment.mixer.Mixer
        implements TrayInterface {
    private static final long serialVersionUID = 1000;

    double heatInput = 0.0;
    private double temperature = Double.NaN, trayPressure = -1.0;

    /**
     * <p>
     * Constructor for SimpleTray.
     * </p>
     */
    public SimpleTray() {}

    /**
     * <p>
     * init.
     * </p>
     */
    public void init() {
        int pp = 0;
        if (streams.size() == 3) {
            pp = 1;
        }
        for (int k = pp; k < streams.size(); k++) {
            (streams.get(k).getThermoSystem()).setTemperature(temperature);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setHeatInput(double heatinp) {
        this.heatInput = heatinp;
    }

    /**
     * <p>
     * calcMixStreamEnthalpy0.
     * </p>
     *
     * @return a double
     */
    public double calcMixStreamEnthalpy0() {
        double enthalpy = 0;

        for (int k = 0; k < streams.size(); k++) {
            streams.get(k).getThermoSystem().init(3);
            enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
            // System.out.println("total enthalpy k : " + ( ((Stream)
            // streams.get(k)).getThermoSystem()).getEnthalpy());
        }
        // System.out.println("total enthalpy of streams: " + enthalpy);
        return enthalpy;
    }

    /** {@inheritDoc} */
    @Override
    public double calcMixStreamEnthalpy() {
        double enthalpy = heatInput;
        if (isSetEnergyStream()) {
            enthalpy -= energyStream.getDuty();
        }

        for (int k = 0; k < streams.size(); k++) {
            streams.get(k).getThermoSystem().init(3);
            enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
            // System.out.println("total enthalpy k : " + ( ((Stream)
            // streams.get(k)).getThermoSystem()).getEnthalpy());
        }
        // System.out.println("total enthalpy of streams: " + enthalpy);
        return enthalpy;
    }

    /**
     * <p>
     * run2.
     * </p>
     */
    public void run2() {
        super.run();
        temperature = mixedStream.getTemperature();
    }

    /**
     * <p>
     * TPflash.
     * </p>
     */
    public void TPflash() {}

    /** {@inheritDoc} */
    @Override
    public void run() {
        double enthalpy = 0.0;
        // double flowRate = ((Stream)
        // streams.get(0)).getThermoSystem().getFlowRate("kg/hr");
        // ((Stream) streams.get(0)).getThermoSystem().display();
        SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();

        // System.out.println("total number of moles " +
        // thermoSystem2.getTotalNumberOfMoles());
        if (trayPressure > 0) {
            thermoSystem2.setPressure(trayPressure);
        }
        mixedStream.setThermoSystem(thermoSystem2);
        // thermoSystem2.display();
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
        if (streams.size() > 0) {
            mixedStream.getThermoSystem().setNumberOfPhases(2);
            mixedStream.getThermoSystem().reInitPhaseType();
            mixedStream.getThermoSystem().init(0);

            mixStream();
            if (trayPressure > 0)
                mixedStream.setPressure(trayPressure, "bara");
            enthalpy = calcMixStreamEnthalpy();
            // System.out.println("temp guess " + guessTemperature());
            if (!isSetOutTemperature()) {
                // mixedStream.getThermoSystem().setTemperature(guessTemperature());
            } else {
                mixedStream.setTemperature(getOutTemperature(), "K");
            }
            // System.out.println("filan temp " + mixedStream.getTemperature());
        }
        if (isSetOutTemperature()) {
            if (!Double.isNaN(getOutTemperature()))
                mixedStream.getThermoSystem().setTemperature(getOutTemperature());
            testOps.TPflash();
            mixedStream.getThermoSystem().init(2);
        } else {
            try {
                testOps.PHflash(enthalpy, 0);
            } catch (Exception e) {
                if (!Double.isNaN(getOutTemperature()))
                    mixedStream.getThermoSystem().setTemperature(getOutTemperature());
                testOps.TPflash();
            }
        }
        setTemperature(mixedStream.getTemperature());
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt) {}

    /**
     * <p>
     * getGasOutStream.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getGasOutStream() {
        return new Stream("", mixedStream.getThermoSystem().phaseToSystem(0));
    }

    /**
     * <p>
     * getLiquidOutStream.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getLiquidOutStream() {
        return new Stream("", mixedStream.getThermoSystem().phaseToSystem(1));
    }

    /**
     * <p>
     * Getter for the field <code>temperature</code>.
     * </p>
     *
     * @return the temperature
     */
    public double getTemperature() {
        return temperature;
    }

    /** {@inheritDoc} */
    @Override
    public void setPressure(double pres) {
        trayPressure = pres;
    }

    /** {@inheritDoc} */
    @Override
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    /** {@inheritDoc} */
    @Override
    public double guessTemperature() {
        if (Double.isNaN(temperature)) {
            double gtemp = 0;
            for (int k = 0; k < streams.size(); k++) {
                gtemp += streams.get(k).getThermoSystem().getTemperature()
                        * streams.get(k).getThermoSystem().getNumberOfMoles()
                        / mixedStream.getThermoSystem().getNumberOfMoles();
            }
            // System.out.println("guess temperature " + gtemp);
            return gtemp;
        } else {
            // System.out.println("temperature " + temperature);
            return temperature;
        }
    }
}
