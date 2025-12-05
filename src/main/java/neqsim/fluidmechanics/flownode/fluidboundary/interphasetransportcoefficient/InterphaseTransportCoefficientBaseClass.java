package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient;

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
   * <p>
   * Constructor for InterphaseTransportCoefficientBaseClass.
   * </p>
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

  /**
   * Calculates the Darcy friction factor using Haaland equation for turbulent flow and f = 64/Re
   * for laminar flow. Includes transition zone interpolation.
   *
   * @param phase phase index
   * @param node flow node interface
   * @return Darcy friction factor (dimensionless)
   */
  @Override
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
    double reynolds = node.getReynoldsNumber(phase);
    if (Math.abs(reynolds) < 1e-10) {
      return 0.0;
    }
    double relativeRoughness = node.getGeometry().getRelativeRoughnes();
    if (Math.abs(reynolds) < 2300) {
      // Laminar flow
      return 64.0 / reynolds;
    } else if (Math.abs(reynolds) < 4000) {
      // Transition zone - interpolate between laminar and turbulent
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent = Math.pow(
          (1.0 / (-1.8 * Math.log10(6.9 / 4000.0 + Math.pow(relativeRoughness / 3.7, 1.11)))), 2.0);
      return fLaminar + (fTurbulent - fLaminar) * (reynolds - 2300.0) / 1700.0;
    } else {
      // Turbulent flow - Haaland equation
      return Math.pow(
          (1.0 / (-1.8 * Math.log10(6.9 / reynolds + Math.pow(relativeRoughness / 3.7, 1.11)))),
          2.0);
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
