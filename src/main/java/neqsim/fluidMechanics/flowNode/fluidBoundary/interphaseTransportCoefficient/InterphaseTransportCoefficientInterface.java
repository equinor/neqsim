/*
 * FrictionFactorInterface.java
 *
 * Created on 12. juni 2001, 19:57
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;

/**
 *
 * @author esol
 * @version
 */
public interface InterphaseTransportCoefficientInterface {

    public double calcWallFrictionFactor(FlowNodeInterface node);

    public double calcWallFrictionFactor(int phase, FlowNodeInterface node);

    public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node);

    public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node);

    public double calcWallMassTransferCoefficient(int phase, double schmidt, FlowNodeInterface node);

    public double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node);

    public double calcInterphaseMassTransferCoefficient(int phase, double schmidt, FlowNodeInterface node);

    public double calcWallHeatTransferCoefficient(int phase, FlowNodeInterface node);

}
