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
 * <p>
 * ReBoiler class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ReBoiler extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000;

    boolean setTemperature = false;
    StreamInterface outStream;
    StreamInterface inStream;
    SystemInterface system;
    private double reboilerDuty = 0.0;

    /**
     * <p>
     * Constructor for ReBoiler.
     * </p>
     */
    public ReBoiler() {}

    /**
     * <p>
     * Constructor for ReBoiler.
     * </p>
     *
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public ReBoiler(StreamInterface inStream) {
        this.inStream = inStream;
        outStream = (StreamInterface) inStream.clone();
    }

    /**
     * <p>
     * Getter for the field <code>outStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOutStream() {
        return outStream;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        System.out.println("out Temperature " + reboilerDuty);
    }

    /**
     * <p>
     * runTransient.
     * </p>
     */
    public void runTransient() {}

    /**
     * <p>
     * Getter for the field <code>reboilerDuty</code>.
     * </p>
     *
     * @return a double
     */
    public double getReboilerDuty() {
        return reboilerDuty;
    }

    /**
     * <p>
     * Setter for the field <code>reboilerDuty</code>.
     * </p>
     *
     * @param reboilerDuty a double
     */
    public void setReboilerDuty(double reboilerDuty) {
        this.reboilerDuty = reboilerDuty;
    }
}
