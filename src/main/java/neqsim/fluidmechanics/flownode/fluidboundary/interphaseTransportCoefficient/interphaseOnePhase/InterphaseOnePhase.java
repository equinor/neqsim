package neqsim.fluidmechanics.flownode.fluidboundary.interphaseTransportCoefficient.interphaseOnePhase;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphaseTransportCoefficient.InterphaseTransportCoefficientBaseClass;

/**
 * <p>
 * InterphaseOnePhase class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseOnePhase extends InterphaseTransportCoefficientBaseClass {
  /**
   * <p>
   * Constructor for InterphaseOnePhase.
   * </p>
   */
  public InterphaseOnePhase() {}

  /**
   * <p>
   * Constructor for InterphaseOnePhase.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseOnePhase(FlowNodeInterface node) {
    // flowNode = node;
  }
}
