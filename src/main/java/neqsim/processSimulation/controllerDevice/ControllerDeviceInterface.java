/*
 * OperationInterafce.java
 *
 * Created on 2. oktober 2000, 22:14
 */

package neqsim.processSimulation.controllerDevice;

import neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface;

/**
 * <p>ControllerDeviceInterface interface.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ControllerDeviceInterface extends java.io.Serializable {
    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName();

    /**
     * <p>setName.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setName(String name);

    /**
     * <p>getMeasuredValue.</p>
     *
     * @return a double
     */
    public double getMeasuredValue();

    /**
     * <p>setControllerSetPoint.</p>
     *
     * @param signal a double
     */
    public void setControllerSetPoint(double signal);

    /**
     * <p>getUnit.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getUnit();

    /**
     * <p>setUnit.</p>
     *
     * @param unit a {@link java.lang.String} object
     */
    public void setUnit(String unit);

    /**
     * <p>setTransmitter.</p>
     *
     * @param device a {@link neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface} object
     */
    public void setTransmitter(MeasurementDeviceInterface device);

    /**
     * <p>run.</p>
     *
     * @param signal a double
     * @param dt a double
     */
    public void run(double signal, double dt);

    /**
     * <p>getResponse.</p>
     *
     * @return a double
     */
    public double getResponse();

    /**
     * <p>isReverseActing.</p>
     *
     * @return a boolean
     */
    public boolean isReverseActing();

    /**
     * <p>setReverseActing.</p>
     *
     * @param reverseActing a boolean
     */
    public void setReverseActing(boolean reverseActing);

    /**
     * <p>setControllerParameters.</p>
     *
     * @param Ksp a double
     * @param Ti a double
     * @param Td a double
     */
    public void setControllerParameters(double Ksp, double Ti, double Td);
}
