package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphaseonephase.interphasepipeflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphaseonephase.InterphaseOnePhase;

/**
 * <p>
 * InterphasePipeFlow class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphasePipeFlow extends InterphaseOnePhase {
  /**
   * <p>
   * Constructor for InterphasePipeFlow.
   * </p>
   */
  public InterphasePipeFlow() {}

  /**
   * <p>
   * Constructor for InterphasePipeFlow.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphasePipeFlow(FlowNodeInterface node) {
    // flowNode = node;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallFrictionFactor(FlowNodeInterface node) {
    return calcWallFrictionFactor(0, node);
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber()) < 2000) {
      return 64.0 / node.getReynoldsNumber(phase);
    } else {
      return Math.pow((1.0 / (-1.8 * Math.log10(6.9 / node.getReynoldsNumber(phase)
          + Math.pow(node.getGeometry().getRelativeRoughnes() / 3.7, 1.11)))), 2.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phaseNum, double prandtlNumber,
      FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber()) < 2000) {
      return 3.66 / node.getGeometry().getDiameter()
          * node.getBulkSystem().getPhases()[phaseNum].getPhysicalProperties().getConductivity();
    }
    // if turbulent - use chilton colburn analogy
    else {
      double temp = node.getBulkSystem().getPhases()[phaseNum].getCp()
          / node.getBulkSystem().getPhases()[phaseNum].getMolarMass()
          / node.getBulkSystem().getPhases()[phaseNum].getNumberOfMolesInPhase()
          * node.getBulkSystem().getPhases()[phaseNum].getPhysicalProperties().getDensity()
          * node.getVelocity();
      return 0.5 * this.calcWallFrictionFactor(phaseNum, node) * Math.pow(prandtlNumber, -2.0 / 3.0)
          * temp;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallMassTransferCoefficient(int phaseNum, double schmidtNumber,
      FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber()) < 2000) {
      return 3.66 / node.getGeometry().getDiameter() / schmidtNumber
          * node.getBulkSystem().getPhases()[phaseNum].getPhysicalProperties()
              .getKinematicViscosity();
    } else {
      double temp = node.getVelocity();
      return 0.5 * this.calcWallFrictionFactor(phaseNum, node) * Math.pow(schmidtNumber, -2.0 / 3.0)
          * temp;
    }
  }
}
