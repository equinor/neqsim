/*
 * EnhancementFactorInterface.java
 *
 * Created on 3. august 2001, 11:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor;

/**
 * <p>
 * EnhancementFactorInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface EnhancementFactorInterface {
    /**
     * <p>
     * calcEnhancementVec.
     * </p>
     *
     * @param phase a int
     */
    public void calcEnhancementVec(int phase);

    /**
     * <p>
     * getEnhancementVec.
     * </p>
     *
     * @param index a int
     * @return a double
     */
    public double getEnhancementVec(int index);

    /**
     * <p>
     * getHattaNumber.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getHattaNumber(int i);
}
