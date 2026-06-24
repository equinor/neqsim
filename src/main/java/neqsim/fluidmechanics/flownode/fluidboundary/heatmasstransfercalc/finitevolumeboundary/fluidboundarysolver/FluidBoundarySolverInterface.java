/*
 * FluidBoundarySolverInterface.java
 *
 * Created on 8. august 2001, 14:51
 */

package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysolver;

/**
 * FluidBoundarySolverInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FluidBoundarySolverInterface {
  /**
   * solve.
   */
  public void solve();

  /**
   * getMolarFlux.
   *
   * @param componentNumber a int
   * @return a double
   */
  public double getMolarFlux(int componentNumber);
}
