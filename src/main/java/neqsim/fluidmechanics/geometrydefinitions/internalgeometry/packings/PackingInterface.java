/*
 * PackingInterface.java
 *
 * Created on 25. august 2001, 23:34
 */

package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings;

/**
 * PackingInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface PackingInterface {
  /**
   * Getter for property size.
   *
   * @return a double
   */
  public double getSize();

  /**
   * Getter for property surfaceAreaPrVolume.
   *
   * @return a double
   */
  public double getSurfaceAreaPrVolume();

  /**
   * Getter for property voidFractionPacking.
   *
   * @return a double
   */
  public double getVoidFractionPacking();

  /**
   * Setter for property voidFractionPacking.
   *
   * @param voidFractionPacking a double
   */
  public void setVoidFractionPacking(double voidFractionPacking);
}
