package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.BubbleFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.DropletFlowNode;

/**
 * <p>
 * InterphaseDropletFlow class for droplet/mist and bubble flow regimes.
 * </p>
 *
 * <p>
 * Implements transport coefficient correlations specific to dispersed flow regimes where one phase
 * exists as discrete particles (bubbles or droplets) in a continuous phase. Uses Ranz-Marshall
 * correlation for particle mass transfer: Sh = 2 + 0.6 * Re^0.5 * Sc^0.33
 * </p>
 *
 * <p>
 * Optionally supports the Abramzon-Sirignano (1989) extended film model for evaporating droplets,
 * which accounts for Stefan flow (blowing) at the droplet surface: Sh* = 2 + (Sh_0 - 2) / F(B_M),
 * where F(B_M) = (1+B_M)^0.7 * ln(1+B_M) / B_M and B_M is the Spalding mass transfer number.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseDropletFlow extends InterphaseTwoPhasePipeFlow
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Flag to enable the Abramzon-Sirignano extended film model for evaporating droplets. When true,
   * the Ranz-Marshall Sherwood number is corrected for Stefan flow (blowing) using the Spalding
   * mass transfer number. Default is false (standard Ranz-Marshall).
   */
  private boolean useAbramzonSirignano = false;

  /**
   * The Spalding mass transfer number B_M = (Y_s - Y_inf) / (1 - Y_s), where Y_s is the vapor mass
   * fraction at the droplet surface and Y_inf is the far-field vapor mass fraction. Set externally
   * based on current evaporation conditions.
   */
  private double spaldingMassTransferNumber = 0.0;

  /**
   * <p>
   * Constructor for InterphaseDropletFlow.
   * </p>
   */
  public InterphaseDropletFlow() {}

  /**
   * <p>
   * Constructor for InterphaseDropletFlow.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseDropletFlow(FlowNodeInterface node) {
    // flowNode = node;
  }

  /**
   * Enables the Abramzon-Sirignano (1989) extended film model for evaporating droplets.
   *
   * @param enable true to enable, false for standard Ranz-Marshall
   */
  public void setUseAbramzonSirignano(boolean enable) {
    this.useAbramzonSirignano = enable;
  }

  /**
   * Returns whether the Abramzon-Sirignano model is enabled.
   *
   * @return true if Abramzon-Sirignano model is active
   */
  public boolean isUseAbramzonSirignano() {
    return useAbramzonSirignano;
  }

  /**
   * Sets the Spalding mass transfer number B_M for the Abramzon-Sirignano model.
   *
   * @param bm the Spalding mass transfer number B_M = (Y_s - Y_inf) / (1 - Y_s)
   */
  public void setSpaldingMassTransferNumber(double bm) {
    this.spaldingMassTransferNumber = bm;
  }

  /**
   * Returns the current Spalding mass transfer number.
   *
   * @return the Spalding mass transfer number B_M
   */
  public double getSpaldingMassTransferNumber() {
    return spaldingMassTransferNumber;
  }

  /**
   * Calculates the Abramzon-Sirignano film thickness correction function F(B_M).
   *
   * <p>
   * F(B_M) = (1 + B_M)^0.7 * ln(1 + B_M) / B_M
   * </p>
   *
   * <p>
   * When B_M approaches zero (no blowing), F(B_M) approaches 1.0, recovering the standard
   * Ranz-Marshall correlation. For large B_M (vigorous evaporation), F(B_M) &gt; 1, which
   * effectively thickens the film and reduces the corrected Sherwood number.
   * </p>
   *
   * @param bm the Spalding mass transfer number
   * @return the correction function value F(B_M)
   */
  public double calcAbramzonSirignanoF(double bm) {
    if (bm < 1.0e-6) {
      return 1.0; // No blowing correction when B_M ~ 0
    }
    return Math.pow(1.0 + bm, 0.7) * Math.log(1.0 + bm) / bm;
  }

  /**
   * Gets the characteristic particle diameter from the flow node. For DropletFlowNode, returns the
   * average droplet diameter. For BubbleFlowNode, returns the average bubble diameter. Falls back
   * to a default of 100 microns if the node type is not recognized.
   *
   * @param node the flow node
   * @return the particle diameter in meters
   */
  private double getParticleDiameter(FlowNodeInterface node) {
    if (node instanceof DropletFlowNode) {
      return ((DropletFlowNode) node).getAverageDropletDiameter();
    } else if (node instanceof BubbleFlowNode) {
      return ((BubbleFlowNode) node).getAverageBubbleDiameter();
    }
    return 100.0e-6; // default 100 micron
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For droplet/bubble flow, uses Ranz-Marshall correlation for mass transfer from/to spherical
   * particles: Sh = 2 + 0.6 * Re^0.5 * Sc^0.33
   * </p>
   *
   * <p>
   * This correlation is valid for single spheres and is widely used for dispersed phase mass
   * transfer in two-phase flows.
   * </p>
   */
  @Override
  public double calcSherwoodNumber(int phaseNum, double reynoldsNumber, double schmidtNumber,
      FlowNodeInterface node) {
    // Ranz-Marshall correlation for spherical particles
    // Sh = 2 + 0.6 * Re^0.5 * Sc^0.33
    // Valid for both bubbles (phaseNum=0) and droplets (phaseNum=1)
    return 2.0 + 0.6 * Math.pow(reynoldsNumber, 0.5) * Math.pow(schmidtNumber, 0.33);
  }

  /**
   * <p>
   * Calculates the Nusselt number for droplet/bubble heat transfer using Ranz-Marshall correlation.
   * Nu = 2 + 0.6 * Re^0.5 * Pr^0.33
   * </p>
   */
  @Override
  public double calcNusseltNumber(int phaseNum, double reynoldsNumber, double prandtlNumber,
      FlowNodeInterface node) {
    // Ranz-Marshall correlation for heat transfer
    // Nu = 2 + 0.6 * Re^0.5 * Pr^0.33
    return 2.0 + 0.6 * Math.pow(reynoldsNumber, 0.5) * Math.pow(prandtlNumber, 0.33);
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber(phase)) < 2000) {
      return 64.0 / node.getReynoldsNumber(phase);
    } else {
      return Math.pow((1.0 / (-1.8 * Math.log10(6.9 / node.getReynoldsNumber(phase)
          + Math.pow(node.getGeometry().getRelativeRoughnes() / 3.7, 1.11)))), 2.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node) {
    // TODO: Should call calcWallFrictionFactor(phase)? Input phase is unused
    return (1.0 + 75.0 * node.getPhaseFraction(1)) * calcWallFrictionFactor(0, node);
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
    // For droplet/bubble flow, use Ranz-Marshall analogy: Nu = 2 + 0.6 Re^0.5 Pr^0.33
    double particleDiameter = getParticleDiameter(node);
    boolean isBubbleFlow = node instanceof BubbleFlowNode;

    double conductivity =
        node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();

    // Determine if this phase is the continuous or dispersed phase
    // Droplet flow: gas (phase 0) is continuous, liquid (phase 1) is dispersed
    // Bubble flow: liquid (phase 1) is continuous, gas (phase 0) is dispersed
    boolean isContinuousPhase = (phaseNum == 0 && !isBubbleFlow) || (phaseNum == 1 && isBubbleFlow);

    if (isContinuousPhase) {
      // Continuous phase: Ranz-Marshall with particle Re
      int continuousPhase = isBubbleFlow ? 1 : 0;
      int dispersedPhase = isBubbleFlow ? 0 : 1;
      double relativeVelocity =
          Math.abs(node.getVelocity(continuousPhase) - node.getVelocity(dispersedPhase));
      double contVelocity = Math.abs(node.getVelocity(continuousPhase));
      if (relativeVelocity < 0.01 * contVelocity) {
        relativeVelocity = Math.max(0.1 * contVelocity, 0.01);
      }
      double nuContinuous = node.getBulkSystem().getPhase(continuousPhase).getPhysicalProperties()
          .getKinematicViscosity();
      if (nuContinuous < 1e-15) {
        nuContinuous = 1e-6;
      }
      double reParticle = relativeVelocity * particleDiameter / nuContinuous;
      double nusseltNumber = 2.0 + 0.6 * Math.pow(reParticle, 0.5) * Math.pow(prandtlNumber, 0.33);
      return nusseltNumber * conductivity / particleDiameter;
    } else {
      // Dispersed phase (inside particle): Kronig-Brink Nu = 17.66
      return 17.66 * conductivity / particleDiameter;
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

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates the interphase mass transfer coefficient for droplet/mist and bubble flow using
   * Ranz-Marshall correlation. The characteristic length scale is the particle (droplet or bubble)
   * diameter, not the pipe hydraulic diameter.
   * </p>
   *
   * <p>
   * The continuous phase uses Ranz-Marshall Sh with particle Reynolds number based on the relative
   * velocity. The dispersed phase uses Kronig-Brink Sh = 17.66 for internally circulating spheres.
   * </p>
   *
   * <p>
   * For droplet flow: gas (phase 0) is continuous, liquid (phase 1) is dispersed. For bubble flow:
   * liquid (phase 1) is continuous, gas (phase 0) is dispersed.
   * </p>
   *
   * <p>
   * The mass transfer coefficient is: k_c = Sh * D_ij / d_particle, where D_ij = nu / Sc.
   * </p>
   *
   * @param phaseNum 0 for gas phase, 1 for liquid phase
   * @param schmidtNumber the binary Schmidt number (nu / D_ij) for the component pair
   * @param node the flow node (DropletFlowNode or BubbleFlowNode)
   * @return the binary mass transfer coefficient in m/s
   */
  @Override
  public double calcInterphaseMassTransferCoefficient(int phaseNum, double schmidtNumber,
      FlowNodeInterface node) {
    // Get droplet/bubble diameter from the flow node
    double particleDiameter = getParticleDiameter(node);
    boolean isBubbleFlow = node instanceof BubbleFlowNode;

    // Binary diffusivity D_ij = kinematic viscosity / Schmidt number
    double kinVisc =
        node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
    if (kinVisc < 1e-15) {
      kinVisc = 1e-6;
    }
    double safeSchmidt = schmidtNumber > 0.0 ? schmidtNumber : 1.0;
    double diffusivity = kinVisc / safeSchmidt;

    // Determine if this phase is the continuous or dispersed phase
    // Droplet flow: gas (phase 0) is continuous, liquid (phase 1) is dispersed
    // Bubble flow: liquid (phase 1) is continuous, gas (phase 0) is dispersed
    boolean isContinuousPhase = (phaseNum == 0 && !isBubbleFlow) || (phaseNum == 1 && isBubbleFlow);

    if (isContinuousPhase) {
      // Continuous phase: Ranz-Marshall with particle Re
      int continuousPhase = isBubbleFlow ? 1 : 0;
      int dispersedPhase = isBubbleFlow ? 0 : 1;
      double relativeVelocity =
          Math.abs(node.getVelocity(continuousPhase) - node.getVelocity(dispersedPhase));
      double contVelocity = Math.abs(node.getVelocity(continuousPhase));
      if (relativeVelocity < 0.01 * contVelocity) {
        relativeVelocity = Math.max(0.1 * contVelocity, 0.01);
      }

      double nuContinuous = node.getBulkSystem().getPhase(continuousPhase).getPhysicalProperties()
          .getKinematicViscosity();
      if (nuContinuous < 1e-15) {
        nuContinuous = 1e-6;
      }
      double reParticle = relativeVelocity * particleDiameter / nuContinuous;

      // Ranz-Marshall: Sh = 2 + 0.6 * Re^0.5 * Sc^0.33
      double sh0 = 2.0 + 0.6 * Math.pow(reParticle, 0.5) * Math.pow(schmidtNumber, 0.33);

      double sherwoodNumber = sh0;
      if (useAbramzonSirignano && spaldingMassTransferNumber > 1.0e-6) {
        // Abramzon-Sirignano (1989) correction for Stefan flow:
        // Sh* = 2 + (Sh_0 - 2) / F(B_M)
        double fBm = calcAbramzonSirignanoF(spaldingMassTransferNumber);
        sherwoodNumber = 2.0 + (sh0 - 2.0) / fBm;
      }

      return sherwoodNumber * diffusivity / particleDiameter;
    } else {
      // Dispersed phase (inside particle): Kronig-Brink for circulating droplets/bubbles
      // Sh = 17.66 (steady-state limit for internally circulating sphere)
      double shKronigBrink = 17.66;
      return shKronigBrink * diffusivity / particleDiameter;
    }
  }
}
