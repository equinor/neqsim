package neqsim.fluidmechanics.flowsystem.onephaseflowsystem;

import neqsim.fluidmechanics.flowsystem.FlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;

/**
 * Abstract OnePhaseFlowSystem class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class OnePhaseFlowSystem extends FlowSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // public FluidMechanicsInterface[] flowNode;
  public PipeData pipe;

  /**
   * Constructor for OnePhaseFlowSystem.
   */
  public OnePhaseFlowSystem() {
  }

  /**
   * Constructor for OnePhaseFlowSystem.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public OnePhaseFlowSystem(SystemInterface system) {
  }
}
