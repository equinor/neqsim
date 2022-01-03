/*
 * FrictionFactorBaseClass.java
 *
 * Created on 12. juni 2001, 19:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.interphasePipeFlow;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;

/**
 * <p>InterphaseAnnularFlow class.</p>
 *
 * @author esol
 */
public class InterphaseAnnularFlow extends InterphaseStratifiedFlow {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new FrictionFactorBaseClass All frictionfactors are the fanning
     * frictionfactor.
     */
    public InterphaseAnnularFlow() {
    }

    /**
     * <p>Constructor for InterphaseAnnularFlow.</p>
     *
     * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public InterphaseAnnularFlow(FlowNodeInterface node) {
        // flowNode = node;
    }

}
