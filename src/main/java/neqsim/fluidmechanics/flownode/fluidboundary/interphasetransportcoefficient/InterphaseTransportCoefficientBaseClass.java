package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * InterphaseTransportCoefficientBaseClass class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseTransportCoefficientBaseClass
    implements InterphaseTransportCoefficientInterface, java.io.Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Constructor for InterphaseTransportCoefficientBaseClass.
   */
  public InterphaseTransportCoefficientBaseClass() {
  }

  /**
   * Constructor for InterphaseTransportCoefficientBaseClass.
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseTransportCoefficientBaseClass(FlowNodeInterface node) {
    // flowNode = node;
  }

  /**
   * Chilton-Colburn Stanton number for a Darcy friction factor.
   *
   * <p>
   * The analogy reads {@code St * X^(2/3) = f_Fanning / 2 = f_Darcy / 8}, with X the Prandtl number for heat and the
   * Schmidt number for mass transfer. The wall friction factors of the transport-coefficient classes are Darcy factors
   * (64/Re, Haaland), so the divisor is 8. For a smooth pipe at Re = 1e5 this gives Nu = 225 Pr^(1/3), against 230
   * Pr^(1/3) from Dittus-Boelter.
   * </p>
   *
   * @param darcyFrictionFactor Darcy friction factor
   * @param prandtlOrSchmidtNumber Prandtl number (heat) or Schmidt number (mass)
   * @return Stanton number, i.e. the transfer coefficient divided by the phase speed (mass) or by rho*cp*speed (heat)
   */
  protected static double chiltonColburnStanton(double darcyFrictionFactor, double prandtlOrSchmidtNumber) {
    return darcyFrictionFactor / 8.0 * Math.pow(prandtlOrSchmidtNumber, -2.0 / 3.0);
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallFrictionFactor(FlowNodeInterface node) {
    return calcWallFrictionFactor(0, node);
  }

  /**
   * Calculates the Darcy friction factor using Haaland equation for turbulent flow and f = 64/Re for laminar flow.
   * Includes transition zone interpolation.
   *
   * @param phase phase index
   * @param node flow node interface
   * @return Darcy friction factor (dimensionless)
   */
  @Override
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
    double reynolds = Math.abs(node.getReynoldsNumber(phase));
    if (reynolds < 1e-10) {
      return 0.0;
    }
    double relativeRoughness = node.getGeometry().getRelativeRoughnes();
    if (Math.abs(reynolds) < 2300) {
      // Laminar flow
      return 64.0 / reynolds;
    } else if (Math.abs(reynolds) < 4000) {
      // Transition zone - interpolate between laminar and turbulent
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent = Math.pow((1.0 / (-1.8 * Math.log10(6.9 / 4000.0 + Math.pow(relativeRoughness / 3.7, 1.11)))),
          2.0);
      return fLaminar + (fTurbulent - fLaminar) * (reynolds - 2300.0) / 1700.0;
    } else {
      // Turbulent flow - Haaland equation
      return Math.pow((1.0 / (-1.8 * Math.log10(6.9 / reynolds + Math.pow(relativeRoughness / 3.7, 1.11)))), 2.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node) {
    return (1.0 + 75.0 * node.getPhaseFraction(1)) * calcWallFrictionFactor(phase, node);
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phase, FlowNodeInterface node) {
    return this.calcWallHeatTransferCoefficient(phase, node.getPrandtlNumber(phase), node);
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallMassTransferCoefficient(int phase, double schmidtNumber, FlowNodeInterface node) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseMassTransferCoefficient(int phase, double schmidt, FlowNodeInterface node) {
    return 0;
  }
}
