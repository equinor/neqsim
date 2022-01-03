/*
 * FrictionFactorBaseClass.java
 *
 * Created on 12. juni 2001, 19:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.interphaseReactorFlow;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.InterphaseTwoPhase;

/**
 * <p>InterphaseReactorFlow class.</p>
 *
 * @author esol
 */
public class InterphaseReactorFlow extends InterphaseTwoPhase {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new FrictionFactorBaseClass All frictionfactors are the fanning
     * frictionfactor.
     */
    public InterphaseReactorFlow() {
    }

    /**
     * <p>Constructor for InterphaseReactorFlow.</p>
     *
     * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public InterphaseReactorFlow(FlowNodeInterface node) {
        // flowNode = node;
    }

}
