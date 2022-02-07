package neqsim.processSimulation.processEquipment;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

public interface TwoPortInterface {

    public double getInletPressure();

    public StreamInterface getInletStream();

    public double getInletTemperature();

    public double getOutletPressure();

    public StreamInterface getOutletStream();

    public double getOutletTemperature();

    public void setInletPressure();

    /**
     * <p>
     * setInletStream.
     * </p>
     *
     * @param inletStream a
     *                    {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *                    object
     */
    public void setInletStream(StreamInterface inletStream);

    public void setInletTemperature();

    /**
     * <p>
     * setOutletPressure.
     * </p>
     *
     * @param pressure a double
     */
    public void setOutletPressure(double pressure);

    public void setOutletStream();

    public void setOutletTemperature();
}
