/*
 * FrictionFactorBaseClass.java
 *
 * Created on 12. juni 2001, 19:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.InterphaseTransportCoefficientBaseClass;

/**
 *
 * @author esol
 * @version
 */
public class InterphaseTwoPhase extends InterphaseTransportCoefficientBaseClass {
    private static final long serialVersionUID = 1000;

    /**
     * Creates new FrictionFactorBaseClass All frictionfactors are the fanning frictionfactor.
     */

    public InterphaseTwoPhase() {}

    public InterphaseTwoPhase(FlowNodeInterface node) {
        // flowNode = node;
    }
}
