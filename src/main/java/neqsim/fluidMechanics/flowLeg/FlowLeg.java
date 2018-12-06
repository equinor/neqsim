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
 * FlowLeg.java
 *
 * Created on 8. desember 2000, 19:30
 */
package neqsim.fluidMechanics.flowLeg;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.FlowNodeSelector;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public abstract class FlowLeg extends java.lang.Object implements FlowLegInterface, java.io.Serializable {

    private static final long serialVersionUID = 1000;

    protected FlowNodeInterface[] flowNode;
    protected int numberOfNodes = 0;
    protected double startLongitudionalCoordinate, endLongitudionalCoordinate;
    protected double startHeightCoordinate, endHeightCoordinate;
    protected double startOuterTemperature, endOuterTemperature, startOuterHeatTransferCoefficient, endOuterHeatTransferCoefficient, startWallHeatTransferCOefficients, endWallHeatTransferCOefficients;
    protected SystemInterface thermoSystem;
    protected GeometryDefinitionInterface equipmentGeometry;
    protected double heightChangePerNode = 0, longitudionalChangePerNode = 0, temperatureChangePerNode = 0;
    protected FlowNodeSelector nodeSelector = new FlowNodeSelector();

    /** Creates new FlowLeg */
    public FlowLeg() {
        flowNode = new FlowNodeInterface[this.getNumberOfNodes()];
    }

    public void createFlowNodes() {
        temperatureChangePerNode = (endOuterTemperature-startOuterTemperature)/(1.0*getNumberOfNodes());
        longitudionalChangePerNode = (endLongitudionalCoordinate-startLongitudionalCoordinate)/(1.0*getNumberOfNodes());
        heightChangePerNode = (endHeightCoordinate-startHeightCoordinate)/(1.0*getNumberOfNodes());
        
        flowNode[0].setDistanceToCenterOfNode(this.startLongitudionalCoordinate + 0.5 * longitudionalChangePerNode);
        flowNode[0].setLengthOfNode(longitudionalChangePerNode);
        flowNode[0].setVerticalPositionOfNode(startHeightCoordinate + 0.5 * heightChangePerNode);
        flowNode[0].getGeometry().getSurroundingEnvironment().setTemperature(startOuterTemperature + 0.5 * temperatureChangePerNode);
        flowNode[0].init();

        for (int i = 0; i < getNumberOfNodes() - 1; i++) {
            flowNode[i + 1] = flowNode[i].getNextNode();
            flowNode[i + 1].setDistanceToCenterOfNode(flowNode[0].getDistanceToCenterOfNode() + (i + 1) * longitudionalChangePerNode);
            flowNode[i + 1].setVerticalPositionOfNode(flowNode[0].getVerticalPositionOfNode() + (i + 1) * heightChangePerNode);
            flowNode[i + 1].setLengthOfNode(longitudionalChangePerNode);
            flowNode[i].getGeometry().getSurroundingEnvironment().setTemperature(startOuterTemperature + (i+1) * temperatureChangePerNode);
            flowNode[i].getGeometry().getSurroundingEnvironment().setHeatTransferCoefficient(startOuterHeatTransferCoefficient + (i+1) * 1.0 / (getNumberOfNodes() * 1.0) * (endOuterHeatTransferCoefficient - startOuterHeatTransferCoefficient));
            flowNode[i].getGeometry().setWallHeatTransferCoefficient(startWallHeatTransferCOefficients + (i+1) * 1.0 / (getNumberOfNodes() * 1.0) * (endWallHeatTransferCOefficients - startWallHeatTransferCOefficients));
            flowNode[i + 1].init();
        }
    }

    public void setFlowNodeTypes() {

        nodeSelector.getFlowNodeType(flowNode);

        for (int i = 0; i < getNumberOfNodes(); i++) {
            flowNode[i].setDistanceToCenterOfNode(flowNode[0].getDistanceToCenterOfNode() + (i) * longitudionalChangePerNode);
            flowNode[i].setLengthOfNode(longitudionalChangePerNode);
            flowNode[i].setVerticalPositionOfNode(flowNode[0].getVerticalPositionOfNode() + (i) * heightChangePerNode);
            flowNode[i].getGeometry().getSurroundingEnvironment().setTemperature(startOuterTemperature + (i) * temperatureChangePerNode);
            flowNode[i].init();
        }
    }

    public void setFlowPattern(String flowPattern) {
        nodeSelector.setFlowPattern(flowNode, flowPattern);
        flowNode[0].init();
        flowNode[0].setDistanceToCenterOfNode(this.startLongitudionalCoordinate + 0.5 * longitudionalChangePerNode);
        flowNode[0].setLengthOfNode(longitudionalChangePerNode);
        flowNode[0].setVerticalPositionOfNode(startHeightCoordinate + 0.5 * heightChangePerNode);

        for (int i = 0; i < getNumberOfNodes() - 1; i++) {
            flowNode[i + 1] = flowNode[i].getNextNode();
            flowNode[i + 1].setDistanceToCenterOfNode(flowNode[0].getDistanceToCenterOfNode() + (i + 1) * longitudionalChangePerNode);
            flowNode[i + 1].setVerticalPositionOfNode(flowNode[0].getVerticalPositionOfNode() + (i + 1) * heightChangePerNode);
            flowNode[i + 1].setLengthOfNode(longitudionalChangePerNode);
            flowNode[i].getGeometry().getSurroundingEnvironment().setTemperature(startOuterTemperature + (i) * temperatureChangePerNode);

            flowNode[i + 1].init();
        }
    }

    public void setThermoSystem(SystemInterface thermoSystem) {
        this.thermoSystem = (SystemInterface) thermoSystem.clone();
    }

    public void setEquipmentGeometry(GeometryDefinitionInterface equipmentGeometry) {
        this.equipmentGeometry = (GeometryDefinitionInterface) equipmentGeometry.clone();
    }

    public void setNumberOfNodes(int numberOfNodes) {
        this.numberOfNodes = numberOfNodes;
    }

    public int getNumberOfNodes() {
        return this.numberOfNodes;
    }

    public void setHeightCoordinates(double startHeightCoordinate, double endHeightCoordinate) {
        this.startHeightCoordinate = startHeightCoordinate;
        this.endHeightCoordinate = endHeightCoordinate;
    }

    public void setOuterHeatTransferCOefficients(double startHeatTransferCoefficient, double endHeatTransferCoefficient) {
        this.startOuterHeatTransferCoefficient = startHeatTransferCoefficient;
        this.endOuterHeatTransferCoefficient = endHeatTransferCoefficient;
    }

    public void setWallHeatTransferCOefficients(double startHeatTransferCoefficient, double endHeatTransferCoefficient) {
        this.startWallHeatTransferCOefficients = startHeatTransferCoefficient;
        this.endWallHeatTransferCOefficients = endHeatTransferCoefficient;
    }
    
    

    public void setLongitudionalCoordinates(double startLongitudionalCoordinate, double endLongitudionalCoordinate) {
        this.startLongitudionalCoordinate = startLongitudionalCoordinate;
        this.endLongitudionalCoordinate = endLongitudionalCoordinate;
    }

    public void setOuterTemperatures(double startTemperature, double endTemperature) {
        this.startOuterTemperature = startTemperature;
        this.endOuterTemperature = endTemperature;
    }

    public FlowNodeInterface[] getFlowNodes() {
        return flowNode;
    }

    public FlowNodeInterface getNode(int i) {
        return flowNode[i];
    }
}
