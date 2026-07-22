/*
 * OnePhaseFlowVisualization.java
 *
 * Created on 26. oktober 2000, 20:08
 */

package neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.onephaseflowvisualization;

/**
 * OnePhaseFlowVisualization class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class OnePhaseFlowVisualization
    extends neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.FlowSystemVisualization {
  /**
   * Constructor for OnePhaseFlowVisualization.
   */
  public OnePhaseFlowVisualization() {
  }

  /**
   * Constructor for OnePhaseFlowVisualization.
   *
   * @param nodes a int
   * @param timeSteps a int
   */
  public OnePhaseFlowVisualization(int nodes, int timeSteps) {
    super(nodes, timeSteps);
  }
}
