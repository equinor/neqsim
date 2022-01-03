/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */
package neqsim.processSimulation.processEquipment.heatExchanger;

/**
 * <p>HeaterInterface interface.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface HeaterInterface {

    /**
     * <p>setName.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setName(String name);

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName();

    /**
     * <p>setdT.</p>
     *
     * @param dT a double
     */
    public void setdT(double dT);

    /**
     * <p>setOutTP.</p>
     *
     * @param temperature a double
     * @param pressure a double
     */
    public void setOutTP(double temperature, double pressure);

    /**
     * <p>setOutTemperature.</p>
     *
     * @param temperature a double
     * @param unit a {@link java.lang.String} object
     */
    public void setOutTemperature(double temperature, String unit);

    /**
     * <p>setOutPressure.</p>
     *
     * @param pressure a double
     * @param unit a {@link java.lang.String} object
     */
    public void setOutPressure(double pressure, String unit);
}
