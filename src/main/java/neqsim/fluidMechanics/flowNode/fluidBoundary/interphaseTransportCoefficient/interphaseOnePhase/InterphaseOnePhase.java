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
   * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
   */
  public InterphaseOnePhase(FlowNodeInterface node) {
    // flowNode = node;
  }
}
