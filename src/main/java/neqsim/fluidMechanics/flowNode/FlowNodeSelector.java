package neqsim.fluidMechanics.flowNode;

import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.AnnularFlow;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.StratifiedFlowNode;

/**
 * <p>
 * FlowNodeSelector class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class FlowNodeSelector {
    /**
     * <p>
     * Constructor for FlowNodeSelector.
     * </p>
     */
    public FlowNodeSelector() {}

    /**
     * <p>
     * getFlowNodeType.
     * </p>
     *
     * @param flowNode an array of {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} objects
     */
    public void getFlowNodeType(FlowNodeInterface[] flowNode) {
        System.out.println("forskjell: " + Math.abs(flowNode[0].getVerticalPositionOfNode()
                - flowNode[flowNode.length - 1].getVerticalPositionOfNode()));

        if (Math.abs(flowNode[0].getVerticalPositionOfNode()
                - flowNode[flowNode.length - 1].getVerticalPositionOfNode()) > 1) {
            for (int i = 0; i < flowNode.length; i++) {
                flowNode[i] = new AnnularFlow(flowNode[i].getBulkSystem(),
                        flowNode[i].getInterphaseSystem(), flowNode[i].getGeometry());
            }
        } else {
            for (int i = 0; i < flowNode.length; i++) {
                flowNode[i] = new StratifiedFlowNode(flowNode[i].getBulkSystem(),
                        flowNode[i].getInterphaseSystem(), flowNode[i].getGeometry());
            }
        }
    }

    /**
     * <p>
     * setFlowPattern.
     * </p>
     *
     * @param flowNode an array of {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} objects
     * @param flowPattern a {@link java.lang.String} object
     */
    public void setFlowPattern(FlowNodeInterface[] flowNode, String flowPattern) {
        System.out.println("pattern er " + flowPattern);
        if (flowPattern.equals("annular")) {
            for (int i = 0; i < flowNode.length; i++) {
                flowNode[i] = new AnnularFlow(flowNode[i].getBulkSystem(),
                        flowNode[i].getInterphaseSystem(), flowNode[i].getGeometry());
            }
        } else if (flowPattern.equals("stratified")) {
            {
                for (int i = 0; i < flowNode.length; i++) {
                    flowNode[i] = new StratifiedFlowNode(flowNode[i].getBulkSystem(),
                            flowNode[i].getInterphaseSystem(), flowNode[i].getGeometry());
                }
            }
        }
    }
}
