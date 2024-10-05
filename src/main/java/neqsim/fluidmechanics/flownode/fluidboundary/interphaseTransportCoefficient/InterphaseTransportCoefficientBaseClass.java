package neqsim.fluidmechanics.flownode.fluidboundary.interphaseTransportCoefficient;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * <p>
 * InterphaseTransportCoefficientBaseClass class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseTransportCoefficientBaseClass
    implements InterphaseTransportCoefficientInterface {
  /**
   * <p>Constructor for InterphaseTransportCoefficientBaseClass.</p>
   */
  public InterphaseTransportCoefficientBaseClass() {}

  /**
   * <p>
   * Constructor for InterphaseTransportCoefficientBaseClass.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseTransportCoefficientBaseClass(FlowNodeInterface node) {
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
  public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node) {
    // TODO: Should calcWallFrictionFactor(phase, node be called below?)
    return (1.0 + 75.0 * node.getPhaseFraction(1)) * calcWallFrictionFactor(0, node);
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber,
      FlowNodeInterface node) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phase, FlowNodeInterface node) {
    return this.calcWallHeatTransferCoefficient(phase, node.getPrandtlNumber(phase), node);
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallMassTransferCoefficient(int phase, double schmidtNumber,
      FlowNodeInterface node) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber,
      FlowNodeInterface node) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseMassTransferCoefficient(int phase, double schmidt,
      FlowNodeInterface node) {
    return 0;
  }
}
