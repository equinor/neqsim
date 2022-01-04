/*
 * ValveInterface.java
 *
 * Created on 22. august 2001, 17:20
 */

package neqsim.processSimulation.processEquipment.valve;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>ValveInterface interface.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface ValveInterface extends ProcessEquipmentInterface {
	/** {@inheritDoc} */
    @Override
	public void run();

    /**
     * <p>setOutletPressure.</p>
     *
     * @param pressure a double
     */
    public void setOutletPressure(double pressure);

    /**
     * <p>setInletStream.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public void setInletStream(StreamInterface inletStream);

    /**
     * <p>getOutStream.</p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOutStream();

	/** {@inheritDoc} */
    @Override
	public String getName();

    /**
     * <p>isIsoThermal.</p>
     *
     * @return a boolean
     */
    public boolean isIsoThermal();

    /**
     * <p>setIsoThermal.</p>
     *
     * @param isoThermal a boolean
     */
    public void setIsoThermal(boolean isoThermal);

    /**
     * <p>getPercentValveOpening.</p>
     *
     * @return a double
     */
    public double getPercentValveOpening();

    /**
     * <p>setPercentValveOpening.</p>
     *
     * @param percentValveOpening a double
     */
    public void setPercentValveOpening(double percentValveOpening);

    /**
     * <p>getCv.</p>
     *
     * @return a double
     */
    public double getCv();

    /**
     * <p>setCv.</p>
     *
     * @param Cv a double
     */
    public void setCv(double Cv);

    /**
     * <p>getOutletPressure.</p>
     *
     * @return a double
     */
    public double getOutletPressure();

    /**
     * <p>getInletPressure.</p>
     *
     * @return a double
     */
    public double getInletPressure();

	/** {@inheritDoc} */
    @Override
	public SystemInterface getThermoSystem();
}
