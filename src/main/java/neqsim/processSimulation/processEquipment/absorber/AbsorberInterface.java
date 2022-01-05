package neqsim.processSimulation.processEquipment.absorber;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;

/**
 * <p>
 * AbsorberInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface AbsorberInterface extends ProcessEquipmentInterface {
    /** {@inheritDoc} */
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
     * setAproachToEquilibrium.
     * </p>
     *
     * @param eff a double
     */
    public void setAproachToEquilibrium(double eff);
}
