/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */
package neqsim.processSimulation.processEquipment.separator;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * SeparatorInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface SeparatorInterface {
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
     * getName.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName();

    /**
     * <p>
     * getThermoSystem.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface getThermoSystem();

    /**
     * <p>
     * setInternalDiameter.
     * </p>
     *
     * @param diam a double
     */
    public void setInternalDiameter(double diam);
}
