/*
 * FrictionFactorBaseClass.java
 *
 * Created on 12. juni 2001, 19:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient;

import neqsim.MathLib.generalMath.GeneralMath;
import neqsim.fluidMechanics.flowNode.FlowNodeInterface;

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
    private static final long serialVersionUID = 1000;

    /**
     *
     * frictionfactor.
     */
    public InterphaseTransportCoefficientBaseClass() {}

    /**
     * <p>
     * Constructor for InterphaseTransportCoefficientBaseClass.
     * </p>
     *
     * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public InterphaseTransportCoefficientBaseClass(FlowNodeInterface node) {
        // flowNode = node;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node) {
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
