/*
 * FrictionFactorBaseClass.java
 *
 * Created on 12. juni 2001, 19:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseOnePhase.interphasePipeFlow;

import neqsim.MathLib.generalMath.GeneralMath;
import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseOnePhase.InterphaseOnePhase;

/**
 *
 * @author esol
 * @version
 */
public class InterphasePipeFlow extends InterphaseOnePhase {
    private static final long serialVersionUID = 1000;

    /**
     * Creates new FrictionFactorBaseClass All frictionfactors are the fanning frictionfactor.
     */

    public InterphasePipeFlow() {}

    public InterphasePipeFlow(FlowNodeInterface node) {
        // flowNode = node;
    }

    @Override
    public double calcWallFrictionFactor(FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber()) < 2000) {
            return 64.0 / node.getReynoldsNumber();
        } else {
            return Math.pow(
                    (1.0 / (-1.8 * GeneralMath.log10(6.9 / node.getReynoldsNumber()
                            + Math.pow(node.getGeometry().getRelativeRoughnes() / 3.7, 1.11)))),
                    2.0);
        }
    }

    @Override
    public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber()) < 2000) {
            return 64.0 / node.getReynoldsNumber(phase);
        } else {
            return Math.pow(
                    (1.0 / (-1.8 * GeneralMath.log10(6.9 / node.getReynoldsNumber(phase)
                            + Math.pow(node.getGeometry().getRelativeRoughnes() / 3.7, 1.11)))),
                    2.0);
        }
    }

    @Override
    public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber,
            FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber()) < 2000) {
            return 3.66 / node.getGeometry().getDiameter() * node.getBulkSystem().getPhases()[phase]
                    .getPhysicalProperties().getConductivity();
        }
        // if turbulent - use chilton colburn analogy
        else {
            double temp = node.getBulkSystem().getPhases()[phase].getCp()
                    / node.getBulkSystem().getPhases()[phase].getMolarMass()
                    / node.getBulkSystem().getPhases()[phase].getNumberOfMolesInPhase()
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getDensity()
                    * node.getVelocity();
            return 0.5 * this.calcWallFrictionFactor(phase, node)
                    * Math.pow(prandtlNumber, -2.0 / 3.0) * temp;
        }
    }

    @Override
    public double calcWallMassTransferCoefficient(int phase, double schmidtNumber,
            FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber()) < 2000) {
            return 3.66 / node.getGeometry().getDiameter() / schmidtNumber
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties()
                            .getKinematicViscosity();
        } else {
            double temp = node.getVelocity();
            return 0.5 * this.calcWallFrictionFactor(phase, node)
                    * Math.pow(schmidtNumber, -2.0 / 3.0) * temp;
        }
    }
}
