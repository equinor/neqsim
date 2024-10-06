/*
 * OnePhaseFlowVisualization.java
 *
 * Created on 26. oktober 2000, 20:08
 */

package neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.onephaseflowvisualization;

/**
 * <p>
 * OnePhaseFlowVisualization class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class OnePhaseFlowVisualization extends
    neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.FlowSystemVisualization {
  /**
   * <p>
   * Constructor for OnePhaseFlowVisualization.
   * </p>
   */
  public OnePhaseFlowVisualization() {}

  /**
   * <p>
   * Constructor for OnePhaseFlowVisualization.
   * </p>
   *
   * @param nodes a int
   * @param timeSteps a int
   */
  public OnePhaseFlowVisualization(int nodes, int timeSteps) {
    super(nodes, timeSteps);
  }
}
