package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * InterphaseTransportCoefficientInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface InterphaseTransportCoefficientInterface {
  /**
   * calcWallFrictionFactor.
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallFrictionFactor(FlowNodeInterface node);

  /**
   * calcWallFrictionFactor.
   *
   * @param phase a int
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node);

  /**
   * calcInterPhaseFrictionFactor.
   *
   * @param phase a int
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node);

  /**
   * calcInterphaseHeatTransferCoefficient.
   *
   * @param phase a int
   * @param prandtlNumber a double
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node);

  /**
   * calcInterphaseMassTransferCoefficient.
   *
   * @param phase a int
   * @param schmidt a double
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcInterphaseMassTransferCoefficient(int phase, double schmidt, FlowNodeInterface node);

  /**
   * calcWallMassTransferCoefficient.
   *
   * @param phase a int
   * @param schmidt a double
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallMassTransferCoefficient(int phase, double schmidt, FlowNodeInterface node);

  /**
   * calcWallHeatTransferCoefficient.
   *
   * @param phase a int
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallHeatTransferCoefficient(int phase, FlowNodeInterface node);

  /**
   * calcWallHeatTransferCoefficient.
   *
   * @param phase a int
   * @param prandtlNumber a double
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node);
}
