/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package neqsim.fluidMechanics.flowNode;

import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.AnnularFlow;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.StratifiedFlowNode;

public class FlowNodeSelector {

    private static final long serialVersionUID = 1000;

    public FlowNodeSelector() {
    }

    public void getFlowNodeType(FlowNodeInterface[] flowNode) {
        System.out.println("forskjell: " + Math.abs(
                flowNode[0].getVerticalPositionOfNode() - flowNode[flowNode.length - 1].getVerticalPositionOfNode()));

        if (Math.abs(flowNode[0].getVerticalPositionOfNode()
                - flowNode[flowNode.length - 1].getVerticalPositionOfNode()) > 1) {
            for (int i = 0; i < flowNode.length; i++) {
                flowNode[i] = new AnnularFlow(flowNode[i].getBulkSystem(), flowNode[i].getInterphaseSystem(),
                        flowNode[i].getGeometry());
            }
        } else {
            for (int i = 0; i < flowNode.length; i++) {
                flowNode[i] = new StratifiedFlowNode(flowNode[i].getBulkSystem(), flowNode[i].getInterphaseSystem(),
                        flowNode[i].getGeometry());
            }
        }
    }

    public void setFlowPattern(FlowNodeInterface[] flowNode, String flowPattern) {
        System.out.println("pattern er " + flowPattern);
        if (flowPattern.equals("annular")) {
            for (int i = 0; i < flowNode.length; i++) {
                flowNode[i] = new AnnularFlow(flowNode[i].getBulkSystem(), flowNode[i].getInterphaseSystem(),
                        flowNode[i].getGeometry());
            }
        } else if (flowPattern.equals("stratified")) {
            {
                for (int i = 0; i < flowNode.length; i++) {
                    flowNode[i] = new StratifiedFlowNode(flowNode[i].getBulkSystem(), flowNode[i].getInterphaseSystem(),
                            flowNode[i].getGeometry());
                }
            }
        }
    }

}