/*
 * FluidBoundarySolverInterface.java
 *
 * Created on 8. august 2001, 14:51
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySolver;

/**
 * <p>
 * FluidBoundarySolverInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FluidBoundarySolverInterface {
    /**
     * <p>
     * solve.
     * </p>
     */
    public void solve();

    /**
     * <p>
     * getMolarFlux.
     * </p>
     *
     * @param componentNumber a int
     * @return a double
     */
    public double getMolarFlux(int componentNumber);
}
