package neqsim.fluidmechanics.flowsystem.twophaseflowsystem;

import neqsim.fluidmechanics.flowsystem.FlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;

/**
 * Abstract TwoPhaseFlowSystem class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class TwoPhaseFlowSystem extends FlowSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  // FlowNodeInterface[] test = new AnnularFlow[100];
  // public FluidMechanicsInterface[] flowNode;
  public PipeData pipe;

  /**
   * Constructor for TwoPhaseFlowSystem.
   */
  public TwoPhaseFlowSystem() {
  }

  /**
   * Constructor for TwoPhaseFlowSystem.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TwoPhaseFlowSystem(SystemInterface system) {
  }
}
