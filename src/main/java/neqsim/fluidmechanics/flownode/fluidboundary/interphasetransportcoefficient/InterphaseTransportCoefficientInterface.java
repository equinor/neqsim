package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * <p>
 * InterphaseTransportCoefficientInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface InterphaseTransportCoefficientInterface {
  /**
   * <p>
   * calcWallFrictionFactor.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallFrictionFactor(FlowNodeInterface node);

  /**
   * <p>
   * calcWallFrictionFactor.
   * </p>
   *
   * @param phase a int
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node);

  /**
   * <p>
   * calcInterPhaseFrictionFactor.
   * </p>
   *
   * @param phase a int
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node);

  /**
   * <p>
   * calcInterphaseHeatTransferCoefficient.
   * </p>
   *
   * @param phase a int
   * @param prandtlNumber a double
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber,
      FlowNodeInterface node);

  /**
   * <p>
   * calcInterphaseMassTransferCoefficient.
   * </p>
   *
   * @param phase a int
   * @param schmidt a double
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcInterphaseMassTransferCoefficient(int phase, double schmidt,
      FlowNodeInterface node);

  /**
   * <p>
   * calcWallMassTransferCoefficient.
   * </p>
   *
   * @param phase a int
   * @param schmidt a double
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallMassTransferCoefficient(int phase, double schmidt, FlowNodeInterface node);

  /**
   * <p>
   * calcWallHeatTransferCoefficient.
   * </p>
   *
   * @param phase a int
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallHeatTransferCoefficient(int phase, FlowNodeInterface node);

  /**
   * <p>
   * calcWallHeatTransferCoefficient.
   * </p>
   *
   * @param phase a int
   * @param prandtlNumber a double
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   * @return a double
   */
  public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber,
      FlowNodeInterface node);
}
