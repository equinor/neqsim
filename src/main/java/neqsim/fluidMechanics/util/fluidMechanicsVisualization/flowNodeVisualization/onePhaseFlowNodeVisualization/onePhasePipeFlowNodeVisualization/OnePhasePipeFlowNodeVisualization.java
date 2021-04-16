/*
 * OnePhasePipeFlowNodeVisualization.java
 *
 * Created on 5. august 2001, 16:29
 */

package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.onePhaseFlowNodeVisualization.onePhasePipeFlowNodeVisualization;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.onePhaseFlowNodeVisualization.OnePhaseFlowNodeVisualization;

/**
 *
 * @author esol
 * @version
 */
public class OnePhasePipeFlowNodeVisualization extends OnePhaseFlowNodeVisualization {

    private static final long serialVersionUID = 1000;

    /** Creates new OnePhasePipeFlowNodeVisualization */
    public OnePhasePipeFlowNodeVisualization() {
    }

    public void setData(FlowNodeInterface node) {
        super.setData(node);
        bulkComposition = new double[2][node.getBulkSystem().getPhases()[0].getNumberOfComponents()];

        for (int i = 0; i < node.getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
            bulkComposition[0][i] = node.getBulkSystem().getPhases()[0].getComponents()[i].getx();
        }
    }
}
