package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

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
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseDropletFlow extends InterphaseTwoPhasePipeFlow
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

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
    // System.out.println("velocity " + node.getVelocity(phase));
    if (Math.abs(node.getReynoldsNumber()) < 2000) {
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
    double redMassTrans = 0;
    double massTrans = 0;
    if (phaseNum == 1) {
      if (Math.abs(node.getReynoldsNumber(phaseNum)) < 300) {
        redMassTrans = 1.099e-2 * Math.pow(node.getReynoldsNumber(phaseNum), 0.3955)
            * Math.pow(schmidtNumber, 0.5);
      } else if (Math.abs(node.getReynoldsNumber(phaseNum)) < 1600) {
        redMassTrans = 2.995e-2 * Math.pow(node.getReynoldsNumber(phaseNum), 0.2134)
            * Math.pow(schmidtNumber, 0.5);
      } else {
        redMassTrans = 9.777e-4 * Math.pow(node.getReynoldsNumber(phaseNum), 0.6804)
            * Math.pow(schmidtNumber, 0.5);
      }
      // System.out.println("redmass" + redMassTrans + " redmass " +
      // redMassTrans/Math.sqrt(schmidtNumber) +" rey " +
      // node.getReynoldsNumber(phase)/schmidtNumber);
      // er usikker paa denne korreksjonen med 1e-2 - maa sjekkes opp mot artikkel av
      // Yih og Chen (1982) - satser paa at de ga den med enhet cm/sek
      massTrans = redMassTrans
          * Math.pow(Math.pow(node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
              .getKinematicViscosity(), 2.0) / gravity, -1.0 / 3.0)
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
          / schmidtNumber;
    }
    if (phaseNum == 0) {
      if (Math.abs(node.getReynoldsNumber(phaseNum)) < 2300) {
        // System.out.println("schmidt " + schmidtNumber +" phaseNum " + phaseNum);
        // System.out.println("hyd diam " + node.getHydraulicDiameter(phaseNum) +" phaseNum "
        // + phaseNum);
        // System.out.println("kin visk " +
        // node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
        // +" phaseNum " + phaseNum);
        // System.out.println("mas " +3.66 / node.getHydraulicDiameter(phaseNum) /
        // schmidtNumber *
        // node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
        // +" phaseNum " + phaseNum);
        massTrans = 3.66 / node.getHydraulicDiameter(phaseNum) / schmidtNumber * node
            .getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity();
      } else {
        double temp = node.getVelocity(phaseNum);
        // System.out.println("mass " + 1e-5* 0.5 * this.calcWallFrictionFactor(phaseNum,
        // node) * Math.pow(schmidtNumber, -2.0/3.0) * temp);
        massTrans = 0.5 * this.calcWallFrictionFactor(phaseNum, node)
            * Math.pow(schmidtNumber, -2.0 / 3.0) * temp;
      }
    }
    // System.out.println("mass "+ massTrans + " phaseNum " + phaseNum + " rey " +
    // node.getReynoldsNumber(phaseNum) + " COMP " + );
    return massTrans;
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
