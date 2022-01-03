/*
 * CompressorInterface.java
 *
 * Created on 22. august 2001, 17:20
 */
package neqsim.processSimulation.processEquipment.compressor;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * CompressorInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface CompressorInterface extends ProcessEquipmentInterface {
    /** {@inheritDoc} */
    @Override
    public void run();

    /**
     * <p>
     * setOutletPressure.
     * </p>
     *
     * @param pressure a double
     */
    public void setOutletPressure(double pressure);

    /**
     * <p>
     * setInletStream.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public void setInletStream(StreamInterface inletStream);

    /**
     * <p>
     * getEnergy.
     * </p>
     *
     * @return a double
     */
    public double getEnergy();

    /** {@inheritDoc} */
    @Override
    public String getName();

    /**
     * <p>
     * getOutStream.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOutStream();

    /**
     * <p>
     * getIsentropicEfficiency.
     * </p>
     *
     * @return a double
     */
    public double getIsentropicEfficiency();

    /**
     * <p>
     * setIsentropicEfficiency.
     * </p>
     *
     * @param isentropicEfficientcy a double
     */
    public void setIsentropicEfficiency(double isentropicEfficientcy);

    /**
     * <p>
     * runTransient.
     * </p>
     */
    public void runTransient();

    /**
     * <p>
     * getPolytropicEfficiency.
     * </p>
     *
     * @return a double
     */
    public double getPolytropicEfficiency();

    /**
     * <p>
     * setPolytropicEfficiency.
     * </p>
     *
     * @param polytropicEfficiency a double
     */
    public void setPolytropicEfficiency(double polytropicEfficiency);

    /**
     * <p>
     * getAntiSurge.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.compressor.AntiSurge} object
     */
    public AntiSurge getAntiSurge();
}
