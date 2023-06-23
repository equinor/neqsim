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
   * Getter for property size.
   * </p>
   *
   * @return a double
   */
  public double getSize();

  /**
   * <p>
   * Getter for property surfaceAreaPrVolume.
   * </p>
   *
   * @return a double
   */
  public double getSurfaceAreaPrVolume();

  /**
   * <p>
   * Getter for property voidFractionPacking.
   * </p>
   *
   * @return a double
   */
  public double getVoidFractionPacking();

  /**
   * <p>
   * Setter for property voidFractionPacking.
   * </p>
   *
   * @param voidFractionPacking a double
   */
  public void setVoidFractionPacking(double voidFractionPacking);
}
