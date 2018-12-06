/*
 * PackingInterface.java
 *
 * Created on 25. august 2001, 23:34
 */

package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings;

/**
 *
 * @author  esol
 * @version
 */
public interface PackingInterface {
    public double getSize();
    public double getSurfaceAreaPrVolume();
    public double getVoidFractionPacking();
    public void setVoidFractionPacking(double voidFractionPacking);
}

