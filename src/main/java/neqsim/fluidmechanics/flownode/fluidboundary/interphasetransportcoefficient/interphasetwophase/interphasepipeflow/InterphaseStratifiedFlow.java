package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * InterphaseStratifiedFlow class for stratified two-phase pipe flow.
 *
 * <p>
 * Implements transport coefficient correlations specific to stratified flow regime, where gas flows above a liquid
 * layer with a relatively flat interface. The correlations are based on Solbraa (2002) and Yih &amp; Chen (1982) for
 * liquid-side mass transfer.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseStratifiedFlow extends InterphaseTwoPhasePipeFlow
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for InterphaseStratifiedFlow.
   */
  public InterphaseStratifiedFlow() {
  }

  /**
   * Constructor for InterphaseStratifiedFlow.
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseStratifiedFlow(FlowNodeInterface node) {
    // flowNode = node;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For stratified flow, the Sherwood number correlation depends on the flow regime in each phase.
   * </p>
   * <ul>
   * <li>Gas phase: Uses correlations for flow over a liquid surface</li>
   * <li>Liquid phase: Uses Yih &amp; Chen (1982) correlations for wavy film mass transfer</li>
   * </ul>
   */
  @Override
  public double calcSherwoodNumber(int phaseNum, double reynoldsNumber, double schmidtNumber, FlowNodeInterface node) {
    if (phaseNum == 0) {
      // Gas phase Sherwood number
      if (reynoldsNumber < 2300) {
        // Laminar flow over liquid surface
        return 3.66;
      } else {
        // Turbulent flow - use Chilton-Colburn analogy
        return 0.023 * Math.pow(reynoldsNumber, 0.83) * Math.pow(schmidtNumber, 0.33);
      }
    } else {
      // Liquid phase Sherwood number (Yih & Chen, 1982)
      if (reynoldsNumber < 300) {
        // Low Reynolds number regime
        return 1.099e-2 * Math.pow(reynoldsNumber, 0.3955) * Math.pow(schmidtNumber, 0.5);
      } else if (reynoldsNumber < 1600) {
        // Transitional regime
        return 2.995e-2 * Math.pow(reynoldsNumber, 0.2134) * Math.pow(schmidtNumber, 0.5);
      } else {
        // High Reynolds number regime
        return 9.777e-4 * Math.pow(reynoldsNumber, 0.6804) * Math.pow(schmidtNumber, 0.5);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
    // The friction factor depends on the magnitude of the Reynolds number; a reversed phase has the same value.
    double reynolds = Math.abs(node.getReynoldsNumber(phase));
    if (reynolds < 2000) {
      return 64.0 / reynolds;
    } else {
      return Math.pow(
          (1.0 / (-1.8 * Math.log10(6.9 / reynolds + Math.pow(node.getGeometry().getRelativeRoughnes() / 3.7, 1.11)))),
          2.0);
    }
  }

  /**
   * Chilton-Colburn Stanton number for a Darcy friction factor.
   *
   * <p>
   * The analogy reads {@code St * X^(2/3) = f_Fanning / 2 = f_Darcy / 8}, with X the Prandtl number for heat and the
   * Schmidt number for mass transfer. {@link #calcWallFrictionFactor(int, FlowNodeInterface)} returns the Darcy factor
   * (64/Re, Haaland), so the divisor is 8. For a smooth pipe at Re = 1e5 this gives Nu = 225 Pr^(1/3), against 230
   * Pr^(1/3) from Dittus-Boelter.
   * </p>
   *
   * @param darcyFrictionFactor Darcy friction factor
   * @param prandtlOrSchmidtNumber Prandtl number (heat) or Schmidt number (mass)
   * @return Stanton number, i.e. the transfer coefficient divided by the phase speed (mass) or by rho*cp*speed (heat)
   */
  static double chiltonColburnStanton(double darcyFrictionFactor, double prandtlOrSchmidtNumber) {
    return darcyFrictionFactor / 8.0 * Math.pow(prandtlOrSchmidtNumber, -2.0 / 3.0);
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node) {
    return (1.0 + 75.0 * node.getPhaseFraction(1)) * calcWallFrictionFactor(phase, node);
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phaseNum, double prandtlNumber, FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber(phaseNum)) < 2000) {
      return 3.66 / node.getHydraulicDiameter(phaseNum)
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    } else {
      // if turbulent - use chilton colburn analogy

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
  public double calcInterphaseHeatTransferCoefficient(int phaseNum, double prandtlNumber, FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber(phaseNum)) < 2000) {
      return 3.66 / node.getHydraulicDiameter(phaseNum)
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    } else {
      // if turbulent - use chilton colburn analogy
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
      return 3.66 / node.getHydraulicDiameter(phaseNum) / schmidtNumber
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
    } else {
      double temp = Math.abs(node.getVelocity(phaseNum));
      return chiltonColburnStanton(this.calcWallFrictionFactor(phaseNum, node), schmidtNumber) * temp;
    }
  }

  /**
   * Sherwood number of a laminar liquid layer at long exposure time, based on the layer depth (Bird, Stewart and
   * Lightfoot, gas absorption into a falling film).
   */
  private static final double LAMINAR_LAYER_SHERWOOD = 3.41;

  /**
   * {@inheritDoc}
   *
   * <p>
   * Liquid side: Yih and Chen (1982), {@code k (nu^2/g)^(1/3) / D = a Re^b Sc^(1/2)}, a dimensionless relation. It
   * tends to zero for a liquid at rest, so the coefficient is not allowed below the diffusion limit of a laminar layer,
   * {@code 3.41 D / depth}, with the depth taken as a quarter of the liquid hydraulic diameter. Gas side: {@code 3.66 D
   * / d_h} in laminar flow and the Chilton-Colburn analogy in turbulent flow. Both sides use the magnitude of the phase
   * Reynolds number and speed, so a phase flowing backwards has the same coefficient as one flowing forwards.
   * </p>
   */
  @Override
  public double calcInterphaseMassTransferCoefficient(int phaseNum, double schmidtNumber, FlowNodeInterface node) {
    double reynolds = Math.abs(node.getReynoldsNumber(phaseNum));
    double kinematicViscosity = node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
    double diffusivity = kinematicViscosity / schmidtNumber;
    if (phaseNum == 1) {
      double redMassTrans;
      if (reynolds < 300) {
        redMassTrans = 1.099e-2 * Math.pow(reynolds, 0.3955) * Math.pow(schmidtNumber, 0.5);
      } else if (reynolds < 1600) {
        redMassTrans = 2.995e-2 * Math.pow(reynolds, 0.2134) * Math.pow(schmidtNumber, 0.5);
      } else {
        redMassTrans = 9.777e-4 * Math.pow(reynolds, 0.6804) * Math.pow(schmidtNumber, 0.5);
      }
      double flowing = redMassTrans * Math.pow(Math.max(Math.pow(kinematicViscosity, 2.0) / gravity, 1e-30), -1.0 / 3.0)
          * diffusivity;
      double depth = node.getHydraulicDiameter(phaseNum) / 4.0;
      double diffusionLimit = depth > 0.0 ? LAMINAR_LAYER_SHERWOOD * diffusivity / depth : 0.0;
      return Math.max(flowing, diffusionLimit);
    }
    if (reynolds < 2300) {
      return 3.66 / node.getHydraulicDiameter(phaseNum) * diffusivity;
    }
    return chiltonColburnStanton(this.calcWallFrictionFactor(phaseNum, node), schmidtNumber)
        * Math.abs(node.getVelocity(phaseNum));
  }

  // public double calcInterphaseMassTransferCoefficient(int phaseNum, double schmidtNumber,
  // FlowNodeInterface node){
  // double redMassTrans=0.0, massTrans=0.0;
  // double c2=0.181, c3=0.72, c4=0.33;
  // if(phaseNum==1){
  // redMassTrans = c2 * Math.pow(node.getReynoldsNumber(phaseNum),c3) * Math.pow(schmidtNumber,
  // c4);
  // //System.out.println("red " + redMassTrans/Math.pow(node.getReynoldsNumber(phaseNum),c3));
  // //System.out.println("sc " + schmidtNumber);
  // massTrans =
  // redMassTrans*node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
  // / schmidtNumber / node.getGeometry().getDiameter();
  // }
  // if(phaseNum==0){
  // //System.out.println("diff " +
  // node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity() /
  // schmidtNumber);

  // //massTrans = 3.66 / node.getHydraulicDiameter(phaseNum) / schmidtNumber *
  // node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
  // massTrans=0.010;
  // }
  // //System.out.println("mass trans " +massTrans + " phaseNum " + phaseNum);
  // return massTrans;
  // }
}
