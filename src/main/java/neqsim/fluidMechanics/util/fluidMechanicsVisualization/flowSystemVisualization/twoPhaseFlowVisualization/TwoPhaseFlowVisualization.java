/*
 * OnePhaseFlowVisualization.java
 *
 * Created on 26. oktober 2000, 20:08
 */

package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.twoPhaseFlowVisualization;

/**
 * <p>TwoPhaseFlowVisualization class.</p>
 *
 * @author esol
 */
public class TwoPhaseFlowVisualization
        extends neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.FlowSystemVisualization {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new OnePhaseFlowVisualization
     */
    public TwoPhaseFlowVisualization() {
    }

    /**
     * <p>Constructor for TwoPhaseFlowVisualization.</p>
     *
     * @param nodes a int
     * @param timeSteps a int
     */
    public TwoPhaseFlowVisualization(int nodes, int timeSteps) {
        super(nodes, timeSteps);
    }

}
