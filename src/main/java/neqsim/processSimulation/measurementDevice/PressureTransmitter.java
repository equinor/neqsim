package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author ESOL
 */
public class PressureTransmitter extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;

    public PressureTransmitter() {
        name = "Pressure Transmitter";
        unit = "bar";
    }

    public PressureTransmitter(StreamInterface stream) {
        this();
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
    }

    @Override
    public void displayResult() {
        System.out.println("measured temperature " + stream.getPressure());
    }

    @Override
    public double getMeasuredValue() {
        return stream.getThermoSystem().getPressure();
    }
}
