package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * <p>
 * InterphaseSlugFlow class.
 * </p>
 * 
 * <p>
 * Calculates interphase transport coefficients for slug flow regime. Slug flow is characterized by
 * alternating liquid slugs and gas bubbles (Taylor bubbles) moving through the pipe. This regime is
 * common in horizontal and near-horizontal pipes at intermediate gas and liquid flow rates.
 * </p>
 * 
 * <p>
 * The correlations implemented here are based on the work of:
 * <ul>
 * <li>Dukler, A.E. and Hubbard, M.G. (1975) - Slug flow model</li>
 * <li>Andreussi, P. and Bendiksen, K.H. (1989) - Slug characteristics</li>
 * </ul>
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class InterphaseSlugFlow extends InterphaseTwoPhasePipeFlow
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Slug unit length to diameter ratio (typical range 15-40). */
  private double slugLengthToDiameterRatio = 30.0;

  /** Liquid holdup in slug body (typically 0.7-1.0). */
  private double liquidHoldupInSlug = 0.85;

  /**
   * <p>
   * Constructor for InterphaseSlugFlow.
   * </p>
   */
  public InterphaseSlugFlow() {}

  /**
   * <p>
   * Constructor for InterphaseSlugFlow.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseSlugFlow(FlowNodeInterface node) {
    // flowNode = node;
  }

  /**
   * <p>
   * Getter for slug length to diameter ratio.
   * </p>
   *
   * @return slug length to diameter ratio
   */
  public double getSlugLengthToDiameterRatio() {
    return slugLengthToDiameterRatio;
  }

  /**
   * <p>
   * Setter for slug length to diameter ratio.
   * </p>
   *
   * @param ratio slug length to diameter ratio (typically 15-40)
   */
  public void setSlugLengthToDiameterRatio(double ratio) {
    this.slugLengthToDiameterRatio = ratio;
  }

  /**
   * <p>
   * Getter for liquid holdup in slug body.
   * </p>
   *
   * @return liquid holdup in slug body (0-1)
   */
  public double getLiquidHoldupInSlug() {
    return liquidHoldupInSlug;
  }

  /**
   * <p>
   * Setter for liquid holdup in slug body.
   * </p>
   *
   * @param holdup liquid holdup in slug body (typically 0.7-1.0)
   */
  public void setLiquidHoldupInSlug(double holdup) {
    this.liquidHoldupInSlug = holdup;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
    // For slug flow, use average friction factor weighted by slug and film regions
    if (Math.abs(node.getReynoldsNumber(phase)) < 2000) {
      return 64.0 / node.getReynoldsNumber(phase);
    } else {
      // Blasius correlation with slug flow enhancement factor
      double baseFriction = Math.pow((1.0 / (-1.8 * Math.log10(6.9 / node.getReynoldsNumber(phase)
          + Math.pow(node.getGeometry().getRelativeRoughnes() / 3.7, 1.11)))), 2.0);
      // Enhancement due to mixing in slug body (typically 1.1-1.3)
      double slugEnhancement = 1.2;
      return baseFriction * slugEnhancement;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node) {
    // Higher interphase friction in slug flow due to intense mixing at slug front
    // Uses Andreussi-Bendiksen correlation framework
    double baseFriction = calcWallFrictionFactor(0, node);
    double mixingEnhancement = 1.0 + 150.0 * node.getPhaseFraction(1);
    return baseFriction * mixingEnhancement;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phaseNum, double prandtlNumber,
      FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber(phaseNum)) < 2000) {
      return 3.66 / node.getHydraulicDiameter(phaseNum)
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    } else {
      // Enhanced heat transfer due to slug-induced mixing
      double temp = node.getBulkSystem().getPhase(phaseNum).getCp()
          / node.getBulkSystem().getPhase(phaseNum).getMolarMass()
          / node.getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          * node.getVelocity(phaseNum);
      // Slug flow enhancement factor (1.3-1.5 typical)
      double slugEnhancement = 1.4;
      return slugEnhancement * 0.5 * this.calcWallFrictionFactor(phaseNum, node)
          * Math.pow(prandtlNumber, -2.0 / 3.0) * temp;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseHeatTransferCoefficient(int phaseNum, double prandtlNumber,
      FlowNodeInterface node) {
    if (Math.abs(node.getReynoldsNumber()) < 2000) {
      return 3.66 / node.getHydraulicDiameter(phaseNum)
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    } else {
      // High interphase heat transfer in slug flow due to intense mixing at slug front
      double temp = node.getBulkSystem().getPhase(phaseNum).getCp()
          / node.getBulkSystem().getPhase(phaseNum).getMolarMass()
          / node.getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          * node.getVelocity(phaseNum);
      // Significant enhancement due to slug passage
      double slugEnhancement = 2.0;
      return slugEnhancement * 0.5 * this.calcWallFrictionFactor(phaseNum, node)
          * Math.pow(prandtlNumber, -2.0 / 3.0) * temp;
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
      // Enhanced wall mass transfer due to slug-induced turbulence
      double slugEnhancement = 1.4;
      return slugEnhancement * 0.5 * this.calcWallFrictionFactor(phaseNum, node)
          * Math.pow(schmidtNumber, -2.0 / 3.0) * temp;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseMassTransferCoefficient(int phaseNum, double schmidtNumber,
      FlowNodeInterface node) {
    double massTrans = 0;

    if (phaseNum == 1) {
      // Liquid phase - enhanced mass transfer at Taylor bubble nose and wake
      if (Math.abs(node.getReynoldsNumber(phaseNum)) < 300) {
        // Laminar film region
        massTrans =
            1.5e-2 * Math.pow(node.getReynoldsNumber(phaseNum), 0.4) * Math.pow(schmidtNumber, 0.5);
      } else if (Math.abs(node.getReynoldsNumber(phaseNum)) < 1600) {
        // Transitional
        massTrans = 4.0e-2 * Math.pow(node.getReynoldsNumber(phaseNum), 0.25)
            * Math.pow(schmidtNumber, 0.5);
      } else {
        // Turbulent slug body - significantly enhanced
        massTrans =
            2.0e-3 * Math.pow(node.getReynoldsNumber(phaseNum), 0.7) * Math.pow(schmidtNumber, 0.5);
      }
      massTrans = massTrans
          * Math.pow(Math.pow(node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
              .getKinematicViscosity(), 2.0) / gravity, -1.0 / 3.0)
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
          / schmidtNumber;
    }

    if (phaseNum == 0) {
      // Gas phase in Taylor bubble
      if (Math.abs(node.getReynoldsNumber(phaseNum)) < 2300) {
        massTrans = 3.66 / node.getHydraulicDiameter(phaseNum) / schmidtNumber * node
            .getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
      } else {
        double temp = node.getVelocity(phaseNum);
        // Enhanced mass transfer from Taylor bubble to liquid film
        double slugEnhancement = 1.5;
        massTrans = slugEnhancement * 0.5 * this.calcWallFrictionFactor(phaseNum, node)
            * Math.pow(schmidtNumber, -2.0 / 3.0) * temp;
      }
    }

    return massTrans;
  }
}
