/*
 * FlowLeg.java
 *
 * Created on 8. desember 2000, 19:30
 */

package neqsim.fluidmechanics.flowleg;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.FlowNodeSelector;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract FlowLeg class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class FlowLeg implements FlowLegInterface, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected FlowNodeInterface[] flowNode;
  protected int numberOfNodes = 0;
  protected double startLongitudionalCoordinate, endLongitudionalCoordinate;
  protected double startHeightCoordinate, endHeightCoordinate;
  protected double startOuterTemperature, endOuterTemperature, startOuterHeatTransferCoefficient,
      endOuterHeatTransferCoefficient, startWallHeatTransferCOefficients,
      endWallHeatTransferCOefficients;
  protected SystemInterface thermoSystem;
  protected GeometryDefinitionInterface equipmentGeometry;
  protected double heightChangePerNode = 0, longitudionalChangePerNode = 0,
      temperatureChangePerNode = 0;
  protected FlowNodeSelector nodeSelector = new FlowNodeSelector();

  /**
   * <p>
   * Constructor for FlowLeg.
   * </p>
   */
  public FlowLeg() {
    flowNode = new FlowNodeInterface[this.getNumberOfNodes()];
  }

  /** {@inheritDoc} */
  @Override
  public void createFlowNodes() {
    temperatureChangePerNode =
        (endOuterTemperature - startOuterTemperature) / (1.0 * getNumberOfNodes());
    longitudionalChangePerNode =
        (endLongitudionalCoordinate - startLongitudionalCoordinate) / (1.0 * getNumberOfNodes());
    heightChangePerNode =
        (endHeightCoordinate - startHeightCoordinate) / (1.0 * getNumberOfNodes());

    flowNode[0].setDistanceToCenterOfNode(
        this.startLongitudionalCoordinate + 0.5 * longitudionalChangePerNode);
    flowNode[0].setLengthOfNode(longitudionalChangePerNode);
    flowNode[0].setVerticalPositionOfNode(startHeightCoordinate + 0.5 * heightChangePerNode);
    flowNode[0].getGeometry().getSurroundingEnvironment()
        .setTemperature(startOuterTemperature + 0.5 * temperatureChangePerNode);
    flowNode[0].init();

    for (int i = 0; i < getNumberOfNodes() - 1; i++) {
      flowNode[i + 1] = flowNode[i].getNextNode();
      flowNode[i + 1].setDistanceToCenterOfNode(
          flowNode[0].getDistanceToCenterOfNode() + (i + 1) * longitudionalChangePerNode);
      flowNode[i + 1].setVerticalPositionOfNode(
          flowNode[0].getVerticalPositionOfNode() + (i + 1) * heightChangePerNode);
      flowNode[i + 1].setLengthOfNode(longitudionalChangePerNode);
      flowNode[i].getGeometry().getSurroundingEnvironment()
          .setTemperature(startOuterTemperature + (i + 1) * temperatureChangePerNode);
      flowNode[i].getGeometry().getSurroundingEnvironment().setHeatTransferCoefficient(
          startOuterHeatTransferCoefficient + (i + 1) * 1.0 / (getNumberOfNodes() * 1.0)
              * (endOuterHeatTransferCoefficient - startOuterHeatTransferCoefficient));
      flowNode[i].getGeometry().setWallHeatTransferCoefficient(
          startWallHeatTransferCOefficients + (i + 1) * 1.0 / (getNumberOfNodes() * 1.0)
              * (endWallHeatTransferCOefficients - startWallHeatTransferCOefficients));
      flowNode[i + 1].init();
    }
  }

  /**
   * <p>
   * setFlowNodeTypes.
   * </p>
   */
  public void setFlowNodeTypes() {
    nodeSelector.getFlowNodeType(flowNode);

    for (int i = 0; i < getNumberOfNodes(); i++) {
      flowNode[i].setDistanceToCenterOfNode(
          flowNode[0].getDistanceToCenterOfNode() + (i) * longitudionalChangePerNode);
      flowNode[i].setLengthOfNode(longitudionalChangePerNode);
      flowNode[i].setVerticalPositionOfNode(
          flowNode[0].getVerticalPositionOfNode() + (i) * heightChangePerNode);
      flowNode[i].getGeometry().getSurroundingEnvironment()
          .setTemperature(startOuterTemperature + (i) * temperatureChangePerNode);
      flowNode[i].init();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setFlowPattern(String flowPattern) {
    nodeSelector.setFlowPattern(flowNode, flowPattern);
    flowNode[0].init();
    flowNode[0].setDistanceToCenterOfNode(
        this.startLongitudionalCoordinate + 0.5 * longitudionalChangePerNode);
    flowNode[0].setLengthOfNode(longitudionalChangePerNode);
    flowNode[0].setVerticalPositionOfNode(startHeightCoordinate + 0.5 * heightChangePerNode);

    for (int i = 0; i < getNumberOfNodes() - 1; i++) {
      flowNode[i + 1] = flowNode[i].getNextNode();
      flowNode[i + 1].setDistanceToCenterOfNode(
          flowNode[0].getDistanceToCenterOfNode() + (i + 1) * longitudionalChangePerNode);
      flowNode[i + 1].setVerticalPositionOfNode(
          flowNode[0].getVerticalPositionOfNode() + (i + 1) * heightChangePerNode);
      flowNode[i + 1].setLengthOfNode(longitudionalChangePerNode);
      flowNode[i].getGeometry().getSurroundingEnvironment()
          .setTemperature(startOuterTemperature + (i) * temperatureChangePerNode);

      flowNode[i + 1].init();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setThermoSystem(SystemInterface thermoSystem) {
    this.thermoSystem = thermoSystem.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void setEquipmentGeometry(GeometryDefinitionInterface equipmentGeometry) {
    this.equipmentGeometry = equipmentGeometry.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfNodes(int numberOfNodes) {
    this.numberOfNodes = numberOfNodes;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfNodes() {
    return this.numberOfNodes;
  }

  /** {@inheritDoc} */
  @Override
  public void setHeightCoordinates(double startHeightCoordinate, double endHeightCoordinate) {
    this.startHeightCoordinate = startHeightCoordinate;
    this.endHeightCoordinate = endHeightCoordinate;
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterHeatTransferCoefficients(double startHeatTransferCoefficient,
      double endHeatTransferCoefficient) {
    this.startOuterHeatTransferCoefficient = startHeatTransferCoefficient;
    this.endOuterHeatTransferCoefficient = endHeatTransferCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public void setWallHeatTransferCoefficients(double startHeatTransferCoefficient,
      double endHeatTransferCoefficient) {
    this.startWallHeatTransferCOefficients = startHeatTransferCoefficient;
    this.endWallHeatTransferCOefficients = endHeatTransferCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public void setLongitudionalCoordinates(double startLongitudionalCoordinate,
      double endLongitudionalCoordinate) {
    this.startLongitudionalCoordinate = startLongitudionalCoordinate;
    this.endLongitudionalCoordinate = endLongitudionalCoordinate;
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterTemperatures(double startTemperature, double endTemperature) {
    this.startOuterTemperature = startTemperature;
    this.endOuterTemperature = endTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface[] getFlowNodes() {
    return flowNode;
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNode(int i) {
    return flowNode[i];
  }
}
