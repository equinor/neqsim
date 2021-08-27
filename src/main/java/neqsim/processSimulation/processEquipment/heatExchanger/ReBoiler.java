/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */

package neqsim.processSimulation.processEquipment.heatExchanger;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author  Even Solbraa
 * @version
 */
public class ReBoiler extends ProcessEquipmentBaseClass {

    private static final long serialVersionUID = 1000;

    boolean setTemperature = false;
    StreamInterface outStream;
    StreamInterface inStream;
    SystemInterface system;
    private double reboilerDuty = 0.0;

    /** Creates new Heater */
    public ReBoiler() {
    }

    public ReBoiler(StreamInterface inStream) {
        this.inStream = inStream;
        outStream = (StreamInterface) inStream.clone();
    }

    public StreamInterface getOutStream() {
        return outStream;
    }

    @Override
    public void run() {
        system = (SystemInterface) inStream.getThermoSystem().clone();
        ThermodynamicOperations testOps = new ThermodynamicOperations(system);
        testOps.TPflash();
        double oldH = system.getEnthalpy();
        testOps = new ThermodynamicOperations(system);
        testOps.TPflash();
        testOps.PHflash(oldH + reboilerDuty, 0);
        outStream.setThermoSystem(system);
        // if(setTemperature) system.setTemperature(temperatureOut);
        // else system.setTemperature(system.getTemperature()+dT);
        // testOps = new ThermodynamicOperations(system);
        // system.setTemperat ure(temperatureOut);
        // testOps.TPflash();
        // double newH = system.getEnthalpy();
        // dH = newH - oldH;
        // // system.setTemperature(temperatureOut);
        // // testOps.TPflash();
        // // system.setTemperature(temperatureOut);
        // outStream.setThermoSystem(system);
    }

    @Override
    public void displayResult() {
        System.out.println("out Temperature " + reboilerDuty);
    }

    public void runTransient() {
    }

    public double getReboilerDuty() {
        return reboilerDuty;
    }

    public void setReboilerDuty(double reboilerDuty) {
        this.reboilerDuty = reboilerDuty;
    }

}
