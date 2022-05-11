/*
 * OnePhasePipeFlowNodeVisualization.java
 *
 * Created on 5. august 2001, 16:29
 */
package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.onePhaseFlowNodeVisualization.onePhasePipeFlowNodeVisualization;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.onePhaseFlowNodeVisualization.OnePhaseFlowNodeVisualization;

/**
 * <p>
 * OnePhasePipeFlowNodeVisualization class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class OnePhasePipeFlowNodeVisualization extends OnePhaseFlowNodeVisualization {
    /**
     * <p>
     * Constructor for OnePhasePipeFlowNodeVisualization.
     * </p>
     */
    public OnePhasePipeFlowNodeVisualization() {}

    /** {@inheritDoc} */
    @Override
    public void setData(FlowNodeInterface node) {
        super.setData(node);
        bulkComposition =
                new double[2][node.getBulkSystem().getPhases()[0].getNumberOfComponents()];

        for (int i = 0; i < node.getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
            bulkComposition[0][i] = node.getBulkSystem().getPhases()[0].getComponents()[i].getx();
        }
    }
}
