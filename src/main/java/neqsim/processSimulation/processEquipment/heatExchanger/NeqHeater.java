package neqsim.processSimulation.processEquipment.heatExchanger;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * NeqHeater class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NeqHeater extends Heater {
    private static final long serialVersionUID = 1000;

    StreamInterface outStream;
    StreamInterface inStream;
    SystemInterface system;
    double dH = 0.0;

    /**
     * <p>
     * Constructor for NeqHeater.
     * </p>
     */
    @Deprecated
    public NeqHeater() {
        this("NeqHeater");
    }

    /**
     * <p>
     * Constructor for NeqHeater.
     * </p>
     *
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    @Deprecated
    public NeqHeater(Stream inStream) {
        this("NeqHeater", inStream);
    }

    /**
     * Constructor for NeqHeater.
     * 
     * @param name
     */
    public NeqHeater(String name) {
        super(name);
    }

    /**
     * Constructor for NeqHeater.
     * 
     * @param name
     * @param inStream
     */
    public NeqHeater(String name, Stream inStream) {
        super(name, inStream);
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutStream() {
        return outStream;
    }

    /** {@inheritDoc} */
    @Override
    public void setOutTemperature(double temperature) {
        this.setTemperature = true;
        this.temperatureOut = temperature;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        system = inStream.getThermoSystem().clone();
        double oldH = system.getEnthalpy();
        if (setTemperature) {
            system.setTemperature(temperatureOut);
        } else {
            system.setTemperature(system.getTemperature() + dT);
        }
        system.init(3);
        double newH = system.getEnthalpy();
        dH = newH - oldH;
        // system.setTemperature(temperatureOut);
        // testOps.TPflash();
        // system.setTemperature(temperatureOut);
        outStream.setThermoSystem(system);
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        System.out.println("heater dH: " + dH);
    }
}
