/*
 * EnhancementFactorInterface.java
 *
 * Created on 3. august 2001, 11:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor;

/**
 *
 * @author  esol
 * @version
 */
public interface EnhancementFactorInterface{
    public void calcEnhancementVec(int phase);
    public double getEnhancementVec(int index);
    public double getHattaNumber(int i);
}

