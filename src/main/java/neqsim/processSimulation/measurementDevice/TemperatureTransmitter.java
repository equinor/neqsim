/*
 * TemperatureTransmitter.java
 *
 * Created on 6. juni 2006, 15:24
 */

package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * TemperatureTransmitter class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class TemperatureTransmitter extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;
    protected StreamInterface stream = null;

    /**
     * <p>Constructor for TemperatureTransmitter.</p>
     */
    public TemperatureTransmitter() {
        name = "Temperature Transmitter";
        unit = "K";
    }

    /**
     * <p>
     * Constructor for TemperatureTransmitter.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public TemperatureTransmitter(StreamInterface stream) {
        this();
        this.stream = stream;
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        System.out.println("measured temperature " + getMeasuredValue());
    }

    /** {@inheritDoc} */
    @Override
    public double getMeasuredValue() {
        return stream.getThermoSystem().getTemperature();
    }
}
