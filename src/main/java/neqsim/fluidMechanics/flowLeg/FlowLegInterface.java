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
 * FlowLegInterface.java
 *
 * Created on 11. desember 2000, 17:47
 */

package neqsim.fluidMechanics.flowLeg;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface FlowLegInterface {

    public void setThermoSystem(SystemInterface thermoSystem);

    public void setEquipmentGeometry(GeometryDefinitionInterface equipmentGeometry);

    public void setNumberOfNodes(int numberOfNodes);

    public void setHeightCoordinates(double startHeightCoordinate, double endHeightCoordinate);

    public void setOuterTemperatures(double temp1, double temp2);

    public void setLongitudionalCoordinates(double startLongitudionalCoordinate, double endLongitudionalCoordinate);

    public void createFlowNodes(FlowNodeInterface node);

    public void createFlowNodes();

    public int getNumberOfNodes();

    public FlowNodeInterface getNode(int i);

    public FlowNodeInterface[] getFlowNodes();

    public void setOuterHeatTransferCOefficients(double startHeatTransferCoefficient,
            double endHeatTransferCoefficient);

    public void setWallHeatTransferCOefficients(double startHeatTransferCoefficient, double endHeatTransferCoefficient);

    public void setFlowPattern(String flowPattern);

}
