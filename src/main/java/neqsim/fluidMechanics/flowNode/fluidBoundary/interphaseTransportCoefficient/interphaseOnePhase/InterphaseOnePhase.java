/*
 * FrictionFactorBaseClass.java
 *
 * Created on 12. juni 2001, 19:58
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseOnePhase;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.InterphaseTransportCoefficientBaseClass;

/**
 * <p>
 * InterphaseOnePhase class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseOnePhase extends InterphaseTransportCoefficientBaseClass {
    private static final long serialVersionUID = 1000;

    /**
     *
     * frictionfactor.
     */
    public InterphaseOnePhase() {}

    /**
     * <p>
     * Constructor for InterphaseOnePhase.
     * </p>
     *
     * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public InterphaseOnePhase(FlowNodeInterface node) {
        // flowNode = node;
    }
}
