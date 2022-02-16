package neqsim.processSimulation.processEquipment.heatExchanger;

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

    SystemInterface system;
    double dH = 0.0;


    /**
     * <p>
     * Constructor for NeqHeater.
     * </p>
     *
     * @param name
     * @param inStream a
     *                 {@link neqsim.processSimulation.processEquipment.stream.Stream}
     *                 object
     */
    public NeqHeater(String name, StreamInterface stream) {
        super(name, stream);
    }

    /** {@inheritDoc} */
    @Override
    @Deprecated
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
