/*
 * FrictionFactorBaseClass.java
 *
 * Created on 12. juni 2001, 19:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.stirredCell;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.interphasePipeFlow.InterphaseStratifiedFlow;

/**
 * <p>InterphaseStirredCellFlow class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseStirredCellFlow extends InterphaseStratifiedFlow {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new FrictionFactorBaseClass All frictionfactors are the fanning
     * frictionfactor.
     */
    public InterphaseStirredCellFlow() {
    }

    /**
     * <p>Constructor for InterphaseStirredCellFlow.</p>
     *
     * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public InterphaseStirredCellFlow(FlowNodeInterface node) {
        // flowNode = node;
    }

	/** {@inheritDoc} */
    @Override
	public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber(phase)) < 2000) {
            return 3.66 / node.getHydraulicDiameter(phase)
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getConductivity();
        }
        // if turbulent - use chilton colburn analogy
        else {
            double temp = node.getBulkSystem().getPhases()[phase].getCp()
                    / node.getBulkSystem().getPhases()[phase].getMolarMass()
                    / node.getBulkSystem().getPhases()[phase].getNumberOfMolesInPhase()
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getDensity()
                    * node.getVelocity(phase);
            return 0.5 * this.calcWallFrictionFactor(phase, node) * Math.pow(prandtlNumber, -2.0 / 3.0) * temp;
        }
    }

	/** {@inheritDoc} */
    @Override
	public double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber()) < 2000) {
            return 3.66 / node.getHydraulicDiameter(phase)
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getConductivity();
        }
        // if turbulent - use chilton colburn analogy
        else {
            double temp = node.getBulkSystem().getPhases()[phase].getCp()
                    / node.getBulkSystem().getPhases()[phase].getMolarMass()
                    / node.getBulkSystem().getPhases()[phase].getNumberOfMolesInPhase()
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getDensity()
                    * node.getVelocity(phase);
            return 0.5 * this.calcWallFrictionFactor(phase, node) * Math.pow(prandtlNumber, -2.0 / 3.0) * temp;
        }
    }

	/** {@inheritDoc} */
    @Override
	public double calcWallMassTransferCoefficient(int phase, double schmidtNumber, FlowNodeInterface node) {
        if (Math.abs(node.getReynoldsNumber()) < 2000) {
            return 3.66 / node.getHydraulicDiameter(phase) / schmidtNumber
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getKinematicViscosity();
        } else {
            double temp = node.getVelocity(phase);
            return 0.5 * this.calcWallFrictionFactor(phase, node) * Math.pow(schmidtNumber, -2.0 / 3.0) * temp;
        }
    }

	/** {@inheritDoc} */
    @Override
	public double calcInterphaseMassTransferCoefficient(int phase, double schmidtNumber, FlowNodeInterface node) {
        double redMassTrans = 0.0, massTrans = 0.0;
        if (phase == 0) {
            double c2 = 0.46, c3 = 0.68, c4 = 0.5;
            redMassTrans = c2 * Math.pow(node.getReynoldsNumber(phase), c3) * Math.pow(schmidtNumber, c4);
            // System.out.println("red gas " +
            // redMassTrans/Math.pow(node.getReynoldsNumber(phase),c3));
            // System.out.println("sc gas " + schmidtNumber);
            massTrans = redMassTrans
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getKinematicViscosity()
                    / schmidtNumber / node.getGeometry().getDiameter();
        }
        if (phase == 1) {
            double c2 = 0.181, c3 = 0.72, c4 = 0.33;
            redMassTrans = c2 * Math.pow(node.getReynoldsNumber(phase), c3) * Math.pow(schmidtNumber, c4);
            // System.out.println("red liq" +
            // redMassTrans/Math.pow(node.getReynoldsNumber(phase),c3));
            // System.out.println("sc liq " + schmidtNumber);
            massTrans = redMassTrans
                    * node.getBulkSystem().getPhases()[phase].getPhysicalProperties().getKinematicViscosity()
                    / schmidtNumber / node.getGeometry().getDiameter();
        }
        return massTrans;
    }
}
