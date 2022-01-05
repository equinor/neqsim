package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.interphasePipeFlow;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;

/**
 * <p>
 * InterphaseDropletFlow class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseDropletFlow extends InterphaseTwoPhasePipeFlow
        implements neqsim.thermo.ThermodynamicConstantsInterface {
    private static final long serialVersionUID = 1000;

    public InterphaseDropletFlow() {}

    /**
     * <p>
     * Constructor for InterphaseDropletFlow.
     * </p>
     *
     * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public InterphaseDropletFlow(FlowNodeInterface node) {
        // flowNode = node;
    }

    /** {@inheritDoc} */
    @Override
    public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber(phase)) < 2000) {
            return 64.0 / node.getReynoldsNumber(phase);
        } else {
            return Math.pow(
                    (1.0 / (-1.8 * Math.log10(6.9 / node.getReynoldsNumber(phase)
                            + Math.pow(node.getGeometry().getRelativeRoughnes() / 3.7, 1.11)))),
                    2.0);
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
    public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber,
            FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber(phase)) < 2000) {
            return 3.66 / node.getHydraulicDiameter(phase) * node.getBulkSystem().getPhases()[phase]
                    .getPhysicalProperties().getConductivity();
        }
        // if turbulent - use chilton colburn analogy
        else {
            double temp = node.getBulkSystem().getPhases()[phase].getCp()
                    / node.getBulkSystem().getPhases()[phase].getMolarMass()
                    / node.getBulkSystem().getPhases()[phase].getNumberOfMolesInPhase()
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getDensity()
                    * node.getVelocity(phase);
            return 0.5 * this.calcWallFrictionFactor(phase, node)
                    * Math.pow(prandtlNumber, -2.0 / 3.0) * temp;
        }
    }

    /** {@inheritDoc} */
    @Override
    public double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber,
            FlowNodeInterface node) {
        // System.out.println("velocity " + node.getVelocity(phase));
        if (Math.abs(node.getReynoldsNumber()) < 2000) {
            return 3.66 / node.getHydraulicDiameter(phase) * node.getBulkSystem().getPhases()[phase]
                    .getPhysicalProperties().getConductivity();
        }
        // if turbulent - use chilton colburn analogy
        else {
            double temp = node.getBulkSystem().getPhases()[phase].getCp()
                    / node.getBulkSystem().getPhases()[phase].getMolarMass()
                    / node.getBulkSystem().getPhases()[phase].getNumberOfMolesInPhase()
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getDensity()
                    * node.getVelocity(phase);

            return 0.5 * this.calcWallFrictionFactor(phase, node)
                    * Math.pow(prandtlNumber, -2.0 / 3.0) * temp;
        }
    }

    /** {@inheritDoc} */
    @Override
    public double calcWallMassTransferCoefficient(int phase, double schmidtNumber,
            FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber()) < 2000) {
            return 3.66 / node.getHydraulicDiameter(phase) / schmidtNumber
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties()
                            .getKinematicViscosity();
        } else {
            double temp = node.getVelocity(phase);
            return 0.5 * this.calcWallFrictionFactor(phase, node)
                    * Math.pow(schmidtNumber, -2.0 / 3.0) * temp;
        }
    }

    /** {@inheritDoc} */
    @Override
    public double calcInterphaseMassTransferCoefficient(int phase, double schmidtNumber,
            FlowNodeInterface node) {
        double redMassTrans = 0;
        double massTrans = 0;
        if (phase == 1) {
            if (Math.abs(node.getReynoldsNumber(phase)) < 300) {
                redMassTrans = 1.099e-2 * Math.pow(node.getReynoldsNumber(phase), 0.3955)
                        * Math.pow(schmidtNumber, 0.5);
            } else if (Math.abs(node.getReynoldsNumber(phase)) < 1600) {
                redMassTrans = 2.995e-2 * Math.pow(node.getReynoldsNumber(phase), 0.2134)
                        * Math.pow(schmidtNumber, 0.5);
            } else {
                redMassTrans = 9.777e-4 * Math.pow(node.getReynoldsNumber(phase), 0.6804)
                        * Math.pow(schmidtNumber, 0.5);
            }
            // System.out.println("redmass" + redMassTrans + " redmass " +
            // redMassTrans/Math.sqrt(schmidtNumber) +" rey " +
            // node.getReynoldsNumber(phase)/schmidtNumber);
            // er usikker paa denne korreksjonen med 1e-2 - maa sjekkes opp mot artikkel av
            // Yih og Chen (1982) - satser paa at de ga den med enhet cm/sek
            massTrans =
                    redMassTrans
                            * Math.pow(Math
                                    .pow(node.getBulkSystem().getPhases()[phase]
                                            .getPhysicalProperties().getKinematicViscosity(), 2.0)
                                    / gravity, -1.0 / 3.0)
                            * node.getBulkSystem().getPhases()[phase].getPhysicalProperties()
                                    .getKinematicViscosity()
                            / schmidtNumber;
        }
        if (phase == 0) {
            if (Math.abs(node.getReynoldsNumber(phase)) < 2300) {
                // System.out.println("schmidt " + schmidtNumber +" phase " + phase);
                // System.out.println("hyd diam " + node.getHydraulicDiameter(phase) +" phase "
                // + phase);
                // System.out.println("kin visk " +
                // node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getKinematicViscosity()
                // +" phase " + phase);
                // System.out.println("mas " +3.66 / node.getHydraulicDiameter(phase) /
                // schmidtNumber *
                // node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getKinematicViscosity()
                // +" phase " + phase);
                massTrans = 3.66 / node.getHydraulicDiameter(phase) / schmidtNumber
                        * node.getBulkSystem().getPhases()[phase].getPhysicalProperties()
                                .getKinematicViscosity();
            } else {
                double temp = node.getVelocity(phase);
                // System.out.println("mass " + 1e-5* 0.5 * this.calcWallFrictionFactor(phase,
                // node) * Math.pow(schmidtNumber, -2.0/3.0) * temp);
                massTrans = 0.5 * this.calcWallFrictionFactor(phase, node)
                        * Math.pow(schmidtNumber, -2.0 / 3.0) * temp;
            }
        }
        // System.out.println("mass "+ massTrans + " phase " + phase + " rey " +
        // node.getReynoldsNumber(phase) + " COMP " + );
        return massTrans;
    }

    // public double calcInterphaseMassTransferCoefficient(int phase, double schmidtNumber,
    // FlowNodeInterface node){
    // double redMassTrans=0.0, massTrans=0.0;
    // double c2=0.181, c3=0.72, c4=0.33;
    // if(phase==1){
    // redMassTrans = c2 * Math.pow(node.getReynoldsNumber(phase),c3) * Math.pow(schmidtNumber, c4);
    // //System.out.println("red " + redMassTrans/Math.pow(node.getReynoldsNumber(phase),c3));
    // //System.out.println("sc " + schmidtNumber);
    // massTrans =
    // redMassTrans*node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getKinematicViscosity()
    // / schmidtNumber / node.getGeometry().getDiameter();
    // }
    // if(phase==0){
    // //System.out.println("diff " +
    // node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getKinematicViscosity() /
    // schmidtNumber);
    //
    // //massTrans = 3.66 / node.getHydraulicDiameter(phase) / schmidtNumber *
    // node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getKinematicViscosity();
    // massTrans=0.010;
    // }
    // //System.out.println("mass trans " +massTrans + " phase " + phase);
    // return massTrans;
    // }
}
