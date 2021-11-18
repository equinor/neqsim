

package neqsim.fluidMechanics.flowSystem.twoPhaseFlowSystem;

import neqsim.fluidMechanics.flowSystem.FlowSystem;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;

public abstract class TwoPhaseFlowSystem extends FlowSystem {
    private static final long serialVersionUID = 1000;
    // FlowNodeInterface[] test = new AnnularFlow[100];
    // public FluidMechanicsInterface[] flowNode;
    public PipeData pipe;

    public TwoPhaseFlowSystem() {}

    public TwoPhaseFlowSystem(SystemInterface system) {}

    public static void main(String[] args) {}
}
