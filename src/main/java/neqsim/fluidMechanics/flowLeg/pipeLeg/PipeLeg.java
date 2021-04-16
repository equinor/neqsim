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

/*
 * PipeLeg.java
 *
 * Created on 8. desember 2000, 19:32
 */

package neqsim.fluidMechanics.flowLeg.pipeLeg;

import neqsim.fluidMechanics.flowLeg.FlowLeg;
import neqsim.fluidMechanics.flowNode.FlowNodeInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PipeLeg extends FlowLeg {

    private static final long serialVersionUID = 1000;

    // FlowNodeInterface[] node;

    /** Creates new PipeLeg */
    public PipeLeg() {
        super();
    }

    public void createFlowNodes(FlowNodeInterface initNode) {
        heightChangePerNode = (this.endHeightCoordinate - this.startHeightCoordinate) / this.getNumberOfNodes();
        longitudionalChangePerNode = (this.endLongitudionalCoordinate - this.startLongitudionalCoordinate)
                / (this.getNumberOfNodes() * 1.0);
        temperatureChangePerNode = (this.endOuterTemperature - this.startOuterTemperature)
                / (this.getNumberOfNodes() * 1.0);

        flowNode = new FlowNodeInterface[this.getNumberOfNodes()];
        this.flowNode[0] = initNode.getNextNode();
        this.equipmentGeometry.setNodeLength(longitudionalChangePerNode);
        this.equipmentGeometry.init();
        flowNode[0].setGeometryDefinitionInterface(this.equipmentGeometry);
        // flowNode = new onePhasePipeFlowNode[this.getNumberOfNodes()];
        // flowNode[0] = new onePhasePipeFlowNode(thermoSystem, this.equipmentGeometry,
        // inletTotalNormalVolumetricFlowRate);
        // flowNode[0] = new AnnularFlow(thermoSystem, this.equipmentGeometry,
        // inletTotalNormalVolumetricFlowRate);
        super.createFlowNodes();
    }

}
