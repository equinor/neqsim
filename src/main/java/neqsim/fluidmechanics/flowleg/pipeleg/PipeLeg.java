/*
 * PipeLeg.java
 *
 * Created on 8. desember 2000, 19:32
 */

package neqsim.fluidmechanics.flowleg.pipeleg;

import neqsim.fluidmechanics.flowleg.FlowLeg;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * <p>
 * PipeLeg class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PipeLeg extends FlowLeg {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // FlowNodeInterface[] node;

  /**
   * <p>
   * Constructor for PipeLeg.
   * </p>
   */
  public PipeLeg() {}

  /** {@inheritDoc} */
  @Override
  public void createFlowNodes(FlowNodeInterface initNode) {
    heightChangePerNode =
        (this.endHeightCoordinate - this.startHeightCoordinate) / this.getNumberOfNodes();
    longitudionalChangePerNode =
        (this.endLongitudionalCoordinate - this.startLongitudionalCoordinate)
            / (this.getNumberOfNodes() * 1.0);
    temperatureChangePerNode =
        (this.endOuterTemperature - this.startOuterTemperature) / (this.getNumberOfNodes() * 1.0);

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
