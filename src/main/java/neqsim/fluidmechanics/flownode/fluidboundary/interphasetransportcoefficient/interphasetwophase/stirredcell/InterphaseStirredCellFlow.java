package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.stirredcell;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseStratifiedFlow;

/**
 * <p>
 * InterphaseStirredCellFlow class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseStirredCellFlow extends InterphaseStratifiedFlow {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for InterphaseStirredCellFlow.
   * </p>
   */
  public InterphaseStirredCellFlow() {}

  /**
   * <p>
   * Constructor for InterphaseStirredCellFlow.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseStirredCellFlow(FlowNodeInterface node) {
    // flowNode = node;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phaseNum, double prandtlNumber,
      FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber(phaseNum)) < 2000) {
      return 3.66 / node.getHydraulicDiameter(phaseNum)
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    } else {
      // if turbulent - use chilton colburn analogy
      double temp = node.getBulkSystem().getPhase(phaseNum).getCp()
          / node.getBulkSystem().getPhase(phaseNum).getMolarMass()
          / node.getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          * node.getVelocity(phaseNum);
      return 0.5 * this.calcWallFrictionFactor(phaseNum, node) * Math.pow(prandtlNumber, -2.0 / 3.0)
          * temp;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseHeatTransferCoefficient(int phaseNum, double prandtlNumber,
      FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber()) < 2000) {
      return 3.66 / node.getHydraulicDiameter(phaseNum)
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    }
    // if turbulent - use chilton colburn analogy
    else {
      double temp = node.getBulkSystem().getPhase(phaseNum).getCp()
          / node.getBulkSystem().getPhase(phaseNum).getMolarMass()
          / node.getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          * node.getVelocity(phaseNum);
      return 0.5 * this.calcWallFrictionFactor(phaseNum, node) * Math.pow(prandtlNumber, -2.0 / 3.0)
          * temp;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallMassTransferCoefficient(int phaseNum, double schmidtNumber,
      FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber()) < 2000) {
      return 3.66 / node.getHydraulicDiameter(phaseNum) / schmidtNumber
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
    } else {
      double temp = node.getVelocity(phaseNum);
      return 0.5 * this.calcWallFrictionFactor(phaseNum, node) * Math.pow(schmidtNumber, -2.0 / 3.0)
          * temp;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseMassTransferCoefficient(int phaseNum, double schmidtNumber,
      FlowNodeInterface node) {
    double redMassTrans = 0.0, massTrans = 0.0;
    if (phaseNum == 0) {
      double c2 = 0.46, c3 = 0.68, c4 = 0.5;
      redMassTrans =
          c2 * Math.pow(node.getReynoldsNumber(phaseNum), c3) * Math.pow(schmidtNumber, c4);
      // System.out.println("red gas " +
      // redMassTrans/Math.pow(node.getReynoldsNumber(phase),c3));
      // System.out.println("sc gas " + schmidtNumber);
      massTrans = redMassTrans
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
          / schmidtNumber / node.getGeometry().getDiameter();
    }
    if (phaseNum == 1) {
      double c2 = 0.181, c3 = 0.72, c4 = 0.33;
      redMassTrans =
          c2 * Math.pow(node.getReynoldsNumber(phaseNum), c3) * Math.pow(schmidtNumber, c4);
      // System.out.println("red liq" +
      // redMassTrans/Math.pow(node.getReynoldsNumber(phase),c3));
      // System.out.println("sc liq " + schmidtNumber);
      massTrans = redMassTrans
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
          / schmidtNumber / node.getGeometry().getDiameter();
    }
    return massTrans;
  }
}
