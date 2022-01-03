/*
 * FrictionFactorBaseClass.java
 *
 * Created on 12. juni 2001, 19:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.interphasePipeFlow;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.InterphaseTwoPhase;

/**
 * <p>InterphaseTwoPhasePipeFlow class.</p>
 *
 * @author esol
 */
public class InterphaseTwoPhasePipeFlow extends InterphaseTwoPhase {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new FrictionFactorBaseClass All frictionfactors are the fanning
     * frictionfactor.
     */
    public InterphaseTwoPhasePipeFlow() {
    }

    /**
     * <p>Constructor for InterphaseTwoPhasePipeFlow.</p>
     *
     * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public InterphaseTwoPhasePipeFlow(FlowNodeInterface node) {
        // flowNode = node;
    }

}
