package neqsim.processSimulation.processEquipment;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

public abstract class TwoPortEquipment extends ProcessEquipmentBaseClass implements TwoPortInterface {
    protected StreamInterface inStream;
    protected StreamInterface outStream;

    public TwoPortEquipment(String name) {
        super(name);
    }

    public TwoPortEquipment(String name, StreamInterface stream) {
        this.inStream = stream;
        this.outStream = stream.clone();
    }

    /** {@inheritDoc} */
    @Override
    public double getInletPressure() {
        return getInletStream().getPressure();
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getInletStream() {
        return inStream;
    }

    /** {@inheritDoc} */
    @Override
    public double getInletTemperature() {
        return getInletStream().getTemperature();
    }

    /** {@inheritDoc} */
    @Override
    public double getOutletPressure() {
        return getOutletStream().getPressure();
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutletStream() {
        return outStream;
    }

    /** {@inheritDoc} */
    @Override
    public double getOutletTemperature() {
        return getOutletStream().getTemperature();
    }

    /** {@inheritDoc} */
    @Override
    public void setInletPressure(double pressure) {
        this.inStream.setPressure(pressure);
    }

    /** {@inheritDoc} */
    @Override
    public void setInletStream(StreamInterface stream) {
        this.inStream = stream;
    }

    /** {@inheritDoc} */
    @Override
    public void setInletTemperature(double temperature) {
        this.inStream.setTemperature(temperature, "unit");
    }

    /** {@inheritDoc} */
    @Override
    public void setOutletPressure(double pressure) {
        this.outStream.setPressure(pressure);
    }

    /** {@inheritDoc} */
    @Override
    public void setOutletStream(StreamInterface stream) {
        this.outStream = stream;
    }

    /** {@inheritDoc} */
    @Override
    public void setOutletTemperature(double temperature) {
        this.outStream.setTemperature(temperature, "unit");
    }
}