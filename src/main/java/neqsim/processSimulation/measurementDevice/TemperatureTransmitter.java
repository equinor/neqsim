/*
 * TemperatureTransmitter.java
 *
 * Created on 6. juni 2006, 15:24
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author ESOL
 */
public class TemperatureTransmitter extends MeasurementDeviceBaseClass {

    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;

    /**
     * Creates a new instance of TemperatureTransmitter
     */
    public TemperatureTransmitter() {
        name = "Temperature Transmitter";
        unit = "K";
    }

    public TemperatureTransmitter(StreamInterface stream) {
        this();
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
    }

    public void displayResult() {
        System.out.println("measured temperature " + getMeasuredValue());
    }

    public double getMeasuredValue() {
        return stream.getThermoSystem().getTemperature();
    }

}
