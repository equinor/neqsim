/*
 * EnhancementFactorInterface.java
 *
 * Created on 3. august 2001, 11:58
 */

package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor;

/**
 * EnhancementFactorInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface EnhancementFactorInterface {
  /**
   * calcEnhancementVec.
   *
   * @param phase a int
   */
  public void calcEnhancementVec(int phase);

  /**
   * getEnhancementVec.
   *
   * @param index a int
   * @return a double
   */
  public double getEnhancementVec(int index);

  /**
   * getHattaNumber.
   *
   * @param i a int
   * @return a double
   */
  public double getHattaNumber(int i);
}
