/*
 * FlowLegInterface.java
 *
 * Created on 11. desember 2000, 17:47
 */

package neqsim.fluidmechanics.flowleg;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * FlowLegInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FlowLegInterface {
  /**
   * setThermoSystem.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermoSystem(SystemInterface thermoSystem);

  /**
   * setEquipmentGeometry.
   *
   * @param equipmentGeometry a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface} object
   */
  public void setEquipmentGeometry(GeometryDefinitionInterface equipmentGeometry);

  /**
   * setNumberOfNodes.
   *
   * @param numberOfNodes a int
   */
  public void setNumberOfNodes(int numberOfNodes);

  /**
   * setHeightCoordinates.
   *
   * @param startHeightCoordinate a double
   * @param endHeightCoordinate a double
   */
  public void setHeightCoordinates(double startHeightCoordinate, double endHeightCoordinate);

  /**
   * setOuterTemperatures.
   *
   * @param temp1 a double
   * @param temp2 a double
   */
  public void setOuterTemperatures(double temp1, double temp2);

  /**
   * setLongitudionalCoordinates.
   *
   * @param startLongitudionalCoordinate a double
   * @param endLongitudionalCoordinate a double
   */
  public void setLongitudionalCoordinates(double startLongitudionalCoordinate, double endLongitudionalCoordinate);

  /**
   * createFlowNodes.
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public void createFlowNodes(FlowNodeInterface node);

  /**
   * createFlowNodes.
   */
  public void createFlowNodes();

  /**
   * getNumberOfNodes.
   *
   * @return a int
   */
  public int getNumberOfNodes();

  /**
   * getNode.
   *
   * @param i a int
   * @return a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public FlowNodeInterface getNode(int i);

  /**
   * getFlowNodes.
   *
   * @return an array of {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} objects
   */
  public FlowNodeInterface[] getFlowNodes();

  /**
   * setOuterHeatTransferCoefficients.
   *
   * @param startHeatTransferCoefficient a double
   * @param endHeatTransferCoefficient a double
   */
  public void setOuterHeatTransferCoefficients(double startHeatTransferCoefficient, double endHeatTransferCoefficient);

  /**
   * setWallHeatTransferCoefficients.
   *
   * @param startHeatTransferCoefficient a double
   * @param endHeatTransferCoefficient a double
   */
  public void setWallHeatTransferCoefficients(double startHeatTransferCoefficient, double endHeatTransferCoefficient);

  /**
   * setFlowPattern.
   *
   * @param flowPattern a {@link java.lang.String} object
   */
  public void setFlowPattern(String flowPattern);
}
