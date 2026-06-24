package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysystem;

import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode.FluidBoundaryNodeInterface;

/**
 * FluidBoundarySystemInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FluidBoundarySystemInterface {
  /**
   * addBoundary.
   *
   * @param boundary a {@link neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface}
   * object
   */
  public void addBoundary(FluidBoundaryInterface boundary);

  /**
   * setNumberOfNodes.
   *
   * @param nodes a int
   */
  public void setNumberOfNodes(int nodes);

  /**
   * getNumberOfNodes.
   *
   * @return a int
   */
  public int getNumberOfNodes();

  /**
   * createSystem.
   */
  public void createSystem();

  /**
   * setFilmThickness.
   *
   * @param filmThickness a double
   */
  public void setFilmThickness(double filmThickness);

  /**
   * getFilmThickness.
   *
   * @return a double
   */
  public double getFilmThickness();

  /**
   * getNode.
   *
   * @param i a int
   * @return a
   * {@link neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode.FluidBoundaryNodeInterface}
   * object
   */
  public FluidBoundaryNodeInterface getNode(int i);

  /**
   * getFluidBoundary.
   *
   * @return a {@link neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface} object
   */
  public FluidBoundaryInterface getFluidBoundary();

  /**
   * getNodeLength.
   *
   * @return a double
   */
  public double getNodeLength();

  /**
   * solve.
   */
  public void solve();
}
