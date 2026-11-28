package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.thermo.system.SystemInterface;

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

  /**
   * {@inheritDoc}
   *
   * <p>
   * The interphase heat transfer coefficients are the heat-transfer counterparts of
   * {@link #calcInterphaseMassTransferCoefficient(int, double, FlowNodeInterface)} with the Prandtl number in place of
   * the Schmidt number. Gas side: the laminar limit or the Chilton-Colburn analogy on the interfacial friction factor,
   * combined with natural convection. Liquid side: the Yih and Chen film relation, {@code h (nu^2/g)^(1/3) /
   * lambda = a Re^b Pr^(1/2)}, bounded below by conduction through a laminar layer, {@code 3.41 lambda / depth}.
   * </p>
   */
  @Override
  public double calcInterphaseHeatTransferCoefficient(int phaseNum, double prandtlNumber, FlowNodeInterface node) {
    double reynolds = Math.abs(node.getReynoldsNumber(phaseNum));
    double conductivity = node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    double kinematicViscosity = node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
    if (phaseNum == 1) {
      double flowing = yihChenReducedNumber(reynolds, prandtlNumber)
          * Math.pow(Math.max(Math.pow(kinematicViscosity, 2.0) / gravity, 1e-30), -1.0 / 3.0) * conductivity;
      double depth = node.getHydraulicDiameter(phaseNum) / 4.0;
      double conductionLimit = depth > 0.0 ? LAMINAR_LAYER_SHERWOOD * conductivity / depth : 0.0;
      return Math.max(flowing, conductionLimit);
    }
    double volumetricHeatCapacity = node.getBulkSystem().getPhase(phaseNum).getCp()
        / node.getBulkSystem().getPhase(phaseNum).getMolarMass()
        / node.getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
        * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity();
    double forced;
    if (reynolds < 2300) {
      forced = 3.66 / node.getHydraulicDiameter(phaseNum) * conductivity;
    } else {
      forced = chiltonColburnStanton(this.calcInterPhaseFrictionFactor(phaseNum, node), prandtlNumber)
          * volumetricHeatCapacity * Math.abs(node.getVelocity(phaseNum));
    }
    double natural = naturalConvectionSpeed(node, kinematicViscosity, prandtlNumber) * volumetricHeatCapacity;
    return Math.cbrt(Math.pow(forced, 3.0) + Math.pow(natural, 3.0));
  }

  /**
   * Yih and Chen (1982) reduced transfer number of a liquid film, {@code a Re^b X^(1/2)}.
   *
   * @param reynolds magnitude of the film Reynolds number
   * @param schmidtOrPrandtl Schmidt number for mass transfer or Prandtl number for heat transfer
   * @return reduced Sherwood or Nusselt number based on the length (nu^2/g)^(1/3)
   */
  private static double yihChenReducedNumber(double reynolds, double schmidtOrPrandtl) {
    if (reynolds < 300) {
      return 1.099e-2 * Math.pow(reynolds, 0.3955) * Math.pow(schmidtOrPrandtl, 0.5);
    } else if (reynolds < 1600) {
      return 2.995e-2 * Math.pow(reynolds, 0.2134) * Math.pow(schmidtOrPrandtl, 0.5);
    }
    return 9.777e-4 * Math.pow(reynolds, 0.6804) * Math.pow(schmidtOrPrandtl, 0.5);
  }

  /**
   * Gas-side transfer speed of turbulent natural convection above the interface.
   *
   * <p>
   * {@code Sh = 0.15 (Gr Sc)^(1/3)} for an unstably stratified horizontal layer (the relation for a heated plate facing
   * upwards, McAdams), in which the length cancels: {@code k = 0.15 (g drho/rho)^(1/3) nu^(1/3) X^(-2/3)}. The density
   * difference is taken between the bulk gas and the gas at the interface of the current film solution. It drives
   * convection only where it is unstable, see {@link #unstableDensityDifference(double, double)}.
   * </p>
   *
   * @param node flow node; its fluid boundary supplies the interface state
   * @param kinematicViscosity gas kinematic viscosity in m2/s
   * @param schmidtOrPrandtl Schmidt number for mass transfer or Prandtl number for heat transfer
   * @return transfer speed in m/s; multiply by rho*cp for a heat transfer coefficient. Zero without an interface state
   */
  protected double naturalConvectionSpeed(FlowNodeInterface node, double kinematicViscosity, double schmidtOrPrandtl) {
    if (node.getFluidBoundary() == null) {
      return 0.0;
    }
    SystemInterface interfaceState = node.getFluidBoundary().getInterphaseSystem();
    if (interfaceState == null || interfaceState.getNumberOfPhases() < 2) {
      return 0.0;
    }
    double bulkDensity = node.getBulkSystem().getPhase(0).getDensity("kg/m3");
    double interfaceDensity = interfaceState.getPhase(0).getDensity("kg/m3");
    double difference = unstableDensityDifference(bulkDensity, interfaceDensity);
    if (!(difference > 0.0) || !(bulkDensity > 0.0) || !Double.isFinite(difference)) {
      return 0.0;
    }
    return 0.15 * Math.cbrt(gravity * difference / bulkDensity * kinematicViscosity)
        * Math.pow(schmidtOrPrandtl, -2.0 / 3.0);
  }

  /**
   * Density difference that drives natural convection in the gas.
   *
   * <p>
   * In stratified flow the interface lies below the gas, so the layer is unstable only when the gas at the interface is
   * lighter than the bulk gas. A heavier interface gas, as above an evaporating condensate, is stably stratified and
   * gives no convection.
   * </p>
   *
   * @param bulkDensity bulk gas density in kg/m3
   * @param interfaceDensity gas density at the interface in kg/m3
   * @return driving density difference in kg/m3, zero when the stratification is stable
   */
  protected double unstableDensityDifference(double bulkDensity, double interfaceDensity) {
    return Math.max(0.0, bulkDensity - interfaceDensity);
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
   * / d_h} in laminar flow and the Chilton-Colburn analogy in turbulent flow, applied to the interfacial friction
   * factor of {@link #calcInterPhaseFrictionFactor(int, FlowNodeInterface)} because the gas exchanges with the wavy
   * liquid surface, not with the pipe wall. Natural convection in the gas is added as {@code (k_forced^3 +
   * k_natural^3)^(1/3)}. Both sides use the magnitude of the phase Reynolds number and speed, so a phase flowing
   * backwards has the same coefficient as one flowing forwards.
   * </p>
   */
  @Override
  public double calcInterphaseMassTransferCoefficient(int phaseNum, double schmidtNumber, FlowNodeInterface node) {
    double reynolds = Math.abs(node.getReynoldsNumber(phaseNum));
    double kinematicViscosity = node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
    double diffusivity = kinematicViscosity / schmidtNumber;
    if (phaseNum == 1) {
      double flowing = yihChenReducedNumber(reynolds, schmidtNumber)
          * Math.pow(Math.max(Math.pow(kinematicViscosity, 2.0) / gravity, 1e-30), -1.0 / 3.0) * diffusivity;
      double depth = node.getHydraulicDiameter(phaseNum) / 4.0;
      double diffusionLimit = depth > 0.0 ? LAMINAR_LAYER_SHERWOOD * diffusivity / depth : 0.0;
      return Math.max(flowing, diffusionLimit);
    }
    double forced;
    if (reynolds < 2300) {
      forced = 3.66 / node.getHydraulicDiameter(phaseNum) * diffusivity;
    } else {
      forced = chiltonColburnStanton(this.calcInterPhaseFrictionFactor(phaseNum, node), schmidtNumber)
          * Math.abs(node.getVelocity(phaseNum));
    }
    double natural = naturalConvectionSpeed(node, kinematicViscosity, schmidtNumber);
    return Math.cbrt(Math.pow(forced, 3.0) + Math.pow(natural, 3.0));
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
