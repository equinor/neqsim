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

    @Override
    public StreamInterface getInletStream() {
        return inStream;
    }

    @Override
    public StreamInterface getOutletStream() {
        return outStream;
    }

    @Override
    public double getInletTemperature() {
        return getInletStream().getTemperature();
    }

    @Override
    public double getInletPressure() {
        return getInletStream().getPressure();
    }

    @Override
    public double getOutletTemperature() {
        return getOutletStream().getTemperature();
    }

    @Override
    public double getOutletPressure() {
        return getOutletStream().getPressure();
    }

    @Override
    public void setInletPressure(double pressure) {
        this.inStream.setPressure(pressure);
    }

    @Override
    public void setInletStream(StreamInterface stream) {
        this.inStream = stream;
    }

    @Override
    public void setInletTemperature(double temperature) {
        this.inStream.setTemperature(temperature, "unit");
    }

    @Override
    public void setOutletPressure(double pressure) {
        this.outStream.setPressure(pressure);
    }

    @Override
    public void setOutletStream(StreamInterface stream) {
        this.outStream = stream;
    }

    @Override
    public void setOutletTemperature(double temperature) {
        this.outStream.setTemperature(temperature, "unit");
    }
}