/*
 * ValveInterface.java
 *
 * Created on 22. august 2001, 17:20
 */
package neqsim.processSimulation.processEquipment.valve;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.TwoPortInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ValveInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface ValveInterface extends ProcessEquipmentInterface, TwoPortInterface {
    /** {@inheritDoc} */
    @Override
    public void run();


    /**
     * <p>
     * getOutStream.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOutStream();

    /** {@inheritDoc} */
    @Override
    public String getName();

    /**
     * <p>
     * isIsoThermal.
     * </p>
     *
     * @return a boolean
     */
    public boolean isIsoThermal();

    /**
     * <p>
     * setIsoThermal.
     * </p>
     *
     * @param isoThermal a boolean
     */
    public void setIsoThermal(boolean isoThermal);

    /**
     * <p>
     * getPercentValveOpening.
     * </p>
     *
     * @return a double
     */
    public double getPercentValveOpening();

    /**
     * <p>
     * setPercentValveOpening.
     * </p>
     *
     * @param percentValveOpening a double
     */
    public void setPercentValveOpening(double percentValveOpening);

    /**
     * <p>
     * getCv.
     * </p>
     *
     * @return a double
     */
    public double getCv();

    /**
     * <p>
     * setCv.
     * </p>
     *
     * @param Cv a double
     */
    public void setCv(double Cv);


    /** {@inheritDoc} */
    @Override
    public SystemInterface getThermoSystem();
}
