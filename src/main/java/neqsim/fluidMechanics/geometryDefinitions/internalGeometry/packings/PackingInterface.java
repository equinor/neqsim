/*
 * PackingInterface.java
 *
 * Created on 25. august 2001, 23:34
 */

package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings;

/**
 * <p>
 * PackingInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface PackingInterface {
    /**
     * <p>
     * getSize.
     * </p>
     *
     * @return a double
     */
    public double getSize();

    /**
     * <p>
     * getSurfaceAreaPrVolume.
     * </p>
     *
     * @return a double
     */
    public double getSurfaceAreaPrVolume();

    /**
     * <p>
     * getVoidFractionPacking.
     * </p>
     *
     * @return a double
     */
    public double getVoidFractionPacking();

    /**
     * <p>
     * setVoidFractionPacking.
     * </p>
     *
     * @param voidFractionPacking a double
     */
    public void setVoidFractionPacking(double voidFractionPacking);
}
