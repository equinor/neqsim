package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.measurementDevice.online.OnlineSignal;

/**
 * <p>
 * MeasurementDeviceInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface MeasurementDeviceInterface extends java.io.Serializable {
    /**
     * <p>
     * displayResult.
     * </p>
     */
    public void displayResult();

    /**
     * <p>
     * getName.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName();

    /**
     * <p>
     * setName.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setName(String name);

    /**
     * <p>
     * getMeasuredValue.
     * </p>
     *
     * @return a double
     */
    public double getMeasuredValue();

    /**
     * <p>
     * getMeasuredValue.
     * </p>
     *
     * @param unit a {@link java.lang.String} object
     * @return a double
     */
    public double getMeasuredValue(String unit);

    /**
     * <p>
     * getOnlineSignal.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.measurementDevice.online.OnlineSignal} object
     */
    public OnlineSignal getOnlineSignal();

    /**
     * <p>
     * getMeasuredPercentValue.
     * </p>
     *
     * @return a double
     */
    public double getMeasuredPercentValue();

    /**
     * <p>
     * getUnit.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getUnit();

    /**
     * <p>
     * setUnit.
     * </p>
     *
     * @param unit a {@link java.lang.String} object
     */
    public void setUnit(String unit);

    /**
     * <p>
     * getMaximumValue.
     * </p>
     *
     * @return a double
     */
    public double getMaximumValue();

    /**
     * <p>
     * setMaximumValue.
     * </p>
     *
     * @param maximumValue a double
     */
    public void setMaximumValue(double maximumValue);

    /**
     * <p>
     * getMinimumValue.
     * </p>
     *
     * @return a double
     */
    public double getMinimumValue();

    /**
     * <p>
     * setMinimumValue.
     * </p>
     *
     * @param minimumValue a double
     */
    public void setMinimumValue(double minimumValue);

    /**
     * <p>
     * isLogging.
     * </p>
     *
     * @return a boolean
     */
    public boolean isLogging();

    /**
     * <p>
     * setLogging.
     * </p>
     *
     * @param logging a boolean
     */
    public void setLogging(boolean logging);

    /**
     * <p>
     * getOnlineValue.
     * </p>
     *
     * @return a double
     */
    public double getOnlineValue();

    /**
     * <p>
     * isOnlineSignal.
     * </p>
     *
     * @return a boolean
     */
    public boolean isOnlineSignal();
}
