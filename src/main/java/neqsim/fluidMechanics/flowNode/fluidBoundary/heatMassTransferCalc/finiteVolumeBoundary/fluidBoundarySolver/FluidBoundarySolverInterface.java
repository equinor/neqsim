/*
 * FluidBoundarySolverInterface.java
 *
 * Created on 8. august 2001, 14:51
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySolver;

/**
 *
 * @author  esol
 * @version
 */
public interface FluidBoundarySolverInterface {
    public void solve();
    public double getMolarFlux(int componentNumber);
}

