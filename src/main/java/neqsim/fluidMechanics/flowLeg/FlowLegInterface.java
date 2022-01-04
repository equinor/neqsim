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
 * <p>
 * FlowLegInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FlowLegInterface {
    /**
     * <p>
     * setThermoSystem.
     * </p>
     *
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public void setThermoSystem(SystemInterface thermoSystem);

    /**
     * <p>
     * setEquipmentGeometry.
     * </p>
     *
     * @param equipmentGeometry a
     *        {@link neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface} object
     */
    public void setEquipmentGeometry(GeometryDefinitionInterface equipmentGeometry);

    /**
     * <p>
     * setNumberOfNodes.
     * </p>
     *
     * @param numberOfNodes a int
     */
    public void setNumberOfNodes(int numberOfNodes);

    /**
     * <p>
     * setHeightCoordinates.
     * </p>
     *
     * @param startHeightCoordinate a double
     * @param endHeightCoordinate a double
     */
    public void setHeightCoordinates(double startHeightCoordinate, double endHeightCoordinate);

    /**
     * <p>
     * setOuterTemperatures.
     * </p>
     *
     * @param temp1 a double
     * @param temp2 a double
     */
    public void setOuterTemperatures(double temp1, double temp2);

    /**
     * <p>
     * setLongitudionalCoordinates.
     * </p>
     *
     * @param startLongitudionalCoordinate a double
     * @param endLongitudionalCoordinate a double
     */
    public void setLongitudionalCoordinates(double startLongitudionalCoordinate,
            double endLongitudionalCoordinate);

    /**
     * <p>
     * createFlowNodes.
     * </p>
     *
     * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public void createFlowNodes(FlowNodeInterface node);

    /**
     * <p>
     * createFlowNodes.
     * </p>
     */
    public void createFlowNodes();

    /**
     * <p>
     * getNumberOfNodes.
     * </p>
     *
     * @return a int
     */
    public int getNumberOfNodes();

    /**
     * <p>
     * getNode.
     * </p>
     *
     * @param i a int
     * @return a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public FlowNodeInterface getNode(int i);

    /**
     * <p>
     * getFlowNodes.
     * </p>
     *
     * @return an array of {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} objects
     */
    public FlowNodeInterface[] getFlowNodes();

    /**
     * <p>
     * setOuterHeatTransferCOefficients.
     * </p>
     *
     * @param startHeatTransferCoefficient a double
     * @param endHeatTransferCoefficient a double
     */
    public void setOuterHeatTransferCOefficients(double startHeatTransferCoefficient,
            double endHeatTransferCoefficient);

    /**
     * <p>
     * setWallHeatTransferCOefficients.
     * </p>
     *
     * @param startHeatTransferCoefficient a double
     * @param endHeatTransferCoefficient a double
     */
    public void setWallHeatTransferCOefficients(double startHeatTransferCoefficient,
            double endHeatTransferCoefficient);

    /**
     * <p>
     * setFlowPattern.
     * </p>
     *
     * @param flowPattern a {@link java.lang.String} object
     */
    public void setFlowPattern(String flowPattern);
}
