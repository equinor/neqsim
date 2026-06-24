package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphaseonephase;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.InterphaseTransportCoefficientBaseClass;

/**
 * InterphaseOnePhase class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseOnePhase extends InterphaseTransportCoefficientBaseClass {
  /**
   * Constructor for InterphaseOnePhase.
   */
  public InterphaseOnePhase() {
  }

  /**
   * Constructor for InterphaseOnePhase.
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseOnePhase(FlowNodeInterface node) {
    // flowNode = node;
  }
}
