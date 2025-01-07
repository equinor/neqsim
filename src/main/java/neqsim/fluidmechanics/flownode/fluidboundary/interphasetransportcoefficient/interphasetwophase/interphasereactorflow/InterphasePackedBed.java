package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasereactorflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * <p>
 * InterphasePackedBed class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphasePackedBed extends InterphaseReactorFlow
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for InterphasePackedBed.
   * </p>
   */
  public InterphasePackedBed() {}

  /**
   * <p>
   * Constructor for InterphasePackedBed.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphasePackedBed(FlowNodeInterface node) {
    // flowNode = node;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
    System.out.println("no def");
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node) {
    System.out.println("no def");
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber,
      FlowNodeInterface node) {
    System.out.println("no def");
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber,
      FlowNodeInterface node) {
    return 100.1;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallMassTransferCoefficient(int phase, double schmidtNumber,
      FlowNodeInterface node) {
    System.out.println("no def");
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseMassTransferCoefficient(int phaseNum, double schmidtNumber,
      FlowNodeInterface node) {
    double redMassTrans = 0;
    double massTrans = 0;
    if (phaseNum == 1) {
      // massTrans = 0.0002;
      redMassTrans =
          0.0051 * Math.pow(node.getReynoldsNumber(phaseNum), 0.67) * Math.pow(schmidtNumber, -0.5)
              * Math.pow(node.getGeometry().getPacking().getSurfaceAreaPrVolume()
                  * node.getGeometry().getPacking().getSize(), 0.4);
      massTrans = redMassTrans * Math.pow(
          node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
              * gravity,
          1.0 / 3.0);
      System.out.println("mas trans liq " + massTrans);
    }
    if (phaseNum == 0) {
      redMassTrans =
          3.6 * Math.pow(node.getReynoldsNumber(phaseNum), 0.7) * Math.pow(schmidtNumber, 0.33)
              * Math.pow(node.getGeometry().getPacking().getSurfaceAreaPrVolume()
                  * node.getGeometry().getPacking().getSize(), -2.0);
      massTrans = redMassTrans * node.getGeometry().getPacking().getSurfaceAreaPrVolume()
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
          / schmidtNumber;
      System.out.println("mas trans gas " + massTrans);
    }
    return massTrans;
  }
}
