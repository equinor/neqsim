/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */

package neqsim.processSimulation.processEquipment.distillation;

/**
 * <p>DistillationInterface interface.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface DistillationInterface {
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
     * <p>setNumberOfTrays.</p>
     *
     * @param number a int
     */
    public void setNumberOfTrays(int number);
}
