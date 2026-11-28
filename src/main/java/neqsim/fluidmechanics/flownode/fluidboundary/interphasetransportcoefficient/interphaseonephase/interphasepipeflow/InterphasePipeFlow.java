package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphaseonephase.interphasepipeflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphaseonephase.InterphaseOnePhase;

/**
 * InterphasePipeFlow class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphasePipeFlow extends InterphaseOnePhase {
  /**
   * Constructor for InterphasePipeFlow.
   */
  public InterphasePipeFlow() {
  }

  /**
   * Constructor for InterphasePipeFlow.
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
    if (Math.abs(node.getReynoldsNumber(phase)) < 2000) {
      return 64.0 / Math.abs(node.getReynoldsNumber(phase));
    } else {
      return Math.pow((1.0 / (-1.8 * Math.log10(6.9 / Math.abs(node.getReynoldsNumber(phase))
          + Math.pow(node.getGeometry().getRelativeRoughnes() / 3.7, 1.11)))), 2.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phaseNum, double prandtlNumber, FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber(phaseNum)) < 2000) {
      return 3.66 / node.getGeometry().getDiameter()
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    }
    // if turbulent - use chilton colburn analogy
    else {
      double temp = node.getBulkSystem().getPhase(phaseNum).getCp()
          / node.getBulkSystem().getPhase(phaseNum).getMolarMass()
          / node.getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          * Math.abs(node.getVelocity(phaseNum));
      return chiltonColburnStanton(this.calcWallFrictionFactor(phaseNum, node), prandtlNumber) * temp;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallMassTransferCoefficient(int phaseNum, double schmidtNumber, FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber(phaseNum)) < 2000) {
      return 3.66 / node.getGeometry().getDiameter() / schmidtNumber
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
    } else {
      double temp = Math.abs(node.getVelocity(phaseNum));
      return chiltonColburnStanton(this.calcWallFrictionFactor(phaseNum, node), schmidtNumber) * temp;
    }
  }
}
