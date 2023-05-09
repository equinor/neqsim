package neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem;

import neqsim.fluidMechanics.flowSystem.FlowSystem;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract OnePhaseFlowSystem class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class OnePhaseFlowSystem extends FlowSystem {
  private static final long serialVersionUID = 1000;

  // public FluidMechanicsInterface[] flowNode;
  public PipeData pipe;

  /**
   * <p>
   * Constructor for OnePhaseFlowSystem.
   * </p>
   */
  public OnePhaseFlowSystem() {}

  /**
   * <p>
   * Constructor for OnePhaseFlowSystem.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public OnePhaseFlowSystem(SystemInterface system) {}
}
