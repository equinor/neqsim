/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */

package neqsim.processSimulation.processEquipment.heatExchanger;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * @author Even Solbraa
 * @version
 */
public class NeqHeater extends Heater {
    private static final long serialVersionUID = 1000;

    StreamInterface outStream;
    StreamInterface inStream;
    SystemInterface system;
    double dH = 0.0;

    /** Creates new Heater */
    public NeqHeater() {}

    public NeqHeater(Stream inStream) {
        this.inStream = inStream;
        outStream = (Stream) inStream.clone();
    }

    @Override
    public StreamInterface getOutStream() {
        return outStream;
    }

    @Override
    public void setOutTemperature(double temperature) {
        this.setTemperature = true;
        this.temperatureOut = temperature;
    }

    @Override
    public void run() {
        system = (SystemInterface) inStream.getThermoSystem().clone();
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

    @Override
    public void displayResult() {
        System.out.println("heater dH: " + dH);
    }
}
