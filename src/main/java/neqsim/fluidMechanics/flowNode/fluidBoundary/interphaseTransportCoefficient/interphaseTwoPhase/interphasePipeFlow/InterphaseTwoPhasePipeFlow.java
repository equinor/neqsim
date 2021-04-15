/*
 * FrictionFactorBaseClass.java
 *
 * Created on 12. juni 2001, 19:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.interphasePipeFlow;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.InterphaseTwoPhase;

/**
 *
 * @author esol
 * @version
 */
public class InterphaseTwoPhasePipeFlow extends InterphaseTwoPhase {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new FrictionFactorBaseClass All frictionfactors are the fanning
     * frictionfactor.
     */

    public InterphaseTwoPhasePipeFlow() {
    }

    public InterphaseTwoPhasePipeFlow(FlowNodeInterface node) {
        // flowNode = node;
    }

}
