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

    public void setLongitudionalCoordinates(double startLongitudionalCoordinate,
            double endLongitudionalCoordinate);

    public void createFlowNodes(FlowNodeInterface node);

    public void createFlowNodes();

    public int getNumberOfNodes();

    public FlowNodeInterface getNode(int i);

    public FlowNodeInterface[] getFlowNodes();

    public void setOuterHeatTransferCOefficients(double startHeatTransferCoefficient,
            double endHeatTransferCoefficient);

    public void setWallHeatTransferCOefficients(double startHeatTransferCoefficient,
            double endHeatTransferCoefficient);

    public void setFlowPattern(String flowPattern);
}
