package neqsim.fluidmechanics.flowsystem.twophaseflowsystem;

import neqsim.fluidmechanics.flowsystem.FlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract TwoPhaseFlowSystem class.
 * </p>
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
   * <p>
   * Constructor for TwoPhaseFlowSystem.
   * </p>
   */
  public TwoPhaseFlowSystem() {}

  /**
   * <p>
   * Constructor for TwoPhaseFlowSystem.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TwoPhaseFlowSystem(SystemInterface system) {}
}
