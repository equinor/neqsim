package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.twoPhaseFlowVisualization;

/**
 * <p>
 * TwoPhaseFlowVisualization class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TwoPhaseFlowVisualization extends
        neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.FlowSystemVisualization {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for TwoPhaseFlowVisualization.
     * </p>
     */
    public TwoPhaseFlowVisualization() {}

    /**
     * <p>
     * Constructor for TwoPhaseFlowVisualization.
     * </p>
     *
     * @param nodes a int
     * @param timeSteps a int
     */
    public TwoPhaseFlowVisualization(int nodes, int timeSteps) {
        super(nodes, timeSteps);
    }
}
