package neqsim.fluidMechanics.flowSystem.twoPhaseFlowSystem;

import neqsim.fluidMechanics.flowSystem.FlowSystem;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>Abstract TwoPhaseFlowSystem class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class TwoPhaseFlowSystem extends FlowSystem {

    private static final long serialVersionUID = 1000;
    // FlowNodeInterface[] test = new AnnularFlow[100];
    // public FluidMechanicsInterface[] flowNode;
    public PipeData pipe;

    /**
     * <p>Constructor for TwoPhaseFlowSystem.</p>
     */
    public TwoPhaseFlowSystem() {}

    /**
     * <p>Constructor for TwoPhaseFlowSystem.</p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public TwoPhaseFlowSystem(SystemInterface system) {}

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {}
}
